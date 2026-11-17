package com.kuaforumcepte.app.data.promo

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.core.data.FunctionsClient
import com.kuaforumcepte.domain.model.Kurus
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class DiscountCode(
    val code: String,
    val description: String?,
    val percent: Int?,
    val amount: Kurus?,
    val validFrom: LocalDate?,
    val validUntil: LocalDate?,
    val maxUses: Int?,
    val usedCount: Int,
    val active: Boolean,
) {
    val valueLabel: String get() = percent?.let { "%$it" } ?: amount?.formatTry().orEmpty()
}

/** Salon indirim kodları (sahip yönetir; müşteri kodu bilerek girer, liste görmez). */
@Singleton
class PromoRepository @Inject constructor(private val firestore: Lazy<FirebaseFirestore>, private val functions: FunctionsClient) {

    fun observeCodes(salonId: String): Flow<List<DiscountCode>> =
        firestore.get().collection("salons").document(salonId).collection("discountCodes")
            .orderBy("updatedAt", Query.Direction.DESCENDING).snapshots()
            .map { qs ->
                qs.documents.map { d ->
                    DiscountCode(
                        code = d.id,
                        description = d.getString("description"),
                        percent = d.getLong("percent")?.toInt(),
                        amount = d.getLong("amountKurus")?.let { Kurus(it) },
                        validFrom = d.getString("validFrom")?.let(LocalDate::parse),
                        validUntil = d.getString("validUntil")?.let(LocalDate::parse),
                        maxUses = d.getLong("maxUses")?.toInt(),
                        usedCount = (d.getLong("usedCount") ?: 0).toInt(),
                        active = d.getBoolean("active") == true,
                    )
                }
            }
            .catch { throw AppException.from(it) }

    suspend fun upsert(salonId: String, c: DiscountCode) {
        functions.call(
            "upsertDiscountCode",
            mapOf(
                "salonId" to salonId, "code" to c.code, "description" to c.description, "percent" to c.percent, "amountKurus" to c.amount?.value,
                "validFrom" to c.validFrom?.toString(), "validUntil" to c.validUntil?.toString(), "maxUses" to c.maxUses, "active" to c.active,
            ),
        )
    }
}
