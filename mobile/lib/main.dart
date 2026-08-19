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
  } catch (e) {
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

  ConnectionState _connectionState = ConnectionState.stopped;

  void _setConnectionState(ConnectionState state) {
  if (!mounted) return;

  setState(() {
    _connectionState = state;
  });
}

  int _selectedCameraIndex = 0;

  String? _errorMessage;

  @override
  void initState() {
    super.initState();

    WidgetsBinding.instance.addObserver(this);

    _initializeCamera();
  }

  // ------------------------------------------------------------
  // CAMERA INITIALIZATION
  // ------------------------------------------------------------

  Future<void> _initializeCamera([int cameraIndex = 0]) async {
    if (cameras.isEmpty) {
      if (!mounted) return;

      setState(() {
        _isCameraReady = false;
        _errorMessage = 'Cihazda kullanılabilir kamera bulunamadı.';
      });

      return;
    }

    if (cameraIndex < 0 || cameraIndex >= cameras.length) {
      return;
    }

    if (_isBusy) {
      return;
    }

    _isBusy = true;

    try {
      await _disposeCameraController();

      if (!mounted) return;

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
      if (!mounted) return;

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
          message = 'Kamera erişimi bu cihazda kısıtlanmış.';
          break;

        case 'CameraDisconnected':
          message = 'Kamera bağlantısı kesildi.';
          break;

        default:
          message =
              'Kamera başlatılamadı: ${e.description ?? e.code}';
      }

      setState(() {
        _isCameraReady = false;
        _isStreaming = false;
        _errorMessage = message;
      });
    } catch (_) {
      if (!mounted) return;

      setState(() {
        _isCameraReady = false;
        _isStreaming = false;
        _errorMessage =
            'Kamera başlatılırken beklenmeyen bir hata oluştu.';
      });
    } finally {
      _isBusy = false;
    }
  }

  // ------------------------------------------------------------
  // CAMERA DISPOSAL
  // ------------------------------------------------------------

  Future<void> _disposeCameraController() async {
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

    if (!_isCameraReady || _isBusy) {
      return;
    }

    final newIndex =
        (_selectedCameraIndex + 1) % cameras.length;

    await _initializeCamera(newIndex);
  }

  // ------------------------------------------------------------
  // START / STOP STREAM
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

  Future<void> _startStreaming() async {
    final controller = _controller;

    if (controller == null ||
        !controller.value.isInitialized ||
        _isStreaming ||
        _isBusy) {
      return;
    }

    _isBusy = true;

    try {
      // Her yayın başlangıcında yeni bir session ID oluştur.
      final sessionId =
          DateTime.now().millisecondsSinceEpoch.toString();

      final sessionOpened =
          await _sessionService.openSession(
        cameraId: 'camera-1',
        sessionId: sessionId,
        sessionToken: 'dev-session-token',
      );

      if (!sessionOpened) {
        if (!mounted) return;

        setState(() {
          _errorMessage =
              'Gateway session açılamadı.';
          _isStreaming = false;
        });

        return;
      }

      // Gateway session başarılı şekilde açıldı.
      _sessionId = sessionId;

      await controller.startImageStream(
  (CameraImage image) async {
    final sessionId = _sessionId;

    if (sessionId == null) {
      return;
    }

    await _frameService.uploadFrame(
      cameraId: 'camera-1',
      sessionId: sessionId,
      frameTimestamp: DateTime.now().toUtc(),
      image: image,
    );
  },
);

      if (!mounted) return;

      setState(() {
        _isStreaming = true;
        _errorMessage = null;
      });
    } on CameraException catch (e) {
      if (!mounted) return;

      setState(() {
        _isStreaming = false;
        _errorMessage =
            'Görüntü aktarımı başlatılamadı: '
            '${e.description ?? e.code}';
      });
    } catch (e) {
      if (!mounted) return;

      setState(() {
        _isStreaming = false;
        _errorMessage =
            'Yayın başlatılırken hata oluştu: $e';
      });
    } finally {
      _isBusy = false;
    }
  }

  Future<void> _stopStreaming() async {
    final controller = _controller;

    if (controller == null || _isBusy) {
      return;
    }

    _isBusy = true;

    try {
      if (controller.value.isStreamingImages) {
        await controller.stopImageStream();
      }

      if (!mounted) return;

      setState(() {
        _isStreaming = false;
      });

      // Şimdilik session kapatma endpoint'ini burada çağırmıyoruz.
      // Close endpoint'i bir sonraki session service aşamasında
      // gerçek şekilde eklenecek.
      _sessionId = null;
    } on CameraException catch (e) {
      if (!mounted) return;

      setState(() {
        _errorMessage =
            'Görüntü aktarımı durdurulamadı: '
            '${e.description ?? e.code}';
      });
    } catch (e) {
      if (!mounted) return;

      setState(() {
        _errorMessage =
            'Yayın durdurulurken hata oluştu: $e';
      });
    } finally {
      _isBusy = false;
    }
  }

  // ------------------------------------------------------------
  // APP LIFECYCLE
  // ------------------------------------------------------------

  @override
  void didChangeAppLifecycleState(
    AppLifecycleState state,
  ) {
    final controller = _controller;

    if (controller == null ||
        !controller.value.isInitialized) {
      return;
    }

    if (state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused) {
      _disposeCameraController();

      if (mounted) {
        setState(() {
          _isCameraReady = false;
          _isStreaming = false;
        });
      }
    } else if (state == AppLifecycleState.resumed) {
      _initializeCamera(_selectedCameraIndex);
    }
  }

  // ------------------------------------------------------------
  // DISPOSE
  // ------------------------------------------------------------

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);

    _disposeCameraController();

    super.dispose();
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
                    child: const Text('Tekrar Dene'),
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
            icon: const Icon(Icons.cameraswitch),
            tooltip: 'Kamerayı değiştir',
            onPressed:
                _isBusy ? null : _switchCamera,
          ),
        ],
      ),
      body: Stack(
        children: [
          Positioned.fill(
            child: CameraPreview(_controller!),
          ),

          Positioned(
            top: 16,
            left: 16,
            child: Container(
              padding: const EdgeInsets.symmetric(
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
                    _isStreaming
                        ? Icons.circle
                        : Icons.pause_circle,
                    size: 12,
                    color: _isStreaming
                        ? Colors.green
                        : Colors.white,
                  ),
                  const SizedBox(width: 8),
                  Text(
                    _isStreaming
                        ? 'Aktarım açık'
                        : 'Hazır',
                    style: const TextStyle(
                      color: Colors.white,
                    ),
                  ),
                ],
              ),
            ),
          ),

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