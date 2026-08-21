import 'dart:convert';

import 'package:camera_stream_app/core/network/backend_client.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  group('login', () {
    test('accessToken döner', () async {
      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient(
          (_) async => http.Response(
            jsonEncode({
              'accessToken': 'jwt-token',
              'refreshToken': 'refresh',
              'tokenType': 'Bearer',
            }),
            200,
          ),
        ),
      );

      expect(
        await client.login(email: 'a@b.c', password: 'x'),
        'jwt-token',
      );
    });

    test('kimlik bilgileri gövdede gönderilir', () async {
      late http.Request captured;

      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient((request) async {
          captured = request;
          return http.Response(
            jsonEncode({'accessToken': 't'}),
            200,
          );
        }),
      );

      await client.login(email: 'admin@isgvision.local', password: 'secret');

      expect(captured.url.path, '/api/v1/auth/login');
      expect(jsonDecode(captured.body), {
        'email': 'admin@isgvision.local',
        'password': 'secret',
      });
    });

    test('401 anlaşılır mesaja çevrilir', () async {
      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient((_) async => http.Response('', 401)),
      );

      expect(
        () => client.login(email: 'a@b.c', password: 'wrong'),
        throwsA(
          isA<BackendAuthException>().having(
            (e) => e.message,
            'message',
            contains('hatalı'),
          ),
        ),
      );
    });

    test('ağ hatası anlaşılır mesaja çevrilir', () async {
      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient((_) => throw Exception('no route')),
      );

      expect(
        () => client.login(email: 'a@b.c', password: 'x'),
        throwsA(
          isA<BackendAuthException>().having(
            (e) => e.message,
            'message',
            contains('ulaşılamıyor'),
          ),
        ),
      );
    });
  });

  group('fetchCameras', () {
    test('Bearer token header olarak gider', () async {
      late http.Request captured;

      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient((request) async {
          captured = request;
          return http.Response('[]', 200);
        }),
      );

      await client.fetchCameras('jwt-token');

      expect(captured.url.path, '/api/v1/cameras');
      expect(captured.headers['Authorization'], 'Bearer jwt-token');
    });

    test('kamera listesi modele dönüşür', () async {
      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient(
          (_) async => http.Response(
            jsonEncode([
              {'id': 'uuid-1', 'name': 'Kamera 1', 'active': true},
              {'id': 'uuid-2', 'name': 'Kamera 2', 'active': false},
            ]),
            200,
          ),
        ),
      );

      final cameras = await client.fetchCameras('token');

      expect(cameras, hasLength(2));
      expect(cameras.first.id, 'uuid-1');
      expect(cameras.first.isSelectable, isTrue);
      expect(cameras.last.isSelectable, isFalse);
    });

    test('süresi dolmuş token yeniden giriş ister', () async {
      final client = BackendClient(
        baseUrl: 'http://backend',
        client: MockClient((_) async => http.Response('', 401)),
      );

      expect(
        () => client.fetchCameras('expired'),
        throwsA(
          isA<BackendAuthException>().having(
            (e) => e.message,
            'message',
            contains('Tekrar giriş'),
          ),
        ),
      );
    });
  });
}
