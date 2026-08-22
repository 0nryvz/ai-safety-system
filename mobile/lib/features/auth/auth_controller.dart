import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/error/api_failure.dart';
import '../../core/models/auth_tokens.dart';
import '../../core/models/user_summary.dart';
import '../../core/network/auth_session_store.dart';
import '../../core/network/authenticated_api.dart';
import '../../core/network/backend_client.dart';
import 'auth_session.dart';

final backendClientProvider = Provider<BackendClient>((ref) {
  final client = BackendClient();
  ref.onDispose(client.close);
  return client;
});

final authSessionProvider =
    StateNotifierProvider<AuthController, AuthSession>((ref) {
  return AuthController(ref.watch(backendClientProvider));
});

/// Operasyon feature'ları authenticated çağrılarını buradan yapar.
final authenticatedApiProvider = Provider<AuthenticatedApi>((ref) {
  return ref.watch(authSessionProvider.notifier).api;
});

/// Oturum state sahibi. Refresh orchestration'ı [AuthenticatedApi] içindedir;
/// bu sınıf yalnız token/user state'ini yazar ve login/logout akışını yürütür.
class AuthController extends StateNotifier<AuthSession>
    implements AuthSessionStore {
  final BackendClient _client;
  late final AuthenticatedApi api;

  AuthController(this._client) : super(const AuthSession.unauthenticated()) {
    api = AuthenticatedApi(_client, this);
  }

  @override
  String? get accessToken => state.accessToken;

  @override
  String? get refreshToken => state.refreshToken;

  @override
  void applyRefreshedTokens(AuthTokens tokens) {
    state = state.copyWith(
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
    );
  }

  @override
  void onSessionInvalid() {
    clearSession();
  }

  /// Login + `/users/me`. Sahte user üretilmez; me başarısızsa session kurulmaz.
  Future<void> signIn({
    required String email,
    required String password,
  }) async {
    final AuthTokens tokens;
    try {
      tokens = await _client.loginTokens(email: email, password: password);
    } on BackendAuthException catch (e) {
      throw ApiFailure(
        kind: e.isUnreachable
            ? ApiFailureKind.network
            : e.kind == BackendAuthFailureKind.invalidCredentials
                ? ApiFailureKind.unauthenticated
                : ApiFailureKind.unknown,
        message: e.message,
      );
    }

    // currentUser çağrısı merkezi pipeline'dan geçsin diye tokenlar önce yazılır;
    // currentUser null olduğu sürece session authenticated sayılmaz.
    state = AuthSession(
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
    );

    final UserSummary user;
    try {
      user = await api.currentUser();
    } on ApiFailure {
      clearSession();
      rethrow;
    } catch (_) {
      clearSession();
      throw const ApiFailure(
        kind: ApiFailureKind.unknown,
        message: 'Kullanıcı bilgisi alınamadı.',
      );
    }

    state = state.copyWith(currentUser: user);
  }

  /// Backend refresh token'ını iptal eder, ardından local session'ı temizler.
  /// Ağ hatasında da local session temizlenir.
  Future<void> signOut() async {
    final token = state.refreshToken;

    try {
      if (token != null && token.isNotEmpty) {
        await _client.logout(token);
      }
    } on ApiFailure {
      // Local çıkış her durumda tamamlanır.
    } finally {
      clearSession();
    }
  }

  void clearSession() {
    state = const AuthSession.unauthenticated();
  }
}
