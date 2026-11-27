package com.example.gezegenkacisi

/**
 * Oyunun merkezi ayarları. Denge değerleri tek yerden değiştirilsin diye burada toplanır.
 */
object GameRules {
    /** Bölüm modunda en fazla can (mevcut oyundaki değer). */
    const val MAX_LIVES = 4

    /** Bir canın kendiliğinden dolma süresi (saniye): 20 dakika. */
    const val LIFE_REGEN_SECONDS = 1200

    /** Oyun içi para ile 1 can alma fiyatı. */
    const val LIFE_COIN_PRICE = 60

    /**
     * Yeniden başlatmadan sonra, internet saati doğrulanınca fazla sayılmış süre geri alınır;
     * bu kadarlık (ms) fark saat kaymasıdır, yok sayılır.
     */
    const val LIFE_CLOCK_TOLERANCE_MS = 2 * 60 * 1000L

    const val LIFE_REGEN_MS = LIFE_REGEN_SECONDS * 1000L
}
