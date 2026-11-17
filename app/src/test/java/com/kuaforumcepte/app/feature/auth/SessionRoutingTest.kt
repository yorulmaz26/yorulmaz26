package com.kuaforumcepte.app.feature.auth

import com.kuaforumcepte.app.data.session.Membership
import com.kuaforumcepte.app.data.session.PendingInvitation
import com.kuaforumcepte.app.data.session.SessionSnapshot
import com.kuaforumcepte.domain.model.EntryRole
import com.kuaforumcepte.domain.model.MemberRole
import com.kuaforumcepte.domain.model.MemberStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionRoutingTest {
    private fun m(id: String, status: MemberStatus = MemberStatus.ACTIVE) = Membership(id, "Salon $id", "123456", MemberRole.STAFF, status)
    private val invite = PendingInvitation("s9", "i1", "Yeni Salon", "Can", "Er", "Berber", "2026-12-01T00:00:00Z")
    private fun snap(exists: Boolean = true, ms: List<Membership> = emptyList(), inv: List<PendingInvitation> = emptyList()) =
        SessionSnapshot(exists, "Ali", "Kaya", "+905321112233", ms, inv, null)

    @Test fun `müşteri profili yoksa kayıt`() {
        assertEquals(SessionTarget.CustomerRegister, SessionRouting.decide(EntryRole.CUSTOMER, snap(exists = false), null))
        assertEquals(SessionTarget.CustomerHome, SessionRouting.decide(EntryRole.CUSTOMER, snap(ms = listOf(m("a"))), null))
    }

    @Test fun `işletme girişinde telefon tek başına rol vermez`() {
        assertEquals(SessionTarget.OwnerRegister, SessionRouting.decide(EntryRole.BUSINESS, snap(), null))
        assertEquals(SessionTarget.OwnerRegister, SessionRouting.decide(EntryRole.BUSINESS, snap(ms = listOf(m("a", MemberStatus.INACTIVE))), null))
    }

    @Test fun `bekleyen davet önce gösterilir`() {
        assertEquals(SessionTarget.Invitations, SessionRouting.decide(EntryRole.BUSINESS, snap(ms = listOf(m("a")), inv = listOf(invite)), null))
    }

    @Test fun `çoklu salonda seçim`() {
        val two = snap(ms = listOf(m("a"), m("b")))
        assertEquals(SessionTarget.SalonPicker, SessionRouting.decide(EntryRole.BUSINESS, two, null))
        assertEquals(SessionTarget.BusinessHome("b"), SessionRouting.decide(EntryRole.BUSINESS, two, "b"))
        assertEquals(SessionTarget.SalonPicker, SessionRouting.decide(EntryRole.BUSINESS, two, "ayrildim"))
        assertEquals(SessionTarget.BusinessHome("a"), SessionRouting.decide(EntryRole.BUSINESS, snap(ms = listOf(m("a"))), null))
    }
}
