import { caller, clearFirestore, expectReason, uuid } from "./helpers";
import { bootstrapSession, registerCustomer } from "../src/auth/session";
import { codeRandom, registerSalonOwner } from "../src/salon/registerSalonOwner";
import { LEGAL_VERSIONS } from "../src/lib/validation";
import { bucket, db } from "../src/lib/admin";

const consents = { kvkkVersion: LEGAL_VERSIONS.kvkk, termsVersion: LEGAL_VERSIONS.terms };
const regCustomer = caller(registerCustomer);
const regOwner = caller(registerSalonOwner);
const boot = caller(bootstrapSession);

async function uploadPending(uid: string, name: string, type = "image/jpeg", bytes = 2048) {
  const path = `uploads/${uid}/pending/${name}`;
  await bucket().file(path).save(Buffer.alloc(bytes, 1), { contentType: type });
  return path;
}

const salonInput = (name = "Işıltı Kuaför") => ({
  name,
  businessPhone: "0212 555 66 77",
  type: "UNISEX_HAIRDRESSER",
  city: "İstanbul",
  district: "Kadıköy",
  addressLine: "Caferağa Mah. Moda Cd. No:1",
  description: "Modern salon",
});

beforeEach(clearFirestore);

describe("müşteri kaydı", () => {
  test("doğrulanmış telefon olmadan kayıt olmaz", async () => {
    await expectReason(regCustomer({ firstName: "Ali", lastName: "Kaya", consents, idempotencyKey: uuid() }, "u1"), "PHONE_NOT_VERIFIED");
  });
  test("onay metinleri olmadan kayıt olmaz", async () => {
    await expectReason(
      regCustomer({ firstName: "Ali", lastName: "Kaya", consents: { kvkkVersion: "eski", termsVersion: "eski" }, idempotencyKey: uuid() }, "u1", "+905321112233"),
      "CONSENT_REQUIRED",
    );
  });
  test("kayıt telefonu token'dan alır ve tekrarlanabilir", async () => {
    const r1 = await regCustomer({ firstName: "Ali", lastName: "Kaya", consents, idempotencyKey: uuid() }, "u1", "+905321112233");
    expect(r1.created).toBe(true);
    const r2 = await regCustomer({ firstName: "Ali", lastName: "Kaya", consents, idempotencyKey: uuid() }, "u1", "+905321112233");
    expect(r2.created).toBe(false);
    const u = await db.doc("users/u1").get();
    expect(u.get("phoneE164")).toBe("+905321112233");
    const b = await boot({}, "u1", "+905321112233");
    expect(b.user.exists).toBe(true);
    expect(b.memberships).toEqual([]);
  });
  test("geçersiz isim reddedilir", async () => {
    await expectReason(regCustomer({ firstName: "A1", lastName: "Kaya", consents, idempotencyKey: uuid() }, "u1", "+905321112233"), "INVALID_NAME");
  });
});

describe("salon sahibi kaydı", () => {
  test("atomik kayıt: salon, kod, üyelik, program, profil", async () => {
    const logo = await uploadPending("o1", "logo.jpg");
    const p1 = await uploadPending("o1", "p1.jpg");
    const res = await regOwner(
      { firstName: "Ayşe", lastName: "Yılmaz", consents, salon: salonInput(), logoPath: logo, photoPaths: [p1], idempotencyKey: uuid() },
      "o1",
      "+905301112233",
    );
    expect(res.code).toMatch(/^[1-9]\d{5}$/);
    const salon = await db.doc(`salons/${res.salonId}`).get();
    expect(salon.get("ownerUid")).toBe("o1");
    expect(salon.get("nameLower")).toBe("isilti kuafor");
    expect(salon.get("photos")).toHaveLength(1);
    expect((await db.doc(`salonCodes/${res.code}`).get()).get("salonId")).toBe(res.salonId);
    expect((await db.doc(`salons/${res.salonId}/members/o1`).get()).get("role")).toBe("OWNER");
    expect((await db.doc(`salons/${res.salonId}/members/o1/schedule/weekly`).get()).exists).toBe(true);
    const [pendingExists] = await bucket().file(logo).exists();
    expect(pendingExists).toBe(false);
    const b = await boot({}, "o1", "+905301112233");
    expect(b.memberships).toEqual([{ salonId: res.salonId, salonName: "Işıltı Kuaför", salonCode: res.code, role: "OWNER", status: "ACTIVE" }]);
  });

  test("tekrar gönderim ikinci salon açmaz", async () => {
    const logo = await uploadPending("o1", "logo.jpg");
    const req = { firstName: "Ayşe", lastName: "Yılmaz", consents, salon: salonInput(), logoPath: logo, photoPaths: [], idempotencyKey: uuid() };
    const a = await regOwner(req, "o1", "+905301112233");
    const b = await regOwner({ ...req, idempotencyKey: uuid() }, "o1", "+905301112233");
    expect(b.salonId).toBe(a.salonId);
    expect((await db.collection("salons").get()).size).toBe(1);
  });

  test("logo zorunlu; başkasının yüklemesi ve geçersiz tür reddedilir", async () => {
    const base = { firstName: "Ayşe", lastName: "Yılmaz", consents, salon: salonInput(), photoPaths: [], idempotencyKey: uuid() };
    await expectReason(regOwner({ ...base, logoPath: "uploads/o1/pending/yok.jpg" }, "o1", "+905301112233"), "IMAGE_NOT_UPLOADED");
    const foreign = await uploadPending("baskasi", "logo.jpg");
    await expectReason(regOwner({ ...base, logoPath: foreign }, "o1", "+905301112233"), "INVALID_IMAGE");
    const gif = await uploadPending("o1", "logo.gif", "image/gif");
    await expectReason(regOwner({ ...base, logoPath: gif }, "o1", "+905301112233"), "INVALID_IMAGE");
  });

  test("il/ilçe doğrulanır", async () => {
    const logo = await uploadPending("o1", "logo.jpg");
    await expectReason(
      regOwner(
        { firstName: "Ayşe", lastName: "Yılmaz", consents, salon: { ...salonInput(), district: "Çankaya" }, logoPath: logo, idempotencyKey: uuid() },
        "o1",
        "+905301112233",
      ),
      "INVALID_LOCATION",
    );
  });

  test("kod çakışmasında farklı kod alınır; eşzamanlı kayıtlar benzersiz", async () => {
    const original = codeRandom.next;
    try {
      // İlk adaylar hep aynı koda düşer → ikinci kayıt sıradaki adaya geçmek zorunda.
      let i = 0;
      const seq = [0.5, 0.5, 0.5, 0.6, 0.7, 0.8, 0.9, 0.1, 0.2, 0.3, 0.4];
      codeRandom.next = () => seq[i++ % seq.length];
      const owners = ["a", "b", "c", "d"];
      const results = await Promise.all(
        owners.map(async (o, k) => {
          const logo = await uploadPending(o, "logo.jpg");
          return regOwner(
            { firstName: "Ayşe", lastName: "Yılmaz", consents, salon: salonInput(`Salon ${k}`), logoPath: logo, idempotencyKey: uuid() },
            o,
            `+90530111220${k}`,
          );
        }),
      );
      const codes = results.map((r: { code: string }) => r.code);
      expect(new Set(codes).size).toBe(owners.length);
      expect((await db.collection("salonCodes").get()).size).toBe(owners.length);
    } finally {
      codeRandom.next = original;
    }
  });
});
