/// Backend 2'nin `GET /api/v1/cameras` yanıtındaki kamera kaydı.
///
/// Mobil uygulama cameraId üretmez; yayın yapılacak kamera bu listeden seçilir.
class CameraOption {
  final String id;
  final String name;
  final String? code;
  final String? departmentName;
  final bool active;

  /// ONLINE, WEAK veya OFFLINE.
  final String? connectionStatus;

  const CameraOption({
    required this.id,
    required this.name,
    this.code,
    this.departmentName,
    required this.active,
    this.connectionStatus,
  });

  factory CameraOption.fromJson(Map<String, dynamic> json) {
    return CameraOption(
      id: json['id'] as String,
      name: (json['name'] as String?) ?? 'İsimsiz kamera',
      code: json['code'] as String?,
      departmentName: json['departmentName'] as String?,
      active: (json['active'] as bool?) ?? false,
      // Backend canonical alanı `status`. Streaming modeli yerel olarak
      // `connectionStatus` tutar; AppShell → Kamera Yayını bu mapping'e bağlıdır.
      connectionStatus:
          (json['status'] as String?) ?? json['connectionStatus'] as String?,
    );
  }

  /// Pasif kameralar için oturum açılamaz; backend `openSession` çağrısını
  /// reddeder.
  bool get isSelectable => active;

  String get subtitle {
    final parts = <String>[
      if (code != null && code!.isNotEmpty) code!,
      if (departmentName != null && departmentName!.isNotEmpty)
        departmentName!,
      if (!active) 'Pasif',
    ];

    return parts.join(' • ');
  }
}
