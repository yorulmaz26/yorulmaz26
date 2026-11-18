import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  doc,
  getDoc,
  serverTimestamp,
  setDoc,
  updateDoc,
  deleteDoc,
  writeBatch,
} from 'firebase/firestore';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-kusdili-profile',
    firestore: {
      rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'),
    },
  });
});
after(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const phone = (uid) =>
  env.authenticatedContext(uid, { firebase: { sign_in_provider: 'phone' } }).firestore();
const anonymous = (uid) =>
  env.authenticatedContext(uid, { firebase: { sign_in_provider: 'anonymous' } }).firestore();
const guest = () => env.unauthenticatedContext().firestore();

function claim(db, uid, username, extra = {}) {
  const b = writeBatch(db);
  b.set(doc(db, 'usernames', username), { uid });
  b.set(doc(db, 'users', uid), {
    username,
    displayName: 'Ayşe',
    createdAt: serverTimestamp(),
    ...extra,
  });
  return b.commit();
}

async function seedProfile(uid, username) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'usernames', username), { uid });
    await setDoc(doc(db, 'users', uid), { username, displayName: 'X', createdAt: new Date() });
  });
}

describe('Profil oluşturma', () => {
  test('telefonla doğrulanmış kullanıcı kendi profilini oluşturur', async () => {
    await assertSucceeds(claim(phone('u1'), 'u1', 'ayse'));
  });

  test('anonim / oturumsuz kullanıcı oluşturamaz', async () => {
    await assertFails(claim(anonymous('u1'), 'u1', 'ayse'));
    await assertFails(claim(guest(), 'u1', 'ayse'));
  });

  test('başkası adına profil oluşturulamaz', async () => {
    await assertFails(claim(phone('u2'), 'u1', 'ayse'));
  });

  test('kullanıcı adı ayırmadan profil yazılamaz', async () => {
    const db = phone('u1');
    await assertFails(
      setDoc(doc(db, 'users', 'u1'), {
        username: 'ayse', displayName: 'A', createdAt: serverTimestamp(),
      }),
    );
  });

  test('profil olmadan kullanıcı adı ayrılamaz (ad stoklama yok)', async () => {
    await assertFails(setDoc(doc(phone('u1'), 'usernames', 'ayse'), { uid: 'u1' }));
  });

  test('alınmış kullanıcı adı alınamaz', async () => {
    await seedProfile('u1', 'ayse');
    await assertFails(claim(phone('u2'), 'u2', 'ayse'));
  });

  test('ikinci kullanıcı adı alınamaz', async () => {
    await seedProfile('u1', 'ayse');
    await assertFails(claim(phone('u1'), 'u1', 'ayse2'));
  });

  test('geçersiz kullanıcı adları reddedilir', async () => {
    for (const bad of ['al', '1ali', 'ali..veli', 'ali.', 'Ali', 'ayşe', 'admin',
      'abcdefghijklmnopqrstu']) {
      await assertFails(claim(phone('u1'), 'u1', bad));
    }
  });

  test('ek alan (ör. telefon) ve sahte createdAt reddedilir', async () => {
    await assertFails(claim(phone('u1'), 'u1', 'ayse', { phone: '+905321234567' }));
    await assertFails(claim(phone('u1'), 'u1', 'ayse', { createdAt: new Date(0) }));
    await assertFails(claim(phone('u1'), 'u1', 'ayse', { displayName: '' }));
    await assertFails(claim(phone('u1'), 'u1', 'ayse', { displayName: 'x'.repeat(41) }));
  });

  test('kilit başka uid gösteremez', async () => {
    const db = phone('u1');
    const b = writeBatch(db);
    b.set(doc(db, 'usernames', 'ayse'), { uid: 'u2' });
    b.set(doc(db, 'users', 'u1'), { username: 'ayse', displayName: 'A', createdAt: serverTimestamp() });
    await assertFails(b.commit());
  });
});

describe('Profil okuma / güncelleme', () => {
  beforeEach(() => seedProfile('u1', 'ayse'));

  test('doğrulanmış kullanıcılar profilleri ve adları okuyabilir', async () => {
    await assertSucceeds(getDoc(doc(phone('u2'), 'users', 'u1')));
    await assertSucceeds(getDoc(doc(phone('u2'), 'usernames', 'ayse')));
  });

  test('oturumsuz / anonim okuyamaz', async () => {
    await assertFails(getDoc(doc(guest(), 'users', 'u1')));
    await assertFails(getDoc(doc(anonymous('u2'), 'usernames', 'ayse')));
  });

  test('yalnızca görünen ad güncellenebilir', async () => {
    await assertSucceeds(updateDoc(doc(phone('u1'), 'users', 'u1'), { displayName: 'Ayşe K.' }));
    await assertFails(updateDoc(doc(phone('u1'), 'users', 'u1'), { username: 'baska' }));
    await assertFails(updateDoc(doc(phone('u2'), 'users', 'u1'), { displayName: 'Hack' }));
  });

  test('profil ve kullanıcı adı silinemez / devredilemez', async () => {
    await assertFails(deleteDoc(doc(phone('u1'), 'users', 'u1')));
    await assertFails(deleteDoc(doc(phone('u1'), 'usernames', 'ayse')));
    await assertFails(setDoc(doc(phone('u1'), 'usernames', 'ayse'), { uid: 'u2' }));
  });

  test('tanımsız koleksiyonlar kapalı', async () => {
    await assertFails(getDoc(doc(phone('u1'), 'chats', 'c1')));
    await assertFails(setDoc(doc(phone('u1'), 'anything', 'x'), { a: 1 }));
  });
});
