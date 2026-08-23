import 'package:camera_stream_app/core/theme/strix_brand.dart';
import 'package:camera_stream_app/features/auth/floating_navigation_menu.dart';
import 'package:camera_stream_app/features/auth/shell_destinations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

Widget _wrap({
  required List<ShellDestination> items,
  required int selectedIndex,
  required ValueChanged<int> onSelected,
}) {
  return MaterialApp(
    theme: StrixBrand.theme(),
    home: FloatingNavigationMenu(
      items: items,
      selectedIndex: selectedIndex,
      onSelected: onSelected,
      child: const Scaffold(
        body: ColoredBox(
          color: StrixBrand.background,
          child: SizedBox.expand(),
        ),
      ),
    ),
  );
}

const _destinations = [
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
  ),
  ShellDestination(
    tab: ShellTab.violations,
    label: 'İhlaller',
    icon: Icons.warning_amber_rounded,
  ),
  ShellDestination(
    tab: ShellTab.notifications,
    label: 'Bildirimler',
    icon: Icons.notifications_none_rounded,
  ),
  ShellDestination(
    tab: ShellTab.users,
    label: 'Kullanıcılar',
    icon: Icons.people_outline_rounded,
  ),
  ShellDestination(
    tab: ShellTab.cameraBroadcast,
    label: 'Kamera Yayını',
    icon: Icons.live_tv_outlined,
  ),
];

void main() {
  testWidgets('uzun Türkçe etiketler taşma üretmez', (tester) async {
    tester.view.physicalSize = const Size(320, 568);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      _wrap(
        items: const [
          ShellDestination(
            tab: ShellTab.dashboard,
            label: 'Dashboard',
            icon: Icons.dashboard_outlined,
          ),
          ShellDestination(
            tab: ShellTab.cameras,
            label: 'Kameralar',
            icon: Icons.videocam_outlined,
          ),
          ShellDestination(
            tab: ShellTab.violations,
            label: 'İhlaller',
            icon: Icons.warning_amber_rounded,
          ),
          ShellDestination(
            tab: ShellTab.notifications,
            label: 'Bildirimler',
            icon: Icons.notifications_none_rounded,
          ),
          ShellDestination(
            tab: ShellTab.users,
            label: 'Kullanıcılar',
            icon: Icons.people_outline_rounded,
          ),
          ShellDestination(
            tab: ShellTab.cameraBroadcast,
            label: 'Kamera Yayını',
            icon: Icons.live_tv_outlined,
          ),
        ],
        selectedIndex: 0,
        onSelected: (_) {},
      ),
    );

    await tester.tap(find.byKey(FloatingNavigationMenu.toggleKey));
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('Kamera Yayını'), findsOneWidget);
    expect(find.text('Bildirimler'), findsOneWidget);
    expect(find.text('Kullanıcılar'), findsOneWidget);
  });

  testWidgets('kısa yükseklikte liste taşmaz, kaydırılabilir', (tester) async {
    tester.view.physicalSize = const Size(360, 420);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      _wrap(
        items: _destinations,
        selectedIndex: 0,
        onSelected: (_) {},
      ),
    );

    await tester.tap(find.byKey(FloatingNavigationMenu.toggleKey));
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.byType(SingleChildScrollView), findsOneWidget);
    expect(find.byIcon(Icons.close_rounded), findsOneWidget);
  });

  testWidgets('sistem geri tuşu yalnızca menüyü kapatır', (tester) async {
    var selected = -1;
    await tester.pumpWidget(
      _wrap(
        items: _destinations,
        selectedIndex: 0,
        onSelected: (index) => selected = index,
      ),
    );

    await tester.tap(find.byKey(FloatingNavigationMenu.toggleKey));
    await tester.pumpAndSettle();
    expect(find.text('Kameralar'), findsOneWidget);

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();

    expect(find.text('Kameralar'), findsNothing);
    expect(find.byIcon(Icons.menu_rounded), findsOneWidget);
    expect(selected, -1);
  });
}
