/// Bildirim tercihleri (pushTokens/{uid}; yalnızca sahibi ve sunucu
/// fonksiyonu okur).
final class PushPrefs {
  const PushPrefs({
    this.enabled = true,
    this.muted = const {},
    this.tokens = const [],
  });

  final bool enabled;
  final Set<String> muted;
  final List<String> tokens;

  bool isMuted(String chatId) => muted.contains(chatId);
}

abstract interface class PushRepository {
  Stream<PushPrefs> watch(String uid);
  Future<void> addToken(String uid, String token);
  Future<void> removeToken(String uid, String token);
  Future<void> setEnabled(String uid, bool enabled);
  Future<void> setMuted(String uid, String chatId, bool muted);
}

/// Platform bildirim servisi (FCM). Testlerde sahtesi kullanılır.
abstract interface class PushMessaging {
  Future<bool> requestPermission();
  Future<String?> token();
  Stream<String> get tokenRefresh;

  /// Uygulama bir bildirime dokunularak açıldıysa ilgili sohbet.
  Future<String?> initialChatId();

  /// Uygulama açıkken / arka plandayken bildirime dokunuldu.
  Stream<String> get openedChatIds;

  Future<void> deleteToken();
}
