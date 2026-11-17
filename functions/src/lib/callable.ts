import { CallableRequest, HttpsError, onCall } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { isEmulator } from "./admin";
import { fail } from "./errors";

export interface Caller {
  uid: string;
  /** Firebase Auth'un doğruladığı telefon (E.164) — yalnızca telefonla giriş yapılmışsa. */
  phone: string | null;
  /** Platform yöneticisi (moderasyon). Yalnızca scripts/setAdmin.js ile atanan özel yetki. */
  isAdmin: boolean;
}

/**
 * Kimlik doğrulamalı callable. Üretimde App Check zorunlu; emülatörde (yerel test) kapalı.
 * Beklenmeyen hatalar istemciye ayrıntı sızdırmadan INTERNAL olarak döner.
 */
export function authedCallable<Req, Res>(name: string, handler: (data: Req, caller: Caller, req: CallableRequest<Req>) => Promise<Res>) {
  return onCall<Req, Promise<Res>>({ enforceAppCheck: !isEmulator, memory: "256MiB" }, async (req) => {
    if (!req.auth) fail("unauthenticated", "UNAUTHENTICATED");
    const phone = (req.auth.token.phone_number as string | undefined) ?? null;
    try {
      return await handler((req.data ?? {}) as Req, { uid: req.auth.uid, phone, isAdmin: req.auth.token.admin === true }, req);
    } catch (e) {
      if (e instanceof HttpsError) throw e;
      logger.error(`${name} failed`, e);
      throw new HttpsError("internal", "INTERNAL", { reason: "INTERNAL" });
    }
  });
}

/** Oturum gerektirmeyen (ama App Check gerektiren) callable — ör. salon kodu çözümleme. */
export function publicCallable<Req, Res>(name: string, handler: (data: Req) => Promise<Res>) {
  return onCall<Req, Promise<Res>>({ enforceAppCheck: !isEmulator, memory: "256MiB" }, async (req) => {
    try {
      return await handler((req.data ?? {}) as Req);
    } catch (e) {
      if (e instanceof HttpsError) throw e;
      logger.error(`${name} failed`, e);
      throw new HttpsError("internal", "INTERNAL", { reason: "INTERNAL" });
    }
  });
}
