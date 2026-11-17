/**
 * Türkiye numaralarını E.164 (+90XXXXXXXXXX) biçimine normalize eder.
 * Kabul: "0532 123 45 67", "532-123-4567", "+90 532 123 45 67", "905321234567".
 * Cep (5xx) ve sabit hat (2xx,3xx,4xx) alan kodlarını kabul eder. Geçersizse null.
 */
export function normalizeTrPhone(raw: string, mobileOnly = false): string | null {
  let digits = raw.replace(/[\s\-().]/g, "");
  if (digits.startsWith("+")) digits = digits.slice(1);
  if (!/^\d+$/.test(digits)) return null;
  if (digits.startsWith("90") && digits.length === 12) digits = digits.slice(2);
  else if (digits.startsWith("0") && digits.length === 11) digits = digits.slice(1);
  if (digits.length !== 10) return null;
  const pattern = mobileOnly ? /^5\d{9}$/ : /^[2-5]\d{9}$/;
  return pattern.test(digits) ? `+90${digits}` : null;
}
