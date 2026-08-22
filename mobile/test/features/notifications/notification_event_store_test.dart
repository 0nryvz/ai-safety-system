import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:camera_stream_app/core/realtime/realtime_event.dart';
import 'package:camera_stream_app/core/realtime/realtime_event_parser.dart';
import 'package:camera_stream_app/features/notifications/data/notification_event_store.dart';

RealtimeEvent alert({
  String violationId = 'v-1',
  String lifecycleStatus = 'ACTIVE',
  String recordingStatus = 'RECORDING',
  bool clipReady = false,
  bool coverImageReady = false,
  String startedAt = '2026-08-22T10:00:00Z',
}) {
  return parseRealtimeFrame(jsonEncode({
    'violationId': violationId,
    'type': 'NO_HELMET',
    'cameraName': 'Kamera 1',
    'departmentName': 'Üretim',
    'startedAt': startedAt,
    'confidence': 0.9,
    'lifecycleStatus': lifecycleStatus,
    'recordingStatus': recordingStatus,
    'clipReady': clipReady,
    'coverImageReady': coverImageReady,
  }));
}

RealtimeEvent update({
  String violationId = 'v-1',
  String lifecycleStatus = 'ENDED',
  String recordingStatus = 'COMPLETED',
  bool clipReady = true,
  String updatedAt = '2026-08-22T10:05:00Z',
  String? errorCode,
}) {
  return parseRealtimeFrame(jsonEncode({
    'violationId': violationId,
    'lifecycleStatus': lifecycleStatus,
    'recordingStatus': recordingStatus,
    'clipReady': clipReady,
    'updatedAt': updatedAt,
    'errorCode': errorCode,
  }));
}

void main() {
  late NotificationEventStore store;

  setUp(() => store = NotificationEventStore());
  tearDown(() => store.dispose());

  test('duplicate alert produces a single item', () {
    expect(store.apply(alert()), isTrue);
    expect(store.apply(alert()), isFalse);
    expect(store.apply(alert()), isFalse);

    expect(store.length, 1);
    expect(store.items.single.violationId, 'v-1');
  });

  test('update mutates the existing violation instead of adding an item', () {
    store.apply(alert());
    expect(store.apply(update()), isTrue);

    expect(store.length, 1);
    final item = store.itemFor('v-1')!;
    expect(item.lifecycleStatus, 'ENDED');
    expect(item.recordingStatus, 'COMPLETED');
    expect(item.clipReady, isTrue);
    expect(item.type, 'NO_HELMET', reason: 'alert detayları korunur');
    expect(item.cameraName, 'Kamera 1');
  });

  test('stale update does not roll back a newer state', () {
    store.apply(alert());
    store.apply(update(updatedAt: '2026-08-22T10:05:00Z'));

    final applied = store.apply(update(
      lifecycleStatus: 'ACTIVE',
      recordingStatus: 'RECORDING',
      clipReady: false,
      updatedAt: '2026-08-22T10:02:00Z',
    ));

    expect(applied, isFalse);
    final item = store.itemFor('v-1')!;
    expect(item.lifecycleStatus, 'ENDED');
    expect(item.clipReady, isTrue);
  });

  test('clip-ready update keeps one item and flips the flag', () {
    store.apply(alert());
    store.apply(update(
      lifecycleStatus: 'ENDED',
      recordingStatus: 'COMPLETED',
      clipReady: true,
      updatedAt: '2026-08-22T10:06:00Z',
    ));

    expect(store.length, 1);
    expect(store.itemFor('v-1')!.clipReady, isTrue);
  });

  test('parse failure never touches the store', () {
    store.apply(alert());
    expect(store.apply(parseRealtimeFrame('not-json')), isFalse);
    expect(store.apply(parseRealtimeFrame('{"foo":1}')), isFalse);

    expect(store.length, 1);
  });

  test('update for an unseen violation creates a partial item', () {
    expect(store.apply(update(violationId: 'v-9', errorCode: 'CLIP_FAILED')),
        isTrue);

    final item = store.itemFor('v-9')!;
    expect(item.type, isNull);
    expect(item.errorCode, 'CLIP_FAILED');
    expect(item.clipReady, isTrue);
  });

  test('distinct violations are kept separately, newest first', () {
    store.apply(alert(violationId: 'v-1'));
    store.apply(alert(
      violationId: 'v-2',
      startedAt: '2026-08-22T10:01:00Z',
    ));

    expect(store.length, 2);
    expect(store.items.first.violationId, 'v-2');
  });

  test('changes stream emits current snapshot', () async {
    final snapshots = <int>[];
    store.changes.listen((items) => snapshots.add(items.length));

    store.apply(alert(violationId: 'v-1'));
    store.apply(alert(violationId: 'v-2'));
    store.apply(alert(violationId: 'v-2'));
    await pumpEventQueue();

    expect(snapshots, [1, 2]);
  });

  test('fingerprint window is bounded', () {
    final small = NotificationEventStore(maxFingerprints: 2);
    addTearDown(small.dispose);

    small.apply(alert(violationId: 'a'));
    small.apply(alert(violationId: 'b', startedAt: '2026-08-22T10:01:00Z'));
    small.apply(alert(violationId: 'c', startedAt: '2026-08-22T10:02:00Z'));

    // En eski fingerprint düşürüldüğü için 'a' tekrar uygulanabilir.
    expect(small.apply(alert(violationId: 'a')), isTrue);
    expect(small.length, 3);
  });
}
