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
/// CameraImage buffer'ı callback biter bitmez geri verilir.
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

  // Encode paralel çalıştığı için sırasız bitebilir. Gateway zaman damgası
  // geriye giden kareyi elediğinden upload sırası korunmak zorunda.
  int _nextSequence = 0;
  int _nextUploadSequence = 0;
  final Map<int, _ReadyFrame> _readyFrames = {};
  bool _draining = false;

  Completer<void>? _pendingUploadsCompleter;

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
  }

  RawYuvFrame? extractFrame(CameraImage cameraImage) {
    if (cameraImage.planes.length < 3) {
      return null;
    }

    final srcW = cameraImage.width;
    final srcH = cameraImage.height;

    if (srcW <= 0 || srcH <= 0) {
      return null;
    }

    // Tam sayı adımla küçültüldüğü için yukarı yuvarlamak hedefin çok altına
    // düşürür (720 -> 360). Aşağı yuvarlayıp hedefin altına inmemeyi seçiyoruz.
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

    final totalStopwatch = Stopwatch()..start();

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

      final ready = _ReadyFrame(
        cameraId: cameraId,
        sessionId: sessionId,
        frameTimestamp: frameTimestamp,
        jpegBytes: jpegBytes,
        epoch: epoch,
        encodeMs: encodeStopwatch.elapsedMilliseconds,
        label: '${frame.outputWidth}x${frame.outputHeight} '
            '(src ${frame.sourceWidth}x${frame.sourceHeight})',
      );

      _readyFrames[sequence] = ready;
      unawaited(_drainUploadQueue());

      final result = await ready.result.future;

      totalStopwatch.stop();

      return result;
    } catch (e) {
      totalStopwatch.stop();

      unawaited(_registerSkip(sequence, epoch));

      if (epoch != _uploadEpoch) {
        return const FrameUploadResult.skipped();
      }

      if (AppConfig.frameDiagnostics) {
        debugPrint(
          'FRAME PERF | ERROR | '
          'total=${totalStopwatch.elapsedMilliseconds}ms | '
          'error=$e',
        );
      }

      // Encode hatası ağ hatası değil; yeniden denenebilir sayılır.
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

  /// Sıra numarasında boşluk kalırsa kuyruk kilitlenir; atlanan kareler de
  /// kaydedilmeli.
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
    unawaited(_drainUploadQueue());

    return skipped.result.future;
  }

  /// Kareleri yakalanma sırasına göre tek tek gönderir.
  Future<void> _drainUploadQueue() async {
    if (_draining) {
      return;
    }

    _draining = true;

    try {
      while (true) {
        final ready = _readyFrames.remove(_nextUploadSequence);

        if (ready == null) {
          // Sıradaki kare henüz encode edilmedi; o bitince yeniden çağrılır.
          break;
        }

        _nextUploadSequence++;

        if (ready.epoch != _uploadEpoch || ready.jpegBytes == null) {
          ready.complete(const FrameUploadResult.skipped());
          continue;
        }

        try {
          final uploadStopwatch = Stopwatch()..start();

          final response = await _apiClient.postJpeg(
            path: '/api/v1/sessions/${ready.sessionId}/frames',
            cameraId: ready.cameraId,
            frameTimestamp: ready.frameTimestamp,
            jpegBytes: ready.jpegBytes!,
          );

          uploadStopwatch.stop();

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
          if (AppConfig.frameDiagnostics) {
            debugPrint('FRAME PERF | UPLOAD ERROR | error=$e');
          }

          ready.complete(
            FrameUploadResult.failed(
              GatewayFailure.network(detail: e.toString()),
            ),
          );
        }
      }
    } finally {
      _draining = false;
    }

    // Drain sırasında yeni kare hazır olmuş olabilir.
    if (_readyFrames.containsKey(_nextUploadSequence)) {
      unawaited(_drainUploadQueue());
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
