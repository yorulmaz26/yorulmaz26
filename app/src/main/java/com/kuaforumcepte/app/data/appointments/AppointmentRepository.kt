package com.kuaforumcepte.app.data.appointments

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.core.data.FunctionsClient
import com.kuaforumcepte.app.core.data.str
import com.kuaforumcepte.domain.model.AppointmentStatus
import com.kuaforumcepte.domain.model.AppointmentTransitions
import com.kuaforumcepte.domain.model.Kurus
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class AppointmentEvent(val to: AppointmentStatus?, val actorRole: String, val reason: String?, val at: Instant?)

data class AppointmentItem(val serviceId: String, val name: String, val price: Kurus, val durationMinutes: Int)

data class Appointment(
    val id: String,
    val number: String,
    val salonId: String,
    val salonName: String,
    val salonAddress: String,
    val staffUid: String,
    val staffName: String,
    val customerUid: String,
    val customerName: String,
    val customerPhoneE164: String,
    val items: List<AppointmentItem>,
    val total: Kurus,
    val finalTotal: Kurus,
    val totalMinutes: Int,
    val startAt: Instant,
    val endAt: Instant,
    val status: AppointmentStatus,
    val pendingExpiresAt: Instant,
    val cancelReason: String?,
    val attentionReason: String?,
    val hiddenFor: List<String>,
    val reviewId: String? = null,
) {
    /** Zamanlayıcı henüz işlemediyse bile süresi dolmuş talep EXPIRED gösterilir. */
    fun effectiveStatus(now: Instant): AppointmentStatus =
        if (status == AppointmentStatus.PENDING && !now.isBefore(pendingExpiresAt)) AppointmentStatus.EXPIRED else status

    fun isActive(now: Instant): Boolean = effectiveStatus(now).isActive
    fun customerCanCancel(now: Instant): Boolean = isActive(now) && status != AppointmentStatus.IN_PROGRESS &&
        AppointmentTransitions.canCustomerCancel(now, startAt)
}

@Singleton
class AppointmentRepository @Inject constructor(
    private val firestore: Lazy<FirebaseFirestore>,
    private val functions: FunctionsClient,
) {
    private fun col() = firestore.get().collection("appointments")

    fun observe(id: String): Flow<Appointment?> = col().document(id).snapshots().map { it.toAppointment() }.catch { throw AppException.from(it) }

    /** Müşterinin bekleyen/onaylı/işlemdeki randevuları (tek aktif randevu kuralı için). */
    fun observeCustomerActive(uid: String): Flow<List<Appointment>> =
        col().whereEqualTo("customerUid", uid)
            .whereIn("status", listOf("PENDING", "CONFIRMED", "IN_PROGRESS"))
            .orderBy("startAt", Query.Direction.DESCENDING).snapshots()
            .map { qs -> qs.documents.mapNotNull { it.toAppointment() } }
            .catch { throw AppException.from(it) }

    fun observeCustomerHistory(uid: String, limit: Long = 50): Flow<List<Appointment>> =
        col().whereEqualTo("customerUid", uid).orderBy("startAt", Query.Direction.DESCENDING).limit(limit).snapshots()
            .map { qs -> qs.documents.mapNotNull { it.toAppointment() } }
            .catch { throw AppException.from(it) }

    /** Salon sahibi: salondaki tüm randevular [from, to). */
    fun observeSalonRange(salonId: String, from: Instant, to: Instant): Flow<List<Appointment>> =
        col().whereEqualTo("salonId", salonId)
            .whereGreaterThanOrEqualTo("startAt", com.google.firebase.Timestamp(java.util.Date.from(from)))
            .whereLessThan("startAt", com.google.firebase.Timestamp(java.util.Date.from(to)))
            .orderBy("startAt", Query.Direction.ASCENDING).limit(500).snapshots()
            .map { qs -> qs.documents.mapNotNull { it.toAppointment() } }
            .catch { throw AppException.from(it) }

    /** Uzman: yalnızca kendisine atanmış randevular (Firestore kuralı da bunu zorunlu kılar). */
    fun observeStaffRange(salonId: String, staffUid: String, from: Instant, to: Instant): Flow<List<Appointment>> =
        col().whereEqualTo("staffUid", staffUid).whereEqualTo("salonId", salonId)
            .whereGreaterThanOrEqualTo("startAt", com.google.firebase.Timestamp(java.util.Date.from(from)))
            .whereLessThan("startAt", com.google.firebase.Timestamp(java.util.Date.from(to)))
            .orderBy("startAt", Query.Direction.ASCENDING).limit(500).snapshots()
            .map { qs -> qs.documents.mapNotNull { it.toAppointment() } }
            .catch { throw AppException.from(it) }

    fun observeEvents(id: String): Flow<List<AppointmentEvent>> =
        col().document(id).collection("events").orderBy("at", Query.Direction.DESCENDING).snapshots()
            .map { qs ->
                qs.documents.map { d ->
                    AppointmentEvent(
                        to = AppointmentStatus.fromWire(d.getString("to")),
                        actorRole = d.getString("actorRole").orEmpty(),
                        reason = d.getString("reason"),
                        at = d.getTimestamp("at")?.toInstant(),
                    )
                }
            }
            .catch { emit(emptyList()) }

    suspend fun reassign(id: String, newStaffUid: String) {
        functions.call("reassignAppointment", mapOf("appointmentId" to id, "newStaffUid" to newStaffUid))
    }

    suspend fun reschedule(id: String, newStartAt: Instant) {
        functions.call("rescheduleAppointment", mapOf("appointmentId" to id, "newStartAt" to newStartAt.toString()))
    }

    /** Yalnızca kendi görünümünden gizler; kayıtlar silinmez. */
    suspend fun hide(ids: Collection<String>): Int =
        ((functions.call("hideAppointments", mapOf("appointmentIds" to ids.toList()))["hidden"] as? Number)?.toInt()) ?: 0

    /** Yetkili fiyat düzeltmesi (gerekçe zorunlu). İşlemdeyken uzman/sahip; tamamlandıysa yalnızca sahip. */
    suspend fun adjustPrice(id: String, newFinal: Kurus, reason: String) {
        functions.call("adjustAppointmentPrice", mapOf("appointmentId" to id, "newFinalKurus" to newFinal.value, "reason" to reason.trim()))
    }

    /** @return yeni durum; süresi dolmuş taleplerde "EXPIRED". */
    suspend fun transition(id: String, action: String, reason: String? = null): AppointmentStatus {
        val r = functions.call("transitionAppointment", mapOf("appointmentId" to id, "action" to action, "reason" to reason))
        return AppointmentStatus.fromWire(r.str("status")) ?: AppointmentStatus.EXPIRED
    }
}

@Suppress("UNCHECKED_CAST")
internal fun DocumentSnapshot.toAppointment(): Appointment? {
    if (!exists()) return null
    val status = AppointmentStatus.fromWire(getString("status")) ?: return null
    return Appointment(
        id = id,
        number = getString("number").orEmpty(),
        salonId = getString("salonId").orEmpty(),
        salonName = getString("salonName").orEmpty(),
        salonAddress = getString("salonAddress").orEmpty(),
        staffUid = getString("staffUid").orEmpty(),
        staffName = getString("staffName").orEmpty(),
        customerUid = getString("customerUid").orEmpty(),
        customerName = getString("customerName").orEmpty(),
        customerPhoneE164 = getString("customerPhoneE164").orEmpty(),
        items = (get("items") as? List<Map<String, Any?>>).orEmpty().map {
            AppointmentItem(it.str("serviceId").orEmpty(), it.str("name").orEmpty(), Kurus((it["priceKurus"] as? Number)?.toLong() ?: 0), (it["durationMinutes"] as? Number)?.toInt() ?: 0)
        },
        total = Kurus(getLong("totalKurus") ?: 0),
        finalTotal = Kurus(getLong("finalKurus") ?: getLong("totalKurus") ?: 0),
        totalMinutes = (getLong("totalMinutes") ?: 0).toInt(),
        startAt = getTimestamp("startAt")?.toInstant() ?: Instant.EPOCH,
        endAt = getTimestamp("endAt")?.toInstant() ?: Instant.EPOCH,
        status = status,
        pendingExpiresAt = getTimestamp("pendingExpiresAt")?.toInstant() ?: Instant.EPOCH,
        cancelReason = getString("cancelReason"),
        attentionReason = (get("attention") as? Map<String, Any?>)?.str("reason"),
        hiddenFor = (get("hiddenFor") as? List<String>).orEmpty(),
        reviewId = getString("reviewId"),
    )
}
