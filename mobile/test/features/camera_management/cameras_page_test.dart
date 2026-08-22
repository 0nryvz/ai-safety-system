import 'package:camera_stream_app/features/camera_management/data/camera_management_repository.dart';
import 'package:camera_stream_app/features/camera_management/models/camera_item.dart';
import 'package:camera_stream_app/features/camera_management/models/camera_management_failure.dart';
import 'package:camera_stream_app/features/camera_management/models/camera_status.dart';
import 'package:camera_stream_app/features/camera_management/models/department_option.dart';
import 'package:camera_stream_app/features/camera_management/presentation/camera_labels.dart';
import 'package:camera_stream_app/features/camera_management/presentation/cameras_page.dart';
import 'package:camera_stream_app/features/camera_management/presentation/widgets/camera_status_badge.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

class _FakeRepository implements CameraManagementPort {
  _FakeRepository(this._loader);

  final Future<List<CameraItem>> Function() _loader;

  @override
  Future<List<CameraItem>> loadCameras() => _loader();

  @override
  Future<CameraItem> createCamera({
    required String name,
    required String code,
    required String departmentId,
  }) async {
    throw UnimplementedError();
  }

  @override
  Future<CameraItem> updateCamera(
    String id, {
    String? name,
    String? code,
    String? departmentId,
    bool? active,
  }) async {
    throw UnimplementedError();
  }

  @override
  Future<List<DepartmentOption>> loadDepartments() async => [];
}

CameraItem _camera({
  required String name,
  required CameraStatus status,
  bool active = true,
}) {
  return CameraItem(
    id: '11111111-0000-4000-8000-000000000001',
    name: name,
    code: 'CAM-01',
    departmentId: '22222222-0000-4000-8000-000000000002',
    departmentName: 'Üretim',
    active: active,
    status: status,
    lastSeenAt: DateTime.utc(2026, 8, 22, 10),
  );
}

void main() {
  Widget wrap(Widget child) => MaterialApp(home: child);

  testWidgets('liste ve ONLINE/WEAK/OFFLINE badge render', (tester) async {
    final repo = _FakeRepository(
      () async => [
        _camera(name: 'Online Kamera', status: CameraStatus.online),
        _camera(name: 'Zayıf Kamera', status: CameraStatus.weak),
        _camera(name: 'Offline Kamera', status: CameraStatus.offline),
      ],
    );

    await tester.pumpWidget(
      wrap(
        CamerasPage(
          repository: repo,
          canManageCameras: false,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Online Kamera'), findsOneWidget);
    expect(find.text('Zayıf Kamera'), findsOneWidget);
    expect(find.text('Offline Kamera'), findsOneWidget);
    expect(find.text('Çevrimiçi'), findsOneWidget);
    expect(find.text('Zayıf'), findsOneWidget);
    expect(find.text('Çevrimdışı'), findsOneWidget);
    expect(find.byType(CameraStatusBadge), findsNWidgets(3));
    expect(
      find.text(formatCameraLastSeen(DateTime.utc(2026, 8, 22, 10))),
      findsNWidgets(3),
    );
  });

  testWidgets('admin aksiyonları görünür', (tester) async {
    final repo = _FakeRepository(
      () async => [_camera(name: 'Admin Kamera', status: CameraStatus.online)],
    );

    await tester.pumpWidget(
      wrap(
        CamerasPage(
          repository: repo,
          canManageCameras: true,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Kamera ekle'), findsOneWidget);
    expect(find.byIcon(Icons.edit_outlined), findsOneWidget);
    expect(find.byType(Switch), findsOneWidget);
  });

  testWidgets('non-admin admin kontrolleri gizli', (tester) async {
    final repo = _FakeRepository(
      () async => [_camera(name: 'Görüntüle', status: CameraStatus.online)],
    );

    await tester.pumpWidget(
      wrap(
        CamerasPage(
          repository: repo,
          canManageCameras: false,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Kamera ekle'), findsNothing);
    expect(find.byIcon(Icons.edit_outlined), findsNothing);
    expect(find.byType(Switch), findsNothing);
  });

  testWidgets('403 forbidden mesajı gösterir', (tester) async {
    final repo = _FakeRepository(
      () async => throw const CameraManagementFailure(
        'Bu işlem için yetkiniz yok.',
        kind: CameraManagementFailureKind.forbidden,
      ),
    );

    await tester.pumpWidget(
      wrap(
        CamerasPage(
          repository: repo,
          canManageCameras: false,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Bu işlem için yetkiniz yok.'), findsOneWidget);
    expect(find.text('Yeniden dene'), findsOneWidget);
  });

  testWidgets('error state yeniden dene gösterir', (tester) async {
    final repo = _FakeRepository(
      () async => throw const CameraManagementFailure(
        'Kamera işlemi tamamlanamadı (500).',
        kind: CameraManagementFailureKind.server,
      ),
    );

    await tester.pumpWidget(
      wrap(
        CamerasPage(
          repository: repo,
          canManageCameras: false,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.textContaining('tamamlanamadı'), findsOneWidget);
    expect(find.text('Yeniden dene'), findsOneWidget);
  });

  testWidgets('empty state', (tester) async {
    final repo = _FakeRepository(() async => []);

    await tester.pumpWidget(
      wrap(
        CamerasPage(
          repository: repo,
          canManageCameras: false,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Kamera bulunmuyor.'), findsOneWidget);
  });

  test('CameraStatusBadge bilinmeyen durum', () {
    expect(
      const CameraStatusBadge(status: CameraStatus.unknown).status.label,
      'Bilinmiyor',
    );
  });
}
