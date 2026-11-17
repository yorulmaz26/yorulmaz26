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
data class CreatedAppointment(val id: String, val number: String, val pendingExpiresAt: Instant, val finalTotal: Kurus)

data class LoyaltyStatus(val rewardsAvailable: Int, val rewardPercent: Int, val visitsRequired: Int, val completedCount: Int, val visitsToNext: Int?)

/** Rezervasyon özetindeki fiyat önizlemesi (bağlayıcı değildir; sunucu randevuda yeniden hesaplar). */
data class PricePreview(val total: Kurus, val discount: Kurus, val finalTotal: Kurus, val codeError: String?, val loyalty: LoyaltyStatus?)

data class WaitlistEntry(val id: String, val salonId: String, val salonName: String, val staffUid: String, val staffName: String, val date: LocalDate)

@Singleton
class BookingRepository @Inject constructor(private val functions: FunctionsClient) {

    /** forSelf=false: başkası adına randevu; müşterinin kendi meşgul saatleri gizlenmez. */
    suspend fun availability(salonId: String, staffUid: String, serviceIds: List<String>, from: LocalDate, days: Int, forSelf: Boolean = true): Availability {
        return parse(
            functions.call(
                "getAvailability",
                mapOf("salonId" to salonId, "staffUid" to staffUid, "serviceIds" to serviceIds, "fromDate" to from.toString(), "days" to days, "forSelf" to forSelf),
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
     * Sunucu fiyatı, müsaitliği, indirim kodunu/sadakat ödülünü transaction içinde yeniden doğrular. Hata nedenleri:
     * PRICE_CHANGED {newTotalKurus}, SLOT_TAKEN, SLOT_UNAVAILABLE, CUSTOMER_BUSY, ACTIVE_APPOINTMENT_LIMIT,
     * CODE_* / LOYALTY_NOT_AVAILABLE, SERVICE_INACTIVE, STAFF_INACTIVE.
     */
    suspend fun create(
        salonId: String, staffUid: String, serviceIds: List<String>, startAt: Instant, expectedTotal: Kurus, idempotencyKey: String,
        guestName: String? = null, discountCode: String? = null, useLoyaltyReward: Boolean = false,
    ): CreatedAppointment {
        val r = functions.call(
            "createAppointment",
            mapOf(
                "salonId" to salonId, "staffUid" to staffUid, "serviceIds" to serviceIds, "startAt" to startAt.toString(),
                "expectedTotalKurus" to expectedTotal.value, "idempotencyKey" to idempotencyKey,
                "guestName" to guestName?.trim()?.ifBlank { null },
                "discountCode" to discountCode?.trim()?.ifBlank { null },
                "useLoyaltyReward" to useLoyaltyReward,
            ),
        )
        return CreatedAppointment(
            r.str("appointmentId").orEmpty(), r.str("number").orEmpty(), Instant.parse(r.str("pendingExpiresAt")),
            Kurus((r["finalKurus"] as? Number)?.toLong() ?: expectedTotal.value),
        )
    }

    suspend fun preview(salonId: String, staffUid: String, serviceIds: List<String>, discountCode: String?, useLoyaltyReward: Boolean): PricePreview {
        val r = functions.call(
            "previewBookingPrice",
            mapOf("salonId" to salonId, "staffUid" to staffUid, "serviceIds" to serviceIds, "discountCode" to discountCode?.trim()?.ifBlank { null }, "useLoyaltyReward" to useLoyaltyReward),
        )
        @Suppress("UNCHECKED_CAST")
        val l = r["loyalty"] as? Map<String, Any?>
        return PricePreview(
            total = Kurus((r["totalKurus"] as Number).toLong()),
            discount = Kurus((r["discountKurus"] as Number).toLong()),
            finalTotal = Kurus((r["finalKurus"] as Number).toLong()),
            codeError = r.str("codeError"),
            loyalty = l?.let {
                LoyaltyStatus(
                    (it["rewardsAvailable"] as Number).toInt(), (it["rewardPercent"] as Number).toInt(), (it["visitsRequired"] as Number).toInt(),
                    (it["completedCount"] as Number).toInt(), (it["visitsToNext"] as? Number)?.toInt(),
                )
            },
        )
    }

    /** Seçilen uzmanın o gününde yer açılırsa bildirim alınır (yer ayrılmaz, ilk gelen alır). */
    suspend fun joinWaitlist(salonId: String, staffUid: String, date: LocalDate) {
        functions.call("joinWaitlist", mapOf("salonId" to salonId, "staffUid" to staffUid, "localDate" to date.toString()))
    }

    suspend fun leaveWaitlist(id: String) {
        functions.call("leaveWaitlist", mapOf("id" to id))
    }
}
