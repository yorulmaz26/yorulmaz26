package com.besaniyelik.dunya.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.ScreenUtils
import com.besaniyelik.dunya.BesSaniyelikDunya
import com.besaniyelik.dunya.Platform
import com.besaniyelik.dunya.ads.AdService
import com.besaniyelik.dunya.level.LevelRepository
import com.besaniyelik.dunya.ui.screens.*

fun main(args: Array<String>) {
    val shots = System.getProperty("shots")
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("5 Saniyelik Dünya")
        setWindowedMode(960, 540)
        useVsync(true)
        setForegroundFPS(60)
        if (shots != null) setWindowedMode(960, 540)
        System.getProperty("win")?.split("x")?.let { setWindowedMode(it[0].toInt(), it[1].toInt()) }
    }
    // -Dads=fake : reklam akışlarını masaüstünde denemek için sahte reklam hizmeti (gerçek reklam yalnızca Android'de)
    val fakeAds = if (System.getProperty("ads") == "fake") FakeAds() else null
    val game = BesSaniyelikDunya(if (fakeAds != null) object : Platform {
        override val name = "desktop"
        override fun vibrate(ms: Int) {}
        override val ads: AdService get() = fakeAds
    } else Platform.NONE)
    if (shots != null) game.debugHook = ScreenshotTour(shots, fakeAds)::step
    Lwjgl3Application(game, config)
}

/**
 * Masaüstü denemesi için sahte reklam. Gerçek cihazdaki gecikmeleri taklit eder: gösterilen reklam yarım saniye sonra
 * "izlendi" sonucunu verir, yeni ödüllü reklam bir saniye sonra hazır olur, afişin yüksekliği istendikten bir saniye
 * sonra belli olur (ekran çoktan yerleşmişken).
 */
class FakeAds : AdService {
    val events = ArrayList<String>()
    private fun later(sec: Float, f: () -> Unit) { com.badlogic.gdx.utils.Timer.schedule(object : com.badlogic.gdx.utils.Timer.Task() { override fun run() = f() }, sec) }
    private var rewardedLoaded = true
    override fun supported() = true
    override fun rewardedReady() = rewardedLoaded
    override fun showRewarded(onResult: (Boolean) -> Unit) {
        events += "ödüllü reklam gösterildi"
        rewardedLoaded = false // reklam tek kullanımlıktır; yenisi biraz sonra yüklenir
        later(0.5f) { onResult(true); later(1f) { rewardedLoaded = true } }
    }
    override fun interstitialReady() = true
    override fun showInterstitial(onClosed: () -> Unit) { events += "geçiş reklamı gösterildi"; later(0.5f, onClosed) }
    override fun privacyOptionsRequired() = true
    override fun showPrivacyOptions() { events += "gizlilik tercihleri açıldı" }
    /** Afiş: gerçek cihazdaki gibi ekran yüksekliğinin yaklaşık %14'ü; yüksekliği gecikmeyle gelir. */
    private var bannerWanted = false
    private var bannerLoaded = false
    val bannerLog = ArrayList<String>()
    val bannerOn get() = bannerWanted
    override fun setBannerVisible(visible: Boolean) {
        if (visible == bannerWanted) return
        bannerWanted = visible; bannerLog += if (visible) "açık" else "kapalı"
        if (visible && !bannerLoaded) later(1f) { bannerLoaded = true }
    }
    override fun bannerHeightPx() = if (bannerWanted && bannerLoaded) (com.badlogic.gdx.Gdx.graphics.height * 0.14f).toInt() else 0
}

/** Ekranları sırayla açıp görüntü alır; bölümleri kayıtlı çözümle gerçek oyun ekranında baştan sona oynatır. */
class ScreenshotTour(private val dir: String, private val fakeAds: FakeAds? = null) {
    private sealed class Step {
        class Run(val action: (BesSaniyelikDunya) -> Unit) : Step()
        class Wait(val frames: Int) : Step()
        class Shot(val name: String) : Step()
        class PlayLevel(val id: Int, val shots: Int) : Step()
    }
    private val steps = ArrayDeque<Step>()
    private var waitLeft = 0
    private var playing: GameScreen? = null
    private var playId = 0
    private var playFrames = 0
    private var playShots = 0
    private var doneAt = -1
    private val log = StringBuilder()

    init {
        com.besaniyelik.dunya.render.WorldRenderer.debugSkip = System.getProperty("skipLayers") ?: ""
        // -Dinsets=sol,üst,sağ,alt : çentikli ekranı taklit eder (arayüzün güvenli alana çekildiğini denemek için)
        System.getProperty("insets")?.split(",")?.map { it.trim().toInt() }?.takeIf { it.size == 4 }?.let { com.besaniyelik.dunya.ui.screens.BaseScreen.debugInsets = it.toIntArray() }
        fun screen(name: String, f: (BesSaniyelikDunya) -> Unit) { steps.add(Step.Run(f)); steps.add(Step.Wait(45)); steps.add(Step.Shot(name)) }
        if (System.getProperty("introGif") != null) {
            for (i in 0 until 115) { steps.add(Step.Wait(3)); steps.add(Step.Shot("gif_%03d".format(i))) }
            steps.add(Step.Run { Gdx.app.exit() })
        }
        // açılış animasyonu kareleri (60 fps varsayımıyla zaman damgaları)
        steps.add(Step.Wait(30)); steps.add(Step.Shot("00_intro_a"))
        steps.add(Step.Wait(55)); steps.add(Step.Shot("00_intro_b"))
        steps.add(Step.Wait(45)); steps.add(Step.Shot("00_intro_c"))
        steps.add(Step.Wait(35)); steps.add(Step.Shot("00_intro_d"))
        steps.add(Step.Wait(25)); steps.add(Step.Shot("00_intro_e"))
        steps.add(Step.Wait(30)); steps.add(Step.Shot("00_intro_f"))
        steps.add(Step.Wait(60)); steps.add(Step.Shot("01_splash"))
        screen("02_menu") { it.go(MainMenuScreen(it)) }
        if (System.getProperty("tourMenus") != "0") {
            screen("03_maps") { it.go(MapSelectScreen(it)) }
            screen("04_levels") { it.go(LevelSelectScreen(it, 1)) }
            screen("05_endless_intro") { it.go(EndlessIntroScreen(it)) }
            screen("06_characters") { it.go(CharacterScreen(it)) }
            screen("07_shop") { it.go(ShopScreen(it)) }
            screen("08_missions") { it.go(MissionsScreen(it, false)) }
            screen("10_achievements") { it.go(AchievementsScreen(it)) }
            screen("11_collection") { it.go(CollectionScreen(it)) }
            screen("12_profile") { it.go(ProfileScreen(it)) }
            screen("13_settings") { it.go(SettingsScreen(it)) }
            steps.add(Step.Run { g -> (g.screen as? SettingsScreen)?.showPrivacy() }); steps.add(Step.Wait(30)); steps.add(Step.Shot("13b_privacy"))
            screen("14_daily") { it.go(DailyRewardScreen(it)) }
            screen("15_howto") { it.go(HowToPlayScreen(it)) }
        }
        val levels = (System.getProperty("tourLevels") ?: "1,23,61,160,215,280,330,377,420,470").split(",").filter { it.isNotBlank() }.map { it.trim().toInt() }
        levels.forEach { steps.add(Step.PlayLevel(it, 3)) }
        if (System.getProperty("tourEndless") != "0") {
            screen("40_endless") { g -> g.go(GameScreen(g, null, 42L, emptyList())) }
            steps.add(Step.Run { tap(true) }); steps.add(Step.Wait(2)); steps.add(Step.Run { tap(false) }); steps.add(Step.Wait(150)); steps.add(Step.Shot("41_endless_run"))
        }
        if (fakeAds != null) BaseScreen.debugBannerOverlay = true
        if (System.getProperty("tourAds") == "1") adScenario()
        steps.add(Step.Run {
            fakeAds?.let { log.append("Reklam olayları: ${it.events}\n"); log.append("Afiş geçişleri: ${it.bannerLog}\n") }
            java.io.File("$dir/tour_log.txt").writeText(log.toString()); Gdx.app.exit()
        })
    }

    /** Metni içeren düğmeyi bulup gerçek dokunuş olayıyla tıklar (reklam düğmelerini uçtan uca denemek için). */
    private fun click(game: BesSaniyelikDunya, text: String): Boolean {
        val stage = (game.screen as? BaseScreen)?.stage ?: return false
        var hit: com.badlogic.gdx.scenes.scene2d.Actor? = null
        fun walk(a: com.badlogic.gdx.scenes.scene2d.Actor) {
            if (a is com.badlogic.gdx.scenes.scene2d.ui.Label && a.text.toString().contains(text) && a.isVisible) hit = a
            if (a is com.badlogic.gdx.scenes.scene2d.Group) a.children.forEach { walk(it) }
        }
        walk(stage.root)
        val a = hit ?: run { log.append("  (düğme bulunamadı: $text)\n"); return false }
        val v = a.localToStageCoordinates(com.badlogic.gdx.math.Vector2(a.width / 2f, a.height / 2f))
        stage.stageToScreenCoordinates(v)
        val p = Gdx.input.inputProcessor ?: return false
        p.touchDown(v.x.toInt(), v.y.toInt(), 0, 0); p.touchUp(v.x.toInt(), v.y.toInt(), 0, 0)
        return true
    }

    /** Reklam akışları: mağaza, günlük ödül, canlar bitince devam, bölüm sonu 2x altın, Sonsuz Dünya dirilişi. */
    private fun adScenario() {
        fun screen(name: String, f: (BesSaniyelikDunya) -> Unit) { steps.add(Step.Run(f)); steps.add(Step.Wait(45)); steps.add(Step.Shot(name)) }
        fun sim(g: BesSaniyelikDunya) = (g.screen as? GameScreen)?.simulation
        fun note(g: BesSaniyelikDunya, what: String) { val d = g.profile.data; log.append("$what -> altın ${d.coins}, elmas ${d.gems}, can ${sim(g)?.hearts ?: "-"}, durum ${sim(g)?.status ?: "-"}\n") }
        fun bannerCheck(g: BesSaniyelikDunya, where: String) { fakeAds?.let { log.append("afiş $where: ${if (it.bannerOn) "AÇIK" else "kapalı"}\n") } }
        fun killAll(n: Int) { repeat(n) { steps.add(Step.Run { g -> sim(g)?.kill(com.besaniyelik.dunya.sim.DeathCause.STUCK) }); steps.add(Step.Wait(170)) } }
        // mağaza: ücretsiz elmas
        screen("60_reklam_magaza") { it.go(ShopScreen(it)) }
        steps.add(Step.Run { g -> note(g, "mağaza önce"); click(g, "Reklam izle: +") }); steps.add(Step.Wait(70))
        steps.add(Step.Run { g -> note(g, "mağaza sonra") }); steps.add(Step.Shot("61_reklam_magaza_sonra"))
        // ikinci izleme: reklam yeniden yüklenince düğme kendiliğinden açılmalı (ekrandan çıkmadan)
        steps.add(Step.Wait(100)); steps.add(Step.Run { g -> click(g, "Reklam izle: +") }); steps.add(Step.Wait(70))
        steps.add(Step.Run { g -> note(g, "mağaza ikinci izleme sonrası"); bannerCheck(g, "mağazada (açık olmalı)") }); steps.add(Step.Wait(60)); steps.add(Step.Shot("61b_magaza_afis"))
        // günlük ödül: al, sonra ikiye katla
        screen("62_gunluk") { it.go(DailyRewardScreen(it)) }
        steps.add(Step.Run { g -> click(g, "Ödülü Al") }); steps.add(Step.Wait(40)); steps.add(Step.Shot("63_gunluk_katla"))
        steps.add(Step.Run { g -> note(g, "günlük önce"); click(g, "ödülü tekrar al") }); steps.add(Step.Wait(70))
        steps.add(Step.Run { g -> note(g, "günlük sonra") }); steps.add(Step.Shot("64_gunluk_sonra"))
        // bölüm: canlar bitince reklamla devam
        steps.add(Step.Run { g -> g.go(GameScreen(g, LevelRepository.level(12), null, emptyList())) }); steps.add(Step.Wait(60))
        steps.add(Step.Run { tap(true) }); steps.add(Step.Wait(2)); steps.add(Step.Run { tap(false) }); steps.add(Step.Wait(30))
        killAll(3); steps.add(Step.Wait(60)); steps.add(Step.Shot("65_canlar_bitti"))
        steps.add(Step.Run { g -> bannerCheck(g, "oyun ekranında (kapalı olmalı)"); note(g, "devam önce"); click(g, "Reklam izle: +2 can") })
        // reklam açılırken ikinci bir düğmeye basmak (elmasla devam / tekrar dene) etkisiz olmalı
        steps.add(Step.Wait(5)); steps.add(Step.Run { g -> click(g, "Tekrar Dene"); log.append("  reklam beklerken 'Tekrar Dene' denendi -> ekran hâlâ oyun mu: ${g.screen is GameScreen}\n") })
        steps.add(Step.Wait(120))
        steps.add(Step.Run { g -> note(g, "devam sonra") }); steps.add(Step.Shot("66_devam_edildi"))
        killAll(2); steps.add(Step.Wait(60)); steps.add(Step.Shot("67_canlar_bitti_ikinci"))
        steps.add(Step.Run { g -> note(g, "ikinci kez (reklam düğmesi olmamalı)"); if (click(g, "Reklam izle: +2 can")) log.append("  HATA: reklamla devam ikinci kez sunuldu\n") })
        // bölüm sonu: geçiş reklamı (deneyimli oyuncu, sıklık koşulları sağlanmış) + ek altın
        steps.add(Step.Run { g ->
            val fa = fakeAds ?: return@Run
            for (i in 20..32) g.profile.data.levels[i] = com.besaniyelik.dunya.progress.LevelProgress(completed = true)
            var skew = 0L
            g.ads = com.besaniyelik.dunya.ads.AdDirector(fa, g.profile) { System.currentTimeMillis() + skew }
            skew = 10 * 60_000L // oturumun ilk dakikaları geçmiş say
            repeat(2) { g.ads.onBreakEarned() }
            log.append("geçiş reklamı koşulları hazırlandı (şu an gösterilebilir mi: ${g.ads.interstitialDue()})\n")
        })
        steps.add(Step.PlayLevel(3, 0))
        steps.add(Step.Run { g -> note(g, "ek altın önce (kayıtlı tamamlanan bölüm: ${g.profile.data.stats.levelsCompleted})"); click(g, "Reklam izle: +") }); steps.add(Step.Wait(70))
        steps.add(Step.Run { g -> note(g, "2x sonra") }); steps.add(Step.Shot("68_iki_kat_altin"))
        // Sonsuz Dünya: diriliş
        steps.add(Step.Run { g -> g.go(GameScreen(g, null, 42L, emptyList())) }); steps.add(Step.Wait(60))
        steps.add(Step.Run { tap(true) }); steps.add(Step.Wait(2)); steps.add(Step.Run { tap(false) }); steps.add(Step.Wait(60))
        killAll(1); steps.add(Step.Wait(40)); steps.add(Step.Shot("69_sonsuz_dirilis"))
        steps.add(Step.Run { g -> note(g, "diriliş önce"); click(g, "Reklam izle: devam et") }); steps.add(Step.Wait(120))
        steps.add(Step.Run { g -> note(g, "diriliş sonra") }); steps.add(Step.Shot("70_sonsuz_devam"))
        killAll(1); steps.add(Step.Wait(60)); steps.add(Step.Run { g -> note(g, "sonsuz bitti") }); steps.add(Step.Shot("71_sonsuz_sonuc"))
        // ayarlar: reklam tercihleri düğmesi
        screen("72_ayarlar_reklam") { it.go(SettingsScreen(it)) }
    }

    private fun tap(down: Boolean) {
        val p = Gdx.input.inputProcessor ?: return
        if (down) p.touchDown(480, 400, 0, 0) else p.touchUp(480, 400, 0, 0)
    }

    fun step(game: BesSaniyelikDunya, frame: Int) {
        val pg = playing
        if (pg != null) {
            playFrames++
            if (playFrames == 50) tap(true)
            if (playFrames == 53) tap(false)
            val sim = pg.simulation
            val expected = sim.level?.solveTicks ?: 3000
            if (playShots < 3 && sim.tick > expected * (playShots + 1) / 4) { playShots++; shot("2_lv%03d_%d".format(playId, playShots)) }
            if (sim.status == com.besaniyelik.dunya.sim.SimStatus.COMPLETE && doneAt < 0) doneAt = playFrames
            if (doneAt > 0 && playFrames - doneAt == 150) {
                shot("2_lv%03d_complete".format(playId))
                log.append("Bölüm $playId: TAMAMLANDI (tick ${sim.tick}, yıldız ${sim.starsCollected}/3, altın ${sim.coinCount}, ölüm ${sim.deaths})\n")
                playing = null
            } else if (playFrames > 60 * 400 || sim.status == com.besaniyelik.dunya.sim.SimStatus.GAME_OVER) {
                shot("2_lv%03d_FAIL".format(playId))
                log.append("Bölüm $playId: BAŞARISIZ (durum ${sim.status}, x=${sim.player.x})\n")
                playing = null
            }
            return
        }
        if (waitLeft > 0) { waitLeft--; return }
        val st = steps.removeFirstOrNull() ?: return
        when (st) {
            is Step.Run -> st.action(game)
            is Step.Wait -> waitLeft = st.frames
            is Step.Shot -> shot(st.name)
            is Step.PlayLevel -> {
                val level = LevelRepository.level(st.id)
                val s = GameScreen(game, level, null, emptyList())
                s.autoplay = com.besaniyelik.dunya.level.InputTrack(level.solution.toIntArray())
                s.timeScale = (System.getProperty("speed") ?: "1").toFloat()
                game.go(s)
                playing = s; playId = st.id; playFrames = 0; playShots = 0; doneAt = -1
            }
        }
    }

    private fun shot(name: String) {
        val pm: Pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        val flipped = Pixmap(pm.width, pm.height, pm.format)
        for (y in 0 until pm.height) flipped.drawPixmap(pm, 0, y, pm.width, 1, 0, pm.height - 1 - y, pm.width, 1)
        PixmapIO.writePNG(Gdx.files.absolute("$dir/$name.png"), flipped)
        pm.dispose(); flipped.dispose()
    }
}
