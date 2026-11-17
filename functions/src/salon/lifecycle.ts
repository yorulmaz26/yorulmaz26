import { authedCallable } from "../lib/callable";
import { db, FieldValue } from "../lib/admin";
import { fail } from "../lib/errors";
import { docId, reqString } from "../lib/validation";
import { auditInTx } from "../lib/audit";
import { notifyInTx } from "../lib/notify";
import { addEvent, appts, readLocks, releaseLocks } from "../booking/appointments";

async function requireOwner(salonId: string, uid: string) {
  const salon = await db.collection("salons").doc(salonId).get();
  if (!salon.exists) fail("not-found", "SALON_NOT_FOUND");
  if (salon.get("ownerUid") !== uid) fail("permission-denied", "PERMISSION_DENIED");
  if (salon.get("status") === "CLOSED") fail("failed-precondition", "SALON_CLOSED");
  return salon;
}

/**
 * Salonu kalıcı olarak kapatır (yalnızca sahip, "KAPAT" onayıyla):
 *  - İşlemdeki (IN_PROGRESS) randevu varsa önce tamamlanmalı/iptal edilmeli.
 *  - Gelecekteki bekleyen/onaylı randevular gerekçeyle iptal edilir, kilitler serbest bırakılır, müşteriler bilgilendirilir.
 *  - Tüm üyelikler sonlanır, salon listeden kalkar; salon kodu tekrar kullanılmaz. Finans/denetim kayıtları korunur.
 * Ardından eski sahip hesabını silebilir.
 */
export const closeSalon = authedCallable<{ salonId?: unknown; confirm?: unknown; reason?: unknown }, { cancelledAppointments: number }>(
  "closeSalon",
  async (data, caller) => {
    const salonId = docId(data.salonId, "salonId");
    const salon = await requireOwner(salonId, caller.uid);
    if (data.confirm !== "KAPAT") fail("invalid-argument", "CONFIRMATION_REQUIRED");
    const reason = reqString(data.reason, "reason", 3, 300);

    const cancelled = await shutDownSalon(salon, { actorUid: caller.uid, actorRole: "OWNER", reason, byAdmin: false });
    return { cancelledAppointments: cancelled };
  },
);

/**
 * Sahipliği salonun aktif bir uzmanına devreder (yalnızca sahip). Eski sahip aynı salonda uzman olarak kalır.
 * Yeni sahibin başka bir salonun sahibi olmaması gerekir (v1: kişi başına bir sahiplik).
 */
export const transferSalonOwnership = authedCallable<{ salonId?: unknown; newOwnerUid?: unknown; confirm?: unknown }, { ownerUid: string }>(
  "transferSalonOwnership",
  async (data, caller) => {
    const salonId = docId(data.salonId, "salonId");
    const newOwnerUid = docId(data.newOwnerUid, "newOwnerUid");
    await requireOwner(salonId, caller.uid);
    if (data.confirm !== "DEVRET") fail("invalid-argument", "CONFIRMATION_REQUIRED");
    if (newOwnerUid === caller.uid) fail("invalid-argument", "SAME_OWNER");

    await db.runTransaction(async (tx) => {
      const salonRef = db.collection("salons").doc(salonId);
      const oldRef = salonRef.collection("members").doc(caller.uid);
      const newRef = salonRef.collection("members").doc(newOwnerUid);
      const [salon, oldM, newM, oldLock, newLock] = await Promise.all([
        tx.get(salonRef), tx.get(oldRef), tx.get(newRef),
        tx.get(db.collection("salonOwners").doc(caller.uid)), tx.get(db.collection("salonOwners").doc(newOwnerUid)),
      ]);
      if (salon.get("ownerUid") !== caller.uid) fail("permission-denied", "PERMISSION_DENIED");
      if (!newM.exists || newM.get("status") !== "ACTIVE" || newM.get("role") !== "STAFF") fail("failed-precondition", "NEW_OWNER_NOT_ACTIVE_STAFF");
      if (newLock.exists) fail("failed-precondition", "NEW_OWNER_ALREADY_OWNS_SALON");
      const newName = `${newM.get("firstName")} ${newM.get("lastName")}`;
      tx.update(salonRef, { ownerUid: newOwnerUid, ownerDisplayName: newName, updatedAt: FieldValue.serverTimestamp() });
      tx.update(newRef, { role: "OWNER", updatedAt: FieldValue.serverTimestamp() });
      tx.update(oldRef, { role: "STAFF", specialty: oldM.get("specialty") === "Salon Sahibi" ? "Uzman" : oldM.get("specialty"), updatedAt: FieldValue.serverTimestamp() });
      const users = db.collection("users");
      tx.set(users.doc(newOwnerUid).collection("memberships").doc(salonId), { role: "OWNER", updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      tx.set(users.doc(caller.uid).collection("memberships").doc(salonId), { role: "STAFF", updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      tx.create(newLock.ref, { salonId, code: salon.get("code"), salonName: salon.get("name"), createdAt: FieldValue.serverTimestamp() });
      if (oldLock.exists) tx.delete(oldLock.ref);
      notifyInTx(tx, newOwnerUid, { type: "OWNERSHIP_TRANSFERRED", title: "Salon sahipliği size devredildi", body: `${salon.get("name")} salonunun yeni sahibi sizsiniz.`, salonId });
      auditInTx(tx, { action: "OWNERSHIP_TRANSFERRED", actorUid: caller.uid, salonId, data: { from: caller.uid, to: newOwnerUid } });
    });
    return { ownerUid: newOwnerUid };
  },
);


/**
 * Salonu kapatmanın ortak gövdesi (sahip kapatması ve platform yöneticisinin kaldırması):
 * gelecekteki aktif randevular gerekçeyle iptal edilir (kilitler serbest, müşteriler bilgilendirilir),
 * salon CLOSED olur, tüm üyelikler sonlanır, sahiplik kilidi kalkar. Finans/denetim kayıtları korunur.
 */
export async function shutDownSalon(
  salon: FirebaseFirestore.DocumentSnapshot,
  o: { actorUid: string; actorRole: "OWNER" | "ADMIN"; reason: string; byAdmin: boolean },
): Promise<number> {
  const salonId = salon.id;
  const active = await appts().where("salonId", "==", salonId).where("status", "in", ["PENDING", "CONFIRMED", "IN_PROGRESS"]).get();
  // Sahip kapatmasında işlemi süren randevu önce kapatılmalı; yönetici kaldırmasında o da iptal edilir.
  if (!o.byAdmin && active.docs.some((d) => d.get("status") === "IN_PROGRESS")) fail("failed-precondition", "IN_PROGRESS_APPOINTMENTS");
  const label = o.byAdmin ? `Salon platformdan kaldırıldı: ${o.reason}` : `Salon kapandı: ${o.reason}`;
  let cancelled = 0;
  for (const doc of active.docs) {
    const done = await db.runTransaction(async (tx) => {
      const a = await tx.get(doc.ref);
      if (!["PENDING", "CONFIRMED", "IN_PROGRESS"].includes(a.get("status"))) return false;
      const locks = await readLocks(tx, a);
      const from = a.get("status");
      tx.update(a.ref, { status: "CANCELLED_BY_STAFF", cancelReason: label, cancelledBy: o.actorUid, cancelledByRole: o.actorRole, cancelledAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
      releaseLocks(tx, a, locks);
      addEvent(tx, a.id, from, "CANCELLED_BY_STAFF", o.actorUid, o.actorRole, label);
      notifyInTx(tx, a.get("customerUid"), { type: "APPOINTMENT_CANCELLED", title: "Randevunuz iptal edildi", body: `${salon.get("name")} artık hizmet vermediği için randevunuz iptal edildi.`, salonId, appointmentId: a.id });
      return true;
    });
    if (done) cancelled++;
  }

  const members = await salon.ref.collection("members").get();
  const ownerUid = salon.get("ownerUid") as string;
  await db.runTransaction(async (tx) => {
    const fresh = await tx.get(salon.ref);
    if (!o.byAdmin && fresh.get("ownerUid") !== o.actorUid) fail("permission-denied", "PERMISSION_DENIED");
    tx.update(salon.ref, {
      status: "CLOSED", closedAt: FieldValue.serverTimestamp(), closedReason: o.reason, updatedAt: FieldValue.serverTimestamp(),
      ...(o.byAdmin ? { removedByAdmin: true, removedBy: o.actorUid } : {}),
    });
    for (const m of members.docs) {
      tx.update(m.ref, { status: "LEFT", acceptsBookings: false, updatedAt: FieldValue.serverTimestamp() });
      tx.set(db.collection("users").doc(m.id).collection("memberships").doc(salonId), { status: "LEFT", updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      if (m.id !== o.actorUid && m.get("status") === "ACTIVE") {
        notifyInTx(tx, m.id, o.byAdmin
          ? { type: "SALON_REMOVED", title: "Salon platformdan kaldırıldı", body: `${salon.get("name")} kaldırıldı. Gerekçe: ${o.reason}`, salonId }
          : { type: "SALON_CLOSED", title: "Salon kapatıldı", body: `${salon.get("name")} salonu kapatıldı; üyeliğiniz sona erdi.`, salonId });
      }
    }
    tx.delete(db.collection("salonOwners").doc(ownerUid));
    auditInTx(tx, { action: o.byAdmin ? "SALON_REMOVED_BY_ADMIN" : "SALON_CLOSED", actorUid: o.actorUid, salonId, data: { reason: o.reason, cancelled } });
  });
  return cancelled;
}
