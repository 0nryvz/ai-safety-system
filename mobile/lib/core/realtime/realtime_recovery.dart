/// Reconnect sonrası REST recovery tetikleyicisi.
///
/// Socket source-of-truth değildir; başarılı reconnect'te feature servisleri
/// kendi REST state'ini yeniler. Bu görevde yalnız hook tanımlanır.
abstract class RealtimeRecovery {
  Future<void> recoverAfterReconnect();
}

/// Feature servisleri bağlanana kadar kullanılan varsayılan.
class NoopRealtimeRecovery implements RealtimeRecovery {
  const NoopRealtimeRecovery();

  @override
  Future<void> recoverAfterReconnect() async {}
}

/// Mevcut feature refresh'ini tetikleyen dar adapter.
///
/// Repository/API yeniden yazılmaz; çağıran taraf mevcut `_load` / repository
/// mekanizmasını bağlar. Bir hedefin hatası diğerlerini durdurmaz.
class CallbackRealtimeRecovery implements RealtimeRecovery {
  final Future<void> Function() _onRecover;

  CallbackRealtimeRecovery(this._onRecover);

  @override
  Future<void> recoverAfterReconnect() async {
    try {
      await _onRecover();
    } catch (_) {
      // Recovery başarısız olsa da diğer hedefler ve mevcut UI state korunur.
    }
  }
}

/// Birden fazla feature recovery'sini tek hook altında toplar.
class CompositeRealtimeRecovery implements RealtimeRecovery {
  final List<RealtimeRecovery> _targets;

  CompositeRealtimeRecovery([List<RealtimeRecovery>? targets])
      : _targets = [...?targets];

  void register(RealtimeRecovery target) => _targets.add(target);

  void clear() => _targets.clear();

  @override
  Future<void> recoverAfterReconnect() async {
    for (final target in List<RealtimeRecovery>.unmodifiable(_targets)) {
      try {
        await target.recoverAfterReconnect();
      } catch (_) {
        // Tek feature hatası kalan REST refresh'leri iptal etmez.
      }
    }
  }
}
