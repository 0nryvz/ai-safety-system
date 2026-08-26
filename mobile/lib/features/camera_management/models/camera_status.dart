/// Backend `CameraResponse.status` — ONLINE | WEAK | OFFLINE.
enum CameraStatus {
  online,
  weak,
  offline,
  unknown;

  static CameraStatus fromJson(String? raw) {
    switch (raw?.toUpperCase()) {
      case 'ONLINE':
        return CameraStatus.online;
      case 'WEAK':
        return CameraStatus.weak;
      case 'OFFLINE':
        return CameraStatus.offline;
      default:
        return CameraStatus.unknown;
    }
  }

  String get wireValue => switch (this) {
        CameraStatus.online => 'ONLINE',
        CameraStatus.weak => 'WEAK',
        CameraStatus.offline => 'OFFLINE',
        CameraStatus.unknown => 'UNKNOWN',
      };

  String get label => switch (this) {
        CameraStatus.online => 'Çevrimiçi',
        CameraStatus.weak => 'Zayıf',
        CameraStatus.offline => 'Çevrimdışı',
        CameraStatus.unknown => 'Bilinmiyor',
      };
}
