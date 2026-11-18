/// İki kişilik sohbetin belge kimliği: üyeler sıralanır, böylece aynı iki
/// kişi için her zaman tek sohbet olur. Güvenlik kuralları da bunu doğrular.
String directChatId(String a, String b) {
  if (a == b) throw ArgumentError('Kendinle sohbet başlatılamaz.');
  final s = [a, b]..sort();
  return 'd_${s[0]}_${s[1]}';
}

final class Chat {
  const Chat({
    required this.id,
    required this.members,
    this.lastActivityAt,
    this.lastSenderId,
    this.readAt = const {},
  });

  final String id;
  final List<String> members;
  final DateTime? lastActivityAt;
  final String? lastSenderId;

  /// Üye → son okuma zamanı.
  final Map<String, DateTime> readAt;

  String otherMember(String me) =>
      members.firstWhere((m) => m != me, orElse: () => me);

  /// Son mesaj başkasındansa ve ben o zamandan sonra okumadıysam.
  bool isUnreadFor(String me) {
    final last = lastActivityAt;
    if (last == null || lastSenderId == null || lastSenderId == me) {
      return false;
    }
    final read = readAt[me];
    return read == null || read.isBefore(last);
  }
}

/// Sunucudaki ham (mühürlü) mesaj.
final class ChatMessage {
  const ChatMessage({
    required this.id,
    required this.senderId,
    required this.encVersion,
    required this.body,
    this.sentAt,
    this.editedAt,
    this.deleted = false,
    this.pending = false,
  });

  final String id;
  final String senderId;
  final int encVersion;
  final String body;

  /// Sunucu zamanı; henüz sunucuya ulaşmamışsa `null`.
  final DateTime? sentAt;
  final DateTime? editedAt;
  final bool deleted;

  /// Yerel yazma henüz sunucuya ulaşmadı.
  final bool pending;

  /// Karşı taraf okudu mu?
  bool isReadBy(String uid, Chat chat) {
    final read = chat.readAt[uid];
    final sent = sentAt;
    return read != null && sent != null && !read.isBefore(sent);
  }
}

abstract interface class ChatRepository {
  /// Kullanıcının sohbetleri, son etkinliğe göre (yeni → eski).
  Stream<List<Chat>> watchChats(String uid);

  Stream<Chat?> watchChat(String chatId);

  /// Son [limit] mesaj, eskiden yeniye.
  Stream<List<ChatMessage>> watchMessages(String chatId, {int limit = 100});

  /// İki kişilik sohbeti yoksa oluşturur; kimliğini döner.
  Future<String> ensureDirectChat({required String me, required String other});

  /// Yeni mesaj kimliği (istemci tarafında; tekrar denemede çift mesaj olmaz).
  String newMessageId(String chatId);

  Future<void> sendMessage({
    required String chatId,
    required String messageId,
    required String senderId,
    required int encVersion,
    required String body,
  });

  Future<void> editMessage({
    required String chatId,
    required String messageId,
    required String body,
  });

  /// Herkesten sil: içerik sunucudan kaldırılır, yalnızca iz kalır.
  Future<void> deleteMessage({
    required String chatId,
    required String messageId,
  });

  Future<void> markRead({required String chatId, required String uid});
}
