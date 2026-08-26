import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // availableCameras() Activity hazır olmadan çağrılmamalı; Tecno/HiOS'ta
  // ProcessCameraProvider native çöküşüne yol açıyor. Kamera listesi UI
  // ayağa kalktıktan ve izin alındıktan sonra StreamingController'da yüklenir.
  runApp(
    const ProviderScope(
      child: CameraStreamApp(),
    ),
  );
}
