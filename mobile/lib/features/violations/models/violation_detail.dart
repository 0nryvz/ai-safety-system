import 'iso_instant.dart';
import 'violation_lifecycle_status.dart';
import 'violation_recording_status.dart';
import 'violation_review_status.dart';
import 'violation_type.dart';

/// Backend `ViolationDetailResponse`.
///
/// `playbackUrl` / `coverImageKey` parse edilmez; clip Onur player'ı kullanır.
class ViolationDetail {
  final String id;
  final String? cameraId;
  final String? cameraName;
  final String? cameraCode;
  final String? departmentId;
  final String? departmentName;
  final String? sessionId;
  final ViolationType type;
  final double? confidence;
  final String? modelVersion;
  final DateTime? detectedAt;
  final DateTime? startedAt;
  final DateTime? endedAt;
  final ViolationLifecycleStatus lifecycleStatus;
  final ViolationReviewStatus reviewStatus;
  final String? reviewedBy;
  final DateTime? reviewedAt;
  final ViolationRecordingStatus recordingStatus;
  final bool clipReady;
  final bool coverImageReady;
  final int version;

  const ViolationDetail({
    required this.id,
    this.cameraId,
    this.cameraName,
    this.cameraCode,
    this.departmentId,
    this.departmentName,
    this.sessionId,
    required this.type,
    this.confidence,
    this.modelVersion,
    this.detectedAt,
    this.startedAt,
    this.endedAt,
    required this.lifecycleStatus,
    required this.reviewStatus,
    this.reviewedBy,
    this.reviewedAt,
    required this.recordingStatus,
    required this.clipReady,
    required this.coverImageReady,
    required this.version,
  });

  factory ViolationDetail.fromJson(Map<String, dynamic> json) {
    return ViolationDetail(
      id: (json['violationId'] as String?) ?? '',
      cameraId: json['cameraId'] as String?,
      cameraName: json['cameraName'] as String?,
      cameraCode: json['cameraCode'] as String?,
      departmentId: json['departmentId'] as String?,
      departmentName: json['departmentName'] as String?,
      sessionId: json['sessionId'] as String?,
      type: ViolationType.fromJson(json['type'] as String?),
      confidence: _asDouble(json['confidence']),
      modelVersion: json['modelVersion'] as String?,
      detectedAt: parseInstant(json['detectedAt']),
      startedAt: parseInstant(json['startedAt']),
      endedAt: parseInstant(json['endedAt']),
      lifecycleStatus:
          ViolationLifecycleStatus.fromJson(json['lifecycleStatus'] as String?),
      reviewStatus:
          ViolationReviewStatus.fromJson(json['reviewStatus'] as String?),
      reviewedBy: json['reviewedBy'] as String?,
      reviewedAt: parseInstant(json['reviewedAt']),
      recordingStatus: ViolationRecordingStatus.fromJson(
        json['recordingStatus']?.toString(),
      ),
      clipReady: json['clipReady'] == true,
      coverImageReady: json['coverImageReady'] == true,
      version: _asInt(json['version']) ?? 0,
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

  static int? _asInt(Object? value) {
    if (value is int) {
      return value;
    }
    if (value is num) {
      return value.toInt();
    }
    return int.tryParse(value?.toString() ?? '');
  }
}
