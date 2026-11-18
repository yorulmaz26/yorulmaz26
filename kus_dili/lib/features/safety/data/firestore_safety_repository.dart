import 'package:cloud_firestore/cloud_firestore.dart';

import '../domain/report.dart';
import '../domain/safety_repository.dart';

/// blocks/{uid}   { uids: [..], updatedAt }
/// reports/{id}   { reporter, reported?, chatId, reason, note?, messages,
///                  createdAt }
final class FirestoreSafetyRepository implements SafetyRepository {
  FirestoreSafetyRepository(this._db);

  final FirebaseFirestore _db;

  DocumentReference<Map<String, dynamic>> _blocks(String uid) =>
      _db.collection('blocks').doc(uid);

  @override
  Stream<Set<String>> watchBlocked(String uid) =>
      _blocks(uid).snapshots().map((s) {
        final l = s.data()?['uids'];
        return l is List ? Set<String>.from(l) : <String>{};
      });

  @override
  Future<void> block({required String uid, required String other}) async {
    final ref = _blocks(uid);
    await _db.runTransaction((tx) async {
      final s = await tx.get(ref);
      final l = s.data()?['uids'];
      final cur = l is List ? List<String>.from(l) : <String>[];
      if (cur.contains(other)) return;
      if (cur.length >= maxBlocked) throw const TooManyBlockedException();
      tx.set(ref, {
        'uids': [...cur, other],
        'updatedAt': FieldValue.serverTimestamp(),
      });
    });
  }

  @override
  Future<void> unblock({required String uid, required String other}) =>
      _blocks(uid).set({
        'uids': FieldValue.arrayRemove([other]),
        'updatedAt': FieldValue.serverTimestamp(),
      }, SetOptions(merge: true));

  @override
  Future<void> report(Report report) => _db.collection('reports').add({
    ...report.toJson(),
    'createdAt': FieldValue.serverTimestamp(),
  });
}
