import 'dart:convert';

import 'package:http/http.dart' as http;

class ApiClient {
  static const String baseUrl = 'http://10.0.2.2:8000';

  Future<http.Response> postJson({
    required String path,
    required Map<String, dynamic> body,
  }) async {
    final uri = Uri.parse('$baseUrl$path');

    return http.post(
      uri,
      headers: {
        'Content-Type': 'application/json',
      },
      body: jsonEncode(body),
    );
  }

  Future<http.Response> postJpeg({
    required String path,
    required String cameraId,
    required DateTime frameTimestamp,
    required List<int> jpegBytes,
  }) async {
    final uri = Uri.parse('$baseUrl$path');

    return http.post(
      uri,
      headers: {
        'Content-Type': 'image/jpeg',
        'X-Camera-Id': cameraId,
        'X-Frame-Timestamp': frameTimestamp.toUtc().toIso8601String(),
      },
      body: jpegBytes,
    );
  }
}