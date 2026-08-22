import 'package:flutter/material.dart';

import 'auth_session.dart';

enum ShellTab {
  dashboard,
  cameras,
  violations,
  notifications,
  users,
}

class ShellDestination {
  final ShellTab tab;
  final String label;
  final IconData icon;
  final IconData selectedIcon;

  const ShellDestination({
    required this.tab,
    required this.label,
    required this.icon,
    required this.selectedIcon,
  });
}

const List<ShellDestination> _baseDestinations = [
  ShellDestination(
    tab: ShellTab.dashboard,
    label: 'Özet',
    icon: Icons.space_dashboard_outlined,
    selectedIcon: Icons.space_dashboard,
  ),
  ShellDestination(
    tab: ShellTab.cameras,
    label: 'Kameralar',
    icon: Icons.videocam_outlined,
    selectedIcon: Icons.videocam,
  ),
  ShellDestination(
    tab: ShellTab.violations,
    label: 'İhlaller',
    icon: Icons.warning_amber_outlined,
    selectedIcon: Icons.warning_amber,
  ),
  ShellDestination(
    tab: ShellTab.notifications,
    label: 'Bildirimler',
    icon: Icons.notifications_outlined,
    selectedIcon: Icons.notifications,
  ),
];

const ShellDestination _usersDestination = ShellDestination(
  tab: ShellTab.users,
  label: 'Kullanıcılar',
  icon: Icons.people_outline,
  selectedIcon: Icons.people,
);

/// Alt menü sekmeleri. Yayın ayrı AppBar eylemidir; 6. sekme menüyü sıkıştırır.
List<ShellDestination> shellDestinationsFor(AuthSession session) {
  return [
    ..._baseDestinations,
    if (session.canManageUsers) _usersDestination,
  ];
}
