package com.kayipadalar.logic.kingdom

import com.kayipadalar.logic.meta.TimeSource
import com.kayipadalar.logic.meta.TrustedClock
import com.kayipadalar.logic.save.LoadResult
import com.kayipadalar.logic.save.SaveSystem

/** Kayıt, güvenilir saat ve krallık simülasyonunu bir arada tutar. Arayüz yalnızca bu sınıfla konuşur. */
class KingdomGame(val cfg: KingdomConfig, private val saves: SaveSystem, time: TimeSource) {
    val loadResult: LoadResult = saves.load()
    val save: KingdomSave = loadResult.data
    val clock = TrustedClock(save.clock, time)
    val k = Kingdom(cfg, save)

    init {
        val now = clock.now()
        if (!save.created) k.newGame(now)
        k.tick(now)
    }

    fun now() = clock.now()
    fun tick(): List<String> = k.tick(clock.now())
    fun persist() = saves.save(save)
}
