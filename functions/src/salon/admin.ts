import { getAuth } from "firebase-admin/auth";
import { authedCallable } from "../lib/callable";
import { db } from "../lib/admin";
import { fail } from "../lib/errors";
import { docId, reqString } from "../lib/validation";
import { audit } from "../lib/audit";
import { normalizeTrPhone } from "../domain";
import { shutDownSalon } from "./lifecycle";

/**
 * Platform yöneticisi: salonu platformdan kaldırır (kalıcı kapatma). Tüm aktif randevular (işlemdekiler dahil)
 * gerekçeyle iptal edilir, üyelikler sonlanır, salon aramadan ve bağlantılardan kalkar; sahip yeniden açamaz.
 * Finans, değerlendirme ve denetim kayıtları silinmez.
 */
export const adminRemoveSalon = authedCallable<{ salonId?: unknown; reason?: unknown; confirm?: unknown }, { cancelledAppointments: number }>(
  "adminRemoveSalon",
  async (data, caller) => {
    if (!caller.isAdmin) fail("permission-denied", "ADMIN_ONLY");
    const salonId = docId(data.salonId, "salonId");
    const reason = reqString(data.reason, "reason", 3, 300);
    if (data.confirm !== "KALDIR") fail("invalid-argument", "CONFIRMATION_REQUIRED");
    const salon = await db.collection("salons").doc(salonId).get();
    if (!salon.exists) fail("not-found", "SALON_NOT_FOUND");
    if (salon.get("status") === "CLOSED") fail("failed-precondition", "SALON_CLOSED");
    const cancelled = await shutDownSalon(salon, { actorUid: caller.uid, actorRole: "ADMIN", reason, byAdmin: true });
    return { cancelledAppointments: cancelled };
  },
);

/**
 * Platform yöneticisi başka bir kullanıcıya yönetici yetkisi verir/geri alır (telefon numarasıyla).
 * Kişi uygulamaya en az bir kez giriş yapmış olmalı. Kendi yetkisini geri alamaz (kilitlenmeyi önler).
 */
export const setPlatformAdmin = authedCallable<{ phone?: unknown; grant?: unknown }, { uid: string; admin: boolean }>(
  "setPlatformAdmin",
  async (data, caller) => {
    if (!caller.isAdmin) fail("permission-denied", "ADMIN_ONLY");
    const phone = normalizeTrPhone(String(data.phone ?? ""), true);
    if (!phone) fail("invalid-argument", "INVALID_PHONE");
    const grant = data.grant !== false;
    let user;
    try {
      user = await getAuth().getUserByPhoneNumber(phone);
    } catch {
      fail("not-found", "USER_NOT_FOUND");
    }
    if (!grant && user.uid === caller.uid) fail("failed-precondition", "CANNOT_REVOKE_SELF");
    const claims = { ...(user.customClaims ?? {}) } as Record<string, unknown>;
    if (grant) claims.admin = true;
    else delete claims.admin;
    await getAuth().setCustomUserClaims(user.uid, claims);
    await db.collection("platformAdmins").doc(user.uid).set(
      grant ? { phoneE164: phone, grantedBy: caller.uid, grantedAt: new Date() } : { phoneE164: phone, revokedBy: caller.uid, revokedAt: new Date(), revoked: true },
      { merge: false },
    );
    await audit({ action: grant ? "ADMIN_GRANTED" : "ADMIN_REVOKED", actorUid: caller.uid, targetId: user.uid });
    return { uid: user.uid, admin: grant };
  },
);
