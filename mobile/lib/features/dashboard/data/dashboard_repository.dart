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
      final results = await Future.wait<Object>(
        [
          _api.fetchSummary(),
          _api.fetchTrend(from: from, to: to, bucket: 'DAY'),
          _api.fetchDistribution(groupBy: 'TYPE'),
          _api.fetchRecentViolations(),
        ],
        eagerError: false,
      );

      return DashboardSnapshot(
        summary: results[0] as DashboardSummary,
        trend: results[1] as List<DashboardTrendPoint>,
        distribution: results[2] as List<DashboardDistributionItem>,
        recentViolations: results[3] as List<RecentViolationItem>,
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
