import 'package:flutter/services.dart';
import 'package:uuid/uuid.dart';

import '../config/app_config.dart';

/// Kameranın Gateway'e bildirdiği kimlik.
///
/// Öncelik sırası:
/// 1. Kullanıcının Backend 2 listesinden seçtiği kamera (kalıcı)
/// 2. `--dart-define=CAMERA_ID` ile provizyonlanan kamera
/// 3. Cihazda üretilip saklanan geliştirme UUID'si
///
/// Üçüncü seçenek yalnızca yerel geliştirme içindir: backend'de böyle bir
/// kamera kaydı olmadığından gerçek pipeline'da foreign key hatası verir.
/// Geliştirme kimliği ayrı bir anahtarda tutulur; böylece provizyonlanmış bir
/// derleme eski bir dev UUID tarafından ele geçirilmez.
class CameraIdentity {
  static const MethodChannel _channel = MethodChannel(
    'camera_stream_app/device_storage',
  );

  static const String _selectedKey = 'selected_camera_id';
  static const String _devFallbackKey = 'dev_camera_id';
  static const Uuid _uuid = Uuid();

  String? _cameraId;
  bool _isProvisioned = false;

  String? get cameraIdOrNull => _cameraId;

  /// Gerçek bir kamera kaydına mı bağlı, yoksa yerel geliştirme kimliği mi.
  bool get isProvisioned => _isProvisioned;

  Future<String> resolve() async {
    final cached = _cameraId;
    if (cached != null) {
      return cached;
    }

    final selected = await _read(_selectedKey);

    if (selected != null && selected.isNotEmpty) {
      _isProvisioned = true;
      _cameraId = selected;
      return selected;
    }

    if (AppConfig.isCameraProvisioned) {
      _isProvisioned = true;
      _cameraId = AppConfig.provisionedCameraId;
      return _cameraId!;
    }

    final stored = await _read(_devFallbackKey);

    if (stored != null && stored.isNotEmpty) {
      _cameraId = stored;
      return stored;
    }

    final generated = _uuid.v4();
    await _write(_devFallbackKey, generated);
    _cameraId = generated;

    return generated;
  }

  /// Her yayın için yeni oturum kimliği. `camera_sessions.id` uuid tipinde.
  String newSessionId() => _uuid.v4();

  /// Kullanıcı Backend 2 listesinden kamera seçtiğinde çağrılır.
  Future<void> select(String cameraId) async {
    _cameraId = cameraId;
    _isProvisioned = true;
    await _write(_selectedKey, cameraId);
  }

  Future<String?> _read(String key) async {
    try {
      return await _channel.invokeMethod<String>('read', {'key': key});
    } on PlatformException {
      return null;
    } on MissingPluginException {
      return null;
    }
  }

  Future<void> _write(String key, String value) async {
    try {
      await _channel.invokeMethod<void>('write', {
        'key': key,
        'value': value,
      });
    } on PlatformException {
      // Kalıcılık başarısızsa kimlik oturum boyunca geçerli kalır.
    } on MissingPluginException {
      // Kalıcılık başarısızsa kimlik oturum boyunca geçerli kalır.
    }
  }
}
