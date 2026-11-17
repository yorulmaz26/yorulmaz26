#!/usr/bin/env node
/**
 * Platform yöneticisi (moderasyon) yetkisi verir/geri alır.
 * Kullanım (proje sahibinin bilgisayarında, servis hesabı anahtarıyla):
 *   GOOGLE_APPLICATION_CREDENTIALS=./service-account.json node scripts/setAdmin.js +905321234567
 *   GOOGLE_APPLICATION_CREDENTIALS=./service-account.json node scripts/setAdmin.js <uid> --revoke
 * Emülatörde: FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 GCLOUD_PROJECT=demo-kuaforum node scripts/setAdmin.js <uid>
 * Kullanıcı yeni yetkiyi görmek için uygulamadan çıkıp yeniden giriş yapmalıdır (token yenilenir).
 */
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");

async function main() {
  const [who, flag] = process.argv.slice(2);
  if (!who) {
    console.error("Kullanım: node scripts/setAdmin.js <uid | +90telefon> [--revoke]");
    process.exit(1);
  }
  initializeApp();
  const auth = getAuth();
  const user = who.startsWith("+") ? await auth.getUserByPhoneNumber(who) : await auth.getUser(who);
  const claims = { ...(user.customClaims || {}) };
  if (flag === "--revoke") delete claims.admin;
  else claims.admin = true;
  await auth.setCustomUserClaims(user.uid, claims);
  console.log(`${user.uid} (${user.phoneNumber || "-"}) admin=${claims.admin === true}`);
}

main().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
