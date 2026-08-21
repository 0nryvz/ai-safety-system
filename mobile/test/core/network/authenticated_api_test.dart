import 'dart:convert';

import 'package:camera_stream_app/core/error/api_failure.dart';
import 'package:camera_stream_app/core/models/auth_tokens.dart';
import 'package:camera_stream_app/core/network/auth_session_store.dart';
import 'package:camera_stream_app/core/network/authenticated_api.dart';
import 'package:camera_stream_app/core/network/backend_client.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

class _FakeStore implements AuthSessionStore {
  @override
  String? accessToken;

  @override
  String? refreshToken;

  int sessionInvalidCount = 0;
  int appliedTokenCount = 0;

  _FakeStore({this.accessToken, this.refreshToken});

  @override
  void applyRefreshedTokens(AuthTokens tokens) {
    appliedTokenCount++;
    accessToken = tokens.accessToken;
    refreshToken = tokens.refreshToken;
  }

  @override
  void onSessionInvalid() {
    sessionInvalidCount++;
    accessToken = null;
    refreshToken = null;
  }
}

AuthenticatedApi _api(_FakeStore store, http.Client client) {
  return AuthenticatedApi(
    BackendClient(baseUrl: 'http://backend', client: client),
    store,
  );
}

String _refreshBody(String accessToken) => jsonEncode({
      'accessToken': accessToken,
      'refreshToken': 'rt',
      'tokenType': 'Bearer',
    });

void main() {
  test('Bearer header session tokenından enjekte edilir', () async {
    final store = _FakeStore(accessToken: 'jwt-1', refreshToken: 'rt');
    late http.Request captured;

    final api = _api(
      store,
      MockClient((request) async {
        captured = request;
        return http.Response(jsonEncode({'ok': true}), 200);
      }),
    );

    await api.getJson('/api/v1/dashboard/summary');

    expect(captured.headers['Authorization'], 'Bearer jwt-1');
  });

  test('401 refresh başarılıysa istek yeni token ile tekrar edilir', () async {
    final store = _FakeStore(accessToken: 'expired', refreshToken: 'rt');
    final authHeaders = <String>[];
    var refreshCalls = 0;

    final api = _api(
      store,
      MockClient((request) async {
        if (request.url.path == '/api/v1/auth/refresh') {
          refreshCalls++;
          return http.Response(_refreshBody('jwt-2'), 200);
        }

        final header = request.headers['Authorization']!;
        authHeaders.add(header);
        if (header == 'Bearer expired') {
          return http.Response('', 401);
        }
        return http.Response(jsonEncode({'ok': true}), 200);
      }),
    );

    final body = await api.getJson('/api/v1/violations/1');

    expect(body['ok'], isTrue);
    expect(refreshCalls, 1);
    expect(authHeaders, ['Bearer expired', 'Bearer jwt-2']);
    expect(store.accessToken, 'jwt-2');
    expect(store.sessionInvalidCount, 0);
  });

  test('401 refresh başarısızsa oturum geçersiz kılınır', () async {
    final store = _FakeStore(accessToken: 'expired', refreshToken: 'rt');

    final api = _api(
      store,
      MockClient((request) async {
        if (request.url.path == '/api/v1/auth/refresh') {
          return http.Response('', 401);
        }
        return http.Response('', 401);
      }),
    );

    await expectLater(
      () => api.getJson('/api/v1/violations'),
      throwsA(
        isA<ApiFailure>().having(
          (e) => e.kind,
          'kind',
          ApiFailureKind.unauthenticated,
        ),
      ),
    );
    expect(store.sessionInvalidCount, 1);
  });

  test('refresh sonrası ikinci 401 sonsuz retry üretmez', () async {
    final store = _FakeStore(accessToken: 'expired', refreshToken: 'rt');
    var refreshCalls = 0;
    var dataCalls = 0;

    final api = _api(
      store,
      MockClient((request) async {
        if (request.url.path == '/api/v1/auth/refresh') {
          refreshCalls++;
          return http.Response(_refreshBody('jwt-2'), 200);
        }
        dataCalls++;
        return http.Response('', 401);
      }),
    );

    await expectLater(
      () => api.getJson('/api/v1/violations'),
      throwsA(isA<ApiFailure>()),
    );

    expect(refreshCalls, 1);
    expect(dataCalls, 2);
    expect(store.sessionInvalidCount, 1);
  });

  test('eşzamanlı 401 tek refresh çağrısı üretir', () async {
    final store = _FakeStore(accessToken: 'expired', refreshToken: 'rt');
    var refreshCalls = 0;

    final api = _api(
      store,
      MockClient((request) async {
        if (request.url.path == '/api/v1/auth/refresh') {
          refreshCalls++;
          await Future<void>.delayed(const Duration(milliseconds: 20));
          return http.Response(_refreshBody('jwt-2'), 200);
        }

        if (request.headers['Authorization'] == 'Bearer expired') {
          return http.Response('', 401);
        }
        return http.Response(jsonEncode({'ok': true}), 200);
      }),
    );

    final results = await Future.wait([
      api.getJson('/api/v1/dashboard/summary'),
      api.getJson('/api/v1/violations'),
      api.getJson('/api/v1/cameras/1'),
    ]);

    expect(results, hasLength(3));
    expect(refreshCalls, 1);
    expect(store.appliedTokenCount, 1);
  });

  test('403 oturumu korur ve forbidden olarak yüzeye çıkar', () async {
    final store = _FakeStore(accessToken: 'jwt-1', refreshToken: 'rt');
    var refreshCalls = 0;

    final api = _api(
      store,
      MockClient((request) async {
        if (request.url.path == '/api/v1/auth/refresh') {
          refreshCalls++;
          return http.Response(_refreshBody('jwt-2'), 200);
        }
        return http.Response('', 403);
      }),
    );

    await expectLater(
      () => api.send(method: 'POST', path: '/api/v1/cameras', body: {'a': 1}),
      throwsA(
        isA<ApiFailure>().having(
          (e) => e.kind,
          'kind',
          ApiFailureKind.forbidden,
        ),
      ),
    );

    expect(refreshCalls, 0);
    expect(store.accessToken, 'jwt-1');
    expect(store.sessionInvalidCount, 0);
  });

  test('token yoksa ve refresh token da yoksa oturum geçersizdir', () async {
    final store = _FakeStore();

    final api = _api(
      store,
      MockClient((_) async => http.Response(jsonEncode({'ok': true}), 200)),
    );

    await expectLater(
      () => api.getJson('/api/v1/users/me'),
      throwsA(
        isA<ApiFailure>().having(
          (e) => e.kind,
          'kind',
          ApiFailureKind.unauthenticated,
        ),
      ),
    );
    expect(store.sessionInvalidCount, 1);
  });

  test('currentUser UserResponse alanlarını parse eder', () async {
    final store = _FakeStore(accessToken: 'jwt-1', refreshToken: 'rt');

    final api = _api(
      store,
      MockClient(
        (request) async => http.Response(
          jsonEncode({
            'id': '11111111-0000-4000-8000-000000000001',
            'email': 'ohs@isgvision.local',
            'fullName': 'Uzman',
            'active': true,
            'roles': ['OHS_SPECIALIST'],
            'departmentIds': ['22222222-0000-4000-8000-000000000001'],
          }),
          200,
        ),
      ),
    );

    final user = await api.currentUser();

    expect(user.email, 'ohs@isgvision.local');
    expect(user.roles, contains('OHS_SPECIALIST'));
    expect(user.isAdmin, isFalse);
  });
}
