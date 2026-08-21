import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/app_config.dart';
import '../error/api_failure.dart';
import '../models/auth_tokens.dart';
import '../models/user_summary.dart';
import '../../features/session/camera_option.dart';

enum BackendAuthFailureKind {
  unreachable,
  invalidCredentials,
  other,
}

class BackendAuthException implements Exception {
  final String message;
  final BackendAuthFailureKind kind;

  const BackendAuthException(
    this.message, {
    this.kind = BackendAuthFailureKind.other,
  });

  bool get isUnreachable => kind == BackendAuthFailureKind.unreachable;

  @override
  String toString() => message;
}

/// Spring Boot backend (Backend 2) istemcisi — operasyon REST sınırı.
///
/// Sözleşme:
/// - `POST /api/v1/auth/login` → AuthResponse
/// - `GET  /api/v1/users/me`   → UserResponse
/// - `GET  /api/v1/cameras`    → CameraResponse[]
///
/// Gateway oturum token'ı ayrı kalır ([AppConfig.cameraKey]).
class BackendClient {
  final http.Client _client;
  final String baseUrl;

  BackendClient({
    http.Client? client,
    String? baseUrl,
  })  : _client = client ?? http.Client(),
        baseUrl = baseUrl ?? AppConfig.backendBaseUrl;

  static const Duration _timeout = Duration(seconds: 10);

  /// Mevcut session/operator akışı için accessToken döner.
  Future<String> login({
    required String email,
    required String password,
  }) async {
    final tokens = await loginTokens(email: email, password: password);
    return tokens.accessToken;
  }

  /// AuthResponse'un tamamını döner (O0 AuthSession seed).
  Future<AuthTokens> loginTokens({
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
        kind: BackendAuthFailureKind.unreachable,
      );
    }

    if (response.statusCode == 401 || response.statusCode == 403) {
      throw const BackendAuthException(
        'E-posta veya şifre hatalı.',
        kind: BackendAuthFailureKind.invalidCredentials,
      );
    }

    if (response.statusCode != 200) {
      throw const BackendAuthException(
        'Giriş yapılamadı.',
        kind: BackendAuthFailureKind.other,
      );
    }

    try {
      final body = jsonDecode(response.body) as Map<String, dynamic>;
      return AuthTokens.fromJson(body);
    } on FormatException {
      throw const BackendAuthException('Backend geçerli bir oturum döndürmedi.');
    }
  }

  Future<UserSummary> fetchCurrentUser(String accessToken) async {
    final response = await sendAuthorized(
      method: 'GET',
      path: '/api/v1/users/me',
      accessToken: accessToken,
    );

    final body = jsonDecode(response.body) as Map<String, dynamic>;
    return UserSummary.fromJson(body);
  }

  Future<List<CameraOption>> fetchCameras(String accessToken) async {
    final http.Response response;

    try {
      response = await _client.get(
        Uri.parse('$baseUrl/api/v1/cameras'),
        headers: {'Authorization': 'Bearer $accessToken'},
      ).timeout(_timeout);
    } catch (_) {
      throw const BackendAuthException(
        'Kamera listesi alınamadı.',
        kind: BackendAuthFailureKind.unreachable,
      );
    }

    if (response.statusCode == 401 || response.statusCode == 403) {
      throw const BackendAuthException(
        'Oturum süresi doldu. Tekrar giriş yapın.',
        kind: BackendAuthFailureKind.invalidCredentials,
      );
    }

    if (response.statusCode != 200) {
      throw const BackendAuthException(
        'Kamera listesi alınamadı.',
        kind: BackendAuthFailureKind.other,
      );
    }

    final decoded = jsonDecode(response.body) as List<dynamic>;

    return decoded
        .cast<Map<String, dynamic>>()
        .map(CameraOption.fromJson)
        .toList(growable: false);
  }

  /// Merkezi authenticated istek. Feature'lar kendi http client'ını kurmaz.
  Future<http.Response> sendAuthorized({
    required String method,
    required String path,
    required String accessToken,
    Map<String, String>? headers,
    Object? body,
  }) async {
    final uri = Uri.parse('$baseUrl$path');
    final merged = <String, String>{
      'Authorization': 'Bearer $accessToken',
      if (body != null) 'Content-Type': 'application/json',
      ...?headers,
    };

    final http.Response response;
    try {
      final encoded = body == null
          ? null
          : body is String
              ? body
              : jsonEncode(body);

      response = await switch (method.toUpperCase()) {
        'GET' => _client.get(uri, headers: merged),
        'POST' => _client.post(uri, headers: merged, body: encoded),
        'PUT' => _client.put(uri, headers: merged, body: encoded),
        'PATCH' => _client.patch(uri, headers: merged, body: encoded),
        'DELETE' => _client.delete(uri, headers: merged, body: encoded),
        _ => throw ApiFailure(
            kind: ApiFailureKind.unknown,
            message: 'Desteklenmeyen HTTP metodu: $method',
          ),
      }
          .timeout(_timeout);
    } on ApiFailure {
      rethrow;
    } catch (_) {
      throw ApiFailure.network;
    }

    if (response.statusCode >= 200 && response.statusCode < 300) {
      return response;
    }

    throw ApiFailure.fromStatusCode(response.statusCode);
  }

  void close() => _client.close();
}
