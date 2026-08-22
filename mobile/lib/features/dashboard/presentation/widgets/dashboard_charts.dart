import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../../core/theme/strix_brand.dart';
import '../../models/dashboard_distribution_item.dart';
import '../../models/dashboard_trend_point.dart';
import '../dashboard_labels.dart';

class DashboardTrendChart extends StatelessWidget {
  final List<DashboardTrendPoint> points;

  const DashboardTrendChart({super.key, required this.points});

  @override
  Widget build(BuildContext context) {
    if (points.isEmpty) {
      return const _EmptyBlock(
        title: '7 günlük trend',
        message: 'Trend verisi yok',
      );
    }

    final maxCount = points
        .map((p) => p.count)
        .fold<int>(0, (a, b) => a > b ? a : b)
        .clamp(1, 1 << 30);
    final total = points.fold<int>(0, (sum, point) => sum + point.count);

    return Container(
      padding: const EdgeInsets.fromLTRB(14, 14, 14, 12),
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
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '7 günlük trend',
                      style: GoogleFonts.inter(
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                        color: StrixBrand.textPrimary,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      'Günlük ihlal sayısı',
                      style: GoogleFonts.inter(
                        fontSize: 12,
                        color: StrixBrand.textSecondary,
                      ),
                    ),
                  ],
                ),
              ),
              Text(
                '$total toplam',
                style: GoogleFonts.inter(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: StrixBrand.textSecondary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          SizedBox(
            height: 132,
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                for (final point in points)
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 3),
                      child: Column(
                        children: [
                          SizedBox(
                            height: 16,
                            child: Text(
                              point.count == 0 ? '' : '${point.count}',
                              style: GoogleFonts.inter(
                                fontSize: 10,
                                fontWeight: FontWeight.w600,
                                color: StrixBrand.textSecondary,
                              ),
                            ),
                          ),
                          Expanded(
                            child: Align(
                              alignment: Alignment.bottomCenter,
                              child: FractionallySizedBox(
                                heightFactor: point.count == 0
                                    ? 0.06
                                    : (point.count / maxCount).clamp(0.08, 1),
                                widthFactor: 1,
                                child: DecoratedBox(
                                  decoration: BoxDecoration(
                                    color: point.count == 0
                                        ? StrixBrand.border
                                        : StrixBrand.primary.withValues(
                                            alpha: 0.9,
                                          ),
                                    borderRadius: BorderRadius.circular(7),
                                  ),
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(height: 6),
                          Text(
                            dashboardWeekdayShort(point.date),
                            maxLines: 1,
                            overflow: TextOverflow.clip,
                            style: GoogleFonts.inter(
                              fontSize: 10,
                              fontWeight: FontWeight.w600,
                              color: StrixBrand.textPrimary,
                            ),
                          ),
                          Text(
                            formatLocalShortDate(point.date),
                            maxLines: 1,
                            overflow: TextOverflow.clip,
                            style: GoogleFonts.inter(
                              fontSize: 9,
                              color: StrixBrand.textSecondary,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class DashboardDistributionChart extends StatelessWidget {
  final List<DashboardDistributionItem> items;

  const DashboardDistributionChart({super.key, required this.items});

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return const _EmptyBlock(
        title: 'İhlal dağılımı',
        message: 'Dağılım verisi yok',
      );
    }

    final total = items.fold<int>(0, (sum, item) => sum + item.count);
    final safeTotal = total == 0 ? 1 : total;

    return Container(
      padding: const EdgeInsets.fromLTRB(14, 14, 14, 8),
      decoration: BoxDecoration(
        color: StrixBrand.surface,
        borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
        border: Border.all(color: StrixBrand.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'İhlal dağılımı',
            style: GoogleFonts.inter(
              fontSize: 14,
              fontWeight: FontWeight.w700,
              color: StrixBrand.textPrimary,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            'Son dönem tür kırılımı',
            style: GoogleFonts.inter(
              fontSize: 12,
              color: StrixBrand.textSecondary,
            ),
          ),
          const SizedBox(height: 12),
          for (final item in items)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: _DistributionRow(
                item: item,
                percent: ((item.count / safeTotal) * 100).round(),
                barValue: item.count / safeTotal,
              ),
            ),
        ],
      ),
    );
  }
}

class _DistributionRow extends StatelessWidget {
  final DashboardDistributionItem item;
  final int percent;
  final double barValue;

  const _DistributionRow({
    required this.item,
    required this.percent,
    required this.barValue,
  });

  @override
  Widget build(BuildContext context) {
    final color = dashboardTypeColor(item.group);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(dashboardTypeIcon(item.group), size: 16, color: color),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                dashboardTypeLabel(item.group),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: GoogleFonts.inter(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: StrixBrand.textPrimary,
                ),
              ),
            ),
            Text(
              '${item.count}',
              style: GoogleFonts.inter(
                fontSize: 13,
                fontWeight: FontWeight.w700,
                color: StrixBrand.textPrimary,
              ),
            ),
            const SizedBox(width: 6),
            Text(
              '%$percent',
              style: GoogleFonts.inter(
                fontSize: 12,
                color: StrixBrand.textSecondary,
              ),
            ),
          ],
        ),
        const SizedBox(height: 6),
        ClipRRect(
          borderRadius: BorderRadius.circular(6),
          child: LinearProgressIndicator(
            value: barValue.clamp(0, 1),
            minHeight: 8,
            backgroundColor: color.withValues(alpha: 0.12),
            color: color,
          ),
        ),
      ],
    );
  }
}

class _EmptyBlock extends StatelessWidget {
  final String title;
  final String message;

  const _EmptyBlock({
    required this.title,
    required this.message,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(14, 14, 14, 20),
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
              title,
              style: GoogleFonts.inter(
                fontSize: 14,
                fontWeight: FontWeight.w700,
                color: StrixBrand.textPrimary,
              ),
            ),
          ),
          const SizedBox(height: 16),
          Icon(
            Icons.insert_chart_outlined,
            color: StrixBrand.textSecondary,
            size: 32,
          ),
          const SizedBox(height: 10),
          Text(
            message,
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
}
