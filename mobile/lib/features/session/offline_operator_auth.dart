import 'camera_option.dart';
import 'demo_cameras.dart';

/// Backend 2 kapalıyken mobil tarafın kabul ettiği demo operatör hesabı.
///
/// Değerler `backend/.../demo-seed.sql` ile aynıdır; backend'e dokunulmaz.
/// Gerçek Backend ayaktayken [BackendClient] kullanılır; bu sınıf yalnızca
/// ağ/erişim yokluğunda giriş UX'ini ayakta tutar.
class OfflineOperatorAuth {
  const OfflineOperatorAuth._();

  static const String email = 'admin@isgvision.local';
  static const String password = '123456';

  static bool matches({
    required String email,
    required String password,
  }) {
    return email.trim().toLowerCase() == OfflineOperatorAuth.email &&
        password == OfflineOperatorAuth.password;
  }

  /// Offline giriş sonrası gösterilecek kamera kataloğu (seed UUID'leri).
  static List<CameraOption> cameras() => DemoCameras.catalog;
}
