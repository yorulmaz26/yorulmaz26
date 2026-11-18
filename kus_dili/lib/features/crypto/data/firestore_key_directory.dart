import 'dart:convert';
import 'dart:typed_data';

import 'package:cloud_firestore/cloud_firestore.dart';

import '../domain/key_directory.dart';

/// keyBundles/{uid}                          { devices: [int], updatedAt }
/// keyBundles/{uid}/devices/{id}             { registrationId, identityKey,
///                                             signedPreKeyId, signedPreKey,
///                                             signedPreKeySignature, name,
///                                             updatedAt }
/// keyBundles/{uid}/devices/{id}/prekeys/{n} { key }
/// Yalnızca AÇIK anahtarlar; özel anahtarlar cihazdan çıkmaz.
final class FirestoreKeyDirectory implements KeyDirectory {
  FirestoreKeyDirectory(this._db);

  final FirebaseFirestore _db;

  DocumentReference<Map<String, dynamic>> _account(String uid) =>
      _db.collection('keyBundles').doc(uid);

  DocumentReference<Map<String, dynamic>> _device(String uid, int id) =>
      _account(uid).collection('devices').doc('$id');

  CollectionReference<Map<String, dynamic>> _preKeys(String uid, int id) =>
      _device(uid, id).collection('prekeys');

  static List<int> _ids(Map<String, dynamic>? d) {
    final l = d?['devices'];
    return l is List ? [for (final x in l) (x as num).toInt()] : const [];
  }

  @override
  Future<List<int>> devices(String uid) async =>
      _ids((await _account(uid).get()).data());

  @override
  Stream<List<int>> watchDevices(String uid) =>
      _account(uid).snapshots().map((s) => _ids(s.data()));

  @override
  Future<List<DeviceInfo>> listDevices(String uid) async {
    final ids = await devices(uid);
    final out = <DeviceInfo>[];
    for (final id in ids) {
      final s = await _device(uid, id).get();
      final d = s.data();
      if (d == null) continue;
      final seen = d['updatedAt'];
      out.add(
        DeviceInfo(
          deviceId: id,
          identity: _identity(d),
          name: d['name'] as String? ?? 'Cihaz',
          lastSeen: seen is Timestamp ? seen.toDate() : null,
        ),
      );
    }
    return out;
  }

  @override
  Future<void> registerDevice(
    String uid,
    int deviceId,
    PublishedIdentity i, {
    required String name,
  }) async {
    final account = _account(uid);
    await _db.runTransaction((tx) async {
      final ids = _ids((await tx.get(account)).data());
      if (!ids.contains(deviceId)) {
        if (ids.length >= maxDevices) throw const TooManyDevicesException();
        tx.set(account, {
          'devices': [...ids, deviceId],
          'updatedAt': FieldValue.serverTimestamp(),
        });
      }
      tx.set(_device(uid, deviceId), {
        'registrationId': i.registrationId,
        'identityKey': base64Encode(i.identityKey),
        'signedPreKeyId': i.signedPreKeyId,
        'signedPreKey': base64Encode(i.signedPreKey),
        'signedPreKeySignature': base64Encode(i.signedPreKeySignature),
        'name': name,
        'updatedAt': FieldValue.serverTimestamp(),
      });
    });
  }

  @override
  Future<void> removeDevice(String uid, int deviceId) async {
    final account = _account(uid);
    await _db.runTransaction((tx) async {
      final ids = _ids((await tx.get(account)).data());
      tx.set(account, {
        'devices': [...ids.where((x) => x != deviceId)],
        'updatedAt': FieldValue.serverTimestamp(),
      });
    });
    // Liste güncellendikten sonra anahtarlar silinir: bu arada kimse yeni
    // oturum kurmaz.
    final old = await _preKeys(uid, deviceId).get();
    for (var i = 0; i < old.docs.length; i += 400) {
      final batch = _db.batch();
      for (final d in old.docs.skip(i).take(400)) {
        batch.delete(d.reference);
      }
      await batch.commit();
    }
    await _device(uid, deviceId).delete();
  }

  @override
  Future<void> replacePreKeys(
    String uid,
    int deviceId,
    Map<int, Uint8List> preKeys,
  ) async {
    final old = await _preKeys(uid, deviceId).get();
    for (var i = 0; i < old.docs.length; i += 400) {
      final batch = _db.batch();
      for (final d in old.docs.skip(i).take(400)) {
        batch.delete(d.reference);
      }
      await batch.commit();
    }
    await addPreKeys(uid, deviceId, preKeys);
  }

  @override
  Future<void> addPreKeys(
    String uid,
    int deviceId,
    Map<int, Uint8List> preKeys,
  ) async {
    final entries = preKeys.entries.toList();
    for (var i = 0; i < entries.length; i += 400) {
      final batch = _db.batch();
      for (final e in entries.skip(i).take(400)) {
        batch.set(_preKeys(uid, deviceId).doc('${e.key}'), {
          'key': base64Encode(e.value),
        });
      }
      await batch.commit();
    }
  }

  @override
  Future<int> countPreKeys(String uid, int deviceId) async =>
      (await _preKeys(uid, deviceId).count().get()).count ?? 0;

  static PublishedIdentity _identity(Map<String, dynamic> d) =>
      PublishedIdentity(
        registrationId: (d['registrationId'] as num).toInt(),
        identityKey: base64Decode(d['identityKey'] as String),
        signedPreKeyId: (d['signedPreKeyId'] as num).toInt(),
        signedPreKey: base64Decode(d['signedPreKey'] as String),
        signedPreKeySignature: base64Decode(
          d['signedPreKeySignature'] as String,
        ),
      );

  @override
  Future<PublishedIdentity?> fetchIdentity(String uid, int deviceId) async {
    final s = await _device(uid, deviceId).get();
    return s.exists ? _identity(s.data()!) : null;
  }

  @override
  Future<ClaimedBundle?> claimBundle(String uid, int deviceId) async {
    final identity = await fetchIdentity(uid, deviceId);
    if (identity == null) return null;
    // Birden fazla cihaz aynı anahtarı almaya çalışırsa yeniden dene.
    for (var attempt = 0; attempt < 3; attempt++) {
      final q = await _preKeys(uid, deviceId).limit(1).get();
      if (q.docs.isEmpty) break;
      final ref = q.docs.single.reference;
      final key = await _db.runTransaction<Uint8List?>((tx) async {
        final s = await tx.get(ref);
        if (!s.exists) return null;
        tx.delete(ref);
        return base64Decode(s.data()!['key'] as String);
      });
      if (key != null) {
        return ClaimedBundle(
          identity,
          preKeyId: int.parse(ref.id),
          preKey: key,
        );
      }
    }
    return ClaimedBundle(identity); // yalnızca imzalı ön-anahtarla
  }
}
