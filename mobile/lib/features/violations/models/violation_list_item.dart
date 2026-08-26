import 'iso_instant.dart';
import 'violation_lifecycle_status.dart';
import 'violation_recording_status.dart';
import 'violation_review_status.dart';
import 'violation_type.dart';

/// Backend `ViolationListItem`.
class ViolationListItem {
  final String id;
  final String? cameraId;
  final String? departmentId;
  final ViolationType type;
  final DateTime? startedAt;
  final DateTime? endedAt;
  final double? confidence;
  final ViolationLifecycleStatus lifecycleStatus;
  final ViolationReviewStatus reviewStatus;
  final ViolationRecordingStatus recordingStatus;
  final DateTime? updatedAt;

  const ViolationListItem({
    required this.id,
    this.cameraId,
    this.departmentId,
    required this.type,
    this.startedAt,
    this.endedAt,
    this.confidence,
    required this.lifecycleStatus,
    required this.reviewStatus,
    required this.recordingStatus,
    this.updatedAt,
  });

  factory ViolationListItem.fromJson(Map<String, dynamic> json) {
    return ViolationListItem(
      id: (json['violationId'] as String?) ?? '',
      cameraId: json['cameraId'] as String?,
      departmentId: json['departmentId'] as String?,
      type: ViolationType.fromJson(json['type'] as String?),
      startedAt: parseInstant(json['startedAt']),
      endedAt: parseInstant(json['endedAt']),
      confidence: _asDouble(json['confidence']),
      lifecycleStatus:
          ViolationLifecycleStatus.fromJson(json['lifecycleStatus'] as String?),
      reviewStatus:
          ViolationReviewStatus.fromJson(json['reviewStatus'] as String?),
      recordingStatus: ViolationRecordingStatus.fromJson(
        json['recordingStatus']?.toString(),
      ),
      updatedAt: parseInstant(json['updatedAt']),
    );
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
