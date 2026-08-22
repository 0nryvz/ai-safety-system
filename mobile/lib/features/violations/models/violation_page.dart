import 'violation_list_item.dart';

/// Backend `PageResponse<ViolationListItem>`.
class ViolationPage {
  final List<ViolationListItem> content;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;

  const ViolationPage({
    required this.content,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
  });

  bool get hasMore => page + 1 < totalPages;

  factory ViolationPage.fromJson(Map<String, dynamic> json) {
    final raw = json['content'];
    final items = raw is List
        ? raw
            .cast<Map<String, dynamic>>()
            .map(ViolationListItem.fromJson)
            .toList(growable: false)
        : const <ViolationListItem>[];

    return ViolationPage(
      content: items,
      page: _asInt(json['page']) ?? 0,
      size: _asInt(json['size']) ?? items.length,
      totalElements: _asInt(json['totalElements']) ?? items.length,
      totalPages: _asInt(json['totalPages']) ?? 1,
    );
  }

  static int? _asInt(Object? value) {
    if (value is int) {
      return value;
    }
    if (value is num) {
      return value.toInt();
    }
    return int.tryParse(value?.toString() ?? '');
  }
}
