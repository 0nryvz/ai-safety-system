import 'package:camera_stream_app/core/error/api_failure.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('ApiFailure.fromStatusCode', () {
    test('status kind eşlemesi', () {
      expect(
        ApiFailure.fromStatusCode(401).kind,
        ApiFailureKind.unauthenticated,
      );
      expect(ApiFailure.fromStatusCode(403).kind, ApiFailureKind.forbidden);
      expect(ApiFailure.fromStatusCode(400).kind, ApiFailureKind.validation);
      expect(ApiFailure.fromStatusCode(422).kind, ApiFailureKind.validation);
      expect(ApiFailure.fromStatusCode(409).kind, ApiFailureKind.conflict);
      expect(ApiFailure.fromStatusCode(500).kind, ApiFailureKind.server);
      expect(ApiFailure.fromStatusCode(418).kind, ApiFailureKind.unknown);
    });
  });
}
