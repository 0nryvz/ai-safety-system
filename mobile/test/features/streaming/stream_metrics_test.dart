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

  group('StreamMetrics tanılama telemetrisi', () {
    void encode(StreamMetrics metrics, int durationMs, int bytes) {
      metrics.recordEncoded(
        sourceWidth: 720,
        sourceHeight: 480,
        encodedWidth: 720,
        encodedHeight: 480,
        jpegBytes: bytes,
        quality: 65,
        durationMs: durationMs,
      );
    }

    test('son kare boyutları raporlanır', () {
      final metrics = StreamMetrics();

      encode(metrics, 20, 48000);

      expect(metrics.lastSourceWidth, 720);
      expect(metrics.lastSourceHeight, 480);
      expect(metrics.lastEncodedWidth, 720);
      expect(metrics.lastEncodedHeight, 480);
      expect(metrics.lastJpegQuality, 65);
    });

    test('encode ve upload süreleri ayrı ortalanır', () {
      final metrics = StreamMetrics();

      encode(metrics, 10, 1000);
      encode(metrics, 30, 3000);

      metrics.recordUploadDuration(40);
      metrics.recordUploadDuration(60);

      expect(metrics.encodeAvgMs, 20);
      expect(metrics.uploadAvgMs, 50);
      expect(metrics.jpegAvgBytes, 2000);
    });

    test('p95 uç değeri yakalar', () {
      final metrics = StreamMetrics();

      for (var i = 1; i <= 100; i++) {
        metrics.recordUploadDuration(i);
      }

      expect(metrics.uploadP95Ms, 95);
      expect(metrics.uploadAvgMs, 50);
    });

    test('örnek penceresi sınırlıdır, uzun yayında büyümez', () {
      final metrics = StreamMetrics();

      for (var i = 0; i < 5000; i++) {
        metrics.recordUploadDuration(7);
      }

      expect(metrics.uploadAvgMs, 7);
      expect(metrics.uploadP95Ms, 7);
    });

    test('örnek yokken sıfır döner', () {
      final metrics = StreamMetrics();

      expect(metrics.encodeAvgMs, 0);
      expect(metrics.encodeP95Ms, 0);
      expect(metrics.uploadAvgMs, 0);
      expect(metrics.jpegAvgBytes, 0);
    });

    test('reset telemetriyi de temizler', () {
      final metrics = StreamMetrics();

      encode(metrics, 25, 50000);
      metrics.recordUploadDuration(80);

      metrics.reset();

      expect(metrics.encodeAvgMs, 0);
      expect(metrics.uploadAvgMs, 0);
      expect(metrics.jpegAvgBytes, 0);
      expect(metrics.lastEncodedWidth, 0);
      expect(metrics.lastJpegQuality, 0);
    });
  });
}
