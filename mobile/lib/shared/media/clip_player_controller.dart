import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../core/error/api_failure.dart';
import 'clip_playback_engine.dart';
import 'clip_player_state.dart';
import 'media_url.dart';
import 'violation_media_api.dart';

typedef MediaTimerFactory = Timer Function(
  Duration delay,
  void Function() callback,
);

Timer defaultMediaTimerFactory(Duration delay, void Function() callback) {
  return Timer(delay, callback);
}

/// Internal safety: URL'i `expiresAt`'ten biraz önce yenile.
/// Backend TTL'si hard-code edilmez; source-of-truth `expiresAt`'tir.
const Duration defaultExpirySkew = Duration(seconds: 15);

/// Clip-url source-of-truth. `recordingStatus` yalnız UX hint:
/// `ERROR` ilk yüklemede distinct playback error gösterebilir; diğer
/// status'ler isteği engellemez.
class ClipPlayerController extends ChangeNotifier {
  final ViolationMediaApi api;
  final ClipPlaybackEngineFactory engineFactory;
  final DateTime Function() now;
  final MediaTimerFactory timerFactory;
  final Duration expirySkew;

  String violationId;
  String? recordingStatus;

  ClipPlayerController({
    required this.api,
    required this.violationId,
    this.recordingStatus,
    this.engineFactory = defaultClipPlaybackEngineFactory,
    DateTime Function()? now,
    this.timerFactory = defaultMediaTimerFactory,
    this.expirySkew = defaultExpirySkew,
  }) : now = now ?? DateTime.now;

  ClipPlayerState _state = const ClipPlayerLoading();
  ClipPlaybackEngine? _engine;
  MediaUrl? _media;
  Timer? _expiryTimer;
  int _generation = 0;
  bool _inFlight = false;
  bool _disposed = false;
  bool _forceFetch = false;

  ClipPlayerState get state => _state;

  ClipPlaybackEngine? get engine => _engine;

  bool get hasPendingExpiryTimer => _expiryTimer?.isActive ?? false;

  int get inFlightCount => _inFlight ? 1 : 0;

  Future<void> start() => _load();

  Future<void> retry() {
    _forceFetch = true;
    return _load(reset: true);
  }

  Future<void> update({
    required String violationId,
    String? recordingStatus,
  }) async {
    if (_disposed) {
      return;
    }

    final idChanged = violationId != this.violationId;
    final statusChanged = recordingStatus != this.recordingStatus;
    if (!idChanged && !statusChanged) {
      return;
    }

    this.violationId = violationId;
    this.recordingStatus = recordingStatus;

    if (idChanged) {
      await _resetEngine();
      _forceFetch = false;
      return _load(reset: true);
    }

    if (this.recordingStatus == 'ERROR' && !_forceFetch) {
      _cancelExpiryTimer();
      _generation++;
      _inFlight = false;
      await _resetEngine();
      _setState(
        const ClipPlayerPlaybackError(
          message: ClipPlayerPlaybackError.recordingFailedLabel,
          fromRecordingStatusHint: true,
        ),
      );
      return;
    }

    if (_state is ClipPlayerReady) {
      return;
    }

    return _load(reset: true);
  }

  @override
  void dispose() {
    _disposed = true;
    _cancelExpiryTimer();
    _generation++;
    _inFlight = false;
    final engine = _engine;
    _engine = null;
    engine?.dispose();
    super.dispose();
  }

  Future<void> _load({bool reset = false}) async {
    if (_disposed) {
      return;
    }

    if (!_forceFetch && recordingStatus == 'ERROR') {
      await _resetEngine();
      _setState(
        const ClipPlayerPlaybackError(
          message: ClipPlayerPlaybackError.recordingFailedLabel,
          fromRecordingStatusHint: true,
        ),
      );
      return;
    }

    if (_inFlight && !reset) {
      return;
    }

    if (!reset && _state is ClipPlayerReady && !_currentUrlNeedsRefresh()) {
      return;
    }

    await _fetchAndOpen();
  }

  Future<void> _fetchAndOpen({bool fromExpiry = false}) async {
    if (_disposed) {
      return;
    }

    _cancelExpiryTimer();
    final generation = ++_generation;
    final id = violationId;
    _inFlight = true;

    if (!fromExpiry) {
      _setState(const ClipPlayerLoading());
    }

    try {
      final media = await api.fetchClipUrl(id);
      if (_isStale(generation, id)) {
        return;
      }

      final engine = engineFactory();
      try {
        await engine.open(Uri.parse(media.url));
      } catch (_) {
        await engine.dispose();
        if (_isStale(generation, id)) {
          return;
        }
        if (media.isExpiredOrNear(now(), expirySkew) && !fromExpiry) {
          await _fetchAndOpen(fromExpiry: true);
          return;
        }
        _media = null;
        _setState(const ClipPlayerPlaybackError());
        return;
      }

      if (_isStale(generation, id)) {
        await engine.dispose();
        return;
      }

      await _resetEngine();
      _engine = engine;
      _media = media;
      _forceFetch = false;
      _setState(const ClipPlayerReady());
      _scheduleExpiry(media, generation, id);
    } on ApiFailure catch (failure) {
      if (_isStale(generation, id)) {
        return;
      }
      _media = null;
      _setState(clipPlayerStateFromFailure(failure));
    } on FormatException {
      if (_isStale(generation, id)) {
        return;
      }
      _media = null;
      _setState(const ClipPlayerPlaybackError());
    } catch (_) {
      if (_isStale(generation, id)) {
        return;
      }
      _media = null;
      _setState(const ClipPlayerPlaybackError());
    } finally {
      if (generation == _generation) {
        _inFlight = false;
      }
    }
  }

  void _scheduleExpiry(MediaUrl media, int generation, String id) {
    _cancelExpiryTimer();
    final delay = media.expiresAt.subtract(expirySkew).difference(now().toUtc());
    if (delay <= Duration.zero) {
      return;
    }

    _expiryTimer = timerFactory(delay, () {
      _expiryTimer = null;
      if (_isStale(generation, id)) {
        return;
      }
      _fetchAndOpen(fromExpiry: true);
    });
  }

  bool _currentUrlNeedsRefresh() {
    final media = _media;
    if (media == null) {
      return true;
    }
    return media.isExpiredOrNear(now(), expirySkew);
  }

  Future<void> _resetEngine() async {
    _cancelExpiryTimer();
    final engine = _engine;
    _engine = null;
    _media = null;
    await engine?.dispose();
  }

  void _cancelExpiryTimer() {
    _expiryTimer?.cancel();
    _expiryTimer = null;
  }

  bool _isStale(int generation, String id) =>
      _disposed || generation != _generation || id != violationId;

  void _setState(ClipPlayerState next) {
    _state = next;
    if (!_disposed) {
      notifyListeners();
    }
  }
}
