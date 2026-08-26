/// Backend user-destination'ı: server `convertAndSendToUser(user, "/queue/alerts")`
/// yayınlar, client `/user` prefix'i ile subscribe eder.
const String realtimeAlertsDestination = '/user/queue/alerts';

class RealtimeConnectRequest {
  final String url;
  final String accessToken;
  final String destination;
  final void Function() onConnected;
  final void Function(Object? error) onDisconnected;
  final void Function(String? body) onMessage;

  const RealtimeConnectRequest({
    required this.url,
    required this.accessToken,
    required this.destination,
    required this.onConnected,
    required this.onDisconnected,
    required this.onMessage,
  });
}

/// STOMP transport sınırı. Reconnect kararı burada değil, `RealtimeClient`
/// içindedir; bu port yalnız tek bağlantı açar/kapatır.
abstract class StompClientPort {
  Future<void> connect(RealtimeConnectRequest request);

  Future<void> disconnect();
}
