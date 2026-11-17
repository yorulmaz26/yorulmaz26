import { DocumentSnapshot, Transaction } from "firebase-admin/firestore";
import { DateTime } from "luxon";
import { authedCallable } from "../lib/callable";
import { db, FieldValue, Timestamp } from "../lib/admin";
import { fail } from "../lib/errors";
import { docId, idempotencyKey, optString } from "../lib/validation";
import { enforceRateLimit } from "../lib/rateLimit";
import { notifyInTx } from "../lib/notify";
import {
  ACTIVE_STATUSES,
  actorRoleFor,
  AppointmentAction,
  AppointmentStatus,
  BUSINESS_ZONE,
  computePendingExpiresAt,
  computeSlots,
  evaluateTransition,
  formatHm,
  localDateOf,
  overlaps,
} from "../domain";
import { busyOf, liveIntervals, LockInterval, loadBookingContext, lockRef } from "./context";
import { ledgerCol, periodKeysOf, writeLedger } from "../finance/ledger";

export const appts = () => db.collection("appointments");
export const customerLockRef = (uid: string) => db.collection("customerLocks").doc(uid);

/** Kullanıcıya gösterilen kısa randevu numarası (belge kimliğinden türetilir). */
export const bookingNumber = (id: string) => `KC-${id.slice(0, 8).toUpperCase()}`;

export function addEvent(tx: Transaction, appointmentId: string, from: AppointmentStatus | null, to: AppointmentStatus, actorUid: string, actorRole: string, reason?: string | null) {
  tx.create(appts().doc(appointmentId).collection("events").doc(), {
    from, to, actorUid, actorRole, reason: reason ?? null, at: FieldValue.serverTimestamp(),
  });
}

/** Terminal duruma geçişte uzman kilidinden ve müşteri kilidinden randevuyu çıkarır. */
export function releaseLocks(tx: Transaction, appt: DocumentSnapshot, lock: DocumentSnapshot, customerLock: DocumentSnapshot) {
  const intervals = ((lock.get("intervals") as LockInterval[] | undefined) ?? []).filter((i) => i.appointmentId !== appt.id);
  if (lock.exists) tx.update(lock.ref, { intervals, updatedAt: FieldValue.serverTimestamp() });
  if (customerLock.get("activeAppointmentId") === appt.id) tx.set(customerLock.ref, { activeAppointmentId: null, expiresAtMs: null, updatedAt: FieldValue.serverTimestamp() });
}

// ---------------------------------------------------------------------------

interface CreateReq {
  salonId?: unknown;
  staffUid?: unknown;
  serviceIds?: unknown;
  startAt?: unknown;
  expectedTotalKurus?: unknown;
  idempotencyKey?: unknown;
}

export interface CreateRes {
  appointmentId: string;
  number: string;
  status: "PENDING";
  pendingExpiresAt: string;
}

/**
 * Randevu talebi. Tek transaction içinde:
 *  - salon/uzman/hizmetler/program/kapatmalar yeniden okunur, fiyat ve müsaitlik yeniden doğrulanır,
 *  - uzman×gün kilidi okunur → [start,end) çakışması varsa SLOT_TAKEN (aynı uzmana çift randevu imkânsız),
 *  - müşteri kilidi okunur → aktif randevu varsa ACTIVE_APPOINTMENT_EXISTS (tek aktif randevu kuralı),
 *  - randevu, kilitler, olay kaydı ve bildirimler birlikte yazılır.
 */
export const createAppointment = authedCallable<CreateReq, CreateRes>("createAppointment", async (data, caller) => {
  if (!caller.phone) fail("failed-precondition", "PHONE_NOT_VERIFIED");
  const salonId = docId(data.salonId, "salonId");
  const staffUid = docId(data.staffUid, "staffUid");
  const key = idempotencyKey(data.idempotencyKey);
  if (!Array.isArray(data.serviceIds)) fail("invalid-argument", "INVALID_ARGUMENT", { field: "serviceIds" });
  const serviceIds = (data.serviceIds as unknown[]).map((s) => docId(s, "serviceIds"));
  const expected = Number(data.expectedTotalKurus);
  if (!Number.isSafeInteger(expected)) fail("invalid-argument", "INVALID_ARGUMENT", { field: "expectedTotalKurus" });
  const start = DateTime.fromISO(String(data.startAt ?? ""), { zone: "utc" });
  if (!start.isValid) fail("invalid-argument", "INVALID_ARGUMENT", { field: "startAt" });
  if (staffUid === caller.uid) fail("failed-precondition", "CANNOT_BOOK_SELF");

  const startAt = start.toJSDate();
  const local = start.setZone(BUSINESS_ZONE);
  const localDate = local.toISODate()!;
  const startMinute = local.hour * 60 + local.minute;

  // Ağ tekrarı: aynı anahtarla oluşturulmuş randevu varsa onu döndür.
  const prior = await appts().where("customerUid", "==", caller.uid).where("idempotencyKey", "==", key).limit(1).get();
  if (!prior.empty) {
    const d = prior.docs[0];
    return { appointmentId: d.id, number: d.get("number"), status: "PENDING", pendingExpiresAt: (d.get("pendingExpiresAt") as Timestamp).toDate().toISOString() };
  }
  await enforceRateLimit(`book_${caller.uid}`, 10, 3600);

  return db.runTransaction(async (tx) => {
    const now = new Date();
    const nowMs = now.getTime();
    const today = localDateOf(now);
    const ctx = await loadBookingContext(tx, salonId, staffUid, serviceIds, today);
    const lock = await tx.get(lockRef(salonId, staffUid, localDate));
    const custLock = await tx.get(customerLockRef(caller.uid));
    const user = await tx.get(db.collection("users").doc(caller.uid));
    const activeId = custLock.get("activeAppointmentId") as string | null | undefined;
    const activeAppt = activeId ? await tx.get(appts().doc(activeId)) : null;

    if (!user.exists) fail("failed-precondition", "PROFILE_REQUIRED");
    if (ctx.totalKurus !== expected) fail("failed-precondition", "PRICE_CHANGED", { newTotalKurus: ctx.totalKurus, totalMinutes: ctx.totalMinutes });

    // Tek aktif randevu kuralı (süresi dolmuş bekleyen talep aktif sayılmaz ve burada kapatılır).
    if (activeAppt?.exists && ACTIVE_STATUSES.has(activeAppt.get("status"))) {
      const pendingExpired = activeAppt.get("status") === "PENDING" && (activeAppt.get("pendingExpiresAt") as Timestamp).toMillis() <= nowMs;
      if (!pendingExpired) fail("failed-precondition", "ACTIVE_APPOINTMENT_EXISTS", { appointmentId: activeAppt.id });
      tx.update(activeAppt.ref, { status: "EXPIRED", updatedAt: FieldValue.serverTimestamp() });
      addEvent(tx, activeAppt.id, "PENDING", "EXPIRED", "system", "SYSTEM");
    }

    const live = liveIntervals(lock, nowMs);
    const endMinute = startMinute + ctx.totalMinutes;
    const requested = { start: startMinute, end: endMinute };
    if (live.some((i) => overlaps({ start: i.startMin, end: i.endMin }, requested))) {
      // Uzmanın başka bir salondaki randevusu da bu aralığı dolduruyor olabilir.
      fail("aborted", "SLOT_TAKEN");
    }
    const slots = computeSlots({
      localDate, now, totalMinutes: ctx.totalMinutes, slotMinutes: ctx.settings.slotMinutes,
      minLeadMinutes: ctx.settings.minLeadMinutes, horizonDays: ctx.settings.horizonDays,
      salonWeeklyClosedDays: ctx.salon.get("weeklyClosedDays") ?? [], salonClosures: ctx.salonClosures,
      staffSchedule: ctx.schedule, staffClosures: ctx.staffClosures, busy: busyOf(live),
    });
    if (!slots.some((s) => s.startAt.getTime() === startAt.getTime())) fail("failed-precondition", "SLOT_UNAVAILABLE");

    const ref = appts().doc();
    const endAt = new Date(startAt.getTime() + ctx.totalMinutes * 60_000);
    const pendingExpiresAt = computePendingExpiresAt(now, startAt, ctx.settings.pendingTtlMinutes);
    const number = bookingNumber(ref.id);
    const customerName = `${user.get("firstName")} ${user.get("lastName")}`;
    const ownerUid = ctx.salon.get("ownerUid") as string;

    tx.create(ref, {
      number,
      salonId,
      salonName: ctx.salon.get("name"),
      salonAddress: `${ctx.salon.get("addressLine")}, ${ctx.salon.get("district")} / ${ctx.salon.get("city")}`,
      staffUid,
      staffName: ctx.staffName,
      ownerUid,
      customerUid: caller.uid,
      customerName,
      customerPhoneE164: caller.phone,
      items: ctx.items,
      serviceIds: ctx.items.map((i) => i.serviceId),
      totalKurus: ctx.totalKurus,
      finalKurus: ctx.totalKurus,
      totalMinutes: ctx.totalMinutes,
      currency: "TRY",
      startAt: Timestamp.fromDate(startAt),
      endAt: Timestamp.fromDate(endAt),
      localDate,
      startMinuteOfDay: startMinute,
      endMinuteOfDay: endMinute,
      timezone: BUSINESS_ZONE,
      status: "PENDING",
      pendingExpiresAt: Timestamp.fromDate(pendingExpiresAt),
      cancelCutoffMinutes: ctx.settings.customerCancelCutoffMinutes,
      hiddenFor: [],
      flags: {},
      idempotencyKey: key,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    const intervals: LockInterval[] = [...live, { appointmentId: ref.id, salonId, startMin: startMinute, endMin: endMinute, expiresAtMs: pendingExpiresAt.getTime() }];
    tx.set(lock.ref, { staffUid, localDate, intervals, updatedAt: FieldValue.serverTimestamp() });
    tx.set(custLock.ref, { activeAppointmentId: ref.id, expiresAtMs: pendingExpiresAt.getTime(), updatedAt: FieldValue.serverTimestamp() });
    addEvent(tx, ref.id, null, "PENDING", caller.uid, "CUSTOMER");

    const when = `${local.setLocale("tr").toFormat("d LLLL cccc")} ${formatHm(startMinute)}`;
    notifyInTx(tx, staffUid, { type: "APPOINTMENT_REQUESTED", title: "Yeni randevu talebi", body: `${when} için onayınızı bekleyen bir talep var.`, salonId, appointmentId: ref.id });
    if (ownerUid !== staffUid) {
      notifyInTx(tx, ownerUid, { type: "APPOINTMENT_REQUESTED_SALON", title: "Salonunuza yeni randevu talebi", body: `${ctx.staffName} için ${when}.`, salonId, appointmentId: ref.id });
    }
    return { appointmentId: ref.id, number, status: "PENDING" as const, pendingExpiresAt: pendingExpiresAt.toISOString() };
  });
});

// ---------------------------------------------------------------------------

interface TransitionReq {
  appointmentId?: unknown;
  action?: unknown;
  reason?: unknown;
}

const ACTIONS: AppointmentAction[] = ["CONFIRM", "REJECT", "CANCEL_BY_CUSTOMER", "CANCEL_BY_STAFF", "MARK_ARRIVED", "MARK_NO_SHOW", "COMPLETE"];

/**
 * Randevu durum geçişleri (durum makinesi sunucuda denetlenir). Süresi dolmuş PENDING talep
 * herhangi bir eylemde önce EXPIRED yapılır ve sonuç { status: "EXPIRED", applied: false } döner.
 * COMPLETE: aynı transaction'da completion_{id} ledger kaydı + rapor sayaçları (çift sayım imkânsız).
 */
export const transitionAppointment = authedCallable<TransitionReq, { status: AppointmentStatus; applied: boolean }>(
  "transitionAppointment",
  async (data, caller) => {
    const appointmentId = docId(data.appointmentId, "appointmentId");
    const action = data.action as AppointmentAction;
    if (!ACTIONS.includes(action)) fail("invalid-argument", "INVALID_ARGUMENT", { field: "action" });
    const reason = optString(data.reason, "reason", 300) || null;

    return db.runTransaction(async (tx) => {
      const appt = await tx.get(appts().doc(appointmentId));
      if (!appt.exists) fail("not-found", "APPOINTMENT_NOT_FOUND");
      const salonId = appt.get("salonId") as string;
      const staffUid = appt.get("staffUid") as string;
      const customerUid = appt.get("customerUid") as string;
      const [salon, lock, custLock] = await Promise.all([
        tx.get(db.collection("salons").doc(salonId)),
        tx.get(lockRef(salonId, staffUid, appt.get("localDate"))),
        tx.get(customerLockRef(customerUid)),
      ]);
      const ownerUid = salon.get("ownerUid") as string;
      const actor = actorRoleFor(caller.uid, { salonOwnerUid: ownerUid, staffUid, customerUid }, action === "CANCEL_BY_CUSTOMER");
      if (!actor) fail("permission-denied", "PERMISSION_DENIED");

      const status = appt.get("status") as AppointmentStatus;
      const now = new Date();
      const startAt = (appt.get("startAt") as Timestamp).toDate();
      const pendingExpiresAt = (appt.get("pendingExpiresAt") as Timestamp).toDate();

      if (status === "PENDING" && now.getTime() >= pendingExpiresAt.getTime()) {
        tx.update(appt.ref, { status: "EXPIRED", updatedAt: FieldValue.serverTimestamp() });
        releaseLocks(tx, appt, lock, custLock);
        addEvent(tx, appointmentId, "PENDING", "EXPIRED", "system", "SYSTEM");
        return { status: "EXPIRED" as AppointmentStatus, applied: false };
      }

      const result = evaluateTransition({ status, action, actor, now, startAt, pendingExpiresAt, reason });
      if (!result.ok) {
        const code = result.reason === "PERMISSION_DENIED" ? "permission-denied" : "failed-precondition";
        fail(code, result.reason === "INVALID_TRANSITION" ? "STATE_CHANGED" : result.reason, { status });
      }
      const to = result.to;
      const update: Record<string, unknown> = { status: to, updatedAt: FieldValue.serverTimestamp() };
      if (to === "CANCELLED_BY_CUSTOMER" || to === "CANCELLED_BY_STAFF") {
        update.cancelReason = reason;
        update.cancelledBy = caller.uid;
        update.cancelledAt = FieldValue.serverTimestamp();
      }
      if (to === "CONFIRMED") {
        update.confirmedAt = FieldValue.serverTimestamp();
        // Onaylanan randevu artık süre aşımına uğramaz: kilitlerdeki son kullanma kaldırılır.
        const intervals = ((lock.get("intervals") as LockInterval[] | undefined) ?? []).map((i) =>
          i.appointmentId === appointmentId ? { ...i, expiresAtMs: null } : i,
        );
        tx.update(lock.ref, { intervals, updatedAt: FieldValue.serverTimestamp() });
        if (custLock.get("activeAppointmentId") === appointmentId) tx.update(custLock.ref, { expiresAtMs: null });
      }
      if (to === "IN_PROGRESS") update.arrivedAt = FieldValue.serverTimestamp();
      if (to === "COMPLETED") {
        // Gerçekleşen hizmet bedeli: finalKurus (yetkili düzeltmeler dahil). Liste tutarı ayrıca tutulur.
        const completion = await tx.get(ledgerCol().doc(`completion_${appointmentId}`));
        if (completion.exists) fail("failed-precondition", "STATE_CHANGED", { status: "COMPLETED" });
        update.completedAt = FieldValue.serverTimestamp();
        writeLedger(tx, {
          id: `completion_${appointmentId}`,
          type: "SERVICE_COMPLETED",
          salonId,
          staffUid,
          appointmentId,
          amountKurus: appt.get("finalKurus") as number,
          listedKurus: appt.get("totalKurus") as number,
          countDelta: 1,
          occurredAt: now,
          periodKeys: periodKeysOf(now),
          createdBy: caller.uid,
        });
      }
      tx.update(appt.ref, update);
      if (!ACTIVE_STATUSES.has(to)) releaseLocks(tx, appt, lock, custLock);
      addEvent(tx, appointmentId, status, to, caller.uid, actor, reason);

      // Bildirimler (kilit ekranına kişisel veri yazılmaz).
      const n = (uid: string, type: string, title: string, body: string) => notifyInTx(tx, uid, { type, title, body, salonId, appointmentId });
      const salonName = appt.get("salonName") as string;
      switch (to) {
        case "CONFIRMED":
          n(customerUid, "APPOINTMENT_CONFIRMED", "Randevunuz onaylandı", `${salonName} randevunuzu onayladı.`);
          break;
        case "CANCELLED_BY_STAFF":
          n(customerUid, "APPOINTMENT_CANCELLED", "Randevunuz iptal edildi", `${salonName} randevunuzu iptal etti.`);
          if (actor === "OWNER" && caller.uid !== staffUid) n(staffUid, "APPOINTMENT_CANCELLED", "Randevu iptal edildi", "Salon sahibi size atanmış bir randevuyu iptal etti.");
          break;
        case "COMPLETED":
          n(customerUid, "REVIEW_INVITE", "Hizmetiniz tamamlandı", `${salonName} deneyiminizi puanlamak ister misiniz?`);
          break;
        case "CANCELLED_BY_CUSTOMER":
          n(staffUid, "APPOINTMENT_CANCELLED", "Randevu iptal edildi", "Müşteri randevusunu iptal etti; saat yeniden müsait.");
          if (ownerUid !== staffUid) n(ownerUid, "APPOINTMENT_CANCELLED", "Salonunuzda randevu iptali", "Bir müşteri randevusunu iptal etti.");
          break;
        default:
          break;
      }
      return { status: to, applied: true };
    });
  },
);
