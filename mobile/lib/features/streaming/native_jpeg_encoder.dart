import 'package:flutter/services.dart';

/// Saf Dart JPEG kodlama gerçek cihazda kare başına ~2 sn sürüyor ve 15 FPS'i
/// karşılamıyor. Kodlama platformun native encoder'ına devredildi; Android'de
/// YuvImage.compressToJpeg arka plan thread havuzunda çalışır.
class NativeJpegEncoder {
  static const MethodChannel _channel = MethodChannel(
    'camera_stream_app/jpeg_encoder',
  );

  Future<Uint8List?> encodeYuv420({
    required Uint8List yBytes,
    required Uint8List uBytes,
    required Uint8List vBytes,
    required int yRowStride,
    required int uRowStride,
    required int vRowStride,
    required int uPixelStride,
    required int vPixelStride,
    required int sourceWidth,
    required int sourceHeight,
    required int step,
    required int rotationDegrees,
    required int quality,
    int sensorOrientation = 0,
    bool isBackCamera = true,
  }) async {
    try {
      final result = await _channel.invokeMethod<Uint8List>(
        'encodeYuv420',
        <String, dynamic>{
          'y': yBytes,
          'u': uBytes,
          'v': vBytes,
          'yRowStride': yRowStride,
          'uRowStride': uRowStride,
          'vRowStride': vRowStride,
          'uPixelStride': uPixelStride,
          'vPixelStride': vPixelStride,
          'width': sourceWidth,
          'height': sourceHeight,
          'step': step,
          'rotationDegrees': rotationDegrees,
          'sensorOrientation': sensorOrientation,
          'isBackCamera': isBackCamera,
          'quality': quality,
        },
      );

      return result;
    } on PlatformException {
      return null;
    } on MissingPluginException {
      return null;
    }
  }
}
