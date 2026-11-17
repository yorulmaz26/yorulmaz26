import { DocumentSnapshot, Transaction } from "firebase-admin/firestore";
import { db } from "../lib/admin";
import { fail } from "../lib/errors";
import { DateRangeClosure, Interval, totals, WeeklySchedule } from "../domain";
import { DEFAULT_BOOKING_SETTINGS } from "../salon/defaults";

export interface BookingSettings {
  slotMinutes: number;
  horizonDays: number;
  pendingTtlMinutes: number;
  customerCancelCutoffMinutes: number;
  minLeadMinutes: number;
}

/** Uzman × yerel gün kilidi. expiresAtMs doluysa PENDING'dir ve o andan sonra slotu tutmaz. */
export interface LockInterval {
  appointmentId: string;
  startMin: number;
  endMin: number;
  expiresAtMs: number | null;
}

export const lockId = (salonId: string, staffUid: string, localDate: string) => `${salonId}_${staffUid}_${localDate}`;
export const lockRef = (salonId: string, staffUid: string, localDate: string) => db.collection("staffDayLocks").doc(lockId(salonId, staffUid, localDate));

export function liveIntervals(snap: DocumentSnapshot | undefined, nowMs: number): LockInterval[] {
  const list = (snap?.get("intervals") as LockInterval[] | undefined) ?? [];
  return list.filter((i) => i.expiresAtMs == null || i.expiresAtMs > nowMs);
}

export const busyOf = (list: LockInterval[]): Interval[] => list.map((i) => ({ start: i.startMin, end: i.endMin }));

export interface ServiceSnapshot {
  serviceId: string;
  name: string;
  priceKurus: number;
  durationMinutes: number;
}

export interface BookingContext {
  salon: DocumentSnapshot;
  settings: BookingSettings;
  staffName: string;
  schedule: WeeklySchedule;
  staffClosures: DateRangeClosure[];
  salonClosures: DateRangeClosure[];
  items: ServiceSnapshot[];
  totalKurus: number;
  totalMinutes: number;
}

type Reader = Pick<Transaction, "get" | "getAll"> | null;

async function read(tx: Reader, ref: FirebaseFirestore.DocumentReference) {
  return tx ? tx.get(ref) : ref.get();
}
async function readQuery(tx: Reader, q: FirebaseFirestore.Query) {
  return tx ? tx.get(q) : q.get();
}

/**
 * Rezervasyon için gereken tüm veriler. Transaction içinde çağrıldığında okumalar
 * transaction'a dahil olur (eşzamanlı değişiklikte yeniden denenir).
 */
export async function loadBookingContext(
  tx: Reader,
  salonId: string,
  staffUid: string,
  /** null → hizmet doğrulaması yapılmaz (ör. snapshot'lı mevcut randevunun saatini değiştirme). */
  serviceIds: string[] | null,
  today: string,
): Promise<BookingContext> {
  const salonRef = db.collection("salons").doc(salonId);
  const memberRef = salonRef.collection("members").doc(staffUid);
  const [salon, member, schedule, staffCl, salonCl] = await Promise.all([
    read(tx, salonRef),
    read(tx, memberRef),
    read(tx, memberRef.collection("schedule").doc("weekly")),
    readQuery(tx, memberRef.collection("closures").where("endDate", ">=", today)),
    readQuery(tx, salonRef.collection("closures").where("endDate", ">=", today)),
  ]);
  if (!salon.exists || salon.get("status") !== "ACTIVE") fail("failed-precondition", "SALON_INACTIVE");
  if (!member.exists || member.get("status") !== "ACTIVE" || member.get("acceptsBookings") === false) fail("failed-precondition", "STAFF_INACTIVE");

  let svcs: DocumentSnapshot[] = [];
  if (serviceIds !== null) {
    const unique = [...new Set(serviceIds)];
    if (unique.length === 0 || unique.length > 10 || unique.length !== serviceIds.length) fail("invalid-argument", "INVALID_ARGUMENT", { field: "serviceIds" });
    const refs = unique.map((id) => salonRef.collection("services").doc(id));
    svcs = tx ? await tx.getAll(...refs) : await db.getAll(...refs);
  }
  const items: ServiceSnapshot[] = svcs.map((s) => {
    if (!s.exists || s.get("deleted") === true || s.get("active") !== true) fail("failed-precondition", "SERVICE_INACTIVE", { serviceId: s.id });
    // Seçili hizmetler yalnızca seçilen uzmana ait olabilir.
    if (s.get("staffUid") !== staffUid) fail("invalid-argument", "SERVICE_STAFF_MISMATCH", { serviceId: s.id });
    return { serviceId: s.id, name: s.get("name"), priceKurus: s.get("priceKurus"), durationMinutes: s.get("durationMinutes") };
  });
  const t = totals(items);
  const closures = (q: FirebaseFirestore.QuerySnapshot) => q.docs.map((d) => ({ startDate: d.get("startDate"), endDate: d.get("endDate") }));
  return {
    salon,
    settings: { ...DEFAULT_BOOKING_SETTINGS, ...(salon.get("bookingSettings") ?? {}) },
    staffName: `${member.get("firstName")} ${member.get("lastName")}`,
    schedule: { days: schedule.get("days") ?? {}, weeklyDaysOff: schedule.get("weeklyDaysOff") ?? [] },
    staffClosures: closures(staffCl),
    salonClosures: closures(salonCl),
    items,
    totalKurus: t.totalKurus,
    totalMinutes: t.totalMinutes,
  };
}
