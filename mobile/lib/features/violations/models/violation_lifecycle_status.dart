/// Backend `ViolationLifecycleStatus`.
enum ViolationLifecycleStatus {
  active,
  preparing,
  completed,
  error,
  unknown;

  static const List<ViolationLifecycleStatus> canonical = [
    ViolationLifecycleStatus.active,
    ViolationLifecycleStatus.preparing,
    ViolationLifecycleStatus.completed,
    ViolationLifecycleStatus.error,
  ];

  static ViolationLifecycleStatus fromJson(String? raw) {
    switch (raw?.toUpperCase()) {
      case 'ACTIVE':
        return ViolationLifecycleStatus.active;
      case 'PREPARING':
        return ViolationLifecycleStatus.preparing;
      case 'COMPLETED':
        return ViolationLifecycleStatus.completed;
      case 'ERROR':
        return ViolationLifecycleStatus.error;
      default:
        return ViolationLifecycleStatus.unknown;
    }
  }

  String get wireValue => switch (this) {
        ViolationLifecycleStatus.active => 'ACTIVE',
        ViolationLifecycleStatus.preparing => 'PREPARING',
        ViolationLifecycleStatus.completed => 'COMPLETED',
        ViolationLifecycleStatus.error => 'ERROR',
        ViolationLifecycleStatus.unknown => 'UNKNOWN',
      };
}
