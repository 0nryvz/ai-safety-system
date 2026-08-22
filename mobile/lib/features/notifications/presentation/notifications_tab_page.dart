import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../auth/auth_controller.dart';
import '../../violations/data/violations_api.dart';
import '../../violations/data/violations_repository.dart';
import '../../violations/presentation/violation_detail_page.dart';
import '../data/realtime_providers.dart';
import 'notifications_page.dart';

/// AppShell Bildirimler sekmesi — mevcut realtime store'u tüketir.
///
/// AppShell bağlantısı ve login sonrası auto-connect Onur HANDOFF'udur.
class NotificationsTabPage extends ConsumerWidget {
  const NotificationsTabPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    ref.watch(realtimeLifecycleProvider);
    final store = ref.watch(notificationEventStoreProvider);
    final client = ref.watch(realtimeClientProvider);
    final api = ref.watch(authenticatedApiProvider);

    return NotificationsPage(
      store: store,
      connectionState: client.state,
      connectionStates: client.states,
      onOpenViolation: (violationId) {
        Navigator.of(context).push(
          MaterialPageRoute<void>(
            builder: (_) => ViolationDetailPage(
              violationId: violationId,
              repository: ViolationsRepository(
                api: ViolationsApi.fromAuthenticated(api),
              ),
            ),
          ),
        );
      },
    );
  }
}
