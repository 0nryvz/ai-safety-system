import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/app_config.dart';
import '../../features/session/camera_option.dart';

class BackendAuthException implements Exception {
  final String message;
  const BackendAuthException(this.message);

  @override
  String toString() => message;
}

/// Spring Boot backend (Backend 2) istemcisi.
///
/// Sözleşme:
/// - `POST /api/v1/auth/login` → `{accessToken, refreshToken, tokenType}`
/// - `GET  /api/v1/cameras`    → `CameraResponse[]`, `Authorization: Bearer`
///
/// Backend yalnızca kamera seçimi için kullanılır. Gateway oturum token'ı MVP
/// boyunca sabittir ve backend'den alınmaz (bkz. [AppConfig.cameraKey]).
class BackendClient {
  final http.Client _client;
  final String baseUrl;

  BackendClient({
    http.Client? client,
    String? baseUrl,
  })  : _client = client ?? http.Client(),
        baseUrl = baseUrl ?? AppConfig.backendBaseUrl;

  static const Duration _timeout = Duration(seconds: 10);

  /// JWT 15 dakika geçerli. Token yalnızca bellekte tutulur, diske yazılmaz.
  Future<String> login({
    required String email,
    required String password,
  }) async {
    final http.Response response;

    try {
      response = await _client
          .post(
            Uri.parse('$baseUrl/api/v1/auth/login'),
            headers: const {'Content-Type': 'application/json'},
            body: jsonEncode({'email': email, 'password': password}),
          )
          .timeout(_timeout);
    } catch (_) {
      throw const BackendAuthException(
        'Backend\'e ulaşılamıyor. Adresi ve ağı kontrol edin.',
      );
    }

    if (response.statusCode == 401 || response.statusCode == 403) {
      throw const BackendAuthException('E-posta veya şifre hatalı.');
    }

    if (response.statusCode != 200) {
      throw const BackendAuthException('Giriş yapılamadı.');
    }

    final body = jsonDecode(response.body) as Map<String, dynamic>;
    final token = body['accessToken'] as String?;

    if (token == null || token.isEmpty) {
      throw const BackendAuthException('Backend geçerli bir oturum döndürmedi.');
    }

    return token;
  }

  Future<List<CameraOption>> fetchCameras(String accessToken) async {
    final http.Response response;

    try {
      response = await _client.get(
        Uri.parse('$baseUrl/api/v1/cameras'),
        headers: {'Authorization': 'Bearer $accessToken'},
      ).timeout(_timeout);
    } catch (_) {
      throw const BackendAuthException('Kamera listesi alınamadı.');
    }

    if (response.statusCode == 401 || response.statusCode == 403) {
      throw const BackendAuthException(
        'Oturum süresi doldu. Tekrar giriş yapın.',
      );
    }

    if (response.statusCode != 200) {
      throw const BackendAuthException('Kamera listesi alınamadı.');
    }

    final decoded = jsonDecode(response.body) as List<dynamic>;

    return decoded
        .cast<Map<String, dynamic>>()
        .map(CameraOption.fromJson)
        .toList(growable: false);
  }

  void close() => _client.close();
}
