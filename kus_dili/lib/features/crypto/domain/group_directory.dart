/// Grup üyeleri ve gönderici anahtarı (sender key) paylaşımları.
///
/// Paylaşım = bir cihazın grup anahtarının, diğer her cihaz için (diğer
/// üyelerin ve gönderenin kendi diğer cihazlarının) ayrı ayrı birebir Signal
/// oturumuyla şifrelenmiş hâli. Sunucu içeriği göremez.
abstract interface class GroupDirectory {
  Future<List<String>> members(String chatId);

  /// chats/{chatId}/senderKeys/{sender}_{device}_{epoch}  (değiştirilemez)
  /// [shares] anahtarları `uid:cihaz` biçimindedir.
  Future<void> publishSenderKey({
    required String chatId,
    required String sender,
    required int device,
    required int epoch,
    required Map<String, String> shares,
  });

  /// [sender]/[device]'ın [epoch] anahtarının [me] (`uid:cihaz`) için
  /// şifrelenmiş paylaşımı.
  Future<String?> fetchShare({
    required String chatId,
    required String sender,
    required int device,
    required int epoch,
    required String me,
  });
}
