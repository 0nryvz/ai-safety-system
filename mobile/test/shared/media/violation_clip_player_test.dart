import 'package:camera_stream_app/core/error/api_failure.dart';
import 'package:camera_stream_app/shared/media/clip_playback_engine.dart';
import 'package:camera_stream_app/shared/media/violation_clip_player.dart';
import 'package:camera_stream_app/shared/media/violation_media_api.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

class _FakeEngine implements ClipPlaybackEngine {
  int disposeCount = 0;
  final List<Uri> opened = [];

  @override
  bool get isInitialized => opened.isNotEmpty;

  @override
  Future<void> open(Uri url) async {
    opened.add(url);
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
  }
}

void main() {
  late List<String> paths;
  late List<Object> script;
  late List<_FakeEngine> engines;

  ViolationMediaApi api() {
    return ViolationMediaApi(
      getJson: (path) async {
        paths.add(path);
        final next = script.removeAt(0);
        if (next is ApiFailure) {
          throw next;
        }
        return next as Map<String, dynamic>;
      },
    );
  }

  Widget wrap(Widget child) {
    return ProviderScope(
      child: MaterialApp(home: Scaffold(body: child)),
    );
  }

  ViolationClipPlayer player({
    Key key = const ValueKey('clip-player'),
    String violationId = 'v-1',
    String? recordingStatus,
  }) {
    return ViolationClipPlayer(
      key: key,
      violationId: violationId,
      recordingStatus: recordingStatus,
      mediaApi: api(),
      engineFactory: () {
        final engine = _FakeEngine();
        engines.add(engine);
        return engine;
      },
    );
  }

  setUp(() {
    paths = [];
    script = [];
    engines = [];
  });

  testWidgets('READY shows the playback surface', (tester) async {
    script.add({
      'url': 'https://cdn.example/clip',
      'expiresAt': '2026-08-22T18:05:00Z',
    });

    await tester.pumpWidget(wrap(player()));
    await tester.pump();
    await tester.pump();

    expect(find.byKey(const Key('fake-player')), findsOneWidget);
    expect(find.text('Klip hazırlanıyor'), findsNothing);
  });

  testWidgets('409 shows preparing copy without crashing', (tester) async {
    script.add(ApiFailure.fromStatusCode(409));

    await tester.pumpWidget(wrap(player()));
    await tester.pump();
    await tester.pump();

    expect(find.text('Klip hazırlanıyor'), findsOneWidget);
    expect(find.byKey(const Key('fake-player')), findsNothing);
  });

  testWidgets('403 shows forbidden copy and no logout control', (tester) async {
    script.add(ApiFailure.fromStatusCode(403));

    await tester.pumpWidget(wrap(player()));
    await tester.pump();
    await tester.pump();

    expect(find.text('Bu klipe erişim yetkiniz yok.'), findsOneWidget);
    expect(find.text('Yeniden dene'), findsNothing);
  });

  testWidgets('network error offers retry', (tester) async {
    script
      ..add(ApiFailure.network)
      ..add({
        'url': 'https://cdn.example/clip',
        'expiresAt': '2026-08-22T18:05:00Z',
      });

    await tester.pumpWidget(wrap(player()));
    await tester.pump();
    await tester.pump();
    expect(find.text('Yeniden dene'), findsOneWidget);

    await tester.tap(find.text('Yeniden dene'));
    await tester.pump();
    await tester.pump();

    expect(find.byKey(const Key('fake-player')), findsOneWidget);
    expect(paths, hasLength(2));
  });

  testWidgets('rebuild does not refetch the same violationId', (tester) async {
    script.add({
      'url': 'https://cdn.example/clip',
      'expiresAt': '2026-08-22T18:05:00Z',
    });

    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          home: Scaffold(
            body: StatefulBuilder(
              builder: (context, setState) {
                return Column(
                  children: [
                    Expanded(child: player()),
                    TextButton(
                      onPressed: () => setState(() {}),
                      child: const Text('rebuild'),
                    ),
                  ],
                );
              },
            ),
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pump();
    expect(paths, hasLength(1));

    await tester.tap(find.text('rebuild'));
    await tester.pump();
    await tester.pump();

    expect(paths, hasLength(1));
    expect(find.byKey(const Key('fake-player')), findsOneWidget);
  });

  testWidgets('violationId change disposes the previous engine', (tester) async {
    script
      ..add({
        'url': 'https://cdn.example/one',
        'expiresAt': '2026-08-22T18:05:00Z',
      })
      ..add({
        'url': 'https://cdn.example/two',
        'expiresAt': '2026-08-22T18:05:00Z',
      });

    await tester.pumpWidget(wrap(player(violationId: 'v-1')));
    await tester.pump();
    await tester.pump();
    await tester.pumpWidget(wrap(player(violationId: 'v-2')));
    await tester.pump();
    await tester.pump();

    expect(engines.first.disposeCount, 1);
    expect(engines.last.opened.single.toString(), contains('two'));
  });

  testWidgets('unmount disposes VideoPlayer resources', (tester) async {
    script.add({
      'url': 'https://cdn.example/clip',
      'expiresAt': '2026-08-22T18:05:00Z',
    });

    await tester.pumpWidget(wrap(player()));
    await tester.pump();
    await tester.pump();
    await tester.pumpWidget(wrap(const SizedBox.shrink()));

    expect(engines.single.disposeCount, 1);
  });

  testWidgets('ERROR hint shows recording-failed copy', (tester) async {
    await tester.pumpWidget(wrap(player(recordingStatus: 'ERROR')));
    await tester.pump();
    await tester.pump();

    expect(find.text('Kayıt oluşturulamadı.'), findsOneWidget);
    expect(paths, isEmpty);
  });
}
