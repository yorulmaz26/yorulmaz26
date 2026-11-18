import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { test } from 'node:test';

import { callContent, planCallPush } from './push';
import { parseUrls, turnCredentials } from './turn';

test('TURN kimlik bilgisi: coturn use-auth-secret biçimi', () => {
  const c = turnCredentials({
    uid: 'u1',
    secret: 'gizli',
    urls: ['turn:turn.ornek.com:3478'],
    nowSeconds: 1_000_000,
    ttlSeconds: 600,
  });
  assert.equal(c.username, '1000600:u1');
  assert.equal(
    c.credential,
    createHmac('sha1', 'gizli').update('1000600:u1').digest('base64'),
  );
  assert.equal(c.ttl, 600);
  assert.deepEqual(c.urls, ['turn:turn.ornek.com:3478']);
});

test('TURN adresleri ayrıştırma', () => {
  assert.deepEqual(
    parseUrls(' turn:a:3478?transport=udp, turns:b:5349 ,http://x, '),
    ['turn:a:3478?transport=udp', 'turns:b:5349'],
  );
  assert.deepEqual(parseUrls(undefined), []);
});

test('arama bildirimi: içeriksiz, kapalıysa yok', () => {
  assert.deepEqual(planCallPush('b', { tokens: ['t'] }), [{ uid: 'b', tokens: ['t'] }]);
  assert.deepEqual(planCallPush('b', { tokens: ['t'], enabled: false }), []);
  assert.deepEqual(planCallPush('b', undefined), []);
  const c = callContent('c1', 'd_a_b', true);
  assert.deepEqual(c.data, { type: 'call', callId: 'c1', chatId: 'd_a_b' });
  assert.equal(c.notification.body, 'Gelen görüntülü arama');
});
