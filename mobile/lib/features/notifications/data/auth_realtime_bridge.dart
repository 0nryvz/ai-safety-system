import '../../../core/realtime/realtime_client.dart';
import '../../../core/realtime/realtime_session_source.dart';
import '../../auth/auth_session.dart';

/// O1 auth session'ını realtime katmanına **okuyarak** taşır.
///
/// Auth state'i burada değiştirilmez; ikinci bir session mekanizması yoktur.
class AuthSessionRealtimeSource implements RealtimeSessionSource {
  AuthSession _session;

  AuthSessionRealtimeSource([
    AuthSession session = const AuthSession.unauthenticated(),
  ]) : _session = session;

  void update(AuthSession session) => _session = session;

  @override
  bool get isAuthenticated => _session.authenticated;

  @override
  String? get accessToken => _session.accessToken;

  /// Login session kimliği kullanıcıdır; access token refresh bunu değiştirmez.
  @override
  String? get sessionKey => _session.currentUser?.id;
}

/// Login/logout ve session değişimini realtime bağlantısına bağlar.
///
/// Production auto-connect için bu sınıfı bir provider'ın izlemesi gerekir;
/// `app.dart`/`AppShell` bağlaması O4 handoff'udur.
class RealtimeLifecycle {
  final AuthSessionRealtimeSource source;
  final RealtimeClient client;

  RealtimeLifecycle({
    required this.source,
    required this.client,
  });

  bool _wasAuthenticated = false;
  String? _sessionKey;

  Future<void> onSession(AuthSession session) async {
    source.update(session);

    if (!session.authenticated) {
      _wasAuthenticated = false;
      _sessionKey = null;
      await client.stop();
      return;
    }

    final key = session.currentUser?.id;

    if (!_wasAuthenticated) {
      _wasAuthenticated = true;
      _sessionKey = key;
      client.start();
      return;
    }

    if (key != _sessionKey) {
      _sessionKey = key;
      await client.handleSessionChanged();
    }

    // Aynı oturumda token refresh: socket kapatılmaz.
  }
}
