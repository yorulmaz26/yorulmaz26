import { authedCallable } from "../lib/callable";
import { db, FieldValue, Timestamp } from "../lib/admin";
import { fail } from "../lib/errors";
import { consents, ConsentInput, idempotencyKey, personName } from "../lib/validation";
import { withIdempotency } from "../lib/idempotency";

export interface MembershipView {
  salonId: string;
  salonName: string;
  salonCode: string;
  role: "OWNER" | "STAFF";
  status: "ACTIVE" | "INACTIVE" | "LEFT";
}

export interface InvitationView {
  salonId: string;
  inviteId: string;
  salonName: string;
  firstName: string;
  lastName: string;
  specialty: string;
  expiresAt: string;
}

export interface BootstrapResult {
  user: { exists: boolean; firstName: string | null; lastName: string | null; phoneE164: string | null };
  memberships: MembershipView[];
  pendingInvitations: InvitationView[];
  activeAppointmentId: string | null;
}

/** Girişten hemen sonra çağrılır; yönlendirme kararı için gereken her şeyi tek seferde döner. */
export const bootstrapSession = authedCallable<Record<string, never>, BootstrapResult>("bootstrapSession", async (_d, caller) => {
  const [userSnap, membershipSnap, lockSnap] = await Promise.all([
    db.collection("users").doc(caller.uid).get(),
    db.collection("users").doc(caller.uid).collection("memberships").get(),
    db.collection("customerLocks").doc(caller.uid).get(),
  ]);

  const memberships: MembershipView[] = membershipSnap.docs.map((d) => ({
    salonId: d.id,
    salonName: d.get("salonName"),
    salonCode: d.get("salonCode"),
    role: d.get("role"),
    status: d.get("status"),
  }));

  let pendingInvitations: InvitationView[] = [];
  if (caller.phone) {
    const now = Timestamp.now();
    const inv = await db
      .collectionGroup("invitations")
      .where("phoneE164", "==", caller.phone)
      .where("status", "==", "PENDING")
      .get();
    const activeSalonIds = new Set(memberships.filter((m) => m.status === "ACTIVE").map((m) => m.salonId));
    pendingInvitations = inv.docs
      .filter((d) => (d.get("expiresAt") as Timestamp).toMillis() > now.toMillis())
      .filter((d) => !activeSalonIds.has(d.get("salonId")))
      .map((d) => ({
        salonId: d.get("salonId"),
        inviteId: d.id,
        salonName: d.get("salonName"),
        firstName: d.get("firstName"),
        lastName: d.get("lastName"),
        specialty: d.get("specialty"),
        expiresAt: (d.get("expiresAt") as Timestamp).toDate().toISOString(),
      }));
  }

  return {
    user: {
      exists: userSnap.exists,
      firstName: userSnap.get("firstName") ?? null,
      lastName: userSnap.get("lastName") ?? null,
      phoneE164: caller.phone,
    },
    memberships,
    pendingInvitations,
    activeAppointmentId: (lockSnap.get("activeAppointmentId") as string | undefined) ?? null,
  };
});

interface RegisterCustomerReq {
  firstName?: unknown;
  lastName?: unknown;
  consents?: ConsentInput;
  idempotencyKey?: unknown;
}

/** Müşteri profili. Telefon istemciden alınmaz; doğrulanmış Auth token'ından yazılır. */
export const registerCustomer = authedCallable<RegisterCustomerReq, { uid: string; created: boolean }>(
  "registerCustomer",
  async (data, caller) => {
    if (!caller.phone) fail("failed-precondition", "PHONE_NOT_VERIFIED");
    const key = idempotencyKey(data.idempotencyKey);
    const firstName = personName(data.firstName, "firstName");
    const lastName = personName(data.lastName, "lastName");
    const c = consents(data.consents);
    return withIdempotency(caller.uid, key, "registerCustomer", async () => {
      const ref = db.collection("users").doc(caller.uid);
      return db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        if (snap.exists) return { uid: caller.uid, created: false };
        tx.create(ref, {
          firstName,
          lastName,
          phoneE164: caller.phone,
          consents: { ...c, acceptedAt: FieldValue.serverTimestamp() },
          createdAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        });
        return { uid: caller.uid, created: true };
      });
    });
  },
);
