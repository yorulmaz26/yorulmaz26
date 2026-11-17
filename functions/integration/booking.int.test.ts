import { caller, clearFirestore, expectReason, uuid } from "./helpers";
import { registerCustomer } from "../src/auth/session";
import { registerSalonOwner } from "../src/salon/registerSalonOwner";
import { acceptInvitation, createStaffInvitation } from "../src/staff/invitations";
import { getAvailability } from "../src/booking/availabilityFn";
import { createAppointment, transitionAppointment } from "../src/booking/appointments";
import { LEGAL_VERSIONS } from "../src/lib/validation";
import { bucket, db, Timestamp } from "../src/lib/admin";
import { addDays, instantAt, isoWeekday, localDateOf } from "../src/domain";

const consents = { kvkkVersion: LEGAL_VERSIONS.kvkk, termsVersion: LEGAL_VERSIONS.terms };
const avail = caller(getAvailability);
const book = caller(createAppointment);
const transition = caller(transitionAppointment);
const OWNER_PHONE = "+905301110000";
const STAFF_PHONE = "+905551112233";
let salonId = "";

function nextWeekday(target: number): string {
  let d = addDays(localDateOf(new Date()), 2);
  while (isoWeekday(d) !== target) d = addDays(d, 1);
  return d;
}
const TUESDAY = nextWeekday(2);
const at = (hm: number) => instantAt(TUESDAY, hm).toISOString();

async function customer(uid: string, phone: string) {
  await caller(registerCustomer)({ firstName: "Müşteri", lastName: uid === "c1" ? "Birinci" : "İkinci", consents, idempotencyKey: uuid() }, uid, phone);
}
async function service(id: string, staffUid: string, price: number, minutes: number, extra: Record<string, unknown> = {}) {
  await db.doc(`salons/${salonId}/services/${id}`).set({ staffUid, salonId, name: id, nameLower: id, description: "", priceKurus: price, durationMinutes: minutes, active: true, deleted: false, ...extra });
}
const req = (o: Record<string, unknown> = {}) => ({
  salonId, staffUid: "staff", serviceIds: ["kesim", "fon"], startAt: at(600), expectedTotalKurus: 55000, idempotencyKey: uuid(), ...o,
});

beforeEach(async () => {
  await clearFirestore();
  const path = "uploads/owner/pending/logo.jpg";
  await bucket().file(path).save(Buffer.alloc(1000, 1), { contentType: "image/jpeg" });
  salonId = (await caller(registerSalonOwner)(
    {
      firstName: "Ayşe", lastName: "Yılmaz", consents, logoPath: path, photoPaths: [], idempotencyKey: uuid(),
      salon: { name: "Işıltı", businessPhone: "02125556677", type: "BEAUTY_SALON", city: "Ankara", district: "Çankaya", addressLine: "Kızılay Mah. 1" },
    }, "owner", OWNER_PHONE,
  )).salonId;
  const inv = await caller(createStaffInvitation)({ salonId, firstName: "Can", lastName: "Er", specialty: "Berber", phone: STAFF_PHONE, idempotencyKey: uuid() }, "owner", OWNER_PHONE);
  await caller(acceptInvitation)({ salonId, inviteId: inv.inviteId, consents }, "staff", STAFF_PHONE);
  await service("kesim", "staff", 40000, 30);
  await service("fon", "staff", 15000, 15);
  await service("boya", "owner", 90000, 60);
  await customer("c1", "+905320000001");
  await customer("c2", "+905320000002");
});

test("müsaitlik: toplam süre ve fiyat sunucuda hesaplanır, slotlar programa uyar", async () => {
  const r = await avail({ salonId, staffUid: "staff", serviceIds: ["kesim", "fon"], fromDate: TUESDAY, days: 1 }, "c1", "+905320000001");
  expect(r.totalKurus).toBe(55000);
  expect(r.totalMinutes).toBe(45);
  const labels = r.days[0].slots.map((s: { label: string }) => s.label);
  expect(labels[0]).toBe("09:00");
  expect(labels[labels.length - 1]).toBe("18:15"); // 18:15 + 45 dk = 19:00
  await expectReason(avail({ salonId, staffUid: "staff", serviceIds: ["boya"], fromDate: TUESDAY }, "c1", "+905320000001"), "SERVICE_STAFF_MISMATCH");
});

test("aynı slota eşzamanlı iki istekten yalnızca biri başarılı", async () => {
  const results = await Promise.allSettled([
    book(req(), "c1", "+905320000001"),
    book(req(), "c2", "+905320000002"),
  ]);
  expect(results.filter((r) => r.status === "fulfilled")).toHaveLength(1);
  const failed = results.find((r) => r.status === "rejected") as PromiseRejectedResult;
  expect(["SLOT_TAKEN", "SLOT_UNAVAILABLE"]).toContain(failed.reason.details.reason);
  expect((await db.collection("appointments").get()).size).toBe(1);
});

test("kısmi çakışma da reddedilir; farklı uzman aynı saatte alınabilir", async () => {
  await book(req(), "c1", "+905320000001"); // 10:00–10:45
  await expectReason(book(req({ serviceIds: ["kesim"], expectedTotalKurus: 40000, startAt: at(630) }), "c2", "+905320000002"), "SLOT_TAKEN");
  const ok = await book(req({ staffUid: "owner", serviceIds: ["boya"], expectedTotalKurus: 90000 }), "c2", "+905320000002");
  expect(ok.status).toBe("PENDING");
  const after = await avail({ salonId, staffUid: "staff", serviceIds: ["kesim"], fromDate: TUESDAY, days: 1 }, "c2", "+905320000002");
  const labels = after.days[0].slots.map((s: { label: string }) => s.label);
  expect(labels).not.toContain("10:00");
  expect(labels).not.toContain("10:15"); // 10:15–10:45 dolu aralıkla kesişir
  expect(labels).toContain("10:45");
});

test("müşteri başına tek aktif randevu (eşzamanlı iki farklı slot dahil)", async () => {
  const results = await Promise.allSettled([
    book(req({ startAt: at(600) }), "c1", "+905320000001"),
    book(req({ startAt: at(720) }), "c1", "+905320000001"),
  ]);
  expect(results.filter((r) => r.status === "fulfilled")).toHaveLength(1);
  await expectReason(book(req({ startAt: at(840) }), "c1", "+905320000001"), "ACTIVE_APPOINTMENT_EXISTS");
});

test("fiyat değişmişse yeniden onay istenir; pasif hizmet ve kendi kendine randevu reddedilir", async () => {
  await db.doc(`salons/${salonId}/services/kesim`).update({ priceKurus: 45000 });
  await expectReason(book(req(), "c1", "+905320000001"), "PRICE_CHANGED");
  await book(req({ expectedTotalKurus: 60000 }), "c1", "+905320000001");
  await db.doc(`salons/${salonId}/services/fon`).update({ active: false });
  await expectReason(book(req({ expectedTotalKurus: 60000, startAt: at(780) }), "c2", "+905320000002"), "SERVICE_INACTIVE");
  await expectReason(book(req({ staffUid: "staff" }), "staff", STAFF_PHONE), "CANNOT_BOOK_SELF");
});

test("ızgara dışı, kapalı gün ve mesai dışı saat reddedilir", async () => {
  await expectReason(book(req({ startAt: at(607) }), "c1", "+905320000001"), "SLOT_UNAVAILABLE");
  await expectReason(book(req({ startAt: at(18 * 60 + 30) }), "c1", "+905320000001"), "SLOT_UNAVAILABLE");
  await db.doc(`salons/${salonId}`).update({ weeklyClosedDays: [2] });
  await expectReason(book(req(), "c1", "+905320000001"), "SLOT_UNAVAILABLE");
});

test("aynı idempotency anahtarı ikinci randevu oluşturmaz", async () => {
  const r = req();
  const a = await book(r, "c1", "+905320000001");
  const b = await book(r, "c1", "+905320000001");
  expect(b.appointmentId).toBe(a.appointmentId);
  expect(a.number).toMatch(/^KC-[A-Z0-9]{8}$/);
});

test("süresi dolan PENDING slotu serbest bırakır ve müşteri yeniden randevu alabilir", async () => {
  const a = await book(req(), "c1", "+905320000001");
  const past = Timestamp.fromMillis(Date.now() - 1000);
  await db.doc(`appointments/${a.appointmentId}`).update({ pendingExpiresAt: past });
  const lockId = `staff_${TUESDAY}`;
  const lock = await db.doc(`staffDayLocks/${lockId}`).get();
  await lock.ref.update({ intervals: lock.get("intervals").map((i: object) => ({ ...i, expiresAtMs: past.toMillis() })) });

  // Başka müşteri aynı slotu alabilir; ilk müşteri de yeni randevu alabilir.
  await book(req(), "c2", "+905320000002");
  await book(req({ startAt: at(720) }), "c1", "+905320000001");
  expect((await db.doc(`appointments/${a.appointmentId}`).get()).get("status")).toBe("EXPIRED");
  // Geç onay reddedilir (durum EXPIRED).
  await expectReason(transition({ appointmentId: a.appointmentId, action: "CONFIRM" }, "staff", STAFF_PHONE), "STATE_CHANGED");
});

test("onay, iptal sonrası slot açılır; yetkiler ve 2 saat kuralı", async () => {
  const a = await book(req(), "c1", "+905320000001");
  await expectReason(transition({ appointmentId: a.appointmentId, action: "CONFIRM" }, "c2", "+905320000002"), "PERMISSION_DENIED");
  await expectReason(transition({ appointmentId: a.appointmentId, action: "CANCEL_BY_CUSTOMER" }, "c2", "+905320000002"), "PERMISSION_DENIED");
  expect((await transition({ appointmentId: a.appointmentId, action: "CONFIRM" }, "staff", STAFF_PHONE)).status).toBe("CONFIRMED");
  const lock = await db.doc(`staffDayLocks/staff_${TUESDAY}`).get();
  expect(lock.get("intervals")[0].expiresAtMs).toBeNull();
  expect((await db.collection("users/c1/notifications").where("type", "==", "APPOINTMENT_CONFIRMED").get()).size).toBe(1);

  expect((await transition({ appointmentId: a.appointmentId, action: "CANCEL_BY_CUSTOMER", reason: "Planım değişti" }, "c1", "+905320000001")).status).toBe("CANCELLED_BY_CUSTOMER");
  expect((await db.doc(`staffDayLocks/staff_${TUESDAY}`).get()).get("intervals")).toEqual([]);
  // Slot ve müşteri kilidi serbest: aynı müşteri aynı slotu tekrar alabilir.
  await book(req(), "c1", "+905320000001");

  // 2 saatten az kala müşteri iptal edemez; salon gerekçeyle iptal edebilir.
  const soon = await db.collection("appointments").where("status", "==", "PENDING").get();
  const id = soon.docs[0].id;
  await db.doc(`appointments/${id}`).update({ status: "CONFIRMED", startAt: Timestamp.fromMillis(Date.now() + 90 * 60_000) });
  await expectReason(transition({ appointmentId: id, action: "CANCEL_BY_CUSTOMER" }, "c1", "+905320000001"), "CANCEL_WINDOW_CLOSED");
  await expectReason(transition({ appointmentId: id, action: "CANCEL_BY_STAFF" }, "owner", OWNER_PHONE), "REASON_REQUIRED");
  expect((await transition({ appointmentId: id, action: "CANCEL_BY_STAFF", reason: "Uzman rahatsızlandı" }, "owner", OWNER_PHONE)).status).toBe("CANCELLED_BY_STAFF");
});
