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
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
} from 'firebase/firestore';

let env;
const A = 'alice1';
const B = 'bobby2';
const C = 'carol3';
const CHAT = `d_${A}_${B}`;
const CALL = 'call1';

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-kusdili-calls',
    firestore: {
      rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'),
    },
  });
});
after(() => env.cleanup());

const db = (uid) =>
  env.authenticatedContext(uid, { firebase: { sign_in_provider: 'phone' } }).firestore();
const seed = (fn) => env.withSecurityRulesDisabled((ctx) => fn(ctx.firestore()));

beforeEach(async () => {
  await env.clearFirestore();
  await seed(async (s) => {
    await setDoc(doc(s, 'chats', CHAT), {
      type: 'direct', members: [A, B], createdAt: new Date(), lastActivityAt: new Date(), readAt: {},
    });
    await setDoc(doc(s, 'chats', 'g_grup000000001'), {
      type: 'group', members: [A, B, C], admins: [A], name: 'G', createdBy: A,
      createdAt: new Date(), lastActivityAt: new Date(), readAt: {},
    });
  });
});

const call = (extra = {}) => ({
  chatId: CHAT, caller: A, callee: B, video: true, state: 'ringing',
  createdAt: serverTimestamp(), ...extra,
});
const seedCall = (state = 'ringing') =>
  seed((s) => setDoc(doc(s, 'calls', CALL), { ...call(), state, createdAt: new Date() }));

describe('Arama oluşturma', () => {
  test('sohbet üyesi diğer üyeyi arar', async () => {
    await assertSucceeds(setDoc(doc(db(A), 'calls', CALL), call()));
  });
  test('geçersiz aramalar reddedilir', async () => {
    await assertFails(setDoc(doc(db(C), 'calls', CALL), call({ caller: C })), 'üye değil');
    await assertFails(setDoc(doc(db(A), 'calls', CALL), call({ callee: C })), 'aranan üye değil');
    await assertFails(setDoc(doc(db(A), 'calls', CALL), call({ caller: B })), 'başkası adına');
    await assertFails(setDoc(doc(db(A), 'calls', CALL), call({ state: 'accepted' })));
    await assertFails(setDoc(doc(db(A), 'calls', CALL), call({ chatId: 'g_grup000000001' })),
      'grup araması yok');
    await assertFails(setDoc(doc(db(A), 'calls', CALL), call({ sdp: 'v=0' })));
  });
});

describe('Arama durumu', () => {
  test('aranan kabul eder; üçüncü kişi okuyamaz/değiştiremez', async () => {
    await seedCall();
    await assertFails(getDoc(doc(db(C), 'calls', CALL)));
    await assertFails(updateDoc(doc(db(A), 'calls', CALL),
      { state: 'accepted', answeredAt: serverTimestamp() }), 'arayan kabul edemez');
    await assertSucceeds(updateDoc(doc(db(B), 'calls', CALL),
      { state: 'accepted', answeredAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(db(A), 'calls', CALL),
      { state: 'ended', endedAt: serverTimestamp(), endedBy: A }));
    await assertFails(updateDoc(doc(db(B), 'calls', CALL),
      { state: 'accepted', answeredAt: serverTimestamp() }), 'biten arama değişmez');
  });

  test('reddetme / meşgul yalnızca aranan; cevapsız yalnızca arayan', async () => {
    await seedCall();
    const end = (uid, state) => updateDoc(doc(db(uid), 'calls', CALL),
      { state, endedAt: serverTimestamp(), endedBy: uid });
    await assertFails(end(A, 'declined'));
    await assertFails(end(B, 'missed'));
    await assertFails(updateDoc(doc(db(B), 'calls', CALL),
      { state: 'declined', endedAt: serverTimestamp(), endedBy: A }), 'endedBy kendisi olmalı');
    await assertSucceeds(end(B, 'busy'));
  });

  test('gelen arama sorgusu yalnızca kendine', async () => {
    await seedCall();
    const q = (uid, who) => query(collection(db(uid), 'calls'),
      where('callee', '==', who), where('state', '==', 'ringing'));
    await assertSucceeds(getDocs(q(B, B)));
    await assertFails(getDocs(q(C, B)));
  });
});

describe('Şifreli sinyaller', () => {
  const sig = (from, to, extra = {}) => ({
    from, to, seq: 0, body: '{"t":3,"c":"..."}', createdAt: serverTimestamp(), ...extra,
  });

  test('taraflar birbirine yazar ve kendininkini okur', async () => {
    await seedCall();
    const ref = await addDoc(collection(db(A), 'calls', CALL, 'signals'), sig(A, B));
    await assertSucceeds(getDoc(doc(db(B), 'calls', CALL, 'signals', ref.id)));
    await assertFails(getDoc(doc(db(C), 'calls', CALL, 'signals', ref.id)));
    await assertFails(updateDoc(doc(db(A), 'calls', CALL, 'signals', ref.id), { body: 'x' }));
  });

  test('sahte gönderen, yabancı, kendine, çok büyük, bitmiş arama reddedilir', async () => {
    await seedCall();
    const col = (uid) => collection(db(uid), 'calls', CALL, 'signals');
    await assertFails(addDoc(col(A), sig(B, A)));
    await assertFails(addDoc(col(C), sig(C, A)));
    await assertFails(addDoc(col(A), sig(A, A)));
    await assertFails(addDoc(col(A), sig(A, B, { body: 'x'.repeat(16385) })));
    await seedCall('ended');
    await assertFails(addDoc(col(A), sig(A, B)));
  });
});
