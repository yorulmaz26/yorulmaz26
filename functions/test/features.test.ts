import {
  buildIcs, codeProblem, discountKurusOf, distanceKm, encodeGeohash, foldLine, geohashPrefixesFor, icsEscape,
  loyaltyRewardsAvailable, normalizeCode, visitsToNextReward,
} from "../src/domain";
import { freedSlot } from "../src/waitlist/waitlist";
import { Timestamp } from "firebase-admin/firestore";

describe("indirim ve sadakat", () => {
  test("yüzde aşağı yuvarlanır, sabit tutar toplamı aşamaz", () => {
    expect(discountKurusOf(33333, { percent: 10 })).toBe(3333);
    expect(discountKurusOf(5000, { amountKurus: 8000 })).toBe(5000);
    expect(discountKurusOf(5000, {})).toBe(0);
  });
  test("kod normalleştirme ve geçerlilik", () => {
    expect(normalizeCode(" yaz 20 ")).toBe("YAZ20");
    expect(normalizeCode("indirim")).toBe("INDIRIM");
    const base = { active: true, validFrom: "2026-11-01", validUntil: "2026-11-30", maxUses: 2, usedCount: 1 };
    expect(codeProblem(base, "2026-11-17")).toBeNull();
    expect(codeProblem({ ...base, active: false }, "2026-11-17")).toBe("CODE_INACTIVE");
    expect(codeProblem(base, "2026-10-31")).toBe("CODE_NOT_STARTED");
    expect(codeProblem(base, "2026-12-01")).toBe("CODE_EXPIRED");
    expect(codeProblem({ ...base, usedCount: 2 }, "2026-11-17")).toBe("CODE_EXHAUSTED");
  });
  test("sadakat ödülü sayımı", () => {
    const p = { enabled: true, visitsRequired: 5, rewardPercent: 50 };
    expect(loyaltyRewardsAvailable(p, 4, 0)).toBe(0);
    expect(loyaltyRewardsAvailable(p, 10, 1)).toBe(1);
    expect(loyaltyRewardsAvailable({ ...p, enabled: false }, 10, 0)).toBe(0);
    expect(visitsToNextReward(p, 7)).toBe(3);
    expect(visitsToNextReward(null, 7)).toBeNull();
  });
});

describe("konum", () => {
  test("geohash bilinen değer ve mesafe", () => {
    expect(encodeGeohash(57.64911, 10.40744, 11)).toBe("u4pruydqqvj");
    expect(distanceKm(41.0082, 28.9784, 39.9334, 32.8597)).toBeGreaterThan(340);
    expect(distanceKm(41.0082, 28.9784, 39.9334, 32.8597)).toBeLessThan(360);
  });
  test("yarıçap içindeki her nokta önek kümesindeki bir hücreye düşer", () => {
    const [lat, lng] = [41.04, 29.0];
    for (const r of [0.5, 3, 10, 25, 50]) {
      const prefixes = geohashPrefixesFor(lat, lng, r);
      for (let k = 0; k < 200; k++) {
        const ang = (k / 200) * 2 * Math.PI;
        const d = r * Math.sqrt(((k * 37) % 100) / 100);
        const pLat = lat + (d / 111) * Math.cos(ang);
        const pLng = lng + (d / (111 * Math.cos((lat * Math.PI) / 180))) * Math.sin(ang);
        const h = encodeGeohash(pLat, pLng, 9);
        expect(prefixes.some((p) => h.startsWith(p))).toBe(true);
      }
    }
  });
});

describe("iCalendar", () => {
  test("kaçış, satır katlama ve iptal durumu", () => {
    expect(icsEscape("a,b;c\nd")).toBe("a\\,b\;c\\nd");
    const long = "SUMMARY:" + "ş".repeat(100);
    const folded = foldLine(long);
    for (const line of folded.split("\r\n")) expect(Buffer.byteLength(line, "utf8")).toBeLessThanOrEqual(75);
    expect(folded.replace(/\r\n /g, "")).toBe(long);
    const ics = buildIcs("Takvim", [
      { uid: "a1", start: new Date("2026-11-17T07:00:00Z"), end: new Date("2026-11-17T07:45:00Z"), summary: "Işıltı – Kesim", cancelled: true },
    ], new Date("2026-11-01T00:00:00Z"));
    expect(ics).toContain("DTSTART:20261117T070000Z");
    expect(ics).toContain("STATUS:CANCELLED");
    expect(ics.startsWith("BEGIN:VCALENDAR\r\n")).toBe(true);
  });
});

describe("bekleme listesi tetikleyicisi", () => {
  const future = Timestamp.fromMillis(Date.now() + 3600_000);
  const base = { status: "CONFIRMED", staffUid: "s", localDate: "2026-11-20", startMinuteOfDay: 600, startAt: future };
  test("iptal, süre aşımı ve taşıma yer açar; tamamlanma ve geçmiş randevu açmaz", () => {
    expect(freedSlot(base, { ...base, status: "CANCELLED_BY_CUSTOMER" }, Date.now())).toEqual({ staffUid: "s", localDate: "2026-11-20" });
    expect(freedSlot({ ...base, status: "PENDING" }, { ...base, status: "EXPIRED" }, Date.now())).not.toBeNull();
    expect(freedSlot(base, { ...base, startMinuteOfDay: 660 }, Date.now())).not.toBeNull();
    expect(freedSlot(base, { ...base, status: "IN_PROGRESS" }, Date.now())).toBeNull();
    expect(freedSlot({ ...base, status: "IN_PROGRESS" }, { ...base, status: "COMPLETED" }, Date.now())).toBeNull();
    expect(freedSlot({ ...base, startAt: Timestamp.fromMillis(Date.now() - 1000) }, { ...base, status: "CANCELLED_BY_STAFF" }, Date.now())).toBeNull();
  });
});
