/// Backend REST base URL'inden STOMP WebSocket URL'i üretir.
///
/// Backend `/ws` endpointini native WebSocket olarak yayınlar (SockJS yok).
String realtimeWebSocketUrl(String backendBaseUrl) {
  var base = backendBaseUrl.trim();
  while (base.endsWith('/')) {
    base = base.substring(0, base.length - 1);
  }

  if (base.startsWith('https://')) {
    base = 'wss://${base.substring('https://'.length)}';
  } else if (base.startsWith('http://')) {
    base = 'ws://${base.substring('http://'.length)}';
  }

  return '$base/ws';
}
