import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../core/error/gateway_failure.dart';
import '../../core/network/api_client.dart';

/// Gateway oturum yaşam döngüsü: open / heartbeat / close.
///
/// Sözleşme: gateway/app/api/routes/sessions.py
/// Gövde alanları camelCase (`cameraId`, `sessionId`, `sessionToken`).
class CameraSessionService {
  final ApiClient _apiClient;

  CameraSessionService({
    ApiClient? apiClient,
  }) : _apiClient = apiClient ?? ApiClient();

  Future<GatewayResult<void>> openSession({
    required String cameraId,
    required String sessionId,
    required String sessionToken,
  }) async {
    return _run(() async {
      final response = await _apiClient.postJson(
        path: '/api/v1/sessions/open',
        body: {
          'cameraId': cameraId,
          'sessionId': sessionId,
          'sessionToken': sessionToken,
        },
      );

      // 201 yeni oturum, 200 aynı kimliklerle reconnect.
      return _resultFor(response, const {200, 201});
    });
  }

  Future<GatewayResult<void>> sendHeartbeat({
    required String cameraId,
    required String sessionId,
  }) async {
    return _run(() async {
      final response = await _apiClient.postHeartbeat(
        path: '/api/v1/sessions/$sessionId/heartbeat',
        cameraId: cameraId,
      );

      return _resultFor(response, const {200});
    });
  }

  Future<GatewayResult<void>> closeSession({
    required String cameraId,
    required String sessionId,
  }) async {
    return _run(() async {
      final response = await _apiClient.postClose(
        path: '/api/v1/sessions/$sessionId/close',
        cameraId: cameraId,
      );

      return _resultFor(response, const {204});
    });
  }

  Future<GatewayResult<void>> _run(
    Future<GatewayResult<void>> Function() request,
  ) async {
    try {
      return await request();
    } catch (error) {
      return GatewayResult<void>.failed(
        GatewayFailure.network(detail: error.toString()),
      );
    }
  }

  GatewayResult<void> _resultFor(
    http.Response response,
    Set<int> successCodes,
  ) {
    if (successCodes.contains(response.statusCode)) {
      return const GatewayResult<void>.success(null);
    }

    return GatewayResult<void>.failed(
      GatewayFailure.fromStatusCode(
        response.statusCode,
        detail: _detailOf(response),
      ),
    );
  }

  /// Gateway hataları `{"detail": "SESSION_CONFLICT"}` biçiminde döner.
  /// 422'de `detail` bir liste olabilir.
  String? _detailOf(http.Response response) {
    if (response.body.isEmpty) {
      return null;
    }

    try {
      final decoded = jsonDecode(response.body);

      if (decoded is Map<String, dynamic>) {
        final detail = decoded['detail'];
        return detail is String ? detail : detail?.toString();
      }
    } catch (_) {
      // Gövde JSON değilse tanılama detayı yok.
    }

    return null;
  }
}
