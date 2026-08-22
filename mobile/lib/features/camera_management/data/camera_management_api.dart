import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../core/error/api_failure.dart';
import '../../../core/network/authenticated_api.dart';
import '../models/camera_item.dart';
import '../models/camera_management_failure.dart';
import '../models/department_option.dart';

/// Kamera yönetimi REST çağrıları — [AuthenticatedApi] sınırını tüketir.
class CameraManagementApi {
  CameraManagementApi({required this._send});

  final Future<dynamic> Function(String method, String path, {Object? body})
      _send;

  factory CameraManagementApi.fromAuthenticated(AuthenticatedApi api) {
    Future<dynamic> send(
      String method,
      String path, {
      Object? body,
    }) async {
      final response = await api.send(
        method: method,
        path: path,
        body: body,
      );
      if (response.body.isEmpty) {
        return null;
      }
      return jsonDecode(response.body);
    }

    return CameraManagementApi(send: send);
  }

  /// Birim testleri için MockClient köprüsü (production yolu değil).
  factory CameraManagementApi.withHttpClient({
    required String accessToken,
    required http.Client client,
    required String baseUrl,
    Duration timeout = const Duration(seconds: 10),
  }) {
    Future<dynamic> send(
      String method,
      String path, {
      Object? body,
    }) async {
      final uri = Uri.parse('$baseUrl$path');
      final headers = {
        'Authorization': 'Bearer $accessToken',
        'Accept': 'application/json',
        if (body != null) 'Content-Type': 'application/json',
      };
      final encoded =
          body == null ? null : body is String ? body : jsonEncode(body);

      final http.Response response;
      try {
        response = await switch (method.toUpperCase()) {
          'GET' => client.get(uri, headers: headers),
          'POST' => client.post(uri, headers: headers, body: encoded),
          'PUT' => client.put(uri, headers: headers, body: encoded),
          _ => throw UnsupportedError(method),
        }.timeout(timeout);
      } catch (_) {
        throw const CameraManagementFailure(
          'Backend\'e ulaşılamıyor. Ağı kontrol edin.',
          kind: CameraManagementFailureKind.offline,
        );
      }

      if (response.statusCode == 401) {
        throw const CameraManagementFailure(
          'Oturum geçersiz. Tekrar giriş yapın.',
          kind: CameraManagementFailureKind.unauthorized,
        );
      }
      if (response.statusCode == 403) {
        throw const CameraManagementFailure(
          'Bu işlem için yetkiniz yok.',
          kind: CameraManagementFailureKind.forbidden,
        );
      }
      if (response.statusCode == 400 || response.statusCode == 422) {
        throw const CameraManagementFailure(
          'Girilen bilgiler geçersiz.',
          kind: CameraManagementFailureKind.validation,
        );
      }
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw CameraManagementFailure(
          'Kamera işlemi tamamlanamadı (${response.statusCode}).',
          kind: CameraManagementFailureKind.server,
        );
      }
      if (response.body.isEmpty) {
        return null;
      }
      return jsonDecode(response.body);
    }

    return CameraManagementApi(send: send);
  }

  Future<List<CameraItem>> fetchCameras() async {
    final body = await _invoke(
      () => _send('GET', '/api/v1/cameras'),
    );
    return (body as List<dynamic>)
        .cast<Map<String, dynamic>>()
        .map(CameraItem.fromJson)
        .toList(growable: false);
  }

  Future<CameraItem> fetchCamera(String id) async {
    final body = await _invoke(
      () => _send('GET', '/api/v1/cameras/$id'),
    );
    return CameraItem.fromJson(body as Map<String, dynamic>);
  }

  Future<CameraItem> createCamera({
    required String name,
    required String code,
    required String departmentId,
  }) async {
    final body = await _invoke(
      () => _send(
        'POST',
        '/api/v1/cameras',
        body: {
          'name': name,
          'code': code,
          'departmentId': departmentId,
        },
      ),
    );
    return CameraItem.fromJson(body as Map<String, dynamic>);
  }

  Future<CameraItem> updateCamera(
    String id, {
    String? name,
    String? code,
    String? departmentId,
    bool? active,
  }) async {
    final payload = <String, dynamic>{};
    if (name != null) {
      payload['name'] = name;
    }
    if (code != null) {
      payload['code'] = code;
    }
    if (departmentId != null) {
      payload['departmentId'] = departmentId;
    }
    if (active != null) {
      payload['active'] = active;
    }

    final body = await _invoke(
      () => _send('PUT', '/api/v1/cameras/$id', body: payload),
    );
    return CameraItem.fromJson(body as Map<String, dynamic>);
  }

  Future<List<DepartmentOption>> fetchDepartments() async {
    final body = await _invoke(
      () => _send('GET', '/api/v1/users/me/departments'),
    );
    return (body as List<dynamic>)
        .cast<Map<String, dynamic>>()
        .map(DepartmentOption.fromJson)
        .toList(growable: false);
  }

  Future<T> _invoke<T>(Future<T> Function() run) async {
    try {
      return await run();
    } on CameraManagementFailure {
      rethrow;
    } on ApiFailure catch (failure) {
      throw _mapApiFailure(failure);
    } catch (_) {
      throw const CameraManagementFailure(
        'Kamera verisi işlenemedi.',
        kind: CameraManagementFailureKind.unknown,
      );
    }
  }

  static CameraManagementFailure _mapApiFailure(ApiFailure failure) {
    final kind = switch (failure.kind) {
      ApiFailureKind.network => CameraManagementFailureKind.offline,
      ApiFailureKind.unauthenticated =>
        CameraManagementFailureKind.unauthorized,
      ApiFailureKind.forbidden => CameraManagementFailureKind.forbidden,
      ApiFailureKind.validation => CameraManagementFailureKind.validation,
      ApiFailureKind.server => CameraManagementFailureKind.server,
      _ => CameraManagementFailureKind.unknown,
    };

    final message = switch (kind) {
      CameraManagementFailureKind.offline =>
        'Backend\'e ulaşılamıyor. Ağı kontrol edin.',
      CameraManagementFailureKind.unauthorized =>
        'Oturum geçersiz. Tekrar giriş yapın.',
      CameraManagementFailureKind.forbidden =>
        'Bu işlem için yetkiniz yok.',
      CameraManagementFailureKind.validation =>
        failure.message.isNotEmpty
            ? failure.message
            : 'Girilen bilgiler geçersiz.',
      CameraManagementFailureKind.server => failure.statusCode == null
          ? 'Kamera işlemi tamamlanamadı.'
          : 'Kamera işlemi tamamlanamadı (${failure.statusCode}).',
      CameraManagementFailureKind.unknown => failure.message,
    };

    return CameraManagementFailure(message, kind: kind);
  }
}
