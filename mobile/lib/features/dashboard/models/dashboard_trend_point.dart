class DashboardTrendPoint {
  final DateTime date;
  final int count;

  const DashboardTrendPoint({
    required this.date,
    required this.count,
  });

  factory DashboardTrendPoint.fromJson(Map<String, dynamic> json) {
    final raw = json['date'];
    final DateTime date;
    if (raw is String) {
      date = DateTime.parse(raw);
    } else {
      date = DateTime.fromMillisecondsSinceEpoch(0, isUtc: true);
    }

    return DashboardTrendPoint(
      date: date,
      count: _asInt(json['count']),
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
