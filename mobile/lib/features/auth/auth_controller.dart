import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/error/api_failure.dart';
import '../../core/models/auth_tokens.dart';
import '../../core/models/user_summary.dart';
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

class AuthController extends StateNotifier<AuthSession> {
  final BackendClient _client;

  AuthController(this._client) : super(const AuthSession.unauthenticated());

  /// Login + `/users/me`. Sahte user üretilmez; me yoksa session kurulmaz.
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

    final UserSummary user;
    try {
      user = await _client.fetchCurrentUser(tokens.accessToken);
    } on ApiFailure {
      rethrow;
    } catch (_) {
      throw const ApiFailure(
        kind: ApiFailureKind.unknown,
        message: 'Kullanıcı bilgisi alınamadı.',
      );
    }

    state = AuthSession(
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      currentUser: user,
    );
  }

  void clearSession() {
    state = const AuthSession.unauthenticated();
  }
}
