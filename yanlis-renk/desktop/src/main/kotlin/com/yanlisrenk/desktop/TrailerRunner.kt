package com.yanlisrenk.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.Scaling
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.yanlisrenk.game.Brand
import com.yanlisrenk.game.YanlisRenkGame
import com.yanlisrenk.game.screens.*
import com.yanlisrenk.game.ui.Pal
import com.yanlisrenk.game.ui.Ui
import com.yanlisrenk.logic.i18n.L
import com.yanlisrenk.logic.shop.Currency
import java.io.File
import java.io.OutputStream

/**
 * Tanıtım videosu kaydedici. Oyunu GERÇEK oynanışla (otomatik oyuncu) sabit 1/FPS adımlarla çalıştırır,
 * her kareyi ham RGBA olarak ffmpeg'e aktarır (H.264). Altyazı (SRT) ve sahne listesi aynı veriden yazılır.
 *
 * Argümanlar: <çıktı.mp4> lang=tr|en variant=store|social size=1080x1920 fps=30
 * Sahne metinleri: tools/trailer/captions_<lang>.json (düzenlenebilir kaynak).
 */
class TrailerGame(
    platform: DesktopPlatform,
    private val out: File,
    private val lang: String,
    private val variant: String,
    private val fps: Int,
    private val captions: Map<String, String>,
) : com.badlogic.gdx.ApplicationListener {
    val game = YanlisRenkGame(platform)
    private lateinit var overlay: Stage
    private lateinit var ffmpeg: Process
    private lateinit var pipe: OutputStream
    private var shotIndex = -1
    private var shotFrame = 0
    private var totalFrames = 0
    private val srt = StringBuilder()
    private var srtIndex = 0
    private var lastCaption: String? = null
    private var captionStart = 0f
    private var scrollPane: ScrollPane? = null
    private var scrollFrom = 0f
    private var scrollTo = 0f

    /** dur: saniye; caption: captions_<lang>.json anahtarı; setup: sahneyi kurar. */
    private class Shot(val dur: Float, val caption: String?, val scroll: Float = 0f, val setup: () -> Unit)
    private val shots = ArrayList<Shot>()

    private fun play(mode: Mode, level: Int, skip: Float): () -> Unit = {
        val s = game.session
        s.data.unlockedLevel = maxOf(s.data.unlockedLevel, level); s.lives.refill()
        game.go({ GameScreen(game, mode, level) }, remember = false)
        (game.screen as GameScreen).debugStart(skip)
    }

    override fun create() {
        game.autoSync = false
        game.create()
        val s = game.session
        s.data.settings.language = lang
        L.setLanguage(lang)
        s.data.settings.reducedMotion = false
        for (i in 1..37) s.data.levelStars[i] = 3
        s.data.unlockedLevel = 38
        s.data.areaIntroSeen.addAll(1..100)
        s.data.tutorialsSeen.add("intro")
        s.credit(Currency.GOLD, 5200, "trailer"); s.credit(Currency.DIAMOND, 60, "trailer")
        listOf("char_robot", "char_panda", "char_kedi", "char_uzayli", "char_kristal").forEach { s.data.owned.add(it) }
        s.data.equipped["CHARACTER"] = "char_kedi"
        s.addXp(900); s.pendingAchievementToasts.clear()
        overlay = Stage(ExtendViewport(720f, 1280f))

        // 0–3 sn: kanca · 3–8: mekanik · 8–15: deneyim · 15–22: çeşitlilik · 22–27: başarı · 27–30: marka
        shots += Shot(3.0f, "hook", setup = play(Mode.LEVEL, 63, 8.5f))
        shots += Shot(2.5f, "tap", setup = play(Mode.LEVEL, 3, 4.0f))
        shots += Shot(2.5f, "rule", setup = play(Mode.LEVEL, 7, 6.5f))
        shots += Shot(3.5f, "twists", setup = play(Mode.LEVEL, 52, 11.0f))
        shots += Shot(3.5f, "surprise", setup = play(Mode.LEVEL, 82, 6.0f))
        shots += Shot(1.6f, "levels", scroll = -900f) { game.go({ SagaMapScreen(game, 30) }, remember = false) }
        shots += Shot(1.2f, "worlds", setup = play(Mode.LEVEL, 278, 8.0f))
        shots += Shot(1.2f, "worlds", setup = play(Mode.LEVEL, 428, 7.0f))
        shots += Shot(1.4f, "characters", scroll = 700f) { game.go({ CharactersScreen(game) }, remember = false) }
        shots += Shot(1.6f, "duel") {
            game.go({ TwoPlayerScreen(game, 7L) }, remember = false)
            (game.screen as TwoPlayerScreen).let { it.debugPlay(9f); it.autoPlay = true }
        }
        shots += Shot(2.5f, "stars") {
            game.go({ GameScreen(game, Mode.LEVEL, 12) }, remember = false)
            (game.screen as GameScreen).debugStart(200f)
        }
        shots += Shot(2.5f, "endless", setup = play(Mode.ENDLESS, 0, 32f))
        shots += Shot(3.0f, null) { brandCard() }

        val (w, h) = Gdx.graphics.backBufferWidth to Gdx.graphics.backBufferHeight
        out.parentFile?.mkdirs()
        ffmpeg = ProcessBuilder(
            "ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgba", "-s", "${w}x$h", "-r", "$fps", "-i", "-",
            "-vf", "vflip", "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", "-r", "$fps", out.absolutePath,
        ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.INHERIT).start()
        pipe = ffmpeg.outputStream.buffered(1 shl 22)
        nextShot()
    }

    private fun nextShot() {
        shotIndex++
        shotFrame = 0
        if (shotIndex >= shots.size) { finish(); return }
        val sh = shots[shotIndex]
        overlay.clear()
        sh.setup()
        scrollPane = null
        if (sh.scroll != 0f) {
            (game.screen as? BaseScreen)?.stage?.let { st ->
                st.act(0f); st.root.children.forEach { (it as? Group)?.validateAll() }
                scrollPane = findScroll(st.root)?.also { sp ->
                    sp.layout(); sp.updateVisualScroll()
                    scrollFrom = sp.scrollY; scrollTo = (sp.scrollY + sh.scroll).coerceIn(0f, sp.maxY)
                    if (scrollTo == scrollFrom) { scrollFrom = (sp.maxY * 0.15f); scrollTo = (scrollFrom + kotlin.math.abs(sh.scroll)).coerceAtMost(sp.maxY) }
                }
            }
        }
        setCaption(sh.caption)
    }

    private fun Group.validateAll() { (this as? com.badlogic.gdx.scenes.scene2d.utils.Layout)?.validate(); children.forEach { (it as? Group)?.validateAll() } }

    private fun findScroll(a: Actor): ScrollPane? {
        if (a is ScrollPane) return a
        if (a is Group) for (c in a.children) findScroll(c)?.let { return it }
        return null
    }

    private val now: Float get() = totalFrames / fps.toFloat()

    private fun setCaption(key: String?) {
        val text = key?.let { captions[it] }
        if (text == lastCaption) return
        closeSrt()
        lastCaption = text
        captionStart = now
        if (text == null) return
        val ui = game.ui
        val t = Table(); t.setFillParent(true); t.top().padTop(185f)
        val card = Table(); card.background = ui.pill(Pal.hex(0xFFFFFF, 0.93f)); card.pad(18f, 40f, 22f, 40f)
        card.add(ui.label(text, Ui.Size.XL, Pal.TEXT, Align.center, wrap = true)).width(600f)
        t.add(card).maxWidth(680f)
        card.isTransform = true; card.setOrigin(Align.center)
        card.color.a = 0f; card.setScale(0.85f)
        card.addAction(Actions.parallel(Actions.fadeIn(0.18f), Actions.scaleTo(1f, 1f, 0.25f, com.badlogic.gdx.math.Interpolation.swingOut)))
        overlay.addActor(t)
        t.layout(); card.setOrigin(Align.center)
    }

    private fun closeSrt() {
        val text = lastCaption ?: return
        if (now - captionStart < 0.05f) return
        srtIndex++
        srt.append(srtIndex).append('\n').append(ts(captionStart)).append(" --> ").append(ts(now)).append('\n').append(text.replace("\n", " ")).append("\n\n")
    }

    private fun ts(t: Float): String {
        val ms = (t * 1000).toLong()
        return "%02d:%02d:%02d,%03d".format(ms / 3600000, ms / 60000 % 60, ms / 1000 % 60, ms % 1000)
    }

    /** 27–30 sn: marka kapanışı (logo, iki dilli ad, geliştirici; sosyal sürümde çağrı butonu). */
    private fun brandCard() {
        game.go({ SplashLikeBackground(game) }, remember = false)
        val ui = game.ui
        val t = Table(); t.setFillParent(true)
        val key = Brand.logo(game.assets)
        t.add(Image(game.assets.drawable(key), Scaling.fit)).size(620f, if (Brand.turkishMain) 470f else 260f).row()
        t.add(ui.label(Brand.subName, Ui.Size.XL, Pal.TEXT)).padTop(4f).row()
        if (variant == "social") {
            val b = ui.button(captions["cta"] ?: "", Pal.GREEN, "play", Ui.Size.XL) {}
            t.add(b).width(560f).height(150f).padTop(50f).row()
            b.isTransform = true
            b.addAction(Actions.forever(Actions.sequence(Actions.scaleTo(1.06f, 1.06f, 0.45f), Actions.scaleTo(1f, 1f, 0.45f))))
            captions["cta_sub"]?.let { t.add(ui.label(it, Ui.Size.M, Pal.TEXT_SOFT)).padTop(18f).row() }
        } else {
            captions["tagline"]?.let { t.add(ui.label(it, Ui.Size.L, Pal.TEXT_SOFT)).padTop(36f).row() }
        }
        t.add(ui.label(Brand.DEVELOPER, Ui.Size.M, Pal.TEXT_SOFT)).padTop(70f).row()
        t.color.a = 0f
        t.addAction(Actions.fadeIn(0.35f))
        overlay.addActor(t)
        t.layout()
        overlay.root.children.forEach { a -> if (a is Table) a.children.forEach { c -> if (c is com.badlogic.gdx.scenes.scene2d.ui.Button) c.setOrigin(Align.center) } }
    }

    override fun render() {
        if (shotIndex >= shots.size) return
        val dt = 1f / fps
        Gdx.gl.glClearColor(1f, 1f, 1f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        game.screen?.render(dt)
        scrollPane?.let { sp ->
            val k = (shotFrame + 1f) / (shots[shotIndex].dur * fps)
            val e = com.badlogic.gdx.math.Interpolation.smooth.apply(k.coerceIn(0f, 1f))
            sp.scrollY = scrollFrom + (scrollTo - scrollFrom) * e; sp.updateVisualScroll()
        }
        overlay.viewport.apply()
        overlay.act(dt); overlay.draw()
        val w = Gdx.graphics.backBufferWidth; val h = Gdx.graphics.backBufferHeight
        val px = ScreenUtils.getFrameBufferPixels(0, 0, w, h, false)
        pipe.write(px)
        totalFrames++; shotFrame++
        if (shotFrame >= Math.round(shots[shotIndex].dur * fps)) nextShot()
    }

    private fun finish() {
        closeSrt(); lastCaption = null
        pipe.flush(); pipe.close()
        val code = ffmpeg.waitFor()
        File(out.parentFile, out.nameWithoutExtension + ".srt").writeText(srt.toString())
        File(out.parentFile, out.nameWithoutExtension + "_shots.txt").writeText(
            "frames=$totalFrames fps=$fps duration=${"%.2f".format(now)}s ffmpeg=$code lang=$lang variant=$variant\n")
        println("TRAILER OK: ${out.absolutePath} frames=$totalFrames ffmpeg=$code")
        Gdx.app.exit()
    }

    override fun resize(width: Int, height: Int) { game.resize(width, height); overlay.viewport.update(width, height, true) }
    override fun pause() {}
    override fun resume() {}
    override fun dispose() { runCatching { game.dispose() } }
}

/** Marka kapanışı için yalnızca menü arka planını çizen boş ekran. */
class SplashLikeBackground(game: YanlisRenkGame) : BaseScreen(game) {
    override val showBanner: Boolean = false
    override fun build() {}
}

fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/trailer.mp4")
    val opts = args.drop(1).filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
    val lang = opts["lang"] ?: "tr"
    val variant = opts["variant"] ?: "store"
    val fps = opts["fps"]?.toIntOrNull() ?: 30
    val (w, h) = (opts["size"] ?: "1080x1920").split('x').map { it.toInt() }
    val capFile = File(opts["captions"] ?: "../../tools/trailer/captions_$lang.json")
    val captions = L.parse(capFile.readText())
    val tmp = kotlin.io.path.createTempDirectory("yrtrailer").toFile()
    val platform = DesktopPlatform(tmp).also { it.allowNetwork = false; it.fakeAds = FakeAds(bannerPx = 0) }
    val cfg = Lwjgl3ApplicationConfiguration()
    cfg.setTitle("Trailer")
    cfg.setWindowedMode(w, h)
    cfg.setResizable(false)
    cfg.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4)
    cfg.setForegroundFPS(0)
    cfg.useVsync(false)
    cfg.disableAudio(true)
    Lwjgl3Application(TrailerGame(platform, out, lang, variant, fps, captions), cfg)
}
