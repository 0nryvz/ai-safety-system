import 'package:camera_stream_app/core/models/auth_tokens.dart';
import 'package:camera_stream_app/core/models/user_summary.dart';
import 'package:camera_stream_app/features/auth/auth_session.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('AuthTokens', () {
    test('AuthResponse json okur', () {
      final tokens = AuthTokens.fromJson({
        'accessToken': 'a',
        'refreshToken': 'r',
        'tokenType': 'Bearer',
      });

      expect(tokens.accessToken, 'a');
      expect(tokens.refreshToken, 'r');
      expect(tokens.tokenType, 'Bearer');
    });

    test('refreshToken yoksa FormatException', () {
      expect(
        () => AuthTokens.fromJson({'accessToken': 'a'}),
        throwsFormatException,
      );
    });
  });

  group('UserSummary', () {
    test('UserResponse json okur', () {
      final user = UserSummary.fromJson({
        'id': '11111111-0000-4000-8000-000000000001',
        'email': 'a@b.c',
        'fullName': 'Ada',
        'active': true,
        'roles': ['OHS_SPECIALIST'],
        'departmentIds': ['22222222-0000-4000-8000-000000000001'],
      });

      expect(user.isAdmin, isFalse);
      expect(user.roles, contains('OHS_SPECIALIST'));
      expect(user.departmentIds, hasLength(1));
    });
  });

  group('AuthSession', () {
    test('token + user yoksa authenticated false', () {
      const session = AuthSession(
        accessToken: 't',
        refreshToken: 'r',
      );
      expect(session.authenticated, isFalse);
    });

    test('token + user varsa authenticated true', () {
      const user = UserSummary(
        id: '1',
        email: 'a@b.c',
        fullName: 'A',
        active: true,
        roles: {'ADMIN'},
        departmentIds: {'d1'},
      );
      const session = AuthSession(
        accessToken: 't',
        refreshToken: 'r',
        currentUser: user,
      );

      expect(session.authenticated, isTrue);
      expect(session.isAdmin, isTrue);
      expect(session.roles, contains('ADMIN'));
      expect(session.departmentIds, contains('d1'));
    });
  });
}
