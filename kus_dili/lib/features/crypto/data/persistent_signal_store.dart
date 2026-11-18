import 'dart:convert';
import 'dart:typed_data';

import 'package:libsignal_protocol_dart/libsignal_protocol_dart.dart';

import '../domain/secure_kv.dart';

/// Signal Protokolü durumunu (kimlik anahtarı, ön-anahtarlar, oturumlar,
/// güvenilen kimlikler) cihazın güvenli deposunda kalıcı tutar.
///
/// Kimlik değişimi politikası: ilk kullanımda güven (TOFU). Karşı tarafın
/// kimlik anahtarı değişirse yeni anahtar kabul edilir ama "değişti"
/// işareti konur; arayüz kullanıcıyı uyarır ve güvenlik numarasını yeniden
/// doğrulamaya yönlendirir.
final class PersistentSignalStore
    implements SignalProtocolStore, SenderKeyStore {
  PersistentSignalStore(this._kv, this._owner);

  final SecureKv _kv;
  final String _owner;

  String _k(String s) => 'sig.$_owner.$s';
  static String _addr(SignalProtocolAddress a) =>
      '${a.getName()}.${a.getDeviceId()}';

  Future<Uint8List?> _bytes(String key) async {
    final v = await _kv.read(_k(key));
    return v == null ? null : base64Decode(v);
  }

  Future<void> _put(String key, Uint8List v) =>
      _kv.write(_k(key), base64Encode(v));

  // ---- Kimlik ---------------------------------------------------------------

  Future<bool> hasIdentity() async => await _kv.read(_k('identity')) != null;

  Future<void> initializeIdentity(
    IdentityKeyPair pair,
    int registrationId,
  ) async {
    await _put('identity', pair.serialize());
    await _kv.write(_k('regId'), '$registrationId');
  }

  @override
  Future<IdentityKeyPair> getIdentityKeyPair() async {
    final b = await _bytes('identity');
    if (b == null) throw StateError('Kimlik anahtarı yok.');
    return IdentityKeyPair.fromSerialized(b);
  }

  @override
  Future<int> getLocalRegistrationId() async =>
      int.parse((await _kv.read(_k('regId')))!);

  @override
  Future<IdentityKey?> getIdentity(SignalProtocolAddress address) async {
    final b = await _bytes('trusted.${_addr(address)}');
    return b == null ? null : IdentityKey.fromBytes(b, 0);
  }

  @override
  Future<bool> isTrustedIdentity(
    SignalProtocolAddress address,
    IdentityKey? identityKey,
    Direction? direction,
  ) async => identityKey != null;

  @override
  Future<bool> saveIdentity(
    SignalProtocolAddress address,
    IdentityKey? identityKey,
  ) async {
    if (identityKey == null) return false;
    final existing = await getIdentity(address);
    final changed =
        existing != null &&
        !_same(existing.serialize(), identityKey.serialize());
    if (existing == null || changed) {
      await _put('trusted.${_addr(address)}', identityKey.serialize());
    }
    if (changed) {
      await _kv.write(_k('changed.${address.getName()}'), '1');
    }
    return changed;
  }

  /// Karşı tarafın kimlik anahtarı son doğrulamadan beri değişti mi?
  Future<bool> identityChanged(String uid) async =>
      await _kv.read(_k('changed.$uid')) != null;

  Future<void> acknowledgeIdentity(String uid) =>
      _kv.delete(_k('changed.$uid'));

  static bool _same(List<int> a, List<int> b) {
    if (a.length != b.length) return false;
    var d = 0;
    for (var i = 0; i < a.length; i++) {
      d |= a[i] ^ b[i];
    }
    return d == 0;
  }

  // ---- Ön-anahtarlar --------------------------------------------------------

  @override
  Future<PreKeyRecord> loadPreKey(int preKeyId) async {
    final b = await _bytes('prekey.$preKeyId');
    if (b == null) throw InvalidKeyIdException('Ön-anahtar yok: $preKeyId');
    return PreKeyRecord.fromBuffer(b);
  }

  @override
  Future<void> storePreKey(int preKeyId, PreKeyRecord record) =>
      _put('prekey.$preKeyId', record.serialize());

  @override
  Future<bool> containsPreKey(int preKeyId) async =>
      await _kv.read(_k('prekey.$preKeyId')) != null;

  @override
  Future<void> removePreKey(int preKeyId) => _kv.delete(_k('prekey.$preKeyId'));

  Future<int> nextPreKeyId() async =>
      int.tryParse(await _kv.read(_k('nextPreKeyId')) ?? '') ?? 1;

  Future<void> setNextPreKeyId(int id) => _kv.write(_k('nextPreKeyId'), '$id');

  // ---- İmzalı ön-anahtar ----------------------------------------------------

  @override
  Future<SignedPreKeyRecord> loadSignedPreKey(int signedPreKeyId) async {
    final b = await _bytes('spk.$signedPreKeyId');
    if (b == null) {
      throw InvalidKeyIdException('İmzalı ön-anahtar yok: $signedPreKeyId');
    }
    return SignedPreKeyRecord.fromSerialized(b);
  }

  @override
  Future<List<SignedPreKeyRecord>> loadSignedPreKeys() async {
    final ids = (await _kv.read(_k('spkIds')))?.split(',') ?? const [];
    return [
      for (final id in ids.where((s) => s.isNotEmpty))
        await loadSignedPreKey(int.parse(id)),
    ];
  }

  @override
  Future<void> storeSignedPreKey(int id, SignedPreKeyRecord record) async {
    await _put('spk.$id', record.serialize());
    final ids = {...?(await _kv.read(_k('spkIds')))?.split(','), '$id'}
      ..remove('');
    await _kv.write(_k('spkIds'), ids.join(','));
  }

  @override
  Future<bool> containsSignedPreKey(int id) async =>
      await _kv.read(_k('spk.$id')) != null;

  @override
  Future<void> removeSignedPreKey(int id) async {
    await _kv.delete(_k('spk.$id'));
    final ids = (await _kv.read(_k('spkIds')))?.split(',') ?? <String>[];
    await _kv.write(_k('spkIds'), (ids..remove('$id')).join(','));
  }

  // ---- Oturumlar ------------------------------------------------------------

  @override
  Future<SessionRecord> loadSession(SignalProtocolAddress address) async {
    final b = await _bytes('session.${_addr(address)}');
    return b == null ? SessionRecord() : SessionRecord.fromSerialized(b);
  }

  @override
  Future<List<int>> getSubDeviceSessions(String name) async => const [];

  @override
  Future<void> storeSession(
    SignalProtocolAddress address,
    SessionRecord record,
  ) => _put('session.${_addr(address)}', record.serialize());

  @override
  Future<bool> containsSession(SignalProtocolAddress address) async =>
      await _kv.read(_k('session.${_addr(address)}')) != null;

  @override
  Future<void> deleteSession(SignalProtocolAddress address) =>
      _kv.delete(_k('session.${_addr(address)}'));

  @override
  Future<void> deleteAllSessions(String name) =>
      deleteSession(SignalProtocolAddress(name, 1));

  // ---- Grup gönderici anahtarları (Sender Keys) -----------------------------

  @override
  Future<void> storeSenderKey(SenderKeyName name, SenderKeyRecord record) =>
      _put('sk.${name.serialize()}', record.serialize());

  @override
  Future<SenderKeyRecord> loadSenderKey(SenderKeyName name) async {
    final b = await _bytes('sk.${name.serialize()}');
    return b == null ? SenderKeyRecord() : SenderKeyRecord.fromSerialized(b);
  }

  // ---- Küçük durum değerleri -----------------------------------------------

  Future<String?> readMeta(String key) => _kv.read(_k('meta.$key'));

  Future<void> writeMeta(String key, String value) =>
      _kv.write(_k('meta.$key'), value);
}
