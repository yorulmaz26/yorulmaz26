package com.kuaforumcepte.domain.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** İş kuralları Europe/Istanbul'da; saklama UTC (Instant). Sunucudaki time.ts ile aynı anahtarlar. */
object BusinessTime {
    val ZONE: ZoneId = ZoneId.of("Europe/Istanbul")
    val LOCALE: Locale = Locale.forLanguageTag("tr-TR")

    fun localDateOf(instant: Instant): LocalDate = instant.atZone(ZONE).toLocalDate()
    fun startOfDay(date: LocalDate): Instant = date.atStartOfDay(ZONE).toInstant()
}

enum class PeriodType(val label: String) { DAY("Günlük"), WEEK("Haftalık"), MONTH("Aylık") }

/** [start, end) UTC aralığı + rapor anahtarı (D2026-11-17 / W2026-11-16 / M2026-11). */
data class ReportPeriod(
    val type: PeriodType,
    val startDate: LocalDate,
    val endDateExclusive: LocalDate,
    val start: Instant,
    val end: Instant,
    val key: String,
) {
    fun contains(instant: Instant): Boolean = !instant.isBefore(start) && instant.isBefore(end)

    fun label(): String {
        val dayFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", BusinessTime.LOCALE)
        return when (type) {
            PeriodType.DAY -> startDate.format(dayFmt)
            PeriodType.WEEK -> "${startDate.format(DateTimeFormatter.ofPattern("d MMM", BusinessTime.LOCALE))} – " +
                endDateExclusive.minusDays(1).format(DateTimeFormatter.ofPattern("d MMM yyyy", BusinessTime.LOCALE))
            PeriodType.MONTH -> startDate.format(DateTimeFormatter.ofPattern("LLLL yyyy", BusinessTime.LOCALE))
        }
    }

    fun previous(): ReportPeriod = of(type, startDate.minusDays(1))
    fun next(): ReportPeriod = of(type, endDateExclusive)

    companion object {
        fun of(type: PeriodType, anchor: LocalDate): ReportPeriod {
            val start = when (type) {
                PeriodType.DAY -> anchor
                PeriodType.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                PeriodType.MONTH -> anchor.withDayOfMonth(1)
            }
            val end = when (type) {
                PeriodType.DAY -> start.plusDays(1)
                PeriodType.WEEK -> start.plusWeeks(1)
                PeriodType.MONTH -> start.plusMonths(1)
            }
            val key = when (type) {
                PeriodType.DAY -> "D$start"
                PeriodType.WEEK -> "W$start"
                PeriodType.MONTH -> "M${start.toString().substring(0, 7)}"
            }
            return ReportPeriod(type, start, end, BusinessTime.startOfDay(start), BusinessTime.startOfDay(end), key)
        }

        fun containing(type: PeriodType, instant: Instant): ReportPeriod = of(type, BusinessTime.localDateOf(instant))
    }
}
