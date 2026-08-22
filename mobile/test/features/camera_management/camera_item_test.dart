import 'package:camera_stream_app/features/camera_management/models/camera_item.dart';
import 'package:camera_stream_app/features/camera_management/models/camera_status.dart';
import 'package:camera_stream_app/features/camera_management/presentation/camera_labels.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('CameraItem', () {
    test('status alanı parse edilir', () {
      final item = CameraItem.fromJson({
        'id': '11111111-0000-4000-8000-000000000001',
        'name': 'Kaynak 1',
        'code': 'CAM-01',
        'departmentId': '22222222-0000-4000-8000-000000000002',
        'departmentName': 'Üretim',
        'active': true,
        'status': 'ONLINE',
        'lastSeenAt': '2026-08-22T10:30:00Z',
        'activeSessionId': null,
      });

      expect(item.status, CameraStatus.online);
      expect(item.name, 'Kaynak 1');
      expect(item.lastSeenAt, isNotNull);
    });

    test('WEAK ve OFFLINE durumları', () {
      final weak = CameraItem.fromJson({
        'id': '1',
        'name': 'A',
        'code': 'A',
        'departmentId': '2',
        'active': true,
        'status': 'WEAK',
      });
      final offline = CameraItem.fromJson({
        'id': '1',
        'name': 'A',
        'code': 'A',
        'departmentId': '2',
        'active': false,
        'status': 'OFFLINE',
      });

      expect(weak.status, CameraStatus.weak);
      expect(offline.status, CameraStatus.offline);
    });

    test('connectionStatus alanı yok sayılır; status kullanılır', () {
      final item = CameraItem.fromJson({
        'id': '1',
        'name': 'A',
        'code': 'A',
        'departmentId': '2',
        'active': true,
        'status': 'ONLINE',
        'connectionStatus': 'OFFLINE',
      });

      expect(item.status, CameraStatus.online);
    });
  });

  group('formatCameraLastSeen', () {
    test('null değeri tire gösterir', () {
      expect(formatCameraLastSeen(null), 'Son görülme: —');
    });

    test('UTC anı yerel saate çevirir', () {
      final utc = DateTime.utc(2026, 8, 22, 10, 30);
      final local = utc.toLocal();
      final text = formatCameraLastSeen(utc);

      expect(text, isNot(contains('Z')));
      expect(
        text,
        'Son görülme: '
        '${local.day.toString().padLeft(2, '0')}.'
        '${local.month.toString().padLeft(2, '0')}.'
        '${local.year.toString().padLeft(4, '0')} '
        '${local.hour.toString().padLeft(2, '0')}:'
        '${local.minute.toString().padLeft(2, '0')}',
      );
    });
  });
}
