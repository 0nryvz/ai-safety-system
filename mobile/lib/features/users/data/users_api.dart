import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../core/error/api_failure.dart';
import '../../../core/models/user_summary.dart';
import '../../../core/network/authenticated_api.dart';
import '../models/user_department_option.dart';
import '../models/user_failure.dart';

/// Kullanıcı yönetimi REST — [AuthenticatedApi] sınırını tüketir.
class UsersApi {
  UsersApi({required this._send});

  final Future<dynamic> Function(String method, String path, {Object? body})
      _send;

  factory UsersApi.fromAuthenticated(AuthenticatedApi api) {
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

    return UsersApi(send: send);
  }

  factory UsersApi.withHttpClient({
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
          'PATCH' => client.patch(uri, headers: headers, body: encoded),
          'DELETE' => client.delete(uri, headers: headers),
          _ => throw UnsupportedError(method),
        }.timeout(timeout);
      } catch (_) {
        throw const UserFailure(
          'Backend\'e ulaşılamıyor. Ağı kontrol edin.',
          kind: UserFailureKind.offline,
        );
      }

      if (response.statusCode == 401) {
        throw const UserFailure(
          'Oturum geçersiz. Tekrar giriş yapın.',
          kind: UserFailureKind.unauthorized,
        );
      }
      if (response.statusCode == 403) {
        throw const UserFailure(
          'Bu işlem için yetkiniz yok.',
          kind: UserFailureKind.forbidden,
        );
      }
      if (response.statusCode == 409) {
        throw const UserFailure(
          'Bu e-posta adresi zaten kullanımda.',
          kind: UserFailureKind.conflict,
        );
      }
      if (response.statusCode == 400 || response.statusCode == 422) {
        throw const UserFailure(
          'Girilen bilgiler geçersiz.',
          kind: UserFailureKind.validation,
        );
      }
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw UserFailure(
          'Kullanıcı işlemi tamamlanamadı (${response.statusCode}).',
          kind: UserFailureKind.server,
        );
      }
      if (response.body.isEmpty) {
        return null;
      }
      return jsonDecode(response.body);
    }

    return UsersApi(send: send);
  }

  Future<List<UserSummary>> fetchUsers() async {
    final body = await _invoke(() => _send('GET', '/api/v1/users'));
    return (body as List<dynamic>)
        .cast<Map<String, dynamic>>()
        .map(UserSummary.fromJson)
        .toList(growable: false);
  }

  Future<UserSummary> fetchUser(String id) async {
    final body = await _invoke(() => _send('GET', '/api/v1/users/$id'));
    return UserSummary.fromJson(body as Map<String, dynamic>);
  }

  Future<UserSummary> createUser({
    required String email,
    required String password,
    required String fullName,
    required List<String> roleNames,
    required List<String> departmentIds,
  }) async {
    final body = await _invoke(
      () => _send(
        'POST',
        '/api/v1/users',
        body: {
          'email': email,
          'password': password,
          'fullName': fullName,
          'roleNames': roleNames,
          'departmentIds': departmentIds,
        },
      ),
    );
    return UserSummary.fromJson(body as Map<String, dynamic>);
  }

  Future<UserSummary> updateUser(
    String id, {
    String? fullName,
    List<String>? roleNames,
    List<String>? departmentIds,
    bool? active,
  }) async {
    final payload = <String, dynamic>{};
    if (fullName != null) {
      payload['fullName'] = fullName;
    }
    if (roleNames != null) {
      payload['roleNames'] = roleNames;
    }
    if (departmentIds != null) {
      payload['departmentIds'] = departmentIds;
    }
    if (active != null) {
      payload['active'] = active;
    }

    final body = await _invoke(
      () => _send('PATCH', '/api/v1/users/$id', body: payload),
    );
    return UserSummary.fromJson(body as Map<String, dynamic>);
  }

  /// Backend deactivate semantic — 204.
  Future<void> deactivateUser(String id) async {
    await _invoke(() => _send('DELETE', '/api/v1/users/$id'));
  }

  Future<List<UserDepartmentOption>> fetchDepartments() async {
    final body = await _invoke(
      () => _send('GET', '/api/v1/users/me/departments'),
    );
    return (body as List<dynamic>)
        .cast<Map<String, dynamic>>()
        .map(UserDepartmentOption.fromJson)
        .toList(growable: false);
  }

  Future<T> _invoke<T>(Future<T> Function() run) async {
    try {
      return await run();
    } on UserFailure {
      rethrow;
    } on ApiFailure catch (failure) {
      throw _mapApiFailure(failure);
    } catch (_) {
      throw const UserFailure(
        'Kullanıcı verisi işlenemedi.',
        kind: UserFailureKind.unknown,
      );
    }
  }

  static UserFailure _mapApiFailure(ApiFailure failure) {
    final kind = switch (failure.kind) {
      ApiFailureKind.network => UserFailureKind.offline,
      ApiFailureKind.unauthenticated => UserFailureKind.unauthorized,
      ApiFailureKind.forbidden => UserFailureKind.forbidden,
      ApiFailureKind.validation => UserFailureKind.validation,
      ApiFailureKind.conflict => UserFailureKind.conflict,
      ApiFailureKind.server => UserFailureKind.server,
      _ => UserFailureKind.unknown,
    };

    final message = switch (kind) {
      UserFailureKind.offline => 'Backend\'e ulaşılamıyor. Ağı kontrol edin.',
      UserFailureKind.unauthorized => 'Oturum geçersiz. Tekrar giriş yapın.',
      UserFailureKind.forbidden => 'Bu işlem için yetkiniz yok.',
      UserFailureKind.validation => 'Girilen bilgiler geçersiz.',
      UserFailureKind.conflict => 'Bu e-posta adresi zaten kullanımda.',
      UserFailureKind.server => failure.statusCode == null
          ? 'Kullanıcı işlemi tamamlanamadı.'
          : 'Kullanıcı işlemi tamamlanamadı (${failure.statusCode}).',
      UserFailureKind.unknown => failure.message,
    };

    return UserFailure(message, kind: kind);
  }
}
