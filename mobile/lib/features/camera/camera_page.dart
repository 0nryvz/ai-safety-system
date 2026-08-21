import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../core/config/app_config.dart';
import '../../core/theme/strix_brand.dart';
import '../../shared/widgets/error_banner.dart';
import '../session/operator_login_page.dart';
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

    // inactive: izin diyaloğu / sistem UI — yayını kesme.
    // paused/hidden/detached: gerçek arka plan.
    if (lifecycle == AppLifecycleState.paused ||
        lifecycle == AppLifecycleState.hidden ||
        lifecycle == AppLifecycleState.detached) {
      controller.handleAppPaused();
      return;
    }

    if (lifecycle == AppLifecycleState.resumed) {
      controller.handleAppResumed();
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(streamingControllerProvider);

    if (!_identityReady) {
      return Scaffold(
        backgroundColor: StrixBrand.background,
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(16),
                child: Image.asset(
                  StrixBrand.logoAsset,
                  width: 56,
                  height: 56,
                  fit: BoxFit.cover,
                ),
              ),
              const SizedBox(height: 16),
              Text(
                StrixBrand.name,
                style: GoogleFonts.inter(
                  fontSize: 20,
                  fontWeight: FontWeight.w700,
                  color: StrixBrand.textPrimary,
                ),
              ),
              const SizedBox(height: 20),
              const SizedBox(
                width: 28,
                height: 28,
                child: CircularProgressIndicator(
                  strokeWidth: 2.5,
                  color: StrixBrand.primary,
                ),
              ),
            ],
          ),
        ),
      );
    }

    if (!state.isCameraAssigned) {
      // Atama yoksa girişe dön — soğuk başlangıç her zaman login.
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) {
          return;
        }
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute<void>(
            builder: (_) => const OperatorLoginPage(),
          ),
          (_) => false,
        );
      });
      return const Scaffold(
        backgroundColor: StrixBrand.background,
        body: Center(
          child: CircularProgressIndicator(color: StrixBrand.primary),
        ),
      );
    }

    return const StrixDashboard();
  }
}

/// Operatör paneli.
class StrixDashboard extends ConsumerWidget {
  const StrixDashboard({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final controller = ref.read(streamingControllerProvider.notifier);

    return Scaffold(
      backgroundColor: StrixBrand.background,
      body: SafeArea(
        child: Column(
          children: [
            Consumer(
              builder: (context, ref, _) {
                final state = ref.watch(streamingControllerProvider);
                return _CommandHeader(state: state);
              },
            ),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
                children: [
                  Consumer(
                    builder: (context, ref, _) {
                      final state = ref.watch(streamingControllerProvider);
                      return _IdentityStrip(
                        state: state,
                        onChangeCamera: () async {
                          await controller.clearCameraAssignment();
                          if (!context.mounted) {
                            return;
                          }
                          Navigator.of(context).pushAndRemoveUntil(
                            MaterialPageRoute<void>(
                              builder: (_) => const OperatorLoginPage(),
                            ),
                            (_) => false,
                          );
                        },
                      );
                    },
                  ),
                  const SizedBox(height: 12),
                  Consumer(
                    builder: (context, ref, _) {
                      final state = ref.watch(streamingControllerProvider);
                      return _KpiStrip(state: state);
                    },
                  ),
                  const SizedBox(height: 14),
                  // FPS tick'leri CameraPreview'ı yeniden kurmasın.
                  const _LiveOpsPanel(),
                  const SizedBox(height: 12),
                  Consumer(
                    builder: (context, ref, _) {
                      final state = ref.watch(streamingControllerProvider);
                      return _TelemetryBoard(state: state);
                    },
                  ),
                  Consumer(
                    builder: (context, ref, _) {
                      final state = ref.watch(streamingControllerProvider);
                      if (state.errorMessage == null) {
                        return const SizedBox.shrink();
                      }
                      return Padding(
                        padding: const EdgeInsets.only(top: 12),
                        child: ErrorBanner(
                          message: state.errorMessage!,
                          actionLabel: state.canOpenSettings
                              ? 'Ayarları Aç'
                              : null,
                          onAction: state.canOpenSettings
                              ? controller.openAppSettings
                              : null,
                        ),
                      );
                    },
                  ),
                  const SizedBox(height: 14),
                  const _OpsFooterNote(),
                ],
              ),
            ),
            Consumer(
              builder: (context, ref, _) {
                final state = ref.watch(streamingControllerProvider);
                return _ActionDock(
                  state: state,
                  onToggle: controller.toggleStreaming,
                );
              },
            ),
          ],
        ),
      ),
    );
  }
}

class _CommandHeader extends StatelessWidget {
  final StreamingState state;

  const _CommandHeader({required this.state});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
      decoration: const BoxDecoration(
        color: StrixBrand.surface,
        border: Border(
          bottom: BorderSide(color: StrixBrand.border),
        ),
      ),
      child: Row(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: Image.asset(
              StrixBrand.logoAsset,
              width: 40,
              height: 40,
              fit: BoxFit.cover,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  StrixBrand.name,
                  style: GoogleFonts.inter(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: StrixBrand.textPrimary,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  'Operatör konsolu',
                  style: GoogleFonts.inter(
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                    color: StrixBrand.textSecondary,
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
            style: GoogleFonts.inter(
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
        StreamConnectionState.connected => StrixBrand.success,
        StreamConnectionState.weak ||
        StreamConnectionState.connecting ||
        StreamConnectionState.reconnecting =>
          StrixBrand.amber,
        StreamConnectionState.offline => StrixBrand.danger,
        StreamConnectionState.stopped => StrixBrand.steel,
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
        color: StrixBrand.surface,
        borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
        border: Border.all(color: StrixBrand.border),
      ),
      child: Row(
        children: [
          Container(
            width: 4,
            height: 42,
            decoration: BoxDecoration(
              color: StrixBrand.primary,
              borderRadius: BorderRadius.circular(4),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Atanmış fabrika kamerası',
                  style: GoogleFonts.inter(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: StrixBrand.primary,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  state.displayCameraTitle,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: GoogleFonts.inter(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    height: 1.2,
                    color: StrixBrand.textPrimary,
                  ),
                ),
                if (state.displayCameraSubtitle.isNotEmpty)
                  Text(
                    state.displayCameraSubtitle,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: GoogleFonts.inter(
                      color: StrixBrand.textSecondary,
                      fontSize: 13,
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
                ? StrixBrand.danger
                : state.sendFps >= AppConfig.targetFps
                    ? StrixBrand.success
                    : StrixBrand.amber,
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: _KpiTile(
            label: 'KAMERA',
            value: '${state.cameraFps}',
            unit: 'FPS',
            hint: state.phoneLensLabel,
            accent: StrixBrand.teal,
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: _KpiTile(
            label: 'HEDEF',
            value: '${AppConfig.targetFps}',
            unit: 'FPS',
            hint: 'Gateway',
            accent: StrixBrand.steel,
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
            StrixBrand.panelElevated,
            StrixBrand.panel,
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
            style: GoogleFonts.inter(
              fontSize: 10,
              fontWeight: FontWeight.w600,
              letterSpacing: 1.1,
              color: StrixBrand.steel,
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
                    style: GoogleFonts.inter(
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
                      color: StrixBrand.steel,
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

class _LiveOpsPanel extends ConsumerWidget {
  const _LiveOpsPanel();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // FPS değişince preview ağacını yeniden kurma.
    final previewKey = ref.watch(
      streamingControllerProvider.select(
        (s) => (
          s.isStreaming,
          s.isCameraReady,
          s.selectedCameraIndex,
          s.availableCameraCount,
          s.phoneLensLabel,
          s.canSwitchPhoneCamera,
          s.errorMessage,
          s.canOpenSettings,
        ),
      ),
    );
    final sendFps = ref.watch(
      streamingControllerProvider.select((s) => s.sendFps),
    );
    final controller = ref.read(streamingControllerProvider.notifier);
    final cameraController = controller.cameraController;

    final isStreaming = previewKey.$1;
    final isCameraReady = previewKey.$2;
    final availableCameraCount = previewKey.$4;
    final phoneLensLabel = previewKey.$5;
    final canSwitchPhoneCamera = previewKey.$6;
    final errorMessage = previewKey.$7;
    final canOpenSettings = previewKey.$8;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Text(
              'YEREL ÖNİZLEME',
              style: GoogleFonts.inter(
                fontSize: 12,
                fontWeight: FontWeight.w800,
                letterSpacing: 1.4,
              ),
            ),
            const Spacer(),
            TextButton.icon(
              onPressed: canSwitchPhoneCamera
                  ? controller.switchPhoneCamera
                  : null,
              icon: const Icon(Icons.cameraswitch_rounded, size: 18),
              label: Text(phoneLensLabel),
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
                color: isStreaming
                    ? StrixBrand.teal.withValues(alpha: 0.55)
                    : StrixBrand.border,
                width: isStreaming ? 1.6 : 1,
              ),
              boxShadow: [
                BoxShadow(
                  color: isStreaming
                      ? StrixBrand.teal.withValues(alpha: 0.22)
                      : Colors.black.withValues(alpha: 0.35),
                  blurRadius: isStreaming ? 28 : 12,
                  offset: const Offset(0, 8),
                ),
              ],
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(17),
              child: Stack(
                fit: StackFit.expand,
                children: [
                  _PreviewBody(
                    availableCameraCount: availableCameraCount,
                    isCameraReady: isCameraReady,
                    errorMessage: errorMessage,
                    canOpenSettings: canOpenSettings,
                    cameraController: cameraController,
                    onRetry: canOpenSettings
                        ? controller.openAppSettings
                        : controller.requestPermissionAgain,
                  ),
                  if (isStreaming)
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
                  if (isStreaming) ...[
                    Positioned(
                      top: 12,
                      left: 12,
                      child: _LiveBadge(fps: sendFps),
                    ),
                    Positioned(
                      top: 12,
                      right: 12,
                      child: _HudChip(
                        label: phoneLensLabel.toUpperCase(),
                      ),
                    ),
                    Positioned(
                      left: 12,
                      right: 12,
                      bottom: 12,
                      child: _PreviewFooterLite(sendFps: sendFps),
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
}

class _PreviewBody extends StatelessWidget {
  final int availableCameraCount;
  final bool isCameraReady;
  final String? errorMessage;
  final bool canOpenSettings;
  final CameraController? cameraController;
  final VoidCallback onRetry;

  const _PreviewBody({
    required this.availableCameraCount,
    required this.isCameraReady,
    required this.errorMessage,
    required this.canOpenSettings,
    required this.cameraController,
    required this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    if (availableCameraCount == 0) {
      return _previewEmpty(Icons.no_photography, 'Telefon kamerası yok');
    }
    if (!isCameraReady || cameraController == null) {
      if (errorMessage != null) {
        return _previewEmpty(
          Icons.error_outline,
          errorMessage!,
          action: canOpenSettings ? 'Ayarları Aç' : 'Tekrar Dene',
        );
      }
      return const Center(
        child: CircularProgressIndicator(color: StrixBrand.teal),
      );
    }

    // Yayın sırasında da canlı önizleme (Texture); ImageAnalysis ayrı akar.
    return CameraPreview(
      cameraController!,
      key: ObjectKey(cameraController),
    );
  }

  Widget _previewEmpty(IconData icon, String message, {String? action}) {
    return ColoredBox(
      color: StrixBrand.panel,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 36, color: StrixBrand.steel),
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
      ..color = StrixBrand.teal.withValues(alpha: 0.55)
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
        color: StrixBrand.danger,
        borderRadius: BorderRadius.circular(8),
        boxShadow: [
          BoxShadow(
            color: StrixBrand.danger.withValues(alpha: 0.4),
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
            style: GoogleFonts.inter(
              fontSize: 11,
              fontWeight: FontWeight.w800,
              letterSpacing: 0.6,
              color: Colors.white,
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
        color: Colors.black.withValues(alpha: 0.62),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.white24),
      ),
      child: Text(
        label,
        style: GoogleFonts.inter(
          fontSize: 10,
          fontWeight: FontWeight.w700,
          letterSpacing: 1,
          color: Colors.white,
        ),
      ),
    );
  }
}

class _PreviewFooterLite extends StatelessWidget {
  final int sendFps;

  const _PreviewFooterLite({required this.sendFps});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.62),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: Colors.white24),
      ),
      child: Text(
        'Gateway JPEG · $sendFps FPS',
        style: const TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w600,
          color: Colors.white,
        ),
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
        color: StrixBrand.panel.withValues(alpha: 0.92),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: StrixBrand.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                'TELEMETRİ',
                style: GoogleFonts.inter(
                  fontSize: 11,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.4,
                ),
              ),
              const Spacer(),
              Text(
                state.connection.label,
                style: const TextStyle(
                  color: StrixBrand.steel,
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
                  accent: StrixBrand.success,
                ),
              ),
              Expanded(
                child: _MetricCell(
                  label: 'Hata',
                  value: '${state.failedFrames}',
                  accent: state.failedFrames > 0
                      ? StrixBrand.danger
                      : StrixBrand.steel,
                ),
              ),
              Expanded(
                child: _MetricCell(
                  label: 'Düşen',
                  value: '${state.droppedFrames}',
                  accent: state.droppedFrames > 0
                      ? StrixBrand.amber
                      : StrixBrand.steel,
                ),
              ),
              Expanded(
                child: _MetricCell(
                  label: 'Güvenilirlik',
                  value: reliability,
                  accent: StrixBrand.teal,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Divider(height: 1, color: StrixBrand.border),
          const SizedBox(height: 10),
          Row(
            children: [
              const Icon(Icons.schedule, size: 14, color: StrixBrand.steel),
              const SizedBox(width: 6),
              Text(
                'Son başarılı paket: $last',
                style: const TextStyle(
                  fontSize: 12,
                  color: StrixBrand.steel,
                ),
              ),
              if (state.isReconnecting) ...[
                const Spacer(),
                Text(
                  'Deneme ${state.reconnectAttempt}/${state.maxReconnectAttempts}',
                  style: const TextStyle(
                    fontSize: 12,
                    color: StrixBrand.amber,
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
          style: GoogleFonts.inter(
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
            color: StrixBrand.steel,
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
      StrixBrand.pitch,
      style: TextStyle(
        color: StrixBrand.steel.withValues(alpha: 0.9),
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
        decoration: const BoxDecoration(
          color: StrixBrand.surface,
          border: Border(
            top: BorderSide(color: StrixBrand.border),
          ),
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
                  style: GoogleFonts.inter(
                    fontSize: 12,
                    color: StrixBrand.textSecondary,
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
                        : Icons.play_arrow_rounded,
                    size: 22,
                  ),
                  label: Text(
                    state.isStreaming ? 'Aktarımı durdur' : 'Yayını başlat',
                  ),
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(52),
                    backgroundColor: state.isStreaming
                        ? StrixBrand.critical
                        : StrixBrand.primary,
                    foregroundColor: Colors.white,
                    textStyle: GoogleFonts.inter(
                      fontWeight: FontWeight.w600,
                      fontSize: 14,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius:
                          BorderRadius.circular(StrixBrand.radiusButton),
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
