class RecentViolationItem {
  final String violationId;
  final DateTime? detectedAt;
  final DateTime? startedAt;
  final String? violationType;
  final String? cameraId;
  final String? departmentId;
  final String? departmentName;
  final String? cameraName;
  final String? cameraCode;
  final String? lifecycleStatus;
  final String? reviewStatus;
  final String? recordingStatus;
  final DateTime? recordingReadyAt;
  final double? confidence;
  final String? modelVersion;

  const RecentViolationItem({
    required this.violationId,
    required this.detectedAt,
    required this.startedAt,
    required this.violationType,
    required this.cameraId,
    required this.departmentId,
    required this.departmentName,
    required this.cameraName,
    required this.cameraCode,
    required this.lifecycleStatus,
    required this.reviewStatus,
    required this.recordingStatus,
    required this.recordingReadyAt,
    required this.confidence,
    required this.modelVersion,
  });

  factory RecentViolationItem.fromJson(Map<String, dynamic> json) {
    return RecentViolationItem(
      violationId: (json['violationId'] as String?) ?? '',
      detectedAt: _parseInstant(json['detectedAt']),
      startedAt: _parseInstant(json['startedAt']),
      violationType: json['violationType'] as String?,
      cameraId: json['cameraId'] as String?,
      departmentId: json['departmentId'] as String?,
      departmentName: json['departmentName'] as String?,
      cameraName: json['cameraName'] as String?,
      cameraCode: json['cameraCode'] as String?,
      lifecycleStatus: json['lifecycleStatus'] as String?,
      reviewStatus: json['reviewStatus'] as String?,
      recordingStatus: _asString(json['recordingStatus']),
      recordingReadyAt: _parseInstant(json['recordingReadyAt']),
      confidence: _asDouble(json['confidence']),
      modelVersion: json['modelVersion'] as String?,
    );
  }

  static DateTime? _parseInstant(Object? value) {
    if (value is! String || value.isEmpty) {
      return null;
    }
    return DateTime.tryParse(value)?.toLocal();
  }

  static String? _asString(Object? value) {
    if (value == null) {
      return null;
    }
    return value.toString();
  }

  static double? _asDouble(Object? value) {
    if (value is double) {
      return value;
    }
    if (value is num) {
      return value.toDouble();
    }
    return null;
  }
}
