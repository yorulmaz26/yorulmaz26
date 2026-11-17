import { db, Timestamp } from "./admin";

const TTL_DAYS = 7;

/**
 * Aynı kullanıcı + aynı anahtarla tekrar gelen istek ilk sonucu alır.
 * İşlemin kendisi ayrıca transaction/benzersiz doküman kimliğiyle çift etkiye karşı korunmalıdır;
 * bu katman ağ tekrarlarında aynı yanıtın dönmesini sağlar.
 */
export async function withIdempotency<R>(uid: string, key: string, action: string, run: () => Promise<R>): Promise<R> {
  const ref = db.collection("idempotencyKeys").doc(`${uid}_${key}`);
  const existing = await ref.get();
  if (existing.exists && existing.get("action") === action) return existing.get("result") as R;
  const result = await run();
  await ref.set({
    uid,
    action,
    result: JSON.parse(JSON.stringify(result ?? null)),
    createdAt: Timestamp.now(),
    expireAt: Timestamp.fromMillis(Date.now() + TTL_DAYS * 86_400_000),
  });
  return result;
}
