import 'package:camera_stream_app/features/camera/camera_permission_service.dart';
import 'package:camera_stream_app/features/streaming/streaming_state.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('StreamConnectionState', () {
    test('sözleşmedeki altı durum da tanımlı', () {
      expect(StreamConnectionState.values, hasLength(6));

      expect(
        StreamConnectionState.values.map((s) => s.name).toSet(),
        {
          'connecting',
          'connected',
          'weak',
          'reconnecting',
          'offline',
          'stopped',
        },
      );
    });

    test('her durumun kullanıcıya gösterilecek etiketi var', () {
      for (final state in StreamConnectionState.values) {
        expect(state.label, isNotEmpty);
      }
    });
  });

  group('kamera değiştirme kuralı', () {
    const ready = StreamingState(
      isCameraReady: true,
      availableCameraCount: 2,
    );

    test('hazır ve boştayken değiştirilebilir', () {
      expect(ready.canSwitchCamera, isTrue);
    });

    test('yayın sırasında değiştirilemez', () {
      expect(ready.copyWith(isStreaming: true).canSwitchCamera, isFalse);
    });

    test('geçiş anında değiştirilemez', () {
      expect(ready.copyWith(isBusy: true).canSwitchCamera, isFalse);
    });

    test('tek kamera varsa değiştirilemez', () {
      expect(
        ready.copyWith(availableCameraCount: 1).canSwitchCamera,
        isFalse,
      );
    });

    test('kamera hazır değilse değiştirilemez', () {
      expect(ready.copyWith(isCameraReady: false).canSwitchCamera, isFalse);
    });
  });

  group('copyWith', () {
    test('verilmeyen alanlar korunur', () {
      const original = StreamingState(
        cameraId: 'cam',
        sessionId: 'session',
        sentFrames: 5,
      );

      final updated = original.copyWith(isStreaming: true);

      expect(updated.cameraId, 'cam');
      expect(updated.sessionId, 'session');
      expect(updated.sentFrames, 5);
      expect(updated.isStreaming, isTrue);
    });

    test('clearError hata mesajını siler', () {
      const original = StreamingState(errorMessage: 'bir hata');

      expect(original.copyWith(clearError: true).errorMessage, isNull);
    });

    test('null errorMessage mevcut mesajı silmez', () {
      const original = StreamingState(errorMessage: 'bir hata');

      expect(original.copyWith(isBusy: true).errorMessage, 'bir hata');
    });

    test('clearSessionId oturumu düşürür', () {
      const original = StreamingState(sessionId: 'session');

      expect(original.copyWith(clearSessionId: true).sessionId, isNull);
    });
  });

  group('varsayılanlar', () {
    test('uygulama durdurulmuş durumda başlar', () {
      const state = StreamingState();

      expect(state.connection, StreamConnectionState.stopped);
      expect(state.isStreaming, isFalse);
      expect(state.isCameraReady, isFalse);
      expect(state.permission, CameraPermissionStatus.unknown);
      expect(state.errorMessage, isNull);
    });

    test('yeniden deneme sınırı tanımlı', () {
      const state = StreamingState();

      expect(state.maxReconnectAttempts, 3);
      expect(state.reconnectAttempt, 0);
    });
  });
}
