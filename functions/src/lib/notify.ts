import { Transaction } from "firebase-admin/firestore";
import { db, FieldValue } from "./admin";

/**
 * Uygulama içi bildirim kutusu kaydı (her zaman yazılır). Push (FCM) Aşama 6'da bu kayıttan tetiklenir.
 * İçerik kilit ekranına düşebileceği için müşteri adı/telefonu gibi kişisel veri içermez.
 */
export interface InboxItem {
  type: string;
  title: string;
  body: string;
  salonId?: string;
  appointmentId?: string;
}

export function notifyInTx(tx: Transaction, uid: string, item: InboxItem): void {
  const ref = db.collection("users").doc(uid).collection("notifications").doc();
  tx.create(ref, { ...item, read: false, createdAt: FieldValue.serverTimestamp() });
}
