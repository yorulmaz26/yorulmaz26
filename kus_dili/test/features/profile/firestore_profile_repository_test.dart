import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/profile/data/firestore_profile_repository.dart';
import 'package:kus_dili/features/profile/domain/user_profile.dart';

void main() {
  late FakeFirebaseFirestore db;
  late FirestoreProfileRepository repo;

  setUp(() {
    db = FakeFirebaseFirestore();
    repo = FirestoreProfileRepository(db);
  });

  test('Profil ve kullanıcı adı kilidi birlikte yazılır', () async {
    await repo.createProfile(
      uid: 'u1',
      username: 'ayse',
      displayName: ' Ayşe ',
    );
    final user = await db.doc('users/u1').get();
    expect(user.data()!['username'], 'ayse');
    expect(user.data()!['displayName'], 'Ayşe');
    expect(
      user.data()!.containsKey('phone'),
      isFalse,
      reason: 'telefon saklanmaz',
    );
    expect((await db.doc('usernames/ayse').get()).data(), {'uid': 'u1'});
  });

  test('Alınmış kullanıcı adı reddedilir, hiçbir şey yazılmaz', () async {
    await repo.createProfile(uid: 'u1', username: 'ayse', displayName: 'A');
    await expectLater(
      repo.createProfile(uid: 'u2', username: 'ayse', displayName: 'B'),
      throwsA(isA<UsernameTakenException>()),
    );
    expect((await db.doc('users/u2').get()).exists, isFalse);
    expect(await repo.isUsernameAvailable('ayse'), isFalse);
    expect(await repo.isUsernameAvailable('veli'), isTrue);
  });

  test('İkinci profil oluşturulamaz', () async {
    await repo.createProfile(uid: 'u1', username: 'ayse', displayName: 'A');
    await expectLater(
      repo.createProfile(uid: 'u1', username: 'ayse2', displayName: 'A'),
      throwsA(isA<ProfileAlreadyExistsException>()),
    );
    expect((await db.doc('usernames/ayse2').get()).exists, isFalse);
  });

  test('watchProfile ve findByUsername', () async {
    final stream = repo.watchProfile('u1');
    expect(await stream.first, isNull);
    await repo.createProfile(uid: 'u1', username: 'ayse', displayName: 'Ayşe');
    final p = await repo.watchProfile('u1').firstWhere((p) => p != null);
    expect(p!.username, 'ayse');
    expect((await repo.findByUsername('ayse'))?.uid, 'u1');
    expect(await repo.findByUsername('yok'), isNull);
  });
}
