import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../core/network/backend_client.dart';
import '../../core/theme/vigil_brand.dart';
import 'camera_option.dart';
import 'demo_cameras.dart';

/// VIGIL atama ekranı — fabrika kamerası seçimi zorunlu ilk adım.
class CameraAssignmentPage extends StatefulWidget {
  final BackendClient? client;
  final ValueChanged<CameraOption> onAssigned;

  const CameraAssignmentPage({
    super.key,
    required this.onAssigned,
    this.client,
  });

  @override
  State<CameraAssignmentPage> createState() => _CameraAssignmentPageState();
}

class _CameraAssignmentPageState extends State<CameraAssignmentPage> {
  late final BackendClient _client = widget.client ?? BackendClient();

  final TextEditingController _email = TextEditingController();
  final TextEditingController _password = TextEditingController();

  List<CameraOption>? _cameras;
  bool _isBusy = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    if (widget.client == null) {
      _client.close();
    }
    super.dispose();
  }

  Future<void> _loadCameras() async {
    setState(() {
      _isBusy = true;
      _error = null;
    });

    try {
      final token = await _client.login(
        email: _email.text.trim(),
        password: _password.text,
      );
      final cameras = await _client.fetchCameras(token);

      if (!mounted) {
        return;
      }

      setState(() {
        _cameras = cameras;
        _isBusy = false;
      });
    } on BackendAuthException catch (e) {
      if (!mounted) {
        return;
      }

      setState(() {
        _error = e.message;
        _isBusy = false;
      });
    }
  }

  void _useDemoCameras() {
    setState(() {
      _cameras = DemoCameras.catalog;
      _error = null;
      _isBusy = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 32),
          children: [
            Row(
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(14),
                  child: Image.asset(
                    'assets/brand/vigil_app_icon.png',
                    width: 52,
                    height: 52,
                    fit: BoxFit.cover,
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        VigilBrand.name,
                        style: GoogleFonts.spaceGrotesk(
                          fontSize: 28,
                          fontWeight: FontWeight.w800,
                          letterSpacing: 2.4,
                        ),
                      ),
                      Text(
                        VigilBrand.tagline.toUpperCase(),
                        style: GoogleFonts.spaceGrotesk(
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                          letterSpacing: 1.3,
                          color: VigilBrand.teal,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 18),
            Text(
              VigilBrand.pitch,
              style: const TextStyle(
                color: VigilBrand.steel,
                height: 1.45,
                fontSize: 14,
              ),
            ),
            const SizedBox(height: 24),
            if (_cameras == null) _loginCard() else _cameraListCard(),
            if (_error != null) ...[
              const SizedBox(height: 14),
              Text(
                _error!,
                style: const TextStyle(color: VigilBrand.danger),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _loginCard() {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: VigilBrand.panelElevated,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Colors.white.withValues(alpha: 0.06)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            '1 · Operatör girişi',
            style: GoogleFonts.spaceGrotesk(
              fontSize: 17,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 6),
          const Text(
            'Kamera listesi Backend 2 yetkisine göre gelir.',
            style: TextStyle(color: VigilBrand.steel, fontSize: 12.5),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _email,
            keyboardType: TextInputType.emailAddress,
            autocorrect: false,
            textInputAction: TextInputAction.next,
            decoration: const InputDecoration(labelText: 'E-posta'),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _password,
            obscureText: true,
            textInputAction: TextInputAction.done,
            onSubmitted: (_) => _isBusy ? null : _loadCameras(),
            decoration: const InputDecoration(labelText: 'Şifre'),
          ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: _isBusy ? null : _loadCameras,
            child: _isBusy
                ? const SizedBox(
                    height: 18,
                    width: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Kameraları getir'),
          ),
          const SizedBox(height: 10),
          OutlinedButton(
            onPressed: _isBusy ? null : _useDemoCameras,
            child: const Text('Demo kamera listesi'),
          ),
          const SizedBox(height: 10),
          const Text(
            'Backend kapalıysa demo listesini kullan. Gateway aktarımı yine '
            'çalışır; token MVP\'de sabittir.',
            style: TextStyle(color: VigilBrand.steel, fontSize: 12, height: 1.4),
          ),
        ],
      ),
    );
  }

  Widget _cameraListCard() {
    final cameras = _cameras!;
    final active = cameras.where((c) => c.isSelectable).toList();
    final inactive = cameras.where((c) => !c.isSelectable).toList();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          '2 · Simüle edilecek kamerayı seç',
          style: GoogleFonts.spaceGrotesk(
            fontSize: 17,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 6),
        const Text(
          'Bu adım olmadan yayın açılamaz — VIGIL bir webcam değildir.',
          style: TextStyle(color: VigilBrand.steel, fontSize: 12.5),
        ),
        const SizedBox(height: 14),
        if (active.isEmpty)
          const Padding(
            padding: EdgeInsets.all(20),
            child: Text(
              'Aktif kamera yok.',
              textAlign: TextAlign.center,
            ),
          )
        else
          ...active.map((c) => _tile(c, enabled: true)),
        if (inactive.isNotEmpty) ...[
          const SizedBox(height: 10),
          const Text('Pasif', style: TextStyle(color: VigilBrand.steel)),
          ...inactive.map((c) => _tile(c, enabled: false)),
        ],
        TextButton(
          onPressed: () => setState(() {
            _cameras = null;
            _error = null;
          }),
          child: const Text('Geri'),
        ),
      ],
    );
  }

  Widget _tile(CameraOption camera, {required bool enabled}) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: VigilBrand.panelElevated,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: enabled
              ? VigilBrand.teal.withValues(alpha: 0.25)
              : Colors.white.withValues(alpha: 0.05),
        ),
      ),
      child: ListTile(
        enabled: enabled,
        leading: Icon(
          enabled ? Icons.videocam : Icons.videocam_off,
          color: enabled ? VigilBrand.teal : VigilBrand.steel,
        ),
        title: Text(
          camera.name,
          style: const TextStyle(fontWeight: FontWeight.w700),
        ),
        subtitle: camera.subtitle.isEmpty ? null : Text(camera.subtitle),
        trailing: enabled
            ? const Icon(Icons.arrow_forward_ios, size: 14)
            : const Text('Pasif', style: TextStyle(color: VigilBrand.danger)),
        onTap: enabled ? () => widget.onAssigned(camera) : null,
      ),
    );
  }
}
