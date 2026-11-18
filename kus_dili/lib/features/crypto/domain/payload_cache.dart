/// Çözülmüş mesaj içeriklerinin cihazdaki önbelleği.
///
/// Signal Protokolü her mesajı yalnızca BİR KEZ çözebilir (anahtarlar
/// kullanıldıktan sonra silinir — ileriye dönük gizlilik). Bu yüzden çözülen
/// içerik ve kendi gönderdiğimiz mesajların içeriği burada, cihazda şifreli
/// olarak saklanır. Anahtar: mühürlü gövdenin SHA-256 özeti.
abstract interface class PayloadCache {
  Future<String?> get(String chatId, String bodyHash);
  Future<void> put(String chatId, String bodyHash, String payloadJson);
  Future<void> removeChat(String chatId);
}
