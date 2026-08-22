import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../core/theme/strix_brand.dart';
import '../../../shared/widgets/error_banner.dart';
import '../data/camera_management_repository.dart';
import '../models/camera_item.dart';
import '../models/camera_management_failure.dart';
import '../models/department_option.dart';

enum CameraFormMode { create, edit }

class CameraFormPage extends StatefulWidget {
  final CameraManagementPort repository;
  final List<DepartmentOption> departments;
  final CameraFormMode mode;
  final CameraItem? camera;

  const CameraFormPage._({
    required this.repository,
    required this.departments,
    required this.mode,
    this.camera,
  });

  factory CameraFormPage.create({
    required CameraManagementPort repository,
    required List<DepartmentOption> departments,
  }) {
    return CameraFormPage._(
      repository: repository,
      departments: departments,
      mode: CameraFormMode.create,
    );
  }

  factory CameraFormPage.edit({
    required CameraManagementPort repository,
    required List<DepartmentOption> departments,
    required CameraItem camera,
  }) {
    return CameraFormPage._(
      repository: repository,
      departments: departments,
      mode: CameraFormMode.edit,
      camera: camera,
    );
  }

  @override
  State<CameraFormPage> createState() => _CameraFormPageState();
}

class _CameraFormPageState extends State<CameraFormPage> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _nameController;
  late final TextEditingController _codeController;
  String? _departmentId;
  bool _active = true;
  bool _submitting = false;
  String? _submitError;

  bool get _isEdit => widget.mode == CameraFormMode.edit;

  /// Mevcut kamera departmanı liste dışında kalırsa dropdown patlamasın.
  List<DepartmentOption> get _departmentOptions {
    final options = List<DepartmentOption>.from(widget.departments);
    final currentId = widget.camera?.departmentId;
    if (currentId != null &&
        currentId.isNotEmpty &&
        !options.any((dept) => dept.id == currentId)) {
      options.insert(
        0,
        DepartmentOption(
          id: currentId,
          name: widget.camera?.departmentName ?? 'Departman',
        ),
      );
    }
    return options;
  }

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController(text: widget.camera?.name ?? '');
    _codeController = TextEditingController(text: widget.camera?.code ?? '');
    final options = _departmentOptions;
    _departmentId = widget.camera?.departmentId ??
        (options.length == 1 ? options.first.id : null);
    _active = widget.camera?.active ?? true;
  }

  @override
  void dispose() {
    _nameController.dispose();
    _codeController.dispose();
    super.dispose();
  }

  String? _requiredField(String? value, String label) {
    if (value == null || value.trim().isEmpty) {
      return '$label zorunludur.';
    }
    return null;
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    if (_departmentId == null || _departmentId!.isEmpty) {
      setState(() => _submitError = 'Departman seçin.');
      return;
    }

    setState(() {
      _submitting = true;
      _submitError = null;
    });

    try {
      if (_isEdit) {
        await widget.repository.updateCamera(
          widget.camera!.id,
          name: _nameController.text.trim(),
          code: _codeController.text.trim(),
          departmentId: _departmentId,
          active: _active,
        );
      } else {
        await widget.repository.createCamera(
          name: _nameController.text.trim(),
          code: _codeController.text.trim(),
          departmentId: _departmentId!,
        );
      }

      if (!mounted) {
        return;
      }
      Navigator.of(context).pop(true);
    } on CameraManagementFailure catch (failure) {
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
        _submitError = _isEdit
            ? 'Kamera güncellenemedi.'
            : 'Kamera oluşturulamadı.';
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
        title: Text(_isEdit ? 'Kamerayı düzenle' : 'Yeni kamera'),
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
              decoration: const InputDecoration(
                labelText: 'Kamera adı',
              ),
              textInputAction: TextInputAction.next,
              validator: (value) => _requiredField(value, 'Kamera adı'),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _codeController,
              enabled: !_submitting,
              decoration: const InputDecoration(
                labelText: 'Kamera kodu',
              ),
              textInputAction: TextInputAction.next,
              validator: (value) => _requiredField(value, 'Kamera kodu'),
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              initialValue: _departmentId,
              isExpanded: true,
              decoration: const InputDecoration(
                labelText: 'Departman',
              ),
              items: [
                for (final dept in _departmentOptions)
                  DropdownMenuItem(
                    value: dept.id,
                    child: Text(
                      dept.name,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
              ],
              onChanged: _submitting
                  ? null
                  : (value) => setState(() => _departmentId = value),
              validator: (value) {
                if (value == null || value.isEmpty) {
                  return 'Departman seçin.';
                }
                return null;
              },
            ),
            if (_isEdit) ...[
              const SizedBox(height: 8),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(
                  'Aktif',
                  style: GoogleFonts.inter(fontWeight: FontWeight.w500),
                ),
                subtitle: Text(
                  'Pasif kameralar yayın oturumu açamaz.',
                  style: GoogleFonts.inter(
                    fontSize: 13,
                    color: StrixBrand.textSecondary,
                  ),
                ),
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
