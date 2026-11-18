import 'package:cloud_firestore/cloud_firestore.dart';

import '../domain/user_profile.dart';

/// Firestore yerleşimi:
///   users/{uid}            { username, displayName, createdAt }
///   usernames/{username}   { uid }   ← benzersizlik kilidi
/// İkisi aynı işlemde (transaction) yazılır; güvenlik kuralları da
/// getAfter() ile ikisinin tutarlı olduğunu doğrular.
final class FirestoreProfileRepository implements ProfileRepository {
  FirestoreProfileRepository(this._db);

  final FirebaseFirestore _db;

  DocumentReference<Map<String, dynamic>> _user(String uid) =>
      _db.collection('users').doc(uid);

  DocumentReference<Map<String, dynamic>> _name(String username) =>
      _db.collection('usernames').doc(username);

  static UserProfile _fromSnap(DocumentSnapshot<Map<String, dynamic>> s) {
    final d = s.data()!;
    final created = d['createdAt'];
    return UserProfile(
      uid: s.id,
      username: d['username'] as String,
      displayName: d['displayName'] as String,
      createdAt: created is Timestamp ? created.toDate() : null,
    );
  }

  @override
  Stream<UserProfile?> watchProfile(String uid) =>
      _user(uid)
          .snapshots(includeMetadataChanges: true)
          // Çevrimdışıyken önbellekte olmaması "profil yok" demek değildir;
          // aksi hâlde kullanıcı yanlışlıkla profil oluşturma ekranına düşer.
          .where((s) => s.exists || !s.metadata.isFromCache)
          .map((s) => s.exists ? _fromSnap(s) : null);

  @override
  Future<bool> isUsernameAvailable(String username) async {
    final s = await _name(username)
        .get(const GetOptions(source: Source.server));
    return !s.exists;
  }

  @override
  Future<void> createProfile({
    required String uid,
    required String username,
    required String displayName,
  }) {
    return _db.runTransaction((tx) async {
      final nameSnap = await tx.get(_name(username));
      if (nameSnap.exists) throw UsernameTakenException(username);
      final userSnap = await tx.get(_user(uid));
      if (userSnap.exists) throw const ProfileAlreadyExistsException();
      tx
        ..set(_name(username), {'uid': uid})
        ..set(_user(uid), {
          'username': username,
          'displayName': displayName.trim(),
          'createdAt': FieldValue.serverTimestamp(),
        });
    });
  }

  @override
  Future<UserProfile?> findByUsername(String username) async {
    final n = await _name(username).get();
    final uid = n.data()?['uid'];
    if (uid is! String) return null;
    final u = await _user(uid).get();
    return u.exists ? _fromSnap(u) : null;
  }
}
