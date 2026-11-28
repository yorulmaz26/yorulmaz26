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
import com.besaniyelik.dunya.i18n.L

fun main(args: Array<String>) {
    val shots = System.getProperty("shots")
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Stopwatch Shuffle / 5 Saniyelik Dünya")
        setWindowedMode(960, 540)
        useVsync(true)
        setForegroundFPS(60)
        if (shots != null) setWindowedMode(960, 540)
        System.getProperty("win")?.split("x")?.let { setWindowedMode(it[0].toInt(), it[1].toInt()) }
    }
    // -Dads=fake : reklam akışlarını masaüstünde denemek için sahte reklam hizmeti (gerçek reklam yalnızca Android'de)
    val fakeAds = if (System.getProperty("ads") == "fake") FakeAds() else null
    // -Dlang=de : oyunu o dilde açar (telefon dilinin masaüstü karşılığı; kayıtlı dil tercihi yoksa geçerlidir)
    val game = BesSaniyelikDunya(object : Platform {
        override val name = "desktop"
        override fun vibrate(ms: Int) {}
        override val ads: AdService get() = fakeAds ?: AdService.NONE
        override fun systemLanguage(): String = System.getProperty("lang") ?: java.util.Locale.getDefault().language
    })
    val record = System.getProperty("record")
    val store = System.getProperty("store")
    when {
        System.getProperty("perf") != null -> game.debugHook = PerfTour(System.getProperty("perf"))::step
        record != null -> game.debugHook = MediaTour(record, MediaTour.Mode.RECORD)::step
        store != null -> game.debugHook = MediaTour(store, MediaTour.Mode.STORE)::step
        shots != null -> game.debugHook = ScreenshotTour(shots, fakeAds)::step
    }
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
        steps.add(Step.Run { g -> note(g, "mağaza önce"); click(g, watchPrefix()) }); steps.add(Step.Wait(70))
        steps.add(Step.Run { g -> note(g, "mağaza sonra") }); steps.add(Step.Shot("61_reklam_magaza_sonra"))
        // ikinci izleme: reklam yeniden yüklenince düğme kendiliğinden açılmalı (ekrandan çıkmadan)
        steps.add(Step.Wait(100)); steps.add(Step.Run { g -> click(g, watchPrefix()) }); steps.add(Step.Wait(70))
        steps.add(Step.Run { g -> note(g, "mağaza ikinci izleme sonrası"); bannerCheck(g, "mağazada (açık olmalı)") }); steps.add(Step.Wait(60)); steps.add(Step.Shot("61b_magaza_afis"))
        // günlük ödül: al, sonra ikiye katla
        screen("62_gunluk") { it.go(DailyRewardScreen(it)) }
        steps.add(Step.Run { g -> click(g, L.t("daily.claim")) }); steps.add(Step.Wait(40)); steps.add(Step.Shot("63_gunluk_katla"))
        steps.add(Step.Run { g -> note(g, "günlük önce"); click(g, L.t("ads.watch_daily")) }); steps.add(Step.Wait(70))
        steps.add(Step.Run { g -> note(g, "günlük sonra") }); steps.add(Step.Shot("64_gunluk_sonra"))
        // bölüm: canlar bitince reklamla devam
        steps.add(Step.Run { g -> g.go(GameScreen(g, LevelRepository.level(12), null, emptyList())) }); steps.add(Step.Wait(60))
        steps.add(Step.Run { tap(true) }); steps.add(Step.Wait(2)); steps.add(Step.Run { tap(false) }); steps.add(Step.Wait(30))
        killAll(3); steps.add(Step.Wait(60)); steps.add(Step.Shot("65_canlar_bitti"))
        steps.add(Step.Run { g -> bannerCheck(g, "oyun ekranında (kapalı olmalı)"); note(g, "devam önce"); click(g, L.n("ads.watch_hearts", 2)) })
        // reklam açılırken ikinci bir düğmeye basmak (elmasla devam / tekrar dene) etkisiz olmalı
        steps.add(Step.Wait(5)); steps.add(Step.Run { g -> click(g, L.t("over.retry")); log.append("  reklam beklerken 'Tekrar Dene' denendi -> ekran hâlâ oyun mu: ${g.screen is GameScreen}\n") })
        steps.add(Step.Wait(120))
        steps.add(Step.Run { g -> note(g, "devam sonra") }); steps.add(Step.Shot("66_devam_edildi"))
        killAll(2); steps.add(Step.Wait(60)); steps.add(Step.Shot("67_canlar_bitti_ikinci"))
        steps.add(Step.Run { g -> note(g, "ikinci kez (reklam düğmesi olmamalı)"); if (click(g, L.n("ads.watch_hearts", 2))) log.append("  HATA: reklamla devam ikinci kez sunuldu\n") })
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
        steps.add(Step.Run { g -> note(g, "ek altın önce (kayıtlı tamamlanan bölüm: ${g.profile.data.stats.levelsCompleted})"); click(g, watchPrefix()) }); steps.add(Step.Wait(70))
        steps.add(Step.Run { g -> note(g, "2x sonra") }); steps.add(Step.Shot("68_iki_kat_altin"))
        // Sonsuz Dünya: diriliş
        steps.add(Step.Run { g -> g.go(GameScreen(g, null, 42L, emptyList())) }); steps.add(Step.Wait(60))
        steps.add(Step.Run { tap(true) }); steps.add(Step.Wait(2)); steps.add(Step.Run { tap(false) }); steps.add(Step.Wait(60))
        killAll(1); steps.add(Step.Wait(40)); steps.add(Step.Shot("69_sonsuz_dirilis"))
        steps.add(Step.Run { g -> note(g, "diriliş önce"); click(g, L.t("endless.revive_watch")) }); steps.add(Step.Wait(120))
        steps.add(Step.Run { g -> note(g, "diriliş sonra") }); steps.add(Step.Shot("70_sonsuz_devam"))
        killAll(1); steps.add(Step.Wait(60)); steps.add(Step.Run { g -> note(g, "sonsuz bitti") }); steps.add(Step.Shot("71_sonsuz_sonuc"))
        // ayarlar: reklam tercihleri düğmesi
        screen("72_ayarlar_reklam") { it.go(SettingsScreen(it)) }
    }

    /** "Reklam izle: " düğmelerinin seçili dildeki ortak başı. */
    private fun watchPrefix() = L.t("ads.watch_daily").substringBefore(":") + ":"

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

/**
 * Mağaza görselleri ve tanıtım videosu için gerçek oyun görüntüsü üretir (seçili dilde, -Dlang).
 *  - STORE: önceden seçilmiş 8 sahnenin ekran görüntüsü (s1..s8.png) + ek sahneler.
 *  - RECORD: seçili bölümleri ve ekranları sabit 30 kare/sn ile kaydeder (oyun süresi gerçek süreden bağımsız
 *    ilerler; kareler doğrudan ffmpeg'e akar). Çıktı: <dizin>/<ad>.mp4 (H.264, kayıpsıza yakın).
 * Kayıt/görüntü sırasında kayıt dosyası vitrin için hazırlanır (ilerleme, altın, karakterler); assets/save sonra silinir.
 */
class MediaTour(private val dir: String, private val mode: Mode) {
    enum class Mode { STORE, RECORD }
    private sealed class Step {
        class Run(val action: (BesSaniyelikDunya) -> Unit) : Step()
        class Wait(val frames: Int) : Step()
        class Shot(val name: String) : Step()
        /** Bölümü kayıtlı çözümle oynatır; [shotsAt] tick'lerinde görüntü alır; [rec] verilirse baştan sona kaydeder. */
        class Play(val id: Int, val shotsAt: Map<Int, String> = emptyMap(), val rec: String? = null, val untilTick: Int = Int.MAX_VALUE, val afterFrames: Int = 120) : Step()
        class RecStart(val name: String) : Step()
        class RecStop : Step()
    }
    private val steps = ArrayDeque<Step>()
    private var waitLeft = 0
    private var playing: GameScreen? = null
    private var play: Step.Play? = null
    private var playFrames = 0
    private var doneAt = -1
    private var ffmpeg: Process? = null
    private var recW = 0; private var recH = 0
    private val log = StringBuilder()

    init {
        java.io.File(dir).mkdirs()
        if (mode == Mode.RECORD) BaseScreen.fixedDelta = 1f / 30f
        fun screen(name: String, wait: Int = 50, f: (BesSaniyelikDunya) -> Unit) { steps.add(Step.Run(f)); steps.add(Step.Wait(wait)); steps.add(Step.Shot(name)) }
        steps.add(Step.Wait(20))
        steps.add(Step.Run { g -> showcaseSave(g) })
        if (mode == Mode.STORE) {
            val spec = System.getProperty("storeSpec") ?: ""
            // biçim: ad=bölüm@tick;... (oyun içi sahneler) — boşsa aday taraması yapılır
            if (spec.isNotEmpty()) {
                for (item in spec.split(";").filter { it.isNotBlank() }) {
                    val (name, rest) = item.split("=")
                    val (lv, tick) = rest.split("@").map { it.trim().toInt() }
                    steps.add(Step.Play(lv, mapOf(tick to name), untilTick = tick + 1, afterFrames = 0))
                }
            } else {
                val lvls = (System.getProperty("scan") ?: "61,160,215,280,330,377,420,470").split(",").map { it.trim().toInt() }
                for (lv in lvls) {
                    val solve = com.besaniyelik.dunya.level.LevelRepository.level(lv).solveTicks
                    steps.add(Step.Play(lv, (1..9).associate { k -> (solve * k / 10) to "scan_%03d_%d".format(lv, k) }, untilTick = solve, afterFrames = 0))
                }
            }
            // menü ve özellik sahneleri
            screen("m_menu") { it.go(MainMenuScreen(it)) }
            screen("m_maps") { it.go(MapSelectScreen(it)) }
            screen("m_levels", 60) { it.go(LevelSelectScreen(it, 6)) }
            screen("m_characters") { it.go(CharacterScreen(it)) }
            screen("m_achievements") { it.go(AchievementsScreen(it)) }
            screen("m_endless_intro") { it.go(EndlessIntroScreen(it)) }
            screen("m_shop") { it.go(ShopScreen(it)) }
            screen("m_daily") { it.go(DailyRewardScreen(it)) }
            screen("m_howto") { it.go(HowToPlayScreen(it)) }
            screen("m_newrule", 70) { it.go(GameScreen(it, com.besaniyelik.dunya.level.LevelRepository.level(41), null, emptyList())) }
            steps.add(Step.Run { g -> (g.screen as? BaseScreen)?.let { s -> s.showLives() } }); steps.add(Step.Wait(40)); steps.add(Step.Shot("m_lives"))
            steps.add(Step.Run { g -> g.go(SettingsScreen(g)) }); steps.add(Step.Wait(50)); steps.add(Step.Shot("m_settings"))
            // bölüm sonu (3 yıldız) ve Sonsuz Dünya oyunu
            steps.add(Step.Play((System.getProperty("completeLevel") ?: "120").toInt(), afterFrames = 150))
            steps.add(Step.Shot("m_complete"))
            steps.add(Step.Run { g -> val s = GameScreen(g, null, 42L, emptyList()); g.go(s) }); steps.add(Step.Wait(60))
            steps.add(Step.Run { tap(true) }); steps.add(Step.Wait(2)); steps.add(Step.Run { tap(false) }); steps.add(Step.Wait(28)); steps.add(Step.Shot("m_endless_run"))
        } else {
            // kayıt listesi: ad=bölüm,... ve ekran kayıtları
            val lv = (System.getProperty("recLevels") ?: "2,61,160,215,280,330,377,420,470,120").split(",").map { it.trim().toInt() }
            for (id in lv) steps.add(Step.Play(id, rec = "lv%03d".format(id), afterFrames = 120))
            steps.add(Step.Run { g -> g.go(MapSelectScreen(g)) }); steps.add(Step.Wait(30)); steps.add(Step.RecStart("maps")); steps.add(Step.Wait(90)); steps.add(Step.RecStop())
            steps.add(Step.Run { g -> g.go(CharacterScreen(g)) }); steps.add(Step.Wait(30)); steps.add(Step.RecStart("characters")); steps.add(Step.Wait(90)); steps.add(Step.RecStop())
            steps.add(Step.Run { g -> g.go(LevelSelectScreen(g, 6)) }); steps.add(Step.Wait(30)); steps.add(Step.RecStart("levelmap")); steps.add(Step.Wait(90)); steps.add(Step.RecStop())
            steps.add(Step.Run { g -> g.go(GameScreen(g, null, 42L, emptyList())) }); steps.add(Step.Wait(40))
            steps.add(Step.RecStart("endless")); steps.add(Step.Run { tap(true) }); steps.add(Step.Wait(2)); steps.add(Step.Run { tap(false) })
            steps.add(Step.Wait(30 * 8)); steps.add(Step.RecStop())
            steps.add(Step.Run { g -> g.go(MainMenuScreen(g)) }); steps.add(Step.Wait(30)); steps.add(Step.RecStart("menu")); steps.add(Step.Wait(90)); steps.add(Step.RecStop())
        }
        steps.add(Step.Run { java.io.File("$dir/media_log.txt").writeText(log.toString()); BaseScreen.fixedDelta = null; Gdx.app.exit() })
    }

    /** Vitrin kaydı: orta seviyede bir oyuncu (ilerleme, yıldızlar, birkaç karakter). */
    private fun showcaseSave(g: BesSaniyelikDunya) {
        val d = g.profile.data
        d.coins = 2450; d.gems = 85; d.playerLevel = 14; d.xp = 300
        d.highestUnlocked = 263
        for (i in 1..262) d.levels[i] = com.besaniyelik.dunya.progress.LevelProgress(completed = true, stars = if (i % 7 == 3) 2 else 3, bestCoins = 30)
        d.stats.levelsCompleted = 262; d.stats.endlessBestScore = 18420; d.stats.endlessBestSeconds = 214f; d.stats.endlessBestDistance = 1630f
        d.ownedSkins.addAll(listOf("damla", "cilek", "nane", "bulut", "kedi", "papatya", "kum", "penguen", "mantar"))
        d.selectedSkin = "pati"
        d.pieces.addAll(listOf(8, 18, 28, 38, 48, 58, 68, 78, 108, 118, 158, 168, 208))
        d.collectionsClaimed.add(1)
        d.achievements.addAll(com.besaniyelik.dunya.progress.Catalog.achievements.take(18).map { it.id })
        d.inventory[com.besaniyelik.dunya.level.PowerupType.SHIELD] = 2; d.inventory[com.besaniyelik.dunya.level.PowerupType.MAGNET] = 1
        d.login.lastClaimDay = g.profile.clock.epochDay() - 1; d.login.cycleDay = 3
        d.seenTutorials.addAll(com.besaniyelik.dunya.sim.Mechanic.entries.map { "m_" + it.name })
        com.besaniyelik.dunya.progress.AchievementManager.check(d) // vitrin istatistiklerinin başarıları önceden açılmış olsun
        g.profile.save()
        log.append("dil: ${L.lang}\n")
    }

    private fun tap(down: Boolean) {
        val p = Gdx.input.inputProcessor ?: return
        val x = Gdx.graphics.width / 2; val y = Gdx.graphics.height / 2
        if (down) p.touchDown(x, y, 0, 0) else p.touchUp(x, y, 0, 0)
    }

    fun step(game: BesSaniyelikDunya, frame: Int) {
        if (ffmpeg != null) writeFrame()
        val pg = playing
        val pl = play
        if (pg != null && pl != null) {
            playFrames++
            if (playFrames == 30) { tap(true) }
            if (playFrames == 32) { tap(false) }
            val sim = pg.simulation
            pl.shotsAt.forEach { (t, name) -> if (sim.tick >= t && !taken.contains(name)) { taken.add(name); shot(name) } }
            if ((sim.status == com.besaniyelik.dunya.sim.SimStatus.COMPLETE || sim.tick >= pl.untilTick) && doneAt < 0) doneAt = playFrames
            if (doneAt > 0 && playFrames - doneAt >= pl.afterFrames) {
                log.append("bölüm ${pl.id}: ${sim.status} tick ${sim.tick} yıldız ${sim.starsCollected}\n")
                if (pl.rec != null) stopRec()
                playing = null; play = null
            } else if (playFrames > 30 * 600 || sim.status == com.besaniyelik.dunya.sim.SimStatus.GAME_OVER) {
                log.append("bölüm ${pl.id}: BAŞARISIZ ${sim.status}\n")
                if (pl.rec != null) stopRec()
                playing = null; play = null
            }
            return
        }
        if (waitLeft > 0) { waitLeft--; return }
        val st = steps.removeFirstOrNull() ?: return
        when (st) {
            is Step.Run -> st.action(game)
            is Step.Wait -> waitLeft = st.frames
            is Step.Shot -> shot(st.name)
            is Step.RecStart -> startRec(st.name)
            is Step.RecStop -> stopRec()
            is Step.Play -> {
                val level = com.besaniyelik.dunya.level.LevelRepository.level(st.id)
                val s = GameScreen(game, level, null, emptyList())
                s.autoplay = com.besaniyelik.dunya.level.InputTrack(level.solution.toIntArray())
                game.go(s)
                playing = s; play = st; playFrames = 0; doneAt = -1
                if (st.rec != null) startRec(st.rec)
            }
        }
    }

    private val taken = HashSet<String>()

    private fun startRec(name: String) {
        stopRec()
        recW = Gdx.graphics.backBufferWidth; recH = Gdx.graphics.backBufferHeight
        val pb = ProcessBuilder("ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgba", "-s", "${recW}x$recH", "-r", "30", "-i", "-",
            "-vf", "vflip", "-c:v", "libx264", "-preset", "veryfast", "-crf", "12", "-pix_fmt", "yuv420p", "$dir/$name.mp4")
        pb.redirectErrorStream(true); pb.redirectOutput(java.io.File("$dir/$name.ffmpeg.log"))
        ffmpeg = pb.start()
        log.append("kayıt: $name ${recW}x$recH\n")
    }

    private fun writeFrame() {
        val px = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixels(0, 0, recW, recH, false)
        try { ffmpeg?.outputStream?.write(px) } catch (e: Exception) { log.append("kayıt hatası: ${e.message}\n"); ffmpeg = null }
    }

    private fun stopRec() {
        val p = ffmpeg ?: return
        ffmpeg = null
        try { p.outputStream.close(); p.waitFor() } catch (_: Exception) {}
    }

    private fun shot(name: String) {
        val pm: Pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        val flipped = Pixmap(pm.width, pm.height, pm.format)
        for (y in 0 until pm.height) flipped.drawPixmap(pm, 0, y, pm.width, 1, 0, pm.height - 1 - y, pm.width, 1)
        PixmapIO.writePNG(Gdx.files.absolute("$dir/$name.png"), flipped)
        pm.dispose(); flipped.dispose()
    }
}

/**
 * Performans ve bellek ölçümü: bölümleri art arda (gerçek hızda) oynatır; her bölüm sonunda çöp toplama sonrası
 * kullanılan bellek ve kare süreleri yazılır. Bellek bölümden bölüme artmıyorsa sızıntı yoktur.
 */
class PerfTour(private val out: String) {
    private val levels = (System.getProperty("perfLevels") ?: "12,61,160,215,280,330,377,420,470,500,12,61").split(",").map { it.trim().toInt() }
    private var idx = -1
    private var screen: GameScreen? = null
    private var frames = 0
    private var last = 0L
    private val times = ArrayList<Long>()
    private val log = StringBuilder()
    private val t0 = System.nanoTime()
    private var waitFrames = 90

    private fun usedMb(): Long { System.gc(); System.gc(); val r = Runtime.getRuntime(); return (r.totalMemory() - r.freeMemory()) / (1024 * 1024) }

    fun step(game: BesSaniyelikDunya, frame: Int) {
        val now = System.nanoTime()
        if (last != 0L && screen != null) times.add(now - last)
        last = now
        if (frame == 1) log.append("ilk kare: %.2f sn\n".format((now - t0) / 1e9))
        if (waitFrames > 0) { waitFrames--; return }
        val s = screen
        if (s != null) {
            frames++
            if (frames == 20) { val p = Gdx.input.inputProcessor; p?.touchDown(10, 10, 0, 0); p?.touchUp(10, 10, 0, 0) }
            val st = s.simulation.status
            if (st == com.besaniyelik.dunya.sim.SimStatus.COMPLETE || st == com.besaniyelik.dunya.sim.SimStatus.GAME_OVER || frames > 60 * 120) {
                val sorted = times.sorted()
                val avg = times.average() / 1e6; val p95 = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)] / 1e6
                log.append("bölüm ${levels[idx]}: $st, kare ${times.size}, ort %.1f ms, p95 %.1f ms, bellek ${usedMb()} MB, java heap max ${Runtime.getRuntime().maxMemory() / 1048576} MB\n".format(avg, p95))
                screen = null; times.clear(); waitFrames = 30
                game.go(MainMenuScreen(game))
            }
            return
        }
        idx++
        if (idx >= levels.size) {
            log.append("menüdeyken bellek: ${usedMb()} MB\n")
            java.io.File(out).writeText(log.toString()); Gdx.app.exit(); return
        }
        if (idx == 0) log.append("başlangıç belleği: ${usedMb()} MB\n")
        val lv = com.besaniyelik.dunya.level.LevelRepository.level(levels[idx])
        val g = GameScreen(game, lv, null, emptyList())
        g.autoplay = com.besaniyelik.dunya.level.InputTrack(lv.solution.toIntArray())
        game.go(g); screen = g; frames = 0; times.clear(); waitFrames = 0
    }
}
