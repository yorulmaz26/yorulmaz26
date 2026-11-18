import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/auth/domain/auth_failure.dart';
import 'package:kus_dili/features/auth/domain/phone_number.dart';

void main() {
  group('PhoneNumber.tryParse — Türkiye', () {
    const valid = [
      '0532 123 45 67',
      '05321234567',
      '532 123 45 67',
      '(532) 123-45-67',
      '90 532 123 45 67',
      '+90 532 123 45 67',
      '0090 532 123 45 67',
      ' +905321234567 ',
    ];
    for (final v in valid) {
      test('"$v" geçerli', () {
        final p = PhoneNumber.tryParse(v);
        expect(p?.e164, '+905321234567');
        expect(p?.display, '+90 532 123 45 67');
      });
    }

    const invalid = [
      '',
      'abc',
      '0212 123 45 67',
      '+90 212 123 45 67',
      '0532 123 45 6',
      '0532 123 45 678',
      '1234',
      '+0 532',
      '532-123-45-67x',
      '++905321234567',
    ];
    for (final v in invalid) {
      test('"$v" geçersiz', () => expect(PhoneNumber.tryParse(v), isNull));
    }
  });

  test('Uluslararası numaralar', () {
    expect(PhoneNumber.tryParse('+49 1512 3456789')?.e164, '+4915123456789');
    expect(PhoneNumber.tryParse('0044 7700 900123')?.e164, '+447700900123');
    expect(PhoneNumber.tryParse('+49 1512 3456789')?.isTurkish, isFalse);
    expect(
      PhoneNumber.tryParse('+1234567890123456'),
      isNull,
      reason: '>15 hane',
    );
  });

  test('Firebase hata kodları Türkçe mesaja eşlenir', () {
    expect(
      AuthFailure.fromFirebaseCode('invalid-verification-code').code,
      AuthFailureCode.invalidCode,
    );
    expect(
      AuthFailure.fromFirebaseCode('session-expired').code,
      AuthFailureCode.codeExpired,
    );
    expect(
      AuthFailure.fromFirebaseCode('too-many-requests').code,
      AuthFailureCode.tooManyRequests,
    );
    expect(
      AuthFailure.fromFirebaseCode('invalid-app-credential').code,
      AuthFailureCode.appNotAuthorized,
    );
    expect(
      AuthFailure.fromFirebaseCode('bilinmeyen').code,
      AuthFailureCode.unknown,
    );
    for (final c in AuthFailureCode.values) {
      expect(AuthFailure(c).message, isNotEmpty);
    }
  });
}
