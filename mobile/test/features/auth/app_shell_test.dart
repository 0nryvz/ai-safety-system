import 'dart:convert';

import 'package:camera_stream_app/app.dart';
import 'package:camera_stream_app/core/network/backend_client.dart';
import 'package:camera_stream_app/core/realtime/realtime_event_parser.dart';
import 'package:camera_stream_app/core/realtime/stomp_client_port.dart';
import 'package:camera_stream_app/features/auth/auth_controller.dart';
import 'package:camera_stream_app/features/auth/auth_login_page.dart';
import 'package:camera_stream_app/features/notifications/data/notification_event_store.dart';
import 'package:camera_stream_app/features/notifications/data/realtime_providers.dart';
import 'package:camera_stream_app/features/session/camera_selection_page.dart';
import 'package:camera_stream_app/features/violations/presentation/violation_detail_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

class _FakePort implements StompClientPort {
  final List<RealtimeConnectRequest> requests = [];
  int disconnectCalls = 0;

  RealtimeConnectRequest get last => requests.last;

  @override
  Future<void> connect(RealtimeConnectRequest request) async {
    requests.add(request);
  }

  @override
  Future<void> disconnect() async {
    disconnectCalls++;
  }

  void connected() => last.onConnected();
}

Map<String, dynamic> _me({
  String id = 'user-1',
  List<String> roles = const ['ADMIN'],
}) {
  return {
    'id': id,
    'email': '$id@isg.local',
    'fullName': 'Ada Admin',
    'active': true,
    'roles': roles,
    'departmentIds': <String>[],
  };
}

String _ok(Object body) => jsonEncode(body);

http.Response _json(Object body, [int status = 200]) {
  return http.Response(_ok(body), status, headers: {'content-type': 'application/json'});
}

MockClient _apiClient({
  int dashboardStatus = 200,
  List<String> roles = const ['ADMIN'],
  bool includeRecent = true,
}) {
  return MockClient((request) async {
    final path = request.url.path;

    if (path == '/api/v1/auth/login' || path == '/api/v1/auth/refresh') {
      return _json({
        'accessToken': 'jwt',
        'refreshToken': 'rt',
        'tokenType': 'Bearer',
      });
    }
    if (path == '/api/v1/auth/logout') {
      return http.Response('', 204);
    }
    if (path == '/api/v1/users/me') {
      return _json(_me(roles: roles));
    }
    if (path == '/api/v1/dashboard/summary') {
      return _json({
        'todayViolationCount': 3,
        'last7DaysViolationCount': 11,
        'mostFrequentViolationType': 'MISSING_GLOVES',
        'activeCameraCount': 5,
        'offlineCameraCount': 0,
        'activeViolationCount': 2,
      }, dashboardStatus);
    }
    if (path == '/api/v1/dashboard/trend' ||
        path == '/api/v1/dashboard/distribution') {
      return _json(<Object>[], dashboardStatus);
    }
    if (path == '/api/v1/dashboard/recent-violations') {
      if (dashboardStatus != 200) {
        return _json(<Object>[], dashboardStatus);
      }
      return _json(
        includeRecent
            ? [
                {
                  'violationId': 'viol-1',
                  'detectedAt': '2026-08-21T12:00:00Z',
                  'startedAt': '2026-08-21T12:00:00Z',
                  'violationType': 'RESTRICTED_ZONE',
                  'cameraId': 'cam-1',
                  'departmentId': 'dep-1',
                  'departmentName': 'Montaj',
                  'cameraName': 'Kapı-A',
                  'cameraCode': 'C1',
                  'lifecycleStatus': 'ACTIVE',
                  'reviewStatus': 'UNREVIEWED',
                  'recordingStatus': 'READY',
                  'confidence': 0.8,
                  'modelVersion': 'v1',
                },
              ]
            : <Object>[],
      );
    }
    if (path == '/api/v1/cameras') {
      return _json([
        {
          'id': '33333333-0000-4000-8000-000000000001',
          'name': 'Kaynak-1 Kamera A',
          'code': 'CAM-WELDING-001',
          'departmentId': '11111111-0000-4000-8000-000000000001',
          'departmentName': 'Kaynakhane',
          'active': true,
          'status': 'ONLINE',
        },
      ]);
    }
    if (path == '/api/v1/violations') {
      return _json({
        'content': [
          {
            'violationId': 'viol-1',
            'type': 'RESTRICTED_ZONE',
            'lifecycleStatus': 'ACTIVE',
            'reviewStatus': 'UNREVIEWED',
            'recordingStatus': 'READY',
            'startedAt': '2026-08-21T12:00:00Z',
          },
        ],
        'page': 0,
        'size': 20,
        'totalElements': 1,
        'totalPages': 1,
      });
    }
    if (path == '/api/v1/violations/viol-1') {
      return _json({
        'violationId': 'viol-1',
        'type': 'RESTRICTED_ZONE',
        'cameraName': 'Kapı-A',
        'lifecycleStatus': 'ACTIVE',
        'reviewStatus': 'UNREVIEWED',
        'recordingStatus': 'PREPARING',
        'clipReady': false,
        'coverImageReady': false,
        'version': 1,
      });
    }
    if (path.endsWith('/clip-url') || path.endsWith('/cover-url')) {
      return http.Response('{"message":"not ready"}', 409);
    }
    if (path == '/api/v1/users') {
      return _json([_me(roles: roles)]);
    }
    if (path == '/api/v1/users/me/departments') {
      return _json(<Object>[]);
    }

    return http.Response('not found $path', 404);
  });
}

Future<void> _signIn(AuthController controller) {
  return controller.signIn(
    email: 'ada@isg.local',
    password: 'secret1',
  );
}

Widget _app({
  required AuthController controller,
  required BackendClient backend,
  required _FakePort port,
  NotificationEventStore? store,
}) {
  return ProviderScope(
    overrides: [
      backendClientProvider.overrideWith((ref) => backend),
      authSessionProvider.overrideWith((ref) => controller),
      stompClientPortProvider.overrideWith((ref) => port),
      if (store != null)
        notificationEventStoreProvider.overrideWith((ref) {
          ref.onDispose(store.dispose);
          return store;
        }),
    ],
    child: const CameraStreamApp(),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late _FakePort port;
  late BackendClient backend;
  late AuthController controller;

  setUp(() {
    port = _FakePort();
  });

  Future<void> pumpSignedIn(
    WidgetTester tester, {
    MockClient? httpClient,
    List<String> roles = const ['ADMIN'],
    int dashboardStatus = 200,
    NotificationEventStore? store,
  }) async {
    tester.view.physicalSize = const Size(1200, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    backend = BackendClient(
      baseUrl: 'http://backend',
      client: httpClient ??
          _apiClient(roles: roles, dashboardStatus: dashboardStatus),
    );
    controller = AuthController(backend);
    await _signIn(controller);

    await tester.pumpWidget(
      _app(
        controller: controller,
        backend: backend,
        port: port,
        store: store,
      ),
    );
    await tester.pump();
    if (port.requests.isNotEmpty) {
      port.connected();
    }
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
  }

  testWidgets('login sonrası AppShell ve Dashboard açılır', (tester) async {
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    expect(find.byType(AuthLoginPage), findsNothing);
    expect(find.text('Dashboard'), findsWidgets);
    expect(find.text('Bugün'), findsOneWidget);
    expect(find.text('3'), findsOneWidget);
  });

  testWidgets('Kameralar, İhlaller ve Bildirimler sekmeleri gerçek sayfadır',
      (tester) async {
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    await tester.tap(find.text('Kameralar'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    expect(find.text('Kaynak-1 Kamera A'), findsOneWidget);
    expect(find.text('Fabrika kameralarını görüntüleyin ve yönetin.'), findsOneWidget);

    await tester.tap(find.text('İhlaller'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    expect(find.text('Geçmiş ihlalleri inceleyin ve gözden geçirin.'), findsOneWidget);

    await tester.tap(find.text('Bildirimler'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    expect(find.text('Canlı ihlal uyarıları. Gizlenen kartlar yalnızca bu oturumda kaybolur.'), findsOneWidget);
  });

  testWidgets('ADMIN Users görür, recent violation detail açar', (tester) async {
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    expect(find.text('Kullanıcılar'), findsOneWidget);

    await tester.tap(find.text('Kullanıcılar'));
    await tester.pumpAndSettle();
    expect(find.text('Ada Admin'), findsWidgets);

    await tester.tap(find.text('Dashboard'));
    await tester.pumpAndSettle();

    final recent = find.textContaining('Kapı-A');
    expect(recent, findsOneWidget);
    await tester.ensureVisible(recent);
    await tester.tap(recent);
    await tester.pumpAndSettle();

    expect(find.byType(ViolationDetailPage), findsOneWidget);
    expect(find.text('İhlal detayı'), findsOneWidget);
  });

  testWidgets('non-admin Users sekmesini görmez', (tester) async {
    await pumpSignedIn(tester, roles: const ['OHS_SPECIALIST']);
    await tester.pumpAndSettle();

    expect(find.text('Kullanıcılar'), findsNothing);
    expect(find.text('Dashboard'), findsWidgets);
    expect(find.text('Kamera Yayını'), findsOneWidget);
  });

  testWidgets('Kamera Yayını backend status alanından kamera listeler',
      (tester) async {
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    await tester.tap(find.text('Kamera Yayını'));
    await tester.pumpAndSettle();

    expect(find.byType(CameraSelectionPage), findsOneWidget);
    expect(find.text('Kamera seçimi'), findsOneWidget);
    expect(find.text('Kaynak-1 Kamera A'), findsOneWidget);
  });

  testWidgets('notification tap mevcut violation detail açar', (tester) async {
    final store = NotificationEventStore();
    store.apply(
      parseRealtimeFrame(
        jsonEncode({
          'violationId': 'viol-1',
          'type': 'RESTRICTED_ZONE',
          'cameraName': 'Kapı-A',
          'departmentName': 'Montaj',
          'startedAt': '2026-08-21T12:00:00Z',
          'confidence': 0.8,
          'lifecycleStatus': 'ACTIVE',
          'recordingStatus': 'READY',
          'clipReady': false,
          'coverImageReady': false,
        }),
      ),
    );

    await pumpSignedIn(tester, store: store);
    await tester.pumpAndSettle();

    await tester.tap(find.text('Bildirimler'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Yasak alan'));
    await tester.pumpAndSettle();

    expect(find.byType(ViolationDetailPage), findsOneWidget);
  });

  testWidgets('authenticated AppShell realtime lifecycle başlatır, logout durur',
      (tester) async {
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    expect(port.requests, hasLength(1));
    expect(port.last.accessToken, 'jwt');
    expect(port.last.destination, '/user/queue/alerts');
    port.connected();
    await tester.pump();

    await tester.tap(find.byTooltip('Çıkış'));
    await tester.pumpAndSettle();

    expect(find.byType(AuthLoginPage), findsOneWidget);
    expect(port.disconnectCalls, greaterThanOrEqualTo(1));
    expect(controller.state.authenticated, isFalse);
  });

  testWidgets('logout notification store temizler, Bildirimler açılmasa da',
      (tester) async {
    final store = NotificationEventStore();
    store.apply(
      parseRealtimeFrame(
        jsonEncode({
          'violationId': 'viol-stale',
          'type': 'RESTRICTED_ZONE',
          'cameraName': 'Kapı-A',
          'departmentName': 'Montaj',
          'startedAt': '2026-08-21T12:00:00Z',
          'confidence': 0.8,
          'lifecycleStatus': 'ACTIVE',
          'recordingStatus': 'READY',
          'clipReady': false,
          'coverImageReady': false,
        }),
      ),
    );

    await pumpSignedIn(tester, store: store);
    await tester.pumpAndSettle();
    expect(store.length, 1);

    await tester.tap(find.byTooltip('Çıkış'));
    await tester.pumpAndSettle();

    expect(find.byType(AuthLoginPage), findsOneWidget);
    expect(store.length, 0);
    expect(port.disconnectCalls, greaterThanOrEqualTo(1));
  });

  testWidgets('403 dashboard sessionı düşürmez', (tester) async {
    await pumpSignedIn(tester, dashboardStatus: 403);
    await tester.pumpAndSettle();

    expect(find.byType(AuthLoginPage), findsNothing);
    expect(controller.state.authenticated, isTrue);
    expect(find.text('Bu dashboard verisine erişim yetkiniz yok.'), findsOneWidget);
    expect(find.byTooltip('Çıkış'), findsOneWidget);
  });

  testWidgets('403 notification store temizlemez', (tester) async {
    final store = NotificationEventStore();
    store.apply(
      parseRealtimeFrame(
        jsonEncode({
          'violationId': 'viol-keep',
          'type': 'RESTRICTED_ZONE',
          'cameraName': 'Kapı-A',
          'departmentName': 'Montaj',
          'startedAt': '2026-08-21T12:00:00Z',
          'confidence': 0.8,
          'lifecycleStatus': 'ACTIVE',
          'recordingStatus': 'READY',
          'clipReady': false,
          'coverImageReady': false,
        }),
      ),
    );

    await pumpSignedIn(tester, store: store, dashboardStatus: 403);
    await tester.pumpAndSettle();

    expect(controller.state.authenticated, isTrue);
    expect(store.length, 1);
    expect(store.items.single.violationId, 'viol-keep');
  });
}
