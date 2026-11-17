package com.kuaforumcepte.domain

import com.kuaforumcepte.domain.time.PeriodType
import com.kuaforumcepte.domain.time.ReportPeriod
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BusinessTimeTest {
    @Test fun `hafta pazartesi 00_00 İstanbul başlar`() {
        val p = ReportPeriod.of(PeriodType.WEEK, LocalDate.parse("2026-11-19"))
        assertEquals("W2026-11-16", p.key)
        assertEquals(Instant.parse("2026-11-15T21:00:00Z"), p.start)
        assertEquals(Instant.parse("2026-11-22T21:00:00Z"), p.end)
        assertTrue(p.contains(Instant.parse("2026-11-22T20:59:59Z"))) // Pazar 23:59:59
        assertFalse(p.contains(Instant.parse("2026-11-22T21:00:00Z"))) // Pazartesi 00:00
    }

    @Test fun `gün ve ay anahtarları sunucuyla aynı`() {
        val instant = Instant.parse("2026-11-30T21:00:00Z") // İstanbul 1 Aralık 00:00
        assertEquals("D2026-12-01", ReportPeriod.containing(PeriodType.DAY, instant).key)
        assertEquals("M2026-12", ReportPeriod.containing(PeriodType.MONTH, instant).key)
        assertEquals("M2026-11", ReportPeriod.containing(PeriodType.MONTH, Instant.parse("2026-11-30T20:59:59Z")).key)
    }

    @Test fun `önceki ve sonraki dönem`() {
        val m = ReportPeriod.of(PeriodType.MONTH, LocalDate.parse("2026-03-15"))
        assertEquals("M2026-02", m.previous().key)
        assertEquals("M2026-04", m.next().key)
        assertEquals("W2026-11-09", ReportPeriod.of(PeriodType.WEEK, LocalDate.parse("2026-11-16")).previous().key)
    }

    @Test fun `Türkçe etiket`() {
        assertEquals("Kasım 2026", ReportPeriod.of(PeriodType.MONTH, LocalDate.parse("2026-11-03")).label())
    }
}
