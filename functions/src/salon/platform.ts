import { authedCallable } from "../lib/callable";
import { db, FieldValue } from "../lib/admin";
import { fail } from "../lib/errors";
import { docId, optString } from "../lib/validation";
import { auditInTx } from "../lib/audit";
import { notifyInTx } from "../lib/notify";
import { distanceKm, geohashPrefixesFor, isInServiceArea } from "../domain";

/**
 * Salon onayı (yalnızca platform yöneticisi). APPROVE: salon aramada görünür ve "Onaylı" rozeti alır.
 * REJECT: aramada görünmez; sahip gerekçeyi görür, bilgileri düzeltip yeniden incelemeye gönderebilir.
 */
export const reviewSalon = authedCallable<{ salonId?: unknown; action?: unknown; note?: unknown }, { verified: boolean }>("reviewSalon", async (data, caller) => {
  if (!caller.isAdmin) fail("permission-denied", "ADMIN_ONLY");
  const salonId = docId(data.salonId, "salonId");
  const action = data.action;
  if (action !== "APPROVE" && action !== "REJECT") fail("invalid-argument", "INVALID_ARGUMENT", { field: "action" });
  const note = optString(data.note, "note", 300) || null;
  if (action === "REJECT" && !note) fail("invalid-argument", "REASON_REQUIRED");
  const ref = db.collection("salons").doc(salonId);
  return db.runTransaction(async (tx) => {
    const salon = await tx.get(ref);
    if (!salon.exists) fail("not-found", "SALON_NOT_FOUND");
    const verified = action === "APPROVE";
    tx.update(ref, {
      verified,
      verification: { status: verified ? "APPROVED" : "REJECTED", note, reviewedAt: FieldValue.serverTimestamp(), reviewedBy: caller.uid },
      updatedAt: FieldValue.serverTimestamp(),
    });
    notifyInTx(tx, salon.get("ownerUid"), verified
      ? { type: "SALON_APPROVED", title: "Salonunuz onaylandı", body: `${salon.get("name")} artık müşteri aramalarında görünüyor.`, salonId }
      : { type: "SALON_REJECTED", title: "Salon başvurunuz düzeltme bekliyor", body: `Gerekçe: ${note}. Bilgileri güncelleyip yeniden incelemeye gönderebilirsiniz.`, salonId });
    auditInTx(tx, { action: verified ? "SALON_APPROVED" : "SALON_REJECTED", actorUid: caller.uid, salonId, data: { note } });
    return { verified };
  });
});

/** Reddedilen salon sahibi bilgileri düzelttikten sonra yeniden incelemeye gönderir. */
export const requestSalonReview = authedCallable<{ salonId?: unknown }, { status: "PENDING" }>("requestSalonReview", async (data, caller) => {
  const salonId = docId(data.salonId, "salonId");
  const ref = db.collection("salons").doc(salonId);
  await db.runTransaction(async (tx) => {
    const salon = await tx.get(ref);
    if (!salon.exists) fail("not-found", "SALON_NOT_FOUND");
    if (salon.get("ownerUid") !== caller.uid) fail("permission-denied", "PERMISSION_DENIED");
    if (salon.get("verification.status") !== "REJECTED") fail("failed-precondition", "STATE_CHANGED");
    tx.update(ref, { "verification.status": "PENDING", "verification.requestedAt": FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
  });
  return { status: "PENDING" };
});

/**
 * Kapatılmış salonu eski sahibi yeniden açar. Koşullar: başka bir salonun sahibi olmamak.
 * Sahip üyeliği yeniden etkinleşir; diğer uzmanlar yeniden davet edilmelidir (eski üyelikler geri gelmez).
 */
export const reopenSalon = authedCallable<{ salonId?: unknown }, { status: "ACTIVE" }>("reopenSalon", async (data, caller) => {
  const salonId = docId(data.salonId, "salonId");
  const ref = db.collection("salons").doc(salonId);
  await db.runTransaction(async (tx) => {
    const [salon, ownerLock, member] = await Promise.all([
      tx.get(ref),
      tx.get(db.collection("salonOwners").doc(caller.uid)),
      tx.get(ref.collection("members").doc(caller.uid)),
    ]);
    if (!salon.exists) fail("not-found", "SALON_NOT_FOUND");
    if (salon.get("ownerUid") !== caller.uid) fail("permission-denied", "PERMISSION_DENIED");
    if (salon.get("status") !== "CLOSED") fail("failed-precondition", "STATE_CHANGED", { status: salon.get("status") });
    // Platform yöneticisinin kaldırdığı salon sahibi tarafından yeniden açılamaz.
    if (salon.get("removedByAdmin") === true) fail("failed-precondition", "SALON_REMOVED");
    if (ownerLock.exists) fail("failed-precondition", "ALREADY_OWNER");
    tx.update(ref, { status: "ACTIVE", reopenedAt: FieldValue.serverTimestamp(), closedReason: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
    if (member.exists) tx.update(member.ref, { status: "ACTIVE", acceptsBookings: true, updatedAt: FieldValue.serverTimestamp() });
    tx.set(db.collection("users").doc(caller.uid).collection("memberships").doc(salonId), {
      salonId, salonName: salon.get("name"), salonCode: salon.get("code"), role: "OWNER", status: "ACTIVE", updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
    tx.create(db.collection("salonOwners").doc(caller.uid), { salonId, code: salon.get("code"), salonName: salon.get("name"), createdAt: FieldValue.serverTimestamp() });
    auditInTx(tx, { action: "SALON_REOPENED", actorUid: caller.uid, salonId });
  });
  return { status: "ACTIVE" };
});

export interface NearbySalon {
  salonId: string;
  name: string;
  type: string;
  city: string;
  district: string;
  addressLine: string;
  logoUrl: string | null;
  ratingAvg: number | null;
  ratingCount: number;
  lat: number;
  lng: number;
  distanceKm: number;
}

/** Konuma göre yakın, aktif ve onaylı salonlar (en fazla 50 km, en yakın 50 sonuç). */
export const searchNearbySalons = authedCallable<{ lat?: unknown; lng?: unknown; radiusKm?: unknown; type?: unknown }, { salons: NearbySalon[] }>(
  "searchNearbySalons",
  async (data) => {
    const lat = Number(data.lat);
    const lng = Number(data.lng);
    if (!isInServiceArea(lat, lng)) fail("invalid-argument", "INVALID_COORDINATES");
    const radiusKm = Math.min(Math.max(Number(data.radiusKm ?? 10) || 10, 1), 50);
    const type = typeof data.type === "string" && data.type ? data.type : null;
    const prefixes = geohashPrefixesFor(lat, lng, radiusKm);
    const snaps = await Promise.all(prefixes.map((p) =>
      db.collection("salons").where("status", "==", "ACTIVE").where("verified", "==", true)
        .orderBy("geohash").startAt(p).endAt(p + "").limit(300).get()));
    const seen = new Set<string>();
    const out: NearbySalon[] = [];
    for (const s of snaps) for (const d of s.docs) {
      if (seen.has(d.id)) continue;
      seen.add(d.id);
      const loc = d.get("location") as { lat: number; lng: number } | null;
      if (!loc) continue;
      if (type && d.get("type") !== type) continue;
      const km = distanceKm(lat, lng, loc.lat, loc.lng);
      if (km > radiusKm) continue;
      out.push({
        salonId: d.id, name: d.get("name"), type: d.get("type"), city: d.get("city"), district: d.get("district"), addressLine: d.get("addressLine"),
        logoUrl: d.get("logoUrl") ?? null, ratingAvg: d.get("ratingAvg") ?? null, ratingCount: d.get("ratingCount") ?? 0,
        lat: loc.lat, lng: loc.lng, distanceKm: Math.round(km * 10) / 10,
      });
    }
    out.sort((a, b) => a.distanceKm - b.distanceKm);
    return { salons: out.slice(0, 50) };
  },
);
