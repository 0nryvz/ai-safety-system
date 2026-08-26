import '../../../core/models/user_summary.dart';
import '../models/user_department_option.dart';
import 'users_api.dart';

abstract class UsersPort {
  Future<List<UserSummary>> loadUsers();

  Future<UserSummary> createUser({
    required String email,
    required String password,
    required String fullName,
    required List<String> roleNames,
    required List<String> departmentIds,
  });

  Future<UserSummary> updateUser(
    String id, {
    String? fullName,
    List<String>? roleNames,
    List<String>? departmentIds,
    bool? active,
  });

  Future<void> deactivateUser(String id);

  Future<List<UserDepartmentOption>> loadDepartments();
}

class UsersRepository implements UsersPort {
  UsersRepository({required this._api});

  final UsersApi _api;

  @override
  Future<List<UserSummary>> loadUsers() => _api.fetchUsers();

  @override
  Future<UserSummary> createUser({
    required String email,
    required String password,
    required String fullName,
    required List<String> roleNames,
    required List<String> departmentIds,
  }) =>
      _api.createUser(
        email: email,
        password: password,
        fullName: fullName,
        roleNames: roleNames,
        departmentIds: departmentIds,
      );

  @override
  Future<UserSummary> updateUser(
    String id, {
    String? fullName,
    List<String>? roleNames,
    List<String>? departmentIds,
    bool? active,
  }) =>
      _api.updateUser(
        id,
        fullName: fullName,
        roleNames: roleNames,
        departmentIds: departmentIds,
        active: active,
      );

  @override
  Future<void> deactivateUser(String id) => _api.deactivateUser(id);

  @override
  Future<List<UserDepartmentOption>> loadDepartments() =>
      _api.fetchDepartments();
}
