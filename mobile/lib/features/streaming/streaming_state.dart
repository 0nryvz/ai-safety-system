import '../camera/camera_permission_service.dart';

/// Sözleşmede tanımlı bağlantı durumları. Flutter'ın kendi `ConnectionState`
/// tipiyle karışmaması için ayrı adlandırıldı.
enum StreamConnectionState {
  connecting,
  connected,
  weak,
  reconnecting,
  offline,
  stopped;

  String get label => switch (this) {
        StreamConnectionState.connecting => 'Bağlanıyor...',
        StreamConnectionState.connected => 'Gateway\'e bağlı',
        StreamConnectionState.weak => 'Bağlantı zayıf',
        StreamConnectionState.reconnecting => 'Yeniden bağlanıyor...',
        StreamConnectionState.offline => 'Çevrimdışı',
        StreamConnectionState.stopped => 'Aktarım durduruldu',
      };
}

/// UI'ın tükettiği tek durum kaynağı. Widget'lar kendi bağlantı boolean'larını
/// tutmaz; hepsi buradan okur.
class StreamingState {
  final StreamConnectionState connection;
  final CameraPermissionStatus permission;

  final bool isCameraReady;
  final bool isStreaming;

  /// Geçiş anlarında butonları çift tıklamaya karşı kilitler.
  final bool isBusy;

  final String? errorMessage;

  /// Kalıcı ret durumunda kullanıcıya ayarlara gitme aksiyonu sunulur.
  final bool canOpenSettings;

  final int selectedCameraIndex;
  final int availableCameraCount;

  /// Telefonun ön/arka kamerası (cihaz donanımı). Fabrika kamera kaydından ayrı.
  final String phoneLensLabel;

  /// Backend'den seçilmiş veya derleme ile provizyonlanmış fabrika kamerası.
  final bool isCameraAssigned;

  final String? cameraId;
  final String? cameraName;
  final String? cameraCode;
  final String? departmentName;
  final String? sessionId;

  final int cameraFps;
  final int sendFps;

  final int reconnectAttempt;
  final int maxReconnectAttempts;

  final int sentFrames;
  final int failedFrames;
  final int droppedFrames;
  final DateTime? lastSuccessAt;

  const StreamingState({
    this.connection = StreamConnectionState.stopped,
    this.permission = CameraPermissionStatus.unknown,
    this.isCameraReady = false,
    this.isStreaming = false,
    this.isBusy = false,
    this.errorMessage,
    this.canOpenSettings = false,
    this.selectedCameraIndex = 0,
    this.availableCameraCount = 0,
    this.phoneLensLabel = '—',
    this.isCameraAssigned = false,
    this.cameraId,
    this.cameraName,
    this.cameraCode,
    this.departmentName,
    this.sessionId,
    this.cameraFps = 0,
    this.sendFps = 0,
    this.reconnectAttempt = 0,
    this.maxReconnectAttempts = 3,
    this.sentFrames = 0,
    this.failedFrames = 0,
    this.droppedFrames = 0,
    this.lastSuccessAt,
  });

  bool get canSwitchPhoneCamera =>
      availableCameraCount > 1 && isCameraReady && !isBusy && !isStreaming;

  bool get canStartStream =>
      isCameraAssigned && isCameraReady && !isBusy && !isStreaming;

  bool get isReconnecting =>
      connection == StreamConnectionState.reconnecting;

  String get displayCameraTitle {
    if (cameraName != null && cameraName!.isNotEmpty) {
      return cameraName!;
    }
    if (isCameraAssigned && cameraId != null) {
      return 'Atanmış kamera';
    }
    return 'Fabrika kamerası seçilmedi';
  }

  String get displayCameraSubtitle {
    final parts = <String>[
      if (cameraCode != null && cameraCode!.isNotEmpty) cameraCode!,
      if (departmentName != null && departmentName!.isNotEmpty)
        departmentName!,
    ];
    return parts.isEmpty ? (cameraId ?? '') : parts.join(' · ');
  }

  StreamingState copyWith({
    StreamConnectionState? connection,
    CameraPermissionStatus? permission,
    bool? isCameraReady,
    bool? isStreaming,
    bool? isBusy,
    String? errorMessage,
    bool clearError = false,
    bool? canOpenSettings,
    int? selectedCameraIndex,
    int? availableCameraCount,
    String? phoneLensLabel,
    bool? isCameraAssigned,
    String? cameraId,
    String? cameraName,
    String? cameraCode,
    String? departmentName,
    bool clearCameraMeta = false,
    String? sessionId,
    bool clearSessionId = false,
    int? cameraFps,
    int? sendFps,
    int? reconnectAttempt,
    int? maxReconnectAttempts,
    int? sentFrames,
    int? failedFrames,
    int? droppedFrames,
    DateTime? lastSuccessAt,
  }) {
    return StreamingState(
      connection: connection ?? this.connection,
      permission: permission ?? this.permission,
      isCameraReady: isCameraReady ?? this.isCameraReady,
      isStreaming: isStreaming ?? this.isStreaming,
      isBusy: isBusy ?? this.isBusy,
      errorMessage: clearError ? null : (errorMessage ?? this.errorMessage),
      canOpenSettings: canOpenSettings ?? this.canOpenSettings,
      selectedCameraIndex: selectedCameraIndex ?? this.selectedCameraIndex,
      availableCameraCount: availableCameraCount ?? this.availableCameraCount,
      phoneLensLabel: phoneLensLabel ?? this.phoneLensLabel,
      isCameraAssigned: isCameraAssigned ?? this.isCameraAssigned,
      cameraId: clearCameraMeta ? null : (cameraId ?? this.cameraId),
      cameraName: clearCameraMeta ? null : (cameraName ?? this.cameraName),
      cameraCode: clearCameraMeta ? null : (cameraCode ?? this.cameraCode),
      departmentName:
          clearCameraMeta ? null : (departmentName ?? this.departmentName),
      sessionId: clearSessionId ? null : (sessionId ?? this.sessionId),
      cameraFps: cameraFps ?? this.cameraFps,
      sendFps: sendFps ?? this.sendFps,
      reconnectAttempt: reconnectAttempt ?? this.reconnectAttempt,
      maxReconnectAttempts: maxReconnectAttempts ?? this.maxReconnectAttempts,
      sentFrames: sentFrames ?? this.sentFrames,
      failedFrames: failedFrames ?? this.failedFrames,
      droppedFrames: droppedFrames ?? this.droppedFrames,
      lastSuccessAt: lastSuccessAt ?? this.lastSuccessAt,
    );
  }
}
