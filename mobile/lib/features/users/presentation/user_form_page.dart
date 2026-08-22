import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../core/models/user_summary.dart';
import '../../../core/theme/strix_brand.dart';
import '../../../shared/widgets/error_banner.dart';
import '../data/users_repository.dart';
import '../models/user_department_option.dart';
import '../models/user_failure.dart';
import '../models/user_role.dart';

enum UserFormMode { create, edit }

class UserFormPage extends StatefulWidget {
  final UsersPort repository;
  final List<UserDepartmentOption> departments;
  final UserFormMode mode;
  final UserSummary? user;

  const UserFormPage._({
    required this.repository,
    required this.departments,
    required this.mode,
    this.user,
  });

  factory UserFormPage.create({
    required UsersPort repository,
    required List<UserDepartmentOption> departments,
  }) {
    return UserFormPage._(
      repository: repository,
      departments: departments,
      mode: UserFormMode.create,
    );
  }

  factory UserFormPage.edit({
    required UsersPort repository,
    required List<UserDepartmentOption> departments,
    required UserSummary user,
  }) {
    return UserFormPage._(
      repository: repository,
      departments: departments,
      mode: UserFormMode.edit,
      user: user,
    );
  }

  @override
  State<UserFormPage> createState() => _UserFormPageState();
}

class _UserFormPageState extends State<UserFormPage> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _emailController;
  late final TextEditingController _passwordController;
  late final TextEditingController _nameController;
  late Set<UserRole> _roles;
  late Set<String> _departmentIds;
  bool _active = true;
  bool _submitting = false;
  String? _submitError;

  bool get _isEdit => widget.mode == UserFormMode.edit;

  @override
  void initState() {
    super.initState();
    _emailController = TextEditingController(text: widget.user?.email ?? '');
    _passwordController = TextEditingController();
    _nameController = TextEditingController(text: widget.user?.fullName ?? '');
    _roles = {
      for (final raw in widget.user?.roles ?? const <String>{})
        ?UserRole.fromWire(raw),
    };
    _departmentIds = {...?widget.user?.departmentIds};
    _active = widget.user?.active ?? true;
  }

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    _nameController.dispose();
    super.dispose();
  }

  String? _required(String? value, String label) {
    if (value == null || value.trim().isEmpty) {
      return '$label zorunludur.';
    }
    return null;
  }

  String? _email(String? value) {
    final required = _required(value, 'E-posta');
    if (required != null) {
      return required;
    }
    final email = value!.trim();
    if (!email.contains('@') || !email.contains('.')) {
      return 'Geçerli bir e-posta girin.';
    }
    return null;
  }

  String? _password(String? value) {
    if (_isEdit) {
      return null;
    }
    final required = _required(value, 'Şifre');
    if (required != null) {
      return required;
    }
    if (value!.trim().length < 6) {
      return 'Şifre en az 6 karakter olmalıdır.';
    }
    return null;
  }

  Future<void> _submit() async {
    if (_submitting) {
      return;
    }
    if (!_formKey.currentState!.validate()) {
      return;
    }
    if (_roles.isEmpty) {
      setState(() => _submitError = 'En az bir rol seçin.');
      return;
    }

    setState(() {
      _submitting = true;
      _submitError = null;
    });

    final roleNames = _roles.map((role) => role.wireValue).toList();
    final departmentIds = _departmentIds.toList();

    try {
      if (_isEdit) {
        await widget.repository.updateUser(
          widget.user!.id,
          fullName: _nameController.text.trim(),
          roleNames: roleNames,
          departmentIds: departmentIds,
          active: _active,
        );
      } else {
        await widget.repository.createUser(
          email: _emailController.text.trim(),
          password: _passwordController.text.trim(),
          fullName: _nameController.text.trim(),
          roleNames: roleNames,
          departmentIds: departmentIds,
        );
      }
      if (!mounted) {
        return;
      }
      Navigator.of(context).pop(true);
    } on UserFailure catch (failure) {
      if (!mounted) {
        return;
      }
      setState(() {
        _submitError = failure.message;
        _submitting = false;
      });
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() {
        _submitError =
            _isEdit ? 'Kullanıcı güncellenemedi.' : 'Kullanıcı oluşturulamadı.';
        _submitting = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: StrixBrand.background,
      resizeToAvoidBottomInset: true,
      appBar: AppBar(
        title: Text(_isEdit ? 'Kullanıcıyı düzenle' : 'Yeni kullanıcı'),
      ),
      body: SafeArea(
        child: Form(
          key: _formKey,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
            keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
            children: [
            if (_submitError != null) ...[
              ErrorBanner(message: _submitError!),
              const SizedBox(height: 12),
            ],
            TextFormField(
              controller: _nameController,
              enabled: !_submitting,
              decoration: const InputDecoration(labelText: 'Ad soyad'),
              textInputAction: TextInputAction.next,
              validator: (value) => _required(value, 'Ad soyad'),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _emailController,
              enabled: !_isEdit && !_submitting,
              decoration: const InputDecoration(labelText: 'E-posta'),
              keyboardType: TextInputType.emailAddress,
              textInputAction: TextInputAction.next,
              validator: _isEdit ? null : _email,
            ),
            if (!_isEdit) ...[
              const SizedBox(height: 12),
              TextFormField(
                controller: _passwordController,
                enabled: !_submitting,
                decoration: const InputDecoration(labelText: 'Şifre'),
                obscureText: true,
                validator: _password,
              ),
            ],
            const SizedBox(height: 16),
            Text(
              'Roller',
              style: GoogleFonts.inter(fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: [
                for (final role in UserRole.canonical)
                  FilterChip(
                    label: Text(role.label),
                    selected: _roles.contains(role),
                    onSelected: _submitting
                        ? null
                        : (selected) {
                            setState(() {
                              if (selected) {
                                _roles.add(role);
                              } else {
                                _roles.remove(role);
                              }
                            });
                          },
                  ),
              ],
            ),
            const SizedBox(height: 16),
            Text(
              'Departmanlar',
              style: GoogleFonts.inter(fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 8),
            if (widget.departments.isEmpty)
              Text(
                'Seçilebilir departman yok.',
                style: GoogleFonts.inter(color: StrixBrand.textSecondary),
              )
            else
              Wrap(
                spacing: 8,
                children: [
                  for (final dept in widget.departments)
                    FilterChip(
                      label: Text(dept.name),
                      selected: _departmentIds.contains(dept.id),
                      onSelected: _submitting
                          ? null
                          : (selected) {
                              setState(() {
                                if (selected) {
                                  _departmentIds.add(dept.id);
                                } else {
                                  _departmentIds.remove(dept.id);
                                }
                              });
                            },
                    ),
                ],
              ),
            if (_isEdit) ...[
              const SizedBox(height: 8),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Aktif'),
                value: _active,
                onChanged: _submitting
                    ? null
                    : (value) => setState(() => _active = value),
              ),
            ],
            const SizedBox(height: 24),
            FilledButton(
              onPressed: _submitting ? null : _submit,
              child: _submitting
                  ? const SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Text(_isEdit ? 'Kaydet' : 'Oluştur'),
            ),
          ],
          ),
        ),
      ),
    );
  }
}
