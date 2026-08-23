import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/config/app_config.dart';
import '../../../core/realtime/realtime_client.dart';
import '../../../core/realtime/realtime_endpoint.dart';
import '../../../core/realtime/realtime_recovery.dart';
import '../../../core/realtime/stomp_client_port.dart';
import '../../../core/realtime/stomp_dart_adapter.dart';
import '../../auth/auth_controller.dart';
import '../../auth/auth_session.dart';
import 'auth_realtime_bridge.dart';
import 'notification_event_store.dart';

final realtimeSessionSourceProvider =
    Provider<AuthSessionRealtimeSource>((ref) {
  return AuthSessionRealtimeSource(ref.read(authSessionProvider));
});

final notificationEventStoreProvider = Provider<NotificationEventStore>((ref) {
  final store = NotificationEventStore();
  ref.onDispose(store.dispose);
  return store;
});

/// Reconnect sonrası S0/S1/S2 sayfalarının mevcut `_load` yolunu tetikler.
///
/// Tek increment = tek recovery. Sayfalar `didUpdateWidget` ile dinler;
/// repository/API yeniden yazılmaz.
final restRecoveryTickProvider = StateProvider<int>((ref) => 0);

final realtimeRecoveryProvider = Provider<CompositeRealtimeRecovery>((ref) {
  return CompositeRealtimeRecovery([
    CallbackRealtimeRecovery(() async {
      ref.read(restRecoveryTickProvider.notifier).state++;
    }),
  ]);
});

final stompClientPortProvider = Provider<StompClientPort>((ref) {
  return StompDartAdapter();
});

final realtimeClientProvider = Provider<RealtimeClient>((ref) {
  final client = RealtimeClient(
    port: ref.watch(stompClientPortProvider),
    session: ref.watch(realtimeSessionSourceProvider),
    url: realtimeWebSocketUrl(AppConfig.backendBaseUrl),
    recovery: ref.watch(realtimeRecoveryProvider),
  );

  final store = ref.watch(notificationEventStoreProvider);
  final subscription = client.events.listen(store.apply);

  ref.onDispose(() {
    subscription.cancel();
    client.dispose();
  });

  return client;
});

/// Auth session değişimlerini realtime bağlantısına bağlar.
///
/// Bu provider izlenmedikçe çalışmaz; production auto-connect için
/// `app.dart`/`AppShell` bağlaması O4 handoff'udur.
final realtimeLifecycleProvider = Provider<RealtimeLifecycle>((ref) {
  final lifecycle = RealtimeLifecycle(
    source: ref.watch(realtimeSessionSourceProvider),
    client: ref.watch(realtimeClientProvider),
    store: ref.watch(notificationEventStoreProvider),
  );

  ref.listen<AuthSession>(
    authSessionProvider,
    (_, next) => lifecycle.onSession(next),
    fireImmediately: true,
  );

  return lifecycle;
});
