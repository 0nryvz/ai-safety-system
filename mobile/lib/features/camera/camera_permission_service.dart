import 'package:flutter/services.dart';

/// Kamera izninin dört durumu. `denied` tekrar sorulabilir, `permanentlyDenied`
/// yalnızca uygulama ayarlarından açılabilir.
enum CameraPermissionStatus {
  granted,
  denied,
  permanentlyDenied,

  /// Platform kanalı yoksa (ör. test ortamı) izin durumu bilinemez; akış
  /// kameranın kendi hatasına bırakılır.
  unknown,
}

/// Android çalışma zamanı izinlerini native tarafa sorar.
///
/// `camera` eklentisi izni yalnızca `initialize()` sırasında örtük olarak
/// ister ve kalıcı ret ile geçici reti ayırt etmez. Ayarlara yönlendirme
/// yapabilmek için bu ayrımın açıkça bilinmesi gerekiyor.
class CameraPermissionService {
  static const MethodChannel _channel = MethodChannel(
    'camera_stream_app/permissions',
  );

  const CameraPermissionService();

  Future<CameraPermissionStatus> check() =>
      _invoke('checkCameraPermission');

  Future<CameraPermissionStatus> request() =>
      _invoke('requestCameraPermission');

  /// Kalıcı rette kullanıcıyı uygulama ayarlarına götürür.
  Future<bool> openAppSettings() async {
    try {
      final opened = await _channel.invokeMethod<bool>('openAppSettings');
      return opened ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  Future<CameraPermissionStatus> _invoke(String method) async {
    try {
      final status = await _channel.invokeMethod<String>(method);
      return _parse(status);
    } on PlatformException {
      return CameraPermissionStatus.unknown;
    } on MissingPluginException {
      return CameraPermissionStatus.unknown;
    }
  }

  CameraPermissionStatus _parse(String? status) => switch (status) {
        'granted' => CameraPermissionStatus.granted,
        'denied' => CameraPermissionStatus.denied,
        'permanentlyDenied' => CameraPermissionStatus.permanentlyDenied,
        _ => CameraPermissionStatus.unknown,
      };
}
