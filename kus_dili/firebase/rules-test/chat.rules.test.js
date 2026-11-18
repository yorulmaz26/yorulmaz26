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
  deleteField,
  doc,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from 'firebase/firestore';

let env;
const A = 'alice1';
const B = 'bobby2';
const C = 'carol3';
const CHAT = `d_${A}_${B}`;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-kusdili-chat',
    firestore: {
      rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'),
    },
  });
});
after(() => env.cleanup());

const db = (uid) =>
  env.authenticatedContext(uid, { firebase: { sign_in_provider: 'phone' } }).firestore();

async function seed(fn) {
  await env.withSecurityRulesDisabled((ctx) => fn(ctx.firestore()));
}

beforeEach(async () => {
  await env.clearFirestore();
  await seed(async (s) => {
    for (const [uid, name] of [[A, 'alice'], [B, 'bobby'], [C, 'carol']]) {
      await setDoc(doc(s, 'users', uid), { username: name, displayName: name, createdAt: new Date() });
    }
  });
});

function createChat(d, members = [A, B], id = CHAT, extra = {}) {
  return setDoc(doc(d, 'chats', id), {
    type: 'direct',
    members,
    createdAt: serverTimestamp(),
    lastActivityAt: serverTimestamp(),
    readAt: {},
    ...extra,
  });
}

async function seedChat() {
  await seed((s) =>
    setDoc(doc(s, 'chats', CHAT), {
      type: 'direct', members: [A, B], createdAt: new Date(), lastActivityAt: new Date(), readAt: {},
    }));
}

function send(d, sender, id = 'm1', extra = {}) {
  const b = writeBatch(d);
  b.set(doc(d, 'chats', CHAT, 'messages', id), {
    senderId: sender, sentAt: serverTimestamp(), encVersion: 0, body: '{"v":1}', deleted: false, ...extra,
  });
  b.update(doc(d, 'chats', CHAT), {
    lastActivityAt: serverTimestamp(), lastSenderId: sender, [`readAt.${sender}`]: serverTimestamp(),
  });
  return b.commit();
}

describe('Sohbet oluşturma', () => {
  test('üye, doğru kimlikle iki kişilik sohbet açar', async () => {
    await assertSucceeds(createChat(db(A)));
  });
  test('üye olmayan başkaları adına sohbet açamaz', async () => {
    await assertFails(createChat(db(C)));
  });
  test('kimlik üyelerle uyuşmalı ve üyeler sıralı olmalı', async () => {
    await assertFails(createChat(db(A), [A, B], `d_${B}_${A}`));
    await assertFails(createChat(db(A), [B, A], `d_${B}_${A}`));
    await assertFails(createChat(db(A), [A, B], 'rastgele'));
  });
  test('profili olmayan kişiyle sohbet açılamaz', async () => {
    await assertFails(createChat(db(A), [A, 'zzzz9'], `d_${A}_zzzz9`));
  });
  test('kendinle, 3 kişiyle veya ek alanla açılamaz', async () => {
    await assertFails(createChat(db(A), [A, A], `d_${A}_${A}`));
    await assertFails(createChat(db(A), [A, B, C], CHAT));
    await assertFails(createChat(db(A), [A, B], CHAT, { preview: 'selam' }));
  });
});

describe('Sohbet okuma', () => {
  test('olmayan kendi sohbetini kontrol edebilir, başkasınınkini değil', async () => {
    await assertSucceeds(getDoc(doc(db(A), 'chats', CHAT)));
    await assertFails(getDoc(doc(db(C), 'chats', CHAT)));
  });
  test('üyeler okur, üçüncü kişi okuyamaz', async () => {
    await seedChat();
    await assertSucceeds(getDoc(doc(db(B), 'chats', CHAT)));
    await assertFails(getDoc(doc(db(C), 'chats', CHAT)));
  });
  test('liste sorgusu yalnızca kendi sohbetleri için', async () => {
    await seedChat();
    const q = (d, uid) =>
      query(collection(d, 'chats'), where('members', 'array-contains', uid), orderBy('lastActivityAt', 'desc'));
    await assertSucceeds(getDocs(q(db(A), A)));
    await assertFails(getDocs(q(db(C), A)));
    await assertFails(getDocs(collection(db(A), 'chats')));
  });
});

describe('Mesajlar', () => {
  beforeEach(seedChat);

  test('üye mesaj gönderir; karşı taraf okur; yabancı okuyamaz', async () => {
    await assertSucceeds(send(db(A), A));
    const q = (d) => query(collection(d, 'chats', CHAT, 'messages'), orderBy('sentAt', 'desc'), limit(50));
    await assertSucceeds(getDocs(q(db(B))));
    await assertFails(getDocs(q(db(C))));
  });

  test('başkası adına, üye olmadan veya sahte zamanla gönderilemez', async () => {
    await assertFails(send(db(A), B));
    await assertFails(send(db(C), C));
    await assertFails(send(db(A), A, 'm2', { sentAt: new Date(0) }));
  });

  test('geçersiz gövde / sürüm / ek alan reddedilir', async () => {
    await assertFails(send(db(A), A, 'm3', { body: '' }));
    await assertFails(send(db(A), A, 'm4', { body: 'x'.repeat(65537) }));
    await assertFails(send(db(A), A, 'm5', { encVersion: 9 }));
    await assertFails(send(db(A), A, 'm6', { plaintext: 'selam' }));
    await assertFails(send(db(A), A, 'm7', { deleted: true }));
  });

  test('gönderen düzenler ve herkesten siler; diğerleri yapamaz', async () => {
    await send(db(A), A);
    const m = (d) => doc(d, 'chats', CHAT, 'messages', 'm1');
    await assertFails(updateDoc(m(db(B)), { body: 'hack', editedAt: serverTimestamp() }));
    await assertFails(updateDoc(m(db(A)), { body: 'yeni', editedAt: new Date(0) }));
    await assertSucceeds(updateDoc(m(db(A)), { body: 'yeni', editedAt: serverTimestamp() }));
    await assertFails(updateDoc(m(db(A)), { deleted: true }));
    await assertFails(updateDoc(m(db(B)), { deleted: true, body: '' }));
    await assertSucceeds(updateDoc(m(db(A)), { deleted: true, body: '' }));
    await assertFails(updateDoc(m(db(A)), { body: 'geri', editedAt: serverTimestamp() }));
    await assertFails(deleteDoc(m(db(A))));
  });
});

describe('Okundu bilgisi ve sohbet güncelleme', () => {
  beforeEach(seedChat);

  test('üye yalnızca kendi okuma zamanını sunucu zamanıyla yazar', async () => {
    await assertSucceeds(updateDoc(doc(db(B), 'chats', CHAT), { [`readAt.${B}`]: serverTimestamp() }));
    await assertFails(updateDoc(doc(db(B), 'chats', CHAT), { [`readAt.${A}`]: serverTimestamp() }));
    await assertFails(updateDoc(doc(db(B), 'chats', CHAT), { [`readAt.${B}`]: new Date(0) }));
    await assertFails(updateDoc(doc(db(C), 'chats', CHAT), { [`readAt.${C}`]: serverTimestamp() }));
  });

  test('üyeler, tür ve son gönderen değiştirilemez; silinemez', async () => {
    const ref = doc(db(A), 'chats', CHAT);
    await assertFails(updateDoc(ref, { members: [A, C] }));
    await assertFails(updateDoc(ref, { type: 'group' }));
    await assertFails(updateDoc(ref, { lastActivityAt: serverTimestamp(), lastSenderId: B }));
    await seed((x) => updateDoc(doc(x, 'chats', CHAT), { [`readAt.${A}`]: new Date() }));
    await assertFails(updateDoc(ref, { [`readAt.${A}`]: deleteField() }));
    await assertFails(deleteDoc(ref));
  });
});

describe('Sohbet ayarları', () => {
  const s = (d, uid) => doc(d, 'users', uid, 'chatSettings', CHAT);
  const ok = { sendMode: 'k', variant: { id: 'g', name: "G'li", template: 'g{v}' }, viewMode: 'asSent' };

  test('yalnızca sahibi okur ve yazar', async () => {
    await assertSucceeds(setDoc(s(db(A), A), ok));
    await assertSucceeds(getDoc(s(db(A), A)));
    await assertFails(getDoc(s(db(B), A)));
    await assertFails(setDoc(s(db(B), A), ok));
  });

  test('geçersiz değerler reddedilir', async () => {
    await assertFails(setDoc(s(db(A), A), { ...ok, sendMode: 'x' }));
    await assertFails(setDoc(s(db(A), A), { ...ok, viewMode: 'x' }));
    await assertFails(setDoc(s(db(A), A), { ...ok, extra: 1 }));
  });
});
