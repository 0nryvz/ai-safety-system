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
    defaultValue: 15,
  );

  /// Sert taban: Gateway'in HTTP 202 ile kabul ettiği saniyedeki kare sayısı
  /// bu değerin altına inmemeli. Encode temposu değil, kabul edilen kare ölçülür.
  static const int minFps = int.fromEnvironment(
    'MIN_FPS',
    defaultValue: 5,
  );

  /// Native encoder tamsayı `step` ile alt örnekler:
  /// `step = ceil(sourceWidth / targetEncodeWidth)`, çıktı `sourceWidth / step`.
  ///
  /// 720 hedefi `ResolutionPreset.medium` kaynağında (720x480) step=1 verir,
  /// yani native downsample devre dışı kalır ve kare kaynak çözünürlüğünde
  /// kodlanır. Kaynak daha küçükse step yine 1'dir; upscale yapılmaz.
  ///
  /// 640 vermek step=2'ye düşürüp kareyi 360x240'a çökertir — 720 kaynakta
  /// 640 hedefi native resampler açık çıktı boyutunu desteklemeden çalışmaz.
  static const int targetEncodeWidth = int.fromEnvironment(
    'ENCODE_WIDTH',
    defaultValue: 720,
  );

  /// PPE detayını koruyan alt sınır. 160/128 sınıfına asla dönülmez.
  static const int degradedEncodeWidth = int.fromEnvironment(
    'ENCODE_WIDTH_DEGRADED',
    defaultValue: 480,
  );

  static const int jpegQuality = int.fromEnvironment(
    'JPEG_QUALITY',
    defaultValue: 65,
  );

  static const int degradedJpegQuality = int.fromEnvironment(
    'JPEG_QUALITY_DEGRADED',
    defaultValue: 55,
  );

  /// 15 FPS için 3 slot (encode ~60–80ms varsayımı).
  static const int maxConcurrentEncodes = int.fromEnvironment(
    'MAX_CONCURRENT_ENCODES',
    defaultValue: 3,
  );

  /// Aynı anda açık HTTP upload sayısı. Yüksek paralellik kareleri Gateway'e
  /// sırasız ulaştırır ve zayıf ağda büyüyen backlog'u gizler.
  static const int maxConcurrentHttpUploads = int.fromEnvironment(
    'MAX_CONCURRENT_UPLOADS',
    defaultValue: 3,
  );

  /// Upload kuyruğu tavanı. Doluyken en eski kare düşürülür: canlı yayında eski
  /// kare değersizdir ve sınırsız backlog Gateway'in AI örneklemesini bozar.
  static const int maxQueuedUploads = int.fromEnvironment(
    'MAX_QUEUED_UPLOADS',
    defaultValue: 6,
  );

  /// Metronom — mobil tarafın kendi gönderim temposu.
  static const int pacedFps = int.fromEnvironment(
    'PACED_FPS',
    defaultValue: 15,
  );

  /// CameraX'e verilen AE hedef aralığı (`fps`..`fps`); [pacedFps] ile aynı
  /// sabit olamaz. Pacing'i düşürmek kamera bind'ini etkilememeli, çünkü
  /// camera_android_camerax bu değeri doğrudan ImageAnalysis/Preview use-case'ine
  /// katı bir aralık olarak geçirir ve cihaz desteklemezse bind başarısız olur.
  ///
  /// 0 verilirse CameraX kendi varsayılan aralığını seçer.
  static const int cameraTargetFps = int.fromEnvironment(
    'CAMERA_TARGET_FPS',
    defaultValue: 15,
  );

  static int? get cameraTargetFpsOrNull =>
      cameraTargetFps > 0 ? cameraTargetFps : null;

  static Duration get paceInterval => Duration(
        milliseconds: (1000 / pacedFps).round().clamp(50, 250),
      );

  static Duration get minFrameInterval => paceInterval;

  static Duration get maxFrameIntervalForMinFps => Duration(
        microseconds: 1000000 ~/ minFps,
      );

  // --------------------------------------------------------------
  // Tanılama
  // --------------------------------------------------------------

  /// Kare başına bir satır ve saniyede bir toplu özet loglar (kaynak/kodlanmış
  /// boyut, JPEG bytes, encode/upload süresi, kabul edilen FPS, kuyruk derinliği).
  /// Ham kare verisi, oturum token'ı veya kimlik bilgisi yazmaz. Yalnızca ölçüm
  /// alırken açılır:
  /// --dart-define=FRAME_DIAGNOSTICS=true
  static const bool frameDiagnostics = bool.fromEnvironment(
    'FRAME_DIAGNOSTICS',
  );
}
