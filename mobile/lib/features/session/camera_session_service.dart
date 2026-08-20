import '../../core/network/api_client.dart';

class CameraSessionService {
  final ApiClient _apiClient;

  CameraSessionService({
    ApiClient? apiClient,
  }) : _apiClient = apiClient ?? ApiClient();

  Future<bool> openSession({
    required String cameraId,
    required String sessionId,
    required String sessionToken,
  }) async {
    final response = await _apiClient.postJson(
      path: '/api/v1/sessions/open',
      body: {
        'cameraId': cameraId,
        'sessionId': sessionId,
        'sessionToken': sessionToken,
      },
    );

        return response.statusCode == 200 ||
        response.statusCode == 201;
  }

  Future<bool> sendHeartbeat({
    required String cameraId,
    required String sessionId,
  }) async {
    final response = await _apiClient.postHeartbeat(
      path: '/api/v1/sessions/$sessionId/heartbeat',
      cameraId: cameraId,
    );

    return response.statusCode == 200;
  }
    Future<bool> closeSession({
    required String cameraId,
    required String sessionId,
  }) async {
    final response = await _apiClient.postClose(
      path: '/api/v1/sessions/$sessionId/close',
      cameraId: cameraId,
    );

    return response.statusCode == 204;
  }
}