import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";
import { bucket, db, Timestamp } from "../lib/admin";
import { localDateOf } from "../domain";

const PENDING_MAX_AGE_MS = 24 * 3600_000;

/** Yarım kalmış kayıt/görsel yüklemeleri (uploads/{uid}/pending) 24 saat sonra silinir. */
export async function cleanupPendingUploads(now = Date.now()): Promise<number> {
  const [files] = await bucket().getFiles({ prefix: "uploads/" });
  const stale = files.filter((f) => f.name.includes("/pending/") && now - new Date(String(f.metadata.timeCreated)).getTime() > PENDING_MAX_AGE_MS);
  await Promise.all(stale.map((f) => f.delete().catch(() => undefined)));
  return stale.length;
}

/** TTL politikası tanımlı değilse de idempotency/hız limiti kayıtları birikmesin. */
export async function cleanupExpiredKeys(now = Date.now()): Promise<number> {
  let removed = 0;
  for (const col of ["idempotencyKeys", "rateLimits"]) {
    const snap = await db.collection(col).where("expireAt", "<", Timestamp.fromMillis(now)).limit(500).get();
    const batch = db.batch();
    snap.docs.forEach((d) => batch.delete(d.ref));
    if (!snap.empty) await batch.commit();
    removed += snap.size;
  }
  return removed;
}

/** Geçmiş günlere ait bekleme listesi kayıtları silinir. */
export async function cleanupWaitlist(now = new Date()): Promise<number> {
  const snap = await db.collection("waitlist").where("localDate", "<", localDateOf(now)).limit(500).get();
  const batch = db.batch();
  snap.docs.forEach((d) => batch.delete(d.ref));
  if (!snap.empty) await batch.commit();
  return snap.size;
}

export const dailyCleanup = onSchedule({ schedule: "every day 04:17", timeZone: "Europe/Istanbul", memory: "256MiB", retryCount: 1 }, async () => {
  const [uploads, keys, waitlist] = [await cleanupPendingUploads(), await cleanupExpiredKeys(), await cleanupWaitlist()];
  logger.info("dailyCleanup", { uploads, keys, waitlist });
});
