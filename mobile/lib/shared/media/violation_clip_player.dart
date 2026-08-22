import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/theme/strix_brand.dart';
import '../../features/auth/auth_controller.dart';
import '../widgets/error_banner.dart';
import 'clip_playback_engine.dart';
import 'clip_player_controller.dart';
import 'clip_player_state.dart';
import 'violation_media_api.dart';

/// Seda'nın violation detail ekranında tek import ile kullanacağı clip player.
///
/// Source-of-truth: `GET /api/v1/violations/{id}/clip-url`.
/// [recordingStatus] yalnız UX hint (`ERROR` → kayıt hatası). READY kararı
/// hint ile süresiz engellenmez.
class ViolationClipPlayer extends ConsumerStatefulWidget {
  final String violationId;
  final String? recordingStatus;

  /// Test override. Production [authenticatedApiProvider] kullanır.
  final ViolationMediaApi? mediaApi;

  /// Test override. Production `video_player` engine'i kullanır.
  final ClipPlaybackEngineFactory? engineFactory;

  const ViolationClipPlayer({
    super.key,
    required this.violationId,
    this.recordingStatus,
    this.mediaApi,
    this.engineFactory,
  });

  @override
  ConsumerState<ViolationClipPlayer> createState() =>
      _ViolationClipPlayerState();
}

class _ViolationClipPlayerState extends ConsumerState<ViolationClipPlayer> {
  ClipPlayerController? _controller;

  @override
  void initState() {
    super.initState();
    _controller = _createController();
    _controller!.addListener(_onController);
    _controller!.start();
  }

  @override
  void didUpdateWidget(ViolationClipPlayer oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.violationId != widget.violationId ||
        oldWidget.recordingStatus != widget.recordingStatus) {
      _controller?.update(
        violationId: widget.violationId,
        recordingStatus: widget.recordingStatus,
      );
    }
  }

  @override
  void dispose() {
    _controller?.removeListener(_onController);
    _controller?.dispose();
    super.dispose();
  }

  ClipPlayerController _createController() {
    final api = widget.mediaApi ??
        ViolationMediaApi.fromAuthenticated(
          ref.read(authenticatedApiProvider),
        );

    return ClipPlayerController(
      api: api,
      violationId: widget.violationId,
      recordingStatus: widget.recordingStatus,
      engineFactory:
          widget.engineFactory ?? defaultClipPlaybackEngineFactory,
    );
  }

  void _onController() {
    if (mounted) {
      setState(() {});
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = _controller;
    if (controller == null) {
      return const SizedBox.shrink();
    }

    return DecoratedBox(
      decoration: BoxDecoration(
        color: StrixBrand.surfaceSubtle,
        borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
        border: Border.all(color: StrixBrand.border),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
        child: AspectRatio(
          aspectRatio: 16 / 9,
          child: _body(controller),
        ),
      ),
    );
  }

  Widget _body(ClipPlayerController controller) {
    final state = controller.state;

    return switch (state) {
      ClipPlayerLoading() => const Center(
          child: CircularProgressIndicator(),
        ),
      ClipPlayerReady() => controller.engine?.buildView() ??
          const SizedBox.shrink(),
      ClipPlayerNotReady() => _status(state.message, StrixBrand.warning),
      ClipPlayerForbidden() => _status(state.message, StrixBrand.critical),
      ClipPlayerNotFound() => _status(state.message, StrixBrand.textSecondary),
      ClipPlayerUnauthorized() => const SizedBox.shrink(),
      ClipPlayerNetworkError(:final message) => Padding(
          padding: const EdgeInsets.all(16),
          child: Center(
            child: ErrorBanner(
              message: message,
              actionLabel: 'Yeniden dene',
              onAction: controller.retry,
            ),
          ),
        ),
      ClipPlayerPlaybackError(:final message) => Padding(
          padding: const EdgeInsets.all(16),
          child: Center(
            child: ErrorBanner(
              message: message,
              actionLabel: 'Yeniden dene',
              onAction: controller.retry,
            ),
          ),
        ),
    };
  }

  Widget _status(String message, Color color) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Text(
          message,
          textAlign: TextAlign.center,
          style: TextStyle(
            color: color,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
    );
  }
}
