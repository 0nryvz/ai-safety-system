import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/app_config.dart';

class ApiClient {
  static const String baseUrl = AppConfig.gatewayBaseUrl;

  /// Keep-alive: her frame için yeni TCP bağlantısı açmamak için.
  final http.Client _client;

  static const Duration jsonTimeout = Duration(seconds: 8);
  static const Duration jpegTimeout = Duration(seconds: 12);

  ApiClient({
    http.Client? client,
  }) : _client = client ?? http.Client();

  Future<http.Response> postJson({
    required String path,
    required Map<String, dynamic> body,
  }) async {
    final uri = Uri.parse('$baseUrl$path');

    return _client
        .post(
          uri,
          headers: {
            'Content-Type': 'application/json',
            'Connection': 'keep-alive',
          },
          body: jsonEncode(body),
        )
        .timeout(jsonTimeout);
  }

  Future<http.Response> postHeartbeat({
    required String path,
    required String cameraId,
  }) async {
    final uri = Uri.parse('$baseUrl$path');

    return _client
        .post(
          uri,
          headers: {
            'Content-Type': 'application/json',
            'Connection': 'keep-alive',
          },
          body: jsonEncode({
            'cameraId': cameraId,
          }),
        )
        .timeout(jsonTimeout);
  }

  Future<http.Response> postClose({
    required String path,
    required String cameraId,
  }) async {
    final uri = Uri.parse('$baseUrl$path');

    return _client
        .post(
          uri,
          headers: {
            'Content-Type': 'application/json',
            'Connection': 'keep-alive',
          },
          body: jsonEncode({
            'cameraId': cameraId,
          }),
        )
        .timeout(jsonTimeout);
  }

  Future<http.Response> postJpeg({
    required String path,
    required String cameraId,
    required DateTime frameTimestamp,
    required List<int> jpegBytes,
  }) async {
    final uri = Uri.parse('$baseUrl$path');

    return _client
        .post(
          uri,
          headers: {
            'Content-Type': 'image/jpeg',
            'Connection': 'keep-alive',
            'X-Camera-Id': cameraId,
            'X-Frame-Timestamp': frameTimestamp.toUtc().toIso8601String(),
          },
          body: jpegBytes,
        )
        .timeout(jpegTimeout);
  }

  void close() {
    _client.close();
  }
}
