import 'package:camera_stream_app/features/dashboard/data/dashboard_repository.dart';
import 'package:camera_stream_app/features/dashboard/models/dashboard_distribution_item.dart';
import 'package:camera_stream_app/features/dashboard/models/dashboard_failure.dart';
import 'package:camera_stream_app/features/dashboard/models/dashboard_summary.dart';
import 'package:camera_stream_app/features/dashboard/models/dashboard_trend_point.dart';
import 'package:camera_stream_app/features/dashboard/models/recent_violation_item.dart';
import 'package:camera_stream_app/features/dashboard/presentation/dashboard_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

class _FakeRepository implements DashboardRepository {
  _FakeRepository(this._loader);

  final Future<DashboardSnapshot> Function() _loader;

  @override
  Future<DashboardSnapshot> load() => _loader();
}

void main() {
  Widget wrap(Widget child) => MaterialApp(home: child);

  testWidgets('summary success KPI gösterir', (tester) async {
    final repo = _FakeRepository(
      () async => DashboardSnapshot(
        summary: const DashboardSummary(
          todayViolationCount: 3,
          last7DaysViolationCount: 11,
          mostFrequentViolationType: 'MISSING_GLOVES',
          activeCameraCount: 5,
          offlineCameraCount: 0,
          activeViolationCount: 2,
        ),
        trend: [
          DashboardTrendPoint(date: DateTime.utc(2026, 8, 20), count: 2),
        ],
        distribution: const [
          DashboardDistributionItem(group: 'MISSING_GLOVES', count: 4),
        ],
        recentViolations: const [],
      ),
    );

    await tester.pumpWidget(
      wrap(
        DashboardPage(
          repository: repo,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('3'), findsOneWidget);
    expect(find.text('11'), findsOneWidget);
    expect(find.text('7 günlük trend'), findsOneWidget);
    expect(find.text('İhlal dağılımı'), findsOneWidget);
  });

  testWidgets('empty trend ve distribution mesajı', (tester) async {
    final repo = _FakeRepository(
      () async => const DashboardSnapshot(
        summary: DashboardSummary(
          todayViolationCount: 0,
          last7DaysViolationCount: 0,
          mostFrequentViolationType: null,
          activeCameraCount: 0,
          offlineCameraCount: 0,
          activeViolationCount: 0,
        ),
        trend: [],
        distribution: [],
        recentViolations: [],
      ),
    );

    await tester.pumpWidget(
      wrap(DashboardPage(repository: repo)),
    );
    await tester.pumpAndSettle();

    expect(find.text('Trend verisi yok'), findsOneWidget);
    expect(find.text('Dağılım verisi yok'), findsOneWidget);
    expect(find.text('Son ihlal bulunmuyor'), findsOneWidget);
  });

  testWidgets('error state yeniden dene gösterir', (tester) async {
    final repo = _FakeRepository(
      () async => throw const DashboardFailure(
        'Dashboard verisi alınamadı (500).',
        kind: DashboardFailureKind.server,
      ),
    );

    await tester.pumpWidget(
      wrap(DashboardPage(repository: repo)),
    );
    await tester.pumpAndSettle();

    expect(find.textContaining('alınamadı'), findsOneWidget);
    expect(find.text('Yeniden dene'), findsOneWidget);
  });

  testWidgets('offline render', (tester) async {
    final repo = _FakeRepository(
      () async => throw const DashboardFailure(
        'Backend\'e ulaşılamıyor. Ağı kontrol edin.',
        kind: DashboardFailureKind.offline,
      ),
    );

    await tester.pumpWidget(
      wrap(DashboardPage(repository: repo)),
    );
    await tester.pumpAndSettle();

    expect(find.textContaining('Çevrimdışı'), findsOneWidget);
  });

  testWidgets('recent card navigation', (tester) async {
    String? tappedId;
    final repo = _FakeRepository(
      () async => DashboardSnapshot(
        summary: const DashboardSummary(
          todayViolationCount: 1,
          last7DaysViolationCount: 1,
          mostFrequentViolationType: 'RESTRICTED_ZONE',
          activeCameraCount: 1,
          offlineCameraCount: 0,
          activeViolationCount: 1,
        ),
        trend: const [],
        distribution: const [],
        recentViolations: [
          RecentViolationItem(
            violationId: 'viol-1',
            detectedAt: DateTime.utc(2026, 8, 21, 12),
            startedAt: DateTime.utc(2026, 8, 21, 12),
            violationType: 'RESTRICTED_ZONE',
            cameraId: 'cam-1',
            departmentId: 'dep-1',
            departmentName: 'Montaj',
            cameraName: 'Kapı-A',
            cameraCode: 'C1',
            lifecycleStatus: 'ACTIVE',
            reviewStatus: 'UNREVIEWED',
            recordingStatus: 'READY',
            recordingReadyAt: null,
            confidence: 0.8,
            modelVersion: 'v1',
          ),
        ],
      ),
    );

    await tester.pumpWidget(
      wrap(
        DashboardPage(
          repository: repo,
          onRecentViolationTap: (id) => tappedId = id,
        ),
      ),
    );
    await tester.pumpAndSettle();

    final tile = find.textContaining('Kapı-A');
    expect(tile, findsOneWidget);
    await tester.ensureVisible(tile);
    await tester.pumpAndSettle();
    await tester.tap(tile);
    await tester.pump();
    expect(tappedId, 'viol-1');
  });

  testWidgets('recoveryTick mevcut _load yolunu tekrarlar', (tester) async {
    var loads = 0;
    final repo = _FakeRepository(() async {
      loads++;
      return const DashboardSnapshot(
        summary: DashboardSummary(
          todayViolationCount: 1,
          last7DaysViolationCount: 1,
          mostFrequentViolationType: null,
          activeCameraCount: 1,
          offlineCameraCount: 0,
          activeViolationCount: 0,
        ),
        trend: [],
        distribution: [],
        recentViolations: [],
      );
    });

    await tester.pumpWidget(wrap(DashboardPage(repository: repo)));
    await tester.pumpAndSettle();
    expect(loads, 1);

    await tester.pumpWidget(
      wrap(DashboardPage(repository: repo, recoveryTick: 1)),
    );
    await tester.pumpAndSettle();
    expect(loads, 2);
  });

  testWidgets('recovery başarısız olsa mevcut KPI kalır', (tester) async {
    var loads = 0;
    final repo = _FakeRepository(() async {
      loads++;
      if (loads > 1) {
        throw const DashboardFailure(
          'Dashboard verisi alınamadı (500).',
          kind: DashboardFailureKind.server,
        );
      }
      return const DashboardSnapshot(
        summary: DashboardSummary(
          todayViolationCount: 7,
          last7DaysViolationCount: 9,
          mostFrequentViolationType: null,
          activeCameraCount: 2,
          offlineCameraCount: 0,
          activeViolationCount: 1,
        ),
        trend: [],
        distribution: [],
        recentViolations: [],
      );
    });

    await tester.pumpWidget(wrap(DashboardPage(repository: repo)));
    await tester.pumpAndSettle();
    expect(find.text('7'), findsOneWidget);

    await tester.pumpWidget(
      wrap(DashboardPage(repository: repo, recoveryTick: 1)),
    );
    await tester.pumpAndSettle();

    expect(find.text('7'), findsOneWidget);
    expect(find.textContaining('alınamadı'), findsOneWidget);
  });

  testWidgets('küçük ekranda overflow yok', (tester) async {
    tester.view.physicalSize = const Size(320, 568);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final repo = _FakeRepository(
      () async => DashboardSnapshot(
        summary: const DashboardSummary(
          todayViolationCount: 3,
          last7DaysViolationCount: 11,
          mostFrequentViolationType: 'MISSING_WELDING_MASK',
          activeCameraCount: 5,
          offlineCameraCount: 1,
          activeViolationCount: 2,
        ),
        trend: [
          for (var i = 0; i < 7; i++)
            DashboardTrendPoint(
              date: DateTime.utc(2026, 8, 16 + i),
              count: i,
            ),
        ],
        distribution: const [
          DashboardDistributionItem(
            group: 'MISSING_WELDING_MASK',
            count: 4,
          ),
        ],
        recentViolations: [
          RecentViolationItem(
            violationId: 'viol-1',
            detectedAt: DateTime.utc(2026, 8, 21, 12),
            startedAt: DateTime.utc(2026, 8, 21, 12),
            violationType: 'RESTRICTED_ZONE',
            cameraId: 'cam-1',
            departmentId: 'dep-1',
            departmentName: 'Çok uzun departman adı',
            cameraName: 'Çok uzun kamera adı ile taşma kontrolü',
            cameraCode: 'C1',
            lifecycleStatus: 'ACTIVE',
            reviewStatus: 'UNREVIEWED',
            recordingStatus: 'READY',
            recordingReadyAt: null,
            confidence: 0.8,
            modelVersion: 'v1',
          ),
        ],
      ),
    );

    await tester.pumpWidget(
      wrap(
        MediaQuery(
          data: const MediaQueryData(
            size: Size(320, 568),
            textScaler: TextScaler.linear(1.2),
          ),
          child: DashboardPage(repository: repo),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('3'), findsOneWidget);
  });

  testWidgets('canonical olmayan type ham enum basmaz', (tester) async {
    final repo = _FakeRepository(
      () async => DashboardSnapshot(
        summary: const DashboardSummary(
          todayViolationCount: 1,
          last7DaysViolationCount: 1,
          mostFrequentViolationType: 'MISSING_WELDING_JACKET',
          activeCameraCount: 1,
          offlineCameraCount: 0,
          activeViolationCount: 1,
        ),
        trend: const [],
        distribution: const [
          DashboardDistributionItem(
            group: 'MISSING_WELDING_JACKET',
            count: 2,
          ),
        ],
        recentViolations: [
          RecentViolationItem(
            violationId: 'viol-jacket',
            detectedAt: DateTime.utc(2026, 8, 21, 12),
            startedAt: DateTime.utc(2026, 8, 21, 12),
            violationType: 'MISSING_WELDING_JACKET',
            cameraId: 'cam-1',
            departmentId: 'dep-1',
            departmentName: 'Kaynak',
            cameraName: 'Kamera A',
            cameraCode: 'C1',
            lifecycleStatus: 'ACTIVE',
            reviewStatus: 'UNREVIEWED',
            recordingStatus: 'READY',
            recordingReadyAt: null,
            confidence: 0.8,
            modelVersion: 'v1',
          ),
        ],
      ),
    );

    await tester.pumpWidget(wrap(DashboardPage(repository: repo)));
    await tester.pumpAndSettle();

    expect(find.text('MISSING_WELDING_JACKET'), findsNothing);
    expect(find.text('Bilinmiyor'), findsWidgets);
    expect(tester.takeException(), isNull);
  });
}
