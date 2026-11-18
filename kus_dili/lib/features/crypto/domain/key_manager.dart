import 'dart:math';
import 'dart:typed_data';

import 'package:libsignal_protocol_dart/libsignal_protocol_dart.dart';

import '../data/persistent_signal_store.dart';
import 'key_directory.dart';

/// Bu cihaz, hesabın başka bir cihazından kaldırıldı. Anahtarlar silinip
/// oturum kapatılmalı.
final class DeviceRemovedException implements Exception {
  const DeviceRemovedException();
  String get message => 'Bu cihazın bağlantısı başka bir cihazdan kaldırıldı.';
}

/// Cihazın Signal anahtarlarını üretir, açık kısımlarını yayımlar ve
/// tek kullanımlık ön-anahtarları yeniler.
///
/// Her kurulum ayrı bir cihazdır (rastgele cihaz kimliği). Aynı hesaba en
/// fazla [maxDevices] cihaz bağlanabilir; telefonla giriş yapılan her yeni
/// cihaz otomatik olarak eklenir ve diğer cihazlarda ve karşı taraflarda
/// "yeni cihaz / güvenlik numarası değişti" uyarısı çıkar.
final class KeyManager {
  KeyManager({
    required this.uid,
    required PersistentSignalStore store,
    required KeyDirectory directory,
    this.deviceName = 'Cihaz',
    Random? random,
  }) : _store = store,
       _dir = directory,
       _random = random ?? Random.secure();

  static const int batchSize = 100;
  static const int refillBelow = 25;
  static const int signedPreKeyId = 1;

  final String uid;
  final String deviceName;
  final PersistentSignalStore _store;
  final KeyDirectory _dir;
  final Random _random;
  Future<int>? _ensured;

  /// Uygulama başına bir kez çalışır (başarısız olursa tekrar denenir).
  /// Bu cihazın kimliğini döndürür.
  Future<int> ensureKeys() => _ensured ??= _run().catchError((Object e) {
    _ensured = null;
    throw e;
  });

  /// Bu cihaz hesaba kaydedildi mi (kaldırılma tespiti için)?
  Future<bool> isRegistered() async =>
      await _store.readMeta('device.registered') != null;

  /// Kayıtlıysa bu cihazın kimliği.
  Future<int?> deviceId() async =>
      int.tryParse(await _store.readMeta('device.id') ?? '');

  Future<int> _run() async {
    if (!await _store.hasIdentity()) {
      final identity = generateIdentityKeyPair();
      await _store.initializeIdentity(identity, generateRegistrationId(false));
      await _store.storeSignedPreKey(
        signedPreKeyId,
        generateSignedPreKey(identity, signedPreKeyId),
      );
    }
    var id = await deviceId();
    if (id == null) {
      // 1 eski tek cihazlı sürüme ayrılmış; 2..2^31-1 arası rastgele.
      id = 2 + _random.nextInt(0x7FFFFFFD);
      await _store.writeMeta('device.id', '$id');
    }
    final registered = await isRegistered();
    final active = await _dir.devices(uid);
    if (!active.contains(id)) {
      if (registered) throw const DeviceRemovedException();
      await _publish(id);
      await _dir.replacePreKeys(uid, id, await _generatePreKeys());
      await _store.writeMeta('device.registered', '1');
      return id;
    }
    final mine = await _store.getIdentityKeyPair();
    final published = await _dir.fetchIdentity(uid, id);
    final same =
        published != null &&
        _same(published.identityKey, mine.getPublicKey().serialize());
    // Yeniden yayımlamak "son görülme" zamanını da günceller.
    await _publish(id);
    if (!same) {
      await _dir.replacePreKeys(uid, id, await _generatePreKeys());
    } else if (await _dir.countPreKeys(uid, id) < refillBelow) {
      await _dir.addPreKeys(uid, id, await _generatePreKeys());
    }
    await _store.writeMeta('device.registered', '1');
    return id;
  }

  Future<void> _publish(int id) async {
    final identity = await _store.getIdentityKeyPair();
    final spk = await _store.loadSignedPreKey(signedPreKeyId);
    await _dir.registerDevice(
      uid,
      id,
      PublishedIdentity(
        registrationId: await _store.getLocalRegistrationId(),
        identityKey: identity.getPublicKey().serialize(),
        signedPreKeyId: signedPreKeyId,
        signedPreKey: spk.getKeyPair().publicKey.serialize(),
        signedPreKeySignature: spk.signature,
      ),
      name: deviceName,
    );
  }

  Future<Map<int, Uint8List>> _generatePreKeys() async {
    final start = await _store.nextPreKeyId();
    final records = generatePreKeys(start, batchSize);
    final out = <int, Uint8List>{};
    for (final r in records) {
      await _store.storePreKey(r.id, r);
      out[r.id] = r.getKeyPair().publicKey.serialize();
    }
    // Kimlikler 24 bitle sınırlı; başa dön.
    await _store.setNextPreKeyId((start + batchSize) % 0xFFFFFF + 1);
    return out;
  }

  static bool _same(List<int> a, List<int> b) {
    if (a.length != b.length) return false;
    var d = 0;
    for (var i = 0; i < a.length; i++) {
      d |= a[i] ^ b[i];
    }
    return d == 0;
  }
}
