import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../core/theme/strix_brand.dart';
import '../../../shared/widgets/error_banner.dart';
import '../data/dashboard_repository.dart';
import '../models/dashboard_failure.dart';
import 'widgets/dashboard_charts.dart';
import 'widgets/dashboard_kpi_grid.dart';
import 'widgets/dashboard_recent_list.dart';

/// Operasyon dashboard'u — backend REST verisi.
///
/// Production: [DashboardTabPage] + [AuthenticatedApi].
/// Test: [repository] inject edilir.
/// Recent kart dokunuşu [onRecentViolationTap] ile detail navigasyonuna açılır.
class DashboardPage extends StatefulWidget {
  final DashboardLoader? repository;
  final ValueChanged<String>? onRecentViolationTap;
  final bool showAppBar;

  const DashboardPage({
    super.key,
    this.repository,
    this.onRecentViolationTap,
    this.showAppBar = true,
  }) : assert(
          repository != null,
          'DashboardPage test veya shell için repository gerektirir.',
        );

  @override
  State<DashboardPage> createState() => _DashboardPageState();
}

class _DashboardPageState extends State<DashboardPage> {
  late final DashboardLoader _repository;
  DashboardSnapshot? _snapshot;
  DashboardFailure? _failure;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _repository = widget.repository!;
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _failure = null;
    });

    try {
      final snapshot = await _repository.load();
      if (!mounted) {
        return;
      }
      setState(() {
        _snapshot = snapshot;
        _loading = false;
      });
    } on DashboardFailure catch (failure) {
      if (!mounted) {
        return;
      }
      setState(() {
        _failure = failure;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() {
        _failure = const DashboardFailure(
          'Dashboard yüklenemedi.',
          kind: DashboardFailureKind.unknown,
        );
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: StrixBrand.background,
      appBar: widget.showAppBar
          ? AppBar(
              title: const Text('Dashboard'),
            )
          : null,
      body: RefreshIndicator(
        color: StrixBrand.primary,
        onRefresh: _load,
        child: _buildBody(),
      ),
    );
  }

  Widget _buildBody() {
    if (_loading && _snapshot == null) {
      return ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        children: const [
          SizedBox(height: 160),
          Center(
            child: CircularProgressIndicator(color: StrixBrand.primary),
          ),
        ],
      );
    }

    if (_failure != null && _snapshot == null) {
      return ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16),
        children: [
          const SizedBox(height: 48),
          ErrorBanner(
            message: _failure!.isOffline
                ? 'Çevrimdışı — backend\'e ulaşılamıyor.'
                : _failure!.message,
            actionLabel: 'Yeniden dene',
            onAction: _load,
          ),
          if (_failure!.isOffline) ...[
            const SizedBox(height: 16),
            Text(
              'Bağlantı gelince aşağı çekerek yenileyebilirsiniz.',
              textAlign: TextAlign.center,
              style: GoogleFonts.inter(
                fontSize: 13,
                color: StrixBrand.textSecondary,
              ),
            ),
          ],
        ],
      );
    }

    final snapshot = _snapshot!;
    final offlineCameras = snapshot.summary.offlineCameraCount;

    return LayoutBuilder(
      builder: (context, constraints) {
        return SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: constraints.maxHeight),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (_failure != null) ...[
                  ErrorBanner(
                    message: _failure!.message,
                    actionLabel: 'Yeniden dene',
                    onAction: _load,
                  ),
                  const SizedBox(height: 12),
                ],
                if (offlineCameras > 0) ...[
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: StrixBrand.warning.withValues(alpha: 0.12),
                      borderRadius:
                          BorderRadius.circular(StrixBrand.radiusCard),
                      border: Border.all(
                        color: StrixBrand.warning.withValues(alpha: 0.4),
                      ),
                    ),
                    child: Text(
                      '$offlineCameras kamera çevrimdışı',
                      style: GoogleFonts.inter(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: StrixBrand.textPrimary,
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                ],
                DashboardKpiGrid(summary: snapshot.summary),
                const SizedBox(height: 14),
                DashboardTrendChart(points: snapshot.trend),
                const SizedBox(height: 14),
                DashboardDistributionChart(items: snapshot.distribution),
                const SizedBox(height: 14),
                DashboardRecentList(
                  items: snapshot.recentViolations,
                  onTap: widget.onRecentViolationTap,
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
