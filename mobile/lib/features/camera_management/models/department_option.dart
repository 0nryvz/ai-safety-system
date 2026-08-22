/// `GET /api/v1/users/me/departments` kaydı.
class DepartmentOption {
  final String id;
  final String name;

  const DepartmentOption({
    required this.id,
    required this.name,
  });

  factory DepartmentOption.fromJson(Map<String, dynamic> json) {
    return DepartmentOption(
      id: json['id'] as String,
      name: (json['name'] as String?) ?? 'Departman',
    );
  }
}
