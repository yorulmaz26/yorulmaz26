import { caller, clearFirestore, expectReason, uuid } from "./helpers";
import { registerSalonOwner } from "../src/salon/registerSalonOwner";
import { updateSalonMedia, updateSalonProfile } from "../src/salon/updateSalon";
import { acceptInvitation, createStaffInvitation } from "../src/staff/invitations";
import { LEGAL_VERSIONS } from "../src/lib/validation";
import { bucket, db } from "../src/lib/admin";

const consents = { kvkkVersion: LEGAL_VERSIONS.kvkk, termsVersion: LEGAL_VERSIONS.terms };
const updateProfile = caller(updateSalonProfile);
const updateMedia = caller(updateSalonMedia);
const OWNER_PHONE = "+905301110000";
const STAFF_PHONE = "+905551112233";
let salonId = "";

async function pending(uid: string, name: string) {
  const path = `uploads/${uid}/pending/${name}`;
  await bucket().file(path).save(Buffer.alloc(1500, 2), { contentType: "image/jpeg" });
  return path;
}
const exists = async (p: string) => (await bucket().file(p).exists())[0];

const profile = (o: Record<string, unknown> = {}) => ({
  salonId, name: "Yeni Işıltı", type: "WOMEN_HAIRDRESSER", businessPhone: "0216 444 55 66",
  city: "İzmir", district: "Konak", addressLine: "Alsancak Mah. 1", description: "Güncel", location: { lat: 38.43, lng: 27.14 }, ...o,
});

beforeEach(async () => {
  await clearFirestore();
  const r = await caller(registerSalonOwner)(
    {
      firstName: "Ayşe", lastName: "Yılmaz", consents, logoPath: await pending("owner", "l.jpg"),
      photoPaths: [await pending("owner", "p1.jpg"), await pending("owner", "p2.jpg")], idempotencyKey: uuid(),
      salon: { name: "Işıltı", businessPhone: "02125556677", type: "BEAUTY_SALON", city: "Ankara", district: "Çankaya", addressLine: "Kızılay Mah. 1" },
    },
    "owner",
    OWNER_PHONE,
  );
  salonId = r.salonId;
  const inv = await caller(createStaffInvitation)(
    { salonId, firstName: "Can", lastName: "Er", specialty: "Berber", phone: STAFF_PHONE, idempotencyKey: uuid() }, "owner", OWNER_PHONE,
  );
  await caller(acceptInvitation)({ salonId, inviteId: inv.inviteId, consents }, "staff", STAFF_PHONE);
});

test("sahip profili günceller; ad değişikliği üyelik aynalarına yansır", async () => {
  await updateProfile(profile(), "owner", OWNER_PHONE);
  const s = await db.doc(`salons/${salonId}`).get();
  expect(s.get("name")).toBe("Yeni Işıltı");
  expect(s.get("nameLower")).toBe("yeni isilti");
  expect(s.get("businessPhoneE164")).toBe("+902164445566");
  expect(s.get("location")).toEqual({ lat: 38.43, lng: 27.14 });
  expect((await db.doc(`users/staff/memberships/${salonId}`).get()).get("salonName")).toBe("Yeni Işıltı");
  expect((await db.doc(`users/owner/memberships/${salonId}`).get()).get("salonName")).toBe("Yeni Işıltı");
  expect(s.get("code")).toMatch(/^\d{6}$/); // kod değişmez
});

test("personel ve yabancı salon ayarlarını değiştiremez; geçersiz veri reddedilir", async () => {
  await expectReason(updateProfile(profile(), "staff", STAFF_PHONE), "PERMISSION_DENIED");
  await expectReason(updateProfile(profile({ district: "Kadıköy" }), "owner", OWNER_PHONE), "INVALID_LOCATION");
  await expectReason(updateProfile(profile({ location: { lat: 48.8, lng: 2.3 } }), "owner", OWNER_PHONE), "INVALID_COORDINATES");
  await expectReason(updateProfile(profile({ businessPhone: "123" }), "owner", OWNER_PHONE), "INVALID_PHONE");
});

test("galeri: sıralama, ekleme, çıkarma ve logo değişimi; eski dosyalar silinir", async () => {
  const before = await db.doc(`salons/${salonId}`).get();
  const [p1, p2] = before.get("photos") as { path: string }[];
  const oldLogo = before.get("logoPath");
  const newPhoto = await pending("owner", "p3.jpg");
  const newLogo = await pending("owner", "l2.jpg");
  const res = await updateMedia(
    { salonId, logoPendingPath: newLogo, photos: [{ pendingPath: newPhoto }, { existingPath: p2.path }] },
    "owner",
    OWNER_PHONE,
  );
  expect(res.photos).toHaveLength(2);
  const after = await db.doc(`salons/${salonId}`).get();
  expect((after.get("photos") as { path: string }[])[1].path).toBe(p2.path);
  expect(after.get("logoPath")).not.toBe(oldLogo);
  expect(await exists(p1.path)).toBe(false);
  expect(await exists(oldLogo)).toBe(false);
  expect(await exists(p2.path)).toBe(true);
  expect(await exists(newPhoto)).toBe(false); // geçici dosya temizlendi
});

test("galeri sınırları ve yetki", async () => {
  const photos = await Promise.all([1, 2, 3, 4, 5, 6].map(async (i) => ({ pendingPath: await pending("owner", `x${i}.jpg`) })));
  await expectReason(updateMedia({ salonId, photos }, "owner", OWNER_PHONE), "TOO_MANY_PHOTOS");
  await expectReason(updateMedia({ salonId, photos: [] }, "staff", STAFF_PHONE), "PERMISSION_DENIED");
  await expectReason(updateMedia({ salonId, photos: [{ existingPath: "salons/baska/photos/x.jpg" }] }, "owner", OWNER_PHONE), "INVALID_IMAGE");
  // Tüm fotoğrafları kaldırmak serbest; logo kalır.
  await updateMedia({ salonId, photos: [] }, "owner", OWNER_PHONE);
  const s = await db.doc(`salons/${salonId}`).get();
  expect(s.get("photos")).toEqual([]);
  expect(await exists(s.get("logoPath"))).toBe(true);
});
