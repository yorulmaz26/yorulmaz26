import { authedCallable } from "../lib/callable";
import { adminAuth, bucket, db, FieldValue, Timestamp } from "../lib/admin";
import { fail } from "../lib/errors";
import { audit } from "../lib/audit";

const ANON = "Silinmiş kullanıcı";

/**
 * Hesap silme (KVKK m.7 / Play hesap silme şartı). Kişisel veriler silinir veya anonimleştirilir;
 * mali kayıtlar (ledger) ve randevu kayıtları kişisel veri içermeyecek şekilde yasal saklama için korunur.
 *  - Aktif (bekleyen/onaylı/işlemde) müşteri randevusu varsa: ACTIVE_APPOINTMENT_EXISTS (önce iptal edilmeli).
 *  - Bir salonun sahibiyse: OWNER_MUST_CLOSE_SALON (salon devri/kapatma destek süreciyle yapılır).
 *  - Personel üyelikleri LEFT yapılır; uzman olarak gelecek aktif randevusu varsa STAFF_HAS_ACTIVE_APPOINTMENTS.
 */
export const deleteAccount = authedCallable<{ confirm?: unknown }, { deleted: true }>("deleteAccount", async (data, caller) => {
  if (data.confirm !== "SIL") fail("invalid-argument", "CONFIRMATION_REQUIRED");
  const uid = caller.uid;

  const owner = await db.collection("salonOwners").doc(uid).get();
  if (owner.exists) fail("failed-precondition", "OWNER_MUST_CLOSE_SALON", { salonId: owner.get("salonId") });

  const appts = db.collection("appointments");
  const activeStates = ["PENDING", "CONFIRMED", "IN_PROGRESS"];
  const nowTs = Timestamp.now();
  const [asCustomer, asStaff] = await Promise.all([
    appts.where("customerUid", "==", uid).where("status", "in", activeStates).get(),
    appts.where("staffUid", "==", uid).where("status", "in", activeStates).get(),
  ]);
  // Süresi dolmuş PENDING talepler aktif sayılmaz.
  const live = (d: FirebaseFirestore.QueryDocumentSnapshot) =>
    !(d.get("status") === "PENDING" && (d.get("pendingExpiresAt") as Timestamp).toMillis() <= nowTs.toMillis());
  if (asCustomer.docs.some(live)) fail("failed-precondition", "ACTIVE_APPOINTMENT_EXISTS");
  if (asStaff.docs.some(live)) fail("failed-precondition", "STAFF_HAS_ACTIVE_APPOINTMENTS");

  const userRef = db.collection("users").doc(uid);
  const writer = db.bulkWriter();

  // Randevular: müşteri kişisel verisi anonimleştirilir (salon kayıtları ve finans korunur).
  const mine = await appts.where("customerUid", "==", uid).get();
  mine.docs.forEach((d) => writer.update(d.ref, { customerName: ANON, customerPhoneE164: "", customerDeleted: true }));
  // Değerlendirmeler: yazar adı anonim; private eşleme silinir.
  const priv = await db.collection("reviewsPrivate").where("customerUid", "==", uid).get();
  priv.docs.forEach((d) => {
    writer.update(db.collection("reviews").doc(d.id), { authorDisplay: ANON });
    writer.delete(d.ref);
  });
  // Personel üyelikleri sonlandırılır.
  const memberships = await userRef.collection("memberships").get();
  for (const m of memberships.docs) {
    writer.set(db.collection("salons").doc(m.id).collection("members").doc(uid), { status: "LEFT", acceptsBookings: false, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    writer.delete(m.ref);
  }
  // Kişiye ait alt koleksiyonlar ve kilitler.
  for (const sub of ["devices", "notifications"]) {
    const snap = await userRef.collection(sub).get();
    snap.docs.forEach((d) => writer.delete(d.ref));
  }
  writer.delete(db.collection("customerLocks").doc(uid));
  writer.delete(userRef);
  await writer.close();

  await bucket().deleteFiles({ prefix: `uploads/${uid}/` }).catch(() => undefined);
  await audit({ action: "ACCOUNT_DELETED", actorUid: uid, data: { appointmentsAnonymized: mine.size } });
  await adminAuth().deleteUser(uid).catch(() => undefined);
  return { deleted: true };
});
