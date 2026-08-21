import 'dart:convert';

import 'package:camera_stream_app/core/error/gateway_failure.dart';
import 'package:camera_stream_app/core/network/api_client.dart';
import 'package:camera_stream_app/features/session/camera_session_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

CameraSessionService _serviceReturning(
  http.Response Function(http.Request request) handler, {
  void Function(http.Request request)? capture,
}) {
  final client = MockClient((request) async {
    capture?.call(request);
    return handler(request);
  });

  return CameraSessionService(apiClient: ApiClient(client: client));
}

void main() {
  group('openSession', () {
    test('gövde camelCase alanlarla gönderilir', () async {
      late http.Request captured;

      final service = _serviceReturning(
        (_) => http.Response('', 201),
        capture: (request) => captured = request,
      );

      await service.openSession(
        cameraId: 'cam-uuid',
        sessionId: 'session-uuid',
        sessionToken: 'token',
      );

      final body = jsonDecode(captured.body) as Map<String, dynamic>;

      expect(body, {
        'cameraId': 'cam-uuid',
        'sessionId': 'session-uuid',
        'sessionToken': 'token',
      });

      // snake_case Gateway sözleşmesine aykırı.
      expect(body.containsKey('camera_id'), isFalse);
      expect(captured.url.path, '/api/v1/sessions/open');
    });

    test('201 yeni oturum başarıdır', () async {
      final service = _serviceReturning((_) => http.Response('', 201));

      final result = await service.openSession(
        cameraId: 'c',
        sessionId: 's',
        sessionToken: 't',
      );

      expect(result.isSuccess, isTrue);
    });

    test('200 reconnect de başarıdır', () async {
      final service = _serviceReturning((_) => http.Response('', 200));

      final result = await service.openSession(
        cameraId: 'c',
        sessionId: 's',
        sessionToken: 't',
      );

      expect(result.isSuccess, isTrue);
    });

    test('geçersiz token 401 olarak ayrışır', () async {
      final service = _serviceReturning(
        (_) => http.Response(
          jsonEncode({'detail': 'INVALID_SESSION_TOKEN'}),
          401,
        ),
      );

      final result = await service.openSession(
        cameraId: 'c',
        sessionId: 's',
        sessionToken: 'wrong',
      );

      expect(result.isSuccess, isFalse);
      expect(result.failure!.kind, GatewayFailureKind.unauthorized);
      expect(result.failure!.detail, 'INVALID_SESSION_TOKEN');
      expect(result.failure!.isRetryable, isFalse);
    });

    test('oturum çakışması 409 olarak ayrışır', () async {
      final service = _serviceReturning(
        (_) => http.Response(jsonEncode({'detail': 'SESSION_CONFLICT'}), 409),
      );

      final result = await service.openSession(
        cameraId: 'c',
        sessionId: 's',
        sessionToken: 't',
      );

      expect(result.failure!.kind, GatewayFailureKind.sessionConflict);
    });

    test('pasif kamera 403 olarak ayrışır', () async {
      final service = _serviceReturning(
        (_) => http.Response(jsonEncode({'detail': 'CAMERA_INACTIVE'}), 403),
      );

      final result = await service.openSession(
        cameraId: 'c',
        sessionId: 's',
        sessionToken: 't',
      );

      expect(result.failure!.kind, GatewayFailureKind.cameraInactive);
    });

    test('ağ hatası uygulamayı çökertmez', () async {
      final client = MockClient((_) => throw const SocketFailure());
      final service = CameraSessionService(
        apiClient: ApiClient(client: client),
      );

      final result = await service.openSession(
        cameraId: 'c',
        sessionId: 's',
        sessionToken: 't',
      );

      expect(result.isSuccess, isFalse);
      expect(result.failure!.kind, GatewayFailureKind.network);
      expect(result.failure!.isRetryable, isTrue);
    });
  });

  group('sendHeartbeat', () {
    test('path sessionId içerir ve gövde camelCase olur', () async {
      late http.Request captured;

      final service = _serviceReturning(
        (_) => http.Response('', 200),
        capture: (request) => captured = request,
      );

      await service.sendHeartbeat(
        cameraId: 'cam-uuid',
        sessionId: 'session-uuid',
      );

      expect(
        captured.url.path,
        '/api/v1/sessions/session-uuid/heartbeat',
      );
      expect(
        jsonDecode(captured.body),
        {'cameraId': 'cam-uuid'},
      );
    });

    test('404 oturum bulunamadı olarak ayrışır ve tekrar denenebilir',
        () async {
      final service = _serviceReturning(
        (_) => http.Response(jsonEncode({'detail': 'SESSION_NOT_FOUND'}), 404),
      );

      final result = await service.sendHeartbeat(
        cameraId: 'c',
        sessionId: 's',
      );

      expect(result.failure!.kind, GatewayFailureKind.sessionNotFound);
      expect(result.failure!.isRetryable, isTrue);
    });
  });

  group('closeSession', () {
    test('204 başarıdır', () async {
      final service = _serviceReturning((_) => http.Response('', 204));

      final result = await service.closeSession(
        cameraId: 'c',
        sessionId: 's',
      );

      expect(result.isSuccess, isTrue);
    });

    test('path close ile biter', () async {
      late http.Request captured;

      final service = _serviceReturning(
        (_) => http.Response('', 204),
        capture: (request) => captured = request,
      );

      await service.closeSession(cameraId: 'c', sessionId: 'abc');

      expect(captured.url.path, '/api/v1/sessions/abc/close');
    });
  });
}

class SocketFailure implements Exception {
  const SocketFailure();
}
