import 'package:camera_stream_app/features/violations/data/violations_repository.dart';
import 'package:camera_stream_app/features/violations/models/violation_detail.dart';
import 'package:camera_stream_app/features/violations/models/violation_failure.dart';
import 'package:camera_stream_app/features/violations/models/violation_filter_option.dart';
import 'package:camera_stream_app/features/violations/models/violation_filters.dart';
import 'package:camera_stream_app/features/violations/models/violation_lifecycle_status.dart';
import 'package:camera_stream_app/features/violations/models/violation_list_item.dart';
import 'package:camera_stream_app/features/violations/models/violation_page.dart';
import 'package:camera_stream_app/features/violations/models/violation_recording_status.dart';
import 'package:camera_stream_app/features/violations/models/violation_review_status.dart';
import 'package:camera_stream_app/features/violations/models/violation_type.dart';
import 'package:camera_stream_app/features/violations/presentation/violations_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

class _FakeRepo implements ViolationsPort {
  _FakeRepo(this._pageLoader);

  final Future<ViolationPage> Function(ViolationFilters filters, int page)
      _pageLoader;

  @override
  Future<ViolationPage> loadPage({
    ViolationFilters filters = ViolationFilters.empty,
    int page = 0,
  }) =>
      _pageLoader(filters, page);

  @override
  Future<ViolationDetail> loadDetail(String id) async {
    throw UnimplementedError();
  }

  @override
  Future<void> submitReview({
    required String id,
    required ViolationReviewStatus reviewStatus,
    required int version,
  }) async {}

  @override
  Future<List<ViolationFilterOption>> loadCameras() async => const [];

  @override
  Future<List<ViolationFilterOption>> loadDepartments() async => const [];
}

ViolationListItem _item({
  required String nameType,
  required ViolationLifecycleStatus lifecycle,
  required ViolationReviewStatus review,
  required ViolationRecordingStatus recording,
}) {
  return ViolationListItem(
    id: 'v-1',
    type: ViolationType.fromJson(nameType),
    startedAt: DateTime.utc(2026, 8, 22, 10),
    lifecycleStatus: lifecycle,
    reviewStatus: review,
    recordingStatus: recording,
  );
}

void main() {
  Widget wrap(Widget child) => MaterialApp(home: child);

  testWidgets('liste ve üç status ayrı render', (tester) async {
    final repo = _FakeRepo(
      (_, _) async => ViolationPage(
        content: [
          _item(
            nameType: 'MISSING_GLOVES',
            lifecycle: ViolationLifecycleStatus.active,
            review: ViolationReviewStatus.unreviewed,
            recording: ViolationRecordingStatus.ready,
          ),
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      ),
    );

    await tester.pumpWidget(wrap(ViolationsPage(repository: repo)));
    await tester.pumpAndSettle();

    expect(find.text('Eldiven'), findsOneWidget);
    expect(find.textContaining('Yaşam: Aktif'), findsOneWidget);
    expect(find.textContaining('İnceleme: İncelenmedi'), findsOneWidget);
    expect(find.textContaining('Kayıt: Hazır'), findsOneWidget);
  });

  testWidgets('empty state', (tester) async {
    final repo = _FakeRepo(
      (_, _) async => const ViolationPage(
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      ),
    );

    await tester.pumpWidget(wrap(ViolationsPage(repository: repo)));
    await tester.pumpAndSettle();

    expect(find.text('İhlal bulunmuyor.'), findsOneWidget);
  });

  testWidgets('error state yeniden dene', (tester) async {
    final repo = _FakeRepo(
      (_, _) async => throw const ViolationFailure(
        'İhlal işlemi tamamlanamadı (500).',
        kind: ViolationFailureKind.server,
      ),
    );

    await tester.pumpWidget(wrap(ViolationsPage(repository: repo)));
    await tester.pumpAndSettle();

    expect(find.textContaining('tamamlanamadı'), findsOneWidget);
    expect(find.text('Yeniden dene'), findsOneWidget);
  });

  testWidgets('recoveryTick mevcut listeyi yeniler', (tester) async {
    var loads = 0;
    final repo = _FakeRepo((_, _) async {
      loads++;
      return ViolationPage(
        content: [
          _item(
            nameType: 'MISSING_GLOVES',
            lifecycle: ViolationLifecycleStatus.active,
            review: ViolationReviewStatus.unreviewed,
            recording: ViolationRecordingStatus.ready,
          ),
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      );
    });

    await tester.pumpWidget(wrap(ViolationsPage(repository: repo)));
    await tester.pumpAndSettle();
    expect(loads, 1);

    await tester.pumpWidget(
      wrap(ViolationsPage(repository: repo, recoveryTick: 1)),
    );
    await tester.pumpAndSettle();
    expect(loads, 2);
    expect(find.text('Eldiven'), findsOneWidget);
  });

  testWidgets('recovery başarısız olsa mevcut ihlal kalır', (tester) async {
    var loads = 0;
    final repo = _FakeRepo((_, _) async {
      loads++;
      if (loads > 1) {
        throw const ViolationFailure(
          'İhlal işlemi tamamlanamadı (500).',
          kind: ViolationFailureKind.server,
        );
      }
      return ViolationPage(
        content: [
          _item(
            nameType: 'MISSING_GLOVES',
            lifecycle: ViolationLifecycleStatus.active,
            review: ViolationReviewStatus.unreviewed,
            recording: ViolationRecordingStatus.ready,
          ),
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      );
    });

    await tester.pumpWidget(wrap(ViolationsPage(repository: repo)));
    await tester.pumpAndSettle();

    await tester.pumpWidget(
      wrap(ViolationsPage(repository: repo, recoveryTick: 1)),
    );
    await tester.pumpAndSettle();

    expect(find.text('Eldiven'), findsOneWidget);
    expect(find.textContaining('tamamlanamadı'), findsOneWidget);
  });
}
