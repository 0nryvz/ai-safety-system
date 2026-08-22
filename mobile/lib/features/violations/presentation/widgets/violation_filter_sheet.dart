import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../../core/theme/strix_brand.dart';
import '../../models/iso_instant.dart';
import '../../models/violation_filter_option.dart';
import '../../models/violation_filters.dart';
import '../../models/violation_lifecycle_status.dart';
import '../../models/violation_recording_status.dart';
import '../../models/violation_review_status.dart';
import '../../models/violation_type.dart';
import '../violation_labels.dart';

Future<ViolationFilters?> showViolationFilterSheet({
  required BuildContext context,
  required ViolationFilters current,
  required List<ViolationFilterOption> cameras,
  required List<ViolationFilterOption> departments,
}) {
  return showModalBottomSheet<ViolationFilters>(
    context: context,
    isScrollControlled: true,
    backgroundColor: StrixBrand.surface,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
    ),
    builder: (context) {
      return _ViolationFilterSheet(
        initial: current,
        cameras: cameras,
        departments: departments,
      );
    },
  );
}

class _ViolationFilterSheet extends StatefulWidget {
  final ViolationFilters initial;
  final List<ViolationFilterOption> cameras;
  final List<ViolationFilterOption> departments;

  const _ViolationFilterSheet({
    required this.initial,
    required this.cameras,
    required this.departments,
  });

  @override
  State<_ViolationFilterSheet> createState() => _ViolationFilterSheetState();
}

class _ViolationFilterSheetState extends State<_ViolationFilterSheet> {
  late DateTime? _fromLocal;
  late DateTime? _toLocal;
  late ViolationType? _type;
  late String? _cameraId;
  late String? _departmentId;
  late ViolationLifecycleStatus? _lifecycle;
  late ViolationReviewStatus? _review;
  late ViolationRecordingStatus? _recording;

  @override
  void initState() {
    super.initState();
    _fromLocal = widget.initial.from?.toLocal();
    _toLocal = widget.initial.to?.toLocal();
    _type = widget.initial.type;
    _cameraId = widget.initial.cameraId;
    _departmentId = widget.initial.departmentId;
    _lifecycle = widget.initial.lifecycleStatus;
    _review = widget.initial.reviewStatus;
    _recording = widget.initial.recordingStatus;
  }

  Future<void> _pickFrom() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _fromLocal ?? DateTime.now(),
      firstDate: DateTime(2024),
      lastDate: DateTime(2030),
    );
    if (picked == null) {
      return;
    }
    setState(() => _fromLocal = picked);
  }

  Future<void> _pickTo() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _toLocal ?? _fromLocal ?? DateTime.now(),
      firstDate: DateTime(2024),
      lastDate: DateTime(2030),
    );
    if (picked == null) {
      return;
    }
    setState(() => _toLocal = picked);
  }

  void _apply() {
    Navigator.of(context).pop(
      ViolationFilters(
        from: _fromLocal == null ? null : startOfLocalDayUtc(_fromLocal!),
        to: _toLocal == null ? null : endOfLocalDayUtc(_toLocal!),
        type: _type,
        cameraId: _cameraId,
        departmentId: _departmentId,
        lifecycleStatus: _lifecycle,
        reviewStatus: _review,
        recordingStatus: _recording,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final bottom = MediaQuery.viewInsetsOf(context).bottom;
    return Padding(
      padding: EdgeInsets.fromLTRB(16, 12, 16, 16 + bottom),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              'Filtreler',
              style: GoogleFonts.inter(
                fontSize: 18,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 12),
            _DateRow(
              label: 'Başlangıç',
              value: _fromLocal == null ? null : formatLocalDate(_fromLocal!),
              onTap: _pickFrom,
              onClear: _fromLocal == null
                  ? null
                  : () => setState(() => _fromLocal = null),
            ),
            _DateRow(
              label: 'Bitiş',
              value: _toLocal == null ? null : formatLocalDate(_toLocal!),
              onTap: _pickTo,
              onClear:
                  _toLocal == null ? null : () => setState(() => _toLocal = null),
            ),
            _Dropdown<ViolationType>(
              label: 'İhlal tipi',
              value: _type,
              items: [
                for (final type in ViolationType.canonical)
                  DropdownMenuItem(
                    value: type,
                    child: Text(violationTypeLabel(type)),
                  ),
              ],
              onChanged: (value) => setState(() => _type = value),
            ),
            _Dropdown<String>(
              label: 'Kamera',
              value: _cameraId,
              items: [
                for (final camera in widget.cameras)
                  DropdownMenuItem(
                    value: camera.id,
                    child: Text(camera.name),
                  ),
              ],
              onChanged: (value) => setState(() => _cameraId = value),
            ),
            _Dropdown<String>(
              label: 'Departman',
              value: _departmentId,
              items: [
                for (final dept in widget.departments)
                  DropdownMenuItem(
                    value: dept.id,
                    child: Text(dept.name),
                  ),
              ],
              onChanged: (value) => setState(() => _departmentId = value),
            ),
            _Dropdown<ViolationLifecycleStatus>(
              label: 'Yaşam döngüsü',
              value: _lifecycle,
              items: [
                for (final status in ViolationLifecycleStatus.canonical)
                  DropdownMenuItem(
                    value: status,
                    child: Text(lifecycleStatusLabel(status)),
                  ),
              ],
              onChanged: (value) => setState(() => _lifecycle = value),
            ),
            _Dropdown<ViolationReviewStatus>(
              label: 'İnceleme',
              value: _review,
              items: [
                for (final status in ViolationReviewStatus.canonical)
                  DropdownMenuItem(
                    value: status,
                    child: Text(reviewStatusLabel(status)),
                  ),
              ],
              onChanged: (value) => setState(() => _review = value),
            ),
            _Dropdown<ViolationRecordingStatus>(
              label: 'Kayıt',
              value: _recording,
              items: [
                for (final status in ViolationRecordingStatus.canonical)
                  DropdownMenuItem(
                    value: status,
                    child: Text(recordingStatusLabel(status)),
                  ),
              ],
              onChanged: (value) => setState(() => _recording = value),
            ),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: _apply,
              child: const Text('Uygula'),
            ),
            TextButton(
              onPressed: () =>
                  Navigator.of(context).pop(ViolationFilters.empty),
              child: const Text('Filtreleri temizle'),
            ),
          ],
        ),
      ),
    );
  }
}

class _DateRow extends StatelessWidget {
  final String label;
  final String? value;
  final VoidCallback onTap;
  final VoidCallback? onClear;

  const _DateRow({
    required this.label,
    required this.value,
    required this.onTap,
    this.onClear,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(label, style: GoogleFonts.inter(fontSize: 13)),
      subtitle: Text(value ?? 'Seçilmedi'),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (onClear != null)
            IconButton(
              onPressed: onClear,
              icon: const Icon(Icons.close, size: 18),
            ),
          const Icon(Icons.calendar_today_outlined, size: 18),
        ],
      ),
      onTap: onTap,
    );
  }
}

class _Dropdown<T> extends StatelessWidget {
  final String label;
  final T? value;
  final List<DropdownMenuItem<T>> items;
  final ValueChanged<T?> onChanged;

  const _Dropdown({
    required this.label,
    required this.value,
    required this.items,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final effectiveValue =
        value != null && items.any((item) => item.value == value)
            ? value
            : null;

    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: DropdownButtonFormField<T>(
        initialValue: effectiveValue,
        decoration: InputDecoration(labelText: label),
        items: [
          DropdownMenuItem<T>(
            value: null,
            child: Text('Tümü', style: GoogleFonts.inter()),
          ),
          ...items,
        ],
        onChanged: onChanged,
      ),
    );
  }
}
