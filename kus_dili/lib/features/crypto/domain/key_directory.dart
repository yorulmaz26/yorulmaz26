import 'dart:typed_data';

/// Bir cihazın sunucuda yayımlanan AÇIK anahtarları.
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

/// Hesaba bağlı bir cihaz (Cihazlar ekranı ve güvenlik numarası için).
final class DeviceInfo {
  const DeviceInfo({
    required this.deviceId,
    required this.identity,
    required this.name,
    this.lastSeen,
  });

  final int deviceId;
  final PublishedIdentity identity;
  final String name;
  final DateTime? lastSeen;
}

/// Oturum kurmak için alınan anahtar paketi (tek kullanımlık ön-anahtar
/// tükenmişse yalnızca imzalı ön-anahtarla kurulur).
final class ClaimedBundle {
  const ClaimedBundle(this.identity, {this.preKeyId, this.preKey});

  final PublishedIdentity identity;
  final int? preKeyId;
  final Uint8List? preKey;
}

/// Hesapta izin verilen en fazla cihaz (Firestore kuralıyla aynı).
const int maxDevices = 5;

/// Hesapta zaten [maxDevices] cihaz var.
final class TooManyDevicesException implements Exception {
  const TooManyDevicesException();
  String get message =>
      'Bu hesaba en fazla $maxDevices cihaz bağlanabilir. Cihazlar '
      'ekranından kullanmadığın bir cihazı kaldır.';
}

/// Açık anahtar dizini (Firestore: keyBundles/{uid}).
///
/// Her kullanıcının en fazla [maxDevices] cihazı vardır; her cihazın kendi
/// kimlik anahtarı, imzalı ön-anahtarı ve tek kullanımlık ön-anahtarları
/// olur. Mesajlar alıcının (ve gönderenin diğer) cihazlarının her birine
/// ayrı ayrı şifrelenir.
abstract interface class KeyDirectory {
  /// Etkin cihaz kimlikleri (sunucudan taze okunur).
  Future<List<int>> devices(String uid);

  Stream<List<int>> watchDevices(String uid);

  Future<List<DeviceInfo>> listDevices(String uid);

  /// Cihazı hesaba ekler (gerekirse) ve açık anahtarlarını yayımlar.
  /// Hesapta yer yoksa [TooManyDevicesException].
  Future<void> registerDevice(
    String uid,
    int deviceId,
    PublishedIdentity identity, {
    required String name,
  });

  /// Cihazı hesaptan kaldırır (anahtarları ve ön-anahtarlarıyla).
  Future<void> removeDevice(String uid, int deviceId);

  /// Mevcut tek kullanımlık ön-anahtarları silip yenilerini yazar.
  Future<void> replacePreKeys(
    String uid,
    int deviceId,
    Map<int, Uint8List> preKeys,
  );

  Future<void> addPreKeys(
    String uid,
    int deviceId,
    Map<int, Uint8List> preKeys,
  );

  Future<int> countPreKeys(String uid, int deviceId);

  Future<PublishedIdentity?> fetchIdentity(String uid, int deviceId);

  /// Bir ön-anahtarı atomik olarak tüketir.
  Future<ClaimedBundle?> claimBundle(String uid, int deviceId);
}
