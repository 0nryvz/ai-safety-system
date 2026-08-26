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

  group('telefon kamerası değiştirme kuralı', () {
    const ready = StreamingState(
      isCameraReady: true,
      availableCameraCount: 2,
    );

    test('hazır ve boştayken değiştirilebilir', () {
      expect(ready.canSwitchPhoneCamera, isTrue);
    });

    test('yayın sırasında değiştirilemez', () {
      expect(ready.copyWith(isStreaming: true).canSwitchPhoneCamera, isFalse);
    });

    test('geçiş anında değiştirilemez', () {
      expect(ready.copyWith(isBusy: true).canSwitchPhoneCamera, isFalse);
    });

    test('tek kamera varsa değiştirilemez', () {
      expect(
        ready.copyWith(availableCameraCount: 1).canSwitchPhoneCamera,
        isFalse,
      );
    });
  });

  group('yayın başlatma kuralı', () {
    test('atanmamış kamerayla yayın açılamaz', () {
      const state = StreamingState(
        isCameraReady: true,
        isCameraAssigned: false,
      );

      expect(state.canStartStream, isFalse);
    });

    test('atanmış ve hazır kamerayla yayın açılabilir', () {
      const state = StreamingState(
        isCameraReady: true,
        isCameraAssigned: true,
        cameraId: 'uuid',
        cameraName: 'Kaynak-1',
      );

      expect(state.canStartStream, isTrue);
      expect(state.displayCameraTitle, 'Kaynak-1');
    });

    test('atanmamışken başlık operatörü yönlendirir', () {
      const state = StreamingState();

      expect(state.displayCameraTitle, contains('seçilmedi'));
    });
  });

  group('copyWith', () {
    test('verilmeyen alanlar korunur', () {
      const original = StreamingState(
        cameraId: 'cam',
        sessionId: 'session',
        sentFrames: 5,
        isCameraAssigned: true,
      );

      final updated = original.copyWith(isStreaming: true);

      expect(updated.cameraId, 'cam');
      expect(updated.sessionId, 'session');
      expect(updated.sentFrames, 5);
      expect(updated.isStreaming, isTrue);
      expect(updated.isCameraAssigned, isTrue);
    });

    test('clearError hata mesajını siler', () {
      const original = StreamingState(errorMessage: 'bir hata');

      expect(original.copyWith(clearError: true).errorMessage, isNull);
    });

    test('clearSessionId oturumu düşürür', () {
      const original = StreamingState(sessionId: 'session');

      expect(original.copyWith(clearSessionId: true).sessionId, isNull);
    });

    test('clearCameraMeta atama bilgilerini siler', () {
      const original = StreamingState(
        isCameraAssigned: true,
        cameraId: 'uuid',
        cameraName: 'Kaynak',
      );

      final cleared = original.copyWith(clearCameraMeta: true);

      expect(cleared.cameraId, isNull);
      expect(cleared.cameraName, isNull);
    });
  });

  group('kabul FPS göstergesi', () {
    test('varsayılan sıfırdır', () {
      expect(const StreamingState().acceptedFps, 0);
    });

    test('copyWith kabul FPS ve kamera FPS için ayrı alan taşır', () {
      final updated = const StreamingState().copyWith(
        cameraFps: 15,
        acceptedFps: 7,
      );

      expect(updated.cameraFps, 15);
      expect(updated.acceptedFps, 7);
    });

    test('kabul FPS kamera FPS güncellenirken korunur', () {
      const original = StreamingState(acceptedFps: 9);

      expect(original.copyWith(cameraFps: 12).acceptedFps, 9);
    });
  });

  group('varsayılanlar', () {
    test('uygulama durdurulmuş ve atanmamış başlar', () {
      const state = StreamingState();

      expect(state.connection, StreamConnectionState.stopped);
      expect(state.isStreaming, isFalse);
      expect(state.isCameraAssigned, isFalse);
      expect(state.permission, CameraPermissionStatus.unknown);
    });
  });
}
