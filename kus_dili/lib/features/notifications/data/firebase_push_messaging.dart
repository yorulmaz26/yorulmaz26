import 'package:firebase_messaging/firebase_messaging.dart';

import '../domain/push.dart';

// FCM belirteç (token) API'si 17.x'te "kurulum kimliği" (FID) modeli lehine
// eskimiş sayılıyor. Sunucu tarafı (Admin SDK) belirteçle çoklu gönderimi
// kararlı biçimde desteklediği için şimdilik bilerek kullanılıyor; FID ile
// doğrudan gönderim olgunlaşınca register()/onRegistered'a geçilecek.
// ignore_for_file: deprecated_member_use

final class FirebasePushMessaging implements PushMessaging {
  FirebasePushMessaging([FirebaseMessaging? m])
    : _m = m ?? FirebaseMessaging.instance;

  final FirebaseMessaging _m;

  static String? _chatOf(RemoteMessage m) {
    final id = m.data['chatId'];
    return id is String && id.isNotEmpty ? id : null;
  }

  @override
  Future<bool> requestPermission() async {
    final s = await _m.requestPermission();
    return s.authorizationStatus == AuthorizationStatus.authorized ||
        s.authorizationStatus == AuthorizationStatus.provisional;
  }

  @override
  Future<String?> token() => _m.getToken();

  @override
  Stream<String> get tokenRefresh => _m.onTokenRefresh;

  @override
  Future<String?> initialChatId() async {
    final m = await _m.getInitialMessage();
    return m == null ? null : _chatOf(m);
  }

  @override
  Stream<String> get openedChatIds => FirebaseMessaging.onMessageOpenedApp
      .map(_chatOf)
      .where((id) => id != null)
      .cast<String>();

  @override
  Future<void> deleteToken() => _m.deleteToken();
}
