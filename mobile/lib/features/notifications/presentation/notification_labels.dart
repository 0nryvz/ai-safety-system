import '../../violations/models/violation_lifecycle_status.dart';
import '../../violations/models/violation_recording_status.dart';
import '../../violations/models/violation_type.dart';
import '../../violations/presentation/violation_labels.dart';
import '../data/notification_item.dart';

String notificationTypeLabel(String? raw) {
  if (raw == null || raw.isEmpty) {
    return 'İhlal';
  }
  return violationTypeLabel(ViolationType.fromJson(raw));
}

String notificationLifecycleLabel(String raw) =>
    lifecycleStatusLabel(ViolationLifecycleStatus.fromJson(raw));

String notificationRecordingLabel(String raw) =>
    recordingStatusLabel(ViolationRecordingStatus.fromJson(raw));

String notificationClipLabel(bool clipReady) =>
    clipReady ? 'Klip hazır' : 'Klip henüz yok';

String notificationConfidenceLabel(double? confidence) =>
    formatConfidence(confidence);

String notificationStartedAtLabel(DateTime? startedAt) =>
    formatLocalDateTime(startedAt);

String notificationCameraLabel(NotificationItem item) =>
    item.cameraName == null || item.cameraName!.isEmpty
        ? 'Kamera yok'
        : item.cameraName!;

String notificationDepartmentLabel(NotificationItem item) =>
    item.departmentName == null || item.departmentName!.isEmpty
        ? 'Departman yok'
        : item.departmentName!;
