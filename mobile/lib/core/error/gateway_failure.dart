/// Gateway'in döndürdüğü hata kodlarının tek tip karşılığı.
///
/// Servis katmanı `bool` yerine bu modeli döndürür; böylece 401 geçersiz token,
/// 403 pasif kamera ve 409 oturum çakışması kullanıcıya ayrı ayrı anlatılabilir
/// ve hangi hatanın yeniden denenebilir olduğu tek yerden bilinir.
enum GatewayFailureKind {
  network,
  unauthorized,
  cameraInactive,
  sessionConflict,
  sessionNotFound,
  frameTooLarge,
  unsupportedFormat,
  invalidRequest,
  lifecycleUnavailable,
  serverError,
  unknown,
}

class GatewayFailure {
  final GatewayFailureKind kind;

  /// Gateway'in `detail` alanındaki kod. Tanılama için tutulur, kullanıcıya
  /// gösterilmez.
  final String? detail;

  final int? statusCode;

  const GatewayFailure({
    required this.kind,
    this.detail,
    this.statusCode,
  });

  const GatewayFailure.network({this.detail})
      : kind = GatewayFailureKind.network,
        statusCode = null;

  /// Gateway sözleşmesindeki durum kodlarını eşler.
  /// Kaynak: gateway/app/api/routes/sessions.py ve frames.py
  factory GatewayFailure.fromStatusCode(
    int statusCode, {
    String? detail,
  }) {
    final kind = switch (statusCode) {
      401 => GatewayFailureKind.unauthorized,
      403 => GatewayFailureKind.cameraInactive,
      404 => GatewayFailureKind.sessionNotFound,
      409 => GatewayFailureKind.sessionConflict,
      413 => GatewayFailureKind.frameTooLarge,
      415 => GatewayFailureKind.unsupportedFormat,
      422 => GatewayFailureKind.invalidRequest,
      503 => GatewayFailureKind.lifecycleUnavailable,
      _ => statusCode >= 500
          ? GatewayFailureKind.serverError
          : GatewayFailureKind.unknown,
    };

    return GatewayFailure(
      kind: kind,
      detail: detail,
      statusCode: statusCode,
    );
  }

  /// Kullanıcıya gösterilecek metin. Teknik stack trace veya token içermez.
  String get userMessage => switch (kind) {
        GatewayFailureKind.network =>
          'Gateway\'e ulaşılamıyor. Ağ bağlantınızı kontrol edin.',
        GatewayFailureKind.unauthorized =>
          'Kamera oturum anahtarı geçersiz veya süresi dolmuş.',
        GatewayFailureKind.cameraInactive =>
          'Bu kamera pasif durumda. Yönetici panelinden etkinleştirilmeli.',
        GatewayFailureKind.sessionConflict =>
          'Bu kamera için başka bir aktif oturum var. '
              'Diğer cihazdaki yayını durdurup tekrar deneyin.',
        GatewayFailureKind.sessionNotFound =>
          'Oturum Gateway tarafında bulunamadı. Yayın yeniden başlatılıyor.',
        GatewayFailureKind.frameTooLarge =>
          'Gönderilen kare boyut sınırını aşıyor. Çözünürlüğü düşürün.',
        GatewayFailureKind.unsupportedFormat =>
          'Gateway bu görüntü biçimini kabul etmiyor.',
        GatewayFailureKind.invalidRequest =>
          'Gateway isteği reddetti: kare bilgileri geçersiz.',
        GatewayFailureKind.lifecycleUnavailable =>
          'Gateway backend\'e ulaşamadı. Birazdan tekrar denenecek.',
        GatewayFailureKind.serverError =>
          'Gateway şu anda yanıt veremiyor. Birazdan tekrar denenecek.',
        GatewayFailureKind.unknown =>
          'Gateway beklenmeyen bir yanıt döndü.',
      };

  /// Yeniden bağlanma denemesinin anlamlı olup olmadığı. Token, pasif kamera ve
  /// çakışma hatalarında tekrar denemek aynı sonucu verir; kullanıcı aksiyonu
  /// gerekir.
  bool get isRetryable => switch (kind) {
        GatewayFailureKind.network ||
        GatewayFailureKind.sessionNotFound ||
        GatewayFailureKind.lifecycleUnavailable ||
        GatewayFailureKind.serverError ||
        GatewayFailureKind.unknown =>
          true,
        GatewayFailureKind.unauthorized ||
        GatewayFailureKind.cameraInactive ||
        GatewayFailureKind.sessionConflict ||
        GatewayFailureKind.frameTooLarge ||
        GatewayFailureKind.unsupportedFormat ||
        GatewayFailureKind.invalidRequest =>
          false,
      };

  @override
  String toString() =>
      'GatewayFailure(${kind.name}, status: $statusCode, detail: $detail)';
}

/// Başarı ya da tipli hata taşıyan sonuç.
class GatewayResult<T> {
  final T? value;
  final GatewayFailure? failure;

  const GatewayResult.success(this.value) : failure = null;
  const GatewayResult.failed(this.failure) : value = null;

  bool get isSuccess => failure == null;
}
