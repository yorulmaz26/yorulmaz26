package com.kuaforumcepte.domain.model

import java.time.Duration
import java.time.Instant

/** Sunucudaki functions/src/domain/statusMachine.ts ile birebir aynı tablo. */
enum class AppointmentStatus(val label: String, val isActive: Boolean) {
    PENDING("Onay Bekliyor", true),
    CONFIRMED("Onaylandı", true),
    IN_PROGRESS("İşlemde", true),
    COMPLETED("Tamamlandı", false),
    CANCELLED_BY_CUSTOMER("Müşteri İptal Etti", false),
    CANCELLED_BY_STAFF("Salon İptal Etti", false),
    EXPIRED("Süresi Doldu", false),
    NO_SHOW("Gelmedi", false);

    val isTerminal: Boolean get() = !isActive

    companion object {
        fun fromWire(value: String?): AppointmentStatus? = entries.firstOrNull { it.name == value }
    }
}

enum class AppointmentAction { CONFIRM, REJECT, CANCEL_BY_CUSTOMER, CANCEL_BY_STAFF, MARK_ARRIVED, MARK_NO_SHOW, COMPLETE, EXPIRE }

/** CUSTOMER: randevunun müşterisi; STAFF: atanmış uzman; OWNER: salon sahibi; SYSTEM: zamanlanmış iş. */
enum class ActorRole { CUSTOMER, STAFF, OWNER, SYSTEM }

sealed interface TransitionResult {
    data class Allowed(val to: AppointmentStatus) : TransitionResult
    data class Denied(val reason: DenyReason) : TransitionResult
}

enum class DenyReason {
    INVALID_TRANSITION, PERMISSION_DENIED, REASON_REQUIRED, PENDING_EXPIRED,
    NOT_YET_EXPIRED, CANCEL_WINDOW_CLOSED, TOO_EARLY
}

/**
 * İstemci tarafında yalnızca butonların görünürlüğü/etkinliği için kullanılır.
 * Bağlayıcı denetim sunucudadır (transitionAppointment).
 */
object AppointmentTransitions {
    val CUSTOMER_CANCEL_CUTOFF: Duration = Duration.ofMinutes(120)
    val ARRIVAL_EARLY_WINDOW: Duration = Duration.ofMinutes(15)

    private data class Rule(
        val from: Set<AppointmentStatus>,
        val to: AppointmentStatus,
        val actors: Set<ActorRole>,
        val reasonRequired: Boolean,
    )

    private val salonSide = setOf(ActorRole.STAFF, ActorRole.OWNER)

    private val rules: Map<AppointmentAction, Rule> = mapOf(
        AppointmentAction.CONFIRM to Rule(setOf(AppointmentStatus.PENDING), AppointmentStatus.CONFIRMED, salonSide, false),
        AppointmentAction.REJECT to Rule(setOf(AppointmentStatus.PENDING), AppointmentStatus.CANCELLED_BY_STAFF, salonSide, true),
        AppointmentAction.CANCEL_BY_CUSTOMER to Rule(
            setOf(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED),
            AppointmentStatus.CANCELLED_BY_CUSTOMER, setOf(ActorRole.CUSTOMER), false,
        ),
        AppointmentAction.CANCEL_BY_STAFF to Rule(
            setOf(AppointmentStatus.CONFIRMED, AppointmentStatus.IN_PROGRESS),
            AppointmentStatus.CANCELLED_BY_STAFF, salonSide, true,
        ),
        AppointmentAction.MARK_ARRIVED to Rule(setOf(AppointmentStatus.CONFIRMED), AppointmentStatus.IN_PROGRESS, salonSide, false),
        AppointmentAction.MARK_NO_SHOW to Rule(setOf(AppointmentStatus.CONFIRMED), AppointmentStatus.NO_SHOW, salonSide, false),
        AppointmentAction.COMPLETE to Rule(setOf(AppointmentStatus.IN_PROGRESS), AppointmentStatus.COMPLETED, salonSide, false),
        AppointmentAction.EXPIRE to Rule(setOf(AppointmentStatus.PENDING), AppointmentStatus.EXPIRED, setOf(ActorRole.SYSTEM), false),
    )

    /** Tam 2 saat sınırında iptale izin verilir: now <= start − 2s. */
    fun canCustomerCancel(now: Instant, startAt: Instant): Boolean =
        !now.isAfter(startAt.minus(CUSTOMER_CANCEL_CUTOFF))

    fun evaluate(
        status: AppointmentStatus,
        action: AppointmentAction,
        actor: ActorRole,
        now: Instant,
        startAt: Instant,
        pendingExpiresAt: Instant,
        reason: String? = null,
    ): TransitionResult {
        val rule = rules.getValue(action)
        if (status !in rule.from) return TransitionResult.Denied(DenyReason.INVALID_TRANSITION)
        if (actor !in rule.actors) return TransitionResult.Denied(DenyReason.PERMISSION_DENIED)
        if (rule.reasonRequired && (reason == null || reason.trim().length < 3)) {
            return TransitionResult.Denied(DenyReason.REASON_REQUIRED)
        }
        val denied: DenyReason? = when (action) {
            AppointmentAction.CONFIRM -> if (!now.isBefore(pendingExpiresAt)) DenyReason.PENDING_EXPIRED else null
            AppointmentAction.EXPIRE -> if (now.isBefore(pendingExpiresAt)) DenyReason.NOT_YET_EXPIRED else null
            AppointmentAction.CANCEL_BY_CUSTOMER -> if (!canCustomerCancel(now, startAt)) DenyReason.CANCEL_WINDOW_CLOSED else null
            AppointmentAction.MARK_ARRIVED -> if (now.isBefore(startAt.minus(ARRIVAL_EARLY_WINDOW))) DenyReason.TOO_EARLY else null
            AppointmentAction.MARK_NO_SHOW -> if (now.isBefore(startAt)) DenyReason.TOO_EARLY else null
            else -> null
        }
        return denied?.let { TransitionResult.Denied(it) } ?: TransitionResult.Allowed(rule.to)
    }

    /** UI için: belirli aktörün o anda yapabileceği eylemler (reason kontrolü hariç). */
    fun availableActions(
        status: AppointmentStatus,
        actor: ActorRole,
        now: Instant,
        startAt: Instant,
        pendingExpiresAt: Instant,
    ): List<AppointmentAction> = AppointmentAction.entries.filter { action ->
        val r = evaluate(status, action, actor, now, startAt, pendingExpiresAt, reason = "ui-check")
        r is TransitionResult.Allowed
    }
}
