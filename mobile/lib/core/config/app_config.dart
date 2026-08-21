/// Derleme zamanı yapılandırması. Endpoint, kimlik ve kodlama parametreleri
/// koda gömülmez; `--dart-define` ile verilir.
///
/// Gateway sözleşmesine göre kamera ve oturum kimlikleri UUID olmak zorunda:
/// `cameras.id` ve `camera_sessions.id` veritabanında uuid tipinde ve
/// `camera_sessions.camera_id` kayıtlı bir kameraya foreign key ile bağlı.
/// Bu yüzden cameraId cihazda rastgele üretilemez; ya Backend 2'den seçilir ya
/// da derleme sırasında provizyonlanır.
///
/// Örnek:
/// ```
/// flutter run \
///   --dart-define=GATEWAY_URL=http://localhost:8000 \
///   --dart-define=BACKEND_URL=http://localhost:8080 \
///   --dart-define=CAMERA_ID=1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed \
///   --dart-define=CAMERA_KEY=... \
///   --dart-define=TARGET_FPS=15
/// ```
class AppConfig {
  const AppConfig._();

  // --------------------------------------------------------------
  // Endpoint'ler
  // --------------------------------------------------------------

  /// Emülatörde host makine 10.0.2.2 ile görünür. Gerçek cihazda
  /// `adb reverse tcp:8000 tcp:8000` ile localhost'a yönlendirilir.
  static const String gatewayBaseUrl = String.fromEnvironment(
    'GATEWAY_URL',
    defaultValue: 'http://10.0.2.2:8000',
  );

  /// Spring Boot backend (Backend 2): kamera listesi ve kimlik doğrulama.
  static const String backendBaseUrl = String.fromEnvironment(
    'BACKEND_URL',
    defaultValue: 'http://10.0.2.2:8080',
  );

  // --------------------------------------------------------------
  // Kamera kimliği
  // --------------------------------------------------------------

  /// Boşsa kullanıcı Backend 2 listesinden kamera seçer; o da yoksa cihazda
  /// kalıcı bir geliştirme UUID'si üretilir.
  static const String provisionedCameraId = String.fromEnvironment(
    'CAMERA_ID',
  );

  static bool get isCameraProvisioned => provisionedCameraId.isNotEmpty;

  /// Gateway `sessionToken` alanı.
  ///
  /// MVP kararı: token Backend 2'ye bağlanmaz, Gateway sabit `dev-session-token`
  /// değerini doğrular. Değer koda gömülmek yerine derleme zamanında verilir ki
  /// ortam başına değiştirilebilsin.
  static const String cameraKey = String.fromEnvironment(
    'CAMERA_KEY',
    defaultValue: 'dev-session-token',
  );

  // --------------------------------------------------------------
  // Kodlama ve gönderim
  // --------------------------------------------------------------

  static const int targetFps = int.fromEnvironment(
    'TARGET_FPS',
    defaultValue: 8,
  );

  /// Gönderim hızının altına düşülmemesi gereken taban.
  /// Ağ/encode baskısında throttle ve drop bu eşiğin altında uygulanmaz.
  static const int minFps = int.fromEnvironment(
    'MIN_FPS',
    defaultValue: 5,
  );

  /// Kareler bu genişliğe indirgenir. Gateway sınırı 2 MiB.
  static const int targetEncodeWidth = int.fromEnvironment(
    'ENCODE_WIDTH',
    defaultValue: 128,
  );

  /// Min-FPS korumasındayken geçici küçültme.
  static const int degradedEncodeWidth = int.fromEnvironment(
    'ENCODE_WIDTH_DEGRADED',
    defaultValue: 96,
  );

  static const int jpegQuality = int.fromEnvironment(
    'JPEG_QUALITY',
    defaultValue: 32,
  );

  static const int degradedJpegQuality = int.fromEnvironment(
    'JPEG_QUALITY_DEGRADED',
    defaultValue: 28,
  );

  /// MethodChannel encode eşzamanlılığı. 8 FPS @ ~125ms için 3 slot payı.
  static const int maxConcurrentEncodes = int.fromEnvironment(
    'MAX_CONCURRENT_ENCODES',
    defaultValue: 3,
  );

  /// HTTP gönderim eşzamanlılığı (encode'dan bağımsız).
  static const int maxConcurrentHttpUploads = int.fromEnvironment(
    'MAX_CONCURRENT_UPLOADS',
    defaultValue: 6,
  );

  /// Geriye dönük isim — encode slot limiti.
  static const int maxConcurrentFrameUploads = maxConcurrentEncodes;

  /// Sabit metronom temposu. 10 agresif; Tecno'da 8 daha kararlı (≥ min 5).
  static const int pacedFps = int.fromEnvironment(
    'PACED_FPS',
    defaultValue: 8,
  );

  static Duration get paceInterval => Duration(
        milliseconds: (1000 / pacedFps).round().clamp(80, 250),
      );

  /// Hedef FPS aralığı (%20 tolerans). Taban altına inildiğinde bu sınır
  /// yok sayılır.
  static Duration get minFrameInterval => paceInterval;

  /// Taban FPS için en uzun kabul edilebilir aralık (~200ms @ 5 FPS).
  static Duration get maxFrameIntervalForMinFps => Duration(
        microseconds: 1000000 ~/ minFps,
      );

  // --------------------------------------------------------------
  // Tanılama
  // --------------------------------------------------------------

  /// Kare başına encode/upload süresi loglar. Saniyede [targetFps] satır
  /// ürettiği için yalnızca ölçüm alırken açılır:
  /// --dart-define=FRAME_DIAGNOSTICS=true
  static const bool frameDiagnostics = bool.fromEnvironment(
    'FRAME_DIAGNOSTICS',
  );
}
