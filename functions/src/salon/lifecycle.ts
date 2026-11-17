import { authedCallable } from "../lib/callable";
import { db, FieldValue } from "../lib/admin";
import { fail } from "../lib/errors";
import { docId, reqString } from "../lib/validation";
import { auditInTx } from "../lib/audit";
import { notifyInTx } from "../lib/notify";
import { addEvent, appts, customerLockRef, releaseLocks } from "../booking/appointments";
import { lockRef } from "../booking/context";

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

    const active = await appts().where("salonId", "==", salonId).where("status", "in", ["PENDING", "CONFIRMED", "IN_PROGRESS"]).get();
    if (active.docs.some((d) => d.get("status") === "IN_PROGRESS")) fail("failed-precondition", "IN_PROGRESS_APPOINTMENTS");

    let cancelled = 0;
    for (const doc of active.docs) {
      const done = await db.runTransaction(async (tx) => {
        const a = await tx.get(doc.ref);
        if (!["PENDING", "CONFIRMED"].includes(a.get("status"))) return false;
        const [lock, cl] = await Promise.all([tx.get(lockRef(salonId, a.get("staffUid"), a.get("localDate"))), tx.get(customerLockRef(a.get("customerUid")))]);
        const from = a.get("status");
        tx.update(a.ref, { status: "CANCELLED_BY_STAFF", cancelReason: `Salon kapandı: ${reason}`, cancelledBy: caller.uid, cancelledAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
        releaseLocks(tx, a, lock, cl);
        addEvent(tx, a.id, from, "CANCELLED_BY_STAFF", caller.uid, "OWNER", `Salon kapandı: ${reason}`);
        notifyInTx(tx, a.get("customerUid"), { type: "APPOINTMENT_CANCELLED", title: "Randevunuz iptal edildi", body: `${salon.get("name")} hizmet vermeyi bıraktığı için randevunuz iptal edildi.`, salonId, appointmentId: a.id });
        return true;
      });
      if (done) cancelled++;
    }

    const members = await salon.ref.collection("members").get();
    await db.runTransaction(async (tx) => {
      const fresh = await tx.get(salon.ref);
      if (fresh.get("ownerUid") !== caller.uid) fail("permission-denied", "PERMISSION_DENIED");
      tx.update(salon.ref, { status: "CLOSED", closedAt: FieldValue.serverTimestamp(), closedReason: reason, updatedAt: FieldValue.serverTimestamp() });
      for (const m of members.docs) {
        tx.update(m.ref, { status: "LEFT", acceptsBookings: false, updatedAt: FieldValue.serverTimestamp() });
        tx.set(db.collection("users").doc(m.id).collection("memberships").doc(salonId), { status: "LEFT", updatedAt: FieldValue.serverTimestamp() }, { merge: true });
        if (m.id !== caller.uid && m.get("status") === "ACTIVE") {
          notifyInTx(tx, m.id, { type: "SALON_CLOSED", title: "Salon kapatıldı", body: `${salon.get("name")} salonu kapatıldı; üyeliğiniz sona erdi.`, salonId });
        }
      }
      tx.delete(db.collection("salonOwners").doc(caller.uid));
      auditInTx(tx, { action: "SALON_CLOSED", actorUid: caller.uid, salonId, data: { reason, cancelled } });
    });
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

