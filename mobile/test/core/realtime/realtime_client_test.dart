import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:camera_stream_app/core/realtime/realtime_client.dart';
import 'package:camera_stream_app/core/realtime/realtime_connection_state.dart';
import 'package:camera_stream_app/core/realtime/realtime_event.dart';
import 'package:camera_stream_app/core/realtime/realtime_recovery.dart';
import 'package:camera_stream_app/core/realtime/realtime_session_source.dart';
import 'package:camera_stream_app/core/realtime/stomp_client_port.dart';

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

class _FakeTimer implements Timer {
  _FakeTimer(this.delay, this.callback);

  final Duration delay;
  final void Function() callback;
  bool _active = true;

  @override
  void cancel() {
    _active = false;
  }

  @override
  bool get isActive => _active;

  @override
  int get tick => 0;

  void fire() {
    if (!_active) {
      return;
    }
    _active = false;
    callback();
  }
}

class _FakeClock {
  final List<_FakeTimer> timers = [];

  Timer create(Duration delay, void Function() callback) {
    final timer = _FakeTimer(delay, callback);
    timers.add(timer);
    return timer;
  }

  List<Duration> get delays => timers.map((t) => t.delay).toList();

  int get activeCount => timers.where((t) => t.isActive).length;

  void fireLast() => timers.last.fire();
}

class _FakeSession implements RealtimeSessionSource {
  bool authenticated = true;
  String? token = 'access-1';
  String? key = 'user-1';

  @override
  bool get isAuthenticated => authenticated;

  @override
  String? get accessToken => token;

  @override
  String? get sessionKey => key;
}

class _CountingRecovery implements RealtimeRecovery {
  int calls = 0;

  @override
  Future<void> recoverAfterReconnect() async {
    calls++;
  }
}

void main() {
  late _FakePort port;
  late _FakeClock clock;
  late _FakeSession session;
  late _CountingRecovery recovery;
  late RealtimeClient client;

  RealtimeClient build() {
    return RealtimeClient(
      port: port,
      session: session,
      url: 'ws://10.0.2.2:8080/ws',
      recovery: recovery,
      timerFactory: clock.create,
    );
  }

  setUp(() {
    port = _FakePort();
    clock = _FakeClock();
    session = _FakeSession();
    recovery = _CountingRecovery();
    client = build();
  });

  tearDown(() async {
    await client.dispose();
  });

  test('start connects once with bearer token and alerts destination',
      () async {
    final states = <RealtimeConnectionState>[];
    client.states.listen(states.add);

    client.start();
    await pumpEventQueue();

    expect(port.requests, hasLength(1));
    expect(port.last.url, 'ws://10.0.2.2:8080/ws');
    expect(port.last.accessToken, 'access-1');
    expect(port.last.destination, '/user/queue/alerts');
    expect(client.state, RealtimeConnectionState.connecting);

    port.connected();
    await pumpEventQueue();

    expect(client.state, RealtimeConnectionState.connected);
    expect(
      states,
      [RealtimeConnectionState.connecting, RealtimeConnectionState.connected],
    );
  });

  test('repeated start keeps a single active connection', () async {
    client.start();
    port.connected();
    client.start();
    client.start();
    await pumpEventQueue();

    expect(port.requests, hasLength(1));
    expect(client.state, RealtimeConnectionState.connected);
  });

  test('unauthenticated session stays offline without connecting', () async {
    session
      ..authenticated = false
      ..token = null;

    client.start();
    await pumpEventQueue();

    expect(port.requests, isEmpty);
    expect(client.state, RealtimeConnectionState.offline);
  });

  test('drop moves to reconnecting and schedules exactly one timer', () async {
    client.start();
    port.connected();
    await pumpEventQueue();

    port.drop();
    port.drop();
    await pumpEventQueue();

    expect(client.state, RealtimeConnectionState.reconnecting);
    expect(clock.timers, hasLength(1));
    expect(clock.activeCount, 1);
    expect(clock.delays.single, const Duration(seconds: 1));
    expect(port.requests, hasLength(1));
  });

  test('backoff escalates and is bounded by the last step', () async {
    client.start();
    port.connected();
    await pumpEventQueue();

    for (var i = 0; i < 7; i++) {
      port.drop();
      await pumpEventQueue();
      clock.fireLast();
      await pumpEventQueue();
    }

    expect(clock.delays, const [
      Duration(seconds: 1),
      Duration(seconds: 2),
      Duration(seconds: 4),
      Duration(seconds: 8),
      Duration(seconds: 16),
      Duration(seconds: 30),
      Duration(seconds: 30),
    ]);
    expect(port.requests, hasLength(8));
    expect(client.state, RealtimeConnectionState.reconnecting);
  });

  test('successful reconnect triggers REST recovery exactly once', () async {
    client.start();
    port.connected();
    await pumpEventQueue();

    expect(recovery.calls, 0, reason: 'first connect is not a recovery');

    port.drop();
    await pumpEventQueue();
    clock.fireLast();
    await pumpEventQueue();
    port.connected();
    await pumpEventQueue();

    expect(client.state, RealtimeConnectionState.connected);
    expect(recovery.calls, 1);

    port.connected();
    await pumpEventQueue();
    expect(recovery.calls, 1);
  });

  test('manual stop closes socket and never reconnects', () async {
    client.start();
    port.connected();
    await pumpEventQueue();

    await client.stop();
    port.drop();
    await pumpEventQueue();

    expect(port.disconnectCalls, 1);
    expect(client.state, RealtimeConnectionState.offline);
    expect(clock.timers, isEmpty);
    expect(port.requests, hasLength(1));
  });

  test('pending reconnect is cancelled by manual stop', () async {
    client.start();
    port.connected();
    await pumpEventQueue();

    port.drop();
    await pumpEventQueue();
    expect(client.hasPendingReconnect, isTrue);

    await client.stop();

    expect(client.hasPendingReconnect, isFalse);
    expect(clock.activeCount, 0);
  });

  test('session change closes the old socket and reconnects with new session',
      () async {
    client.start();
    port.connected();
    await pumpEventQueue();

    session
      ..key = 'user-2'
      ..token = 'access-user-2';
    await client.handleSessionChanged();
    await pumpEventQueue();

    expect(port.disconnectCalls, 1);
    expect(port.requests, hasLength(2));
    expect(port.last.accessToken, 'access-user-2');
    expect(client.state, RealtimeConnectionState.connecting);
  });

  test('logout via session change stops without reconnect', () async {
    client.start();
    port.connected();
    await pumpEventQueue();

    session
      ..authenticated = false
      ..token = null
      ..key = null;
    await client.handleSessionChanged();
    port.drop();
    await pumpEventQueue();

    expect(port.disconnectCalls, 1);
    expect(client.state, RealtimeConnectionState.offline);
    expect(clock.timers, isEmpty);
  });

  test('token refresh keeps socket open and later reconnect uses fresh token',
      () async {
    client.start();
    port.connected();
    await pumpEventQueue();
    expect(client.state, RealtimeConnectionState.connected);

    session.token = 'access-refreshed';
    await client.handleSessionChanged();
    await pumpEventQueue();

    expect(port.disconnectCalls, 0, reason: 'refresh must not close socket');
    expect(port.requests, hasLength(1));
    expect(client.state, RealtimeConnectionState.connected);

    port.drop();
    await pumpEventQueue();
    clock.fireLast();
    await pumpEventQueue();

    expect(port.requests, hasLength(2));
    expect(port.last.accessToken, 'access-refreshed');
  });

  test('malformed frame is surfaced as parse failure without breaking stream',
      () async {
    final events = <RealtimeEvent>[];
    client.events.listen(events.add);

    client.start();
    port.connected();
    await pumpEventQueue();

    port.message('not-json');
    port.message(
      '{"violationId":"v-1","lifecycleStatus":"ENDED",'
      '"recordingStatus":"COMPLETED","clipReady":true,'
      '"updatedAt":"2026-08-22T10:05:00Z"}',
    );
    await pumpEventQueue();

    expect(events, hasLength(2));
    expect(events.first, isA<RealtimeParseFailure>());
    expect(events.last, isA<RealtimeViolationUpdateEvent>());
    expect(client.state, RealtimeConnectionState.connected);
  });

  test('dispose cancels timers, closes streams and drops the socket', () async {
    client.start();
    port.connected();
    await pumpEventQueue();

    port.drop();
    await pumpEventQueue();
    expect(clock.activeCount, 1);

    await client.dispose();

    expect(clock.activeCount, 0);
    expect(port.disconnectCalls, 1);
    await expectLater(client.events, emitsDone);
    await expectLater(client.states, emitsDone);

    // Dispose sonrası gelen frame'ler yok sayılır.
    port.message('{}');
    client.start();
    await pumpEventQueue();
    expect(port.requests, hasLength(1));
  });
}
