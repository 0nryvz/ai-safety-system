import 'package:camera_stream_app/features/camera_management/data/camera_management_repository.dart';
import 'package:camera_stream_app/features/camera_management/models/camera_item.dart';
import 'package:camera_stream_app/features/camera_management/models/camera_status.dart';
import 'package:camera_stream_app/features/camera_management/models/department_option.dart';
import 'package:camera_stream_app/features/camera_management/presentation/camera_form_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

class _FormFakeRepository implements CameraManagementPort {
  @override
  Future<List<CameraItem>> loadCameras() async => [];

  @override
  Future<CameraItem> createCamera({
    required String name,
    required String code,
    required String departmentId,
  }) async {
    return CameraItem(
      id: 'new-id',
      name: name,
      code: code,
      departmentId: departmentId,
      active: true,
      status: CameraStatus.offline,
    );
  }

  @override
  Future<CameraItem> updateCamera(
    String id, {
    String? name,
    String? code,
    String? departmentId,
    bool? active,
  }) async {
    return CameraItem(
      id: id,
      name: name ?? 'X',
      code: code ?? 'Y',
      departmentId: departmentId ?? 'dept-1',
      active: active ?? true,
      status: CameraStatus.offline,
    );
  }

  @override
  Future<List<DepartmentOption>> loadDepartments() async => const [
        DepartmentOption(id: 'dept-1', name: 'Üretim'),
      ];
}

void main() {
  testWidgets('create form validation boş alanları reddeder', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: CameraFormPage.create(
          repository: _FormFakeRepository(),
          departments: const [
            DepartmentOption(id: 'dept-1', name: 'Üretim'),
          ],
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Oluştur'));
    await tester.pumpAndSettle();

    expect(find.text('Kamera adı zorunludur.'), findsOneWidget);
    expect(find.text('Kamera kodu zorunludur.'), findsOneWidget);
  });

  testWidgets('edit form validation boş alanları reddeder', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: CameraFormPage.edit(
          repository: _FormFakeRepository(),
          departments: const [
            DepartmentOption(id: 'dept-1', name: 'Üretim'),
          ],
          camera: const CameraItem(
            id: 'cam-1',
            name: 'Kaynak 1',
            code: 'CAM-01',
            departmentId: 'dept-1',
            departmentName: 'Üretim',
            active: true,
            status: CameraStatus.online,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextFormField).at(0), '   ');
    await tester.enterText(find.byType(TextFormField).at(1), '');
    await tester.tap(find.text('Kaydet'));
    await tester.pumpAndSettle();

    expect(find.text('Kamera adı zorunludur.'), findsOneWidget);
    expect(find.text('Kamera kodu zorunludur.'), findsOneWidget);
  });

  testWidgets('edit form listede olmayan departmanı korur', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: CameraFormPage.edit(
          repository: _FormFakeRepository(),
          departments: const [
            DepartmentOption(id: 'dept-2', name: 'Lojistik'),
          ],
          camera: const CameraItem(
            id: 'cam-1',
            name: 'Kaynak 1',
            code: 'CAM-01',
            departmentId: 'dept-1',
            departmentName: 'Üretim',
            active: true,
            status: CameraStatus.online,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Üretim'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
