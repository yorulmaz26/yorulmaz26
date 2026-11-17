import type { ActorRole } from "./statusMachine";

export interface AppointmentParties {
  salonOwnerUid: string;
  staffUid: string;
  customerUid: string;
}

/**
 * Çağıranın bir randevu üzerindeki rolü. Sahip aynı zamanda atanmış uzman olabilir;
 * bu durumda OWNER döner (daha geniş yetki). Müşteri kendi salonunda da müşteri olabilir:
 * müşteri eylemleri için ayrıca customerUid denetlenir.
 */
export function actorRoleFor(callerUid: string, p: AppointmentParties, wantsCustomerAction: boolean): ActorRole | null {
  if (wantsCustomerAction) return callerUid === p.customerUid ? "CUSTOMER" : null;
  if (callerUid === p.salonOwnerUid) return "OWNER";
  if (callerUid === p.staffUid) return "STAFF";
  return null;
}

export type ReportScope = { kind: "SALON" } | { kind: "STAFF"; staffUid: string };

/** Rapor erişimi: sahip her şeyi; personel yalnızca kendi kırılımını görür. */
export function canReadReport(callerUid: string, salonOwnerUid: string, isActiveStaff: boolean, scope: ReportScope): boolean {
  if (callerUid === salonOwnerUid) return true;
  return isActiveStaff && scope.kind === "STAFF" && scope.staffUid === callerUid;
}
