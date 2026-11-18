import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/privacy/domain/local_privacy_prefs.dart';
import 'package:kus_dili/features/privacy/domain/pin_vault.dart';

import '../../helpers/in_memory.dart';

void main() {
  late InMemorySecureKv kv;
  late DateTime now;
  PinVault vault() => PinVault(kv, 'u1', clock: () => now, iterations: 1000);

  setUp(() {
    kv = InMemorySecureKv();
    now = DateTime(2026, 11, 18, 12);
  });

  test('PIN doğrulama ve düz metin saklanmaması', () async {
    expect(await vault().hasPin(), isFalse);
    await vault().setPin('482913');
    expect(await vault().hasPin(), isTrue);
    expect(kv.data.values.join(), isNot(contains('482913')));
    expect(await vault().verify('482913'), isA<PinOk>());
    final wrong = await vault().verify('000000');
    expect(wrong, isA<PinWrong>());
    expect((wrong as PinWrong).attemptsLeft, 4);
  });

  test('Geçersiz PIN biçimi', () {
    for (final p in ['123', '123456789', '12a4', '']) {
      expect(PinVault.validate(p), isNotNull, reason: p);
    }
    expect(PinVault.validate('1234'), isNull);
    expect(() => vault().setPin('12'), throwsArgumentError);
  });

  test(
    '5 hatadan sonra artan bekleme; uygulama yeniden açılsa da sürer',
    () async {
      await vault().setPin('1234');
      for (var i = 0; i < 4; i++) {
        expect(await vault().verify('9999'), isA<PinWrong>());
      }
      final locked = await vault().verify('9999');
      expect(locked, isA<PinLockedOut>());
      expect(
        (locked as PinLockedOut).until,
        now.add(const Duration(seconds: 30)),
      );

      // Bekleme sırasında doğru PIN bile kabul edilmez.
      expect(await vault().verify('1234'), isA<PinLockedOut>());

      now = now.add(const Duration(seconds: 31));
      final again = await vault().verify('9999');
      expect(
        (again as PinLockedOut).until,
        now.add(const Duration(seconds: 60)),
      );

      now = now.add(const Duration(seconds: 61));
      expect(await vault().verify('1234'), isA<PinOk>());
      // Başarı sayacı sıfırlar.
      expect(await vault().verify('9999'), isA<PinWrong>());
    },
  );

  test('Bekleme en fazla 1 saat', () async {
    await vault().setPin('1234');
    PinCheck r = const PinOk();
    for (var i = 0; i < 30; i++) {
      r = await vault().verify('0000');
      if (r is PinLockedOut) now = r.until;
    }
    now = now.subtract(const Duration(seconds: 1));
    r = await vault().verify('0000');
    expect(
      (r as PinLockedOut).until.difference(now),
      lessThanOrEqualTo(const Duration(hours: 1, seconds: 1)),
    );
  });

  test('PIN kaldırma', () async {
    await vault().setPin('1234');
    await vault().clear();
    expect(await vault().hasPin(), isFalse);
  });

  test('Gizlilik tercihleri yalnızca yerelde, JSON gidiş-dönüş', () async {
    final store = PrivacyPrefsStore(kv, 'u1');
    expect((await store.load()).appLock, isFalse);
    await store.save(
      const PrivacyPrefs(
        hiddenChats: {'d_a_b'},
        lockedChats: {'d_a_c'},
        appLock: true,
        biometrics: true,
      ),
    );
    final p = await PrivacyPrefsStore(kv, 'u1').load();
    expect(p.isHidden('d_a_b'), isTrue);
    expect(p.isLocked('d_a_c'), isTrue);
    expect(p.appLock, isTrue);
    expect((await PrivacyPrefsStore(kv, 'u2').load()).hiddenChats, isEmpty);
  });
}
