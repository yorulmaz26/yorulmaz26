export const APPOINTMENT_STATUSES = [
  "PENDING",
  "CONFIRMED",
  "IN_PROGRESS",
  "COMPLETED",
  "CANCELLED_BY_CUSTOMER",
  "CANCELLED_BY_STAFF",
  "EXPIRED",
  "NO_SHOW",
] as const;
export type AppointmentStatus = (typeof APPOINTMENT_STATUSES)[number];

export type AppointmentAction =
  | "CONFIRM"
  | "REJECT"
  | "CANCEL_BY_CUSTOMER"
  | "CANCEL_BY_STAFF"
  | "MARK_ARRIVED"
  | "MARK_NO_SHOW"
  | "COMPLETE"
  | "EXPIRE";

/** CUSTOMER: randevunun müşterisi; STAFF: atanmış uzman; OWNER: salon sahibi; SYSTEM: zamanlanmış iş. */
export type ActorRole = "CUSTOMER" | "STAFF" | "OWNER" | "SYSTEM";

export const ACTIVE_STATUSES: ReadonlySet<AppointmentStatus> = new Set(["PENDING", "CONFIRMED", "IN_PROGRESS"]);

export function isTerminal(status: AppointmentStatus): boolean {
  return !ACTIVE_STATUSES.has(status);
}

export const CUSTOMER_CANCEL_CUTOFF_MINUTES = 120;
export const ARRIVAL_EARLY_WINDOW_MINUTES = 15;

interface Rule {
  from: AppointmentStatus[];
  to: AppointmentStatus;
  actors: ActorRole[];
  reasonRequired: boolean;
}

const RULES: Record<AppointmentAction, Rule> = {
  CONFIRM: { from: ["PENDING"], to: "CONFIRMED", actors: ["STAFF", "OWNER"], reasonRequired: false },
  REJECT: { from: ["PENDING"], to: "CANCELLED_BY_STAFF", actors: ["STAFF", "OWNER"], reasonRequired: true },
  CANCEL_BY_CUSTOMER: {
    from: ["PENDING", "CONFIRMED"],
    to: "CANCELLED_BY_CUSTOMER",
    actors: ["CUSTOMER"],
    reasonRequired: false,
  },
  CANCEL_BY_STAFF: {
    from: ["CONFIRMED", "IN_PROGRESS"],
    to: "CANCELLED_BY_STAFF",
    actors: ["STAFF", "OWNER"],
    reasonRequired: true,
  },
  MARK_ARRIVED: { from: ["CONFIRMED"], to: "IN_PROGRESS", actors: ["STAFF", "OWNER"], reasonRequired: false },
  MARK_NO_SHOW: { from: ["CONFIRMED"], to: "NO_SHOW", actors: ["STAFF", "OWNER"], reasonRequired: false },
  COMPLETE: { from: ["IN_PROGRESS"], to: "COMPLETED", actors: ["STAFF", "OWNER"], reasonRequired: false },
  EXPIRE: { from: ["PENDING"], to: "EXPIRED", actors: ["SYSTEM"], reasonRequired: false },
};

export interface TransitionContext {
  status: AppointmentStatus;
  action: AppointmentAction;
  actor: ActorRole;
  now: Date;
  startAt: Date;
  pendingExpiresAt: Date;
  reason?: string | null;
}

export type TransitionResult =
  | { ok: true; to: AppointmentStatus }
  | {
      ok: false;
      reason:
        | "INVALID_TRANSITION"
        | "PERMISSION_DENIED"
        | "REASON_REQUIRED"
        | "PENDING_EXPIRED"
        | "NOT_YET_EXPIRED"
        | "CANCEL_WINDOW_CLOSED"
        | "TOO_EARLY";
    };

const MINUTE = 60_000;

/** Müşteri iptali: now <= startAt − 2 saat ise izinli (tam sınırda izin verilir). */
export function canCustomerCancel(now: Date, startAt: Date, cutoffMinutes = CUSTOMER_CANCEL_CUTOFF_MINUTES): boolean {
  return now.getTime() <= startAt.getTime() - cutoffMinutes * MINUTE;
}

export function evaluateTransition(ctx: TransitionContext): TransitionResult {
  const rule = RULES[ctx.action];
  if (!rule.from.includes(ctx.status)) return { ok: false, reason: "INVALID_TRANSITION" };
  if (!rule.actors.includes(ctx.actor)) return { ok: false, reason: "PERMISSION_DENIED" };
  if (rule.reasonRequired && !(ctx.reason && ctx.reason.trim().length >= 3)) {
    return { ok: false, reason: "REASON_REQUIRED" };
  }
  const now = ctx.now.getTime();
  switch (ctx.action) {
    case "CONFIRM":
      if (now >= ctx.pendingExpiresAt.getTime()) return { ok: false, reason: "PENDING_EXPIRED" };
      break;
    case "EXPIRE":
      if (now < ctx.pendingExpiresAt.getTime()) return { ok: false, reason: "NOT_YET_EXPIRED" };
      break;
    case "CANCEL_BY_CUSTOMER":
      if (!canCustomerCancel(ctx.now, ctx.startAt)) return { ok: false, reason: "CANCEL_WINDOW_CLOSED" };
      break;
    case "MARK_ARRIVED":
      if (now < ctx.startAt.getTime() - ARRIVAL_EARLY_WINDOW_MINUTES * MINUTE) return { ok: false, reason: "TOO_EARLY" };
      break;
    case "MARK_NO_SHOW":
      if (now < ctx.startAt.getTime()) return { ok: false, reason: "TOO_EARLY" };
      break;
    default:
      break;
  }
  return { ok: true, to: rule.to };
}

/** pendingExpiresAt = min(createdAt + ttl, startAt) */
export function computePendingExpiresAt(createdAt: Date, startAt: Date, ttlMinutes: number): Date {
  return new Date(Math.min(createdAt.getTime() + ttlMinutes * MINUTE, startAt.getTime()));
}
