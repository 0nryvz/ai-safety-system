class ViolationFilterOption {
  final String id;
  final String name;

  const ViolationFilterOption({
    required this.id,
    required this.name,
  });

  factory ViolationFilterOption.fromJson(Map<String, dynamic> json) {
    return ViolationFilterOption(
      id: json['id'] as String,
      name: (json['name'] as String?) ?? '—',
    );
  }
}
