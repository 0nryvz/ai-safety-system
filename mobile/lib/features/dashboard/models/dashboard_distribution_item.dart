class DashboardDistributionItem {
  final String group;
  final int count;

  const DashboardDistributionItem({
    required this.group,
    required this.count,
  });

  factory DashboardDistributionItem.fromJson(Map<String, dynamic> json) {
    return DashboardDistributionItem(
      group: (json['group'] as String?) ?? '',
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
