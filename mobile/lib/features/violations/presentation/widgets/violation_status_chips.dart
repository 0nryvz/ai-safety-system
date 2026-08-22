import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../../core/theme/strix_brand.dart';
import '../../models/violation_lifecycle_status.dart';
import '../../models/violation_recording_status.dart';
import '../../models/violation_review_status.dart';
import '../violation_labels.dart';

class ViolationStatusChips extends StatelessWidget {
  final ViolationLifecycleStatus lifecycleStatus;
  final ViolationReviewStatus reviewStatus;
  final ViolationRecordingStatus recordingStatus;

  const ViolationStatusChips({
    super.key,
    required this.lifecycleStatus,
    required this.reviewStatus,
    required this.recordingStatus,
  });

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 6,
      runSpacing: 6,
      children: [
        _Chip(
          prefix: 'Yaşam',
          label: lifecycleStatusLabel(lifecycleStatus),
          color: _lifecycleColor(lifecycleStatus),
        ),
        _Chip(
          prefix: 'İnceleme',
          label: reviewStatusLabel(reviewStatus),
          color: _reviewColor(reviewStatus),
        ),
        _Chip(
          prefix: 'Kayıt',
          label: recordingStatusLabel(recordingStatus),
          color: _recordingColor(recordingStatus),
        ),
      ],
    );
  }

  Color _lifecycleColor(ViolationLifecycleStatus status) {
    return switch (status) {
      ViolationLifecycleStatus.active => StrixBrand.primary,
      ViolationLifecycleStatus.preparing => StrixBrand.warning,
      ViolationLifecycleStatus.completed => StrixBrand.success,
      ViolationLifecycleStatus.error => StrixBrand.critical,
      ViolationLifecycleStatus.unknown => StrixBrand.textSecondary,
    };
  }

  Color _reviewColor(ViolationReviewStatus status) {
    return switch (status) {
      ViolationReviewStatus.unreviewed => StrixBrand.warning,
      ViolationReviewStatus.reviewed => StrixBrand.primary,
      ViolationReviewStatus.confirmed => StrixBrand.success,
      ViolationReviewStatus.falseAlarm => StrixBrand.textSecondary,
      ViolationReviewStatus.unknown => StrixBrand.textSecondary,
    };
  }

  Color _recordingColor(ViolationRecordingStatus status) {
    return switch (status) {
      ViolationRecordingStatus.ready => StrixBrand.success,
      ViolationRecordingStatus.error => StrixBrand.critical,
      ViolationRecordingStatus.processing ||
      ViolationRecordingStatus.recording ||
      ViolationRecordingStatus.requested =>
        StrixBrand.warning,
      ViolationRecordingStatus.unknown => StrixBrand.textSecondary,
    };
  }
}

class _Chip extends StatelessWidget {
  final String prefix;
  final String label;
  final Color color;

  const _Chip({
    required this.prefix,
    required this.label,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        '$prefix: $label',
        style: GoogleFonts.inter(
          fontSize: 11,
          fontWeight: FontWeight.w600,
          color: color,
        ),
      ),
    );
  }
}
