import { allocateSalonCode, canReadReport, isValidSalonCode, normalizeTrPhone, ratingAverage, totals } from "../src/domain";

describe("telefon normalizasyonu", () => {
  test.each([
    ["0532 123 45 67", "+905321234567"],
    ["532-123-4567", "+905321234567"],
    ["+90 (532) 123 45 67", "+905321234567"],
    ["905321234567", "+905321234567"],
    ["0212 555 66 77", "+902125556677"],
  ])("%s", (raw, e164) => expect(normalizeTrPhone(raw)).toBe(e164));
  test("geçersiz", () => {
    expect(normalizeTrPhone("12345")).toBeNull();
    expect(normalizeTrPhone("0112 555 66 77")).toBeNull();
    expect(normalizeTrPhone("0212 555 66 77", true)).toBeNull();
    expect(normalizeTrPhone("05321234567a")).toBeNull();
  });
});

describe("salon kodu", () => {
  test("çakışmada yeniden dener", async () => {
    const seq = [0, 0, 0.5];
    const taken = new Set(["100000"]);
    const code = await allocateSalonCode(() => seq.shift()!, async (c) => {
      if (taken.has(c)) return false;
      taken.add(c);
      return true;
    });
    expect(code).toBe("550000");
    expect(isValidSalonCode(code)).toBe(true);
  });
  test("kapasite tükenince hata", async () => {
    await expect(allocateSalonCode(() => 0, async () => false, 3)).rejects.toThrow("SALON_CODE_EXHAUSTED");
  });
  test("geçerlilik", () => {
    expect(isValidSalonCode("012345")).toBe(false);
    expect(isValidSalonCode("12345")).toBe(false);
  });
});

test("toplamlar kuruş ve dakika", () => {
  expect(totals([{ priceKurus: 25050, durationMinutes: 30 }, { priceKurus: 10000, durationMinutes: 15 }])).toEqual({
    totalKurus: 35050,
    totalMinutes: 45,
  });
  expect(() => totals([{ priceKurus: 1.5, durationMinutes: 5 }])).toThrow();
});

test("puan ortalaması", () => {
  expect(ratingAverage(0, 0)).toBeNull();
  expect(ratingAverage(14, 3)).toBe(4.7);
  expect(ratingAverage(9, 2)).toBe(4.5);
});

test("rapor yetkisi", () => {
  expect(canReadReport("owner", "owner", false, { kind: "SALON" })).toBe(true);
  expect(canReadReport("s1", "owner", true, { kind: "SALON" })).toBe(false);
  expect(canReadReport("s1", "owner", true, { kind: "STAFF", staffUid: "s2" })).toBe(false);
  expect(canReadReport("s1", "owner", true, { kind: "STAFF", staffUid: "s1" })).toBe(true);
  expect(canReadReport("s1", "owner", false, { kind: "STAFF", staffUid: "s1" })).toBe(false);
});
