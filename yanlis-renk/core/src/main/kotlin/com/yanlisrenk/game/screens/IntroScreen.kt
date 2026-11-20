package com.yanlisrenk.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.yanlisrenk.game.YanlisRenkGame
import com.yanlisrenk.game.render.CharacterPainter
import com.yanlisrenk.game.render.Particles
import com.yanlisrenk.game.ui.Pal
import com.yanlisrenk.logic.shop.ShopCatalog
import kotlin.math.sin

/**
 * Magic Lumos Software açılış animasyonu (oyuna özel).
 *
 * 0.0–1.2 sn  Devre hatları oyunun 4 renginde logoya doğru çizilir, uçlarında ışık noktaları akar.
 * 0.6–2.0 sn  Renk değiştiren "lumos" ışık huzmesi logoyu soldan sağa aydınlatır.
 * 1.6–2.9 sn  Oyunun küp karakteri aşağıdan gelir, dokunuşlarla K→M→Y→S olur, logodan kapı gibi geçer.
 * 2.9 sn      Dört renkli ışık patlaması, logo parlar.
 * 4.2–4.8 sn  Oyunun aydınlık zeminine geçiş → SplashScreen.
 * Dokunarak atlanabilir.
 */
class IntroScreen(game: YanlisRenkGame) : BaseScreen(game) {
    override val music: String = ""
    private val logo = Texture(Gdx.files.internal("brand/magic_lumos.jpg")).also {
        it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }
    private val sr = ShapeRenderer()
    private val particles = Particles(400)
    private val layout = GlyphLayout()
    private var t = 0f
    private var done = false
    private var burst = false
    private var lastTapColor = -1
    private val bg = Pal.hex(0x04172D)
    private val light = Pal.hex(0xF8FBFF)

    companion object {
        /** Animasyon zaman ölçeği: 1.8 → tüm akış ~8,6 sn sürer. */
        const val SLOW = 1.8f
        const val TOTAL = 4.8f
        const val BURST = 2.9f
    }

    /** Devre hatları: ekran kenarından logoya kırık çizgiler (x, y noktaları). */
    private var traces: List<FloatArray> = emptyList()

    private fun buildTraces(cx: Float, topEnd: Float, bottomEnd: Float, screenTop: Float, screenBottom: Float): List<FloatArray> {
        val r = java.util.Random(42)
        val list = ArrayList<FloatArray>()
        for (i in 0 until 16) {
            val top = i % 2 == 0
            val x0 = cx - 320f + (i / 2) * 88f + r.nextInt(20)
            val yEdge = if (top) screenTop + 20f else screenBottom - 20f
            val yEnd = if (top) topEnd else bottomEnd
            val yMid = if (top) yEnd + 120f + r.nextInt(140) else yEnd - 120f - r.nextInt(140)
            val xEnd = cx + (x0 - cx) * 0.55f
            list.add(floatArrayOf(x0, yEdge, x0, yMid, xEnd, yEnd))
        }
        return list
    }

    override fun build() {}

    override fun extraInput(): InputProcessor = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (t > 0.6f) finish()
            return true
        }
    }

    override fun onBack() = finish()

    private fun finish() {
        if (done) return
        done = true
        game.go({ SplashScreen(game) }, remember = false)
    }

    override fun render(delta: Float) {
        val dt = minOf(delta, 0.05f)
        t += dt / SLOW
        particles.update(dt)
        stage.viewport.apply()
        val cam = stage.camera
        val w = stage.viewport.worldWidth
        val h = stage.viewport.worldHeight
        val x0 = cam.position.x - w / 2
        val y0 = cam.position.y - h / 2
        val cx = cam.position.x
        val cy = cam.position.y + 40f

        Gdx.gl.glClearColor(bg.r, bg.g, bg.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        // logo yerleşimi (genişliğe sığdır)
        val lw = w * 1.45f
        val lh = lw * logo.height / logo.width
        val lx = cx - lw / 2
        val ly = cy - lh / 2
        if (traces.isEmpty()) traces = buildTraces(cx, cy + lh * 0.22f, cy - lh * 0.22f, y0 + h, y0)

        sr.projectionMatrix = cam.combined
        sr.begin(ShapeRenderer.ShapeType.Filled)
        // 2) devre hatları ve akan ışıklar (oyunun 4 rengi)
        val draw = ((t - 0.05f) / 1.1f).coerceIn(0f, 1f)
        val fadeTraces = if (t > 3.6f) (1f - (t - 3.6f) / 0.6f).coerceIn(0f, 1f) else 1f
        traces.forEachIndexed { i, tr ->
            val c = Pal.game[i % 4]
            val segs = tr.size / 2 - 1
            val total = draw * segs
            for (s in 0 until segs) {
                val f = (total - s).coerceIn(0f, 1f)
                if (f <= 0f) break
                val ax = tr[s * 2]; val ay = tr[s * 2 + 1]
                val bx = tr[s * 2 + 2]; val by = tr[s * 2 + 3]
                sr.setColor(c.r, c.g, c.b, 0.35f * fadeTraces)
                sr.rectLine(ax, ay, ax + (bx - ax) * f, ay + (by - ay) * f, 3f)
            }
            // uçtaki ışık noktası
            if (draw < 1f || t < 3.6f) {
                val p = ((t * 0.55f + i * 0.13f) % 1f)
                val pos = point(tr, if (draw < 1f) draw else p)
                sr.setColor(c.r, c.g, c.b, 0.9f * fadeTraces)
                sr.circle(pos[0], pos[1], 6f, 12)
                sr.setColor(1f, 1f, 1f, 0.8f * fadeTraces)
                sr.circle(pos[0], pos[1], 2.5f, 8)
            }
            // devre ucu halkası
            if (draw >= 1f) {
                sr.setColor(c.r, c.g, c.b, 0.6f * fadeTraces)
                sr.circle(tr[tr.size - 2], tr[tr.size - 1], 7f, 14)
                sr.setColor(bg.r, bg.g, bg.b, 1f)
                sr.circle(tr[tr.size - 2], tr[tr.size - 1], 4f, 12)
            }
        }

        sr.end()

        val batch = stage.batch
        batch.projectionMatrix = cam.combined

        // 1) logo: lumos huzmesiyle soldan sağa açılır
        val reveal = Interpolation.pow2Out.apply(((t - 0.6f) / 1.4f).coerceIn(0f, 1f))
        val glowPulse = if (t > BURST) (1f - ((t - BURST) / 0.8f).coerceIn(0f, 1f)) else 0f
        batch.begin()
        if (reveal > 0f) {
            val rw = (logo.width * reveal).toInt().coerceAtLeast(1)
            batch.setColor(1f, 1f, 1f, 0.35f + 0.65f * reveal)
            batch.draw(logo, lx, ly, lw * rw / logo.width, lh, 0, 0, rw, logo.height, false, false)
        }
        if (glowPulse > 0f) {
            val g = game.assets.region("glow")
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
            batch.setColor(1f, 1f, 1f, 0.55f * glowPulse)
            batch.draw(g, cx - lw * 0.38f, cy - lh * 0.3f, lw * 0.76f, lh * 0.6f)
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        }
        batch.setColor(1f, 1f, 1f, 1f)
        batch.end()
        // SpriteBatch.end() harmanlamayı kapatır → şekiller için yeniden aç
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        sr.begin(ShapeRenderer.ShapeType.Filled)
        // logo görselinin üst/alt kenarını zemine yumuşakça karıştır
        val clear = Color(bg.r, bg.g, bg.b, 0f)
        sr.rect(x0, ly + lh - 80f, w, 82f, clear, clear, bg, bg)
        sr.rect(x0, ly - 2f, w, 82f, bg, bg, clear, clear)



        // 3) lumos huzmesi
        if (t in 0.6f..2.15f) {
            val bx = lx + lw * reveal
            val bc = Pal.game[((t * 5f).toInt()) % 4]
            val a = 1f - ((t - 2.0f) / 0.15f).coerceIn(0f, 1f)
            sr.setColor(bc.r, bc.g, bc.b, 0.25f * a); sr.rect(bx - 30f, ly + lh * 0.25f, 60f, lh * 0.5f)
            sr.setColor(1f, 1f, 1f, 0.75f * a); sr.rect(bx - 3f, ly + lh * 0.27f, 6f, lh * 0.46f)
            if (MathUtils.random() < 0.6f) particles.emit(bx, ly + MathUtils.random(lh * 0.3f, lh * 0.7f), 1, bc, 140f, 9f, 0.6f, 1, -40f)
        }

        // 4) küp karakter aşağıdan gelir, renk değiştirir, logodan geçer
        if (t in 1.6f..BURST + 0.05f) {
            val k = ((t - 1.6f) / (BURST - 1.6f)).coerceIn(0f, 1f)
            val py = MathUtils.lerp(y0 - 80f, cy - 20f, Interpolation.pow2Out.apply(k))
            val ci = minOf(3, (k * 4f).toInt())
            if (ci != lastTapColor) {
                lastTapColor = ci
                game.audio.play("tap", 0.9f + ci * 0.08f, 0.7f)
                particles.emit(cx, py, 10, Pal.game[ci], 260f, 5f, 0.45f, 0, 0f)
            }
            val squash = 1f - ((k * 4f) % 1f).coerceIn(0f, 1f)
            CharacterPainter.drawAny(sr, game.world.batch, ShopCatalog.characters[0], 0, Pal.game[ci], cx, py, 110f, t, squash * 0.8f, idleAnim = false)
        }
        if (t >= BURST && !burst) {
            burst = true
            game.audio.play("unlock")
            for (k in 0 until 4) particles.emit(cx, cy - 20f, 26, Pal.game[k], 900f, 9f, 1.1f, if (k % 2 == 0) 1 else 0, -300f)
        }
        particles.draw(sr)

        // 6) çıkış: oyunun aydınlık zeminine geçiş
        if (t > 4.2f) {
            val a = ((t - 4.2f) / 0.6f).coerceIn(0f, 1f)
            sr.setColor(light.r, light.g, light.b, a)
            sr.rect(x0, y0, w, h)
        }
        // giriş: siyahtan açılma
        if (t < 0.4f) { sr.setColor(0f, 0f, 0f, 1f - t / 0.4f); sr.rect(x0, y0, w, h) }
        sr.end()

        // "Dokunarak geç" ipucu (logo altı yazısı yok)
        if (t > BURST + 0.2f && t < 4.4f) {
            val a = ((t - BURST - 0.2f) / 0.5f).coerceIn(0f, 1f) * (1f - ((t - 4.0f) / 0.4f).coerceIn(0f, 1f))
            val s = ui.font(com.yanlisrenk.game.ui.Ui.Size.S)
            stage.batch.begin()
            s.setColor(0.6f, 0.7f, 0.85f, a * 0.8f)
            layout.setText(s, "Dokunarak geç")
            s.draw(stage.batch, layout, cx - layout.width / 2, y0 + 70f)
            s.setColor(Color.WHITE)
            stage.batch.end()
        }

        if (t >= TOTAL) finish()
    }

    private fun point(tr: FloatArray, f: Float): FloatArray {
        val segs = tr.size / 2 - 1
        val x = (f * segs).coerceIn(0f, segs - 0.0001f)
        val s = x.toInt()
        val k = x - s
        return floatArrayOf(tr[s * 2] + (tr[s * 2 + 2] - tr[s * 2]) * k, tr[s * 2 + 1] + (tr[s * 2 + 3] - tr[s * 2 + 1]) * k)
    }

    override fun dispose() {
        super.dispose()
        logo.dispose()
        sr.dispose()
    }

    
}
