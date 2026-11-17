import { authedCallable } from "../lib/callable";
import { db, FieldValue } from "../lib/admin";
import { fail } from "../lib/errors";
import { docId, optString } from "../lib/validation";
import { auditInTx } from "../lib/audit";
import { notifyInTx } from "../lib/notify";
import { ratingAverage } from "../domain";

/**
 * Salon sahibinin değerlendirme kararı:
 *  - APPROVE: yorum yayınlanır, salon ve uzman puan ortalamalarına eklenir; müşteri ve uzman bilgilendirilir.
 *  - REJECT (gerekçe zorunlu): yorum yayınlanmaz, puanlara girmez; müşteri gerekçeyle bilgilendirilir.
 *    Reddedilen yorumlar platform yöneticisinin denetimine açık kalır (kötüye kullanım takibi).
 * Yalnızca onay bekleyen (PENDING) yorum için; karar bir kez verilir.
 */
export const decideReview = authedCallable<{ reviewId?: unknown; action?: unknown; note?: unknown }, { approval: "APPROVED" | "REJECTED" }>(
  "decideReview",
  async (data, caller) => {
    const reviewId = docId(data.reviewId, "reviewId");
    const action = data.action;
    if (action !== "APPROVE" && action !== "REJECT") fail("invalid-argument", "INVALID_ARGUMENT", { field: "action" });
    const note = optString(data.note, "note", 300) || null;
    if (action === "REJECT" && !(note && note.length >= 3)) fail("invalid-argument", "REASON_REQUIRED");

    return db.runTransaction(async (tx) => {
      const ref = db.collection("reviews").doc(reviewId);
      const review = await tx.get(ref);
      if (!review.exists) fail("not-found", "REVIEW_NOT_FOUND");
      const salonRef = db.collection("salons").doc(review.get("salonId"));
      const memberRef = salonRef.collection("members").doc(review.get("staffUid"));
      const [salon, member, priv] = await Promise.all([tx.get(salonRef), tx.get(memberRef), tx.get(db.collection("reviewsPrivate").doc(reviewId))]);
      if (salon.get("ownerUid") !== caller.uid) fail("permission-denied", "PERMISSION_DENIED");
      if (review.get("approval") !== "PENDING") fail("failed-precondition", "STATE_CHANGED", { approval: review.get("approval") ?? "APPROVED" });

      const customerUid = priv.get("customerUid") as string | undefined;
      const apptRef = db.collection("appointments").doc(reviewId);
      const base = { salonId: salon.id, appointmentId: reviewId };
      if (action === "APPROVE") {
        const sSum = ((salon.get("ratingSum") as number | undefined) ?? 0) + (review.get("salonRating") as number);
        const sCount = ((salon.get("ratingCount") as number | undefined) ?? 0) + 1;
        tx.update(salonRef, { ratingSum: sSum, ratingCount: sCount, ratingAvg: ratingAverage(sSum, sCount) });
        if (member.exists) {
          const mSum = ((member.get("ratingSum") as number | undefined) ?? 0) + (review.get("staffRating") as number);
          const mCount = ((member.get("ratingCount") as number | undefined) ?? 0) + 1;
          tx.update(memberRef, { ratingSum: mSum, ratingCount: mCount, ratingAvg: ratingAverage(mSum, mCount) });
        }
        tx.update(ref, { hidden: false, approval: "APPROVED", decidedAt: FieldValue.serverTimestamp(), decidedBy: caller.uid, publishedAt: FieldValue.serverTimestamp() });
        tx.update(apptRef, { reviewStatus: "APPROVED" });
        notifyInTx(tx, customerUid, { ...base, type: "REVIEW_APPROVED", title: "Değerlendirmeniz yayınlandı", body: `${salon.get("name")} değerlendirmenizi yayınladı. Teşekkürler!` });
        if (review.get("staffUid") !== caller.uid) {
          notifyInTx(tx, review.get("staffUid"), { ...base, type: "REVIEW_RECEIVED", title: "Yeni değerlendirme", body: `Bir müşteriniz ${review.get("staffRating")} yıldız verdi.` });
        }
      } else {
        tx.update(ref, { hidden: true, approval: "REJECTED", rejectNote: note, decidedAt: FieldValue.serverTimestamp(), decidedBy: caller.uid });
        tx.update(apptRef, { reviewStatus: "REJECTED" });
        notifyInTx(tx, customerUid, { ...base, type: "REVIEW_REJECTED", title: "Değerlendirmeniz yayınlanmadı", body: `${salon.get("name")} değerlendirmenizi yayınlamadı. Gerekçe: ${note}` });
      }
      auditInTx(tx, { action: action === "APPROVE" ? "REVIEW_APPROVED" : "REVIEW_REJECTED", actorUid: caller.uid, salonId: salon.id, targetId: reviewId, data: { note } });
      return { approval: action === "APPROVE" ? "APPROVED" as const : "REJECTED" as const };
    });
  },
);
