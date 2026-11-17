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
    val salonId: String = "",
    val reportCount: Int = 0,
    val flagged: Boolean = false,
    val hidden: Boolean = false,
    /** Salon sahibinin herkese açık yanıtı. */
    val ownerReply: String? = null,
    val ownerReplyAt: Instant? = null,
)

enum class ReportReason(val label: String) {
    OFFENSIVE("Hakaret / uygunsuz dil"),
    SPAM("Spam veya reklam"),
    FALSE_INFO("Yanıltıcı / gerçek dışı"),
    PERSONAL_DATA("Kişisel veri içeriyor"),
    OTHER("Diğer"),
}

data class ReviewReport(val reason: ReportReason?, val note: String, val createdAt: Instant?)

@Singleton
class ReviewRepository @Inject constructor(
    private val firestore: Lazy<FirebaseFirestore>,
    private val functions: FunctionsClient,
) {
    private fun reviews() = firestore.get().collection("reviews")

    /** Yalnızca yayındaki (gizlenmemiş) yorumlar; Firestore kuralı da gizlenenleri yöneticiye kısıtlar. */
    fun observeSalon(salonId: String, limit: Long = 10): Flow<List<Review>> =
        reviews().whereEqualTo("salonId", salonId).whereEqualTo("hidden", false)
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(limit).snapshots()
            .map { qs -> qs.documents.map { it.toReview() } }
            .catch { throw AppException.from(it) }

    /** Yönetici: bir salonun tüm yorumları (kaldırılanlar dahil), en yeniden eskiye. */
    fun observeSalonAll(salonId: String): Flow<List<Review>> =
        reviews().whereEqualTo("salonId", salonId).orderBy("createdAt", Query.Direction.DESCENDING).limit(200).snapshots()
            .map { qs -> qs.documents.map { it.toReview() } }
            .catch { throw AppException.from(it) }

    /** Yönetici: şikâyet eşiğini aşan, yayındaki yorumlar. */
    fun observeFlagged(): Flow<List<Review>> =
        reviews().whereEqualTo("flagged", true).whereEqualTo("hidden", false)
            .orderBy("reportCount", Query.Direction.DESCENDING).limit(100).snapshots()
            .map { qs -> qs.documents.map { it.toReview() } }
            .catch { throw AppException.from(it) }

    /** Yönetici: moderasyonla gizlenmiş yorumlar. */
    fun observeHidden(): Flow<List<Review>> =
        reviews().whereEqualTo("hidden", true).orderBy("moderatedAt", Query.Direction.DESCENDING).limit(100).snapshots()
            .map { qs -> qs.documents.map { it.toReview() } }
            .catch { throw AppException.from(it) }

    fun observeReports(reviewId: String): Flow<List<ReviewReport>> =
        firestore.get().collection("reviewReports").whereEqualTo("reviewId", reviewId)
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(20).snapshots()
            .map { qs ->
                qs.documents.map { d ->
                    ReviewReport(ReportReason.entries.firstOrNull { it.name == d.getString("reason") }, d.getString("note").orEmpty(), d.getTimestamp("createdAt")?.toInstant())
                }
            }
            .catch { emit(emptyList()) }

    suspend fun report(reviewId: String, reason: ReportReason, note: String) {
        functions.call("reportReview", mapOf("reviewId" to reviewId, "reason" to reason.name, "note" to note.trim()))
    }

    /** action: HIDE | RESTORE | DISMISS (yalnızca platform yöneticisi). */
    /** Salon sahibinin yanıtı; boş metin yanıtı kaldırır. */
    suspend fun reply(reviewId: String, text: String) {
        functions.call("replyToReview", mapOf("reviewId" to reviewId, "text" to text.trim()))
    }

    suspend fun moderate(reviewId: String, action: String, note: String) {
        functions.call("moderateReview", mapOf("reviewId" to reviewId, "action" to action, "note" to note.trim()))
    }

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

@Suppress("UNCHECKED_CAST")
private fun com.google.firebase.firestore.DocumentSnapshot.toReview() = Review(
    id = id,
    staffName = getString("staffName").orEmpty(),
    serviceNames = (get("serviceNames") as? List<String>).orEmpty(),
    salonRating = (getLong("salonRating") ?: 0).toInt(),
    staffRating = (getLong("staffRating") ?: 0).toInt(),
    serviceRating = getLong("serviceRating")?.toInt(),
    comment = getString("comment").orEmpty(),
    author = getString("authorDisplay").orEmpty(),
    createdAt = getTimestamp("createdAt")?.toInstant(),
    salonId = getString("salonId").orEmpty(),
    reportCount = (getLong("reportCount") ?: 0).toInt(),
    flagged = getBoolean("flagged") == true,
    hidden = getBoolean("hidden") == true,
    ownerReply = (get("ownerReply") as? Map<String, Any?>)?.get("text") as? String,
    ownerReplyAt = ((get("ownerReply") as? Map<String, Any?>)?.get("at") as? com.google.firebase.Timestamp)?.toInstant(),
)
