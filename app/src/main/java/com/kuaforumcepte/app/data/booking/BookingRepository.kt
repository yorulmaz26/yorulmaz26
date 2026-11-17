package com.kuaforumcepte.app.data.booking

import com.kuaforumcepte.app.core.data.FunctionsClient
import com.kuaforumcepte.app.core.data.list
import com.kuaforumcepte.app.core.data.str
import com.kuaforumcepte.domain.model.Kurus
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class Slot(val startAt: Instant, val label: String)
data class AvailabilityDay(val date: LocalDate, val slots: List<Slot>)
data class Availability(val total: Kurus, val totalMinutes: Int, val horizonDays: Int, val days: List<AvailabilityDay>)
data class CreatedAppointment(val id: String, val number: String, val pendingExpiresAt: Instant)

@Singleton
class BookingRepository @Inject constructor(private val functions: FunctionsClient) {

    suspend fun availability(salonId: String, staffUid: String, serviceIds: List<String>, from: LocalDate, days: Int): Availability {
        return parse(
            functions.call(
                "getAvailability",
                mapOf("salonId" to salonId, "staffUid" to staffUid, "serviceIds" to serviceIds, "fromDate" to from.toString(), "days" to days),
            ),
        )
    }

    private fun parse(r: Map<String, Any?>): Availability {
        return Availability(
            total = Kurus((r["totalKurus"] as Number).toLong()),
            totalMinutes = (r["totalMinutes"] as Number).toInt(),
            horizonDays = (r["horizonDays"] as? Number)?.toInt() ?: 60,
            days = r.list("days").map { d ->
                AvailabilityDay(
                    LocalDate.parse(d.str("date")),
                    d.list("slots").map { s -> Slot(Instant.parse(s.str("startAt")), s.str("label").orEmpty()) },
                )
            },
        )
    }

    /** Yeniden planlama: mevcut randevunun süresiyle, kendisi hariç müsaitlik. */
    suspend fun availabilityForReschedule(appointmentId: String, from: LocalDate, days: Int): Availability =
        parse(functions.call("getAvailability", mapOf("appointmentId" to appointmentId, "fromDate" to from.toString(), "days" to days)))

    /**
     * Sunucu fiyatı ve müsaitliği transaction içinde yeniden doğrular. Hata nedenleri:
     * PRICE_CHANGED {newTotalKurus}, SLOT_TAKEN, SLOT_UNAVAILABLE, ACTIVE_APPOINTMENT_EXISTS {appointmentId}, SERVICE_INACTIVE, STAFF_INACTIVE.
     */
    suspend fun create(salonId: String, staffUid: String, serviceIds: List<String>, startAt: Instant, expectedTotal: Kurus, idempotencyKey: String): CreatedAppointment {
        val r = functions.call(
            "createAppointment",
            mapOf(
                "salonId" to salonId, "staffUid" to staffUid, "serviceIds" to serviceIds, "startAt" to startAt.toString(),
                "expectedTotalKurus" to expectedTotal.value, "idempotencyKey" to idempotencyKey,
            ),
        )
        return CreatedAppointment(r.str("appointmentId").orEmpty(), r.str("number").orEmpty(), Instant.parse(r.str("pendingExpiresAt")))
    }
}
