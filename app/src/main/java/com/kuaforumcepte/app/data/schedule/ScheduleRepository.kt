package com.kuaforumcepte.app.data.schedule

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.core.data.FunctionsClient
import com.kuaforumcepte.domain.schedule.DateClosure
import com.kuaforumcepte.domain.schedule.DaySchedule
import com.kuaforumcepte.domain.schedule.TimeRange
import com.kuaforumcepte.domain.schedule.WeeklySchedule
import com.kuaforumcepte.domain.time.BusinessTime
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Takvim değişikliğinin etkilediği mevcut randevu. */
data class ScheduleConflict(
    val appointmentId: String,
    val localDate: LocalDate,
    val start: String,
    val end: String,
    val customerName: String,
    val reason: String,
) {
    val reasonLabel: String
        get() = when (reason) {
            "SALON_CLOSED_DAY" -> "Salon o gün kapalı olacak"
            "SALON_CLOSURE" -> "Salon tarih kapatması"
            "STAFF_DAY_OFF" -> "Uzmanın haftalık izin günü"
            "STAFF_CLOSURE" -> "Uzmanın kapattığı tarih"
            "OVERLAPS_BREAK" -> "Molayla çakışıyor"
            else -> "Çalışma saatleri dışında kalıyor"
        }
}

/** Önizleme veya uygulama sonucu; applied=false ve conflicts doluysa kullanıcı onayı beklenir. */
data class ChangeResult(val applied: Boolean, val conflicts: List<ScheduleConflict>)

@Singleton
class ScheduleRepository @Inject constructor(
    private val firestore: Lazy<FirebaseFirestore>,
    private val functions: FunctionsClient,
    private val clock: Clock,
) {
    private fun salon(salonId: String) = firestore.get().collection("salons").document(salonId)
    private fun member(salonId: String, uid: String) = salon(salonId).collection("members").document(uid)
    private fun today(): LocalDate = LocalDate.now(clock.withZone(BusinessTime.ZONE))

    fun observeWeekly(salonId: String, uid: String): Flow<WeeklySchedule> =
        member(salonId, uid).collection("schedule").document("weekly").snapshots()
            .map { it.toWeekly() }
            .catch { throw AppException.from(it) }

    fun observeStaffClosures(salonId: String, uid: String): Flow<List<DateClosure>> =
        closures(member(salonId, uid).collection("closures"), "note")

    fun observeSalonClosures(salonId: String): Flow<List<DateClosure>> =
        closures(salon(salonId).collection("closures"), "publicNote")

    private fun closures(col: com.google.firebase.firestore.CollectionReference, noteField: String): Flow<List<DateClosure>> =
        col.whereGreaterThanOrEqualTo("endDate", today().toString()).orderBy("endDate", Query.Direction.ASCENDING).snapshots()
            .map { qs ->
                qs.documents.mapNotNull { d ->
                    runCatching {
                        DateClosure(d.id, LocalDate.parse(d.getString("startDate")), LocalDate.parse(d.getString("endDate")), d.getString(noteField).orEmpty())
                    }.getOrNull()
                }.sortedBy { it.startDate }
            }
            .catch { throw AppException.from(it) }

    // ---- Değişiklikler (sunucu çakışma analizi yapar) ----

    suspend fun updateWeekly(salonId: String, schedule: WeeklySchedule, dryRun: Boolean, acknowledge: Boolean): ChangeResult =
        change("updateWeeklySchedule", mapOf("salonId" to salonId, "days" to schedule.toWire(), "weeklyDaysOff" to schedule.weeklyDaysOff.sorted()), dryRun, acknowledge)

    suspend fun upsertStaffClosure(salonId: String, closureId: String?, start: LocalDate, end: LocalDate, note: String, dryRun: Boolean, acknowledge: Boolean) =
        change("upsertStaffDateClosure", closurePayload(salonId, closureId, start, end, note), dryRun, acknowledge)

    suspend fun deleteStaffClosure(salonId: String, closureId: String) {
        functions.call("deleteStaffDateClosure", mapOf("salonId" to salonId, "closureId" to closureId))
    }

    suspend fun updateSalonClosedDays(salonId: String, days: Set<Int>, dryRun: Boolean, acknowledge: Boolean) =
        change("updateSalonClosedDays", mapOf("salonId" to salonId, "weeklyClosedDays" to days.sorted()), dryRun, acknowledge)

    suspend fun upsertSalonClosure(salonId: String, closureId: String?, start: LocalDate, end: LocalDate, note: String, dryRun: Boolean, acknowledge: Boolean) =
        change("upsertSalonDateClosure", closurePayload(salonId, closureId, start, end, note), dryRun, acknowledge)

    suspend fun deleteSalonClosure(salonId: String, closureId: String) {
        functions.call("deleteSalonDateClosure", mapOf("salonId" to salonId, "closureId" to closureId))
    }

    private fun closurePayload(salonId: String, closureId: String?, start: LocalDate, end: LocalDate, note: String) = mapOf(
        "salonId" to salonId, "closureId" to closureId, "startDate" to start.toString(), "endDate" to end.toString(), "note" to note.trim(),
    )

    private suspend fun change(name: String, payload: Map<String, Any?>, dryRun: Boolean, acknowledge: Boolean): ChangeResult = try {
        val r = functions.call(name, payload + mapOf("dryRun" to dryRun, "acknowledgeConflicts" to acknowledge))
        ChangeResult(r["applied"] == true, parseConflicts(r["conflicts"]))
    } catch (e: AppException) {
        // Önizleme ile uygulama arasında yeni bir randevu çakışmaya girdiyse tekrar onay istenir.
        if (e.reason == "CONFLICTS_NOT_ACKNOWLEDGED") ChangeResult(false, parseConflicts(e.details["conflicts"])) else throw e
    }

    private fun parseConflicts(raw: Any?): List<ScheduleConflict> = (raw as? List<*>).orEmpty().mapNotNull { item ->
        val m = item as? Map<*, *> ?: return@mapNotNull null
        ScheduleConflict(
            appointmentId = m["appointmentId"] as? String ?: return@mapNotNull null,
            localDate = runCatching { LocalDate.parse(m["localDate"] as String) }.getOrNull() ?: return@mapNotNull null,
            start = m["start"] as? String ?: "",
            end = m["end"] as? String ?: "",
            customerName = m["customerName"] as? String ?: "",
            reason = m["reason"] as? String ?: "",
        )
    }
}

private fun WeeklySchedule.toWire(): Map<String, Any> = (1..7).associate { d ->
    val day = day(d)
    d.toString() to mapOf(
        "shifts" to day.shifts.map { mapOf("start" to TimeRange.hm(it.start), "end" to TimeRange.hm(it.end)) },
        "breaks" to day.breaks.map { mapOf("start" to TimeRange.hm(it.start), "end" to TimeRange.hm(it.end)) },
    )
}

@Suppress("UNCHECKED_CAST")
internal fun DocumentSnapshot.toWeekly(): WeeklySchedule {
    if (!exists()) return WeeklySchedule.default()
    val days = (get("days") as? Map<String, Any?>).orEmpty()
    fun ranges(raw: Any?): List<TimeRange> = (raw as? List<Map<String, Any?>>).orEmpty().mapNotNull { r ->
        val s = TimeRange.parseHm(r["start"] as? String ?: return@mapNotNull null) ?: return@mapNotNull null
        val e = TimeRange.parseHm(r["end"] as? String ?: return@mapNotNull null, allowEndOfDay = true) ?: return@mapNotNull null
        TimeRange(s, e)
    }
    return WeeklySchedule(
        days = (1..7).associateWith { d ->
            val day = days[d.toString()] as? Map<String, Any?>
            DaySchedule(ranges(day?.get("shifts")), ranges(day?.get("breaks")))
        },
        weeklyDaysOff = (get("weeklyDaysOff") as? List<Number>).orEmpty().map { it.toInt() }.toSet(),
    )
}
