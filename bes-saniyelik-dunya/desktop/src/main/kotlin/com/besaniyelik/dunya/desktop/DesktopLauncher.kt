package com.besaniyelik.dunya.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.ScreenUtils
import com.besaniyelik.dunya.BesSaniyelikDunya
import com.besaniyelik.dunya.level.LevelRepository
import com.besaniyelik.dunya.ui.screens.*

fun main(args: Array<String>) {
    val shots = System.getProperty("shots")
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("5 Saniyelik Dünya")
        setWindowedMode(450, 800)
        useVsync(true)
        setForegroundFPS(60)
        if (shots != null) setWindowedMode(540, 960)
    }
    val game = BesSaniyelikDunya()
    if (shots != null) game.debugHook = ScreenshotTour(shots)::step
    Lwjgl3Application(game, config)
}

/** Ekranları sırayla açıp görüntü alır; bölümleri kayıtlı çözümle gerçek oyun ekranında baştan sona oynatır. */
class ScreenshotTour(private val dir: String) {
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
        fun screen(name: String, f: (BesSaniyelikDunya) -> Unit) { steps.add(Step.Run(f)); steps.add(Step.Wait(45)); steps.add(Step.Shot(name)) }
        steps.add(Step.Wait(20)); steps.add(Step.Shot("01_splash"))
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
            screen("14_daily") { it.go(DailyRewardScreen(it)) }
            screen("15_howto") { it.go(HowToPlayScreen(it)) }
        }
        val levels = (System.getProperty("tourLevels") ?: "1,23,61,160,215,280,330,377,420,470").split(",").filter { it.isNotBlank() }.map { it.trim().toInt() }
        levels.forEach { steps.add(Step.PlayLevel(it, 3)) }
        if (System.getProperty("tourEndless") != "0") {
            screen("40_endless") { g -> g.go(GameScreen(g, null, 42L, emptyList())) }
            steps.add(Step.Run { tap(true) }); steps.add(Step.Wait(2)); steps.add(Step.Run { tap(false) }); steps.add(Step.Wait(150)); steps.add(Step.Shot("41_endless_run"))
        }
        steps.add(Step.Run { java.io.File("$dir/tour_log.txt").writeText(log.toString()); Gdx.app.exit() })
    }

    private fun tap(down: Boolean) {
        val p = Gdx.input.inputProcessor ?: return
        if (down) p.touchDown(10, 700, 0, 0) else p.touchUp(10, 700, 0, 0)
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
            } else if (playFrames > 60 * 200 || sim.status == com.besaniyelik.dunya.sim.SimStatus.GAME_OVER) {
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
