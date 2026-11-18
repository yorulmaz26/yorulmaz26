import 'package:cloud_firestore/cloud_firestore.dart';

import '../domain/chat_settings.dart';

/// users/{uid}/chatSettings/{chatId} — yalnızca sahibi okur/yazar.
final class FirestoreChatSettingsRepository implements ChatSettingsRepository {
  FirestoreChatSettingsRepository(this._db);

  final FirebaseFirestore _db;

  DocumentReference<Map<String, dynamic>> _doc(String uid, String chatId) =>
      _db.collection('users').doc(uid).collection('chatSettings').doc(chatId);

  @override
  Stream<ChatSettings> watch({required String uid, required String chatId}) =>
      _doc(uid, chatId).snapshots().map(
        (s) =>
            s.exists ? ChatSettings.fromJson(s.data()!) : const ChatSettings(),
      );

  @override
  Future<void> save({
    required String uid,
    required String chatId,
    required ChatSettings settings,
  }) => _doc(uid, chatId).set(settings.toJson());
}
