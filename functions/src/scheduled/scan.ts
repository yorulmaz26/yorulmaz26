import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";
import { db, FieldValue, Timestamp } from "../lib/admin";
import { notifyInTx } from "../lib/notify";
import { formatHm } from "../domain";
import { addEvent, appts, customerLockRef, releaseLocks } from "../booking/appointments";
import { lockRef } from "../booking/context";

const REMINDER_MINUTES = 120;
const ARRIVAL_PROMPT_MAX_AGE_HOURS = 6;
const BATCH = 200;

/** Süresi dolan PENDING taleplerini EXPIRED yapar; slot ve müşteri kilidini serbest bırakır; taraflara bildirir. */
export async function expirePending(now: Date): Promise<number> {
  const snap = await appts().where("status", "==", "PENDING").where("pendingExpiresAt", "<=", Timestamp.fromDate(now)).limit(BATCH).get();
  let count = 0;
  for (const doc of snap.docs) {
    const done = await db.runTransaction(async (tx) => {
      const a = await tx.get(doc.ref);
      if (a.get("status") !== "PENDING" || (a.get("pendingExpiresAt") as Timestamp).toMillis() > now.getTime()) return false;
      const [lock, cl] = await Promise.all([
        tx.get(lockRef(a.get("salonId"), a.get("staffUid"), a.get("localDate"))),
        tx.get(customerLockRef(a.get("customerUid"))),
      ]);
      tx.update(a.ref, { status: "EXPIRED", updatedAt: FieldValue.serverTimestamp() });
      releaseLocks(tx, a, lock, cl);
      addEvent(tx, a.id, "PENDING", "EXPIRED", "system", "SYSTEM");
      const base = { salonId: a.get("salonId"), appointmentId: a.id };
      notifyInTx(tx, a.get("customerUid"), { ...base, type: "APPOINTMENT_EXPIRED", title: "Randevu talebiniz düştü", body: `${a.get("salonName")} talebinizi süresi içinde yanıtlamadı. Yeni randevu alabilirsiniz.` });
      notifyInTx(tx, a.get("staffUid"), { ...base, type: "APPOINTMENT_EXPIRED", title: "Yanıtlanmayan talep düştü", body: "Bir randevu talebi onay süresi dolduğu için otomatik iptal oldu." });
      return true;
    });
    if (done) count++;
  }
  return count;
}

/** Onaylı randevudan 2 saat önce müşteriye hatırlatma (bayrakla bir kez). */
export async function sendReminders(now: Date): Promise<number> {
  const until = Timestamp.fromMillis(now.getTime() + REMINDER_MINUTES * 60_000);
  const snap = await appts().where("status", "==", "CONFIRMED").where("startAt", ">", Timestamp.fromDate(now)).where("startAt", "<=", until).limit(BATCH).get();
  let count = 0;
  for (const doc of snap.docs) {
    if (doc.get("flags.reminder2hSentAt")) continue;
    const done = await db.runTransaction(async (tx) => {
      const a = await tx.get(doc.ref);
      if (a.get("status") !== "CONFIRMED" || a.get("flags.reminder2hSentAt")) return false;
      tx.update(a.ref, { "flags.reminder2hSentAt": FieldValue.serverTimestamp() });
      notifyInTx(tx, a.get("customerUid"), {
        type: "APPOINTMENT_REMINDER", title: "Randevunuz yaklaşıyor",
        body: `${a.get("salonName")} randevunuz bugün ${formatHm(a.get("startMinuteOfDay"))}'da.`,
        salonId: a.get("salonId"), appointmentId: a.id,
      });
      return true;
    });
    if (done) count++;
  }
  return count;
}

/** Başlangıç saati gelen onaylı randevu için uzmana "Müşteri geldi mi?" (bir kez). */
export async function sendArrivalPrompts(now: Date): Promise<number> {
  const from = Timestamp.fromMillis(now.getTime() - ARRIVAL_PROMPT_MAX_AGE_HOURS * 3600_000);
  const snap = await appts().where("status", "==", "CONFIRMED").where("startAt", ">=", from).where("startAt", "<=", Timestamp.fromDate(now)).limit(BATCH).get();
  let count = 0;
  for (const doc of snap.docs) {
    if (doc.get("flags.arrivalPromptSentAt")) continue;
    const done = await db.runTransaction(async (tx) => {
      const a = await tx.get(doc.ref);
      if (a.get("status") !== "CONFIRMED" || a.get("flags.arrivalPromptSentAt")) return false;
      tx.update(a.ref, { "flags.arrivalPromptSentAt": FieldValue.serverTimestamp() });
      notifyInTx(tx, a.get("staffUid"), {
        type: "ARRIVAL_PROMPT", title: "Müşteri geldi mi?",
        body: `${formatHm(a.get("startMinuteOfDay"))} randevusu başladı. “Geldi” veya “Gelmedi” olarak işaretleyin.`,
        salonId: a.get("salonId"), appointmentId: a.id,
      });
      return true;
    });
    if (done) count++;
  }
  return count;
}

/** Her dakika: süre aşımı, 2 saat hatırlatması, "Müşteri geldi mi?". Tümü idempotent. */
export const scanAppointments = onSchedule({ schedule: "every 1 minutes", timeZone: "Europe/Istanbul", memory: "256MiB", retryCount: 0 }, async () => {
  const now = new Date();
  const [expired, reminders, prompts] = [await expirePending(now), await sendReminders(now), await sendArrivalPrompts(now)];
  if (expired + reminders + prompts > 0) logger.info("scanAppointments", { expired, reminders, prompts });
});
