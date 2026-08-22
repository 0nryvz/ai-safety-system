enum UserFailureKind {
  offline,
  unauthorized,
  forbidden,
  validation,
  conflict,
  server,
  unknown,
}

class UserFailure implements Exception {
  final String message;
  final UserFailureKind kind;

  const UserFailure(
    this.message, {
    this.kind = UserFailureKind.unknown,
  });

  bool get isOffline => kind == UserFailureKind.offline;

  bool get isForbidden => kind == UserFailureKind.forbidden;

  @override
  String toString() => message;
}
