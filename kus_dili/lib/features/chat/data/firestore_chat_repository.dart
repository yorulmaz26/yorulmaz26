import 'package:cloud_firestore/cloud_firestore.dart';

import '../domain/chat.dart';

/// Firestore yerleşimi:
///   chats/{chatId}  { type, members[2], createdAt, lastActivityAt,
///                     lastSenderId, readAt{uid: ts} }   ← önizleme metni YOK
///   chats/{chatId}/messages/{id}
///                   { senderId, sentAt, encVersion, body, deleted, editedAt? }
final class FirestoreChatRepository implements ChatRepository {
  FirestoreChatRepository(this._db);

  final FirebaseFirestore _db;

  CollectionReference<Map<String, dynamic>> get _chats =>
      _db.collection('chats');

  CollectionReference<Map<String, dynamic>> _messages(String chatId) =>
      _chats.doc(chatId).collection('messages');

  static DateTime? _ts(Object? v) => v is Timestamp ? v.toDate() : null;

  static Chat _chat(DocumentSnapshot<Map<String, dynamic>> s) {
    final d = s.data()!;
    final read = <String, DateTime>{};
    final rawRead = d['readAt'];
    if (rawRead is Map) {
      rawRead.forEach((k, v) {
        final t = _ts(v);
        if (t != null) read[k.toString()] = t;
      });
    }
    return Chat(
      id: s.id,
      members: List<String>.from(d['members'] as List),
      lastActivityAt: _ts(d['lastActivityAt']),
      lastSenderId: d['lastSenderId'] as String?,
      readAt: read,
    );
  }

  static ChatMessage _message(DocumentSnapshot<Map<String, dynamic>> s) {
    final d = s.data()!;
    return ChatMessage(
      id: s.id,
      senderId: d['senderId'] as String,
      encVersion: (d['encVersion'] as num).toInt(),
      body: d['body'] as String? ?? '',
      sentAt: _ts(d['sentAt']),
      editedAt: _ts(d['editedAt']),
      deleted: d['deleted'] == true,
      pending: s.metadata.hasPendingWrites,
      expireAt: _ts(d['expireAt']),
      viewOnce: d['viewOnce'] == true,
    );
  }

  @override
  Stream<List<Chat>> watchChats(String uid) => _chats
      .where('members', arrayContains: uid)
      .orderBy('lastActivityAt', descending: true)
      .limit(200)
      .snapshots()
      .map((q) => q.docs.map(_chat).toList());

  @override
  Stream<Chat?> watchChat(String chatId) =>
      _chats.doc(chatId).snapshots().map((s) => s.exists ? _chat(s) : null);

  @override
  Stream<List<ChatMessage>> watchMessages(String chatId, {int limit = 100}) =>
      _messages(chatId)
          .orderBy('sentAt', descending: true)
          .limit(limit)
          .snapshots(includeMetadataChanges: true)
          .map((q) => q.docs.map(_message).toList().reversed.toList());

  @override
  Future<String> ensureDirectChat({
    required String me,
    required String other,
  }) async {
    final id = directChatId(me, other);
    final ref = _chats.doc(id);
    await _db.runTransaction((tx) async {
      final snap = await tx.get(ref);
      if (snap.exists) return;
      tx.set(ref, {
        'type': 'direct',
        'members': [me, other]..sort(),
        'createdAt': FieldValue.serverTimestamp(),
        'lastActivityAt': FieldValue.serverTimestamp(),
        'readAt': <String, Object?>{},
      });
    });
    return id;
  }

  @override
  String newMessageId(String chatId) => _messages(chatId).doc().id;

  @override
  Future<void> sendMessage({
    required String chatId,
    required String messageId,
    required String senderId,
    required int encVersion,
    required String body,
    DateTime? expireAt,
    bool viewOnce = false,
  }) {
    final batch = _db.batch()
      ..set(_messages(chatId).doc(messageId), {
        'senderId': senderId,
        'sentAt': FieldValue.serverTimestamp(),
        'encVersion': encVersion,
        'body': body,
        'deleted': false,
        if (expireAt != null) 'expireAt': Timestamp.fromDate(expireAt),
        if (viewOnce) 'viewOnce': true,
      })
      ..update(_chats.doc(chatId), {
        'lastActivityAt': FieldValue.serverTimestamp(),
        'lastSenderId': senderId,
        // Kendi mesajımı okumuş sayılırım.
        'readAt.$senderId': FieldValue.serverTimestamp(),
      });
    return batch.commit();
  }

  @override
  Future<void> consumeViewOnce({
    required String chatId,
    required String messageId,
  }) => _messages(chatId).doc(messageId).update({'deleted': true, 'body': ''});

  @override
  Future<void> editMessage({
    required String chatId,
    required String messageId,
    required String body,
  }) =>
      _messages(chatId)
          .doc(messageId)
          .update({'body': body, 'editedAt': FieldValue.serverTimestamp()});

  @override
  Future<void> deleteMessage({
    required String chatId,
    required String messageId,
  }) => _messages(chatId).doc(messageId).update({'deleted': true, 'body': ''});

  @override
  Future<void> markRead({required String chatId, required String uid}) =>
      _chats.doc(chatId).update({'readAt.$uid': FieldValue.serverTimestamp()});
}
