import { Transaction } from "firebase-admin/firestore";
import { db, FieldValue, Timestamp } from "../lib/admin";
import { localDateOf, PeriodKeys, periodKeysOf } from "../domain";

export const ledgerCol = () => db.collection("financialLedger");
export const aggregateRef = (salonId: string, scope: string, periodKey: string) =>
  db.collection("reportAggregates").doc(`${salonId}_${scope}_${periodKey}`);

export type LedgerType = "SERVICE_COMPLETED" | "ADJUSTMENT" | "REVERSAL";

export interface LedgerWrite {
  id: string;
  type: LedgerType;
  salonId: string;
  staffUid: string;
  appointmentId: string;
  amountKurus: number;
  /** Tamamlama kaydında: randevudaki liste (rezervasyon) toplamı. */
  listedKurus: number;
  /** Uzman primi: tamamlama anındaki oran (%) ve tutar. Düzeltmelerde fark tutarı. */
  commissionPercent?: number;
  commissionKurus?: number;
  countDelta: number;
  occurredAt: Date;
  /** Raporun yazılacağı dönemler (düzeltmelerde orijinal tamamlama dönemleri). */
  periodKeys: PeriodKeys;
  createdBy: string;
  reason?: string | null;
}

/**
 * Değişmez ledger hareketi + 6 rapor sayacı (salon ALL ve uzman × gün/hafta/ay) aynı transaction'da.
 * Ledger belgesi tx.create ile yazılır: aynı kimlikle ikinci yazım transaction'ı düşürür → çift sayım imkânsız.
 * Sayaçlar sıfırlanmaz; yeni dönem yeni anahtar demektir, geçmiş raporlar korunur.
 */
export function writeLedger(tx: Transaction, e: LedgerWrite): void {
  tx.create(ledgerCol().doc(e.id), {
    type: e.type,
    salonId: e.salonId,
    staffUid: e.staffUid,
    appointmentId: e.appointmentId,
    amountKurus: e.amountKurus,
    listedKurus: e.listedKurus,
    commissionPercent: e.commissionPercent ?? 0,
    commissionKurus: e.commissionKurus ?? 0,
    currency: "TRY",
    occurredAt: Timestamp.fromDate(e.occurredAt),
    localDate: localDateOf(e.occurredAt),
    periodKeys: e.periodKeys,
    createdBy: e.createdBy,
    reason: e.reason ?? null,
    createdAt: FieldValue.serverTimestamp(),
  });
  const periods: [string, string][] = [["DAY", e.periodKeys.day], ["WEEK", e.periodKeys.week], ["MONTH", e.periodKeys.month]];
  for (const [periodType, key] of periods) {
    for (const scope of ["ALL", e.staffUid]) {
      tx.set(
        aggregateRef(e.salonId, scope, key),
        {
          salonId: e.salonId,
          staffUid: scope === "ALL" ? null : scope,
          periodType,
          periodKey: key,
          completedCount: FieldValue.increment(e.countDelta),
          amountKurus: FieldValue.increment(e.amountKurus),
          listedKurus: FieldValue.increment(e.listedKurus),
          commissionKurus: FieldValue.increment(e.commissionKurus ?? 0),
          updatedAt: FieldValue.serverTimestamp(),
        },
        { merge: true },
      );
    }
  }
}

export { periodKeysOf };
