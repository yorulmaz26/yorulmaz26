package com.kayipadalar.game

import com.kayipadalar.logic.meta.TimeSample
import com.kayipadalar.logic.meta.TimeSource

/** Platforma özgü hizmetler (Android / masaüstü). */
interface Platform {
    /** Duvar saati, monoton saat (önyüklemeden beri ms) ve önyükleme sayacı. */
    val timeSource: TimeSource
    fun vibrate(ms: Int) {}
    val name: String
}

class DesktopPlatform : Platform {
    private val startWall = System.currentTimeMillis()
    private val startNano = System.nanoTime()
    override val timeSource = TimeSource {
        // Masaüstünde önyükleme sayacı yoktur; işlem başına monoton saat kullanılır.
        TimeSample(System.currentTimeMillis(), (System.nanoTime() - startNano) / 1_000_000, startWall)
    }
    override val name = "desktop"
}
