import 'dart:convert';

import 'package:camera_stream_app/app.dart';
import 'package:camera_stream_app/core/network/backend_client.dart';
import 'package:camera_stream_app/core/realtime/realtime_event_parser.dart';
import 'package:camera_stream_app/core/realtime/stomp_client_port.dart';
import 'package:camera_stream_app/features/auth/app_shell.dart';
import 'package:camera_stream_app/features/auth/auth_controller.dart';
import 'package:camera_stream_app/features/auth/auth_login_page.dart';
import 'package:camera_stream_app/features/auth/floating_navigation_menu.dart';
import 'package:camera_stream_app/features/auth/shell_destinations.dart';
import 'package:camera_stream_app/features/camera/camera_page.dart';
import 'package:camera_stream_app/features/camera_management/presentation/cameras_page.dart';
import 'package:camera_stream_app/features/session/operator_login_page.dart';
import 'package:camera_stream_app/features/notifications/data/notification_event_store.dart';
import 'package:camera_stream_app/features/notifications/data/realtime_providers.dart';
import 'package:camera_stream_app/features/session/camera_selection_page.dart';
import 'package:camera_stream_app/features/violations/presentation/violation_detail_page.dart';
import 'package:camera_stream_app/features/violations/presentation/violations_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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

  Future<void> openFloatingNav(WidgetTester tester) async {
    await tester.tap(find.byKey(FloatingNavigationMenu.toggleKey));
    await tester.pumpAndSettle();
  }

  Future<void> selectFloatingNav(WidgetTester tester, ShellTab tab) async {
    if (find.byKey(FloatingNavigationMenu.itemKey(tab)).evaluate().isEmpty) {
      await openFloatingNav(tester);
    }
    await tester.tap(find.byKey(FloatingNavigationMenu.itemKey(tab)));
    await tester.pumpAndSettle();
  }

  testWidgets('login sonrası AppShell ve Dashboard açılır', (tester) async {
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    expect(find.byType(AuthLoginPage), findsNothing);
    expect(find.byType(AppShell), findsOneWidget);
    expect(find.byType(NavigationBar), findsNothing);
    expect(find.byKey(FloatingNavigationMenu.toggleKey), findsOneWidget);
    expect(find.byIcon(Icons.menu_rounded), findsOneWidget);
    expect(find.text('Bugün'), findsOneWidget);
    expect(find.text('3'), findsOneWidget);
  });

  testWidgets('Kameralar, İhlaller ve Bildirimler sekmeleri gerçek sayfadır',
      (tester) async {
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    await selectFloatingNav(tester, ShellTab.cameras);
    expect(find.text('Kaynak-1 Kamera A'), findsOneWidget);
    expect(find.text('Fabrika kameralarını görüntüleyin ve yönetin.'), findsOneWidget);

    await selectFloatingNav(tester, ShellTab.violations);
    expect(find.text('Geçmiş ihlalleri inceleyin ve gözden geçirin.'), findsOneWidget);

    await selectFloatingNav(tester, ShellTab.notifications);
    expect(find.text('Canlı ihlal uyarıları. Gizlenen kartlar yalnızca bu oturumda kaybolur.'), findsOneWidget);
  });

  testWidgets('ADMIN Users görür, recent violation detail açar', (tester) async {
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    await openFloatingNav(tester);
    expect(find.text('Kullanıcılar'), findsOneWidget);

    await tester.tap(find.byKey(FloatingNavigationMenu.itemKey(ShellTab.users)));
    await tester.pumpAndSettle();
    expect(find.text('Ada Admin'), findsWidgets);

    await selectFloatingNav(tester, ShellTab.dashboard);

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

    await openFloatingNav(tester);
    expect(find.text('Kullanıcılar'), findsNothing);
    expect(
      find.byKey(FloatingNavigationMenu.itemKey(ShellTab.users)),
      findsNothing,
    );
    expect(find.text('Dashboard'), findsOneWidget);
    expect(find.text('Kamera Yayını'), findsOneWidget);
  });

  testWidgets('Kamera Yayını backend status alanından kamera listeler',
      (tester) async {
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    await selectFloatingNav(tester, ShellTab.cameraBroadcast);

    expect(find.byType(CameraSelectionPage), findsOneWidget);
    expect(find.byKey(FloatingNavigationMenu.toggleKey), findsOneWidget);
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

    await selectFloatingNav(tester, ShellTab.notifications);

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

  testWidgets('floating menü kapalı başlar ve tekrar dokununca kapanır',
      (tester) async {
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    expect(find.byType(NavigationBar), findsNothing);
    expect(find.byIcon(Icons.menu_rounded), findsOneWidget);
    expect(find.byIcon(Icons.close_rounded), findsNothing);
    expect(
      find.byKey(FloatingNavigationMenu.itemKey(ShellTab.cameras)),
      findsNothing,
    );

    await openFloatingNav(tester);
    expect(find.byIcon(Icons.close_rounded), findsOneWidget);
    expect(find.text('Kameralar'), findsOneWidget);
    expect(find.text('İhlaller'), findsOneWidget);
    expect(find.text('Bildirimler'), findsOneWidget);
    expect(find.text('Kamera Yayını'), findsOneWidget);

    await tester.tap(find.byKey(FloatingNavigationMenu.toggleKey));
    await tester.pumpAndSettle();
    expect(find.byIcon(Icons.menu_rounded), findsOneWidget);
    expect(
      find.byKey(FloatingNavigationMenu.itemKey(ShellTab.cameras)),
      findsNothing,
    );
  });

  testWidgets('overlay dokunuşu menüyü kapatır', (tester) async {
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    await openFloatingNav(tester);
    expect(find.text('Kameralar'), findsOneWidget);

    await tester.tapAt(const Offset(24, 120));
    await tester.pumpAndSettle();
    expect(
      find.byKey(FloatingNavigationMenu.itemKey(ShellTab.cameras)),
      findsNothing,
    );
    expect(find.byIcon(Icons.menu_rounded), findsOneWidget);
  });

  testWidgets('Kameralar seçimi sayfayı değiştirir ve menüyü kapatır',
      (tester) async {
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    await selectFloatingNav(tester, ShellTab.cameras);
    expect(find.byType(CamerasPage), findsOneWidget);
    expect(find.text('Kaynak-1 Kamera A'), findsOneWidget);
    expect(find.byIcon(Icons.menu_rounded), findsOneWidget);
    expect(
      find.byKey(FloatingNavigationMenu.itemKey(ShellTab.cameras)),
      findsNothing,
    );
  });

  testWidgets('İhlaller seçimi sayfayı değiştirir ve menüyü kapatır',
      (tester) async {
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    await selectFloatingNav(tester, ShellTab.violations);
    expect(find.byType(ViolationsPage), findsOneWidget);
    expect(find.text('Geçmiş ihlalleri inceleyin ve gözden geçirin.'), findsOneWidget);
    expect(
      find.byKey(FloatingNavigationMenu.itemKey(ShellTab.violations)),
      findsNothing,
    );
  });

  testWidgets('aktif hedef menüde seçili görünür', (tester) async {
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    await openFloatingNav(tester);
    expect(
      find.descendant(
        of: find.byKey(FloatingNavigationMenu.itemKey(ShellTab.dashboard)),
        matching: find.byIcon(Icons.dashboard_rounded),
      ),
      findsOneWidget,
    );

    await tester.tap(find.byKey(FloatingNavigationMenu.itemKey(ShellTab.cameras)));
    await tester.pumpAndSettle();
    await openFloatingNav(tester);
    expect(
      find.descendant(
        of: find.byKey(FloatingNavigationMenu.itemKey(ShellTab.cameras)),
        matching: find.byIcon(Icons.videocam_rounded),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: find.byKey(FloatingNavigationMenu.itemKey(ShellTab.dashboard)),
        matching: find.byIcon(Icons.dashboard_rounded),
      ),
      findsNothing,
    );
  });

  testWidgets('aktif sayfaya tekrar dokunmak menüyü kapatır, sayfayı sıfırlamaz',
      (tester) async {
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    expect(find.text('Bugün'), findsOneWidget);
    await openFloatingNav(tester);
    await tester.tap(
      find.byKey(FloatingNavigationMenu.itemKey(ShellTab.dashboard)),
    );
    await tester.pumpAndSettle();

    expect(find.text('Bugün'), findsOneWidget);
    expect(find.text('3'), findsOneWidget);
    expect(
      find.byKey(FloatingNavigationMenu.itemKey(ShellTab.dashboard)),
      findsNothing,
    );
  });

  testWidgets('sistem geri tuşu açık menüyü önce kapatır', (tester) async {
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    await openFloatingNav(tester);
    expect(find.text('Kameralar'), findsOneWidget);

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();

    expect(
      find.byKey(FloatingNavigationMenu.itemKey(ShellTab.cameras)),
      findsNothing,
    );
    expect(find.byType(AppShell), findsOneWidget);
    expect(find.text('Bugün'), findsOneWidget);
  });

  testWidgets('Departmanlar menüde yoktur', (tester) async {
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    await openFloatingNav(tester);
    expect(find.text('Departmanlar'), findsNothing);
    expect(find.textContaining('Departman'), findsNothing);
  });

  testWidgets('Kamera seçimi AppBar geri Login göstermez', (tester) async {
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    await selectFloatingNav(tester, ShellTab.cameraBroadcast);
    expect(find.byType(CameraSelectionPage), findsOneWidget);
    expect(find.byKey(FloatingNavigationMenu.toggleKey), findsOneWidget);

    await tester.tap(find.byKey(CameraSelectionPage.backButtonKey));
    await tester.pumpAndSettle();

    expect(find.byType(AuthLoginPage), findsNothing);
    expect(find.byType(OperatorLoginPage), findsNothing);
    expect(find.byType(CameraSelectionPage), findsNothing);
    expect(find.byType(AppShell), findsOneWidget);
    expect(find.text('Bugün'), findsOneWidget);
    expect(controller.state.authenticated, isTrue);
    expect(find.byKey(FloatingNavigationMenu.toggleKey), findsOneWidget);
  });

  testWidgets('Kamera seçimi sistem geri AppBar geri ile aynıdır',
      (tester) async {
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    await selectFloatingNav(tester, ShellTab.cameraBroadcast);
    expect(find.byType(CameraSelectionPage), findsOneWidget);

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();

    expect(find.byType(AuthLoginPage), findsNothing);
    expect(find.byType(OperatorLoginPage), findsNothing);
    expect(find.byType(CameraSelectionPage), findsNothing);
    expect(find.text('Bugün'), findsOneWidget);
    expect(controller.state.authenticated, isTrue);
  });

  testWidgets('Kamera yayını shell içinde kalır ve geri seçime döner',
      (tester) async {
    _mockDeviceStorage(tester);
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    await selectFloatingNav(tester, ShellTab.cameraBroadcast);
    await tester.tap(find.text('Kaynak-1 Kamera A'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(find.byType(CameraPage), findsOneWidget);
    expect(find.byType(AuthLoginPage), findsNothing);
    expect(find.byType(OperatorLoginPage), findsNothing);
    expect(find.byKey(FloatingNavigationMenu.toggleKey), findsOneWidget);

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();

    expect(find.byType(CameraPage), findsNothing);
    expect(find.byType(CameraSelectionPage), findsOneWidget);
    expect(find.byKey(FloatingNavigationMenu.toggleKey), findsOneWidget);
  });

  testWidgets('logout sonrası giriş Dashboard açar, kamera seçimi kalmaz',
      (tester) async {
    await pumpSignedIn(tester);
    await tester.pumpAndSettle();

    await selectFloatingNav(tester, ShellTab.cameraBroadcast);
    expect(find.byType(CameraSelectionPage), findsOneWidget);

    await tester.tap(find.byKey(CameraSelectionPage.backButtonKey));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Çıkış'));
    await tester.pumpAndSettle();
    expect(find.byType(AuthLoginPage), findsOneWidget);

    await _signIn(controller);
    await tester.pumpAndSettle();

    expect(find.byType(AppShell), findsOneWidget);
    expect(find.byType(AuthLoginPage), findsNothing);
    expect(find.byType(CameraSelectionPage), findsNothing);
    expect(find.byType(OperatorLoginPage), findsNothing);
    expect(find.text('Bugün'), findsOneWidget);
  });
}

void _mockDeviceStorage(WidgetTester tester) {
  final stored = <String, String>{};
  tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
    const MethodChannel('camera_stream_app/device_storage'),
    (call) async {
      final args = call.arguments as Map<dynamic, dynamic>?;
      if (call.method == 'read') {
        return stored[args?['key']];
      }
      if (call.method == 'write') {
        stored[args?['key'] as String] = args?['value'] as String;
      }
      return null;
    },
  );
}
