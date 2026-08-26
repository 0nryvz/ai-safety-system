import 'package:camera_stream_app/core/models/user_summary.dart';
import 'package:camera_stream_app/features/users/data/users_repository.dart';
import 'package:camera_stream_app/features/users/models/user_department_option.dart';
import 'package:camera_stream_app/features/users/models/user_failure.dart';
import 'package:camera_stream_app/features/users/presentation/users_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

class _FakeUsers implements UsersPort {
  _FakeUsers({
    this.users = const [],
    this.loadError,
  });

  List<UserSummary> users;
  List<UserDepartmentOption> departments = const [];
  UserFailure? loadError;
  String? lastDeactivatedId;
  String? lastUpdatedId;
  bool? lastActive;

  @override
  Future<List<UserSummary>> loadUsers() async {
    if (loadError != null) {
      throw loadError!;
    }
    return users;
  }

  @override
  Future<UserSummary> createUser({
    required String email,
    required String password,
    required String fullName,
    required List<String> roleNames,
    required List<String> departmentIds,
  }) async {
    throw UnimplementedError();
  }

  @override
  Future<UserSummary> updateUser(
    String id, {
    String? fullName,
    List<String>? roleNames,
    List<String>? departmentIds,
    bool? active,
  }) async {
    lastUpdatedId = id;
    lastActive = active;
    return users.firstWhere((user) => user.id == id);
  }

  @override
  Future<void> deactivateUser(String id) async {
    lastDeactivatedId = id;
  }

  @override
  Future<List<UserDepartmentOption>> loadDepartments() async => departments;
}

UserSummary _user({
  String id = 'user-1',
  String email = 'ada@isg.local',
  String fullName = 'Ada Admin',
  bool active = true,
  Set<String> roles = const {'ADMIN'},
}) {
  return UserSummary(
    id: id,
    email: email,
    fullName: fullName,
    active: active,
    departmentName: 'Üretim',
    roles: roles,
    departmentIds: const {'dept-1'},
  );
}

void main() {
  Widget wrap(Widget child) => MaterialApp(home: child);

  testWidgets('admin aksiyonları görünür', (tester) async {
    await tester.pumpWidget(
      wrap(
        UsersPage(
          repository: _FakeUsers(users: [_user()]),
          canManageUsers: true,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Kullanıcı ekle'), findsOneWidget);
    expect(find.byIcon(Icons.edit_outlined), findsOneWidget);
    expect(find.text('Pasifleştir'), findsOneWidget);
    expect(find.byType(Switch), findsOneWidget);
  });

  testWidgets('non-admin admin kontrolleri gizli', (tester) async {
    await tester.pumpWidget(
      wrap(
        UsersPage(
          repository: _FakeUsers(users: [_user()]),
          canManageUsers: false,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Ada Admin'), findsOneWidget);
    expect(find.text('Kullanıcı ekle'), findsNothing);
    expect(find.byIcon(Icons.edit_outlined), findsNothing);
    expect(find.text('Pasifleştir'), findsNothing);
    expect(find.byType(Switch), findsNothing);
  });

  testWidgets('liste ad, e-posta ve rol gösterir', (tester) async {
    await tester.pumpWidget(
      wrap(
        UsersPage(
          repository: _FakeUsers(
            users: [
              _user(roles: const {'OHS_SPECIALIST'}),
            ],
          ),
          canManageUsers: false,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Ada Admin'), findsOneWidget);
    expect(find.text('ada@isg.local'), findsOneWidget);
    expect(find.text('İSG uzmanı'), findsOneWidget);
    expect(find.text('Üretim'), findsOneWidget);
    expect(find.text('Aktif'), findsOneWidget);
  });

  testWidgets('403 forbidden mesajı gösterir', (tester) async {
    await tester.pumpWidget(
      wrap(
        UsersPage(
          repository: _FakeUsers(
            loadError: const UserFailure(
              'Bu işlem için yetkiniz yok.',
              kind: UserFailureKind.forbidden,
            ),
          ),
          canManageUsers: false,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Bu işlem için yetkiniz yok.'), findsOneWidget);
    expect(find.text('Yeniden dene'), findsOneWidget);
  });

  testWidgets('empty state', (tester) async {
    await tester.pumpWidget(
      wrap(
        UsersPage(
          repository: _FakeUsers(),
          canManageUsers: true,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Kullanıcı bulunmuyor.'), findsOneWidget);
  });

  testWidgets('Pasifleştir DELETE/deactivate çağırır', (tester) async {
    final repo = _FakeUsers(users: [_user(id: 'user-2')]);

    await tester.pumpWidget(
      wrap(
        UsersPage(
          repository: repo,
          canManageUsers: true,
          currentUserId: 'user-1',
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Pasifleştir'));
    await tester.pumpAndSettle();

    expect(repo.lastDeactivatedId, 'user-2');
  });

  testWidgets('kendi hesabını pasifleştirme gizli', (tester) async {
    await tester.pumpWidget(
      wrap(
        UsersPage(
          repository: _FakeUsers(users: [_user(id: 'self')]),
          canManageUsers: true,
          currentUserId: 'self',
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Pasifleştir'), findsNothing);
    expect(find.byType(Switch), findsNothing);
    expect(find.byIcon(Icons.edit_outlined), findsOneWidget);
  });

  testWidgets('küçük ekranda uzun isim overflow yok', (tester) async {
    tester.view.physicalSize = const Size(320, 568);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      wrap(
        UsersPage(
          repository: _FakeUsers(
            users: [
              _user(
                fullName: 'Çok Uzun Kullanıcı Adı Taşma Kontrolü İçin',
                email: 'cokuzun.kullanici.adi@ornek.isg.local',
              ),
            ],
          ),
          canManageUsers: true,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('Pasifleştir'), findsOneWidget);
  });
}
