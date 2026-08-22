import 'package:flutter_test/flutter_test.dart';
import 'package:camera_stream_app/core/models/user_summary.dart';
import 'package:camera_stream_app/core/realtime/realtime_client.dart';
import 'package:camera_stream_app/core/realtime/realtime_connection_state.dart';
import 'package:camera_stream_app/core/realtime/stomp_client_port.dart';
import 'package:camera_stream_app/features/auth/auth_session.dart';
import 'package:camera_stream_app/features/notifications/data/auth_realtime_bridge.dart';

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

void main() {
  late _FakePort port;
  late AuthSessionRealtimeSource source;
  late RealtimeClient client;
  late RealtimeLifecycle lifecycle;

  setUp(() {
    port = _FakePort();
    source = AuthSessionRealtimeSource();
    client = RealtimeClient(
      port: port,
      session: source,
      url: 'ws://10.0.2.2:8080/ws',
    );
    lifecycle = RealtimeLifecycle(source: source, client: client);
  });

  tearDown(() async => client.dispose());

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
}
