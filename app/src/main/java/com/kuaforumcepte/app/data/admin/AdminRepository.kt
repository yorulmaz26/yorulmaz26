package com.kuaforumcepte.app.data.admin

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.core.data.FunctionsClient
import com.kuaforumcepte.app.data.salon.SalonProfile
import com.kuaforumcepte.app.data.salon.toSalon
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class PlatformAdmin(val uid: String, val phoneE164: String)

/** Platform yöneticisi (moderatör) işlemleri. Yetki sunucuda ve Firestore kurallarında da denetlenir. */
@Singleton
class AdminRepository @Inject constructor(private val firestore: Lazy<FirebaseFirestore>, private val functions: FunctionsClient) {

    /** Tüm salonlar (aktif/kapalı), en yeniden eskiye. */
    fun observeAllSalons(): Flow<List<SalonProfile>> =
        firestore.get().collection("salons").orderBy("createdAt", Query.Direction.DESCENDING).limit(300).snapshots()
            .map { qs -> qs.documents.mapNotNull { it.toSalon() } }
            .catch { throw AppException.from(it) }

    fun observeAdmins(): Flow<List<PlatformAdmin>> =
        firestore.get().collection("platformAdmins").snapshots()
            .map { qs -> qs.documents.filter { it.getBoolean("revoked") != true }.map { PlatformAdmin(it.id, it.getString("phoneE164").orEmpty()) } }
            .catch { emit(emptyList()) }

    /** Salonu platformdan kaldırır (kalıcı kapatma; aktif randevular gerekçeyle iptal edilir). */
    suspend fun removeSalon(salonId: String, reason: String): Int =
        ((functions.call("adminRemoveSalon", mapOf("salonId" to salonId, "reason" to reason.trim(), "confirm" to "KALDIR"))["cancelledAppointments"] as? Number)?.toInt()) ?: 0

    /** Telefon numarasıyla yönetici yetkisi verir/geri alır. Kişi uygulamaya en az bir kez giriş yapmış olmalı. */
    suspend fun setAdmin(phone: String, grant: Boolean) {
        functions.call("setPlatformAdmin", mapOf("phone" to phone, "grant" to grant))
    }
}
