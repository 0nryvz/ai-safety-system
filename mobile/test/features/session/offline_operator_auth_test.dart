import 'package:flutter_test/flutter_test.dart';

import 'package:camera_stream_app/features/session/offline_operator_auth.dart';

void main() {
  group('OfflineOperatorAuth', () {
    test('demo seed hesabını kabul eder', () {
      expect(
        OfflineOperatorAuth.matches(
          email: 'admin@isgvision.local',
          password: '123456',
        ),
        isTrue,
      );
    });

    test('e-posta büyük/küçük harfe duyarsızdır', () {
      expect(
        OfflineOperatorAuth.matches(
          email: 'Admin@ISGVision.local',
          password: '123456',
        ),
        isTrue,
      );
    });

    test('yanlış şifreyi reddeder', () {
      expect(
        OfflineOperatorAuth.matches(
          email: OfflineOperatorAuth.email,
          password: 'wrong',
        ),
        isFalse,
      );
    });

    test('katalog seed kameralarını döner', () {
      final cameras = OfflineOperatorAuth.cameras();
      expect(cameras, isNotEmpty);
      expect(cameras.first.id, startsWith('33333333-'));
    });
  });
}
