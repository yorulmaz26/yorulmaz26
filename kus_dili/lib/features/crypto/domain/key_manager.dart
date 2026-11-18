import 'dart:typed_data';

import 'package:libsignal_protocol_dart/libsignal_protocol_dart.dart';

import '../data/persistent_signal_store.dart';
import 'key_directory.dart';

/// Cihazın Signal anahtarlarını üretir, açık kısımlarını yayımlar ve
/// tek kullanımlık ön-anahtarları yeniler.
final class KeyManager {
  KeyManager({
    required this.uid,
    required PersistentSignalStore store,
    required KeyDirectory directory,
  }) : _store = store,
       _dir = directory;

  static const int batchSize = 100;
  static const int refillBelow = 25;
  static const int signedPreKeyId = 1;

  final String uid;
  final PersistentSignalStore _store;
  final KeyDirectory _dir;
  Future<void>? _ensured;

  /// Uygulama başına bir kez çalışır (başarısız olursa tekrar denenir).
  Future<void> ensureKeys() => _ensured ??= _run().catchError((Object e) {
    _ensured = null;
    throw e;
  });

  Future<void> _run() async {
    if (!await _store.hasIdentity()) {
      await _createAndPublish();
      return;
    }
    final mine = await _store.getIdentityKeyPair();
    final published = await _dir.fetchIdentity(uid);
    final same =
        published != null &&
        _same(published.identityKey, mine.getPublicKey().serialize());
    if (!same) {
      // Sunucudaki anahtar bu cihazın değil (ör. başka telefonda kurulum):
      // bu cihazın anahtarlarını yeniden yayımla.
      await _publishIdentity();
      await _replacePreKeys();
      return;
    }
    if (await _dir.countPreKeys(uid) < refillBelow) {
      await _dir.addPreKeys(uid, await _generatePreKeys());
    }
  }

  Future<void> _createAndPublish() async {
    final identity = generateIdentityKeyPair();
    await _store.initializeIdentity(identity, generateRegistrationId(false));
    await _store.storeSignedPreKey(
      signedPreKeyId,
      generateSignedPreKey(identity, signedPreKeyId),
    );
    await _publishIdentity();
    await _replacePreKeys();
  }

  Future<void> _publishIdentity() async {
    final identity = await _store.getIdentityKeyPair();
    final spk = await _store.loadSignedPreKey(signedPreKeyId);
    await _dir.publishIdentity(
      uid,
      PublishedIdentity(
        registrationId: await _store.getLocalRegistrationId(),
        identityKey: identity.getPublicKey().serialize(),
        signedPreKeyId: signedPreKeyId,
        signedPreKey: spk.getKeyPair().publicKey.serialize(),
        signedPreKeySignature: spk.signature,
      ),
    );
  }

  Future<void> _replacePreKeys() async =>
      _dir.replacePreKeys(uid, await _generatePreKeys());

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
