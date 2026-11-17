import { caller, clearFirestore, expectReason, uuid } from "./helpers";
import { registerSalonOwner } from "../src/salon/registerSalonOwner";
import { acceptInvitation, createStaffInvitation } from "../src/staff/invitations";
import {
  deleteStaffDateClosure, serviceImpact, updateSalonClosedDays, updateWeeklySchedule, upsertSalonDateClosure, upsertStaffDateClosure,
} from "../src/schedule/scheduleFunctions";
import { LEGAL_VERSIONS } from "../src/lib/validation";
import { bucket, db, Timestamp } from "../src/lib/admin";
import { addDays, instantAt, isoWeekday, localDateOf } from "../src/domain";

const consents = { kvkkVersion: LEGAL_VERSIONS.kvkk, termsVersion: LEGAL_VERSIONS.terms };
const weekly = caller(updateWeeklySchedule);
const staffClosure = caller(upsertStaffDateClosure);
const delStaffClosure = caller(deleteStaffDateClosure);
const salonDays = caller(updateSalonClosedDays);
const salonClosure = caller(upsertSalonDateClosure);
const impact = caller(serviceImpact);
const OWNER_PHONE = "+905301110000";
const STAFF_PHONE = "+905551112233";
let salonId = "";

// Gelecekte bir Salı (ISO 2) bul — varsayılan program Pzt–Cmt 09:00–19:00.
function nextWeekday(target: number): string {
  let d = addDays(localDateOf(new Date()), 2);
  while (isoWeekday(d) !== target) d = addDays(d, 1);
  return d;
}
const TUESDAY = nextWeekday(2);

async function seedAppointment(id: string, staffUid: string, localDate: string, start: number, end: number, serviceId = "svc1") {
  await db.doc(`appointments/${id}`).set({
    salonId, staffUid, customerUid: "cust", customerName: "Ali Kaya", status: "CONFIRMED",
    localDate, startMinuteOfDay: start, endMinuteOfDay: end,
    startAt: Timestamp.fromDate(instantAt(localDate, start)), endAt: Timestamp.fromDate(instantAt(localDate, end)),
    items: [{ serviceId, name: "Kesim", priceKurus: 40000, durationMinutes: end - start }],
  });
}

const day = (shifts: [string, string][], breaks: [string, string][] = []) => ({
  shifts: shifts.map(([start, end]) => ({ start, end })), breaks: breaks.map(([start, end]) => ({ start, end })),
});
const fullWeek = (override: Record<string, unknown> = {}) => {
  const d: Record<string, unknown> = {};
  for (let i = 1; i <= 6; i++) d[String(i)] = day([["09:00", "19:00"]]);
  d["7"] = day([]);
  return { ...d, ...override };
};

beforeEach(async () => {
  await clearFirestore();
  const path = "uploads/owner/pending/logo.jpg";
  await bucket().file(path).save(Buffer.alloc(1000, 1), { contentType: "image/jpeg" });
  salonId = (await caller(registerSalonOwner)(
    {
      firstName: "Ayşe", lastName: "Yılmaz", consents, logoPath: path, photoPaths: [], idempotencyKey: uuid(),
      salon: { name: "Işıltı", businessPhone: "02125556677", type: "BEAUTY_SALON", city: "Ankara", district: "Çankaya", addressLine: "Kızılay Mah. 1" },
    },
    "owner", OWNER_PHONE,
  )).salonId;
  const inv = await caller(createStaffInvitation)(
    { salonId, firstName: "Can", lastName: "Er", specialty: "Berber", phone: STAFF_PHONE, idempotencyKey: uuid() }, "owner", OWNER_PHONE,
  );
  await caller(acceptInvitation)({ salonId, inviteId: inv.inviteId, consents }, "staff", STAFF_PHONE);
});

test("çalışma saatini daraltmak: önizleme çakışmayı gösterir, onaysız uygulanmaz, onayla işaretlenir", async () => {
  await seedAppointment("a1", "staff", TUESDAY, 17 * 60, 18 * 60);
  await seedAppointment("a2", "staff", TUESDAY, 10 * 60, 11 * 60);
  const days = fullWeek({ "2": day([["09:00", "16:00"]], [["12:00", "13:00"]]) });

  const preview = await weekly({ salonId, days, weeklyDaysOff: [7], dryRun: true }, "staff", STAFF_PHONE);
  expect(preview.applied).toBe(false);
  expect(preview.conflicts.map((c: { appointmentId: string; reason: string }) => [c.appointmentId, c.reason])).toEqual([["a1", "OUTSIDE_WORKING_HOURS"]]);

  await expectReason(weekly({ salonId, days, weeklyDaysOff: [7] }, "staff", STAFF_PHONE), "CONFLICTS_NOT_ACKNOWLEDGED");
  const res = await weekly({ salonId, days, weeklyDaysOff: [7], acknowledgeConflicts: true }, "staff", STAFF_PHONE);
  expect(res.applied).toBe(true);
  const sched = await db.doc(`salons/${salonId}/members/staff/schedule/weekly`).get();
  expect(sched.get("days")["2"].shifts).toEqual([{ start: "09:00", end: "16:00" }]);
  expect(sched.get("version")).toBe(2);
  expect((await db.doc("appointments/a1").get()).get("attention").reason).toBe("OUTSIDE_WORKING_HOURS");
  expect((await db.doc("appointments/a2").get()).get("attention")).toBeUndefined();
  const ownerInbox = await db.collection("users/owner/notifications").where("type", "==", "SCHEDULE_CONFLICT").get();
  expect(ownerInbox.size).toBe(1);
});

test("geçersiz program reddedilir", async () => {
  await expectReason(weekly({ salonId, days: fullWeek({ "1": day([["10:00", "09:00"]]) }), weeklyDaysOff: [] }, "staff", STAFF_PHONE), "END_BEFORE_START");
  await expectReason(weekly({ salonId, days: fullWeek({ "1": day([["09:00", "12:00"], ["11:00", "14:00"]]) }), weeklyDaysOff: [] }, "staff", STAFF_PHONE), "SHIFTS_OVERLAP");
  await expectReason(weekly({ salonId, days: fullWeek({ "1": day([["09:00", "12:00"]], [["11:30", "12:30"]]) }), weeklyDaysOff: [] }, "staff", STAFF_PHONE), "BREAK_OUTSIDE_SHIFT");
  await expectReason(weekly({ salonId, days: fullWeek(), weeklyDaysOff: [8] }, "staff", STAFF_PHONE), "INVALID_ARGUMENT");
  await expectReason(weekly({ salonId, days: fullWeek(), weeklyDaysOff: [] }, "stranger", "+905009998877"), "PERMISSION_DENIED");
});

test("haftalık izin günü eklemek o günkü randevuları işaretler", async () => {
  await seedAppointment("a1", "staff", TUESDAY, 600, 660);
  const p = await weekly({ salonId, days: fullWeek(), weeklyDaysOff: [2, 7], dryRun: true }, "staff", STAFF_PHONE);
  expect(p.conflicts[0].reason).toBe("STAFF_DAY_OFF");
});

test("kişisel tarih kapatma: çakışma, onay, silme", async () => {
  await seedAppointment("a1", "staff", TUESDAY, 600, 660);
  const req = { salonId, startDate: TUESDAY, endDate: addDays(TUESDAY, 2), note: "Yıllık izin" };
  const p = await staffClosure({ ...req, dryRun: true }, "staff", STAFF_PHONE);
  expect(p.conflicts[0].reason).toBe("STAFF_CLOSURE");
  await expectReason(staffClosure(req, "staff", STAFF_PHONE), "CONFLICTS_NOT_ACKNOWLEDGED");
  const r = await staffClosure({ ...req, acknowledgeConflicts: true }, "staff", STAFF_PHONE);
  const doc = await db.doc(`salons/${salonId}/members/staff/closures/${r.closureId}`).get();
  expect(doc.get("note")).toBe("Yıllık izin");
  await expectReason(staffClosure({ salonId, startDate: "2020-01-01", endDate: "2020-01-02" }, "staff", STAFF_PHONE), "PAST_DATE");
  await delStaffClosure({ salonId, closureId: r.closureId }, "staff", STAFF_PHONE);
  expect((await db.doc(`salons/${salonId}/members/staff/closures/${r.closureId}`).get()).exists).toBe(false);
});

test("salon kapalı günleri yalnızca sahip; tüm uzmanların randevuları analiz edilir", async () => {
  await seedAppointment("a1", "staff", TUESDAY, 600, 660);
  await seedAppointment("a2", "owner", TUESDAY, 700, 760);
  await expectReason(salonDays({ salonId, weeklyClosedDays: [2] }, "staff", STAFF_PHONE), "PERMISSION_DENIED");
  const p = await salonDays({ salonId, weeklyClosedDays: [2], dryRun: true }, "owner", OWNER_PHONE);
  expect(p.conflicts.map((c: { appointmentId: string }) => c.appointmentId).sort()).toEqual(["a1", "a2"]);
  await salonDays({ salonId, weeklyClosedDays: [2], acknowledgeConflicts: true }, "owner", OWNER_PHONE);
  expect((await db.doc(`salons/${salonId}`).get()).get("weeklyClosedDays")).toEqual([2]);
  const staffInbox = await db.collection("users/staff/notifications").where("type", "==", "SCHEDULE_CONFLICT").get();
  expect(staffInbox.size).toBe(1);
  await expectReason(salonDays({ salonId, weeklyClosedDays: [1, 2, 3, 4, 5, 6, 7] }, "owner", OWNER_PHONE), "ALL_DAYS_CLOSED");
});

test("salon tarih kapatması kamuya açık notla saklanır", async () => {
  const r = await salonClosure({ salonId, startDate: TUESDAY, endDate: TUESDAY, note: "Bakım" }, "owner", OWNER_PHONE);
  expect((await db.doc(`salons/${salonId}/closures/${r.closureId}`).get()).get("publicNote")).toBe("Bakım");
});

test("hizmet etkisi: hizmeti içeren gelecek randevu sayısı", async () => {
  await db.doc(`salons/${salonId}/services/svc1`).set({ staffUid: "staff", salonId, name: "Kesim" });
  await seedAppointment("a1", "staff", TUESDAY, 600, 660, "svc1");
  await seedAppointment("a2", "staff", TUESDAY, 700, 760, "svc2");
  expect((await impact({ salonId, serviceId: "svc1" }, "staff", STAFF_PHONE)).futureAppointments).toBe(1);
  expect((await impact({ salonId, serviceId: "svc1" }, "owner", OWNER_PHONE)).futureAppointments).toBe(1);
  await expectReason(impact({ salonId, serviceId: "svc1" }, "stranger", "+905009998877"), "PERMISSION_DENIED");
});
