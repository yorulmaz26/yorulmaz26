import { authedCallable } from "../lib/callable";
import { db, FieldValue } from "../lib/admin";
import { fail } from "../lib/errors";
import { docId, optString } from "../lib/validation";
import { audit } from "../lib/audit";
import {
  assertLocalDate,
  codeProblem,
  discountKurusOf,
  DISCOUNT_CODE_RE,
  localDateOf,
  LoyaltyProgram,
  loyaltyRewardsAvailable,
  MAX_PERCENT,
  normalizeCode,
  visitsToNextReward,
} from "../domain";
import { loadBookingContext } from "../booking/context";
import { customerStatsRef, discountCodeRef, discountUseRef } from "../booking/appointments";

async function requireActiveOwner(salonId: string, uid: string) {
  const salon = await db.collection("salons").doc(salonId).get();
  if (!salon.exists) fail("not-found", "SALON_NOT_FOUND");
  if (salon.get("ownerUid") !== uid) fail("permission-denied", "PERMISSION_DENIED");
  if (salon.get("status") !== "ACTIVE") fail("failed-precondition", "SALON_INACTIVE");
  return salon;
}

function optDate(v: unknown, field: string): string | null {
  if (v === undefined || v === null || v === "") return null;
  const s = String(v);
  try { assertLocalDate(s); } catch { fail("invalid-argument", "INVALID_DATE", { field }); }
  return s;
}

interface UpsertCodeReq {
  salonId?: unknown;
  code?: unknown;
  description?: unknown;
  percent?: unknown;
  amountKurus?: unknown;
  validFrom?: unknown;
  validUntil?: unknown;
  maxUses?: unknown;
  active?: unknown;
}

/**
 * İndirim kodu oluştur/güncelle (yalnızca sahip). Yüzde (1–90) veya sabit tutar (≥1 TL) — biri.
 * Kod salon içinde benzersizdir; her müşteri bir kodu bir kez kullanabilir. Kullanım sayısı
 * randevu oluşturulurken ayrılır, iptal/süre aşımında iade edilir.
 */
export const upsertDiscountCode = authedCallable<UpsertCodeReq, { code: string }>("upsertDiscountCode", async (data, caller) => {
  const salonId = docId(data.salonId, "salonId");
  const code = normalizeCode(String(data.code ?? ""));
  if (!DISCOUNT_CODE_RE.test(code)) fail("invalid-argument", "INVALID_CODE", { field: "code" });
  const description = optString(data.description, "description", 120);
  const percent = data.percent === undefined || data.percent === null || data.percent === "" ? null : Number(data.percent);
  const amountKurus = data.amountKurus === undefined || data.amountKurus === null || data.amountKurus === "" ? null : Number(data.amountKurus);
  if ((percent === null) === (amountKurus === null)) fail("invalid-argument", "DISCOUNT_KIND_REQUIRED");
  if (percent !== null && (!Number.isInteger(percent) || percent < 1 || percent > MAX_PERCENT)) fail("invalid-argument", "INVALID_ARGUMENT", { field: "percent" });
  if (amountKurus !== null && (!Number.isSafeInteger(amountKurus) || amountKurus < 100 || amountKurus > 100_000_000)) fail("invalid-argument", "INVALID_ARGUMENT", { field: "amountKurus" });
  const validFrom = optDate(data.validFrom, "validFrom");
  const validUntil = optDate(data.validUntil, "validUntil");
  if (validFrom && validUntil && validUntil < validFrom) fail("invalid-argument", "INVALID_DATE_RANGE");
  const maxUses = data.maxUses === undefined || data.maxUses === null || data.maxUses === "" ? null : Number(data.maxUses);
  if (maxUses !== null && (!Number.isInteger(maxUses) || maxUses < 1 || maxUses > 100_000)) fail("invalid-argument", "INVALID_ARGUMENT", { field: "maxUses" });
  const active = data.active !== false;

  await requireActiveOwner(salonId, caller.uid);
  const ref = discountCodeRef(salonId, code);
  await db.runTransaction(async (tx) => {
    const cur = await tx.get(ref);
    tx.set(ref, {
      salonId, code, description: description || null, percent, amountKurus, validFrom, validUntil, maxUses, active,
      usedCount: cur.exists ? cur.get("usedCount") ?? 0 : 0,
      createdAt: cur.exists ? cur.get("createdAt") : FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
  });
  await audit({ action: "DISCOUNT_CODE_UPSERTED", actorUid: caller.uid, salonId, targetId: code });
  return { code };
});

/**
 * Sadakat programı (yalnızca sahip): her `visitsRequired` tamamlanmış ziyarette bir ödül; ödül
 * bir sonraki randevuda `rewardPercent` indirim olarak kullanılır. Kapatmak: enabled=false.
 */
export const updateLoyaltyProgram = authedCallable<{ salonId?: unknown; enabled?: unknown; visitsRequired?: unknown; rewardPercent?: unknown }, LoyaltyProgram>(
  "updateLoyaltyProgram",
  async (data, caller) => {
    const salonId = docId(data.salonId, "salonId");
    const enabled = data.enabled === true;
    const visitsRequired = Number(data.visitsRequired);
    const rewardPercent = Number(data.rewardPercent);
    if (!Number.isInteger(visitsRequired) || visitsRequired < 3 || visitsRequired > 50) fail("invalid-argument", "INVALID_ARGUMENT", { field: "visitsRequired" });
    if (!Number.isInteger(rewardPercent) || rewardPercent < 5 || rewardPercent > 100) fail("invalid-argument", "INVALID_ARGUMENT", { field: "rewardPercent" });
    const salon = await requireActiveOwner(salonId, caller.uid);
    const program: LoyaltyProgram = { enabled, visitsRequired, rewardPercent };
    await salon.ref.update({ loyalty: program, updatedAt: FieldValue.serverTimestamp() });
    await audit({ action: "LOYALTY_UPDATED", actorUid: caller.uid, salonId, data: { ...program } });
    return program;
  },
);

/**
 * Rezervasyon özetinde fiyat önizlemesi: liste toplamı, varsa kod/ödül indirimi ve sadakat durumu.
 * Bağlayıcı değildir; createAppointment aynı kuralları transaction içinde yeniden uygular.
 */
export const previewBookingPrice = authedCallable<
  { salonId?: unknown; staffUid?: unknown; serviceIds?: unknown; discountCode?: unknown; useLoyaltyReward?: unknown },
  {
    totalKurus: number;
    discountKurus: number;
    finalKurus: number;
    codeError: string | null;
    loyalty: { enabled: boolean; rewardsAvailable: number; rewardPercent: number; visitsRequired: number; completedCount: number; visitsToNext: number | null } | null;
  }
>("previewBookingPrice", async (data, caller) => {
  const salonId = docId(data.salonId, "salonId");
  const staffUid = docId(data.staffUid, "staffUid");
  if (!Array.isArray(data.serviceIds)) fail("invalid-argument", "INVALID_ARGUMENT", { field: "serviceIds" });
  const serviceIds = (data.serviceIds as unknown[]).map((s) => docId(s, "serviceIds"));
  const today = localDateOf(new Date());
  const ctx = await loadBookingContext(null, salonId, staffUid, serviceIds, today);
  const program = ctx.salon.get("loyalty") as LoyaltyProgram | undefined;
  const stats = await customerStatsRef(salonId, caller.uid).get();
  const completed = (stats.get("completedCount") as number | undefined) ?? 0;
  const available = loyaltyRewardsAvailable(program, completed, (stats.get("rewardsUsed") as number | undefined) ?? 0);
  const loyalty = program?.enabled
    ? { enabled: true, rewardsAvailable: available, rewardPercent: program.rewardPercent, visitsRequired: program.visitsRequired, completedCount: completed, visitsToNext: visitsToNextReward(program, completed) }
    : null;

  let discountKurus = 0;
  let codeError: string | null = null;
  const rawCode = data.discountCode ? normalizeCode(String(data.discountCode)) : "";
  if (rawCode) {
    if (!DISCOUNT_CODE_RE.test(rawCode)) codeError = "CODE_NOT_FOUND";
    else {
      const [c, use] = await db.getAll(discountCodeRef(salonId, rawCode), discountUseRef(salonId, rawCode, caller.uid));
      if (!c.exists) codeError = "CODE_NOT_FOUND";
      else {
        codeError = codeProblem({ active: c.get("active") === true, validFrom: c.get("validFrom") ?? null, validUntil: c.get("validUntil") ?? null, maxUses: c.get("maxUses") ?? null, usedCount: c.get("usedCount") ?? 0 }, today)
          ?? (use.exists ? "CODE_ALREADY_USED" : null);
        if (!codeError) discountKurus = discountKurusOf(ctx.totalKurus, { percent: c.get("percent") ?? null, amountKurus: c.get("amountKurus") ?? null });
      }
    }
  } else if (data.useLoyaltyReward === true && program && available > 0) {
    discountKurus = discountKurusOf(ctx.totalKurus, { percent: program.rewardPercent });
  }
  return { totalKurus: ctx.totalKurus, discountKurus, finalKurus: ctx.totalKurus - discountKurus, codeError, loyalty };
});
