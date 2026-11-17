package com.kuaforumcepte.app.data.registration

/**
 * Yasal metin sürümleri — sunucudaki LEGAL_VERSIONS (functions/src/lib/validation.ts) ile aynı olmalıdır.
 * Metinler assets/legal altında TASLAK olarak bulunur ve yayından önce hukuki inceleme gerektirir.
 */
enum class LegalDocument(val title: String, val asset: String) {
    KVKK("KVKK Aydınlatma Metni", "legal/kvkk_aydinlatma.md"),
    TERMS("Kullanım Koşulları", "legal/kullanim_kosullari.md"),
    ;

    companion object {
        const val KVKK_VERSION = "2026-11-v1"
        const val TERMS_VERSION = "2026-11-v1"
        fun consentPayload(): Map<String, Any?> = mapOf("kvkkVersion" to KVKK_VERSION, "termsVersion" to TERMS_VERSION)
    }
}
