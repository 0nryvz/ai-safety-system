import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../core/error/api_failure.dart';
import '../../../core/network/authenticated_api.dart';
import '../models/violation_detail.dart';
import '../models/violation_failure.dart';
import '../models/violation_filter_option.dart';
import '../models/violation_filters.dart';
import '../models/violation_page.dart';
import '../models/violation_review_status.dart';

/// İhlal REST çağrıları — [AuthenticatedApi] sınırını tüketir.
class ViolationsApi {
  ViolationsApi({required this._send});

  final Future<dynamic> Function(String method, String path, {Object? body})
      _send;

  factory ViolationsApi.fromAuthenticated(AuthenticatedApi api) {
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

    return ViolationsApi(send: send);
  }

  factory ViolationsApi.withHttpClient({
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
          _ => throw UnsupportedError(method),
        }.timeout(timeout);
      } catch (_) {
        throw const ViolationFailure(
          'Backend\'e ulaşılamıyor. Ağı kontrol edin.',
          kind: ViolationFailureKind.offline,
        );
      }

      if (response.statusCode == 401) {
        throw const ViolationFailure(
          'Oturum geçersiz. Tekrar giriş yapın.',
          kind: ViolationFailureKind.unauthorized,
        );
      }
      if (response.statusCode == 403) {
        throw const ViolationFailure(
          'Bu işlem için yetkiniz yok.',
          kind: ViolationFailureKind.forbidden,
        );
      }
      if (response.statusCode == 409) {
        throw const ViolationFailure(
          'Kayıt değişmiş. Güncel hali yükleniyor.',
          kind: ViolationFailureKind.conflict,
        );
      }
      if (response.statusCode == 400 || response.statusCode == 422) {
        throw const ViolationFailure(
          'Filtre veya inceleme bilgileri geçersiz.',
          kind: ViolationFailureKind.validation,
        );
      }
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw ViolationFailure(
          'İhlal işlemi tamamlanamadı (${response.statusCode}).',
          kind: ViolationFailureKind.server,
        );
      }
      if (response.body.isEmpty) {
        return null;
      }
      return jsonDecode(response.body);
    }

    return ViolationsApi(send: send);
  }

  Future<ViolationPage> fetchViolations({
    ViolationFilters filters = ViolationFilters.empty,
    int page = 0,
    int size = 20,
  }) async {
    final query = filters.toQueryParameters(page: page, size: size);
    final path = Uri(
      path: '/api/v1/violations',
      queryParameters: query,
    ).toString();
    final body = await _invoke(() => _send('GET', path));
    return ViolationPage.fromJson(body as Map<String, dynamic>);
  }

  Future<ViolationDetail> fetchDetail(String id) async {
    final body = await _invoke(
      () => _send('GET', '/api/v1/violations/$id'),
    );
    return ViolationDetail.fromJson(body as Map<String, dynamic>);
  }

  Future<void> reviewViolation({
    required String id,
    required ViolationReviewStatus reviewStatus,
    required int version,
  }) async {
    await _invoke(
      () => _send(
        'PATCH',
        '/api/v1/violations/$id/review',
        body: {
          'reviewStatus': reviewStatus.wireValue,
          'version': version,
        },
      ),
    );
  }

  Future<List<ViolationFilterOption>> fetchCameras() async {
    final body = await _invoke(() => _send('GET', '/api/v1/cameras'));
    return (body as List<dynamic>)
        .cast<Map<String, dynamic>>()
        .map(ViolationFilterOption.fromJson)
        .toList(growable: false);
  }

  Future<List<ViolationFilterOption>> fetchDepartments() async {
    final body = await _invoke(
      () => _send('GET', '/api/v1/users/me/departments'),
    );
    return (body as List<dynamic>)
        .cast<Map<String, dynamic>>()
        .map(ViolationFilterOption.fromJson)
        .toList(growable: false);
  }

  Future<T> _invoke<T>(Future<T> Function() run) async {
    try {
      return await run();
    } on ViolationFailure {
      rethrow;
    } on ApiFailure catch (failure) {
      throw _mapApiFailure(failure);
    } catch (_) {
      throw const ViolationFailure(
        'İhlal verisi işlenemedi.',
        kind: ViolationFailureKind.unknown,
      );
    }
  }

  static ViolationFailure _mapApiFailure(ApiFailure failure) {
    final kind = switch (failure.kind) {
      ApiFailureKind.network => ViolationFailureKind.offline,
      ApiFailureKind.unauthenticated => ViolationFailureKind.unauthorized,
      ApiFailureKind.forbidden => ViolationFailureKind.forbidden,
      ApiFailureKind.validation => ViolationFailureKind.validation,
      ApiFailureKind.conflict => ViolationFailureKind.conflict,
      ApiFailureKind.server => ViolationFailureKind.server,
      _ => ViolationFailureKind.unknown,
    };

    final message = switch (kind) {
      ViolationFailureKind.offline =>
        'Backend\'e ulaşılamıyor. Ağı kontrol edin.',
      ViolationFailureKind.unauthorized =>
        'Oturum geçersiz. Tekrar giriş yapın.',
      ViolationFailureKind.forbidden => 'Bu işlem için yetkiniz yok.',
      ViolationFailureKind.validation =>
        failure.message.isNotEmpty
            ? failure.message
            : 'Filtre veya inceleme bilgileri geçersiz.',
      ViolationFailureKind.conflict =>
        'Kayıt değişmiş. Güncel hali yükleniyor.',
      ViolationFailureKind.server => failure.statusCode == null
          ? 'İhlal işlemi tamamlanamadı.'
          : 'İhlal işlemi tamamlanamadı (${failure.statusCode}).',
      ViolationFailureKind.unknown => failure.message,
    };

    return ViolationFailure(message, kind: kind);
  }
}
