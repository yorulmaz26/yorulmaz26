package com.kuaforumcepte.app.data.notifications

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.kuaforumcepte.app.core.data.AppException
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class InboxItem(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val appointmentId: String?,
    val salonId: String?,
    val read: Boolean,
    val createdAt: Instant?,
)

/** Uygulama içi bildirim kutusu: push izni/ulaşımından bağımsız tek doğru kaynak. */
@Singleton
class NotificationRepository @Inject constructor(private val firestore: Lazy<FirebaseFirestore>) {
    private fun col(uid: String) = firestore.get().collection("users").document(uid).collection("notifications")

    fun observe(uid: String, limit: Long = 100): Flow<List<InboxItem>> =
        col(uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(limit).snapshots()
            .map { qs ->
                qs.documents.map { d ->
                    InboxItem(
                        id = d.id,
                        type = d.getString("type").orEmpty(),
                        title = d.getString("title").orEmpty(),
                        body = d.getString("body").orEmpty(),
                        appointmentId = d.getString("appointmentId")?.takeIf { it.isNotBlank() },
                        salonId = d.getString("salonId"),
                        read = d.getBoolean("read") == true,
                        createdAt = d.getTimestamp("createdAt")?.toInstant(),
                    )
                }
            }
            .catch { throw AppException.from(it) }

    fun unreadCount(uid: String): Flow<Int> =
        col(uid).whereEqualTo("read", false).limit(99).snapshots().map { it.size() }.catch { emit(0) }

    suspend fun markRead(uid: String, ids: Collection<String>) {
        if (ids.isEmpty()) return
        try {
            ids.chunked(400).forEach { chunk ->
                val batch = firestore.get().batch()
                chunk.forEach { batch.update(col(uid).document(it), "read", true) }
                batch.commit().await()
            }
        } catch (t: Throwable) {
            throw AppException.from(t)
        }
    }
}
