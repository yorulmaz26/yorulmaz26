package com.kuaforumcepte.app.data.booking

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.data.salon.SalonProfile
import com.kuaforumcepte.app.data.salon.toSalon
import com.kuaforumcepte.domain.model.SalonType
import com.kuaforumcepte.domain.text.SearchKey
import dagger.Lazy
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

enum class SalonSort(val label: String) { NAME("Ada göre"), RATING("Puana göre") }

data class SalonListQuery(
    val search: String = "",
    val sort: SalonSort = SalonSort.NAME,
    val type: SalonType? = null,
    val city: String? = null,
    val district: String? = null,
) {
    val searchKey: String get() = SearchKey.of(search)
    /** Arama ön ek eşleşmesi ada göre sıralama gerektirir (Firestore aralık sorgusu). */
    val effectiveSort: SalonSort get() = if (searchKey.isNotEmpty()) SalonSort.NAME else sort
}

class SalonPage(val items: List<SalonProfile>, val cursor: DocumentSnapshot?, val hasMore: Boolean)

/**
 * "Kayıtlı Kuaförlerimiz" listesi: yalnızca ACTIVE salonlar, sayfalı (startAfter imleci).
 * Puana göre sıralamada eşit/boş puanlar ad ve belge kimliğiyle deterministik sıralanır;
 * puanı olmayan ("Yeni") salonlar en sonda gelir.
 */
@Singleton
class SalonDirectoryRepository @Inject constructor(private val firestore: Lazy<FirebaseFirestore>) {

    suspend fun page(q: SalonListQuery, after: DocumentSnapshot?, size: Long = PAGE_SIZE): SalonPage = try {
        var query: Query = firestore.get().collection("salons").whereEqualTo("status", "ACTIVE")
        q.type?.let { query = query.whereEqualTo("type", it.name) }
        q.city?.let { query = query.whereEqualTo("city", it) }
        q.district?.let { query = query.whereEqualTo("district", it) }
        val key = q.searchKey
        if (key.isNotEmpty()) {
            query = query.whereGreaterThanOrEqualTo("nameLower", key).whereLessThan("nameLower", key + "")
        }
        query = when (q.effectiveSort) {
            SalonSort.NAME -> query.orderBy("nameLower").orderBy(FieldPath.documentId())
            SalonSort.RATING -> query.orderBy("ratingAvg", Query.Direction.DESCENDING).orderBy("nameLower").orderBy(FieldPath.documentId())
        }
        if (after != null) query = query.startAfter(after)
        val snap = query.limit(size).get().await()
        SalonPage(snap.documents.mapNotNull { it.toSalon() }, snap.documents.lastOrNull(), snap.size().toLong() == size)
    } catch (t: Throwable) {
        throw AppException.from(t)
    }

    companion object {
        const val PAGE_SIZE = 20L
    }
}
