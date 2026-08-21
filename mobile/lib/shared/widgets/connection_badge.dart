import 'package:flutter/material.dart';

import '../../core/theme/strix_brand.dart';
import '../../features/streaming/streaming_state.dart';

/// Bağlantı durumunu tek kaynaktan okuyan rozet.
class ConnectionBadge extends StatelessWidget {
  final StreamConnectionState connection;

  const ConnectionBadge({
    super.key,
    required this.connection,
  });

  Color get _color => switch (connection) {
        StreamConnectionState.connected => StrixBrand.success,
        StreamConnectionState.weak => StrixBrand.warning,
        StreamConnectionState.connecting ||
        StreamConnectionState.reconnecting =>
          StrixBrand.warning,
        StreamConnectionState.offline => StrixBrand.critical,
        StreamConnectionState.stopped => StrixBrand.textSecondary,
      };

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: StrixBrand.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: StrixBrand.border),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.circle, size: 10, color: _color),
          const SizedBox(width: 8),
          Text(
            connection.label,
            style: const TextStyle(
              color: StrixBrand.textPrimary,
              fontSize: 13,
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }
}
