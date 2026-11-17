package com.kuaforumcepte.domain

import com.kuaforumcepte.domain.schedule.ClosureValidator
import com.kuaforumcepte.domain.schedule.DateClosure
import com.kuaforumcepte.domain.schedule.DaySchedule
import com.kuaforumcepte.domain.schedule.ScheduleError
import com.kuaforumcepte.domain.schedule.ScheduleValidator
import com.kuaforumcepte.domain.schedule.TimeRange
import com.kuaforumcepte.domain.schedule.WeeklySchedule
import com.kuaforumcepte.domain.schedule.Weekdays
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleTest {
    private fun r(a: String, b: String) = TimeRange(TimeRange.parseHm(a)!!, TimeRange.parseHm(b, true)!!)

    @Test fun `geçerli çok vardiyalı gün`() {
        val d = DaySchedule(listOf(r("09:00", "12:00"), r("13:00", "24:00")), listOf(r("10:30", "10:45")))
        assertNull(ScheduleValidator.validate(d))
        assertEquals(165 + 660, d.workingMinutes)
    }

    @Test fun `doğrulama hataları sunucuyla aynı`() {
        assertEquals(ScheduleError.END_BEFORE_START, ScheduleValidator.validate(DaySchedule(listOf(r("12:00", "09:00")))))
        assertEquals(ScheduleError.SHIFTS_OVERLAP, ScheduleValidator.validate(DaySchedule(listOf(r("09:00", "12:00"), r("11:59", "14:00")))))
        assertEquals(ScheduleError.BREAKS_OVERLAP, ScheduleValidator.validate(DaySchedule(listOf(r("09:00", "18:00")), listOf(r("12:00", "13:00"), r("12:30", "12:45")))))
        assertEquals(ScheduleError.BREAK_OUTSIDE_SHIFT, ScheduleValidator.validate(DaySchedule(listOf(r("09:00", "12:00"), r("13:00", "18:00")), listOf(r("11:30", "13:30")))))
        assertEquals(ScheduleError.TOO_MANY_RANGES, ScheduleValidator.validate(DaySchedule(List(5) { r("0${it}:00", "0${it}:30") })))
    }

    @Test fun `saat ayrıştırma ve varsayılan program`() {
        assertNull(TimeRange.parseHm("24:00"))
        assertEquals(1440, TimeRange.parseHm("24:00", allowEndOfDay = true))
        assertNull(TimeRange.parseHm("9:00"))
        assertEquals("09:00–19:00", WeeklySchedule.default().day(1).shifts.single().label)
        assertTrue(!WeeklySchedule.default().isWorking(7))
        assertEquals("Pazartesi", Weekdays.name(1))
    }

    @Test fun `tarih kapatma`() {
        val today = LocalDate.parse("2026-11-17")
        assertNull(ClosureValidator.validate(today, today.plusDays(3), "İzin", today))
        assertEquals(ClosureValidator.Error.PAST_DATE, ClosureValidator.validate(today.minusDays(1), today, "", today))
        assertEquals(ClosureValidator.Error.END_BEFORE_START, ClosureValidator.validate(today.plusDays(2), today.plusDays(1), "", today))
        assertEquals(ClosureValidator.Error.RANGE_TOO_LONG, ClosureValidator.validate(today, today.plusDays(400), "", today))
        val c = DateClosure("x", today, today.plusDays(2), "")
        assertEquals(3, c.dayCount)
        assertTrue(c.covers(today.plusDays(2)))
    }
}

class ServiceRulesTest {
    @Test fun `hizmet sınırları`() {
        val ok = com.kuaforumcepte.domain.booking.ServiceRules.validate("Saç Kesimi", "250,50", 30, "")
        assertTrue(ok.isValid)
        val bad = com.kuaforumcepte.domain.booking.ServiceRules.validate("S", "0,50", 7, "x".repeat(501))
        assertEquals(listOf(true, true, true, true), listOf(bad.name != null, bad.price != null, bad.duration != null, bad.description != null))
        assertTrue(com.kuaforumcepte.domain.booking.ServiceRules.validate("Boya", "1.000.001", 600, "").price != null)
        assertEquals(120, com.kuaforumcepte.domain.booking.ServiceRules.durationOptions.size)
    }
}

class ScheduleSummaryTest {
    @Test fun `ardışık aynı günler birleştirilir`() {
        val r = { a: Int, b: Int -> TimeRange(a * 60, b * 60) }
        val week = WeeklySchedule(
            days = (1..5).associateWith { DaySchedule(listOf(r(9, 19)), listOf(r(12, 13))) } + (6 to DaySchedule(listOf(r(10, 16)))),
            weeklyDaysOff = setOf(7),
        )
        val lines = com.kuaforumcepte.domain.schedule.ScheduleSummary.lines(week)
        assertEquals(3, lines.size)
        assertTrue(lines[0].endsWith(": 09:00–19:00 (mola 12:00–13:00)"), lines[0])
        assertTrue(lines[1].endsWith(": 10:00–16:00"), lines[1])
        assertTrue(lines[2].endsWith(": İzinli"), lines[2])
        val closedSat = com.kuaforumcepte.domain.schedule.ScheduleSummary.lines(week, setOf(6))
        assertTrue(closedSat[1].endsWith(": Salon kapalı"), closedSat[1])
    }
}
