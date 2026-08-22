import '../models/dashboard_distribution_item.dart';
import '../models/dashboard_failure.dart';
import '../models/dashboard_summary.dart';
import '../models/dashboard_trend_point.dart';
import '../models/recent_violation_item.dart';
import 'dashboard_api.dart';

abstract class DashboardLoader {
  Future<DashboardSnapshot> load();
}

class DashboardRepository implements DashboardLoader {
  final DashboardApi _api;

  DashboardRepository({required this._api});

  /// Son 7 gün trend (UTC günleri) + TYPE dağılımı.
  @override
  Future<DashboardSnapshot> load() async {
    final nowUtc = DateTime.now().toUtc();
    final to = DateTime.utc(nowUtc.year, nowUtc.month, nowUtc.day);
    final from = to.subtract(const Duration(days: 6));

    try {
      final summaryFuture = _api.fetchSummary();
      final trendFuture = _api.fetchTrend(from: from, to: to, bucket: 'DAY');
      final distributionFuture = _api.fetchDistribution(groupBy: 'TYPE');
      final recentFuture = _api.fetchRecentViolations();

      final DashboardSummary summary = await summaryFuture;
      final List<DashboardTrendPoint> trend = await trendFuture;
      final List<DashboardDistributionItem> distribution =
          await distributionFuture;
      final List<RecentViolationItem> recentViolations = await recentFuture;

      return DashboardSnapshot(
        summary: summary,
        trend: trend,
        distribution: distribution,
        recentViolations: recentViolations,
      );
    } on DashboardFailure {
      rethrow;
    } catch (_) {
      throw const DashboardFailure(
        'Dashboard verisi işlenemedi.',
        kind: DashboardFailureKind.unknown,
      );
    }
  }
}
