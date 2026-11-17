import { caller, clearFirestore, expectReason, uuid } from "./helpers";
import { registerCustomer } from "../src/auth/session";
import { registerSalonOwner } from "../src/salon/registerSalonOwner";
import { acceptInvitation, createStaffInvitation } from "../src/staff/invitations";
import { createAppointment, transitionAppointment } from "../src/booking/appointments";
import { getAvailability } from "../src/booking/availabilityFn";
import { submitReview } from "../src/reviews/submitReview";
import { moderateReview, reportReview } from "../src/reviews/moderation";
import { closeSalon, transferSalonOwnership } from "../src/salon/lifecycle";
import { syncPhoneNumber, updateMyName } from "../src/account/profile";
import { deleteAccount } from "../src/account/deleteAccount";
import { LEGAL_VERSIONS } from "../src/lib/validation";
import { bucket, db, Timestamp } from "../src/lib/admin";
import { addDays, instantAt, isoWeekday, localDateOf } from "../src/domain";

const consents = { kvkkVersion: LEGAL_VERSIONS.kvkk, termsVersion: LEGAL_VERSIONS.terms };
const book = caller(createAppointment);
const transition = caller(transitionAppointment);
const OWNER = "+905301110000";
const OWNER2 = "+905301110002";
const STAFF = "+905551112233";
const C1 = "+905320000001";
const C2 = "+905320000002";
let salonA = "";
let salonB = "";

function nextWeekday(target: number): string {
  let d = addDays(localDateOf(new Date()), 2);
  while (isoWeekday(d) !== target) d = addDays(d, 1);
  return d;
}
const TUE = nextWeekday(2);

async function salon(owner: string, phone: string, name: string) {
  const path = `uploads/${owner}/pending/logo.jpg`;
  await bucket().file(path).save(Buffer.alloc(1000, 1), { contentType: "image/jpeg" });
  return (await caller(registerSalonOwner)({
    firstName: "Ayşe", lastName: "Yılmaz", consents, logoPath: path, photoPaths: [], idempotencyKey: uuid(),
    salon: { name, businessPhone: "02125556677", type: "BEAUTY_SALON", city: "Ankara", district: "Çankaya", addressLine: "Kızılay Mah. 1" },
  }, owner, phone)).salonId as string;
}
async function join(salonId: string, ownerUid: string, ownerPhone: string) {
  const inv = await caller(createStaffInvitation)({ salonId, firstName: "Can", lastName: "Er", specialty: "Berber", phone: STAFF, idempotencyKey: uuid() }, ownerUid, ownerPhone);
  await caller(acceptInvitation)({ salonId, inviteId: inv.inviteId, consents }, "staff", STAFF);
  await db.doc(`salons/${salonId}/services/kesim`).set({ staffUid: "staff", salonId, name: "Kesim", nameLower: "kesim", priceKurus: 40000, durationMinutes: 30, active: true, deleted: false });
}
const bookAt = (salonId: string, uid: string, phone: string, min: number) =>
  book({ salonId, staffUid: "staff", serviceIds: ["kesim"], startAt: instantAt(TUE, min).toISOString(), expectedTotalKurus: 40000, idempotencyKey: uuid() }, uid, phone);

beforeEach(async () => {
  await clearFirestore();
  salonA = await salon("owner", OWNER, "Işıltı");
  await join(salonA, "owner", OWNER);
  await caller(registerCustomer)({ firstName: "Ali", lastName: "Kaya", consents, idempotencyKey: uuid() }, "c1", C1);
  await caller(registerCustomer)({ firstName: "Zeynep", lastName: "Şahin", consents, idempotencyKey: uuid() }, "c2", C2);
});

async function completedReview(uid: string, phone: string, min: number, salonRating: number, staffRating: number) {
  const a = await bookAt(salonA, uid, phone, min);
  await transition({ appointmentId: a.appointmentId, action: "CONFIRM" }, "staff", STAFF);
  await db.doc(`appointments/${a.appointmentId}`).update({ startAt: Timestamp.fromMillis(Date.now() - 60_000) });
  await transition({ appointmentId: a.appointmentId, action: "MARK_ARRIVED" }, "staff", STAFF);
  await transition({ appointmentId: a.appointmentId, action: "COMPLETE" }, "staff", STAFF);
  await caller(submitReview)({ appointmentId: a.appointmentId, salonRating, staffRating }, uid, phone);
  return a.appointmentId as string;
}

test("aynı uzman iki salonda çalışsa da aynı saate iki randevu alamaz", async () => {
  salonB = await salon("owner2", OWNER2, "Parıltı");
  await join(salonB, "owner2", OWNER2);
  await bookAt(salonA, "c1", C1, 600);
  await expectReason(bookAt(salonB, "c2", C2, 600), "SLOT_TAKEN");
  const av = await caller(getAvailability)({ salonId: salonB, staffUid: "staff", serviceIds: ["kesim"], fromDate: TUE, days: 1 }, "c2", C2);
  const labels = av.days[0].slots.map((s: { label: string }) => s.label);
  expect(labels).not.toContain("10:00");
  expect(labels).toContain("10:30");
  await bookAt(salonB, "c2", C2, 630);
});

describe("yorum şikâyeti ve moderasyon", () => {
  test("şikâyet eşiği, tekil şikâyet, yalnızca yönetici gizler; gizleme ortalamadan çıkarır, geri alma ekler", async () => {
    const r1 = await completedReview("c1", C1, 600, 1, 2);
    await completedReview("c2", C2, 720, 5, 4);
    expect((await db.doc(`salons/${salonA}`).get()).get("ratingAvg")).toBe(3);

    expect(await caller(reportReview)({ reviewId: r1, reason: "OFFENSIVE" }, "x1", "+905000000001")).toMatchObject({ reportCount: 1, flagged: false });
    await expectReason(caller(reportReview)({ reviewId: r1, reason: "SPAM" }, "x1", "+905000000001"), "ALREADY_REPORTED");
    await caller(reportReview)({ reviewId: r1, reason: "SPAM" }, "x2", "+905000000002");
    expect(await caller(reportReview)({ reviewId: r1, reason: "OTHER" }, "x3", "+905000000003")).toMatchObject({ reportCount: 3, flagged: true });

    await expectReason(caller(moderateReview)({ reviewId: r1, action: "HIDE" }, "owner", OWNER), "PERMISSION_DENIED");
    await caller(moderateReview)({ reviewId: r1, action: "HIDE", note: "Hakaret" }, "admin", undefined, { admin: true });
    const s1 = await db.doc(`salons/${salonA}`).get();
    expect([s1.get("ratingSum"), s1.get("ratingCount"), s1.get("ratingAvg")]).toEqual([5, 1, 5]);
    expect((await db.doc(`salons/${salonA}/members/staff`).get()).get("ratingAvg")).toBe(4);
    expect((await db.doc(`reviews/${r1}`).get()).get("hidden")).toBe(true);
    await expectReason(caller(reportReview)({ reviewId: r1, reason: "SPAM" }, "x4", "+905000000004"), "REVIEW_NOT_FOUND");

    await caller(moderateReview)({ reviewId: r1, action: "RESTORE" }, "admin", undefined, { admin: true });
    expect((await db.doc(`salons/${salonA}`).get()).get("ratingAvg")).toBe(3);
  });

  test("kişisel veri şikâyeti tek başına kuyruğa alır; DISMISS bayrağı kaldırır", async () => {
    const r1 = await completedReview("c1", C1, 600, 4, 4);
    expect(await caller(reportReview)({ reviewId: r1, reason: "PERSONAL_DATA" }, "x1", "+905000000001")).toMatchObject({ flagged: true });
    await caller(moderateReview)({ reviewId: r1, action: "DISMISS" }, "admin", undefined, { admin: true });
    const r = await db.doc(`reviews/${r1}`).get();
    expect([r.get("flagged"), r.get("hidden")]).toEqual([false, false]);
  });
});

test("salonu kapatma: işlemdeki randevu engeller; gelecek randevular iptal, üyelikler biter, sahip hesabını silebilir", async () => {
  const a = await bookAt(salonA, "c1", C1, 600);
  await transition({ appointmentId: a.appointmentId, action: "CONFIRM" }, "staff", STAFF);
  await db.doc(`appointments/${a.appointmentId}`).update({ startAt: Timestamp.fromMillis(Date.now() - 60_000) });
  await transition({ appointmentId: a.appointmentId, action: "MARK_ARRIVED" }, "staff", STAFF);
  await expectReason(caller(closeSalon)({ salonId: salonA, confirm: "KAPAT", reason: "Taşınıyoruz" }, "owner", OWNER), "IN_PROGRESS_APPOINTMENTS");
  await transition({ appointmentId: a.appointmentId, action: "COMPLETE" }, "staff", STAFF);
  const b = await bookAt(salonA, "c2", C2, 720);

  await expectReason(caller(closeSalon)({ salonId: salonA, confirm: "KAPAT", reason: "Taşınıyoruz" }, "staff", STAFF), "PERMISSION_DENIED");
  await expectReason(caller(closeSalon)({ salonId: salonA, confirm: "evet", reason: "Taşınıyoruz" }, "owner", OWNER), "CONFIRMATION_REQUIRED");
  const r = await caller(closeSalon)({ salonId: salonA, confirm: "KAPAT", reason: "Taşınıyoruz" }, "owner", OWNER);
  expect(r.cancelledAppointments).toBe(1);
  expect((await db.doc(`appointments/${b.appointmentId}`).get()).get("status")).toBe("CANCELLED_BY_STAFF");
  expect((await db.doc(`staffDayLocks/staff_${TUE}`).get()).get("intervals")).toEqual([]);
  expect((await db.doc(`salons/${salonA}`).get()).get("status")).toBe("CLOSED");
  expect((await db.doc(`salons/${salonA}/members/staff`).get()).get("status")).toBe("LEFT");
  expect((await db.doc(`users/staff/memberships/${salonA}`).get()).get("status")).toBe("LEFT");
  expect((await db.doc(`financialLedger/completion_${a.appointmentId}`).get()).exists).toBe(true);
  await caller(deleteAccount)({ confirm: "SIL" }, "owner", OWNER);
});

test("sahiplik devri: roller ve sahiplik kilitleri yer değiştirir", async () => {
  await expectReason(caller(transferSalonOwnership)({ salonId: salonA, newOwnerUid: "c1", confirm: "DEVRET" }, "owner", OWNER), "NEW_OWNER_NOT_ACTIVE_STAFF");
  await expectReason(caller(transferSalonOwnership)({ salonId: salonA, newOwnerUid: "staff", confirm: "DEVRET" }, "staff", STAFF), "PERMISSION_DENIED");
  await caller(transferSalonOwnership)({ salonId: salonA, newOwnerUid: "staff", confirm: "DEVRET" }, "owner", OWNER);
  const s = await db.doc(`salons/${salonA}`).get();
  expect([s.get("ownerUid"), s.get("ownerDisplayName")]).toEqual(["staff", "Can Er"]);
  expect((await db.doc(`salons/${salonA}/members/owner`).get()).get("role")).toBe("STAFF");
  expect((await db.doc(`users/staff/memberships/${salonA}`).get()).get("role")).toBe("OWNER");
  expect((await db.doc("salonOwners/staff").get()).exists).toBe(true);
  expect((await db.doc("salonOwners/owner").get()).exists).toBe(false);
});

test("ad ve telefon değişikliği aktif randevulara yansır; telefon yalnızca doğrulanmış token'dan", async () => {
  const a = await bookAt(salonA, "c1", C1, 600);
  await caller(updateMyName)({ firstName: "Mehmet", lastName: "Kaya" }, "c1", C1);
  expect((await db.doc(`appointments/${a.appointmentId}`).get()).get("customerName")).toBe("Mehmet Kaya");
  await caller(updateMyName)({ firstName: "Cem", lastName: "Er" }, "staff", STAFF);
  expect((await db.doc(`salons/${salonA}/members/staff`).get()).get("firstName")).toBe("Cem");
  expect((await db.doc(`appointments/${a.appointmentId}`).get()).get("staffName")).toBe("Cem Er");

  const NEW = "+905329999999";
  expect((await caller(syncPhoneNumber)({}, "c1", NEW)).phoneE164).toBe(NEW);
  expect((await db.doc("users/c1").get()).get("phoneE164")).toBe(NEW);
  expect((await db.doc(`appointments/${a.appointmentId}`).get()).get("customerPhoneE164")).toBe(NEW);
  await expectReason(caller(syncPhoneNumber)({}, "c1"), "PHONE_NOT_VERIFIED");
});
