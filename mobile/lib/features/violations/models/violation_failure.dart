enum ViolationFailureKind {
  offline,
  unauthorized,
  forbidden,
  validation,
  conflict,
  server,
  unknown,
}

class ViolationFailure implements Exception {
  final String message;
  final ViolationFailureKind kind;

  const ViolationFailure(
    this.message, {
    this.kind = ViolationFailureKind.unknown,
  });

  bool get isOffline => kind == ViolationFailureKind.offline;

  bool get isConflict => kind == ViolationFailureKind.conflict;

  @override
  String toString() => message;
}
