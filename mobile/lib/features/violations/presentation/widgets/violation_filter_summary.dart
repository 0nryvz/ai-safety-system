import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../../core/theme/strix_brand.dart';
import '../../models/violation_filters.dart';
import '../violation_labels.dart';

class ViolationFilterSummary extends StatelessWidget {
  final ViolationFilters filters;
  final VoidCallback onClear;

  const ViolationFilterSummary({
    super.key,
    required this.filters,
    required this.onClear,
  });

  @override
  Widget build(BuildContext context) {
    if (filters.isEmpty) {
      return const SizedBox.shrink();
    }

    final chips = <String>[
      if (filters.from != null) 'Başlangıç ${formatLocalDate(filters.from!)}',
      if (filters.to != null) 'Bitiş ${formatLocalDate(filters.to!)}',
      if (filters.type != null) violationTypeLabel(filters.type!),
      if (filters.lifecycleStatus != null)
        'Yaşam: ${lifecycleStatusLabel(filters.lifecycleStatus!)}',
      if (filters.reviewStatus != null)
        'İnceleme: ${reviewStatusLabel(filters.reviewStatus!)}',
      if (filters.recordingStatus != null)
        'Kayıt: ${recordingStatusLabel(filters.recordingStatus!)}',
    ];

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Wrap(
              spacing: 6,
              runSpacing: 6,
              children: [
                for (final chip in chips)
                  Chip(
                    label: Text(chip, style: GoogleFonts.inter(fontSize: 12)),
                    visualDensity: VisualDensity.compact,
                    backgroundColor: StrixBrand.surfaceSubtle,
                  ),
              ],
            ),
          ),
          TextButton(
            onPressed: onClear,
            child: const Text('Temizle'),
          ),
        ],
      ),
    );
  }
}
