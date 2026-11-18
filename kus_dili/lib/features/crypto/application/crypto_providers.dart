import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';

import '../../../core/firebase/firebase_providers.dart';
import '../../chat/application/chat_providers.dart';
import '../data/encrypted_file_payload_cache.dart';
import '../data/firestore_key_directory.dart';
import '../data/flutter_secure_kv.dart';
import '../data/persistent_signal_store.dart';
import '../domain/key_directory.dart';
import '../domain/key_manager.dart';
import '../domain/payload_cache.dart';
import '../domain/secure_kv.dart';

final secureKvProvider = Provider<SecureKv>((ref) => FlutterSecureKv());

final keyDirectoryProvider = Provider<KeyDirectory>(
  (ref) => FirestoreKeyDirectory(ref.watch(firestoreProvider)),
);

final signalStoreProvider = Provider<PersistentSignalStore?>((ref) {
  final uid = ref.watch(myUidProvider);
  return uid == null
      ? null
      : PersistentSignalStore(ref.watch(secureKvProvider), uid);
});

final payloadCacheProvider = Provider<PayloadCache?>((ref) {
  final uid = ref.watch(myUidProvider);
  if (uid == null) return null;
  return EncryptedFilePayloadCache(
    directory: getApplicationSupportDirectory,
    keys: ref.watch(secureKvProvider),
    ownerUid: uid,
  );
});

final keyManagerProvider = Provider<KeyManager?>((ref) {
  final uid = ref.watch(myUidProvider);
  final store = ref.watch(signalStoreProvider);
  if (uid == null || store == null) return null;
  return KeyManager(
    uid: uid,
    store: store,
    directory: ref.watch(keyDirectoryProvider),
  );
});

/// Oturum açılınca anahtarları üretir/yayımlar ki başkaları bize şifreli
/// mesaj gönderebilsin.
final e2eeBootstrapProvider = FutureProvider<void>((ref) async {
  await ref.watch(keyManagerProvider)?.ensureKeys();
});

/// Karşı tarafın kimlik anahtarı değişti mi (uyarı bandı için)?
final identityChangedProvider = FutureProvider.family<bool, String>((
  ref,
  uid,
) async {
  final store = ref.watch(signalStoreProvider);
  return store != null && await store.identityChanged(uid);
});
