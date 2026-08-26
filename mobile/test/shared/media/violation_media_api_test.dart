import 'dart:convert';

import 'package:camera_stream_app/core/error/api_failure.dart';
import 'package:camera_stream_app/core/models/auth_tokens.dart';
import 'package:camera_stream_app/core/network/auth_session_store.dart';
import 'package:camera_stream_app/core/network/authenticated_api.dart';
import 'package:camera_stream_app/core/network/backend_client.dart';
import 'package:camera_stream_app/shared/media/violation_media_api.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

class _FakeStore implements AuthSessionStore {
  @override
  String? accessToken;

  @override
  String? refreshToken;

  int sessionInvalidCount = 0;

  _FakeStore({this.accessToken, this.refreshToken});

  @override
  void applyRefreshedTokens(AuthTokens tokens) {
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

void main() {
  late _FakeStore store;
  late List<http.Request> requests;

  ViolationMediaApi apiWith(http.Response Function(http.Request) respond) {
    requests = [];
    store = _FakeStore(accessToken: 'jwt', refreshToken: 'rt');
    final client = MockClient((request) async {
      requests.add(request);
      return respond(request);
    });
    return ViolationMediaApi.fromAuthenticated(
      AuthenticatedApi(
        BackendClient(baseUrl: 'http://backend', client: client),
        store,
      ),
    );
  }

  Map<String, dynamic> urlBody() => {
        'url': 'https://minio.example/presigned-clip',
        'expiresAt': '2026-08-18T18:05:00Z',
      };

  test('READY clip-url is fetched by violationId with Bearer header', () async {
    final api = apiWith((request) {
      return http.Response(jsonEncode(urlBody()), 200);
    });

    final media = await api.fetchClipUrl('v-1');

    expect(requests, hasLength(1));
    expect(requests.single.method, 'GET');
    expect(
      requests.single.url.path,
      '/api/v1/violations/v-1/clip-url',
    );
    expect(requests.single.headers['Authorization'], 'Bearer jwt');
    expect(requests.single.url.query, isEmpty);
    expect(media.url, 'https://minio.example/presigned-clip');
    expect(media.expiresAt.toUtc(), DateTime.parse('2026-08-18T18:05:00Z'));
  });

  test('request path and body never contain objectKey, coverImageKey or '
      'playbackUrl', () async {
    final api = apiWith((request) {
      return http.Response(
        jsonEncode({
          ...urlBody(),
          'objectKey': 'violations/secret.mp4',
          'coverImageKey': 'violations/cover.jpg',
          'playbackUrl': 'https://bucket/direct',
        }),
        200,
      );
    });

    await api.fetchClipUrl('v-9');
    await api.fetchCoverUrl('v-9');

    for (final request in requests) {
      expect(request.url.toString(), isNot(contains('objectKey')));
      expect(request.url.toString(), isNot(contains('coverImageKey')));
      expect(request.url.toString(), isNot(contains('playbackUrl')));
      expect(request.body, isEmpty);
    }
    expect(
      requests.map((r) => r.url.path).toList(),
      [
        '/api/v1/violations/v-9/clip-url',
        '/api/v1/violations/v-9/cover-url',
      ],
    );
  });

  test('409 clip-url is conflict, not a generic server error', () async {
    final api = apiWith((_) => http.Response('{"code":"RECORDING_NOT_READY"}', 409));

    try {
      await api.fetchClipUrl('v-1');
      fail('expected ApiFailure');
    } on ApiFailure catch (failure) {
      expect(failure.statusCode, 409);
      expect(failure.kind, ApiFailureKind.conflict);
      expect(failure.kind, isNot(ApiFailureKind.server));
    }
    expect(store.sessionInvalidCount, 0);
  });

  test('403 does not clear the session', () async {
    final api = apiWith((_) => http.Response('{"code":"FORBIDDEN"}', 403));

    try {
      await api.fetchClipUrl('v-1');
      fail('expected ApiFailure');
    } on ApiFailure catch (failure) {
      expect(failure.kind, ApiFailureKind.forbidden);
    }
    expect(store.sessionInvalidCount, 0);
    expect(store.accessToken, 'jwt');
  });

  test('404 keeps statusCode so media layer can map notFound', () async {
    final api = apiWith((_) => http.Response('{"code":"RECORDING_NOT_FOUND"}', 404));

    try {
      await api.fetchClipUrl('v-1');
      fail('expected ApiFailure');
    } on ApiFailure catch (failure) {
      expect(failure.statusCode, 404);
      expect(failure.kind, ApiFailureKind.unknown);
    }
  });

  test('cover 200 parses the same MediaUrl contract', () async {
    final api = apiWith((request) {
      expect(request.url.path, '/api/v1/violations/v-2/cover-url');
      return http.Response(
        jsonEncode({
          'url': 'https://minio.example/presigned-cover',
          'expiresAt': '2026-08-18T18:05:00Z',
        }),
        200,
      );
    });

    final media = await api.fetchCoverUrl('v-2');
    expect(media.url, 'https://minio.example/presigned-cover');
  });

  test('cover 409 is conflict and does not throw a generic server failure',
      () async {
    final api = apiWith(
      (_) => http.Response('{"code":"COVER_IMAGE_NOT_READY"}', 409),
    );

    try {
      await api.fetchCoverUrl('v-2');
      fail('expected ApiFailure');
    } on ApiFailure catch (failure) {
      expect(failure.statusCode, 409);
      expect(failure.kind, ApiFailureKind.conflict);
      expect(failure.kind, isNot(ApiFailureKind.server));
    }
    expect(store.sessionInvalidCount, 0);
  });

  test('expired clip is refetched with the same violationId', () async {
    var calls = 0;
    final api = apiWith((request) {
      calls++;
      return http.Response(
        jsonEncode({
          'url': 'https://minio.example/clip-$calls',
          'expiresAt': '2026-08-18T18:05:00Z',
        }),
        200,
      );
    });

    final first = await api.fetchClipUrl('v-1');
    final second = await api.fetchClipUrl('v-1');

    expect(first.url, 'https://minio.example/clip-1');
    expect(second.url, 'https://minio.example/clip-2');
    expect(
      requests.map((r) => r.url.path).toSet(),
      {'/api/v1/violations/v-1/clip-url'},
    );
  });
}
