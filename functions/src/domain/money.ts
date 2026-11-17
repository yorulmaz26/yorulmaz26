/** Para tutarları kuruş cinsinden tam sayıdır (TRY). */
export const CURRENCY = "TRY";
export const MIN_SERVICE_PRICE_KURUS = 100; // 1 TL
export const MAX_SERVICE_PRICE_KURUS = 100_000_000; // 1.000.000 TL

export function assertKurus(value: unknown): asserts value is number {
  if (typeof value !== "number" || !Number.isSafeInteger(value)) {
    throw new Error("Tutar kuruş cinsinden tam sayı olmalıdır");
  }
}

export interface PricedItem {
  priceKurus: number;
  durationMinutes: number;
}

export function totals(items: PricedItem[]): { totalKurus: number; totalMinutes: number } {
  let totalKurus = 0;
  let totalMinutes = 0;
  for (const i of items) {
    assertKurus(i.priceKurus);
    totalKurus += i.priceKurus;
    totalMinutes += i.durationMinutes;
  }
  if (!Number.isSafeInteger(totalKurus)) throw new Error("Tutar taşması");
  return { totalKurus, totalMinutes };
}

/** Puan ortalaması, 1 ondalık; oy yoksa null ("Yeni"). */
export function ratingAverage(sum: number, count: number): number | null {
  if (count <= 0) return null;
  return Math.round((sum / count) * 10) / 10;
}
