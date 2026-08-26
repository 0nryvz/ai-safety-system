enum CameraManagementFailureKind {
  offline,
  unauthorized,
  forbidden,
  validation,
  server,
  unknown,
}

class CameraManagementFailure implements Exception {
  final String message;
  final CameraManagementFailureKind kind;

  const CameraManagementFailure(
    this.message, {
    this.kind = CameraManagementFailureKind.unknown,
  });

  bool get isOffline => kind == CameraManagementFailureKind.offline;

  bool get isForbidden => kind == CameraManagementFailureKind.forbidden;

  @override
  String toString() => message;
}
