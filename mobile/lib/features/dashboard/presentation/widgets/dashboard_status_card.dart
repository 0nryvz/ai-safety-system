import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../../core/theme/strix_brand.dart';
import '../../models/dashboard_summary.dart';
import '../dashboard_labels.dart';

class DashboardStatusCard extends StatelessWidget {
  final DashboardSummary summary;

  const DashboardStatusCard({super.key, required this.summary});

  @override
  Widget build(BuildContext context) {
    final active = summary.activeViolationCount;
    final offline = summary.offlineCameraCount;
    final cameras = summary.activeCameraCount;
    final attention = active > 0 || offline > 0;
    final accent = active > 0
        ? dashboardAccentForCount(active)
        : offline > 0
            ? StrixBrand.warning
            : StrixBrand.success;

    final headline = active > 0
        ? '$active açık ihlal izleniyor'
        : 'Açık ihlal yok';
    final cameraLine = offline > 0
        ? '$offline kamera çevrimdışı'
        : cameras > 0
            ? '$cameras kamera çevrimiçi'
            : 'Kayıtlı kamera yok';
    final frequent = summary.mostFrequentViolationType;

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 14),
      decoration: BoxDecoration(
        color: StrixBrand.surface,
        borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
        border: Border.all(color: StrixBrand.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: accent.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(
                  attention
                      ? Icons.shield_moon_outlined
                      : Icons.verified_outlined,
                  color: accent,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Saha durumu',
                      style: GoogleFonts.inter(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: StrixBrand.textSecondary,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      headline,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: GoogleFonts.inter(
                        fontSize: 18,
                        fontWeight: FontWeight.w700,
                        height: 1.2,
                        color: StrixBrand.textPrimary,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          _StatusLine(
            icon: offline > 0
                ? Icons.videocam_off_outlined
                : Icons.videocam_outlined,
            label: cameraLine,
            color: offline > 0 ? StrixBrand.warning : StrixBrand.success,
          ),
          const SizedBox(height: 8),
          _StatusLine(
            icon: Icons.warning_amber_outlined,
            label: '${summary.todayViolationCount} ihlal bugün',
            color: dashboardAccentForCount(summary.todayViolationCount),
          ),
          if (frequent != null && frequent.isNotEmpty) ...[
            const SizedBox(height: 8),
            _StatusLine(
              icon: dashboardTypeIcon(frequent),
              label: 'En sık: ${dashboardTypeLabel(frequent)}',
              color: dashboardTypeColor(frequent),
            ),
          ],
        ],
      ),
    );
  }
}

class _StatusLine extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;

  const _StatusLine({
    required this.icon,
    required this.label,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withValues(alpha: 0.22)),
      ),
      child: Row(
        children: [
          Icon(icon, size: 16, color: color),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: GoogleFonts.inter(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: StrixBrand.textPrimary,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
