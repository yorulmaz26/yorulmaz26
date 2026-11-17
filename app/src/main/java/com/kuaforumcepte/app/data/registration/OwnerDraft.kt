package com.kuaforumcepte.app.data.registration

import kotlinx.serialization.Serializable

/** İşlenmiş (sıkıştırılmış) yerel görsel + tamamlanmışsa Storage'daki geçici yolu. */
@Serializable
data class DraftImage(
    val localPath: String,
    val uploadedPath: String? = null,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val sizeBytes: Long = 0,
)

/**
 * Salon sahibi kayıt taslağı. Telefon doğrulandıktan sonra kayıt yarıda kalırsa
 * (uygulama kapanması, bağlantı kopması, yükleme kesilmesi) bu taslaktan devam edilir.
 * draftId aynı zamanda sunucu idempotency anahtarıdır.
 */
@Serializable
data class OwnerDraft(
    val draftId: String,
    val uid: String,
    val step: Int = 0,
    val firstName: String = "",
    val lastName: String = "",
    val salonName: String = "",
    val businessPhone: String = "",
    val salonType: String? = null,
    val city: String? = null,
    val district: String? = null,
    val addressLine: String = "",
    val description: String = "",
    val logo: DraftImage? = null,
    val photos: List<DraftImage> = emptyList(),
    val kvkkAccepted: Boolean = false,
    val termsAccepted: Boolean = false,
)
