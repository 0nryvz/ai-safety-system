import 'package:flutter/material.dart';

import 'core/theme/vigil_brand.dart';
import 'features/camera/camera_page.dart';

class CameraStreamApp extends StatelessWidget {
  const CameraStreamApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: '${VigilBrand.name} — ${VigilBrand.tagline}',
      theme: VigilBrand.theme(),
      home: const CameraPage(),
    );
  }
}
