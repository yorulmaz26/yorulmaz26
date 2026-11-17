package com.kuaforumcepte.domain

import com.kuaforumcepte.domain.auth.Permission
import com.kuaforumcepte.domain.auth.PermissionPolicy
import com.kuaforumcepte.domain.booking.ServiceOption
import com.kuaforumcepte.domain.booking.ServiceSelection
import com.kuaforumcepte.domain.booking.formatDuration
import com.kuaforumcepte.domain.model.Kurus
import com.kuaforumcepte.domain.model.MemberRole
import com.kuaforumcepte.domain.model.Rating
import com.kuaforumcepte.domain.model.SalonCode
import com.kuaforumcepte.domain.phone.PhoneNumbers
import com.kuaforumcepte.domain.text.SearchKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MiscDomainTest {
    @Test fun `telefon normalizasyonu`() {
        assertEquals("+905321234567", PhoneNumbers.normalizeTr("0532 123 45 67"))
        assertEquals("+905321234567", PhoneNumbers.normalizeTr("+90 (532) 123-45-67"))
        assertEquals("+902125556677", PhoneNumbers.normalizeTr("0212 555 66 77"))
        assertNull(PhoneNumbers.normalizeTr("0212 555 66 77", mobileOnly = true))
        assertNull(PhoneNumbers.normalizeTr("12345"))
        assertEquals("0532 123 45 67", PhoneNumbers.formatForDisplay("+905321234567"))
    }

    @Test fun `hizmet seçimi toplamları ve tek uzman kuralı`() {
        val a = ServiceOption("a", "u1", "Saç Kesimi", Kurus(40000), 30)
        val b = ServiceOption("b", "u1", "Fön", Kurus(15050), 15)
        val other = ServiceOption("c", "u2", "Manikür", Kurus(30000), 45)
        val s = ServiceSelection.EMPTY.toggle(a).toggle(b)
        assertEquals(Kurus(55050), s.totalPrice)
        assertEquals(45, s.totalMinutes)
        assertFailsWith<IllegalArgumentException> { s.toggle(other) }
        val removed = s.toggle(a).toggle(b)
        assertTrue(removed.isEmpty)
        assertNull(removed.staffUid)
        assertEquals("1 sa 15 dk", formatDuration(75))
        assertEquals("45 dk", formatDuration(45))
    }

    @Test fun `yetki matrisi`() {
        assertTrue(PermissionPolicy.can(MemberRole.OWNER, Permission.VIEW_SALON_REPORTS))
        assertFalse(PermissionPolicy.can(MemberRole.STAFF, Permission.VIEW_SALON_REPORTS))
        assertFalse(PermissionPolicy.can(MemberRole.STAFF, Permission.MANAGE_STAFF))
        assertFalse(PermissionPolicy.can(MemberRole.STAFF, Permission.VIEW_ALL_SALON_APPOINTMENTS))
        assertTrue(PermissionPolicy.can(MemberRole.STAFF, Permission.MANAGE_OWN_SERVICES))
        assertTrue(PermissionPolicy.can(MemberRole.STAFF, Permission.VIEW_OWN_REPORTS))
    }

    @Test fun `puan ortalaması ve Yeni etiketi`() {
        assertEquals("Yeni", Rating.NONE.label())
        assertEquals(4.7, Rating(14, 3).average)
        assertEquals("4,7", Rating(14, 3).label())
    }

    @Test fun `salon kodu ve arama anahtarı`() {
        assertTrue(SalonCode.isValid("123456"))
        assertFalse(SalonCode.isValid("012345"))
        assertEquals("istanbul guzellik salonu", SearchKey.of("  İSTANBUL   Güzellik Salonu "))
        assertEquals("isik kuafor", SearchKey.of("IŞIK Kuaför"))
    }
}
