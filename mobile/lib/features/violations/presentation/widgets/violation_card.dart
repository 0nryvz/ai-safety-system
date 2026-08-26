import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../../core/theme/strix_brand.dart';
import '../../models/violation_list_item.dart';
import '../violation_labels.dart';
import 'violation_status_chips.dart';

class ViolationCard extends StatelessWidget {
  final ViolationListItem item;
  final VoidCallback? onTap;

  const ViolationCard({
    super.key,
    required this.item,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Material(
        color: StrixBrand.surface,
        borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
          child: Container(
            padding: const EdgeInsets.fromLTRB(14, 14, 14, 12),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
              border: Border.all(color: StrixBrand.border),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        violationTypeLabel(item.type),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: GoogleFonts.inter(
                          fontWeight: FontWeight.w600,
                          fontSize: 15,
                          color: StrixBrand.textPrimary,
                        ),
                      ),
                    ),
                    const Icon(
                      Icons.chevron_right,
                      color: StrixBrand.textSecondary,
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  formatLocalDateTime(item.startedAt),
                  style: GoogleFonts.inter(
                    fontSize: 13,
                    color: StrixBrand.textSecondary,
                  ),
                ),
                const SizedBox(height: 10),
                ViolationStatusChips(
                  lifecycleStatus: item.lifecycleStatus,
                  reviewStatus: item.reviewStatus,
                  recordingStatus: item.recordingStatus,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
