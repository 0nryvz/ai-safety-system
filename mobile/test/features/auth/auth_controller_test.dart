import 'dart:convert';

import 'package:camera_stream_app/core/error/api_failure.dart';
import 'package:camera_stream_app/core/network/backend_client.dart';
import 'package:camera_stream_app/features/auth/auth_controller.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

const _meBody = {
  'id': '11111111-0000-4000-8000-000000000001',
  'email': 'admin@isgvision.local',
  'fullName': 'Admin',
  'active': true,
  'roles': ['ADMIN'],
  'departmentIds': <String>[],
};

AuthController _controller(http.Client client) {
  return AuthController(
    BackendClient(baseUrl: 'http://backend', client: client),
  );
}

String _authBody({String accessToken = 'jwt', String refreshToken = 'rt'}) {
  return jsonEncode({
    'accessToken': accessToken,
    'refreshToken': refreshToken,
    'tokenType': 'Bearer',
  });
}

void main() {
  group('signIn', () {
    test('login + me ile session kurar', () async {
      final controller = _controller(
        MockClient((request) async {
          if (request.url.path == '/api/v1/auth/login') {
            return http.Response(_authBody(), 200);
          }
          if (request.url.path == '/api/v1/users/me') {
            expect(request.headers['Authorization'], 'Bearer jwt');
            return http.Response(jsonEncode(_meBody), 200);
          }
          return http.Response('', 404);
        }),
      );

      await controller.signIn(
        email: 'admin@isgvision.local',
        password: '123456',
      );

      expect(controller.state.authenticated, isTrue);
      expect(controller.state.accessToken, 'jwt');
      expect(controller.state.refreshToken, 'rt');
      expect(controller.state.currentUser?.email, 'admin@isgvision.local');
      expect(controller.state.isAdmin, isTrue);
      expect(controller.state.canManageUsers, isTrue);
    });

    test('geçersiz kimlik bilgisi session kurmaz', () async {
      final controller = _controller(
        MockClient((_) async => http.Response('', 401)),
      );

      await expectLater(
        () => controller.signIn(email: 'a@b.c', password: 'wrong'),
        throwsA(
          isA<ApiFailure>().having(
            (e) => e.kind,
            'kind',
            ApiFailureKind.unauthenticated,
          ),
        ),
      );
      expect(controller.state.authenticated, isFalse);
    });

    test('me 401 ise session temizlenir', () async {
      final controller = _controller(
        MockClient((request) async {
          if (request.url.path == '/api/v1/auth/login') {
            return http.Response(_authBody(), 200);
          }
          return http.Response('', 401);
        }),
      );

      await expectLater(
        () => controller.signIn(email: 'a@b.c', password: 'x'),
        throwsA(isA<ApiFailure>()),
      );
      expect(controller.state.authenticated, isFalse);
      expect(controller.state.accessToken, isNull);
    });
  });

  group('401 pipeline', () {
    test('süresi dolmuş access token refresh ile yenilenir', () async {
      var refreshCalls = 0;

      final controller = _controller(
        MockClient((request) async {
          switch (request.url.path) {
            case '/api/v1/auth/login':
              return http.Response(_authBody(accessToken: 'expired'), 200);
            case '/api/v1/auth/refresh':
              refreshCalls++;
              return http.Response(_authBody(accessToken: 'fresh'), 200);
            case '/api/v1/users/me':
              return request.headers['Authorization'] == 'Bearer expired'
                  ? http.Response('', 401)
                  : http.Response(jsonEncode(_meBody), 200);
            default:
              return http.Response('', 404);
          }
        }),
      );

      await controller.signIn(email: 'a@b.c', password: 'x');

      expect(refreshCalls, 1);
      expect(controller.state.accessToken, 'fresh');
      expect(controller.state.authenticated, isTrue);
    });

    test('403 oturumu korur', () async {
      final controller = _controller(
        MockClient((request) async {
          switch (request.url.path) {
            case '/api/v1/auth/login':
              return http.Response(_authBody(), 200);
            case '/api/v1/users/me':
              return http.Response(jsonEncode(_meBody), 200);
            default:
              return http.Response('', 403);
          }
        }),
      );

      await controller.signIn(email: 'a@b.c', password: 'x');

      await expectLater(
        () => controller.api.getJsonList('/api/v1/users'),
        throwsA(
          isA<ApiFailure>().having(
            (e) => e.kind,
            'kind',
            ApiFailureKind.forbidden,
          ),
        ),
      );

      expect(controller.state.authenticated, isTrue);
      expect(controller.state.accessToken, 'jwt');
    });
  });

  group('signOut', () {
    test('backend logout çağrılır ve session temizlenir', () async {
      final logoutBodies = <String>[];

      final controller = _controller(
        MockClient((request) async {
          switch (request.url.path) {
            case '/api/v1/auth/login':
              return http.Response(_authBody(), 200);
            case '/api/v1/users/me':
              return http.Response(jsonEncode(_meBody), 200);
            case '/api/v1/auth/logout':
              logoutBodies.add(request.body);
              return http.Response('', 200);
            default:
              return http.Response('', 404);
          }
        }),
      );

      await controller.signIn(email: 'a@b.c', password: 'x');
      await controller.signOut();

      expect(logoutBodies, [jsonEncode({'refreshToken': 'rt'})]);
      expect(controller.state.authenticated, isFalse);
      expect(controller.state.refreshToken, isNull);
    });

    test('logout ağ hatası olsa da local session temizlenir', () async {
      final controller = _controller(
        MockClient((request) async {
          switch (request.url.path) {
            case '/api/v1/auth/login':
              return http.Response(_authBody(), 200);
            case '/api/v1/users/me':
              return http.Response(jsonEncode(_meBody), 200);
            default:
              throw Exception('no route');
          }
        }),
      );

      await controller.signIn(email: 'a@b.c', password: 'x');
      await controller.signOut();

      expect(controller.state.authenticated, isFalse);
    });
  });
}
