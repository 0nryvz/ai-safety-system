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
import 'auth_controller.dart';
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
    };
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

    return Scaffold(
      backgroundColor: StrixBrand.background,
      appBar: AppBar(
        titleSpacing: 16,
        title: Row(
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: Image.asset(
                StrixBrand.logoAsset,
                width: 28,
                height: 28,
                fit: BoxFit.cover,
              ),
            ),
            const SizedBox(width: 10),
            Text(
              StrixBrand.shortName,
              style: GoogleFonts.inter(fontWeight: FontWeight.w700),
            ),
          ],
        ),
        actions: [
          _BroadcastAction(
            loading: _loadingCameras,
            compact: MediaQuery.sizeOf(context).width < 420,
            onPressed: _openCameraBroadcast,
          ),
          if (userLabel.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(right: 2),
              child: Center(
                child: Tooltip(
                  message: userLabel,
                  child: Container(
                    constraints: BoxConstraints(
                      maxWidth: MediaQuery.sizeOf(context).width * 0.28,
                    ),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 6,
                    ),
                    decoration: BoxDecoration(
                      color: StrixBrand.surfaceSubtle,
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(color: StrixBrand.border),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(
                          Icons.person_outline,
                          size: 16,
                          color: StrixBrand.textSecondary,
                        ),
                        if (MediaQuery.sizeOf(context).width >= 400) ...[
                          const SizedBox(width: 6),
                          Flexible(
                            child: Text(
                              userLabel,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: GoogleFonts.inter(
                                fontSize: 12,
                                fontWeight: FontWeight.w600,
                                color: StrixBrand.textSecondary,
                              ),
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
              ),
            ),
          IconButton(
            tooltip: 'Çıkış',
            onPressed: () {
              ref.read(authSessionProvider.notifier).signOut();
            },
            icon: const Icon(Icons.logout_rounded),
          ),
        ],
      ),
      body: Column(
        children: [
          if (_error != null) _ShellErrorBanner(message: _error!),
          Expanded(
            child: _bodyFor(activeTab),
          ),
        ],
      ),
      bottomNavigationBar: DecoratedBox(
        decoration: const BoxDecoration(
          color: StrixBrand.surface,
          border: Border(
            top: BorderSide(color: StrixBrand.border),
          ),
        ),
        child: NavigationBar(
          selectedIndex: selectedIndex,
          labelBehavior: MediaQuery.sizeOf(context).width < 360
              ? NavigationDestinationLabelBehavior.onlyShowSelected
              : NavigationDestinationLabelBehavior.alwaysShow,
          onDestinationSelected: (index) {
            setState(() => _tab = destinations[index].tab);
          },
          destinations: [
            for (final destination in destinations)
              NavigationDestination(
                icon: Icon(destination.icon),
                selectedIcon: Icon(
                  destination.selectedIcon,
                  color: StrixBrand.primary,
                ),
                label: destination.label,
                tooltip: destination.label,
              ),
          ],
        ),
      ),
    );
  }
}

class _BroadcastAction extends StatelessWidget {
  final bool loading;
  final bool compact;
  final VoidCallback onPressed;

  const _BroadcastAction({
    required this.loading,
    required this.compact,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    final icon = loading
        ? const SizedBox(
            width: 18,
            height: 18,
            child: CircularProgressIndicator(strokeWidth: 2),
          )
        : const Icon(Icons.live_tv_outlined, size: 20);

    if (compact) {
      return IconButton(
        tooltip: 'Kamera Yayını',
        onPressed: loading ? null : onPressed,
        icon: icon,
      );
    }

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 4),
      child: Tooltip(
        message: 'Kamera Yayını',
        child: FilledButton.tonalIcon(
          onPressed: loading ? null : onPressed,
          icon: icon,
          label: Text(
            'Yayın',
            style: GoogleFonts.inter(fontWeight: FontWeight.w600),
          ),
          style: FilledButton.styleFrom(
            visualDensity: VisualDensity.compact,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            minimumSize: const Size(0, 36),
            foregroundColor: StrixBrand.primary,
            backgroundColor: StrixBrand.primary.withValues(alpha: 0.10),
          ),
        ),
      ),
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
