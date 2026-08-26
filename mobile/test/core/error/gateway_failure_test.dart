import 'package:camera_stream_app/core/error/gateway_failure.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('GatewayFailure.fromStatusCode', () {
    test('Gateway sözleşmesindeki kodları doğru türe eşler', () {
      final cases = <int, GatewayFailureKind>{
        401: GatewayFailureKind.unauthorized,
        403: GatewayFailureKind.cameraInactive,
        404: GatewayFailureKind.sessionNotFound,
        409: GatewayFailureKind.sessionConflict,
        413: GatewayFailureKind.frameTooLarge,
        415: GatewayFailureKind.unsupportedFormat,
        422: GatewayFailureKind.invalidRequest,
        503: GatewayFailureKind.lifecycleUnavailable,
      };

      cases.forEach((statusCode, expected) {
        expect(
          GatewayFailure.fromStatusCode(statusCode).kind,
          expected,
          reason: '$statusCode yanlış eşlendi',
        );
      });
    });

    test('bilinmeyen 5xx sunucu hatası sayılır', () {
      expect(
        GatewayFailure.fromStatusCode(500).kind,
        GatewayFailureKind.serverError,
      );
    });

    test('detail alanı tanılama için korunur', () {
      final failure = GatewayFailure.fromStatusCode(
        409,
        detail: 'SESSION_CONFLICT',
      );

      expect(failure.detail, 'SESSION_CONFLICT');
      expect(failure.statusCode, 409);
    });
  });

  group('yeniden denenebilirlik', () {
    test('kullanıcı aksiyonu gerektiren hatalar tekrar denenmez', () {
      const notRetryable = [
        GatewayFailureKind.unauthorized,
        GatewayFailureKind.cameraInactive,
        GatewayFailureKind.sessionConflict,
        GatewayFailureKind.frameTooLarge,
        GatewayFailureKind.unsupportedFormat,
        GatewayFailureKind.invalidRequest,
      ];

      for (final kind in notRetryable) {
        expect(
          GatewayFailure(kind: kind).isRetryable,
          isFalse,
          reason: '${kind.name} tekrar denenmemeli',
        );
      }
    });

    test('geçici hatalar tekrar denenir', () {
      const retryable = [
        GatewayFailureKind.network,
        GatewayFailureKind.sessionNotFound,
        GatewayFailureKind.lifecycleUnavailable,
        GatewayFailureKind.serverError,
        GatewayFailureKind.unknown,
      ];

      for (final kind in retryable) {
        expect(
          GatewayFailure(kind: kind).isRetryable,
          isTrue,
          reason: '${kind.name} tekrar denenmeli',
        );
      }
    });
  });

  group('kullanıcı mesajları', () {
    test('her tür için anlaşılır ve boş olmayan mesaj döner', () {
      for (final kind in GatewayFailureKind.values) {
        final message = GatewayFailure(kind: kind).userMessage;

        expect(message, isNotEmpty);
        expect(
          message,
          isNot(contains('Exception')),
          reason: '${kind.name} teknik detay sızdırıyor',
        );
      }
    });

    test('oturum çakışması diğer cihazı işaret eder', () {
      const failure = GatewayFailure(
        kind: GatewayFailureKind.sessionConflict,
      );

      expect(failure.userMessage, contains('başka bir aktif oturum'));
    });

    test('pasif kamera ayrı mesaj verir', () {
      const failure = GatewayFailure(
        kind: GatewayFailureKind.cameraInactive,
      );

      expect(failure.userMessage, contains('pasif'));
    });
  });

  group('GatewayResult', () {
    test('başarı sonucunda failure yoktur', () {
      const result = GatewayResult<void>.success(null);

      expect(result.isSuccess, isTrue);
      expect(result.failure, isNull);
    });

    test('hata sonucunda failure taşınır', () {
      const result = GatewayResult<void>.failed(
        GatewayFailure(kind: GatewayFailureKind.unauthorized),
      );

      expect(result.isSuccess, isFalse);
      expect(result.failure!.kind, GatewayFailureKind.unauthorized);
    });
  });
}
