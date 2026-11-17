import { HttpsError, FunctionsErrorCode } from "firebase-functions/v2/https";

/**
 * Tüm hatalar makine-okunur `details.reason` taşır; istemci bunu Türkçe mesaja eşler
 * (app/.../core/data/FunctionErrorMapper.kt). Mesaj alanı yalnızca loglama içindir.
 */
export function fail(code: FunctionsErrorCode, reason: string, extra: Record<string, unknown> = {}): never {
  throw new HttpsError(code, reason, { reason, ...extra });
}
