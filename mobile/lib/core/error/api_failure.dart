/// Operasyon REST çağrıları için merkezi hata sınırı.
enum ApiFailureKind {
  unauthenticated,
  forbidden,
  validation,
  network,
  server,
  conflict,
  unknown,
}

class ApiFailure implements Exception {
  final ApiFailureKind kind;
  final String message;
  final int? statusCode;

  const ApiFailure({
    required this.kind,
    required this.message,
    this.statusCode,
  });

  factory ApiFailure.fromStatusCode(
    int statusCode, {
    String? detail,
  }) {
    final kind = switch (statusCode) {
      401 => ApiFailureKind.unauthenticated,
      403 => ApiFailureKind.forbidden,
      409 => ApiFailureKind.conflict,
      400 || 422 => ApiFailureKind.validation,
      >= 500 => ApiFailureKind.server,
      _ => ApiFailureKind.unknown,
    };

    final message = detail ??
        switch (kind) {
          ApiFailureKind.unauthenticated => 'Oturum geçersiz. Tekrar giriş yapın.',
          ApiFailureKind.forbidden => 'Bu işlem için yetkiniz yok.',
          ApiFailureKind.validation => 'İstek doğrulanamadı.',
          ApiFailureKind.conflict => 'Çakışma oluştu. Güncel veriyi yenileyin.',
          ApiFailureKind.server => 'Sunucu hatası.',
          ApiFailureKind.network => 'Ağ hatası.',
          ApiFailureKind.unknown => 'Beklenmeyen hata ($statusCode).',
        };

    return ApiFailure(
      kind: kind,
      message: message,
      statusCode: statusCode,
    );
  }

  static const ApiFailure network = ApiFailure(
    kind: ApiFailureKind.network,
    message: 'Sunucuya ulaşılamıyor. Bağlantınızı kontrol edin.',
  );

  @override
  String toString() =>
      'ApiFailure(${kind.name}, status: $statusCode, message: $message)';
}
