import { caller, clearFirestore, expectReason, uuid } from "./helpers";
import { registerCustomer } from "../src/auth/session";
import { registerSalonOwner } from "../src/salon/registerSalonOwner";
import { acceptInvitation, createStaffInvitation } from "../src/staff/invitations";
import { createAppointment, transitionAppointment } from "../src/booking/appointments";
import { submitReview } from "../src/reviews/submitReview";
import { deleteAccount } from "../src/account/deleteAccount";
import { cleanupExpiredKeys, cleanupPendingUploads } from "../src/scheduled/cleanup";
import { LEGAL_VERSIONS } from "../src/lib/validation";
import { adminAuth, bucket, db, Timestamp } from "../src/lib/admin";
import { addDays, instantAt, isoWeekday, localDateOf } from "../src/domain";

const consents = { kvkkVersion: LEGAL_VERSIONS.kvkk, termsVersion: LEGAL_VERSIONS.terms };
const del = caller(deleteAccount);
const transition = caller(transitionAppointment);
const OWNER = "+905301110000";
const STAFF = "+905551112233";
const C1 = "+905320000001";
let salonId = "";

function nextWeekday(target: number): string {
  let d = addDays(localDateOf(new Date()), 2);
  while (isoWeekday(d) !== target) d = addDays(d, 1);
  return d;
}
const TUE = nextWeekday(2);

beforeEach(async () => {
  await clearFirestore();
  const path = "uploads/owner/pending/logo.jpg";
  await bucket().file(path).save(Buffer.alloc(1000, 1), { contentType: "image/jpeg" });
  salonId = (await caller(registerSalonOwner)({
    firstName: "Ayşe", lastName: "Yılmaz", consents, logoPath: path, photoPaths: [], idempotencyKey: uuid(),
    salon: { name: "Işıltı", businessPhone: "02125556677", type: "BEAUTY_SALON", city: "Ankara", district: "Çankaya", addressLine: "Kızılay Mah. 1" },
  }, "owner", OWNER)).salonId;
  const inv = await caller(createStaffInvitation)({ salonId, firstName: "Can", lastName: "Er", specialty: "Berber", phone: STAFF, idempotencyKey: uuid() }, "owner", OWNER);
  await caller(acceptInvitation)({ salonId, inviteId: inv.inviteId, consents }, "staff", STAFF);
  await db.doc(`salons/${salonId}/services/kesim`).set({ staffUid: "staff", salonId, name: "Kesim", nameLower: "kesim", priceKurus: 40000, durationMinutes: 30, active: true, deleted: false });
  await adminAuth().createUser({ uid: "c1", phoneNumber: C1 }).catch(() => undefined);
  await caller(registerCustomer)({ firstName: "Ali", lastName: "Kaya", consents, idempotencyKey: uuid() }, "c1", C1);
});

test("aktif randevusu varken silinemez; sonra kişisel veri anonimleşir, finans korunur", async () => {
  const a = await caller(createAppointment)({ salonId, staffUid: "staff", serviceIds: ["kesim"], startAt: instantAt(TUE, 600).toISOString(), expectedTotalKurus: 40000, idempotencyKey: uuid() }, "c1", C1);
  await expectReason(del({ confirm: "SIL" }, "c1", C1), "ACTIVE_APPOINTMENT_EXISTS");
  await expectReason(del({ confirm: "EVET" }, "c1", C1), "CONFIRMATION_REQUIRED");
  await transition({ appointmentId: a.appointmentId, action: "CONFIRM" }, "staff", STAFF);
  await db.doc(`appointments/${a.appointmentId}`).update({ startAt: Timestamp.fromMillis(Date.now() - 60_000) });
  await transition({ appointmentId: a.appointmentId, action: "MARK_ARRIVED" }, "staff", STAFF);
  await transition({ appointmentId: a.appointmentId, action: "COMPLETE" }, "staff", STAFF);
  await caller(submitReview)({ appointmentId: a.appointmentId, salonRating: 5, staffRating: 5, comment: "Harika" }, "c1", C1);

  await del({ confirm: "SIL" }, "c1", C1);
  expect((await db.doc("users/c1").get()).exists).toBe(false);
  const appt = await db.doc(`appointments/${a.appointmentId}`).get();
  expect(appt.get("customerName")).toBe("Silinmiş kullanıcı");
  expect(appt.get("customerPhoneE164")).toBe("");
  expect(appt.get("finalKurus")).toBe(40000);
  expect((await db.doc(`financialLedger/completion_${a.appointmentId}`).get()).exists).toBe(true);
  expect((await db.doc(`reviews/${a.appointmentId}`).get()).get("authorDisplay")).toBe("Silinmiş kullanıcı");
  expect((await db.doc(`reviewsPrivate/${a.appointmentId}`).get()).exists).toBe(false);
  await expect(adminAuth().getUser("c1")).rejects.toBeTruthy();
});

test("salon sahibi doğrudan silinemez; personel üyeliği sonlandırılır", async () => {
  await expectReason(del({ confirm: "SIL" }, "owner", OWNER), "OWNER_MUST_CLOSE_SALON");
  await del({ confirm: "SIL" }, "staff", STAFF);
  expect((await db.doc(`salons/${salonId}/members/staff`).get()).get("status")).toBe("LEFT");
});

test("bakım: eski geçici yüklemeler ve süresi dolmuş anahtarlar silinir", async () => {
  await bucket().file("uploads/x/pending/old.jpg").save(Buffer.alloc(10), { contentType: "image/jpeg" });
  expect(await cleanupPendingUploads(Date.now() + 25 * 3600_000)).toBeGreaterThanOrEqual(1);
  await db.doc("idempotencyKeys/k1").set({ expireAt: Timestamp.fromMillis(Date.now() - 1000) });
  await db.doc("rateLimits/r1").set({ expireAt: Timestamp.fromMillis(Date.now() + 100000) });
  expect(await cleanupExpiredKeys()).toBe(1);
  expect((await db.doc("rateLimits/r1").get()).exists).toBe(true);
});
