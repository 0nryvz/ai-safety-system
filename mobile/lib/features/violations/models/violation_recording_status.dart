/// Backend recordingStatus string — `REQUESTED|RECORDING|PROCESSING|READY|ERROR`.
enum ViolationRecordingStatus {
  requested,
  recording,
  processing,
  ready,
  error,
  unknown;

  static const List<ViolationRecordingStatus> canonical = [
    ViolationRecordingStatus.requested,
    ViolationRecordingStatus.recording,
    ViolationRecordingStatus.processing,
    ViolationRecordingStatus.ready,
    ViolationRecordingStatus.error,
  ];

  static ViolationRecordingStatus fromJson(String? raw) {
    switch (raw?.toUpperCase()) {
      case 'REQUESTED':
      case 'PENDING':
        return ViolationRecordingStatus.requested;
      case 'RECORDING':
        return ViolationRecordingStatus.recording;
      case 'PROCESSING':
        return ViolationRecordingStatus.processing;
      case 'READY':
        return ViolationRecordingStatus.ready;
      case 'ERROR':
        return ViolationRecordingStatus.error;
      default:
        return ViolationRecordingStatus.unknown;
    }
  }

  /// Filtre query'sine giden canonical değer. PENDING gönderilmez.
  String get wireValue => switch (this) {
        ViolationRecordingStatus.requested => 'REQUESTED',
        ViolationRecordingStatus.recording => 'RECORDING',
        ViolationRecordingStatus.processing => 'PROCESSING',
        ViolationRecordingStatus.ready => 'READY',
        ViolationRecordingStatus.error => 'ERROR',
        ViolationRecordingStatus.unknown => 'UNKNOWN',
      };
}
