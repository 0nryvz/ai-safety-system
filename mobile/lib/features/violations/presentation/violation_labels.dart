import '../models/violation_lifecycle_status.dart';
import '../models/violation_recording_status.dart';
import '../models/violation_review_status.dart';
import '../models/violation_type.dart';

String violationTypeLabel(ViolationType type) {
  return switch (type) {
    ViolationType.missingWeldingMask => 'Kaynak maskesi',
    ViolationType.missingGloves => 'Eldiven',
    ViolationType.missingWeldingApron => 'Kaynak önlüğü',
    ViolationType.restrictedZone => 'Yasak alan',
    ViolationType.unprotectedPerson => 'Korumasız kişi',
    ViolationType.unknown => 'Bilinmiyor',
  };
}

String lifecycleStatusLabel(ViolationLifecycleStatus status) {
  return switch (status) {
    ViolationLifecycleStatus.active => 'Aktif',
    ViolationLifecycleStatus.preparing => 'Hazırlanıyor',
    ViolationLifecycleStatus.completed => 'Tamamlandı',
    ViolationLifecycleStatus.error => 'Hata',
    ViolationLifecycleStatus.unknown => 'Bilinmiyor',
  };
}

String reviewStatusLabel(ViolationReviewStatus status) {
  return switch (status) {
    ViolationReviewStatus.unreviewed => 'İncelenmedi',
    ViolationReviewStatus.reviewed => 'İncelendi',
    ViolationReviewStatus.confirmed => 'Onaylandı',
    ViolationReviewStatus.falseAlarm => 'Yanlış alarm',
    ViolationReviewStatus.unknown => 'Bilinmiyor',
  };
}

String recordingStatusLabel(ViolationRecordingStatus status) {
  return switch (status) {
    ViolationRecordingStatus.requested => 'Kayıt bekliyor',
    ViolationRecordingStatus.recording => 'Kaydediliyor',
    ViolationRecordingStatus.processing => 'İşleniyor',
    ViolationRecordingStatus.ready => 'Hazır',
    ViolationRecordingStatus.error => 'Kayıt hatası',
    ViolationRecordingStatus.unknown => 'Bilinmiyor',
  };
}

String formatLocalDateTime(DateTime? value) {
  if (value == null) {
    return '—';
  }
  final local = value.toLocal();
  final d = local.day.toString().padLeft(2, '0');
  final m = local.month.toString().padLeft(2, '0');
  final y = local.year.toString().padLeft(4, '0');
  final hh = local.hour.toString().padLeft(2, '0');
  final mm = local.minute.toString().padLeft(2, '0');
  return '$d.$m.$y $hh:$mm';
}

String formatLocalDate(DateTime value) {
  final local = value.toLocal();
  final d = local.day.toString().padLeft(2, '0');
  final m = local.month.toString().padLeft(2, '0');
  final y = local.year.toString().padLeft(4, '0');
  return '$d.$m.$y';
}

String formatConfidence(double? value) {
  if (value == null) {
    return '—';
  }
  final percent = (value <= 1 ? value * 100 : value).round();
  return '%$percent';
}
