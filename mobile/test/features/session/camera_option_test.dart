import 'package:camera_stream_app/features/session/camera_option.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('CameraOption.fromJson', () {
    test('Backend CameraResponse status alanını okur', () {
      final camera = CameraOption.fromJson({
        'id': '33333333-0000-4000-8000-000000000001',
        'name': 'Kaynak-1 Kamera A',
        'code': 'CAM-WELDING-001',
        'departmentId': '11111111-0000-4000-8000-000000000001',
        'departmentName': 'Kaynakhane',
        'active': true,
        'status': 'ONLINE',
        'lastSeenAt': '2026-08-21T10:00:00Z',
        'activeSessionId': null,
      });

      expect(camera.id, '33333333-0000-4000-8000-000000000001');
      expect(camera.name, 'Kaynak-1 Kamera A');
      expect(camera.code, 'CAM-WELDING-001');
      expect(camera.departmentName, 'Kaynakhane');
      expect(camera.active, isTrue);
      expect(camera.connectionStatus, 'ONLINE');
    });

    test('status yoksa eski connectionStatus alanına düşer', () {
      final camera = CameraOption.fromJson({
        'id': 'uuid',
        'name': 'Kamera',
        'active': true,
        'connectionStatus': 'WEAK',
      });

      expect(camera.connectionStatus, 'WEAK');
    });

    test('status, connectionStatus üzerinde önceliklidir', () {
      final camera = CameraOption.fromJson({
        'id': 'uuid',
        'name': 'Kamera',
        'active': true,
        'status': 'OFFLINE',
        'connectionStatus': 'ONLINE',
      });

      expect(camera.connectionStatus, 'OFFLINE');
    });

    test('eksik opsiyonel alanlar çökme üretmez', () {
      final camera = CameraOption.fromJson({
        'id': 'uuid',
        'name': 'Kamera',
        'active': false,
      });

      expect(camera.code, isNull);
      expect(camera.departmentName, isNull);
      expect(camera.connectionStatus, isNull);
    });

    test('name yoksa yer tutucu kullanılır', () {
      final camera = CameraOption.fromJson({'id': 'uuid', 'active': true});

      expect(camera.name, 'İsimsiz kamera');
    });
  });

  group('seçilebilirlik', () {
    test('pasif kamera seçilemez', () {
      const camera = CameraOption(id: 'x', name: 'A', active: false);

      expect(camera.isSelectable, isFalse);
      expect(camera.subtitle, contains('Pasif'));
    });

    test('aktif kamera seçilebilir', () {
      const camera = CameraOption(id: 'x', name: 'A', active: true);

      expect(camera.isSelectable, isTrue);
      expect(camera.subtitle, isNot(contains('Pasif')));
    });

    test('alt başlık kod ve departmanı birleştirir', () {
      const camera = CameraOption(
        id: 'x',
        name: 'A',
        code: 'CAM-1',
        departmentName: 'Kaynakhane',
        active: true,
      );

      expect(camera.subtitle, 'CAM-1 • Kaynakhane');
    });
  });
}
