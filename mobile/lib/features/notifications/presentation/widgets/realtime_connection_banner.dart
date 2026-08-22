import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../../core/realtime/realtime_connection_state.dart';
import '../../../../core/theme/strix_brand.dart';

class RealtimeConnectionBanner extends StatelessWidget {
  final RealtimeConnectionState state;

  const RealtimeConnectionBanner({super.key, required this.state});

  @override
  Widget build(BuildContext context) {
    final spec = switch (state) {
      RealtimeConnectionState.connecting => (
          message: 'Bildirim hattına bağlanıyor…',
          color: StrixBrand.primary,
        ),
      RealtimeConnectionState.reconnecting => (
          message: 'Yeniden bağlanıyor…',
          color: StrixBrand.warning,
        ),
      RealtimeConnectionState.offline => (
          message: 'Çevrimdışı — mevcut bildirimler korunuyor.',
          color: StrixBrand.critical,
        ),
      RealtimeConnectionState.connected => null,
    };

    if (spec == null) {
      return const SizedBox.shrink();
    }

    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: spec.color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: spec.color.withValues(alpha: 0.35)),
      ),
      child: Row(
        children: [
          Icon(
            state == RealtimeConnectionState.offline
                ? Icons.cloud_off_outlined
                : Icons.sync,
            size: 18,
            color: spec.color,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              spec.message,
              style: GoogleFonts.inter(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: spec.color,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
