import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../shared/widgets/connection_badge.dart';
import '../../shared/widgets/error_banner.dart';
import '../../shared/widgets/stream_stats_overlay.dart';
import '../session/camera_picker_sheet.dart';
import '../streaming/streaming_controller.dart';
import '../streaming/streaming_state.dart';

class CameraPage extends ConsumerStatefulWidget {
  const CameraPage({super.key});

  @override
  ConsumerState<CameraPage> createState() => _CameraPageState();
}

class _CameraPageState extends ConsumerState<CameraPage>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();

    WidgetsBinding.instance.addObserver(this);

    // Notifier build() sırasında yan etki tetiklenmemeli.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(streamingControllerProvider.notifier).initialize();
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState lifecycle) {
    final controller = ref.read(streamingControllerProvider.notifier);

    final isBackground = lifecycle == AppLifecycleState.inactive ||
        lifecycle == AppLifecycleState.paused ||
        lifecycle == AppLifecycleState.hidden;

    if (isBackground) {
      controller.handleAppPaused();
      return;
    }

    if (lifecycle == AppLifecycleState.resumed) {
      controller.handleAppResumed();
    }
  }

  Future<void> _pickBackendCamera() async {
    final cameraId = await CameraPickerSheet.show(context);

    if (cameraId == null || !mounted) {
      return;
    }

    await ref
        .read(streamingControllerProvider.notifier)
        .selectBackendCamera(cameraId);
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(streamingControllerProvider);
    final controller = ref.read(streamingControllerProvider.notifier);

    if (state.availableCameraCount == 0) {
      return _messageScaffold(
        icon: Icons.no_photography,
        message: state.errorMessage ?? 'Kullanılabilir kamera bulunamadı.',
      );
    }

    if (!state.isCameraReady) {
      return _preparingScaffold(state, controller);
    }

    return _previewScaffold(state, controller);
  }

  // ------------------------------------------------------------------

  Scaffold _messageScaffold({
    required IconData icon,
    required String message,
  }) {
    return Scaffold(
      appBar: AppBar(title: const Text('Camera Stream')),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, size: 64),
              const SizedBox(height: 16),
              Text(message, textAlign: TextAlign.center),
            ],
          ),
        ),
      ),
    );
  }

  Scaffold _preparingScaffold(
    StreamingState state,
    StreamingController controller,
  ) {
    final hasError = state.errorMessage != null;

    return Scaffold(
      appBar: AppBar(title: const Text('Camera Stream')),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (hasError)
                const Icon(Icons.error_outline, size: 56)
              else
                const CircularProgressIndicator(),
              const SizedBox(height: 16),
              Text(
                state.errorMessage ?? 'Kamera hazırlanıyor...',
                textAlign: TextAlign.center,
              ),
              if (hasError) ...[
                const SizedBox(height: 16),
                if (state.canOpenSettings)
                  ElevatedButton.icon(
                    onPressed: controller.openAppSettings,
                    icon: const Icon(Icons.settings),
                    label: const Text('Ayarları Aç'),
                  )
                else
                  ElevatedButton(
                    onPressed: state.isBusy
                        ? null
                        : controller.requestPermissionAgain,
                    child: const Text('Tekrar Dene'),
                  ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Scaffold _previewScaffold(
    StreamingState state,
    StreamingController controller,
  ) {
    final cameraController = controller.cameraController;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Camera Stream'),
        actions: [
          IconButton(
            icon: const Icon(Icons.videocam_outlined),
            tooltip: 'Backend kamerası seç',
            onPressed: state.isStreaming ? null : _pickBackendCamera,
          ),
          IconButton(
            icon: const Icon(Icons.cameraswitch),
            tooltip: 'Kamerayı değiştir',
            onPressed: state.canSwitchCamera ? controller.switchCamera : null,
          ),
        ],
      ),
      body: Stack(
        children: [
          if (cameraController != null)
            Positioned.fill(child: CameraPreview(cameraController)),

          Positioned(
            top: 16,
            left: 16,
            child: ConnectionBadge(connection: state.connection),
          ),

          if (state.isStreaming)
            Positioned(
              top: 16,
              right: 16,
              child: Material(
                color: Colors.transparent,
                child: StreamStatsOverlay(state: state),
              ),
            ),

          Positioned(
            left: 16,
            right: 16,
            bottom: 96,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (state.errorMessage != null)
                  ErrorBanner(
                    message: state.errorMessage!,
                    actionLabel:
                        state.canOpenSettings ? 'Ayarları Aç' : null,
                    onAction: state.canOpenSettings
                        ? controller.openAppSettings
                        : null,
                  ),
                const SizedBox(height: 8),
                _cameraIdChip(state),
              ],
            ),
          ),

          Positioned(
            bottom: 30,
            left: 20,
            right: 20,
            child: ElevatedButton.icon(
              onPressed: (state.isBusy && !state.isStreaming)
                  ? null
                  : controller.toggleStreaming,
              icon: Icon(
                state.isStreaming ? Icons.stop : Icons.play_arrow,
              ),
              label: Text(
                state.isStreaming ? 'Yayını Durdur' : 'Yayını Başlat',
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// Hangi kamera kaydının simüle edildiği demo sırasında görünür olmalı.
  Widget _cameraIdChip(StreamingState state) {
    final cameraId = state.cameraId;

    if (cameraId == null) {
      return const SizedBox.shrink();
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.black54,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        'Kamera: $cameraId',
        style: const TextStyle(color: Colors.white70, fontSize: 11),
      ),
    );
  }
}
