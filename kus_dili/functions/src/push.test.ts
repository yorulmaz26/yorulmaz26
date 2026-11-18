import assert from 'node:assert/strict';
import { test } from 'node:test';

import { isDeadTokenError, messageContent, planMessagePush } from './push';

test('gönderen, kapalı, sessize alınmış ve belirteçsiz üyeler atlanır', () => {
  const targets = planMessagePush({
    chatId: 'c1',
    senderId: 'a',
    members: ['a', 'b', 'c', 'd', 'e', 'b'],
    prefs: {
      a: { tokens: ['ta'] },
      b: { tokens: ['tb1', 'tb2', 'tb1', ''] },
      c: { tokens: ['tc'], enabled: false },
      d: { tokens: ['td'], muted: ['c1'] },
    },
  });
  assert.deepEqual(targets, [{ uid: 'b', tokens: ['tb1', 'tb2'] }]);
});

test('başka sohbeti sessize almış kullanıcı yine bildirim alır', () => {
  const targets = planMessagePush({
    chatId: 'c1',
    senderId: 'a',
    members: ['a', 'b'],
    prefs: { b: { tokens: ['t'], muted: ['c2'] } },
  });
  assert.equal(targets.length, 1);
});

test('göndereni engelleyen üye bildirim almaz', () => {
  const targets = planMessagePush({
    chatId: 'g1',
    senderId: 'a',
    members: ['a', 'b', 'c'],
    prefs: { b: { tokens: ['tb'] }, c: { tokens: ['tc'] } },
    blocked: { b: ['a'], c: ['z'] },
  });
  assert.deepEqual(targets.map((t) => t.uid), ['c']);
});

test('kullanıcı başına en fazla 10 cihaz (en yeniler)', () => {
  const tokens = Array.from({ length: 15 }, (_, i) => `t${i}`);
  const [t] = planMessagePush({
    chatId: 'c', senderId: 'a', members: ['a', 'b'], prefs: { b: { tokens } },
  });
  assert.deepEqual(t.tokens, tokens.slice(5));
});

test('bildirim içeriği mesaj, gönderen veya grup adı içermez', () => {
  const c = messageContent('g_abc', true);
  assert.deepEqual(c.data, { type: 'message', chatId: 'g_abc' });
  assert.equal(c.notification.title, 'Kuş Dili');
  assert.equal(c.notification.body, 'Bir grupta yeni mesaj var');
  assert.equal(messageContent('d_a_b', false).notification.body, 'Yeni bir mesajın var');
  assert.equal(c.android.collapseKey, 'g_abc');
  assert.equal(Object.keys(c.data).length, 2);
});

test('ölü belirteç hata kodları', () => {
  assert.equal(isDeadTokenError('messaging/registration-token-not-registered'), true);
  assert.equal(isDeadTokenError('messaging/internal-error'), false);
  assert.equal(isDeadTokenError(undefined), false);
});
