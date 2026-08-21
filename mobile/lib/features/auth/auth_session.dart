import '../../core/models/user_summary.dart';

/// Bellek içi operasyon oturumu (MVP: disk yok).
class AuthSession {
  final String? accessToken;
  final String? refreshToken;
  final UserSummary? currentUser;

  const AuthSession({
    this.accessToken,
    this.refreshToken,
    this.currentUser,
  });

  const AuthSession.unauthenticated()
      : accessToken = null,
        refreshToken = null,
        currentUser = null;

  bool get authenticated =>
      accessToken != null &&
      accessToken!.isNotEmpty &&
      currentUser != null;

  Set<String> get roles => currentUser?.roles ?? const {};

  Set<String> get departmentIds => currentUser?.departmentIds ?? const {};

  bool get isAdmin => roles.contains('ADMIN');

  /// UI görünürlüğü yetkinin yerine geçmez; backend kararı esastır.
  bool get canManageCameras => isAdmin;

  bool get canManageUsers => isAdmin;

  AuthSession copyWith({
    String? accessToken,
    String? refreshToken,
    UserSummary? currentUser,
  }) {
    return AuthSession(
      accessToken: accessToken ?? this.accessToken,
      refreshToken: refreshToken ?? this.refreshToken,
      currentUser: currentUser ?? this.currentUser,
    );
  }
}
