import 'dart:convert';

import 'package:camera_stream_app/features/violations/data/violations_api.dart';
import 'package:camera_stream_app/features/violations/models/violation_failure.dart';
import 'package:camera_stream_app/features/violations/models/violation_filters.dart';
import 'package:camera_stream_app/features/violations/models/violation_lifecycle_status.dart';
import 'package:camera_stream_app/features/violations/models/violation_recording_status.dart';
import 'package:camera_stream_app/features/violations/models/violation_review_status.dart';
import 'package:camera_stream_app/features/violations/models/violation_type.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

ViolationsApi _api(MockClient client) {
  return ViolationsApi.withHttpClient(
    accessToken: 'jwt',
    baseUrl: 'http://backend',
    client: client,
  );
}

void main() {
  group('ViolationsApi', () {
    test('liste page parse ve filtreler encode edilir', () async {
      late Uri captured;
      final api = _api(
        MockClient((request) async {
          captured = request.url;
          expect(request.headers['Authorization'], 'Bearer jwt');
          return http.Response(
            jsonEncode({
              'content': [
                {
                  'violationId': 'v-1',
                  'type': 'MISSING_WELDING_MASK',
                  'lifecycleStatus': 'COMPLETED',
                  'reviewStatus': 'REVIEWED',
                  'recordingStatus': 'READY',
                  'startedAt': '2026-08-22T00:00:00Z',
                },
              ],
              'page': 0,
              'size': 20,
              'totalElements': 1,
              'totalPages': 1,
            }),
            200,
          );
        }),
      );

      final page = await api.fetchViolations(
        filters: ViolationFilters(
          from: DateTime.utc(2026, 8, 22),
          to: DateTime.utc(2026, 8, 22, 23, 59, 59),
          type: ViolationType.missingWeldingMask,
          cameraId: 'cam-1',
          departmentId: 'dep-1',
          lifecycleStatus: ViolationLifecycleStatus.completed,
          reviewStatus: ViolationReviewStatus.reviewed,
          recordingStatus: ViolationRecordingStatus.ready,
        ),
      );

      expect(captured.path, '/api/v1/violations');
      expect(captured.queryParameters['from'], '2026-08-22T00:00:00Z');
      expect(captured.queryParameters['to'], '2026-08-22T23:59:59Z');
      expect(captured.queryParameters['type'], 'MISSING_WELDING_MASK');
      expect(captured.queryParameters['cameraId'], 'cam-1');
      expect(captured.queryParameters['departmentId'], 'dep-1');
      expect(captured.queryParameters['lifecycleStatus'], 'COMPLETED');
      expect(captured.queryParameters['reviewStatus'], 'REVIEWED');
      expect(captured.queryParameters['recordingStatus'], 'READY');
      expect(captured.queryParameters['page'], '0');
      expect(page.content.first.id, 'v-1');
    });

    test('detail parse edilir; playbackUrl kullanılmaz', () async {
      final api = _api(
        MockClient((request) async {
          expect(request.url.path, '/api/v1/violations/v-1');
          return http.Response(
            jsonEncode({
              'violationId': 'v-1',
              'cameraId': 'c-1',
              'cameraName': 'Kaynak 1',
              'cameraCode': 'CAM-01',
              'departmentId': 'd-1',
              'departmentName': 'Üretim',
              'type': 'MISSING_GLOVES',
              'confidence': 0.8,
              'modelVersion': 'yolo-1',
              'detectedAt': '2026-08-22T10:00:00Z',
              'startedAt': '2026-08-22T10:00:00Z',
              'lifecycleStatus': 'COMPLETED',
              'reviewStatus': 'UNREVIEWED',
              'recordingStatus': 'READY',
              'clipReady': true,
              'coverImageReady': true,
              'playbackUrl': 'http://minio/should-not-use',
              'coverImageKey': 'secret-key',
              'version': 4,
            }),
            200,
          );
        }),
      );

      final detail = await api.fetchDetail('v-1');
      expect(detail.id, 'v-1');
      expect(detail.version, 4);
      expect(detail.clipReady, isTrue);
      expect(detail.cameraName, 'Kaynak 1');
    });

    test('review PATCH version gönderir', () async {
      final api = _api(
        MockClient((request) async {
          expect(request.method, 'PATCH');
          expect(request.url.path, '/api/v1/violations/v-1/review');
          final body = jsonDecode(request.body) as Map<String, dynamic>;
          expect(body['reviewStatus'], 'CONFIRMED');
          expect(body['version'], 4);
          return http.Response(
            jsonEncode({
              'violationId': 'v-1',
              'reviewStatus': 'CONFIRMED',
              'version': 5,
            }),
            200,
          );
        }),
      );

      await api.reviewViolation(
        id: 'v-1',
        reviewStatus: ViolationReviewStatus.confirmed,
        version: 4,
      );
    });

    test('409 conflict failure olur', () async {
      final api = _api(
        MockClient((_) async => http.Response('', 409)),
      );

      expect(
        () => api.reviewViolation(
          id: 'v-1',
          reviewStatus: ViolationReviewStatus.confirmed,
          version: 1,
        ),
        throwsA(
          isA<ViolationFailure>().having(
            (e) => e.kind,
            'kind',
            ViolationFailureKind.conflict,
          ),
        ),
      );
    });
  });
}
