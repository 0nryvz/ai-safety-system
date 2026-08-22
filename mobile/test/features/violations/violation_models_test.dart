import 'package:camera_stream_app/features/violations/models/iso_instant.dart';
import 'package:camera_stream_app/features/violations/models/violation_filters.dart';
import 'package:camera_stream_app/features/violations/models/violation_lifecycle_status.dart';
import 'package:camera_stream_app/features/violations/models/violation_list_item.dart';
import 'package:camera_stream_app/features/violations/models/violation_page.dart';
import 'package:camera_stream_app/features/violations/models/violation_recording_status.dart';
import 'package:camera_stream_app/features/violations/models/violation_review_status.dart';
import 'package:camera_stream_app/features/violations/models/violation_type.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('ViolationType', () {
    test('canonical 5 type parse edilir; jacket yok', () {
      expect(
        ViolationType.fromJson('MISSING_WELDING_MASK'),
        ViolationType.missingWeldingMask,
      );
      expect(
        ViolationType.fromJson('MISSING_GLOVES'),
        ViolationType.missingGloves,
      );
      expect(
        ViolationType.fromJson('MISSING_WELDING_APRON'),
        ViolationType.missingWeldingApron,
      );
      expect(
        ViolationType.fromJson('RESTRICTED_ZONE'),
        ViolationType.restrictedZone,
      );
      expect(
        ViolationType.fromJson('UNPROTECTED_PERSON'),
        ViolationType.unprotectedPerson,
      );
      expect(
        ViolationType.fromJson('MISSING_WELDING_JACKET'),
        ViolationType.unknown,
      );
      expect(ViolationType.canonical, hasLength(5));
      expect(
        ViolationType.canonical.map((t) => t.wireValue),
        isNot(contains('MISSING_WELDING_JACKET')),
      );
    });
  });

  group('iso instant', () {
    test('UTC Instant YYYY-MM-DD değil tam ISO gönderir', () {
      final instant = DateTime.utc(2026, 8, 22);
      expect(formatIsoInstant(instant), '2026-08-22T00:00:00Z');
      expect(formatIsoInstant(instant).contains('T'), isTrue);
      expect(formatIsoInstant(instant).endsWith('Z'), isTrue);
    });

    test('yerel gün başı/sonu UTC Instant olur', () {
      final local = DateTime(2026, 8, 22);
      final from = startOfLocalDayUtc(local);
      final to = endOfLocalDayUtc(local);
      expect(formatIsoInstant(from), contains('T'));
      expect(formatIsoInstant(to), contains('T'));
      expect(from.isUtc, isTrue);
      expect(to.isUtc, isTrue);
    });
  });

  group('ViolationFilters', () {
    test('tüm filtreler query olarak encode edilir', () {
      final query = ViolationFilters(
        from: DateTime.utc(2026, 8, 22),
        to: DateTime.utc(2026, 8, 22, 23, 59, 59),
        type: ViolationType.missingGloves,
        cameraId: 'cam-1',
        departmentId: 'dep-1',
        lifecycleStatus: ViolationLifecycleStatus.completed,
        reviewStatus: ViolationReviewStatus.confirmed,
        recordingStatus: ViolationRecordingStatus.ready,
      ).toQueryParameters(page: 1, size: 20);

      expect(query['from'], '2026-08-22T00:00:00Z');
      expect(query['to'], '2026-08-22T23:59:59Z');
      expect(query['type'], 'MISSING_GLOVES');
      expect(query['cameraId'], 'cam-1');
      expect(query['departmentId'], 'dep-1');
      expect(query['lifecycleStatus'], 'COMPLETED');
      expect(query['reviewStatus'], 'CONFIRMED');
      expect(query['recordingStatus'], 'READY');
      expect(query['page'], '1');
      expect(query['size'], '20');
      expect(query.containsKey('sort'), isFalse);
      expect(query['from'], isNot(matches(r'^\d{4}-\d{2}-\d{2}$')));
    });
  });

  group('ViolationPage / list item', () {
    test('page parse edilir', () {
      final page = ViolationPage.fromJson({
        'content': [
          {
            'violationId': 'v-1',
            'cameraId': 'c-1',
            'departmentId': 'd-1',
            'type': 'RESTRICTED_ZONE',
            'startedAt': '2026-08-22T10:00:00Z',
            'endedAt': null,
            'confidence': 0.91,
            'lifecycleStatus': 'ACTIVE',
            'reviewStatus': 'UNREVIEWED',
            'recordingStatus': 'RECORDING',
            'updatedAt': '2026-08-22T10:01:00Z',
          },
        ],
        'page': 0,
        'size': 20,
        'totalElements': 1,
        'totalPages': 1,
      });

      expect(page.content, hasLength(1));
      expect(page.page, 0);
      expect(page.hasMore, isFalse);
      final item = page.content.first;
      expect(item.id, 'v-1');
      expect(item.type, ViolationType.restrictedZone);
      expect(item.lifecycleStatus, ViolationLifecycleStatus.active);
      expect(item.reviewStatus, ViolationReviewStatus.unreviewed);
      expect(item.recordingStatus, ViolationRecordingStatus.recording);
    });

    test('lifecycle/review/recording ayrı parse edilir', () {
      final item = ViolationListItem.fromJson({
        'violationId': 'v-2',
        'type': 'MISSING_GLOVES',
        'lifecycleStatus': 'ERROR',
        'reviewStatus': 'FALSE_ALARM',
        'recordingStatus': 'READY',
      });
      expect(item.lifecycleStatus, isNot(equals(item.reviewStatus.toString())));
      expect(item.lifecycleStatus, ViolationLifecycleStatus.error);
      expect(item.reviewStatus, ViolationReviewStatus.falseAlarm);
      expect(item.recordingStatus, ViolationRecordingStatus.ready);
    });
  });
}
