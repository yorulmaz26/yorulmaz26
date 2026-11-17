import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { readFileSync } from "fs";
import { resolve } from "path";
import {
  collection, collectionGroup, doc, getDoc, getDocs, query, serverTimestamp, setDoc, updateDoc, where,
  setLogLevel,
} from "firebase/firestore";

setLogLevel("error"); // beklenen PERMISSION_DENIED uyarılarını gizle

let env: RulesTestEnvironment;

const OWNER = "owner1";
const STAFF = "staff1";
const OTHER_STAFF = "staff2";
const CUSTOMER = "cust1";
const STRANGER = "cust2";
const SALON = "salonA";

const salon = {
  name: "Işıltı Kuaför", nameLower: "isilti kuafor", type: "UNISEX_HAIRDRESSER",
  businessPhoneE164: "+902125556677", city: "İstanbul", district: "Kadıköy", addressLine: "Moda Cd. 1",
  description: "", location: null, logoUrl: "https://x/logo.jpg", logoPath: "salons/salonA/logo/l.jpg", photos: [],
  ownerUid: OWNER, ownerDisplayName: "Ayşe Yılmaz", code: "482913", status: "ACTIVE",
  ratingSum: 0, ratingCount: 0, ratingAvg: null, weeklyClosedDays: [7], timezone: "Europe/Istanbul",
  createdAt: new Date(), updatedAt: new Date(),
};

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-kuaforum",
    firestore: { rules: readFileSync(resolve(__dirname, "../../firestore.rules"), "utf8") },
  });
});
afterAll(async () => env.cleanup());

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "salons", SALON), salon);
    await setDoc(doc(db, "salons", SALON, "members", OWNER), { uid: OWNER, role: "OWNER", status: "ACTIVE" });
    await setDoc(doc(db, "salons", SALON, "members", STAFF), { uid: STAFF, role: "STAFF", status: "ACTIVE" });
    await setDoc(doc(db, "salons", SALON, "members", OTHER_STAFF), { uid: OTHER_STAFF, role: "STAFF", status: "INACTIVE" });
    await setDoc(doc(db, "appointments", "ap1"), {
      salonId: SALON, staffUid: STAFF, customerUid: CUSTOMER, status: "PENDING", totalKurus: 40000,
      customerPhoneE164: "+905321234567",
    });
    await setDoc(doc(db, "users", CUSTOMER), { firstName: "Ali", lastName: "Kaya", phoneE164: "+905321234567" });
    await setDoc(doc(db, "salons", SALON, "invitations", "inv1"), {
      phoneE164: "+905551112233", status: "PENDING", firstName: "Can", lastName: "Er", specialty: "Berber",
    });
    await setDoc(doc(db, "salonCodes", "482913"), { salonId: SALON });
    await setDoc(doc(db, "financialLedger", "completion_apX"), { salonId: SALON, staffUid: STAFF, amountKurus: 40000 });
    await setDoc(doc(db, "salons", SALON, "services", "svcStaff"), {
      staffUid: STAFF, salonId: SALON, name: "Kesim", nameLower: "kesim", description: "", priceKurus: 40000,
      durationMinutes: 30, active: true, deleted: false, createdAt: new Date(), updatedAt: new Date(),
    });
  });
});

const db = (uid: string | null, phone?: string) =>
  uid ? env.authenticatedContext(uid, phone ? { phone_number: phone } : {}).firestore() : env.unauthenticatedContext().firestore();

describe("randevular", () => {
  test("müşteri kendi randevusunu okur, başkası okuyamaz", async () => {
    await assertSucceeds(getDoc(doc(db(CUSTOMER), "appointments", "ap1")));
    await assertSucceeds(getDoc(doc(db(STAFF), "appointments", "ap1")));
    await assertSucceeds(getDoc(doc(db(OWNER), "appointments", "ap1")));
    await assertFails(getDoc(doc(db(STRANGER), "appointments", "ap1")));
    await assertFails(getDoc(doc(db(OTHER_STAFF), "appointments", "ap1")));
    await assertFails(getDoc(doc(db(null), "appointments", "ap1")));
  });
  test("istemci randevu durumunu değiştiremez veya randevu yaratamaz", async () => {
    await assertFails(updateDoc(doc(db(STAFF), "appointments", "ap1"), { status: "CONFIRMED" }));
    await assertFails(updateDoc(doc(db(CUSTOMER), "appointments", "ap1"), { totalKurus: 1 }));
    await assertFails(setDoc(doc(db(CUSTOMER), "appointments", "new"), { salonId: SALON, customerUid: CUSTOMER }));
  });
  test("sorgu: müşteri yalnızca kendi filtresiyle listeler", async () => {
    await assertSucceeds(getDocs(query(collection(db(CUSTOMER), "appointments"), where("customerUid", "==", CUSTOMER))));
    await assertFails(getDocs(collection(db(CUSTOMER), "appointments")));
    await assertSucceeds(getDocs(query(collection(db(OWNER), "appointments"), where("salonId", "==", SALON))));
    await assertFails(getDocs(query(collection(db(STAFF), "appointments"), where("salonId", "==", SALON))));
  });
});

describe("salon", () => {
  test("sahip bile salonu doğrudan güncelleyemez (yalnızca fonksiyonlar)", async () => {
    await assertFails(updateDoc(doc(db(OWNER), "salons", SALON), {
      description: "Yeni açıklama", updatedAt: serverTimestamp(),
    }));
  });
  test("sahip sahipliği, kodu, puanı değiştiremez", async () => {
    await assertFails(updateDoc(doc(db(OWNER), "salons", SALON), { ownerUid: STRANGER, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db(OWNER), "salons", SALON), { code: "111111", updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db(OWNER), "salons", SALON), { ratingAvg: 5, updatedAt: serverTimestamp() }));
  });
  test("personel salon ayarlarını değiştiremez", async () => {
    await assertFails(updateDoc(doc(db(STAFF), "salons", SALON), { description: "x", updatedAt: serverTimestamp() }));
  });
  test("istemci üyelik yaratamaz (kod bilmek yetki vermez)", async () => {
    await assertFails(setDoc(doc(db(STRANGER), "salons", SALON, "members", STRANGER), { uid: STRANGER, role: "STAFF", status: "ACTIVE" }));
  });
  test("salon kodu tek tek okunur, listelenemez", async () => {
    await assertSucceeds(getDoc(doc(db(STRANGER), "salonCodes", "482913")));
    await assertFails(getDocs(collection(db(STRANGER), "salonCodes")));
  });
});

describe("davetler", () => {
  test("yalnızca sahip ve doğrulanmış telefonlu davetli okur", async () => {
    await assertSucceeds(getDoc(doc(db(OWNER), "salons", SALON, "invitations", "inv1")));
    await assertSucceeds(getDoc(doc(db("newbie", "+905551112233"), "salons", SALON, "invitations", "inv1")));
    await assertFails(getDoc(doc(db(STAFF), "salons", SALON, "invitations", "inv1")));
    await assertFails(getDoc(doc(db("other", "+905559998877"), "salons", SALON, "invitations", "inv1")));
  });
  test("davetli kendi davetlerini collection-group ile bulur", async () => {
    const q = query(collectionGroup(db("newbie", "+905551112233"), "invitations"), where("phoneE164", "==", "+905551112233"));
    await assertSucceeds(getDocs(q));
  });
});

describe("hizmetler", () => {
  const svc = (staffUid: string) => ({
    staffUid, salonId: SALON, name: "Fön", nameLower: "fon", description: "", priceKurus: 15000,
    durationMinutes: 15, active: true, deleted: false, createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
  });
  test("aktif üye kendi hizmetini oluşturur", async () => {
    await assertSucceeds(setDoc(doc(db(STAFF), "salons", SALON, "services", "s1"), svc(STAFF)));
  });
  test("başkası adına, pasif üye veya geçersiz fiyatla oluşturamaz", async () => {
    await assertFails(setDoc(doc(db(STAFF), "salons", SALON, "services", "s2"), svc(OWNER)));
    await assertFails(setDoc(doc(db(OTHER_STAFF), "salons", SALON, "services", "s3"), svc(OTHER_STAFF)));
    await assertFails(setDoc(doc(db(STAFF), "salons", SALON, "services", "s4"), { ...svc(STAFF), priceKurus: 0 }));
    await assertFails(setDoc(doc(db(STAFF), "salons", SALON, "services", "s5"), { ...svc(STAFF), durationMinutes: 7 }));
  });
  test("başka personelin hizmetini değiştiremez; sahibi de değiştiremez", async () => {
    await assertFails(updateDoc(doc(db(OWNER), "salons", SALON, "services", "svcStaff"), { priceKurus: 1000, updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(db(STAFF), "salons", SALON, "services", "svcStaff"), { priceKurus: 45000, updatedAt: serverTimestamp() }));
  });
  test("silme yumuşak silmedir", async () => {
    await assertSucceeds(updateDoc(doc(db(STAFF), "salons", SALON, "services", "svcStaff"), {
      deleted: true, active: false, updatedAt: serverTimestamp(),
    }));
  });
});

describe("finans ve kullanıcı", () => {
  test("ledger: sahip ve ilgili uzman okur, müşteri okuyamaz, kimse yazamaz", async () => {
    await assertSucceeds(getDoc(doc(db(OWNER), "financialLedger", "completion_apX")));
    await assertSucceeds(getDoc(doc(db(STAFF), "financialLedger", "completion_apX")));
    await assertFails(getDoc(doc(db(CUSTOMER), "financialLedger", "completion_apX")));
    await assertFails(setDoc(doc(db(OWNER), "financialLedger", "x"), { salonId: SALON, amountKurus: 1 }));
  });
  test("kullanıcı yalnızca kendi profilini okur ve yalnızca adını değiştirir", async () => {
    await assertSucceeds(getDoc(doc(db(CUSTOMER), "users", CUSTOMER)));
    await assertFails(getDoc(doc(db(STRANGER), "users", CUSTOMER)));
    await assertSucceeds(updateDoc(doc(db(CUSTOMER), "users", CUSTOMER), { firstName: "Veli", updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db(CUSTOMER), "users", CUSTOMER), { phoneE164: "+905000000000", updatedAt: serverTimestamp() }));
  });
  test("rapor sayaçları: sahip salon toplamını, uzman yalnızca kendi satırını okur", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "reportAggregates", "a_ALL"), { salonId: SALON, staffUid: null, amountKurus: 1 });
      await setDoc(doc(ctx.firestore(), "reportAggregates", "a_staff"), { salonId: SALON, staffUid: STAFF, amountKurus: 1 });
      await setDoc(doc(ctx.firestore(), "reportAggregates", "a_other"), { salonId: SALON, staffUid: OTHER_STAFF, amountKurus: 1 });
    });
    await assertSucceeds(getDoc(doc(db(OWNER), "reportAggregates", "a_ALL")));
    await assertFails(getDoc(doc(db(STAFF), "reportAggregates", "a_ALL")));
    await assertSucceeds(getDoc(doc(db(STAFF), "reportAggregates", "a_staff")));
    await assertFails(getDoc(doc(db(STAFF), "reportAggregates", "a_other")));
    await assertFails(setDoc(doc(db(OWNER), "reportAggregates", "a_ALL"), { salonId: SALON, amountKurus: 999 }));
  });
  test("kullanıcı yalnızca kendi üyelik aynasını okur, yazamaz", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "users", STAFF, "memberships", SALON), { salonId: SALON, role: "STAFF", status: "ACTIVE" });
    });
    await assertSucceeds(getDocs(collection(db(STAFF), "users", STAFF, "memberships")));
    await assertFails(getDocs(collection(db(CUSTOMER), "users", STAFF, "memberships")));
    await assertFails(setDoc(doc(db(STAFF), "users", STAFF, "memberships", "x"), { role: "OWNER", status: "ACTIVE" }));
  });
  test("sahip davet listesini okur; personel okuyamaz", async () => {
    await assertSucceeds(getDocs(collection(db(OWNER), "salons", SALON, "invitations")));
    await assertFails(getDocs(collection(db(STAFF), "salons", SALON, "invitations")));
  });
  test("değerlendirmeler herkese açık okunur ama istemci yazamaz; müşteri kimliği gizli koleksiyonda", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "reviews", "ap1"), { salonId: SALON, salonRating: 5 });
      await setDoc(doc(ctx.firestore(), "reviewsPrivate", "ap1"), { customerUid: CUSTOMER });
    });
    await assertSucceeds(getDoc(doc(db(STRANGER), "reviews", "ap1")));
    await assertFails(setDoc(doc(db(CUSTOMER), "reviews", "ap2"), { salonId: SALON, salonRating: 5 }));
    await assertFails(getDoc(doc(db(OWNER), "reviewsPrivate", "ap1")));
  });
  test("gizlenmiş yorum ve şikâyet ayrıntıları yalnızca platform yöneticisine açık", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "reviews", "gizli"), { salonId: SALON, hidden: true, salonRating: 1 });
      await setDoc(doc(ctx.firestore(), "reviewReports", "gizli_x"), { reviewId: "gizli", reason: "SPAM" });
    });
    const admin = env.authenticatedContext("admin", { admin: true }).firestore();
    await assertFails(getDoc(doc(db(STRANGER), "reviews", "gizli")));
    await assertFails(getDoc(doc(db(OWNER), "reviews", "gizli")));
    await assertSucceeds(getDoc(doc(admin, "reviews", "gizli")));
    await assertFails(getDoc(doc(db(OWNER), "reviewReports", "gizli_x")));
    await assertSucceeds(getDoc(doc(admin, "reviewReports", "gizli_x")));
    await assertFails(setDoc(doc(admin, "reviews", "gizli"), { hidden: false }));
  });
  test("moderatör listesi yalnızca moderatörlere açık", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => { await setDoc(doc(ctx.firestore(), "platformAdmins", "a1"), { phoneE164: "+905550000001" }); });
    await assertFails(getDoc(doc(db(OWNER), "platformAdmins", "a1")));
    await assertSucceeds(getDoc(doc(env.authenticatedContext("admin", { admin: true }).firestore(), "platformAdmins", "a1")));
  });
  test("kilit koleksiyonları istemciye kapalı", async () => {
    await assertFails(getDoc(doc(db(OWNER), "salonOwners", OWNER)));
    await assertFails(getDoc(doc(db(CUSTOMER), "customerLocks", CUSTOMER)));
    await assertFails(setDoc(doc(db(STAFF), "staffDayLocks", "x"), { intervals: [] }));
  });
  test("favoriler: yalnızca kendisi ekler/siler/okur; alan doğrulaması", async () => {
    const fav = { salonId: SALON, salonName: "Işıltı", city: "İstanbul", district: "Kadıköy", createdAt: serverTimestamp() };
    await assertSucceeds(setDoc(doc(db(CUSTOMER), "users", CUSTOMER, "favorites", SALON), fav));
    await assertFails(setDoc(doc(db(STRANGER), "users", CUSTOMER, "favorites", "x"), { ...fav, salonId: "x" }));
    await assertFails(setDoc(doc(db(CUSTOMER), "users", CUSTOMER, "favorites", "y"), { ...fav, salonId: "baska" }));
    await assertFails(setDoc(doc(db(CUSTOMER), "users", CUSTOMER, "favorites", "z"), { ...fav, salonId: "z", extra: 1 }));
    await assertSucceeds(getDocs(collection(db(CUSTOMER), "users", CUSTOMER, "favorites")));
    await assertFails(getDocs(collection(db(STRANGER), "users", CUSTOMER, "favorites")));
  });
  test("prim oranı, müşteri istatistiği ve indirim kodları rol bazlı okunur; istemci yazamaz", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const f = ctx.firestore();
      await setDoc(doc(f, "salons", SALON, "staffPrivate", STAFF), { commissionPercent: 40 });
      await setDoc(doc(f, "salons", SALON, "customers", CUSTOMER), { completedCount: 2, noShowCount: 1 });
      await setDoc(doc(f, "salons", SALON, "discountCodes", "YAZ20"), { percent: 20, active: true });
      await setDoc(doc(f, "waitlist", "w1"), { customerUid: CUSTOMER, staffUid: STAFF });
    });
    await assertSucceeds(getDoc(doc(db(STAFF), "salons", SALON, "staffPrivate", STAFF)));
    await assertSucceeds(getDoc(doc(db(OWNER), "salons", SALON, "staffPrivate", STAFF)));
    await assertFails(getDoc(doc(db(CUSTOMER), "salons", SALON, "staffPrivate", STAFF)));
    await assertSucceeds(getDoc(doc(db(CUSTOMER), "salons", SALON, "customers", CUSTOMER)));
    await assertSucceeds(getDoc(doc(db(STAFF), "salons", SALON, "customers", CUSTOMER)));
    await assertFails(getDoc(doc(db(OTHER_STAFF), "salons", SALON, "customers", CUSTOMER))); // pasif uzman
    await assertFails(getDoc(doc(db(STRANGER), "salons", SALON, "customers", CUSTOMER)));
    await assertFails(getDoc(doc(db(CUSTOMER), "salons", SALON, "discountCodes", "YAZ20")));
    await assertSucceeds(getDoc(doc(db(OWNER), "salons", SALON, "discountCodes", "YAZ20")));
    await assertFails(setDoc(doc(db(OWNER), "salons", SALON, "discountCodes", "YENI"), { percent: 90 }));
    await assertSucceeds(getDoc(doc(db(CUSTOMER), "waitlist", "w1")));
    await assertFails(getDoc(doc(db(STRANGER), "waitlist", "w1")));
    await assertFails(getDoc(doc(db(CUSTOMER), "calendarTokens", "t")));
    await assertFails(getDoc(doc(db(CUSTOMER), "discountCodeUses", "u")));
  });
});
