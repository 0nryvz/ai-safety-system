import 'dart:convert';

import 'package:camera_stream_app/core/error/api_failure.dart';
import 'package:camera_stream_app/core/network/backend_client.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  group('login', () {
    test('accessToken döner', () async {
      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient(
          (_) async => http.Response(
            jsonEncode({
              'accessToken': 'jwt-token',
              'refreshToken': 'refresh',
              'tokenType': 'Bearer',
            }),
            200,
          ),
        ),
      );

      expect(
        await client.login(email: 'a@b.c', password: 'x'),
        'jwt-token',
      );
    });

    test('kimlik bilgileri gövdede gönderilir', () async {
      late http.Request captured;

      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient((request) async {
          captured = request;
          return http.Response(
            jsonEncode({
              'accessToken': 't',
              'refreshToken': 'r',
              'tokenType': 'Bearer',
            }),
            200,
          );
        }),
      );

      await client.login(email: 'admin@isgvision.local', password: 'secret');

      expect(captured.url.path, '/api/v1/auth/login');
      expect(jsonDecode(captured.body), {
        'email': 'admin@isgvision.local',
        'password': 'secret',
      });
    });

    test('401 anlaşılır mesaja çevrilir', () async {
      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient((_) async => http.Response('', 401)),
      );

      expect(
        () => client.login(email: 'a@b.c', password: 'wrong'),
        throwsA(
          isA<BackendAuthException>().having(
            (e) => e.message,
            'message',
            contains('hatalı'),
          ),
        ),
      );
    });

    test('ağ hatası anlaşılır mesaja çevrilir', () async {
      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient((_) => throw Exception('no route')),
      );

      expect(
        () => client.login(email: 'a@b.c', password: 'x'),
        throwsA(
          isA<BackendAuthException>().having(
            (e) => e.message,
            'message',
            contains('ulaşılamıyor'),
          ),
        ),
      );
    });
  });

  group('loginTokens', () {
    test('AuthResponse alanlarını döner', () async {
      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient(
          (_) async => http.Response(
            jsonEncode({
              'accessToken': 'jwt',
              'refreshToken': 'rt',
              'tokenType': 'Bearer',
            }),
            200,
          ),
        ),
      );

      final tokens = await client.loginTokens(email: 'a@b.c', password: 'x');

      expect(tokens.accessToken, 'jwt');
      expect(tokens.refreshToken, 'rt');
      expect(tokens.tokenType, 'Bearer');
    });
  });

  group('refreshTokens', () {
    test('refreshToken gövdede gider ve AuthResponse döner', () async {
      late http.Request captured;

      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient((request) async {
          captured = request;
          return http.Response(
            jsonEncode({
              'accessToken': 'new-jwt',
              'refreshToken': 'rt',
              'tokenType': 'Bearer',
            }),
            200,
          );
        }),
      );

      final tokens = await client.refreshTokens('rt');

      expect(captured.url.path, '/api/v1/auth/refresh');
      expect(jsonDecode(captured.body), {'refreshToken': 'rt'});
      expect(tokens.accessToken, 'new-jwt');
    });

    test('401 unauthenticated olarak yüzeye çıkar', () async {
      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient((_) async => http.Response('', 401)),
      );

      expect(
        () => client.refreshTokens('revoked'),
        throwsA(
          isA<ApiFailure>().having(
            (e) => e.kind,
            'kind',
            ApiFailureKind.unauthenticated,
          ),
        ),
      );
    });
  });

  group('logout', () {
    test('refreshToken gövdede gider', () async {
      late http.Request captured;

      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient((request) async {
          captured = request;
          return http.Response('', 200);
        }),
      );

      await client.logout('rt');

      expect(captured.url.path, '/api/v1/auth/logout');
      expect(jsonDecode(captured.body), {'refreshToken': 'rt'});
    });

    test('ağ hatası ApiFailure.network olur', () async {
      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient((_) => throw Exception('no route')),
      );

      expect(
        () => client.logout('rt'),
        throwsA(
          isA<ApiFailure>().having(
            (e) => e.kind,
            'kind',
            ApiFailureKind.network,
          ),
        ),
      );
    });
  });

  group('fetchCurrentUser', () {
    test('UserResponse alanlarını okur', () async {
      late http.Request captured;

      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient((request) async {
          captured = request;
          return http.Response(
            jsonEncode({
              'id': '11111111-0000-4000-8000-000000000001',
              'email': 'admin@isgvision.local',
              'fullName': 'Admin',
              'active': true,
              'departmentId': null,
              'departmentName': null,
              'roles': ['ADMIN'],
              'departmentIds': ['22222222-0000-4000-8000-000000000001'],
              'createdAt': '2026-08-21T10:00:00Z',
            }),
            200,
          );
        }),
      );

      final user = await client.fetchCurrentUser('jwt-token');

      expect(captured.url.path, '/api/v1/users/me');
      expect(captured.headers['Authorization'], 'Bearer jwt-token');
      expect(user.email, 'admin@isgvision.local');
      expect(user.roles, contains('ADMIN'));
      expect(user.departmentIds, hasLength(1));
      expect(user.isAdmin, isTrue);
    });

    test('401 ApiFailure.unauthenticated olur', () async {
      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient((_) async => http.Response('', 401)),
      );

      expect(
        () => client.fetchCurrentUser('expired'),
        throwsA(
          isA<ApiFailure>().having(
            (e) => e.kind,
            'kind',
            ApiFailureKind.unauthenticated,
          ),
        ),
      );
    });
  });

  group('sendAuthorized', () {
    test('403 forbidden eşlenir', () async {
      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient((_) async => http.Response('', 403)),
      );

      expect(
        () => client.sendAuthorized(
          method: 'GET',
          path: '/api/v1/users',
          accessToken: 't',
        ),
        throwsA(
          isA<ApiFailure>().having(
            (e) => e.kind,
            'kind',
            ApiFailureKind.forbidden,
          ),
        ),
      );
    });

    test('409 conflict eşlenir', () async {
      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient((_) async => http.Response('', 409)),
      );

      expect(
        () => client.sendAuthorized(
          method: 'PATCH',
          path: '/api/v1/violations/x/review',
          accessToken: 't',
          body: {'reviewStatus': 'REVIEWED', 'version': 0},
        ),
        throwsA(
          isA<ApiFailure>().having(
            (e) => e.kind,
            'kind',
            ApiFailureKind.conflict,
          ),
        ),
      );
    });
  });

  group('fetchCameras', () {
    test('Bearer token header olarak gider', () async {
      late http.Request captured;

      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient((request) async {
          captured = request;
          return http.Response('[]', 200);
        }),
      );

      await client.fetchCameras('jwt-token');

      expect(captured.url.path, '/api/v1/cameras');
      expect(captured.headers['Authorization'], 'Bearer jwt-token');
    });

    test('kamera listesi modele dönüşür', () async {
      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient(
          (_) async => http.Response(
            jsonEncode([
              {'id': 'uuid-1', 'name': 'Kamera 1', 'active': true},
              {'id': 'uuid-2', 'name': 'Kamera 2', 'active': false},
            ]),
            200,
          ),
        ),
      );

      final cameras = await client.fetchCameras('token');

      expect(cameras, hasLength(2));
      expect(cameras.first.id, 'uuid-1');
      expect(cameras.first.isSelectable, isTrue);
      expect(cameras.last.isSelectable, isFalse);
    });

    test('süresi dolmuş token yeniden giriş ister', () async {
      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient((_) async => http.Response('', 401)),
      );

      expect(
        () => client.fetchCameras('expired'),
        throwsA(
          isA<BackendAuthException>().having(
            (e) => e.message,
            'message',
            contains('Tekrar giriş'),
          ),
        ),
      );
    });
  });
}
