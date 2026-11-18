import { initializeApp } from 'firebase-admin/app';
import { FieldValue, getFirestore } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { defineSecret, defineString } from 'firebase-functions/params';
import { onDocumentCreated } from 'firebase-functions/v2/firestore';
import { HttpsError, onCall } from 'firebase-functions/v2/https';
import { logger } from 'firebase-functions/v2';

import { parseUrls, turnCredentials as makeTurnCredentials } from './turn';
import {
  callContent,
  isDeadTokenError,
  messageContent,
  planCallPush,
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

/** Gelen arama → arayana değil arananın cihazlarına içeriksiz bildirim. */
export const notifyIncomingCall = onDocumentCreated(
  { document: 'calls/{callId}', region: REGION },
  async (event) => {
    const c = event.data?.data();
    if (!c || c.state !== 'ringing' || typeof c.callee !== 'string') return;
    const prefs = await prefsOf([c.callee]);
    const targets = planCallPush(c.callee, prefs[c.callee]);
    if (targets.length === 0) return;
    await deliver(targets, callContent(event.params.callId, String(c.chatId), c.video === true));
  },
);

const TURN_SECRET = defineSecret('TURN_SECRET');
const TURN_URLS = defineString('TURN_URLS', { default: '' });

/**
 * Arama için kısa ömürlü TURN kimlik bilgisi. TURN yapılandırılmamışsa boş
 * döner; uygulama yalnızca STUN ile dener.
 */
export const turnCredentials = onCall(
  { region: REGION, secrets: [TURN_SECRET] },
  (req) => {
    const auth = req.auth;
    if (!auth || auth.token.firebase?.sign_in_provider !== 'phone') {
      throw new HttpsError('unauthenticated', 'Giriş gerekli.');
    }
    const urls = parseUrls(TURN_URLS.value());
    const secret = TURN_SECRET.value();
    if (urls.length === 0 || !secret) return { urls: [] };
    return makeTurnCredentials({
      uid: auth.uid,
      secret,
      urls,
      nowSeconds: Math.floor(Date.now() / 1000),
    });
  },
);
