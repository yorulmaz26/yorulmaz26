package com.kuaforumcepte.app.data.services

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.core.data.FunctionsClient
import com.kuaforumcepte.domain.model.Kurus
import com.kuaforumcepte.domain.text.SearchKey
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class StaffService(
    val id: String,
    val staffUid: String,
    val name: String,
    val description: String,
    val price: Kurus,
    val durationMinutes: Int,
    val active: Boolean,
)

data class ServiceDraft(val name: String, val description: String, val price: Kurus, val durationMinutes: Int, val active: Boolean)

/**
 * Uzmanın kendi hizmetleri. Firestore kuralları yalnızca staffUid == kendi uid'si olan
 * hizmetlere ve geçerli fiyat/süre sınırlarına izin verir; silme yumuşak silmedir
 * (geçmiş randevu snapshot'ları etkilenmez).
 */
@Singleton
class ServiceRepository @Inject constructor(
    private val firestore: Lazy<FirebaseFirestore>,
    private val functions: FunctionsClient,
) {
    private fun col(salonId: String) = firestore.get().collection("salons").document(salonId).collection("services")

    fun observeMine(salonId: String, uid: String): Flow<List<StaffService>> =
        col(salonId).whereEqualTo("staffUid", uid).whereEqualTo("deleted", false)
            .orderBy("nameLower", Query.Direction.ASCENDING).snapshots()
            .map { qs ->
                qs.documents.map { d ->
                    StaffService(
                        id = d.id,
                        staffUid = d.getString("staffUid").orEmpty(),
                        name = d.getString("name").orEmpty(),
                        description = d.getString("description").orEmpty(),
                        price = Kurus(d.getLong("priceKurus") ?: 0),
                        durationMinutes = (d.getLong("durationMinutes") ?: 0).toInt(),
                        active = d.getBoolean("active") == true,
                    )
                }
            }
            .catch { throw AppException.from(it) }

    private fun fields(salonId: String, d: ServiceDraft): Map<String, Any> = mapOf(
        "salonId" to salonId,
        "name" to d.name.trim(),
        "nameLower" to SearchKey.of(d.name),
        "description" to d.description.trim(),
        "priceKurus" to d.price.value,
        "durationMinutes" to d.durationMinutes,
        "active" to d.active,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    suspend fun create(salonId: String, uid: String, draft: ServiceDraft) = write {
        col(salonId).document().set(fields(salonId, draft) + mapOf("staffUid" to uid, "deleted" to false, "createdAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun update(salonId: String, serviceId: String, draft: ServiceDraft) = write {
        col(salonId).document(serviceId).update(fields(salonId, draft)).await()
    }

    suspend fun setActive(salonId: String, serviceId: String, active: Boolean) = write {
        col(salonId).document(serviceId).update(mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    /** Yumuşak silme: listeden kalkar, yeni randevuda seçilemez; geçmiş randevular korunur. */
    suspend fun delete(salonId: String, serviceId: String) = write {
        col(salonId).document(serviceId).update(mapOf("deleted" to true, "active" to false, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun futureAppointmentCount(salonId: String, serviceId: String): Int =
        ((functions.call("serviceImpact", mapOf("salonId" to salonId, "serviceId" to serviceId))["futureAppointments"] as? Number)?.toInt()) ?: 0

    private suspend fun write(block: suspend () -> Unit) = try {
        block()
    } catch (t: Throwable) {
        throw AppException.from(t)
    }
}
