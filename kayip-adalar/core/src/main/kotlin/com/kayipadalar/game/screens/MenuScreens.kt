package com.kayipadalar.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Scaling
import com.kayipadalar.game.KayipAdalarGame
import com.kayipadalar.game.ui.Palette
import com.kayipadalar.game.view.Bar
import kotlin.math.sin

/** Yükleme ekranı: atlas yüklenirken logo ve ilerleme çubuğu. */
class SplashScreen(app: KayipAdalarGame) : BaseScreen(app) {
    override val drawSky = false
    private var ready = false
    private var shown = 0f

    override fun build() {
        root.center()
        root.add(label("Kayıp Adalar", "huge", align = Align.center)).row()
        root.add(label("Hareketli Krallık", "title", align = Align.center)).padBottom(40f).row()
        root.add(Bar(theme, true, 26f) { app.assets.progress }).width(480f).height(26f).row()
        root.add(label("Adalar yükleniyor…", "lightSmall")).padTop(12f)
    }

    override fun update(dt: Float) {
        shown += dt
        if (!ready && app.assets.update()) {
            ready = true
            app.assets.finish()
            app.onAssetsReady()
        }
        if (ready && shown > 0.6f) app.resetTo(MenuScreen(app))
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.12f, 0.21f, 0.34f, 1f)
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT)
        update(delta.coerceAtMost(0.05f))
        stage.act(delta); stage.draw()
    }
}

/** Menü arka planındaki süzülen ada kompozisyonu (tamamen sprite). */
class MenuIsland(private val app: KayipAdalarGame) : Actor() {
    private var t = 0f
    override fun act(delta: Float) { super.act(delta); t += delta }
    override fun draw(batch: Batch, parentAlpha: Float) {
        val a = app.assets
        fun put(name: String, cx: Float, cy: Float, w: Float, phase: Float = 0f) {
            val r = a[name]; val h = w * r.regionHeight / r.regionWidth.coerceAtLeast(1)
            batch.setColor(1f, 1f, 1f, parentAlpha)
            batch.draw(r, x + cx - w / 2, y + cy - h / 2 + sin(t * 1.3f + phase) * 8f, w, h)
        }
        put("islands/big_forest", width * 0.13f, height * 0.62f, 220f, 1f)
        put("islands/big_crystal", width * 0.88f, height * 0.66f, 210f, 2f)
        put("islands/home", width / 2, height * 0.42f, 470f)
        put("buildings/hi_castle", width / 2, height * 0.58f, 250f)
        put("buildings/hi_house", width / 2 - 150f, height * 0.5f, 120f)
        put("buildings/hi_farm", width / 2 + 150f, height * 0.5f, 130f)
        put("ships/ship_explore", width * 0.18f, height * 0.18f, 150f, 3f)
        batch.setColor(1f, 1f, 1f, 1f)
    }
}

class MenuScreen(app: KayipAdalarGame) : BaseScreen(app) {
    override fun build() {
        root.top()
        val logo = Image(assets["ui/logo"]).also { it.setScaling(Scaling.fit) }
        root.add(logo).size(560f, 380f).padTop(30f).row()
        root.add(MenuIsland(app)).growX().height(470f).row()
        val first = !app.game.save.seenIntro
        val col = Table()
        col.add(button(if (first) "Oyuna Başla" else "Krallığa Dön", "green", icon = "icons/crown") { app.goTo(KingdomScreen(app)) }).width(520f).height(100f).pad(8f).row()
        col.add(button("Nasıl Oynanır?", icon = "icons/scroll") { howTo() }).width(520f).height(86f).pad(6f).row()
        col.add(button("Ayarlar", icon = "icons/gear") { app.goTo(SettingsScreen(app)) }).width(520f).height(86f).pad(6f).row()
        root.add(col).padTop(6f)
        app.game.loadResult.message?.let { toast(it, Palette.red) }
    }

    fun howTo() = dialog(
        "Nasıl Oynanır?",
        "1. Boş arsalara (+) dokunup bina kur. Binaları yükselt.\n" +
            "2. Binaların üstünde kaynak simgesi belirince dokun, topla.\n" +
            "3. Kışla'da asker eğit; Harita'dan adaları keşfet ve korsanlardan kurtar.\n" +
            "4. Kurtardığın adayı krallığına çek: yeni arsalar ve bonuslar kazanırsın.\n" +
            "5. Görev kartı her zaman bir sonraki adımı gösterir; \"Git\" seni oraya götürür.",
        "portraits/scholar",
    )

    override fun onBack() { app.game.persist(); Gdx.app.exit() }
}

class SettingsScreen(app: KayipAdalarGame) : BaseScreen(app) {
    override fun build() {
        val st = app.game.save.settings
        root.top()
        val head = Table()
        head.add(button("←", "smallWood") { app.back() }).size(80f, 70f).padRight(14f)
        head.add(label("Ayarlar", "title")).growX().left()
        root.add(head).growX().pad(20f).row()
        val p = parchment(24f)
        fun toggle(text: String, v: Boolean, set: (Boolean) -> Unit) {
            p.add(label(text)).left().expandX()
            p.add(button(if (v) "Açık" else "Kapalı", if (v) "smallGreen" else "smallRed") { set(!v); app.game.persist(); rebuild() }).size(150f, 66f).pad(6f).row()
        }
        toggle("Ses efektleri", st.sound) { st.sound = it }
        toggle("Müzik", st.music) { st.music = it; app.audio.updateMusic() }
        toggle("Titreşim", st.vibration) { st.vibration = it }
        p.add(label("Oyuncu adı: ${app.game.save.playerName}", "small")).left().colspan(2).padTop(14f).row()
        p.add(label("Kayıt sürümü 2 · otomatik kayıt ve yedek etkin", "tiny")).left().colspan(2).padTop(6f).row()
        p.add(button("Krallığı sıfırla", "smallRed") {
            dialog("Emin misin?", "Bütün ilerlemen silinir ve yeni bir krallık kurarsın.", null, null,
                "Hayır" to {}, "Evet, sıfırla" to { app.k.newGame(app.game.now()); app.game.save.seenIntro = false; app.game.persist(); toast("Yeni krallık kuruldu.") })
        }).colspan(2).growX().height(76f).padTop(20f).row()
        root.add(p).width(660f)
    }
}
