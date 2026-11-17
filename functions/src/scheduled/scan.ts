import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";
import { db, FieldValue, Timestamp } from "../lib/admin";
import { notifyInTx } from "../lib/notify";
import { formatHm } from "../domain";
import { addEvent, appts, readLocks, releaseLocks, whenLabel } from "../booking/appointments";

const REMINDER_MINUTES = 120;
const DAY_REMINDER_MINUTES = 24 * 60;
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
      const locks = await readLocks(tx, a);
      tx.update(a.ref, { status: "EXPIRED", updatedAt: FieldValue.serverTimestamp() });
      releaseLocks(tx, a, locks);
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

/**
 * Onaylı randevu hatırlatmaları (bayrakla her biri bir kez):
 *  - 24 saat kala: müşteriye; ayrıca son 2 saatte uygulamadan iptal edilemeyeceği hatırlatılır,
 *  - 2 saat kala: müşteriye.
 * 24 saat penceresi (2 sa, 24 sa]: randevu 24 saatten az kala onaylandıysa 24 saat hatırlatması
 * hemen gider (2 saat hatırlatmasıyla çakışmaz). Elden randevularda müşteri hesabı yoktur, atlanır.
 */
async function remind(now: Date, minutes: number, lowerMinutes: number, flag: string, body: (a: FirebaseFirestore.DocumentSnapshot) => string): Promise<number> {
  const from = Timestamp.fromMillis(now.getTime() + lowerMinutes * 60_000);
  const until = Timestamp.fromMillis(now.getTime() + minutes * 60_000);
  const snap = await appts().where("status", "==", "CONFIRMED").where("startAt", ">", from).where("startAt", "<=", until).limit(BATCH).get();
  let count = 0;
  for (const doc of snap.docs) {
    if (doc.get(`flags.${flag}`) || !doc.get("customerUid")) continue;
    const done = await db.runTransaction(async (tx) => {
      const a = await tx.get(doc.ref);
      if (a.get("status") !== "CONFIRMED" || a.get(`flags.${flag}`)) return false;
      tx.update(a.ref, { [`flags.${flag}`]: FieldValue.serverTimestamp() });
      notifyInTx(tx, a.get("customerUid"), { type: "APPOINTMENT_REMINDER", title: "Randevunuz yaklaşıyor", body: body(a), salonId: a.get("salonId"), appointmentId: a.id });
      return true;
    });
    if (done) count++;
  }
  return count;
}

export async function sendReminders(now: Date): Promise<number> {
  const day = await remind(now, DAY_REMINDER_MINUTES, REMINDER_MINUTES, "reminder24hSentAt", (a) =>
    `${a.get("salonName")} randevunuz ${whenLabel((a.get("startAt") as Timestamp).toDate())}. Gelemeyecekseniz şimdi iptal edin; son 2 saatte iptal için salonu aramanız gerekir.`);
  const soon = await remind(now, REMINDER_MINUTES, 0, "reminder2hSentAt", (a) =>
    `${a.get("salonName")} randevunuz bugün ${formatHm(a.get("startMinuteOfDay"))}'da.`);
  return day + soon;
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

/** Her dakika: süre aşımı, 24 saat ve 2 saat hatırlatması, "Müşteri geldi mi?". Tümü idempotent. */
export const scanAppointments = onSchedule({ schedule: "every 1 minutes", timeZone: "Europe/Istanbul", memory: "256MiB", retryCount: 0 }, async () => {
  const now = new Date();
  const [expired, reminders, prompts] = [await expirePending(now), await sendReminders(now), await sendArrivalPrompts(now)];
  if (expired + reminders + prompts > 0) logger.info("scanAppointments", { expired, reminders, prompts });
});
