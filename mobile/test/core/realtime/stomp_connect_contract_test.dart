import 'package:flutter_test/flutter_test.dart';
import 'package:camera_stream_app/core/realtime/realtime_endpoint.dart';
import 'package:camera_stream_app/core/realtime/stomp_client_port.dart';
import 'package:camera_stream_app/core/realtime/stomp_dart_adapter.dart';
import 'package:stomp_dart_client/stomp_dart_client.dart';

/// Ağ açmayan STOMP client: `activate()` doğrudan CONNECTED frame simüle eder.
class _FakeStompClient extends StompClient {
  _FakeStompClient(StompConfig config) : super(config: config);

  bool activated = false;
  bool deactivated = false;
  final List<String> subscriptions = [];
  StompFrameCallback? messageCallback;
  int unsubscribeCalls = 0;

  @override
  void activate() {
    activated = true;
    config.onConnect(StompFrame(command: 'CONNECTED'));
  }

  @override
  void deactivate() {
    deactivated = true;
  }

  @override
  StompUnsubscribe subscribe({
    required String destination,
    required StompFrameCallback callback,
    Map<String, String>? headers,
  }) {
    subscriptions.add(destination);
    messageCallback = callback;
    return ({Map<String, String>? unsubscribeHeaders}) => unsubscribeCalls++;
  }
}

void main() {
  group('STOMP CONNECT contract', () {
    test('Authorization is a STOMP CONNECT native header, not a WS handshake '
        'header', () {
      final config = buildRealtimeStompConfig(
        url: 'ws://10.0.2.2:8080/ws',
        accessToken: 'token-1',
        onConnect: (_) {},
        onWebSocketDone: () {},
        onWebSocketError: (_) {},
        onStompError: (_) {},
      );

      expect(config.stompConnectHeaders, {'Authorization': 'Bearer token-1'});
      expect(config.webSocketConnectHeaders, isNull);
    });

    test('package auto-reconnect is disabled: RealtimeClient owns reconnect',
        () {
      final config = buildRealtimeStompConfig(
        url: 'ws://10.0.2.2:8080/ws',
        accessToken: 'token-1',
        onConnect: (_) {},
        onWebSocketDone: () {},
        onWebSocketError: (_) {},
        onStompError: (_) {},
      );

      expect(config.reconnectDelay, Duration.zero);
      expect(config.useSockJS, isFalse);
    });

    test('adapter connects with Bearer header and subscribes to '
        '/user/queue/alerts', () async {
      final created = <_FakeStompClient>[];
      final adapter = StompDartAdapter(
        clientFactory: (config) {
          final client = _FakeStompClient(config);
          created.add(client);
          return client;
        },
      );

      var connected = false;
      final bodies = <String?>[];

      await adapter.connect(
        RealtimeConnectRequest(
          url: 'ws://10.0.2.2:8080/ws',
          accessToken: 'access-42',
          destination: realtimeAlertsDestination,
          onConnected: () => connected = true,
          onDisconnected: (_) {},
          onMessage: bodies.add,
        ),
      );

      expect(created, hasLength(1));
      final client = created.single;
      expect(client.activated, isTrue);
      expect(client.config.url, 'ws://10.0.2.2:8080/ws');
      expect(
        client.config.stompConnectHeaders,
        {'Authorization': 'Bearer access-42'},
      );
      expect(client.subscriptions, ['/user/queue/alerts']);
      expect(connected, isTrue);

      client.messageCallback!(StompFrame(command: 'MESSAGE', body: '{}'));
      expect(bodies, ['{}']);
    });

    test('adapter keeps a single socket and cleans up subscription', () async {
      final created = <_FakeStompClient>[];
      final adapter = StompDartAdapter(
        clientFactory: (config) {
          final client = _FakeStompClient(config);
          created.add(client);
          return client;
        },
      );

      RealtimeConnectRequest request(String token) => RealtimeConnectRequest(
            url: 'ws://10.0.2.2:8080/ws',
            accessToken: token,
            destination: realtimeAlertsDestination,
            onConnected: () {},
            onDisconnected: (_) {},
            onMessage: (_) {},
          );

      await adapter.connect(request('first'));
      await adapter.connect(request('second'));

      expect(created, hasLength(2));
      expect(created.first.deactivated, isTrue);
      expect(created.first.unsubscribeCalls, 1);
      expect(created.last.deactivated, isFalse);

      await adapter.disconnect();
      expect(created.last.deactivated, isTrue);
      expect(created.last.unsubscribeCalls, 1);
    });

    test('websocket url is derived from backend base url', () {
      expect(
        realtimeWebSocketUrl('http://10.0.2.2:8080'),
        'ws://10.0.2.2:8080/ws',
      );
      expect(
        realtimeWebSocketUrl('https://api.example.com/'),
        'wss://api.example.com/ws',
      );
    });
  });
}
