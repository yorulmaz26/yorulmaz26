import { Transaction } from "firebase-admin/firestore";
import { authedCallable, Caller } from "../lib/callable";
import { db, FieldValue, Timestamp } from "../lib/admin";
import { fail } from "../lib/errors";
import { docId, optString } from "../lib/validation";
import { auditInTx } from "../lib/audit";
import { notifyInTx } from "../lib/notify";
import {
  BookedInterval,
  ConflictReason,
  conflictReason,
  DateRangeClosure,
  DaySchedule,
  formatHm,
  localDateOf,
  ScheduleContext,
  validateClosureRange,
  validateDaySchedule,
  WeeklySchedule,
} from "../domain";

const MAX_SHIFTS_PER_DAY = 4;
const MAX_BREAKS_PER_DAY = 6;
const MAX_ACTIVE_CLOSURES = 100;

const salonRef = (salonId: string) => db.collection("salons").doc(salonId);
const memberRef = (salonId: string, uid: string) => salonRef(salonId).collection("members").doc(uid);

export interface ConflictView {
  appointmentId: string;
  staffUid: string;
  localDate: string;
  start: string;
  end: string;
  status: string;
  customerName: string;
  reason: ConflictReason;
}

interface FutureAppointment extends BookedInterval {
  id: string;
  staffUid: string;
  status: string;
  customerName: string;
}

/** Bugünden itibaren aktif (PENDING/CONFIRMED) randevular; staffUid verilirse yalnızca o uzman. */
async function futureAppointments(salonId: string, staffUid?: string): Promise<FutureAppointment[]> {
  let q = db.collection("appointments").where("salonId", "==", salonId);
  if (staffUid) q = q.where("staffUid", "==", staffUid);
  const snap = await q.where("status", "in", ["PENDING", "CONFIRMED"]).where("startAt", ">=", Timestamp.now()).get();
  return snap.docs.map((d) => ({
    id: d.id,
    staffUid: d.get("staffUid"),
    status: d.get("status"),
    customerName: d.get("customerName") ?? "",
    localDate: d.get("localDate"),
    startMinute: d.get("startMinuteOfDay"),
    endMinute: d.get("endMinuteOfDay"),
  }));
}

async function closuresOf(col: FirebaseFirestore.CollectionReference): Promise<DateRangeClosure[]> {
  const today = localDateOf(new Date());
  const snap = await col.where("endDate", ">=", today).get();
  return snap.docs.map((d) => ({ startDate: d.get("startDate"), endDate: d.get("endDate") }));
}

function asSchedule(data: FirebaseFirestore.DocumentData | undefined): WeeklySchedule {
  return { days: data?.days ?? {}, weeklyDaysOff: data?.weeklyDaysOff ?? [] };
}

/** Uzman bazında bağlam yükleyip (değişiklik uygulanmış haliyle) çakışan randevuları bulur. */
async function analyze(
  salonId: string,
  appointments: FutureAppointment[],
  override: (staffUid: string, base: ScheduleContext) => ScheduleContext,
): Promise<ConflictView[]> {
  if (appointments.length === 0) return [];
  const salon = await salonRef(salonId).get();
  const salonClosures = await closuresOf(salonRef(salonId).collection("closures"));
  const byStaff = new Map<string, ScheduleContext>();
  const out: ConflictView[] = [];
  for (const a of appointments) {
    let ctx = byStaff.get(a.staffUid);
    if (!ctx) {
      const [schedule, staffClosures] = await Promise.all([
        memberRef(salonId, a.staffUid).collection("schedule").doc("weekly").get(),
        closuresOf(memberRef(salonId, a.staffUid).collection("closures")),
      ]);
      ctx = override(a.staffUid, {
        salonWeeklyClosedDays: salon.get("weeklyClosedDays") ?? [],
        salonClosures,
        staffSchedule: asSchedule(schedule.data()),
        staffClosures,
      });
      byStaff.set(a.staffUid, ctx);
    }
    const reason = conflictReason(a, ctx);
    if (reason) {
      out.push({
        appointmentId: a.id,
        staffUid: a.staffUid,
        localDate: a.localDate,
        start: formatHm(a.startMinute),
        end: formatHm(a.endMinute),
        status: a.status,
        customerName: a.customerName,
        reason,
      });
    }
  }
  return out.sort((x, y) => (x.localDate + x.start).localeCompare(y.localDate + y.start));
}

/**
 * Çakışmalar sessizce bozulmaz: randevular "ilgilenilmesi gerekiyor" olarak işaretlenir ve
 * uzman ile salon sahibine bildirim düşer. Yeniden planlama / iptal randevu yönetiminden yapılır.
 */
function flagConflicts(tx: Transaction, salonId: string, ownerUid: string, actorUid: string, conflicts: ConflictView[], source: string) {
  if (conflicts.length === 0) return;
  for (const c of conflicts) {
    tx.update(db.collection("appointments").doc(c.appointmentId), {
      attention: { reason: c.reason, source, at: FieldValue.serverTimestamp() },
      updatedAt: FieldValue.serverTimestamp(),
    });
  }
  const recipients = new Set([...conflicts.map((c) => c.staffUid), ownerUid]);
  for (const uid of recipients) {
    const own = conflicts.filter((c) => c.staffUid === uid || uid === ownerUid).length;
    notifyInTx(tx, uid, {
      type: "SCHEDULE_CONFLICT",
      title: "Takvim değişikliği randevuları etkiliyor",
      body: `${own} randevu yeni takvimle çakışıyor. Randevular bölümünden yeniden planlayın veya gerekçeyle iptal edin.`,
      salonId,
    });
  }
  auditInTx(tx, { action: "SCHEDULE_CONFLICTS_FLAGGED", actorUid, salonId, data: { source, count: conflicts.length } });
}

interface ApplyFlags {
  dryRun?: unknown;
  acknowledgeConflicts?: unknown;
}

/** dryRun → yalnızca analiz; değilse çakışma varsa açık onay (acknowledgeConflicts) gerekir. */
function gate(flags: ApplyFlags, conflicts: ConflictView[]): "PREVIEW" | "APPLY" {
  if (flags.dryRun === true) return "PREVIEW";
  if (conflicts.length > 0 && flags.acknowledgeConflicts !== true) fail("failed-precondition", "CONFLICTS_NOT_ACKNOWLEDGED", { conflicts });
  return "APPLY";
}

async function requireActiveMember(salonId: string, caller: Caller) {
  const [salon, member] = await Promise.all([salonRef(salonId).get(), memberRef(salonId, caller.uid).get()]);
  if (!salon.exists) fail("not-found", "SALON_NOT_FOUND");
  if (!member.exists || member.get("status") !== "ACTIVE") fail("permission-denied", "PERMISSION_DENIED");
  return salon;
}

async function requireOwner(salonId: string, caller: Caller) {
  const salon = await salonRef(salonId).get();
  if (!salon.exists) fail("not-found", "SALON_NOT_FOUND");
  if (salon.get("ownerUid") !== caller.uid) fail("permission-denied", "PERMISSION_DENIED");
  return salon;
}

function parseWeekdays(value: unknown, field: string): number[] {
  if (!Array.isArray(value) || value.some((d) => !Number.isInteger(d) || d < 1 || d > 7)) {
    fail("invalid-argument", "INVALID_ARGUMENT", { field });
  }
  return [...new Set(value as number[])].sort();
}

function parseSchedule(daysRaw: unknown, offRaw: unknown): WeeklySchedule {
  if (!daysRaw || typeof daysRaw !== "object") fail("invalid-argument", "INVALID_ARGUMENT", { field: "days" });
  const days: Record<string, DaySchedule> = {};
  for (let d = 1; d <= 7; d++) {
    const raw = (daysRaw as Record<string, { shifts?: unknown; breaks?: unknown }>)[String(d)] ?? { shifts: [], breaks: [] };
    const shifts = Array.isArray(raw.shifts) ? raw.shifts : [];
    const breaks = Array.isArray(raw.breaks) ? raw.breaks : [];
    if (shifts.length > MAX_SHIFTS_PER_DAY || breaks.length > MAX_BREAKS_PER_DAY) fail("invalid-argument", "TOO_MANY_RANGES", { day: d });
    const day: DaySchedule = {
      shifts: shifts.map((r: { start?: unknown; end?: unknown }) => ({ start: String(r?.start), end: String(r?.end) })),
      breaks: breaks.map((r: { start?: unknown; end?: unknown }) => ({ start: String(r?.start), end: String(r?.end) })),
    };
    const err = validateDaySchedule(day);
    if (err) fail("invalid-argument", err, { day: d });
    days[String(d)] = day;
  }
  return { days, weeklyDaysOff: parseWeekdays(offRaw ?? [], "weeklyDaysOff") };
}

interface Result {
  applied: boolean;
  conflicts: ConflictView[];
}

// ---------------------------------------------------------------------------
// Uzmanın kendi programı
// ---------------------------------------------------------------------------

interface WeeklyReq extends ApplyFlags {
  salonId?: unknown;
  days?: unknown;
  weeklyDaysOff?: unknown;
}

/** Kişisel haftalık çalışma saatleri, molalar ve haftalık izin günleri (yalnızca kendisi). */
export const updateWeeklySchedule = authedCallable<WeeklyReq, Result>("updateWeeklySchedule", async (data, caller) => {
  const salonId = docId(data.salonId, "salonId");
  const schedule = parseSchedule(data.days, data.weeklyDaysOff);
  const salon = await requireActiveMember(salonId, caller);
  const appts = await futureAppointments(salonId, caller.uid);
  const conflicts = await analyze(salonId, appts, (_uid, base) => ({ ...base, staffSchedule: schedule }));
  if (gate(data, conflicts) === "PREVIEW") return { applied: false, conflicts };
  const ref = memberRef(salonId, caller.uid).collection("schedule").doc("weekly");
  await db.runTransaction(async (tx) => {
    const cur = await tx.get(ref);
    tx.set(ref, { ...schedule, version: ((cur.get("version") as number | undefined) ?? 0) + 1, updatedAt: FieldValue.serverTimestamp() });
    flagConflicts(tx, salonId, salon.get("ownerUid"), caller.uid, conflicts, "WEEKLY_SCHEDULE");
    auditInTx(tx, { action: "WEEKLY_SCHEDULE_UPDATED", actorUid: caller.uid, salonId, targetId: caller.uid });
  });
  return { applied: true, conflicts };
});

interface ClosureReq extends ApplyFlags {
  salonId?: unknown;
  closureId?: unknown;
  startDate?: unknown;
  endDate?: unknown;
  note?: unknown;
}

function parseClosure(data: ClosureReq) {
  const startDate = String(data.startDate ?? "");
  const endDate = String(data.endDate ?? "");
  const err = validateClosureRange(startDate, endDate, localDateOf(new Date()));
  if (err) fail("invalid-argument", err);
  return { startDate, endDate, note: optString(data.note, "note", 200) };
}

const inRange = (c: { startDate: string; endDate: string }) => (a: FutureAppointment) => a.localDate >= c.startDate && a.localDate <= c.endDate;

/** Kişisel özel tarih / aralık kapatma (ekle veya düzenle). */
export const upsertStaffDateClosure = authedCallable<ClosureReq, Result & { closureId: string }>(
  "upsertStaffDateClosure",
  async (data, caller) => {
    const salonId = docId(data.salonId, "salonId");
    const closure = parseClosure(data);
    const salon = await requireActiveMember(salonId, caller);
    const col = memberRef(salonId, caller.uid).collection("closures");
    const ref = data.closureId ? col.doc(docId(data.closureId, "closureId")) : col.doc();
    const appts = (await futureAppointments(salonId, caller.uid)).filter(inRange(closure));
    const conflicts = await analyze(salonId, appts, (_u, base) => ({ ...base, staffClosures: [...base.staffClosures, closure] }));
    if (gate(data, conflicts) === "PREVIEW") return { applied: false, conflicts, closureId: ref.id };
    if (!data.closureId && (await col.where("endDate", ">=", localDateOf(new Date())).count().get()).data().count >= MAX_ACTIVE_CLOSURES) {
      fail("resource-exhausted", "TOO_MANY_CLOSURES");
    }
    await db.runTransaction(async (tx) => {
      if (data.closureId && !(await tx.get(ref)).exists) fail("not-found", "CLOSURE_NOT_FOUND");
      tx.set(ref, { ...closure, staffUid: caller.uid, salonId, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      flagConflicts(tx, salonId, salon.get("ownerUid"), caller.uid, conflicts, "STAFF_CLOSURE");
    });
    return { applied: true, conflicts, closureId: ref.id };
  },
);

/** Kapatmayı kaldırmak randevuları bozamaz; yalnızca yeni müsaitlik açar. */
export const deleteStaffDateClosure = authedCallable<{ salonId?: unknown; closureId?: unknown }, { ok: true }>(
  "deleteStaffDateClosure",
  async (data, caller) => {
    const salonId = docId(data.salonId, "salonId");
    const closureId = docId(data.closureId, "closureId");
    await requireActiveMember(salonId, caller);
    await memberRef(salonId, caller.uid).collection("closures").doc(closureId).delete();
    return { ok: true };
  },
);

// ---------------------------------------------------------------------------
// Salon geneli (yalnızca sahibi)
// ---------------------------------------------------------------------------

interface SalonWeeklyReq extends ApplyFlags {
  salonId?: unknown;
  weeklyClosedDays?: unknown;
}

export const updateSalonClosedDays = authedCallable<SalonWeeklyReq, Result>("updateSalonClosedDays", async (data, caller) => {
  const salonId = docId(data.salonId, "salonId");
  const weeklyClosedDays = parseWeekdays(data.weeklyClosedDays, "weeklyClosedDays");
  if (weeklyClosedDays.length === 7) fail("invalid-argument", "ALL_DAYS_CLOSED");
  const salon = await requireOwner(salonId, caller);
  const conflicts = await analyze(salonId, await futureAppointments(salonId), (_u, base) => ({ ...base, salonWeeklyClosedDays: weeklyClosedDays }));
  if (gate(data, conflicts) === "PREVIEW") return { applied: false, conflicts };
  await db.runTransaction(async (tx) => {
    tx.update(salonRef(salonId), { weeklyClosedDays, updatedAt: FieldValue.serverTimestamp() });
    flagConflicts(tx, salonId, salon.get("ownerUid"), caller.uid, conflicts, "SALON_WEEKLY");
    auditInTx(tx, { action: "SALON_CLOSED_DAYS_UPDATED", actorUid: caller.uid, salonId, data: { weeklyClosedDays } });
  });
  return { applied: true, conflicts };
});

export const upsertSalonDateClosure = authedCallable<ClosureReq, Result & { closureId: string }>(
  "upsertSalonDateClosure",
  async (data, caller) => {
    const salonId = docId(data.salonId, "salonId");
    const closure = parseClosure(data);
    const salon = await requireOwner(salonId, caller);
    const col = salonRef(salonId).collection("closures");
    const ref = data.closureId ? col.doc(docId(data.closureId, "closureId")) : col.doc();
    const appts = (await futureAppointments(salonId)).filter(inRange(closure));
    const conflicts = await analyze(salonId, appts, (_u, base) => ({ ...base, salonClosures: [...base.salonClosures, closure] }));
    if (gate(data, conflicts) === "PREVIEW") return { applied: false, conflicts, closureId: ref.id };
    await db.runTransaction(async (tx) => {
      if (data.closureId && !(await tx.get(ref)).exists) fail("not-found", "CLOSURE_NOT_FOUND");
      // Kamuya açık koleksiyon: not alanı "publicNote" olarak saklanır (müşteriler görür).
      tx.set(ref, { startDate: closure.startDate, endDate: closure.endDate, publicNote: closure.note, salonId, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      flagConflicts(tx, salonId, salon.get("ownerUid"), caller.uid, conflicts, "SALON_CLOSURE");
    });
    return { applied: true, conflicts, closureId: ref.id };
  },
);

export const deleteSalonDateClosure = authedCallable<{ salonId?: unknown; closureId?: unknown }, { ok: true }>(
  "deleteSalonDateClosure",
  async (data, caller) => {
    const salonId = docId(data.salonId, "salonId");
    const closureId = docId(data.closureId, "closureId");
    await requireOwner(salonId, caller);
    await salonRef(salonId).collection("closures").doc(closureId).delete();
    return { ok: true };
  },
);

// ---------------------------------------------------------------------------
// Hizmet değişikliği etkisi (bilgilendirme)
// ---------------------------------------------------------------------------

/**
 * Hizmeti içeren gelecek aktif randevu sayısı. Randevular fiyat/süre snapshot'ı taşıdığı için
 * hizmet düzenleme veya silme mevcut randevuları değiştirmez; bu sayı kullanıcıya gösterilir.
 */
export const serviceImpact = authedCallable<{ salonId?: unknown; serviceId?: unknown }, { futureAppointments: number }>(
  "serviceImpact",
  async (data, caller) => {
    const salonId = docId(data.salonId, "salonId");
    const serviceId = docId(data.serviceId, "serviceId");
    const svc = await salonRef(salonId).collection("services").doc(serviceId).get();
    if (!svc.exists) fail("not-found", "SERVICE_NOT_FOUND");
    const salon = await salonRef(salonId).get();
    if (svc.get("staffUid") !== caller.uid && salon.get("ownerUid") !== caller.uid) fail("permission-denied", "PERMISSION_DENIED");
    const snap = await db
      .collection("appointments")
      .where("salonId", "==", salonId)
      .where("staffUid", "==", svc.get("staffUid"))
      .where("status", "in", ["PENDING", "CONFIRMED"])
      .where("startAt", ">=", Timestamp.now())
      .get();
    const count = snap.docs.filter((d) => ((d.get("items") as { serviceId: string }[] | undefined) ?? []).some((i) => i.serviceId === serviceId)).length;
    return { futureAppointments: count };
  },
);
