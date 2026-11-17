import { authedCallable } from "../lib/callable";
import { db, FieldValue } from "../lib/admin";
import { fail } from "../lib/errors";
import { docId, optString } from "../lib/validation";
import { enforceRateLimit } from "../lib/rateLimit";
import { auditInTx } from "../lib/audit";
import { notifyInTx } from "../lib/notify";

/**
 * Salon sahibinin yoruma herkese açık yanıtı (tek yanıt; düzenlenebilir). Boş metin yanıtı kaldırır.
 * Yorum sahibine bildirim gider (kimliği reviewsPrivate'tan; yanıtta kişisel veri tutulmaz).
 */
export const replyToReview = authedCallable<{ reviewId?: unknown; text?: unknown }, { replied: boolean }>("replyToReview", async (data, caller) => {
  const reviewId = docId(data.reviewId, "reviewId");
  const text = optString(data.text, "text", 500).replace(/\s+/g, " ");
  if (text && text.length < 2) fail("invalid-argument", "INVALID_ARGUMENT", { field: "text" });
  await enforceRateLimit(`reply_${caller.uid}`, 60, 86400);
  return db.runTransaction(async (tx) => {
    const reviewRef = db.collection("reviews").doc(reviewId);
    const [review, priv] = await Promise.all([tx.get(reviewRef), tx.get(db.collection("reviewsPrivate").doc(reviewId))]);
    if (!review.exists) fail("not-found", "REVIEW_NOT_FOUND");
    const salon = await tx.get(db.collection("salons").doc(review.get("salonId")));
    if (salon.get("ownerUid") !== caller.uid) fail("permission-denied", "PERMISSION_DENIED");
    if (!text) {
      tx.update(reviewRef, { ownerReply: FieldValue.delete() });
    } else {
      const first = !review.get("ownerReply");
      tx.update(reviewRef, { ownerReply: { text, at: FieldValue.serverTimestamp(), by: salon.get("ownerDisplayName") ?? salon.get("name") } });
      if (first) {
        notifyInTx(tx, priv.get("customerUid"), { type: "REVIEW_REPLIED", title: "Yorumunuza yanıt geldi", body: `${salon.get("name")} değerlendirmenize yanıt verdi.`, salonId: review.get("salonId"), appointmentId: reviewId });
      }
    }
    auditInTx(tx, { action: text ? "REVIEW_REPLIED" : "REVIEW_REPLY_REMOVED", actorUid: caller.uid, salonId: review.get("salonId"), targetId: reviewId });
    return { replied: !!text };
  });
});
