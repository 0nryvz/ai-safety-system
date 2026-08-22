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
        padding: const EdgeInsets.fromLTRB(14, 14, 14, 24),
        decoration: BoxDecoration(
          color: StrixBrand.surface,
          borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
          border: Border.all(color: StrixBrand.border),
        ),
        child: Column(
          children: [
            Align(
              alignment: Alignment.centerLeft,
              child: Text(
                'Son ihlaller',
                style: GoogleFonts.inter(
                  fontSize: 14,
                  fontWeight: FontWeight.w700,
                  color: StrixBrand.textPrimary,
                ),
              ),
            ),
            const SizedBox(height: 18),
            Icon(
              Icons.inbox_outlined,
              color: StrixBrand.textSecondary,
              size: 32,
            ),
            const SizedBox(height: 10),
            Text(
              'Son ihlal bulunmuyor',
              textAlign: TextAlign.center,
              style: GoogleFonts.inter(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: StrixBrand.textPrimary,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              'Yeni ihlaller burada listelenir.',
              textAlign: TextAlign.center,
              style: GoogleFonts.inter(
                fontSize: 13,
                color: StrixBrand.textSecondary,
              ),
            ),
          ],
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
            padding: const EdgeInsets.fromLTRB(14, 14, 14, 4),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    'Son ihlaller',
                    style: GoogleFonts.inter(
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                      color: StrixBrand.textPrimary,
                    ),
                  ),
                ),
                Text(
                  '${items.length} kayıt',
                  style: GoogleFonts.inter(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: StrixBrand.textSecondary,
                  ),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 0, 14, 8),
            child: Text(
              'Karta dokunarak detayı açın',
              style: GoogleFonts.inter(
                fontSize: 12,
                color: StrixBrand.textSecondary,
              ),
            ),
          ),
          for (var i = 0; i < items.length; i++) ...[
            if (i > 0) const Divider(height: 1, color: StrixBrand.border),
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
    final typeColor = dashboardTypeColor(item.violationType);
    final statusColor = dashboardStatusColor(item.lifecycleStatus);

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 12, 10, 12),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: typeColor.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(
                  dashboardTypeIcon(item.violationType),
                  color: typeColor,
                  size: 20,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      dashboardTypeLabel(item.violationType),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: GoogleFonts.inter(
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                        color: StrixBrand.textPrimary,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      subtitle.isEmpty ? 'Kamera bilgisi yok' : subtitle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: GoogleFonts.inter(
                        fontSize: 12,
                        color: StrixBrand.textSecondary,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 6,
                      runSpacing: 6,
                      children: [
                        _StatusChip(
                          label: dashboardStatusLabel(item.lifecycleStatus),
                          color: statusColor,
                        ),
                        _StatusChip(
                          label: dashboardStatusLabel(item.reviewStatus),
                          color: dashboardStatusColor(item.reviewStatus),
                        ),
                        Text(
                          formatLocalDateTime(item.detectedAt ?? item.startedAt),
                          style: GoogleFonts.inter(
                            fontSize: 11,
                            color: StrixBrand.textSecondary,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              if (onTap != null)
                const Padding(
                  padding: EdgeInsets.only(top: 8),
                  child: Icon(
                    Icons.chevron_right,
                    color: StrixBrand.textSecondary,
                    size: 22,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  final String label;
  final Color color;

  const _StatusChip({
    required this.label,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.10),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: color.withValues(alpha: 0.28)),
      ),
      child: Text(
        label,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: GoogleFonts.inter(
          fontSize: 10,
          fontWeight: FontWeight.w700,
          color: color,
        ),
      ),
    );
  }
}
