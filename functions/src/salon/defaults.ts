import type { WeeklySchedule } from "../domain";

/** Yeni uzman için varsayılan program: Pzt–Cmt 09:00–19:00, Pazar izin. Aşama 4'te düzenlenir. */
export function defaultWeeklySchedule(): WeeklySchedule {
  const day = { shifts: [{ start: "09:00", end: "19:00" }], breaks: [] };
  return { days: { "1": day, "2": day, "3": day, "4": day, "5": day, "6": day, "7": { shifts: [], breaks: [] } }, weeklyDaysOff: [7] };
}

export const DEFAULT_BOOKING_SETTINGS = {
  slotMinutes: 15,
  horizonDays: 60,
  pendingTtlMinutes: 30,
  customerCancelCutoffMinutes: 120,
  minLeadMinutes: 30,
};

export const SALON_TYPES = ["BEAUTY_SALON", "MEN_HAIRDRESSER", "WOMEN_HAIRDRESSER", "UNISEX_HAIRDRESSER"] as const;
export type SalonType = (typeof SALON_TYPES)[number];
export const MAX_SALON_PHOTOS = 5;
