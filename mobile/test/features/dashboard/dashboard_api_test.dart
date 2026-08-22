import 'dart:convert';

import 'package:camera_stream_app/features/dashboard/data/dashboard_api.dart';
import 'package:camera_stream_app/features/dashboard/data/dashboard_repository.dart';
import 'package:camera_stream_app/features/dashboard/models/dashboard_failure.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

DashboardApi _api(MockClient client) {
  return DashboardApi.withHttpClient(
    accessToken: 'jwt',
    baseUrl: 'http://backend',
    client: client,
  );
}

void main() {
  group('DashboardApi', () {
    test('summary success parse edilir', () async {
      final api = _api(
        MockClient((request) async {
          expect(request.url.path, '/api/v1/dashboard/summary');
          expect(request.headers['Authorization'], 'Bearer jwt');
          return http.Response(
            jsonEncode({
              'todayViolationCount': 2,
              'last7DaysViolationCount': 9,
              'mostFrequentViolationType': 'MISSING_GLOVES',
              'activeCameraCount': 4,
              'offlineCameraCount': 1,
              'activeViolationCount': 3,
            }),
            200,
          );
        }),
      );

      final summary = await api.fetchSummary();
      expect(summary.todayViolationCount, 2);
      expect(summary.mostFrequentViolationType, 'MISSING_GLOVES');
      expect(summary.offlineCameraCount, 1);
    });

    test('empty trend listesi kabul edilir', () async {
      final api = _api(
        MockClient((request) async {
          expect(request.url.path, '/api/v1/dashboard/trend');
          expect(request.url.queryParameters['bucket'], 'DAY');
          return http.Response('[]', 200);
        }),
      );

      final trend = await api.fetchTrend(
        from: DateTime.utc(2026, 8, 1),
        to: DateTime.utc(2026, 8, 7),
      );
      expect(trend, isEmpty);
    });

    test('empty distribution listesi kabul edilir', () async {
      final api = _api(
        MockClient((request) async {
          expect(request.url.path, '/api/v1/dashboard/distribution');
          expect(request.url.queryParameters['groupBy'], 'TYPE');
          return http.Response('[]', 200);
        }),
      );

      final items = await api.fetchDistribution();
      expect(items, isEmpty);
    });

    test('ağ hatası offline failure olur', () async {
      final api = _api(
        MockClient((_) => throw Exception('socket')),
      );

      expect(
        () => api.fetchSummary(),
        throwsA(
          isA<DashboardFailure>().having(
            (e) => e.kind,
            'kind',
            DashboardFailureKind.offline,
          ),
        ),
      );
    });

    test('500 server failure olur', () async {
      final api = _api(
        MockClient((_) async => http.Response('', 500)),
      );

      expect(
        () => api.fetchSummary(),
        throwsA(
          isA<DashboardFailure>().having(
            (e) => e.kind,
            'kind',
            DashboardFailureKind.server,
          ),
        ),
      );
    });
  });

  group('DashboardRepository', () {
    test('dört endpoint birleşir', () async {
      final api = _api(
        MockClient((request) async {
          switch (request.url.path) {
            case '/api/v1/dashboard/summary':
              return http.Response(
                jsonEncode({
                  'todayViolationCount': 0,
                  'last7DaysViolationCount': 0,
                  'mostFrequentViolationType': null,
                  'activeCameraCount': 1,
                  'offlineCameraCount': 0,
                  'activeViolationCount': 0,
                }),
                200,
              );
            case '/api/v1/dashboard/trend':
              return http.Response(
                jsonEncode([
                  {'date': '2026-08-20', 'count': 1},
                ]),
                200,
              );
            case '/api/v1/dashboard/distribution':
              return http.Response(
                jsonEncode([
                  {'group': 'MISSING_GLOVES', 'count': 2},
                ]),
                200,
              );
            case '/api/v1/dashboard/recent-violations':
              return http.Response(
                jsonEncode([
                  {
                    'violationId': 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
                    'detectedAt': '2026-08-21T10:00:00Z',
                    'startedAt': '2026-08-21T09:59:00Z',
                    'violationType': 'MISSING_GLOVES',
                    'cameraId': '11111111-1111-1111-1111-111111111111',
                    'departmentId': '22222222-2222-2222-2222-222222222222',
                    'departmentName': 'Kaynak',
                    'cameraName': 'Hat-1',
                    'cameraCode': 'CAM-1',
                    'lifecycleStatus': 'ACTIVE',
                    'reviewStatus': 'UNREVIEWED',
                    'recordingStatus': 'READY',
                    'recordingReadyAt': '2026-08-21T10:05:00Z',
                    'confidence': 0.91,
                    'modelVersion': 'v1',
                  },
                ]),
                200,
              );
            default:
              return http.Response('not found', 404);
          }
        }),
      );

      final snapshot = await DashboardRepository(api: api).load();
      expect(snapshot.summary.activeCameraCount, 1);
      expect(snapshot.trend, hasLength(1));
      expect(snapshot.distribution.first.group, 'MISSING_GLOVES');
      expect(
        snapshot.recentViolations.first.violationId,
        'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
      );
    });
  });
}
