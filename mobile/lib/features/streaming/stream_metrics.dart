/// Aktarım sayaçları. Yalnızca teknik tanılama içindir; ham kare verisi ya da
/// token tutmaz.
class StreamMetrics {
  int _sentFrames = 0;
  int _failedFrames = 0;
  int _droppedFrames = 0;
  DateTime? _lastSuccessAt;

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

  void recordSent() {
    _sentFrames++;
    _lastSuccessAt = DateTime.now();
  }

  void recordFailed() => _failedFrames++;

  void recordDropped() => _droppedFrames++;

  void reset() {
    _sentFrames = 0;
    _failedFrames = 0;
    _droppedFrames = 0;
    _lastSuccessAt = null;
  }

  @override
  String toString() => 'sent=$_sentFrames '
      'failed=$_failedFrames '
      'dropped=$_droppedFrames';
}
