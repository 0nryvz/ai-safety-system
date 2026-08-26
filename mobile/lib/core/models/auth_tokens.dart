/// `POST /api/v1/auth/login` ve `/refresh` yanıtı.
class AuthTokens {
  final String accessToken;
  final String refreshToken;
  final String tokenType;

  const AuthTokens({
    required this.accessToken,
    required this.refreshToken,
    this.tokenType = 'Bearer',
  });

  factory AuthTokens.fromJson(Map<String, dynamic> json) {
    final access = json['accessToken'] as String?;
    final refresh = json['refreshToken'] as String?;

    if (access == null || access.isEmpty) {
      throw const FormatException('accessToken missing');
    }
    if (refresh == null || refresh.isEmpty) {
      throw const FormatException('refreshToken missing');
    }

    return AuthTokens(
      accessToken: access,
      refreshToken: refresh,
      tokenType: (json['tokenType'] as String?) ?? 'Bearer',
    );
  }
}
