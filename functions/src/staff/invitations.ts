import { DocumentSnapshot, Transaction } from "firebase-admin/firestore";
import { authedCallable, Caller } from "../lib/callable";
import { adminAuth, db, FieldValue, Timestamp } from "../lib/admin";
import { fail } from "../lib/errors";
import { consents, ConsentInput, docId, idempotencyKey, personName, reqString } from "../lib/validation";
import { withIdempotency } from "../lib/idempotency";
import { enforceRateLimit } from "../lib/rateLimit";
import { audit, auditInTx } from "../lib/audit";
import { notifyInTx } from "../lib/notify";
import { normalizeTrPhone } from "../domain";
import { defaultWeeklySchedule } from "../salon/defaults";

export const INVITATION_TTL_DAYS = 7;

const salonRef = (salonId: string) => db.collection("salons").doc(salonId);

async function requireOwner(salonId: string, caller: Caller): Promise<DocumentSnapshot> {
  const salon = await salonRef(salonId).get();
  if (!salon.exists) fail("not-found", "SALON_NOT_FOUND");
  if (salon.get("ownerUid") !== caller.uid) fail("permission-denied", "PERMISSION_DENIED");
  return salon;
}

function mirrorMembership(tx: Transaction, uid: string, salon: DocumentSnapshot, role: string, status: string) {
  tx.set(
    db.collection("users").doc(uid).collection("memberships").doc(salon.id),
    {
      salonId: salon.id,
      salonName: salon.get("name"),
      salonCode: salon.get("code"),
      role,
      status,
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );
}

/** Aktif salon randevusu olan gelecek randevular (Aşama 6'da yeniden planlama akışına bağlanır). */
async function futureActiveAppointments(salonId: string, staffUid: string): Promise<string[]> {
  const snap = await db
    .collection("appointments")
    .where("salonId", "==", salonId)
    .where("staffUid", "==", staffUid)
    .where("status", "in", ["PENDING", "CONFIRMED"])
    .where("startAt", ">=", Timestamp.now())
    .get();
  return snap.docs.map((d) => d.id);
}

// ---------------------------------------------------------------------------

interface CreateInvitationReq {
  salonId?: unknown;
  firstName?: unknown;
  lastName?: unknown;
  specialty?: unknown;
  phone?: unknown;
  idempotencyKey?: unknown;
}

/** Yalnızca salon sahibi. Davet, davetlinin doğrulanmış telefonuna bağlıdır; salon kodu yetki vermez. */
export const createStaffInvitation = authedCallable<CreateInvitationReq, { inviteId: string }>(
  "createStaffInvitation",
  async (data, caller) => {
    const salonId = docId(data.salonId, "salonId");
    const key = idempotencyKey(data.idempotencyKey);
    const firstName = personName(data.firstName, "firstName");
    const lastName = personName(data.lastName, "lastName");
    const specialty = reqString(data.specialty, "specialty", 2, 60);
    const phone = normalizeTrPhone(String(data.phone ?? ""), true);
    if (!phone) fail("invalid-argument", "INVALID_PHONE", { field: "phone" });

    return withIdempotency(caller.uid, key, "createStaffInvitation", async () => {
      const salon = await requireOwner(salonId, caller);
      if (phone === caller.phone) fail("failed-precondition", "CANNOT_INVITE_SELF");
      await enforceRateLimit(`invite_${salonId}`, 30, 86_400);

      // Bu telefonla kayıtlı bir kullanıcı zaten aktif üyeyse davet gereksiz.
      const existingUser = await adminAuth().getUserByPhoneNumber(phone).catch(() => null);
      if (existingUser) {
        const m = await salonRef(salonId).collection("members").doc(existingUser.uid).get();
        if (m.exists && m.get("status") === "ACTIVE") fail("already-exists", "ALREADY_MEMBER");
      }

      const invitations = salonRef(salonId).collection("invitations");
      const dup = await invitations.where("phoneE164", "==", phone).where("status", "==", "PENDING").get();
      if (dup.docs.some((d) => (d.get("expiresAt") as Timestamp).toMillis() > Date.now())) {
        fail("already-exists", "ALREADY_INVITED");
      }

      const ref = invitations.doc();
      await ref.create({
        salonId,
        salonName: salon.get("name"),
        phoneE164: phone,
        firstName,
        lastName,
        specialty,
        status: "PENDING",
        createdBy: caller.uid,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
        expiresAt: Timestamp.fromMillis(Date.now() + INVITATION_TTL_DAYS * 86_400_000),
      });
      await audit({ action: "INVITATION_CREATED", actorUid: caller.uid, salonId, targetId: ref.id });
      return { inviteId: ref.id };
    });
  },
);

interface InviteRef {
  salonId?: unknown;
  inviteId?: unknown;
}

export const revokeInvitation = authedCallable<InviteRef, { status: string }>("revokeInvitation", async (data, caller) => {
  const salonId = docId(data.salonId, "salonId");
  const inviteId = docId(data.inviteId, "inviteId");
  await requireOwner(salonId, caller);
  const ref = salonRef(salonId).collection("invitations").doc(inviteId);
  return db.runTransaction(async (tx) => {
    const inv = await tx.get(ref);
    if (!inv.exists) fail("not-found", "INVITATION_NOT_FOUND");
    if (inv.get("status") !== "PENDING") fail("failed-precondition", "STATE_CHANGED", { status: inv.get("status") });
    tx.update(ref, { status: "REVOKED", updatedAt: FieldValue.serverTimestamp() });
    auditInTx(tx, { action: "INVITATION_REVOKED", actorUid: caller.uid, salonId, targetId: inviteId });
    return { status: "REVOKED" };
  });
});

interface AcceptReq extends InviteRef {
  consents?: ConsentInput;
}

/**
 * Davetli, OTP ile doğrulanmış telefonuyla daveti kabul eder → ilgili salonun STAFF üyesi olur.
 * Profil yoksa davet bilgileriyle oluşturulur (yasal onaylar gerekir). Genel salon kaydı açılmaz.
 */
export const acceptInvitation = authedCallable<AcceptReq, { salonId: string; salonName: string }>(
  "acceptInvitation",
  async (data, caller) => {
    if (!caller.phone) fail("failed-precondition", "PHONE_NOT_VERIFIED");
    const salonId = docId(data.salonId, "salonId");
    const inviteId = docId(data.inviteId, "inviteId");
    const sRef = salonRef(salonId);
    const invRef = sRef.collection("invitations").doc(inviteId);
    const memberRef = sRef.collection("members").doc(caller.uid);
    const userRef = db.collection("users").doc(caller.uid);

    return db.runTransaction(async (tx) => {
      const [inv, salon, member, user] = await Promise.all([tx.get(invRef), tx.get(sRef), tx.get(memberRef), tx.get(userRef)]);
      if (!inv.exists || !salon.exists) fail("not-found", "INVITATION_NOT_FOUND");
      if (inv.get("phoneE164") !== caller.phone) fail("permission-denied", "INVITATION_PHONE_MISMATCH");
      if (inv.get("status") !== "PENDING") fail("failed-precondition", "STATE_CHANGED", { status: inv.get("status") });
      if ((inv.get("expiresAt") as Timestamp).toMillis() <= Date.now()) fail("failed-precondition", "INVITATION_EXPIRED");
      if (salon.get("status") !== "ACTIVE") fail("failed-precondition", "SALON_INACTIVE");
      if (member.exists && member.get("status") === "ACTIVE") fail("already-exists", "ALREADY_MEMBER");
      if (member.exists && member.get("role") === "OWNER") fail("already-exists", "ALREADY_MEMBER");

      const now = FieldValue.serverTimestamp();
      const firstName = user.exists ? user.get("firstName") : inv.get("firstName");
      const lastName = user.exists ? user.get("lastName") : inv.get("lastName");
      if (!user.exists) {
        const c = consents(data.consents);
        tx.create(userRef, {
          firstName,
          lastName,
          phoneE164: caller.phone,
          consents: { ...c, acceptedAt: now },
          createdAt: now,
          updatedAt: now,
        });
      }
      const memberData = {
        uid: caller.uid,
        salonId,
        role: "STAFF",
        status: "ACTIVE",
        firstName,
        lastName,
        specialty: inv.get("specialty"),
        acceptsBookings: true,
        joinedAt: now,
        updatedAt: now,
      };
      if (member.exists) {
        tx.update(memberRef, memberData); // eski puanlar korunur
      } else {
        tx.create(memberRef, { ...memberData, ratingSum: 0, ratingCount: 0, ratingAvg: null, createdAt: now });
        tx.set(memberRef.collection("schedule").doc("weekly"), { ...defaultWeeklySchedule(), version: 1, updatedAt: now });
      }
      mirrorMembership(tx, caller.uid, salon, "STAFF", "ACTIVE");
      tx.update(invRef, { status: "ACCEPTED", acceptedBy: caller.uid, updatedAt: now });
      notifyInTx(tx, salon.get("ownerUid"), {
        type: "STAFF_JOINED",
        title: "Yeni uzman katıldı",
        body: "Davet ettiğiniz uzman salonunuza katıldı.",
        salonId,
      });
      auditInTx(tx, { action: "INVITATION_ACCEPTED", actorUid: caller.uid, salonId, targetId: inviteId });
      return { salonId, salonName: salon.get("name") };
    });
  },
);

export const declineInvitation = authedCallable<InviteRef, { status: string }>("declineInvitation", async (data, caller) => {
  if (!caller.phone) fail("failed-precondition", "PHONE_NOT_VERIFIED");
  const salonId = docId(data.salonId, "salonId");
  const inviteId = docId(data.inviteId, "inviteId");
  const ref = salonRef(salonId).collection("invitations").doc(inviteId);
  return db.runTransaction(async (tx) => {
    const inv = await tx.get(ref);
    if (!inv.exists) fail("not-found", "INVITATION_NOT_FOUND");
    if (inv.get("phoneE164") !== caller.phone) fail("permission-denied", "INVITATION_PHONE_MISMATCH");
    if (inv.get("status") !== "PENDING") fail("failed-precondition", "STATE_CHANGED", { status: inv.get("status") });
    tx.update(ref, { status: "DECLINED", updatedAt: FieldValue.serverTimestamp() });
    return { status: "DECLINED" };
  });
});

interface MemberRef {
  salonId?: unknown;
  memberUid?: unknown;
}

interface UpdateMemberReq extends MemberRef {
  firstName?: unknown;
  lastName?: unknown;
  specialty?: unknown;
}

/** Sahip herhangi bir üyenin, üye yalnızca kendi kartını (ad, uzmanlık) düzenler. */
export const updateStaffMember = authedCallable<UpdateMemberReq, { ok: true }>("updateStaffMember", async (data, caller) => {
  const salonId = docId(data.salonId, "salonId");
  const memberUid = docId(data.memberUid, "memberUid");
  const firstName = personName(data.firstName, "firstName");
  const lastName = personName(data.lastName, "lastName");
  const specialty = reqString(data.specialty, "specialty", 2, 60);
  const salon = await salonRef(salonId).get();
  if (!salon.exists) fail("not-found", "SALON_NOT_FOUND");
  if (salon.get("ownerUid") !== caller.uid && memberUid !== caller.uid) fail("permission-denied", "PERMISSION_DENIED");
  const ref = salonRef(salonId).collection("members").doc(memberUid);
  await db.runTransaction(async (tx) => {
    const m = await tx.get(ref);
    if (!m.exists) fail("not-found", "MEMBER_NOT_FOUND");
    tx.update(ref, { firstName, lastName, specialty, updatedAt: FieldValue.serverTimestamp() });
    if (m.get("role") === "OWNER") {
      tx.update(salonRef(salonId), { ownerDisplayName: `${firstName} ${lastName}`, updatedAt: FieldValue.serverTimestamp() });
    }
    auditInTx(tx, { action: "MEMBER_UPDATED", actorUid: caller.uid, salonId, targetId: memberUid });
  });
  return { ok: true };
});

interface SetStatusReq extends MemberRef {
  status?: unknown;
}

/** Sahip personeli pasife alır / yeniden aktifleştirir. Sahip kendini pasifleştiremez. */
export const setMemberStatus = authedCallable<SetStatusReq, { status: string; affectedFutureAppointments: string[] }>(
  "setMemberStatus",
  async (data, caller) => {
    const salonId = docId(data.salonId, "salonId");
    const memberUid = docId(data.memberUid, "memberUid");
    const status = data.status;
    if (status !== "ACTIVE" && status !== "INACTIVE") fail("invalid-argument", "INVALID_ARGUMENT", { field: "status" });
    const salon = await requireOwner(salonId, caller);
    if (memberUid === caller.uid) fail("failed-precondition", "OWNER_CANNOT_CHANGE_SELF");
    const ref = salonRef(salonId).collection("members").doc(memberUid);
    await db.runTransaction(async (tx) => {
      const m = await tx.get(ref);
      if (!m.exists || m.get("status") === "LEFT") fail("not-found", "MEMBER_NOT_FOUND");
      tx.update(ref, { status, acceptsBookings: status === "ACTIVE", updatedAt: FieldValue.serverTimestamp() });
      mirrorMembership(tx, memberUid, salon, "STAFF", status);
      notifyInTx(tx, memberUid, {
        type: status === "ACTIVE" ? "MEMBER_ACTIVATED" : "MEMBER_DEACTIVATED",
        title: status === "ACTIVE" ? "Hesabınız aktifleştirildi" : "Salon hesabınız pasife alındı",
        body: `${salon.get("name")} salonundaki uzman hesabınızın durumu güncellendi.`,
        salonId,
      });
      auditInTx(tx, { action: "MEMBER_STATUS", actorUid: caller.uid, salonId, targetId: memberUid, data: { status } });
    });
    return { status, affectedFutureAppointments: status === "INACTIVE" ? await futureActiveAppointments(salonId, memberUid) : [] };
  },
);

/** Personelin salondan ayrılması. Sahip ayrılamaz. */
export const leaveSalon = authedCallable<{ salonId?: unknown }, { status: string; affectedFutureAppointments: string[] }>(
  "leaveSalon",
  async (data, caller) => {
    const salonId = docId(data.salonId, "salonId");
    const sRef = salonRef(salonId);
    const ref = sRef.collection("members").doc(caller.uid);
    await db.runTransaction(async (tx) => {
      const [salon, m] = await Promise.all([tx.get(sRef), tx.get(ref)]);
      if (!salon.exists || !m.exists || m.get("status") === "LEFT") fail("not-found", "MEMBER_NOT_FOUND");
      if (m.get("role") === "OWNER") fail("failed-precondition", "OWNER_CANNOT_LEAVE");
      tx.update(ref, { status: "LEFT", acceptsBookings: false, updatedAt: FieldValue.serverTimestamp() });
      mirrorMembership(tx, caller.uid, salon, "STAFF", "LEFT");
      notifyInTx(tx, salon.get("ownerUid"), {
        type: "STAFF_LEFT",
        title: "Bir uzman salondan ayrıldı",
        body: "Uzman listenizi ve gelecek randevuları kontrol edin.",
        salonId,
      });
      auditInTx(tx, { action: "MEMBER_LEFT", actorUid: caller.uid, salonId, targetId: caller.uid });
    });
    return { status: "LEFT", affectedFutureAppointments: await futureActiveAppointments(salonId, caller.uid) };
  },
);
