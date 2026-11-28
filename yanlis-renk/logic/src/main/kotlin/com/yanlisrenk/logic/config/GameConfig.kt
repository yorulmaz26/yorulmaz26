package com.yanlisrenk.logic.config

/**
 * Oyunun merkezi ayar dosyası. Can sistemi ve zamanla ilgili tüm sabitler buradan okunur;
 * değer değiştirmek için yalnızca bu dosya düzenlenir (LifeManager, SaveManager, sunucu ve arayüz
 * bu değerleri kullanır).
 */
object GameConfig {
    /** En fazla can (bonus ürünleri hariç bu sınır aşılmaz). */
    const val MAX_LIVES: Int = 4

    /** Bir canın dolma süresi (saniye). 1200 sn = 20 dakika. */
    const val LIFE_REGEN_SECONDS: Int = 1200

    /** [LIFE_REGEN_SECONDS] milisaniye cinsinden. */
    const val LIFE_REGEN_MS: Long = LIFE_REGEN_SECONDS * 1000L

    /**
     * Günlük ödül / görev yenilemesi için: yeni güne geçilmesi, kayıtlı günün başlangıcından bu yana
     * en az bu kadar güvenilir süre geçmesini gerektirir. Saat dilimini ileri alarak aynı gün içinde
     * ikinci günlük ödülü almayı engeller; normal kullanım (gece yarısı, kısa yolculuk) etkilenmez.
     */
    const val DAY_ADVANCE_MIN_MS: Long = 21L * 3_600_000L

    /** Sunucu doğrulaması olmadan yeniden başlatma sonrası geriye dönük verilebilecek en uzun süre. */
    const val MAX_OFFLINE_GAP_CREDIT_MS: Long = 7L * 24 * 3_600_000L

    /** Doğrulanmamış (çevrimdışı) cihazda duvar saatinin güvenilir saatten en fazla bu kadar önde kabul edilmesi. */
    const val LENIENT_WALL_AHEAD_MS: Long = 36L * 3_600_000L
}
