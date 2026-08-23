import 'package:flutter/material.dart';

import 'auth_session.dart';

enum ShellTab {
  dashboard,
  cameras,
  violations,
  notifications,
  users,
  cameraBroadcast,
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
    IconData? selectedIcon,
  }) : selectedIcon = selectedIcon ?? icon;
}

const List<ShellDestination> _baseDestinations = [
  ShellDestination(
    tab: ShellTab.dashboard,
    label: 'Dashboard',
    icon: Icons.dashboard_outlined,
    selectedIcon: Icons.dashboard_rounded,
  ),
  ShellDestination(
    tab: ShellTab.cameras,
    label: 'Kameralar',
    icon: Icons.videocam_outlined,
    selectedIcon: Icons.videocam_rounded,
  ),
  ShellDestination(
    tab: ShellTab.violations,
    label: 'İhlaller',
    icon: Icons.warning_amber_rounded,
    selectedIcon: Icons.warning_amber,
  ),
  ShellDestination(
    tab: ShellTab.notifications,
    label: 'Bildirimler',
    icon: Icons.notifications_none_rounded,
    selectedIcon: Icons.notifications_rounded,
  ),
];

const ShellDestination _usersDestination = ShellDestination(
  tab: ShellTab.users,
  label: 'Kullanıcılar',
  icon: Icons.people_outline_rounded,
  selectedIcon: Icons.people_rounded,
);

const ShellDestination _broadcastDestination = ShellDestination(
  tab: ShellTab.cameraBroadcast,
  label: 'Kamera Yayını',
  icon: Icons.live_tv_outlined,
  selectedIcon: Icons.live_tv,
);

/// Role-aware navigation girişleri. Backend yetkisi esastır; buradaki
/// görünürlük yalnız UI gürültüsünü azaltır.
List<ShellDestination> shellDestinationsFor(AuthSession session) {
  return [
    ..._baseDestinations,
    if (session.canManageUsers) _usersDestination,
    _broadcastDestination,
  ];
}
