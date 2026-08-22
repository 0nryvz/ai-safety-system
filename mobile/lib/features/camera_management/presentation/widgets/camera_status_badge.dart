import 'package:flutter/material.dart';

import '../../../../core/theme/strix_brand.dart';
import '../../models/camera_status.dart';

class CameraStatusBadge extends StatelessWidget {
  final CameraStatus status;

  const CameraStatusBadge({super.key, required this.status});

  @override
  Widget build(BuildContext context) {
    final (Color bg, Color fg) = switch (status) {
      CameraStatus.online => (
          StrixBrand.successBackground,
          StrixBrand.success,
        ),
      CameraStatus.weak => (
          StrixBrand.warning.withValues(alpha: 0.15),
          StrixBrand.warning,
        ),
      CameraStatus.offline => (
          StrixBrand.critical.withValues(alpha: 0.1),
          StrixBrand.critical,
        ),
      CameraStatus.unknown => (
          StrixBrand.surfaceSubtle,
          StrixBrand.textSecondary,
        ),
    };

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        status.label,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w600,
          color: fg,
        ),
      ),
    );
  }
}
