import { authedCallable } from "../lib/callable";
import { db, FieldValue, Timestamp } from "../lib/admin";
import { fail } from "../lib/errors";
import { docId } from "../lib/validation";
import { notifyInTx } from "../lib/notify";
import { auditInTx } from "../lib/audit";
import { BUSINESS_ZONE, computeSlots, conflictReason, formatHm, localDateOf, overlaps } from "../domain";
import { DateTime } from "luxon";
import { busyOf, liveIntervals, LockInterval, loadBookingContext, lockRef } from "./context";
import { ActiveEntry, addEvent, appts, customerLockRef } from "./appointments";

const ACTIVE_EDITABLE = new Set(["PENDING", "CONFIRMED"]);

/**
 * Uzmanı değiştir (yalnızca salon sahibi). Randevu kalemleri (fiyat/süre snapshot) korunur;
 * yeni uzmanın o aralıkta çalışıyor ve boş olması gerekir. Müşteri ve iki uzman bilgilendirilir.
 */
export const reassignAppointment = authedCallable<{ appointmentId?: unknown; newStaffUid?: unknown }, { staffUid: string }>(
  "reassignAppointment",
  async (data, caller) => {
    const appointmentId = docId(data.appointmentId, "appointmentId");
    const newStaffUid = docId(data.newStaffUid, "newStaffUid");
    return db.runTransaction(async (tx) => {
      const appt = await tx.get(appts().doc(appointmentId));
      if (!appt.exists) fail("not-found", "APPOINTMENT_NOT_FOUND");
      const salonId = appt.get("salonId") as string;
      const oldStaff = appt.get("staffUid") as string;
      const localDate = appt.get("localDate") as string;
      const start = appt.get("startMinuteOfDay") as number;
      const end = appt.get("endMinuteOfDay") as number;
      if (newStaffUid === oldStaff) fail("invalid-argument", "SAME_STAFF");
      if (newStaffUid === appt.get("customerUid")) fail("failed-precondition", "CANNOT_BOOK_SELF");

      const memberRef = db.collection("salons").doc(salonId).collection("members").doc(newStaffUid);
      const [salon, member, schedule, staffCl, salonCl, oldLock, newLock] = await Promise.all([
        tx.get(db.collection("salons").doc(salonId)),
        tx.get(memberRef),
        tx.get(memberRef.collection("schedule").doc("weekly")),
        tx.get(memberRef.collection("closures").where("endDate", ">=", localDate)),
        tx.get(db.collection("salons").doc(salonId).collection("closures").where("endDate", ">=", localDate)),
        tx.get(lockRef(oldStaff, localDate)),
        tx.get(lockRef(newStaffUid, localDate)),
      ]);
      if (salon.get("ownerUid") !== caller.uid) fail("permission-denied", "PERMISSION_DENIED");
      if (!ACTIVE_EDITABLE.has(appt.get("status"))) fail("failed-precondition", "STATE_CHANGED", { status: appt.get("status") });
      if (!member.exists || member.get("status") !== "ACTIVE" || member.get("acceptsBookings") === false) fail("failed-precondition", "STAFF_INACTIVE");

      const reason = conflictReason(
        { localDate, startMinute: start, endMinute: end },
        {
          salonWeeklyClosedDays: salon.get("weeklyClosedDays") ?? [],
          salonClosures: salonCl.docs.map((d) => ({ startDate: d.get("startDate"), endDate: d.get("endDate") })),
          staffSchedule: { days: schedule.get("days") ?? {}, weeklyDaysOff: schedule.get("weeklyDaysOff") ?? [] },
          staffClosures: staffCl.docs.map((d) => ({ startDate: d.get("startDate"), endDate: d.get("endDate") })),
        },
      );
      if (reason) fail("failed-precondition", "STAFF_NOT_AVAILABLE", { conflict: reason });
      const nowMs = Date.now();
      const newLive = liveIntervals(newLock, nowMs);
      if (newLive.some((i) => overlaps({ start: i.startMin, end: i.endMin }, { start, end }))) fail("aborted", "SLOT_TAKEN");

      const mine = ((oldLock.get("intervals") as LockInterval[] | undefined) ?? []).find((i) => i.appointmentId === appointmentId);
      tx.set(oldLock.ref, { intervals: ((oldLock.get("intervals") as LockInterval[] | undefined) ?? []).filter((i) => i.appointmentId !== appointmentId), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      tx.set(newLock.ref, {
        staffUid: newStaffUid, localDate,
        intervals: [...newLive, { appointmentId, salonId, startMin: start, endMin: end, expiresAtMs: mine?.expiresAtMs ?? null, role: "STAFF" }],
        updatedAt: FieldValue.serverTimestamp(),
      });
      const newName = `${member.get("firstName")} ${member.get("lastName")}`;
      tx.update(appt.ref, { staffUid: newStaffUid, staffName: newName, attention: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
      addEvent(tx, appointmentId, appt.get("status"), appt.get("status"), caller.uid, "OWNER", `Uzman değişti: ${appt.get("staffName")} → ${newName}`);
      notifyInTx(tx, appt.get("customerUid"), { type: "APPOINTMENT_REASSIGNED", title: "Randevunuzun uzmanı değişti", body: `${appt.get("salonName")} randevunuz artık ${newName} ile.`, salonId, appointmentId });
      notifyInTx(tx, newStaffUid, { type: "APPOINTMENT_ASSIGNED", title: "Size bir randevu atandı", body: "Salon sahibi size bir randevu atadı.", salonId, appointmentId });
      if (oldStaff !== caller.uid) notifyInTx(tx, oldStaff, { type: "APPOINTMENT_UNASSIGNED", title: "Bir randevunuz başka uzmana atandı", body: "Salon sahibi randevuyu yeniden atadı.", salonId, appointmentId });
      auditInTx(tx, { action: "APPOINTMENT_REASSIGNED", actorUid: caller.uid, salonId, targetId: appointmentId, data: { from: oldStaff, to: newStaffUid } });
      return { staffUid: newStaffUid };
    });
  },
);

/**
 * Salon tarafı saat değişikliği (atanmış uzman veya sahip). Yeni başlangıç, aynı uzmanın güncel
 * müsaitliğinde (randevunun kendisi hariç) geçerli bir slot olmalıdır. Müşteri bilgilendirilir;
 * uygun değilse kendi iptal hakkını kullanabilir.
 */
export const rescheduleAppointment = authedCallable<{ appointmentId?: unknown; newStartAt?: unknown }, { startAt: string }>(
  "rescheduleAppointment",
  async (data, caller) => {
    const appointmentId = docId(data.appointmentId, "appointmentId");
    const start = DateTime.fromISO(String(data.newStartAt ?? ""), { zone: "utc" });
    if (!start.isValid) fail("invalid-argument", "INVALID_ARGUMENT", { field: "newStartAt" });
    const local = start.setZone(BUSINESS_ZONE);
    const newDate = local.toISODate()!;
    const newStart = local.hour * 60 + local.minute;

    return db.runTransaction(async (tx) => {
      const appt = await tx.get(appts().doc(appointmentId));
      if (!appt.exists) fail("not-found", "APPOINTMENT_NOT_FOUND");
      const salonId = appt.get("salonId") as string;
      const staffUid = appt.get("staffUid") as string;
      const oldDate = appt.get("localDate") as string;
      const now = new Date();
      const salonSnap = await tx.get(db.collection("salons").doc(salonId));
      if (caller.uid !== staffUid && salonSnap.get("ownerUid") !== caller.uid) fail("permission-denied", "PERMISSION_DENIED");
      // Hizmetler snapshot'tan gelir; yalnızca salon/uzman/program/kapatmalar yeniden okunur.
      const ctx = await loadBookingContext(tx, salonId, staffUid, null, localDateOf(now));
      const salon = ctx.salon;
      const customerUid = appt.get("customerUid") as string | null;
      const personLock = appt.get("personLock") === true && customerUid;
      const [oldLock, newLock, pOld, pNew, custLock] = await Promise.all([
        tx.get(lockRef(staffUid, oldDate)),
        tx.get(lockRef(staffUid, newDate)),
        personLock ? tx.get(lockRef(customerUid, oldDate)) : Promise.resolve(null),
        personLock ? tx.get(lockRef(customerUid, newDate)) : Promise.resolve(null),
        customerUid ? tx.get(customerLockRef(customerUid)) : Promise.resolve(null),
      ]);
      if (!ACTIVE_EDITABLE.has(appt.get("status"))) fail("failed-precondition", "STATE_CHANGED", { status: appt.get("status") });

      const minutes = appt.get("totalMinutes") as number;
      const nowMs = now.getTime();
      const others = liveIntervals(newLock, nowMs).filter((i) => i.appointmentId !== appointmentId);
      const slots = computeSlots({
        localDate: newDate, now, totalMinutes: minutes, slotMinutes: ctx.settings.slotMinutes, minLeadMinutes: ctx.settings.minLeadMinutes,
        horizonDays: ctx.settings.horizonDays, salonWeeklyClosedDays: salon.get("weeklyClosedDays") ?? [], salonClosures: ctx.salonClosures,
        staffSchedule: ctx.schedule, staffClosures: ctx.staffClosures, busy: busyOf(others),
      });
      if (!slots.some((s) => s.startAt.getTime() === start.toMillis())) fail("failed-precondition", "SLOT_UNAVAILABLE");
      // Müşteri kendisi için almışsa yeni saatte başka randevusu olmamalı.
      const pOthers = pNew ? liveIntervals(pNew, nowMs).filter((i) => i.appointmentId !== appointmentId) : [];
      if (pOthers.some((i) => overlaps({ start: i.startMin, end: i.endMin }, { start: newStart, end: newStart + minutes }))) fail("failed-precondition", "CUSTOMER_BUSY");

      const mine = ((oldLock.get("intervals") as LockInterval[] | undefined) ?? []).find((i) => i.appointmentId === appointmentId);
      const moved: LockInterval = { appointmentId, salonId, startMin: newStart, endMin: newStart + minutes, expiresAtMs: mine?.expiresAtMs ?? null, role: "STAFF" };
      const without = (l: FirebaseFirestore.DocumentSnapshot) => ((l.get("intervals") as LockInterval[] | undefined) ?? []).filter((i) => i.appointmentId !== appointmentId);
      if (oldDate !== newDate) tx.set(oldLock.ref, { intervals: without(oldLock), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      tx.set(newLock.ref, { staffUid, localDate: newDate, intervals: [...others, moved], updatedAt: FieldValue.serverTimestamp() });
      if (pOld && pNew) {
        if (oldDate !== newDate) tx.set(pOld.ref, { intervals: without(pOld), updatedAt: FieldValue.serverTimestamp() }, { merge: true });
        tx.set(pNew.ref, { staffUid: customerUid, localDate: newDate, intervals: [...pOthers, { ...moved, role: "CUSTOMER" }], updatedAt: FieldValue.serverTimestamp() });
      }
      if (custLock?.exists) {
        const active = ((custLock.get("active") as ActiveEntry[] | undefined) ?? []).map((e) => (e.appointmentId === appointmentId ? { ...e, startAtMs: start.toMillis() } : e));
        tx.update(custLock.ref, { active });
      }
      const startAt = start.toJSDate();
      const pendingExp = (appt.get("pendingExpiresAt") as Timestamp).toMillis();
      tx.update(appt.ref, {
        startAt: Timestamp.fromDate(startAt),
        endAt: Timestamp.fromMillis(startAt.getTime() + minutes * 60_000),
        localDate: newDate,
        startMinuteOfDay: newStart,
        endMinuteOfDay: newStart + minutes,
        // PENDING talebin son onay anı yeni başlangıcı aşamaz.
        pendingExpiresAt: Timestamp.fromMillis(Math.min(pendingExp, startAt.getTime())),
        attention: FieldValue.delete(),
        flags: {},
        updatedAt: FieldValue.serverTimestamp(),
      });
      const label = `${local.setLocale("tr").toFormat("d LLLL cccc")} ${formatHm(newStart)}`;
      addEvent(tx, appointmentId, appt.get("status"), appt.get("status"), caller.uid, caller.uid === staffUid ? "STAFF" : "OWNER", `Saat değişti → ${label}`);
      notifyInTx(tx, appt.get("customerUid"), {
        type: "APPOINTMENT_RESCHEDULED", title: "Randevu saatiniz değişti",
        body: `${appt.get("salonName")} randevunuzu ${label} olarak güncelledi. Uygun değilse uygulamadan iptal edebilirsiniz.`,
        salonId, appointmentId,
      });
      return { startAt: startAt.toISOString() };
    });
  },
);

/** Geçmiş (terminal) randevuları yalnızca çağıranın listesinden gizler; kayıtlar silinmez. */
export const hideAppointments = authedCallable<{ appointmentIds?: unknown }, { hidden: number }>("hideAppointments", async (data, caller) => {
  if (!Array.isArray(data.appointmentIds) || data.appointmentIds.length === 0 || data.appointmentIds.length > 100) {
    fail("invalid-argument", "INVALID_ARGUMENT", { field: "appointmentIds" });
  }
  const ids = [...new Set((data.appointmentIds as unknown[]).map((i) => docId(i, "appointmentIds")))];
  const snaps = await db.getAll(...ids.map((id) => appts().doc(id)));
  const ownerCache = new Map<string, string>();
  const batch = db.batch();
  let hidden = 0;
  for (const s of snaps) {
    if (!s.exists) continue;
    const salonId = s.get("salonId") as string;
    if (!ownerCache.has(salonId)) ownerCache.set(salonId, (await db.collection("salons").doc(salonId).get()).get("ownerUid"));
    const visible = [s.get("customerUid"), s.get("staffUid"), ownerCache.get(salonId)].includes(caller.uid);
    if (!visible) fail("permission-denied", "PERMISSION_DENIED");
    if (["PENDING", "CONFIRMED", "IN_PROGRESS"].includes(s.get("status"))) fail("failed-precondition", "CANNOT_HIDE_ACTIVE");
    batch.update(s.ref, { hiddenFor: FieldValue.arrayUnion(caller.uid) });
    hidden++;
  }
  await batch.commit();
  return { hidden };
});
