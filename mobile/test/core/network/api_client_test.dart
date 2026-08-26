import 'package:camera_stream_app/core/network/api_client.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  group('postJpeg frame metadata', () {
    test('cameraId ve UTC timestamp header olarak gider', () async {
      late http.Request captured;

      final client = ApiClient(
        client: MockClient((request) async {
          captured = request;
          return http.Response('', 202);
        }),
      );

      // Yerel saat verilse bile UTC'ye çevrilmeli.
      final localTime = DateTime.parse('2026-08-21T12:00:00+03:00');

      await client.postJpeg(
        path: '/api/v1/sessions/s-1/frames',
        cameraId: 'cam-uuid',
        frameTimestamp: localTime,
        jpegBytes: const [0xFF, 0xD8, 0xFF, 0xD9],
      );

      expect(captured.headers['X-Camera-Id'], 'cam-uuid');
      expect(captured.headers['Content-Type'], 'image/jpeg');

      final timestamp = captured.headers['X-Frame-Timestamp']!;

      // Gateway timezone taşımayan damgayı 422 ile reddediyor.
      expect(timestamp, endsWith('Z'));
      expect(DateTime.parse(timestamp).isUtc, isTrue);
      expect(DateTime.parse(timestamp), localTime.toUtc());
    });

    test('gövde ham JPEG byte dizisidir', () async {
      late http.Request captured;

      final client = ApiClient(
        client: MockClient((request) async {
          captured = request;
          return http.Response('', 202);
        }),
      );

      await client.postJpeg(
        path: '/api/v1/sessions/s-1/frames',
        cameraId: 'cam',
        frameTimestamp: DateTime.now(),
        jpegBytes: const [0xFF, 0xD8, 0x00, 0xFF, 0xD9],
      );

      expect(captured.bodyBytes, [0xFF, 0xD8, 0x00, 0xFF, 0xD9]);
    });

    test('keep-alive ile bağlantı yeniden kullanılır', () async {
      late http.Request captured;

      final client = ApiClient(
        client: MockClient((request) async {
          captured = request;
          return http.Response('', 202);
        }),
      );

      await client.postJpeg(
        path: '/api/v1/sessions/s/frames',
        cameraId: 'cam',
        frameTimestamp: DateTime.now(),
        jpegBytes: const [0xFF, 0xD8, 0xFF, 0xD9],
      );

      expect(captured.headers['Connection'], 'keep-alive');
    });
  });
}
