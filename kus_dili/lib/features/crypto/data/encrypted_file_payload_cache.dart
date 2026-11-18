import 'dart:convert';
import 'dart:io';

import 'package:cryptography/cryptography.dart';

import '../domain/payload_cache.dart';
import '../domain/secure_kv.dart';

/// Sohbet başına bir dosya; içerik AES-256-GCM ile şifreli. Anahtar cihazın
/// güvenli deposunda (Keystore/Keychain) tutulur, dosya sisteminde değil.
final class EncryptedFilePayloadCache implements PayloadCache {
  EncryptedFilePayloadCache({
    required Future<Directory> Function() directory,
    required SecureKv keys,
    required String ownerUid,
  }) : _directory = directory, // ignore: prefer_initializing_formals
       _keys = keys, // ignore: prefer_initializing_formals
       _owner = ownerUid;

  final Future<Directory> Function() _directory;
  final SecureKv _keys;
  final String _owner;
  final _algo = AesGcm.with256bits();
  SecretKey? _key;
  final Map<String, Map<String, String>> _chats = {};
  Future<void> _queue = Future.value();

  String get _keyName => 'cache.key.$_owner';

  Future<SecretKey> _secret() async {
    final k = _key;
    if (k != null) return k;
    final stored = await _keys.read(_keyName);
    if (stored != null) {
      return _key = SecretKey(base64Decode(stored));
    }
    final fresh = await _algo.newSecretKey();
    await _keys.write(_keyName, base64Encode(await fresh.extractBytes()));
    return _key = fresh;
  }

  Future<File> _file(String chatId) async {
    final dir = Directory('${(await _directory()).path}/kus_cache/$_owner');
    await dir.create(recursive: true);
    final safe = chatId.replaceAll(RegExp(r'[^A-Za-z0-9_-]'), '_');
    return File('${dir.path}/$safe.bin');
  }

  Future<Map<String, String>> _load(String chatId) async {
    final cached = _chats[chatId];
    if (cached != null) return cached;
    final f = await _file(chatId);
    var map = <String, String>{};
    if (await f.exists()) {
      try {
        final bytes = await f.readAsBytes();
        final box = SecretBox.fromConcatenation(
          bytes,
          nonceLength: 12,
          macLength: 16,
        );
        final clear = await _algo.decrypt(box, secretKey: await _secret());
        map = Map<String, String>.from(jsonDecode(utf8.decode(clear)) as Map);
      } catch (_) {
        // Bozuk veya başka anahtarla yazılmış dosya: yok say.
        map = {};
      }
    }
    return _chats[chatId] = map;
  }

  Future<T> _serial<T>(Future<T> Function() op) {
    final r = _queue.then((_) => op());
    _queue = r.then((_) {}, onError: (_) {});
    return r;
  }

  @override
  Future<String?> get(String chatId, String bodyHash) =>
      _serial(() async => (await _load(chatId))[bodyHash]);

  @override
  Future<void> put(String chatId, String bodyHash, String payloadJson) =>
      _serial(() async {
        final map = await _load(chatId);
        map[bodyHash] = payloadJson;
        await _write(chatId, map);
      });

  Future<void> _write(String chatId, Map<String, String> map) async {
    final box = await _algo.encrypt(
      utf8.encode(jsonEncode(map)),
      secretKey: await _secret(),
    );
    final f = await _file(chatId);
    final tmp = File('${f.path}.tmp');
    await tmp.writeAsBytes(box.concatenation(), flush: true);
    await tmp.rename(f.path);
  }

  @override
  Future<void> remove(String chatId, String bodyHash) => _serial(() async {
    final map = await _load(chatId);
    if (map.remove(bodyHash) == null) return;
    await _write(chatId, map);
  });

  @override
  Future<void> removeChat(String chatId) => _serial(() async {
    _chats.remove(chatId);
    final f = await _file(chatId);
    if (await f.exists()) await f.delete();
  });

  @override
  Future<void> clearAll() => _serial(() async {
    _chats.clear();
    final dir = Directory('${(await _directory()).path}/kus_cache/$_owner');
    if (await dir.exists()) await dir.delete(recursive: true);
  });
}
