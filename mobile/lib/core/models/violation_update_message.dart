/// Backend `ViolationUpdateMessage` (STOMP `/user/queue/alerts`).
///
/// `errorCode` backendde nullable; diğer alanlar zorunludur.
class ViolationUpdateMessage {
  final String violationId;
  final String lifecycleStatus;
  final String recordingStatus;
  final bool clipReady;
  final DateTime updatedAt;
  final String? errorCode;

  const ViolationUpdateMessage({
    required this.violationId,
    required this.lifecycleStatus,
    required this.recordingStatus,
    required this.clipReady,
    required this.updatedAt,
    this.errorCode,
  });

  factory ViolationUpdateMessage.fromJson(Map<String, dynamic> json) {
    final violationId = json['violationId'];
    final lifecycleStatus = json['lifecycleStatus'];
    final recordingStatus = json['recordingStatus'];
    final clipReady = json['clipReady'];
    final updatedAtRaw = json['updatedAt'];

    if (violationId is! String || violationId.isEmpty) {
      throw const FormatException('violationId missing');
    }
    if (lifecycleStatus is! String || lifecycleStatus.isEmpty) {
      throw const FormatException('lifecycleStatus missing');
    }
    if (recordingStatus is! String || recordingStatus.isEmpty) {
      throw const FormatException('recordingStatus missing');
    }
    if (clipReady is! bool) {
      throw const FormatException('clipReady missing');
    }

    final updatedAt =
        updatedAtRaw is String ? DateTime.tryParse(updatedAtRaw) : null;
    if (updatedAt == null) {
      throw const FormatException('updatedAt missing');
    }

    return ViolationUpdateMessage(
      violationId: violationId,
      lifecycleStatus: lifecycleStatus,
      recordingStatus: recordingStatus,
      clipReady: clipReady,
      updatedAt: updatedAt,
      errorCode: json['errorCode'] as String?,
    );
  }
}
