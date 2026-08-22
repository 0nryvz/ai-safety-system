import 'package:camera_stream_app/shared/media/media.dart';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../core/theme/strix_brand.dart';
import '../../../shared/widgets/error_banner.dart';
import '../data/violations_repository.dart';
import '../models/violation_detail.dart';
import '../models/violation_failure.dart';
import '../models/violation_review_status.dart';
import 'violation_labels.dart';
import 'widgets/violation_status_chips.dart';

class ViolationDetailPage extends StatefulWidget {
  final String violationId;
  final ViolationsPort repository;

  /// Test override. Production [ViolationClipPlayer] kullanır.
  final Widget Function(String violationId, String? recordingStatus)?
      clipPlayerBuilder;

  const ViolationDetailPage({
    super.key,
    required this.violationId,
    required this.repository,
    this.clipPlayerBuilder,
  });

  @override
  State<ViolationDetailPage> createState() => _ViolationDetailPageState();
}

class _ViolationDetailPageState extends State<ViolationDetailPage> {
  ViolationDetail? _detail;
  ViolationFailure? _failure;
  bool _loading = true;
  bool _reviewing = false;
  String? _reviewMessage;
  bool _reviewConflict = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _failure = null;
    });

    try {
      final detail = await widget.repository.loadDetail(widget.violationId);
      if (!mounted) {
        return;
      }
      setState(() {
        _detail = detail;
        _loading = false;
      });
    } on ViolationFailure catch (failure) {
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
        _failure = const ViolationFailure(
          'İhlal detayı yüklenemedi.',
          kind: ViolationFailureKind.unknown,
        );
        _loading = false;
      });
    }
  }

  Future<void> _submitReview(ViolationReviewStatus status) async {
    final detail = _detail;
    if (detail == null || _reviewing) {
      return;
    }

    setState(() {
      _reviewing = true;
      _reviewMessage = null;
      _reviewConflict = false;
    });

    try {
      await widget.repository.submitReview(
        id: detail.id,
        reviewStatus: status,
        version: detail.version,
      );
      final refreshed = await widget.repository.loadDetail(detail.id);
      if (!mounted) {
        return;
      }
      setState(() {
        _detail = refreshed;
        _reviewing = false;
        _reviewMessage = 'İnceleme kaydedildi.';
        _reviewConflict = false;
      });
    } on ViolationFailure catch (failure) {
      await _handleReviewFailure(failure);
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() {
        _reviewing = false;
        _reviewMessage = 'İnceleme kaydedilemedi.';
        _reviewConflict = false;
      });
    }
  }

  Future<void> _handleReviewFailure(ViolationFailure failure) async {
    if (failure.isConflict) {
      try {
        final refreshed =
            await widget.repository.loadDetail(widget.violationId);
        if (!mounted) {
          return;
        }
        setState(() {
          _detail = refreshed;
          _reviewing = false;
          _reviewConflict = true;
          _reviewMessage =
              'Kayıt başka bir işlemle güncellenmiş. Güncel hali yüklendi; '
              'yeni sürüm üzerinden tekrar deneyin.';
        });
        return;
      } catch (_) {
        if (!mounted) {
          return;
        }
        setState(() {
          _reviewing = false;
          _reviewConflict = true;
          _reviewMessage =
              'Kayıt değişmiş. Güncel veriyi almak için yeniden deneyin.';
        });
        return;
      }
    }

    if (!mounted) {
      return;
    }
    setState(() {
      _reviewing = false;
      _reviewConflict = false;
      _reviewMessage = failure.message;
    });
  }

  Widget _clipHost(ViolationDetail detail) {
    final recording = detail.recordingStatus.wireValue;
    if (widget.clipPlayerBuilder != null) {
      return widget.clipPlayerBuilder!(detail.id, recording);
    }
    return ViolationClipPlayer(
      violationId: detail.id,
      recordingStatus: recording,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: StrixBrand.background,
      appBar: AppBar(
        title: const Text('İhlal detayı'),
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_loading && _detail == null) {
      return const Center(
        child: CircularProgressIndicator(color: StrixBrand.primary),
      );
    }

    if (_failure != null && _detail == null) {
      return ListView(
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
        ],
      );
    }

    final detail = _detail!;
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
      children: [
        Text(
          violationTypeLabel(detail.type),
          style: GoogleFonts.inter(
            fontSize: 22,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 8),
        ViolationStatusChips(
          lifecycleStatus: detail.lifecycleStatus,
          reviewStatus: detail.reviewStatus,
          recordingStatus: detail.recordingStatus,
        ),
        const SizedBox(height: 16),
        _clipHost(detail),
        const SizedBox(height: 16),
        _MetaCard(detail: detail),
        const SizedBox(height: 16),
        Text(
          'İnceleme',
          style: GoogleFonts.inter(
            fontSize: 16,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          'Sürüm ${detail.version}',
          style: GoogleFonts.inter(
            fontSize: 12,
            color: StrixBrand.textSecondary,
          ),
        ),
        if (_reviewMessage != null) ...[
          const SizedBox(height: 12),
          _reviewConflict
              ? ErrorBanner(
                  message: _reviewMessage!,
                  actionLabel: 'Tekrar dene',
                  onAction: _reviewing ? null : _load,
                )
              : Text(
                  _reviewMessage!,
                  style: GoogleFonts.inter(
                    color: StrixBrand.success,
                    fontWeight: FontWeight.w600,
                  ),
                ),
        ],
        const SizedBox(height: 12),
        for (final status in ViolationReviewStatus.patchable) ...[
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: OutlinedButton(
              onPressed: _reviewing ? null : () => _submitReview(status),
              child: Text(reviewStatusLabel(status)),
            ),
          ),
        ],
      ],
    );
  }
}

class _MetaCard extends StatelessWidget {
  final ViolationDetail detail;

  const _MetaCard({required this.detail});

  @override
  Widget build(BuildContext context) {
    final camera = [
      if (detail.cameraName != null && detail.cameraName!.isNotEmpty)
        detail.cameraName!,
      if (detail.cameraCode != null && detail.cameraCode!.isNotEmpty)
        detail.cameraCode!,
    ].join(' • ');

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: StrixBrand.surface,
        borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
        border: Border.all(color: StrixBrand.border),
      ),
      child: Column(
        children: [
          _row('Kamera', camera.isEmpty ? '—' : camera),
          _row('Departman', detail.departmentName ?? '—'),
          _row('Güven', formatConfidence(detail.confidence)),
          _row('Model', detail.modelVersion ?? '—'),
          _row('Tespit', formatLocalDateTime(detail.detectedAt)),
          _row('Başlangıç', formatLocalDateTime(detail.startedAt)),
          _row('Bitiş', formatLocalDateTime(detail.endedAt)),
          _row('Klip', detail.clipReady ? 'Hazır' : 'Hazırlanıyor'),
          _row('Kapak', detail.coverImageReady ? 'Hazır' : 'Hazırlanıyor'),
        ],
      ),
    );
  }

  Widget _row(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 96,
            child: Text(
              label,
              style: GoogleFonts.inter(
                fontSize: 13,
                color: StrixBrand.textSecondary,
              ),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: GoogleFonts.inter(
                fontSize: 13,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
