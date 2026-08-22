import '../models/auth_tokens.dart';

/// `AuthenticatedApi` ile oturum sahibi arasındaki tek yönlü sınır.
///
/// core katmanı feature katmanını import etmez; oturumu tutan taraf bu
/// arayüzü uygular. Böylece refresh orchestration'ı ile session state
/// arasında circular dependency oluşmaz.
abstract class AuthSessionStore {
  String? get accessToken;

  String? get refreshToken;

  /// Refresh başarılı olduğunda yeni tokenları yazar.
  void applyRefreshedTokens(AuthTokens tokens);

  /// Refresh mümkün değilse oturumu geçersiz kılar (login'e düşer).
  void onSessionInvalid();
}
