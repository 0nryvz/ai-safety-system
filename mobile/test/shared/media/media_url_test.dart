import 'package:camera_stream_app/shared/media/media_url.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('MediaUrl', () {
    test('parses url and expiresAt only', () {
      final media = MediaUrl.fromJson({
        'url': 'https://minio.example/presigned',
        'expiresAt': '2026-08-18T18:05:00Z',
        'objectKey': 'should-be-ignored',
        'coverImageKey': 'should-be-ignored',
        'playbackUrl': 'should-be-ignored',
      });

      expect(media.url, 'https://minio.example/presigned');
      expect(media.expiresAt.toUtc().toIso8601String(), '2026-08-18T18:05:00.000Z');
    });

    test('missing url or expiresAt fails safely', () {
      expect(
        () => MediaUrl.fromJson({'expiresAt': '2026-08-18T18:05:00Z'}),
        throwsFormatException,
      );
      expect(
        () => MediaUrl.fromJson({'url': 'https://x'}),
        throwsFormatException,
      );
      expect(
        () => MediaUrl.fromJson({'url': 'https://x', 'expiresAt': 'not-an-instant'}),
        throwsFormatException,
      );
    });

    test('toString does not leak objectKey, coverImageKey or the raw url', () {
      final media = MediaUrl.fromJson({
        'url': 'https://minio.example/violations/secret-key?X-Amz-Signature=abc',
        'expiresAt': '2026-08-18T18:05:00Z',
      });

      expect(media.toString(), isNot(contains('objectKey')));
      expect(media.toString(), isNot(contains('coverImageKey')));
      expect(media.toString(), isNot(contains('secret-key')));
      expect(media.toString(), isNot(contains('X-Amz-Signature')));
    });

    test('expiry uses expiresAt minus skew', () {
      final media = MediaUrl(
        url: 'https://x',
        expiresAt: DateTime.parse('2026-08-18T18:05:00Z'),
      );
      const skew = Duration(seconds: 15);

      expect(
        media.isExpiredOrNear(DateTime.parse('2026-08-18T18:04:44Z'), skew),
        isFalse,
      );
      expect(
        media.isExpiredOrNear(DateTime.parse('2026-08-18T18:04:45Z'), skew),
        isTrue,
      );
    });
  });
}
