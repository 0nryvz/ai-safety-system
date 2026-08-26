import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../../core/theme/strix_brand.dart';
import '../../data/notification_item.dart';
import '../notification_labels.dart';

class NotificationCard extends StatelessWidget {
  final NotificationItem item;
  final bool seen;
  final VoidCallback? onOpen;
  final VoidCallback? onDismiss;

  const NotificationCard({
    super.key,
    required this.item,
    required this.seen,
    this.onOpen,
    this.onDismiss,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Material(
        color: StrixBrand.surface,
        borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
        child: Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
            border: Border.all(
              color: seen ? StrixBrand.border : StrixBrand.primary,
            ),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (!seen)
                Container(
                  width: 4,
                  margin: const EdgeInsets.fromLTRB(8, 14, 0, 14),
                  decoration: BoxDecoration(
                    color: StrixBrand.primary,
                    borderRadius: BorderRadius.circular(4),
                  ),
                ),
              Expanded(
                child: InkWell(
                  onTap: onOpen,
                  borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(12, 14, 8, 12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                notificationTypeLabel(item.type),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: GoogleFonts.inter(
                                  fontWeight: FontWeight.w600,
                                  fontSize: 15,
                                ),
                              ),
                            ),
                            if (!seen)
                              Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 8,
                                  vertical: 3,
                                ),
                                decoration: BoxDecoration(
                                  color:
                                      StrixBrand.primary.withValues(alpha: 0.1),
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                child: Text(
                                  'Yeni',
                                  style: GoogleFonts.inter(
                                    fontSize: 11,
                                    fontWeight: FontWeight.w600,
                                    color: StrixBrand.primary,
                                  ),
                                ),
                              ),
                          ],
                        ),
                        Text(
                          notificationCameraLabel(item),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: GoogleFonts.inter(fontSize: 13),
                        ),
                        Text(
                          notificationDepartmentLabel(item),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: GoogleFonts.inter(
                            fontSize: 12,
                            color: StrixBrand.textSecondary,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          notificationStartedAtLabel(item.startedAt),
                          style: GoogleFonts.inter(
                            fontSize: 12,
                            color: StrixBrand.textSecondary,
                          ),
                        ),
                        Text(
                          'Güven: ${notificationConfidenceLabel(item.confidence)}',
                          style: GoogleFonts.inter(
                            fontSize: 12,
                            color: StrixBrand.textSecondary,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Wrap(
                          spacing: 6,
                          runSpacing: 6,
                          children: [
                            _Chip(
                              label: notificationLifecycleLabel(
                                item.lifecycleStatus,
                              ),
                            ),
                            _Chip(
                              label: notificationRecordingLabel(
                                item.recordingStatus,
                              ),
                            ),
                            _Chip(
                              label: notificationClipLabel(item.clipReady),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              ),
              if (onDismiss != null)
                IconButton(
                  tooltip: 'Gizle',
                  onPressed: onDismiss,
                  icon: const Icon(Icons.close, size: 18),
                  constraints: const BoxConstraints(
                    minWidth: 48,
                    minHeight: 48,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Chip extends StatelessWidget {
  final String label;

  const _Chip({required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: StrixBrand.surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: StrixBrand.border),
      ),
      child: Text(
        label,
        style: GoogleFonts.inter(
          fontSize: 11,
          fontWeight: FontWeight.w600,
          color: StrixBrand.textSecondary,
        ),
      ),
    );
  }
}
