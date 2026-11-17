package com.kuaforumcepte.app.data.registration

import com.kuaforumcepte.app.core.data.AppException
import com.kuaforumcepte.app.core.data.FunctionsClient
import com.kuaforumcepte.app.core.data.str
import com.kuaforumcepte.app.data.media.StorageUploader
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SalonCreated(val salonId: String, val code: String, val salonName: String)

@Singleton
class RegistrationRepository @Inject constructor(
    private val functions: FunctionsClient,
    private val uploader: StorageUploader,
    private val drafts: OwnerDraftStore,
) {
    suspend fun registerCustomer(firstName: String, lastName: String, idempotencyKey: String = UUID.randomUUID().toString()) {
        functions.call(
            "registerCustomer",
            mapOf(
                "firstName" to firstName.trim(),
                "lastName" to lastName.trim(),
                "consents" to LegalDocument.consentPayload(),
                "idempotencyKey" to idempotencyKey,
            ),
        )
    }

    /**
     * 1) Henüz yüklenmemiş görselleri yükler (her başarılı yükleme taslağa yazılır → kesintide kaldığı yerden),
     * 2) registerSalonOwner'ı draftId ile (idempotent) çağırır, 3) başarılıysa taslağı siler.
     */
    suspend fun registerSalonOwner(draft: OwnerDraft, onProgress: (String, Float) -> Unit): SalonCreated {
        var current = draft
        val logo = current.logo ?: throw AppException("INVALID_IMAGE")
        if (logo.uploadedPath == null) {
            val path = uploader.uploadPending(current.uid, File(logo.localPath), "${current.draftId}_logo.jpg") {
                onProgress("Logo yükleniyor", it)
            }
            current = current.copy(logo = logo.copy(uploadedPath = path))
            drafts.save(current)
        }
        val photos = current.photos.toMutableList()
        photos.forEachIndexed { i, p ->
            if (p.uploadedPath == null) {
                val path = uploader.uploadPending(current.uid, File(p.localPath), "${current.draftId}_photo_$i.jpg") {
                    onProgress("Fotoğraf ${i + 1}/${photos.size} yükleniyor", it)
                }
                photos[i] = p.copy(uploadedPath = path)
                current = current.copy(photos = photos.toList())
                drafts.save(current)
            }
        }
        onProgress("Salonunuz oluşturuluyor", 1f)
        val r = functions.call(
            "registerSalonOwner",
            mapOf(
                "firstName" to current.firstName.trim(),
                "lastName" to current.lastName.trim(),
                "consents" to LegalDocument.consentPayload(),
                "salon" to mapOf(
                    "name" to current.salonName.trim(),
                    "businessPhone" to current.businessPhone,
                    "type" to current.salonType,
                    "city" to current.city,
                    "district" to current.district,
                    "addressLine" to current.addressLine.trim(),
                    "description" to current.description.trim(),
                ),
                "logoPath" to current.logo?.uploadedPath,
                "photoPaths" to current.photos.mapNotNull { it.uploadedPath },
                "idempotencyKey" to current.draftId,
            ),
        )
        drafts.clear(current.uid)
        return SalonCreated(r.str("salonId").orEmpty(), r.str("code").orEmpty(), r.str("salonName").orEmpty())
    }
}
