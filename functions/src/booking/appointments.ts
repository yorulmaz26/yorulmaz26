import { DocumentSnapshot, Transaction } from "firebase-admin/firestore";
import { DateTime } from "luxon";
import { authedCallable } from "../lib/callable";
import { db, FieldValue, Timestamp } from "../lib/admin";
import { fail } from "../lib/errors";
import { docId, idempotencyKey, optString, personName } from "../lib/validation";
import { enforceRateLimit } from "../lib/rateLimit";
import { notifyInTx } from "../lib/notify";
import {
  ACTIVE_STATUSES,
  actorRoleFor,
  addDays,
  AppointmentAction,
  AppointmentStatus,
  BUSINESS_ZONE,
  codeProblem,
  computePendingExpiresAt,
  computeSlots,
  discountKurusOf,
  DISCOUNT_CODE_RE,
  evaluateTransition,
  formatHm,
  localDateOf,
  LoyaltyProgram,
  loyaltyRewardsAvailable,
  normalizeCode,
  normalizeTrPhone,
  overlaps,
} from "../domain";
import { busyOf, liveIntervals, LockInterval, loadBookingContext, lockRef } from "./context";
import { ledgerCol, periodKeysOf, writeLedger } from "../finance/ledger";

export const appts = () => db.collection("appointments");
export const customerLockRef = (uid: string) => db.collection("customerLocks").doc(uid);
/** Salon × müşteri istatistiği: tamamlanan/gelmeyen sayısı ve sadakat ödülü kullanımı. */
export const customerStatsRef = (salonId: string, uid: string) => db.collection("salons").doc(salonId).collection("customers").doc(uid);
export const discountCodeRef = (salonId: string, code: string) => db.collection("salons").doc(salonId).collection("discountCodes").doc(code);
export const discountUseRef = (salonId: string, code: string, uid: string) => db.collection("discountCodeUses").doc(`${salonId}_${code}_${uid}`);
export const waitlistRef = (staffUid: string, localDate: string, uid: string) => db.collection("waitlist").doc(`${staffUid}_${localDate}_${uid}`);

/** Bir müşterinin aynı anda tutabileceği aktif randevu sayısı (kendisi + başkası adına). */
export const MAX_ACTIVE_APPOINTMENTS = 3;
/** Başlangıcından bu kadar süre geçmiş ama kapatılmamış randevu sınıra sayılmaz (uzman işaretlemeyi unutmuş olabilir). */
const STALE_ACTIVE_MS = 12 * 3600_000;

/** Kullanıcıya gösterilen kısa randevu numarası (belge kimliğinden türetilir). */
export const bookingNumber = (id: string) => `KC-${id.slice(0, 8).toUpperCase()}`;

export function whenLabel(startAt: Date): string {
  const l = DateTime.fromJSDate(startAt, { zone: BUSINESS_ZONE }).setLocale("tr");
  return `${l.toFormat("d LLLL cccc")} ${l.toFormat("HH:mm")}`;
}

export function addEvent(tx: Transaction, appointmentId: string, from: AppointmentStatus | null, to: AppointmentStatus, actorUid: string, actorRole: string, reason?: string | null) {
  tx.create(appts().doc(appointmentId).collection("events").doc(), {
    from, to, actorUid, actorRole, reason: reason ?? null, at: FieldValue.serverTimestamp(),
  });
}

// ---------------------------------------------------------------------------
// Kilitler

export interface ActiveEntry {
  appointmentId: string;
  /** PENDING ise onay süresinin sonu; onaylıysa null. */
  expiresAtMs: number | null;
  startAtMs: number;
}

/** Müşterinin sınıra sayılan aktif randevuları (süresi dolmuş talepler ve bayat kayıtlar hariç). */
export function countedActive(custLock: DocumentSnapshot | null, nowMs: number): ActiveEntry[] {
  const list = (custLock?.get("active") as ActiveEntry[] | undefined) ?? [];
  return list.filter((e) => (e.expiresAtMs == null || e.expiresAtMs > nowMs) && e.startAtMs > nowMs - STALE_ACTIVE_MS);
}

export interface ApptLocks {
  /** Uzmanın kişi×gün kilidi. */
  staff: DocumentSnapshot;
  /** Müşterinin kendi kişi×gün kilidi (yalnızca kendisi için alınmış randevularda). */
  person: DocumentSnapshot | null;
  /** Müşterinin aktif randevu listesi (elden randevularda yok). */
  customer: DocumentSnapshot | null;
}

/** Randevunun bağlı olduğu tüm kilitleri okur (transaction'da yazmalardan önce çağrılmalı). */
export async function readLocks(tx: Transaction, a: DocumentSnapshot): Promise<ApptLocks> {
  const customerUid = a.get("customerUid") as string | null;
  const localDate = a.get("localDate") as string;
  const [staff, person, customer] = await Promise.all([
    tx.get(lockRef(a.get("staffUid"), localDate)),
    a.get("personLock") === true && customerUid ? tx.get(lockRef(customerUid, localDate)) : Promise.resolve(null),
    customerUid ? tx.get(customerLockRef(customerUid)) : Promise.resolve(null),
  ]);
  return { staff, person, customer };
}

function stripInterval(tx: Transaction, lock: DocumentSnapshot | null, appointmentId: string) {
  if (!lock?.exists) return;
  const intervals = ((lock.get("intervals") as LockInterval[] | undefined) ?? []).filter((i) => i.appointmentId !== appointmentId);
  tx.update(lock.ref, { intervals, updatedAt: FieldValue.serverTimestamp() });
}

/**
 * Terminal duruma geçişte randevuyu kilitlerden çıkarır. consumed=false ise (iptal, süre aşımı, gelmedi)
 * ayrılmış indirim kodu kullanımı ve sadakat ödülü iade edilir; tamamlanan randevuda tüketilmiş sayılır.
 */
export function releaseLocks(tx: Transaction, appt: DocumentSnapshot, locks: ApptLocks, consumed = false) {
  stripInterval(tx, locks.staff, appt.id);
  stripInterval(tx, locks.person, appt.id);
  if (locks.customer?.exists) {
    const active = ((locks.customer.get("active") as ActiveEntry[] | undefined) ?? []).filter((e) => e.appointmentId !== appt.id);
    tx.set(locks.customer.ref, { active, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  }
  if (consumed) return;
  const d = appt.get("discount") as { type: string; code?: string } | null | undefined;
  const salonId = appt.get("salonId") as string;
  const customerUid = appt.get("customerUid") as string | null;
  if (d?.type === "CODE" && d.code && customerUid) {
    tx.set(discountCodeRef(salonId, d.code), { usedCount: FieldValue.increment(-1) }, { merge: true });
    tx.delete(discountUseRef(salonId, d.code, customerUid));
  }
  if (d?.type === "LOYALTY" && customerUid) {
    tx.set(customerStatsRef(salonId, customerUid), { rewardsUsed: FieldValue.increment(-1) }, { merge: true });
  }
}

// ---------------------------------------------------------------------------

interface CreateReq {
  salonId?: unknown;
  staffUid?: unknown;
  serviceIds?: unknown;
  startAt?: unknown;
  expectedTotalKurus?: unknown;
  idempotencyKey?: unknown;
  /** Başkası adına randevu (ör. çocuk): hizmeti alacak kişinin adı. Boşsa müşterinin kendisi. */
  guestName?: unknown;
  discountCode?: unknown;
  useLoyaltyReward?: unknown;
}

export interface CreateRes {
  appointmentId: string;
  number: string;
  status: "PENDING";
  pendingExpiresAt: string;
  finalKurus: number;
}

export interface AppliedDiscount {
  type: "CODE" | "LOYALTY";
  code: string | null;
  percent: number | null;
  amountKurus: number | null;
  discountKurus: number;
}

/**
 * İndirim kodu / sadakat ödülünü transaction içinde değerlendirir. Okumaları yapar, yazmaları
 * geri dönen `commit` fonksiyonunda toplar (Firestore: önce tüm okumalar, sonra yazmalar).
 */
export async function evaluateDiscount(
  tx: Transaction,
  p: { salon: DocumentSnapshot; customerUid: string; totalKurus: number; today: string; code: string | null; useLoyalty: boolean },
): Promise<{ discount: AppliedDiscount | null; commit: () => void }> {
  const salonId = p.salon.id;
  if (p.code && p.useLoyalty) fail("invalid-argument", "DISCOUNT_CONFLICT");
  if (p.code) {
    if (!DISCOUNT_CODE_RE.test(p.code)) fail("failed-precondition", "CODE_NOT_FOUND");
    const [c, use] = await Promise.all([tx.get(discountCodeRef(salonId, p.code)), tx.get(discountUseRef(salonId, p.code, p.customerUid))]);
    if (!c.exists) fail("failed-precondition", "CODE_NOT_FOUND");
    const problem = codeProblem({
      active: c.get("active") === true, validFrom: c.get("validFrom") ?? null, validUntil: c.get("validUntil") ?? null,
      maxUses: c.get("maxUses") ?? null, usedCount: c.get("usedCount") ?? 0,
    }, p.today);
    if (problem) fail("failed-precondition", problem);
    if (use.exists) fail("failed-precondition", "CODE_ALREADY_USED");
    const percent = (c.get("percent") as number | null) ?? null;
    const amountKurus = (c.get("amountKurus") as number | null) ?? null;
    const discount: AppliedDiscount = { type: "CODE", code: p.code, percent, amountKurus, discountKurus: discountKurusOf(p.totalKurus, { percent, amountKurus }) };
    return {
      discount,
      commit: () => {
        tx.update(c.ref, { usedCount: FieldValue.increment(1), updatedAt: FieldValue.serverTimestamp() });
        tx.set(use.ref, { salonId, code: p.code, customerUid: p.customerUid, createdAt: FieldValue.serverTimestamp() });
      },
    };
  }
  if (p.useLoyalty) {
    const program = p.salon.get("loyalty") as LoyaltyProgram | undefined;
    const stats = await tx.get(customerStatsRef(salonId, p.customerUid));
    const available = loyaltyRewardsAvailable(program, stats.get("completedCount") ?? 0, stats.get("rewardsUsed") ?? 0);
    if (!program || available < 1) fail("failed-precondition", "LOYALTY_NOT_AVAILABLE");
    const discount: AppliedDiscount = {
      type: "LOYALTY", code: null, percent: program.rewardPercent, amountKurus: null,
      discountKurus: discountKurusOf(p.totalKurus, { percent: program.rewardPercent }),
    };
    return { discount, commit: () => tx.set(stats.ref, { rewardsUsed: FieldValue.increment(1), updatedAt: FieldValue.serverTimestamp() }, { merge: true }) };
  }
  return { discount: null, commit: () => undefined };
}

/**
 * Randevu talebi. Tek transaction içinde:
 *  - salon/uzman/hizmetler/program/kapatmalar yeniden okunur, fiyat ve müsaitlik yeniden doğrulanır,
 *  - uzmanın kişi×gün kilidi → çakışma varsa SLOT_TAKEN (aynı uzmana çift randevu imkânsız, salonlar arası dahil),
 *  - müşteri kendisi için alıyorsa kendi kişi×gün kilidi → çakışma varsa CUSTOMER_BUSY
 *    (aynı saatte başka randevusu var ya da o saatte uzman olarak hizmet veriyor),
 *  - aktif randevu sayısı en fazla MAX_ACTIVE_APPOINTMENTS,
 *  - indirim kodu / sadakat ödülü doğrulanır ve ayrılır,
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
  const guestName = data.guestName === undefined || data.guestName === null || data.guestName === "" ? null : personName(data.guestName, "guestName");
  const code = data.discountCode === undefined || data.discountCode === null || data.discountCode === "" ? null : normalizeCode(String(data.discountCode));
  const useLoyalty = data.useLoyaltyReward === true;
  if (staffUid === caller.uid) fail("failed-precondition", "CANNOT_BOOK_SELF");

  const startAt = start.toJSDate();
  const local = start.setZone(BUSINESS_ZONE);
  const localDate = local.toISODate()!;
  const startMinute = local.hour * 60 + local.minute;
  const forSelf = guestName === null;

  // Ağ tekrarı: aynı anahtarla oluşturulmuş randevu varsa onu döndür.
  const prior = await appts().where("customerUid", "==", caller.uid).where("idempotencyKey", "==", key).limit(1).get();
  if (!prior.empty) {
    const d = prior.docs[0];
    return { appointmentId: d.id, number: d.get("number"), status: "PENDING", pendingExpiresAt: (d.get("pendingExpiresAt") as Timestamp).toDate().toISOString(), finalKurus: d.get("finalKurus") };
  }
  await enforceRateLimit(`book_${caller.uid}`, 10, 3600);

  return db.runTransaction(async (tx) => {
    const now = new Date();
    const nowMs = now.getTime();
    const today = localDateOf(now);
    const ctx = await loadBookingContext(tx, salonId, staffUid, serviceIds, today);
    const [lock, personLock, custLock, user] = await Promise.all([
      tx.get(lockRef(staffUid, localDate)),
      forSelf ? tx.get(lockRef(caller.uid, localDate)) : Promise.resolve(null),
      tx.get(customerLockRef(caller.uid)),
      tx.get(db.collection("users").doc(caller.uid)),
    ]);
    if (!user.exists) fail("failed-precondition", "PROFILE_REQUIRED");
    if (ctx.totalKurus !== expected) fail("failed-precondition", "PRICE_CHANGED", { newTotalKurus: ctx.totalKurus, totalMinutes: ctx.totalMinutes });

    const active = countedActive(custLock, nowMs);
    if (active.length >= MAX_ACTIVE_APPOINTMENTS) fail("failed-precondition", "ACTIVE_APPOINTMENT_LIMIT", { limit: MAX_ACTIVE_APPOINTMENTS });

    const live = liveIntervals(lock, nowMs);
    const endMinute = startMinute + ctx.totalMinutes;
    const requested = { start: startMinute, end: endMinute };
    // Uzmanın başka bir salondaki randevusu (veya kendi aldığı randevu) da bu aralığı dolduruyor olabilir.
    if (live.some((i) => overlaps({ start: i.startMin, end: i.endMin }, requested))) fail("aborted", "SLOT_TAKEN");
    const personLive = forSelf ? liveIntervals(personLock ?? undefined, nowMs) : [];
    if (personLive.some((i) => overlaps({ start: i.startMin, end: i.endMin }, requested))) fail("failed-precondition", "CUSTOMER_BUSY");

    const slots = computeSlots({
      localDate, now, totalMinutes: ctx.totalMinutes, slotMinutes: ctx.settings.slotMinutes,
      minLeadMinutes: ctx.settings.minLeadMinutes, horizonDays: ctx.settings.horizonDays,
      salonWeeklyClosedDays: ctx.salon.get("weeklyClosedDays") ?? [], salonClosures: ctx.salonClosures,
      staffSchedule: ctx.schedule, staffClosures: ctx.staffClosures, busy: busyOf(live),
    });
    if (!slots.some((s) => s.startAt.getTime() === startAt.getTime())) fail("failed-precondition", "SLOT_UNAVAILABLE");

    const disc = await evaluateDiscount(tx, { salon: ctx.salon, customerUid: caller.uid, totalKurus: ctx.totalKurus, today, code, useLoyalty });
    const finalKurus = ctx.totalKurus - (disc.discount?.discountKurus ?? 0);

    // ---- yazmalar
    const ref = appts().doc();
    const endAt = new Date(startAt.getTime() + ctx.totalMinutes * 60_000);
    const pendingExpiresAt = computePendingExpiresAt(now, startAt, ctx.settings.pendingTtlMinutes);
    const number = bookingNumber(ref.id);
    const bookerName = `${user.get("firstName")} ${user.get("lastName")}`;
    const ownerUid = ctx.salon.get("ownerUid") as string;
    const expMs = pendingExpiresAt.getTime();

    tx.create(ref, {
      number,
      salonId,
      salonName: ctx.salon.get("name"),
      salonAddress: `${ctx.salon.get("addressLine")}, ${ctx.salon.get("district")} / ${ctx.salon.get("city")}`,
      staffUid,
      staffName: ctx.staffName,
      ownerUid,
      customerUid: caller.uid,
      // Hizmeti alacak kişi: başkası adına ise misafirin adı; randevu sahibi ayrıca tutulur.
      customerName: guestName ?? bookerName,
      bookedByName: bookerName,
      guestName,
      customerPhoneE164: caller.phone,
      walkIn: false,
      personLock: forSelf,
      items: ctx.items,
      serviceIds: ctx.items.map((i) => i.serviceId),
      totalKurus: ctx.totalKurus,
      discount: disc.discount,
      finalKurus,
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
    tx.set(lock.ref, {
      staffUid, localDate, updatedAt: FieldValue.serverTimestamp(),
      intervals: [...live, { appointmentId: ref.id, salonId, startMin: startMinute, endMin: endMinute, expiresAtMs: expMs, role: "STAFF" }],
    });
    if (forSelf && personLock) {
      tx.set(personLock.ref, {
        staffUid: caller.uid, localDate, updatedAt: FieldValue.serverTimestamp(),
        intervals: [...personLive, { appointmentId: ref.id, salonId, startMin: startMinute, endMin: endMinute, expiresAtMs: expMs, role: "CUSTOMER" }],
      });
    }
    tx.set(custLock.ref, { active: [...active, { appointmentId: ref.id, expiresAtMs: expMs, startAtMs: startAt.getTime() }], updatedAt: FieldValue.serverTimestamp() });
    disc.commit();
    // Bekleme listesindeyse artık gerek yok.
    tx.delete(waitlistRef(staffUid, localDate, caller.uid));
    addEvent(tx, ref.id, null, "PENDING", caller.uid, "CUSTOMER");

    const when = `${local.setLocale("tr").toFormat("d LLLL cccc")} ${formatHm(startMinute)}`;
    notifyInTx(tx, staffUid, { type: "APPOINTMENT_REQUESTED", title: "Yeni randevu talebi", body: `${when} için onayınızı bekleyen bir talep var.`, salonId, appointmentId: ref.id });
    if (ownerUid !== staffUid) {
      notifyInTx(tx, ownerUid, { type: "APPOINTMENT_REQUESTED_SALON", title: "Salonunuza yeni randevu talebi", body: `${ctx.staffName} için ${when}.`, salonId, appointmentId: ref.id });
    }
    return { appointmentId: ref.id, number, status: "PENDING" as const, pendingExpiresAt: pendingExpiresAt.toISOString(), finalKurus };
  });
});

// ---------------------------------------------------------------------------

interface WalkInReq {
  salonId?: unknown;
  staffUid?: unknown;
  serviceIds?: unknown;
  startAt?: unknown;
  customerName?: unknown;
  customerPhone?: unknown;
  idempotencyKey?: unknown;
}

/**
 * Elden randevu: telefonla arayan ya da kapıdan gelen, uygulaması olmayan müşteri için uzman (kendi adına)
 * veya salon sahibi (herhangi bir uzman adına) kayıt açar. Doğrudan CONFIRMED olur; uzmanın takvimini
 * kilitler, böylece uygulamadan aynı saate randevu verilemez. Uzman programı dışı saat de seçilebilir
 * (salon bilerek açıyor); yalnızca çakışma ve ufuk denetlenir. Müşteriye bildirim gönderilmez.
 */
export const createWalkInAppointment = authedCallable<WalkInReq, { appointmentId: string; number: string }>(
  "createWalkInAppointment",
  async (data, caller) => {
    const salonId = docId(data.salonId, "salonId");
    const staffUid = docId(data.staffUid, "staffUid");
    const key = idempotencyKey(data.idempotencyKey);
    if (!Array.isArray(data.serviceIds)) fail("invalid-argument", "INVALID_ARGUMENT", { field: "serviceIds" });
    const serviceIds = (data.serviceIds as unknown[]).map((s) => docId(s, "serviceIds"));
    const customerName = personName(data.customerName, "customerName");
    const rawPhone = optString(data.customerPhone, "customerPhone", 20);
    const phone = rawPhone ? normalizeTrPhone(rawPhone) : null;
    if (rawPhone && !phone) fail("invalid-argument", "INVALID_PHONE", { field: "customerPhone" });
    const start = DateTime.fromISO(String(data.startAt ?? ""), { zone: "utc" });
    if (!start.isValid || start.minute % 5 !== 0) fail("invalid-argument", "INVALID_ARGUMENT", { field: "startAt" });

    const prior = await appts().where("createdBy", "==", caller.uid).where("idempotencyKey", "==", key).limit(1).get();
    if (!prior.empty) return { appointmentId: prior.docs[0].id, number: prior.docs[0].get("number") };
    await enforceRateLimit(`walkin_${caller.uid}`, 60, 3600);

    const startAt = start.toJSDate();
    const local = start.setZone(BUSINESS_ZONE);
    const localDate = local.toISODate()!;
    const startMinute = local.hour * 60 + local.minute;

    return db.runTransaction(async (tx) => {
      const now = new Date();
      const today = localDateOf(now);
      const ctx = await loadBookingContext(tx, salonId, staffUid, serviceIds, today);
      const ownerUid = ctx.salon.get("ownerUid") as string;
      if (caller.uid !== staffUid && caller.uid !== ownerUid) fail("permission-denied", "PERMISSION_DENIED");
      if (startAt.getTime() < now.getTime() - 60 * 60_000) fail("failed-precondition", "START_IN_PAST");
      if (localDate > addDays(today, ctx.settings.horizonDays)) fail("failed-precondition", "BEYOND_HORIZON");
      const endMinute = startMinute + ctx.totalMinutes;
      if (endMinute > 24 * 60) fail("failed-precondition", "CROSSES_MIDNIGHT");
      const lock = await tx.get(lockRef(staffUid, localDate));
      const live = liveIntervals(lock, now.getTime());
      if (live.some((i) => overlaps({ start: i.startMin, end: i.endMin }, { start: startMinute, end: endMinute }))) fail("aborted", "SLOT_TAKEN");

      const ref = appts().doc();
      const number = bookingNumber(ref.id);
      tx.create(ref, {
        number, salonId, salonName: ctx.salon.get("name"),
        salonAddress: `${ctx.salon.get("addressLine")}, ${ctx.salon.get("district")} / ${ctx.salon.get("city")}`,
        staffUid, staffName: ctx.staffName, ownerUid,
        customerUid: null, customerName, bookedByName: null, guestName: null, customerPhoneE164: phone,
        walkIn: true, personLock: false, createdBy: caller.uid,
        items: ctx.items, serviceIds: ctx.items.map((i) => i.serviceId),
        totalKurus: ctx.totalKurus, discount: null, finalKurus: ctx.totalKurus, totalMinutes: ctx.totalMinutes, currency: "TRY",
        startAt: Timestamp.fromDate(startAt), endAt: Timestamp.fromMillis(startAt.getTime() + ctx.totalMinutes * 60_000),
        localDate, startMinuteOfDay: startMinute, endMinuteOfDay: endMinute, timezone: BUSINESS_ZONE,
        status: "CONFIRMED", pendingExpiresAt: Timestamp.fromDate(startAt), confirmedAt: FieldValue.serverTimestamp(),
        cancelCutoffMinutes: ctx.settings.customerCancelCutoffMinutes,
        hiddenFor: [], flags: {}, idempotencyKey: key,
        createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
      });
      tx.set(lock.ref, {
        staffUid, localDate, updatedAt: FieldValue.serverTimestamp(),
        intervals: [...live, { appointmentId: ref.id, salonId, startMin: startMinute, endMin: endMinute, expiresAtMs: null, role: "STAFF" }],
      });
      const actor = caller.uid === ownerUid ? "OWNER" : "STAFF";
      addEvent(tx, ref.id, null, "CONFIRMED", caller.uid, actor, "Elden randevu");
      if (caller.uid !== staffUid) {
        notifyInTx(tx, staffUid, { type: "APPOINTMENT_ASSIGNED", title: "Size bir randevu eklendi", body: `Salon sahibi ${whenLabel(startAt)} için elden randevu ekledi.`, salonId, appointmentId: ref.id });
      }
      return { appointmentId: ref.id, number };
    });
  },
);

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
 * Müşteri son 2 saatte iptal edemez (CANCEL_WINDOW_CLOSED); uzman ve sahip her zaman iptal edebilir.
 * COMPLETE: aynı transaction'da completion_{id} ledger kaydı (komisyon dahil) + rapor sayaçları.
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
      const customerUid = (appt.get("customerUid") as string | null) ?? null;
      const salonRef = db.collection("salons").doc(salonId);
      const [salon, locks, member, completion] = await Promise.all([
        tx.get(salonRef),
        readLocks(tx, appt),
        action === "COMPLETE" ? tx.get(salonRef.collection("staffPrivate").doc(staffUid)) : Promise.resolve(null),
        action === "COMPLETE" ? tx.get(ledgerCol().doc(`completion_${appointmentId}`)) : Promise.resolve(null),
      ]);
      const ownerUid = salon.get("ownerUid") as string;
      const actor = actorRoleFor(caller.uid, { salonOwnerUid: ownerUid, staffUid, customerUid: customerUid ?? "" }, action === "CANCEL_BY_CUSTOMER");
      if (!actor) fail("permission-denied", "PERMISSION_DENIED");

      const status = appt.get("status") as AppointmentStatus;
      const now = new Date();
      const startAt = (appt.get("startAt") as Timestamp).toDate();
      const pendingExpiresAt = (appt.get("pendingExpiresAt") as Timestamp).toDate();

      if (status === "PENDING" && now.getTime() >= pendingExpiresAt.getTime()) {
        tx.update(appt.ref, { status: "EXPIRED", updatedAt: FieldValue.serverTimestamp() });
        releaseLocks(tx, appt, locks);
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
        update.cancelledByRole = actor;
        update.cancelledAt = FieldValue.serverTimestamp();
      }
      if (to === "CONFIRMED") {
        update.confirmedAt = FieldValue.serverTimestamp();
        // Onaylanan randevu artık süre aşımına uğramaz: kilitlerdeki son kullanma kaldırılır.
        for (const l of [locks.staff, locks.person]) {
          if (!l?.exists) continue;
          const intervals = ((l.get("intervals") as LockInterval[] | undefined) ?? []).map((i) => (i.appointmentId === appointmentId ? { ...i, expiresAtMs: null } : i));
          tx.update(l.ref, { intervals, updatedAt: FieldValue.serverTimestamp() });
        }
        if (locks.customer?.exists) {
          const active = ((locks.customer.get("active") as ActiveEntry[] | undefined) ?? []).map((e) => (e.appointmentId === appointmentId ? { ...e, expiresAtMs: null } : e));
          tx.update(locks.customer.ref, { active });
        }
      }
      if (to === "IN_PROGRESS") update.arrivedAt = FieldValue.serverTimestamp();
      if (to === "COMPLETED") {
        // Gerçekleşen hizmet bedeli: finalKurus (indirim ve yetkili düzeltmeler dahil). Liste tutarı ayrıca tutulur.
        if (completion?.exists) fail("failed-precondition", "STATE_CHANGED", { status: "COMPLETED" });
        const amount = appt.get("finalKurus") as number;
        const commissionPercent = (member?.get("commissionPercent") as number | undefined) ?? 0;
        update.completedAt = FieldValue.serverTimestamp();
        update.commissionPercent = commissionPercent;
        writeLedger(tx, {
          id: `completion_${appointmentId}`,
          type: "SERVICE_COMPLETED",
          salonId,
          staffUid,
          appointmentId,
          amountKurus: amount,
          listedKurus: appt.get("totalKurus") as number,
          commissionPercent,
          commissionKurus: Math.round((amount * commissionPercent) / 100),
          countDelta: 1,
          occurredAt: now,
          periodKeys: periodKeysOf(now),
          createdBy: caller.uid,
        });
      }
      tx.update(appt.ref, update);
      if (!ACTIVE_STATUSES.has(to)) releaseLocks(tx, appt, locks, to === "COMPLETED");
      addEvent(tx, appointmentId, status, to, caller.uid, actor, reason);

      // Salon × müşteri istatistiği (gelmeyen müşteri uyarısı ve sadakat programı için).
      if (customerUid && (to === "COMPLETED" || to === "NO_SHOW")) {
        tx.set(customerStatsRef(salonId, customerUid), {
          customerUid,
          salonId,
          ...(to === "COMPLETED" ? { completedCount: FieldValue.increment(1), lastVisitAt: FieldValue.serverTimestamp() } : { noShowCount: FieldValue.increment(1), lastNoShowAt: FieldValue.serverTimestamp() }),
          updatedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
      }

      // Bildirimler (kilit ekranına kişisel veri yazılmaz). Elden randevuda müşteri bildirimi yoktur.
      const n = (uid: string | null, type: string, title: string, body: string) => notifyInTx(tx, uid, { type, title, body, salonId, appointmentId });
      const salonName = appt.get("salonName") as string;
      const when = whenLabel(startAt);
      switch (to) {
        case "CONFIRMED":
          n(customerUid, "APPOINTMENT_CONFIRMED", "Randevunuz onaylandı", `${salonName} ${when} randevunuzu onayladı.`);
          break;
        case "CANCELLED_BY_STAFF":
          n(customerUid, "APPOINTMENT_CANCELLED", "Randevunuz iptal edildi", `${salonName} ${when} randevunuzu iptal etti.`);
          // İptali yapmayan salon tarafı da haberdar olur.
          if (caller.uid !== staffUid) n(staffUid, "APPOINTMENT_CANCELLED", "Randevu iptal edildi", `Salon sahibi ${when} randevunuzu iptal etti.`);
          if (caller.uid !== ownerUid) n(ownerUid, "APPOINTMENT_CANCELLED", "Uzman bir randevuyu iptal etti", `${appt.get("staffName")}, ${when} randevusunu iptal etti.`);
          break;
        case "COMPLETED":
          n(customerUid, "REVIEW_INVITE", "Hizmetiniz tamamlandı", `${salonName} deneyiminizi puanlamak ister misiniz?`);
          break;
        case "CANCELLED_BY_CUSTOMER":
          n(staffUid, "APPOINTMENT_CANCELLED", "Müşteri randevuyu iptal etti", `${when} randevusu iptal edildi; saat yeniden müsait.`);
          if (ownerUid !== staffUid) n(ownerUid, "APPOINTMENT_CANCELLED", "Salonunuzda randevu iptali", `${appt.get("staffName")} için ${when} randevusu müşteri tarafından iptal edildi.`);
          break;
        default:
          break;
      }
      return { status: to, applied: true };
    });
  },
);

// ---------------------------------------------------------------------------

/**
 * Son 2 saatte müşteri uygulamadan iptal edemez; bunun yerine salonla iletişime geçer. Bu çağrı
 * yalnızca randevunun müşterisine, aktif randevu için uzmanın ve salon sahibinin telefonunu verir.
 */
export const getAppointmentContacts = authedCallable<{ appointmentId?: unknown }, { contacts: { role: "STAFF" | "OWNER" | "SALON"; name: string; phoneE164: string }[] }>(
  "getAppointmentContacts",
  async (data, caller) => {
    const appointmentId = docId(data.appointmentId, "appointmentId");
    const appt = await appts().doc(appointmentId).get();
    if (!appt.exists) fail("not-found", "APPOINTMENT_NOT_FOUND");
    if (appt.get("customerUid") !== caller.uid) fail("permission-denied", "PERMISSION_DENIED");
    if (!ACTIVE_STATUSES.has(appt.get("status"))) fail("failed-precondition", "STATE_CHANGED", { status: appt.get("status") });
    const salon = await db.collection("salons").doc(appt.get("salonId")).get();
    const staffUid = appt.get("staffUid") as string;
    const ownerUid = salon.get("ownerUid") as string;
    const [staffUser, ownerUser] = await db.getAll(db.collection("users").doc(staffUid), db.collection("users").doc(ownerUid));
    const contacts: { role: "STAFF" | "OWNER" | "SALON"; name: string; phoneE164: string }[] = [];
    if (staffUser.get("phoneE164")) contacts.push({ role: "STAFF", name: appt.get("staffName"), phoneE164: staffUser.get("phoneE164") });
    if (ownerUid !== staffUid && ownerUser.get("phoneE164")) contacts.push({ role: "OWNER", name: salon.get("ownerDisplayName") ?? "Salon sahibi", phoneE164: ownerUser.get("phoneE164") });
    if (salon.get("businessPhoneE164")) contacts.push({ role: "SALON", name: salon.get("name"), phoneE164: salon.get("businessPhoneE164") });
    return { contacts };
  },
);
