import 'dart:async';

import 'package:camera/camera.dart';
import 'package:flutter/material.dart';

import 'features/frame/camera_frame_service.dart';
import 'features/session/camera_session_service.dart';

late List<CameraDescription> cameras;

enum ConnectionState {
  connecting,
  connected,
  weak,
  reconnecting,
  offline,
  stopped,
}

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  try {
    cameras = await availableCameras();
  } catch (_) {
    cameras = [];
  }

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Camera Stream',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: Colors.blue,
        ),
        useMaterial3: true,
      ),
      home: const CameraPage(),
    );
  }
}

class CameraPage extends StatefulWidget {
  const CameraPage({super.key});

  @override
  State<CameraPage> createState() => _CameraPageState();
}

class _CameraPageState extends State<CameraPage>
    with WidgetsBindingObserver {
  final CameraSessionService _sessionService =
      CameraSessionService();

  final CameraFrameService _frameService =
      CameraFrameService();

  CameraController? _controller;

  String? _sessionId;

  bool _isCameraReady = false;
  bool _isStreaming = false;
  bool _isBusy = false;
  bool _isUploadingFrame = false;

  ConnectionState _connectionState =
      ConnectionState.stopped;

  Timer? _heartbeatTimer;
  Timer? _reconnectTimer;

  int _streamGeneration = 0;
  int _activeFrameCallbacks = 0;
  Completer<void>? _frameCallbacksCompleter;

  int _reconnectAttempt = 0;
  int _consecutiveFrameFailures = 0;

  bool _manualStop = false;
  bool _isReconnecting = false;
  bool _isAppInBackground = false;

  static const int _maxReconnectAttempts = 3;

  int _selectedCameraIndex = 0;

  String? _errorMessage;

  // ------------------------------------------------------------
  // CONNECTION STATE
  // ------------------------------------------------------------

  void _setConnectionState(ConnectionState state) {
    if (!mounted) {
      return;
    }

    setState(() {
      _connectionState = state;
    });
  }

  // ------------------------------------------------------------
  // INIT
  // ------------------------------------------------------------
    @override
  void initState() {
    super.initState();

    WidgetsBinding.instance.addObserver(this);

    _initializeCamera();
  }

  // ------------------------------------------------------------
  // CAMERA INITIALIZATION
  // ------------------------------------------------------------

  Future<void> _initializeCamera([
    int cameraIndex = 0,
  ]) async {
    if (cameras.isEmpty) {
      if (!mounted) {
        return;
      }

      setState(() {
        _isCameraReady = false;
        _errorMessage =
            'Cihazda kullanılabilir kamera bulunamadı.';
      });

      return;
    }

    if (cameraIndex < 0 ||
        cameraIndex >= cameras.length) {
      return;
    }

    if (_isBusy) {
      return;
    }

    _isBusy = true;

    try {
      await _disposeCameraController();

      if (!mounted) {
        return;
      }

      setState(() {
        _isCameraReady = false;
        _errorMessage = null;
      });

      final controller = CameraController(
        cameras[cameraIndex],
        ResolutionPreset.medium,
        enableAudio: false,
      );

      _controller = controller;

      await controller.initialize();

      if (!mounted) {
        await controller.dispose();
        return;
      }

      setState(() {
        _selectedCameraIndex = cameraIndex;
        _isCameraReady = true;
        _errorMessage = null;
      });
    } on CameraException catch (e) {
      if (!mounted) {
        return;
      }

      String message;

      switch (e.code) {
        case 'CameraAccessDenied':
          message =
              'Kamera izni reddedildi. Kamera iznini vererek tekrar deneyin.';
          break;

        case 'CameraAccessDeniedWithoutPrompt':
          message =
              'Kamera izni verilmemiş. Cihaz ayarlarından kamera iznini açın.';
          break;

        case 'CameraAccessRestricted':
          message =
              'Kamera erişimi bu cihazda kısıtlanmış.';
          break;

        case 'CameraDisconnected':
          message =
              'Kamera bağlantısı kesildi.';
          break;

        default:
          message =
              'Kamera başlatılamadı: '
              '${e.description ?? e.code}';
      }

      setState(() {
        _isCameraReady = false;
        _isStreaming = false;
        _errorMessage = message;
      });

      _setConnectionState(ConnectionState.offline);
    } catch (_) {
      if (!mounted) {
        return;
      }

      setState(() {
        _isCameraReady = false;
        _isStreaming = false;
        _errorMessage =
            'Kamera başlatılırken beklenmeyen bir hata oluştu.';
      });

      _setConnectionState(ConnectionState.offline);
    } finally {
      _isBusy = false;
    }
  }

  // ------------------------------------------------------------
  // CAMERA DISPOSAL
  // ------------------------------------------------------------
    Future<void> _disposeCameraController() async {
    _streamGeneration++;
    _sessionId = null;
    final controller = _controller;

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

    _controller = null;
    _isStreaming = false;
  }

  // ------------------------------------------------------------
  // CAMERA SWITCH
  // ------------------------------------------------------------

  Future<void> _switchCamera() async {
    if (cameras.length < 2) {
      return;
    }

    if (!_isCameraReady ||
        _isBusy ||
        _isStreaming) {
      return;
    }

    final newIndex =
        (_selectedCameraIndex + 1) % cameras.length;

    await _initializeCamera(newIndex);
  }

  // ------------------------------------------------------------
  // START / STOP TOGGLE
  // ------------------------------------------------------------

  Future<void> _toggleStreaming() async {
    final controller = _controller;

    if (controller == null ||
        !controller.value.isInitialized ||
        _isBusy) {
      return;
    }

    if (_isStreaming) {
      await _stopStreaming();
    } else {
      await _startStreaming();
    }
  }

  // ------------------------------------------------------------
  // START STREAMING
  // ------------------------------------------------------------

  Future<void> _startStreaming({
    bool automaticReconnect = false,
  }) async {
    final controller = _controller;

    if (controller == null ||
        !controller.value.isInitialized ||
        _isStreaming ||
        _isBusy) {
      return;
    }

    if (_isAppInBackground) {
      return;
    }

    if (!automaticReconnect) {
      _manualStop = false;
      _reconnectAttempt = 0;
      _consecutiveFrameFailures = 0;
      _isReconnecting = false;
      _reconnectTimer?.cancel();
      _reconnectTimer = null;
    }

    _isBusy = true;

    if (automaticReconnect) {
      _setConnectionState(
        ConnectionState.reconnecting,
      );
    } else {
      _setConnectionState(
        ConnectionState.connecting,
      );
    }

    try {
      final sessionId =
          DateTime.now().millisecondsSinceEpoch.toString();

      final sessionOpened =
          await _sessionService.openSession(
        cameraId: 'camera-1',
        sessionId: sessionId,
        sessionToken: 'dev-session-token',
      );

      if (!sessionOpened) {
        if (mounted) {
          setState(() {
            _isStreaming = false;
            _errorMessage = 'Gateway session açılamadı.';
          });
        }

        _setConnectionState(
          automaticReconnect
              ? ConnectionState.reconnecting
              : ConnectionState.offline,
        );

        if (automaticReconnect) {
          _scheduleReconnect();
        }

        return;
      }

      _sessionId = sessionId;

      _heartbeatTimer?.cancel();
      _heartbeatTimer = Timer.periodic(
        const Duration(seconds: 10),
        (_) async {
          final currentSessionId = _sessionId;

          if (currentSessionId == null ||
              _manualStop ||
              _isAppInBackground) {
            return;
          }

          try {
            final heartbeatOk =
                await _sessionService.sendHeartbeat(
              cameraId: 'camera-1',
              sessionId: currentSessionId,
            );

            if (!heartbeatOk) {
              _handleConnectionFailure(
                'Gateway heartbeat başarısız.',
              );
            }
          } catch (_) {
            _handleConnectionFailure(
              'Gateway heartbeat gönderilemedi.',
            );
          }
        },
      );

      final streamGeneration = _streamGeneration;

      await controller.startImageStream(
        (CameraImage image) async {
          if (_manualStop ||
              _isAppInBackground ||
              _isReconnecting ||
              streamGeneration != _streamGeneration ||
              _isUploadingFrame) {
            return;
          }

          _activeFrameCallbacks++;

          final currentSessionId = _sessionId;

          if (currentSessionId == null) {
            _activeFrameCallbacks--;
            _completeFrameCallbacksIfNeeded();
            return;
          }

          _isUploadingFrame = true;

          try {
            final uploaded =
                await _frameService.uploadFrame(
              cameraId: 'camera-1',
              sessionId: currentSessionId,
              frameTimestamp: DateTime.now().toUtc(),
              image: image,
            );

            if (streamGeneration != _streamGeneration ||
                _sessionId != currentSessionId ||
                _manualStop ||
                _isAppInBackground ||
                _isReconnecting) {
              return;
            }

            if (uploaded) {
              _consecutiveFrameFailures = 0;

              if (_connectionState !=
                      ConnectionState.connected &&
                  !_isReconnecting &&
                  mounted) {
                _setConnectionState(
                  ConnectionState.connected,
                );
              }
            } else {
              _handleFrameFailure();
            }
          } catch (_) {
            _handleFrameFailure();
          } finally {
            _isUploadingFrame = false;
            _activeFrameCallbacks--;
            _completeFrameCallbacksIfNeeded();
          }
        },
      );

      if (!mounted) {
        return;
      }

      _reconnectAttempt = 0;
      _consecutiveFrameFailures = 0;
      _isReconnecting = false;

      setState(() {
        _isStreaming = true;
        _errorMessage = null;
      });

      _setConnectionState(
        ConnectionState.connected,
      );
    } on CameraException catch (e) {
      if (!mounted) {
        return;
      }

      setState(() {
        _isStreaming = false;
        _errorMessage =
            'Görüntü aktarımı başlatılamadı: '
            '${e.description ?? e.code}';
      });

      if (automaticReconnect) {
        _scheduleReconnect();
      } else {
        _setConnectionState(
          ConnectionState.offline,
        );
      }
    } catch (e) {
      if (!mounted) {
        return;
      }

      setState(() {
        _isStreaming = false;
        _errorMessage =
            'Yayın başlatılırken hata oluştu: $e';
      });

      if (automaticReconnect) {
        _scheduleReconnect();
      } else {
        _setConnectionState(
          ConnectionState.offline,
        );
      }
    } finally {
      _isBusy = false;
    }
  }

  // ------------------------------------------------------------
  // FRAME FAILURE
  // ------------------------------------------------------------

  void _handleFrameFailure() {
    if (_manualStop ||
        _isAppInBackground ||
        _isReconnecting) {
      return;
    }

    _consecutiveFrameFailures++;

    if (_consecutiveFrameFailures == 1) {
      _setConnectionState(
        ConnectionState.weak,
      );
    }

    if (_consecutiveFrameFailures >= 3) {
      _handleConnectionFailure(
        'Arka arkaya frame gönderim hatası oluştu.',
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

    await _frameCallbacksCompleter!.future;

    _frameCallbacksCompleter = null;
  }

  // ------------------------------------------------------------
  // CONNECTION FAILURE
  // ------------------------------------------------------------

  void _handleConnectionFailure(
    String message,
  ) {
    if (_manualStop ||
        _isAppInBackground ||
        _isReconnecting) {
      return;
    }

    if (mounted) {
      setState(() {
        _errorMessage = message;
      });
    }

    _scheduleReconnect();
  }

  // ------------------------------------------------------------
  // RECONNECT
  // ------------------------------------------------------------

  void _scheduleReconnect() {
    if (_manualStop ||
        _isAppInBackground ||
        _isReconnecting) {
      return;
    }

    if (_reconnectAttempt >= _maxReconnectAttempts) {
      _isReconnecting = false;

      _setConnectionState(
        ConnectionState.offline,
      );

      if (mounted) {
        setState(() {
          _isStreaming = false;
          _errorMessage =
              'Bağlantı kurulamadı. Lütfen tekrar deneyin.';
        });
      }

      return;
    }

    _isReconnecting = true;

    _setConnectionState(
      ConnectionState.reconnecting,
    );

    _reconnectTimer?.cancel();

    final delaySeconds = 1 << _reconnectAttempt;
    _reconnectAttempt++;

    final delay = Duration(
      seconds: delaySeconds.clamp(1, 8),
    );

    _reconnectTimer = Timer(
      delay,
      _performReconnect,
    );
  }

  Future<void> _performReconnect() async {
    if (_manualStop ||
        _isAppInBackground) {
      _isReconnecting = false;
      return;
    }

    _reconnectTimer = null;

    final controller = _controller;

    if (controller == null ||
        !controller.value.isInitialized) {
      _isReconnecting = false;

      _setConnectionState(
        ConnectionState.offline,
      );

      return;
    }

    _isBusy = true;

    try {
      final oldSessionId = _sessionId;

      // Eski stream callback'lerini geçersiz hale getir.
      _streamGeneration++;

      // Eski callback'lerin eski session'ı kullanmasını engelle.
      _sessionId = null;

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

      if (oldSessionId != null) {
        try {
          await _sessionService.closeSession(
            cameraId: 'camera-1',
            sessionId: oldSessionId,
          );
        } catch (_) {
          // Eski session zaten kapanmış olabilir.
        }
      }

      _isStreaming = false;
      _consecutiveFrameFailures = 0;
    } finally {
      _isBusy = false;
    }

    _isReconnecting = false;

    if (_manualStop ||
        _isAppInBackground) {
      _setConnectionState(
        ConnectionState.stopped,
      );

      return;
    }

    await _startStreaming(
      automaticReconnect: true,
    );
  }

  // ------------------------------------------------------------
  // STOP STREAMING
  // ------------------------------------------------------------

  Future<void> _stopStreaming({
    bool fromLifecycle = false,
  }) async {
    final controller = _controller;

    if (controller == null || _isBusy) {
      return;
    }

    _isBusy = true;

    if (!fromLifecycle) {
      _manualStop = true;
    }

    _isReconnecting = false;

    _reconnectTimer?.cancel();
    _reconnectTimer = null;

    final sessionId = _sessionId;

    // Eski frame callback'lerini geçersiz hale getir.
    _streamGeneration++;

    // Eski callback'lerin eski session'ı kullanmasını engelle.
    _sessionId = null;

    _heartbeatTimer?.cancel();
    _heartbeatTimer = null;

    try {
      if (controller.value.isStreamingImages) {
        await controller.stopImageStream();
      }

      await _waitForActiveFrameCallbacks();
      await _frameService.waitForPendingUploads();

      if (sessionId != null) {
        try {
          await _sessionService.closeSession(
            cameraId: 'camera-1',
            sessionId: sessionId,
          );
        } catch (_) {
          // Session zaten kapanmış olabilir.
        }
      }

      if (!mounted) {
        return;
      }

      setState(() {
        _isStreaming = false;

        if (!fromLifecycle) {
          _errorMessage = null;
        }
      });

      if (!fromLifecycle) {
        _setConnectionState(
          ConnectionState.stopped,
        );
      }
    } on CameraException catch (e) {
      if (!mounted) {
        return;
      }

      setState(() {
        _isStreaming = false;
        _errorMessage =
            'Görüntü aktarımı durdurulamadı: '
            '${e.description ?? e.code}';
      });

      _setConnectionState(
        ConnectionState.stopped,
      );
    } catch (e) {
      if (!mounted) {
        return;
      }

      setState(() {
        _isStreaming = false;
        _errorMessage =
            'Yayın durdurulurken hata oluştu: $e';
      });

      _setConnectionState(
        ConnectionState.stopped,
      );
    } finally {
      _isBusy = false;
    }
  }

  // APP LIFECYCLE
  // ------------------------------------------------------------

  @override
  void didChangeAppLifecycleState(
    AppLifecycleState state,
  ) {
    final isBackground =
        state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused ||
        state == AppLifecycleState.hidden;

    if (isBackground) {
      _isAppInBackground = true;

      if (_isStreaming) {
        _stopStreaming(
          fromLifecycle: true,
        );
      }

      _disposeCameraController();

      if (mounted) {
        setState(() {
          _isCameraReady = false;
          _isStreaming = false;
        });
      }

      _setConnectionState(
        ConnectionState.stopped,
      );

      return;
    }

    if (state == AppLifecycleState.resumed) {
      _isAppInBackground = false;

      // Önceden aktif olan session'ı sessizce
      // tekrar kullanmıyoruz.
      // Kamera yeniden hazırlanıyor.
      _initializeCamera(
        _selectedCameraIndex,
      );
    }
  }

  // ------------------------------------------------------------
  // DISPOSE
  // ------------------------------------------------------------

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(
      this,
    );

    _heartbeatTimer?.cancel();
    _heartbeatTimer = null;

    _reconnectTimer?.cancel();
    _reconnectTimer = null;

    _manualStop = true;
    _isReconnecting = false;
    _sessionId = null;

    _disposeCameraController();

    super.dispose();
  }

  // ------------------------------------------------------------
  // CONNECTION TEXT
  // ------------------------------------------------------------

  String _connectionText() {
    switch (_connectionState) {
      case ConnectionState.connecting:
        return 'Bağlanıyor...';

      case ConnectionState.connected:
        return 'Bağlı';

      case ConnectionState.weak:
        return 'Bağlantı zayıf';

      case ConnectionState.reconnecting:
        return 'Yeniden bağlanıyor...';
            case ConnectionState.offline:
        return 'Çevrimdışı';

      case ConnectionState.stopped:
        return 'Hazır';
    }
  }

  Color _connectionColor() {
    switch (_connectionState) {
      case ConnectionState.connected:
        return Colors.green;

      case ConnectionState.weak:
        return Colors.orange;

      case ConnectionState.connecting:
      case ConnectionState.reconnecting:
        return Colors.amber;

      case ConnectionState.offline:
        return Colors.red;

      case ConnectionState.stopped:
        return Colors.white;
    }
  }

  // ------------------------------------------------------------
  // UI
  // ------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    if (cameras.isEmpty) {
      return Scaffold(
        appBar: AppBar(
          title: const Text('Camera Stream'),
        ),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment:
                  MainAxisAlignment.center,
              children: [
                const Icon(
                  Icons.no_photography,
                  size: 64,
                ),
                const SizedBox(height: 16),
                Text(
                  _errorMessage ??
                      'Kullanılabilir kamera bulunamadı.',
                  textAlign: TextAlign.center,
                ),
              ],
            ),
          ),
        ),
      );
    }

    if (!_isCameraReady) {
      return Scaffold(
        appBar: AppBar(
          title: const Text('Camera Stream'),
        ),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment:
                  MainAxisAlignment.center,
              children: [
                if (_errorMessage == null)
                  const CircularProgressIndicator()
                else
                  const Icon(
                    Icons.error_outline,
                    size: 56,
                  ),
                const SizedBox(height: 16),
                Text(
                  _errorMessage ??
                      'Kamera hazırlanıyor...',
                  textAlign: TextAlign.center,
                ),
                if (_errorMessage != null) ...[
                  const SizedBox(height: 16),
                  ElevatedButton(
                    onPressed: _isBusy
                        ? null
                        : () => _initializeCamera(
                              _selectedCameraIndex,
                            ),
                    child: const Text(
                      'Tekrar Dene',
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('Camera Stream'),
        actions: [
          IconButton(
            icon: const Icon(
              Icons.cameraswitch,
            ),
            tooltip: 'Kamerayı değiştir',
            onPressed:
                (_isBusy || _isStreaming)
                    ? null
                    : _switchCamera,
          ),
        ],
      ),
      body: Stack(
        children: [
          Positioned.fill(
            child: CameraPreview(
              _controller!,
            ),
          ),

          // ----------------------------------------------------
          // CONNECTION STATUS
          // ----------------------------------------------------
                    Positioned(
            top: 16,
            left: 16,
            child: Container(
              padding:
                  const EdgeInsets.symmetric(
                horizontal: 12,
                vertical: 8,
              ),
              decoration: BoxDecoration(
                color: Colors.black54,
                borderRadius:
                    BorderRadius.circular(20),
              ),
              child: Row(
                mainAxisSize:
                    MainAxisSize.min,
                children: [
                  Icon(
                    Icons.circle,
                    size: 12,
                    color: _connectionColor(),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    _connectionText(),
                    style: const TextStyle(
                      color: Colors.white,
                    ),
                  ),
                ],
              ),
            ),
          ),

          // ----------------------------------------------------
          // ERROR
          // ----------------------------------------------------

          if (_errorMessage != null)
            Positioned(
              left: 16,
              right: 16,
              bottom: 100,
              child: Container(
                padding:
                    const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.red.shade700,
                  borderRadius:
                      BorderRadius.circular(12),
                ),
                child: Text(
                  _errorMessage!,
                  style: const TextStyle(
                    color: Colors.white,
                  ),
                  textAlign: TextAlign.center,
                ),
              ),
            ),

          // ----------------------------------------------------
          // START / STOP
          // ----------------------------------------------------

          Positioned(
            bottom: 30,
            left: 20,
            right: 20,
            child: ElevatedButton.icon(
              onPressed: _isBusy
                  ? null
                  : _toggleStreaming,
              icon: Icon(
                _isStreaming
                    ? Icons.stop
                    : Icons.play_arrow,
              ),
              label: Text(
                _isStreaming
                    ? 'Yayını Durdur'
                    : 'Yayını Başlat',
              ),
            ),
          ),
        ],
      ),
    );
  }
}
