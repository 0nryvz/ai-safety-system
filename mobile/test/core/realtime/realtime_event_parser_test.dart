import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:camera_stream_app/core/realtime/realtime_event.dart';
import 'package:camera_stream_app/core/realtime/realtime_event_parser.dart';

String alertJson({
  String violationId = 'v-1',
  String lifecycleStatus = 'ACTIVE',
  String recordingStatus = 'RECORDING',
  bool clipReady = false,
  bool coverImageReady = false,
  String startedAt = '2026-08-22T10:00:00Z',
}) {
  return jsonEncode({
    'violationId': violationId,
    'type': 'NO_HELMET',
    'cameraName': 'Kamera 1',
    'departmentName': 'Üretim',
    'startedAt': startedAt,
    'confidence': 0.91,
    'lifecycleStatus': lifecycleStatus,
    'recordingStatus': recordingStatus,
    'clipReady': clipReady,
    'coverImageReady': coverImageReady,
  });
}

String updateJson({
  String violationId = 'v-1',
  String lifecycleStatus = 'ENDED',
  String recordingStatus = 'COMPLETED',
  bool clipReady = true,
  String updatedAt = '2026-08-22T10:05:00Z',
  String? errorCode,
}) {
  return jsonEncode({
    'violationId': violationId,
    'lifecycleStatus': lifecycleStatus,
    'recordingStatus': recordingStatus,
    'clipReady': clipReady,
    'updatedAt': updatedAt,
    'errorCode': errorCode,
  });
}

void main() {
  group('realtime frame parsing', () {
    test('AlertMessage is parsed with real backend fields', () {
      final event = parseRealtimeFrame(alertJson());

      expect(event, isA<RealtimeAlertEvent>());
      final alert = (event as RealtimeAlertEvent).message;
      expect(alert.violationId, 'v-1');
      expect(alert.type, 'NO_HELMET');
      expect(alert.cameraName, 'Kamera 1');
      expect(alert.departmentName, 'Üretim');
      expect(alert.confidence, 0.91);
      expect(alert.lifecycleStatus, 'ACTIVE');
      expect(alert.recordingStatus, 'RECORDING');
      expect(alert.clipReady, isFalse);
      expect(alert.coverImageReady, isFalse);
      expect(alert.startedAt.toUtc().hour, 10);
    });

    test('ViolationUpdateMessage is parsed separately', () {
      final event = parseRealtimeFrame(updateJson(errorCode: 'CLIP_FAILED'));

      expect(event, isA<RealtimeViolationUpdateEvent>());
      final update = (event as RealtimeViolationUpdateEvent).message;
      expect(update.violationId, 'v-1');
      expect(update.lifecycleStatus, 'ENDED');
      expect(update.recordingStatus, 'COMPLETED');
      expect(update.clipReady, isTrue);
      expect(update.errorCode, 'CLIP_FAILED');
    });

    test('malformed and unknown frames produce safe parse failures', () {
      expect(parseRealtimeFrame(null), isA<RealtimeParseFailure>());
      expect(parseRealtimeFrame(''), isA<RealtimeParseFailure>());
      expect(parseRealtimeFrame('not-json'), isA<RealtimeParseFailure>());
      expect(parseRealtimeFrame('[1,2,3]'), isA<RealtimeParseFailure>());
      expect(
        parseRealtimeFrame('{"foo":"bar"}'),
        isA<RealtimeParseFailure>(),
      );
    });

    test('missing required alert field does not throw', () {
      final payload = jsonDecode(alertJson()) as Map<String, dynamic>
        ..remove('lifecycleStatus');

      final event = parseRealtimeFrame(jsonEncode(payload));

      expect(event, isA<RealtimeParseFailure>());
      expect((event as RealtimeParseFailure).reason, contains('lifecycle'));
    });

    test('update with unparsable timestamp fails safely', () {
      final event = parseRealtimeFrame(
        '{"violationId":"v-1","lifecycleStatus":"ENDED",'
        '"recordingStatus":"COMPLETED","clipReady":true,'
        '"updatedAt":"tomorrow"}',
      );

      expect(event, isA<RealtimeParseFailure>());
    });

    test('fingerprint is deterministic and state-sensitive', () {
      final a = parseRealtimeFrame(alertJson());
      final b = parseRealtimeFrame(alertJson());
      final c = parseRealtimeFrame(alertJson(clipReady: true));

      expect(a.fingerprint, b.fingerprint);
      expect(a.fingerprint, isNot(c.fingerprint));
    });
  });
}
