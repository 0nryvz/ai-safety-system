import 'camera_option.dart';

/// Backend 2 kapalıyken kullanılan demo kamera listesi.
///
/// Alanlar `demo-seed.sql` ve `CameraResponse` ile aynıdır; mock alan adı
/// uydurulmaz. Gerçek backend ayağa kalkınca giriş akışı tercih edilir.
class DemoCameras {
  const DemoCameras._();

  static const List<CameraOption> catalog = [
    CameraOption(
      id: '33333333-0000-4000-8000-000000000001',
      name: 'Kaynak-1 Kamera A',
      code: 'CAM-WELDING-001',
      departmentName: 'Kaynak Hatti 1',
      active: true,
      connectionStatus: 'OFFLINE',
    ),
    CameraOption(
      id: '33333333-0000-4000-8000-000000000002',
      name: 'Kaynak-2 Kamera B',
      code: 'CAM-WELDING-002',
      departmentName: 'Kaynak Hatti 2',
      active: true,
      connectionStatus: 'OFFLINE',
    ),
    CameraOption(
      id: '33333333-0000-4000-8000-000000000003',
      name: 'Kaynak-1 Kamera C',
      code: 'CAM-WELDING-003',
      departmentName: 'Kaynak Hatti 1',
      active: true,
      connectionStatus: 'OFFLINE',
    ),
  ];
}
