/// Realtime katmanının oturumu okuduğu tek yönlü sınır.
///
/// core katmanı `features/auth` import etmez; oturum sahibi tarafta bir adapter
/// bu arayüzü uygular. Realtime ikinci bir auth/session mekanizması kurmaz.
abstract class RealtimeSessionSource {
  bool get isAuthenticated;

  /// CONNECT frame'inde kullanılacak güncel access token.
  String? get accessToken;

  /// Login session kimliği (kullanıcı bazlı). Token refresh bu değeri
  /// değiştirmez; yalnız farklı oturum socketi kapatır.
  String? get sessionKey;
}
