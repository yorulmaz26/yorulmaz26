import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";
import { getMessaging } from "firebase-admin/messaging";
import { db, FieldValue } from "../lib/admin";

const INVALID_TOKEN_CODES = new Set(["messaging/registration-token-not-registered", "messaging/invalid-registration-token", "messaging/invalid-argument"]);

/**
 * Uygulama içi kutuya yazılan her bildirim için kullanıcının cihazlarına FCM gönderir.
 * - Yalnızca "data" mesajı (uygulama her durumda kendi bildirimini oluşturur ve doğru ekrana deep link verir).
 * - En az bir kez çalışan tetikleyiciye karşı idempotent: push.claimedAt transaction ile tek sefer alınır.
 * - Geçersiz token'lar silinir. İçerik kişisel veri (müşteri adı/telefonu) içermez.
 */
export const pushOnNotification = onDocumentCreated(
  { document: "users/{uid}/notifications/{notificationId}", memory: "256MiB", retry: false },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const uid = event.params.uid;
    const claimed = await db.runTransaction(async (tx) => {
      const fresh = await tx.get(snap.ref);
      if (fresh.get("push.claimedAt")) return false;
      tx.update(snap.ref, { "push.claimedAt": FieldValue.serverTimestamp() });
      return true;
    });
    if (!claimed) return;

    const devices = await db.collection("users").doc(uid).collection("devices").get();
    if (devices.empty) {
      await snap.ref.update({ "push.result": "NO_DEVICE" });
      return;
    }
    const d = snap.data();
    const res = await getMessaging().sendEachForMulticast({
      tokens: devices.docs.map((x) => x.get("token") as string),
      data: {
        type: String(d.type ?? ""),
        title: String(d.title ?? ""),
        body: String(d.body ?? ""),
        notificationId: snap.id,
        appointmentId: String(d.appointmentId ?? ""),
        salonId: String(d.salonId ?? ""),
      },
      android: { priority: "high", ttl: 24 * 3600 * 1000 },
    });
    const stale = res.responses
      .map((r, i) => (!r.success && r.error && INVALID_TOKEN_CODES.has(r.error.code) ? devices.docs[i].ref : null))
      .filter((r): r is FirebaseFirestore.DocumentReference => r !== null);
    await Promise.all(stale.map((r) => r.delete()));
    await snap.ref.update({ "push.result": res.successCount > 0 ? "SENT" : "FAILED", "push.successCount": res.successCount });
    if (res.failureCount > 0) logger.info("push failures", { uid, failures: res.failureCount, removed: stale.length });
  },
);
