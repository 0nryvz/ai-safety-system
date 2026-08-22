import 'iso_instant.dart';
import 'violation_lifecycle_status.dart';
import 'violation_recording_status.dart';
import 'violation_review_status.dart';
import 'violation_type.dart';

class ViolationFilters {
  final DateTime? from;
  final DateTime? to;
  final ViolationType? type;
  final String? cameraId;
  final String? departmentId;
  final ViolationLifecycleStatus? lifecycleStatus;
  final ViolationReviewStatus? reviewStatus;
  final ViolationRecordingStatus? recordingStatus;

  const ViolationFilters({
    this.from,
    this.to,
    this.type,
    this.cameraId,
    this.departmentId,
    this.lifecycleStatus,
    this.reviewStatus,
    this.recordingStatus,
  });

  static const empty = ViolationFilters();

  bool get isEmpty =>
      from == null &&
      to == null &&
      type == null &&
      (cameraId == null || cameraId!.isEmpty) &&
      (departmentId == null || departmentId!.isEmpty) &&
      lifecycleStatus == null &&
      reviewStatus == null &&
      recordingStatus == null;

  ViolationFilters copyWith({
    DateTime? from,
    DateTime? to,
    ViolationType? type,
    String? cameraId,
    String? departmentId,
    ViolationLifecycleStatus? lifecycleStatus,
    ViolationReviewStatus? reviewStatus,
    ViolationRecordingStatus? recordingStatus,
    bool clearFrom = false,
    bool clearTo = false,
    bool clearType = false,
    bool clearCameraId = false,
    bool clearDepartmentId = false,
    bool clearLifecycle = false,
    bool clearReview = false,
    bool clearRecording = false,
  }) {
    return ViolationFilters(
      from: clearFrom ? null : from ?? this.from,
      to: clearTo ? null : to ?? this.to,
      type: clearType ? null : type ?? this.type,
      cameraId: clearCameraId ? null : cameraId ?? this.cameraId,
      departmentId:
          clearDepartmentId ? null : departmentId ?? this.departmentId,
      lifecycleStatus:
          clearLifecycle ? null : lifecycleStatus ?? this.lifecycleStatus,
      reviewStatus: clearReview ? null : reviewStatus ?? this.reviewStatus,
      recordingStatus:
          clearRecording ? null : recordingStatus ?? this.recordingStatus,
    );
  }

  /// Spring `GET /api/v1/violations` query. `from`/`to` ISO-8601 Instant.
  Map<String, String> toQueryParameters({
    required int page,
    int size = 20,
  }) {
    final query = <String, String>{
      'page': '$page',
      'size': '$size',
      'sort': 'startedAt,desc',
    };

    if (from != null) {
      query['from'] = formatIsoInstant(from!);
    }
    if (to != null) {
      query['to'] = formatIsoInstant(to!);
    }
    if (type != null && type != ViolationType.unknown) {
      query['type'] = type!.wireValue;
    }
    if (cameraId != null && cameraId!.isNotEmpty) {
      query['cameraId'] = cameraId!;
    }
    if (departmentId != null && departmentId!.isNotEmpty) {
      query['departmentId'] = departmentId!;
    }
    if (lifecycleStatus != null &&
        lifecycleStatus != ViolationLifecycleStatus.unknown) {
      query['lifecycleStatus'] = lifecycleStatus!.wireValue;
    }
    if (reviewStatus != null &&
        reviewStatus != ViolationReviewStatus.unknown) {
      query['reviewStatus'] = reviewStatus!.wireValue;
    }
    if (recordingStatus != null &&
        recordingStatus != ViolationRecordingStatus.unknown) {
      query['recordingStatus'] = recordingStatus!.wireValue;
    }

    return query;
  }
}
