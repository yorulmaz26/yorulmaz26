import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:kus_dili/features/crypto/data/firestore_group_directory.dart';
import 'package:kus_dili/features/crypto/data/firestore_key_directory.dart';
import 'package:kus_dili/features/crypto/data/persistent_signal_store.dart';
import 'package:kus_dili/features/crypto/domain/key_manager.dart';
import 'package:kus_dili/features/crypto/domain/signal_sealer.dart';

import 'in_memory.dart';

/// Bir kullanıcının cihazı: kendi güvenli deposu, önbelleği ve Signal
/// istemcisi. Sunucu (Firestore) diğer cihazlarla paylaşılır.
class SignalClient {
  SignalClient(this.uid, this.db, {InMemorySecureKv? kv})
    : kv = kv ?? InMemorySecureKv() {
    store = PersistentSignalStore(this.kv, uid);
    final dir = FirestoreKeyDirectory(db);
    keys = KeyManager(uid: uid, store: store, directory: dir);
    sealer = SignalSealer(
      myUid: uid,
      store: store,
      directory: dir,
      cache: cache,
      ensureKeys: keys.ensureKeys,
      groups: FirestoreGroupDirectory(db),
    );
  }

  final String uid;
  final FakeFirebaseFirestore db;
  final InMemorySecureKv kv;
  final cache = InMemoryPayloadCache();
  late final PersistentSignalStore store;
  late final KeyManager keys;
  late final SignalSealer sealer;

  /// Aynı cihazda uygulamanın yeniden başlatılması (depo korunur).
  SignalClient restart() =>
      SignalClient(uid, db, kv: kv)..cache.data.addAll(cache.data);
}
