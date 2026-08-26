import 'dart:async';
import 'dart:convert';

import 'package:camera_stream_app/core/realtime/realtime_connection_state.dart';
import 'package:camera_stream_app/core/realtime/realtime_event_parser.dart';
import 'package:camera_stream_app/features/notifications/data/notification_event_store.dart';
import 'package:camera_stream_app/features/notifications/presentation/notifications_page.dart';
import 'package:camera_stream_app/features/notifications/presentation/widgets/notification_card.dart';
import 'package:camera_stream_app/features/violations/presentation/violation_labels.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

NotificationEventStore _storeWithAlert({
  String violationId = 'v-1',
  String type = 'MISSING_GLOVES',
  String lifecycleStatus = 'ACTIVE',
  String recordingStatus = 'RECORDING',
  bool clipReady = false,
  String startedAt = '2026-08-22T10:00:00Z',
}) {
  final store = NotificationEventStore();
  store.apply(
    parseRealtimeFrame(
      jsonEncode({
        'violationId': violationId,
        'type': type,
        'cameraName': 'Kamera 1',
        'departmentName': 'Üretim',
        'startedAt': startedAt,
        'confidence': 0.91,
        'lifecycleStatus': lifecycleStatus,
        'recordingStatus': recordingStatus,
        'clipReady': clipReady,
        'coverImageReady': false,
      }),
    ),
  );
  return store;
}

void _applyUpdate(
  NotificationEventStore store, {
  String violationId = 'v-1',
  String lifecycleStatus = 'COMPLETED',
  String recordingStatus = 'READY',
  bool clipReady = true,
  String updatedAt = '2026-08-22T10:05:00Z',
}) {
  store.apply(
    parseRealtimeFrame(
      jsonEncode({
        'violationId': violationId,
        'lifecycleStatus': lifecycleStatus,
        'recordingStatus': recordingStatus,
        'clipReady': clipReady,
        'updatedAt': updatedAt,
      }),
    ),
  );
}

void main() {
  late StreamController<RealtimeConnectionState> connection;

  setUp(() {
    connection = StreamController<RealtimeConnectionState>.broadcast(
      sync: true,
    );
  });

  tearDown(() async {
    await connection.close();
  });

  Widget wrap(NotificationEventStore store, {ValueChanged<String>? onOpen}) {
    return MaterialApp(
      home: NotificationsPage(
        store: store,
        connectionState: RealtimeConnectionState.connected,
        connectionStates: connection.stream,
        onOpenViolation: onOpen,
      ),
    );
  }

  testWidgets('alert kartı type, kamera, zaman ve status gösterir',
      (tester) async {
    final store = _storeWithAlert();
    addTearDown(store.dispose);

    await tester.pumpWidget(wrap(store));
    await tester.pump();

    expect(find.text('Eldiven'), findsOneWidget);
    expect(find.text('Kamera 1'), findsOneWidget);
    expect(find.text('Üretim'), findsOneWidget);
    expect(
      find.text(formatLocalDateTime(DateTime.utc(2026, 8, 22, 10))),
      findsOneWidget,
    );
    expect(find.text('Güven: %91'), findsOneWidget);
    expect(find.text('Aktif'), findsOneWidget);
    expect(find.text('Kaydediliyor'), findsOneWidget);
    expect(find.text('Klip henüz yok'), findsOneWidget);
    expect(find.text('Yeni'), findsOneWidget);
    expect(find.byType(NotificationCard), findsOneWidget);
  });

  testWidgets('aynı violation update mevcut kartı günceller', (tester) async {
    final store = _storeWithAlert();
    addTearDown(store.dispose);

    await tester.pumpWidget(wrap(store));
    await tester.pump();

    _applyUpdate(store);
    await tester.pump();
    await tester.pump();

    expect(find.byType(NotificationCard), findsOneWidget);
    expect(find.text('Eldiven'), findsOneWidget);
    expect(find.text('Kamera 1'), findsOneWidget);
    expect(find.text('Aktif'), findsNothing);
    expect(find.text('Tamamlandı'), findsOneWidget);
    expect(find.text('Hazır'), findsOneWidget);
    expect(find.text('Klip hazır'), findsOneWidget);
  });

  testWidgets('local gizle kartı kaldırır, store item kalır', (tester) async {
    final store = _storeWithAlert();
    addTearDown(store.dispose);

    await tester.pumpWidget(wrap(store));
    await tester.pump();

    await tester.tap(find.byTooltip('Gizle'));
    await tester.pump();

    expect(find.byType(NotificationCard), findsNothing);
    expect(store.length, 1);

    _applyUpdate(store);
    await tester.pump();
    await tester.pump();

    expect(find.byType(NotificationCard), findsNothing);
    expect(store.itemFor('v-1')!.clipReady, isTrue);
  });

  testWidgets('tap violation detail callback çağırır', (tester) async {
    final store = _storeWithAlert();
    addTearDown(store.dispose);
    String? opened;

    await tester.pumpWidget(wrap(store, onOpen: (id) => opened = id));
    await tester.pump();

    await tester.tap(find.text('Eldiven'));
    await tester.pump();

    expect(opened, 'v-1');
    expect(find.text('Yeni'), findsNothing);
  });

  testWidgets('offline mevcut bildirimleri korur', (tester) async {
    final store = _storeWithAlert();
    addTearDown(store.dispose);

    await tester.pumpWidget(wrap(store));
    await tester.pump();

    connection.add(RealtimeConnectionState.offline);
    await tester.pump();
    await tester.pump();

    expect(find.text('Eldiven'), findsOneWidget);
    expect(
      find.text('Çevrimdışı — mevcut bildirimler korunuyor.'),
      findsOneWidget,
    );
  });

  testWidgets('reconnect badge görünür', (tester) async {
    final store = _storeWithAlert();
    addTearDown(store.dispose);

    await tester.pumpWidget(wrap(store));
    await tester.pump();

    connection.add(RealtimeConnectionState.reconnecting);
    await tester.pump();
    await tester.pump();

    expect(find.text('Yeniden bağlanıyor…'), findsOneWidget);
    expect(find.text('Eldiven'), findsOneWidget);
  });

  testWidgets('connected empty state', (tester) async {
    final store = NotificationEventStore();
    addTearDown(store.dispose);

    await tester.pumpWidget(wrap(store));
    await tester.pump();

    expect(find.text('Bildirim bulunmuyor.'), findsOneWidget);
    expect(find.byType(NotificationCard), findsNothing);
  });

  testWidgets('connecting loading state', (tester) async {
    final store = NotificationEventStore();
    addTearDown(store.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: NotificationsPage(
          store: store,
          connectionState: RealtimeConnectionState.connecting,
          connectionStates: connection.stream,
        ),
      ),
    );
    await tester.pump();

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(find.text('Bildirim hattına bağlanıyor…'), findsOneWidget);
    expect(find.text('Bildirim bulunmuyor.'), findsNothing);
  });

  testWidgets('offline empty state ayrıdır', (tester) async {
    final store = NotificationEventStore();
    addTearDown(store.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: NotificationsPage(
          store: store,
          connectionState: RealtimeConnectionState.offline,
          connectionStates: connection.stream,
        ),
      ),
    );
    await tester.pump();

    expect(find.text('Çevrimdışı. Yeni bildirim alınamıyor.'), findsOneWidget);
    expect(
      find.text('Çevrimdışı — mevcut bildirimler korunuyor.'),
      findsOneWidget,
    );
    expect(find.byType(CircularProgressIndicator), findsNothing);
  });

  testWidgets('küçük ekranda overflow yok', (tester) async {
    tester.view.physicalSize = const Size(320, 568);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final store = _storeWithAlert();
    addTearDown(store.dispose);

    await tester.pumpWidget(wrap(store));
    await tester.pump();

    expect(tester.takeException(), isNull);
    expect(find.text('Eldiven'), findsOneWidget);
    expect(find.text('Yeni'), findsOneWidget);
  });
}
