package com.emirzinciri.robotkomutan.platform

import com.emirzinciri.robotkomutan.logic.progress.GameClock
import java.io.File
import java.util.UUID

/** Platforma özgü hizmetler. Android başlatıcısı kendi uygulamasını verir. */
interface Platform {
    val clock: GameClock
    /** Kayıt klasörü (uygulamanın özel dahili depolaması). */
    fun saveDir(): File
    fun vibrate(ms: Int) {}
    val name: String
}

/** Masaüstü: monotonik saat işlem başına sıfırlanır; açılış kimliği her çalıştırmada yenidir (duvar saatine düşer). */
class DesktopPlatform(private val dir: File) : Platform {
    private val boot = UUID.randomUUID().toString()
    override val clock = object : GameClock {
        override fun wallMs() = System.currentTimeMillis()
        override fun monotonicMs() = System.nanoTime() / 1_000_000
        override fun bootId() = boot
    }
    override fun saveDir() = dir
    override val name = "desktop"
}
