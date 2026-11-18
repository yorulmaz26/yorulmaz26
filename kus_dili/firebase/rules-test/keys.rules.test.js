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
  writeBatch,
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
  name: 'Android telefon',
  updatedAt: serverTimestamp(),
  ...extra,
});
const account = (devices) => ({ devices, updatedAt: serverTimestamp() });

/** Cihazı hesaba ekleyip anahtarlarını aynı işlemde yayımlar. */
function register(d, uid, devices, id, extra = {}) {
  const b = writeBatch(d);
  b.set(doc(d, 'keyBundles', uid), account(devices));
  b.set(doc(d, `keyBundles/${uid}/devices`, `${id}`), bundle(extra));
  return b.commit();
}

const seed = (fn) => env.withSecurityRulesDisabled((ctx) => fn(ctx.firestore()));

describe('Cihaz listesi', () => {
  test('sahibi cihazını ekler ve yayımlar; yeniden yayımlar', async () => {
    await assertSucceeds(register(db('a'), 'a', [11], 11));
    await assertSucceeds(setDoc(doc(db('a'), 'keyBundles/a/devices', '11'), bundle({ registrationId: 9 })));
    await assertSucceeds(register(db('a'), 'a', [11, 22], 22));
  });

  test('en fazla 5 cihaz', async () => {
    await assertSucceeds(register(db('a'), 'a', [1, 2, 3, 4, 5], 5));
    await assertFails(register(db('a'), 'a', [1, 2, 3, 4, 5, 6], 6));
  });

  test('listede olmayan cihaz yayımlanamaz', async () => {
    await assertSucceeds(register(db('a'), 'a', [11], 11));
    await assertFails(setDoc(doc(db('a'), 'keyBundles/a/devices', '99'), bundle()));
    await assertFails(setDoc(doc(db('a'), 'keyBundles/a/devices', 'abc'), bundle()));
  });

  test('başkası adına eklenemez, liste değiştirilemez', async () => {
    await assertFails(register(db('b'), 'a', [11], 11));
    await register(db('a'), 'a', [11], 11);
    await assertFails(setDoc(doc(db('b'), 'keyBundles', 'a'), account([11, 66])));
    await assertFails(updateDoc(doc(db('b'), 'keyBundles/a/devices', '11'), { identityKey: key }));
    await assertFails(deleteDoc(doc(db('a'), 'keyBundles', 'a')));
  });

  test('özel anahtar gibi ek alanlar, uzun değerler ve sahte zaman reddedilir', async () => {
    await assertFails(register(db('a'), 'a', [1], 1, { privateKey: key }));
    await assertFails(register(db('a'), 'a', [1], 1, { identityKey: 'x'.repeat(65) }));
    await assertFails(register(db('a'), 'a', [1], 1, { name: 'x'.repeat(41) }));
    await assertFails(register(db('a'), 'a', [1], 1, { updatedAt: new Date(0) }));
    await assertFails(setDoc(doc(db('a'), 'keyBundles', 'a'), { devices: [1], updatedAt: serverTimestamp(), x: 1 }));
  });

  test('cihaz kaldırma: önce listeden çıkar, sonra anahtarları sil', async () => {
    await register(db('a'), 'a', [11, 22], 22);
    await seed((s) => setDoc(doc(s, 'keyBundles/a/devices', '11'), { identityKey: key }));
    // Listede iken silinemez.
    await assertFails(deleteDoc(doc(db('a'), 'keyBundles/a/devices', '11')));
    await assertSucceeds(setDoc(doc(db('a'), 'keyBundles', 'a'), account([22])));
    await assertSucceeds(deleteDoc(doc(db('a'), 'keyBundles/a/devices', '11')));
    // Başkası silemez.
    await seed((s) => setDoc(doc(s, 'keyBundles/a/devices', '33'), { identityKey: key }));
    await assertFails(deleteDoc(doc(db('b'), 'keyBundles/a/devices', '33')));
  });

  test('doğrulanmış kullanıcılar okur, oturumsuz okuyamaz', async () => {
    await register(db('a'), 'a', [11], 11);
    await assertSucceeds(getDoc(doc(db('b'), 'keyBundles', 'a')));
    await assertSucceeds(getDoc(doc(db('b'), 'keyBundles/a/devices', '11')));
    await assertFails(getDoc(doc(guest(), 'keyBundles', 'a')));
    await assertFails(getDoc(doc(guest(), 'keyBundles/a/devices', '11')));
  });
});

describe('Tek kullanımlık ön-anahtarlar', () => {
  const pre = 'keyBundles/a/devices/11/prekeys';

  test('sahibi ekler; başkası ekleyemez; geçersiz kimlik/alan reddedilir', async () => {
    await assertSucceeds(setDoc(doc(db('a'), pre, '1'), { key }));
    await assertFails(setDoc(doc(db('b'), pre, '2'), { key }));
    await assertFails(setDoc(doc(db('a'), pre, 'abc'), { key }));
    await assertFails(setDoc(doc(db('a'), pre, '3'), { key, priv: key }));
  });

  test('başkası bir anahtarı tüketebilir (oturum kurmak için) ama değiştiremez', async () => {
    await setDoc(doc(db('a'), pre, '1'), { key });
    const q = query(collection(db('b'), pre), limit(1));
    await assertSucceeds(getDocs(q));
    await assertSucceeds(getCountFromServer(collection(db('b'), pre)));
    await assertFails(updateDoc(doc(db('b'), pre, '1'), { key: 'X' }));
    await assertSucceeds(deleteDoc(doc(db('b'), pre, '1')));
    await assertFails(deleteDoc(doc(guest(), pre, '1')));
  });
});
