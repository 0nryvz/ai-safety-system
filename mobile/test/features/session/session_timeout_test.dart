import 'package:camera_stream_app/core/error/gateway_failure.dart';
import 'package:camera_stream_app/core/network/api_client.dart';
import 'package:camera_stream_app/features/session/camera_session_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  test('Gateway timeout ağ hatası olarak yüzeye çıkar', () async {
    final client = ApiClient(
      client: MockClient((request) async {
        await Future<void>.delayed(const Duration(seconds: 30));
        return http.Response('{}', 200);
      }),
    );

    addTearDown(client.close);

    final service = CameraSessionService(apiClient: client);

    final result = await service.openSession(
      cameraId: 'cam',
      sessionId: 'sess',
      sessionToken: 'token',
    );

    expect(result.isSuccess, isFalse);
    expect(result.failure?.kind, GatewayFailureKind.network);
  }, timeout: const Timeout(Duration(seconds: 15)));
}
