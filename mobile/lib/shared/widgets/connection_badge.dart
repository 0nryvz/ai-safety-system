import 'package:flutter/material.dart';

import '../../features/streaming/streaming_state.dart';

/// Bağlantı durumunu tek kaynaktan okuyan rozet.
class ConnectionBadge extends StatelessWidget {
  final StreamConnectionState connection;

  const ConnectionBadge({
    super.key,
    required this.connection,
  });

  Color get _color => switch (connection) {
        StreamConnectionState.connected => Colors.green,
        StreamConnectionState.weak => Colors.orange,
        StreamConnectionState.connecting ||
        StreamConnectionState.reconnecting =>
          Colors.amber,
        StreamConnectionState.offline => Colors.red,
        StreamConnectionState.stopped => Colors.white,
      };

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.black54,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.circle, size: 12, color: _color),
          const SizedBox(width: 8),
          Text(
            connection.label,
            style: const TextStyle(color: Colors.white),
          ),
        ],
      ),
    );
  }
}
