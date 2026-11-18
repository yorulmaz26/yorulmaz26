import assert from 'node:assert/strict';
import { test } from 'node:test';

import { leaveGroup, ownedDocPaths } from './account';

test('gruptan çıkış: üye ve yöneticilikten düşer', () => {
  assert.deepEqual(leaveGroup({ id: 'g', members: ['a', 'b', 'c'], admins: ['a', 'b'] }, 'a'), {
    members: ['b', 'c'],
    admins: ['b'],
  });
});

test('son yönetici silinirse ilk üye yönetici olur', () => {
  assert.deepEqual(leaveGroup({ id: 'g', members: ['a', 'b', 'c'], admins: ['a'] }, 'a'), {
    members: ['b', 'c'],
    admins: ['b'],
  });
});

test('tek üyeli grup boşalır', () => {
  assert.deepEqual(leaveGroup({ id: 'g', members: ['a'], admins: ['a'] }, 'a'), {
    members: [],
    admins: [],
  });
});

test('silinecek belgeler', () => {
  assert.deepEqual(ownedDocPaths('u1', 'ayse'), [
    'users/u1',
    'usernames/ayse',
    'pushTokens/u1',
    'keyBundles/u1',
  ]);
  assert.deepEqual(ownedDocPaths('u1', undefined).length, 3);
});
