import 'package:camera/camera.dart';
import 'package:camera_stream_app/features/streaming/frame_rotation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

int _back(int sensor, DeviceOrientation orientation) =>
    computeFrameRotationDegrees(
      sensorOrientationDegrees: sensor,
      lensDirection: CameraLensDirection.back,
      deviceOrientation: orientation,
    );

int _front(int sensor, DeviceOrientation orientation) =>
    computeFrameRotationDegrees(
      sensorOrientationDegrees: sensor,
      lensDirection: CameraLensDirection.front,
      deviceOrientation: orientation,
    );

void main() {
  group('surfaceRotationDegrees', () {
    test('camera_android_camerax Surface sabitleriyle aynı eşleme', () {
      expect(surfaceRotationDegrees(DeviceOrientation.portraitUp), 0);
      expect(surfaceRotationDegrees(DeviceOrientation.landscapeLeft), 90);
      expect(surfaceRotationDegrees(DeviceOrientation.portraitDown), 180);
      expect(surfaceRotationDegrees(DeviceOrientation.landscapeRight), 270);
    });
  });

  group('arka kamera (isOppositeFacing)', () {
    test('gözlemlenen bug: sensör 90, portre -> 90 derece düzeltme gerekir', () {
      expect(_back(90, DeviceOrientation.portraitUp), 90);
    });

    test('sensör 90, landscapeLeft -> düzeltme gerekmez', () {
      expect(_back(90, DeviceOrientation.landscapeLeft), 0);
    });

    test('sensör 90, tüm cihaz yönleri', () {
      expect(_back(90, DeviceOrientation.portraitUp), 90);
      expect(_back(90, DeviceOrientation.landscapeLeft), 0);
      expect(_back(90, DeviceOrientation.portraitDown), 270);
      expect(_back(90, DeviceOrientation.landscapeRight), 180);
    });

    test('sensör 270 cihazı da doğru çalışır', () {
      expect(_back(270, DeviceOrientation.portraitUp), 270);
      expect(_back(270, DeviceOrientation.landscapeLeft), 180);
      expect(_back(270, DeviceOrientation.portraitDown), 90);
      expect(_back(270, DeviceOrientation.landscapeRight), 0);
    });

    test('sensör 0 olan tablet: portrede döndürme yok', () {
      expect(_back(0, DeviceOrientation.portraitUp), 0);
      expect(_back(0, DeviceOrientation.landscapeLeft), 270);
    });
  });

  group('ön kamera', () {
    test('cihaz yönü ters işaretle toplanır', () {
      expect(_front(270, DeviceOrientation.portraitUp), 270);
      expect(_front(270, DeviceOrientation.landscapeLeft), 0);
      expect(_front(270, DeviceOrientation.portraitDown), 90);
      expect(_front(270, DeviceOrientation.landscapeRight), 180);
    });

    test('aynı sensör açısında ön ve arka farklı sonuç verir', () {
      expect(
        _front(90, DeviceOrientation.landscapeLeft),
        isNot(_back(90, DeviceOrientation.landscapeLeft)),
      );
    });
  });

  group('normalizeRotationDegrees', () {
    test('sonuç her zaman [0, 360) aralığında ve 90nin katı', () {
      for (final degrees in [-450, -270, -90, 0, 90, 359, 360, 720]) {
        final normalized = normalizeRotationDegrees(degrees);

        expect(normalized, inInclusiveRange(0, 270));
        expect(normalized % 90, 0);
      }
    });

    test('dik olmayan açı en yakın 90a yuvarlanır', () {
      expect(normalizeRotationDegrees(89), 90);
      expect(normalizeRotationDegrees(44), 0);
      expect(normalizeRotationDegrees(46), 90);
    });

    test('negatif açı pozitife taşınır', () {
      expect(normalizeRotationDegrees(-90), 270);
      expect(normalizeRotationDegrees(-180), 180);
    });
  });

  group('plugin landscapeLeft yutması (kanıtlanan hata)', () {
    test('portre pencere + landscapeLeft eklenti → 90 uygulanır, 0 değil', () {
      final decision = decideFrameRotation(
        sensorOrientationDegrees: 90,
        lensDirection: CameraLensDirection.back,
        pluginOrientation: DeviceOrientation.landscapeLeft,
        windowSize: const Size(1080, 1920),
        sourceWidth: 720,
        sourceHeight: 480,
      );

      expect(decision.displayOrientation, DeviceOrientation.portraitUp);
      expect(decision.computedDegrees, 90);
      expect(decision.appliedDegrees, 90);
    });

    test('eski formül landscapeLeft ile 0 üretir; reconcile bunu yakalar', () {
      final pluginComputed = _back(90, DeviceOrientation.landscapeLeft);
      expect(pluginComputed, 0);

      expect(
        reconcileBufferRotation(
          computedRotationDegrees: pluginComputed,
          sourceWidth: 720,
          sourceHeight: 480,
          sensorOrientationDegrees: 90,
          displayIsPortrait: true,
        ),
        90,
      );
    });

    test('gerçek landscape pencerede 0 kalır; yanlış portre zorlanmaz', () {
      final decision = decideFrameRotation(
        sensorOrientationDegrees: 90,
        lensDirection: CameraLensDirection.back,
        pluginOrientation: DeviceOrientation.landscapeLeft,
        windowSize: const Size(1920, 1080),
        sourceWidth: 720,
        sourceHeight: 480,
      );

      expect(decision.displayOrientation, DeviceOrientation.landscapeLeft);
      expect(decision.appliedDegrees, 0);
    });

    test('90 sabiti yok: sensör 270 portrede 270 uygular', () {
      final decision = decideFrameRotation(
        sensorOrientationDegrees: 270,
        lensDirection: CameraLensDirection.back,
        pluginOrientation: DeviceOrientation.landscapeLeft,
        windowSize: const Size(1080, 1920),
        sourceWidth: 720,
        sourceHeight: 480,
      );

      expect(decision.appliedDegrees, 270);
    });
  });

  group('rotationSwapsAxes', () {
    test('yalnızca 90 ve 270 eksenleri değiştirir', () {
      expect(rotationSwapsAxes(0), isFalse);
      expect(rotationSwapsAxes(90), isTrue);
      expect(rotationSwapsAxes(180), isFalse);
      expect(rotationSwapsAxes(270), isTrue);
    });

    test('90 derece hard-code edilmedi: 0 ve 180 için iş yapılmaz', () {
      expect(rotationSwapsAxes(_back(90, DeviceOrientation.landscapeLeft)), isFalse);
      expect(rotationSwapsAxes(_back(90, DeviceOrientation.landscapeRight)), isFalse);
    });
  });
}
