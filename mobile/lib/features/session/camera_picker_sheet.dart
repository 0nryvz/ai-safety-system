import 'package:flutter/material.dart';

import '../../core/network/backend_client.dart';
import 'camera_option.dart';

/// Backend 2'den yetkili kamera listesini getirip seçtirir.
///
/// Kullanıcı girişi gerekiyor çünkü `GET /api/v1/cameras` JWT ister ve liste
/// kullanıcının erişebildiği departmanlarla sınırlıdır. Token yalnızca bellekte
/// tutulur; ekranda veya logda gösterilmez.
class CameraPickerSheet extends StatefulWidget {
  final BackendClient client;

  const CameraPickerSheet({
    super.key,
    required this.client,
  });

  /// Seçilen kameranın UUID'sini döner, iptal edilirse null.
  static Future<String?> show(
    BuildContext context, {
    BackendClient? client,
  }) {
    return showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      builder: (_) => CameraPickerSheet(
        client: client ?? BackendClient(),
      ),
    );
  }

  @override
  State<CameraPickerSheet> createState() => _CameraPickerSheetState();
}

class _CameraPickerSheetState extends State<CameraPickerSheet> {
  final TextEditingController _email = TextEditingController();
  final TextEditingController _password = TextEditingController();

  List<CameraOption>? _cameras;
  bool _isBusy = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _loadCameras() async {
    setState(() {
      _isBusy = true;
      _error = null;
    });

    try {
      final token = await widget.client.login(
        email: _email.text.trim(),
        password: _password.text,
      );

      final cameras = await widget.client.fetchCameras(token);

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

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 20,
        bottom: MediaQuery.of(context).viewInsets.bottom + 20,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            'Kamera Seç',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 4),
          const Text(
            'Bu telefonun hangi fabrika kamerasını simüle ettiğini seçin.',
            style: TextStyle(fontSize: 12),
          ),
          const SizedBox(height: 16),
          if (_cameras == null) _loginForm() else _cameraList(),
          if (_error != null) ...[
            const SizedBox(height: 12),
            Text(
              _error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ],
        ],
      ),
    );
  }

  Widget _loginForm() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        TextField(
          controller: _email,
          keyboardType: TextInputType.emailAddress,
          autocorrect: false,
          decoration: const InputDecoration(
            labelText: 'E-posta',
            border: OutlineInputBorder(),
          ),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _password,
          obscureText: true,
          decoration: const InputDecoration(
            labelText: 'Şifre',
            border: OutlineInputBorder(),
          ),
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
              : const Text('Kameraları Getir'),
        ),
      ],
    );
  }

  Widget _cameraList() {
    final cameras = _cameras!;

    if (cameras.isEmpty) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 24),
        child: Text(
          'Bu hesabın erişebildiği kamera yok.',
          textAlign: TextAlign.center,
        ),
      );
    }

    return ConstrainedBox(
      constraints: const BoxConstraints(maxHeight: 360),
      child: ListView.separated(
        shrinkWrap: true,
        itemCount: cameras.length,
        separatorBuilder: (_, _) => const Divider(height: 1),
        itemBuilder: (context, index) {
          final camera = cameras[index];

          return ListTile(
            enabled: camera.isSelectable,
            title: Text(camera.name),
            subtitle: camera.subtitle.isEmpty ? null : Text(camera.subtitle),
            trailing: camera.isSelectable
                ? const Icon(Icons.chevron_right)
                : const Icon(Icons.block, size: 18),
            onTap: camera.isSelectable
                ? () => Navigator.of(context).pop(camera.id)
                : null,
          );
        },
      ),
    );
  }
}
