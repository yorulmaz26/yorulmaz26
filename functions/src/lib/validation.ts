import { fail } from "./errors";

const NAME_RE = /^[\p{L}][\p{L}\s.'-]{0,48}[\p{L}.]$/u;

export function reqString(value: unknown, field: string, min: number, max: number): string {
  if (typeof value !== "string") fail("invalid-argument", "INVALID_ARGUMENT", { field });
  const v = value.trim().replace(/\s+/g, " ");
  if (v.length < min || v.length > max) fail("invalid-argument", "INVALID_ARGUMENT", { field });
  return v;
}

export function optString(value: unknown, field: string, max: number): string {
  if (value === undefined || value === null || value === "") return "";
  if (typeof value !== "string") fail("invalid-argument", "INVALID_ARGUMENT", { field });
  const v = value.trim();
  if (v.length > max) fail("invalid-argument", "INVALID_ARGUMENT", { field });
  return v;
}

/** Ad/soyad: harf ile başlar, 2–50 karakter, harf/boşluk/kesme/tire. */
export function personName(value: unknown, field: string): string {
  const v = reqString(value, field, 2, 50);
  if (!NAME_RE.test(v)) fail("invalid-argument", "INVALID_NAME", { field });
  return v;
}

export function idempotencyKey(value: unknown): string {
  if (typeof value !== "string" || !/^[A-Za-z0-9-]{8,64}$/.test(value)) {
    fail("invalid-argument", "INVALID_ARGUMENT", { field: "idempotencyKey" });
  }
  return value;
}

export function docId(value: unknown, field: string): string {
  if (typeof value !== "string" || !/^[A-Za-z0-9_-]{1,128}$/.test(value)) {
    fail("invalid-argument", "INVALID_ARGUMENT", { field });
  }
  return value;
}

/** Yürürlükteki yasal metin sürümleri. Metin değiştiğinde artırın; eski sürümle kayıt reddedilir. */
export const LEGAL_VERSIONS = { kvkk: "2026-11-v1", terms: "2026-11-v1" } as const;

export interface ConsentInput {
  kvkkVersion?: unknown;
  termsVersion?: unknown;
}

export function consents(value: ConsentInput | undefined) {
  if (!value || value.kvkkVersion !== LEGAL_VERSIONS.kvkk || value.termsVersion !== LEGAL_VERSIONS.terms) {
    fail("failed-precondition", "CONSENT_REQUIRED", { current: LEGAL_VERSIONS });
  }
  return { kvkkVersion: LEGAL_VERSIONS.kvkk, termsVersion: LEGAL_VERSIONS.terms };
}
