package com.zamaninefendisi.game

import com.zamaninefendisi.logic.save.GameClock

/** Platforma özgü hizmetler. Android başlatıcısı monotonik saat ve açılış sayacını sağlar. */
interface Platform {
    val clock: GameClock
    val name: String
    /** Kayıt klasörü: null ise uygulamanın özel yerel deposu (Android), değilse kullanıcı klasörü altındaki yol. */
    val externalSaveDir: String? get() = null
}

/** Masaüstü/test: monotonik saat süreç başına sıfırlandığından her açılış "yeniden başlatma" sayılır. */
class DefaultPlatform : Platform {
    private val processId = System.nanoTime().toString()
    override val clock = object : GameClock {
        override fun wallMs() = System.currentTimeMillis()
        override fun elapsedMs() = System.nanoTime() / 1_000_000
        override fun bootId() = processId
    }
    override val name = "Masaüstü"
    override val externalSaveDir = ".zamanin-efendisi/"
}
