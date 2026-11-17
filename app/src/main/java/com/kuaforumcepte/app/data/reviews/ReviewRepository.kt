package com.kuaforumcepte.app.data.reviews

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.core.data.FunctionsClient
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Kamuya açık değerlendirme (müşteri kimliği içermez; yazar adı kısaltılmış). */
data class Review(
    val id: String,
    val staffName: String,
    val serviceNames: List<String>,
    val salonRating: Int,
    val staffRating: Int,
    val serviceRating: Int?,
    val comment: String,
    val author: String,
    val createdAt: Instant?,
)

@Singleton
class ReviewRepository @Inject constructor(
    private val firestore: Lazy<FirebaseFirestore>,
    private val functions: FunctionsClient,
) {
    fun observeSalon(salonId: String, limit: Long = 10): Flow<List<Review>> =
        firestore.get().collection("reviews").whereEqualTo("salonId", salonId)
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(limit).snapshots()
            .map { qs ->
                qs.documents.map { d ->
                    @Suppress("UNCHECKED_CAST")
                    Review(
                        id = d.id,
                        staffName = d.getString("staffName").orEmpty(),
                        serviceNames = (d.get("serviceNames") as? List<String>).orEmpty(),
                        salonRating = (d.getLong("salonRating") ?: 0).toInt(),
                        staffRating = (d.getLong("staffRating") ?: 0).toInt(),
                        serviceRating = d.getLong("serviceRating")?.toInt(),
                        comment = d.getString("comment").orEmpty(),
                        author = d.getString("authorDisplay").orEmpty(),
                        createdAt = d.getTimestamp("createdAt")?.toInstant(),
                    )
                }
            }
            .catch { throw AppException.from(it) }

    suspend fun submit(appointmentId: String, salonRating: Int, staffRating: Int, serviceRating: Int?, comment: String) {
        functions.call(
            "submitReview",
            mapOf(
                "appointmentId" to appointmentId, "salonRating" to salonRating, "staffRating" to staffRating,
                "serviceRating" to serviceRating, "comment" to comment.trim(),
            ),
        )
    }
}
