import 'dart:typed_data';

/// Bir kullanıcının sunucuda yayımlanan AÇIK anahtarları.
final class PublishedIdentity {
  const PublishedIdentity({
    required this.registrationId,
    required this.identityKey,
    required this.signedPreKeyId,
    required this.signedPreKey,
    required this.signedPreKeySignature,
  });

  final int registrationId;
  final Uint8List identityKey;
  final int signedPreKeyId;
  final Uint8List signedPreKey;
  final Uint8List signedPreKeySignature;
}

/// Oturum kurmak için alınan anahtar paketi (tek kullanımlık ön-anahtar
/// tükenmişse yalnızca imzalı ön-anahtarla kurulur).
final class ClaimedBundle {
  const ClaimedBundle(this.identity, {this.preKeyId, this.preKey});

  final PublishedIdentity identity;
  final int? preKeyId;
  final Uint8List? preKey;
}

/// Açık anahtar dizini (Firestore: keyBundles/{uid}).
abstract interface class KeyDirectory {
  Future<void> publishIdentity(String uid, PublishedIdentity identity);

  /// Mevcut tek kullanımlık ön-anahtarları silip yenilerini yazar.
  Future<void> replacePreKeys(String uid, Map<int, Uint8List> preKeys);

  Future<void> addPreKeys(String uid, Map<int, Uint8List> preKeys);

  Future<int> countPreKeys(String uid);

  Future<PublishedIdentity?> fetchIdentity(String uid);

  /// Bir ön-anahtarı atomik olarak tüketir.
  Future<ClaimedBundle?> claimBundle(String uid);
}
