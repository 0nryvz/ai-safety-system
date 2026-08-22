import 'dart:convert';

import 'package:camera_stream_app/core/models/user_summary.dart';
import 'package:camera_stream_app/features/users/data/users_api.dart';
import 'package:camera_stream_app/features/users/models/user_failure.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

UsersApi _api(MockClient client) {
  return UsersApi.withHttpClient(
    accessToken: 'jwt',
    baseUrl: 'http://backend',
    client: client,
  );
}

Map<String, dynamic> _userJson({
  String id = '11111111-0000-4000-8000-000000000001',
  String email = 'admin@isg.local',
  String fullName = 'Ada Admin',
  bool active = true,
  List<String> roles = const ['ADMIN'],
  List<String> departmentIds = const ['22222222-0000-4000-8000-000000000002'],
}) {
  return {
    'id': id,
    'email': email,
    'fullName': fullName,
    'active': active,
    'departmentId': departmentIds.isEmpty ? null : departmentIds.first,
    'departmentName': departmentIds.isEmpty ? null : 'Üretim',
    'roles': roles,
    'departmentIds': departmentIds,
    'createdAt': '2026-08-22T10:00:00Z',
  };
}

void main() {
  group('UsersApi', () {
    test('liste UserResponse parse edilir', () async {
      final api = _api(
        MockClient((request) async {
          expect(request.method, 'GET');
          expect(request.url.path, '/api/v1/users');
          expect(request.headers['Authorization'], 'Bearer jwt');
          return http.Response(jsonEncode([_userJson()]), 200);
        }),
      );

      final users = await api.fetchUsers();
      expect(users, hasLength(1));
      expect(users.first.id, '11111111-0000-4000-8000-000000000001');
      expect(users.first.email, 'admin@isg.local');
      expect(users.first.fullName, 'Ada Admin');
      expect(users.first.active, isTrue);
      expect(users.first.roles, {'ADMIN'});
      expect(
        users.first.departmentIds,
        {'22222222-0000-4000-8000-000000000002'},
      );
    });

    test('UserSummary contract alanlarını parse eder', () {
      final user = UserSummary.fromJson(_userJson(
        roles: ['OHS_SPECIALIST', 'SHIFT_SUPERVISOR'],
      ));

      expect(user.departmentId, '22222222-0000-4000-8000-000000000002');
      expect(user.departmentName, 'Üretim');
      expect(user.roles, {'OHS_SPECIALIST', 'SHIFT_SUPERVISOR'});
      expect(user.createdAt, isNotNull);
    });

    test('create 201 gövdesini kabul eder ve doğru alanları gönderir', () async {
      late Map<String, dynamic> sent;
      final api = _api(
        MockClient((request) async {
          expect(request.method, 'POST');
          expect(request.url.path, '/api/v1/users');
          sent = jsonDecode(request.body) as Map<String, dynamic>;
          return http.Response(
            jsonEncode(_userJson(
              id: 'new-id',
              email: 'yeni@isg.local',
              fullName: 'Yeni Kullanici',
              roles: const ['SHIFT_SUPERVISOR'],
            )),
            201,
            headers: const {'content-type': 'application/json; charset=utf-8'},
          );
        }),
      );

      final created = await api.createUser(
        email: 'yeni@isg.local',
        password: 'secret1',
        fullName: 'Yeni Kullanici',
        roleNames: const ['SHIFT_SUPERVISOR'],
        departmentIds: const ['dept-1'],
      );
      expect(created.id, 'new-id');
      expect(created.fullName, 'Yeni Kullanici');
      expect(sent['email'], 'yeni@isg.local');
      expect(sent['password'], 'secret1');
      expect(sent['fullName'], 'Yeni Kullanici');
      expect(sent['roleNames'], ['SHIFT_SUPERVISOR']);
      expect(sent['departmentIds'], ['dept-1']);
    });

    test('update PATCH gövdesi gönderir', () async {
      final api = _api(
        MockClient((request) async {
          expect(request.method, 'PATCH');
          expect(request.url.path, '/api/v1/users/id-1');
          final body = jsonDecode(request.body) as Map<String, dynamic>;
          expect(body['fullName'], 'Güncel Ad');
          expect(body['roleNames'], ['ADMIN']);
          expect(body['departmentIds'], ['dept-2']);
          expect(body['active'], false);
          return http.Response(
            jsonEncode(_userJson(
              id: 'id-1',
              fullName: 'Güncel Ad',
              active: false,
            )),
            200,
          );
        }),
      );

      final updated = await api.updateUser(
        'id-1',
        fullName: 'Güncel Ad',
        roleNames: const ['ADMIN'],
        departmentIds: const ['dept-2'],
        active: false,
      );
      expect(updated.fullName, 'Güncel Ad');
      expect(updated.active, isFalse);
    });

    test('deactivate DELETE 204 boş gövdeyi kabul eder', () async {
      final api = _api(
        MockClient((request) async {
          expect(request.method, 'DELETE');
          expect(request.url.path, '/api/v1/users/id-1');
          expect(request.body, isEmpty);
          return http.Response('', 204);
        }),
      );

      await api.deactivateUser('id-1');
    });

    test('departman listesi /users/me/departments parse edilir', () async {
      final api = _api(
        MockClient((request) async {
          expect(request.method, 'GET');
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
      expect(departments.first.id, 'dept-1');
      expect(departments.first.name, 'Üretim');
    });

    test('403 forbidden failure olur', () async {
      final api = _api(
        MockClient((_) async => http.Response('', 403)),
      );

      expect(
        () => api.fetchUsers(),
        throwsA(
          isA<UserFailure>().having(
            (e) => e.kind,
            'kind',
            UserFailureKind.forbidden,
          ),
        ),
      );
    });

    test('409 duplicate email conflict olur', () async {
      final api = _api(
        MockClient((_) async => http.Response('', 409)),
      );

      expect(
        () => api.createUser(
          email: 'dup@isg.local',
          password: 'secret1',
          fullName: 'Dup',
          roleNames: const ['ADMIN'],
          departmentIds: const [],
        ),
        throwsA(
          isA<UserFailure>().having(
            (e) => e.kind,
            'kind',
            UserFailureKind.conflict,
          ),
        ),
      );
    });
  });
}
