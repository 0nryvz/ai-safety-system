import 'package:camera/camera.dart';
import 'package:flutter/services.dart';

/// CameraX `ImageAnalysis` buffer'ı **sensör koordinatlarında** teslim edilir;
/// upright hale gelmesi için gereken açı `ImageProxy.imageInfo.rotationDegrees`
/// içindedir. Ancak `camera_android_camerax`, `CameraImageData`'yı yalnızca
/// format/plane/width/height ile kurar ve bu alanı taşımaz —
/// `CameraImageData` tipinde rotation alanı da yoktur. Bu yüzden açı burada
/// mevcut metadata'dan yeniden hesaplanır.
///
/// Formül CameraX `CameraOrientationUtil.getRelativeImageRotation` ile aynıdır:
///
/// ```text
/// isOppositeFacing  -> (sensor - display + 360) % 360
/// aksi halde        -> (sensor + display) % 360
/// ```
///
/// `isOppositeFacing`, CameraX'te `LENS_FACING_BACK == lensFacing` kontrolüdür;
/// ön ve harici kameralar ters yöne bakmıyor sayılır.
int computeFrameRotationDegrees({
  required int sensorOrientationDegrees,
  required CameraLensDirection lensDirection,
  required DeviceOrientation deviceOrientation,
}) {
  final sensor = normalizeRotationDegrees(sensorOrientationDegrees);
  final display = surfaceRotationDegrees(deviceOrientation);

  final isOppositeFacing = lensDirection == CameraLensDirection.back;

  return normalizeRotationDegrees(
    isOppositeFacing ? sensor - display : sensor + display,
  );
}

/// Cihaz yönünün doğal yönden saat yönünde kaç derece döndüğü.
///
/// Eşleme `camera_android_camerax`'in `setTargetRotation`'a beslediği
/// `_getRotationConstantFromDeviceOrientation` ile birebir aynıdır
/// (`Surface.rotation90` = 90°).
int surfaceRotationDegrees(DeviceOrientation orientation) {
  return switch (orientation) {
    DeviceOrientation.portraitUp => 0,
    DeviceOrientation.landscapeLeft => 90,
    DeviceOrientation.portraitDown => 180,
    DeviceOrientation.landscapeRight => 270,
  };
}

/// Açıyı en yakın 90'ın katına yuvarlayıp [0, 360) aralığına indirger.
/// NV21 paketleme yalnızca dik açıları destekler.
int normalizeRotationDegrees(int degrees) {
  final snapped = (degrees / 90).round() * 90;

  return ((snapped % 360) + 360) % 360;
}

/// 90/270'te genişlik ve yükseklik yer değiştirir.
bool rotationSwapsAxes(int rotationDegrees) {
  final rotation = normalizeRotationDegrees(rotationDegrees);

  return rotation == 90 || rotation == 270;
}

bool isPortraitSize(Size size) {
  if (size == Size.zero) {
    return true;
  }

  return size.height >= size.width;
}

/// Kamera eklentisinin `deviceOrientation` değeri UI ile çelişebilir.
///
/// `camera_android_camerax` yönü Activity configuration + display rotation
/// ile üretir. Bazı cihazlarda yayın portredeyken `landscapeLeft` gelir;
/// formül o zaman `(sensor 90 - display 90) = 0` deyip döndürmeyi yutar.
/// Flutter penceresinin gerçek en/boy oranı source of truth'tur; eklenti
/// yalnız aynı eksen içinde up/down veya left/right ayrımı için kullanılır.
DeviceOrientation resolveDisplayOrientation({
  required Size windowSize,
  required DeviceOrientation pluginOrientation,
}) {
  if (isPortraitSize(windowSize)) {
    return pluginOrientation == DeviceOrientation.portraitDown
        ? DeviceOrientation.portraitDown
        : DeviceOrientation.portraitUp;
  }

  return pluginOrientation == DeviceOrientation.landscapeRight
      ? DeviceOrientation.landscapeRight
      : DeviceOrientation.landscapeLeft;
}

/// Landscape buffer + portre UI kombinasyonunda 0/180 kabul edilmez.
///
/// Bu, eklentinin yanliş `landscapeLeft` raporladığı durumda formülün
/// `(90 - 90) = 0` üretmesini yakalar. 90 sabiti yazılmaz; eksen değişimi
/// gerektiren gerçek `sensorOrientation` kullanılır.
int reconcileBufferRotation({
  required int computedRotationDegrees,
  required int sourceWidth,
  required int sourceHeight,
  required int sensorOrientationDegrees,
  required bool displayIsPortrait,
}) {
  final computed = normalizeRotationDegrees(computedRotationDegrees);
  final bufferIsLandscape = sourceWidth > sourceHeight;

  if (displayIsPortrait &&
      bufferIsLandscape &&
      !rotationSwapsAxes(computed)) {
    final sensor = normalizeRotationDegrees(sensorOrientationDegrees);
    if (rotationSwapsAxes(sensor)) {
      return sensor;
    }
  }

  return computed;
}

/// Pencere boyutu + eklenti yönü + sensör metadata'sından uygulanacak açı.
FrameRotationDecision decideFrameRotation({
  required int sensorOrientationDegrees,
  required CameraLensDirection lensDirection,
  required DeviceOrientation pluginOrientation,
  required Size windowSize,
  required int sourceWidth,
  required int sourceHeight,
}) {
  final display = resolveDisplayOrientation(
    windowSize: windowSize,
    pluginOrientation: pluginOrientation,
  );
  final computed = computeFrameRotationDegrees(
    sensorOrientationDegrees: sensorOrientationDegrees,
    lensDirection: lensDirection,
    deviceOrientation: display,
  );
  final applied = reconcileBufferRotation(
    computedRotationDegrees: computed,
    sourceWidth: sourceWidth,
    sourceHeight: sourceHeight,
    sensorOrientationDegrees: sensorOrientationDegrees,
    displayIsPortrait: isPortraitSize(windowSize),
  );

  return FrameRotationDecision(
    pluginOrientation: pluginOrientation,
    displayOrientation: display,
    windowSize: windowSize,
    sensorOrientation: normalizeRotationDegrees(sensorOrientationDegrees),
    lensDirection: lensDirection,
    computedDegrees: computed,
    appliedDegrees: applied,
  );
}

class FrameRotationDecision {
  final DeviceOrientation pluginOrientation;
  final DeviceOrientation displayOrientation;
  final Size windowSize;
  final int sensorOrientation;
  final CameraLensDirection lensDirection;
  final int computedDegrees;
  final int appliedDegrees;

  const FrameRotationDecision({
    required this.pluginOrientation,
    required this.displayOrientation,
    required this.windowSize,
    required this.sensorOrientation,
    required this.lensDirection,
    required this.computedDegrees,
    required this.appliedDegrees,
  });
}
