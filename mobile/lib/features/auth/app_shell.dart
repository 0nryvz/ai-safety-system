import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../core/network/backend_client.dart';
import '../../core/theme/strix_brand.dart';
import '../../features/session/camera_selection_page.dart';
import '../../shared/widgets/placeholder_page.dart';
import 'auth_controller.dart';

enum _ShellTab {
  dashboard,
  cameras,
  violations,
  notifications,
  users,
  cameraBroadcast,
}

/// Role-aware AppShell placeholder. Feature içerikleri sonra bağlanır.
class AppShell extends ConsumerStatefulWidget {
  const AppShell({super.key});

  @override
  ConsumerState<AppShell> createState() => _AppShellState();
}

class _AppShellState extends ConsumerState<AppShell> {
  _ShellTab _tab = _ShellTab.dashboard;
  bool _openingBroadcast = false;

  List<_ShellDestination> _destinations(bool isAdmin) {
    return [
      const _ShellDestination(
        tab: _ShellTab.dashboard,
        label: 'Dashboard',
        icon: Icons.dashboard_outlined,
      ),
      const _ShellDestination(
        tab: _ShellTab.cameras,
        label: 'Kameralar',
        icon: Icons.videocam_outlined,
      ),
      const _ShellDestination(
        tab: _ShellTab.violations,
        label: 'İhlaller',
        icon: Icons.warning_amber_outlined,
      ),
      const _ShellDestination(
        tab: _ShellTab.notifications,
        label: 'Bildirimler',
        icon: Icons.notifications_outlined,
      ),
      if (isAdmin)
        const _ShellDestination(
          tab: _ShellTab.users,
          label: 'Kullanıcılar',
          icon: Icons.people_outline,
        ),
      const _ShellDestination(
        tab: _ShellTab.cameraBroadcast,
        label: 'Kamera Yayını',
        icon: Icons.live_tv_outlined,
      ),
    ];
  }

  Future<void> _openCameraBroadcast() async {
    if (_openingBroadcast) {
      return;
    }

    final session = ref.read(authSessionProvider);
    final token = session.accessToken;
    if (token == null || token.isEmpty) {
      return;
    }

    setState(() => _openingBroadcast = true);

    try {
      final cameras =
          await ref.read(backendClientProvider).fetchCameras(token);

      if (!mounted) {
        return;
      }

      await Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => CameraSelectionPage(cameras: cameras),
        ),
      );
    } on BackendAuthException catch (e) {
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.message)),
      );
      if (e.kind == BackendAuthFailureKind.invalidCredentials) {
        ref.read(authSessionProvider.notifier).clearSession();
      }
    } catch (_) {
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Kamera listesi alınamadı.')),
      );
    } finally {
      if (mounted) {
        setState(() => _openingBroadcast = false);
      }
    }
  }

  Widget _bodyFor(_ShellTab tab) {
    return switch (tab) {
      _ShellTab.dashboard => const PlaceholderPage(title: 'Dashboard'),
      _ShellTab.cameras => const PlaceholderPage(title: 'Kameralar'),
      _ShellTab.violations => const PlaceholderPage(title: 'İhlaller'),
      _ShellTab.notifications => const PlaceholderPage(title: 'Bildirimler'),
      _ShellTab.users => const PlaceholderPage(title: 'Kullanıcılar'),
      _ShellTab.cameraBroadcast => PlaceholderPage(
          title: 'Kamera Yayını',
          subtitle: _openingBroadcast
              ? 'Kamera listesi yükleniyor…'
              : 'Mevcut yayın akışını açmak için alttaki sekmeyi kullanın.',
        ),
    };
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(authSessionProvider);
    final destinations = _destinations(session.isAdmin);
    final selectedIndex = destinations.indexWhere((d) => d.tab == _tab);
    final safeIndex = selectedIndex < 0 ? 0 : selectedIndex;

    if (selectedIndex < 0 && destinations.isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          setState(() => _tab = destinations.first.tab);
        }
      });
    }

    final userLabel = session.currentUser?.fullName.isNotEmpty == true
        ? session.currentUser!.fullName
        : session.currentUser?.email ?? '';

    return Scaffold(
      backgroundColor: StrixBrand.background,
      appBar: AppBar(
        title: Text(
          StrixBrand.shortName,
          style: GoogleFonts.inter(fontWeight: FontWeight.w700),
        ),
        actions: [
          if (userLabel.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: Center(
                child: Text(
                  userLabel,
                  style: GoogleFonts.inter(
                    fontSize: 13,
                    color: StrixBrand.textSecondary,
                  ),
                ),
              ),
            ),
          IconButton(
            tooltip: 'Çıkış',
            onPressed: () {
              ref.read(authSessionProvider.notifier).clearSession();
            },
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: _bodyFor(destinations[safeIndex].tab),
      bottomNavigationBar: NavigationBar(
        selectedIndex: safeIndex,
        onDestinationSelected: (index) {
          final dest = destinations[index];
          if (dest.tab == _ShellTab.cameraBroadcast) {
            setState(() => _tab = dest.tab);
            _openCameraBroadcast();
            return;
          }
          setState(() => _tab = dest.tab);
        },
        destinations: [
          for (final d in destinations)
            NavigationDestination(
              icon: Icon(d.icon),
              label: d.label,
            ),
        ],
      ),
    );
  }
}

class _ShellDestination {
  final _ShellTab tab;
  final String label;
  final IconData icon;

  const _ShellDestination({
    required this.tab,
    required this.label,
    required this.icon,
  });
}
