import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:cryptography/cryptography.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Anahtar deposu soyutlaması (testlerde bellek içi sürüm kullanılır).
abstract class KeyStore {
  Future<String?> read(String key);
  Future<void> write(String key, String value);
  Future<void> delete(String key);
}

/// iOS Keychain / Android Keystore destekli güvenli depo.
class SecureKeyStore implements KeyStore {
  SecureKeyStore()
      : _s = const FlutterSecureStorage(
          iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock_this_device),
        );
  final FlutterSecureStorage _s;

  @override
  Future<String?> read(String key) => _s.read(key: key);
  @override
  Future<void> write(String key, String value) => _s.write(key: key, value: value);
  @override
  Future<void> delete(String key) => _s.delete(key: key);
}

class MemoryKeyStore implements KeyStore {
  final _m = <String, String>{};
  @override
  Future<String?> read(String key) async => _m[key];
  @override
  Future<void> write(String key, String value) async => _m[key] = value;
  @override
  Future<void> delete(String key) async => _m.remove(key);
}

/// Alan düzeyinde şifreleme (AES-256-GCM).
///
/// * Veri anahtarı ilk açılışta rastgele üretilir ve yalnızca güvenli depoda
///   (Keychain/Keystore) saklanır; veritabanına yazılmaz.
/// * Şifreli metinler `enc:v1:<base64(nonce|şifreli|mac)>` biçimindedir.
/// * Yedekler, kullanıcı parolasından PBKDF2-HMAC-SHA256 ile türetilen ayrı
///   bir anahtarla şifrelenir.
class CryptoService {
  CryptoService(this._store);

  static const prefix = 'enc:v1:';
  static const _dataKeyName = 'hc_data_key_v1';
  static const pbkdf2Iterations = 210000;

  final KeyStore _store;
  final _aes = AesGcm.with256bits();
  SecretKey? _key;

  static bool isEncrypted(String? s) => s != null && s.startsWith(prefix);

  Future<SecretKey> _dataKey() async {
    if (_key != null) return _key!;
    final existing = await _store.read(_dataKeyName);
    if (existing != null) {
      _key = SecretKey(base64Decode(existing));
    } else {
      final bytes = randomBytes(32);
      await _store.write(_dataKeyName, base64Encode(bytes));
      _key = SecretKey(bytes);
    }
    return _key!;
  }

  /// Yedeğe konmak üzere veri anahtarını dışa verir.
  Future<String> exportDataKey() async => base64Encode(await (await _dataKey()).extractBytes());

  static Uint8List randomBytes(int n) {
    final r = Random.secure();
    return Uint8List.fromList(List<int>.generate(n, (_) => r.nextInt(256)));
  }

  Future<String> encrypt(String plain) async {
    if (plain.isEmpty) return plain;
    if (isEncrypted(plain)) return plain;
    return encryptWithKey(plain, await _dataKey());
  }

  Future<String> decrypt(String value) async {
    if (!isEncrypted(value)) return value;
    return decryptWithKey(value, await _dataKey());
  }

  /// Şifre çözülemezse (ör. anahtar kaybı) yer tutucu döner, uygulama çökmez.
  Future<String> decryptOrPlaceholder(String value) async {
    try {
      return await decrypt(value);
    } catch (_) {
      return '[Bu içerik bu cihazda çözülemedi]';
    }
  }

  Future<String> encryptWithKey(String plain, SecretKey key) async {
    final box = await _aes.encrypt(utf8.encode(plain), secretKey: key, nonce: _aes.newNonce());
    return prefix + base64Encode(box.concatenation());
  }

  Future<String> decryptWithKey(String value, SecretKey key) async {
    final raw = base64Decode(value.substring(prefix.length));
    final box = SecretBox.fromConcatenation(raw, nonceLength: 12, macLength: 16);
    final clear = await _aes.decrypt(box, secretKey: key);
    return utf8.decode(clear);
  }

  // --------------------------- Bayt şifreleme ----------------------------
  Future<Uint8List> encryptBytes(List<int> data, SecretKey key) async {
    final box = await _aes.encrypt(data, secretKey: key, nonce: _aes.newNonce());
    return box.concatenation();
  }

  Future<List<int>> decryptBytes(List<int> data, SecretKey key) {
    final box = SecretBox.fromConcatenation(data, nonceLength: 12, macLength: 16);
    return _aes.decrypt(box, secretKey: key);
  }

  static Future<SecretKey> deriveKey(String password, List<int> salt, {int iterations = pbkdf2Iterations}) {
    final kdf = Pbkdf2.hmacSha256(iterations: iterations, bits: 256);
    return kdf.deriveKeyFromPassword(password: password, nonce: salt);
  }

  static Future<String> sha256Hex(List<int> data) async {
    final h = await Sha256().hash(data);
    return h.bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  }
}
