import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:camera_stream_app/core/models/user_summary.dart';
import 'package:camera_stream_app/core/realtime/realtime_client.dart';
import 'package:camera_stream_app/core/realtime/realtime_connection_state.dart';
import 'package:camera_stream_app/core/realtime/realtime_event_parser.dart';
import 'package:camera_stream_app/core/realtime/stomp_client_port.dart';
import 'package:camera_stream_app/features/auth/auth_session.dart';
import 'package:camera_stream_app/features/notifications/data/auth_realtime_bridge.dart';
import 'package:camera_stream_app/features/notifications/data/notification_event_store.dart';

class _FakePort implements StompClientPort {
  final List<RealtimeConnectRequest> requests = [];
  int disconnectCalls = 0;

  RealtimeConnectRequest get last => requests.last;

  @override
  Future<void> connect(RealtimeConnectRequest request) async {
    requests.add(request);
  }

  @override
  Future<void> disconnect() async {
    disconnectCalls++;
  }

  void connected() => last.onConnected();

  void drop() => last.onDisconnected(null);

  void message(String? body) => last.onMessage(body);
}

UserSummary user(String id) => UserSummary(
      id: id,
      email: '$id@example.com',
      fullName: id,
      active: true,
    );

AuthSession session(String userId, String token) => AuthSession(
      accessToken: token,
      refreshToken: 'refresh-$userId',
      currentUser: user(userId),
    );

String alertBody({
  required String violationId,
  String startedAt = '2026-08-22T10:00:00Z',
}) {
  return jsonEncode({
    'violationId': violationId,
    'type': 'NO_HELMET',
    'cameraName': 'Kamera 1',
    'departmentName': 'Üretim',
    'startedAt': startedAt,
    'confidence': 0.9,
    'lifecycleStatus': 'ACTIVE',
    'recordingStatus': 'RECORDING',
    'clipReady': false,
    'coverImageReady': false,
  });
}

void main() {
  late _FakePort port;
  late AuthSessionRealtimeSource source;
  late RealtimeClient client;
  late NotificationEventStore store;
  late RealtimeLifecycle lifecycle;

  setUp(() {
    port = _FakePort();
    source = AuthSessionRealtimeSource();
    client = RealtimeClient(
      port: port,
      session: source,
      url: 'ws://10.0.2.2:8080/ws',
    );
    store = NotificationEventStore();
    lifecycle = RealtimeLifecycle(
      source: source,
      client: client,
      store: store,
    );
  });

  tearDown(() async {
    await client.dispose();
    await store.dispose();
  });

  test('source reads O1 session without owning auth state', () {
    expect(source.isAuthenticated, isFalse);
    expect(source.accessToken, isNull);
    expect(source.sessionKey, isNull);

    source.update(session('user-1', 'access-1'));

    expect(source.isAuthenticated, isTrue);
    expect(source.accessToken, 'access-1');
    expect(source.sessionKey, 'user-1');
  });

  test('login starts the realtime connection', () async {
    await lifecycle.onSession(session('user-1', 'access-1'));
    await pumpEventQueue();

    expect(port.requests, hasLength(1));
    expect(port.last.accessToken, 'access-1');
    expect(port.last.destination, '/user/queue/alerts');
    expect(client.state, RealtimeConnectionState.connecting);
  });

  test('token refresh in the same session does not touch the socket', () async {
    await lifecycle.onSession(session('user-1', 'access-1'));
    port.connected();
    await pumpEventQueue();

    await lifecycle.onSession(session('user-1', 'access-2'));
    await pumpEventQueue();

    expect(port.disconnectCalls, 0);
    expect(port.requests, hasLength(1));
    expect(client.state, RealtimeConnectionState.connected);
    expect(source.accessToken, 'access-2');
  });

  test('logout stops the connection and blocks reconnect', () async {
    await lifecycle.onSession(session('user-1', 'access-1'));
    port.connected();
    await pumpEventQueue();

    await lifecycle.onSession(const AuthSession.unauthenticated());
    await pumpEventQueue();

    expect(port.disconnectCalls, 1);
    expect(client.state, RealtimeConnectionState.offline);
    expect(client.hasPendingReconnect, isFalse);
  });

  test('re-login after logout opens a fresh connection', () async {
    await lifecycle.onSession(session('user-1', 'access-1'));
    port.connected();
    await lifecycle.onSession(const AuthSession.unauthenticated());
    await lifecycle.onSession(session('user-1', 'access-9'));
    await pumpEventQueue();

    expect(port.requests, hasLength(2));
    expect(port.last.accessToken, 'access-9');
  });

  test('different user closes the old socket', () async {
    await lifecycle.onSession(session('user-1', 'access-1'));
    port.connected();
    await pumpEventQueue();

    await lifecycle.onSession(session('user-2', 'access-2'));
    await pumpEventQueue();

    expect(port.disconnectCalls, 1);
    expect(port.requests, hasLength(2));
    expect(port.last.accessToken, 'access-2');
  });

  test('explicit logout clears the notification store', () async {
    await lifecycle.onSession(session('user-1', 'access-1'));
    port.connected();
    store.apply(parseRealtimeFrame(alertBody(violationId: 'v-a')));
    expect(store.length, 1);

    await lifecycle.onSession(const AuthSession.unauthenticated());
    await pumpEventQueue();

    expect(store.length, 0);
    expect(store.items, isEmpty);
  });

  test('logout still stops the socket and blocks reconnect after store clear',
      () async {
    await lifecycle.onSession(session('user-1', 'access-1'));
    port.connected();
    store.apply(parseRealtimeFrame(alertBody(violationId: 'v-a')));

    await lifecycle.onSession(const AuthSession.unauthenticated());
    port.drop();
    await pumpEventQueue();

    expect(store.length, 0);
    expect(port.disconnectCalls, 1);
    expect(client.state, RealtimeConnectionState.offline);
    expect(client.hasPendingReconnect, isFalse);
  });

  test('User A alerts do not survive logout into User B', () async {
    final subscription = client.events.listen(store.apply);
    addTearDown(subscription.cancel);

    await lifecycle.onSession(session('user-1', 'access-1'));
    port.connected();
    port.message(alertBody(violationId: 'v-user-a'));
    await pumpEventQueue();
    expect(store.items.single.violationId, 'v-user-a');

    await lifecycle.onSession(const AuthSession.unauthenticated());
    await pumpEventQueue();
    expect(store.length, 0);

    await lifecycle.onSession(session('user-2', 'access-2'));
    port.connected();
    await pumpEventQueue();

    expect(store.length, 0, reason: 'User A kartı User B oturumuna sızmaz');

    port.message(alertBody(
      violationId: 'v-user-b',
      startedAt: '2026-08-22T11:00:00Z',
    ));
    await pumpEventQueue();

    expect(store.length, 1);
    expect(store.items.single.violationId, 'v-user-b');
  });

  test('token refresh in the same session does not clear the store', () async {
    await lifecycle.onSession(session('user-1', 'access-1'));
    port.connected();
    store.apply(parseRealtimeFrame(alertBody(violationId: 'v-keep')));
    expect(store.length, 1);

    await lifecycle.onSession(session('user-1', 'access-2'));
    await pumpEventQueue();

    expect(store.length, 1);
    expect(store.items.single.violationId, 'v-keep');
    expect(port.disconnectCalls, 0);
    expect(client.state, RealtimeConnectionState.connected);
  });

  test('temporary disconnect/reconnect does not clear the store', () async {
    await lifecycle.onSession(session('user-1', 'access-1'));
    port.connected();
    store.apply(parseRealtimeFrame(alertBody(violationId: 'v-keep')));

    port.drop();
    await pumpEventQueue();

    expect(store.length, 1);
    expect(store.items.single.violationId, 'v-keep');
    expect(client.hasPendingReconnect, isTrue);
  });
}
