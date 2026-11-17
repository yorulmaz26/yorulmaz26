import { authedCallable } from "../lib/callable";
import { db, FieldValue } from "../lib/admin";
import { fail } from "../lib/errors";
import { docId, optString } from "../lib/validation";
import { auditInTx } from "../lib/audit";
import { notifyInTx } from "../lib/notify";
import { ratingAverage } from "../domain";

interface Req {
  appointmentId?: unknown;
  salonRating?: unknown;
  staffRating?: unknown;
  serviceRating?: unknown;
  comment?: unknown;
}

function stars(v: unknown, field: string, optional = false): number | null {
  if (optional && (v === undefined || v === null)) return null;
  if (!Number.isInteger(v) || (v as number) < 1 || (v as number) > 5) fail("invalid-argument", "INVALID_RATING", { field });
  return v as number;
}

/** "Ayşe Yılmaz" → "Ayşe Y." (KVKK: yorumlarda soyad kısaltılır). */
export function authorDisplay(first: string, last: string): string {
  const l = last.trim();
  return `${first.trim()} ${l ? l.charAt(0).toLocaleUpperCase("tr-TR") + "." : ""}`.trim();
}

/**
 * Değerlendirme (randevu başına bir set, düzenlenemez):
 *  - salonRating → salonun ortalamasına, staffRating → uzmanın ortalamasına girer,
 *  - serviceRating isteğe bağlıdır, ayrı gösterilir ve hiçbir ortalamaya girmez.
 * Belge kimliği = randevu kimliği; transaction içinde yoksa oluşturulur → tekrar/eşzamanlı puanlama imkânsız.
 */
export const submitReview = authedCallable<Req, { reviewId: string; salonRatingAvg: number | null; staffRatingAvg: number | null }>(
  "submitReview",
  async (data, caller) => {
    const appointmentId = docId(data.appointmentId, "appointmentId");
    const salonRating = stars(data.salonRating, "salonRating")!;
    const staffRating = stars(data.staffRating, "staffRating")!;
    const serviceRating = stars(data.serviceRating, "serviceRating", true);
    const comment = optString(data.comment, "comment", 500).replace(/\s+/g, " ");

    return db.runTransaction(async (tx) => {
      const apptRef = db.collection("appointments").doc(appointmentId);
      const reviewRef = db.collection("reviews").doc(appointmentId);
      const appt = await tx.get(apptRef);
      if (!appt.exists) fail("not-found", "APPOINTMENT_NOT_FOUND");
      if (appt.get("customerUid") !== caller.uid) fail("permission-denied", "PERMISSION_DENIED");
      if (appt.get("status") !== "COMPLETED") fail("failed-precondition", "NOT_COMPLETED");
      const salonId = appt.get("salonId") as string;
      const staffUid = appt.get("staffUid") as string;
      const salonRef = db.collection("salons").doc(salonId);
      const memberRef = salonRef.collection("members").doc(staffUid);
      const [existing, salon, member, user] = await Promise.all([tx.get(reviewRef), tx.get(salonRef), tx.get(memberRef), tx.get(db.collection("users").doc(caller.uid))]);
      if (existing.exists) fail("already-exists", "ALREADY_REVIEWED");

      const sSum = ((salon.get("ratingSum") as number | undefined) ?? 0) + salonRating;
      const sCount = ((salon.get("ratingCount") as number | undefined) ?? 0) + 1;
      const mSum = ((member.get("ratingSum") as number | undefined) ?? 0) + staffRating;
      const mCount = ((member.get("ratingCount") as number | undefined) ?? 0) + 1;
      const salonAvg = ratingAverage(sSum, sCount);
      const staffAvg = ratingAverage(mSum, mCount);

      tx.create(reviewRef, {
        salonId,
        staffUid,
        staffName: appt.get("staffName"),
        serviceNames: ((appt.get("items") as { name: string }[] | undefined) ?? []).map((i) => i.name),
        salonRating,
        staffRating,
        serviceRating,
        comment,
        authorDisplay: authorDisplay(user.get("firstName") ?? "Müşteri", user.get("lastName") ?? ""),
        createdAt: FieldValue.serverTimestamp(),
      });
      tx.create(db.collection("reviewsPrivate").doc(appointmentId), { customerUid: caller.uid, salonId, createdAt: FieldValue.serverTimestamp() });
      tx.update(salonRef, { ratingSum: sSum, ratingCount: sCount, ratingAvg: salonAvg });
      if (member.exists) tx.update(memberRef, { ratingSum: mSum, ratingCount: mCount, ratingAvg: staffAvg });
      tx.update(apptRef, { reviewId: appointmentId, updatedAt: FieldValue.serverTimestamp() });
      notifyInTx(tx, staffUid, { type: "REVIEW_RECEIVED", title: "Yeni değerlendirme", body: `Bir müşteriniz ${staffRating} yıldız verdi.`, salonId, appointmentId });
      auditInTx(tx, { action: "REVIEW_SUBMITTED", actorUid: caller.uid, salonId, targetId: appointmentId });
      return { reviewId: appointmentId, salonRatingAvg: salonAvg, staffRatingAvg: staffAvg };
    });
  },
);
