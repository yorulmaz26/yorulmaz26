package com.kuaforumcepte.app.core.data

import com.kuaforumcepte.app.core.common.UiText

/** Sunucu/istemci hata nedenlerinin Türkçe kullanıcı mesajları. */
object ErrorMessages {
    private val messages = mapOf(
        AppException.NETWORK to "İnternet bağlantısı yok veya zayıf. Bağlantınızı kontrol edip tekrar deneyin.",
        AppException.RATE_LIMITED to "Çok fazla deneme yapıldı. Lütfen bir süre bekleyip tekrar deneyin.",
        AppException.INVALID_CODE to "Doğrulama kodu hatalı. Lütfen SMS ile gelen 6 haneli kodu kontrol edin.",
        AppException.CODE_EXPIRED to "Kodun süresi doldu. Lütfen yeni kod isteyin.",
        AppException.INVALID_PHONE to "Geçerli bir cep telefonu numarası girin (5XX XXX XX XX).",
        AppException.PERMISSION_DENIED to "Bu işlem için yetkiniz yok.",
        AppException.NOT_SIGNED_IN to "Oturumunuz sona erdi. Lütfen yeniden giriş yapın.",
        "PHONE_NOT_VERIFIED" to "Telefon numaranız doğrulanmadı. Lütfen SMS ile yeniden giriş yapın.",
        "CONSENT_REQUIRED" to "Devam etmek için aydınlatma metnini ve kullanım koşullarını onaylamanız gerekir.",
        "INVALID_NAME" to "Ad ve soyad yalnızca harflerden oluşmalı (2–50 karakter).",
        "INVALID_ARGUMENT" to "Girilen bilgilerden biri geçersiz. Lütfen kontrol edin.",
        "INVALID_LOCATION" to "Seçilen il/ilçe geçersiz.",
        "INVALID_IMAGE" to "Görsel kabul edilmedi. JPG/PNG/WEBP ve en fazla 5 MB olmalıdır.",
        "IMAGE_NOT_UPLOADED" to "Görsel yüklemesi tamamlanmadı. Lütfen tekrar deneyin.",
        "UPLOAD_FAILED" to "Görsel yüklenemedi. Bağlantınızı kontrol edip tekrar deneyin.",
        "UPLOAD_REJECTED" to "Görsel yükleme izni reddedildi. Lütfen yeniden giriş yapın.",
        "TOO_MANY_PHOTOS" to "En fazla 5 mekan fotoğrafı yükleyebilirsiniz.",
        "SALON_CODE_EXHAUSTED" to "Salon kodu tahsis edilemedi. Lütfen birkaç saniye sonra tekrar deneyin.",
        "ALREADY_INVITED" to "Bu numaraya gönderilmiş, süresi dolmamış bir davet zaten var.",
        "ALREADY_MEMBER" to "Bu kişi zaten salonunuzun aktif uzmanı.",
        "CANNOT_INVITE_SELF" to "Kendi numaranızı davet edemezsiniz.",
        "INVITATION_NOT_FOUND" to "Davet bulunamadı.",
        "INVITATION_PHONE_MISMATCH" to "Bu davet farklı bir telefon numarasına gönderilmiş.",
        "INVITATION_EXPIRED" to "Davetin süresi dolmuş. Salon sahibinden yeni davet isteyin.",
        "STATE_CHANGED" to "Kayıt bu arada değişti. Liste güncellendi, lütfen tekrar kontrol edin.",
        "SALON_INACTIVE" to "Salon şu anda aktif değil.",
        "SALON_NOT_FOUND" to "Salon bulunamadı.",
        "MEMBER_NOT_FOUND" to "Uzman kaydı bulunamadı.",
        "OWNER_CANNOT_CHANGE_SELF" to "Salon sahibi kendi hesabını pasife alamaz.",
        "OWNER_CANNOT_LEAVE" to "Salon sahibi salondan ayrılamaz.",
        "FIREBASE_NOT_CONFIGURED" to "Uygulama sunucuya bağlı değil (Firebase yapılandırması eksik).",
        "ACTIVITY_UNAVAILABLE" to "Doğrulama başlatılamadı. Lütfen ekrana geri dönüp tekrar deneyin.",
        "IMAGE_TYPE_NOT_SUPPORTED" to "Bu dosya türü desteklenmiyor. JPG, PNG, WEBP veya HEIC seçin.",
        "IMAGE_TOO_LARGE" to "Seçilen dosya çok büyük (en fazla 20 MB).",
        "IMAGE_DECODE_FAILED" to "Görsel okunamadı. Başka bir görsel seçin.",
        "INVALID_COORDINATES" to "Konum Türkiye sınırları içinde olmalıdır.",
        "LOCATION_DISABLED" to "Cihazınızda konum kapalı. Ayarlardan açıp tekrar deneyin.",
        "LOCATION_UNAVAILABLE" to "Konum alınamadı. Açık alanda tekrar deneyin veya adresten bulun.",
        "LOCATION_PERMISSION" to "Konum izni verilmedi. Adresten bulma seçeneğini kullanabilirsiniz.",
        "GEOCODER_UNAVAILABLE" to "Bu cihazda adresten konum bulma desteklenmiyor.",
        "ADDRESS_NOT_FOUND" to "Adres haritada bulunamadı. İl/ilçe ve açık adresi kontrol edin.",
        "SALON_LINK_INVALID" to "Bu salon bağlantısı geçersiz veya salon artık aktif değil.",
        "QR_VERIFY_FAILED" to "QR kod doğrulanamadı. Lütfen tekrar oluşturun.",
    )

    fun of(t: Throwable): UiText {
        val e = AppException.from(t)
        return UiText.Plain(messages[e.reason] ?: "Bir şeyler ters gitti. Lütfen tekrar deneyin. (${e.reason})")
    }
}
