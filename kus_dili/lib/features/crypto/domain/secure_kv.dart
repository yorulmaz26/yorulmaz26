/// Cihazdaki güvenli anahtar-değer deposu (Android Keystore / iOS Keychain).
/// Özel anahtarlar ve oturum durumları yalnızca burada tutulur; sunucuya
/// asla gönderilmez.
abstract interface class SecureKv {
  Future<String?> read(String key);
  Future<void> write(String key, String value);
  Future<void> delete(String key);

  /// Hesap silinirken: bu uygulamanın tüm güvenli verisini siler.
  Future<void> clearAll();
}
