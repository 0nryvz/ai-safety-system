import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../auth/auth_controller.dart';
import '../../notifications/data/realtime_providers.dart';
import '../data/dashboard_api.dart';
import '../data/dashboard_repository.dart';
import 'dashboard_page.dart';

/// AppShell Dashboard sekmesi — [AuthenticatedApi] oturumunu kullanır.
class DashboardTabPage extends ConsumerWidget {
  final ValueChanged<String>? onRecentViolationTap;

  const DashboardTabPage({
    super.key,
    this.onRecentViolationTap,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final api = ref.watch(authenticatedApiProvider);

    return DashboardPage(
      repository: DashboardRepository(
        api: DashboardApi.fromAuthenticated(api),
      ),
      showAppBar: false,
      onRecentViolationTap: onRecentViolationTap,
      recoveryTick: ref.watch(restRecoveryTickProvider),
    );
  }
}
