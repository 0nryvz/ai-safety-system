import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../../core/theme/strix_brand.dart';
import '../../models/camera_item.dart';
import '../camera_labels.dart';
import 'camera_status_badge.dart';

class CameraCard extends StatelessWidget {
  final CameraItem camera;
  final bool canManage;
  final VoidCallback? onTap;
  final VoidCallback? onEdit;
  final ValueChanged<bool>? onActiveChanged;

  const CameraCard({
    super.key,
    required this.camera,
    required this.canManage,
    this.onTap,
    this.onEdit,
    this.onActiveChanged,
  });

  @override
  Widget build(BuildContext context) {
    final enabled = camera.active;

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Material(
        color: StrixBrand.surface,
        borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
          child: Container(
            padding: const EdgeInsets.fromLTRB(14, 14, 14, 12),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
              border: Border.all(color: StrixBrand.border),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      width: 44,
                      height: 44,
                      decoration: BoxDecoration(
                        color: enabled
                            ? StrixBrand.primary.withValues(alpha: 0.1)
                            : StrixBrand.surfaceSubtle,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Icon(
                        enabled
                            ? Icons.videocam_outlined
                            : Icons.videocam_off_outlined,
                        color: enabled
                            ? StrixBrand.primary
                            : StrixBrand.textSecondary,
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
                              color: StrixBrand.textPrimary,
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
                    CameraStatusBadge(status: camera.status),
                  ],
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    _MetaChip(
                      label: enabled ? 'Aktif' : 'Pasif',
                      color: enabled ? StrixBrand.success : StrixBrand.critical,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        formatCameraLastSeen(camera.lastSeenAt),
                        style: GoogleFonts.inter(
                          fontSize: 12,
                          color: StrixBrand.textSecondary,
                        ),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    if (canManage) ...[
                      if (onActiveChanged != null)
                        Switch.adaptive(
                          value: enabled,
                          onChanged: onActiveChanged,
                          activeTrackColor:
                              StrixBrand.primary.withValues(alpha: 0.45),
                        ),
                      if (onEdit != null)
                        IconButton(
                          tooltip: 'Düzenle',
                          onPressed: onEdit,
                          icon: const Icon(Icons.edit_outlined, size: 20),
                          color: StrixBrand.textSecondary,
                          visualDensity: VisualDensity.compact,
                        ),
                    ],
                    if (onTap != null && enabled)
                      const Icon(
                        Icons.chevron_right,
                        color: StrixBrand.textSecondary,
                      ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _MetaChip extends StatelessWidget {
  final String label;
  final Color color;

  const _MetaChip({required this.label, required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        label,
        style: GoogleFonts.inter(
          fontSize: 11,
          fontWeight: FontWeight.w600,
          color: color,
        ),
      ),
    );
  }
}
