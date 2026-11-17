import { randomInt } from "crypto";
import { authedCallable } from "../lib/callable";
import { db, FieldValue } from "../lib/admin";
import { fail } from "../lib/errors";
import { consents, ConsentInput, idempotencyKey, optString, personName, reqString } from "../lib/validation";
import { withIdempotency } from "../lib/idempotency";
import { enforceRateLimit } from "../lib/rateLimit";
import { auditInTx } from "../lib/audit";
import { assertPendingImage, deleteQuietly, promoteImage, StoredImage } from "../lib/storageFiles";
import { generateSalonCode, isValidCityDistrict, normalizeTrPhone, searchKey, SALON_CODE_MAX_ATTEMPTS } from "../domain";
import { DEFAULT_BOOKING_SETTINGS, defaultWeeklySchedule, MAX_SALON_PHOTOS, SALON_TYPES, SalonType } from "./defaults";

interface RegisterSalonOwnerReq {
  firstName?: unknown;
  lastName?: unknown;
  consents?: ConsentInput;
  salon?: {
    name?: unknown;
    businessPhone?: unknown;
    type?: unknown;
    city?: unknown;
    district?: unknown;
    addressLine?: unknown;
    description?: unknown;
  };
  logoPath?: unknown;
  photoPaths?: unknown;
  idempotencyKey?: unknown;
}

export interface RegisterSalonOwnerRes {
  salonId: string;
  code: string;
  salonName: string;
}

/** Testlerde kod çakışmasını zorlamak için değiştirilebilir rastgele kaynak. */
export const codeRandom = { next: (): number => randomInt(0, 1_000_000) / 1_000_000 };

/**
 * Salon sahibi kaydı — tek transaction'da: benzersiz 6 haneli kod rezervasyonu, salon,
 * OWNER üyeliği, varsayılan program, kullanıcı profili, üyelik aynası ve sahiplik kilidi.
 * v1: bir kullanıcı en fazla bir salonun sahibi olabilir (salonOwners/{uid}).
 */
export const registerSalonOwner = authedCallable<RegisterSalonOwnerReq, RegisterSalonOwnerRes>(
  "registerSalonOwner",
  async (data, caller) => {
    if (!caller.phone) fail("failed-precondition", "PHONE_NOT_VERIFIED");
    const key = idempotencyKey(data.idempotencyKey);
    const firstName = personName(data.firstName, "firstName");
    const lastName = personName(data.lastName, "lastName");
    const c = consents(data.consents);
    const s = data.salon ?? {};
    const name = reqString(s.name, "salon.name", 2, 80);
    const businessPhone = normalizeTrPhone(String(s.businessPhone ?? ""));
    if (!businessPhone) fail("invalid-argument", "INVALID_PHONE", { field: "salon.businessPhone" });
    if (!SALON_TYPES.includes(s.type as SalonType)) fail("invalid-argument", "INVALID_ARGUMENT", { field: "salon.type" });
    const city = reqString(s.city, "salon.city", 2, 40);
    const district = reqString(s.district, "salon.district", 2, 60);
    if (!isValidCityDistrict(city, district)) fail("invalid-argument", "INVALID_LOCATION");
    const addressLine = reqString(s.addressLine, "salon.addressLine", 5, 300);
    const description = optString(s.description, "salon.description", 1000);
    const photoPaths = Array.isArray(data.photoPaths) ? data.photoPaths : [];
    if (photoPaths.length > MAX_SALON_PHOTOS) fail("invalid-argument", "TOO_MANY_PHOTOS");

    return withIdempotency(caller.uid, key, "registerSalonOwner", async () => {
      const ownerLock = db.collection("salonOwners").doc(caller.uid);
      const existing = await ownerLock.get();
      if (existing.exists) {
        return { salonId: existing.get("salonId"), code: existing.get("code"), salonName: existing.get("salonName") };
      }
      await enforceRateLimit(`registerSalon_${caller.uid}`, 5, 3600);

      const logoPath = await assertPendingImage(caller.uid, data.logoPath, "logo");
      const pending = [logoPath];
      for (let i = 0; i < photoPaths.length; i++) pending.push(await assertPendingImage(caller.uid, photoPaths[i], `photos[${i}]`));

      const salonRef = db.collection("salons").doc();
      const salonId = salonRef.id;
      const stamp = Date.now();
      const logo = await promoteImage(logoPath, `salons/${salonId}/logo/logo_${stamp}.jpg`);
      const photos: StoredImage[] = [];
      for (let i = 1; i < pending.length; i++) {
        photos.push(await promoteImage(pending[i], `salons/${salonId}/photos/photo_${stamp}_${i}.jpg`));
      }

      let result: RegisterSalonOwnerRes;
      try {
        result = await db.runTransaction(async (tx) => {
          const lock = await tx.get(ownerLock);
          if (lock.exists) {
            return { salonId: lock.get("salonId"), code: lock.get("code"), salonName: lock.get("salonName") };
          }
          const userRef = db.collection("users").doc(caller.uid);
          const userSnap = await tx.get(userRef);

          // Benzersiz kod: aday kodları transaction içinde oku; ilk boş olanı al.
          // Eşzamanlı iki kayıt aynı kodu seçerse Firestore transaction'ı biri için yeniden dener.
          // Çekiliş sayısı sınırlıdır: rastgele kaynak tekrar üretse bile döngü sonlanır.
          const candidates = new Set<string>();
          for (let i = 0; i < SALON_CODE_MAX_ATTEMPTS * 3 && candidates.size < SALON_CODE_MAX_ATTEMPTS; i++) {
            candidates.add(generateSalonCode(codeRandom.next));
          }
          const refs = [...candidates].map((cd) => db.collection("salonCodes").doc(cd));
          const snaps = await tx.getAll(...refs);
          const free = snaps.find((x) => !x.exists);
          if (!free) fail("resource-exhausted", "SALON_CODE_EXHAUSTED");
          const code = free.id;

          const ownerDisplayName = `${firstName} ${lastName}`;
          const now = FieldValue.serverTimestamp();
          tx.create(free.ref, { salonId, createdAt: now });
          tx.create(salonRef, {
            name,
            nameLower: searchKey(name),
            type: s.type,
            businessPhoneE164: businessPhone,
            city,
            district,
            addressLine,
            description,
            location: null,
            logoUrl: logo.url,
            logoPath: logo.path,
            photos: photos.map((p) => ({ url: p.url, path: p.path })),
            ownerUid: caller.uid,
            ownerDisplayName,
            code,
            status: "ACTIVE",
            ratingSum: 0,
            ratingCount: 0,
            ratingAvg: null,
            weeklyClosedDays: [],
            bookingSettings: DEFAULT_BOOKING_SETTINGS,
            timezone: "Europe/Istanbul",
            createdAt: now,
            updatedAt: now,
          });
          const memberRef = salonRef.collection("members").doc(caller.uid);
          tx.create(memberRef, {
            uid: caller.uid,
            salonId,
            role: "OWNER",
            status: "ACTIVE",
            firstName,
            lastName,
            specialty: "Salon Sahibi",
            ratingSum: 0,
            ratingCount: 0,
            ratingAvg: null,
            acceptsBookings: true,
            joinedAt: now,
            createdAt: now,
            updatedAt: now,
          });
          tx.create(memberRef.collection("schedule").doc("weekly"), { ...defaultWeeklySchedule(), version: 1, updatedAt: now });
          if (userSnap.exists) {
            tx.update(userRef, { firstName, lastName, updatedAt: now });
          } else {
            tx.create(userRef, {
              firstName,
              lastName,
              phoneE164: caller.phone,
              consents: { ...c, acceptedAt: now },
              createdAt: now,
              updatedAt: now,
            });
          }
          tx.set(userRef.collection("memberships").doc(salonId), {
            salonId,
            salonName: name,
            salonCode: code,
            role: "OWNER",
            status: "ACTIVE",
            updatedAt: now,
          });
          tx.create(ownerLock, { salonId, code, salonName: name, createdAt: now });
          auditInTx(tx, { action: "SALON_CREATED", actorUid: caller.uid, salonId, data: { code } });
          return { salonId, code, salonName: name };
        });
      } catch (e) {
        await deleteQuietly([logo.path, ...photos.map((p) => p.path)]);
        throw e;
      }
      if (result.salonId !== salonId) await deleteQuietly([logo.path, ...photos.map((p) => p.path)]);
      await deleteQuietly(pending);
      return result;
    });
  },
);
