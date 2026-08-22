import 'package:camera_stream_app/features/violations/data/violations_repository.dart';
import 'package:camera_stream_app/features/violations/models/violation_detail.dart';
import 'package:camera_stream_app/features/violations/models/violation_failure.dart';
import 'package:camera_stream_app/features/violations/models/violation_filter_option.dart';
import 'package:camera_stream_app/features/violations/models/violation_filters.dart';
import 'package:camera_stream_app/features/violations/models/violation_lifecycle_status.dart';
import 'package:camera_stream_app/features/violations/models/violation_page.dart';
import 'package:camera_stream_app/features/violations/models/violation_recording_status.dart';
import 'package:camera_stream_app/features/violations/models/violation_review_status.dart';
import 'package:camera_stream_app/features/violations/models/violation_type.dart';
import 'package:camera_stream_app/features/violations/presentation/violation_detail_page.dart';
import 'package:camera_stream_app/shared/media/clip_playback_engine.dart';
import 'package:camera_stream_app/shared/media/violation_clip_player.dart';
import 'package:camera_stream_app/shared/media/violation_media_api.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

class _FakeEngine implements ClipPlaybackEngine {
  @override
  bool get isInitialized => true;

  @override
  Future<void> open(Uri url) async {}

  @override
  Future<void> play() async {}

  @override
  Future<void> pause() async {}

  @override
  Widget buildView() => const SizedBox(key: Key('fake-player'));

  @override
  Future<void> dispose() async {}
}

class _DetailRepo implements ViolationsPort {
  _DetailRepo({
    required this.details,
    this.reviewError,
  });

  final List<ViolationDetail> details;
  final ViolationFailure? reviewError;
  int detailCalls = 0;
  int? lastReviewVersion;
  ViolationReviewStatus? lastReviewStatus;

  @override
  Future<ViolationPage> loadPage({
    ViolationFilters filters = ViolationFilters.empty,
    int page = 0,
  }) async =>
      const ViolationPage(
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      );

  @override
  Future<ViolationDetail> loadDetail(String id) async {
    final index = detailCalls.clamp(0, details.length - 1);
    detailCalls++;
    return details[index];
  }

  @override
  Future<void> submitReview({
    required String id,
    required ViolationReviewStatus reviewStatus,
    required int version,
  }) async {
    lastReviewVersion = version;
    lastReviewStatus = reviewStatus;
    if (reviewError != null) {
      throw reviewError!;
    }
  }

  @override
  Future<List<ViolationFilterOption>> loadCameras() async => const [];

  @override
  Future<List<ViolationFilterOption>> loadDepartments() async => const [];
}

ViolationDetail _detail({
  required ViolationRecordingStatus recording,
  int version = 3,
  ViolationReviewStatus review = ViolationReviewStatus.unreviewed,
}) {
  return ViolationDetail(
    id: 'v-1',
    cameraName: 'Kaynak 1',
    cameraCode: 'CAM-01',
    departmentName: 'Üretim',
    type: ViolationType.missingGloves,
    confidence: 0.82,
    modelVersion: 'yolo-1',
    detectedAt: DateTime.utc(2026, 8, 22, 10),
    startedAt: DateTime.utc(2026, 8, 22, 10),
    lifecycleStatus: ViolationLifecycleStatus.completed,
    reviewStatus: review,
    recordingStatus: recording,
    clipReady: recording == ViolationRecordingStatus.ready,
    coverImageReady: recording == ViolationRecordingStatus.ready,
    version: version,
  );
}

Widget _clip(String id, String? status) {
  return ViolationClipPlayer(
    violationId: id,
    recordingStatus: status,
    mediaApi: ViolationMediaApi(
      getJson: (_) async => {
        'url': 'https://example.com/clip.mp4',
        'expiresAt': '2026-08-22T12:00:00Z',
      },
    ),
    engineFactory: () => _FakeEngine(),
  );
}

void main() {
  Widget wrap(WidgetTester tester, Widget child) {
    tester.view.physicalSize = const Size(800, 2000);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    return ProviderScope(
      child: MaterialApp(home: child),
    );
  }

  testWidgets('detail metadata ve üç status', (tester) async {
    final repo = _DetailRepo(details: [_detail(recording: ViolationRecordingStatus.ready)]);

    await tester.pumpWidget(
      wrap(
        tester,
        ViolationDetailPage(
          violationId: 'v-1',
          repository: repo,
          clipPlayerBuilder: _clip,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Eldiven'), findsWidgets);
    expect(find.textContaining('Kaynak 1'), findsOneWidget);
    expect(find.text('yolo-1'), findsOneWidget);
    expect(find.textContaining('Yaşam: Tamamlandı'), findsOneWidget);
    expect(find.textContaining('İnceleme: İncelenmedi'), findsOneWidget);
    expect(find.textContaining('Kayıt: Hazır'), findsOneWidget);
    expect(find.text('Sürüm 3'), findsOneWidget);
  });

  testWidgets('review success detail version gönderir', (tester) async {
    final repo = _DetailRepo(
      details: [
        _detail(recording: ViolationRecordingStatus.ready, version: 3),
        _detail(
          recording: ViolationRecordingStatus.ready,
          version: 4,
          review: ViolationReviewStatus.confirmed,
        ),
      ],
    );

    await tester.pumpWidget(
      wrap(
        tester,
        ViolationDetailPage(
          violationId: 'v-1',
          repository: repo,
          clipPlayerBuilder: _clip,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Onaylandı'));
    await tester.pumpAndSettle();

    expect(repo.lastReviewVersion, 3);
    expect(repo.lastReviewStatus, ViolationReviewStatus.confirmed);
    expect(find.text('İnceleme kaydedildi.'), findsOneWidget);
    expect(find.text('Sürüm 4'), findsOneWidget);
  });

  testWidgets('409 conflict refetch ve mesaj', (tester) async {
    final repo = _DetailRepo(
      details: [
        _detail(recording: ViolationRecordingStatus.ready, version: 3),
        _detail(
          recording: ViolationRecordingStatus.ready,
          version: 7,
          review: ViolationReviewStatus.reviewed,
        ),
      ],
      reviewError: const ViolationFailure(
        'Kayıt değişmiş. Güncel hali yükleniyor.',
        kind: ViolationFailureKind.conflict,
      ),
    );

    await tester.pumpWidget(
      wrap(
        tester,
        ViolationDetailPage(
          violationId: 'v-1',
          repository: repo,
          clipPlayerBuilder: _clip,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Onaylandı'));
    await tester.pumpAndSettle();

    expect(find.textContaining('güncellenmiş'), findsOneWidget);
    expect(find.text('Sürüm 7'), findsOneWidget);
    expect(find.textContaining('İnceleme: İncelendi'), findsOneWidget);
  });

  testWidgets('READY clip host widget', (tester) async {
    final repo = _DetailRepo(
      details: [_detail(recording: ViolationRecordingStatus.ready)],
    );

    await tester.pumpWidget(
      wrap(
        tester,
        ViolationDetailPage(
          violationId: 'v-1',
          repository: repo,
          clipPlayerBuilder: _clip,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(ViolationClipPlayer), findsOneWidget);
  });

  testWidgets('NOT_READY clip host widget', (tester) async {
    final repo = _DetailRepo(
      details: [_detail(recording: ViolationRecordingStatus.processing)],
    );

    await tester.pumpWidget(
      wrap(
        tester,
        ViolationDetailPage(
          violationId: 'v-1',
          repository: repo,
          clipPlayerBuilder: _clip,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(ViolationClipPlayer), findsOneWidget);
    expect(find.textContaining('Kayıt: İşleniyor'), findsOneWidget);
  });

  testWidgets('ERROR clip host widget', (tester) async {
    final repo = _DetailRepo(
      details: [_detail(recording: ViolationRecordingStatus.error)],
    );

    await tester.pumpWidget(
      wrap(
        tester,
        ViolationDetailPage(
          violationId: 'v-1',
          repository: repo,
          clipPlayerBuilder: _clip,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(ViolationClipPlayer), findsOneWidget);
    expect(find.textContaining('Kayıt: Kayıt hatası'), findsOneWidget);
    expect(find.text('Oluşturulamadı'), findsWidgets);
    expect(find.text('Hazırlanıyor'), findsNothing);
  });
}
