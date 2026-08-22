import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../core/theme/strix_brand.dart';
import '../../../shared/widgets/error_banner.dart';
import '../data/dashboard_repository.dart';
import '../models/dashboard_failure.dart';
import 'widgets/dashboard_charts.dart';
import 'widgets/dashboard_kpi_grid.dart';
import 'widgets/dashboard_recent_list.dart';
import 'widgets/dashboard_status_card.dart';

/// Operasyon dashboard'u — backend REST verisi.
///
/// Production: [DashboardTabPage] + [AuthenticatedApi].
/// Test: [repository] inject edilir.
/// Recent kart dokunuşu [onRecentViolationTap] ile detail navigasyonuna açılır.
class DashboardPage extends StatefulWidget {
  final DashboardLoader? repository;
  final ValueChanged<String>? onRecentViolationTap;
  final bool showAppBar;

  /// Realtime reconnect recovery tick. Artınca mevcut [_load] tekrarlanır.
  final int recoveryTick;

  const DashboardPage({
    super.key,
    this.repository,
    this.onRecentViolationTap,
    this.showAppBar = true,
    this.recoveryTick = 0,
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
  Future<void>? _inFlight;

  @override
  void initState() {
    super.initState();
    _repository = widget.repository!;
    _load();
  }

  @override
  void didUpdateWidget(DashboardPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.recoveryTick != widget.recoveryTick) {
      _load();
    }
  }

  Future<void> _load() {
    final pending = _inFlight;
    if (pending != null) {
      return pending;
    }

    late final Future<void> future;
    future = () async {
      try {
        await _loadBody();
      } finally {
        if (identical(_inFlight, future)) {
          _inFlight = null;
        }
      }
    }();
    _inFlight = future;
    return future;
  }

  Future<void> _loadBody() async {
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
              actions: [
                IconButton(
                  tooltip: 'Yenile',
                  onPressed: _loading ? null : _load,
                  icon: const Icon(Icons.refresh),
                ),
              ],
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
          const SizedBox(height: 16),
          Text(
            _failure!.isOffline
                ? 'Bağlantınızı kontrol edip yeniden deneyin.'
                : _failure!.kind == DashboardFailureKind.forbidden
                    ? 'Bu işlem için yönetici yetkisi gerekir.'
                    : 'Biraz sonra yeniden deneyebilirsiniz.',
            textAlign: TextAlign.center,
            style: GoogleFonts.inter(
              fontSize: 13,
              color: StrixBrand.textSecondary,
            ),
          ),
        ],
      );
    }

    final snapshot = _snapshot!;

    return LayoutBuilder(
      builder: (context, constraints) {
        return SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: constraints.maxHeight),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (!widget.showAppBar) ...[
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Özet',
                              style: GoogleFonts.inter(
                                fontSize: 22,
                                fontWeight: FontWeight.w700,
                                color: StrixBrand.textPrimary,
                              ),
                            ),
                            const SizedBox(height: 6),
                            Text(
                              'Günün ihlal ve kamera durumuna hızlı bakış.',
                              style: GoogleFonts.inter(
                                fontSize: 14,
                                color: StrixBrand.textSecondary,
                              ),
                            ),
                          ],
                        ),
                      ),
                      IconButton(
                        tooltip: 'Yenile',
                        onPressed: _loading ? null : _load,
                        icon: _loading
                            ? const SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            : const Icon(Icons.refresh),
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),
                ],
                if (_failure != null) ...[
                  ErrorBanner(
                    message: _failure!.message,
                    actionLabel: 'Yeniden dene',
                    onAction: _load,
                  ),
                  const SizedBox(height: 12),
                ],
                DashboardStatusCard(summary: snapshot.summary),
                const SizedBox(height: 12),
                DashboardKpiGrid(summary: snapshot.summary),
                const SizedBox(height: 12),
                DashboardTrendChart(points: snapshot.trend),
                const SizedBox(height: 12),
                DashboardDistributionChart(items: snapshot.distribution),
                const SizedBox(height: 12),
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
