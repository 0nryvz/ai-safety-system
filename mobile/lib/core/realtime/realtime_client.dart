import 'dart:async';

import 'realtime_connection_state.dart';
import 'realtime_event.dart';
import 'realtime_event_parser.dart';
import 'realtime_recovery.dart';
import 'realtime_session_source.dart';
import 'realtime_timer.dart';
import 'stomp_client_port.dart';

/// Bounded backoff: son değer üst sınırdır.
const List<Duration> defaultRealtimeBackoff = [
  Duration(seconds: 1),
  Duration(seconds: 2),
  Duration(seconds: 4),
  Duration(seconds: 8),
  Duration(seconds: 16),
  Duration(seconds: 30),
];

/// Realtime bağlantısının tek sahibi.
///
/// Sorumluluk sınırı:
/// - login session başına **tek** aktif socket
/// - **tek** reconnect timer ve bounded backoff (paket auto-reconnect kapalı)
/// - manual disconnect / logout sonrası reconnect yok
/// - session değişiminde eski socket kapatılır (token refresh socketi kapatmaz)
/// - başarılı reconnect'te REST recovery **tam bir kez**
///
/// Auth state'i [RealtimeSessionSource] üzerinden okur; ikinci bir auth/session
/// mekanizması kurmaz.
class RealtimeClient {
  final StompClientPort port;
  final RealtimeSessionSource session;
  final RealtimeRecovery recovery;
  final String url;
  final String destination;
  final List<Duration> backoff;
  final RealtimeTimerFactory timerFactory;

  RealtimeClient({
    required this.port,
    required this.session,
    required this.url,
    this.recovery = const NoopRealtimeRecovery(),
    this.destination = realtimeAlertsDestination,
    this.backoff = defaultRealtimeBackoff,
    this.timerFactory = defaultRealtimeTimerFactory,
  });

  final StreamController<RealtimeConnectionState> _stateController =
      StreamController<RealtimeConnectionState>.broadcast();
  final StreamController<RealtimeEvent> _eventController =
      StreamController<RealtimeEvent>.broadcast();

  RealtimeConnectionState _state = RealtimeConnectionState.offline;
  Timer? _reconnectTimer;
  int _generation = 0;
  int _attempt = 0;
  bool _manualStop = true;
  bool _disposed = false;
  bool _connectInFlight = false;
  bool _recoveryPending = false;
  String? _activeSessionKey;

  RealtimeConnectionState get state => _state;

  Stream<RealtimeConnectionState> get states => _stateController.stream;

  Stream<RealtimeEvent> get events => _eventController.stream;

  bool get hasPendingReconnect => _reconnectTimer?.isActive ?? false;

  /// Login sonrası bağlanır. Zaten bağlıysa yeni socket açmaz.
  void start() {
    if (_disposed) {
      return;
    }

    _manualStop = false;

    if (!session.isAuthenticated) {
      _setState(RealtimeConnectionState.offline);
      return;
    }

    _activeSessionKey = session.sessionKey;

    if (_state == RealtimeConnectionState.connected || _connectInFlight) {
      return;
    }

    _attempt = 0;
    _connect(firstAttempt: true);
  }

  /// Manual disconnect / logout: reconnect planlanmaz.
  Future<void> stop() async {
    if (_disposed) {
      return;
    }

    _manualStop = true;
    _cancelReconnectTimer();
    _attempt = 0;
    _recoveryPending = false;
    _generation++;
    _connectInFlight = false;
    _activeSessionKey = null;

    await port.disconnect();
    _setState(RealtimeConnectionState.offline);
  }

  /// Oturum değişimini uygular.
  ///
  /// - authenticated değilse socket kapanır, reconnect olmaz
  /// - `sessionKey` değiştiyse eski socket kapatılıp yenisi açılır
  /// - aynı oturumda token refresh socketi kapatmaz
  Future<void> handleSessionChanged() async {
    if (_disposed) {
      return;
    }

    if (!session.isAuthenticated) {
      await stop();
      return;
    }

    final key = session.sessionKey;
    if (_activeSessionKey != null && key != _activeSessionKey) {
      await stop();
      start();
      return;
    }

    if (_manualStop) {
      return;
    }

    if (_state == RealtimeConnectionState.offline) {
      start();
    }
  }

  Future<void> dispose() async {
    if (_disposed) {
      return;
    }

    _disposed = true;
    _cancelReconnectTimer();
    _generation++;
    _connectInFlight = false;

    await port.disconnect();
    await _stateController.close();
    await _eventController.close();
  }

  Future<void> _connect({bool firstAttempt = false}) async {
    if (_disposed || _manualStop) {
      return;
    }

    final token = session.accessToken;
    if (!session.isAuthenticated || token == null || token.isEmpty) {
      _setState(RealtimeConnectionState.offline);
      return;
    }

    _cancelReconnectTimer();

    final generation = ++_generation;
    _connectInFlight = true;
    _setState(
      firstAttempt
          ? RealtimeConnectionState.connecting
          : RealtimeConnectionState.reconnecting,
    );

    final request = RealtimeConnectRequest(
      url: url,
      accessToken: token,
      destination: destination,
      onConnected: () => _handleConnected(generation),
      onDisconnected: (_) => _handleDisconnected(generation),
      onMessage: (body) => _handleMessage(generation, body),
    );

    try {
      await port.connect(request);
    } catch (_) {
      if (_isStale(generation)) {
        return;
      }
      _connectInFlight = false;
      _scheduleReconnect();
    }
  }

  void _handleConnected(int generation) {
    if (_isStale(generation)) {
      return;
    }

    _connectInFlight = false;
    _attempt = 0;
    _setState(RealtimeConnectionState.connected);

    if (_recoveryPending) {
      _recoveryPending = false;
      recovery.recoverAfterReconnect();
    }
  }

  void _handleDisconnected(int generation) {
    if (_isStale(generation)) {
      return;
    }

    _connectInFlight = false;

    if (_manualStop) {
      _setState(RealtimeConnectionState.offline);
      return;
    }

    // Bir kopma yaşandı: sonraki başarılı bağlantıda REST recovery gerekir.
    _recoveryPending = true;
    _setState(RealtimeConnectionState.reconnecting);
    _scheduleReconnect();
  }

  void _handleMessage(int generation, String? body) {
    if (_isStale(generation)) {
      return;
    }

    final event = parseRealtimeFrame(body);
    if (!_eventController.isClosed) {
      _eventController.add(event);
    }
  }

  void _scheduleReconnect() {
    if (_disposed || _manualStop) {
      return;
    }

    // Tek timer: bekleyen bir deneme varsa ikinci timer açılmaz.
    if (_reconnectTimer?.isActive ?? false) {
      return;
    }

    final index = _attempt < backoff.length ? _attempt : backoff.length - 1;
    final delay = backoff[index];
    _attempt++;

    _reconnectTimer = timerFactory(delay, () {
      _reconnectTimer = null;
      _connect();
    });
  }

  void _cancelReconnectTimer() {
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
  }

  bool _isStale(int generation) => _disposed || generation != _generation;

  void _setState(RealtimeConnectionState next) {
    if (_state == next) {
      return;
    }
    _state = next;
    if (!_stateController.isClosed) {
      _stateController.add(next);
    }
  }
}
