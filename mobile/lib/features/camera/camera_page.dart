import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../core/config/app_config.dart';
import '../../core/theme/vigil_brand.dart';
import '../../shared/widgets/error_banner.dart';
import '../session/camera_assignment_page.dart';
import '../session/camera_option.dart';
import '../streaming/streaming_controller.dart';
import '../streaming/streaming_state.dart';

class CameraPage extends ConsumerStatefulWidget {
  const CameraPage({super.key});

  @override
  ConsumerState<CameraPage> createState() => _CameraPageState();
}

class _CameraPageState extends ConsumerState<CameraPage>
    with WidgetsBindingObserver {
  bool _identityReady = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final controller = ref.read(streamingControllerProvider.notifier);
      await controller.loadCameraIdentity();
      if (mounted) {
        setState(() => _identityReady = true);
      }
      // Kamera izni + liste Activity ayaktayken; UI'ı bloklamaz.
      await controller.initialize();
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

  Future<void> _onAssigned(CameraOption camera) async {
    await ref
        .read(streamingControllerProvider.notifier)
        .selectBackendCamera(camera);
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(streamingControllerProvider);

    if (!_identityReady) {
      return Scaffold(
        body: Stack(
          fit: StackFit.expand,
          children: [
            const _OpsAtmosphere(),
            Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Image.asset(
                    'assets/brand/vigil_app_icon.png',
                    width: 64,
                    height: 64,
                  ),
                  const SizedBox(height: 20),
                  Text(
                    VigilBrand.name,
                    style: GoogleFonts.spaceGrotesk(
                      fontSize: 28,
                      fontWeight: FontWeight.w800,
                      letterSpacing: 3,
                    ),
                  ),
                  const SizedBox(height: 16),
                  const SizedBox(
                    width: 28,
                    height: 28,
                    child: CircularProgressIndicator(
                      strokeWidth: 2.5,
                      color: VigilBrand.teal,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      );
    }

    if (!state.isCameraAssigned) {
      return CameraAssignmentPage(onAssigned: _onAssigned);
    }

    return const VigilDashboard();
  }
}

/// Satışa yönelik operatör komuta paneli.
class VigilDashboard extends ConsumerWidget {
  const VigilDashboard({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(streamingControllerProvider);
    final controller = ref.read(streamingControllerProvider.notifier);
    final camera = controller.cameraController;

    return Scaffold(
      body: Stack(
        fit: StackFit.expand,
        children: [
          const _OpsAtmosphere(),
          SafeArea(
            child: Column(
              children: [
                _CommandHeader(state: state),
                Expanded(
                  child: ListView(
                    padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
                    children: [
                      _IdentityStrip(
                        state: state,
                        onChangeCamera: () =>
                            CameraAssignmentPageRoute.push(context, ref),
                      ),
                      const SizedBox(height: 12),
                      _KpiStrip(state: state),
                      const SizedBox(height: 14),
                      _LiveOpsPanel(
                        state: state,
                        cameraController: camera,
                        onSwitchLens: controller.switchPhoneCamera,
                        onRetry: state.canOpenSettings
                            ? controller.openAppSettings
                            : controller.requestPermissionAgain,
                      ),
                      const SizedBox(height: 12),
                      _TelemetryBoard(state: state),
                      if (state.errorMessage != null) ...[
                        const SizedBox(height: 12),
                        ErrorBanner(
                          message: state.errorMessage!,
                          actionLabel:
                              state.canOpenSettings ? 'Ayarları Aç' : null,
                          onAction: state.canOpenSettings
                              ? controller.openAppSettings
                              : null,
                        ),
                      ],
                      const SizedBox(height: 14),
                      const _OpsFooterNote(),
                    ],
                  ),
                ),
                _ActionDock(
                  state: state,
                  onToggle: controller.toggleStreaming,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _OpsAtmosphere extends StatelessWidget {
  const _OpsAtmosphere();

  @override
  Widget build(BuildContext context) {
    return const DecoratedBox(
      decoration: BoxDecoration(
        gradient: RadialGradient(
          center: Alignment(-0.55, -0.85),
          radius: 1.15,
          colors: [
            Color(0xFF163A36),
            VigilBrand.ink,
          ],
          stops: [0.0, 0.72],
        ),
      ),
      child: CustomPaint(
        painter: _GridPainter(),
        child: SizedBox.expand(),
      ),
    );
  }
}

class _GridPainter extends CustomPainter {
  const _GridPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.white.withValues(alpha: 0.028)
      ..strokeWidth = 1;

    const step = 28.0;
    for (var x = 0.0; x < size.width; x += step) {
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), paint);
    }
    for (var y = 0.0; y < size.height; y += step) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), paint);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class _CommandHeader extends StatelessWidget {
  final StreamingState state;

  const _CommandHeader({required this.state});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 8),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(2),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(14),
              border: Border.all(
                color: VigilBrand.teal.withValues(alpha: 0.45),
              ),
              boxShadow: [
                BoxShadow(
                  color: VigilBrand.teal.withValues(alpha: 0.18),
                  blurRadius: 16,
                ),
              ],
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: Image.asset(
                'assets/brand/vigil_app_icon.png',
                width: 44,
                height: 44,
                fit: BoxFit.cover,
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  VigilBrand.name,
                  style: GoogleFonts.spaceGrotesk(
                    fontSize: 24,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 2.4,
                    height: 1,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'OPERATÖR KONSOLU',
                  style: GoogleFonts.spaceGrotesk(
                    fontSize: 10,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 1.6,
                    color: VigilBrand.teal,
                  ),
                ),
              ],
            ),
          ),
          _GatewayChip(state: state),
        ],
      ),
    );
  }
}

class _GatewayChip extends StatelessWidget {
  final StreamingState state;

  const _GatewayChip({required this.state});

  @override
  Widget build(BuildContext context) {
    final color = _statusColor(state.connection);
    final label = _shortStatus(state.connection);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          _PulseDot(color: color, active: state.isStreaming),
          const SizedBox(width: 7),
          Text(
            label,
            style: GoogleFonts.spaceGrotesk(
              fontSize: 11,
              fontWeight: FontWeight.w800,
              letterSpacing: 0.8,
              color: color,
            ),
          ),
        ],
      ),
    );
  }

  String _shortStatus(StreamConnectionState s) => switch (s) {
        StreamConnectionState.connected => 'CANLI',
        StreamConnectionState.connecting => 'AÇILIYOR',
        StreamConnectionState.reconnecting => 'YENİDEN',
        StreamConnectionState.weak => 'ZAYIF',
        StreamConnectionState.offline => 'OFFLINE',
        StreamConnectionState.stopped => 'HAZIR',
      };

  Color _statusColor(StreamConnectionState s) => switch (s) {
        StreamConnectionState.connected => VigilBrand.success,
        StreamConnectionState.weak ||
        StreamConnectionState.connecting ||
        StreamConnectionState.reconnecting =>
          VigilBrand.amber,
        StreamConnectionState.offline => VigilBrand.danger,
        StreamConnectionState.stopped => VigilBrand.steel,
      };
}

class _PulseDot extends StatefulWidget {
  final Color color;
  final bool active;

  const _PulseDot({required this.color, required this.active});

  @override
  State<_PulseDot> createState() => _PulseDotState();
}

class _PulseDotState extends State<_PulseDot>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1100),
    );
    if (widget.active) {
      _controller.repeat(reverse: true);
    }
  }

  @override
  void didUpdateWidget(covariant _PulseDot oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.active && !_controller.isAnimating) {
      _controller.repeat(reverse: true);
    } else if (!widget.active && _controller.isAnimating) {
      _controller.stop();
      _controller.value = 1;
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, _) {
        final t = widget.active ? _controller.value : 1.0;
        return Container(
          width: 8,
          height: 8,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: widget.color,
            boxShadow: [
              BoxShadow(
                color: widget.color.withValues(alpha: 0.25 + 0.45 * t),
                blurRadius: 4 + 8 * t,
              ),
            ],
          ),
        );
      },
    );
  }
}

class _IdentityStrip extends StatelessWidget {
  final StreamingState state;
  final VoidCallback onChangeCamera;

  const _IdentityStrip({
    required this.state,
    required this.onChangeCamera,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 12, 8, 12),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        gradient: const LinearGradient(
          begin: Alignment.centerLeft,
          end: Alignment.centerRight,
          colors: [
            Color(0xFF14332F),
            VigilBrand.panelElevated,
          ],
        ),
        border: Border.all(color: VigilBrand.teal.withValues(alpha: 0.28)),
      ),
      child: Row(
        children: [
          Container(
            width: 4,
            height: 42,
            decoration: BoxDecoration(
              color: VigilBrand.teal,
              borderRadius: BorderRadius.circular(4),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'ATANMIŞ FABRİKA KAMERASI',
                  style: GoogleFonts.spaceGrotesk(
                    fontSize: 9,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 1.3,
                    color: VigilBrand.teal,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  state.displayCameraTitle,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: GoogleFonts.spaceGrotesk(
                    fontSize: 18,
                    fontWeight: FontWeight.w800,
                    height: 1.1,
                  ),
                ),
                if (state.displayCameraSubtitle.isNotEmpty)
                  Text(
                    state.displayCameraSubtitle,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: VigilBrand.steel,
                      fontSize: 12,
                    ),
                  ),
              ],
            ),
          ),
          if (!state.isStreaming)
            TextButton(
              onPressed: onChangeCamera,
              child: const Text('Değiştir'),
            ),
        ],
      ),
    );
  }
}

class _KpiStrip extends StatelessWidget {
  final StreamingState state;

  const _KpiStrip({required this.state});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: _KpiTile(
            label: 'GÖNDERİM',
            value: '${state.sendFps}',
            unit: 'FPS',
            hint: 'min ${AppConfig.minFps}',
            accent: state.sendFps < AppConfig.minFps
                ? VigilBrand.danger
                : state.sendFps >= AppConfig.targetFps
                    ? VigilBrand.success
                    : VigilBrand.amber,
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: _KpiTile(
            label: 'KAMERA',
            value: '${state.cameraFps}',
            unit: 'FPS',
            hint: state.phoneLensLabel,
            accent: VigilBrand.teal,
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: _KpiTile(
            label: 'HEDEF',
            value: '${AppConfig.targetFps}',
            unit: 'FPS',
            hint: 'Gateway',
            accent: VigilBrand.steel,
          ),
        ),
      ],
    );
  }
}

class _KpiTile extends StatelessWidget {
  final String label;
  final String value;
  final String unit;
  final String hint;
  final Color accent;

  const _KpiTile({
    required this.label,
    required this.value,
    required this.unit,
    required this.hint,
    required this.accent,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 10),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            VigilBrand.panelElevated,
            VigilBrand.panel,
          ],
        ),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: accent.withValues(alpha: 0.4)),
        boxShadow: [
          BoxShadow(
            color: accent.withValues(alpha: 0.08),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: GoogleFonts.spaceGrotesk(
              fontSize: 10,
              fontWeight: FontWeight.w600,
              letterSpacing: 1.1,
              color: VigilBrand.steel,
            ),
          ),
          const SizedBox(height: 6),
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Flexible(
                child: AnimatedSwitcher(
                  duration: const Duration(milliseconds: 180),
                  child: Text(
                    value,
                    key: ValueKey('$label-$value'),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: GoogleFonts.spaceGrotesk(
                      fontSize: 24,
                      fontWeight: FontWeight.w800,
                      color: accent,
                      height: 1,
                    ),
                  ),
                ),
              ),
              if (unit.isNotEmpty) ...[
                const SizedBox(width: 4),
                Padding(
                  padding: const EdgeInsets.only(bottom: 2),
                  child: Text(
                    unit,
                    style: const TextStyle(
                      fontSize: 11,
                      color: VigilBrand.steel,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ],
          ),
          const SizedBox(height: 6),
          Text(
            hint,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 10,
              color: accent.withValues(alpha: 0.85),
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

class _LiveOpsPanel extends StatelessWidget {
  final StreamingState state;
  final CameraController? cameraController;
  final VoidCallback onSwitchLens;
  final VoidCallback onRetry;

  const _LiveOpsPanel({
    required this.state,
    required this.cameraController,
    required this.onSwitchLens,
    required this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Text(
              'YEREL ÖNİZLEME',
              style: GoogleFonts.spaceGrotesk(
                fontSize: 12,
                fontWeight: FontWeight.w800,
                letterSpacing: 1.4,
              ),
            ),
            const Spacer(),
            TextButton.icon(
              onPressed: state.canSwitchPhoneCamera ? onSwitchLens : null,
              icon: const Icon(Icons.cameraswitch_rounded, size: 18),
              label: Text(state.phoneLensLabel),
            ),
          ],
        ),
        const SizedBox(height: 8),
        AspectRatio(
          aspectRatio: 4 / 3,
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: Colors.black,
              borderRadius: BorderRadius.circular(18),
              border: Border.all(
                color: state.isStreaming
                    ? VigilBrand.teal.withValues(alpha: 0.55)
                    : Colors.white.withValues(alpha: 0.1),
                width: state.isStreaming ? 1.6 : 1,
              ),
              boxShadow: [
                BoxShadow(
                  color: state.isStreaming
                      ? VigilBrand.teal.withValues(alpha: 0.22)
                      : Colors.black.withValues(alpha: 0.35),
                  blurRadius: state.isStreaming ? 28 : 12,
                  offset: const Offset(0, 8),
                ),
              ],
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(17),
              child: Stack(
                fit: StackFit.expand,
                children: [
                  _previewBody(),
                  if (state.isStreaming)
                    IgnorePointer(
                      child: DecoratedBox(
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            begin: Alignment.topCenter,
                            end: Alignment.bottomCenter,
                            colors: [
                              Colors.black.withValues(alpha: 0.28),
                              Colors.transparent,
                              Colors.transparent,
                              Colors.black.withValues(alpha: 0.45),
                            ],
                            stops: const [0, 0.22, 0.65, 1],
                          ),
                        ),
                      ),
                    ),
                  const CustomPaint(painter: _BezelPainter()),
                  if (state.isStreaming) ...[
                    Positioned(
                      top: 12,
                      left: 12,
                      child: _LiveBadge(fps: state.sendFps),
                    ),
                    Positioned(
                      top: 12,
                      right: 12,
                      child: _HudChip(
                        label: state.phoneLensLabel.toUpperCase(),
                      ),
                    ),
                    Positioned(
                      left: 12,
                      right: 12,
                      bottom: 12,
                      child: _PreviewFooter(state: state),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _previewBody() {
    if (state.availableCameraCount == 0) {
      return _empty(Icons.no_photography, 'Telefon kamerası yok');
    }
    if (!state.isCameraReady || cameraController == null) {
      if (state.errorMessage != null) {
        return _empty(
          Icons.error_outline,
          state.errorMessage!,
          action: state.canOpenSettings ? 'Ayarları Aç' : 'Tekrar Dene',
        );
      }
      return const Center(
        child: CircularProgressIndicator(color: VigilBrand.teal),
      );
    }
    return CameraPreview(cameraController!);
  }

  Widget _empty(IconData icon, String message, {String? action}) {
    return ColoredBox(
      color: VigilBrand.panel,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 36, color: VigilBrand.steel),
            const SizedBox(height: 10),
            Text(message, textAlign: TextAlign.center),
            if (action != null) ...[
              const SizedBox(height: 12),
              FilledButton(onPressed: onRetry, child: Text(action)),
            ],
          ],
        ),
      ),
    );
  }
}

class _BezelPainter extends CustomPainter {
  const _BezelPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = VigilBrand.teal.withValues(alpha: 0.55)
      ..strokeWidth = 2
      ..style = PaintingStyle.stroke;

    const len = 18.0;
    const inset = 10.0;

    void corner(Offset o, double dx, double dy) {
      canvas.drawLine(o, o.translate(dx * len, 0), paint);
      canvas.drawLine(o, o.translate(0, dy * len), paint);
    }

    corner(const Offset(inset, inset), 1, 1);
    corner(Offset(size.width - inset, inset), -1, 1);
    corner(Offset(inset, size.height - inset), 1, -1);
    corner(Offset(size.width - inset, size.height - inset), -1, -1);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class _LiveBadge extends StatelessWidget {
  final int fps;

  const _LiveBadge({required this.fps});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: VigilBrand.danger,
        borderRadius: BorderRadius.circular(8),
        boxShadow: [
          BoxShadow(
            color: VigilBrand.danger.withValues(alpha: 0.4),
            blurRadius: 12,
          ),
        ],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const _PulseDot(color: Colors.white, active: true),
          const SizedBox(width: 7),
          Text(
            'CANLI  $fps FPS',
            style: GoogleFonts.spaceGrotesk(
              fontSize: 11,
              fontWeight: FontWeight.w800,
              letterSpacing: 0.6,
            ),
          ),
        ],
      ),
    );
  }
}

class _HudChip extends StatelessWidget {
  final String label;

  const _HudChip({required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.55),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.white.withValues(alpha: 0.15)),
      ),
      child: Text(
        label,
        style: GoogleFonts.spaceGrotesk(
          fontSize: 10,
          fontWeight: FontWeight.w700,
          letterSpacing: 1,
        ),
      ),
    );
  }
}

class _PreviewFooter extends StatelessWidget {
  final StreamingState state;

  const _PreviewFooter({required this.state});

  @override
  Widget build(BuildContext context) {
    final session = state.sessionId == null
        ? 'oturum yok'
        : '${state.sessionId!.substring(0, 8)}…';

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.55),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(
              'Gateway JPEG · $session',
              style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600),
            ),
          ),
          Text(
            '${state.sentFrames} kare',
            style: GoogleFonts.spaceGrotesk(
              fontSize: 11,
              fontWeight: FontWeight.w700,
              color: VigilBrand.teal,
            ),
          ),
        ],
      ),
    );
  }
}

class _TelemetryBoard extends StatelessWidget {
  final StreamingState state;

  const _TelemetryBoard({required this.state});

  @override
  Widget build(BuildContext context) {
    final total = state.sentFrames + state.failedFrames;
    final reliability = total == 0
        ? '—'
        : '${((state.sentFrames / total) * 100).round()}%';
    final last = state.lastSuccessAt == null
        ? '—'
        : _relative(state.lastSuccessAt!);

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: VigilBrand.panel.withValues(alpha: 0.92),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.white.withValues(alpha: 0.07)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                'TELEMETRİ',
                style: GoogleFonts.spaceGrotesk(
                  fontSize: 11,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.4,
                ),
              ),
              const Spacer(),
              Text(
                state.connection.label,
                style: const TextStyle(
                  color: VigilBrand.steel,
                  fontSize: 11,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: _MetricCell(
                  label: 'Gönderilen',
                  value: '${state.sentFrames}',
                  accent: VigilBrand.success,
                ),
              ),
              Expanded(
                child: _MetricCell(
                  label: 'Hata',
                  value: '${state.failedFrames}',
                  accent: state.failedFrames > 0
                      ? VigilBrand.danger
                      : VigilBrand.steel,
                ),
              ),
              Expanded(
                child: _MetricCell(
                  label: 'Düşen',
                  value: '${state.droppedFrames}',
                  accent: state.droppedFrames > 0
                      ? VigilBrand.amber
                      : VigilBrand.steel,
                ),
              ),
              Expanded(
                child: _MetricCell(
                  label: 'Güvenilirlik',
                  value: reliability,
                  accent: VigilBrand.teal,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Divider(height: 1, color: Colors.white.withValues(alpha: 0.06)),
          const SizedBox(height: 10),
          Row(
            children: [
              const Icon(Icons.schedule, size: 14, color: VigilBrand.steel),
              const SizedBox(width: 6),
              Text(
                'Son başarılı paket: $last',
                style: const TextStyle(
                  fontSize: 12,
                  color: VigilBrand.steel,
                ),
              ),
              if (state.isReconnecting) ...[
                const Spacer(),
                Text(
                  'Deneme ${state.reconnectAttempt}/${state.maxReconnectAttempts}',
                  style: const TextStyle(
                    fontSize: 12,
                    color: VigilBrand.amber,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }

  String _relative(DateTime at) {
    final diff = DateTime.now().difference(at);
    if (diff.inSeconds < 3) return 'şimdi';
    if (diff.inSeconds < 60) return '${diff.inSeconds} sn önce';
    if (diff.inMinutes < 60) return '${diff.inMinutes} dk önce';
    return '${diff.inHours} sa önce';
  }
}

class _MetricCell extends StatelessWidget {
  final String label;
  final String value;
  final Color accent;

  const _MetricCell({
    required this.label,
    required this.value,
    required this.accent,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(
          value,
          style: GoogleFonts.spaceGrotesk(
            fontSize: 18,
            fontWeight: FontWeight.w800,
            color: accent,
            height: 1,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          label,
          style: const TextStyle(
            fontSize: 10,
            color: VigilBrand.steel,
            fontWeight: FontWeight.w600,
          ),
        ),
      ],
    );
  }
}

class _OpsFooterNote extends StatelessWidget {
  const _OpsFooterNote();

  @override
  Widget build(BuildContext context) {
    return Text(
      VigilBrand.pitch,
      style: TextStyle(
        color: VigilBrand.steel.withValues(alpha: 0.9),
        fontSize: 12,
        height: 1.4,
      ),
    );
  }
}

class _ActionDock extends StatelessWidget {
  final StreamingState state;
  final VoidCallback onToggle;

  const _ActionDock({
    required this.state,
    required this.onToggle,
  });

  @override
  Widget build(BuildContext context) {
    final canPress = state.isStreaming ? true : state.canStartStream;
    final subtitle = state.isStreaming
        ? 'Gateway oturumu açık · ${state.sendFps} FPS gönderiliyor'
        : state.canStartStream
            ? 'Atama hazır · JPEG kareleri Gateway’e gidecek'
            : 'Kamera veya izin hazır değil';

    return Material(
      color: Colors.transparent,
      child: Container(
        decoration: BoxDecoration(
          color: VigilBrand.panelElevated.withValues(alpha: 0.96),
          border: Border(
            top: BorderSide(color: Colors.white.withValues(alpha: 0.08)),
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.35),
              blurRadius: 20,
              offset: const Offset(0, -6),
            ),
          ],
        ),
        child: SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  subtitle,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontSize: 12,
                    color: VigilBrand.steel,
                    fontWeight: FontWeight.w500,
                  ),
                ),
                const SizedBox(height: 10),
                FilledButton.icon(
                  onPressed: (state.isBusy && !state.isStreaming) || !canPress
                      ? null
                      : onToggle,
                  icon: Icon(
                    state.isStreaming
                        ? Icons.stop_rounded
                        : Icons.bolt_rounded,
                    size: 22,
                  ),
                  label: Text(
                    state.isStreaming
                        ? 'Aktarımı durdur'
                        : 'Gateway oturumu aç · aktar',
                  ),
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(56),
                    backgroundColor: state.isStreaming
                        ? VigilBrand.danger
                        : VigilBrand.teal,
                    foregroundColor:
                        state.isStreaming ? Colors.white : VigilBrand.ink,
                    textStyle: GoogleFonts.spaceGrotesk(
                      fontWeight: FontWeight.w800,
                      fontSize: 15,
                      letterSpacing: 0.3,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class CameraAssignmentPageRoute {
  static Future<void> push(BuildContext context, WidgetRef ref) {
    return Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => CameraAssignmentPage(
          onAssigned: (camera) async {
            await ref
                .read(streamingControllerProvider.notifier)
                .selectBackendCamera(camera);
            if (context.mounted) {
              Navigator.of(context).pop();
            }
          },
        ),
      ),
    );
  }
}
