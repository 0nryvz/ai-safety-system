class DashboardSummary {
  final int todayViolationCount;
  final int last7DaysViolationCount;
  final String? mostFrequentViolationType;
  final int activeCameraCount;
  final int offlineCameraCount;
  final int activeViolationCount;

  const DashboardSummary({
    required this.todayViolationCount,
    required this.last7DaysViolationCount,
    required this.mostFrequentViolationType,
    required this.activeCameraCount,
    required this.offlineCameraCount,
    required this.activeViolationCount,
  });

  factory DashboardSummary.fromJson(Map<String, dynamic> json) {
    return DashboardSummary(
      todayViolationCount: _asInt(json['todayViolationCount']),
      last7DaysViolationCount: _asInt(json['last7DaysViolationCount']),
      mostFrequentViolationType: json['mostFrequentViolationType'] as String?,
      activeCameraCount: _asInt(json['activeCameraCount']),
      offlineCameraCount: _asInt(json['offlineCameraCount']),
      activeViolationCount: _asInt(json['activeViolationCount']),
    );
  }

  static int _asInt(Object? value) {
    if (value is int) {
      return value;
    }
    if (value is num) {
      return value.toInt();
    }
    return 0;
  }
}
