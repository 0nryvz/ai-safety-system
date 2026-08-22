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
    return GridView.count(
      crossAxisCount: 2,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      mainAxisSpacing: 10,
      crossAxisSpacing: 10,
      childAspectRatio: 1.55,
      children: [
        _KpiCard(
          label: 'Bugün',
          value: '${summary.todayViolationCount}',
          hint: 'ihlal',
          accent: dashboardAccentForCount(summary.todayViolationCount),
        ),
        _KpiCard(
          label: 'Son 7 gün',
          value: '${summary.last7DaysViolationCount}',
          hint: 'ihlal',
          accent: dashboardAccentForCount(summary.last7DaysViolationCount),
        ),
        _KpiCard(
          label: 'Aktif kameralar',
          value: '${summary.activeCameraCount}',
          hint: 'çevrimiçi',
          accent: StrixBrand.success,
        ),
        _KpiCard(
          label: 'Aktif ihlaller',
          value: '${summary.activeViolationCount}',
          hint: summary.mostFrequentViolationType == null
              ? 'açık kayıt'
              : dashboardTypeLabel(summary.mostFrequentViolationType),
          accent: dashboardAccentForCount(summary.activeViolationCount),
        ),
      ],
    );
  }
}

class _KpiCard extends StatelessWidget {
  final String label;
  final String value;
  final String hint;
  final Color accent;

  const _KpiCard({
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
          Text(
            label,
            style: GoogleFonts.inter(
              fontSize: 12,
              fontWeight: FontWeight.w500,
              color: StrixBrand.textSecondary,
            ),
          ),
          const Spacer(),
          Text(
            value,
            style: GoogleFonts.inter(
              fontSize: 28,
              fontWeight: FontWeight.w700,
              color: accent,
              height: 1.05,
            ),
          ),
          const SizedBox(height: 4),
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
