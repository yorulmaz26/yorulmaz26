/**
 * Kampanya (indirim kodu) ve sadakat programı kuralları — saf fonksiyonlar.
 * İndirim yalnızca hizmet bedelini düşürür; ödeme alınmaz (tahsilat uygulama dışında).
 */
export const DISCOUNT_CODE_RE = /^[A-Z0-9]{4,16}$/;
export const MAX_PERCENT = 90;

export interface DiscountRule {
  percent?: number | null;
  amountKurus?: number | null;
}

/** İndirim tutarı (kuruş, aşağı yuvarlanır). Toplamı aşamaz; en az 0. */
export function discountKurusOf(totalKurus: number, rule: DiscountRule): number {
  if (rule.percent != null) return Math.max(0, Math.min(totalKurus, Math.floor((totalKurus * rule.percent) / 100)));
  if (rule.amountKurus != null) return Math.max(0, Math.min(totalKurus, rule.amountKurus));
  return 0;
}

export function normalizeCode(raw: string): string {
  return raw.trim().toLocaleUpperCase("tr").replace(/İ/g, "I").replace(/\s+/g, "");
}

export interface CodeState {
  active: boolean;
  validFrom: string | null; // YYYY-MM-DD dahil
  validUntil: string | null; // YYYY-MM-DD dahil
  maxUses: number | null;
  usedCount: number;
}

export type CodeProblem = "CODE_INACTIVE" | "CODE_NOT_STARTED" | "CODE_EXPIRED" | "CODE_EXHAUSTED";

/** Kodun belirtilen yerel günde kullanılabilirliği. */
export function codeProblem(c: CodeState, today: string): CodeProblem | null {
  if (!c.active) return "CODE_INACTIVE";
  if (c.validFrom && today < c.validFrom) return "CODE_NOT_STARTED";
  if (c.validUntil && today > c.validUntil) return "CODE_EXPIRED";
  if (c.maxUses != null && c.usedCount >= c.maxUses) return "CODE_EXHAUSTED";
  return null;
}

export interface LoyaltyProgram {
  enabled: boolean;
  /** Kaç tamamlanmış ziyarette bir ödül kazanılır (3–50). */
  visitsRequired: number;
  /** Ödül indirimi yüzdesi (5–100). */
  rewardPercent: number;
}

/** Kullanılabilir ödül sayısı: floor(tamamlanan / gerekli) − kullanılan/ayrılmış. */
export function loyaltyRewardsAvailable(p: LoyaltyProgram | null | undefined, completedCount: number, rewardsUsed: number): number {
  if (!p || !p.enabled || p.visitsRequired <= 0) return 0;
  return Math.max(0, Math.floor(completedCount / p.visitsRequired) - rewardsUsed);
}

/** Bir sonraki ödüle kalan ziyaret sayısı (program kapalıysa null). */
export function visitsToNextReward(p: LoyaltyProgram | null | undefined, completedCount: number): number | null {
  if (!p || !p.enabled || p.visitsRequired <= 0) return null;
  const r = completedCount % p.visitsRequired;
  return p.visitsRequired - r;
}
