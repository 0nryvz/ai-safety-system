import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../core/error/api_failure.dart';
import '../../core/theme/strix_brand.dart';
import '../../features/session/camera_option.dart';
import '../../features/session/camera_selection_page.dart';
import '../../shared/widgets/placeholder_page.dart';
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

  Widget _bodyFor(ShellTab tab, {required bool canManageCameras}) {
    return switch (tab) {
      ShellTab.dashboard => const PlaceholderPage(title: 'Dashboard'),
      ShellTab.cameras => PlaceholderPage(
          title: 'Kameralar',
          subtitle: canManageCameras
              ? 'ADMIN: kamera oluşturma/güncelleme aksiyonları bu ekranda açılacak.'
              : null,
        ),
      ShellTab.violations => const PlaceholderPage(title: 'İhlaller'),
      ShellTab.notifications => const PlaceholderPage(title: 'Bildirimler'),
      ShellTab.users => const PlaceholderPage(title: 'Kullanıcılar'),
      ShellTab.cameraBroadcast => PlaceholderPage(
          title: 'Kamera Yayını',
          subtitle: _loadingCameras
              ? 'Kamera listesi yükleniyor…'
              : 'Mevcut yayın akışını açmak için sekmeye yeniden dokunun.',
        ),
    };
  }

  @override
  Widget build(BuildContext context) {
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
            child: _bodyFor(
              activeTab,
              canManageCameras: session.canManageCameras,
            ),
          ),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: selectedIndex,
        onDestinationSelected: (index) {
          final destination = destinations[index];
          setState(() => _tab = destination.tab);

          if (destination.tab == ShellTab.cameraBroadcast) {
            _openCameraBroadcast();
          }
        },
        destinations: [
          for (final destination in destinations)
            NavigationDestination(
              icon: Icon(destination.icon),
              label: destination.label,
            ),
        ],
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
