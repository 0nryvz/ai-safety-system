import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../core/network/backend_client.dart';
import '../../core/theme/strix_brand.dart';
import 'camera_option.dart';
import 'camera_selection_page.dart';
import 'demo_cameras.dart';
import 'offline_operator_auth.dart';

/// Uygulama soğuk başlangıç ekranı — kurumsal operatör girişi.
class OperatorLoginPage extends StatefulWidget {
  const OperatorLoginPage({super.key});

  @override
  State<OperatorLoginPage> createState() => _OperatorLoginPageState();
}

class _OperatorLoginPageState extends State<OperatorLoginPage> {
  final BackendClient _client = BackendClient();
  final TextEditingController _email = TextEditingController(
    text: OfflineOperatorAuth.email,
  );
  final TextEditingController _password = TextEditingController(
    text: OfflineOperatorAuth.password,
  );

  bool _isBusy = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    _client.close();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _isBusy = true;
      _error = null;
    });

    final email = _email.text.trim();
    final password = _password.text;

    try {
      final token = await _client.login(email: email, password: password);

      List<CameraOption> cameras;
      var offline = false;
      try {
        cameras = await _client.fetchCameras(token);
      } on BackendAuthException catch (e) {
        if (e.isUnreachable &&
            OfflineOperatorAuth.matches(email: email, password: password)) {
          cameras = OfflineOperatorAuth.cameras();
          offline = true;
        } else {
          rethrow;
        }
      }

      if (!mounted) {
        return;
      }

      await Navigator.of(context).pushReplacement(
        MaterialPageRoute<void>(
          builder: (_) => CameraSelectionPage(
            cameras: cameras,
            usedOfflineCatalog: offline,
          ),
        ),
      );
    } on BackendAuthException catch (e) {
      if (!mounted) {
        return;
      }

      if (e.isUnreachable &&
          OfflineOperatorAuth.matches(email: email, password: password)) {
        await Navigator.of(context).pushReplacement(
          MaterialPageRoute<void>(
            builder: (_) => CameraSelectionPage(
              cameras: OfflineOperatorAuth.cameras(),
              usedOfflineCatalog: true,
            ),
          ),
        );
        return;
      }

      setState(() {
        _error = e.isUnreachable
            ? 'Backend kapalı. Demo: ${OfflineOperatorAuth.email} / '
                '${OfflineOperatorAuth.password}'
            : e.message;
      });
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() {
        _error = 'Giriş sırasında beklenmeyen hata. Tekrar deneyin.';
      });
    } finally {
      if (mounted) {
        setState(() => _isBusy = false);
      }
    }
  }

  void _demoWithoutLogin() {
    Navigator.of(context).pushReplacement(
      MaterialPageRoute<void>(
        builder: (_) => CameraSelectionPage(
          cameras: DemoCameras.catalog,
          usedOfflineCatalog: true,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: StrixBrand.background,
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 440),
            child: ListView(
              padding: const EdgeInsets.fromLTRB(24, 48, 24, 32),
              children: [
                Center(
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(16),
                    child: Image.asset(
                      StrixBrand.logoAsset,
                      width: 64,
                      height: 64,
                      fit: BoxFit.cover,
                    ),
                  ),
                ),
                const SizedBox(height: 20),
                Text(
                  StrixBrand.name,
                  textAlign: TextAlign.center,
                  style: GoogleFonts.inter(
                    fontSize: 22,
                    fontWeight: FontWeight.w700,
                    color: StrixBrand.textPrimary,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  StrixBrand.tagline,
                  textAlign: TextAlign.center,
                  style: GoogleFonts.inter(
                    fontSize: 14,
                    fontWeight: FontWeight.w400,
                    color: StrixBrand.textSecondary,
                  ),
                ),
                const SizedBox(height: 32),
                Container(
                  padding: const EdgeInsets.all(24),
                  decoration: BoxDecoration(
                    color: StrixBrand.surface,
                    borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
                    border: Border.all(color: StrixBrand.border),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text(
                        'Operatör girişi',
                        style: GoogleFonts.inter(
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                          color: StrixBrand.textPrimary,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Kurumsal hesabınızla giriş yapın. Backend kapalıysa '
                        'demo hesabı ile devam edebilirsiniz.',
                        style: GoogleFonts.inter(
                          fontSize: 13,
                          height: 1.45,
                          color: StrixBrand.textSecondary,
                        ),
                      ),
                      const SizedBox(height: 20),
                      TextField(
                        controller: _email,
                        keyboardType: TextInputType.emailAddress,
                        autocorrect: false,
                        textInputAction: TextInputAction.next,
                        decoration: const InputDecoration(
                          labelText: 'E-posta',
                        ),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _password,
                        obscureText: true,
                        textInputAction: TextInputAction.done,
                        onSubmitted: (_) => _isBusy ? null : _submit(),
                        decoration: const InputDecoration(
                          labelText: 'Şifre',
                        ),
                      ),
                      const SizedBox(height: 20),
                      FilledButton(
                        onPressed: _isBusy ? null : _submit,
                        child: _isBusy
                            ? const SizedBox(
                                height: 18,
                                width: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              )
                            : const Text('Giriş yap'),
                      ),
                      const SizedBox(height: 12),
                      OutlinedButton(
                        onPressed: _isBusy ? null : _demoWithoutLogin,
                        child: const Text('Demo katalog ile devam'),
                      ),
                    ],
                  ),
                ),
                if (_error != null) ...[
                  const SizedBox(height: 16),
                  Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: StrixBrand.critical.withValues(alpha: 0.08),
                      borderRadius:
                          BorderRadius.circular(StrixBrand.radiusInput),
                      border: Border.all(
                        color: StrixBrand.critical.withValues(alpha: 0.35),
                      ),
                    ),
                    child: Text(
                      _error!,
                      style: GoogleFonts.inter(
                        fontSize: 13,
                        height: 1.4,
                        color: StrixBrand.critical,
                      ),
                    ),
                  ),
                ],
                const SizedBox(height: 24),
                Text(
                  'Demo: ${OfflineOperatorAuth.email} / '
                  '${OfflineOperatorAuth.password}',
                  textAlign: TextAlign.center,
                  style: GoogleFonts.inter(
                    fontSize: 12,
                    color: StrixBrand.textSecondary,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
