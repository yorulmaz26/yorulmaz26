import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';

import '../../../core/firebase/firebase_providers.dart';
import '../../chat/application/chat_providers.dart';
import '../data/encrypted_file_payload_cache.dart';
import '../data/firestore_group_directory.dart';
import '../data/firestore_key_directory.dart';
import '../data/flutter_secure_kv.dart';
import '../data/persistent_signal_store.dart';
import '../domain/group_directory.dart';
import '../domain/key_directory.dart';
import '../domain/key_manager.dart';
import '../domain/payload_cache.dart';
import '../domain/secure_kv.dart';

final secureKvProvider = Provider<SecureKv>((ref) => FlutterSecureKv());

final keyDirectoryProvider = Provider<KeyDirectory>(
  (ref) => FirestoreKeyDirectory(ref.watch(firestoreProvider)),
);

final groupDirectoryProvider = Provider<GroupDirectory>(
  (ref) => FirestoreGroupDirectory(ref.watch(firestoreProvider)),
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

/// Cihazlar ekranında görünen ad.
final deviceNameProvider = Provider<String>(
  (ref) => switch (defaultTargetPlatform) {
    TargetPlatform.iOS => 'iPhone',
    TargetPlatform.android => 'Android telefon',
    _ => 'Cihaz',
  },
);

final keyManagerProvider = Provider<KeyManager?>((ref) {
  final uid = ref.watch(myUidProvider);
  final store = ref.watch(signalStoreProvider);
  if (uid == null || store == null) return null;
  return KeyManager(
    uid: uid,
    store: store,
    directory: ref.watch(keyDirectoryProvider),
    deviceName: ref.watch(deviceNameProvider),
  );
});

/// Oturum açılınca anahtarları üretir/yayımlar ki başkaları bize şifreli
/// mesaj gönderebilsin.
final e2eeBootstrapProvider = FutureProvider<void>((ref) async {
  await ref.watch(keyManagerProvider)?.ensureKeys();
});

/// Bu cihazın kimliği (henüz anahtar üretilmediyse `null`).
final thisDeviceIdProvider = FutureProvider<int?>((ref) async {
  ref.watch(e2eeBootstrapProvider);
  return ref.watch(keyManagerProvider)?.deviceId();
});

/// Hesabımın etkin cihaz kimlikleri (canlı).
final myDeviceIdsProvider = StreamProvider<List<int>>((ref) {
  final uid = ref.watch(myUidProvider);
  if (uid == null) return Stream.value(const []);
  return ref.watch(keyDirectoryProvider).watchDevices(uid);
});

/// Cihazlar ekranı için ayrıntılar.
final myDevicesProvider = FutureProvider<List<DeviceInfo>>((ref) async {
  final uid = ref.watch(myUidProvider);
  if (uid == null) return const [];
  await ref.watch(myDeviceIdsProvider.future);
  return ref.watch(keyDirectoryProvider).listDevices(uid);
});

/// Hesabıma, bu cihaz kaydolduktan sonra yeni bir cihaz bağlandı mı?
/// (Telefon numaramla başkası giriş yaptıysa fark edilsin diye.)
final newOwnDeviceProvider = FutureProvider<bool>((ref) async {
  final uid = ref.watch(myUidProvider);
  final store = ref.watch(signalStoreProvider);
  final ids = await ref.watch(myDeviceIdsProvider.future);
  final me = await ref.watch(thisDeviceIdProvider.future);
  if (uid == null || store == null || me == null || !ids.contains(me)) {
    return false;
  }
  await store.updateKnownDevices(uid, ids);
  return store.identityChanged(uid);
});

/// Bu cihaz başka bir cihazdan kaldırıldı mı? Sunucudan bir kez daha
/// doğrulanır (önbellekten gelen eski liste yanlış alarm vermesin).
final deviceRemovedProvider = FutureProvider<bool>((ref) async {
  final boot = ref.watch(e2eeBootstrapProvider);
  if (boot.error is DeviceRemovedException) return true;
  final uid = ref.watch(myUidProvider);
  final keys = ref.watch(keyManagerProvider);
  final ids = await ref.watch(myDeviceIdsProvider.future);
  if (uid == null || keys == null || !await keys.isRegistered()) return false;
  final me = await keys.deviceId();
  if (me == null || ids.contains(me)) return false;
  return !(await ref.read(keyDirectoryProvider).devices(uid)).contains(me);
});

/// Karşı tarafın kimlik anahtarı değişti mi (uyarı bandı için)?
final identityChangedProvider = FutureProvider.family<bool, String>((
  ref,
  uid,
) async {
  final store = ref.watch(signalStoreProvider);
  return store != null && await store.identityChanged(uid);
});
