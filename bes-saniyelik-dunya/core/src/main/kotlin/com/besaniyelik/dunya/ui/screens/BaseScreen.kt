package com.besaniyelik.dunya.ui.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.besaniyelik.dunya.BesSaniyelikDunya
import com.besaniyelik.dunya.Theme
import com.besaniyelik.dunya.progress.Catalog
import com.besaniyelik.dunya.render.CharState
import com.besaniyelik.dunya.render.CharacterRenderer
import com.besaniyelik.dunya.ui.Palette
import com.besaniyelik.dunya.ui.UiButton
import com.besaniyelik.dunya.ui.popIn
import com.besaniyelik.dunya.i18n.L

/** Menülerde arkada kayan, açık renkli tema manzarası. */
class MenuBackground(private val game: BesSaniyelikDunya, private var map: Int = 1) {
    private val cam = OrthographicCamera()
    private val vp = ExtendViewport(24f, 13.5f, cam)
    private var t = 0f
    private var theme: Theme = game.assets.theme(map)
    private val clouds = List(6) { floatArrayOf(MathUtils.random(0f, 14f), MathUtils.random(6f, 16f), MathUtils.random(0.6f, 1.3f)) }
    var showRunner = true
    /** Verilirse arka plan zamanla değil bu değere (ör. kaydırma konumu) göre kayar. */
    var scrollOverride: (() -> Float)? = null

    fun setMap(m: Int) { if (m != map) { map = m; theme = game.assets.theme(m) } }
    fun resize(w: Int, h: Int) = vp.update(w, h, true)

    fun render(dt: Float) {
        t += dt
        val b = game.batch
        vp.apply()
        val cxw = vp.worldWidth / 2f
        cam.position.set(cxw, 5f, 0f); cam.update()
        b.projectionMatrix = cam.combined
        b.begin()
        val vw = vp.worldWidth; val vh = vp.worldHeight
        val left = cxw - vw / 2; val bottom = 5f - vh / 2
        theme.drawSky(b, left, bottom, vw, vh)
        val scroll = scrollOverride?.invoke() ?: (t * 1.2f)
        fun par(tex: com.badlogic.gdx.graphics.Texture, y: Float, h: Float, f: Float) {
            val tw = h * com.besaniyelik.dunya.Theme.PARALLAX_ASPECT
            val u0 = (scroll * f) / tw
            b.draw(tex, left, y, vw, h, u0, 1f, u0 + vw / tw, 0f)
        }
        par(theme.far, -1.2f, 9.5f, 0.15f)
        par(theme.mid, -1.8f, 7.2f, 0.4f)
        val cloud = game.assets.g("pt_dust")
        b.setColor(1f, 1f, 1f, 0.85f)
        for (c in clouds) {
            val x = ((c[0] - t * 0.3f * c[2]) % 16f + 16f) % 16f - 2f + left
            b.draw(cloud, x, c[1], 2.4f * c[2], 1.4f * c[2])
        }
        b.setColor(1f, 1f, 1f, 1f)
        // zemin şeridi
        var x = left - ((scroll) % 1f)
        while (x < left + vw) {
            b.draw(theme.top, x, -1f, 1f, 1f)
            var y = -2f
            while (y > bottom - 1f) { b.draw(theme.fill, x, y, 1f, 1f); y -= 1f }
            x += 1f
        }
        if (showRunner) {
            val skin = Catalog.skin(game.profile.data.selectedSkin)
            val jump = ((t % 2.2f) / 2.2f)
            val jy = if (jump > 0.7f) kotlin.math.sin((jump - 0.7f) / 0.3f * Math.PI.toFloat()) * 1.6f else 0f
            renderer.draw(b, skin, left + vw * 0.28f, 0.7f + jy * 1.3f, 1.4f, if (jy > 0.05f) CharState.JUMP else CharState.RUN, t)
        }
        b.end()
    }

    private val renderer = CharacterRenderer(game.assets)
}

/** Bütün ekranların temeli: Scene2D sahnesi, geri tuşu, geçiş, bildirim ve diyalog yardımcıları. */
abstract class BaseScreen(val game: BesSaniyelikDunya) : ScreenAdapter() {
    /** Arayüz sanal çözünürlüğü: yatay 1280 x 720 (geniş ekranlarda genişlik artar). */
    val stage = Stage(ExtendViewport(1280f, 720f), game.batch)
    val kit get() = game.kit
    val a get() = game.assets
    val root = Table().apply { setFillParent(true); top() }
    open val background: MenuBackground? by lazy { MenuBackground(game) }
    /** Ekran açılırken hangi renkten belirdiği (çıkış her zaman beyaza). */
    open val fadeInColor: Color = Color.WHITE
    private var fade = 1f
    private var fadeDir = -1f
    private var fadeDone: (() -> Unit)? = null
    private val fadeImg by lazy { Image(a.ui("white")).apply { color = Color(1f, 1f, 1f, 1f); touchable = Touchable.disabled } }
    protected val input = InputMultiplexer()

    init {
        stage.addActor(root)
        input.addProcessor(stage)
        input.addProcessor(object : InputAdapter() {
            override fun keyDown(keycode: Int): Boolean {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) { if (!adBusy) onBack(); return true }
                return false
            }
        })
    }

    abstract fun build()

    open fun onBack() { game.go(MainMenuScreen(game)) }

    override fun show() {
        game.ads.setBanner(bannerAllowed)
        Gdx.input.inputProcessor = input
        build()
        stage.addActor(fadeImg)
        fade = 1f; fadeDir = -1f
    }

    fun rebuild() {
        root.clear()
        build()
        fadeImg.toFront()
    }

    fun fadeOut(done: () -> Unit) { fadeDir = 1f; fadeDone = done; Gdx.input.inputProcessor = null }

    /** Ödüllü reklam düğmesi olan menü ekranları true döndürür: reklamın hazır olma durumu değişince haber verilir. */
    open val watchesAdReadiness: Boolean = false
    private var adReadyKnown: Boolean? = null
    private var adPoll = 0f

    /** Ödüllü reklam hazır oldu / hazır olmaktan çıktı (saniyede iki kez yoklanır). */
    open fun onAdReadyChanged(ready: Boolean) {}

    /** build() içinde kullanılır: ekrana çizilen "hazır" durumu, yoklamanın karşılaştırdığı değer olur. */
    protected fun adReadyNow(): Boolean { val r = game.ads.rewardedReady; adReadyKnown = r; return r }

    override fun render(delta: Float) {
        val dt = (fixedDelta ?: delta).coerceAtMost(1 / 20f)
        syncBanner()
        if (watchesAdReadiness && !adBusy) {
            adPoll -= dt
            if (adPoll <= 0f) {
                adPoll = 0.5f
                val ready = game.ads.rewardedReady
                if (adReadyKnown != null && ready != adReadyKnown) onAdReadyChanged(ready)
                adReadyKnown = ready
            }
        }
        Gdx.gl.glClearColor(Palette.BG.r, Palette.BG.g, Palette.BG.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        renderWorld(dt)
        stage.act(dt)
        fade = (fade + fadeDir * dt * 5f).coerceIn(0f, 1f)
        fadeImg.setBounds(-2000f, -2000f, 6000f, 6000f)
        val fc = if (fadeDir < 0) fadeInColor else Color.WHITE
        fadeImg.color.set(fc.r, fc.g, fc.b, Interpolation.smooth.apply(fade))
        fadeImg.isVisible = fade > 0.001f
        stage.viewport.apply()
        stage.draw()
        if (fadeDir > 0 && fade >= 1f) { val f = fadeDone; fadeDone = null; f?.invoke() }
    }

    open fun renderWorld(dt: Float) { background?.render(dt) }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        background?.resize(width, height)
        applySafeArea()
    }

    /**
     * Çentikli / kamera delikli ekranlarda arayüz güvenli alana çekilir. Android 15+ oyunu kenardan kenara çizer;
     * köşedeki duraklat düğmesi ya da sayaçlar çentiğin altında kalmasın diye kök tablo, ekran kesiği kadar içeri alınır.
     * Kesik olmayan ekranlarda (ve masaüstünde) pay sıfırdır.
     */
    private fun applySafeArea() {
        val g = Gdx.graphics
        if (g.width <= 0 || g.height <= 0) return
        val d = debugInsets
        val l = d?.get(0) ?: g.safeInsetLeft; val t = d?.get(1) ?: g.safeInsetTop
        val r = d?.get(2) ?: g.safeInsetRight; val b = d?.get(3) ?: g.safeInsetBottom
        val sx = stage.viewport.worldWidth / g.width
        val sy = stage.viewport.worldHeight / g.height
        // afiş reklam görünüyorsa içerik onun üstünde biter (afiş hiçbir düğmenin üstüne binmez)
        root.pad(t * sy, l * sx, (b + bannerPx) * sy, r * sx)
        root.invalidate() // pad() tek başına yerleşimi yenilemez; afiş yüksekliği ekran açıldıktan sonra da gelebilir
        // İçerik afişin üstüne sığmıyorsa (kaydırılmayan, yüksek ekran) bu ekranda afiş hiç gösterilmez
        if (bannerPx > 0) {
            root.validate()
            if (root.minHeight > stage.height + 0.5f) {
                bannerBlocked = true
                game.ads.setBanner(false)
                bannerPx = 0
                root.pad(t * sy, l * sx, b * sy, r * sx); root.invalidate()
            }
        }
        if (debugBannerOverlay) {
            debugBanner?.remove(); debugBanner = null
            if (bannerPx > 0) {
                val box = Table().apply { background = kit.patch("white").tint(Color(0.2f, 0.2f, 0.25f, 0.85f)); touchable = Touchable.disabled }
                box.add(kit.label(L.t("ads.banner_area"), a.f22, Color.WHITE))
                box.setBounds(0f, 0f, stage.width, bannerPx * sy)
                stage.addActor(box); debugBanner = box
            }
        }
    }

    /**
     * Bu ekranın altında afiş (banner) reklam gösterilebilir mi? Yalnızca içeriği kaydırılabilen liste ekranlarında
     * açılır; oyun ekranında ve altı düğmelerle dolu ekranlarda kapalıdır.
     */
    open val bannerAllowed: Boolean = false
    private var bannerPx = 0
    /** Bu ekranın içeriği afişle birlikte sığmadı: ekran kapanana kadar afiş istenmez. */
    private var bannerBlocked = false
    private var debugBanner: Table? = null

    /** Afişin yüksekliği reklam yüklendiğinde belli olur; değişince yerleşim yeniden hesaplanır. */
    private fun syncBanner() {
        val bp = if (bannerAllowed && !bannerBlocked) game.ads.bannerHeightPx else 0
        if (bp != bannerPx) { bannerPx = bp; applySafeArea() }
    }

    private var adBusy = false

    /**
     * İsteğe bağlı ödüllü reklam: yalnızca oyuncu, ödülü açıkça yazan bir düğmeye bastığında çağrılır. Aynı anda tek
     * istek işlenir; reklam tamamlanmazsa ödül verilmez ve oyuncuya bildirilir.
     */
    fun watchAd(onEarned: () -> Unit) {
        if (adBusy) return
        adBusy = true
        // Reklam açılana kadar (ve açılamazsa sonuç gelene kadar) ekrandaki diğer düğmeler kilitlenir: aynı anda
        // "elmasla devam" ya da "bitir" gibi ikinci bir seçim yapılamaz, ödül iki kez ya da hiç verilmez.
        val shield = Table().apply {
            setFillParent(true); touchable = Touchable.enabled
            background = kit.patch("white").tint(Color(0.97f, 0.98f, 1f, 0.7f))
            add(kit.label(L.t("ads.opening"), a.f36, Palette.INK))
        }
        stage.addActor(shield)
        stage.cancelTouchFocus() // o anda başka bir düğmede duran ikinci parmağın dokunuşu da iptal edilir
        val done = { adBusy = false; shield.remove() }
        game.ads.showRewarded(
            onEarned = { done(); if (game.screen === this) onEarned() },
            onSkipped = { done(); if (game.screen === this) toast(L.t("ads.not_completed")) },
        )
    }

    companion object {
        /** Yalnızca masaüstü denemesi için: [sol, üst, sağ, alt] ekran kesiği (piksel). */
        var debugInsets: IntArray? = null
        /** Yalnızca masaüstü denemesi için: afişin kaplayacağı alanı koyu bir şeritle gösterir. */
        var debugBannerOverlay = false
        /** Yalnızca masaüstü video kaydı için: her kare bu kadar oyun süresi ilerler (gerçek süreden bağımsız). */
        var fixedDelta: Float? = null
    }

    override fun dispose() { stage.dispose() }

    // ------------------------------------------------------------ yardımcılar
    /** Üst çubuk: geri butonu, başlık ve para birimleri. */
    fun header(title: String, showCurrency: Boolean = true, back: () -> Unit = { onBack() }): Table {
        val t = Table()
        t.add(kit.iconButton("g_back", "white", 92f, 48f) { back() }).size(92f).left().padLeft(20f)
        val titleCell = Table().apply { background = kit.patch("banner"); pad(10f, 30f, 14f, 30f); add(kit.label(title, a.f36, Palette.INK)) }
        t.add(titleCell).expandX().center()
        if (showCurrency) {
            val c = Table()
            c.add(kit.currency("b_coin", game.profile.data.coins.toString(), a.f22)).right().row()
            c.add(kit.currency("b_gem", game.profile.data.gems.toString(), a.f22)).right().padTop(4f)
            val right = Table()
            if (game.lives.enabled) right.add(livesChip(announce = true)).padRight(10f)
            right.add(c).right().width(160f)
            t.add(right).right().padRight(16f)
        } else t.add().width(112f)
        return t
    }

    private var toastY = 0f
    /** Bildirim süresi (oyun ekranı kısaltır). */
    open val toastTime = 2.4f
    /** Oyun ekranında bildirimler sol üstte, küçük gösterilir (ipucu ve kural etiketiyle çakışmasın). */
    open val toastCompact = false
    fun toast(text: String, icon: String? = null) {
        val t = Table().apply {
            background = kit.patch("banner"); pad(16f, 26f, 20f, 26f)
            if (icon != null) add(Image(a.ui(icon))).size(56f).padRight(12f)
            if (toastCompact) { pad(10f, 16f, 14f, 18f); cells.firstOrNull()?.size(42f)?.padRight(8f) }
            add(kit.label(text, if (toastCompact) a.f22 else a.f28, Palette.INK, wrap = true)).width(if (toastCompact) 240f else 460f)
        }
        t.pack()
        if (toastCompact) t.setPosition(24f + root.padLeft, stage.height - 118f - root.padTop - t.height - toastY)
        else t.setPosition((stage.width - t.width) / 2, stage.height - 150f - toastY)
        toastY += t.height + 10f
        t.color.a = 0f
        t.addAction(Actions.sequence(Actions.fadeIn(0.2f), Actions.delay(toastTime), Actions.fadeOut(0.3f), Actions.run { toastY = (toastY - t.height - 10f).coerceAtLeast(0f) }, Actions.removeActor()))
        stage.addActor(t)
    }

    /** Ortada açılan modal diyalog; arka plan yarı saydam açık renk. */
    /** Sabit boyutlu sonuç pencereleri için ölçü (içerik ne olursa olsun pencere aynı kalır). */
    val resultW get() = kotlin.math.min(1060f, stage.viewport.worldWidth - 40f)
    val resultH get() = kotlin.math.min(620f, stage.viewport.worldHeight - 40f)

    fun dialog(fixedW: Float = 0f, fixedH: Float = 0f, build: (Table, () -> Unit) -> Unit): Table {
        val overlay = Table().apply { setFillParent(true); background = kit.patch("white").tint(Color(0.97f, 0.98f, 1f, 0.6f)); touchable = Touchable.enabled }
        val panel = kit.panel()
        lateinit var close: () -> Unit
        close = { overlay.addAction(Actions.sequence(Actions.fadeOut(0.15f), Actions.removeActor())) }
        build(panel, close)
        // yatay ekranda yüksek içerik kaydırılabilir olur
        if (fixedW > 0f && fixedH > 0f) {
            overlay.add(panel).size(fixedW, fixedH).center()
            overlay.addListener(object : ClickListener() { override fun clicked(event: InputEvent?, x: Float, y: Float) {} })
            stage.addActor(overlay)
            panel.popIn()
            return overlay
        }
        panel.pack()
        val w = kotlin.math.max(640f, panel.prefWidth)
        val maxH = stage.viewport.worldHeight - 24f
        if (panel.prefHeight > maxH) overlay.add(scroll(panel)).width(w).height(maxH).center()
        else overlay.add(panel).width(w).center()
        overlay.addListener(object : ClickListener() { override fun clicked(event: InputEvent?, x: Float, y: Float) {} })
        stage.addActor(overlay)
        panel.popIn()
        return overlay
    }

    fun scroll(content: Actor): ScrollPane = ScrollPane(content).apply { setScrollingDisabled(true, false); fadeScrollBars = true; setOverscroll(false, true) }
}
