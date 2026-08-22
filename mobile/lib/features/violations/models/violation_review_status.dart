/// Backend `ViolationReviewStatus`.
enum ViolationReviewStatus {
  unreviewed,
  reviewed,
  confirmed,
  falseAlarm,
  unknown;

  static const List<ViolationReviewStatus> canonical = [
    ViolationReviewStatus.unreviewed,
    ViolationReviewStatus.reviewed,
    ViolationReviewStatus.confirmed,
    ViolationReviewStatus.falseAlarm,
  ];

  /// PATCH body'de gönderilebilir değerler (UNREVIEWED yok).
  static const List<ViolationReviewStatus> patchable = [
    ViolationReviewStatus.reviewed,
    ViolationReviewStatus.confirmed,
    ViolationReviewStatus.falseAlarm,
  ];

  static ViolationReviewStatus fromJson(String? raw) {
    switch (raw?.toUpperCase()) {
      case 'UNREVIEWED':
        return ViolationReviewStatus.unreviewed;
      case 'REVIEWED':
        return ViolationReviewStatus.reviewed;
      case 'CONFIRMED':
        return ViolationReviewStatus.confirmed;
      case 'FALSE_ALARM':
        return ViolationReviewStatus.falseAlarm;
      default:
        return ViolationReviewStatus.unknown;
    }
  }

  String get wireValue => switch (this) {
        ViolationReviewStatus.unreviewed => 'UNREVIEWED',
        ViolationReviewStatus.reviewed => 'REVIEWED',
        ViolationReviewStatus.confirmed => 'CONFIRMED',
        ViolationReviewStatus.falseAlarm => 'FALSE_ALARM',
        ViolationReviewStatus.unknown => 'UNKNOWN',
      };
}
