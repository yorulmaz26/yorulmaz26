package com.kuaforumcepte.app.data.favorites

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.data.booking.WaitlistEntry
import com.kuaforumcepte.app.data.salon.SalonProfile
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class FavoriteSalon(val salonId: String, val salonName: String, val city: String?, val district: String?)

/**
 * Favori salonlar (users/{uid}/favorites — istemci yazar, kurallar alanları doğrular) ve
 * müşterinin bekleme listesi kayıtları (yalnızca okunur; ekleme/çıkarma sunucu fonksiyonuyla).
 */
@Singleton
class FavoritesRepository @Inject constructor(private val firestore: Lazy<FirebaseFirestore>) {
    private fun col(uid: String) = firestore.get().collection("users").document(uid).collection("favorites")

    fun observe(uid: String): Flow<List<FavoriteSalon>> =
        col(uid).orderBy("createdAt", Query.Direction.DESCENDING).snapshots()
            .map { qs -> qs.documents.map { FavoriteSalon(it.id, it.getString("salonName").orEmpty(), it.getString("city"), it.getString("district")) } }
            .catch { emit(emptyList()) }

    fun isFavorite(uid: String, salonId: String): Flow<Boolean> =
        col(uid).document(salonId).snapshots().map { it.exists() }.catch { emit(false) }

    suspend fun add(uid: String, salon: SalonProfile) = try {
        col(uid).document(salon.id).set(
            mapOf("salonId" to salon.id, "salonName" to salon.name.take(80), "city" to salon.city, "district" to salon.district, "createdAt" to FieldValue.serverTimestamp()),
        ).await()
        Unit
    } catch (t: Throwable) { throw AppException.from(t) }

    suspend fun remove(uid: String, salonId: String) = try {
        col(uid).document(salonId).delete().await()
        Unit
    } catch (t: Throwable) { throw AppException.from(t) }

    fun observeWaitlist(uid: String, today: LocalDate): Flow<List<WaitlistEntry>> =
        firestore.get().collection("waitlist").whereEqualTo("customerUid", uid).whereGreaterThanOrEqualTo("localDate", today.toString())
            .orderBy("localDate").snapshots()
            .map { qs ->
                qs.documents.map { d ->
                    WaitlistEntry(
                        d.id, d.getString("salonId").orEmpty(), d.getString("salonName").orEmpty(), d.getString("staffUid").orEmpty(),
                        d.getString("staffName").orEmpty(), LocalDate.parse(d.getString("localDate")),
                    )
                }
            }
            .catch { throw AppException.from(it) }
}
