import 'package:camera_stream_app/features/streaming/publish_session_store.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('PublishSessionStore reconnect sözleşmesi', () {
    test('1. manual Start -> session A', () {
      final store = PublishSessionStore();
      var next = 0;
      final ids = ['A', 'B', 'C'];

      final sessionA = store.beginManualSession(() => ids[next++]);

      expect(sessionA, 'A');
      expect(store.sessionId, 'A');
    });

    test('2. network failure -> automatic reconnect -> yine session A', () {
      final store = PublishSessionStore();
      store.beginManualSession(() => 'A');

      final reconnectId = store.sessionForAutomaticReconnect();

      expect(reconnectId, 'A');
      expect(store.sessionId, 'A');
    });

    test('3. ikinci automatic reconnect -> yine session A', () {
      final store = PublishSessionStore();
      store.beginManualSession(() => 'A');

      expect(store.sessionForAutomaticReconnect(), 'A');
      expect(store.sessionForAutomaticReconnect(), 'A');
      expect(store.sessionId, 'A');
    });

    test('4. manual Stop -> Start -> session B ve B != A', () {
      final store = PublishSessionStore();
      var next = 0;
      final ids = ['A', 'B'];

      final sessionA = store.beginManualSession(() => ids[next++]);
      store.clear();
      final sessionB = store.beginManualSession(() => ids[next++]);

      expect(sessionA, 'A');
      expect(sessionB, 'B');
      expect(sessionB, isNot(sessionA));
      expect(store.sessionId, 'B');
    });

    test('5. manual Stop sonrası reconnect timer çalışmamalı', () {
      expect(
        ReconnectEligibility.canSchedule(
          manualStop: true,
          isAppInBackground: false,
          alreadyReconnecting: false,
        ),
        isFalse,
      );

      expect(
        ReconnectEligibility.canSchedule(
          manualStop: false,
          isAppInBackground: false,
          alreadyReconnecting: false,
        ),
        isTrue,
      );
    });

    test('automatic reconnect yeni UUID üretmez', () {
      final store = PublishSessionStore();
      var minted = 0;

      store.beginManualSession(() {
        minted++;
        return 'session-$minted';
      });

      store.sessionForAutomaticReconnect();
      store.sessionForAutomaticReconnect();

      expect(minted, 1);
      expect(store.sessionId, 'session-1');
    });
  });
}
