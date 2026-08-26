import 'dart:async';

/// Reconnect zamanlaması test edilebilir olsun diye enjekte edilir.
typedef RealtimeTimerFactory = Timer Function(
  Duration delay,
  void Function() callback,
);

Timer defaultRealtimeTimerFactory(Duration delay, void Function() callback) {
  return Timer(delay, callback);
}
