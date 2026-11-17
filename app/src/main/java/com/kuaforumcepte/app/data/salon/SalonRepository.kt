package com.kuaforumcepte.app.data.salon

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.core.data.FunctionsClient
import com.kuaforumcepte.app.data.media.StorageUploader
import com.kuaforumcepte.domain.model.Rating
import com.kuaforumcepte.domain.model.SalonType
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class GeoPoint(val lat: Double, val lng: Double)
data class SalonPhoto(val url: String, val path: String)

/** salons/{id} dokümanının kamuya açık görünümü. */
data class SalonProfile(
    val id: String,
    val name: String,
    val code: String,
    val type: SalonType?,
    val businessPhoneE164: String,
    val city: String,
    val district: String,
    val addressLine: String,
    val description: String,
    val location: GeoPoint?,
    val logoUrl: String,
    val logoPath: String,
    val photos: List<SalonPhoto>,
    val ownerUid: String,
    val ownerDisplayName: String,
    val rating: Rating,
    val active: Boolean,
    val weeklyClosedDays: Set<Int> = emptySet(),
) {
    val fullAddress: String get() = "$addressLine, $district / $city"
}

/** Galeri düzenlemesinde her öğe ya mevcut bir Storage yolu ya da yeni işlenmiş yerel dosyadır. */
sealed interface MediaItem {
    data class Existing(val photo: SalonPhoto) : MediaItem
    data class New(val localPath: String) : MediaItem
}

data class SalonProfileInput(
    val name: String,
    val type: SalonType,
    val businessPhone: String,
    val city: String,
    val district: String,
    val addressLine: String,
    val description: String,
    val location: GeoPoint?,
)

@Singleton
class SalonRepository @Inject constructor(
    private val firestore: Lazy<FirebaseFirestore>,
    private val functions: FunctionsClient,
    private val uploader: StorageUploader,
) {
    private fun ref(salonId: String) = firestore.get().collection("salons").document(salonId)

    /** Anlık dinleyici: sahip, personel ve müşteri aynı dokümanı görür; değişiklikler hemen yansır. */
    fun observe(salonId: String): Flow<SalonProfile?> =
        ref(salonId).snapshots().map { it.toSalon() }.catch { throw AppException.from(it) }

    /** Salon kodu → salonId (deep link / QR). */
    suspend fun resolveCode(code: String): String? = try {
        firestore.get().collection("salonCodes").document(code).get().await().getString("salonId")
    } catch (t: Throwable) {
        throw AppException.from(t)
    }

    suspend fun updateProfile(salonId: String, input: SalonProfileInput) {
        functions.call(
            "updateSalonProfile",
            mapOf(
                "salonId" to salonId,
                "name" to input.name.trim(),
                "type" to input.type.name,
                "businessPhone" to input.businessPhone,
                "city" to input.city,
                "district" to input.district,
                "addressLine" to input.addressLine.trim(),
                "description" to input.description.trim(),
                "location" to input.location?.let { mapOf("lat" to it.lat, "lng" to it.lng) },
            ),
        )
    }

    /** Salonu kalıcı olarak kapatır (gelecek randevular gerekçeyle iptal edilir). @return iptal edilen randevu sayısı */
    suspend fun closeSalon(salonId: String, reason: String): Int =
        ((functions.call("closeSalon", mapOf("salonId" to salonId, "confirm" to "KAPAT", "reason" to reason.trim()))["cancelledAppointments"] as? Number)?.toInt()) ?: 0

    /** Sahipliği salonun aktif bir uzmanına devreder; eski sahip uzman olarak kalır. */
    suspend fun transferOwnership(salonId: String, newOwnerUid: String) {
        functions.call("transferSalonOwnership", mapOf("salonId" to salonId, "newOwnerUid" to newOwnerUid, "confirm" to "DEVRET"))
    }

    /**
     * Yeni görselleri geçici alana yükler, ardından updateSalonMedia ile tek seferde uygular.
     * @param newLogoLocalPath null ise logo değişmez.
     */
    suspend fun updateMedia(
        uid: String,
        salonId: String,
        newLogoLocalPath: String?,
        photos: List<MediaItem>,
        onProgress: (String, Float) -> Unit,
    ) {
        val batch = UUID.randomUUID().toString()
        val logoPending = newLogoLocalPath?.let { path ->
            uploader.uploadPending(uid, File(path), "${batch}_logo.jpg") { onProgress("Logo yükleniyor", it) }
        }
        val newCount = photos.count { it is MediaItem.New }
        var uploaded = 0
        val refs = photos.mapIndexed { i, item ->
            when (item) {
                is MediaItem.Existing -> mapOf("existingPath" to item.photo.path)
                is MediaItem.New -> {
                    uploaded++
                    val p = uploader.uploadPending(uid, File(item.localPath), "${batch}_photo_$i.jpg") {
                        onProgress("Fotoğraf $uploaded/$newCount yükleniyor", it)
                    }
                    mapOf("pendingPath" to p)
                }
            }
        }
        onProgress("Kaydediliyor", 1f)
        functions.call("updateSalonMedia", mapOf("salonId" to salonId, "logoPendingPath" to logoPending, "photos" to refs))
    }
}

@Suppress("UNCHECKED_CAST")
internal fun DocumentSnapshot.toSalon(): SalonProfile? {
    if (!exists()) return null
    val loc = get("location") as? Map<String, Any?>
    return SalonProfile(
        id = id,
        name = getString("name").orEmpty(),
        code = getString("code").orEmpty(),
        type = SalonType.fromWire(getString("type")),
        businessPhoneE164 = getString("businessPhoneE164").orEmpty(),
        city = getString("city").orEmpty(),
        district = getString("district").orEmpty(),
        addressLine = getString("addressLine").orEmpty(),
        description = getString("description").orEmpty(),
        location = loc?.let {
            val lat = (it["lat"] as? Number)?.toDouble()
            val lng = (it["lng"] as? Number)?.toDouble()
            if (lat != null && lng != null) GeoPoint(lat, lng) else null
        },
        logoUrl = getString("logoUrl").orEmpty(),
        logoPath = getString("logoPath").orEmpty(),
        photos = (get("photos") as? List<Map<String, Any?>>).orEmpty().mapNotNull { m ->
            val url = m["url"] as? String ?: return@mapNotNull null
            SalonPhoto(url, m["path"] as? String ?: "")
        },
        ownerUid = getString("ownerUid").orEmpty(),
        ownerDisplayName = getString("ownerDisplayName").orEmpty(),
        rating = Rating(getLong("ratingSum") ?: 0, getLong("ratingCount") ?: 0),
        active = getString("status") == "ACTIVE",
        weeklyClosedDays = (get("weeklyClosedDays") as? List<Number>).orEmpty().map { it.toInt() }.toSet(),
    )
}
