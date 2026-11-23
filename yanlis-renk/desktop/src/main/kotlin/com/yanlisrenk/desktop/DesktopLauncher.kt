package com.yanlisrenk.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.yanlisrenk.game.PlatformServices
import com.yanlisrenk.game.YanlisRenkGame
import com.yanlisrenk.logic.save.SaveStorage
import com.yanlisrenk.logic.time.PlatformClock
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.TimeZone
import java.util.UUID

/** Masaüstü platformu: geliştirme ve görsel kalite kontrol içindir (Android ile aynı çekirdek). */
/** Masaüstü QA için sahte reklamlar: banner alanı yer tutucuyla gösterilir, tam ekran reklamlar anında kapanır. */
class FakeAds(private val bannerPx: Int) : com.yanlisrenk.game.AdService {
    private var visible = false
    var interstitials = 0; var rewardeds = 0
    override val bannerHeightPx: Int get() = if (visible) bannerPx else 0
    override fun setBannerVisible(visible: Boolean) { this.visible = visible }
    override fun isInterstitialReady() = true
    override fun showInterstitial(onClosed: () -> Unit) { interstitials++; onClosed() }
    override fun isRewardedReady() = true
    override fun showRewarded(onResult: (Boolean) -> Unit) { rewardeds++; onResult(true) }
    override val drawsPlaceholder = true
}

class DesktopPlatform(dir: File) : PlatformServices {
    /** QA'da banner alanını görmek için ayarlanabilir. */
    var fakeAds: com.yanlisrenk.game.AdService = com.yanlisrenk.game.NoAds
    override val ads: com.yanlisrenk.game.AdService get() = fakeAds
    private val start = System.nanoTime()
    override val clock = object : PlatformClock {
        override fun wallMillis() = System.currentTimeMillis()
        override fun elapsedMillis() = (System.nanoTime() - start) / 1_000_000L + 10_000L
        override fun bootCount() = -1
    }
    override val storage = FileStorage(dir)
    override fun tzOffsetMillis() = TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
    var allowNetwork = true
    override fun fetchServerTime(onResult: (Long?) -> Unit) {
        if (!allowNetwork) { onResult(null); return }
        Thread { onResult(HttpTime.fetch()) }.apply { isDaemon = true }.start()
    }
    override fun vibrate(ms: Int) {}
    override fun newInstallId() = UUID.randomUUID().toString()
    override val platformName = "Masaüstü"
    override val versionName = "1.0.0"
}

class FileStorage(private val dir: File) : SaveStorage {
    init { dir.mkdirs() }
    override fun read(name: String): String? = File(dir, name).takeIf { it.exists() }?.readText()
    override fun write(name: String, content: String) {
        val tmp = File(dir, "$name.tmp"); tmp.writeText(content)
        val f = File(dir, name)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }
    override fun delete(name: String) { File(dir, name).delete() }
}

object HttpTime {
    /** HTTPS "Date" başlığından zaman alır (saniye hassasiyeti). */
    fun fetch(url: String = "https://www.google.com/generate_204"): Long? = runCatching {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.requestMethod = "HEAD"; c.connectTimeout = 4000; c.readTimeout = 4000
        c.connect()
        val d = c.date
        c.disconnect()
        if (d > 0) d else null
    }.getOrNull()
}

fun main() {
    val cfg = Lwjgl3ApplicationConfiguration()
    cfg.setTitle("Yanlış Renk")
    cfg.setWindowedMode(450, 900)
    cfg.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4)
    cfg.setForegroundFPS(60)
    Lwjgl3Application(YanlisRenkGame(DesktopPlatform(File(System.getProperty("user.home"), ".yanlisrenk"))), cfg)
}
