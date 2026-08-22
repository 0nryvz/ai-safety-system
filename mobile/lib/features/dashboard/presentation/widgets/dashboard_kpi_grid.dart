import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../../core/theme/strix_brand.dart';
import '../../models/dashboard_summary.dart';
import '../dashboard_labels.dart';

class DashboardKpiGrid extends StatelessWidget {
  final DashboardSummary summary;

  const DashboardKpiGrid({super.key, required this.summary});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: _KpiCard(
                icon: Icons.today_outlined,
                label: 'Bugün',
                value: '${summary.todayViolationCount}',
                hint: 'ihlal',
                accent: dashboardAccentForCount(summary.todayViolationCount),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: _KpiCard(
                icon: Icons.date_range_outlined,
                label: 'Son 7 gün',
                value: '${summary.last7DaysViolationCount}',
                hint: 'ihlal',
                accent: dashboardAccentForCount(summary.last7DaysViolationCount),
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: _KpiCard(
                icon: Icons.videocam_outlined,
                label: 'Aktif kameralar',
                value: '${summary.activeCameraCount}',
                hint: 'çevrimiçi',
                accent: StrixBrand.success,
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: _KpiCard(
                icon: Icons.warning_amber_outlined,
                label: 'Aktif ihlaller',
                value: '${summary.activeViolationCount}',
                hint: 'açık kayıt',
                accent: dashboardAccentForCount(summary.activeViolationCount),
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _KpiCard extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final String hint;
  final Color accent;

  const _KpiCard({
    required this.icon,
    required this.label,
    required this.value,
    required this.hint,
    required this.accent,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
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
                width: 28,
                height: 28,
                decoration: BoxDecoration(
                  color: accent.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(icon, size: 16, color: accent),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: GoogleFonts.inter(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: StrixBrand.textSecondary,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          FittedBox(
            fit: BoxFit.scaleDown,
            alignment: Alignment.centerLeft,
            child: Text(
              value,
              style: GoogleFonts.inter(
                fontSize: 28,
                fontWeight: FontWeight.w700,
                color: accent,
                height: 1.05,
              ),
            ),
          ),
          const SizedBox(height: 2),
          Text(
            hint,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: GoogleFonts.inter(
              fontSize: 11,
              fontWeight: FontWeight.w500,
              color: StrixBrand.textSecondary,
            ),
          ),
        ],
      ),
    );
  }
}
