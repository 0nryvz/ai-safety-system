import '../models/camera_item.dart';
import '../models/department_option.dart';
import 'camera_management_api.dart';

abstract class CameraManagementPort {
  Future<List<CameraItem>> loadCameras();

  Future<CameraItem> createCamera({
    required String name,
    required String code,
    required String departmentId,
  });

  Future<CameraItem> updateCamera(
    String id, {
    String? name,
    String? code,
    String? departmentId,
    bool? active,
  });

  Future<List<DepartmentOption>> loadDepartments();
}

abstract class CamerasLoader {
  Future<List<CameraItem>> loadCameras();
}

class CameraManagementRepository
    implements CameraManagementPort, CamerasLoader {
  CameraManagementRepository({required this._api});

  final CameraManagementApi _api;

  @override
  Future<List<CameraItem>> loadCameras() => _api.fetchCameras();

  Future<CameraItem> fetchCamera(String id) => _api.fetchCamera(id);

  @override
  Future<CameraItem> createCamera({
    required String name,
    required String code,
    required String departmentId,
  }) =>
      _api.createCamera(
        name: name,
        code: code,
        departmentId: departmentId,
      );

  @override
  Future<CameraItem> updateCamera(
    String id, {
    String? name,
    String? code,
    String? departmentId,
    bool? active,
  }) =>
      _api.updateCamera(
        id,
        name: name,
        code: code,
        departmentId: departmentId,
        active: active,
      );

  @override
  Future<List<DepartmentOption>> loadDepartments() => _api.fetchDepartments();
}
