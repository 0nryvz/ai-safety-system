import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../../core/error/api_failure.dart';
import '../../../core/network/authenticated_api.dart';
import '../models/dashboard_distribution_item.dart';
import '../models/dashboard_failure.dart';
import '../models/dashboard_summary.dart';
import '../models/dashboard_trend_point.dart';
import '../models/recent_violation_item.dart';

/// Dashboard REST çağrıları — Onur'un [AuthenticatedApi] sınırını tüketir.
class DashboardApi {
  final Future<dynamic> Function(String path) _get;

  DashboardApi({required this._get});

  factory DashboardApi.fromAuthenticated(AuthenticatedApi api) {
    return DashboardApi(
      get: (path) async {
        final response = await api.send(method: 'GET', path: path);
        if (response.body.isEmpty) {
          return null;
        }
        return jsonDecode(response.body);
      },
    );
  }

  /// Birim testleri için MockClient köprüsü (production yolu değil).
  factory DashboardApi.withHttpClient({
    required String accessToken,
    required http.Client client,
    required String baseUrl,
    Duration timeout = const Duration(seconds: 10),
  }) {
    return DashboardApi(
      get: (path) async {
        final uri = Uri.parse('$baseUrl$path');
        final http.Response response;
        try {
          response = await client
              .get(
                uri,
                headers: {
                  'Authorization': 'Bearer $accessToken',
                  'Accept': 'application/json',
                },
              )
              .timeout(timeout);
        } catch (_) {
          throw const DashboardFailure(
            'Backend\'e ulaşılamıyor. Ağı kontrol edin.',
            kind: DashboardFailureKind.offline,
          );
        }

        if (response.statusCode == 401) {
          throw const DashboardFailure(
            'Oturum geçersiz. Tekrar giriş yapın.',
            kind: DashboardFailureKind.unauthorized,
          );
        }
        if (response.statusCode == 403) {
          throw const DashboardFailure(
            'Bu dashboard verisine erişim yetkiniz yok.',
            kind: DashboardFailureKind.forbidden,
          );
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
          throw DashboardFailure(
            'Dashboard verisi alınamadı (${response.statusCode}).',
            kind: DashboardFailureKind.server,
          );
        }
        if (response.body.isEmpty) {
          return null;
        }
        return jsonDecode(response.body);
      },
    );
  }

  Future<DashboardSummary> fetchSummary() async {
    final body = await _invoke(() => _get('/api/v1/dashboard/summary'));
    return DashboardSummary.fromJson(body as Map<String, dynamic>);
  }

  Future<List<DashboardTrendPoint>> fetchTrend({
    required DateTime from,
    required DateTime to,
    String bucket = 'DAY',
  }) async {
    final path =
        '/api/v1/dashboard/trend?from=${_formatDate(from)}&to=${_formatDate(to)}&bucket=$bucket';
    final body = await _invoke(() => _get(path));
    return (body as List<dynamic>)
        .cast<Map<String, dynamic>>()
        .map(DashboardTrendPoint.fromJson)
        .toList(growable: false);
  }

  Future<List<DashboardDistributionItem>> fetchDistribution({
    String groupBy = 'TYPE',
  }) async {
    final body = await _invoke(
      () => _get('/api/v1/dashboard/distribution?groupBy=$groupBy'),
    );
    return (body as List<dynamic>)
        .cast<Map<String, dynamic>>()
        .map(DashboardDistributionItem.fromJson)
        .toList(growable: false);
  }

  Future<List<RecentViolationItem>> fetchRecentViolations() async {
    final body = await _invoke(() => _get('/api/v1/dashboard/recent-violations'));
    return (body as List<dynamic>)
        .cast<Map<String, dynamic>>()
        .map(RecentViolationItem.fromJson)
        .toList(growable: false);
  }

  Future<T> _invoke<T>(Future<T> Function() run) async {
    try {
      return await run();
    } on DashboardFailure {
      rethrow;
    } on ApiFailure catch (failure) {
      throw _mapApiFailure(failure);
    } catch (_) {
      throw const DashboardFailure(
        'Dashboard verisi işlenemedi.',
        kind: DashboardFailureKind.unknown,
      );
    }
  }

  static DashboardFailure _mapApiFailure(ApiFailure failure) {
    final kind = switch (failure.kind) {
      ApiFailureKind.network => DashboardFailureKind.offline,
      ApiFailureKind.unauthenticated => DashboardFailureKind.unauthorized,
      ApiFailureKind.forbidden => DashboardFailureKind.forbidden,
      ApiFailureKind.server => DashboardFailureKind.server,
      _ => DashboardFailureKind.unknown,
    };

    final message = switch (kind) {
      DashboardFailureKind.offline =>
        'Backend\'e ulaşılamıyor. Ağı kontrol edin.',
      DashboardFailureKind.unauthorized =>
        'Oturum geçersiz. Tekrar giriş yapın.',
      DashboardFailureKind.forbidden =>
        'Bu dashboard verisine erişim yetkiniz yok.',
      DashboardFailureKind.server => failure.statusCode == null
          ? 'Dashboard verisi alınamadı.'
          : 'Dashboard verisi alınamadı (${failure.statusCode}).',
      DashboardFailureKind.unknown => failure.message,
    };

    return DashboardFailure(message, kind: kind);
  }

  static String _formatDate(DateTime value) {
    final y = value.year.toString().padLeft(4, '0');
    final m = value.month.toString().padLeft(2, '0');
    final d = value.day.toString().padLeft(2, '0');
    return '$y-$m-$d';
  }
}
