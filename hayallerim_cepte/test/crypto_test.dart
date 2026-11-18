import 'package:flutter_test/flutter_test.dart';
import 'package:hayallerim_cepte/core/services/crypto_service.dart';
import 'package:hayallerim_cepte/core/services/lock_service.dart';

void main() {
  test('şifreleme gidiş-dönüş, her seferinde farklı nonce', () async {
    final c = CryptoService(MemoryKeyStore());
    final a = await c.encrypt('Merhaba dünya ✨');
    final b = await c.encrypt('Merhaba dünya ✨');
    expect(a, startsWith(CryptoService.prefix));
    expect(a, isNot(b));
    expect(await c.decrypt(a), 'Merhaba dünya ✨');
    expect(await c.encrypt(''), '');
    expect(await c.decrypt('düz metin'), 'düz metin');
  });

  test('farklı anahtarla çözülemez, yer tutucu döner', () async {
    final a = await CryptoService(MemoryKeyStore()).encrypt('gizli');
    final other = CryptoService(MemoryKeyStore());
    expect(await other.decryptOrPlaceholder(a), contains('çözülemedi'));
  });

  test('anahtar güvenli depoda kalıcıdır', () async {
    final store = MemoryKeyStore();
    final enc = await CryptoService(store).encrypt('kalıcı');
    expect(await CryptoService(store).decrypt(enc), 'kalıcı');
  });

  test('PIN: özet saklanır, hatalı denemelerde bekleme', () async {
    final store = MemoryKeyStore();
    final lock = LockService(store);
    expect(await lock.hasPin(), false);
    await lock.setPin('1234');
    expect(await store.read('hc_pin_hash_v1'), isNot(contains('1234')));
    expect(await lock.verifyPin('1234'), true);
    for (var i = 0; i < 5; i++) {
      expect(await lock.verifyPin('0000'), false);
    }
    expect(await lock.lockoutRemaining(), isNotNull);
    expect(await lock.verifyPin('1234'), false); // bekleme süresi içinde
    expect(() => lock.setPin('12'), throwsArgumentError);
  });
}
