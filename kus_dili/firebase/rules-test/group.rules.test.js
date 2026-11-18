import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  arrayRemove,
  arrayUnion,
  collection,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  updateDoc,
  writeBatch,
} from 'firebase/firestore';

let env;
const G = 'g_abcdefghij0123';
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-kusdili-group',
    firestore: {
      rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'),
    },
  });
});
after(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const db = (uid) =>
  env.authenticatedContext(uid, { firebase: { sign_in_provider: 'phone' } }).firestore();

const group = (extra = {}) => ({
  type: 'group',
  name: 'Kuşlar',
  members: ['a', 'b', 'c'],
  admins: ['a'],
  createdBy: 'a',
  createdAt: serverTimestamp(),
  lastActivityAt: serverTimestamp(),
  readAt: {},
  ...extra,
});

async function seedGroup(extra = {}) {
  await env.withSecurityRulesDisabled((ctx) =>
    setDoc(doc(ctx.firestore(), 'chats', G), {
      ...group(), createdAt: new Date(), lastActivityAt: new Date(), ...extra,
    }));
}

describe('Grup oluşturma', () => {
  test('kurucu tek yönetici olarak oluşturur', async () => {
    await assertSucceeds(setDoc(doc(db('a'), 'chats', G), group()));
  });
  test('geçersiz oluşturmalar reddedilir', async () => {
    await assertFails(setDoc(doc(db('a'), 'chats', G), group({ admins: ['a', 'b'] })));
    await assertFails(setDoc(doc(db('z'), 'chats', G), group({ createdBy: 'z', admins: ['z'] })),
      'kurucu üye olmalı');
    await assertFails(setDoc(doc(db('a'), 'chats', G), group({ name: '' })));
    await assertFails(setDoc(doc(db('a'), 'chats', G), group({ members: ['a'] })));
    await assertFails(setDoc(doc(db('a'), 'chats', 'g_x'), group()), 'kimlik biçimi');
    await assertFails(setDoc(doc(db('a'), 'chats', G),
      group({ members: Array.from({ length: 65 }, (_, i) => i === 0 ? 'a' : `u${i}`) })));
    await assertFails(setDoc(doc(db('a'), 'chats', G), group({ preview: 'x' })));
  });
});

describe('Grup yönetimi', () => {
  beforeEach(() => seedGroup());

  test('yönetici ad değiştirir, üye ekler/çıkarır, yönetici atar', async () => {
    const r = doc(db('a'), 'chats', G);
    await assertSucceeds(updateDoc(r, { name: 'Yeni ad' }));
    await assertSucceeds(updateDoc(r, { members: arrayUnion('d') }));
    await assertSucceeds(updateDoc(r, { members: arrayRemove('c'), admins: arrayRemove('c') }));
    await assertSucceeds(updateDoc(r, { admins: arrayUnion('b') }));
  });

  test('yönetici olmayan yönetemez', async () => {
    const r = doc(db('b'), 'chats', G);
    await assertFails(updateDoc(r, { name: 'Hack' }));
    await assertFails(updateDoc(r, { members: arrayUnion('z') }));
    await assertFails(updateDoc(r, { admins: arrayUnion('b') }));
  });

  test('yönetici üye olmayanı yönetici yapamaz, son yöneticiyi kaldıramaz', async () => {
    const r = doc(db('a'), 'chats', G);
    await assertFails(updateDoc(r, { admins: arrayUnion('z') }));
    await assertFails(updateDoc(r, { admins: [] }));
    await assertFails(updateDoc(r, { type: 'direct' }));
  });

  test('üye ayrılabilir ama başkasını çıkaramaz', async () => {
    await assertFails(updateDoc(doc(db('b'), 'chats', G), { members: arrayRemove('c') }));
    await assertSucceeds(updateDoc(doc(db('b'), 'chats', G), { members: arrayRemove('b') }));
    await assertFails(getDoc(doc(db('b'), 'chats', G)), 'ayrılan artık okuyamaz');
  });

  test('son yönetici ayrılırken yerine bir üye geçmeli', async () => {
    await assertFails(updateDoc(doc(db('a'), 'chats', G), {
      members: arrayRemove('a'), admins: arrayRemove('a'),
    }));
    await assertFails(updateDoc(doc(db('a'), 'chats', G), {
      members: arrayRemove('a'), admins: ['z'],
    }));
    await assertSucceeds(updateDoc(doc(db('a'), 'chats', G), {
      members: arrayRemove('a'), admins: ['b'],
    }));
  });
});

describe('Grup mesajları ve anahtar paylaşımları', () => {
  beforeEach(() => seedGroup());

  test('üyeler mesaj gönderir ve okur; üye olmayan okuyamaz', async () => {
    const fb = db('b');
    const b = writeBatch(fb);
    b.set(doc(fb, 'chats', G, 'messages', 'm1'), {
      senderId: 'b', sentAt: serverTimestamp(), encVersion: 1, body: '{"t":"sk"}', deleted: false,
    });
    b.update(doc(fb, 'chats', G), {
      lastActivityAt: serverTimestamp(), lastSenderId: 'b', 'readAt.b': serverTimestamp(),
    });
    await assertSucceeds(b.commit());
    await assertSucceeds(getDocs(collection(db('c'), 'chats', G, 'messages')));
    await assertFails(getDocs(collection(db('z'), 'chats', G, 'messages')));
  });

  test('paylaşım: yalnızca kendi adına, cihaz ve dönemle, değiştirilemez', async () => {
    const ok = {
      sender: 'a', device: 7, epoch: 42,
      shares: { 'b:3': 'x', 'c:9': 'y', 'a:8': 'z' }, createdAt: serverTimestamp(),
    };
    const sk = (u, id) => doc(db(u), 'chats', G, 'senderKeys', id);
    await assertSucceeds(setDoc(sk('a', 'a_7_42'), ok));
    await assertFails(setDoc(sk('a', 'a_7_42'), ok), 'değiştirilemez');
    await assertFails(setDoc(sk('a', 'b_7_43'), { ...ok, sender: 'b', epoch: 43 }));
    await assertFails(setDoc(sk('a', 'a_7_99'), { ...ok, epoch: 45 }), 'kimlik dönemle uyuşmalı');
    await assertFails(setDoc(sk('a', 'a_8_46'), { ...ok, epoch: 46 }), 'kimlik cihazla uyuşmalı');
    await assertFails(setDoc(sk('a', 'a_7_47'), { ...ok, epoch: 47, device: '7' }));
    const many = Object.fromEntries(Array.from({ length: 321 }, (_, i) => [`b:${i}`, 'x']));
    await assertFails(setDoc(sk('a', 'a_7_48'), { ...ok, epoch: 48, shares: many }), 'en fazla 320');
    await assertFails(setDoc(sk('z', 'z_1_1'), { ...ok, sender: 'z', device: 1, epoch: 1 }));
    await assertSucceeds(getDoc(sk('c', 'a_7_42')));
    await assertFails(getDoc(sk('z', 'a_7_42')));
  });
});
