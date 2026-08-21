import 'package:flutter/material.dart';

import '../../core/config/app_config.dart';
import '../../features/streaming/streaming_state.dart';

/// Operatörün akışın sağlığını görebilmesi için teknik sayaçlar.
/// Ham kare verisi veya token göstermez.
class StreamStatsOverlay extends StatelessWidget {
  final StreamingState state;

  const StreamStatsOverlay({
    super.key,
    required this.state,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: const Color(0xCC000000),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white24),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.end,
        mainAxisSize: MainAxisSize.min,
        children: [
          _fpsLine(
            'Kamera ${state.cameraFps} FPS',
            state.cameraFps,
            fontSize: 16,
            weight: FontWeight.w700,
          ),
          const SizedBox(height: 4),
          _fpsLine(
            'Gönderim ${state.sendFps} FPS',
            state.sendFps,
            fontSize: 13,
            weight: FontWeight.w600,
          ),
          const SizedBox(height: 6),
          _detail(
            'Gönderilen ${state.sentFrames}  '
            'Hata ${state.failedFrames}  '
            'Düşen ${state.droppedFrames}',
          ),
          if (state.reconnectAttempt > 0)
            _detail(
              'Deneme ${state.reconnectAttempt}/${state.maxReconnectAttempts}',
            ),
        ],
      ),
    );
  }

  Widget _fpsLine(
    String text,
    int value, {
    required double fontSize,
    required FontWeight weight,
  }) {
    return Text(
      text,
      style: TextStyle(
        color: value >= AppConfig.targetFps
            ? Colors.greenAccent
            : value >= AppConfig.minFps
                ? Colors.orangeAccent
                : Colors.redAccent,
        fontWeight: weight,
        fontSize: fontSize,
      ),
    );
  }

  Widget _detail(String text) => Padding(
        padding: const EdgeInsets.only(top: 2),
        child: Text(
          text,
          style: const TextStyle(color: Colors.white70, fontSize: 11),
        ),
      );
}
