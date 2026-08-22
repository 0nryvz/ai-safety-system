import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../core/models/user_summary.dart';
import '../../../core/theme/strix_brand.dart';
import '../../../shared/widgets/error_banner.dart';
import '../data/users_repository.dart';
import '../models/user_department_option.dart';
import '../models/user_failure.dart';
import 'user_form_page.dart';
import 'widgets/user_card.dart';

class UsersPage extends StatefulWidget {
  final UsersPort repository;
  final bool canManageUsers;
  final String? currentUserId;

  const UsersPage({
    super.key,
    required this.repository,
    required this.canManageUsers,
    this.currentUserId,
  });

  @override
  State<UsersPage> createState() => _UsersPageState();
}

class _UsersPageState extends State<UsersPage> {
  List<UserSummary>? _users;
  UserFailure? _failure;
  bool _loading = true;
  String? _actionError;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _failure = null;
      _actionError = null;
    });

    try {
      final users = await widget.repository.loadUsers();
      if (!mounted) {
        return;
      }
      setState(() {
        _users = users;
        _loading = false;
      });
    } on UserFailure catch (failure) {
      if (!mounted) {
        return;
      }
      setState(() {
        _failure = failure;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() {
        _failure = const UserFailure(
          'Kullanıcı listesi yüklenemedi.',
          kind: UserFailureKind.unknown,
        );
        _loading = false;
      });
    }
  }

  Future<List<UserDepartmentOption>?> _departments() async {
    try {
      return await widget.repository.loadDepartments();
    } on UserFailure catch (failure) {
      if (!mounted) {
        return null;
      }
      setState(() => _actionError = failure.message);
      return null;
    } catch (_) {
      if (!mounted) {
        return null;
      }
      setState(() => _actionError = 'Departman listesi alınamadı.');
      return null;
    }
  }

  Future<void> _openCreate() async {
    final departments = await _departments();
    if (departments == null || !mounted) {
      return;
    }
    final created = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => UserFormPage.create(
          repository: widget.repository,
          departments: departments,
        ),
      ),
    );
    if (created == true) {
      await _load();
    }
  }

  Future<void> _openEdit(UserSummary user) async {
    final departments = await _departments();
    if (departments == null || !mounted) {
      return;
    }
    final saved = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => UserFormPage.edit(
          repository: widget.repository,
          departments: departments,
          user: user,
        ),
      ),
    );
    if (saved == true) {
      await _load();
    }
  }

  Future<void> _deactivate(UserSummary user) async {
    setState(() => _actionError = null);
    try {
      await widget.repository.deactivateUser(user.id);
      await _load();
    } on UserFailure catch (failure) {
      if (!mounted) {
        return;
      }
      setState(() => _actionError = failure.message);
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() => _actionError = 'Kullanıcı pasifleştirilemedi.');
    }
  }

  Future<void> _toggleActive(UserSummary user, bool active) async {
    setState(() => _actionError = null);
    try {
      if (!active) {
        await widget.repository.deactivateUser(user.id);
      } else {
        await widget.repository.updateUser(user.id, active: true);
      }
      await _load();
    } on UserFailure catch (failure) {
      if (!mounted) {
        return;
      }
      setState(() => _actionError = failure.message);
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() => _actionError = 'Kullanıcı durumu güncellenemedi.');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: StrixBrand.background,
      floatingActionButton: widget.canManageUsers
          ? FloatingActionButton.extended(
              onPressed: _loading ? null : _openCreate,
              icon: const Icon(Icons.person_add_outlined),
              label: const Text('Kullanıcı ekle'),
            )
          : null,
      body: RefreshIndicator(
        color: StrixBrand.primary,
        onRefresh: _load,
        child: _buildBody(),
      ),
    );
  }

  Widget _buildBody() {
    if (_loading && _users == null) {
      return ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        children: const [
          SizedBox(height: 160),
          Center(
            child: CircularProgressIndicator(color: StrixBrand.primary),
          ),
        ],
      );
    }

    if (_failure != null && _users == null) {
      return ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16),
        children: [
          const SizedBox(height: 48),
          ErrorBanner(
            message: _failure!.isOffline
                ? 'Çevrimdışı — backend\'e ulaşılamıyor.'
                : _failure!.message,
            actionLabel: 'Yeniden dene',
            onAction: _load,
          ),
        ],
      );
    }

    final users = _users ?? const <UserSummary>[];

    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 88),
      children: [
        Text(
          'Kullanıcılar',
          style: GoogleFonts.inter(
            fontSize: 22,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 6),
        Text(
          widget.canManageUsers
              ? 'Operatör hesaplarını görüntüleyin ve yönetin.'
              : 'Bu ekran yalnızca yöneticiler içindir.',
          style: GoogleFonts.inter(
            fontSize: 14,
            color: StrixBrand.textSecondary,
          ),
        ),
        if (_actionError != null) ...[
          const SizedBox(height: 12),
          ErrorBanner(message: _actionError!),
        ],
        const SizedBox(height: 16),
        if (users.isEmpty)
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: StrixBrand.surface,
              borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
              border: Border.all(color: StrixBrand.border),
            ),
            child: Text(
              'Kullanıcı bulunmuyor.',
              textAlign: TextAlign.center,
              style: GoogleFonts.inter(color: StrixBrand.textSecondary),
            ),
          )
        else
          ...users.map(
            (user) {
              final isSelf = widget.currentUserId == user.id;
              return UserCard(
                user: user,
                canManage: widget.canManageUsers,
                onEdit: widget.canManageUsers ? () => _openEdit(user) : null,
                onDeactivate: widget.canManageUsers && !isSelf && user.active
                    ? () => _deactivate(user)
                    : null,
                onActiveChanged: widget.canManageUsers && !isSelf
                    ? (value) => _toggleActive(user, value)
                    : null,
              );
            },
          ),
      ],
    );
  }
}
