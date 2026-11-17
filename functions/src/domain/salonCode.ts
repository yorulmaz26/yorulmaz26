/** 6 haneli salon kodu: 100000–999999 (900.000 kapasite). Kod kimliktir, yetki vermez. */
export const SALON_CODE_MIN = 100_000;
export const SALON_CODE_MAX = 999_999;
export const SALON_CODE_MAX_ATTEMPTS = 10;

export function isValidSalonCode(code: string): boolean {
  return /^[1-9]\d{5}$/.test(code);
}

/** rng: [0,1) — üretimde crypto.randomInt kullanılır, testte deterministik. */
export function generateSalonCode(rng: () => number): string {
  const span = SALON_CODE_MAX - SALON_CODE_MIN + 1;
  return String(SALON_CODE_MIN + Math.floor(rng() * span));
}

/**
 * Benzersiz kod tahsisi. tryReserve, kodu transaction içinde "yoksa oluştur" ile
 * rezerve etmeye çalışır ve başarılıysa true döner.
 */
export async function allocateSalonCode(
  rng: () => number,
  tryReserve: (code: string) => Promise<boolean>,
  maxAttempts = SALON_CODE_MAX_ATTEMPTS,
): Promise<string> {
  for (let i = 0; i < maxAttempts; i++) {
    const code = generateSalonCode(rng);
    if (await tryReserve(code)) return code;
  }
  throw new Error("SALON_CODE_EXHAUSTED");
}
