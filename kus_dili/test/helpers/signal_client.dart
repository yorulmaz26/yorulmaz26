import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:kus_dili/features/crypto/data/firestore_group_directory.dart';
import 'package:kus_dili/features/crypto/data/firestore_key_directory.dart';
import 'package:kus_dili/features/crypto/data/persistent_signal_store.dart';
import 'package:kus_dili/features/crypto/domain/key_manager.dart';
import 'package:kus_dili/features/crypto/domain/signal_sealer.dart';

import 'in_memory.dart';

/// Bir kullanıcının bir cihazı: kendi güvenli deposu, önbelleği ve Signal
/// istemcisi. Sunucu (Firestore) diğer cihazlarla paylaşılır. Aynı [uid] ile
/// birden fazla istemci = aynı hesabın farklı cihazları.
class SignalClient {
  SignalClient(this.uid, this.db, {InMemorySecureKv? kv, this.name = 'Test'})
    : kv = kv ?? InMemorySecureKv() {
    store = PersistentSignalStore(this.kv, uid);
    dir = FirestoreKeyDirectory(db);
    keys = KeyManager(uid: uid, store: store, directory: dir, deviceName: name);
    sealer = SignalSealer(
      myUid: uid,
      store: store,
      directory: dir,
      cache: cache,
      ensureKeys: keys.ensureKeys,
      groups: FirestoreGroupDirectory(db),
      // Testlerde cihaz listesi her seferinde taze okunur.
      deviceListTtl: Duration.zero,
    );
  }

  final String uid;
  final String name;
  final FakeFirebaseFirestore db;
  final InMemorySecureKv kv;
  final cache = InMemoryPayloadCache();
  late final PersistentSignalStore store;
  late final FirestoreKeyDirectory dir;
  late final KeyManager keys;
  late final SignalSealer sealer;

  Future<int> get deviceId async => (await keys.deviceId())!;

  /// Bu cihazın ön-anahtar koleksiyonu.
  Future<String> get preKeysPath async =>
      'keyBundles/$uid/devices/${await deviceId}/prekeys';

  /// Aynı cihazda uygulamanın yeniden başlatılması (depo korunur).
  SignalClient restart() =>
      SignalClient(uid, db, kv: kv, name: name)..cache.data.addAll(cache.data);
}
