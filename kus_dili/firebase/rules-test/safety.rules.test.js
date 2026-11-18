import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  addDoc,
  collection,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  writeBatch,
} from 'firebase/firestore';

let env;
const A = 'alice1';
const B = 'bobby2';
const C = 'carol3';
const CHAT = `d_${A}_${B}`;
const GROUP = 'g_abcdefghij12';

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-kusdili-safety',
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

const seedChat = () =>
  seed((s) =>
    setDoc(doc(s, 'chats', CHAT), {
      type: 'direct', members: [A, B], createdAt: new Date(), lastActivityAt: new Date(), readAt: {},
    }));

const seedGroup = () =>
  seed((s) =>
    setDoc(doc(s, 'chats', GROUP), {
      type: 'group', name: 'Kuşlar', members: [A, B, C], admins: [A], createdBy: A,
      createdAt: new Date(), lastActivityAt: new Date(), readAt: {},
    }));

const block = (d, owner, uids) =>
  setDoc(doc(d, 'blocks', owner), { uids, updatedAt: serverTimestamp() });

function send(d, sender, chat = CHAT, id = 'm1') {
  const b = writeBatch(d);
  b.set(doc(d, 'chats', chat, 'messages', id), {
    senderId: sender, sentAt: serverTimestamp(), encVersion: 1, body: '{"v":1}', deleted: false,
  });
  b.update(doc(d, 'chats', chat), {
    lastActivityAt: serverTimestamp(), lastSenderId: sender, [`readAt.${sender}`]: serverTimestamp(),
  });
  return b.commit();
}

describe('Engel listesi', () => {
  test('sahibi yazar ve okur', async () => {
    await assertSucceeds(block(db(A), A, [B]));
    await assertSucceeds(getDoc(doc(db(A), 'blocks', A)));
  });
  test('başkası okuyamaz ve yazamaz (engellenen listeyi göremez)', async () => {
    await seed((s) => setDoc(doc(s, 'blocks', A), { uids: [B], updatedAt: new Date() }));
    await assertFails(getDoc(doc(db(B), 'blocks', A)));
    await assertFails(block(db(B), A, []));
  });
  test('kendini engelleyemez, ek alan ve sahte zaman reddedilir', async () => {
    await assertFails(block(db(A), A, [A]));
    await assertFails(setDoc(doc(db(A), 'blocks', A), { uids: [B], updatedAt: serverTimestamp(), x: 1 }));
    await assertFails(setDoc(doc(db(A), 'blocks', A), { uids: [B], updatedAt: new Date(0) }));
  });
});

describe('Engellenince birebir mesaj', () => {
  test('engellenen kişi mesaj gönderemez', async () => {
    await seedChat();
    await seed((s) => setDoc(doc(s, 'blocks', A), { uids: [B], updatedAt: new Date() }));
    await assertFails(send(db(B), B));
  });
  test('engelleyen de mesaj gönderemez (iki yönlü)', async () => {
    await seedChat();
    await seed((s) => setDoc(doc(s, 'blocks', A), { uids: [B], updatedAt: new Date() }));
    await assertFails(send(db(A), A));
  });
  test('engel kaldırılınca yeniden gönderilir', async () => {
    await seedChat();
    await seed((s) => setDoc(doc(s, 'blocks', A), { uids: [], updatedAt: new Date() }));
    await assertSucceeds(send(db(B), B));
  });
  test('başka birini engellemek bu sohbeti etkilemez', async () => {
    await seedChat();
    await seed((s) => setDoc(doc(s, 'blocks', A), { uids: [C], updatedAt: new Date() }));
    await assertSucceeds(send(db(B), B));
  });
  test('engellenen kişi yeni sohbet açamaz', async () => {
    await seed((s) => setDoc(doc(s, 'blocks', A), { uids: [B], updatedAt: new Date() }));
    await assertFails(setDoc(doc(db(B), 'chats', CHAT), {
      type: 'direct', members: [A, B], createdAt: serverTimestamp(),
      lastActivityAt: serverTimestamp(), readAt: {},
    }));
  });
  test('gruplarda engel mesajı sunucuda durdurmaz (istemcide gizlenir)', async () => {
    await seedGroup();
    await seed((s) => setDoc(doc(s, 'blocks', A), { uids: [B], updatedAt: new Date() }));
    await assertSucceeds(send(db(B), B, GROUP));
  });
});

describe('Şikâyet', () => {
  const report = (d, extra = {}) =>
    addDoc(collection(d, 'reports'), {
      reporter: A, reported: B, chatId: CHAT, reason: 'spam',
      messages: [{ id: 'm1', senderId: B, text: 'satın al', sentAt: 1 }],
      createdAt: serverTimestamp(), ...extra,
    });

  test('sohbet üyesi karşı tarafı şikâyet eder', async () => {
    await seedChat();
    await assertSucceeds(report(db(A)));
    await assertSucceeds(report(db(A), { note: 'sürekli reklam', messages: [] }));
  });
  test('grubun kendisi şikâyet edilebilir', async () => {
    await seedGroup();
    await assertSucceeds(addDoc(collection(db(C), 'reports'), {
      reporter: C, chatId: GROUP, reason: 'inappropriate', messages: [], createdAt: serverTimestamp(),
    }));
  });
  test('kimse okuyamaz, değiştiremez', async () => {
    await seedChat();
    await seed((s) => setDoc(doc(s, 'reports', 'r1'), { reporter: A, chatId: CHAT }));
    await assertFails(getDoc(doc(db(A), 'reports', 'r1')));
    await assertFails(getDocs(collection(db(A), 'reports')));
    await assertFails(setDoc(doc(db(A), 'reports', 'r1'), { reporter: A }));
  });
  test('başkası adına, üye olmadığı sohbet için veya kendini şikâyet edemez', async () => {
    await seedChat();
    await assertFails(report(db(B)));
    await assertFails(addDoc(collection(db(C), 'reports'), {
      reporter: C, reported: B, chatId: CHAT, reason: 'spam', messages: [], createdAt: serverTimestamp(),
    }));
    await assertFails(report(db(A), { reported: A }));
    await assertFails(report(db(A), { reported: C }));
  });
  test('geçersiz neden, uzun not, 5\'ten fazla mesaj reddedilir', async () => {
    await seedChat();
    await assertFails(report(db(A), { reason: 'kötü' }));
    await assertFails(report(db(A), { note: 'x'.repeat(501) }));
    await assertFails(report(db(A), { messages: Array(6).fill({ id: 'm', senderId: B, text: 'a' }) }));
    await assertFails(report(db(A), { extra: true }));
  });
});
