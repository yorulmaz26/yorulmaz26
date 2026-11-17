import { authedCallable } from "../lib/callable";
import { db } from "../lib/admin";
import { fail } from "../lib/errors";
import { docId } from "../lib/validation";
import { addDays, assertLocalDate, computeSlots, localDateOf } from "../domain";
import { busyOf, liveIntervals, loadBookingContext, lockRef } from "./context";

interface Req {
  /** Yeniden planlama modu: mevcut randevunun süresi kullanılır ve kendisi meşgul sayılmaz. */
  appointmentId?: unknown;
  salonId?: unknown;
  staffUid?: unknown;
  serviceIds?: unknown;
  fromDate?: unknown;
  days?: unknown;
}

export interface AvailabilityDay {
  date: string;
  slots: { startAt: string; label: string }[];
}

/**
 * Sunucunun güvenilir müsaitlik hesabı (docs/ARCHITECTURE.md §6). Gösterilen slot garanti değildir;
 * createAppointment transaction içinde yeniden doğrular.
 */
export const getAvailability = authedCallable<Req, { totalKurus: number; totalMinutes: number; horizonDays: number; slotMinutes: number; days: AvailabilityDay[] }>(
  "getAvailability",
  async (data, caller) => {
    let salonId: string;
    let staffUid: string;
    let serviceIds: string[] | null;
    let excludeId: string | null = null;
    let fixedMinutes: number | null = null;
    if (data.appointmentId !== undefined) {
      excludeId = docId(data.appointmentId, "appointmentId");
      const appt = await db.collection("appointments").doc(excludeId).get();
      if (!appt.exists) fail("not-found", "APPOINTMENT_NOT_FOUND");
      salonId = appt.get("salonId");
      staffUid = appt.get("staffUid");
      const owner = (await db.collection("salons").doc(salonId).get()).get("ownerUid");
      if (caller.uid !== staffUid && caller.uid !== owner) fail("permission-denied", "PERMISSION_DENIED");
      serviceIds = null;
      fixedMinutes = appt.get("totalMinutes");
    } else {
      salonId = docId(data.salonId, "salonId");
      staffUid = docId(data.staffUid, "staffUid");
      if (!Array.isArray(data.serviceIds)) fail("invalid-argument", "INVALID_ARGUMENT", { field: "serviceIds" });
      serviceIds = (data.serviceIds as unknown[]).map((s) => docId(s, "serviceIds"));
    }
    const now = new Date();
    const today = localDateOf(now);
    const fromDate = typeof data.fromDate === "string" ? data.fromDate : today;
    try {
      assertLocalDate(fromDate);
    } catch {
      fail("invalid-argument", "INVALID_DATE");
    }
    const days = Math.min(Math.max(Number(data.days ?? 7), 1), 14);

    const ctx = await loadBookingContext(null, salonId, staffUid, serviceIds, today);
    const dates = Array.from({ length: days }, (_, i) => addDays(fromDate < today ? today : fromDate, i));
    const locks = await db.getAll(...dates.map((d) => lockRef(salonId, staffUid, d)));
    const nowMs = now.getTime();

    const totalMinutes = fixedMinutes ?? ctx.totalMinutes;
    return {
      totalKurus: ctx.totalKurus,
      totalMinutes,
      horizonDays: ctx.settings.horizonDays,
      slotMinutes: ctx.settings.slotMinutes,
      days: dates.map((date, i) => ({
        date,
        slots: computeSlots({
          localDate: date,
          now,
          totalMinutes,
          slotMinutes: ctx.settings.slotMinutes,
          minLeadMinutes: ctx.settings.minLeadMinutes,
          horizonDays: ctx.settings.horizonDays,
          salonWeeklyClosedDays: ctx.salon.get("weeklyClosedDays") ?? [],
          salonClosures: ctx.salonClosures,
          staffSchedule: ctx.schedule,
          staffClosures: ctx.staffClosures,
          busy: busyOf(liveIntervals(locks[i], nowMs).filter((x) => x.appointmentId !== excludeId)),
        }).map((s) => ({ startAt: s.startAt.toISOString(), label: s.label })),
      })),
    };
  },
);
