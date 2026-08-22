enum UserRole {
  admin,
  ohsSpecialist,
  shiftSupervisor;

  static const List<UserRole> canonical = [
    UserRole.admin,
    UserRole.ohsSpecialist,
    UserRole.shiftSupervisor,
  ];

  static UserRole? fromWire(String raw) {
    switch (raw.toUpperCase()) {
      case 'ADMIN':
        return UserRole.admin;
      case 'OHS_SPECIALIST':
        return UserRole.ohsSpecialist;
      case 'SHIFT_SUPERVISOR':
        return UserRole.shiftSupervisor;
      default:
        return null;
    }
  }

  String get wireValue => switch (this) {
        UserRole.admin => 'ADMIN',
        UserRole.ohsSpecialist => 'OHS_SPECIALIST',
        UserRole.shiftSupervisor => 'SHIFT_SUPERVISOR',
      };

  String get label => switch (this) {
        UserRole.admin => 'Yönetici',
        UserRole.ohsSpecialist => 'İSG uzmanı',
        UserRole.shiftSupervisor => 'Vardiya amiri',
      };
}

List<UserRole> parseUserRoles(Iterable<String> raw) {
  return [
    for (final name in raw)
      if (UserRole.fromWire(name) != null) UserRole.fromWire(name)!,
  ];
}
