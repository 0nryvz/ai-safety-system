import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:uuid/uuid.dart';

import '../config/app_config.dart';
import '../../features/session/camera_option.dart';

/// Kameranın Gateway'e bildirdiği kimlik ve operatör ekranında gösterilen
/// fabrika kamerası meta verisi.
///
/// Öncelik sırası:
/// 1. Kullanıcının Backend 2 listesinden seçtiği kamera (kalıcı)
/// 2. `--dart-define=CAMERA_ID` ile provizyonlanan kamera
/// 3. Cihazda üretilip saklanan geliştirme UUID'si (yalnızca yerel deneme)
class CameraIdentity {
  static const MethodChannel _channel = MethodChannel(
    'camera_stream_app/device_storage',
  );

  static const String _selectedKey = 'selected_camera_id';
  static const String _metaKey = 'selected_camera_meta';
  static const String _devFallbackKey = 'dev_camera_id';
  static const Uuid _uuid = Uuid();

  String? _cameraId;
  String? _cameraName;
  String? _cameraCode;
  String? _departmentName;
  bool _isAssigned = false;

  String? get cameraIdOrNull => _cameraId;
  String? get cameraName => _cameraName;
  String? get cameraCode => _cameraCode;
  String? get departmentName => _departmentName;

  /// Backend listesinden seçilmiş veya derleme ile provizyonlanmış mı.
  /// Geliştirme UUID'si bunu true yapmaz; rastgele kimlikle yayın açılamaz.
  bool get isAssigned => _isAssigned;

  Future<ResolvedCameraIdentity> resolve() async {
    final selected = await _read(_selectedKey);

    if (selected != null && selected.isNotEmpty) {
      _cameraId = selected;
      _isAssigned = true;
      await _loadMeta();
      return _snapshot();
    }

    if (AppConfig.isCameraProvisioned) {
      _cameraId = AppConfig.provisionedCameraId;
      _isAssigned = true;
      _cameraName ??= 'Provizyonlu kamera';
      _cameraCode ??= AppConfig.provisionedCameraId;
      return _snapshot();
    }

    final stored = await _read(_devFallbackKey);
    final id = (stored != null && stored.isNotEmpty)
        ? stored
        : _uuid.v4();

    if (stored == null || stored.isEmpty) {
      await _write(_devFallbackKey, id);
    }

    _cameraId = id;
    _isAssigned = false;
    _cameraName = null;
    _cameraCode = null;
    _departmentName = null;

    return _snapshot();
  }

  String newSessionId() => _uuid.v4();

  /// Backend 2 listesinden seçilen fabrika kamerasını kalıcı olarak bağlar.
  Future<ResolvedCameraIdentity> select(CameraOption camera) async {
    _cameraId = camera.id;
    _cameraName = camera.name;
    _cameraCode = camera.code;
    _departmentName = camera.departmentName;
    _isAssigned = true;

    await _write(_selectedKey, camera.id);
    await _write(
      _metaKey,
      jsonEncode({
        'name': camera.name,
        'code': camera.code,
        'departmentName': camera.departmentName,
      }),
    );

    return _snapshot();
  }

  Future<void> clearAssignment() async {
    _cameraId = null;
    _cameraName = null;
    _cameraCode = null;
    _departmentName = null;
    _isAssigned = false;

    await _write(_selectedKey, '');
    await _write(_metaKey, '');
  }

  Future<void> _loadMeta() async {
    final raw = await _read(_metaKey);
    if (raw == null || raw.isEmpty) {
      return;
    }

    try {
      final json = jsonDecode(raw) as Map<String, dynamic>;
      _cameraName = json['name'] as String?;
      _cameraCode = json['code'] as String?;
      _departmentName = json['departmentName'] as String?;
    } catch (_) {
      // Eski kayıtlar bozulmuş olabilir; kimlik yine de geçerlidir.
    }
  }

  ResolvedCameraIdentity _snapshot() => ResolvedCameraIdentity(
        cameraId: _cameraId!,
        isAssigned: _isAssigned,
        cameraName: _cameraName,
        cameraCode: _cameraCode,
        departmentName: _departmentName,
      );

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

class ResolvedCameraIdentity {
  final String cameraId;
  final bool isAssigned;
  final String? cameraName;
  final String? cameraCode;
  final String? departmentName;

  const ResolvedCameraIdentity({
    required this.cameraId,
    required this.isAssigned,
    this.cameraName,
    this.cameraCode,
    this.departmentName,
  });
}
