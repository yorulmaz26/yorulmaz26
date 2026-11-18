/**
 * Bildirim planlama — saf fonksiyonlar (birim testli).
 *
 * Mesajlar uçtan uca şifreli olduğu için sunucu içeriği BİLMEZ; bildirimler
 * de içerik, gönderen adı veya grup adı taşımaz. Yalnızca sohbet kimliği
 * (dokununca doğru sohbet açılsın diye) ve genel bir metin gönderilir.
 */

export interface PushPrefs {
  tokens?: string[];
  enabled?: boolean;
  muted?: string[];
}

export interface PushTarget {
  uid: string;
  tokens: string[];
}

export interface PushContent {
  data: Record<string, string>;
  notification: { title: string; body: string };
  android: {
    priority: 'high';
    collapseKey: string;
    notification: { tag: string };
  };
  apns: { payload: { aps: { 'thread-id': string; sound: string } } };
}

export const MAX_TOKENS_PER_USER = 10;

/** Yeni mesaj için bildirim alacak kullanıcılar ve cihazları. */
export function planMessagePush(args: {
  chatId: string;
  senderId: string;
  members: string[];
  prefs: Record<string, PushPrefs | undefined>;
}): PushTarget[] {
  const out: PushTarget[] = [];
  for (const uid of new Set(args.members)) {
    if (uid === args.senderId) continue;
    const p = args.prefs[uid];
    if (!p || p.enabled === false) continue;
    if ((p.muted ?? []).includes(args.chatId)) continue;
    const tokens = [...new Set((p.tokens ?? []).filter((t) => typeof t === 'string' && t.length > 0))]
      .slice(-MAX_TOKENS_PER_USER);
    if (tokens.length > 0) out.push({ uid, tokens });
  }
  return out;
}

export function messageContent(chatId: string, isGroup: boolean): PushContent {
  return {
    data: { type: 'message', chatId },
    notification: {
      title: 'Kuş Dili',
      body: isGroup ? 'Bir grupta yeni mesaj var' : 'Yeni bir mesajın var',
    },
    android: {
      priority: 'high',
      collapseKey: chatId,
      // Aynı sohbetin bildirimleri tek bildirimde birleşir.
      notification: { tag: chatId },
    },
    apns: { payload: { aps: { 'thread-id': chatId, sound: 'default' } } },
  };
}

/** FCM'in geçersiz saydığı (silinmesi gereken) belirteç hata kodları. */
export function isDeadTokenError(code: string | undefined): boolean {
  return (
    code === 'messaging/registration-token-not-registered' ||
    code === 'messaging/invalid-registration-token' ||
    code === 'messaging/invalid-argument'
  );
}
