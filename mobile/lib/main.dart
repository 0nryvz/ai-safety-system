import 'package:camera/camera.dart';
import 'package:flutter/material.dart';

late List<CameraDescription> cameras;

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  cameras = await availableCameras();

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
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
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

class _CameraPageState extends State<CameraPage> {
  CameraController? _controller;
  bool _isCameraReady = false;
  int _selectedCameraIndex = 0;
  bool _isStreaming = false;
  CameraImage? _latestImage;

  @override
  void initState() {
    super.initState();
    _initializeCamera();
  }

  Future<void> _initializeCamera([int cameraIndex = 0]) async {
  _controller?.dispose();

  _controller = CameraController(
    cameras[cameraIndex],
    ResolutionPreset.medium,
    enableAudio: false,
  );

  await _controller!.initialize();

  if (!mounted) return;

  setState(() {
    _selectedCameraIndex = cameraIndex;
    _isCameraReady = true;
  });
}
Future<void> _switchCamera() async {
  if (cameras.length < 2) return;

  final newIndex = _selectedCameraIndex == 0 ? 1 : 0;

  await _initializeCamera(newIndex);
}
Future<void> _toggleStreaming() async {
  if (_controller == null) return;

  if (!_isStreaming) {
    await _controller!.startImageStream((CameraImage image) {
      _latestImage = image;
    });
  } else {
    await _controller!.stopImageStream();
  }

  setState(() {
    _isStreaming = !_isStreaming;
  });
}
  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!_isCameraReady) {
      return const Scaffold(
        body: Center(
          child: CircularProgressIndicator(),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
  title: const Text("Camera Stream"),
  actions: [
    IconButton(
      icon: const Icon(Icons.cameraswitch),
      onPressed: _switchCamera,
    ),
  ],
),
      body: Stack(
  children: [
    CameraPreview(_controller!),

    Positioned(
      bottom: 30,
      left: 20,
      right: 20,
      child: ElevatedButton(
        onPressed: _toggleStreaming,
        child: Text(
          _isStreaming ? "Yayını Durdur" : "Yayını Başlat",
        ),
      ),
    ),
  ],
),
    );
  }
}