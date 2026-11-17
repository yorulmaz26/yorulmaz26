package com.kuaforumcepte.domain.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Gün içi dakika aralığı [start, end). end = 1440 (24:00) gün sonu olarak geçerlidir. */
data class TimeRange(val start: Int, val end: Int) {
    val label: String get() = "${hm(start)}–${hm(end)}"

    fun overlaps(o: TimeRange): Boolean = start < o.end && o.start < end
    fun contains(o: TimeRange): Boolean = o.start >= start && o.end <= end

    companion object {
        fun hm(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

        /** "HH:mm" → dakika; 24:00 yalnızca bitiş için. */
        fun parseHm(value: String, allowEndOfDay: Boolean = false): Int? {
            val m = Regex("^([01]\\d|2[0-4]):([0-5]\\d)$").matchEntire(value) ?: return null
            val minutes = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
            return if (minutes > 1440 || (minutes == 1440 && !allowEndOfDay)) null else minutes
        }
    }
}

data class DaySchedule(val shifts: List<TimeRange> = emptyList(), val breaks: List<TimeRange> = emptyList()) {
    val workingMinutes: Int get() = shifts.sumOf { it.end - it.start } - breaks.sumOf { it.end - it.start }
}

/** Anahtar: ISO haftanın günü 1 (Pzt) … 7 (Paz). */
data class WeeklySchedule(val days: Map<Int, DaySchedule>, val weeklyDaysOff: Set<Int>) {
    fun day(isoDay: Int): DaySchedule = days[isoDay] ?: DaySchedule()
    fun isWorking(isoDay: Int): Boolean = isoDay !in weeklyDaysOff && day(isoDay).shifts.isNotEmpty()

    companion object {
        /** Sunucudaki varsayılanla aynı: Pzt–Cmt 09:00–19:00, Pazar izin. */
        fun default(): WeeklySchedule = WeeklySchedule(
            days = (1..7).associateWith { if (it == 7) DaySchedule() else DaySchedule(listOf(TimeRange(540, 1140))) },
            weeklyDaysOff = setOf(7),
        )
    }
}

enum class ScheduleError(val message: String) {
    END_BEFORE_START("Bitiş saati başlangıçtan sonra olmalı."),
    SHIFTS_OVERLAP("Vardiyalar birbiriyle çakışıyor."),
    BREAKS_OVERLAP("Molalar birbiriyle çakışıyor."),
    BREAK_OUTSIDE_SHIFT("Mola, bir çalışma aralığının içinde olmalı."),
    TOO_MANY_RANGES("Bir günde en fazla 4 vardiya ve 6 mola eklenebilir."),
}

/** functions/src/domain/availability.ts → validateDaySchedule ile aynı kurallar. */
object ScheduleValidator {
    const val MAX_SHIFTS = 4
    const val MAX_BREAKS = 6

    fun validate(day: DaySchedule): ScheduleError? {
        if (day.shifts.size > MAX_SHIFTS || day.breaks.size > MAX_BREAKS) return ScheduleError.TOO_MANY_RANGES
        if ((day.shifts + day.breaks).any { it.end <= it.start }) return ScheduleError.END_BEFORE_START
        fun anyOverlap(list: List<TimeRange>) = list.sortedBy { it.start }.zipWithNext().any { (a, b) -> b.start < a.end }
        if (anyOverlap(day.shifts)) return ScheduleError.SHIFTS_OVERLAP
        if (anyOverlap(day.breaks)) return ScheduleError.BREAKS_OVERLAP
        if (!day.breaks.all { br -> day.shifts.any { it.contains(br) } }) return ScheduleError.BREAK_OUTSIDE_SHIFT
        return null
    }

    fun validate(week: WeeklySchedule): Map<Int, ScheduleError> =
        (1..7).mapNotNull { d -> validate(week.day(d))?.let { d to it } }.toMap()
}

object Weekdays {
    private val tr = Locale.forLanguageTag("tr-TR")
    fun name(isoDay: Int): String = DayOfWeek.of(isoDay).getDisplayName(TextStyle.FULL, tr)
    fun short(isoDay: Int): String = DayOfWeek.of(isoDay).getDisplayName(TextStyle.SHORT, tr)
}

/** Tarih kapatması (dahil–dahil). */
data class DateClosure(val id: String, val startDate: LocalDate, val endDate: LocalDate, val note: String) {
    fun covers(date: LocalDate): Boolean = !date.isBefore(startDate) && !date.isAfter(endDate)
    val dayCount: Long get() = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1
}

object ClosureValidator {
    enum class Error(val message: String) {
        END_BEFORE_START("Bitiş tarihi başlangıçtan önce olamaz."),
        PAST_DATE("Geçmiş bir tarih kapatılamaz."),
        RANGE_TOO_LONG("Tek seferde en fazla 1 yıllık aralık kapatılabilir."),
        NOTE_TOO_LONG("Açıklama en fazla 200 karakter olabilir."),
    }

    fun validate(start: LocalDate, end: LocalDate, note: String, today: LocalDate): Error? = when {
        end.isBefore(start) -> Error.END_BEFORE_START
        start.isBefore(today) -> Error.PAST_DATE
        end.isAfter(start.plusDays(365)) -> Error.RANGE_TOO_LONG
        note.length > 200 -> Error.NOTE_TOO_LONG
        else -> null
    }
}

/** Müşteriye gösterilen okunaklı haftalık özet; aynı programa sahip ardışık günler birleştirilir. */
object ScheduleSummary {
    fun dayLabel(week: WeeklySchedule, isoDay: Int, salonClosedDays: Set<Int>): String = when {
        isoDay in salonClosedDays -> "Salon kapalı"
        !week.isWorking(isoDay) -> "İzinli"
        else -> {
            val d = week.day(isoDay)
            val shifts = d.shifts.joinToString(", ") { it.label }
            if (d.breaks.isEmpty()) shifts else "$shifts (mola ${d.breaks.joinToString(", ") { it.label }})"
        }
    }

    /** Ör. ["Pzt–Cum: 09:00–19:00", "Cmt: 10:00–16:00", "Paz: İzinli"] */
    fun lines(week: WeeklySchedule, salonClosedDays: Set<Int> = emptySet()): List<String> {
        val out = mutableListOf<String>()
        var startDay = 1
        var current = dayLabel(week, 1, salonClosedDays)
        for (d in 2..8) {
            val label = if (d <= 7) dayLabel(week, d, salonClosedDays) else null
            if (label != current) {
                val range = if (startDay == d - 1) Weekdays.short(startDay) else "${Weekdays.short(startDay)}–${Weekdays.short(d - 1)}"
                out += "$range: $current"
                startDay = d
                if (label != null) current = label
            }
        }
        return out
    }
}
