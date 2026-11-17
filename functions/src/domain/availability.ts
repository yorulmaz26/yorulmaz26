import { addDays, instantAt, isoWeekday, localDateOf, parseHm, formatHm } from "./time";

/** Yarı açık dakika aralığı [start, end). */
export interface Interval {
  start: number;
  end: number;
}

export interface HmRange {
  start: string; // "HH:mm"
  end: string; // "HH:mm" (24:00 bitiş olarak geçerli)
}

export interface DaySchedule {
  shifts: HmRange[];
  breaks: HmRange[];
}

/** Anahtar: ISO haftanın günü "1".."7" */
export interface WeeklySchedule {
  days: Partial<Record<string, DaySchedule>>;
  weeklyDaysOff: number[];
}

export interface DateRangeClosure {
  startDate: string; // dahil
  endDate: string; // dahil
}

export interface AvailabilityInput {
  localDate: string;
  now: Date;
  totalMinutes: number;
  slotMinutes: number;
  minLeadMinutes: number;
  horizonDays: number;
  salonWeeklyClosedDays: number[];
  salonClosures: DateRangeClosure[];
  staffSchedule: WeeklySchedule;
  staffClosures: DateRangeClosure[];
  /** Aynı uzmanın o günkü aktif (PENDING/CONFIRMED/IN_PROGRESS) randevuları */
  busy: Interval[];
}

export interface Slot {
  startMinute: number;
  startAt: Date;
  label: string;
}

export function toInterval(r: HmRange): Interval {
  return { start: parseHm(r.start), end: parseHm(r.end, true) };
}

/** Aralıkları sıralar ve çakışan/bitişikleri birleştirir. */
export function mergeIntervals(list: Interval[]): Interval[] {
  const sorted = list.filter((i) => i.end > i.start).sort((a, b) => a.start - b.start);
  const out: Interval[] = [];
  for (const i of sorted) {
    const last = out[out.length - 1];
    if (last && i.start <= last.end) last.end = Math.max(last.end, i.end);
    else out.push({ ...i });
  }
  return out;
}

/** base − remove (her ikisi de aralık listesi). */
export function subtractIntervals(base: Interval[], remove: Interval[]): Interval[] {
  let result = mergeIntervals(base);
  for (const r of mergeIntervals(remove)) {
    const next: Interval[] = [];
    for (const b of result) {
      if (r.end <= b.start || r.start >= b.end) {
        next.push(b);
        continue;
      }
      if (r.start > b.start) next.push({ start: b.start, end: r.start });
      if (r.end < b.end) next.push({ start: r.end, end: b.end });
    }
    result = next;
  }
  return result;
}

export function overlaps(a: Interval, b: Interval): boolean {
  return a.start < b.end && b.start < a.end;
}

export function closureCovers(closures: DateRangeClosure[], localDate: string): boolean {
  return closures.some((c) => c.startDate <= localDate && localDate <= c.endDate);
}

export type ScheduleValidationError =
  | "END_BEFORE_START"
  | "SHIFTS_OVERLAP"
  | "BREAK_OUTSIDE_SHIFT"
  | "BREAKS_OVERLAP"
  | "INVALID_TIME";

/** Bir günün vardiya/mola doğrulaması (gece yarısını geçen vardiya desteklenmez). */
export function validateDaySchedule(day: DaySchedule): ScheduleValidationError | null {
  let shifts: Interval[];
  let breaks: Interval[];
  try {
    shifts = day.shifts.map(toInterval);
    breaks = day.breaks.map(toInterval);
  } catch {
    return "INVALID_TIME";
  }
  if ([...shifts, ...breaks].some((i) => i.end <= i.start)) return "END_BEFORE_START";
  const hasOverlap = (list: Interval[]) => {
    const s = [...list].sort((a, b) => a.start - b.start);
    return s.some((x, idx) => idx > 0 && x.start < s[idx - 1].end);
  };
  if (hasOverlap(shifts)) return "SHIFTS_OVERLAP";
  if (hasOverlap(breaks)) return "BREAKS_OVERLAP";
  if (!breaks.every((b) => shifts.some((s) => b.start >= s.start && b.end <= s.end))) return "BREAK_OUTSIDE_SHIFT";
  return null;
}

/**
 * Sunucunun tek doğru müsaitlik hesabı (docs/ARCHITECTURE.md §6).
 * Bir başlangıç ancak [start, start+D) aralığının tamamı müsaitse döner.
 */
export function computeSlots(input: AvailabilityInput): Slot[] {
  const { localDate, now, totalMinutes: D, slotMinutes: S } = input;
  if (D <= 0 || S <= 0) return [];

  const today = localDateOf(now);
  if (localDate < today || localDate > addDays(today, input.horizonDays)) return [];

  const weekday = isoWeekday(localDate);
  if (input.salonWeeklyClosedDays.includes(weekday)) return [];
  if (input.staffSchedule.weeklyDaysOff.includes(weekday)) return [];
  if (closureCovers(input.salonClosures, localDate)) return [];
  if (closureCovers(input.staffClosures, localDate)) return [];

  const day = input.staffSchedule.days[String(weekday)];
  if (!day || day.shifts.length === 0) return [];

  const free = subtractIntervals(day.shifts.map(toInterval), [...day.breaks.map(toInterval), ...input.busy]);
  const earliest = now.getTime() + input.minLeadMinutes * 60_000;

  const slots: Slot[] = [];
  for (const f of free) {
    for (let t = Math.ceil(f.start / S) * S; t + D <= f.end; t += S) {
      const startAt = instantAt(localDate, t);
      if (startAt.getTime() >= earliest) slots.push({ startMinute: t, startAt, label: formatHm(t) });
    }
  }
  return slots;
}
