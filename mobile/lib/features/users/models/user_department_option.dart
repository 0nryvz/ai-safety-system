class UserDepartmentOption {
  final String id;
  final String name;

  const UserDepartmentOption({
    required this.id,
    required this.name,
  });

  factory UserDepartmentOption.fromJson(Map<String, dynamic> json) {
    return UserDepartmentOption(
      id: json['id'] as String,
      name: (json['name'] as String?) ?? 'Departman',
    );
  }
}
