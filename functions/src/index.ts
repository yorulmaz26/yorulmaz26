/**
 * Kuaförüm Cepte — Cloud Functions giriş noktası (bölge: europe-west1).
 * Sözleşmeler: docs/API_CONTRACTS.md. Domain kuralları: src/domain.
 */
import { setGlobalOptions } from "firebase-functions/v2";

setGlobalOptions({ region: "europe-west1", maxInstances: 20 });

// Aşama 2 — kimlik, kayıt, personel daveti ve rol sistemi
export { bootstrapSession, registerCustomer } from "./auth/session";
export { registerSalonOwner } from "./salon/registerSalonOwner";
export {
  createStaffInvitation,
  revokeInvitation,
  acceptInvitation,
  declineInvitation,
  updateStaffMember,
  setMemberStatus,
  leaveSalon,
} from "./staff/invitations";

// Aşama 3 — salon profili, görseller, paylaşım sayfası
export { updateSalonProfile, updateSalonMedia } from "./salon/updateSalon";
export { salonLanding } from "./salon/landing";

// Aşama 4 — çalışma takvimi, izinler, kapatmalar, hizmet etkisi
export {
  updateWeeklySchedule,
  upsertStaffDateClosure,
  deleteStaffDateClosure,
  updateSalonClosedDays,
  upsertSalonDateClosure,
  deleteSalonDateClosure,
  serviceImpact,
} from "./schedule/scheduleFunctions";

// Aşama 5 — müsaitlik ve rezervasyon
export { getAvailability } from "./booking/availabilityFn";
export { createAppointment, transitionAppointment } from "./booking/appointments";
