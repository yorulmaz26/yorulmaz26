import { caller, clearFirestore, expectReason, uuid } from "./helpers";
import { registerCustomer } from "../src/auth/session";
import { registerSalonOwner } from "../src/salon/registerSalonOwner";
import { acceptInvitation, createStaffInvitation } from "../src/staff/invitations";
import { createAppointment, transitionAppointment } from "../src/booking/appointments";
import { adjustAppointmentPrice, getReports, rebuildReportAggregates } from "../src/finance/financeFunctions";
import { LEGAL_VERSIONS } from "../src/lib/validation";
import { bucket, db, Timestamp } from "../src/lib/admin";
import { addDays, instantAt, isoWeekday, localDateOf, periodKeysOf } from "../src/domain";

const consents = { kvkkVersion: LEGAL_VERSIONS.kvkk, termsVersion: LEGAL_VERSIONS.terms };
const book = caller(createAppointment);
const transition = caller(transitionAppointment);
const adjust = caller(adjustAppointmentPrice);
const reports = caller(getReports);
const rebuild = caller(rebuildReportAggregates);
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
  await caller(registerCustomer)({ firstName: "Ali", lastName: "Kaya", consents, idempotencyKey: uuid() }, "c1", C1);
});

/** Randevuyu oluşturup "Geldi" durumuna getirir (başlangıç zamanı geçmişe çekilir). */
async function inProgress(start = 600): Promise<string> {
  const a = await book({ salonId, staffUid: "staff", serviceIds: ["kesim"], startAt: instantAt(TUE, start).toISOString(), expectedTotalKurus: 40000, idempotencyKey: uuid() }, "c1", C1);
  await transition({ appointmentId: a.appointmentId, action: "CONFIRM" }, "staff", STAFF);
  await db.doc(`appointments/${a.appointmentId}`).update({ startAt: Timestamp.fromMillis(Date.now() - 60_000) });
  await transition({ appointmentId: a.appointmentId, action: "MARK_ARRIVED" }, "staff", STAFF);
  return a.appointmentId;
}

const today = () => localDateOf(new Date());
const agg = async (scope: string, key: string) => (await db.doc(`reportAggregates/${salonId}_${scope}_${key}`).get()).data();

test("tamamlama tek ledger kaydı ve 6 sayaç yazar; ikinci tamamlama çift sayım yapmaz", async () => {
  const id = await inProgress();
  expect((await transition({ appointmentId: id, action: "COMPLETE" }, "staff", STAFF)).status).toBe("COMPLETED");
  await expectReason(transition({ appointmentId: id, action: "COMPLETE" }, "staff", STAFF), "STATE_CHANGED");
  await expectReason(transition({ appointmentId: id, action: "COMPLETE" }, "owner", OWNER), "STATE_CHANGED");
  const ledger = await db.collection("financialLedger").where("appointmentId", "==", id).get();
  expect(ledger.size).toBe(1);
  expect(ledger.docs[0].id).toBe(`completion_${id}`);
  expect(ledger.docs[0].get("amountKurus")).toBe(40000);
  const keys = periodKeysOf(new Date());
  for (const k of [keys.day, keys.week, keys.month]) {
    expect(await agg("ALL", k)).toMatchObject({ completedCount: 1, amountKurus: 40000 });
    expect(await agg("staff", k)).toMatchObject({ completedCount: 1, amountKurus: 40000 });
  }
  expect((await db.collection("users/c1/notifications").where("type", "==", "REVIEW_INVITE").get()).size).toBe(1);
});

test("eşzamanlı iki 'tamamla' isteğinden yalnızca biri sayılır", async () => {
  const id = await inProgress();
  const r = await Promise.allSettled([
    transition({ appointmentId: id, action: "COMPLETE" }, "staff", STAFF),
    transition({ appointmentId: id, action: "COMPLETE" }, "owner", OWNER),
  ]);
  expect(r.filter((x) => x.status === "fulfilled")).toHaveLength(1);
  expect((await agg("ALL", periodKeysOf(new Date()).day))?.completedCount).toBe(1);
});

test("tamamlanmamış randevu finansa yazılmaz; Gelmedi gelir oluşturmaz", async () => {
  const a = await book({ salonId, staffUid: "staff", serviceIds: ["kesim"], startAt: instantAt(TUE, 720).toISOString(), expectedTotalKurus: 40000, idempotencyKey: uuid() }, "c1", C1);
  await expectReason(transition({ appointmentId: a.appointmentId, action: "COMPLETE" }, "staff", STAFF), "STATE_CHANGED");
  await transition({ appointmentId: a.appointmentId, action: "CONFIRM" }, "staff", STAFF);
  await db.doc(`appointments/${a.appointmentId}`).update({ startAt: Timestamp.fromMillis(Date.now() - 60_000) });
  await transition({ appointmentId: a.appointmentId, action: "MARK_NO_SHOW" }, "staff", STAFF);
  expect((await db.collection("financialLedger").get()).size).toBe(0);
  expect((await db.collection("reportAggregates").get()).size).toBe(0);
});

test("işlem sırasında fiyat düzeltme tamamlanan tutara yansır; liste tutarı ayrı tutulur", async () => {
  const id = await inProgress();
  await expectReason(adjust({ appointmentId: id, newFinalKurus: 45000, reason: "" }, "staff", STAFF), "INVALID_ARGUMENT");
  await expectReason(adjust({ appointmentId: id, newFinalKurus: 45000, reason: "Ek bakım" }, "c1", C1), "PERMISSION_DENIED");
  await adjust({ appointmentId: id, newFinalKurus: 45000, reason: "Ek bakım uygulandı" }, "staff", STAFF);
  await transition({ appointmentId: id, action: "COMPLETE" }, "staff", STAFF);
  expect(await agg("ALL", periodKeysOf(new Date()).month)).toMatchObject({ completedCount: 1, amountKurus: 45000, listedKurus: 40000 });
  expect((await db.collection("auditLogs").where("action", "==", "PRICE_ADJUSTED").get()).size).toBe(1);
});

test("tamamlandıktan sonra düzeltme yalnızca sahip; fark kaydı orijinal döneme işlenir, kayıt silinmez", async () => {
  const id = await inProgress();
  await transition({ appointmentId: id, action: "COMPLETE" }, "staff", STAFF);
  await expectReason(adjust({ appointmentId: id, newFinalKurus: 35000, reason: "İndirim" }, "staff", STAFF), "PERMISSION_DENIED");
  await adjust({ appointmentId: id, newFinalKurus: 35000, reason: "Sadakat indirimi" }, "owner", OWNER);
  await adjust({ appointmentId: id, newFinalKurus: 38000, reason: "İndirim düzeltildi" }, "owner", OWNER);
  const ledger = await db.collection("financialLedger").where("appointmentId", "==", id).get();
  expect(ledger.docs.map((d) => [d.get("type"), d.get("amountKurus")]).sort()).toEqual([
    ["ADJUSTMENT", -5000], ["ADJUSTMENT", 3000], ["SERVICE_COMPLETED", 40000],
  ].sort());
  const day = periodKeysOf(new Date()).day;
  expect(await agg("staff", day)).toMatchObject({ completedCount: 1, amountKurus: 38000, listedKurus: 40000 });
  await expectReason(adjust({ appointmentId: id, newFinalKurus: 38000, reason: "Aynı" }, "owner", OWNER), "NO_CHANGE");
});

test("rapor yetkisi: sahip salon + kırılım; uzman yalnızca kendi satırı", async () => {
  const id = await inProgress();
  await transition({ appointmentId: id, action: "COMPLETE" }, "staff", STAFF);
  const o = await reports({ salonId, periodType: "WEEK", anchorDate: today() }, "owner", OWNER);
  expect(o.salon).toMatchObject({ completedCount: 1, amountKurus: 40000 });
  expect(o.staff.map((x: { staffUid: string }) => x.staffUid)).toEqual(["staff", "owner"]);
  const s = await reports({ salonId, periodType: "WEEK", anchorDate: today() }, "staff", STAFF);
  expect(s.salon).toBeNull();
  expect(s.staff).toHaveLength(1);
  expect(s.staff[0]).toMatchObject({ staffUid: "staff", completedCount: 1 });
  await expectReason(reports({ salonId, periodType: "WEEK", anchorDate: today() }, "c1", C1), "PERMISSION_DENIED");
  // Önceki dönem: veriler korunur, yeni dönem sıfırdan başlar.
  const prev = await reports({ salonId, periodType: "MONTH", anchorDate: addDays(today(), -40) }, "owner", OWNER);
  expect(prev.salon).toMatchObject({ completedCount: 0, amountKurus: 0 });
});

test("sayaçlar ledger'dan yeniden üretilebilir", async () => {
  const id = await inProgress();
  await transition({ appointmentId: id, action: "COMPLETE" }, "staff", STAFF);
  const key = periodKeysOf(new Date()).day;
  await db.doc(`reportAggregates/${salonId}_ALL_${key}`).update({ amountKurus: 1 }); // bozulma
  await expectReason(rebuild({ salonId }, "staff", STAFF), "PERMISSION_DENIED");
  const r = await rebuild({ salonId }, "owner", OWNER);
  expect(r.entries).toBe(1);
  expect(await agg("ALL", key)).toMatchObject({ completedCount: 1, amountKurus: 40000 });
});
