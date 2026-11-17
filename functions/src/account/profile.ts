import { authedCallable } from "../lib/callable";
import { db, FieldValue, Timestamp } from "../lib/admin";
import { fail } from "../lib/errors";
import { personName } from "../lib/validation";
import { audit } from "../lib/audit";

const ACTIVE = ["PENDING", "CONFIRMED", "IN_PROGRESS"];

/**
 * Ad/soyad değişikliği: profil, uzman kartları (tüm salonlar), sahip adı ve gelecekteki aktif randevulardaki
 * müşteri adı birlikte güncellenir. Geçmiş randevular olduğu gibi kalır (o anki kaydı yansıtır).
 */
export const updateMyName = authedCallable<{ firstName?: unknown; lastName?: unknown }, { ok: true }>("updateMyName", async (data, caller) => {
  const firstName = personName(data.firstName, "firstName");
  const lastName = personName(data.lastName, "lastName");
  const userRef = db.collection("users").doc(caller.uid);
  if (!(await userRef.get()).exists) fail("failed-precondition", "PROFILE_REQUIRED");
  const full = `${firstName} ${lastName}`;
  const writer = db.bulkWriter();
  writer.update(userRef, { firstName, lastName, updatedAt: FieldValue.serverTimestamp() });
  const memberships = await userRef.collection("memberships").get();
  for (const m of memberships.docs) {
    const salonRef = db.collection("salons").doc(m.id);
    writer.set(salonRef.collection("members").doc(caller.uid), { firstName, lastName, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    if (m.get("role") === "OWNER" && m.get("status") === "ACTIVE") writer.update(salonRef, { ownerDisplayName: full });
  }
  const future = await db.collection("appointments").where("customerUid", "==", caller.uid).where("status", "in", ACTIVE).get();
  future.docs.forEach((d) => writer.update(d.ref, { customerName: full }));
  const asStaff = await db.collection("appointments").where("staffUid", "==", caller.uid).where("status", "in", ACTIVE).get();
  asStaff.docs.forEach((d) => writer.update(d.ref, { staffName: full }));
  await writer.close();
  return { ok: true };
});

/**
 * Telefon numarası değişikliği: istemci Firebase Auth'ta yeni numarayı OTP ile doğrular
 * (currentUser.updatePhoneNumber), token'ı yeniler ve bunu çağırır. Sunucu yeni numarayı yalnızca
 * doğrulanmış token'dan alır; profil ve aktif randevulardaki iletişim numarası güncellenir.
 */
export const syncPhoneNumber = authedCallable<Record<string, never>, { phoneE164: string }>("syncPhoneNumber", async (_d, caller) => {
  if (!caller.phone) fail("failed-precondition", "PHONE_NOT_VERIFIED");
  const userRef = db.collection("users").doc(caller.uid);
  const user = await userRef.get();
  if (!user.exists) fail("failed-precondition", "PROFILE_REQUIRED");
  const old = user.get("phoneE164") as string | undefined;
  if (old === caller.phone) return { phoneE164: caller.phone };
  const writer = db.bulkWriter();
  writer.update(userRef, { phoneE164: caller.phone, phoneChangedAt: Timestamp.now(), updatedAt: FieldValue.serverTimestamp() });
  const future = await db.collection("appointments").where("customerUid", "==", caller.uid).where("status", "in", ACTIVE).get();
  future.docs.forEach((d) => writer.update(d.ref, { customerPhoneE164: caller.phone }));
  await writer.close();
  await audit({ action: "PHONE_CHANGED", actorUid: caller.uid, data: { appointments: future.size } });
  return { phoneE164: caller.phone };
});
