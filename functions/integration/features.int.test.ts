import { caller, clearFirestore, expectReason, fft, uuid } from "./helpers";
import { registerCustomer } from "../src/auth/session";
import { registerSalonOwner } from "../src/salon/registerSalonOwner";
import { acceptInvitation, createStaffInvitation, updateStaffMember } from "../src/staff/invitations";
import { createAppointment, createWalkInAppointment, getAppointmentContacts, transitionAppointment } from "../src/booking/appointments";
import { getAvailability } from "../src/booking/availabilityFn";
import { upsertDiscountCode, updateLoyaltyProgram, previewBookingPrice } from "../src/promo/promotions";
import { getReports } from "../src/finance/financeFunctions";
import { joinWaitlist, leaveWaitlist, onAppointmentFreed } from "../src/waitlist/waitlist";
import { replyToReview } from "../src/reviews/reply";
import { submitReview } from "../src/reviews/submitReview";
import { reopenSalon, requestSalonReview, reviewSalon, searchNearbySalons } from "../src/salon/platform";
import { closeSalon } from "../src/salon/lifecycle";
import { updateSalonProfile } from "../src/salon/updateSalon";
import { getCalendarFeed, collectEvents } from "../src/calendar/calendarFeed";
import { sendReminders } from "../src/scheduled/scan";
import { LEGAL_VERSIONS } from "../src/lib/validation";
import { bucket, db, Timestamp } from "../src/lib/admin";
import { addDays, instantAt, isoWeekday, localDateOf } from "../src/domain";

const consents = { kvkkVersion: LEGAL_VERSIONS.kvkk, termsVersion: LEGAL_VERSIONS.terms };
const book = caller(createAppointment);
const walkIn = caller(createWalkInAppointment);
const transition = caller(transitionAppointment);
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
const at = (hm: number, date = TUE) => instantAt(date, hm).toISOString();
const req = (o: Record<string, unknown> = {}) => ({ salonId, staffUid: "staff", serviceIds: ["kesim"], startAt: at(600), expectedTotalKurus: 40000, idempotencyKey: uuid(), ...o });
const notes = async (uid: string, type: string) => (await db.collection(`users/${uid}/notifications`).where("type", "==", type).get()).size;

/** Randevuyu geçmişe çekip tamamlar (uzman). */
async function complete(id: string) {
  await transition({ appointmentId: id, action: "CONFIRM" }, "staff", STAFF);
  await db.doc(`appointments/${id}`).update({ startAt: Timestamp.fromMillis(Date.now() - 60_000) });
  await transition({ appointmentId: id, action: "MARK_ARRIVED" }, "staff", STAFF);
  await transition({ appointmentId: id, action: "COMPLETE" }, "staff", STAFF);
}

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
  await db.doc(`salons/${salonId}/services/kesim`).set({ staffUid: "staff", salonId, name: "Kesim", nameLower: "kesim", description: "", priceKurus: 40000, durationMinutes: 30, active: true, deleted: false });
  await caller(registerCustomer)({ firstName: "Müşteri", lastName: "Birinci", consents, idempotencyKey: uuid() }, "c1", C1);
  await caller(registerCustomer)({ firstName: "Müşteri", lastName: "İkinci", consents, idempotencyKey: uuid() }, "c2", C2);
});

test("elden randevu: uzman/sahip açar, takvimi kilitler, müşteri bildirimi ve hesabı yoktur", async () => {
  const w = await walkIn({ salonId, staffUid: "staff", serviceIds: ["kesim"], startAt: at(600), customerName: "Hasan Demir", customerPhone: "0532 111 22 33", idempotencyKey: uuid() }, "staff", STAFF);
  const doc = await db.doc(`appointments/${w.appointmentId}`).get();
  expect(doc.get("status")).toBe("CONFIRMED");
  expect(doc.get("customerUid")).toBeNull();
  expect(doc.get("customerPhoneE164")).toBe("+905321112233");
  expect(doc.get("walkIn")).toBe(true);
  // Uygulamadan aynı saat alınamaz; mesai dışı saat de açılabilir (salon bilerek açar).
  await expectReason(book(req(), "c1", C1), "SLOT_TAKEN");
  await walkIn({ salonId, staffUid: "staff", serviceIds: ["kesim"], startAt: at(20 * 60), customerName: "Gece Müşterisi", idempotencyKey: uuid() }, "owner", OWNER);
  expect(await notes("staff", "APPOINTMENT_ASSIGNED")).toBe(1);
  // Yetkisiz kişi ve başka uzman adına uzman açamaz.
  await expectReason(walkIn({ salonId, staffUid: "staff", serviceIds: ["kesim"], startAt: at(720), customerName: "X Y", idempotencyKey: uuid() }, "c1", C1), "PERMISSION_DENIED");
  // Tamamlanabilir; ledger'a işlenir, müşteri istatistiği yazılmaz.
  await db.doc(`appointments/${w.appointmentId}`).update({ startAt: Timestamp.fromMillis(Date.now() - 60_000) });
  await transition({ appointmentId: w.appointmentId, action: "MARK_ARRIVED" }, "staff", STAFF);
  await transition({ appointmentId: w.appointmentId, action: "COMPLETE" }, "staff", STAFF);
  expect((await db.doc(`financialLedger/completion_${w.appointmentId}`).get()).get("amountKurus")).toBe(40000);
  expect((await db.collection(`salons/${salonId}/customers`).get()).size).toBe(0);
});

test("indirim kodu: önizleme, uygulama, kişi başı bir kez, iptalde iade", async () => {
  await expectReason(caller(upsertDiscountCode)({ salonId, code: "YAZ20", percent: 20 }, "staff", STAFF), "PERMISSION_DENIED");
  await caller(upsertDiscountCode)({ salonId, code: "yaz20", percent: 20, maxUses: 1 }, "owner", OWNER);
  const p = await caller(previewBookingPrice)({ salonId, staffUid: "staff", serviceIds: ["kesim"], discountCode: "YAZ20" }, "c1", C1);
  expect([p.discountKurus, p.finalKurus, p.codeError]).toEqual([8000, 32000, null]);
  const a = await book(req({ discountCode: "YAZ20" }), "c1", C1);
  expect(a.finalKurus).toBe(32000);
  expect((await db.doc(`salons/${salonId}/discountCodes/YAZ20`).get()).get("usedCount")).toBe(1);
  await expectReason(book(req({ startAt: at(720), discountCode: "YAZ20" }), "c2", C2), "CODE_EXHAUSTED");
  await expectReason(book(req({ startAt: at(720), discountCode: "YOKBOYLE" }), "c2", C2), "CODE_NOT_FOUND");
  // İptal → kullanım iade; aynı müşteri yeniden kullanabilir.
  await transition({ appointmentId: a.appointmentId, action: "CANCEL_BY_CUSTOMER" }, "c1", C1);
  expect((await db.doc(`salons/${salonId}/discountCodes/YAZ20`).get()).get("usedCount")).toBe(0);
  const b = await book(req({ startAt: at(720), discountCode: "YAZ20" }), "c1", C1);
  await complete(b.appointmentId);
  expect((await db.doc(`financialLedger/completion_${b.appointmentId}`).get()).get("amountKurus")).toBe(32000);
  // Tamamlanan randevuda kullanım tüketilmiş sayılır.
  expect((await db.doc(`salons/${salonId}/discountCodes/YAZ20`).get()).get("usedCount")).toBe(1);
  await caller(upsertDiscountCode)({ salonId, code: "YAZ20", percent: 20, maxUses: 10 }, "owner", OWNER);
  await expectReason(book(req({ startAt: at(840), discountCode: "YAZ20" }), "c1", C1), "CODE_ALREADY_USED");
});

test("sadakat: 3 ziyarette bir %50 ödül; kod ile birlikte kullanılamaz; gelmeyen sayılır", async () => {
  await caller(updateLoyaltyProgram)({ salonId, enabled: true, visitsRequired: 3, rewardPercent: 50 }, "owner", OWNER);
  for (const [i, hm] of [[0, 540], [1, 600], [2, 660]] as const) {
    const a = await book(req({ startAt: at(hm, addDays(TUE, i * 7)) }), "c1", C1);
    await complete(a.appointmentId);
  }
  const p = await caller(previewBookingPrice)({ salonId, staffUid: "staff", serviceIds: ["kesim"], useLoyaltyReward: true }, "c1", C1);
  expect(p.loyalty.rewardsAvailable).toBe(1);
  expect(p.finalKurus).toBe(20000);
  await expectReason(book(req({ useLoyaltyReward: true, discountCode: "X1234" }), "c1", C1), "DISCOUNT_CONFLICT");
  const r = await book(req({ useLoyaltyReward: true }), "c1", C1);
  expect(r.finalKurus).toBe(20000);
  await expectReason(book(req({ startAt: at(720), useLoyaltyReward: true }), "c1", C1), "LOYALTY_NOT_AVAILABLE");
  // Gelmedi → ödül iade edilir, gelmeme sayacı artar.
  await transition({ appointmentId: r.appointmentId, action: "CONFIRM" }, "staff", STAFF);
  await db.doc(`appointments/${r.appointmentId}`).update({ startAt: Timestamp.fromMillis(Date.now() - 60_000) });
  await transition({ appointmentId: r.appointmentId, action: "MARK_NO_SHOW" }, "staff", STAFF);
  const stats = await db.doc(`salons/${salonId}/customers/c1`).get();
  expect([stats.get("completedCount"), stats.get("noShowCount"), stats.get("rewardsUsed")]).toEqual([3, 1, 0]);
});

test("prim: sahip oranı belirler, tamamlamada ledger ve rapora işlenir; oran herkese açık kartta yok", async () => {
  await expectReason(caller(updateStaffMember)({ salonId, memberUid: "staff", firstName: "Can", lastName: "Er", specialty: "Berber", commissionPercent: 40 }, "staff", STAFF), "PERMISSION_DENIED");
  await caller(updateStaffMember)({ salonId, memberUid: "staff", firstName: "Can", lastName: "Er", specialty: "Berber", commissionPercent: 40 }, "owner", OWNER);
  expect((await db.doc(`salons/${salonId}/members/staff`).get()).get("commissionPercent")).toBeUndefined();
  const a = await book(req(), "c1", C1);
  await complete(a.appointmentId);
  expect((await db.doc(`financialLedger/completion_${a.appointmentId}`).get()).get("commissionKurus")).toBe(16000);
  const rep = await caller(getReports)({ salonId, periodType: "DAY", anchorDate: localDateOf(new Date()) }, "owner", OWNER);
  const line = rep.staff.find((s: { staffUid: string }) => s.staffUid === "staff");
  expect([line.commissionKurus, line.commissionPercent]).toEqual([16000, 40]);
  expect(rep.salon.commissionKurus).toBe(16000);
});

test("bekleme listesi: iptal olunca listedekilere bildirim; kendi randevusunu alınca listeden çıkar", async () => {
  const a = await book(req(), "c1", C1);
  const w = await caller(joinWaitlist)({ salonId, staffUid: "staff", localDate: TUE }, "c2", C2);
  const before = await db.doc(`appointments/${a.appointmentId}`).get();
  await transition({ appointmentId: a.appointmentId, action: "CANCEL_BY_CUSTOMER" }, "c1", C1);
  const after = await db.doc(`appointments/${a.appointmentId}`).get();
  const wrapped = fft.wrap(onAppointmentFreed);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  await (wrapped as any)({ data: fft.makeChange(before, after), params: { id: a.appointmentId } });
  expect(await notes("c2", "WAITLIST_SLOT_OPENED")).toBe(1);
  // Tekrar tetiklenirse 30 dk içinde ikinci bildirim gitmez.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  await (wrapped as any)({ data: fft.makeChange(before, after), params: { id: a.appointmentId } });
  expect(await notes("c2", "WAITLIST_SLOT_OPENED")).toBe(1);
  await book(req(), "c2", C2);
  expect((await db.doc(`waitlist/${w.id}`).get()).exists).toBe(false);
  await caller(joinWaitlist)({ salonId, staffUid: "staff", localDate: TUE }, "c1", C1);
  await expectReason(caller(leaveWaitlist)({ id: `staff_${TUE}_c1` }, "c2", C2), "PERMISSION_DENIED");
  await caller(leaveWaitlist)({ id: `staff_${TUE}_c1` }, "c1", C1);
});

test("iptal kuralları: müşteri son 2 saatte iptal edemez, iletişim bilgisi alır; uzman iptal edince sahip bilgilenir", async () => {
  const a = await book(req(), "c1", C1);
  await transition({ appointmentId: a.appointmentId, action: "CONFIRM" }, "staff", STAFF);
  await db.doc(`appointments/${a.appointmentId}`).update({ startAt: Timestamp.fromMillis(Date.now() + 60 * 60_000) });
  await expectReason(transition({ appointmentId: a.appointmentId, action: "CANCEL_BY_CUSTOMER" }, "c1", C1), "CANCEL_WINDOW_CLOSED");
  const c = await caller(getAppointmentContacts)({ appointmentId: a.appointmentId }, "c1", C1);
  expect(c.contacts.map((x: { role: string }) => x.role)).toEqual(["STAFF", "OWNER", "SALON"]);
  expect(c.contacts[0].phoneE164).toBe(STAFF);
  await expectReason(caller(getAppointmentContacts)({ appointmentId: a.appointmentId }, "c2", C2), "PERMISSION_DENIED");
  await transition({ appointmentId: a.appointmentId, action: "CANCEL_BY_STAFF", reason: "Acil durum" }, "staff", STAFF);
  expect(await notes("owner", "APPOINTMENT_CANCELLED")).toBe(1);
  expect(await notes("c1", "APPOINTMENT_CANCELLED")).toBe(1);
  // Müşteri iptalinde hem uzman hem sahip bilgilendirilir.
  const b = await book(req({ startAt: at(720) }), "c1", C1);
  await transition({ appointmentId: b.appointmentId, action: "CANCEL_BY_CUSTOMER" }, "c1", C1);
  expect(await notes("staff", "APPOINTMENT_CANCELLED")).toBe(1);
  expect(await notes("owner", "APPOINTMENT_CANCELLED")).toBe(2);
});

test("24 saat hatırlatması bir kez gider ve iptal kuralını hatırlatır", async () => {
  const a = await book(req(), "c1", C1);
  await transition({ appointmentId: a.appointmentId, action: "CONFIRM" }, "staff", STAFF);
  await db.doc(`appointments/${a.appointmentId}`).update({ startAt: Timestamp.fromMillis(Date.now() + 20 * 3600_000) });
  expect(await sendReminders(new Date())).toBe(1);
  expect(await sendReminders(new Date())).toBe(0);
  const n = await db.collection("users/c1/notifications").where("type", "==", "APPOINTMENT_REMINDER").get();
  expect(n.docs[0].get("body")).toContain("son 2 saatte");
});

test("yoruma salon sahibi yanıtı", async () => {
  const a = await book(req(), "c1", C1);
  await complete(a.appointmentId);
  await caller(submitReview)({ appointmentId: a.appointmentId, salonRating: 4, staffRating: 5, comment: "Güzeldi" }, "c1", C1);
  await expectReason(caller(replyToReview)({ reviewId: a.appointmentId, text: "Teşekkürler" }, "staff", STAFF), "PERMISSION_DENIED");
  await caller(replyToReview)({ reviewId: a.appointmentId, text: "Teşekkür ederiz, yine bekleriz!" }, "owner", OWNER);
  expect((await db.doc(`reviews/${a.appointmentId}`).get()).get("ownerReply.text")).toBe("Teşekkür ederiz, yine bekleriz!");
  expect(await notes("c1", "REVIEW_REPLIED")).toBe(1);
  await caller(replyToReview)({ reviewId: a.appointmentId, text: "" }, "owner", OWNER);
  expect((await db.doc(`reviews/${a.appointmentId}`).get()).get("ownerReply")).toBeUndefined();
});

test("salon onayı, konuma göre arama, kapatıp yeniden açma", async () => {
  const s = await db.doc(`salons/${salonId}`).get();
  expect([s.get("verified"), s.get("verification.status")]).toEqual([false, "PENDING"]);
  await caller(updateSalonProfile)({
    salonId, name: "Işıltı", businessPhone: "02125556677", type: "BEAUTY_SALON", city: "Ankara", district: "Çankaya",
    addressLine: "Kızılay Mah. 1", description: "", location: { lat: 39.9208, lng: 32.8541 },
  }, "owner", OWNER);
  const near = caller(searchNearbySalons);
  expect((await near({ lat: 39.925, lng: 32.86, radiusKm: 5 }, "c1", C1)).salons).toHaveLength(0); // onaysız
  await expectReason(caller(reviewSalon)({ salonId, action: "APPROVE" }, "c1", C1), "ADMIN_ONLY");
  await expectReason(caller(reviewSalon)({ salonId, action: "REJECT" }, "admin", undefined, { admin: true }), "REASON_REQUIRED");
  await caller(reviewSalon)({ salonId, action: "REJECT", note: "Adres eksik" }, "admin", undefined, { admin: true });
  await caller(requestSalonReview)({ salonId }, "owner", OWNER);
  await caller(reviewSalon)({ salonId, action: "APPROVE" }, "admin", undefined, { admin: true });
  expect(await notes("owner", "SALON_APPROVED")).toBe(1);
  const found = (await near({ lat: 39.925, lng: 32.86, radiusKm: 5 }, "c1", C1)).salons;
  expect(found.map((x: { salonId: string }) => x.salonId)).toEqual([salonId]);
  expect(found[0].distanceKm).toBeLessThan(1.5);
  expect((await near({ lat: 41.01, lng: 28.97, radiusKm: 50 }, "c1", C1)).salons).toHaveLength(0); // İstanbul

  await caller(closeSalon)({ salonId, confirm: "KAPAT", reason: "Tadilat" }, "owner", OWNER);
  await expectReason(caller(reopenSalon)({ salonId }, "staff", STAFF), "PERMISSION_DENIED");
  await caller(reopenSalon)({ salonId }, "owner", OWNER);
  expect((await db.doc(`salons/${salonId}`).get()).get("status")).toBe("ACTIVE");
  expect((await db.doc(`salons/${salonId}/members/owner`).get()).get("status")).toBe("ACTIVE");
  expect((await db.doc(`salons/${salonId}/members/staff`).get()).get("status")).toBe("LEFT");
  expect((await db.doc("salonOwners/owner").get()).exists).toBe(true);
  await expectReason(caller(reopenSalon)({ salonId }, "owner", OWNER), "STATE_CHANGED");
});

test("takvim aboneliği: kişiye özel adres, rol bazlı başlıklar, yenileme eski adresi geçersiz kılar", async () => {
  const a = await book(req(), "c1", C1);
  const feed = caller(getCalendarFeed);
  const f1 = await feed({}, "c1", C1);
  expect(f1.path).toBe(`/cal/${f1.token}.ics`);
  expect((await feed({}, "c1", C1)).token).toBe(f1.token);
  const asCustomer = await collectEvents("c1");
  expect(asCustomer[0].summary).toContain("Işıltı");
  const asStaff = await collectEvents("staff");
  expect(asStaff[0].summary).toContain("Müşteri Birinci");
  const asOwner = await collectEvents("owner");
  expect(asOwner[0].summary).toContain("Can Er");
  await transition({ appointmentId: a.appointmentId, action: "CANCEL_BY_CUSTOMER" }, "c1", C1);
  expect((await collectEvents("c1"))[0].cancelled).toBe(true);
  const f2 = await feed({ rotate: true }, "c1", C1);
  expect(f2.token).not.toBe(f1.token);
  expect((await db.doc(`calendarTokens/${f1.token}`).get()).exists).toBe(false);
});

test("müsaitlik başkası adına modunda müşterinin kendi meşguliyetini yok sayar", async () => {
  await book(req(), "c1", C1);
  const r = await caller(getAvailability)({ salonId, staffUid: "staff", serviceIds: ["kesim"], fromDate: TUE, days: 1 }, "c2", C2);
  expect(r.days[0].slots.map((s: { label: string }) => s.label)).not.toContain("10:00");
});
