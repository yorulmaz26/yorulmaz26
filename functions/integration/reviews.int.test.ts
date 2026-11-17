import { caller, clearFirestore, expectReason, uuid } from "./helpers";
import { registerCustomer } from "../src/auth/session";
import { registerSalonOwner } from "../src/salon/registerSalonOwner";
import { acceptInvitation, createStaffInvitation } from "../src/staff/invitations";
import { createAppointment, transitionAppointment } from "../src/booking/appointments";
import { authorDisplay, submitReview } from "../src/reviews/submitReview";
import { decideReview } from "../src/reviews/approval";
import { moderateReview } from "../src/reviews/moderation";
import { LEGAL_VERSIONS } from "../src/lib/validation";
import { bucket, db, Timestamp } from "../src/lib/admin";
import { addDays, instantAt, isoWeekday, localDateOf } from "../src/domain";

const consents = { kvkkVersion: LEGAL_VERSIONS.kvkk, termsVersion: LEGAL_VERSIONS.terms };
const book = caller(createAppointment);
const transition = caller(transitionAppointment);
const review = caller(submitReview);
const decide = caller(decideReview);
const approve = (id: string) => decide({ reviewId: id, action: "APPROVE" }, "owner", OWNER);
const OWNER = "+905301110000";
const STAFF = "+905551112233";
const C1 = "+905320000001";
const C2 = "+905320000002";
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
  await caller(registerCustomer)({ firstName: "Ali", lastName: "Kaya", consents, idempotencyKey: uuid() }, "c1", C1);
  await caller(registerCustomer)({ firstName: "Zeynep", lastName: "Şahin", consents, idempotencyKey: uuid() }, "c2", C2);
});

async function completed(uid: string, phone: string, start: number): Promise<string> {
  const a = await book({ salonId, staffUid: "staff", serviceIds: ["kesim"], startAt: instantAt(TUE, start).toISOString(), expectedTotalKurus: 40000, idempotencyKey: uuid() }, uid, phone);
  await transition({ appointmentId: a.appointmentId, action: "CONFIRM" }, "staff", STAFF);
  await db.doc(`appointments/${a.appointmentId}`).update({ startAt: Timestamp.fromMillis(Date.now() - 60_000) });
  await transition({ appointmentId: a.appointmentId, action: "MARK_ARRIVED" }, "staff", STAFF);
  await transition({ appointmentId: a.appointmentId, action: "COMPLETE" }, "staff", STAFF);
  return a.appointmentId;
}

test("tamamlanmamış randevu puanlanamaz", async () => {
  const a = await book({ salonId, staffUid: "staff", serviceIds: ["kesim"], startAt: instantAt(TUE, 600).toISOString(), expectedTotalKurus: 40000, idempotencyKey: uuid() }, "c1", C1);
  await expectReason(review({ appointmentId: a.appointmentId, salonRating: 5, staffRating: 5 }, "c1", C1), "NOT_COMPLETED");
});

test("yalnızca randevunun müşterisi, bir kez; ortalamalar ayrı ve 1 ondalık", async () => {
  const a1 = await completed("c1", C1, 600);
  await expectReason(review({ appointmentId: a1, salonRating: 5, staffRating: 5 }, "c2", C2), "PERMISSION_DENIED");
  await expectReason(review({ appointmentId: a1, salonRating: 6, staffRating: 5 }, "c1", C1), "INVALID_RATING");
  const r = await review({ appointmentId: a1, salonRating: 5, staffRating: 4, serviceRating: 3, comment: "  Çok   memnun kaldım " }, "c1", C1);
  expect(r).toEqual({ reviewId: a1, approval: "PENDING" });
  await expectReason(review({ appointmentId: a1, salonRating: 1, staffRating: 1 }, "c1", C1), "ALREADY_REVIEWED");
  await approve(a1);
  expect((await db.doc(`salons/${salonId}`).get()).get("ratingAvg")).toBe(5);

  const a2 = await completed("c2", C2, 720);
  await review({ appointmentId: a2, salonRating: 4, staffRating: 4 }, "c2", C2);
  await approve(a2);
  const salon = await db.doc(`salons/${salonId}`).get();
  expect([salon.get("ratingSum"), salon.get("ratingCount"), salon.get("ratingAvg")]).toEqual([9, 2, 4.5]);
  const member = await db.doc(`salons/${salonId}/members/staff`).get();
  expect([member.get("ratingSum"), member.get("ratingCount")]).toEqual([8, 2]);
  const rev = await db.doc(`reviews/${a1}`).get();
  expect(rev.get("comment")).toBe("Çok memnun kaldım");
  expect(rev.get("authorDisplay")).toBe("Ali K.");
  expect(rev.get("serviceRating")).toBe(3);
  expect(rev.get("customerUid")).toBeUndefined(); // kamuya açık belgede uid yok
  expect((await db.doc(`reviewsPrivate/${a1}`).get()).get("customerUid")).toBe("c1");
});

test("eşzamanlı iki puanlama isteğinden yalnızca biri sayılır", async () => {
  const a1 = await completed("c1", C1, 600);
  const res = await Promise.allSettled([
    review({ appointmentId: a1, salonRating: 5, staffRating: 5 }, "c1", C1),
    review({ appointmentId: a1, salonRating: 1, staffRating: 1 }, "c1", C1),
  ]);
  expect(res.filter((x) => x.status === "fulfilled")).toHaveLength(1);
  await approve(a1);
  expect((await db.doc(`salons/${salonId}`).get()).get("ratingCount")).toBe(1);
});

test("salon sahibi onayına kadar yayınlanmaz ve puanlara girmez; ret gerekçeli, karar bir kez", async () => {
  const a1 = await completed("c1", C1, 600);
  await review({ appointmentId: a1, salonRating: 1, staffRating: 2, comment: "Beğenmedim" }, "c1", C1);
  const pending = await db.doc(`reviews/${a1}`).get();
  expect([pending.get("hidden"), pending.get("approval")]).toEqual([true, "PENDING"]);
  expect((await db.doc(`salons/${salonId}`).get()).get("ratingCount")).toBe(0);
  expect((await db.doc(`appointments/${a1}`).get()).get("reviewStatus")).toBe("PENDING");
  expect((await db.collection("users/owner/notifications").where("type", "==", "REVIEW_PENDING").get()).size).toBe(1);

  // Yalnızca salon sahibi karar verir; ret için gerekçe zorunlu.
  await expectReason(decide({ reviewId: a1, action: "APPROVE" }, "staff", STAFF), "PERMISSION_DENIED");
  await expectReason(decide({ reviewId: a1, action: "REJECT" }, "owner", OWNER), "REASON_REQUIRED");
  await decide({ reviewId: a1, action: "REJECT", note: "Hakaret içeriyor" }, "owner", OWNER);
  const rejected = await db.doc(`reviews/${a1}`).get();
  expect([rejected.get("hidden"), rejected.get("approval")]).toEqual([true, "REJECTED"]);
  expect((await db.doc(`salons/${salonId}`).get()).get("ratingCount")).toBe(0);
  expect((await db.collection("users/c1/notifications").where("type", "==", "REVIEW_REJECTED").get()).size).toBe(1);
  await expectReason(approve(a1), "STATE_CHANGED");
  // Platform yöneticisi reddedilmiş yorumu moderasyonla yayınlayamaz.
  await expectReason(caller(moderateReview)({ reviewId: a1, action: "RESTORE" }, "admin", undefined, { admin: true }), "STATE_CHANGED");

  const a2 = await completed("c2", C2, 720);
  await review({ appointmentId: a2, salonRating: 4, staffRating: 5 }, "c2", C2);
  await approve(a2);
  const pub = await db.doc(`reviews/${a2}`).get();
  expect([pub.get("hidden"), pub.get("approval")]).toEqual([false, "APPROVED"]);
  const salon = await db.doc(`salons/${salonId}`).get();
  expect([salon.get("ratingSum"), salon.get("ratingCount")]).toEqual([4, 1]);
  expect((await db.collection("users/c2/notifications").where("type", "==", "REVIEW_APPROVED").get()).size).toBe(1);
  expect((await db.collection("users/staff/notifications").where("type", "==", "REVIEW_RECEIVED").get()).size).toBe(1);
});

test("yazar adı kısaltması", () => {
  expect(authorDisplay("Zeynep", "şahin")).toBe("Zeynep Ş.");
  expect(authorDisplay("Ali", "")).toBe("Ali");
});
