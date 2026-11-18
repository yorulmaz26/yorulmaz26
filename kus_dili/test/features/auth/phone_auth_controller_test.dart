import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/auth/application/auth_providers.dart';
import 'package:kus_dili/features/auth/application/phone_auth_controller.dart';
import 'package:kus_dili/features/auth/domain/auth_failure.dart';

import '../../helpers/fake_auth_repository.dart';

void main() {
  late FakeAuthRepository repo;
  late ProviderContainer c;
  late DateTime now;

  setUp(() {
    repo = FakeAuthRepository();
    now = DateTime(2026, 11, 17, 12);
    c = ProviderContainer(
      overrides: [
        authRepositoryProvider.overrideWithValue(repo),
        clockProvider.overrideWithValue(() => now),
      ],
    );
  });
  tearDown(() => c.dispose());

  PhoneAuthController ctrl() => c.read(phoneAuthControllerProvider.notifier);
  PhoneAuthState state() => c.read(phoneAuthControllerProvider);

  test('Geçersiz numara SMS göndermez', () async {
    await ctrl().submitPhone('0212 123 45 67');
    expect(repo.sendCalls, isEmpty);
    expect(state().error, contains('geçersiz'));
    expect(state().step, PhoneAuthStep.enterPhone);
  });

  test('Kod gönderilir, doğru kodla oturum açılır', () async {
    await ctrl().submitPhone('0532 123 45 67');
    expect(repo.sendCalls.single.$1.e164, '+905321234567');
    expect(state().step, PhoneAuthStep.enterCode);
    expect(state().verificationId, 'vid-1');

    await ctrl().submitCode('12345');
    expect(state().error, 'Kod 6 haneli olmalı.');
    await ctrl().submitCode('000000');
    expect(state().error, 'Doğrulama kodu hatalı.');
    expect(repo.currentUser, isNull);

    await ctrl().submitCode('123 456');
    expect(state().error, isNull);
    expect(repo.currentUser?.phoneNumber, '+905321234567');
  });

  test('Tekrar gönderme 60 sn bekletilir ve resendToken kullanılır', () async {
    await ctrl().submitPhone('05321234567');
    await ctrl().resend();
    expect(repo.sendCalls, hasLength(1), reason: 'bekleme süresi dolmadı');
    now = now.add(const Duration(seconds: 61));
    await ctrl().resend();
    expect(repo.sendCalls, hasLength(2));
    expect(repo.sendCalls.last.$2, 42);
    expect(state().verificationId, 'vid-2');
  });

  test('Firebase hatası kullanıcıya Türkçe gösterilir', () async {
    repo.nextSendFailure = const AuthFailure(AuthFailureCode.tooManyRequests);
    await ctrl().submitPhone('05321234567');
    expect(state().busy, isFalse);
    expect(state().step, PhoneAuthStep.enterPhone);
    expect(state().error, contains('Çok fazla deneme'));
  });

  test('Android otomatik doğrulama', () async {
    repo.autoVerify = true;
    await ctrl().submitPhone('05321234567');
    expect(repo.currentUser, isNotNull);
    expect(state().busy, isFalse);
  });

  test('Numarayı değiştir ilk adıma döner', () async {
    await ctrl().submitPhone('05321234567');
    ctrl().editPhone();
    expect(state().step, PhoneAuthStep.enterPhone);
    expect(state().verificationId, isNull);
  });
}
