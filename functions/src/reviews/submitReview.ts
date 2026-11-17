import { authedCallable } from "../lib/callable";
import { db, FieldValue } from "../lib/admin";
import { fail } from "../lib/errors";
import { docId, optString } from "../lib/validation";
import { auditInTx } from "../lib/audit";
import { notifyInTx } from "../lib/notify";

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
 * Değerlendirme (randevu başına bir set, düzenlenemez). Salon sahibi onaylayana kadar YAYINLANMAZ
 * (hidden:true, approval:PENDING) ve puanlar ortalamalara eklenmez; onayda (decideReview) eklenir.
 *  - salonRating → salonun ortalamasına, staffRating → uzmanın ortalamasına girer,
 *  - serviceRating isteğe bağlıdır, ayrı gösterilir ve hiçbir ortalamaya girmez.
 * Belge kimliği = randevu kimliği; transaction içinde yoksa oluşturulur → tekrar/eşzamanlı puanlama imkânsız.
 */
export const submitReview = authedCallable<Req, { reviewId: string; approval: "PENDING" }>(
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
      const [existing, salon, user] = await Promise.all([tx.get(reviewRef), tx.get(salonRef), tx.get(db.collection("users").doc(caller.uid))]);
      if (existing.exists) fail("already-exists", "ALREADY_REVIEWED");

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
        // Salon sahibi onayına kadar kamuya kapalı; ortalamalara henüz girmez.
        hidden: true,
        approval: "PENDING",
        flagged: false,
        reportCount: 0,
        createdAt: FieldValue.serverTimestamp(),
      });
      tx.create(db.collection("reviewsPrivate").doc(appointmentId), { customerUid: caller.uid, salonId, createdAt: FieldValue.serverTimestamp() });
      tx.update(apptRef, { reviewId: appointmentId, reviewStatus: "PENDING", updatedAt: FieldValue.serverTimestamp() });
      notifyInTx(tx, salon.get("ownerUid"), {
        type: "REVIEW_PENDING", title: "Onay bekleyen değerlendirme",
        body: "Bir müşteriniz değerlendirme yaptı. Yayınlanması için onayınız gerekiyor.", salonId, appointmentId,
      });
      auditInTx(tx, { action: "REVIEW_SUBMITTED", actorUid: caller.uid, salonId, targetId: appointmentId });
      return { reviewId: appointmentId, approval: "PENDING" as const };
    });
  },
);
