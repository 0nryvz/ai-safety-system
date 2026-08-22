import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../error/api_failure.dart';
import '../models/auth_tokens.dart';
import '../models/user_summary.dart';
import 'auth_session_store.dart';
import 'backend_client.dart';

/// Operasyon REST çağrılarının tek authenticated giriş noktası.
///
/// Sorumluluk sınırı:
/// - Bearer injection
/// - 401 sonrası **tek** single-flight refresh
/// - refresh sonrası isteği **bir kez** retry
/// - refresh mümkün değilse oturumu geçersiz kılma
/// - 403'te oturumu koruma (yalnız [ApiFailureKind.forbidden] yüzeye çıkar)
///
/// Refresh mekanizması yalnızca burada bulunur; `BackendClient` transport,
/// `AuthController` ise state sahibidir. Dashboard/cameras/violations/users
/// servisleri de bu katmanı kullanır.
class AuthenticatedApi {
  final BackendClient _client;
  final AuthSessionStore _store;

  AuthenticatedApi(this._client, this._store);

  Future<bool>? _inFlightRefresh;

  /// Token isteyen her transport çağrısı için ortak 401/refresh davranışı.
  Future<T> authorized<T>(
    Future<T> Function(String accessToken) call,
  ) async {
    var token = _store.accessToken;

    if (token == null || token.isEmpty) {
      if (!await _refreshOnce()) {
        _store.onSessionInvalid();
        throw ApiFailure.fromStatusCode(401);
      }
      token = _store.accessToken;
      if (token == null || token.isEmpty) {
        _store.onSessionInvalid();
        throw ApiFailure.fromStatusCode(401);
      }
    }

    try {
      return await call(token);
    } on ApiFailure catch (failure) {
      if (failure.kind != ApiFailureKind.unauthenticated) {
        // 403 dahil diğer hatalar oturumu düşürmez.
        rethrow;
      }

      if (!await _refreshOnce()) {
        _store.onSessionInvalid();
        rethrow;
      }

      final refreshed = _store.accessToken;
      if (refreshed == null || refreshed.isEmpty) {
        _store.onSessionInvalid();
        rethrow;
      }

      // Tek retry; ikinci 401'de sonsuz döngü yerine oturum düşer.
      try {
        return await call(refreshed);
      } on ApiFailure catch (retryFailure) {
        if (retryFailure.kind == ApiFailureKind.unauthenticated) {
          _store.onSessionInvalid();
        }
        rethrow;
      }
    }
  }

  Future<http.Response> send({
    required String method,
    required String path,
    Map<String, String>? headers,
    Object? body,
  }) {
    return authorized(
      (accessToken) => _client.sendAuthorized(
        method: method,
        path: path,
        accessToken: accessToken,
        headers: headers,
        body: body,
      ),
    );
  }

  Future<Map<String, dynamic>> getJson(String path) async {
    final response = await send(method: 'GET', path: path);
    return jsonDecode(response.body) as Map<String, dynamic>;
  }

  Future<List<dynamic>> getJsonList(String path) async {
    final response = await send(method: 'GET', path: path);
    return jsonDecode(response.body) as List<dynamic>;
  }

  /// `GET /api/v1/users/me`
  Future<UserSummary> currentUser() {
    return authorized(_client.fetchCurrentUser);
  }

  /// Eşzamanlı 401'lerde tek refresh çalışır (refresh storm yok).
  Future<bool> _refreshOnce() {
    final inFlight = _inFlightRefresh;
    if (inFlight != null) {
      return inFlight;
    }

    final started = _runRefresh();
    _inFlightRefresh = started;

    return started.whenComplete(() {
      if (_inFlightRefresh == started) {
        _inFlightRefresh = null;
      }
    });
  }

  Future<bool> _runRefresh() async {
    final refreshToken = _store.refreshToken;
    if (refreshToken == null || refreshToken.isEmpty) {
      return false;
    }

    try {
      final AuthTokens tokens = await _client.refreshTokens(refreshToken);
      _store.applyRefreshedTokens(tokens);
      return true;
    } on ApiFailure {
      return false;
    } catch (_) {
      return false;
    }
  }
}
