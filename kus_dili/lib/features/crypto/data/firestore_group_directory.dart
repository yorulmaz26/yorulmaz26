import 'package:cloud_firestore/cloud_firestore.dart';

import '../domain/group_directory.dart';

final class FirestoreGroupDirectory implements GroupDirectory {
  FirestoreGroupDirectory(this._db);

  final FirebaseFirestore _db;

  DocumentReference<Map<String, dynamic>> _chat(String id) =>
      _db.collection('chats').doc(id);

  @override
  Future<List<String>> members(String chatId) async {
    final s = await _chat(chatId).get();
    final m = s.data()?['members'];
    return m is List ? List<String>.from(m) : const [];
  }

  @override
  Future<void> publishSenderKey({
    required String chatId,
    required String sender,
    required int epoch,
    required Map<String, String> shares,
  }) => _chat(chatId).collection('senderKeys').doc('${sender}_$epoch').set({
    'sender': sender,
    'epoch': epoch,
    'shares': shares,
    'createdAt': FieldValue.serverTimestamp(),
  });

  @override
  Future<String?> fetchShare({
    required String chatId,
    required String sender,
    required int epoch,
    required String me,
  }) async {
    final s = await _chat(chatId)
        .collection('senderKeys')
        .doc('${sender}_$epoch')
        .get();
    final shares = s.data()?['shares'];
    final v = shares is Map ? shares[me] : null;
    return v is String ? v : null;
  }
}
