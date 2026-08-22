import 'package:camera_stream_app/core/config/app_config.dart';
import 'package:camera_stream_app/features/streaming/camera_frame_service.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';

RawYuvFrame _frame({
  required int sourceWidth,
  required int sourceHeight,
  required int step,
  int rotationDegrees = 0,
}) {
  final empty = Uint8List(0);

  return RawYuvFrame(
    yBytes: empty,
    uBytes: empty,
    vBytes: empty,
    yRowStride: sourceWidth,
    uRowStride: sourceWidth ~/ 2,
    vRowStride: sourceWidth ~/ 2,
    uPixelStride: 1,
    vPixelStride: 1,
    sourceWidth: sourceWidth,
    sourceHeight: sourceHeight,
    step: step,
    rotationDegrees: rotationDegrees,
  );
}

void main() {
  group('downsampleStep', () {
    test('medium kaynağında 720 hedefi native downsample yapmaz', () {
      expect(
        CameraFrameService.downsampleStep(sourceWidth: 720, targetWidth: 720),
        1,
      );
    });

    test('kaynak hedeften küçükse upscale yerine step 1 kalır', () {
      for (final sourceWidth in [320, 480, 640, 704]) {
        expect(
          CameraFrameService.downsampleStep(
            sourceWidth: sourceWidth,
            targetWidth: 720,
          ),
          1,
          reason: '$sourceWidth kaynağı büyütülmemeli',
        );
      }
    });

    test('720 kaynakta 640 hedefi kareyi 360a çökertir', () {
      // Bu iterasyonda 640 hedefinin neden kullanılamadığının kaydı: native
      // encoder yalnızca tamsayı step destekliyor.
      final step = CameraFrameService.downsampleStep(
        sourceWidth: 720,
        targetWidth: 640,
      );

      expect(step, 2);
      expect(
        _frame(sourceWidth: 720, sourceHeight: 480, step: step).outputWidth,
        360,
      );
    });

    test('yüksek çözünürlüklü kaynak hedefe doğru küçültülür', () {
      expect(
        CameraFrameService.downsampleStep(sourceWidth: 1280, targetWidth: 720),
        2,
      );
      expect(
        CameraFrameService.downsampleStep(sourceWidth: 1920, targetWidth: 720),
        3,
      );
    });
  });

  group('kodlanmış kare boyutu', () {
    test('medium kaynağı 720x480 olarak kodlanır', () {
      final frame = _frame(sourceWidth: 720, sourceHeight: 480, step: 1);

      expect(frame.outputWidth, 720);
      expect(frame.outputHeight, 480);
    });

    test('boyutlar NV21 için çift sayıya indirilir', () {
      final frame = _frame(sourceWidth: 721, sourceHeight: 481, step: 1);

      expect(frame.outputWidth.isEven, isTrue);
      expect(frame.outputHeight.isEven, isTrue);
      expect(frame.outputWidth, 720);
      expect(frame.outputHeight, 480);
    });

    test('90 ve 270 derecede eksenler yer değiştirir', () {
      for (final rotation in [90, 270]) {
        final frame = _frame(
          sourceWidth: 720,
          sourceHeight: 480,
          step: 1,
          rotationDegrees: rotation,
        );

        expect(frame.outputWidth, 480, reason: '$rotation derece');
        expect(frame.outputHeight, 720, reason: '$rotation derece');
      }
    });

    test('0 ve 180 derecede boyutlar korunur', () {
      for (final rotation in [0, 180]) {
        final frame = _frame(
          sourceWidth: 720,
          sourceHeight: 480,
          step: 1,
          rotationDegrees: rotation,
        );

        expect(frame.outputWidth, 720, reason: '$rotation derece');
        expect(frame.outputHeight, 480, reason: '$rotation derece');
      }
    });

    test('döndürme piksel sayısını ve kalite profilini değiştirmez', () {
      final upright = _frame(sourceWidth: 720, sourceHeight: 480, step: 1);
      final rotated = _frame(
        sourceWidth: 720,
        sourceHeight: 480,
        step: 1,
        rotationDegrees: 90,
      );

      expect(
        rotated.outputWidth * rotated.outputHeight,
        upright.outputWidth * upright.outputHeight,
      );
    });

    test('döndürme alt örneklemeden sonra uygulanır', () {
      final frame = _frame(
        sourceWidth: 1280,
        sourceHeight: 720,
        step: 2,
        rotationDegrees: 90,
      );

      expect(frame.outputWidth, 360);
      expect(frame.outputHeight, 640);
    });

    test('çıktı hiçbir zaman kaynaktan büyük değildir', () {
      for (final source in [
        (320, 240),
        (640, 480),
        (720, 480),
        (1280, 720),
        (1920, 1080),
      ]) {
        final step = CameraFrameService.downsampleStep(
          sourceWidth: source.$1,
          targetWidth: AppConfig.targetEncodeWidth,
        );
        final frame = _frame(
          sourceWidth: source.$1,
          sourceHeight: source.$2,
          step: step,
        );

        expect(frame.outputWidth, lessThanOrEqualTo(source.$1));
        expect(frame.outputHeight, lessThanOrEqualTo(source.$2));
      }
    });
  });

  group('varsayılan profil', () {
    test('hedef genişlik 720-class, 160/128 sınıfına dönüş yok', () {
      expect(AppConfig.targetEncodeWidth, 720);
      expect(AppConfig.degradedEncodeWidth, greaterThanOrEqualTo(480));
    });

    test('JPEG kalitesi PPE detayı için 60-65 aralığında', () {
      expect(AppConfig.jpegQuality, inInclusiveRange(60, 65));
      expect(AppConfig.degradedJpegQuality, greaterThanOrEqualTo(50));
    });

    test('kabul tabanı 5 FPS', () {
      expect(AppConfig.minFps, 5);
    });

    test('upload kuyruğu ve eşzamanlılık sınırlı', () {
      expect(AppConfig.maxQueuedUploads, greaterThan(0));
      expect(AppConfig.maxQueuedUploads, lessThanOrEqualTo(30));
      expect(AppConfig.maxConcurrentHttpUploads, lessThanOrEqualTo(6));
    });

    test('metronom yalnızca pacedFps üzerinden türetilir', () {
      // CameraX AE aralığı ayrı sabittir; pacing'i değiştirmek kamera bind'ini
      // etkilememeli.
      expect(
        AppConfig.paceInterval.inMilliseconds,
        (1000 / AppConfig.pacedFps).round().clamp(50, 250),
      );
      expect(AppConfig.cameraTargetFpsOrNull, AppConfig.cameraTargetFps);
    });
  });

  group('upload sonucu', () {
    test('yalnızca 202 kabul sayılır ve süresini taşır', () {
      const sent = FrameUploadResult.sent(uploadMs: 42);

      expect(sent.isSent, isTrue);
      expect(sent.uploadMs, 42);
    });

    test('atlanan kare kabul sayılmaz', () {
      const skipped = FrameUploadResult.skipped();

      expect(skipped.isSent, isFalse);
      expect(skipped.uploadMs, 0);
    });
  });
}
