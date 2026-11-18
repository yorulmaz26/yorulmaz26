/// Grup üyeleri ve gönderici anahtarı (sender key) paylaşımları.
///
/// Paylaşım = bir üyenin grup anahtarının, her diğer üye için ayrı ayrı
/// birebir Signal oturumuyla şifrelenmiş hâli. Sunucu içeriği göremez.
abstract interface class GroupDirectory {
  Future<List<String>> members(String chatId);

  /// chats/{chatId}/senderKeys/{sender}_{epoch}  (değiştirilemez)
  Future<void> publishSenderKey({
    required String chatId,
    required String sender,
    required int epoch,
    required Map<String, String> shares,
  });

  /// [sender]'ın [epoch] anahtarının [me] için şifrelenmiş paylaşımı.
  Future<String?> fetchShare({
    required String chatId,
    required String sender,
    required int epoch,
    required String me,
  });
}
