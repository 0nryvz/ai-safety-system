import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../auth/auth_controller.dart';
import '../../camera/camera_page.dart';
import '../../session/camera_option.dart';
import '../../streaming/streaming_controller.dart';
import '../data/camera_management_api.dart';
import '../data/camera_management_repository.dart';
import '../models/camera_item.dart';
import 'cameras_page.dart';

/// AppShell Kameralar sekmesi — [AuthenticatedApi] oturumunu kullanır.
class CamerasTabPage extends ConsumerWidget {
  const CamerasTabPage({super.key});

  CameraOption _toCameraOption(CameraItem item) {
    return CameraOption(
      id: item.id,
      name: item.name,
      code: item.code,
      departmentName: item.departmentName,
      active: item.active,
      // Mevcut streaming akışı `connectionStatus` okur; backend alanı `status`.
      connectionStatus: item.status.wireValue,
    );
  }

  Future<void> _openBroadcast(
    BuildContext context,
    WidgetRef ref,
    CameraItem camera,
  ) async {
    await ref
        .read(streamingControllerProvider.notifier)
        .selectBackendCamera(_toCameraOption(camera));

    if (!context.mounted) {
      return;
    }

    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => const CameraPage(),
      ),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final api = ref.watch(authenticatedApiProvider);
    final session = ref.watch(authSessionProvider);
    final repository = CameraManagementRepository(
      api: CameraManagementApi.fromAuthenticated(api),
    );

    return CamerasPage(
      repository: repository,
      canManageCameras: session.canManageCameras,
      onOpenBroadcast: (camera) => _openBroadcast(context, ref, camera),
    );
  }
}
