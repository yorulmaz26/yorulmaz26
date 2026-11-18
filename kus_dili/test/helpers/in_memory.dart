import 'package:kus_dili/features/crypto/domain/payload_cache.dart';
import 'package:kus_dili/features/crypto/domain/secure_kv.dart';

/// YALNIZCA TESTLER: cihazın güvenli deposunun bellek içi karşılığı.
class InMemorySecureKv implements SecureKv {
  final Map<String, String> data = {};

  @override
  Future<String?> read(String key) async => data[key];

  @override
  Future<void> write(String key, String value) async => data[key] = value;

  @override
  Future<void> delete(String key) async => data.remove(key);

  @override
  Future<void> clearAll() async => data.clear();
}

/// YALNIZCA TESTLER: bellek içi çözülmüş içerik önbelleği.
class InMemoryPayloadCache implements PayloadCache {
  final Map<String, String> data = {};

  @override
  Future<String?> get(String chatId, String bodyHash) async =>
      data['$chatId/$bodyHash'];

  @override
  Future<void> put(String chatId, String bodyHash, String payloadJson) async =>
      data['$chatId/$bodyHash'] = payloadJson;

  @override
  Future<void> remove(String chatId, String bodyHash) async =>
      data.remove('$chatId/$bodyHash');

  @override
  Future<void> removeChat(String chatId) async =>
      data.removeWhere((k, _) => k.startsWith('$chatId/'));

  @override
  Future<void> clearAll() async => data.clear();
}
