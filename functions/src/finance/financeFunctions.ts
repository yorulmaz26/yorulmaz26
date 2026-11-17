import { authedCallable } from "../lib/callable";
import { db, FieldValue, Timestamp } from "../lib/admin";
import { fail } from "../lib/errors";
import { docId, reqString } from "../lib/validation";
import { auditInTx } from "../lib/audit";
import { notifyInTx } from "../lib/notify";
import { assertLocalDate, PeriodType, periodRange, PeriodKeys } from "../domain";
import { addEvent, appts } from "../booking/appointments";
import { aggregateRef, ledgerCol, writeLedger } from "./ledger";

export const MAX_FINAL_KURUS = 100_000_000;

/**
 * Yetkili fiyat düzeltmesi (gerekçe zorunlu, denetim kaydı):
 *  - IN_PROGRESS: atanmış uzman veya sahip; yalnızca finalKurus değişir (tamamlamada bu tutar yazılır).
 *  - COMPLETED: yalnızca sahip; ADJUSTMENT ledger hareketi (fark) orijinal tamamlama dönemlerine işlenir.
 */
export const adjustAppointmentPrice = authedCallable<{ appointmentId?: unknown; newFinalKurus?: unknown; reason?: unknown }, { finalKurus: number }>(
  "adjustAppointmentPrice",
  async (data, caller) => {
    const appointmentId = docId(data.appointmentId, "appointmentId");
    const newFinal = Number(data.newFinalKurus);
    if (!Number.isSafeInteger(newFinal) || newFinal < 0 || newFinal > MAX_FINAL_KURUS) fail("invalid-argument", "INVALID_ARGUMENT", { field: "newFinalKurus" });
    const reason = reqString(data.reason, "reason", 3, 300);

    return db.runTransaction(async (tx) => {
      const appt = await tx.get(appts().doc(appointmentId));
      if (!appt.exists) fail("not-found", "APPOINTMENT_NOT_FOUND");
      const salonId = appt.get("salonId") as string;
      const staffUid = appt.get("staffUid") as string;
      const salon = await tx.get(db.collection("salons").doc(salonId));
      const isOwner = salon.get("ownerUid") === caller.uid;
      const status = appt.get("status") as string;
      const current = appt.get("finalKurus") as number;
      if (newFinal === current) fail("failed-precondition", "NO_CHANGE");

      if (status === "IN_PROGRESS") {
        if (!isOwner && caller.uid !== staffUid) fail("permission-denied", "PERMISSION_DENIED");
      } else if (status === "COMPLETED") {
        if (!isOwner) fail("permission-denied", "PERMISSION_DENIED");
        const completion = await tx.get(ledgerCol().doc(`completion_${appointmentId}`));
        if (!completion.exists) fail("failed-precondition", "LEDGER_MISSING");
        const seq = ((appt.get("adjustmentCount") as number | undefined) ?? 0) + 1;
        writeLedger(tx, {
          id: `adjustment_${appointmentId}_${seq}`,
          type: "ADJUSTMENT",
          salonId,
          staffUid,
          appointmentId,
          amountKurus: newFinal - current,
          listedKurus: 0,
          countDelta: 0,
          occurredAt: new Date(),
          periodKeys: completion.get("periodKeys") as PeriodKeys,
          createdBy: caller.uid,
          reason,
        });
        tx.update(appt.ref, { adjustmentCount: seq });
      } else {
        fail("failed-precondition", "STATE_CHANGED", { status });
      }
      tx.update(appt.ref, { finalKurus: newFinal, updatedAt: FieldValue.serverTimestamp() });
      addEvent(tx, appointmentId, status as never, status as never, caller.uid, isOwner ? "OWNER" : "STAFF",
        `Fiyat düzeltmesi: ${(current / 100).toFixed(2)} → ${(newFinal / 100).toFixed(2)} TL (${reason})`);
      auditInTx(tx, { action: "PRICE_ADJUSTED", actorUid: caller.uid, salonId, targetId: appointmentId, data: { from: current, to: newFinal, reason, status } });
      if (status === "COMPLETED" && staffUid !== caller.uid) {
        notifyInTx(tx, staffUid, { type: "PRICE_ADJUSTED", title: "Tamamlanan randevuda fiyat düzeltildi", body: "Salon sahibi bir randevunuzun tutarını düzeltti.", salonId, appointmentId });
      }
      return { finalKurus: newFinal };
    });
  },
);

interface ReportReq {
  salonId?: unknown;
  periodType?: unknown;
  anchorDate?: unknown;
}

export interface ReportLine {
  completedCount: number;
  amountKurus: number;
  listedKurus: number;
}

export interface StaffReportLine extends ReportLine {
  staffUid: string;
  name: string;
  role: string;
  status: string;
}

const EMPTY: ReportLine = { completedCount: 0, amountKurus: 0, listedKurus: 0 };

/**
 * Dönem raporu. Sahip: salon toplamı + tüm uzmanların kırılımı. Uzman: yalnızca kendi satırı
 * (salon toplamı ve diğer uzmanlar dönmez). Tutarlar kuruş; "gerçekleşen hizmet bedeli"dir, tahsilat değildir.
 */
export const getReports = authedCallable<ReportReq, {
  period: { type: PeriodType; key: string; start: string; end: string };
  salon: ReportLine | null;
  staff: StaffReportLine[];
}>("getReports", async (data, caller) => {
  const salonId = docId(data.salonId, "salonId");
  const type = data.periodType as PeriodType;
  if (!["DAY", "WEEK", "MONTH"].includes(type)) fail("invalid-argument", "INVALID_ARGUMENT", { field: "periodType" });
  const anchor = String(data.anchorDate ?? "");
  try { assertLocalDate(anchor); } catch { fail("invalid-argument", "INVALID_DATE"); }
  const range = periodRange(type, anchor);

  const salon = await db.collection("salons").doc(salonId).get();
  if (!salon.exists) fail("not-found", "SALON_NOT_FOUND");
  const isOwner = salon.get("ownerUid") === caller.uid;
  const members = isOwner
    ? (await salon.ref.collection("members").get()).docs
    : [await salon.ref.collection("members").doc(caller.uid).get()].filter((m) => m.exists);
  if (!isOwner && (members.length === 0 || members[0].get("status") !== "ACTIVE")) fail("permission-denied", "PERMISSION_DENIED");

  const refs = [...(isOwner ? [aggregateRef(salonId, "ALL", range.key)] : []), ...members.map((m) => aggregateRef(salonId, m.id, range.key))];
  const snaps = await db.getAll(...refs);
  const line = (s: FirebaseFirestore.DocumentSnapshot): ReportLine =>
    s.exists ? { completedCount: s.get("completedCount") ?? 0, amountKurus: s.get("amountKurus") ?? 0, listedKurus: s.get("listedKurus") ?? 0 } : { ...EMPTY };
  const staffSnaps = isOwner ? snaps.slice(1) : snaps;
  return {
    period: { type, key: range.key, start: range.start.toISOString(), end: range.end.toISOString() },
    salon: isOwner ? line(snaps[0]) : null,
    staff: members
      .map((m, i) => ({
        staffUid: m.id,
        name: `${m.get("firstName")} ${m.get("lastName")}`,
        role: m.get("role"),
        status: m.get("status"),
        ...line(staffSnaps[i]),
      }))
      // Ayrılmış/pasif ve o dönem işlemi olmayanlar listeyi kalabalıklaştırmasın; aktifler her zaman görünür.
      .filter((x) => x.status === "ACTIVE" || x.completedCount !== 0 || x.amountKurus !== 0)
      .sort((a, b) => b.amountKurus - a.amountKurus || a.name.localeCompare(b.name, "tr")),
  };
});

/**
 * Bakım: rapor sayaçlarını değişmez ledger'dan yeniden üretir (yalnızca sahip, salon kapsamı).
 * Sayaçlar ile ledger tutarsızlaşırsa (ör. manuel müdahale) doğruluk kaynağı ledger'dır.
 */
export const rebuildReportAggregates = authedCallable<{ salonId?: unknown }, { entries: number; aggregates: number }>(
  "rebuildReportAggregates",
  async (data, caller) => {
    const salonId = docId(data.salonId, "salonId");
    const salon = await db.collection("salons").doc(salonId).get();
    if (salon.get("ownerUid") !== caller.uid) fail("permission-denied", "PERMISSION_DENIED");
    const entries = await ledgerCol().where("salonId", "==", salonId).get();
    const sums = new Map<string, { staffUid: string | null; periodType: string; periodKey: string } & ReportLine>();
    for (const e of entries.docs) {
      const keys = e.get("periodKeys") as PeriodKeys;
      const count = e.get("type") === "SERVICE_COMPLETED" ? 1 : e.get("type") === "REVERSAL" ? -1 : 0;
      for (const [periodType, key] of [["DAY", keys.day], ["WEEK", keys.week], ["MONTH", keys.month]] as const) {
        for (const scope of ["ALL", e.get("staffUid") as string]) {
          const id = `${salonId}_${scope}_${key}`;
          const cur = sums.get(id) ?? { staffUid: scope === "ALL" ? null : scope, periodType, periodKey: key, ...EMPTY };
          cur.completedCount += count;
          cur.amountKurus += e.get("amountKurus") as number;
          cur.listedKurus += (e.get("listedKurus") as number | undefined) ?? 0;
          sums.set(id, cur);
        }
      }
    }
    const old = await db.collection("reportAggregates").where("salonId", "==", salonId).get();
    const writer = db.bulkWriter();
    old.docs.filter((d) => !sums.has(d.id)).forEach((d) => writer.delete(d.ref));
    sums.forEach((v, id) => writer.set(db.collection("reportAggregates").doc(id), { salonId, ...v, updatedAt: FieldValue.serverTimestamp(), rebuiltAt: Timestamp.now() }));
    await writer.close();
    return { entries: entries.size, aggregates: sums.size };
  },
);

