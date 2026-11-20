package com.yanlisrenk.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.ScreenUtils
import com.yanlisrenk.game.YanlisRenkGame
import com.yanlisrenk.game.screens.*
import com.yanlisrenk.logic.shop.Currency
import java.io.File

/**
 * Görsel kalite kontrol: oyunu gerçek OpenGL ile açar, her ekrana gider ve PNG kaydeder.
 * Ayrıca her ekranı birkaç kare çalıştırarak çökme olmadığını doğrular (menü geçiş testi).
 * Kullanım: xvfb-run ./gradlew :desktop:screenshots
 */
class ScreenshotGame(platform: DesktopPlatform, private val outDir: File) : com.badlogic.gdx.ApplicationListener {
    val game = YanlisRenkGame(platform)
    private var step = 0
    private var frames = 0
    private val shots = ArrayList<Pair<String, () -> Unit>>()
    val log = StringBuilder()
    private var failures = 0
    private var frameTimes = ArrayList<Float>()
    private var elapsed = 0f

    override fun create() {
        game.autoSync = false
        game.create()
        outDir.mkdirs()
        val s = game.session
        // örnek ilerleme: ilk 37 bölüm, biraz para, birkaç karakter
        for (i in 1..37) s.data.levelStars[i] = 1 + (i * 7) % 3
        s.data.unlockedLevel = 38
        s.data.areaIntroSeen.addAll(1..8)
        s.data.collection.addAll(1..7)
        s.data.tutorialsSeen.add("intro")
        s.credit(Currency.GOLD, 5200, "qa"); s.credit(Currency.DIAMOND, 60, "qa")
        listOf("char_robot", "char_panda", "char_kedi", "cos_party", "trl_stars").forEach { s.data.owned.add(it) }
        s.data.equipped["CHARACTER"] = "char_kedi"; s.data.equipped["COSTUME"] = "cos_party"; s.data.equipped["TRAIL"] = "trl_stars"
        s.addXp(900)
        s.checkAchievements(); s.pendingAchievementToasts.clear()
        s.data.missions.daily.first().progress = s.data.missions.daily.first().target
        s.save()

        fun go(name: String, f: () -> BaseScreen) = shots.add(name to { s.data.unlockedLevel = 38; game.go(f, remember = false) })
        fun play(name: String, mode: Mode, level: Int, secs: Float) = shots.add(name to {
            s.data.unlockedLevel = maxOf(38, level); s.lives.refill()
            game.go({ GameScreen(game, mode, level) }, remember = false)
            Gdx.app.postRunnable { (game.screen as? GameScreen)?.debugStart(secs) }
        })
        go("00a_intro") { IntroScreen(game) }
        go("00b_intro") { IntroScreen(game) }
        go("00c_intro") { IntroScreen(game) }
        go("00d_intro") { IntroScreen(game) }
        go("01_splash") { SplashScreen(game) }
        shots.add("02_ana_menu" to { s.data.daily.lastClaimDay = s.dayKey(); s.data.unlockedLevel = 38; game.go({ MainMenuScreen(game) }, remember = false) })
        go("03_harita") { WorldMapScreen(game) }
        go("04_bolum_secim") { AreaScreen(game, 8) }
        go("05a_bolum_baslangic") { GameScreen(game, Mode.LEVEL, 36) }
        play("05b_oyun_alan1", Mode.LEVEL, 3, 6.5f)
        play("05c_oyun_ters", Mode.LEVEL, 7, 9f)
        play("05d_oyun_donen", Mode.LEVEL, 32, 8f)
        play("05e_oyun_gokkusagi", Mode.LEVEL, 37, 10f)
        play("05f_oyun_ayna", Mode.LEVEL, 52, 12f)
        play("05g_oyun_yaniltici", Mode.LEVEL, 63, 9f)
        play("05h_oyun_zincir", Mode.LEVEL, 82, 7f)
        play("05i_oyun_kayan", Mode.LEVEL, 78, 11f)
        play("05j_oyun_final500", Mode.LEVEL, 500, 22f)
        play("05k_oyun_dunya12", Mode.LEVEL, 278, 9f)
        play("05l_oyun_dunya14", Mode.LEVEL, 333, 10f)
        play("05m_oyun_dunya18", Mode.LEVEL, 428, 8f)
        play("05n_oyun_dunya19", Mode.LEVEL, 466, 11f)
        play("06_sonsuz", Mode.ENDLESS, 0, 25f)
        go("09_canlar") { LivesScreen(game) }
        go("10_market") { ShopScreen(game) }
        go("10b_market_can") { ShopScreen(game, com.yanlisrenk.logic.shop.ShopCategory.LIFE) }
        go("11_karakterler") { CharactersScreen(game) }
        shots.add("11b_karakterler_alt" to {
            s.data.unlockedLevel = 38; game.go({ CharactersScreen(game) }, remember = false)
            Gdx.app.postRunnable { (game.screen as? BaseScreen)?.root?.let { r -> com.badlogic.gdx.utils.Array<com.badlogic.gdx.scenes.scene2d.Actor>().also { r.stage?.root?.findActor<com.badlogic.gdx.scenes.scene2d.Actor>("x") } }
                fun findScroll(a: com.badlogic.gdx.scenes.scene2d.Actor): com.badlogic.gdx.scenes.scene2d.ui.ScrollPane? {
                    if (a is com.badlogic.gdx.scenes.scene2d.ui.ScrollPane) return a
                    if (a is com.badlogic.gdx.scenes.scene2d.Group) for (c in a.children) findScroll(c)?.let { return it }
                    return null
                }
                (game.screen as? BaseScreen)?.stage?.root?.let { findScroll(it) }?.let { it.layout(); it.scrollPercentY = 0.42f; it.updateVisualScroll() }
            }
        })
        go("12_kostumler") { CostumesScreen(game) }
        go("13_gorevler") { MissionsScreen(game) }
        go("13b_gorevler_haftalik") { MissionsScreen(game, true) }
        go("14_basarilar") { AchievementsScreen(game) }
        go("15_koleksiyon") { CollectionScreen(game) }
        go("16_profil") { ProfileScreen(game) }
        go("17_gunluk_odul") { DailyRewardScreen(game) }
        go("18_haftalik") { WeeklyChallengeScreen(game) }
        go("19_ayarlar") { SettingsScreen(game) }
        go("20_ogretici") { TutorialScreen(game) }
        go("21_alan_acilis") { AreaIntroScreen(game, 11) }
        go("22_alan_finali") { AreaFinalScreen(game, 40) }
        go("23_ustalik") { WorldMapScreen(game, true) }
        go("25_odul") { RewardScreen(game, com.yanlisrenk.logic.Reward(gold = 420, diamonds = 5, xp = 80, areaCompleted = 7, collectible = "Dönen Pamuk Şeker", levelUps = mutableListOf(6)), 35, false) }
        // Oyun bitti / tamamlandı ekranları: bölümü gerçekten oynatarak
        shots.add("07_bolum_tamam" to {
            game.go({ GameScreen(game, Mode.LEVEL, 12) }, remember = false)
            Gdx.app.postRunnable { (game.screen as? GameScreen)?.debugStart(200f) }
        })
        shots.add("08_oyun_bitti" to {
            game.go({ GameScreen(game, Mode.LEVEL, 14) }, remember = false)
            Gdx.app.postRunnable { (game.screen as? GameScreen)?.debugStart(60f, oracle = false) }
        })
        shots.add("24_duraklatma" to {
            game.go({ GameScreen(game, Mode.LEVEL, 20) }, remember = false)
            Gdx.app.postRunnable { (game.screen as? GameScreen)?.let { it.debugStart(5f); it.pause() } }
        })
        shots[0].second()
    }

    override fun render() {
        val t0 = System.nanoTime()
        try { game.render() } catch (e: Throwable) {
            failures++; log.append("HATA ${shots.getOrNull(step)?.first}: ${e}\n"); e.printStackTrace()
        }
        frameTimes.add((System.nanoTime() - t0) / 1e6f)
        frames++
        elapsed += Gdx.graphics.deltaTime
        val name0 = shots[step].first
        val wait = when {
            name0.startsWith("00a") -> 1.8f
            name0.startsWith("00b") -> 3.4f
            name0.startsWith("00c") -> 4.7f
            name0.startsWith("00d") -> 6.0f
            name0.startsWith("07") || name0.startsWith("08") -> 2.6f
            else -> 0.8f
        }
        if (frames >= 20 && elapsed >= wait) {
            elapsed = 0f
            val name = shots[step].first
            val px = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
            // dikey çevir
            val flipped = Pixmap(px.width, px.height, Pixmap.Format.RGBA8888)
            for (y in 0 until px.height) flipped.drawPixmap(px, 0, y, px.width, 1, 0, px.height - y - 1, px.width, 1)
            PixmapIO.writePNG(Gdx.files.absolute(File(outDir, "$name.png").absolutePath), flipped)
            px.dispose(); flipped.dispose()
            val avg = frameTimes.average()
            log.append("OK $name (${frames} kare, ort. ${"%.1f".format(avg)} ms/kare)\n")
            frameTimes.clear()
            frames = 0
            step++
            if (step >= shots.size) {
                File(outDir, "screenshots_log.txt").writeText(log.toString() + "Hata sayısı: $failures\n")
                println(log); Gdx.app.exit(); return
            }
            shots[step].second()
        }
    }

    override fun resize(width: Int, height: Int) = game.resize(width, height)
    override fun pause() {}
    override fun resume() {}
    override fun dispose() { game.dispose() }
}

fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/screenshots")
    val tmp = kotlin.io.path.createTempDirectory("yrqa").toFile()
    val platform = DesktopPlatform(tmp).also { it.allowNetwork = false }
    val cfg = Lwjgl3ApplicationConfiguration()
    cfg.setTitle("Yanlış Renk QA")
    cfg.setWindowedMode(540, 1080)
    cfg.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4)
    cfg.setForegroundFPS(60)
    cfg.useVsync(false)
    Lwjgl3Application(ScreenshotGame(platform, out), cfg)
}
