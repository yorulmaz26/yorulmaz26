package com.kuaforumcepte.domain

import com.kuaforumcepte.domain.model.ActorRole
import com.kuaforumcepte.domain.model.AppointmentAction
import com.kuaforumcepte.domain.model.AppointmentStatus
import com.kuaforumcepte.domain.model.AppointmentTransitions
import com.kuaforumcepte.domain.model.DenyReason
import com.kuaforumcepte.domain.model.TransitionResult
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppointmentTransitionsTest {
    private val start = Instant.parse("2026-11-20T10:00:00Z")
    private val pendingExp = Instant.parse("2026-11-19T10:30:00Z")
    private val now = Instant.parse("2026-11-19T10:00:00Z")

    private fun eval(
        status: AppointmentStatus,
        action: AppointmentAction,
        actor: ActorRole,
        at: Instant = now,
        reason: String? = null,
    ) = AppointmentTransitions.evaluate(status, action, actor, at, start, pendingExp, reason)

    @Test fun `uzman bekleyen randevuyu onaylar`() {
        assertEquals(TransitionResult.Allowed(AppointmentStatus.CONFIRMED),
            eval(AppointmentStatus.PENDING, AppointmentAction.CONFIRM, ActorRole.STAFF))
    }

    @Test fun `geç onay reddedilir`() {
        assertEquals(TransitionResult.Denied(DenyReason.PENDING_EXPIRED),
            eval(AppointmentStatus.PENDING, AppointmentAction.CONFIRM, ActorRole.OWNER, at = pendingExp))
    }

    @Test fun `müşteri onaylayamaz`() {
        assertEquals(TransitionResult.Denied(DenyReason.PERMISSION_DENIED),
            eval(AppointmentStatus.PENDING, AppointmentAction.CONFIRM, ActorRole.CUSTOMER))
    }

    @Test fun `salon iptali gerekçe ister`() {
        assertEquals(TransitionResult.Denied(DenyReason.REASON_REQUIRED),
            eval(AppointmentStatus.CONFIRMED, AppointmentAction.CANCEL_BY_STAFF, ActorRole.STAFF, reason = " "))
    }

    @Test fun `çift tamamlama mümkün değil`() {
        assertEquals(TransitionResult.Denied(DenyReason.INVALID_TRANSITION),
            eval(AppointmentStatus.COMPLETED, AppointmentAction.COMPLETE, ActorRole.STAFF))
    }

    @Test fun `iki saat sınırı tam sınırda izinli`() {
        assertTrue(AppointmentTransitions.canCustomerCancel(Instant.parse("2026-11-20T08:00:00Z"), start))
        assertFalse(AppointmentTransitions.canCustomerCancel(Instant.parse("2026-11-20T08:00:01Z"), start))
    }

    @Test fun `geldi butonu en erken 15 dk önce`() {
        assertEquals(TransitionResult.Denied(DenyReason.TOO_EARLY),
            eval(AppointmentStatus.CONFIRMED, AppointmentAction.MARK_ARRIVED, ActorRole.STAFF, at = Instant.parse("2026-11-20T09:44:59Z")))
        assertEquals(TransitionResult.Allowed(AppointmentStatus.IN_PROGRESS),
            eval(AppointmentStatus.CONFIRMED, AppointmentAction.MARK_ARRIVED, ActorRole.STAFF, at = Instant.parse("2026-11-20T09:45:00Z")))
    }

    @Test fun `UI eylemleri durum ve zamana göre`() {
        val actions = AppointmentTransitions.availableActions(
            AppointmentStatus.CONFIRMED, ActorRole.STAFF, Instant.parse("2026-11-20T10:05:00Z"), start, pendingExp,
        )
        assertEquals(
            listOf(AppointmentAction.CANCEL_BY_STAFF, AppointmentAction.MARK_ARRIVED, AppointmentAction.MARK_NO_SHOW),
            actions,
        )
        val customer = AppointmentTransitions.availableActions(AppointmentStatus.CONFIRMED, ActorRole.CUSTOMER, now, start, pendingExp)
        assertEquals(listOf(AppointmentAction.CANCEL_BY_CUSTOMER), customer)
    }

    @Test fun `aktif ve terminal durumlar`() {
        val active = AppointmentStatus.entries.filter { it.isActive }.toSet()
        assertEquals(setOf(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED, AppointmentStatus.IN_PROGRESS), active)
    }
}
