import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../core/error/api_failure.dart';
import '../../core/theme/strix_brand.dart';
import '../../features/camera_management/presentation/cameras_tab_page.dart';
import '../../features/dashboard/presentation/dashboard_tab_page.dart';
import '../../features/notifications/data/realtime_providers.dart';
import '../../features/notifications/presentation/notifications_tab_page.dart';
import '../../features/session/camera_option.dart';
import '../../features/session/camera_selection_page.dart';
import '../../features/users/presentation/users_tab_page.dart';
import '../../features/violations/data/violations_api.dart';
import '../../features/violations/data/violations_repository.dart';
import '../../features/violations/presentation/violation_detail_page.dart';
import '../../features/violations/presentation/violations_tab_page.dart';
import '../../shared/widgets/placeholder_page.dart';
import 'auth_controller.dart';
import 'floating_navigation_menu.dart';
import 'shell_destinations.dart';

/// Role-aware AppShell. Auth kararları (401/refresh/session invalid) merkezi
/// pipeline'da alınır; bu widget yalnız sonucu ve [ApiFailure] mesajını gösterir.
class AppShell extends ConsumerStatefulWidget {
  const AppShell({super.key});

  @override
  ConsumerState<AppShell> createState() => _AppShellState();
}

class _AppShellState extends ConsumerState<AppShell> {
  ShellTab _tab = ShellTab.dashboard;
  bool _loadingCameras = false;
  String? _error;

  Future<void> _openCameraBroadcast() async {
    if (_loadingCameras) {
      return;
    }

    setState(() {
      _loadingCameras = true;
      _error = null;
    });

    try {
      final decoded = await ref
          .read(authenticatedApiProvider)
          .getJsonList('/api/v1/cameras');

      final cameras = decoded
          .cast<Map<String, dynamic>>()
          .map(CameraOption.fromJson)
          .toList(growable: false);

      if (!mounted) {
        return;
      }

      await Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => CameraSelectionPage(cameras: cameras),
        ),
      );
    } on ApiFailure catch (failure) {
      if (!mounted) {
        return;
      }
      setState(() => _error = failure.message);
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() => _error = 'Kamera listesi alınamadı.');
    } finally {
      if (mounted) {
        setState(() => _loadingCameras = false);
      }
    }
  }

  void _openViolationDetail(String violationId) {
    if (violationId.isEmpty) {
      return;
    }

    final api = ref.read(authenticatedApiProvider);
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ViolationDetailPage(
          violationId: violationId,
          repository: ViolationsRepository(
            api: ViolationsApi.fromAuthenticated(api),
          ),
        ),
      ),
    );
  }

  Widget _bodyFor(ShellTab tab) {
    return switch (tab) {
      ShellTab.dashboard => DashboardTabPage(
          onRecentViolationTap: _openViolationDetail,
        ),
      ShellTab.cameras => const CamerasTabPage(),
      ShellTab.violations => const ViolationsTabPage(),
      ShellTab.notifications => const NotificationsTabPage(),
      ShellTab.users => const UsersTabPage(),
      ShellTab.cameraBroadcast => PlaceholderPage(
          title: 'Kamera Yayını',
          subtitle: _loadingCameras
              ? 'Kamera listesi yükleniyor…'
              : 'Mevcut yayın akışını açmak için sekmeye yeniden dokunun.',
        ),
    };
  }

  void _onDestinationSelected(List<ShellDestination> destinations, int index) {
    if (index < 0 || index >= destinations.length) {
      return;
    }

    final destination = destinations[index];
    if (destination.tab != _tab) {
      setState(() => _tab = destination.tab);
    }

    if (destination.tab == ShellTab.cameraBroadcast) {
      _openCameraBroadcast();
    }
  }

  @override
  Widget build(BuildContext context) {
    // Login sonrası tek STOMP bağlantısını başlatır; logout'ta kapatır.
    ref.watch(realtimeLifecycleProvider);
    final session = ref.watch(authSessionProvider);
    final destinations = shellDestinationsFor(session);
    final rawIndex = destinations.indexWhere((d) => d.tab == _tab);
    final selectedIndex = rawIndex < 0 ? 0 : rawIndex;
    final activeTab = destinations[selectedIndex].tab;

    final userLabel = session.currentUser?.fullName.isNotEmpty == true
        ? session.currentUser!.fullName
        : session.currentUser?.email ?? '';

    return FloatingNavigationMenu(
      items: destinations,
      selectedIndex: selectedIndex,
      onSelected: (index) => _onDestinationSelected(destinations, index),
      child: Scaffold(
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
                ref.read(authSessionProvider.notifier).signOut();
              },
              icon: const Icon(Icons.logout),
            ),
          ],
        ),
        body: Column(
          children: [
            if (_error != null) _ShellErrorBanner(message: _error!),
            Expanded(
              child: _withActionClearance(_bodyFor(activeTab)),
            ),
          ],
        ),
      ),
    );
  }

  Widget _withActionClearance(Widget child) {
    return Builder(
      builder: (context) {
        final media = MediaQuery.of(context);
        return MediaQuery(
          data: media.copyWith(
            padding: media.padding.copyWith(
              bottom:
                  media.padding.bottom + FloatingNavigationMenu.actionClearance,
            ),
            viewPadding: media.viewPadding.copyWith(
              bottom: media.viewPadding.bottom +
                  FloatingNavigationMenu.actionClearance,
            ),
          ),
          child: child,
        );
      },
    );
  }
}

class _ShellErrorBanner extends StatelessWidget {
  final String message;

  const _ShellErrorBanner({required this.message});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.fromLTRB(16, 12, 16, 0),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: StrixBrand.critical.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(StrixBrand.radiusInput),
        border: Border.all(color: StrixBrand.critical.withValues(alpha: 0.35)),
      ),
      child: Text(
        message,
        style: GoogleFonts.inter(
          fontSize: 13,
          height: 1.4,
          color: StrixBrand.critical,
        ),
      ),
    );
  }
}
