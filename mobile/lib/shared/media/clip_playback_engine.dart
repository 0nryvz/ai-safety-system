import 'package:flutter/widgets.dart';
import 'package:video_player/video_player.dart';

/// Presigned clip URL'ini oynatan sürücü. Testler fake ile değiştirir;
/// production [VideoPlayerClipEngine] kullanır.
abstract class ClipPlaybackEngine {
  bool get isInitialized;

  Future<void> open(Uri url);

  Future<void> play();

  Future<void> pause();

  Widget buildView();

  Future<void> dispose();
}

typedef ClipPlaybackEngineFactory = ClipPlaybackEngine Function();

ClipPlaybackEngine defaultClipPlaybackEngineFactory() =>
    VideoPlayerClipEngine();

class VideoPlayerClipEngine implements ClipPlaybackEngine {
  VideoPlayerController? _controller;

  @override
  bool get isInitialized => _controller?.value.isInitialized ?? false;

  @override
  Future<void> open(Uri url) async {
    await dispose();
    final controller = VideoPlayerController.networkUrl(url);
    _controller = controller;
    await controller.initialize();
    await controller.setLooping(true);
    await controller.play();
  }

  @override
  Future<void> play() async {
    await _controller?.play();
  }

  @override
  Future<void> pause() async {
    await _controller?.pause();
  }

  @override
  Widget buildView() {
    final controller = _controller;
    if (controller == null || !controller.value.isInitialized) {
      return const SizedBox.shrink();
    }

    final aspect = controller.value.aspectRatio;
    return AspectRatio(
      aspectRatio: aspect == 0 ? 16 / 9 : aspect,
      child: VideoPlayer(controller),
    );
  }

  @override
  Future<void> dispose() async {
    final controller = _controller;
    _controller = null;
    await controller?.dispose();
  }
}
