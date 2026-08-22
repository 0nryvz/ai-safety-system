import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../auth/auth_controller.dart';
import '../data/users_api.dart';
import '../data/users_repository.dart';
import 'users_page.dart';

/// AppShell Kullanıcılar sekmesi — [AuthenticatedApi] oturumunu kullanır.
///
/// AppShell bağlantısı Onur HANDOFF'udur.
class UsersTabPage extends ConsumerWidget {
  const UsersTabPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final api = ref.watch(authenticatedApiProvider);
    final session = ref.watch(authSessionProvider);

    return UsersPage(
      repository: UsersRepository(
        api: UsersApi.fromAuthenticated(api),
      ),
      canManageUsers: session.canManageUsers,
      currentUserId: session.currentUser?.id,
    );
  }
}
