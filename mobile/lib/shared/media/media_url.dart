/// Backend `MediaUrlResponse` — yalnız `url` ve `expiresAt`.
///
/// `objectKey`, `coverImageKey`, `playbackUrl` bu modele girmez.
class MediaUrl {
  final String url;
  final DateTime expiresAt;

  const MediaUrl({
    required this.url,
    required this.expiresAt,
  });

  factory MediaUrl.fromJson(Map<String, dynamic> json) {
    final url = json['url'];
    if (url is! String || url.isEmpty) {
      throw const FormatException('url missing');
    }

    final raw = json['expiresAt'];
    final expiresAt = raw is String ? DateTime.tryParse(raw) : null;
    if (expiresAt == null) {
      throw const FormatException('expiresAt missing');
    }

    return MediaUrl(url: url, expiresAt: expiresAt.toUtc());
  }

  bool isExpiredOrNear(DateTime now, Duration skew) {
    return !now.toUtc().isBefore(expiresAt.subtract(skew));
  }

  /// Presigned query imzası loga sızmasın.
  @override
  String toString() => 'MediaUrl(expiresAt: ${expiresAt.toIso8601String()})';
}
