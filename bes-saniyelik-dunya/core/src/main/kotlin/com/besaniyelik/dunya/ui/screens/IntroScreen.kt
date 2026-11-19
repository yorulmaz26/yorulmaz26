package com.besaniyelik.dunya.ui.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.besaniyelik.dunya.BesSaniyelikDunya
import com.besaniyelik.dunya.audio.Sfx
import com.besaniyelik.dunya.progress.Catalog
import com.besaniyelik.dunya.render.CharState
import com.besaniyelik.dunya.render.CharacterRenderer
import com.besaniyelik.dunya.render.Particles
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Magic Lumos Software açılış animasyonu, oyunun diliyle:
 * devre çizgileri açılır → harfler zıplayarak düşer → Pati harflerin üstünde zıplar → "Lumos" ışık patlaması →
 * SOFTWARE ters yer çekimiyle baş aşağı gelip düzelir → ışık süzmesi → oyunun açılış ekranı. Dokunarak geçilebilir.
 */
class IntroScreen(game: BesSaniyelikDunya) : BaseScreen(game) {
    override val background: MenuBackground? = null
    override val fadeInColor = Color(0.04f, 0.11f, 0.19f, 1f)

    private class Part(val name: String, val tex: Texture, val sx: Int, val sy: Int, val w: Int, val h: Int)
    private val parts = HashMap<String, Part>()
    private var t = 0f
    private var done = false
    private val particles = Particles(400)
    private val character = CharacterRenderer(a)
    private val skin = Catalog.skin(game.profile.data.selectedSkin)
    private val letterDip = FloatArray(10)
    private val played = HashSet<String>()

    // logo yerleşimi (kaynak logo koordinatlarından sahne koordinatlarına)
    private val srcCx = 679f
    private val srcCy = 400f
    private val s = 660f / 846f
    private val cx get() = stage.viewport.worldWidth / 2f
    private val cy get() = stage.viewport.worldHeight * 0.52f

    private val navy = Color(0.04f, 0.11f, 0.19f, 1f)
    private val white by lazy { a.ui("white").let { com.badlogic.gdx.graphics.g2d.TextureRegion(it, 6, 6, 4, 4) } }
    private val soft by lazy { a.ui("soft") }
    private val glow by lazy { a.g("pt_glow") }
    private val sparkle by lazy { a.g("pt_sparkle") }
    private val star by lazy { a.g("pt_star") }
    private val dust by lazy { a.g("pt_dust") }

    // zaman çizelgesi (saniye)
    private val tLettersStart = 0.45f
    private val tLetterStep = 0.07f
    private val tPatiIn = 1.35f
    private val hopTargets = intArrayOf(0, 2, 4, 6, 8, 9)
    private val tHopStart = 1.65f
    private val tHop = 0.17f
    private val tBigJump get() = tHopStart + (hopTargets.size - 1) * tHop
    private val tBurst get() = tBigJump + 0.28f
    private val tSoftware get() = tBurst + 0.05f
    private val tFlip get() = tSoftware + 0.45f
    private val tEnd get() = tFlip + 1.4f // logonun tamamı bir süre ekranda kalır

    /** Animasyon hızı: 1 = özgün, daha küçük = daha yavaş (toplam ≈ 7 sn). */
    private val speed = 0.65f

    init {
        val layout = Gdx.files.internal("intro/layout.txt").readString("UTF-8").lines().filter { it.isNotBlank() && !it.startsWith("#") }
        for (line in layout) {
            val (n, x, y, w, h) = line.trim().split(" ")
            val tex = Texture(Gdx.files.internal("intro/$n.png")).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
            parts[n] = Part(n, tex, x.toInt(), y.toInt(), w.toInt(), h.toInt())
        }
        input.addProcessor(object : InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean { if (t > 0.3f) finish(); return true }
        })
    }

    override fun build() {}

    private fun finish() {
        if (done) return
        done = true
        game.go(SplashScreen(game))
    }

    // kaynak koordinatı → sahne
    private fun px(x: Float) = cx + (x - srcCx) * s
    private fun py(y: Float) = cy - (y - srcCy) * s

    private fun drawPart(b: Batch, p: Part, dx: Float = 0f, dy: Float = 0f, alpha: Float = 1f, scaleY: Float = 1f, scale: Float = 1f) {
        val w = p.w * s * scale; val h = p.h * s * scale
        val x = px(p.sx + p.w / 2f) - w / 2f + dx
        val yc = py(p.sy + p.h / 2f) + dy
        b.setColor(1f, 1f, 1f, alpha)
        b.draw(p.tex, x, yc - h * scaleY / 2f, w, h * scaleY)
    }

    private fun once(key: String, f: () -> Unit) { if (played.add(key)) f() }

    override fun renderWorld(dt: Float) {
        t += dt * speed
        if (t > tEnd + 0.15f) finish()
        particles.update(dt * speed)
        val b = game.batch
        stage.viewport.apply()
        b.projectionMatrix = stage.camera.combined
        b.begin()
        val vw = stage.viewport.worldWidth; val vh = stage.viewport.worldHeight
        // arka plan: lacivert + merkez ışıması
        b.color = navy; b.draw(white, -10f, -10f, vw + 20f, vh + 20f)
        b.setColor(0.13f, 0.27f, 0.45f, 0.55f + 0.1f * sin(t * 2f)); b.draw(soft, cx - 520f, cy - 520f, 1040f, 1040f)

        // 1) devre çizgileri merkezden dışa açılır
        val circ = parts["circuits"]!!
        val reveal = Interpolation.pow2Out.apply((t / 0.9f).coerceIn(0f, 1f))
        val cw = circ.w * s; val ch = circ.h * s
        b.flush()
        val clip = Rectangle(cx - cw / 2f * reveal, -10f, cw * reveal, vh + 20f)
        val sc = Rectangle()
        ScissorStack.calculateScissors(stage.camera, b.transformMatrix, clip, sc)
        if (ScissorStack.pushScissors(sc)) {
            // portre ekranı doldurmak için büyük ve soluk ikinci kopya
            for (k in -2..2) {
                val bw = cw * 1.5f; val bh = ch * 1.5f
                val y0 = cy - bh / 2f + k * bh * 0.78f
                b.setColor(1f, 1f, 1f, if (k == 0) 0.3f else 0.2f)
                if (k % 2 == 0) b.draw(circ.tex, cx - bw / 2f, y0, bw, bh) else b.draw(circ.tex, cx - bw / 2f, y0 + bh, bw, -bh)
            }
            b.setColor(1f, 1f, 1f, 0.95f); b.draw(circ.tex, cx - cw / 2f, cy - ch / 2f, cw, ch)
            b.flush(); ScissorStack.popScissors()
        }
        // veri ışıkları devreler boyunca kayar
        if (t < 3.2f && MathUtils.randomBoolean(0.35f)) {
            val dir = if (MathUtils.randomBoolean()) 1f else -1f
            particles.spawn(glow, cx, cy + MathUtils.random(-200f, 200f), dir * MathUtils.random(300f, 600f), 0f, 1.2f, 18f, 6f, Color(0.55f, 0.8f, 1f, 0.9f))
        }

        // 2) MAGIC LUMOS harfleri zıplayarak düşer
        for (i in 0 until 10) {
            val p = parts["letter_$i"]!!
            val lt = t - (tLettersStart + i * tLetterStep)
            if (lt < 0f) continue
            val k = (lt / 0.55f).coerceIn(0f, 1f)
            val dy = (1f - Interpolation.bounceOut.apply(k)) * 520f
            if (k >= 0.36f) once("land$i") {
                particles.burst(dust, px(p.sx + p.w / 2f), py((p.sy + p.h).toFloat()), 4, 120f, 0.4f, 34f, Color(0.7f, 0.85f, 1f, 0.5f))
                if (i % 3 == 0) game.audio.play(Sfx.LAND, 1.2f)
            }
            letterDip[i] = max(0f, letterDip[i] - dt * speed * 6f)
            val dip = -sin(letterDip[i] * Math.PI.toFloat()) * 12f
            val squash = if (k in 0.36f..0.5f) 0.88f else 1f
            drawPart(b, p, 0f, dy + dip, min(1f, lt * 6f), squash)
        }

        // köşeli parantezler ve kod işaretleri yanlardan kayar
        val bt = ((t - 1.2f) / 0.5f).coerceIn(0f, 1f)
        if (bt > 0f) {
            val e = Interpolation.swingOut.apply(bt)
            drawPart(b, parts["bracket_l"]!!, -(1f - e) * 200f, 0f, bt)
            drawPart(b, parts["bracket_r"]!!, (1f - e) * 200f, 0f, bt)
        }
        val tg = ((t - tBurst) / 0.4f).coerceIn(0f, 1f)
        if (tg > 0f) drawPart(b, parts["tags"]!!, 0f, (1f - tg) * 20f, tg)

        // 3) Pati koşar ve harflerin üstünde zıplar
        drawPati(b)

        // 4) Lumos ışık patlaması
        val bp = t - tBurst
        if (bp >= 0f) {
            once("burst") {
                game.audio.play(Sfx.STAR)
                game.vibrate(30)
                particles.burst(star, cx, burstY(), 28, 520f, 1.1f, 34f, Color(1f, 0.9f, 0.5f, 1f), colors = listOf(Color(1f, 0.9f, 0.5f, 1f), Color(0.6f, 0.85f, 1f, 1f), Color.WHITE))
                particles.burst(sparkle, cx, burstY(), 18, 320f, 0.9f, 26f, Color.WHITE)
            }
            if (bp < 0.9f) {
                val r = Interpolation.pow2Out.apply(bp / 0.9f) * 900f
                b.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
                b.setColor(1f, 0.95f, 0.8f, (1f - bp / 0.9f) * 0.8f)
                b.draw(soft, cx - r, burstY() - r, r * 2, r * 2)
                b.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            }
            // harfler ışığı yansıtır
            val gl = (1f - bp / 1.2f).coerceIn(0f, 1f) * 0.7f
            if (gl > 0f) {
                b.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
                for (i in 0 until 10) drawPart(b, parts["letter_$i"]!!, 0f, 0f, gl)
                b.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            }
        }

        // 5) SOFTWARE: ters yer çekimiyle baş aşağı aşağıdan gelir, sonra düzelir
        val st = t - tSoftware
        if (st > 0f) {
            val flip = ((t - tFlip) / 0.3f).coerceIn(0f, 1f)
            if (flip > 0f) once("flip") { game.audio.play(Sfx.RULE); particles.burst(sparkle, cx, py(470f), 12, 260f, 0.6f, 22f, Color(0.7f, 0.9f, 1f, 1f)) }
            val scaleY = -1f + 2f * Interpolation.swingOut.apply(flip).coerceIn(-0.2f, 1.2f)
            for (i in 0 until 10) {
                val lt = ((st - i * 0.03f) / 0.4f).coerceIn(0f, 1f)
                if (lt <= 0f) continue
                val dy = -(1f - Interpolation.pow3Out.apply(lt)) * 260f
                drawPart(b, parts["soft_$i"]!!, 0f, dy, lt, scaleY)
            }
        }

        // 6) ışık süzmesi ve "sunar"
        val sw = (t - (tFlip + 0.25f)) / 0.6f
        if (sw in 0f..1f) {
            b.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
            val x = px(260f) + (px(1110f) - px(260f)) * sw
            b.setColor(1f, 1f, 1f, 0.35f * sin(sw * Math.PI.toFloat()))
            b.draw(soft, x - 60f, py(300f) - 260f, 120f, 300f)
            b.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        }
        val su = ((t - tFlip) / 0.4f).coerceIn(0f, 1f)
        if (su > 0f) {
            a.f28.setColor(0.75f, 0.85f, 1f, su)
            a.f28.draw(b, "sunar", 0f, py(520f) - 10f, vw, com.badlogic.gdx.utils.Align.center, false)
            a.f28.setColor(Color.WHITE)
        }
        particles.draw(b)
        b.setColor(1f, 1f, 1f, 1f)
        b.end()
    }

    private fun burstY() = py(332f) + 230f

    /** Pati: soldan koşarak gelir, seçilen harflerin tepelerinde zıplar, son harften büyük zıplamayla ışığa dönüşür. */
    private fun drawPati(b: Batch) {
        if (t < tPatiIn || t > tBurst) return
        val size = 74f
        val topY = py(332f) // harflerin üst kenarı
        fun letterX(i: Int) = parts["letter_$i"]!!.let { px(it.sx + it.w / 2f) }
        val x: Float; val y: Float; val state: CharState
        if (t < tHopStart) {
            // koşarak gelir ve ilk harfe zıplar
            val k = (t - tPatiIn) / (tHopStart - tPatiIn)
            x = MathUtils.lerp(-60f, letterX(0), k)
            val base = py(420f) + size * 0.45f
            y = MathUtils.lerp(base, topY + size * 0.45f, k) + sin(k * Math.PI.toFloat()) * 120f
            state = if (k < 0.4f) CharState.RUN else CharState.JUMP
        } else if (t < tBigJump) {
            val hop = ((t - tHopStart) / tHop).toInt().coerceIn(0, hopTargets.size - 2)
            val k = ((t - tHopStart) / tHop - hop).coerceIn(0f, 1f)
            val from = hopTargets[hop]; val to = hopTargets[hop + 1]
            if (k < 0.05f) once("hop$hop") { letterDip[from] = 1f; game.audio.play(Sfx.JUMP, 1f + hop * 0.08f) }
            x = MathUtils.lerp(letterX(from), letterX(to), k)
            y = topY + size * 0.45f + sin(k * Math.PI.toFloat()) * 70f
            state = if (k < 0.5f) CharState.JUMP else CharState.FALL
        } else {
            // büyük zıplama: merkezdeki ışık noktasına
            once("big") { letterDip[9] = 1f; game.audio.play(Sfx.SPRING) }
            val k = ((t - tBigJump) / (tBurst - tBigJump)).coerceIn(0f, 1f)
            val e = Interpolation.pow2Out.apply(k)
            x = MathUtils.lerp(letterX(9), cx, e)
            y = MathUtils.lerp(topY + size * 0.45f, burstY(), e)
            state = CharState.HAPPY
        }
        val tail = abs(sin(t * 30f))
        if (MathUtils.randomBoolean(0.5f)) particles.spawn(sparkle, x - 20f, y - 20f, -60f, -20f, 0.4f, 16f * (0.5f + tail * 0.5f), 4f, Color(1f, 0.92f, 0.6f, 0.9f))
        character.draw(b, skin, x, y, size, state, t)
    }

    override fun onBack() { finish() }

    override fun dispose() {
        super.dispose()
        parts.values.forEach { it.tex.dispose() }
    }
}
