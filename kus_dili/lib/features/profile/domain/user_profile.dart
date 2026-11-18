/// Herkese açık profil (users/{uid}). Telefon numarası burada TUTULMAZ.
final class UserProfile {
  const UserProfile({
    required this.uid,
    required this.username,
    required this.displayName,
    this.createdAt,
  });

  final String uid;
  final String username;
  final String displayName;
  final DateTime? createdAt;
}

final class UsernameTakenException implements Exception {
  const UsernameTakenException(this.username);
  final String username;
}

final class ProfileAlreadyExistsException implements Exception {
  const ProfileAlreadyExistsException();
}

abstract interface class ProfileRepository {
  /// Profil yoksa `null` yayar. Yalnızca sunucu tarafından doğrulanmış
  /// "yok" bilgisi `null` sayılır (önbellekteki boşluk sayılmaz).
  Stream<UserProfile?> watchProfile(String uid);

  Future<bool> isUsernameAvailable(String username);

  /// Kullanıcı adını atomik olarak ayırır ve profili oluşturur.
  /// Ad alınmışsa [UsernameTakenException].
  Future<void> createProfile({
    required String uid,
    required String username,
    required String displayName,
  });

  /// Kullanıcı adıyla arkadaş bulma.
  Future<UserProfile?> findByUsername(String username);
}
