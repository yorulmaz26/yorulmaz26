import { caller, clearFirestore, expectReason, fft, uuid } from "./helpers";
import { registerCustomer } from "../src/auth/session";
import { registerSalonOwner } from "../src/salon/registerSalonOwner";
import { acceptInvitation, createStaffInvitation } from "../src/staff/invitations";
import { getAvailability } from "../src/booking/availabilityFn";
import { createAppointment, transitionAppointment } from "../src/booking/appointments";
import { hideAppointments, reassignAppointment, rescheduleAppointment } from "../src/booking/manage";
import { expirePending, sendArrivalPrompts, sendReminders } from "../src/scheduled/scan";
import { pushOnNotification } from "../src/notify/push";
import { LEGAL_VERSIONS } from "../src/lib/validation";
import { bucket, db, Timestamp } from "../src/lib/admin";
import { addDays, instantAt, isoWeekday, localDateOf } from "../src/domain";

const consents = { kvkkVersion: LEGAL_VERSIONS.kvkk, termsVersion: LEGAL_VERSIONS.terms };
const book = caller(createAppointment);
const transition = caller(transitionAppointment);
const reassign = caller(reassignAppointment);
const reschedule = caller(rescheduleAppointment);
const hide = caller(hideAppointments);
const avail = caller(getAvailability);
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
const at = (m: number, date = TUE) => instantAt(date, m).toISOString();

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
});

const bookKesim = (start = 600) => book({ salonId, staffUid: "staff", serviceIds: ["kesim"], startAt: at(start), expectedTotalKurus: 40000, idempotencyKey: uuid() }, "c1", C1);
const lockOf = async (staff: string, date = TUE) => (await db.doc(`staffDayLocks/${salonId}_${staff}_${date}`).get()).get("intervals") ?? [];

test("sahip uzmanı değiştirir: kilit taşınır, snapshot korunur, taraflara bildirim", async () => {
  const a = await bookKesim();
  await expectReason(reassign({ appointmentId: a.appointmentId, newStaffUid: "owner" }, "staff", STAFF), "PERMISSION_DENIED");
  await reassign({ appointmentId: a.appointmentId, newStaffUid: "owner" }, "owner", OWNER);
  const doc = await db.doc(`appointments/${a.appointmentId}`).get();
  expect(doc.get("staffUid")).toBe("owner");
  expect(doc.get("staffName")).toBe("Ayşe Yılmaz");
  expect(doc.get("items")[0].priceKurus).toBe(40000);
  expect(await lockOf("staff")).toEqual([]);
  expect((await lockOf("owner")).map((i: { appointmentId: string }) => i.appointmentId)).toEqual([a.appointmentId]);
  expect((await db.collection("users/c1/notifications").where("type", "==", "APPOINTMENT_REASSIGNED").get()).size).toBe(1);
});

test("dolu veya çalışmayan uzmana atama reddedilir", async () => {
  const a = await bookKesim();
  await db.doc(`staffDayLocks/${salonId}_owner_${TUE}`).set({ intervals: [{ appointmentId: "x", startMin: 590, endMin: 620, expiresAtMs: null }] });
  await expectReason(reassign({ appointmentId: a.appointmentId, newStaffUid: "owner" }, "owner", OWNER), "SLOT_TAKEN");
  await db.doc(`salons/${salonId}/members/owner/schedule/weekly`).update({ weeklyDaysOff: [2, 7] });
  await expectReason(reassign({ appointmentId: a.appointmentId, newStaffUid: "owner" }, "owner", OWNER), "STAFF_NOT_AVAILABLE");
});

test("saat değiştirme: yeniden planlama müsaitliği kendini hariç tutar; tarih değişince kilit taşınır", async () => {
  const a = await bookKesim(600);
  await db.doc(`appointments/${a.appointmentId}`).update({ attention: { reason: "OUTSIDE_WORKING_HOURS" } });
  const av = await avail({ appointmentId: a.appointmentId, fromDate: TUE, days: 1 }, "staff", STAFF);
  expect(av.days[0].slots.map((s: { label: string }) => s.label)).toContain("10:00");
  await expectReason(reschedule({ appointmentId: a.appointmentId, newStartAt: at(600) }, "c1", C1), "PERMISSION_DENIED");
  await expectReason(reschedule({ appointmentId: a.appointmentId, newStartAt: at(1200) }, "staff", STAFF), "SLOT_UNAVAILABLE");
  const WED = addDays(TUE, 1);
  await reschedule({ appointmentId: a.appointmentId, newStartAt: at(660, WED) }, "staff", STAFF);
  const doc = await db.doc(`appointments/${a.appointmentId}`).get();
  expect(doc.get("localDate")).toBe(WED);
  expect(doc.get("startMinuteOfDay")).toBe(660);
  expect(doc.get("attention")).toBeUndefined();
  expect(await lockOf("staff", TUE)).toEqual([]);
  expect((await lockOf("staff", WED))[0].startMin).toBe(660);
  expect((await db.collection("users/c1/notifications").where("type", "==", "APPOINTMENT_RESCHEDULED").get()).size).toBe(1);
});

test("zamanlanmış işler: süre aşımı, hatırlatma ve 'Müşteri geldi mi?' yalnızca bir kez", async () => {
  const a = await bookKesim(600);
  await db.doc(`appointments/${a.appointmentId}`).update({ pendingExpiresAt: Timestamp.fromMillis(Date.now() - 1000) });
  expect(await expirePending(new Date())).toBe(1);
  expect(await expirePending(new Date())).toBe(0);
  expect((await db.doc(`appointments/${a.appointmentId}`).get()).get("status")).toBe("EXPIRED");
  expect(await lockOf("staff")).toEqual([]);
  expect((await db.doc("customerLocks/c1").get()).get("activeAppointmentId")).toBeNull();
  expect((await db.collection("users/c1/notifications").where("type", "==", "APPOINTMENT_EXPIRED").get()).size).toBe(1);

  const b = await bookKesim(720);
  await transition({ appointmentId: b.appointmentId, action: "CONFIRM" }, "staff", STAFF);
  await db.doc(`appointments/${b.appointmentId}`).update({ startAt: Timestamp.fromMillis(Date.now() + 90 * 60_000) });
  expect(await sendReminders(new Date())).toBe(1);
  expect(await sendReminders(new Date())).toBe(0);
  await db.doc(`appointments/${b.appointmentId}`).update({ startAt: Timestamp.fromMillis(Date.now() - 60_000) });
  expect(await sendArrivalPrompts(new Date())).toBe(1);
  expect(await sendArrivalPrompts(new Date())).toBe(0);
  expect((await db.collection("users/staff/notifications").where("type", "==", "ARRIVAL_PROMPT").get()).size).toBe(1);
  // İptal edilmiş randevuya hatırlatma gitmez.
  await db.doc(`appointments/${b.appointmentId}`).update({ status: "CANCELLED_BY_STAFF", "flags.reminder2hSentAt": null, startAt: Timestamp.fromMillis(Date.now() + 60 * 60_000) });
  expect(await sendReminders(new Date())).toBe(0);
});

test("gizleme yalnızca terminal randevular ve yalnızca çağıranın görünümü", async () => {
  const a = await bookKesim();
  await expectReason(hide({ appointmentIds: [a.appointmentId] }, "c1", C1), "CANNOT_HIDE_ACTIVE");
  await transition({ appointmentId: a.appointmentId, action: "CANCEL_BY_CUSTOMER" }, "c1", C1);
  await expectReason(hide({ appointmentIds: [a.appointmentId] }, "stranger", "+905009998877"), "PERMISSION_DENIED");
  expect((await hide({ appointmentIds: [a.appointmentId] }, "c1", C1)).hidden).toBe(1);
  const doc = await db.doc(`appointments/${a.appointmentId}`).get();
  expect(doc.get("hiddenFor")).toEqual(["c1"]);
  expect(doc.get("status")).toBe("CANCELLED_BY_CUSTOMER"); // kayıt silinmez
});

test("bildirim gönderimi: cihaz yoksa işaretlenir; tekrar tetiklenirse ikinci kez işlenmez", async () => {
  const ref = db.collection("users/c1/notifications").doc("n1");
  await ref.set({ type: "X", title: "t", body: "b", read: false });
  const wrapped = fft.wrap(pushOnNotification);
  const snap = await ref.get();
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  await (wrapped as any)({ data: snap, params: { uid: "c1", notificationId: "n1" } });
  expect((await ref.get()).get("push.result")).toBe("NO_DEVICE");
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  await (wrapped as any)({ data: snap, params: { uid: "c1", notificationId: "n1" } });
  expect((await ref.get()).get("push.result")).toBe("NO_DEVICE");
});
