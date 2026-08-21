import 'dart:convert';

import 'package:camera_stream_app/core/error/api_failure.dart';
import 'package:camera_stream_app/core/network/backend_client.dart';
import 'package:camera_stream_app/features/auth/auth_controller.dart';
import 'package:camera_stream_app/features/auth/auth_session.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  group('AuthController.signIn', () {
    test('login + me ile session kurar', () async {
      final httpClient = MockClient((request) async {
        if (request.url.path.endsWith('/auth/login')) {
          return http.Response(
            jsonEncode({
              'accessToken': 'jwt',
              'refreshToken': 'rt',
              'tokenType': 'Bearer',
            }),
            200,
          );
        }
        if (request.url.path.endsWith('/users/me')) {
          return http.Response(
            jsonEncode({
              'id': '11111111-0000-4000-8000-000000000001',
              'email': 'admin@isgvision.local',
              'fullName': 'Admin',
              'active': true,
              'roles': ['ADMIN'],
              'departmentIds': <String>[],
            }),
            200,
          );
        }
        return http.Response('', 404);
      });

      final controller = AuthController(
        BackendClient(baseUrl: 'http://backend', client: httpClient),
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
    });

    test('me 401 ise session kurulmaz', () async {
      final httpClient = MockClient((request) async {
        if (request.url.path.endsWith('/auth/login')) {
          return http.Response(
            jsonEncode({
              'accessToken': 'jwt',
              'refreshToken': 'rt',
              'tokenType': 'Bearer',
            }),
            200,
          );
        }
        return http.Response('', 401);
      });

      final controller = AuthController(
        BackendClient(baseUrl: 'http://backend', client: httpClient),
      );

      await expectLater(
        () => controller.signIn(email: 'a@b.c', password: 'x'),
        throwsA(isA<ApiFailure>()),
      );
      expect(controller.state.authenticated, isFalse);
    });

    test('clearSession unauthenticated yapar', () async {
      final httpClient = MockClient((request) async {
        if (request.url.path.endsWith('/auth/login')) {
          return http.Response(
            jsonEncode({
              'accessToken': 'jwt',
              'refreshToken': 'rt',
              'tokenType': 'Bearer',
            }),
            200,
          );
        }
        return http.Response(
          jsonEncode({
            'id': '1',
            'email': 'a@b.c',
            'fullName': 'A',
            'active': true,
            'roles': ['SHIFT_SUPERVISOR'],
            'departmentIds': <String>[],
          }),
          200,
        );
      });

      final controller = AuthController(
        BackendClient(baseUrl: 'http://backend', client: httpClient),
      );
      await controller.signIn(email: 'a@b.c', password: 'x');
      controller.clearSession();

      expect(controller.state, isA<AuthSession>());
      expect(controller.state.authenticated, isFalse);
    });
  });
}
