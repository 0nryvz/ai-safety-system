import 'package:camera_stream_app/core/models/user_summary.dart';
import 'package:camera_stream_app/features/users/data/users_repository.dart';
import 'package:camera_stream_app/features/users/models/user_department_option.dart';
import 'package:camera_stream_app/features/users/presentation/user_form_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

class _FormFakeUsers implements UsersPort {
  String? createdEmail;
  String? createdPassword;
  String? createdName;
  List<String>? createdRoles;
  List<String>? createdDepartments;
  String? updatedId;
  String? updatedName;
  List<String>? updatedRoles;
  List<String>? updatedDepartments;
  bool? updatedActive;

  @override
  Future<List<UserSummary>> loadUsers() async => [];

  @override
  Future<UserSummary> createUser({
    required String email,
    required String password,
    required String fullName,
    required List<String> roleNames,
    required List<String> departmentIds,
  }) async {
    createdEmail = email;
    createdPassword = password;
    createdName = fullName;
    createdRoles = roleNames;
    createdDepartments = departmentIds;
    return UserSummary(
      id: 'new-id',
      email: email,
      fullName: fullName,
      active: true,
      roles: roleNames.toSet(),
      departmentIds: departmentIds.toSet(),
    );
  }

  @override
  Future<UserSummary> updateUser(
    String id, {
    String? fullName,
    List<String>? roleNames,
    List<String>? departmentIds,
    bool? active,
  }) async {
    updatedId = id;
    updatedName = fullName;
    updatedRoles = roleNames;
    updatedDepartments = departmentIds;
    updatedActive = active;
    return UserSummary(
      id: id,
      email: 'ada@isg.local',
      fullName: fullName ?? 'Ada Admin',
      active: active ?? true,
      roles: {...?roleNames},
      departmentIds: {...?departmentIds},
    );
  }

  @override
  Future<void> deactivateUser(String id) async {}

  @override
  Future<List<UserDepartmentOption>> loadDepartments() async => const [
        UserDepartmentOption(id: 'dept-1', name: 'Üretim'),
      ];
}

const _departments = [
  UserDepartmentOption(id: 'dept-1', name: 'Üretim'),
];

void main() {
  testWidgets('create form validation boş alanları reddeder', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: UserFormPage.create(
          repository: _FormFakeUsers(),
          departments: _departments,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Oluştur'));
    await tester.pumpAndSettle();

    expect(find.text('Ad soyad zorunludur.'), findsOneWidget);
    expect(find.text('E-posta zorunludur.'), findsOneWidget);
    expect(find.text('Şifre zorunludur.'), findsOneWidget);
  });

  testWidgets('create form e-posta ve şifre kurallarını uygular', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: UserFormPage.create(
          repository: _FormFakeUsers(),
          departments: _departments,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextFormField).at(0), 'Ada');
    await tester.enterText(find.byType(TextFormField).at(1), 'not-an-email');
    await tester.enterText(find.byType(TextFormField).at(2), '123');
    await tester.tap(find.text('Oluştur'));
    await tester.pumpAndSettle();

    expect(find.text('Geçerli bir e-posta girin.'), findsOneWidget);
    expect(find.text('Şifre en az 6 karakter olmalıdır.'), findsOneWidget);
  });

  testWidgets('create form en az bir rol ister', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: UserFormPage.create(
          repository: _FormFakeUsers(),
          departments: _departments,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextFormField).at(0), 'Ada Admin');
    await tester.enterText(find.byType(TextFormField).at(1), 'ada@isg.local');
    await tester.enterText(find.byType(TextFormField).at(2), 'secret1');
    await tester.tap(find.text('Oluştur'));
    await tester.pumpAndSettle();

    expect(find.text('En az bir rol seçin.'), findsOneWidget);
  });

  testWidgets('create geçerli form gönderir', (tester) async {
    final repo = _FormFakeUsers();

    await tester.pumpWidget(
      MaterialApp(
        home: UserFormPage.create(
          repository: repo,
          departments: _departments,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextFormField).at(0), 'Ada Admin');
    await tester.enterText(find.byType(TextFormField).at(1), 'ada@isg.local');
    await tester.enterText(find.byType(TextFormField).at(2), 'secret1');
    await tester.tap(find.text('Vardiya amiri'));
    await tester.tap(find.text('Üretim'));
    await tester.tap(find.text('Oluştur'));
    await tester.pumpAndSettle();

    expect(repo.createdEmail, 'ada@isg.local');
    expect(repo.createdPassword, 'secret1');
    expect(repo.createdName, 'Ada Admin');
    expect(repo.createdRoles, ['SHIFT_SUPERVISOR']);
    expect(repo.createdDepartments, ['dept-1']);
  });

  testWidgets('edit form PATCH alanlarını gönderir', (tester) async {
    final repo = _FormFakeUsers();

    await tester.pumpWidget(
      MaterialApp(
        home: UserFormPage.edit(
          repository: repo,
          departments: _departments,
          user: const UserSummary(
            id: 'user-1',
            email: 'ada@isg.local',
            fullName: 'Ada Admin',
            active: true,
            roles: {'ADMIN'},
            departmentIds: {'dept-1'},
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextFormField).at(0), 'Ada Güncel');
    await tester.tap(find.text('Kaydet'));
    await tester.pumpAndSettle();

    expect(repo.updatedId, 'user-1');
    expect(repo.updatedName, 'Ada Güncel');
    expect(repo.updatedRoles, ['ADMIN']);
    expect(repo.updatedDepartments, ['dept-1']);
    expect(repo.updatedActive, isTrue);
  });
}
