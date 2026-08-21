import 'package:camera_stream_app/features/streaming/streaming_state.dart';
import 'package:camera_stream_app/shared/widgets/connection_badge.dart';
import 'package:camera_stream_app/shared/widgets/error_banner.dart';
import 'package:camera_stream_app/shared/widgets/stream_stats_overlay.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

Widget _wrap(Widget child) => MaterialApp(
      home: Scaffold(body: child),
    );

void main() {
  group('ConnectionBadge', () {
    testWidgets('her durum için etiketi gösterir', (tester) async {
      for (final state in StreamConnectionState.values) {
        await tester.pumpWidget(
          _wrap(ConnectionBadge(connection: state)),
        );

        expect(find.text(state.label), findsOneWidget);
      }
    });
  });

  group('ErrorBanner', () {
    testWidgets('mesajı gösterir', (tester) async {
      await tester.pumpWidget(
        _wrap(const ErrorBanner(message: 'Bir şeyler ters gitti')),
      );

      expect(find.text('Bir şeyler ters gitti'), findsOneWidget);
    });

    testWidgets('aksiyon verilmezse buton çıkmaz', (tester) async {
      await tester.pumpWidget(
        _wrap(const ErrorBanner(message: 'hata')),
      );

      expect(find.byType(TextButton), findsNothing);
    });

    testWidgets('aksiyon tetiklenir', (tester) async {
      var tapped = false;

      await tester.pumpWidget(
        _wrap(
          ErrorBanner(
            message: 'Kamera izni kapalı',
            actionLabel: 'Ayarları Aç',
            onAction: () => tapped = true,
          ),
        ),
      );

      await tester.tap(find.text('Ayarları Aç'));

      expect(tapped, isTrue);
    });
  });

  group('StreamStatsOverlay', () {
    testWidgets('FPS ve sayaçları gösterir', (tester) async {
      await tester.pumpWidget(
        _wrap(
          const StreamStatsOverlay(
            state: StreamingState(
              cameraFps: 30,
              sendFps: 15,
              sentFrames: 120,
              failedFrames: 2,
              droppedFrames: 7,
            ),
          ),
        ),
      );

      expect(find.text('Kamera 30 FPS'), findsOneWidget);
      expect(find.text('Gönderim 15 FPS'), findsOneWidget);
      expect(
        find.text('Gönderilen 120  Hata 2  Düşen 7'),
        findsOneWidget,
      );
    });

    testWidgets('yeniden deneme yokken sayaç gizlenir', (tester) async {
      await tester.pumpWidget(
        _wrap(const StreamStatsOverlay(state: StreamingState())),
      );

      expect(find.textContaining('Deneme'), findsNothing);
    });

    testWidgets('yeniden denerken sayaç görünür', (tester) async {
      await tester.pumpWidget(
        _wrap(
          const StreamStatsOverlay(
            state: StreamingState(reconnectAttempt: 2),
          ),
        ),
      );

      expect(find.text('Deneme 2/3'), findsOneWidget);
    });
  });
}
