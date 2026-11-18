import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getCountFromServer,
  getDoc,
  getDocs,
  limit,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
} from 'firebase/firestore';

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-kusdili-keys',
    firestore: {
      rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'),
    },
  });
});
after(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const db = (uid) =>
  env.authenticatedContext(uid, { firebase: { sign_in_provider: 'phone' } }).firestore();
const guest = () => env.unauthenticatedContext().firestore();

const key = 'BQ' + 'A'.repeat(42);
const bundle = (extra = {}) => ({
  registrationId: 1234,
  identityKey: key,
  signedPreKeyId: 1,
  signedPreKey: key,
  signedPreKeySignature: 'S'.repeat(88),
  updatedAt: serverTimestamp(),
  ...extra,
});

describe('Açık anahtar dizini', () => {
  test('sahibi kendi paketini yayımlar ve günceller', async () => {
    await assertSucceeds(setDoc(doc(db('a'), 'keyBundles', 'a'), bundle()));
    await assertSucceeds(setDoc(doc(db('a'), 'keyBundles', 'a'), bundle({ registrationId: 99 })));
  });

  test('başkası adına yayımlanamaz, silinemez', async () => {
    await assertFails(setDoc(doc(db('b'), 'keyBundles', 'a'), bundle()));
    await setDoc(doc(db('a'), 'keyBundles', 'a'), bundle());
    await assertFails(updateDoc(doc(db('b'), 'keyBundles', 'a'), { identityKey: key }));
    await assertFails(deleteDoc(doc(db('a'), 'keyBundles', 'a')));
  });

  test('özel anahtar gibi ek alanlar ve aşırı uzun değerler reddedilir', async () => {
    await assertFails(setDoc(doc(db('a'), 'keyBundles', 'a'), bundle({ privateKey: key })));
    await assertFails(setDoc(doc(db('a'), 'keyBundles', 'a'), bundle({ identityKey: 'x'.repeat(65) })));
    await assertFails(setDoc(doc(db('a'), 'keyBundles', 'a'), bundle({ updatedAt: new Date(0) })));
  });

  test('doğrulanmış kullanıcılar okur, oturumsuz okuyamaz', async () => {
    await setDoc(doc(db('a'), 'keyBundles', 'a'), bundle());
    await assertSucceeds(getDoc(doc(db('b'), 'keyBundles', 'a')));
    await assertFails(getDoc(doc(guest(), 'keyBundles', 'a')));
  });
});

describe('Tek kullanımlık ön-anahtarlar', () => {
  test('sahibi ekler; başkası ekleyemez; geçersiz kimlik/alan reddedilir', async () => {
    await assertSucceeds(setDoc(doc(db('a'), 'keyBundles/a/prekeys', '1'), { key }));
    await assertFails(setDoc(doc(db('b'), 'keyBundles/a/prekeys', '2'), { key }));
    await assertFails(setDoc(doc(db('a'), 'keyBundles/a/prekeys', 'abc'), { key }));
    await assertFails(setDoc(doc(db('a'), 'keyBundles/a/prekeys', '3'), { key, priv: key }));
  });

  test('başkası bir anahtarı tüketebilir (oturum kurmak için) ama değiştiremez', async () => {
    await setDoc(doc(db('a'), 'keyBundles/a/prekeys', '1'), { key });
    const q = query(collection(db('b'), 'keyBundles/a/prekeys'), limit(1));
    await assertSucceeds(getDocs(q));
    await assertSucceeds(getCountFromServer(collection(db('b'), 'keyBundles/a/prekeys')));
    await assertFails(updateDoc(doc(db('b'), 'keyBundles/a/prekeys', '1'), { key: 'X' }));
    await assertSucceeds(deleteDoc(doc(db('b'), 'keyBundles/a/prekeys', '1')));
    await assertFails(deleteDoc(doc(guest(), 'keyBundles/a/prekeys', '1')));
  });
});
