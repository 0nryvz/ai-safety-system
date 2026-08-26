import 'dart:async';

import 'package:camera_stream_app/core/realtime/realtime_client.dart';
import 'package:camera_stream_app/core/realtime/realtime_connection_state.dart';
import 'package:camera_stream_app/core/realtime/realtime_recovery.dart';
import 'package:camera_stream_app/core/realtime/realtime_session_source.dart';
import 'package:camera_stream_app/core/realtime/stomp_client_port.dart';
import 'package:camera_stream_app/features/notifications/data/realtime_providers.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

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
}

class _FakeTimer implements Timer {
  _FakeTimer(this.callback);

  final void Function() callback;
  bool _active = true;

  @override
  void cancel() => _active = false;

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

class _FakeSession implements RealtimeSessionSource {
  @override
  bool get isAuthenticated => true;

  @override
  String? get accessToken => 'access-1';

  @override
  String? get sessionKey => 'user-1';
}

void main() {
  test('CallbackRealtimeRecovery mevcut callbacki bir kez çağırır', () async {
    var calls = 0;
    final recovery = CallbackRealtimeRecovery(() async {
      calls++;
    });

    await recovery.recoverAfterReconnect();
    await recovery.recoverAfterReconnect();

    expect(calls, 2);
  });

  test('CallbackRealtimeRecovery hata fırlatmaz', () async {
    final recovery = CallbackRealtimeRecovery(() async {
      throw StateError('refresh failed');
    });

    await expectLater(recovery.recoverAfterReconnect(), completes);
  });

  test('CompositeRealtimeRecovery üç feature callbackini tetikler', () async {
    final hits = <String>[];
    final recovery = CompositeRealtimeRecovery([
      CallbackRealtimeRecovery(() async => hits.add('dashboard')),
      CallbackRealtimeRecovery(() async => hits.add('cameras')),
      CallbackRealtimeRecovery(() async {
        throw StateError('cameras ok, violations fail');
      }),
      CallbackRealtimeRecovery(() async => hits.add('violations-never')),
    ]);

    // violations callback after a throw still runs because composite isolates.
    final isolated = CompositeRealtimeRecovery([
      CallbackRealtimeRecovery(() async => hits.add('dashboard')),
      CallbackRealtimeRecovery(() async => hits.add('cameras')),
      CallbackRealtimeRecovery(() async => hits.add('violations')),
    ]);

    await isolated.recoverAfterReconnect();
    expect(hits, ['dashboard', 'cameras', 'violations']);

    hits.clear();
    await recovery.recoverAfterReconnect();
    expect(hits, ['dashboard', 'cameras', 'violations-never']);
  });

  test('production recovery hook REST tickini tam bir kez artırır', () async {
    final container = ProviderContainer();
    addTearDown(container.dispose);

    expect(container.read(restRecoveryTickProvider), 0);

    await container.read(realtimeRecoveryProvider).recoverAfterReconnect();
    expect(container.read(restRecoveryTickProvider), 1);

    await container.read(realtimeRecoveryProvider).recoverAfterReconnect();
    expect(container.read(restRecoveryTickProvider), 2);
  });

  test('reconnect recovery tickini bir kez artırır', () async {
    final container = ProviderContainer();
    addTearDown(container.dispose);

    final port = _FakePort();
    final timers = <_FakeTimer>[];
    final client = RealtimeClient(
      port: port,
      session: _FakeSession(),
      url: 'ws://10.0.2.2:8080/ws',
      recovery: container.read(realtimeRecoveryProvider),
      timerFactory: (delay, callback) {
        final timer = _FakeTimer(callback);
        timers.add(timer);
        return timer;
      },
    );
    addTearDown(client.dispose);

    client.start();
    await pumpEventQueue();
    port.connected();
    await pumpEventQueue();
    expect(container.read(restRecoveryTickProvider), 0);

    port.drop();
    await pumpEventQueue();
    timers.last.fire();
    await pumpEventQueue();
    port.connected();
    await pumpEventQueue();

    expect(client.state, RealtimeConnectionState.connected);
    expect(container.read(restRecoveryTickProvider), 1);

    port.connected();
    await pumpEventQueue();
    expect(container.read(restRecoveryTickProvider), 1);
  });
}
