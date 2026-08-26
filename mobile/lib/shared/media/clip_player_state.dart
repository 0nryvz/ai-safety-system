import '../../core/error/api_failure.dart';

/// Clip player'ın test edilebilir durumları.
///
/// 401 session invalid mevcut [AuthenticatedApi] pipeline'ına bırakılır;
/// player logout çağırmaz. 404, `ApiFailureKind.unknown` olsa bile
/// `statusCode == 404` ile [ClipPlayerNotFound] olur.
sealed class ClipPlayerState {
  const ClipPlayerState();
}

class ClipPlayerLoading extends ClipPlayerState {
  const ClipPlayerLoading();
}

class ClipPlayerReady extends ClipPlayerState {
  const ClipPlayerReady();
}

class ClipPlayerNotReady extends ClipPlayerState {
  static const String label = 'Klip hazırlanıyor';

  const ClipPlayerNotReady();

  String get message => label;
}

class ClipPlayerForbidden extends ClipPlayerState {
  static const String label = 'Bu klipe erişim yetkiniz yok.';

  const ClipPlayerForbidden();

  String get message => label;
}

class ClipPlayerNotFound extends ClipPlayerState {
  static const String label = 'Klip bulunamadı.';

  const ClipPlayerNotFound();

  String get message => label;
}

class ClipPlayerUnauthorized extends ClipPlayerState {
  const ClipPlayerUnauthorized();
}

class ClipPlayerNetworkError extends ClipPlayerState {
  final String message;

  const ClipPlayerNetworkError([
    this.message = 'Sunucuya ulaşılamıyor. Bağlantınızı kontrol edin.',
  ]);
}

class ClipPlayerPlaybackError extends ClipPlayerState {
  static const String label = 'Klip oynatılamadı.';
  static const String recordingFailedLabel = 'Kayıt oluşturulamadı.';

  final String message;
  final bool fromRecordingStatusHint;

  const ClipPlayerPlaybackError({
    this.message = label,
    this.fromRecordingStatusHint = false,
  });
}

/// HTTP / [ApiFailure] → player state. `ApiFailure` enum'u değiştirilmez.
ClipPlayerState clipPlayerStateFromFailure(ApiFailure failure) {
  if (failure.statusCode == 404) {
    return const ClipPlayerNotFound();
  }

  return switch (failure.kind) {
    ApiFailureKind.conflict => const ClipPlayerNotReady(),
    ApiFailureKind.forbidden => const ClipPlayerForbidden(),
    ApiFailureKind.unauthenticated => const ClipPlayerUnauthorized(),
    ApiFailureKind.network => ClipPlayerNetworkError(failure.message),
    ApiFailureKind.server ||
    ApiFailureKind.validation ||
    ApiFailureKind.unknown =>
      ClipPlayerPlaybackError(message: failure.message),
  };
}
