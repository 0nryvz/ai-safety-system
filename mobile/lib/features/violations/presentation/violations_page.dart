import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../core/theme/strix_brand.dart';
import '../../../shared/widgets/error_banner.dart';
import '../data/violations_repository.dart';
import '../models/violation_failure.dart';
import '../models/violation_filter_option.dart';
import '../models/violation_filters.dart';
import '../models/violation_list_item.dart';
import 'violation_detail_page.dart';
import 'widgets/violation_card.dart';
import 'widgets/violation_filter_sheet.dart';
import 'widgets/violation_filter_summary.dart';

class ViolationsPage extends StatefulWidget {
  final ViolationsPort repository;

  const ViolationsPage({
    super.key,
    required this.repository,
  });

  @override
  State<ViolationsPage> createState() => _ViolationsPageState();
}

class _ViolationsPageState extends State<ViolationsPage> {
  ViolationFilters _filters = ViolationFilters.empty;
  List<ViolationListItem> _items = const [];
  int _page = 0;
  bool _hasMore = false;
  bool _loading = true;
  bool _loadingMore = false;
  ViolationFailure? _failure;

  @override
  void initState() {
    super.initState();
    _load(reset: true);
  }

  Future<void> _load({required bool reset}) async {
    if (reset) {
      setState(() {
        _loading = true;
        _failure = null;
        _page = 0;
      });
    } else {
      setState(() => _loadingMore = true);
    }

    try {
      final page = await widget.repository.loadPage(
        filters: _filters,
        page: reset ? 0 : _page + 1,
      );
      if (!mounted) {
        return;
      }
      setState(() {
        _items = reset ? page.content : [..._items, ...page.content];
        _page = page.page;
        _hasMore = page.hasMore;
        _loading = false;
        _loadingMore = false;
        _failure = null;
      });
    } on ViolationFailure catch (failure) {
      if (!mounted) {
        return;
      }
      setState(() {
        _failure = failure;
        _loading = false;
        _loadingMore = false;
        if (reset) {
          _items = const [];
        }
      });
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() {
        _failure = const ViolationFailure(
          'İhlal listesi yüklenemedi.',
          kind: ViolationFailureKind.unknown,
        );
        _loading = false;
        _loadingMore = false;
      });
    }
  }

  Future<void> _openFilters() async {
    List<ViolationFilterOption> cameras = const [];
    List<ViolationFilterOption> departments = const [];
    try {
      final results = await Future.wait([
        widget.repository.loadCameras(),
        widget.repository.loadDepartments(),
      ]);
      cameras = results[0];
      departments = results[1];
    } catch (_) {
      // Filtre dropdown'ları boş kalabilir; tarih/status hâlâ çalışır.
    }

    if (!mounted) {
      return;
    }

    final applied = await showViolationFilterSheet(
      context: context,
      current: _filters,
      cameras: cameras,
      departments: departments,
    );
    if (applied == null) {
      return;
    }
    setState(() => _filters = applied);
    await _load(reset: true);
  }

  Future<void> _openDetail(ViolationListItem item) async {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ViolationDetailPage(
          violationId: item.id,
          repository: widget.repository,
        ),
      ),
    );
    if (mounted) {
      await _load(reset: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: StrixBrand.background,
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _loading ? null : _openFilters,
        icon: const Icon(Icons.filter_list),
        label: const Text('Filtre'),
      ),
      body: RefreshIndicator(
        color: StrixBrand.primary,
        onRefresh: () => _load(reset: true),
        child: _buildBody(),
      ),
    );
  }

  Widget _buildBody() {
    if (_loading && _items.isEmpty) {
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

    if (_failure != null && _items.isEmpty) {
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
            onAction: () => _load(reset: true),
          ),
        ],
      );
    }

    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 88),
      children: [
        Text(
          'İhlaller',
          style: GoogleFonts.inter(
            fontSize: 22,
            fontWeight: FontWeight.w700,
            color: StrixBrand.textPrimary,
          ),
        ),
        const SizedBox(height: 6),
        Text(
          'Geçmiş ihlalleri inceleyin ve gözden geçirin.',
          style: GoogleFonts.inter(
            fontSize: 14,
            color: StrixBrand.textSecondary,
          ),
        ),
        const SizedBox(height: 12),
        ViolationFilterSummary(
          filters: _filters,
          onClear: () {
            setState(() => _filters = ViolationFilters.empty);
            _load(reset: true);
          },
        ),
        if (_items.isEmpty)
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: StrixBrand.surface,
              borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
              border: Border.all(color: StrixBrand.border),
            ),
            child: Text(
              'İhlal bulunmuyor.',
              textAlign: TextAlign.center,
              style: GoogleFonts.inter(color: StrixBrand.textSecondary),
            ),
          )
        else ...[
          for (final item in _items)
            ViolationCard(
              item: item,
              onTap: () => _openDetail(item),
            ),
          if (_hasMore)
            TextButton(
              onPressed: _loadingMore ? null : () => _load(reset: false),
              child: _loadingMore
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Text('Daha fazla yükle'),
            ),
        ],
      ],
    );
  }
}
