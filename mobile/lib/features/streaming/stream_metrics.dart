/// Aktarım sayaçları. Yalnızca teknik tanılama içindir; ham kare verisi ya da
/// token tutmaz.
class StreamMetrics {
  /// Süre/boyut örnekleri sınırlı bir pencerede tutulur; uzun yayında listeler
  /// büyümemeli.
  static const int _sampleWindow = 120;

  int _sentFrames = 0;
  int _failedFrames = 0;
  int _droppedFrames = 0;
  DateTime? _lastSuccessAt;

  final List<int> _encodeDurations = [];
  final List<int> _uploadDurations = [];
  final List<int> _jpegSizes = [];

  int _lastSourceWidth = 0;
  int _lastSourceHeight = 0;
  int _lastEncodedWidth = 0;
  int _lastEncodedHeight = 0;
  int _lastJpegQuality = 0;

  /// Gateway'in HTTP 202 ile kabul ettiği kare sayısı. Encode tamamlanması
  /// değil, yalnızca başarılı yükleme sayılır.
  int get sentFrames => _sentFrames;
  int get failedFrames => _failedFrames;

  /// Ağ yavaşken veya hedef hızın üstünde kare gelirken bilinçli olarak
  /// atılan kareler.
  int get droppedFrames => _droppedFrames;

  DateTime? get lastSuccessAt => _lastSuccessAt;

  /// Son başarılı gönderimin üzerinden geçen süre. Bağlantı zayıflığını tek bir
  /// timeout yerine bu ve ardışık hata sayısıyla birlikte değerlendirmek için.
  Duration? get sinceLastSuccess {
    final last = _lastSuccessAt;
    return last == null ? null : DateTime.now().difference(last);
  }

  int get lastSourceWidth => _lastSourceWidth;
  int get lastSourceHeight => _lastSourceHeight;
  int get lastEncodedWidth => _lastEncodedWidth;
  int get lastEncodedHeight => _lastEncodedHeight;
  int get lastJpegQuality => _lastJpegQuality;

  int get encodeAvgMs => _average(_encodeDurations);
  int get encodeP95Ms => _percentile95(_encodeDurations);
  int get uploadAvgMs => _average(_uploadDurations);
  int get uploadP95Ms => _percentile95(_uploadDurations);
  int get jpegAvgBytes => _average(_jpegSizes);

  void recordSent() {
    _sentFrames++;
    _lastSuccessAt = DateTime.now();
  }

  void recordFailed() => _failedFrames++;

  void recordDropped() => _droppedFrames++;

  /// Kodlanan karenin ölçüleri. Boyutlar native encoder'ın ürettiği gerçek
  /// çıktıyı yansıtır; hedef genişlik değil.
  void recordEncoded({
    required int sourceWidth,
    required int sourceHeight,
    required int encodedWidth,
    required int encodedHeight,
    required int jpegBytes,
    required int quality,
    required int durationMs,
  }) {
    _lastSourceWidth = sourceWidth;
    _lastSourceHeight = sourceHeight;
    _lastEncodedWidth = encodedWidth;
    _lastEncodedHeight = encodedHeight;
    _lastJpegQuality = quality;

    _addSample(_encodeDurations, durationMs);
    _addSample(_jpegSizes, jpegBytes);
  }

  void recordUploadDuration(int durationMs) {
    _addSample(_uploadDurations, durationMs);
  }

  void reset() {
    _sentFrames = 0;
    _failedFrames = 0;
    _droppedFrames = 0;
    _lastSuccessAt = null;

    _encodeDurations.clear();
    _uploadDurations.clear();
    _jpegSizes.clear();

    _lastSourceWidth = 0;
    _lastSourceHeight = 0;
    _lastEncodedWidth = 0;
    _lastEncodedHeight = 0;
    _lastJpegQuality = 0;
  }

  static void _addSample(List<int> samples, int value) {
    samples.add(value);

    if (samples.length > _sampleWindow) {
      samples.removeAt(0);
    }
  }

  static int _average(List<int> samples) {
    if (samples.isEmpty) {
      return 0;
    }

    var total = 0;
    for (final sample in samples) {
      total += sample;
    }

    return total ~/ samples.length;
  }

  static int _percentile95(List<int> samples) {
    if (samples.isEmpty) {
      return 0;
    }

    final sorted = List<int>.from(samples)..sort();
    final index = ((sorted.length - 1) * 0.95).round();

    return sorted[index];
  }

  @override
  String toString() => 'sent=$_sentFrames '
      'failed=$_failedFrames '
      'dropped=$_droppedFrames';
}
