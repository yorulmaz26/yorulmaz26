import { canCustomerCancel, computePendingExpiresAt, evaluateTransition, TransitionContext } from "../src/domain";

const start = new Date("2026-11-20T10:00:00Z");
const base: TransitionContext = {
  status: "PENDING",
  action: "CONFIRM",
  actor: "STAFF",
  now: new Date("2026-11-19T10:00:00Z"),
  startAt: start,
  pendingExpiresAt: new Date("2026-11-19T10:30:00Z"),
};
const ev = (o: Partial<TransitionContext>) => evaluateTransition({ ...base, ...o });

describe("randevu durum makinesi", () => {
  test("uzman bekleyeni süresi dolmadan onaylar", () => {
    expect(ev({})).toEqual({ ok: true, to: "CONFIRMED" });
  });
  test("süre dolduktan sonra geç onay reddedilir", () => {
    expect(ev({ now: new Date("2026-11-19T10:30:00Z") })).toEqual({ ok: false, reason: "PENDING_EXPIRED" });
  });
  test("müşteri onaylayamaz", () => {
    expect(ev({ actor: "CUSTOMER" })).toEqual({ ok: false, reason: "PERMISSION_DENIED" });
  });
  test("ret gerekçe ister", () => {
    expect(ev({ action: "REJECT" })).toEqual({ ok: false, reason: "REASON_REQUIRED" });
    expect(ev({ action: "REJECT", reason: "Uzman hasta" })).toEqual({ ok: true, to: "CANCELLED_BY_STAFF" });
  });
  test("tamamlanan randevu tekrar tamamlanamaz", () => {
    expect(ev({ status: "COMPLETED", action: "COMPLETE" })).toEqual({ ok: false, reason: "INVALID_TRANSITION" });
  });
  test("tamamlama yalnızca IN_PROGRESS'ten", () => {
    expect(ev({ status: "CONFIRMED", action: "COMPLETE" }).ok).toBe(false);
    expect(ev({ status: "IN_PROGRESS", action: "COMPLETE" })).toEqual({ ok: true, to: "COMPLETED" });
  });
  test("gelmedi yalnızca başlangıçtan sonra", () => {
    expect(ev({ status: "CONFIRMED", action: "MARK_NO_SHOW", now: new Date("2026-11-20T09:59:00Z") }).ok).toBe(false);
    expect(ev({ status: "CONFIRMED", action: "MARK_NO_SHOW", now: start })).toEqual({ ok: true, to: "NO_SHOW" });
  });
  test("geldi en erken başlangıçtan 15 dk önce", () => {
    expect(ev({ status: "CONFIRMED", action: "MARK_ARRIVED", now: new Date("2026-11-20T09:44:00Z") }).ok).toBe(false);
    expect(ev({ status: "CONFIRMED", action: "MARK_ARRIVED", now: new Date("2026-11-20T09:45:00Z") }).ok).toBe(true);
  });
  test("sistem süresi dolmayanı EXPIRE edemez", () => {
    expect(ev({ action: "EXPIRE", actor: "SYSTEM" })).toEqual({ ok: false, reason: "NOT_YET_EXPIRED" });
    expect(ev({ action: "EXPIRE", actor: "SYSTEM", now: base.pendingExpiresAt })).toEqual({ ok: true, to: "EXPIRED" });
  });
});

describe("müşteri 2 saat iptal sınırı", () => {
  test("tam 2 saat kala izinli, 1 ms sonra değil", () => {
    expect(canCustomerCancel(new Date("2026-11-20T08:00:00Z"), start)).toBe(true);
    expect(canCustomerCancel(new Date("2026-11-20T08:00:00.001Z"), start)).toBe(false);
  });
  test("onaylı randevu pencere kapandıktan sonra iptal edilemez", () => {
    expect(
      ev({ status: "CONFIRMED", action: "CANCEL_BY_CUSTOMER", actor: "CUSTOMER", now: new Date("2026-11-20T09:00:00Z") }),
    ).toEqual({ ok: false, reason: "CANCEL_WINDOW_CLOSED" });
  });
});

test("pendingExpiresAt başlangıcı aşmaz", () => {
  const created = new Date("2026-11-20T09:45:00Z");
  expect(computePendingExpiresAt(created, start, 30).toISOString()).toBe(start.toISOString());
  expect(computePendingExpiresAt(base.now, start, 30).toISOString()).toBe("2026-11-19T10:30:00.000Z");
});
