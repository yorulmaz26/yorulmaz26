import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/crypto/data/encrypted_file_payload_cache.dart';

import '../../helpers/in_memory.dart';

void main() {
  late Directory tmp;
  setUp(() => tmp = Directory.systemTemp.createTempSync('kus_cache_'));
  tearDown(() => tmp.deleteSync(recursive: true));

  test('Diskte şifreli saklanır, yeniden açılınca okunur', () async {
    final kv = InMemorySecureKv();
    EncryptedFilePayloadCache make() => EncryptedFilePayloadCache(
      directory: () async => tmp,
      keys: kv,
      ownerUid: 'u1',
    );
    final c = make();
    await c.put('d_a_b', 'h1', '{"text":"Seni seviyorum"}');
    await c.put('d_a_b', 'h2', '{"text":"İkinci"}');

    final files = tmp.listSync(recursive: true).whereType<File>().toList();
    expect(files, hasLength(1));
    final raw = files.single.readAsBytesSync();
    expect(String.fromCharCodes(raw), isNot(contains('seviyorum')));

    final again = make();
    expect(await again.get('d_a_b', 'h1'), '{"text":"Seni seviyorum"}');
    expect(await again.get('d_a_b', 'yok'), isNull);

    // Anahtar kaybolursa (başka cihaz) içerik okunamaz, hata da fırlatmaz.
    final stranger = EncryptedFilePayloadCache(
      directory: () async => tmp,
      keys: InMemorySecureKv(),
      ownerUid: 'u1',
    );
    expect(await stranger.get('d_a_b', 'h1'), isNull);

    await again.removeChat('d_a_b');
    expect(await make().get('d_a_b', 'h1'), isNull);
  });
}
