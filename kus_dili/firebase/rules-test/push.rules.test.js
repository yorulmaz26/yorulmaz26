import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { arrayUnion, doc, getDoc, serverTimestamp, setDoc } from 'firebase/firestore';

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-kusdili-push',
    firestore: {
      rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'),
    },
  });
});
after(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const db = (uid) =>
  env.authenticatedContext(uid, { firebase: { sign_in_provider: 'phone' } }).firestore();

describe('Bildirim belirteçleri', () => {
  test('sahibi yazar ve okur; başkası okuyamaz/yazamaz', async () => {
    await assertSucceeds(setDoc(doc(db('a'), 'pushTokens', 'a'),
      { tokens: arrayUnion('t1'), updatedAt: serverTimestamp() }, { merge: true }));
    await assertSucceeds(getDoc(doc(db('a'), 'pushTokens', 'a')));
    await assertFails(getDoc(doc(db('b'), 'pushTokens', 'a')));
    await assertFails(setDoc(doc(db('b'), 'pushTokens', 'a'),
      { tokens: ['x'], updatedAt: serverTimestamp() }));
  });

  test('geçersiz alanlar ve sınırlar', async () => {
    const r = doc(db('a'), 'pushTokens', 'a');
    await assertFails(setDoc(r, { tokens: Array(11).fill('t'), updatedAt: serverTimestamp() }));
    await assertFails(setDoc(r, { enabled: 'evet', updatedAt: serverTimestamp() }));
    await assertFails(setDoc(r, { tokens: [], extra: 1, updatedAt: serverTimestamp() }));
    await assertFails(setDoc(r, { tokens: [], updatedAt: new Date(0) }));
    await assertSucceeds(setDoc(r, { enabled: false, muted: ['d_a_b'], updatedAt: serverTimestamp() }));
  });
});
