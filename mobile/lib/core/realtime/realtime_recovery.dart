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
      await target.recoverAfterReconnect();
    }
  }
}
