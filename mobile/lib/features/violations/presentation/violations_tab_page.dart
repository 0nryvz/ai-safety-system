import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../auth/auth_controller.dart';
import '../../notifications/data/realtime_providers.dart';
import '../data/violations_api.dart';
import '../data/violations_repository.dart';
import 'violations_page.dart';

/// AppShell İhlaller sekmesi — [AuthenticatedApi] oturumunu kullanır.
///
/// AppShell bağlantısı Onur HANDOFF'udur; bu widget feature içinde hazırdır.
class ViolationsTabPage extends ConsumerWidget {
  const ViolationsTabPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final api = ref.watch(authenticatedApiProvider);

    return ViolationsPage(
      repository: ViolationsRepository(
        api: ViolationsApi.fromAuthenticated(api),
      ),
      recoveryTick: ref.watch(restRecoveryTickProvider),
    );
  }
}
