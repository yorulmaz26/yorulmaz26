import { Transaction } from "firebase-admin/firestore";
import { db, FieldValue } from "./admin";

export interface AuditEntry {
  action: string;
  actorUid: string;
  salonId?: string;
  targetId?: string;
  data?: Record<string, unknown>;
}

export function auditInTx(tx: Transaction, entry: AuditEntry): void {
  tx.create(db.collection("auditLogs").doc(), { ...entry, at: FieldValue.serverTimestamp() });
}

export async function audit(entry: AuditEntry): Promise<void> {
  await db.collection("auditLogs").add({ ...entry, at: FieldValue.serverTimestamp() });
}
