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

/// Stream callback içinde CameraImage'den alınan (ve mümkünse küçültülmüş) kopya.
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

  int _pendingUploads = 0;
  int _uploadEpoch = 0;

  int _nextSequence = 0;
  int _nextUploadSequence = 0;
  final Map<int, _ReadyFrame> _readyFrames = {};

  /// Sıralı kuyruktan paralel HTTP; zaman damgası yakalanma anıdır.
  int _inFlightUploads = 0;

  Completer<void>? _pendingUploadsCompleter;

  int get _maxParallelUploads =>
      AppConfig.maxConcurrentFrameUploads.clamp(1, 6);

  CameraFrameService({
    ApiClient? apiClient,
    NativeJpegEncoder? encoder,
  })  : _apiClient = apiClient ?? ApiClient(),
        _encoder = encoder ?? NativeJpegEncoder();

  Future<void> warmUp() async {}

  void cancelUploads() {
    _uploadEpoch++;

    for (final ready in _readyFrames.values) {
      ready.complete(const FrameUploadResult.skipped());
    }

    _readyFrames.clear();
    _nextSequence = 0;
    _nextUploadSequence = 0;
    // Uçuştaki HTTP'lerin finally bloğu _inFlightUploads'ı düşürür;
    // burada sıfırlamak sayacı bozar.
  }

  /// CameraImage buffer'ı callback bitince geri alındığı için düzlemler
  /// hemen kopyalanır. İndirgeme native encoder'da [step] ile yapılır; burada
  /// ağır piksel döngüsü callback'i kilitlemez.
  RawYuvFrame? extractFrame(CameraImage cameraImage) {
    if (cameraImage.planes.length < 3) {
      return null;
    }

    final srcW = cameraImage.width;
    final srcH = cameraImage.height;

    if (srcW <= 0 || srcH <= 0) {
      return null;
    }

    final step = (srcW ~/ AppConfig.targetEncodeWidth).clamp(1, 16);

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
    );
  }

  Future<FrameUploadResult> encodeAndUpload({
    required String cameraId,
    required String sessionId,
    required DateTime frameTimestamp,
    required RawYuvFrame frame,
  }) async {
    final epoch = _uploadEpoch;
    final sequence = _nextSequence++;
    _pendingUploads++;

    try {
      if (epoch != _uploadEpoch) {
        return _registerSkip(sequence, epoch);
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
        quality: AppConfig.jpegQuality,
      );

      encodeStopwatch.stop();

      if (epoch != _uploadEpoch) {
        return _registerSkip(sequence, epoch);
      }

      if (jpegBytes == null || jpegBytes.isEmpty) {
        unawaited(_registerSkip(sequence, epoch));
        return FrameUploadResult.failed(
          GatewayFailure.network(detail: 'jpeg_encode_failed'),
        );
      }

      final ready = _ReadyFrame(
        cameraId: cameraId,
        sessionId: sessionId,
        frameTimestamp: frameTimestamp,
        jpegBytes: jpegBytes,
        epoch: epoch,
        encodeMs: encodeStopwatch.elapsedMilliseconds,
        label: '${frame.outputWidth}x${frame.outputHeight}',
      );

      _readyFrames[sequence] = ready;
      _pumpUploadQueue();

      return ready.result.future;
    } catch (e) {
      unawaited(_registerSkip(sequence, epoch));

      if (epoch != _uploadEpoch) {
        return const FrameUploadResult.skipped();
      }

      if (AppConfig.frameDiagnostics) {
        debugPrint('FRAME PERF | ERROR | error=$e');
      }

      return FrameUploadResult.failed(
        GatewayFailure.network(detail: e.toString()),
      );
    } finally {
      _pendingUploads--;

      if (_pendingUploads == 0 &&
          _pendingUploadsCompleter != null &&
          !_pendingUploadsCompleter!.isCompleted) {
        _pendingUploadsCompleter!.complete();
      }
    }
  }

  Future<FrameUploadResult> _registerSkip(int sequence, int epoch) {
    if (_readyFrames.containsKey(sequence)) {
      return Future.value(const FrameUploadResult.skipped());
    }

    final skipped = _ReadyFrame(
      cameraId: '',
      sessionId: '',
      frameTimestamp: DateTime.now().toUtc(),
      jpegBytes: null,
      epoch: epoch,
      encodeMs: 0,
      label: 'skipped',
    );

    _readyFrames[sequence] = skipped;
    _pumpUploadQueue();

    return skipped.result.future;
  }

  /// Sıradaki kareleri en fazla [_maxParallelUploads] kadar paralel yollar.
  void _pumpUploadQueue() {
    while (_inFlightUploads < _maxParallelUploads) {
      final ready = _readyFrames.remove(_nextUploadSequence);
      if (ready == null) {
        break;
      }

      _nextUploadSequence++;

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
          'FRAME PERF | '
          'jpeg=${ready.encodeMs}ms | '
          'upload=${uploadStopwatch.elapsedMilliseconds}ms | '
          'size=${ready.jpegBytes!.length} bytes | '
          '${ready.label}',
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

  Future<void> waitForPendingUploads({
    Duration timeout = const Duration(milliseconds: 250),
  }) async {
    if (_pendingUploads == 0) {
      return;
    }

    _pendingUploadsCompleter ??= Completer<void>();

    try {
      await _pendingUploadsCompleter!.future.timeout(timeout);
    } on TimeoutException {
      // Stop akışını bloklamamak için.
    }

    if (_pendingUploads == 0) {
      _pendingUploadsCompleter = null;
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
  final int encodeMs;
  final String label;
  final Completer<FrameUploadResult> result = Completer<FrameUploadResult>();

  _ReadyFrame({
    required this.cameraId,
    required this.sessionId,
    required this.frameTimestamp,
    required this.jpegBytes,
    required this.epoch,
    required this.encodeMs,
    required this.label,
  });

  void complete(FrameUploadResult value) {
    if (!result.isCompleted) {
      result.complete(value);
    }
  }
}
