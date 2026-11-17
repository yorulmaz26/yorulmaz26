/**
 * YALNIZCA TEST PROJESİ: Bir randevuyu zaman kuralını beklemeden "Geldi" (IN_PROGRESS) durumuna getirir.
 * Böylece "İşlemi Tamamla" ve puanlama akışı hemen denenebilir. Tamamlama ve ledger uygulamadan yapılır.
 * Kullanım: node scripts/testMarkArrived.js [KC-XXXXXXXX]   (boşsa en son bekleyen/onaylı randevu)
 * Üretim projesinde ÇALIŞTIRMAYIN.
 */
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

async function main() {
  const number = (process.argv[2] || "").trim().toUpperCase();
  initializeApp();
  const db = getFirestore();
  let q = db.collection("appointments");
  q = number ? q.where("number", "==", number) : q.where("status", "in", ["PENDING", "CONFIRMED"]);
  const snap = await q.get();
  if (snap.empty) throw new Error(number ? `Randevu bulunamadı: ${number}` : "Bekleyen/onaylı randevu yok");
  const doc = snap.docs.sort((a, b) => (b.get("createdAt")?.toMillis?.() ?? 0) - (a.get("createdAt")?.toMillis?.() ?? 0))[0];

  await db.runTransaction(async (tx) => {
    const a = await tx.get(doc.ref);
    const status = a.get("status");
    if (!["PENDING", "CONFIRMED"].includes(status)) throw new Error(`Randevu durumu uygun değil: ${status}`);
    const date = a.get("localDate");
    const lockRefs = [db.collection("staffDayLocks").doc(`${a.get("staffUid")}_${date}`)];
    if (a.get("personLock") === true && a.get("customerUid")) lockRefs.push(db.collection("staffDayLocks").doc(`${a.get("customerUid")}_${date}`));
    const custRef = a.get("customerUid") ? db.collection("customerLocks").doc(a.get("customerUid")) : null;
    const locks = await Promise.all(lockRefs.map((r) => tx.get(r)));
    const cust = custRef ? await tx.get(custRef) : null;
    // PENDING ise önce onaylanmış sayılır: kilitlerdeki son kullanma kaldırılır (süre aşımına düşmesin).
    for (const l of locks) {
      if (!l.exists) continue;
      tx.update(l.ref, { intervals: (l.get("intervals") || []).map((i) => (i.appointmentId === a.id ? { ...i, expiresAtMs: null } : i)) });
    }
    if (cust && cust.exists) {
      tx.update(cust.ref, { active: (cust.get("active") || []).map((e) => (e.appointmentId === a.id ? { ...e, expiresAtMs: null } : e)) });
    }
    tx.update(a.ref, {
      status: "IN_PROGRESS",
      ...(status === "PENDING" ? { confirmedAt: FieldValue.serverTimestamp() } : {}),
      arrivedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    tx.create(a.ref.collection("events").doc(), {
      from: status, to: "IN_PROGRESS", actorUid: "test-tool", actorRole: "SYSTEM", reason: "Test aracı: Geldi", at: FieldValue.serverTimestamp(),
    });
  });
  console.log(`Tamam: ${doc.get("number")} (${doc.get("salonName")}, ${doc.get("customerName")}) → Geldi / İşlemde`);
}

main().catch((e) => { console.error("HATA:", e.message); process.exit(1); });
