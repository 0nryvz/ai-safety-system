import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../core/theme/strix_brand.dart';
import '../camera/camera_page.dart';
import '../streaming/streaming_controller.dart';
import 'camera_option.dart';

/// Giriş sonrası fabrika kamerası seçimi.
class CameraSelectionPage extends ConsumerWidget {
  final List<CameraOption> cameras;

  const CameraSelectionPage({
    super.key,
    required this.cameras,
  });

  Future<void> _assign(
    BuildContext context,
    WidgetRef ref,
    CameraOption camera,
  ) async {
    await ref
        .read(streamingControllerProvider.notifier)
        .selectBackendCamera(camera);

    if (!context.mounted) {
      return;
    }

    await Navigator.of(context).pushReplacement(
      MaterialPageRoute<void>(
        builder: (_) => const CameraPage(),
      ),
    );
  }

  void _onBack(BuildContext context) {
    leaveCameraFlow(context);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final active = cameras.where((c) => c.isSelectable).toList();
    final inactive = cameras.where((c) => !c.isSelectable).toList();

    return Scaffold(
      backgroundColor: StrixBrand.background,
      appBar: AppBar(
        title: Text(
          StrixBrand.shortName,
          style: GoogleFonts.inter(fontWeight: FontWeight.w700),
        ),
        leading: IconButton(
          tooltip: 'Geri',
          icon: const Icon(Icons.arrow_back),
          onPressed: () => _onBack(context),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
        children: [
          Text(
            'Kamera seçimi',
            style: GoogleFonts.inter(
              fontSize: 22,
              fontWeight: FontWeight.w700,
              color: StrixBrand.textPrimary,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Bu telefon seçilen fabrika kamerasını temsil eder. '
            'Yayın için bir aktif kamera seçin.',
            style: GoogleFonts.inter(
              fontSize: 14,
              height: 1.45,
              color: StrixBrand.textSecondary,
            ),
          ),
          const SizedBox(height: 24),
          if (active.isEmpty)
            Container(
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: StrixBrand.surface,
                borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
                border: Border.all(color: StrixBrand.border),
              ),
              child: Text(
                'Aktif kamera yok.',
                textAlign: TextAlign.center,
                style: GoogleFonts.inter(color: StrixBrand.textSecondary),
              ),
            )
          else
            ...active.map(
              (c) => _CameraTile(
                camera: c,
                enabled: true,
                onTap: () => _assign(context, ref, c),
              ),
            ),
          if (inactive.isNotEmpty) ...[
            const SizedBox(height: 16),
            Text(
              'Pasif kameralar',
              style: GoogleFonts.inter(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: StrixBrand.textSecondary,
              ),
            ),
            const SizedBox(height: 8),
            ...inactive.map(
              (c) => _CameraTile(camera: c, enabled: false, onTap: null),
            ),
          ],
        ],
      ),
    );
  }
}

class _CameraTile extends StatelessWidget {
  final CameraOption camera;
  final bool enabled;
  final VoidCallback? onTap;

  const _CameraTile({
    required this.camera,
    required this.enabled,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final iconBg = enabled
        ? StrixBrand.primary.withValues(alpha: 0.10)
        : StrixBrand.surfaceSubtle;
    final iconColor =
        enabled ? StrixBrand.primary : StrixBrand.textSecondary;

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Material(
        color: StrixBrand.surface,
        borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
              border: Border.all(color: StrixBrand.border),
            ),
            child: Row(
              children: [
                Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: iconBg,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(
                    enabled
                        ? Icons.videocam_outlined
                        : Icons.videocam_off_outlined,
                    color: iconColor,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        camera.name,
                        style: GoogleFonts.inter(
                          fontWeight: FontWeight.w600,
                          fontSize: 15,
                          color: enabled
                              ? StrixBrand.textPrimary
                              : StrixBrand.textSecondary,
                        ),
                      ),
                      if (camera.subtitle.isNotEmpty) ...[
                        const SizedBox(height: 4),
                        Text(
                          camera.subtitle,
                          style: GoogleFonts.inter(
                            fontSize: 13,
                            color: StrixBrand.textSecondary,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
                if (enabled)
                  const Icon(
                    Icons.chevron_right,
                    color: StrixBrand.textSecondary,
                  )
                else
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: StrixBrand.critical.withValues(alpha: 0.08),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      'Pasif',
                      style: GoogleFonts.inter(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: StrixBrand.critical,
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
