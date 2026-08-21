import 'dart:async';

import 'package:camera/camera.dart';
import 'package:flutter/foundation.dart';

import '../../core/config/app_config.dart';
import '../../core/network/api_client.dart';
import 'jpeg_encoder_worker.dart';

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
  /// AI modeli imgsz=640 ile eğitildi; kaynak daha küçükse olduğu gibi gider.
  static const int targetEncodeWidth = 640;
  static const int jpegQuality = 70;

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
      if (!ready.result.isCompleted) {
        ready.result.complete(false);
      }
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
    final step = (srcW ~/ targetEncodeWidth).clamp(1, 16);

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

  Future<bool> encodeAndUpload({
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
        quality: jpegQuality,
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

      final uploaded = await ready.result.future;

      totalStopwatch.stop();

      return uploaded;
    } catch (e) {
      totalStopwatch.stop();

      unawaited(_registerSkip(sequence, epoch));

      if (epoch != _uploadEpoch) {
        return false;
      }

      if (AppConfig.frameDiagnostics) {
        debugPrint(
          'FRAME PERF | ERROR | '
          'total=${totalStopwatch.elapsedMilliseconds}ms | '
          'error=$e',
        );
      }

      return false;
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
  Future<bool> _registerSkip(int sequence, int epoch) {
    if (_readyFrames.containsKey(sequence)) {
      return Future.value(false);
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

        if (ready.epoch != _uploadEpoch) {
          ready.complete(false);
          continue;
        }

        if (ready.jpegBytes == null) {
          ready.complete(false);
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

          ready.complete(response.statusCode == 202);
        } catch (e) {
          if (AppConfig.frameDiagnostics) {
            debugPrint('FRAME PERF | UPLOAD ERROR | error=$e');
          }
          ready.complete(false);
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
  final Completer<bool> result = Completer<bool>();

  _ReadyFrame({
    required this.cameraId,
    required this.sessionId,
    required this.frameTimestamp,
    required this.jpegBytes,
    required this.epoch,
    required this.encodeMs,
    required this.label,
  });

  void complete(bool value) {
    if (!result.isCompleted) {
      result.complete(value);
    }
  }
}
