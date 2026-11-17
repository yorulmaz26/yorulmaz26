import { instantAt, isoWeekday, localDateOf, periodKeysOf, periodRange, parseHm } from "../src/domain";

describe("time (Europe/Istanbul)", () => {
  test("yerel 00:00 → UTC 21:00 önceki gün", () => {
    expect(instantAt("2026-11-17", 0).toISOString()).toBe("2026-11-16T21:00:00.000Z");
  });

  test("UTC 21:30 → İstanbul'da ertesi gün", () => {
    expect(localDateOf(new Date("2026-11-16T21:30:00Z"))).toBe("2026-11-17");
  });

  test("ISO haftanın günü: 2026-11-16 Pazartesi", () => {
    expect(isoWeekday("2026-11-16")).toBe(1);
    expect(isoWeekday("2026-11-22")).toBe(7);
  });

  test("Pazar 23:59:59 aynı haftada, Pazartesi 00:00 yeni hafta", () => {
    const sundayLate = new Date("2026-11-22T20:59:59Z"); // İstanbul 23:59:59
    const mondayStart = new Date("2026-11-22T21:00:00Z"); // İstanbul 00:00
    expect(periodKeysOf(sundayLate).week).toBe("W2026-11-16");
    expect(periodKeysOf(mondayStart).week).toBe("W2026-11-23");
    expect(periodKeysOf(mondayStart).day).toBe("D2026-11-23");
  });

  test("ay sınırı yerel saate göre", () => {
    expect(periodKeysOf(new Date("2026-11-30T21:00:00Z")).month).toBe("M2026-12");
    expect(periodKeysOf(new Date("2026-11-30T20:59:59Z")).month).toBe("M2026-11");
  });

  test("dönem aralıkları [start,end)", () => {
    const w = periodRange("WEEK", "2026-11-19");
    expect(w.key).toBe("W2026-11-16");
    expect(w.start.toISOString()).toBe("2026-11-15T21:00:00.000Z");
    expect(w.end.toISOString()).toBe("2026-11-22T21:00:00.000Z");
    const m = periodRange("MONTH", "2026-02-10");
    expect(m.key).toBe("M2026-02");
    expect(m.end.toISOString()).toBe("2026-02-28T21:00:00.000Z");
    const d = periodRange("DAY", "2026-11-17");
    expect(d.end.getTime() - d.start.getTime()).toBe(24 * 3600_000);
  });

  test("saat ayrıştırma", () => {
    expect(parseHm("09:30")).toBe(570);
    expect(parseHm("24:00", true)).toBe(1440);
    expect(() => parseHm("24:00")).toThrow();
    expect(() => parseHm("9:30")).toThrow();
  });
});
