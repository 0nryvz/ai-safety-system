import 'dart:async';

import 'package:camera/camera.dart';
import 'package:flutter/foundation.dart';

import '../../core/config/app_config.dart';
import '../../core/error/gateway_failure.dart';
import '../../core/network/api_client.dart';
import 'native_jpeg_encoder.dart';

/// Bir karenin akıbeti. `skipped` hata değildir: yayın durdurulduğu veya
/// yeniden bağlanıldığı için geçersizleşen kareleri ifade eder.
enum FrameOutcome { sent, skipped, failed }

class FrameUploadResult {
  final FrameOutcome outcome;
  final GatewayFailure? failure;

  const FrameUploadResult.sent()
      : outcome = FrameOutcome.sent,
        failure = null;

  const FrameUploadResult.skipped()
      : outcome = FrameOutcome.skipped,
        failure = null;

  const FrameUploadResult.failed(this.failure) : outcome = FrameOutcome.failed;

  bool get isSent => outcome == FrameOutcome.sent;
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
  });

  int get outputWidth => sourceWidth ~/ step;
  int get outputHeight => sourceHeight ~/ step;
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
      AppConfig.maxConcurrentHttpUploads.clamp(2, 12);

  CameraFrameService({
    ApiClient? apiClient,
    NativeJpegEncoder? encoder,
  })  : _apiClient = apiClient ?? ApiClient(),
        _encoder = encoder ?? NativeJpegEncoder();

  Future<void> warmUp() async {}

  void cancelUploads() {
    _uploadEpoch++;

    for (final ready in _uploadQueue) {
      ready.complete(const FrameUploadResult.skipped());
    }
    _uploadQueue.clear();
  }

  /// Plane'leri kopyalar; mümkünse Dart'ta küçültür ki MethodChannel küçük
  /// kalsın (tam çözünürlük YUV FPS'i 4'e düşürüyordu).
  RawYuvFrame? extractFrame(
    CameraImage cameraImage, {
    int? encodeWidth,
  }) {
    if (cameraImage.planes.length < 3) {
      return null;
    }

    final srcW = cameraImage.width;
    final srcH = cameraImage.height;

    if (srcW <= 0 || srcH <= 0) {
      return null;
    }

    final targetW = (encodeWidth ?? AppConfig.targetEncodeWidth).clamp(80, 1280);
    final step = ((srcW + targetW - 1) ~/ targetW).clamp(1, 16);
    final outW = (srcW ~/ step) & ~1;
    final outH = (srcH ~/ step) & ~1;

    if (outW < 2 || outH < 2) {
      return null;
    }

    final yPlane = cameraImage.planes[0];
    final uPlane = cameraImage.planes[1];
    final vPlane = cameraImage.planes[2];

    if (step == 1) {
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
        step: 1,
      );
    }

    final ySrc = yPlane.bytes;
    final yRow = yPlane.bytesPerRow;
    final yOut = Uint8List(outW * outH);
    var yi = 0;

    for (var row = 0; row < outH; row++) {
      final srcOff = row * step * yRow;
      if (srcOff + (outW - 1) * step >= ySrc.length) {
        return null;
      }
      for (var col = 0; col < outW; col++) {
        yOut[yi++] = ySrc[srcOff + col * step];
      }
    }

    final outCw = outW ~/ 2;
    final outCh = outH ~/ 2;
    final uSrc = uPlane.bytes;
    final vSrc = vPlane.bytes;
    final uRow = uPlane.bytesPerRow;
    final vRow = vPlane.bytesPerRow;
    final uPix = uPlane.bytesPerPixel ?? 1;
    final vPix = vPlane.bytesPerPixel ?? 1;
    final uOut = Uint8List(outCw * outCh);
    final vOut = Uint8List(outCw * outCh);
    var ci = 0;

    for (var row = 0; row < outCh; row++) {
      final uOff = row * step * uRow;
      final vOff = row * step * vRow;
      for (var col = 0; col < outCw; col++) {
        final sx = col * step;
        final uIdx = uOff + sx * uPix;
        final vIdx = vOff + sx * vPix;
        if (uIdx >= uSrc.length || vIdx >= vSrc.length) {
          return null;
        }
        uOut[ci] = uSrc[uIdx];
        vOut[ci] = vSrc[vIdx];
        ci++;
      }
    }

    return RawYuvFrame(
      yBytes: yOut,
      uBytes: uOut,
      vBytes: vOut,
      yRowStride: outW,
      uRowStride: outCw,
      vRowStride: outCw,
      uPixelStride: 1,
      vPixelStride: 1,
      sourceWidth: outW,
      sourceHeight: outH,
      step: 1,
    );
  }

  /// Yalnızca JPEG encode. Slot'u hızlı serbest bırakmak için upload ayrıdır.
  Future<Uint8List?> encodeJpeg(
    RawYuvFrame frame, {
    int? jpegQuality,
  }) async {
    final epoch = _uploadEpoch;
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
        quality: jpegQuality ?? AppConfig.jpegQuality,
      );

      encodeStopwatch.stop();

      if (epoch != _uploadEpoch) {
        return null;
      }

      if (AppConfig.frameDiagnostics) {
        debugPrint(
          'FRAME PERF | jpeg=${encodeStopwatch.elapsedMilliseconds}ms | '
          '${frame.outputWidth}x${frame.outputHeight} | '
          'size=${jpegBytes?.length ?? 0}',
        );
      }

      if (jpegBytes == null || jpegBytes.isEmpty) {
        return null;
      }

      return jpegBytes;
    } catch (e) {
      if (AppConfig.frameDiagnostics) {
        debugPrint('FRAME PERF | ENCODE ERROR | error=$e');
      }
      return null;
    } finally {
      _pendingWork--;
      _completePendingIfNeeded();
    }
  }

  /// Sıra beklemeden paralel HTTP. Canlı yayında timestamp yeterlidir.
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

      if (AppConfig.frameDiagnostics) {
        debugPrint(
          'FRAME PERF | upload=${uploadStopwatch.elapsedMilliseconds}ms | '
          'size=${ready.jpegBytes!.length}',
        );
      }

      ready.complete(
        response.statusCode == 202
            ? const FrameUploadResult.sent()
            : FrameUploadResult.failed(
                GatewayFailure.fromStatusCode(response.statusCode),
              ),
      );
    } catch (e) {
      if (epochAtStart != _uploadEpoch) {
        ready.complete(const FrameUploadResult.skipped());
        return;
      }

      if (AppConfig.frameDiagnostics) {
        debugPrint('FRAME PERF | UPLOAD ERROR | error=$e');
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
