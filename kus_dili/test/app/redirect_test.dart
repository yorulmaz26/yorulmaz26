import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/app/router.dart';
import 'package:kus_dili/features/auth/application/auth_providers.dart';

void main() {
  const all = [
    Routes.splash,
    Routes.auth,
    Routes.profileSetup,
    Routes.home,
    Routes.firebaseSetup,
  ];

  test('Çevirici ve öğrenme modülü her durumda açık', () {
    for (final s in SessionStatus.values) {
      for (final loc in [
        Routes.translator,
        Routes.learn,
        Routes.learnQuiz,
        Routes.learnLesson('aile'),
      ]) {
        expect(redirectFor(s, loc), isNull, reason: '$s $loc');
      }
    }
  });

  final expected = {
    SessionStatus.firebaseUnavailable: Routes.firebaseSetup,
    SessionStatus.loading: Routes.splash,
    SessionStatus.error: Routes.splash,
    SessionStatus.signedOut: Routes.auth,
    SessionStatus.needsProfile: Routes.profileSetup,
  };
  expected.forEach((status, target) {
    test('$status → $target', () {
      for (final loc in all) {
        expect(
          redirectFor(status, loc),
          loc == target ? isNull : target,
          reason: loc,
        );
      }
    });
  });

  test('ready: giriş ekranlarından ana ekrana, diğerlerinde kal', () {
    expect(redirectFor(SessionStatus.ready, Routes.auth), Routes.home);
    expect(redirectFor(SessionStatus.ready, Routes.splash), Routes.home);
    expect(redirectFor(SessionStatus.ready, Routes.profileSetup), Routes.home);
    expect(redirectFor(SessionStatus.ready, Routes.home), isNull);
  });
}
