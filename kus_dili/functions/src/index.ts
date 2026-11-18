import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { FieldValue, getFirestore } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { onDocumentCreated } from 'firebase-functions/v2/firestore';
import { HttpsError, onCall } from 'firebase-functions/v2/https';
import { logger } from 'firebase-functions/v2';

import { leaveGroup, ownedDocPaths } from './account';
import {
  isDeadTokenError,
  messageContent,
  planMessagePush,
  PushContent,
  PushPrefs,
  PushTarget,
} from './push';

initializeApp();

const REGION = 'europe-west1';

/** Hedeflere gönderir; geçersiz belirteçleri pushTokens'tan siler. */
export async function deliver(targets: PushTarget[], content: PushContent): Promise<void> {
  const db = getFirestore();
  const owner = new Map<string, string>();
  const tokens: string[] = [];
  for (const t of targets) {
    for (const tok of t.tokens) {
      owner.set(tok, t.uid);
      tokens.push(tok);
    }
  }
  for (let i = 0; i < tokens.length; i += 500) {
    const batch = tokens.slice(i, i + 500);
    const res = await getMessaging().sendEachForMulticast({ ...content, tokens: batch });
    const dead = new Map<string, string[]>();
    res.responses.forEach((r, idx) => {
      if (!r.success && isDeadTokenError(r.error?.code)) {
        const tok = batch[idx];
        const uid = owner.get(tok);
        if (uid) dead.set(uid, [...(dead.get(uid) ?? []), tok]);
      }
    });
    await Promise.all(
      [...dead].map(([uid, toks]) =>
        db.doc(`pushTokens/${uid}`).update({ tokens: FieldValue.arrayRemove(...toks) }),
      ),
    );
  }
}

async function prefsOf(uids: string[]): Promise<Record<string, PushPrefs | undefined>> {
  if (uids.length === 0) return {};
  const db = getFirestore();
  const snaps = await db.getAll(...uids.map((u) => db.doc(`pushTokens/${u}`)));
  return Object.fromEntries(snaps.map((s) => [s.id, s.data() as PushPrefs | undefined]));
}

/** Yeni mesaj → diğer üyelere içeriksiz bildirim. */
export const notifyNewMessage = onDocumentCreated(
  { document: 'chats/{chatId}/messages/{messageId}', region: REGION },
  async (event) => {
    const msg = event.data?.data();
    if (!msg || typeof msg.senderId !== 'string') return;
    const chatId = event.params.chatId;
    const chat = (await getFirestore().doc(`chats/${chatId}`).get()).data();
    if (!chat || !Array.isArray(chat.members)) return;
    const members = chat.members as string[];
    const targets = planMessagePush({
      chatId,
      senderId: msg.senderId,
      members,
      prefs: await prefsOf(members.filter((m) => m !== msg.senderId)),
    });
    if (targets.length === 0) return;
    await deliver(targets, messageContent(chatId, chat.type === 'group'));
    logger.info('bildirim', { chatId, recipients: targets.length });
  },
);

/**
 * Hesabı kalıcı olarak siler (mağaza kuralı: uygulama içinden hesap silme).
 * Profil, kullanıcı adı, açık anahtarlar, bildirim belirteçleri, sohbet
 * ayarları silinir; gruplardan çıkılır; en sonda Firebase hesabı silinir.
 */
export const deleteAccount = onCall({ region: REGION }, async (req) => {
  const auth = req.auth;
  if (!auth || auth.token.firebase?.sign_in_provider !== 'phone') {
    throw new HttpsError('unauthenticated', 'Giriş gerekli.');
  }
  const uid = auth.uid;
  const db = getFirestore();
  const profile = (await db.doc(`users/${uid}`).get()).data();

  // Gruplardan çık.
  const groups = await db
    .collection('chats')
    .where('members', 'array-contains', uid)
    .where('type', '==', 'group')
    .get();
  for (const g of groups.docs) {
    const d = g.data();
    await g.ref.update(
      leaveGroup({ id: g.id, members: d.members ?? [], admins: d.admins ?? [] }, uid),
    );
  }

  // Alt koleksiyonlarla birlikte sahip olunan belgeler.
  for (const path of ownedDocPaths(uid, profile?.username)) {
    await db.recursiveDelete(db.doc(path));
  }

  await getAuth().deleteUser(uid);
  logger.info('hesap silindi', { groups: groups.size });
  return { ok: true };
});
