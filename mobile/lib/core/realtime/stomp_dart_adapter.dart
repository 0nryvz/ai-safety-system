import 'package:stomp_dart_client/stomp_dart_client.dart';

import 'stomp_client_port.dart';

/// JWT **STOMP CONNECT native header** olarak gider; backend
/// `WebSocketJwtChannelInterceptor` yalnız CONNECT frame'inin
/// `Authorization` native header'ını okur.
const String realtimeAuthorizationHeader = 'Authorization';

/// Reconnect'in tek sahibi `RealtimeClient` olduğu için paketin built-in
/// auto-reconnect'i kapatılır (`reconnectDelay = 0`).
StompConfig buildRealtimeStompConfig({
  required String url,
  required String accessToken,
  required void Function(StompFrame frame) onConnect,
  required void Function() onWebSocketDone,
  required void Function(dynamic error) onWebSocketError,
  required void Function(StompFrame frame) onStompError,
}) {
  return StompConfig(
    url: url,
    reconnectDelay: Duration.zero,
    stompConnectHeaders: {
      realtimeAuthorizationHeader: 'Bearer $accessToken',
    },
    onConnect: onConnect,
    onWebSocketDone: onWebSocketDone,
    onWebSocketError: onWebSocketError,
    onStompError: onStompError,
  );
}

class StompDartAdapter implements StompClientPort {
  final StompClient Function(StompConfig config) _clientFactory;

  StompDartAdapter({
    StompClient Function(StompConfig config)? clientFactory,
  }) : _clientFactory =
            clientFactory ?? ((config) => StompClient(config: config));

  StompClient? _client;
  StompUnsubscribe? _unsubscribe;

  @override
  Future<void> connect(RealtimeConnectRequest request) async {
    await disconnect();

    final config = buildRealtimeStompConfig(
      url: request.url,
      accessToken: request.accessToken,
      onConnect: (_) {
        _unsubscribe = _client?.subscribe(
          destination: request.destination,
          callback: (frame) => request.onMessage(frame.body),
        );
        request.onConnected();
      },
      onWebSocketDone: () => request.onDisconnected(null),
      onWebSocketError: request.onDisconnected,
      onStompError: (frame) => request.onDisconnected(frame.body),
    );

    final client = _clientFactory(config);
    _client = client;
    client.activate();
  }

  @override
  Future<void> disconnect() async {
    _unsubscribe?.call();
    _unsubscribe = null;
    _client?.deactivate();
    _client = null;
  }
}
