import { caller, clearFirestore, expectReason, uuid } from "./helpers";
import { bootstrapSession } from "../src/auth/session";
import { registerSalonOwner } from "../src/salon/registerSalonOwner";
import {
  acceptInvitation, createStaffInvitation, declineInvitation, leaveSalon, revokeInvitation, setMemberStatus, updateStaffMember,
} from "../src/staff/invitations";
import { LEGAL_VERSIONS } from "../src/lib/validation";
import { bucket, db } from "../src/lib/admin";

const consents = { kvkkVersion: LEGAL_VERSIONS.kvkk, termsVersion: LEGAL_VERSIONS.terms };
const invite = caller(createStaffInvitation);
const accept = caller(acceptInvitation);
const decline = caller(declineInvitation);
const revoke = caller(revokeInvitation);
const setStatus = caller(setMemberStatus);
const leave = caller(leaveSalon);
const update = caller(updateStaffMember);
const boot = caller(bootstrapSession);

const OWNER_PHONE = "+905301110000";
const STAFF_PHONE = "+905551112233";
let salonId = "";

beforeEach(async () => {
  await clearFirestore();
  const path = "uploads/owner/pending/logo.jpg";
  await bucket().file(path).save(Buffer.alloc(1000, 1), { contentType: "image/jpeg" });
  const r = await caller(registerSalonOwner)(
    {
      firstName: "Ayşe", lastName: "Yılmaz", consents, logoPath: path, photoPaths: [], idempotencyKey: uuid(),
      salon: { name: "Işıltı", businessPhone: "02125556677", type: "BEAUTY_SALON", city: "Ankara", district: "Çankaya", addressLine: "Kızılay Mah. 1" },
    },
    "owner",
    OWNER_PHONE,
  );
  salonId = r.salonId;
});

const inviteReq = (phone = "0555 111 22 33") => ({
  salonId, firstName: "Can", lastName: "Er", specialty: "Berber", phone, idempotencyKey: uuid(),
});

test("yalnızca salon sahibi davet oluşturur", async () => {
  await expectReason(invite(inviteReq(), "stranger", "+905009998877"), "PERMISSION_DENIED");
  const { inviteId } = await invite(inviteReq(), "owner", OWNER_PHONE);
  expect(inviteId).toBeTruthy();
  await expectReason(invite(inviteReq(), "owner", OWNER_PHONE), "ALREADY_INVITED");
  await expectReason(invite(inviteReq("0530 111 00 00"), "owner", OWNER_PHONE), "CANNOT_INVITE_SELF");
  await expectReason(invite(inviteReq("0212 555 66 77"), "owner", OWNER_PHONE), "INVALID_PHONE");
});

test("salon kodunu bilmek yetmez: farklı telefon daveti kabul edemez", async () => {
  const { inviteId } = await invite(inviteReq(), "owner", OWNER_PHONE);
  await expectReason(accept({ salonId, inviteId, consents }, "intruder", "+905009998877"), "INVITATION_PHONE_MISMATCH");
  await expectReason(accept({ salonId, inviteId, consents }, "intruder"), "PHONE_NOT_VERIFIED");
});

test("davetli doğrulanmış telefonla kabul eder ve STAFF olur", async () => {
  const { inviteId } = await invite(inviteReq(), "owner", OWNER_PHONE);
  const b1 = await boot({}, "staff", STAFF_PHONE);
  expect(b1.pendingInvitations.map((i: { inviteId: string }) => i.inviteId)).toEqual([inviteId]);
  await expectReason(accept({ salonId, inviteId }, "staff", STAFF_PHONE), "CONSENT_REQUIRED");
  const r = await accept({ salonId, inviteId, consents }, "staff", STAFF_PHONE);
  expect(r.salonId).toBe(salonId);
  const m = await db.doc(`salons/${salonId}/members/staff`).get();
  expect(m.get("role")).toBe("STAFF");
  expect(m.get("firstName")).toBe("Can");
  expect((await db.doc(`users/staff`).get()).get("phoneE164")).toBe(STAFF_PHONE);
  const b2 = await boot({}, "staff", STAFF_PHONE);
  expect(b2.pendingInvitations).toEqual([]);
  expect(b2.memberships[0]).toMatchObject({ salonId, role: "STAFF", status: "ACTIVE" });
  await expectReason(accept({ salonId, inviteId, consents }, "staff", STAFF_PHONE), "STATE_CHANGED");
  const ownerInbox = await db.collection("users/owner/notifications").get();
  expect(ownerInbox.docs.map((d) => d.get("type"))).toContain("STAFF_JOINED");
});

test("iptal edilen veya reddedilen davet kabul edilemez", async () => {
  const a = await invite(inviteReq(), "owner", OWNER_PHONE);
  await revoke({ salonId, inviteId: a.inviteId }, "owner", OWNER_PHONE);
  await expectReason(accept({ salonId, inviteId: a.inviteId, consents }, "staff", STAFF_PHONE), "STATE_CHANGED");
  const b = await invite(inviteReq(), "owner", OWNER_PHONE);
  await expectReason(revoke({ salonId, inviteId: b.inviteId }, "staff", STAFF_PHONE), "PERMISSION_DENIED");
  await decline({ salonId, inviteId: b.inviteId }, "staff", STAFF_PHONE);
  await expectReason(accept({ salonId, inviteId: b.inviteId, consents }, "staff", STAFF_PHONE), "STATE_CHANGED");
});

test("pasife alma, ayrılma ve düzenleme yetkileri", async () => {
  const { inviteId } = await invite(inviteReq(), "owner", OWNER_PHONE);
  await accept({ salonId, inviteId, consents }, "staff", STAFF_PHONE);

  await expectReason(setStatus({ salonId, memberUid: "owner", status: "INACTIVE" }, "owner", OWNER_PHONE), "OWNER_CANNOT_CHANGE_SELF");
  await expectReason(setStatus({ salonId, memberUid: "staff", status: "INACTIVE" }, "staff", STAFF_PHONE), "PERMISSION_DENIED");
  await setStatus({ salonId, memberUid: "staff", status: "INACTIVE" }, "owner", OWNER_PHONE);
  expect((await db.doc(`users/staff/memberships/${salonId}`).get()).get("status")).toBe("INACTIVE");
  await setStatus({ salonId, memberUid: "staff", status: "ACTIVE" }, "owner", OWNER_PHONE);

  await expectReason(
    update({ salonId, memberUid: "owner", firstName: "X", lastName: "Y", specialty: "Kesim" }, "staff", STAFF_PHONE),
    "INVALID_ARGUMENT",
  );
  await expectReason(
    update({ salonId, memberUid: "owner", firstName: "Xx", lastName: "Yy", specialty: "Kesim" }, "staff", STAFF_PHONE),
    "PERMISSION_DENIED",
  );
  await update({ salonId, memberUid: "staff", firstName: "Cem", lastName: "Er", specialty: "Renk" }, "staff", STAFF_PHONE);
  expect((await db.doc(`salons/${salonId}/members/staff`).get()).get("specialty")).toBe("Renk");

  await expectReason(leave({ salonId }, "owner", OWNER_PHONE), "OWNER_CANNOT_LEAVE");
  await leave({ salonId }, "staff", STAFF_PHONE);
  expect((await db.doc(`salons/${salonId}/members/staff`).get()).get("status")).toBe("LEFT");

  // Ayrılan personel yeniden davet edilip katılabilir.
  const again = await invite(inviteReq(), "owner", OWNER_PHONE);
  await accept({ salonId, inviteId: again.inviteId }, "staff", STAFF_PHONE);
  expect((await db.doc(`salons/${salonId}/members/staff`).get()).get("status")).toBe("ACTIVE");
});
