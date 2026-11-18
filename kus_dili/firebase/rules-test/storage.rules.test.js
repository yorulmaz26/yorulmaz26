import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { doc, setDoc } from 'firebase/firestore';
import {
  deleteObject,
  getBytes,
  listAll,
  ref,
  uploadBytes,
} from 'firebase/storage';

let env;
const CHAT = 'd_alice1_bobby2';
const FILE = 'a'.repeat(32);
let n = 0;
let current = FILE;
const path = (id = current, chat = CHAT) => `attachments/${chat}/${id}`;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-kusdili',
    firestore: {
      rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'),
    },
    storage: {
      rules: readFileSync(new URL('../storage.rules', import.meta.url), 'utf8'),
    },
  });
});
after(() => env.cleanup());
beforeEach(async () => {
  // Her test kendi dosya kimliğini kullanır (emülatör temizliği gecikebilir).
  n += 1;
  current = n.toString(16).padStart(32, '0');
  await env.clearFirestore();
  await env.clearStorage();
  await env.withSecurityRulesDisabled((ctx) =>
    setDoc(doc(ctx.firestore(), 'chats', CHAT), {
      type: 'direct', members: ['alice1', 'bobby2'], createdAt: new Date(),
      lastActivityAt: new Date(), readAt: {},
    }));
});

const st = (uid) =>
  env.authenticatedContext(uid, { firebase: { sign_in_provider: 'phone' } }).storage();
const bytes = new Uint8Array([1, 2, 3, 4]);
const meta = (uid, extra = {}) => ({
  contentType: 'application/octet-stream',
  customMetadata: { uploader: uid },
  ...extra,
});

describe('Şifreli ek dosyalar', () => {
  test('üye yükler, diğer üye indirir, yabancı indiremez', async () => {
    await assertSucceeds(uploadBytes(ref(st('alice1'), path()), bytes, meta('alice1')));
    await assertSucceeds(getBytes(ref(st('bobby2'), path())));
    await assertFails(getBytes(ref(st('carol3'), path())));
  });

  test('üye olmayan yükleyemez; başkası adına yüklenemez', async () => {
    await assertFails(uploadBytes(ref(st('carol3'), path()), bytes, meta('carol3')));
    await assertFails(uploadBytes(ref(st('alice1'), path()), bytes, meta('bobby2')));
  });

  test('yalnızca opak ikili veri, geçerli kimlik, sınırlı boyut', async () => {
    await assertFails(uploadBytes(ref(st('alice1'), path()), bytes,
      meta('alice1', { contentType: 'image/jpeg' })));
    await assertFails(uploadBytes(ref(st('alice1'), path('foto.jpg')), bytes, meta('alice1')));
    const big = new Uint8Array(50 * 1024 * 1024 + 29);
    await assertFails(uploadBytes(ref(st('alice1'), path()), big, meta('alice1')));
  });

  test('üzerine yazılamaz', async () => {
    await uploadBytes(ref(st('alice1'), path()), bytes, meta('alice1'));
    await assertFails(uploadBytes(ref(st('alice1'), path()), bytes, meta('alice1')));
  });

  test('yalnızca yükleyen siler', async () => {
    await uploadBytes(ref(st('alice1'), path()), bytes, meta('alice1'));
    await assertFails(deleteObject(ref(st('bobby2'), path())));
    await assertSucceeds(deleteObject(ref(st('alice1'), path())));
  });

  test('listelenemez', async () => {
    await uploadBytes(ref(st('alice1'), path()), bytes, meta('alice1'));
    await assertFails(listAll(ref(st('alice1'), `attachments/${CHAT}`)));
  });

  test('başka yollar kapalı', async () => {
    await assertFails(uploadBytes(ref(st('alice1'), 'public/x'), bytes, meta('alice1')));
    await assertFails(uploadBytes(ref(st('alice1'), path(current, 'd_x_y')), bytes, meta('alice1')));
  });
});
