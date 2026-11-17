import { db, Timestamp } from "./admin";
import { fail } from "./errors";

/** Sabit pencereli sayaç: windowSeconds içinde en fazla `limit` işlem. */
export async function enforceRateLimit(key: string, limit: number, windowSeconds: number): Promise<void> {
  const window = Math.floor(Date.now() / 1000 / windowSeconds);
  const ref = db.collection("rateLimits").doc(`${key}_${windowSeconds}_${window}`);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const count = (snap.get("count") as number | undefined) ?? 0;
    if (count >= limit) fail("resource-exhausted", "RATE_LIMITED", { retryAfterSeconds: windowSeconds });
    tx.set(ref, {
      count: count + 1,
      expireAt: Timestamp.fromMillis((window + 1) * windowSeconds * 1000 + 86_400_000),
    });
  });
}
