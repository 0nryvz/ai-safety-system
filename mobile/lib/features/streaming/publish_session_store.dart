/// Yayın oturumu kimliği.
///
/// Sözleşme:
/// - Manuel Start (ilk veya Stop sonrası) → yeni UUID
/// - Automatic reconnect → aynı UUID korunur
/// - Manuel Stop → kimlik temizlenir
class PublishSessionStore {
  String? _sessionId;

  String? get sessionId => _sessionId;

  bool get hasSession => _sessionId != null;

  /// Kullanıcı Start verdiğinde çağrılır.
  String beginManualSession(String Function() createId) {
    _sessionId = createId();
    return _sessionId!;
  }

  /// Network/frame/heartbeat reconnect: mevcut kimliği döndürür.
  String? sessionForAutomaticReconnect() => _sessionId;

  /// Manuel Stop (ve yaşam döngüsü stop) sonrası.
  void clear() {
    _sessionId = null;
  }
}

/// Automatic reconnect timer'ının planlanıp planlanamayacağı.
class ReconnectEligibility {
  const ReconnectEligibility._();

  static bool canSchedule({
    required bool manualStop,
    required bool isAppInBackground,
    required bool alreadyReconnecting,
  }) {
    return !manualStop && !isAppInBackground && !alreadyReconnecting;
  }
}
