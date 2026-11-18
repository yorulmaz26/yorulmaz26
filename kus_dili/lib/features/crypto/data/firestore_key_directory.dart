import 'dart:convert';
import 'dart:typed_data';

import 'package:cloud_firestore/cloud_firestore.dart';

import '../domain/key_directory.dart';

/// keyBundles/{uid}            { registrationId, identityKey, signedPreKeyId,
///                               signedPreKey, signedPreKeySignature, updatedAt }
/// keyBundles/{uid}/prekeys/{id} { key }
/// Yalnızca AÇIK anahtarlar; özel anahtarlar cihazdan çıkmaz.
final class FirestoreKeyDirectory implements KeyDirectory {
  FirestoreKeyDirectory(this._db);

  final FirebaseFirestore _db;

  DocumentReference<Map<String, dynamic>> _bundle(String uid) =>
      _db.collection('keyBundles').doc(uid);

  CollectionReference<Map<String, dynamic>> _preKeys(String uid) =>
      _bundle(uid).collection('prekeys');

  @override
  Future<void> publishIdentity(String uid, PublishedIdentity i) =>
      _bundle(uid).set({
        'registrationId': i.registrationId,
        'identityKey': base64Encode(i.identityKey),
        'signedPreKeyId': i.signedPreKeyId,
        'signedPreKey': base64Encode(i.signedPreKey),
        'signedPreKeySignature': base64Encode(i.signedPreKeySignature),
        'updatedAt': FieldValue.serverTimestamp(),
      });

  @override
  Future<void> replacePreKeys(String uid, Map<int, Uint8List> preKeys) async {
    final old = await _preKeys(uid).get();
    final batch = _db.batch();
    for (final d in old.docs) {
      batch.delete(d.reference);
    }
    await batch.commit();
    await addPreKeys(uid, preKeys);
  }

  @override
  Future<void> addPreKeys(String uid, Map<int, Uint8List> preKeys) async {
    final entries = preKeys.entries.toList();
    for (var i = 0; i < entries.length; i += 400) {
      final batch = _db.batch();
      for (final e in entries.skip(i).take(400)) {
        batch.set(_preKeys(uid).doc('${e.key}'), {
          'key': base64Encode(e.value),
        });
      }
      await batch.commit();
    }
  }

  @override
  Future<int> countPreKeys(String uid) async =>
      (await _preKeys(uid).count().get()).count ?? 0;

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
  Future<PublishedIdentity?> fetchIdentity(String uid) async {
    final s = await _bundle(uid).get();
    return s.exists ? _identity(s.data()!) : null;
  }

  @override
  Future<ClaimedBundle?> claimBundle(String uid) async {
    final identity = await fetchIdentity(uid);
    if (identity == null) return null;
    // Birden fazla cihaz aynı anahtarı almaya çalışırsa yeniden dene.
    for (var attempt = 0; attempt < 3; attempt++) {
      final q = await _preKeys(uid).limit(1).get();
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
