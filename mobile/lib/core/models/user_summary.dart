/// `GET /api/v1/users/me` — backend [UserResponse] alanları.
class UserSummary {
  final String id;
  final String email;
  final String fullName;
  final bool active;
  final String? departmentId;
  final String? departmentName;
  final Set<String> roles;
  final Set<String> departmentIds;
  final DateTime? createdAt;

  const UserSummary({
    required this.id,
    required this.email,
    required this.fullName,
    required this.active,
    this.departmentId,
    this.departmentName,
    this.roles = const {},
    this.departmentIds = const {},
    this.createdAt,
  });

  bool get isAdmin => roles.contains('ADMIN');

  factory UserSummary.fromJson(Map<String, dynamic> json) {
    return UserSummary(
      id: json['id'] as String,
      email: (json['email'] as String?) ?? '',
      fullName: (json['fullName'] as String?) ?? '',
      active: (json['active'] as bool?) ?? false,
      departmentId: json['departmentId'] as String?,
      departmentName: json['departmentName'] as String?,
      roles: _stringSet(json['roles']),
      departmentIds: _stringSet(json['departmentIds']),
      createdAt: _parseInstant(json['createdAt']),
    );
  }

  static Set<String> _stringSet(Object? raw) {
    if (raw is! Iterable) {
      return const {};
    }
    return raw.map((e) => e.toString()).toSet();
  }

  static DateTime? _parseInstant(Object? raw) {
    if (raw is! String || raw.isEmpty) {
      return null;
    }
    return DateTime.tryParse(raw);
  }
}
