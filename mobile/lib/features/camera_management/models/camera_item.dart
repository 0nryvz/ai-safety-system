import 'camera_status.dart';

/// Backend `CameraResponse` — yönetim listesi modeli.
class CameraItem {
  final String id;
  final String name;
  final String code;
  final String departmentId;
  final String? departmentName;
  final bool active;
  final CameraStatus status;
  final DateTime? lastSeenAt;
  final String? activeSessionId;

  const CameraItem({
    required this.id,
    required this.name,
    required this.code,
    required this.departmentId,
    this.departmentName,
    required this.active,
    required this.status,
    this.lastSeenAt,
    this.activeSessionId,
  });

  factory CameraItem.fromJson(Map<String, dynamic> json) {
    return CameraItem(
      id: json['id'] as String,
      name: (json['name'] as String?) ?? 'İsimsiz kamera',
      code: (json['code'] as String?) ?? '',
      departmentId: json['departmentId'] as String,
      departmentName: json['departmentName'] as String?,
      active: (json['active'] as bool?) ?? false,
      status: CameraStatus.fromJson(json['status'] as String?),
      lastSeenAt: _parseInstant(json['lastSeenAt']),
      activeSessionId: json['activeSessionId'] as String?,
    );
  }

  static DateTime? _parseInstant(Object? value) {
    if (value == null) {
      return null;
    }
    if (value is String && value.isNotEmpty) {
      return DateTime.tryParse(value)?.toUtc();
    }
    return null;
  }

  String get subtitle {
    final parts = <String>[
      if (code.isNotEmpty) code,
      if (departmentName != null && departmentName!.isNotEmpty)
        departmentName!,
    ];
    return parts.join(' • ');
  }
}
