import 'dart:convert';

import 'package:camera_stream_app/features/camera_management/data/camera_management_api.dart';
import 'package:camera_stream_app/features/camera_management/models/camera_management_failure.dart';
import 'package:camera_stream_app/features/camera_management/models/camera_status.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

CameraManagementApi _api(MockClient client) {
  return CameraManagementApi.withHttpClient(
    accessToken: 'jwt',
    baseUrl: 'http://backend',
    client: client,
  );
}

void main() {
  group('CameraManagementApi', () {
    test('liste parse edilir', () async {
      final api = _api(
        MockClient((request) async {
          expect(request.url.path, '/api/v1/cameras');
          expect(request.headers['Authorization'], 'Bearer jwt');
          return http.Response(
            jsonEncode([
              {
                'id': '11111111-0000-4000-8000-000000000001',
                'name': 'Kaynak 1',
                'code': 'CAM-01',
                'departmentId': '22222222-0000-4000-8000-000000000002',
                'departmentName': 'Üretim',
                'active': true,
                'status': 'ONLINE',
                'lastSeenAt': '2026-08-22T10:00:00Z',
                'activeSessionId': null,
              },
            ]),
            200,
          );
        }),
      );

      final cameras = await api.fetchCameras();
      expect(cameras, hasLength(1));
      expect(cameras.first.status, CameraStatus.online);
      expect(cameras.first.code, 'CAM-01');
    });

    test('403 forbidden failure olur', () async {
      final api = _api(
        MockClient((_) async => http.Response('', 403)),
      );

      expect(
        () => api.createCamera(
          name: 'X',
          code: 'Y',
          departmentId: '22222222-0000-4000-8000-000000000002',
        ),
        throwsA(
          isA<CameraManagementFailure>().having(
            (e) => e.kind,
            'kind',
            CameraManagementFailureKind.forbidden,
          ),
        ),
      );
    });

    test('create doğru gövde gönderir', () async {
      final api = _api(
        MockClient((request) async {
          expect(request.method, 'POST');
          expect(request.url.path, '/api/v1/cameras');
          final body = jsonDecode(request.body) as Map<String, dynamic>;
          expect(body['name'], 'Yeni');
          expect(body['code'], 'N-01');
          expect(body['departmentId'], 'dept-1');
          return http.Response(
            jsonEncode({
              'id': 'new-id',
              'name': 'Yeni',
              'code': 'N-01',
              'departmentId': 'dept-1',
              'active': true,
              'status': 'OFFLINE',
            }),
            200,
          );
        }),
      );

      final created = await api.createCamera(
        name: 'Yeni',
        code: 'N-01',
        departmentId: 'dept-1',
      );
      expect(created.name, 'Yeni');
      expect(created.status, CameraStatus.offline);
    });

    test('GET /cameras/{id} parse edilir', () async {
      final api = _api(
        MockClient((request) async {
          expect(request.method, 'GET');
          expect(
            request.url.path,
            '/api/v1/cameras/11111111-0000-4000-8000-000000000001',
          );
          return http.Response(
            jsonEncode({
              'id': '11111111-0000-4000-8000-000000000001',
              'name': 'Kaynak 1',
              'code': 'CAM-01',
              'departmentId': '22222222-0000-4000-8000-000000000002',
              'departmentName': 'Üretim',
              'active': true,
              'status': 'WEAK',
              'lastSeenAt': '2026-08-22T10:00:00Z',
              'activeSessionId': 'sess-1',
            }),
            200,
          );
        }),
      );

      final camera = await api.fetchCamera(
        '11111111-0000-4000-8000-000000000001',
      );
      expect(camera.status, CameraStatus.weak);
      expect(camera.activeSessionId, 'sess-1');
    });

    test('create 201 CREATED gövdesini kabul eder', () async {
      final api = _api(
        MockClient((request) async {
          expect(request.method, 'POST');
          return http.Response(
            jsonEncode({
              'id': 'new-id',
              'name': 'Yeni',
              'code': 'N-01',
              'departmentId': 'dept-1',
              'active': true,
              'status': 'OFFLINE',
            }),
            201,
          );
        }),
      );

      final created = await api.createCamera(
        name: 'Yeni',
        code: 'N-01',
        departmentId: 'dept-1',
      );
      expect(created.id, 'new-id');
    });

    test('update PUT gövdesi gönderir', () async {
      final api = _api(
        MockClient((request) async {
          expect(request.method, 'PUT');
          expect(request.url.path, '/api/v1/cameras/id-1');
          final body = jsonDecode(request.body) as Map<String, dynamic>;
          expect(body['name'], 'Güncel');
          expect(body['active'], false);
          expect(body.containsKey('code'), isFalse);
          return http.Response(
            jsonEncode({
              'id': 'id-1',
              'name': 'Güncel',
              'code': 'CAM-01',
              'departmentId': 'dept-1',
              'active': false,
              'status': 'OFFLINE',
            }),
            200,
          );
        }),
      );

      final updated = await api.updateCamera('id-1', name: 'Güncel', active: false);
      expect(updated.active, isFalse);
      expect(updated.name, 'Güncel');
    });

    test('departman listesi parse edilir', () async {
      final api = _api(
        MockClient((request) async {
          expect(request.url.path, '/api/v1/users/me/departments');
          return http.Response(
            jsonEncode([
              {'id': 'dept-1', 'name': 'Üretim'},
            ]),
            200,
          );
        }),
      );

      final departments = await api.fetchDepartments();
      expect(departments, hasLength(1));
      expect(departments.first.name, 'Üretim');
    });

    test('update validation failure olur', () async {
      final api = _api(
        MockClient((_) async => http.Response('', 400)),
      );

      expect(
        () => api.updateCamera('id-1', name: ''),
        throwsA(
          isA<CameraManagementFailure>().having(
            (e) => e.kind,
            'kind',
            CameraManagementFailureKind.validation,
          ),
        ),
      );
    });
  });
}
