import 'package:cloud_firestore/cloud_firestore.dart';

import '../domain/push.dart';

final class FirestorePushRepository implements PushRepository {
  FirestorePushRepository(this._db);

  final FirebaseFirestore _db;

  DocumentReference<Map<String, dynamic>> _doc(String uid) =>
      _db.collection('pushTokens').doc(uid);

  @override
  Stream<PushPrefs> watch(String uid) => _doc(uid).snapshots().map((s) {
    final d = s.data() ?? const {};
    return PushPrefs(
      enabled: d['enabled'] != false,
      muted: {...?(d['muted'] as List?)?.cast<String>()},
      tokens: [...?(d['tokens'] as List?)?.cast<String>()],
    );
  });

  Future<void> _merge(String uid, Map<String, Object?> data) => _doc(uid).set({
    ...data,
    'updatedAt': FieldValue.serverTimestamp(),
  }, SetOptions(merge: true));

  @override
  Future<void> addToken(String uid, String token) => _merge(uid, {
    'tokens': FieldValue.arrayUnion([token]),
  });

  @override
  Future<void> removeToken(String uid, String token) => _merge(uid, {
    'tokens': FieldValue.arrayRemove([token]),
  });

  @override
  Future<void> setEnabled(String uid, bool enabled) =>
      _merge(uid, {'enabled': enabled});

  @override
  Future<void> setMuted(String uid, String chatId, bool muted) => _merge(uid, {
    'muted': muted
        ? FieldValue.arrayUnion([chatId])
        : FieldValue.arrayRemove([chatId]),
  });
}
