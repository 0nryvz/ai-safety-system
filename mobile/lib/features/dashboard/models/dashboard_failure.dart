import 'dashboard_distribution_item.dart';
import 'dashboard_summary.dart';
import 'dashboard_trend_point.dart';
import 'recent_violation_item.dart';

enum DashboardFailureKind {
  offline,
  unauthorized,
  forbidden,
  server,
  unknown,
}

class DashboardFailure implements Exception {
  final String message;
  final DashboardFailureKind kind;

  const DashboardFailure(
    this.message, {
    this.kind = DashboardFailureKind.unknown,
  });

  bool get isOffline => kind == DashboardFailureKind.offline;

  @override
  String toString() => message;
}

class DashboardSnapshot {
  final DashboardSummary summary;
  final List<DashboardTrendPoint> trend;
  final List<DashboardDistributionItem> distribution;
  final List<RecentViolationItem> recentViolations;

  const DashboardSnapshot({
    required this.summary,
    required this.trend,
    required this.distribution,
    required this.recentViolations,
  });
}
