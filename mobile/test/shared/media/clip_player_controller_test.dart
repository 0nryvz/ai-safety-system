import 'dart:async';

import 'package:camera_stream_app/core/error/api_failure.dart';
import 'package:camera_stream_app/shared/media/clip_playback_engine.dart';
import 'package:camera_stream_app/shared/media/clip_player_controller.dart';
import 'package:camera_stream_app/shared/media/clip_player_state.dart';
import 'package:camera_stream_app/shared/media/violation_media_api.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

class _FakeEngine implements ClipPlaybackEngine {
  final List<Uri> opened = [];
  int disposeCount = 0;
  bool failOpen = false;
  bool initialized = false;

  @override
  bool get isInitialized => initialized;

  @override
  Future<void> open(Uri url) async {
    if (failOpen) {
      throw Exception('open failed');
    }
    opened.add(url);
    initialized = true;
  }

  @override
  Future<void> play() async {}

  @override
  Future<void> pause() async {}

  @override
  Widget buildView() => const SizedBox(key: Key('fake-player'));

  @override
  Future<void> dispose() async {
    disposeCount++;
    initialized = false;
  }
}

class _FakeTimer implements Timer {
  _FakeTimer(this.delay, this.callback);

  final Duration delay;
  final void Function() callback;
  bool _active = true;

  @override
  void cancel() => _active = false;

  @override
  bool get isActive => _active;

  @override
  int get tick => 0;

  void fire() {
    if (!_active) {
      return;
    }
    _active = false;
    callback();
  }
}

class _Clock {
  DateTime current = DateTime.parse('2026-08-22T18:00:00Z');
  final timers = <_FakeTimer>[];

  Timer create(Duration delay, void Function() callback) {
    final timer = _FakeTimer(delay, callback);
    timers.add(timer);
    return timer;
  }

  int get activeCount => timers.where((t) => t.isActive).length;
}

void main() {
  late List<String> paths;
  late List<Object> script;
  late List<_FakeEngine> engines;
  late _Clock clock;

  Map<String, dynamic> clipJson({
    String url = 'https://cdn.example/clip-a',
    String expiresAt = '2026-08-22T18:05:00Z',
  }) {
    return {'url': url, 'expiresAt': expiresAt};
  }

  ViolationMediaApi api() {
    return ViolationMediaApi(
      getJson: (path) async {
        paths.add(path);
        expect(path, isNot(contains('objectKey')));
        expect(path, isNot(contains('coverImageKey')));
        expect(path, isNot(contains('playbackUrl')));
        if (script.isEmpty) {
          throw StateError('unexpected $path');
        }
        final next = script.removeAt(0);
        if (next is ApiFailure) {
          throw next;
        }
        return next as Map<String, dynamic>;
      },
    );
  }

  ClipPlayerController controller({String? recordingStatus}) {
    return ClipPlayerController(
      api: api(),
      violationId: 'v-1',
      recordingStatus: recordingStatus,
      engineFactory: () {
        final engine = _FakeEngine();
        engines.add(engine);
        return engine;
      },
      now: () => clock.current,
      timerFactory: clock.create,
    );
  }

  setUp(() {
    paths = [];
    script = [];
    engines = [];
    clock = _Clock();
  });

  test('200 prepares playback with the presigned url', () async {
    script.add(clipJson());
    final player = controller();
    addTearDown(player.dispose);

    await player.start();

    expect(player.state, isA<ClipPlayerReady>());
    expect(engines.single.opened, [Uri.parse('https://cdn.example/clip-a')]);
    expect(paths, ['/api/v1/violations/v-1/clip-url']);
  });

  test('409 maps to notReady, not a server error', () async {
    script.add(ApiFailure.fromStatusCode(409));
    final player = controller();
    addTearDown(player.dispose);

    await player.start();

    expect(player.state, isA<ClipPlayerNotReady>());
    expect((player.state as ClipPlayerNotReady).message, 'Klip hazırlanıyor');
    expect(engines, isEmpty);
  });

  test('PROCESSING hint still calls clip-url', () async {
    script.add(ApiFailure.fromStatusCode(409));
    final player = controller(recordingStatus: 'PROCESSING');
    addTearDown(player.dispose);

    await player.start();

    expect(paths, ['/api/v1/violations/v-1/clip-url']);
    expect(player.state, isA<ClipPlayerNotReady>());
  });

  test('REQUESTED and RECORDING hints do not block clip-url', () async {
    script
      ..add(clipJson())
      ..add(clipJson(url: 'https://cdn.example/clip-b'));

    final requested = controller(recordingStatus: 'REQUESTED');
    addTearDown(requested.dispose);
    await requested.start();
    expect(requested.state, isA<ClipPlayerReady>());

    final recording = controller(recordingStatus: 'RECORDING');
    addTearDown(recording.dispose);
    await recording.start();
    expect(recording.state, isA<ClipPlayerReady>());

    expect(paths, hasLength(2));
  });

  test('ERROR hint is a distinct playback error and skips the first fetch',
      () async {
    final player = controller(recordingStatus: 'ERROR');
    addTearDown(player.dispose);

    await player.start();

    expect(player.state, isA<ClipPlayerPlaybackError>());
    final error = player.state as ClipPlayerPlaybackError;
    expect(error.fromRecordingStatusHint, isTrue);
    expect(error.message, ClipPlayerPlaybackError.recordingFailedLabel);
    expect(paths, isEmpty);
  });

  test('retry after ERROR hint uses clip-url as source of truth', () async {
    script.add(clipJson());
    final player = controller(recordingStatus: 'ERROR');
    addTearDown(player.dispose);

    await player.start();
    await player.retry();

    expect(player.state, isA<ClipPlayerReady>());
    expect(paths, ['/api/v1/violations/v-1/clip-url']);
  });

  test('403 is forbidden and does not look like a session-clear path',
      () async {
    script.add(ApiFailure.fromStatusCode(403));
    final player = controller();
    addTearDown(player.dispose);

    await player.start();

    expect(player.state, isA<ClipPlayerForbidden>());
    expect(
      (player.state as ClipPlayerForbidden).message,
      'Bu klipe erişim yetkiniz yok.',
    );
  });

  test('404 maps to notFound via statusCode even when kind is unknown',
      () async {
    script.add(ApiFailure.fromStatusCode(404));
    expect(script.single, isA<ApiFailure>());
    expect((script.single as ApiFailure).kind, ApiFailureKind.unknown);

    final player = controller();
    addTearDown(player.dispose);
    await player.start();

    expect(player.state, isA<ClipPlayerNotFound>());
  });

  test('network failure can retry', () async {
    script
      ..add(ApiFailure.network)
      ..add(clipJson());
    final player = controller();
    addTearDown(player.dispose);

    await player.start();
    expect(player.state, isA<ClipPlayerNetworkError>());

    await player.retry();
    expect(player.state, isA<ClipPlayerReady>());
    expect(paths, hasLength(2));
  });

  test('expired url is refetched for the same violationId and playback restarts',
      () async {
    script
      ..add(clipJson(url: 'https://cdn.example/old', expiresAt: '2026-08-22T18:01:00Z'))
      ..add(clipJson(url: 'https://cdn.example/new', expiresAt: '2026-08-22T18:10:00Z'));

    final player = controller();
    addTearDown(player.dispose);
    await player.start();

    expect(engines, hasLength(1));
    expect(clock.timers, hasLength(1));
    expect(clock.timers.single.delay, const Duration(seconds: 45));

    clock.timers.single.fire();
    await pumpEventQueue();

    expect(paths, hasLength(2));
    expect(paths.toSet(), {'/api/v1/violations/v-1/clip-url'});
    expect(engines, hasLength(2));
    expect(engines.first.disposeCount, 1);
    expect(engines.last.opened, [Uri.parse('https://cdn.example/new')]);
    expect(player.state, isA<ClipPlayerReady>());
  });

  test('playback failure on a near-expired url refetches once', () async {
    script
      ..add(clipJson(expiresAt: '2026-08-22T17:59:00Z'))
      ..add(clipJson(url: 'https://cdn.example/fresh'));

    final localEngines = <_FakeEngine>[];
    final player = ClipPlayerController(
      api: api(),
      violationId: 'v-1',
      engineFactory: () {
        final engine = _FakeEngine();
        localEngines.add(engine);
        if (localEngines.length == 1) {
          engine.failOpen = true;
        }
        return engine;
      },
      now: () => clock.current,
      timerFactory: clock.create,
    );
    addTearDown(player.dispose);

    await player.start();

    expect(player.state, isA<ClipPlayerReady>());
    expect(paths, hasLength(2));
    expect(localEngines.first.disposeCount, 1);
    expect(localEngines.last.opened.single.toString(), contains('fresh'));
  });

  test('repeated start does not storm clip-url', () async {
    script.add(clipJson());
    final player = controller();
    addTearDown(player.dispose);

    final first = player.start();
    final second = player.start();
    await Future.wait([first, second]);
    await player.start();

    expect(paths, hasLength(1));
  });

  test('violationId change disposes the old engine and fetches the new id',
      () async {
    script
      ..add(clipJson(url: 'https://cdn.example/one'))
      ..add(clipJson(url: 'https://cdn.example/two'));
    final player = controller();
    addTearDown(player.dispose);

    await player.start();
    await player.update(violationId: 'v-2', recordingStatus: 'READY');

    expect(paths, [
      '/api/v1/violations/v-1/clip-url',
      '/api/v1/violations/v-2/clip-url',
    ]);
    expect(engines.first.disposeCount, 1);
    expect(engines.last.opened, [Uri.parse('https://cdn.example/two')]);
  });

  test('dispose cancels expiry timer and disposes the engine', () async {
    script.add(clipJson());
    final player = controller();

    await player.start();
    expect(clock.activeCount, 1);
    expect(engines.single.disposeCount, 0);

    player.dispose();

    expect(clock.activeCount, 0);
    expect(engines.single.disposeCount, 1);
    expect(player.hasPendingExpiryTimer, isFalse);
  });
}
