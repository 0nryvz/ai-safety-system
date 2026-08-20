import 'package:camera/camera.dart';
import 'package:image/image.dart' as img;

import '../../core/network/api_client.dart';

class CameraFrameService {
  final ApiClient _apiClient;

  CameraFrameService({
    ApiClient? apiClient,
  }) : _apiClient = apiClient ?? ApiClient();

  Future<bool> uploadFrame({
    required String cameraId,
    required String sessionId,
    required DateTime frameTimestamp,
    required CameraImage image,
  }) async {
    final jpegBytes = _convertToJpeg(image);

    if (jpegBytes == null) {
      return false;
    }

    final response = await _apiClient.postJpeg(
      path: '/api/v1/sessions/$sessionId/frames',
      cameraId: cameraId,
      frameTimestamp: frameTimestamp,
      jpegBytes: jpegBytes,
    );

    return response.statusCode == 202;
  }

  List<int>? _convertToJpeg(CameraImage cameraImage) {
    if (cameraImage.format.group == ImageFormatGroup.bgra8888) {
      return _convertBgra8888ToJpeg(cameraImage);
    }

    if (cameraImage.format.group == ImageFormatGroup.yuv420) {
      return _convertYuv420ToJpeg(cameraImage);
    }

    return null;
  }

  List<int>? _convertBgra8888ToJpeg(CameraImage cameraImage) {
    final image = img.Image(
      width: cameraImage.width,
      height: cameraImage.height,
    );

    final bytes = cameraImage.planes.first.bytes;
    final bytesPerRow = cameraImage.planes.first.bytesPerRow;

    for (var y = 0; y < cameraImage.height; y++) {
      for (var x = 0; x < cameraImage.width; x++) {
        final index = y * bytesPerRow + x * 4;

        final b = bytes[index];
        final g = bytes[index + 1];
        final r = bytes[index + 2];
        final a = bytes[index + 3];

        image.setPixelRgba(x, y, r, g, b, a);
      }
    }

    return img.encodeJpg(image, quality: 80);
  }

  List<int>? _convertYuv420ToJpeg(CameraImage cameraImage) {
    final width = cameraImage.width;
    final height = cameraImage.height;

    final yPlane = cameraImage.planes[0];
    final uPlane = cameraImage.planes[1];
    final vPlane = cameraImage.planes[2];

    final image = img.Image(
      width: width,
      height: height,
    );

    for (var y = 0; y < height; y++) {
      for (var x = 0; x < width; x++) {
        final yIndex =
            y * yPlane.bytesPerRow + x;

        final uvX = x ~/ 2;
        final uvY = y ~/ 2;

        final uIndex =
            uvY * uPlane.bytesPerRow +
            uvX * (uPlane.bytesPerPixel ?? 1);

        final vIndex =
            uvY * vPlane.bytesPerRow +
            uvX * (vPlane.bytesPerPixel ?? 1);

        final yValue = yPlane.bytes[yIndex];
        final uValue = uPlane.bytes[uIndex];
        final vValue = vPlane.bytes[vIndex];

        final r = yValue + 1.402 * (vValue - 128);
        final g = yValue -
            0.344136 * (uValue - 128) -
            0.714136 * (vValue - 128);
        final b = yValue + 1.772 * (uValue - 128);

        image.setPixelRgb(
          x,
          y,
          r.round().clamp(0, 255),
          g.round().clamp(0, 255),
          b.round().clamp(0, 255),
        );
      }
    }

    return img.encodeJpg(image, quality: 80);
  }
}