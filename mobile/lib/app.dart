import 'package:flutter/material.dart';

import 'features/camera/camera_page.dart';

class CameraStreamApp extends StatelessWidget {
  const CameraStreamApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Camera Stream',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const CameraPage(),
    );
  }
}
