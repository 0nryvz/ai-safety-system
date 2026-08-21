import 'package:flutter/material.dart';

import 'core/theme/strix_brand.dart';
import 'features/session/operator_login_page.dart';

class CameraStreamApp extends StatelessWidget {
  const CameraStreamApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: StrixBrand.name,
      theme: StrixBrand.theme(),
      home: const OperatorLoginPage(),
    );
  }
}
