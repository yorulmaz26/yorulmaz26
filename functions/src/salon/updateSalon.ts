import { authedCallable, Caller } from "../lib/callable";
import { db, FieldValue } from "../lib/admin";
import { fail } from "../lib/errors";
import { optString, reqString, docId } from "../lib/validation";
import { auditInTx } from "../lib/audit";
import { assertPendingImage, deleteQuietly, promoteImage, StoredImage } from "../lib/storageFiles";
import { isValidCityDistrict, normalizeTrPhone, searchKey, encodeGeohash } from "../domain";
import { MAX_SALON_PHOTOS, SALON_TYPES, SalonType } from "./defaults";

async function requireOwnedSalon(salonId: string, caller: Caller) {
  const snap = await db.collection("salons").doc(salonId).get();
  if (!snap.exists) fail("not-found", "SALON_NOT_FOUND");
  if (snap.get("ownerUid") !== caller.uid) fail("permission-denied", "PERMISSION_DENIED");
  return snap;
}

/** Türkiye sınırlarını kabaca kapsayan kutu — yanlış girilmiş koordinatı reddetmek için. */
export function isInTurkey(lat: number, lng: number): boolean {
  return lat >= 35.7 && lat <= 42.2 && lng >= 25.5 && lng <= 45.0;
}

interface UpdateProfileReq {
  salonId?: unknown;
  name?: unknown;
  type?: unknown;
  businessPhone?: unknown;
  city?: unknown;
  district?: unknown;
  addressLine?: unknown;
  description?: unknown;
  location?: { lat?: unknown; lng?: unknown } | null;
}

/**
 * Dükkan Ayarları (yalnızca salon sahibi). Salon adı değişirse tüm üyelerin
 * users/{uid}/memberships aynası aynı transaction'da güncellenir.
 */
export const updateSalonProfile = authedCallable<UpdateProfileReq, { ok: true }>("updateSalonProfile", async (data, caller) => {
  const salonId = docId(data.salonId, "salonId");
  const name = reqString(data.name, "name", 2, 80);
  if (!SALON_TYPES.includes(data.type as SalonType)) fail("invalid-argument", "INVALID_ARGUMENT", { field: "type" });
  const businessPhone = normalizeTrPhone(String(data.businessPhone ?? ""));
  if (!businessPhone) fail("invalid-argument", "INVALID_PHONE", { field: "businessPhone" });
  const city = reqString(data.city, "city", 2, 40);
  const district = reqString(data.district, "district", 2, 60);
  if (!isValidCityDistrict(city, district)) fail("invalid-argument", "INVALID_LOCATION");
  const addressLine = reqString(data.addressLine, "addressLine", 5, 300);
  const description = optString(data.description, "description", 1000);
  let location: { lat: number; lng: number } | null = null;
  if (data.location) {
    const lat = Number(data.location.lat);
    const lng = Number(data.location.lng);
    if (!Number.isFinite(lat) || !Number.isFinite(lng) || !isInTurkey(lat, lng)) {
      fail("invalid-argument", "INVALID_COORDINATES");
    }
    location = { lat: Math.round(lat * 1e6) / 1e6, lng: Math.round(lng * 1e6) / 1e6 };
  }

  await requireOwnedSalon(salonId, caller);
  const salonRef = db.collection("salons").doc(salonId);
  const members = await salonRef.collection("members").get();
  await db.runTransaction(async (tx) => {
    const salon = await tx.get(salonRef);
    if (salon.get("ownerUid") !== caller.uid) fail("permission-denied", "PERMISSION_DENIED");
    const nameChanged = salon.get("name") !== name;
    tx.update(salonRef, {
      name,
      nameLower: searchKey(name),
      type: data.type,
      businessPhoneE164: businessPhone,
      city,
      district,
      addressLine,
      description,
      location,
      // "Yakınımdaki salonlar" araması için (hücre önekleriyle aralık sorgusu).
      geohash: location ? encodeGeohash(location.lat, location.lng, 9) : null,
      updatedAt: FieldValue.serverTimestamp(),
    });
    if (nameChanged) {
      for (const m of members.docs) {
        tx.set(db.collection("users").doc(m.id).collection("memberships").doc(salonId), { salonName: name }, { merge: true });
      }
      tx.set(db.collection("salonOwners").doc(caller.uid), { salonName: name }, { merge: true });
    }
    auditInTx(tx, { action: "SALON_PROFILE_UPDATED", actorUid: caller.uid, salonId, data: { nameChanged } });
  });
  return { ok: true };
});

interface PhotoRef {
  existingPath?: unknown;
  pendingPath?: unknown;
}

interface UpdateMediaReq {
  salonId?: unknown;
  /** Yeni logo (geçici alana yüklenmiş). Verilmezse logo değişmez. */
  logoPendingPath?: unknown;
  /** Galerinin yeni, sıralı hali (≤5): mevcut yol veya yeni yüklenmiş geçici yol. */
  photos?: unknown;
}

/**
 * Logo ve galeri güncelleme. Logo zorunludur (silinemez, yalnızca değiştirilir).
 * Galeriden çıkarılan dosyalar ve eski logo, güncelleme başarılı olduktan sonra silinir.
 */
export const updateSalonMedia = authedCallable<UpdateMediaReq, { logoUrl: string; photos: StoredImage[] }>(
  "updateSalonMedia",
  async (data, caller) => {
    const salonId = docId(data.salonId, "salonId");
    const salon = await requireOwnedSalon(salonId, caller);
    if (!Array.isArray(data.photos)) fail("invalid-argument", "INVALID_ARGUMENT", { field: "photos" });
    const refs = data.photos as PhotoRef[];
    if (refs.length > MAX_SALON_PHOTOS) fail("invalid-argument", "TOO_MANY_PHOTOS");

    const currentPhotos = (salon.get("photos") as StoredImage[] | undefined) ?? [];
    const currentByPath = new Map(currentPhotos.map((p) => [p.path, p]));
    const stamp = Date.now();
    const created: string[] = [];
    const pendingToDelete: string[] = [];

    try {
      const photos: StoredImage[] = [];
      for (let i = 0; i < refs.length; i++) {
        const r = refs[i] ?? {};
        if (typeof r.existingPath === "string") {
          const existing = currentByPath.get(r.existingPath);
          if (!existing) fail("invalid-argument", "INVALID_IMAGE", { field: `photos[${i}]` });
          photos.push(existing);
        } else {
          const pending = await assertPendingImage(caller.uid, r.pendingPath, `photos[${i}]`);
          const img = await promoteImage(pending, `salons/${salonId}/photos/photo_${stamp}_${i}.jpg`);
          created.push(img.path);
          pendingToDelete.push(pending);
          photos.push(img);
        }
      }
      if (new Set(photos.map((p) => p.path)).size !== photos.length) fail("invalid-argument", "INVALID_IMAGE");

      let logo: StoredImage = { path: salon.get("logoPath"), url: salon.get("logoUrl") };
      if (data.logoPendingPath !== undefined && data.logoPendingPath !== null) {
        const pending = await assertPendingImage(caller.uid, data.logoPendingPath, "logo");
        logo = await promoteImage(pending, `salons/${salonId}/logo/logo_${stamp}.jpg`);
        created.push(logo.path);
        pendingToDelete.push(pending);
      }

      await db.runTransaction(async (tx) => {
        const ref = db.collection("salons").doc(salonId);
        const fresh = await tx.get(ref);
        // Başka bir cihazdan eşzamanlı güncelleme olduysa eski listeye göre karar vermeyelim.
        if (fresh.get("logoPath") !== salon.get("logoPath") || JSON.stringify(fresh.get("photos")) !== JSON.stringify(currentPhotos)) {
          fail("aborted", "STATE_CHANGED");
        }
        tx.update(ref, {
          logoUrl: logo.url,
          logoPath: logo.path,
          photos: photos.map((p) => ({ url: p.url, path: p.path })),
          updatedAt: FieldValue.serverTimestamp(),
        });
        auditInTx(tx, { action: "SALON_MEDIA_UPDATED", actorUid: caller.uid, salonId, data: { photos: photos.length } });
      });

      const keep = new Set([logo.path, ...photos.map((p) => p.path)]);
      const removed = [salon.get("logoPath") as string, ...currentPhotos.map((p) => p.path)].filter((p) => p && !keep.has(p));
      await deleteQuietly([...removed, ...pendingToDelete]);
      return { logoUrl: logo.url, photos };
    } catch (e) {
      await deleteQuietly(created);
      throw e;
    }
  },
);
