import { conflictReason, ScheduleContext, validateClosureRange } from "../src/domain";

// 2026-11-18 Çarşamba (3), 2026-11-22 Pazar (7)
const ctx = (o: Partial<ScheduleContext> = {}): ScheduleContext => ({
  salonWeeklyClosedDays: [],
  salonClosures: [],
  staffSchedule: {
    days: { "3": { shifts: [{ start: "09:00", end: "12:00" }, { start: "13:00", end: "18:00" }], breaks: [{ start: "15:00", end: "15:30" }] } },
    weeklyDaysOff: [],
  },
  staffClosures: [],
  ...o,
});
const appt = (start: number, end: number, localDate = "2026-11-18") => ({ localDate, startMinute: start, endMinute: end });

describe("takvim değişikliği etki analizi", () => {
  test("uygun randevu çakışmaz", () => {
    expect(conflictReason(appt(9 * 60, 10 * 60), ctx())).toBeNull();
    expect(conflictReason(appt(14 * 60, 15 * 60), ctx())).toBeNull(); // mola 15:00'te başlar, [14,15) kesişmez
  });
  test("vardiya daraltma ve vardiyalar arası boşluk", () => {
    expect(conflictReason(appt(11 * 60 + 30, 12 * 60 + 30), ctx())).toBe("OUTSIDE_WORKING_HOURS");
    expect(conflictReason(appt(18 * 60, 18 * 60 + 30), ctx())).toBe("OUTSIDE_WORKING_HOURS");
  });
  test("molayla kesişme", () => {
    expect(conflictReason(appt(14 * 60 + 45, 15 * 60 + 15), ctx())).toBe("OVERLAPS_BREAK");
  });
  test("kapalı gün, izin ve kapatmalar öncelik sırasıyla", () => {
    expect(conflictReason(appt(600, 660), ctx({ salonWeeklyClosedDays: [3] }))).toBe("SALON_CLOSED_DAY");
    expect(conflictReason(appt(600, 660), ctx({ salonClosures: [{ startDate: "2026-11-17", endDate: "2026-11-19" }] }))).toBe("SALON_CLOSURE");
    expect(conflictReason(appt(600, 660), ctx({ staffSchedule: { ...ctx().staffSchedule, weeklyDaysOff: [3] } }))).toBe("STAFF_DAY_OFF");
    expect(conflictReason(appt(600, 660), ctx({ staffClosures: [{ startDate: "2026-11-18", endDate: "2026-11-18" }] }))).toBe("STAFF_CLOSURE");
    expect(conflictReason(appt(600, 660, "2026-11-22"), ctx())).toBe("OUTSIDE_WORKING_HOURS"); // Pazar tanımsız
  });
});

test("kapatma aralığı doğrulaması", () => {
  expect(validateClosureRange("2026-11-20", "2026-11-25", "2026-11-17")).toBeNull();
  expect(validateClosureRange("2026-11-20", "2026-11-19", "2026-11-17")).toBe("INVALID_DATE");
  expect(validateClosureRange("2026-11-16", "2026-11-19", "2026-11-17")).toBe("PAST_DATE");
  expect(validateClosureRange("2026-11-20", "2027-12-01", "2026-11-17")).toBe("RANGE_TOO_LONG");
  expect(validateClosureRange("2026-02-30", "2026-03-01", "2026-01-01")).toBe("INVALID_DATE");
});
