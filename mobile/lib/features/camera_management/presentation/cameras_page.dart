import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../core/theme/strix_brand.dart';
import '../../../shared/widgets/error_banner.dart';
import '../data/camera_management_repository.dart';
import '../models/camera_item.dart';
import '../models/camera_management_failure.dart';
import '../models/department_option.dart';
import 'camera_form_page.dart';
import 'widgets/camera_card.dart';

/// Operasyon kamera listesi — backend REST verisi.
class CamerasPage extends StatefulWidget {
  final CameraManagementPort repository;
  final bool canManageCameras;
  final ValueChanged<CameraItem>? onOpenBroadcast;

  /// Realtime reconnect recovery tick. Artınca mevcut [_load] tekrarlanır.
  final int recoveryTick;

  const CamerasPage({
    super.key,
    required this.repository,
    required this.canManageCameras,
    this.onOpenBroadcast,
    this.recoveryTick = 0,
  });

  @override
  State<CamerasPage> createState() => _CamerasPageState();
}

class _CamerasPageState extends State<CamerasPage> {
  List<CameraItem>? _cameras;
  CameraManagementFailure? _failure;
  bool _loading = true;
  String? _actionError;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didUpdateWidget(CamerasPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.recoveryTick != widget.recoveryTick) {
      _load();
    }
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _failure = null;
      _actionError = null;
    });

    try {
      final cameras = await widget.repository.loadCameras();
      if (!mounted) {
        return;
      }
      setState(() {
        _cameras = cameras;
        _loading = false;
      });
    } on CameraManagementFailure catch (failure) {
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
        _failure = const CameraManagementFailure(
          'Kamera listesi yüklenemedi.',
          kind: CameraManagementFailureKind.unknown,
        );
        _loading = false;
      });
    }
  }

  Future<void> _openCreate() async {
    List<DepartmentOption> departments;
    try {
      departments = await widget.repository.loadDepartments();
    } on CameraManagementFailure catch (failure) {
      if (!mounted) {
        return;
      }
      setState(() => _actionError = failure.message);
      return;
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() => _actionError = 'Departman listesi alınamadı.');
      return;
    }

    if (!mounted) {
      return;
    }

    final created = await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(
        builder: (_) => CameraFormPage.create(
          repository: widget.repository,
          departments: departments,
        ),
      ),
    );

    if (created == true) {
      await _load();
    }
  }

  Future<void> _openEdit(CameraItem camera) async {
    List<DepartmentOption> departments;
    try {
      departments = await widget.repository.loadDepartments();
    } on CameraManagementFailure catch (failure) {
      if (!mounted) {
        return;
      }
      setState(() => _actionError = failure.message);
      return;
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() => _actionError = 'Departman listesi alınamadı.');
      return;
    }

    if (!mounted) {
      return;
    }

    final saved = await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(
        builder: (_) => CameraFormPage.edit(
          repository: widget.repository,
          departments: departments,
          camera: camera,
        ),
      ),
    );

    if (saved == true) {
      await _load();
    }
  }

  Future<void> _toggleActive(CameraItem camera, bool active) async {
    setState(() => _actionError = null);

    try {
      await widget.repository.updateCamera(camera.id, active: active);
      await _load();
    } on CameraManagementFailure catch (failure) {
      if (!mounted) {
        return;
      }
      setState(() => _actionError = failure.message);
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() => _actionError = 'Kamera durumu güncellenemedi.');
    }
  }

  void _onCameraTap(CameraItem camera) {
    if (!camera.active) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '${camera.name} pasif — yayın açılamaz.',
            style: GoogleFonts.inter(),
          ),
        ),
      );
      return;
    }
    widget.onOpenBroadcast?.call(camera);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: StrixBrand.background,
      floatingActionButton: widget.canManageCameras
          ? FloatingActionButton.extended(
              onPressed: _loading ? null : _openCreate,
              icon: const Icon(Icons.add),
              label: const Text('Kamera ekle'),
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
    if (_loading && _cameras == null) {
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

    if (_failure != null && _cameras == null) {
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

    final cameras = _cameras ?? const <CameraItem>[];

    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 88),
      children: [
        Text(
          'Kameralar',
          style: GoogleFonts.inter(
            fontSize: 22,
            fontWeight: FontWeight.w700,
            color: StrixBrand.textPrimary,
          ),
        ),
        const SizedBox(height: 6),
        Text(
          widget.canManageCameras
              ? 'Fabrika kameralarını görüntüleyin ve yönetin.'
              : 'Fabrika kameralarını görüntüleyin.',
          style: GoogleFonts.inter(
            fontSize: 14,
            color: StrixBrand.textSecondary,
          ),
        ),
        if (_failure != null) ...[
          const SizedBox(height: 12),
          ErrorBanner(
            message: _failure!.isOffline
                ? 'Çevrimdışı — backend\'e ulaşılamıyor.'
                : _failure!.message,
            actionLabel: 'Yeniden dene',
            onAction: _load,
          ),
        ],
        if (_actionError != null) ...[
          const SizedBox(height: 12),
          ErrorBanner(message: _actionError!),
        ],
        const SizedBox(height: 16),
        if (cameras.isEmpty)
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: StrixBrand.surface,
              borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
              border: Border.all(color: StrixBrand.border),
            ),
            child: Text(
              'Kamera bulunmuyor.',
              textAlign: TextAlign.center,
              style: GoogleFonts.inter(color: StrixBrand.textSecondary),
            ),
          )
        else
          ...cameras.map(
            (camera) => CameraCard(
              camera: camera,
              canManage: widget.canManageCameras,
              onTap: widget.onOpenBroadcast != null
                  ? () => _onCameraTap(camera)
                  : null,
              onEdit: widget.canManageCameras
                  ? () => _openEdit(camera)
                  : null,
              onActiveChanged: widget.canManageCameras
                  ? (value) => _toggleActive(camera, value)
                  : null,
            ),
          ),
      ],
    );
  }
}
