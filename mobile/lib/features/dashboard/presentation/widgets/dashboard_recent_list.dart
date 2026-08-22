import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../../core/theme/strix_brand.dart';
import '../../models/recent_violation_item.dart';
import '../dashboard_labels.dart';

class DashboardRecentList extends StatelessWidget {
  final List<RecentViolationItem> items;
  final ValueChanged<String>? onTap;

  const DashboardRecentList({
    super.key,
    required this.items,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(vertical: 28, horizontal: 16),
        decoration: BoxDecoration(
          color: StrixBrand.surface,
          borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
          border: Border.all(color: StrixBrand.border),
        ),
        child: Text(
          'Son ihlal bulunmuyor',
          textAlign: TextAlign.center,
          style: GoogleFonts.inter(
            fontSize: 13,
            color: StrixBrand.textSecondary,
          ),
        ),
      );
    }

    return Container(
      decoration: BoxDecoration(
        color: StrixBrand.surface,
        borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
        border: Border.all(color: StrixBrand.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 14, 14, 8),
            child: Text(
              'Son ihlaller',
              style: GoogleFonts.inter(
                fontSize: 14,
                fontWeight: FontWeight.w700,
                color: StrixBrand.textPrimary,
              ),
            ),
          ),
          for (var i = 0; i < items.length; i++) ...[
            if (i > 0)
              const Divider(height: 1, color: StrixBrand.border),
            _RecentTile(
              item: items[i],
              onTap: onTap == null || items[i].violationId.isEmpty
                  ? null
                  : () => onTap!(items[i].violationId),
            ),
          ],
        ],
      ),
    );
  }
}

class _RecentTile extends StatelessWidget {
  final RecentViolationItem item;
  final VoidCallback? onTap;

  const _RecentTile({
    required this.item,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final subtitle = [
      if (item.cameraName != null && item.cameraName!.isNotEmpty)
        item.cameraName!,
      if (item.departmentName != null && item.departmentName!.isNotEmpty)
        item.departmentName!,
    ].join(' · ');

    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    dashboardTypeLabel(item.violationType),
                    style: GoogleFonts.inter(
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: StrixBrand.textPrimary,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    subtitle.isEmpty ? 'Kamera bilgisi yok' : subtitle,
                    style: GoogleFonts.inter(
                      fontSize: 12,
                      color: StrixBrand.textSecondary,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    formatLocalDateTime(item.detectedAt ?? item.startedAt),
                    style: GoogleFonts.inter(
                      fontSize: 11,
                      color: StrixBrand.textSecondary,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                _StatusChip(label: dashboardStatusLabel(item.lifecycleStatus)),
                const SizedBox(height: 6),
                Text(
                  dashboardStatusLabel(item.reviewStatus),
                  style: GoogleFonts.inter(
                    fontSize: 10,
                    color: StrixBrand.textSecondary,
                  ),
                ),
              ],
            ),
            if (onTap != null) ...[
              const SizedBox(width: 4),
              const Icon(
                Icons.chevron_right,
                color: StrixBrand.textSecondary,
                size: 20,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  final String label;

  const _StatusChip({required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: StrixBrand.surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: StrixBrand.border),
      ),
      child: Text(
        label,
        style: GoogleFonts.inter(
          fontSize: 10,
          fontWeight: FontWeight.w600,
          color: StrixBrand.textPrimary,
        ),
      ),
    );
  }
}
