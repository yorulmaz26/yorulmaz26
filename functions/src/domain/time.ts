import { DateTime } from "luxon";

/** Tüm iş kuralları bu IANA bölgesinde değerlendirilir; saklama UTC'dir. */
export const BUSINESS_ZONE = "Europe/Istanbul";

const LOCAL_DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

export function assertLocalDate(value: string): void {
  if (!LOCAL_DATE_RE.test(value) || !DateTime.fromISO(value, { zone: BUSINESS_ZONE }).isValid) {
    throw new Error(`Geçersiz yerel tarih: ${value}`);
  }
}

/** UTC anının İstanbul'daki yerel tarihi (YYYY-MM-DD). */
export function localDateOf(instant: Date): string {
  return DateTime.fromJSDate(instant, { zone: BUSINESS_ZONE }).toISODate()!;
}

/** Yerel tarih + gün içi dakika → UTC anı (yaz/kış saatine dayanıklı). */
export function instantAt(localDate: string, minuteOfDay: number): Date {
  assertLocalDate(localDate);
  return DateTime.fromISO(localDate, { zone: BUSINESS_ZONE })
    .startOf("day")
    .plus({ minutes: minuteOfDay })
    .toUTC()
    .toJSDate();
}

/** ISO haftanın günü: 1 = Pazartesi ... 7 = Pazar. */
export function isoWeekday(localDate: string): number {
  assertLocalDate(localDate);
  return DateTime.fromISO(localDate, { zone: BUSINESS_ZONE }).weekday;
}

export function addDays(localDate: string, days: number): string {
  assertLocalDate(localDate);
  return DateTime.fromISO(localDate, { zone: BUSINESS_ZONE }).plus({ days }).toISODate()!;
}

/** "HH:mm" → gün içi dakika. "24:00" yalnızca bitiş için geçerlidir. */
export function parseHm(value: string, allowEndOfDay = false): number {
  const m = /^([01]\d|2[0-4]):([0-5]\d)$/.exec(value);
  if (!m) throw new Error(`Geçersiz saat: ${value}`);
  const minutes = Number(m[1]) * 60 + Number(m[2]);
  if (minutes > 24 * 60 || (minutes === 24 * 60 && !allowEndOfDay)) {
    throw new Error(`Geçersiz saat: ${value}`);
  }
  return minutes;
}

export function formatHm(minuteOfDay: number): string {
  const h = Math.floor(minuteOfDay / 60);
  const m = minuteOfDay % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

export type PeriodType = "DAY" | "WEEK" | "MONTH";

export interface PeriodKeys {
  day: string; // D2026-11-17
  week: string; // W2026-11-16 (haftanın pazartesisi)
  month: string; // M2026-11
}

/** Bir anın düştüğü gün/hafta/ay rapor anahtarları (İstanbul yerel sınırları). */
export function periodKeysOf(instant: Date): PeriodKeys {
  const local = DateTime.fromJSDate(instant, { zone: BUSINESS_ZONE });
  const monday = local.startOf("week"); // Luxon: ISO hafta, Pazartesi başlar
  return {
    day: `D${local.toISODate()}`,
    week: `W${monday.toISODate()}`,
    month: `M${local.toFormat("yyyy-MM")}`,
  };
}

/** Dönem aralığı [start, end) UTC; anchorDate dönemin içindeki herhangi bir yerel tarih. */
export function periodRange(type: PeriodType, anchorDate: string): { start: Date; end: Date; key: string } {
  assertLocalDate(anchorDate);
  const anchor = DateTime.fromISO(anchorDate, { zone: BUSINESS_ZONE });
  const unit = type === "DAY" ? "day" : type === "WEEK" ? "week" : "month";
  const start = anchor.startOf(unit);
  const end = start.plus(type === "DAY" ? { days: 1 } : type === "WEEK" ? { weeks: 1 } : { months: 1 });
  const keys = periodKeysOf(start.toJSDate());
  const key = type === "DAY" ? keys.day : type === "WEEK" ? keys.week : keys.month;
  return { start: start.toUTC().toJSDate(), end: end.toUTC().toJSDate(), key };
}
