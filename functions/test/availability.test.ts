import { AvailabilityInput, computeSlots, subtractIntervals, validateDaySchedule } from "../src/domain";

// 2026-11-18 Çarşamba (ISO 3). "now" bir gün önce İstanbul 12:00.
const input = (o: Partial<AvailabilityInput> = {}): AvailabilityInput => ({
  localDate: "2026-11-18",
  now: new Date("2026-11-17T09:00:00Z"),
  totalMinutes: 45,
  slotMinutes: 15,
  minLeadMinutes: 30,
  horizonDays: 60,
  salonWeeklyClosedDays: [7],
  salonClosures: [],
  staffSchedule: {
    days: {
      "3": {
        shifts: [{ start: "09:00", end: "12:00" }, { start: "13:00", end: "15:00" }],
        breaks: [{ start: "10:30", end: "10:45" }],
      },
    },
    weeklyDaysOff: [],
  },
  staffClosures: [],
  busy: [],
  ...o,
});
const labels = (o?: Partial<AvailabilityInput>) => computeSlots(input(o)).map((s) => s.label);

describe("müsaitlik", () => {
  test("vardiya, mola ve süre dikkate alınır; kısmi boşluk seçilemez", () => {
    expect(labels()).toEqual([
      "09:00", "09:15", "09:30", "09:45",
      // 10:00 → 10:45 molaya taşar; 10:45 → 11:30 ok
      "10:45", "11:00", "11:15",
      "13:00", "13:15", "13:30", "13:45", "14:00", "14:15",
    ]);
  });

  test("mevcut randevu çakışan başlangıçları kapatır", () => {
    const l = labels({ busy: [{ start: 13 * 60 + 30, end: 14 * 60 }] });
    expect(l).not.toContain("13:00");
    expect(l).not.toContain("13:15");
    expect(l).toContain("14:00");
    expect(l).not.toContain("13:30");
  });

  test("slot başlangıcı ızgaraya hizalanır", () => {
    const l = labels({ busy: [{ start: 9 * 60, end: 9 * 60 + 10 }] });
    expect(l[0]).toBe("09:15");
  });

  test("salon haftalık kapalı günü ve tarih kapaması", () => {
    expect(labels({ salonWeeklyClosedDays: [3] })).toEqual([]);
    expect(labels({ salonClosures: [{ startDate: "2026-11-18", endDate: "2026-11-18" }] })).toEqual([]);
    expect(labels({ staffClosures: [{ startDate: "2026-11-15", endDate: "2026-11-20" }] })).toEqual([]);
    expect(labels({ staffSchedule: { days: input().staffSchedule.days, weeklyDaysOff: [3] } })).toEqual([]);
  });

  test("geçmiş tarih, ufuk dışı ve en erken rezervasyon süresi", () => {
    expect(labels({ localDate: "2026-11-16" })).toEqual([]);
    expect(labels({ horizonDays: 0 })).toEqual([]);
    // now = 2026-11-18 İstanbul 09:20 → en erken 09:50 → ilk slot 10:45? (10:00 molaya taşar)
    expect(labels({ now: new Date("2026-11-18T06:20:00Z") })[0]).toBe("10:45");
  });

  test("toplam süre vardiyadan uzunsa slot yok", () => {
    expect(labels({ totalMinutes: 200 })).toEqual([]);
  });

  test("slot UTC anı doğru", () => {
    expect(computeSlots(input())[0].startAt.toISOString()).toBe("2026-11-18T06:00:00.000Z");
  });
});

test("aralık çıkarma", () => {
  expect(subtractIntervals([{ start: 0, end: 100 }], [{ start: 20, end: 30 }, { start: 25, end: 40 }])).toEqual([
    { start: 0, end: 20 },
    { start: 40, end: 100 },
  ]);
});

describe("günlük program doğrulama", () => {
  test("geçerli", () => {
    expect(validateDaySchedule({ shifts: [{ start: "09:00", end: "18:00" }], breaks: [{ start: "12:00", end: "13:00" }] })).toBeNull();
  });
  test("hatalar", () => {
    expect(validateDaySchedule({ shifts: [{ start: "18:00", end: "09:00" }], breaks: [] })).toBe("END_BEFORE_START");
    expect(
      validateDaySchedule({ shifts: [{ start: "09:00", end: "12:00" }, { start: "11:00", end: "14:00" }], breaks: [] }),
    ).toBe("SHIFTS_OVERLAP");
    expect(
      validateDaySchedule({ shifts: [{ start: "09:00", end: "12:00" }], breaks: [{ start: "11:30", end: "12:30" }] }),
    ).toBe("BREAK_OUTSIDE_SHIFT");
    expect(validateDaySchedule({ shifts: [{ start: "9:00", end: "12:00" }], breaks: [] })).toBe("INVALID_TIME");
  });
});
