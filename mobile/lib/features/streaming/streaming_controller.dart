import 'dart:async';

import 'package:camera/camera.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/config/app_config.dart';
import '../../core/device/camera_identity.dart';
import '../../core/error/gateway_failure.dart';
import '../camera/camera_permission_service.dart';
import '../session/camera_session_service.dart';
import 'camera_frame_service.dart';
import 'stream_metrics.dart';
import 'streaming_state.dart';

/// Cihazda bulunan kameralar. `main()` içinde gerçek listeyle override edilir.
final availableCamerasProvider = Provider<List<CameraDescription>>(
  (ref) => const <CameraDescription>[],
);

final streamingControllerProvider =
    NotifierProvider<StreamingController, StreamingState>(
  StreamingController.new,
);

/// Kamera yaşam döngüsü, Gateway oturumu ve kare aktarımının tek sahibi.
///
/// Widget'lar burada tutulan durumu okur; kendi bağlantı bayraklarını
/// tutmazlar. Böylece farklı widget'ların çelişkili durum göstermesi engellenir.
class StreamingController extends Notifier<StreamingState> {
  late final CameraSessionService _sessionService;
  late final CameraFrameService _frameService;
  late final CameraIdentity _identity;
  late final CameraPermissionService _permissions;
  late final List<CameraDescription> _cameras;

  final StreamMetrics _metrics = StreamMetrics();

  CameraController? _cameraController;

  Timer? _heartbeatTimer;
  Timer? _reconnectTimer;
  Timer? _fpsTimer;

  /// Yayın kuşağı. Durdurma/yeniden bağlanma sonrası eski stream callback'leri
  /// bu sayaç sayesinde geçersizleşir.
  int _streamGeneration = 0;

  int _activeFrameUploads = 0;
  int _activeFrameCallbacks = 0;
  Completer<void>? _frameCallbacksCompleter;

  int _consecutiveFrameFailures = 0;

  bool _manualStop = false;
  bool _isReconnecting = false;
  bool _isAppInBackground = false;
  bool _disposed = false;

  DateTime? _lastAcceptedFrameAt;
  final List<DateTime> _cameraFrameTimestamps = [];
  final List<DateTime> _sentFrameTimestamps = [];
  Future<void>? _stopCleanupFuture;

  CameraController? get cameraController => _cameraController;

  @override
  StreamingState build() {
    _sessionService = CameraSessionService();
    _frameService = CameraFrameService();
    _identity = CameraIdentity();
    _permissions = const CameraPermissionService();
    _cameras = ref.watch(availableCamerasProvider);

    ref.onDispose(_disposeResources);

    return StreamingState(
      availableCameraCount: _cameras.length,
      maxReconnectAttempts: _maxReconnectAttempts,
    );
  }

  static const int _maxReconnectAttempts = 3;

  void _update(StreamingState Function(StreamingState) transform) {
    if (_disposed) {
      return;
    }

    state = transform(state);
  }

  // ------------------------------------------------------------------
  // Başlatma ve izin
  // ------------------------------------------------------------------

  Future<void> initialize() async {
    unawaited(_frameService.warmUp());
    unawaited(loadCameraIdentity());

    final granted = await _ensurePermission();

    if (!granted) {
      return;
    }

    await initializeCamera(_preferredCameraIndex());
  }

  /// İzin verilmemişse ister; kalıcı rette ayarlara yönlendirme aksiyonunu açar.
  Future<bool> _ensurePermission() async {
    var status = await _permissions.check();

    if (status == CameraPermissionStatus.denied) {
      status = await _permissions.request();
    }

    _update(
      (s) => s.copyWith(
        permission: status,
        canOpenSettings: status == CameraPermissionStatus.permanentlyDenied,
      ),
    );

    switch (status) {
      case CameraPermissionStatus.granted:
      case CameraPermissionStatus.unknown:
        return true;

      case CameraPermissionStatus.denied:
        _update(
          (s) => s.copyWith(
            isCameraReady: false,
            errorMessage: 'Yayın yapabilmek için kamera izni gerekiyor.',
          ),
        );
        return false;

      case CameraPermissionStatus.permanentlyDenied:
        _update(
          (s) => s.copyWith(
            isCameraReady: false,
            errorMessage: 'Kamera izni kapalı. '
                'Ayarlar > Uygulama izinleri bölümünden açabilirsiniz.',
          ),
        );
        return false;
    }
  }

  Future<void> requestPermissionAgain() async {
    if (await _ensurePermission()) {
      await initializeCamera(state.selectedCameraIndex);
    }
  }

  Future<bool> openAppSettings() => _permissions.openAppSettings();

  /// Kullanıcı Backend 2 listesinden kamera seçtiğinde çağrılır. Yayın
  /// sırasında kamera değiştirilemez; önce durdurulmalıdır.
  Future<void> selectBackendCamera(String cameraId) async {
    if (state.isStreaming) {
      return;
    }

    await _identity.select(cameraId);

    _update((s) => s.copyWith(cameraId: cameraId, clearError: true));
  }

  /// Uygulama açılışında seçili kamerayı ekranda gösterebilmek için.
  Future<void> loadCameraIdentity() async {
    final cameraId = await _identity.resolve();
    _update((s) => s.copyWith(cameraId: cameraId));
  }

  int _preferredCameraIndex() {
    if (_cameras.isEmpty) {
      return 0;
    }

    final frontIndex = _cameras.indexWhere(
      (camera) => camera.lensDirection == CameraLensDirection.front,
    );

    return frontIndex >= 0 ? frontIndex : 0;
  }

  // ------------------------------------------------------------------
  // Kamera yaşam döngüsü
  // ------------------------------------------------------------------

  Future<void> initializeCamera(int cameraIndex) async {
    if (_cameras.isEmpty) {
      _update(
        (s) => s.copyWith(
          isCameraReady: false,
          errorMessage: 'Cihazda kullanılabilir kamera bulunamadı.',
        ),
      );
      return;
    }

    if (cameraIndex < 0 || cameraIndex >= _cameras.length || state.isBusy) {
      return;
    }

    _update((s) => s.copyWith(isBusy: true));

    try {
      await _disposeCameraController();

      _update(
        (s) => s.copyWith(isCameraReady: false, clearError: true),
      );

      final controller = CameraController(
        _cameras[cameraIndex],
        ResolutionPreset.medium,
        enableAudio: false,
        imageFormatGroup: ImageFormatGroup.yuv420,
      );

      _cameraController = controller;

      await controller.initialize();

      if (_disposed) {
        await controller.dispose();
        return;
      }

      _update(
        (s) => s.copyWith(
          selectedCameraIndex: cameraIndex,
          isCameraReady: true,
          clearError: true,
        ),
      );
    } on CameraException catch (e) {
      _handleCameraException(e);
    } catch (_) {
      _update(
        (s) => s.copyWith(
          isCameraReady: false,
          isStreaming: false,
          connection: StreamConnectionState.offline,
          errorMessage: 'Kamera başlatılırken beklenmeyen bir hata oluştu.',
        ),
      );
    } finally {
      _update((s) => s.copyWith(isBusy: false));
    }
  }

  void _handleCameraException(CameraException e) {
    final (message, permanentlyDenied) = switch (e.code) {
      'CameraAccessDenied' => (
          'Kamera izni reddedildi. İzin verip tekrar deneyin.',
          false,
        ),
      'CameraAccessDeniedWithoutPrompt' => (
          'Kamera izni kapalı. '
              'Ayarlar > Uygulama izinleri bölümünden açabilirsiniz.',
          true,
        ),
      'CameraAccessRestricted' => (
          'Kamera erişimi bu cihazda kısıtlanmış.',
          false,
        ),
      'CameraDisconnected' => ('Kamera bağlantısı kesildi.', false),
      'cameraPermission' => ('Kamera izni verilmedi.', false),
      _ => (
          'Kamera başlatılamadı. Başka bir uygulama kamerayı '
              'kullanıyor olabilir.',
          false,
        ),
    };

    _update(
      (s) => s.copyWith(
        isCameraReady: false,
        isStreaming: false,
        connection: StreamConnectionState.offline,
        errorMessage: message,
        canOpenSettings: permanentlyDenied || s.canOpenSettings,
      ),
    );
  }

  Future<void> _disposeCameraController() async {
    _streamGeneration++;

    final controller = _cameraController;

    if (controller == null) {
      return;
    }

    try {
      if (controller.value.isStreamingImages) {
        await controller.stopImageStream();
      }
    } catch (_) {
      // Stream zaten durmuş olabilir.
    }

    try {
      await controller.dispose();
    } catch (_) {
      // Controller zaten dispose edilmiş olabilir.
    }

    _cameraController = null;

    _update(
      (s) => s.copyWith(isStreaming: false, clearSessionId: true),
    );
  }

  Future<void> switchCamera() async {
    if (!state.canSwitchCamera) {
      return;
    }

    final nextIndex = (state.selectedCameraIndex + 1) % _cameras.length;

    await initializeCamera(nextIndex);
  }

  // ------------------------------------------------------------------
  // Yayın kontrolü
  // ------------------------------------------------------------------

  Future<void> toggleStreaming() async {
    final controller = _cameraController;

    if (controller == null ||
        !controller.value.isInitialized ||
        state.isBusy) {
      return;
    }

    if (state.isStreaming) {
      await stopStreaming();
    } else {
      await startStreaming();
    }
  }

  Future<void> startStreaming({bool automaticReconnect = false}) async {
    final controller = _cameraController;

    if (controller == null ||
        !controller.value.isInitialized ||
        state.isStreaming ||
        state.isBusy ||
        _isAppInBackground) {
      return;
    }

    final pendingStop = _stopCleanupFuture;
    if (pendingStop != null) {
      try {
        await pendingStop.timeout(const Duration(seconds: 2));
      } catch (_) {
        // Temizlik uzarsa yeni yayını bloklamıyoruz.
      }
      _stopCleanupFuture = null;
    }

    if (!automaticReconnect) {
      _manualStop = false;
      _consecutiveFrameFailures = 0;
      _isReconnecting = false;
      _reconnectTimer?.cancel();
      _reconnectTimer = null;
      _metrics.reset();

      _update((s) => s.copyWith(reconnectAttempt: 0));
    }

    _update(
      (s) => s.copyWith(
        isBusy: true,
        connection: automaticReconnect
            ? StreamConnectionState.reconnecting
            : StreamConnectionState.connecting,
      ),
    );

    try {
      final cameraId = await _identity.resolve();

      // Kullanıcı stop-start yaptığında yeni oturum açılır; reconnect de yeni
      // sessionId üretir çünkü Gateway eski oturumu kapatılmış sayar.
      final sessionId = _identity.newSessionId();

      final opened = await _sessionService.openSession(
        cameraId: cameraId,
        sessionId: sessionId,
        sessionToken: AppConfig.cameraKey,
      );

      if (!opened.isSuccess) {
        _handleSessionOpenFailure(opened.failure!, automaticReconnect);
        return;
      }

      _update(
        (s) => s.copyWith(cameraId: cameraId, sessionId: sessionId),
      );

      _startHeartbeat();

      final streamGeneration = _streamGeneration;
      _startFpsMonitoring();

      _update((s) => s.copyWith(isStreaming: true));

      // ÖNEMLİ: callback içinde await edilmemeli. Await, CameraX
      // ImageAnalysis buffer'ını kilitler ve FPS 1-5'e düşer.
      await controller.startImageStream(
        (image) => _onCameraFrame(image, streamGeneration),
      );

      if (_disposed) {
        return;
      }

      _consecutiveFrameFailures = 0;
      _isReconnecting = false;

      _update(
        (s) => s.copyWith(
          isStreaming: true,
          reconnectAttempt: 0,
          connection: StreamConnectionState.connected,
          clearError: true,
        ),
      );
    } on CameraException catch (e) {
      _stopFpsMonitoring();

      _update(
        (s) => s.copyWith(
          isStreaming: false,
          errorMessage: 'Görüntü aktarımı başlatılamadı: '
              '${e.description ?? e.code}',
        ),
      );

      _afterStartFailure(automaticReconnect);
    } catch (_) {
      _stopFpsMonitoring();

      _update(
        (s) => s.copyWith(
          isStreaming: false,
          errorMessage: 'Yayın başlatılırken beklenmeyen bir hata oluştu.',
        ),
      );

      _afterStartFailure(automaticReconnect);
    } finally {
      _update((s) => s.copyWith(isBusy: false));
    }
  }

  void _handleSessionOpenFailure(
    GatewayFailure failure,
    bool automaticReconnect,
  ) {
    _update(
      (s) => s.copyWith(
        isStreaming: false,
        errorMessage: failure.userMessage,
      ),
    );

    // Token, pasif kamera ve çakışma hatalarında tekrar denemek aynı sonucu
    // verir; kullanıcı aksiyonu gerekir.
    if (!failure.isRetryable) {
      _manualStop = true;
      _isReconnecting = false;
      _update(
        (s) => s.copyWith(connection: StreamConnectionState.offline),
      );
      return;
    }

    _afterStartFailure(automaticReconnect);
  }

  void _afterStartFailure(bool automaticReconnect) {
    if (automaticReconnect) {
      _scheduleReconnect();
    } else {
      _update(
        (s) => s.copyWith(connection: StreamConnectionState.offline),
      );
    }
  }

  void _startHeartbeat() {
    _heartbeatTimer?.cancel();

    _heartbeatTimer = Timer.periodic(
      const Duration(seconds: 10),
      (_) async {
        final sessionId = state.sessionId;
        final cameraId = state.cameraId;

        if (sessionId == null ||
            cameraId == null ||
            _manualStop ||
            _isAppInBackground) {
          return;
        }

        final result = await _sessionService.sendHeartbeat(
          cameraId: cameraId,
          sessionId: sessionId,
        );

        if (!result.isSuccess) {
          _handleConnectionFailure(result.failure!);
        }
      },
    );
  }

  // ------------------------------------------------------------------
  // Kare akışı
  // ------------------------------------------------------------------

  void _onCameraFrame(CameraImage image, int streamGeneration) {
    if (_manualStop ||
        !state.isStreaming ||
        _isAppInBackground ||
        _isReconnecting ||
        streamGeneration != _streamGeneration) {
      return;
    }

    final now = DateTime.now();
    _cameraFrameTimestamps.add(now);

    // Sınırlı kuyruk: ağ yavaşsa yeni kareyi düşürüp belleği korur.
    if (_activeFrameUploads >= AppConfig.maxConcurrentFrameUploads) {
      _metrics.recordDropped();
      return;
    }

    final lastAccepted = _lastAcceptedFrameAt;
    if (lastAccepted != null &&
        now.difference(lastAccepted) < AppConfig.minFrameInterval) {
      _metrics.recordDropped();
      return;
    }

    final sessionId = state.sessionId;
    final cameraId = state.cameraId;

    if (sessionId == null || cameraId == null) {
      return;
    }

    // Yalnızca plane kopyası; CameraImage buffer'ı hemen serbest kalır.
    final rawFrame = _frameService.extractFrame(image);

    if (rawFrame == null) {
      return;
    }

    _lastAcceptedFrameAt = now;
    _sentFrameTimestamps.add(now);
    _activeFrameUploads++;
    _activeFrameCallbacks++;

    final frameTimestamp = DateTime.now().toUtc();

    unawaited(
      _uploadFrame(
        cameraId: cameraId,
        sessionId: sessionId,
        frameTimestamp: frameTimestamp,
        frame: rawFrame,
        streamGeneration: streamGeneration,
      ),
    );
  }

  Future<void> _uploadFrame({
    required String cameraId,
    required String sessionId,
    required DateTime frameTimestamp,
    required RawYuvFrame frame,
    required int streamGeneration,
  }) async {
    try {
      final result = await _frameService.encodeAndUpload(
        cameraId: cameraId,
        sessionId: sessionId,
        frameTimestamp: frameTimestamp,
        frame: frame,
      );

      if (streamGeneration != _streamGeneration ||
          state.sessionId != sessionId ||
          _manualStop ||
          !state.isStreaming) {
        return;
      }

      switch (result.outcome) {
        case FrameOutcome.sent:
          _metrics.recordSent();
          _consecutiveFrameFailures = 0;

          if (state.connection != StreamConnectionState.connected &&
              !_isReconnecting) {
            _update(
              (s) => s.copyWith(connection: StreamConnectionState.connected),
            );
          }

        case FrameOutcome.skipped:
          _metrics.recordDropped();

        case FrameOutcome.failed:
          _metrics.recordFailed();
          _handleFrameFailure(result.failure);
      }
    } catch (_) {
      _metrics.recordFailed();

      if (!_manualStop && state.isStreaming) {
        _handleFrameFailure(null);
      }
    } finally {
      _activeFrameUploads--;
      _activeFrameCallbacks--;
      _completeFrameCallbacksIfNeeded();
    }
  }

  void _handleFrameFailure(GatewayFailure? failure) {
    if (_manualStop || _isAppInBackground || _isReconnecting) {
      return;
    }

    // Yeniden denemenin çözmeyeceği hatalarda beklemeden dur.
    if (failure != null && !failure.isRetryable) {
      _manualStop = true;

      _update(
        (s) => s.copyWith(
          connection: StreamConnectionState.offline,
          errorMessage: failure.userMessage,
        ),
      );

      unawaited(stopStreaming());
      return;
    }

    _consecutiveFrameFailures++;

    // Zayıf bağlantı tek bir timeout'tan değil, ardışık hatalardan çıkarılır.
    if (_consecutiveFrameFailures == 1) {
      _update((s) => s.copyWith(connection: StreamConnectionState.weak));
    }

    if (_consecutiveFrameFailures >= 3) {
      _handleConnectionFailure(
        failure ?? const GatewayFailure.network(),
      );
    }
  }

  void _completeFrameCallbacksIfNeeded() {
    if (_activeFrameCallbacks == 0 &&
        _frameCallbacksCompleter != null &&
        !_frameCallbacksCompleter!.isCompleted) {
      _frameCallbacksCompleter!.complete();
    }
  }

  Future<void> _waitForActiveFrameCallbacks() async {
    if (_activeFrameCallbacks == 0) {
      return;
    }

    _frameCallbacksCompleter ??= Completer<void>();

    try {
      await _frameCallbacksCompleter!.future.timeout(
        const Duration(milliseconds: 400),
      );
    } on TimeoutException {
      // Stop akışını bloklamamak için.
    }

    if (_activeFrameCallbacks == 0) {
      _frameCallbacksCompleter = null;
    }
  }

  // ------------------------------------------------------------------
  // FPS göstergesi
  // ------------------------------------------------------------------

  void _startFpsMonitoring() {
    _fpsTimer?.cancel();
    _lastAcceptedFrameAt = null;
    _cameraFrameTimestamps.clear();
    _sentFrameTimestamps.clear();

    _update((s) => s.copyWith(cameraFps: 0, sendFps: 0));

    _fpsTimer = Timer.periodic(
      const Duration(milliseconds: 200),
      (_) => _refreshFps(),
    );
  }

  void _stopFpsMonitoring() {
    _fpsTimer?.cancel();
    _fpsTimer = null;
    _lastAcceptedFrameAt = null;
    _cameraFrameTimestamps.clear();
    _sentFrameTimestamps.clear();

    _update((s) => s.copyWith(cameraFps: 0, sendFps: 0));
  }

  void _refreshFps() {
    if (_disposed || !state.isStreaming) {
      return;
    }

    final now = DateTime.now();
    const window = Duration(seconds: 1);

    _cameraFrameTimestamps.removeWhere(
      (at) => now.difference(at) > window,
    );
    _sentFrameTimestamps.removeWhere(
      (at) => now.difference(at) > window,
    );

    _update(
      (s) => s.copyWith(
        cameraFps: _cameraFrameTimestamps.length,
        sendFps: _sentFrameTimestamps.length,
        sentFrames: _metrics.sentFrames,
        failedFrames: _metrics.failedFrames,
        droppedFrames: _metrics.droppedFrames,
        lastSuccessAt: _metrics.lastSuccessAt,
      ),
    );
  }

  // ------------------------------------------------------------------
  // Yeniden bağlanma
  // ------------------------------------------------------------------

  void _handleConnectionFailure(GatewayFailure failure) {
    if (_manualStop || _isAppInBackground || _isReconnecting) {
      return;
    }

    _update((s) => s.copyWith(errorMessage: failure.userMessage));

    if (!failure.isRetryable) {
      _manualStop = true;
      _update(
        (s) => s.copyWith(connection: StreamConnectionState.offline),
      );
      unawaited(stopStreaming());
      return;
    }

    _scheduleReconnect();
  }

  void _scheduleReconnect() {
    if (_manualStop || _isAppInBackground || _isReconnecting) {
      return;
    }

    if (state.reconnectAttempt >= _maxReconnectAttempts) {
      _isReconnecting = false;

      _update(
        (s) => s.copyWith(
          isStreaming: false,
          connection: StreamConnectionState.offline,
          errorMessage: 'Bağlantı kurulamadı. Lütfen tekrar deneyin.',
        ),
      );

      return;
    }

    _isReconnecting = true;

    final attempt = state.reconnectAttempt;

    _update(
      (s) => s.copyWith(
        connection: StreamConnectionState.reconnecting,
        reconnectAttempt: attempt + 1,
      ),
    );

    _reconnectTimer?.cancel();

    // Sınırlı exponential backoff: 1s, 2s, 4s.
    final delay = Duration(seconds: (1 << attempt).clamp(1, 8));

    _reconnectTimer = Timer(delay, _performReconnect);
  }

  Future<void> _performReconnect() async {
    if (_manualStop || _isAppInBackground) {
      _isReconnecting = false;
      return;
    }

    _reconnectTimer = null;

    final controller = _cameraController;

    if (controller == null || !controller.value.isInitialized) {
      _isReconnecting = false;
      _update(
        (s) => s.copyWith(connection: StreamConnectionState.offline),
      );
      return;
    }

    _update((s) => s.copyWith(isBusy: true));

    try {
      final oldSessionId = state.sessionId;
      final oldCameraId = state.cameraId;

      _streamGeneration++;

      _update(
        (s) => s.copyWith(isStreaming: false, clearSessionId: true),
      );

      _frameService.cancelUploads();

      _heartbeatTimer?.cancel();
      _heartbeatTimer = null;

      try {
        if (controller.value.isStreamingImages) {
          await controller.stopImageStream();
        }
      } catch (_) {
        // Stream zaten durmuş olabilir.
      }

      await _waitForActiveFrameCallbacks();
      await _frameService.waitForPendingUploads();

      if (oldSessionId != null && oldCameraId != null) {
        await _sessionService.closeSession(
          cameraId: oldCameraId,
          sessionId: oldSessionId,
        );
      }

      _consecutiveFrameFailures = 0;
      _stopFpsMonitoring();
    } finally {
      _update((s) => s.copyWith(isBusy: false));
    }

    _isReconnecting = false;

    if (_manualStop || _isAppInBackground) {
      _update(
        (s) => s.copyWith(connection: StreamConnectionState.stopped),
      );
      return;
    }

    await startStreaming(automaticReconnect: true);
  }

  // ------------------------------------------------------------------
  // Durdurma
  // ------------------------------------------------------------------

  Future<void> stopStreaming({bool fromLifecycle = false}) async {
    final controller = _cameraController;

    if (controller == null) {
      return;
    }

    // Kullanıcı kararı: manuel stop sonrası otomatik reconnect yapılmaz.
    if (!fromLifecycle) {
      _manualStop = true;
    }

    _isReconnecting = false;
    _reconnectTimer?.cancel();
    _reconnectTimer = null;

    final sessionId = state.sessionId;
    final cameraId = state.cameraId;

    _streamGeneration++;
    _frameService.cancelUploads();
    _stopFpsMonitoring();

    _heartbeatTimer?.cancel();
    _heartbeatTimer = null;

    // UI hemen tekrar tıklanabilir olsun; ağır temizlik arka planda sürer.
    _update(
      (s) => s.copyWith(
        isStreaming: false,
        isBusy: false,
        clearSessionId: true,
        connection: fromLifecycle
            ? s.connection
            : StreamConnectionState.stopped,
        errorMessage: fromLifecycle ? s.errorMessage : null,
        clearError: !fromLifecycle,
      ),
    );

    _stopCleanupFuture = _cleanupAfterStop(
      controller: controller,
      sessionId: sessionId,
      cameraId: cameraId,
    );

    unawaited(_stopCleanupFuture!);
  }

  Future<void> _cleanupAfterStop({
    required CameraController controller,
    required String? sessionId,
    required String? cameraId,
  }) async {
    try {
      if (controller.value.isStreamingImages) {
        await controller.stopImageStream().timeout(
              const Duration(seconds: 2),
            );
      }
    } catch (_) {
      // Stream zaten durmuş veya timeout olmuş olabilir.
    }

    try {
      await _waitForActiveFrameCallbacks();
      await _frameService.waitForPendingUploads();
    } catch (_) {
      // Bekleme başarısız olsa da close çağrısı yapılmalı.
    }

    if (sessionId != null && cameraId != null) {
      await _sessionService.closeSession(
        cameraId: cameraId,
        sessionId: sessionId,
      );
    }
  }

  // ------------------------------------------------------------------
  // Uygulama yaşam döngüsü
  // ------------------------------------------------------------------

  /// Arka planda sessizce yayın sürdürmek platform politikalarına aykırı;
  /// aktarım ve kamera kontrollü biçimde durdurulur.
  void handleAppPaused() {
    _isAppInBackground = true;

    if (state.isStreaming) {
      unawaited(stopStreaming(fromLifecycle: true));
    }

    unawaited(_disposeCameraController());

    _update(
      (s) => s.copyWith(
        isCameraReady: false,
        isStreaming: false,
        connection: StreamConnectionState.stopped,
      ),
    );
  }

  /// Ön plana dönünce eski oturum sessizce yeniden kullanılmaz; kamera
  /// baştan hazırlanır. Böylece çift oturum açılmaz.
  void handleAppResumed() {
    _isAppInBackground = false;
    unawaited(initializeCamera(state.selectedCameraIndex));
  }

  // ------------------------------------------------------------------
  // Temizlik
  // ------------------------------------------------------------------

  void _disposeResources() {
    _disposed = true;
    _manualStop = true;
    _isReconnecting = false;

    _heartbeatTimer?.cancel();
    _heartbeatTimer = null;

    _reconnectTimer?.cancel();
    _reconnectTimer = null;

    _fpsTimer?.cancel();
    _fpsTimer = null;

    _frameService.cancelUploads();
    _frameService.dispose();

    final controller = _cameraController;
    _cameraController = null;

    if (controller != null) {
      unawaited(controller.dispose());
    }
  }
}
