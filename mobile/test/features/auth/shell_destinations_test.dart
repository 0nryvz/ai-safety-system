import 'package:camera_stream_app/core/models/user_summary.dart';
import 'package:camera_stream_app/features/auth/auth_session.dart';
import 'package:camera_stream_app/features/auth/shell_destinations.dart';
import 'package:flutter_test/flutter_test.dart';

AuthSession _sessionWithRoles(Set<String> roles) {
  return AuthSession(
    accessToken: 'jwt',
    refreshToken: 'rt',
    currentUser: UserSummary(
      id: '1',
      email: 'a@b.c',
      fullName: 'A',
      active: true,
      roles: roles,
    ),
  );
}

void main() {
  test('ADMIN Kullanıcılar girişini görür', () {
    final destinations = shellDestinationsFor(_sessionWithRoles({'ADMIN'}));

    expect(
      destinations.map((d) => d.tab),
      containsAll([
        ShellTab.dashboard,
        ShellTab.cameras,
        ShellTab.violations,
        ShellTab.notifications,
        ShellTab.users,
        ShellTab.cameraBroadcast,
      ]),
    );
    expect(destinations.last.tab, ShellTab.cameraBroadcast);
  });

  test('OHS_SPECIALIST Kullanıcılar girişini görmez', () {
    final destinations =
        shellDestinationsFor(_sessionWithRoles({'OHS_SPECIALIST'}));

    expect(destinations.map((d) => d.tab), isNot(contains(ShellTab.users)));
    expect(destinations.map((d) => d.tab), contains(ShellTab.cameraBroadcast));
  });

  test('SHIFT_SUPERVISOR kamera yönetimi affordance görmez', () {
    final session = _sessionWithRoles({'SHIFT_SUPERVISOR'});

    expect(session.canManageCameras, isFalse);
    expect(session.canManageUsers, isFalse);
    expect(
      shellDestinationsFor(session).map((d) => d.tab),
      isNot(contains(ShellTab.users)),
    );
  });

  test('ADMIN kamera yönetimi affordance görür', () {
    expect(_sessionWithRoles({'ADMIN'}).canManageCameras, isTrue);
  });
}
