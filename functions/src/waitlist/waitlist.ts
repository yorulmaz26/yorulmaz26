import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";
import { authedCallable } from "../lib/callable";
import { db, FieldValue, Timestamp } from "../lib/admin";
import { fail } from "../lib/errors";
import { docId } from "../lib/validation";
import { enforceRateLimit } from "../lib/rateLimit";
import { addDays, assertLocalDate, BUSINESS_ZONE, localDateOf } from "../domain";
import { waitlistRef } from "../booking/appointments";
import { DateTime } from "luxon";

export const MAX_WAITLIST_PER_CUSTOMER = 10;
/** Aynı kişiye aynı gün için en sık bu aralıkla "yer açıldı" bildirimi gider. */
const RENOTIFY_MS = 30 * 60_000;

/**
 * Bekleme listesi: seçilen uzmanın o gününde uygun saat yoksa müşteri listeye girer; o gün için bir
 * randevu iptal edilir/düşer/başka güne taşınırsa listedekilere bildirim gider (ilk gelen alır — slot ayrılmaz).
 */
export const joinWaitlist = authedCallable<{ salonId?: unknown; staffUid?: unknown; localDate?: unknown }, { id: string }>("joinWaitlist", async (data, caller) => {
  const salonId = docId(data.salonId, "salonId");
  const staffUid = docId(data.staffUid, "staffUid");
  const localDate = String(data.localDate ?? "");
  try { assertLocalDate(localDate); } catch { fail("invalid-argument", "INVALID_DATE"); }
  if (staffUid === caller.uid) fail("failed-precondition", "CANNOT_BOOK_SELF");
  const today = localDateOf(new Date());
  if (localDate < today || localDate > addDays(today, 60)) fail("failed-precondition", "INVALID_DATE");
  await enforceRateLimit(`waitlist_${caller.uid}`, 30, 86400);
  const [salon, member, mine] = await Promise.all([
    db.collection("salons").doc(salonId).get(),
    db.collection("salons").doc(salonId).collection("members").doc(staffUid).get(),
    db.collection("waitlist").where("customerUid", "==", caller.uid).where("localDate", ">=", today).get(),
  ]);
  if (!salon.exists || salon.get("status") !== "ACTIVE") fail("failed-precondition", "SALON_INACTIVE");
  if (!member.exists || member.get("status") !== "ACTIVE") fail("failed-precondition", "STAFF_INACTIVE");
  const ref = waitlistRef(staffUid, localDate, caller.uid);
  if (!mine.docs.some((d) => d.id === ref.id) && mine.size >= MAX_WAITLIST_PER_CUSTOMER) fail("failed-precondition", "WAITLIST_LIMIT", { limit: MAX_WAITLIST_PER_CUSTOMER });
  await ref.set({
    customerUid: caller.uid, salonId, salonName: salon.get("name"), staffUid,
    staffName: `${member.get("firstName")} ${member.get("lastName")}`, localDate,
    notifiedAt: null, createdAt: FieldValue.serverTimestamp(),
  }, { merge: true });
  return { id: ref.id };
});

export const leaveWaitlist = authedCallable<{ id?: unknown }, { ok: true }>("leaveWaitlist", async (data, caller) => {
  const id = docId(data.id, "id");
  const ref = db.collection("waitlist").doc(id);
  const snap = await ref.get();
  if (snap.exists && snap.get("customerUid") !== caller.uid) fail("permission-denied", "PERMISSION_DENIED");
  await ref.delete();
  return { ok: true };
});

/** Bir randevunun (uzman, gün) aralığını serbest bırakıp bırakmadığı. */
export function freedSlot(before: FirebaseFirestore.DocumentData, after: FirebaseFirestore.DocumentData, nowMs: number): { staffUid: string; localDate: string } | null {
  const wasActive = ["PENDING", "CONFIRMED"].includes(before.status);
  if (!wasActive) return null;
  const startMs = (before.startAt as Timestamp).toMillis();
  if (startMs <= nowMs) return null;
  const nowCancelled = ["CANCELLED_BY_CUSTOMER", "CANCELLED_BY_STAFF", "EXPIRED"].includes(after.status);
  const moved = after.staffUid !== before.staffUid || after.localDate !== before.localDate || after.startMinuteOfDay !== before.startMinuteOfDay;
  return nowCancelled || moved ? { staffUid: before.staffUid, localDate: before.localDate } : null;
}

export async function notifyWaitlist(staffUid: string, localDate: string, salonId: string, nowMs = Date.now()): Promise<number> {
  const snap = await db.collection("waitlist").where("staffUid", "==", staffUid).where("localDate", "==", localDate).limit(50).get();
  const label = DateTime.fromISO(localDate, { zone: BUSINESS_ZONE }).setLocale("tr").toFormat("d LLLL cccc");
  let sent = 0;
  for (const d of snap.docs) {
    const last = d.get("notifiedAt") as Timestamp | null;
    if (last && nowMs - last.toMillis() < RENOTIFY_MS) continue;
    const batch = db.batch();
    batch.update(d.ref, { notifiedAt: FieldValue.serverTimestamp() });
    batch.create(db.collection("users").doc(d.get("customerUid")).collection("notifications").doc(), {
      type: "WAITLIST_SLOT_OPENED", title: "Beklediğiniz günde yer açıldı",
      body: `${d.get("staffName")} için ${label} gününde boş saat var. Hemen randevu alabilirsiniz.`,
      salonId: d.get("salonId") ?? salonId, staffUid, localDate, read: false, createdAt: FieldValue.serverTimestamp(),
    });
    await batch.commit();
    sent++;
  }
  return sent;
}

/** Randevu iptal/süre aşımı/taşıma → bekleme listesine bildirim (tüm yollar için tek nokta). */
export const onAppointmentFreed = onDocumentUpdated({ document: "appointments/{id}", memory: "256MiB", retry: false }, async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!before || !after) return;
  const freed = freedSlot(before, after, Date.now());
  if (!freed) return;
  const sent = await notifyWaitlist(freed.staffUid, freed.localDate, before.salonId);
  if (sent) logger.info("waitlist notified", { appointment: event.params.id, sent });
});
