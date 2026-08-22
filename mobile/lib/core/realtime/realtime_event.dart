import '../models/alert_message.dart';
import '../models/violation_update_message.dart';

/// Realtime MESSAGE frame'inin type-safe sonucu.
sealed class RealtimeEvent {
  const RealtimeEvent();

  /// Dedupe anahtarı. Backend sözleşmesinde `eventId`/`version` yoktur; bu
  /// yüzden fingerprint yalnız gerçek DTO alanlarından üretilir.
  String get fingerprint;
}

class RealtimeAlertEvent extends RealtimeEvent {
  final AlertMessage message;

  const RealtimeAlertEvent(this.message);

  @override
  String get fingerprint => [
        message.violationId,
        'alert',
        message.lifecycleStatus,
        message.recordingStatus,
        message.clipReady,
        message.coverImageReady,
        message.startedAt.toUtc().toIso8601String(),
      ].join('|');
}

class RealtimeViolationUpdateEvent extends RealtimeEvent {
  final ViolationUpdateMessage message;

  const RealtimeViolationUpdateEvent(this.message);

  @override
  String get fingerprint => [
        message.violationId,
        'update',
        message.lifecycleStatus,
        message.recordingStatus,
        message.clipReady,
        message.errorCode ?? '',
        message.updatedAt.toUtc().toIso8601String(),
      ].join('|');
}

/// Unknown/malformed frame. Store'a yazılmaz, uygulamayı düşürmez.
class RealtimeParseFailure extends RealtimeEvent {
  final String reason;
  final String? rawBody;

  const RealtimeParseFailure(this.reason, {this.rawBody});

  @override
  String get fingerprint => 'parse-failure|$reason';

  @override
  String toString() => 'RealtimeParseFailure($reason)';
}
