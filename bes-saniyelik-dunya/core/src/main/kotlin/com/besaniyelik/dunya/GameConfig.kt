package com.besaniyelik.dunya

/**
 * Merkezi oyun ayarları. Can sistemi ve dil gibi yayın sonrası değiştirilmesi muhtemel değerler yalnızca buradadır.
 */
object GameConfig {
    // ------------------------------------------------------------------ can sistemi
    /** Kalıcı canların açık/kapalı anahtarı (false: oyun eskisi gibi sınırsız denenir). */
    const val LIVES_ENABLED = true
    /** En fazla can. Oyundaki mevcut değer (her denemede 3 kalp) korunarak 3 olarak belirlendi. */
    const val MAX_LIVES = 3
    /** Bir canın yenilenme süresi (saniye): 20 dakika. */
    const val LIFE_REGEN_SECONDS = 1200L
    /** İlk bu kadar bölümde can harcanmaz (yeni oyuncu öğrenirken beklemesin). */
    const val FREE_LEVELS = 10
    /** "Reklam izle: +1 can" ödülü ve günlük sınırı. */
    const val LIFE_AD_REWARD = 1
    const val LIFE_ADS_PER_DAY = 5
    /** Canları elmasla doldurma bedeli. */
    const val LIFE_REFILL_GEMS = 12

    // ------------------------------------------------------------------ bölüm içi kalpler
    /** Bir bölüm denemesindeki kalp sayısı (her ölümde 1 kalp gider, kontrol noktasından devam edilir). */
    const val HEARTS_PER_ATTEMPT = 3
    const val ENDLESS_HEARTS = 1

    // ------------------------------------------------------------------ dil
    /** Desteklenen diller (ISO 639-1). Yeni dil: assets/i18n/<kod>.json ekleyip buraya yazmak yeterli. */
    val LANGUAGES = listOf("tr", "en", "de", "it", "fr", "es")
    /** Sistem dili desteklenmiyorsa kullanılacak dil. */
    const val DEFAULT_LANGUAGE = "en"
}
