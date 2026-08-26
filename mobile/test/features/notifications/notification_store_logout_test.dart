import 'dart:convert';

import 'package:camera_stream_app/core/error/api_failure.dart';
import 'package:camera_stream_app/core/models/auth_tokens.dart';
import 'package:camera_stream_app/core/network/backend_client.dart';
import 'package:camera_stream_app/core/realtime/realtime_event_parser.dart';
import 'package:camera_stream_app/core/realtime/stomp_client_port.dart';
import 'package:camera_stream_app/features/auth/auth_controller.dart';
import 'package:camera_stream_app/features/notifications/data/notification_event_store.dart';
import 'package:camera_stream_app/features/notifications/data/realtime_providers.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

class _FakePort implements StompClientPort {
  final List<RealtimeConnectRequest> requests = [];
  int disconnectCalls = 0;

  @override
  Future<void> connect(RealtimeConnectRequest request) async {
    requests.add(request);
  }

  @override
  Future<void> disconnect() async {
    disconnectCalls++;
  }
}

Map<String, dynamic> _me({
  String id = 'user-1',
  String email = 'user-1@isg.local',
}) {
  return {
    'id': id,
    'email': email,
    'fullName': id,
    'active': true,
    'roles': ['ADMIN'],
    'departmentIds': <String>[],
  };
}

String _authBody({String accessToken = 'jwt', String refreshToken = 'rt'}) {
  return jsonEncode({
    'accessToken': accessToken,
    'refreshToken': refreshToken,
    'tokenType': 'Bearer',
  });
}

MockClient _apiClient({
  String userId = 'user-1',
  bool logoutFails = false,
  int usersStatus = 200,
}) {
  return MockClient((request) async {
    switch (request.url.path) {
      case '/api/v1/auth/login':
        return http.Response(_authBody(), 200);
      case '/api/v1/auth/refresh':
        return http.Response(_authBody(accessToken: 'fresh'), 200);
      case '/api/v1/auth/logout':
        if (logoutFails) {
          throw Exception('logout unreachable');
        }
        return http.Response('', 204);
      case '/api/v1/users/me':
        return http.Response(
          jsonEncode(_me(id: userId, email: '$userId@isg.local')),
          200,
        );
      case '/api/v1/users':
        return http.Response('', usersStatus);
      default:
        return http.Response('not found', 404);
    }
  });
}

void applyAlert(NotificationEventStore store, String violationId) {
  store.apply(
    parseRealtimeFrame(
      jsonEncode({
        'violationId': violationId,
        'type': 'NO_HELMET',
        'cameraName': 'Kamera 1',
        'departmentName': 'Üretim',
        'startedAt': '2026-08-22T10:00:00Z',
        'confidence': 0.9,
        'lifecycleStatus': 'ACTIVE',
        'recordingStatus': 'RECORDING',
        'clipReady': false,
        'coverImageReady': false,
      }),
    ),
  );
}

void main() {
  Future<({
    ProviderContainer container,
    AuthController controller,
    NotificationEventStore store,
    _FakePort port,
  })> boot({
    MockClient? httpClient,
    String userId = 'user-1',
    bool logoutFails = false,
    int usersStatus = 200,
  }) async {
    final port = _FakePort();
    final store = NotificationEventStore();
    final backend = BackendClient(
      baseUrl: 'http://backend',
      client: httpClient ??
          _apiClient(
            userId: userId,
            logoutFails: logoutFails,
            usersStatus: usersStatus,
          ),
    );
    final controller = AuthController(backend);
    await controller.signIn(email: 'a@b.c', password: 'x');

    final container = ProviderContainer(
      overrides: [
        backendClientProvider.overrideWith((ref) => backend),
        authSessionProvider.overrideWith((ref) => controller),
        stompClientPortProvider.overrideWith((ref) => port),
        notificationEventStoreProvider.overrideWith((ref) {
          ref.onDispose(store.dispose);
          return store;
        }),
      ],
    );

    container.read(realtimeLifecycleProvider);
    applyAlert(store, 'v-$userId');
    return (
      container: container,
      controller: controller,
      store: store,
      port: port,
    );
  }

  test('production logout path clears the store', () async {
    final env = await boot();
    addTearDown(env.container.dispose);
    expect(env.store.length, 1);

    await env.controller.signOut();
    await pumpEventQueue();

    expect(env.store.length, 0);
    expect(env.controller.state.authenticated, isFalse);
    expect(env.port.disconnectCalls, greaterThanOrEqualTo(1));
  });

  test('403 does not clear the store or the session', () async {
    final env = await boot(usersStatus: 403);
    addTearDown(env.container.dispose);

    await expectLater(
      () => env.controller.api.getJsonList('/api/v1/users'),
      throwsA(
        isA<ApiFailure>().having(
          (e) => e.kind,
          'kind',
          ApiFailureKind.forbidden,
        ),
      ),
    );
    await pumpEventQueue();

    expect(env.controller.state.authenticated, isTrue);
    expect(env.store.length, 1);
    expect(env.store.items.single.violationId, 'v-user-1');
  });

  test('token refresh success does not clear the store', () async {
    final env = await boot();
    addTearDown(env.container.dispose);

    env.controller.applyRefreshedTokens(
      const AuthTokens(accessToken: 'rotated', refreshToken: 'rt-2'),
    );
    await pumpEventQueue();

    expect(env.controller.state.authenticated, isTrue);
    expect(env.controller.state.accessToken, 'rotated');
    expect(env.store.length, 1);
    expect(env.store.items.single.violationId, 'v-user-1');
  });

  test('logout backend failure still clears local session and store', () async {
    final env = await boot(logoutFails: true);
    addTearDown(env.container.dispose);
    expect(env.store.length, 1);

    await env.controller.signOut();
    await pumpEventQueue();

    expect(env.controller.state.authenticated, isFalse);
    expect(env.store.length, 0);
  });

  test('User A logout then User B login does not keep User A alerts', () async {
    var currentUserId = 'user-a';
    final httpClient = MockClient((request) async {
      switch (request.url.path) {
        case '/api/v1/auth/login':
          return http.Response(_authBody(), 200);
        case '/api/v1/auth/logout':
          return http.Response('', 204);
        case '/api/v1/users/me':
          return http.Response(
            jsonEncode(_me(id: currentUserId, email: '$currentUserId@isg.local')),
            200,
          );
        default:
          return http.Response('not found', 404);
      }
    });

    final env = await boot(httpClient: httpClient, userId: 'user-a');
    addTearDown(env.container.dispose);
    expect(env.store.items.single.violationId, 'v-user-a');

    await env.controller.signOut();
    await pumpEventQueue();
    expect(env.store.length, 0);

    currentUserId = 'user-b';
    await env.controller.signIn(email: 'b@b.c', password: 'x');
    await pumpEventQueue();

    expect(env.store.length, 0, reason: 'User A kartı User B oturumuna sızmaz');
    expect(env.controller.state.currentUser?.id, 'user-b');

    applyAlert(env.store, 'v-user-b');
    expect(env.store.length, 1);
    expect(env.store.items.single.violationId, 'v-user-b');
  });
}
