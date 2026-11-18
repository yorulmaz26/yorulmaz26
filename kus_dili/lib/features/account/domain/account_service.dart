/// Hesabı kalıcı olarak siler (sunucu tarafı: `deleteAccount` fonksiyonu).
abstract interface class AccountService {
  Future<void> deleteAccount();
}
