import 'message_payload.dart';

/// Mesaj paketini sunucuya yazılacak biçime çevirir ve geri açar.
///
/// Aşama 7'de bunun yerini Signal Protokolü tabanlı bir uygulama alacak
/// (`encVersion` 1). Uygulamanın geri kalanı yalnızca bu arayüzü bilir; bu
/// sayede şifreleme eklendiğinde ekranlar ve depolar değişmez.
abstract interface class MessageSealer {
  int get encVersion;

  Future<String> seal(String chatId, MessagePayload payload);

  /// [senderId]: mesajı gönderen (şifre çözmede karşı tarafın adresi).
  Future<MessagePayload> open(
    String chatId,
    String senderId,
    int encVersion,
    String body,
  );

  /// Bu cihazdaki çözülmüş kopyayı siler (tek görüntülük mesajlar).
  Future<void> forget(String chatId, String body);
}

final class UnsupportedEncryptionException implements Exception {
  const UnsupportedEncryptionException(this.encVersion);
  final int encVersion;
}

/// ⚠️ GELİŞTİRME SÜRÜMÜ: şifreleme YOK. Paket JSON olarak düz metin yazılır
/// ve Firebase tarafından okunabilir. Yalnızca Aşama 4-6 geliştirmesi için;
/// Aşama 7 tamamlanmadan uygulama yayınlanmamalıdır.
final class PlaintextDevSealer implements MessageSealer {
  const PlaintextDevSealer();

  @override
  int get encVersion => 0;

  @override
  Future<String> seal(String chatId, MessagePayload payload) async =>
      payload.encode();

  @override
  Future<MessagePayload> open(
    String chatId,
    String senderId,
    int encVersion,
    String body,
  ) async {
    if (encVersion != 0) throw UnsupportedEncryptionException(encVersion);
    return MessagePayload.decode(body);
  }

  @override
  Future<void> forget(String chatId, String body) async {}
}
