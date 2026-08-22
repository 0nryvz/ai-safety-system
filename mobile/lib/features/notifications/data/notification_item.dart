/// Bir ihlal için birleşik realtime durumu.
///
/// Aynı `violationId` için alert ve sonraki update'ler tek item üzerinde
/// güncellenir; her event için yeni kart üretilmez.
class NotificationItem {
  final String violationId;
  final String? type;
  final String? cameraName;
  final String? departmentName;
  final DateTime? startedAt;
  final double? confidence;
  final String lifecycleStatus;
  final String recordingStatus;
  final bool clipReady;
  final bool coverImageReady;
  final String? errorCode;

  /// Item'a uygulanan son event'in efektif zamanı (alert `startedAt`,
  /// update `updatedAt`). Stale event'i reddetmek için kullanılır.
  final DateTime lastEventAt;

  const NotificationItem({
    required this.violationId,
    required this.lifecycleStatus,
    required this.recordingStatus,
    required this.clipReady,
    required this.coverImageReady,
    required this.lastEventAt,
    this.type,
    this.cameraName,
    this.departmentName,
    this.startedAt,
    this.confidence,
    this.errorCode,
  });

  NotificationItem copyWith({
    String? type,
    String? cameraName,
    String? departmentName,
    DateTime? startedAt,
    double? confidence,
    String? lifecycleStatus,
    String? recordingStatus,
    bool? clipReady,
    bool? coverImageReady,
    String? errorCode,
    DateTime? lastEventAt,
  }) {
    return NotificationItem(
      violationId: violationId,
      type: type ?? this.type,
      cameraName: cameraName ?? this.cameraName,
      departmentName: departmentName ?? this.departmentName,
      startedAt: startedAt ?? this.startedAt,
      confidence: confidence ?? this.confidence,
      lifecycleStatus: lifecycleStatus ?? this.lifecycleStatus,
      recordingStatus: recordingStatus ?? this.recordingStatus,
      clipReady: clipReady ?? this.clipReady,
      coverImageReady: coverImageReady ?? this.coverImageReady,
      errorCode: errorCode ?? this.errorCode,
      lastEventAt: lastEventAt ?? this.lastEventAt,
    );
  }
}
