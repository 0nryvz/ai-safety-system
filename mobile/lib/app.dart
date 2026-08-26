import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/theme/strix_brand.dart';
import 'features/auth/app_shell.dart';
import 'features/auth/auth_controller.dart';
import 'features/auth/auth_login_page.dart';

class CameraStreamApp extends ConsumerWidget {
  const CameraStreamApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(authSessionProvider);

    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: StrixBrand.name,
      theme: StrixBrand.theme(),
      home: session.authenticated
          ? const AppShell()
          : const AuthLoginPage(),
    );
  }
}
