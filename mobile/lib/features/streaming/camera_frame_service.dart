import 'dart:async';

import 'package:camera/camera.dart';
import 'package:flutter/foundation.dart';

import '../../core/config/app_config.dart';
import '../../core/error/gateway_failure.dart';
import '../../core/network/api_client.dart';
import 'frame_rotation.dart';
import 'native_jpeg_encoder.dart';

/// Bir karenin akıbeti. `skipped` hata değildir: yayın durdurulduğu veya
/// yeniden bağlanıldığı için geçersizleşen kareleri ifade eder.
enum FrameOutcome { sent, skipped, failed }

class FrameUploadResult {
  final FrameOutcome outcome;
  final GatewayFailure? failure;

  /// HTTP isteğinin süresi. `skipped` sonuçlarda istek hiç açılmadığı için 0'dır.
  final int uploadMs;

  const FrameUploadResult.sent({this.uploadMs = 0})
      : outcome = FrameOutcome.sent,
        failure = null;

  const FrameUploadResult.skipped()
      : outcome = FrameOutcome.skipped,
        failure = null,
        uploadMs = 0;

  const FrameUploadResult.failed(this.failure, {this.uploadMs = 0})
      : outcome = FrameOutcome.failed;

  bool get isSent => outcome == FrameOutcome.sent;
}

/// Native encoder'ın ürettiği kare ve ölçüm verisi.
class EncodedFrame {
  final Uint8List bytes;
  final int width;
  final int height;
  final int quality;
  final int encodeMs;

  const EncodedFrame({
    required this.bytes,
    required this.width,
    required this.height,
    required this.quality,
    required this.encodeMs,
  });
}

/// Stream callback içinde CameraImage'den alınan kopya.
class RawYuvFrame {
  final Uint8List yBytes;
  final Uint8List uBytes;
  final Uint8List vBytes;

  final int yRowStride;
  final int uRowStride;
  final int vRowStride;
  final int uPixelStride;
  final int vPixelStride;

  final int sourceWidth;
  final int sourceHeight;
  final int step;

  /// Kareyi upright yapmak için native encoder'ın uygulayacağı saat yönü açı.
  /// Sensör koordinatlarından ekran koordinatlarına geçişi sağlar.
  final int rotationDegrees;

  /// Native tarafın Display rotation ile çapraz kontrolü / yedek hesabı için.
  final int sensorOrientation;
  final bool isBackCamera;

  const RawYuvFrame({
    required this.yBytes,
    required this.uBytes,
    required this.vBytes,
    required this.yRowStride,
    required this.uRowStride,
    required this.vRowStride,
    required this.uPixelStride,
    required this.vPixelStride,
    required this.sourceWidth,
    required this.sourceHeight,
    required this.step,
    required this.rotationDegrees,
    this.sensorOrientation = 0,
    this.isBackCamera = true,
  });

  /// MainActivity.encode() ile birebir aynı formül: NV21 kroma düzlemi yarı
  /// çözünürlüklü olduğu için boyutlar çift sayıya indirilir.
  int get _sampledWidth => (sourceWidth ~/ step) & ~1;
  int get _sampledHeight => (sourceHeight ~/ step) & ~1;

  /// Döndürme sonrası gerçek JPEG boyutu; 90/270'te eksenler yer değiştirir.
  /// Tanılama çıktısının doğru olması buna bağlı.
  int get outputWidth =>
      rotationSwapsAxes(rotationDegrees) ? _sampledHeight : _sampledWidth;

  int get outputHeight =>
      rotationSwapsAxes(rotationDegrees) ? _sampledWidth : _sampledHeight;
}

class CameraFrameService {
  final ApiClient _apiClient;
  final NativeJpegEncoder _encoder;

  int _pendingWork = 0;
  int _uploadEpoch = 0;
  int _inFlightUploads = 0;
  final List<_ReadyFrame> _uploadQueue = [];

  Completer<void>? _pendingWorkCompleter;

  int get _maxParallelUploads =>
      AppConfig.maxConcurrentHttpUploads.clamp(1, 6);

  int get _maxQueuedUploads => AppConfig.maxQueuedUploads.clamp(1, 30);

  /// Yükleme bekleyen kare sayısı. Sürekli tavana yapışması üreticinin
  /// tüketiciyi geçtiğini gösterir.
  int get queuedUploadCount => _uploadQueue.length;

  int get inFlightUploadCount => _inFlightUploads;

  CameraFrameService({
    ApiClient? apiClient,
    NativeJpegEncoder? encoder,
  })  : _apiClient = apiClient ?? ApiClient(),
        _encoder = encoder ?? NativeJpegEncoder();

  Future<void> warmUp() async {}

  /// Native encoder'ın uyguladığı tamsayı alt örnekleme adımı.
  ///
  /// Hedef genişlik kaynağa eşit veya ondan büyükse 1 döner: kare kaynak
  /// çözünürlüğünde kodlanır ve hiçbir zaman büyütülmez. Hedef kaynaktan
  /// küçükse çıktı `sourceWidth / step` olur; bu yüzden 720 kaynakta 640 hedefi
  /// step=2 üzerinden 360'a çöker. Açık çıktı boyutu native tarafta
  /// desteklenene kadar hedef, kaynak genişliğinin altına indirilmemeli.
  static int downsampleStep({
    required int sourceWidth,
    required int targetWidth,
  }) {
    final target = targetWidth.clamp(48, 1280);

    return ((sourceWidth + target - 1) ~/ target).clamp(1, 16);
  }

  void cancelUploads() {
    _uploadEpoch++;

    for (final ready in _uploadQueue) {
      ready.complete(const FrameUploadResult.skipped());
    }
    _uploadQueue.clear();
  }

  /// Plane'leri kopyalar; downsample native encode'da yapılır.
  /// Dart iç içe döngü ImageAnalysis'i kilitliyip FPS'i 3–6'ya düşürüyordu.
  RawYuvFrame? extractFrame(
    CameraImage cameraImage, {
    int? encodeWidth,
    int rotationDegrees = 0,
    int sensorOrientation = 0,
    bool isBackCamera = true,
  }) {
    if (cameraImage.planes.length < 3) {
      return null;
    }

    final srcW = cameraImage.width;
    final srcH = cameraImage.height;

    if (srcW <= 0 || srcH <= 0) {
      return null;
    }

    final step = downsampleStep(
      sourceWidth: srcW,
      targetWidth: encodeWidth ?? AppConfig.targetEncodeWidth,
    );

    final yPlane = cameraImage.planes[0];
    final uPlane = cameraImage.planes[1];
    final vPlane = cameraImage.planes[2];

    return RawYuvFrame(
      yBytes: Uint8List.fromList(yPlane.bytes),
      uBytes: Uint8List.fromList(uPlane.bytes),
      vBytes: Uint8List.fromList(vPlane.bytes),
      yRowStride: yPlane.bytesPerRow,
      uRowStride: uPlane.bytesPerRow,
      vRowStride: vPlane.bytesPerRow,
      uPixelStride: uPlane.bytesPerPixel ?? 1,
      vPixelStride: vPlane.bytesPerPixel ?? 1,
      sourceWidth: srcW,
      sourceHeight: srcH,
      step: step,
      rotationDegrees: normalizeRotationDegrees(rotationDegrees),
      sensorOrientation: normalizeRotationDegrees(sensorOrientation),
      isBackCamera: isBackCamera,
    );
  }

  /// Yalnızca JPEG encode. Slot'u hızlı serbest bırakmak için upload ayrıdır.
  Future<EncodedFrame?> encodeJpeg(
    RawYuvFrame frame, {
    int? jpegQuality,
  }) async {
    final epoch = _uploadEpoch;
    final quality = jpegQuality ?? AppConfig.jpegQuality;
    _pendingWork++;

    try {
      if (epoch != _uploadEpoch) {
        return null;
      }

      final encodeStopwatch = Stopwatch()..start();

      final jpegBytes = await _encoder.encodeYuv420(
        yBytes: frame.yBytes,
        uBytes: frame.uBytes,
        vBytes: frame.vBytes,
        yRowStride: frame.yRowStride,
        uRowStride: frame.uRowStride,
        vRowStride: frame.vRowStride,
        uPixelStride: frame.uPixelStride,
        vPixelStride: frame.vPixelStride,
        sourceWidth: frame.sourceWidth,
        sourceHeight: frame.sourceHeight,
        step: frame.step,
        rotationDegrees: frame.rotationDegrees,
        sensorOrientation: frame.sensorOrientation,
        isBackCamera: frame.isBackCamera,
        quality: quality,
      );

      encodeStopwatch.stop();

      if (epoch != _uploadEpoch) {
        return null;
      }

      if (jpegBytes == null || jpegBytes.isEmpty) {
        return null;
      }

      return EncodedFrame(
        bytes: jpegBytes,
        width: frame.outputWidth,
        height: frame.outputHeight,
        quality: quality,
        encodeMs: encodeStopwatch.elapsedMilliseconds,
      );
    } catch (e) {
      if (AppConfig.frameDiagnostics) {
        debugPrint('FRAME | ENCODE ERROR | error=$e');
      }
      return null;
    } finally {
      _pendingWork--;
      _completePendingIfNeeded();
    }
  }

  /// Sınırlı paralellikle HTTP. Canlı yayında timestamp yeterlidir.
  ///
  /// Kuyruk doluysa en eski kare düşürülür: üretici tüketiciyi geçtiğinde
  /// backlog büyütmek yerine bilinçli olarak eski kare atılır.
  Future<FrameUploadResult> uploadJpeg({
    required String cameraId,
    required String sessionId,
    required DateTime frameTimestamp,
    required Uint8List jpegBytes,
  }) {
    final epoch = _uploadEpoch;
    final ready = _ReadyFrame(
      cameraId: cameraId,
      sessionId: sessionId,
      frameTimestamp: frameTimestamp,
      jpegBytes: jpegBytes,
      epoch: epoch,
    );

    _pendingWork++;
    _uploadQueue.add(ready);

    while (_uploadQueue.length > _maxQueuedUploads) {
      _uploadQueue.removeAt(0).complete(const FrameUploadResult.skipped());
    }

    _pumpUploadQueue();

    return ready.result.future.whenComplete(() {
      _pendingWork--;
      _completePendingIfNeeded();
    });
  }

  void _pumpUploadQueue() {
    while (_inFlightUploads < _maxParallelUploads && _uploadQueue.isNotEmpty) {
      final ready = _uploadQueue.removeAt(0);

      if (ready.epoch != _uploadEpoch || ready.jpegBytes == null) {
        ready.complete(const FrameUploadResult.skipped());
        continue;
      }

      _inFlightUploads++;
      unawaited(_uploadOne(ready));
    }
  }

  Future<void> _uploadOne(_ReadyFrame ready) async {
    final epochAtStart = ready.epoch;

    try {
      if (epochAtStart != _uploadEpoch) {
        ready.complete(const FrameUploadResult.skipped());
        return;
      }

      final uploadStopwatch = Stopwatch()..start();

      final response = await _apiClient.postJpeg(
        path: '/api/v1/sessions/${ready.sessionId}/frames',
        cameraId: ready.cameraId,
        frameTimestamp: ready.frameTimestamp,
        jpegBytes: ready.jpegBytes!,
      );

      uploadStopwatch.stop();

      if (epochAtStart != _uploadEpoch) {
        ready.complete(const FrameUploadResult.skipped());
        return;
      }

      // Kabul ölçütü Gateway sözleşmesindeki 202'dir; başka hiçbir 2xx
      // "kabul edildi" sayılmaz.
      ready.complete(
        response.statusCode == 202
            ? FrameUploadResult.sent(
                uploadMs: uploadStopwatch.elapsedMilliseconds,
              )
            : FrameUploadResult.failed(
                GatewayFailure.fromStatusCode(response.statusCode),
                uploadMs: uploadStopwatch.elapsedMilliseconds,
              ),
      );
    } catch (e) {
      if (epochAtStart != _uploadEpoch) {
        ready.complete(const FrameUploadResult.skipped());
        return;
      }

      if (AppConfig.frameDiagnostics) {
        debugPrint('FRAME | UPLOAD ERROR | error=$e');
      }

      ready.complete(
        FrameUploadResult.failed(
          GatewayFailure.network(detail: e.toString()),
        ),
      );
    } finally {
      if (_inFlightUploads > 0) {
        _inFlightUploads--;
      }
      if (epochAtStart == _uploadEpoch) {
        _pumpUploadQueue();
      }
    }
  }

  void _completePendingIfNeeded() {
    if (_pendingWork == 0 &&
        _pendingWorkCompleter != null &&
        !_pendingWorkCompleter!.isCompleted) {
      _pendingWorkCompleter!.complete();
    }
  }

  Future<void> waitForPendingUploads({
    Duration timeout = const Duration(milliseconds: 250),
  }) async {
    if (_pendingWork == 0) {
      return;
    }

    _pendingWorkCompleter ??= Completer<void>();

    try {
      await _pendingWorkCompleter!.future.timeout(timeout);
    } on TimeoutException {
      // Stop akışını bloklamamak için.
    }

    if (_pendingWork == 0) {
      _pendingWorkCompleter = null;
    }
  }

  void dispose() {
    cancelUploads();
    _apiClient.close();
  }
}

class _ReadyFrame {
  final String cameraId;
  final String sessionId;
  final DateTime frameTimestamp;
  final Uint8List? jpegBytes;
  final int epoch;
  final Completer<FrameUploadResult> result = Completer<FrameUploadResult>();

  _ReadyFrame({
    required this.cameraId,
    required this.sessionId,
    required this.frameTimestamp,
    required this.jpegBytes,
    required this.epoch,
  });

  void complete(FrameUploadResult value) {
    if (!result.isCompleted) {
      result.complete(value);
    }
  }
}
