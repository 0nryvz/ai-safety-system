import 'package:camera_stream_app/features/streaming/stream_metrics.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('StreamMetrics', () {
    test('başlangıçta tüm sayaçlar sıfırdır', () {
      final metrics = StreamMetrics();

      expect(metrics.sentFrames, 0);
      expect(metrics.failedFrames, 0);
      expect(metrics.droppedFrames, 0);
      expect(metrics.lastSuccessAt, isNull);
      expect(metrics.sinceLastSuccess, isNull);
    });

    test('başarılı gönderim son başarı zamanını günceller', () {
      final metrics = StreamMetrics();

      metrics.recordSent();

      expect(metrics.sentFrames, 1);
      expect(metrics.lastSuccessAt, isNotNull);
      expect(metrics.sinceLastSuccess, isNotNull);
    });

    test('hata ve düşen kareler ayrı sayılır', () {
      final metrics = StreamMetrics();

      metrics.recordFailed();
      metrics.recordFailed();
      metrics.recordDropped();

      expect(metrics.failedFrames, 2);
      expect(metrics.droppedFrames, 1);
      expect(metrics.sentFrames, 0);

      // Hata son başarı zamanını ilerletmemeli.
      expect(metrics.lastSuccessAt, isNull);
    });

    test('reset yeni yayın için sayaçları temizler', () {
      final metrics = StreamMetrics();

      metrics.recordSent();
      metrics.recordFailed();
      metrics.recordDropped();

      metrics.reset();

      expect(metrics.sentFrames, 0);
      expect(metrics.failedFrames, 0);
      expect(metrics.droppedFrames, 0);
      expect(metrics.lastSuccessAt, isNull);
    });
  });
}
