package com.yanlisrenk.game.render

import com.yanlisrenk.logic.i18n.L
import com.yanlisrenk.logic.i18n.lupper

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Disposable
import com.yanlisrenk.game.Assets
import com.yanlisrenk.game.ui.Pal
import com.yanlisrenk.logic.model.GameColor
import com.yanlisrenk.logic.model.GateData
import com.yanlisrenk.logic.model.Motion
import com.yanlisrenk.logic.model.PadType
import com.yanlisrenk.logic.model.PickupType
import com.yanlisrenk.logic.model.RuleType
import com.yanlisrenk.logic.model.ZoneType
import com.yanlisrenk.logic.run.RunWorld
import com.yanlisrenk.logic.shop.ShopItem
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/** Nesne havuzlu parçacık sistemi. */
class Particles(capacity: Int = 600) {
    class P { var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var life = 0f; var max = 1f; var size = 0.1f; var kind = 0; var rot = 0f; val color = Color() ; var grav = 0f }
    private val pool = Array(capacity) { P() }
    private var count = 0
    val active: Int get() = count

    /** kind: 0 daire, 1 yıldız, 2 kalp, 3 kare, 4 elmas, 5 halka, 6 kar, 7 nota */
    fun emit(x: Float, y: Float, n: Int, color: Color, speed: Float, size: Float, life: Float, kind: Int = 0, grav: Float = -4f, spread: Float = 360f, dir: Float = 90f) {
        for (i in 0 until n) {
            if (count >= pool.size) return
            val p = pool[count++]
            val a = (dir + MathUtils.random(-spread / 2, spread / 2)) * MathUtils.degRad
            val sp = speed * MathUtils.random(0.4f, 1f)
            p.x = x; p.y = y; p.vx = MathUtils.cos(a) * sp; p.vy = MathUtils.sin(a) * sp
            p.life = life * MathUtils.random(0.7f, 1f); p.max = p.life; p.size = size * MathUtils.random(0.6f, 1.2f)
            p.kind = kind; p.color.set(color); p.rot = MathUtils.random(360f); p.grav = grav
        }
    }

    fun update(dt: Float) {
        var i = 0
        while (i < count) {
            val p = pool[i]
            p.life -= dt
            if (p.life <= 0f) { val last = pool[count - 1]; pool[count - 1] = p; pool[i] = last; count--; continue }
            p.vy += p.grav * dt; p.x += p.vx * dt; p.y += p.vy * dt; p.rot += dt * 180f
            i++
        }
    }

    fun draw(sr: ShapeRenderer) {
        for (i in 0 until count) {
            val p = pool[i]
            val k = p.life / p.max
            sr.setColor(p.color.r, p.color.g, p.color.b, p.color.a * minOf(1f, k * 1.6f))
            val s = if (p.kind == 5) p.size * (1f + (1f - k) * 3f) else p.size
            when (p.kind) {
                1 -> Shapes.star(sr, p.x, p.y, s, s * 0.42f, 5, p.rot)
                2 -> Shapes.heart(sr, p.x, p.y, s * 2f)
                3 -> sr.rect(p.x - s / 2, p.y - s / 2, s / 2, s / 2, s, s * 0.6f, 1f, 1f, p.rot)
                4 -> Shapes.polygon(sr, p.x, p.y, s, 4, p.rot)
                5 -> { for (j in 0 until 16) { val a = j * 22.5f * MathUtils.degRad; sr.circle(p.x + MathUtils.cos(a) * s, p.y + MathUtils.sin(a) * s, 0.05f, 6) } }
                6 -> { for (j in 0 until 3) { val a = (j * 60f + p.rot) * MathUtils.degRad; sr.rectLine(p.x - MathUtils.cos(a) * s, p.y - MathUtils.sin(a) * s, p.x + MathUtils.cos(a) * s, p.y + MathUtils.sin(a) * s, s * 0.25f) } }
                7 -> { sr.circle(p.x, p.y, s * 0.5f, 8); sr.rect(p.x + s * 0.35f, p.y, s * 0.15f, s * 1.4f) }
                else -> sr.circle(p.x, p.y, s, 10)
            }
        }
    }

    fun clear() { count = 0 }
}

/** Oyun dünyasını çizer. Dünya birimleri: genişlik 9, yol x=1.4..7.6. */
class WorldRenderer(private val assets: Assets) : Disposable {
    val sr = ShapeRenderer(5000)
    val batch = SpriteBatch()
    val particles = Particles()
    private val layout = GlyphLayout()
    private val tmp = Color()
    private val fogTmp = ArrayList<FloatArray>()
    private val dark = Pal.hex(0x253858)

    companion object {
        const val W = 9f
        const val CX = 4.5f
        const val ROAD_L = 1.35f
        const val ROAD_R = 7.65f
        const val ROAD_W = ROAD_R - ROAD_L
        /** Karakterin ekran altına uzaklığı: 9:16 ekranda bile bilgi çubuğu altında ≥ 11 birim ileri görülür. */
        const val PLAYER_SCREEN_Y = 2.9f
        const val PLAYER_SIZE = 1.15f
        val shapeIcons = arrayOf("shape_circle", "shape_square", "shape_triangle", "shape_star")
    }

    private class Glyph(var region: TextureRegion, var x: Float, var y: Float, var w: Float, var h: Float, val color: Color = Color())
    private val glyphs = ArrayList<Glyph>()
    private var glyphCount = 0
    private class Txt(var text: String, var x: Float, var y: Float, var scale: Float, val color: Color = Color(), var maxW: Float = 99f)
    private val texts = ArrayList<Txt>()
    private var textCount = 0

    private fun glyph(name: String, cx: Float, cy: Float, size: Float, color: Color = Pal.WHITE, alpha: Float = 1f) {
        if (glyphCount >= glyphs.size) glyphs.add(Glyph(assets.region(name), 0f, 0f, 0f, 0f))
        val g = glyphs[glyphCount++]
        g.region = assets.region(name); g.x = cx - size / 2; g.y = cy - size / 2; g.w = size; g.h = size
        g.color.set(color.r, color.g, color.b, color.a * alpha)
    }

    private fun glyphWH(name: String, cx: Float, cy: Float, w: Float, h: Float, alpha: Float = 1f, color: Color = Pal.WHITE) {
        glyph(name, cx, cy, 1f, color, alpha)
        val g = glyphs[glyphCount - 1]
        g.x = cx - w / 2; g.y = cy - h / 2; g.w = w; g.h = h
    }

    /** Görsel varsa onun adını, yoksa yedeğini döndürür. */
    private fun art(name: String, fallback: String) = if (assets.has(name)) name else fallback

    /** Dünya yazısı; [maxW] dünya biriminde en geniş alan (uzun çeviriler orantılı küçültülür). */
    private fun text(s: String, cx: Float, cy: Float, scale: Float, color: Color, alpha: Float = 1f, maxW: Float = ROAD_W - 0.4f) {
        if (textCount >= texts.size) texts.add(Txt("", 0f, 0f, 1f))
        val t = texts[textCount++]
        t.text = s; t.x = cx; t.y = cy; t.scale = scale; t.maxW = maxW; t.color.set(color.r, color.g, color.b, color.a * alpha)
    }

    class Look(
        val character: ShopItem,
        val costume: Int,
        val trail: Int,
        val colorBlind: Boolean,
        val reducedMotion: Boolean,
    )

    /** Karakter rengi geçişi ve iz geçmişi */
    private val displayColor = Color(Pal.game[0])
    private val trailColors = IntArray(24) { -1 }
    private var trailTimer = 0f
    var squash = 0f
    private var blinkT = 2f

    fun resetRun(startColor: Int) {
        displayColor.set(Pal.game[startColor])
        trailColors.fill(-1)
        particles.clear()
        squash = 0f
    }

    fun update(dt: Float, world: RunWorld) {
        particles.update(dt)
        squash = maxOf(0f, squash - dt * 6f)
        val target = Pal.game[world.color]
        displayColor.lerp(target, minOf(1f, dt * 18f))
        trailTimer += dt
        if (trailTimer > 0.04f) {
            trailTimer = 0f
            System.arraycopy(trailColors, 0, trailColors, 1, trailColors.size - 1)
            trailColors[0] = world.color
        }
        blinkT -= dt
        if (blinkT < -0.12f) blinkT = MathUtils.random(2f, 4.5f)
    }

    fun render(cam: OrthographicCamera, world: RunWorld, th: Theme, look: Look, time: Float, finishY: Float?) {
        glyphCount = 0; textCount = 0
        val left = cam.position.x - cam.viewportWidth / 2
        val bottom = cam.position.y - cam.viewportHeight / 2
        val top = bottom + cam.viewportHeight
        val vw = cam.viewportWidth
        val vh = cam.viewportHeight
        val py = world.y
        val tr = world.track

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        // kullanıcı arka planı (11–20. dünyalar): ekranı kaplayacak şekilde, hafif paralaksla
        val bgTex = assets.background("bg/world_${th.world.id}.jpg")
        if (bgTex != null) {
            batch.projectionMatrix = cam.combined
            batch.disableBlending()
            batch.begin()
            val ratio = bgTex.height.toFloat() / bgTex.width
            var bw = vw * 1.06f
            var bh = bw * ratio
            if (bh < vh * 1.06f) { bh = vh * 1.06f; bw = bh / ratio }
            val drift = if (look.reducedMotion) 0f else sin(cam.position.y * 0.02f) * vh * 0.015f
            batch.draw(bgTex, left + (vw - bw) / 2, bottom + (vh - bh) / 2 + drift, bw, bh)
            batch.end()
            batch.enableBlending()
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        }
        sr.projectionMatrix = cam.combined
        sr.begin(ShapeRenderer.ShapeType.Filled)
        if (bgTex == null) {
            Backgrounds.drawSky(sr, th, left, bottom, vw, vh)
            Backgrounds.drawLayers(sr, th, left, bottom, vw, vh, cam.position.y, time, look.reducedMotion)
        }

        // yol (görsel arka planda daha saydam: resmin kendi yolu görünsün)
        sr.color = tmp.set(th.path.r, th.path.g, th.path.b, if (bgTex != null) 0.42f else 0.82f)
        sr.rect(ROAD_L, bottom, ROAD_W, vh)
        sr.color = tmp.set(1f, 1f, 1f, 0.9f)
        sr.rect(ROAD_L - 0.08f, bottom, 0.12f, vh); sr.rect(ROAD_R - 0.04f, bottom, 0.12f, vh)
        // orta kesik çizgi
        sr.color = tmp.set(th.a1.r, th.a1.g, th.a1.b, 0.25f)
        var dy = floor(bottom / 1.2f) * 1.2f
        while (dy < top) { Shapes.roundRect(sr, CX - 0.06f, dy, 0.12f, 0.6f, 0.06f); dy += 1.2f }

        // bölgeler
        for ((zi, z) in tr.zones.withIndex()) {
            if (z.y2 < bottom || z.y1 > top) continue
            val y1 = maxOf(z.y1, bottom); val y2 = minOf(z.y2, top)
            when (z.type) {
                ZoneType.SPEED -> {
                    val fast = z.value > 1f
                    sr.color = tmp.set(if (fast) 1f else 0.6f, if (fast) 0.84f else 0.85f, if (fast) 0.3f else 1f, 0.22f)
                    sr.rect(ROAD_L, y1, ROAD_W, y2 - y1)
                    sr.color = tmp.set(1f, 1f, 1f, 0.7f)
                    var cy = floor(z.y1 / 1.4f) * 1.4f + ((time * (if (fast) 3f else 1f)) % 1.4f)
                    while (cy < z.y2) {
                        if (cy in y1..y2) for (sx in floatArrayOf(2.4f, 6.6f)) {
                            if (fast) { sr.triangle(sx - 0.35f, cy, sx + 0.35f, cy, sx, cy + 0.4f) } else { sr.triangle(sx - 0.35f, cy + 0.4f, sx + 0.35f, cy + 0.4f, sx, cy) }
                        }
                        cy += 1.4f
                    }
                    if (z.y1 in bottom..top) text(if (fast) L.f("world.hiz", "%.1f".format(z.value)) else L.t("world.yavas"), CX, z.y1 + 0.55f, 0.012f, dark)
                }
                ZoneType.MIRROR -> {
                    sr.color = tmp.set(0.75f, 0.65f, 1f, 0.25f); sr.rect(ROAD_L, y1, ROAD_W, y2 - y1)
                    if (z.y1 in bottom..top) { sr.color = tmp.set(0.66f, 0.55f, 1f, 0.95f); Shapes.roundRect(sr, CX - 1.6f, z.y1 + 0.05f, 3.2f, 0.75f, 0.35f); text(L.t("world.ayna"), CX, z.y1 + 0.44f, 0.012f, Pal.WHITE, 1f, 2.9f) }
                }
                ZoneType.LOCK -> {
                    sr.color = tmp.set(0.55f, 0.6f, 0.7f, 0.18f); sr.rect(ROAD_L, y1, ROAD_W, y2 - y1)
                    if (z.y1 in bottom..top) {
                        sr.color = tmp.set(1f, 1f, 1f, 0.95f); Shapes.roundRect(sr, CX - 1.9f, z.y1 + 0.05f, 3.8f, 0.85f, 0.4f)
                        glyph("lock", CX - 1.35f, z.y1 + 0.48f, 0.62f)
                        z.colors.forEachIndexed { i, c ->
                            sr.color = Pal.gameColor(c); sr.circle(CX - 0.5f + i * 0.8f, z.y1 + 0.48f, 0.27f, 16)
                            if (look.colorBlind) glyph(shapeIcons[c], CX - 0.5f + i * 0.8f, z.y1 + 0.48f, 0.3f)
                            sr.color = dark; sr.rectLine(CX - 0.75f + i * 0.8f, z.y1 + 0.23f, CX - 0.25f + i * 0.8f, z.y1 + 0.73f, 0.08f)
                        }
                    }
                }
                ZoneType.FOG -> {}
            }
            if (zi < 0) break
        }
        // bitiş çizgisi
        if (finishY != null && finishY in bottom - 1f..top + 1f) {
            for (i in 0 until 12) for (j in 0 until 2) {
                sr.color = if ((i + j) % 2 == 0) dark else Pal.WHITE
                sr.rect(ROAD_L + i * ROAD_W / 12, finishY + j * 0.35f, ROAD_W / 12, 0.35f)
            }
        }

        // pedler
        for ((i, p) in tr.pads.withIndex()) {
            if (p.y < bottom - 1 || p.y > top + 1) continue
            val used = p.y <= py
            val a = if (used) 0.4f else 1f
            if (p.type == PadType.SET) {
                sr.color = tmp.set(Pal.gameColor(p.value)).also { it.a = a }
                Shapes.roundRect(sr, CX - 1.1f, p.y - 0.32f, 2.2f, 0.64f, 0.3f)
                if (look.colorBlind) glyph(shapeIcons[p.value], CX, p.y, 0.42f, alpha = a)
                text(L.t("world.renk"), CX - 1.75f, p.y + 0.12f, 0.009f, dark, a, 1.5f)
            } else {
                if (assets.has("obj_pad")) glyphWH("obj_pad", CX, p.y, 3.0f, 0.75f, a)
                else for (k in 0 until 4) { sr.color = tmp.set(Pal.game[k]).also { it.a = a }; sr.rect(CX - 1.1f + k * 0.55f, p.y - 0.3f, 0.55f, 0.6f) }
                text("+${p.value}", CX, p.y + 0.15f, 0.013f, Pal.WHITE, a)
            }
            if (i < 0) break
        }

        // altınlar ve toplanabilirler
        for ((i, p) in tr.pickups.withIndex()) {
            if (p.y < bottom - 1 || p.y > top + 1) continue
            if (i in world.pickupTaken) continue
            if (p.y <= py) continue
            val bob = sin(time * 4f + i) * 0.08f
            when (p.type) {
                PickupType.COIN -> {
                    if (p.color >= 0) { sr.color = Pal.gameColor(p.color); sr.circle(CX, p.y + bob, 0.42f, 20) }
                    glyph(art("obj_coin", "coin"), CX, p.y + bob, if (p.color >= 0) 0.66f else 0.78f)
                    if (p.color >= 0 && look.colorBlind) glyph(shapeIcons[p.color], CX + 0.42f, p.y + bob + 0.32f, 0.3f)
                }
                PickupType.DIAMOND -> { sr.color = tmp.set(1f, 1f, 1f, 0.5f); sr.circle(CX, p.y + bob, 0.55f + sin(time * 5f) * 0.05f, 20); glyph(art("obj_diamond", "diamond"), CX, p.y + bob, 0.9f) }
                PickupType.SHIELD -> { sr.color = tmp.set(0.5f, 0.8f, 1f, 0.45f); sr.circle(CX, p.y + bob, 0.55f, 20); glyph(art("obj_shield", "shield"), CX, p.y + bob, 0.85f) }
            }
        }

        // tabelalar (hafıza zinciri)
        for (s in tr.signs) {
            if (s.y < bottom - 2 || s.y > top + 2) continue
            if (s.y - py > world.visibleDistanceAt(s.y)) continue
            val n = s.gates.size
            val bw = 1.0f * n + 0.8f
            sr.color = dark; Shapes.roundRect(sr, CX - bw / 2 - 0.08f, s.y - 0.08f, bw + 0.16f, 1.26f, 0.4f)
            sr.color = Pal.WHITE; Shapes.roundRect(sr, CX - bw / 2, s.y, bw, 1.1f, 0.35f)
            s.gates.forEachIndexed { k, gi ->
                val g = tr.gates[gi]
                val c = g.colors.firstOrNull() ?: 0
                val x = CX - bw / 2 + 0.9f + k * 1.0f
                sr.color = Pal.gameColor(c); sr.circle(x, s.y + 0.55f, 0.36f, 18)
                if (look.colorBlind) glyph(shapeIcons[c], x, s.y + 0.55f, 0.4f)
                text("${k + 1}", x, s.y + 1.45f, 0.009f, dark)
            }
        }

        // kapılar
        var lastGateColor = -1
        for ((i, g) in tr.gates.withIndex()) {
            if (g.y < bottom - 1.5f || g.y > top + 1.5f) continue
            drawGate(g, i, world, look, time, py, th, top)
        }
        if (lastGateColor > 99) return

        // kapı görselleri karakterin altında kalsın: şimdiye kadarki simgeleri çiz
        flushGlyphs(cam)
        // iz
        drawTrail(look, py)
        if (bgTex == null) Backgrounds.drawNear(sr, th, left, bottom, vw, vh, cam.position.y, time, look.reducedMotion)

        // karakter
        val blink = if (blinkT < 0f) 1f else 0f
        CharacterPainter.drawAny(sr, batch, look.character, look.costume, displayColor, CX, py, PLAYER_SIZE, time, squash, blink, !look.reducedMotion)
        if (look.colorBlind) {
            sr.color = Pal.WHITE; sr.circle(CX + 0.62f, py + 0.55f, 0.24f, 16)
            sr.color = Pal.gameColor(world.color); sr.circle(CX + 0.62f, py + 0.55f, 0.19f, 16)
            glyph(shapeIcons[world.color], CX + 0.62f, py + 0.55f, 0.28f)
        }
        if (world.shields > 0) { sr.color = tmp.set(0.55f, 0.85f, 1f, 0.28f + 0.08f * sin(time * 6f)); sr.circle(CX, py, 0.95f, 28) }

        // sis
        // sis perdeleri: yalnızca doğrulayıcının sisli saydığı aralığı kapatır; perdeden önce görüş tamamen net
        world.fogBanks(fogTmp)
        for (b in fogTmp) {
            val y1 = b[0]; val y2 = minOf(b[1], top + 1f)
            if (y2 <= y1) continue
            val fade = minOf(1.0f, (y2 - y1) / 2f)
            val solid = Color(1f, 1f, 1f, 0.97f); val clear = Color(1f, 1f, 1f, 0f)
            sr.rect(left, y1, vw, fade, clear, clear, solid, solid)
            sr.color = solid; sr.rect(left, y1 + fade, vw, maxOf(0f, y2 - y1 - fade * 2))
            if (b[1] <= top + 1f) sr.rect(left, y2 - fade, vw, fade, solid, solid, clear, clear)
        }
        particles.draw(sr)

        flushGlyphs(cam, restartShapes = false)
    }

    /** Biriken simge ve yazıları çizer; [restartShapes] → ShapeRenderer'ı yeniden başlatır. */
    private fun flushGlyphs(cam: OrthographicCamera, restartShapes: Boolean = true) {
        if (sr.isDrawing) sr.end()
        batch.projectionMatrix = cam.combined
        batch.begin()
        for (k in 0 until glyphCount) {
            val g = glyphs[k]
            batch.color = g.color
            batch.draw(g.region, g.x, g.y, g.w, g.h)
        }
        batch.color = Color.WHITE
        val f = assets.fontWorld
        for (k in 0 until textCount) {
            val t = texts[k]
            val old = f.data.scaleX
            f.data.setScale(t.scale)
            f.color = t.color
            layout.setText(f, t.text)
            if (layout.width > t.maxW) { f.data.setScale(t.scale * t.maxW / layout.width); layout.setText(f, t.text) }
            f.draw(batch, layout, t.x - layout.width / 2, t.y + layout.height / 2)
            f.data.setScale(old)
        }
        f.color = Color.WHITE
        batch.end()
        glyphCount = 0; textCount = 0
        if (restartShapes) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            sr.begin(ShapeRenderer.ShapeType.Filled)
        }
    }

    private fun visibility(g: GateData, i: Int, world: RunWorld, py: Float, time: Float): Float {
        val dist = g.y - py
        var a = 1f
        if (g.revealDist > 0f && dist > 0f) a = ((g.revealDist - dist) / 1.2f + 1f).coerceIn(0f, 1f)
        if (g.flicker && dist > 0f) {
            val ph = (time / com.yanlisrenk.logic.level.LevelValidator.FLICKER_PERIOD + i * 0.37f) % 1f
            a *= if (ph < 0.6f) 1f else 0.08f
        }
        // ekran/sis görüşü: doğrulayıcıyla aynı kural (gizli kapının kendi yumuşak açılışı yukarıda)
        if (dist > world.visibleDistanceAt(g.y)) a = 0f
        return a
    }

    private fun drawGate(g: GateData, i: Int, world: RunWorld, look: Look, time: Float, py: Float, th: Theme, top: Float) {
        val dist = g.y - py
        val passed = dist < 0f
        var alpha = visibility(g, i, world, py, time)
        if (passed) alpha = maxOf(0f, 0.5f + dist * 0.25f)
        if (alpha <= 0.01f) return
        val h = 0.62f
        val y0 = g.y - h / 2
        val hideColors = (g.chain || (g.memoryDist > 0f && dist < g.memoryDist)) && !passed
        val style = th.area.gateStyle
        val t = world.time

        if (assets.has("gate_normal_body") && drawSpriteGate(g, look, alpha, hideColors, t)) return

        // gölge + çerçeve
        sr.color = tmp.set(0.15f, 0.22f, 0.35f, 0.16f * alpha); Shapes.roundRect(sr, ROAD_L - 0.05f, y0 - 0.14f, ROAD_W + 0.1f, h, h / 2)
        sr.color = tmp.set(dark).also { it.a = alpha }
        if (g.rule == RuleType.FREE) {
            // sahte kapı: kesik çizgili
            var x = ROAD_L
            while (x < ROAD_R) { Shapes.roundRect(sr, x, y0 + h * 0.3f, 0.45f, h * 0.4f, 0.12f); x += 0.7f }
            text(L.t("world.sahte"), CX, g.y + 0.02f, 0.012f, dark, alpha)
            posts(style, g.y, Pal.GREY, alpha)
            return
        }
        if (!(hideColors && assets.has("obj_gate_hidden"))) Shapes.roundRect(sr, ROAD_L - 0.07f, y0 - 0.07f, ROAD_W + 0.14f, h + 0.14f, h / 2 + 0.07f)

        val inner = ROAD_W
        if (hideColors) {
            if (assets.has("obj_gate_hidden")) {
                // gizli/hafıza kapısı görseli (ortasında soru işareti var) → kural simgesi sola
                glyphWH("obj_gate_hidden", CX, g.y, ROAD_W + 0.5f, (ROAD_W + 0.5f) / 4f, alpha)
                ruleGlyph(g, alpha, ROAD_L + 0.15f)
                return
            }
            sr.color = tmp.set(0.78f, 0.82f, 0.88f, alpha); Shapes.roundRect(sr, ROAD_L, y0, inner, h, h / 2)
            glyph("sym_question", CX - 1.7f, g.y, 0.6f, alpha = alpha); glyph("sym_question", CX + 1.7f, g.y, 0.6f, alpha = alpha)
            posts(style, g.y, Pal.GREY, alpha)
            ruleGlyph(g, alpha)
            return
        }
        when {
            g.motion == Motion.ROTATE -> {
                val n = g.colors.size
                val segW = 2.2f
                val f = t / g.segDur + g.phase
                val i0 = floor(f).toInt()
                val frac = f - i0
                for (k in -3..3) {
                    val c = g.colors[Math.floorMod(i0 + k, n)]
                    val xc = CX + (k + 0.5f - frac) * segW
                    val xl = maxOf(ROAD_L + 0.1f, xc - segW / 2); val xr = minOf(ROAD_R - 0.1f, xc + segW / 2)
                    if (xr <= xl) continue
                    sr.color = tmp.set(Pal.gameColor(c)).also { it.a = alpha }
                    sr.rect(xl, y0, xr - xl, h)
                    if (look.colorBlind && xr - xl > 0.6f) glyph(shapeIcons[c], (xl + xr) / 2, g.y, 0.42f, alpha = alpha)
                }
                sr.color = tmp.set(1f, 1f, 1f, 0.9f * alpha); sr.triangle(CX - 0.22f, y0 + h + 0.32f, CX + 0.22f, y0 + h + 0.32f, CX, y0 + h + 0.05f)
            }
            g.motion == Motion.SLIDE -> {
                val f = t / g.segDur + g.phase
                val idx = Math.floorMod(floor(f).toInt(), g.colors.size)
                val frac = f - floor(f)
                val off = (0.08f + 2.6f * sin(frac * MathUtils.PI))
                val b = if (idx == 0) CX + off else CX - off
                sr.color = tmp.set(Pal.gameColor(g.colors[0])).also { it.a = alpha }; sr.rect(ROAD_L + 0.1f, y0, b - ROAD_L - 0.1f, h)
                sr.color = tmp.set(Pal.gameColor(g.colors[1 % g.colors.size])).also { it.a = alpha }; sr.rect(b, y0, ROAD_R - 0.1f - b, h)
                if (look.colorBlind) { glyph(shapeIcons[g.colors[0]], (ROAD_L + b) / 2, g.y, 0.42f, alpha = alpha); glyph(shapeIcons[g.colors[1 % g.colors.size]], (b + ROAD_R) / 2, g.y, 0.42f, alpha = alpha) }
                sr.color = tmp.set(1f, 1f, 1f, 0.95f * alpha); sr.rect(b - 0.05f, y0 - 0.1f, 0.1f, h + 0.2f)
                sr.triangle(CX - 0.22f, y0 + h + 0.32f, CX + 0.22f, y0 + h + 0.32f, CX, y0 + h + 0.05f)
            }
            g.motion == Motion.TIMED -> {
                val f = t / g.segDur + g.phase
                val idx = Math.floorMod(floor(f).toInt(), g.colors.size)
                val c = g.colors[idx]
                val rem = 1f - (f - floor(f))
                sr.color = tmp.set(Pal.gameColor(c)).also { it.a = alpha }; Shapes.roundRect(sr, ROAD_L, y0, inner, h, h / 2)
                // sayaç halkası
                val rx = ROAD_R - 0.75f
                sr.color = tmp.set(1f, 1f, 1f, alpha); sr.circle(rx, g.y, 0.34f, 20)
                sr.color = tmp.set(Pal.gameColor(g.colors[(idx + 1) % g.colors.size])).also { it.a = alpha }; sr.arc(rx, g.y, 0.28f, 90f, 360f * rem, 20)
                if (look.colorBlind) glyph(shapeIcons[c], CX - 1.6f, g.y, 0.44f, alpha = alpha)
                glyph("clock", ROAD_L + 0.6f, g.y, 0.5f, alpha = alpha)
            }
            g.rule == RuleType.ANY || g.rule == RuleType.NONE -> {
                val n = g.colors.size
                val sw = inner / n
                g.colors.forEachIndexed { k, c ->
                    sr.color = tmp.set(Pal.gameColor(c)).also { it.a = alpha }
                    if (k == 0) Shapes.roundRect(sr, ROAD_L, y0, sw + h, h, h / 2)
                    else if (k == n - 1) Shapes.roundRect(sr, ROAD_L + k * sw - h, y0, sw + h, h, h / 2)
                    else sr.rect(ROAD_L + k * sw, y0, sw, h)
                }
                g.colors.forEachIndexed { k, c ->
                    val cx = ROAD_L + (k + 0.5f) * sw
                    if (look.colorBlind) glyph(shapeIcons[c], cx + (if (g.split) 0f else 0.55f), g.y, 0.4f, alpha = alpha)
                    if (g.rule == RuleType.NONE) { sr.color = tmp.set(1f, 1f, 1f, 0.85f * alpha); sr.rectLine(cx - 0.45f, y0 + 0.06f, cx + 0.45f, y0 + h - 0.06f, 0.1f) }
                }
                if (g.split) { sr.color = tmp.set(1f, 1f, 1f, alpha); sr.rect(CX - 0.04f, y0, 0.08f, h) }
            }
            else -> {
                val c = g.colors.firstOrNull() ?: 0
                val fill = if (g.rule == RuleType.REPEAT) Pal.hex(0xEDEFF5) else Pal.gameColor(c)
                sr.color = tmp.set(fill).also { it.a = alpha }; Shapes.roundRect(sr, ROAD_L, y0, inner, h, h / 2)
                if (g.rule == RuleType.DIFF) {
                    // ters kapı: çapraz beyaz şeritler
                    sr.color = tmp.set(1f, 1f, 1f, 0.35f * alpha)
                    var x = ROAD_L + 0.4f
                    while (x < ROAD_R - 0.5f) { sr.triangle(x, y0 + 0.05f, x + 0.25f, y0 + 0.05f, x + 0.5f, y0 + h - 0.05f); x += 0.7f }
                }
                if (g.word >= 0) {
                    text(GameColor.of(g.word).upper, CX + 0.2f, g.y + 0.02f, 0.017f, Pal.WHITE, alpha, 3.5f)
                    // neye bakılacağını gösteren göz rozeti
                    glyph("circle", ROAD_R - 0.62f, g.y, 0.84f, Pal.WHITE, alpha)
                    glyph(if (g.readBy == 1) "sym_word" else "sym_paint", ROAD_R - 0.62f, g.y, 0.6f, dark, alpha)
                    if (look.colorBlind && g.readBy == 0) glyph(shapeIcons[c], ROAD_R - 1.45f, g.y, 0.4f, alpha = alpha)
                } else if (look.colorBlind && g.rule != RuleType.REPEAT) {
                    glyph(shapeIcons[c], CX + 1.2f, g.y, 0.44f, alpha = alpha); glyph(shapeIcons[c], CX - 1.2f, g.y, 0.44f, alpha = alpha)
                }
            }
        }
        if (assets.has("gate_posts_deco")) gateSprite("gate_posts_deco", g.y, Pal.WHITE, alpha)
        else posts(style, g.y, if (g.colors.isNotEmpty() && g.motion == Motion.STATIC) Pal.gameColor(g.colors[0]) else th.a1, alpha)
        ruleGlyph(g, alpha, if (g.word >= 0) ROAD_L + 0.62f else CX)
    }

    // ---- kullanıcı kapı görselleri: boyanan gövde + boyanmayan süs (direkler, amblem, saat) ----
    private val gateCanvasW = (ROAD_W + 0.2f) / 0.702f     // direklerin iç kenarları yolun kenarına denk gelsin
    private val gateCanvasH = gateCanvasW / 4f
    private val gateCenterFromBottom = 0.453f                // kapsül/amblem merkezi tuvalin alttan %45'inde

    private fun gateSprite(name: String, y: Float, color: Color, alpha: Float) {
        glyphWH(name, CX, y - gateCenterFromBottom * gateCanvasH + gateCanvasH / 2, gateCanvasW, gateCanvasH, alpha, color)
    }

    /** Tek renkli kapıları görselle çizer. Çizildiyse true; çok renkli/hareketli kapılar vektörle sürer. */
    private fun drawSpriteGate(g: GateData, look: Look, alpha: Float, hideColors: Boolean, t: Float): Boolean {
        if (g.rule == RuleType.FREE) {
            gateSprite("gate_sahte_body", g.y, Pal.hex(0xE3E8F2), alpha * 0.85f)
            gateSprite("gate_sahte_deco", g.y, Pal.WHITE, alpha)
            text(L.t("world.sahte"), CX, g.y + 0.02f, 0.012f, dark, alpha)
            return true
        }
        if (hideColors) return false
        if (g.motion == Motion.TIMED) {
            val f = t / g.segDur + g.phase
            val idx = Math.floorMod(floor(f).toInt(), g.colors.size)
            val c = g.colors[idx]
            val rem = 1f - (f - floor(f))
            sr.color = tmp.set(0.15f, 0.22f, 0.35f, 0.14f * alpha); Shapes.roundRect(sr, ROAD_L - 0.05f, g.y - 0.75f, ROAD_W + 0.1f, 0.4f, 0.2f)
            gateSprite("gate_zaman_body", g.y, Pal.gameColor(c), alpha)
            gateSprite("gate_zaman_deco", g.y, Pal.WHITE, alpha)
            // sıradaki renk: kalan süre azaldıkça büyüyen baloncuk
            val nx = CX - 1.6f
            val nc = Pal.gameColor(g.colors[(idx + 1) % g.colors.size])
            glyph("circle", nx, g.y, 0.86f, Pal.WHITE, alpha)
            glyph("circle", nx, g.y, 0.2f + 0.5f * (1f - rem), nc, alpha)
            if (look.colorBlind) glyph(shapeIcons[c], CX - 0.3f, g.y, 0.44f, alpha = alpha)
            return true
        }
        if (g.motion != Motion.STATIC) return false
        if (g.rule != RuleType.SAME && g.rule != RuleType.DIFF && g.rule != RuleType.NEXT && g.rule != RuleType.PREV && g.rule != RuleType.REPEAT) return false
        val c = g.colors.firstOrNull() ?: 0
        val fill = if (g.rule == RuleType.REPEAT) Pal.hex(0xEDEFF5) else Pal.gameColor(c)
        val diff = g.rule == RuleType.DIFF
        sr.color = tmp.set(0.15f, 0.22f, 0.35f, 0.14f * alpha); Shapes.roundRect(sr, ROAD_L - 0.05f, g.y - 0.75f, ROAD_W + 0.1f, 0.4f, 0.2f)
        val emblem = g.word < 0 && (g.rule == RuleType.SAME || diff)
        val bodyName = when { diff -> "gate_ters_body"; emblem -> "gate_normal_body"; assets.has("gate_normal_body_full") -> "gate_normal_body_full"; else -> "gate_normal_body" }
        gateSprite(bodyName, g.y, fill, alpha)
        when {
            emblem -> gateSprite(if (diff) "gate_ters_deco" else "gate_normal_deco", g.y, Pal.WHITE, alpha)
            else -> gateSprite("gate_posts_deco", g.y, Pal.WHITE, alpha)
        }
        if (g.word >= 0) {
            text(GameColor.of(g.word).upper, CX + 0.2f, g.y + 0.02f, 0.017f, Pal.WHITE, alpha, 3.5f)
            glyph("circle", ROAD_R - 0.62f, g.y, 0.84f, Pal.WHITE, alpha)
            glyph(if (g.readBy == 1) "sym_word" else "sym_paint", ROAD_R - 0.62f, g.y, 0.6f, dark, alpha)
            if (look.colorBlind && g.readBy == 0) glyph(shapeIcons[c], ROAD_R - 1.45f, g.y, 0.4f, alpha = alpha)
            ruleGlyph(g, alpha, ROAD_L + 0.62f)
        } else {
            if (look.colorBlind && g.rule != RuleType.REPEAT) {
                glyph(shapeIcons[c], CX + 2.0f, g.y, 0.44f, alpha = alpha); glyph(shapeIcons[c], CX - 2.0f, g.y, 0.44f, alpha = alpha)
            }
            if (!emblem) ruleGlyph(g, alpha, CX)
        }
        return true
    }

    /** Kapının kural simgesi (= ≠ → ← ↺) — renkten bağımsız okunabilir. */
    private fun ruleGlyph(g: GateData, alpha: Float, x: Float = CX) {
        val name = when (g.rule) {
            RuleType.SAME, RuleType.ANY -> "sym_equal"
            RuleType.DIFF, RuleType.NONE -> "sym_notequal"
            RuleType.NEXT -> "sym_next"
            RuleType.PREV -> "sym_prev"
            RuleType.REPEAT -> "sym_repeat"
            RuleType.FREE -> return
        }
        glyph("circle", x, g.y, 0.94f, dark, alpha)
        glyph("circle", x, g.y, 0.8f, Pal.WHITE, alpha)
        glyph(name, x, g.y, 0.62f, dark, alpha)
    }

    private fun posts(style: Int, y: Float, col: Color, alpha: Float) {
        for (x in floatArrayOf(ROAD_L - 0.15f, ROAD_R + 0.15f)) {
            sr.color = tmp.set(dark).also { it.a = alpha }
            when (style % 5) {
                0 -> sr.circle(x, y, 0.4f, 18)
                1 -> Shapes.roundRect(sr, x - 0.36f, y - 0.36f, 0.72f, 0.72f, 0.14f)
                2 -> Shapes.polygon(sr, x, y, 0.45f, 4, 0f)
                3 -> Shapes.star(sr, x, y, 0.5f, 0.26f)
                else -> Shapes.polygon(sr, x, y, 0.42f, 6, 0f)
            }
            sr.color = tmp.set(if (style >= 5) Pal.WHITE else col).also { it.a = alpha }
            when (style % 5) {
                0 -> sr.circle(x, y, 0.31f, 18)
                1 -> Shapes.roundRect(sr, x - 0.28f, y - 0.28f, 0.56f, 0.56f, 0.1f)
                2 -> Shapes.polygon(sr, x, y, 0.35f, 4, 0f)
                3 -> Shapes.star(sr, x, y, 0.4f, 0.2f)
                else -> Shapes.polygon(sr, x, y, 0.33f, 6, 0f)
            }
        }
    }

    private fun drawTrail(look: Look, py: Float) {
        val style = look.trail
        if (style == 0) return
        for (k in 1 until trailColors.size) {
            val c = trailColors[k]
            if (c < 0) continue
            val f = 1f - k / trailColors.size.toFloat()
            val y = py - 0.3f - k * 0.16f
            val col = tmp.set(Pal.gameColor(if (style == 5) (k / 3) % 4 else c)).also { it.a = f * 0.85f }
            sr.color = col
            val s = 0.32f * f + 0.05f
            when (style) {
                1 -> if (k % 2 == 0) sr.circle(CX, y, s * 0.7f, 10)
                2 -> sr.rect(CX - s, y, s * 2, 0.17f)
                3 -> if (k % 3 == 0) Shapes.star(sr, CX + sin(k * 1.3f) * 0.2f, y, s, s * 0.45f)
                4 -> if (k % 3 == 0) Shapes.heart(sr, CX + sin(k * 1.1f) * 0.2f, y, s * 1.8f)
                5 -> sr.rect(CX - 0.4f * f - 0.1f, y, 0.8f * f + 0.2f, 0.17f)
                6 -> if (k % 2 == 0) { sr.color = tmp.set(1f, 1f, 1f, f * 0.7f); sr.circle(CX + sin(k * 2f) * 0.25f, y, s * 0.6f, 10) }
                7 -> sr.ellipse(CX - s * 1.6f, y, s * 3.2f, 0.22f, 12)
                8 -> if (k % 2 == 0) sr.rect(CX - s / 2 + ((k * 37) % 5 - 2) * 0.08f, y, s, s)
                9 -> if (k % 2 == 0) { sr.color = tmp.set(Pal.game[k % 4]).also { it.a = f }; sr.rect(CX + sin(k * 3f) * 0.4f, y, 0.12f, 0.2f) }
            }
        }
    }

    // ---- efektler ----

    fun tapEffect(style: Int, color: Int, py: Float) {
        squash = 1f
        val c = Pal.gameColor(color)
        when (style) {
            1 -> particles.emit(CX, py, 10, Pal.YELLOW, 4f, 0.12f, 0.5f, 1, 0f)
            2 -> particles.emit(CX, py, 1, c, 0f, 0.5f, 0.45f, 5, 0f)
            3 -> repeat(4) { k -> particles.emit(CX, py, 4, Pal.game[k], 5f, 0.12f, 0.6f, 3, -6f) }
            4 -> particles.emit(CX, py, 8, c, 6f, 0.18f, 0.5f, 1, 0f)
            5 -> particles.emit(CX, py, 6, Pal.PINK, 3.5f, 0.14f, 0.6f, 2, 2f)
            6 -> particles.emit(CX, py, 8, Pal.TURQ, 4.5f, 0.15f, 0.5f, 4, -3f)
            7 -> repeat(4) { k -> particles.emit(CX, py, 3, Pal.game[k], 4f, 0.12f, 0.5f, 0, 0f) }
            else -> particles.emit(CX, py, 8, c, 3.5f, 0.11f, 0.35f, 0, 0f)
        }
    }

    fun gateEffect(style: Int, color: Int, y: Float, ok: Boolean) {
        val c = Pal.gameColor(color)
        if (!ok) { particles.emit(CX, y, 26, Pal.GREY, 6f, 0.16f, 0.7f, 3, -8f); return }
        when (style) {
            1 -> particles.emit(CX, y, 22, Pal.YELLOW, 8f, 0.08f, 0.45f, 0, -2f, 120f, 90f)
            2 -> particles.emit(CX, y, 14, Pal.WHITE, 3f, 0.2f, 0.8f, 0, 3f)
            3 -> particles.emit(CX, y, 12, Pal.YELLOW, 9f, 0.12f, 0.35f, 4, 0f, 60f, 90f)
            4 -> particles.emit(CX, y, 14, Pal.PINK, 4f, 0.16f, 0.9f, 0, -1.5f)
            5 -> particles.emit(CX, y, 14, Pal.WHITE, 3f, 0.16f, 1f, 6, -1f)
            6 -> particles.emit(CX, y, 8, Pal.TEXT, 3f, 0.2f, 0.9f, 7, 2f)
            7 -> repeat(4) { k -> particles.emit(CX + (k - 1.5f) * 1.5f, y + 1f, 8, Pal.game[k], 5f, 0.1f, 0.7f, 1, -5f) }
            else -> { particles.emit(ROAD_L + 0.3f, y, 8, c, 4f, 0.13f, 0.4f, 0, -3f, 120f, 30f); particles.emit(ROAD_R - 0.3f, y, 8, c, 4f, 0.13f, 0.4f, 0, -3f, 120f, 150f) }
        }
    }

    fun coinEffect(y: Float) = particles.emit(CX, y, 8, Pal.YELLOW, 3.5f, 0.1f, 0.4f, 1, 0f)
    fun diamondEffect(y: Float) = particles.emit(CX, y, 16, Pal.TURQ, 5f, 0.12f, 0.6f, 4, -2f)

    override fun dispose() { sr.dispose(); batch.dispose() }
}
