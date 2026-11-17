import { authedCallable } from "../lib/callable";
import { db, FieldValue } from "../lib/admin";
import { fail } from "../lib/errors";
import { docId, optString } from "../lib/validation";
import { enforceRateLimit } from "../lib/rateLimit";
import { auditInTx } from "../lib/audit";
import { notifyInTx } from "../lib/notify";
import { ratingAverage } from "../domain";

/** Bu kadar farklı kullanıcı şikâyet edince yorum moderasyon kuyruğuna düşer (yayında kalır). */
export const FLAG_THRESHOLD = 3;
const REASONS = ["OFFENSIVE", "SPAM", "FALSE_INFO", "PERSONAL_DATA", "OTHER"] as const;
type Reason = (typeof REASONS)[number];

/** Oturumlu herkes bir yorumu bir kez şikâyet edebilir (hakaret, spam, yanlış bilgi, kişisel veri). */
export const reportReview = authedCallable<{ reviewId?: unknown; reason?: unknown; note?: unknown }, { reportCount: number; flagged: boolean }>(
  "reportReview",
  async (data, caller) => {
    const reviewId = docId(data.reviewId, "reviewId");
    const reason = data.reason as Reason;
    if (!REASONS.includes(reason)) fail("invalid-argument", "INVALID_ARGUMENT", { field: "reason" });
    const note = optString(data.note, "note", 300);
    await enforceRateLimit(`report_${caller.uid}`, 20, 86_400);
    return db.runTransaction(async (tx) => {
      const ref = db.collection("reviews").doc(reviewId);
      const reportRef = db.collection("reviewReports").doc(`${reviewId}_${caller.uid}`);
      const [review, existing] = await Promise.all([tx.get(ref), tx.get(reportRef)]);
      if (!review.exists || review.get("hidden") === true) fail("not-found", "REVIEW_NOT_FOUND");
      if (existing.exists) fail("already-exists", "ALREADY_REPORTED");
      const reportCount = ((review.get("reportCount") as number | undefined) ?? 0) + 1;
      const flagged = review.get("flagged") === true || reportCount >= FLAG_THRESHOLD || reason === "PERSONAL_DATA";
      tx.create(reportRef, { reviewId, reporterUid: caller.uid, reason, note, salonId: review.get("salonId"), createdAt: FieldValue.serverTimestamp() });
      tx.update(ref, { reportCount, flagged, lastReportedAt: FieldValue.serverTimestamp() });
      return { reportCount, flagged };
    });
  },
);

/**
 * Platform yöneticisi kararı. HIDE: yorum yayından kalkar ve puanları salon/uzman ortalamasından çıkarılır.
 * RESTORE: geri yayınlanır ve puanlar yeniden eklenir. DISMISS: şikâyet reddedilir, yorum yayında kalır.
 */
export const moderateReview = authedCallable<{ reviewId?: unknown; action?: unknown; note?: unknown }, { hidden: boolean; flagged: boolean }>(
  "moderateReview",
  async (data, caller) => {
    if (!caller.isAdmin) fail("permission-denied", "PERMISSION_DENIED");
    const reviewId = docId(data.reviewId, "reviewId");
    const action = data.action as "HIDE" | "RESTORE" | "DISMISS";
    if (!["HIDE", "RESTORE", "DISMISS"].includes(action)) fail("invalid-argument", "INVALID_ARGUMENT", { field: "action" });
    const note = optString(data.note, "note", 300);

    return db.runTransaction(async (tx) => {
      const ref = db.collection("reviews").doc(reviewId);
      const review = await tx.get(ref);
      if (!review.exists) fail("not-found", "REVIEW_NOT_FOUND");
      const salonRef = db.collection("salons").doc(review.get("salonId"));
      const memberRef = salonRef.collection("members").doc(review.get("staffUid"));
      const [salon, member] = await Promise.all([tx.get(salonRef), tx.get(memberRef)]);
      const hidden = review.get("hidden") === true;

      const apply = (sign: 1 | -1) => {
        const sSum = ((salon.get("ratingSum") as number | undefined) ?? 0) + sign * (review.get("salonRating") as number);
        const sCount = Math.max(0, ((salon.get("ratingCount") as number | undefined) ?? 0) + sign);
        tx.update(salonRef, { ratingSum: sSum, ratingCount: sCount, ratingAvg: ratingAverage(sSum, sCount) });
        if (member.exists) {
          const mSum = ((member.get("ratingSum") as number | undefined) ?? 0) + sign * (review.get("staffRating") as number);
          const mCount = Math.max(0, ((member.get("ratingCount") as number | undefined) ?? 0) + sign);
          tx.update(memberRef, { ratingSum: mSum, ratingCount: mCount, ratingAvg: ratingAverage(mSum, mCount) });
        }
      };

      let next = { hidden, flagged: false };
      if (action === "HIDE") {
        if (hidden) fail("failed-precondition", "STATE_CHANGED");
        apply(-1);
        next = { hidden: true, flagged: false };
        notifyInTx(tx, salon.get("ownerUid"), { type: "REVIEW_HIDDEN", title: "Bir değerlendirme kaldırıldı", body: "Topluluk kurallarına aykırı bulunan bir değerlendirme yayından kaldırıldı ve puanınızdan çıkarıldı.", salonId: salon.id });
      } else if (action === "RESTORE") {
        // Salon sahibinin onayını bekleyen/reddettiği yorum moderasyonla yayınlanamaz.
        if (!hidden || ["PENDING", "REJECTED"].includes(review.get("approval"))) fail("failed-precondition", "STATE_CHANGED");
        apply(1);
        next = { hidden: false, flagged: false };
      }
      tx.update(ref, { ...next, moderatedAt: FieldValue.serverTimestamp(), moderatedBy: caller.uid, moderationNote: note || null });
      auditInTx(tx, { action: `REVIEW_${action}`, actorUid: caller.uid, salonId: salon.id, targetId: reviewId, data: { note } });
      return next;
    });
  },
);
