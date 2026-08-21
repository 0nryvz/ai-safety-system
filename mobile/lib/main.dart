import 'package:camera/camera.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';
import 'features/streaming/streaming_controller.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  List<CameraDescription> cameras;

  try {
    cameras = await availableCameras();
  } catch (_) {
    // İzin verilmemişse liste alınamaz; izin akışı ekranda yürütülür.
    cameras = const [];
  }

  runApp(
    ProviderScope(
      overrides: [
        availableCamerasProvider.overrideWithValue(cameras),
      ],
      child: const CameraStreamApp(),
    ),
  );
}
