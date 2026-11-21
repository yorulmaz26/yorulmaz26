package com.besaniyelik.dunya.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.besaniyelik.dunya.Assets
import com.besaniyelik.dunya.Theme
import com.besaniyelik.dunya.level.*
import com.besaniyelik.dunya.progress.Skin
import com.besaniyelik.dunya.progress.Trail
import com.besaniyelik.dunya.sim.*
import kotlin.math.*

/** Simülasyonu çizer. Oyun mantığına dokunmaz; yalnızca durumu okur ve olaylara görsel tepki verir. */
class WorldRenderer(
    private val a: Assets,
    private val sim: Simulation,
    private val map: Int,
    private val skin: Skin,
    private val trail: Trail,
    reducedEffects: Boolean,
) {
    val camera = OrthographicCamera()
    val viewport = ExtendViewport(VIEW_W, VIEW_H, camera)
    val theme: Theme = a.theme(map)
    val particles = Particles().apply { reduced = reducedEffects }
    private val character = CharacterRenderer(a)
    private val bodyColor = rgb(skin.body)
    private val trailColors = trail.colors.map { rgb(it) }
    private val trailRegion: TextureRegion = a.g(when (trail.shape) {
        "heart" -> "pt_heart"; "star" -> "pt_star"; "bubble" -> "pt_bubble"; "note" -> "pt_note"; "confetti" -> "pt_confetti"
        "crystal" -> "pt_crystal"; "glow" -> "pt_glow"; "dot" -> "pt_dot"; else -> "pt_dust"
    })
    private val ambientRegion = a.g(when (map) { 1, 7 -> "pt_leaf"; 2 -> "pt_bubble"; 4 -> "pt_snow"; 5 -> "pt_glow"; 6 -> "pt_confetti"; 8 -> "pt_crystal"; 10 -> "pt_star"; else -> "pt_dot" })
    private val ambientColors = when (map) {
        1, 7 -> listOf(Color.valueOf("8FDB6A"), Color.valueOf("FFB6C9")); 2 -> listOf(Color.WHITE); 4 -> listOf(Color.WHITE)
        5 -> listOf(Color.valueOf("FFF59D"), Color.valueOf("C6FF8A")); 6 -> listOf(Color.valueOf("FF9F43"), Color.valueOf("58D6D0"), Color.valueOf("FFD54F"))
        8 -> listOf(Color.valueOf("C9E8FF"), Color.valueOf("E3D9FF")); 10 -> listOf(Color.valueOf("FFE27A"), Color.valueOf("FFB6C9"))
        else -> listOf(Color.valueOf("FFE7B8"), Color.WHITE)
    }
    var time = 0f
    private var camX = sim.player.x + LOOK_AHEAD
    private var flip = 1f
    private var squash = 0f
    private var trailTimer = 0f
    private var ambientTimer = 0f
    private var deathT = 0f
    var happy = false

    private class Decor(val region: TextureRegion, val x: Float, val y: Float, val w: Float, val h: Float, val down: Boolean)
    private val decors = ArrayList<Decor>()
    private var decorProcessed = 0

    private val spike = a.g("spike")
    /** Diken görseli 5 eşit diş hücresidir (tools/art/user_art.py spike_sheet). */
    private val spikeCells = Array(5) { TextureRegion(spike, it * (spike.regionWidth / 5), 0, spike.regionWidth / 5, spike.regionHeight) }
    private val saw = a.g("saw"); private val spring0 = a.g("spring_0"); private val spring1 = a.g("spring_1")
    private val boost = a.g("boost"); private val flag0 = a.g("flag_0"); private val flag1 = a.g("flag_1"); private val goal = a.g("goal")
    private val chaser = a.g("chaser"); private val ghost = a.g("tile_ghost"); private val shift = a.g("tile_shift"); private val move = a.g("tile_move")
    private val coins = (0 until 6).map { a.g("coin_$it") }
    private val star = a.g("star_item"); private val piece = a.g("piece_item"); private val bubble = a.g("bubble"); private val glow = a.g("pt_glow")
    private val dust = a.g("pt_dust"); private val sparkle = a.g("pt_sparkle"); private val starP = a.g("pt_star"); private val confetti = a.g("pt_confetti")
    private val haze = a.ui("white").let { TextureRegion(it, 6, 6, 4, 4) }
    private val ring = a.g("pt_ring"); private val line = a.g("pt_line"); private val arrow = a.g("arrow")
    private val confettiColors = listOf(Color.valueOf("FF6F91"), Color.valueOf("FFD54F"), Color.valueOf("58D6D0"), Color.valueOf("73D65C"), Color.valueOf("B388FF"), Color.valueOf("FF9F43"))

    companion object {
        /** Yalnızca masaüstü teşhis turu: virgülle ayrılmış atlanacak katmanlar. */
        var debugSkip = ""
        private val BUSY_MAPS = setOf(5, 7, 8, 9, 10)
        // Yatay ekran: en az 21 x 11.8 karo görünür; zemin (0) ve tavan (9) aynı anda ekranda (geniş ekranlarda genişlik artar)
        const val VIEW_W = 21f
        const val VIEW_H = 11.8f
        const val LOOK_AHEAD = 4.6f
        const val CAM_Y = Physics.CEIL / 2f + 0.2f
    }

    fun resize(w: Int, h: Int) = viewport.update(w, h, false)

    fun update(dt: Float) {
        time += dt
        val p = sim.player
        // oyuncu ekranın soldan yaklaşık %28'inde durur
        val target = p.x + max(LOOK_AHEAD, viewport.worldWidth * 0.22f)
        camX += (target - camX) * min(1f, dt * 8f)
        if (abs(target - camX) > 8f) camX = target
        camera.position.set(camX, CAM_Y, 0f)
        camera.update()
        val ws = sim.effects.gravitySign.toFloat()
        flip += (ws - flip) * min(1f, dt * 12f)
        squash = max(0f, squash - dt * 4f)
        particles.update(dt)
        // iz efekti
        if (sim.status == SimStatus.RUNNING) {
            trailTimer -= dt
            if (trailTimer <= 0f && (p.grounded || trail.id != "toz")) {
                trailTimer = if (trail.id == "toz") 0.09f else 0.05f
                val fy = p.y - p.hh * ws
                particles.spawn(trailRegion, p.x - p.hw * 0.6f, fy + 0.1f * ws, -1f + MathUtils.random(-0.4f, 0.4f), MathUtils.random(0.2f, 0.8f) * ws,
                    0.5f, if (trail.id == "toz") 0.32f else 0.26f, 0.05f, trailColors[MathUtils.random(trailColors.size - 1)].cpy().also { it.a = 0.85f }, vr = 90f)
            }
        }
        if (sim.status == SimStatus.DYING) deathT += dt else deathT = 0f
        // ortam parçacıkları
        ambientTimer -= dt
        if (ambientTimer <= 0f) {
            ambientTimer = 0.25f
            val vw = viewport.worldWidth; val vh = viewport.worldHeight
            particles.spawn(ambientRegion, camX + vw / 2 + 1f, CAM_Y + MathUtils.random(-vh / 2.5f, vh / 2.5f), MathUtils.random(-2.5f, -1f), MathUtils.random(-0.6f, 0.4f),
                9f, MathUtils.random(0.18f, 0.34f), 0.15f, ambientColors[MathUtils.random(ambientColors.size - 1)].cpy().also { it.a = 0.7f }, vr = MathUtils.random(-60f, 60f))
        }
        if (sim.effects.timeScale > 1.1f && MathUtils.randomBoolean(0.5f)) {
            val vh = viewport.worldHeight
            particles.spawn(line, camX + viewport.worldWidth / 2, CAM_Y + MathUtils.random(-vh / 3, vh / 3), -26f, 0f, 0.6f, 1.6f, 1.4f, Color(1f, 1f, 1f, 0.5f))
        }
        buildDecor()
    }

    private fun buildDecor() {
        val solids = sim.solids
        while (decorProcessed < solids.size) {
            val s = solids[decorProcessed]
            decorProcessed++
            val d = s.def
            if (d.type != SolidType.GROUND && d.type != SolidType.CEILING) continue
            if (d.w < 3f) continue
            val down = d.type == SolidType.CEILING
            val surface = if (down) d.y else d.y + d.h
            val rnd = java.util.Random(s.index * 7919L + d.x.toLong())
            var x = d.x + 1f + rnd.nextFloat() * 2f
            while (x < d.x + d.w - 1f) {
                val near = sim.hazards.any { abs(it.def.x + (if (it.def.type == HazardType.SPIKE) it.def.w / 2 else 0f) - x) < 2.2f } ||
                    sim.pads.any { abs(it.def.x - x) < 2.5f } || sim.solids.any { it.def.type != SolidType.GROUND && it.def.type != SolidType.CEILING && x > it.def.x - 1.5f && x < it.def.x + it.def.w + 1.5f && abs((if (down) it.def.y + it.def.h else it.def.y) - surface) < 5f }
                if (!near && rnd.nextFloat() < 0.75f) {
                    val r = theme.decor[rnd.nextInt(theme.decor.size)]
                    val sc = (1f / 110f) * (0.9f + rnd.nextFloat() * 0.2f)
                    val w = r.regionWidth * sc; val h = r.regionHeight * sc
                    decors.add(Decor(r, x - w / 2, if (down) surface + 0.08f else surface - 0.08f, w, h, down))
                }
                x += 2.5f + rnd.nextFloat() * 4f
            }
        }
        if (decors.size > 400) decors.removeAll { it.x + it.w < camX - 20f }
    }

    // ------------------------------------------------------------------ olaylar
    fun onJump() { val p = sim.player; squash = 0f; particles.burst(dust, p.x, p.y - p.hh * sim.effects.gravitySign, 5, 2f, 0.35f, 0.3f, Color(1f, 1f, 1f, 0.9f)) }
    fun onLand(impact: Float) { squash = 0.6f * impact + 0.2f; val p = sim.player; particles.burst(dust, p.x, p.y - p.hh * sim.effects.gravitySign, (4 + impact * 6).toInt(), 2.5f, 0.4f, 0.32f, Color(1f, 1f, 1f, 0.9f)) }
    fun onCoin(c: Collectible) { particles.burst(sparkle, c.x, c.y, 5, 2.5f, 0.35f, 0.25f, Color.valueOf("FFE27A")) }
    fun onStar(c: Collectible) { particles.burst(starP, c.x, c.y, 16, 5f, 0.8f, 0.35f, Color.valueOf("FFD54F")); particles.spawn(ring, c.x, c.y, 0f, 0f, 0.5f, 0.4f, 3f, Color.valueOf("FFE27A")) }
    fun onPiece(c: Collectible) { particles.burst(starP, c.x, c.y, 20, 5f, 0.9f, 0.35f, Color.valueOf("C6A8FF")) }
    fun onPowerup() { val p = sim.player; particles.spawn(ring, p.x, p.y, 0f, 0f, 0.5f, 0.5f, 3.5f, Color.valueOf("58D6D0")) }
    fun onDeath() { val p = sim.player; particles.burst(starP, p.x, p.y, 18, 6f, 0.8f, 0.3f, bodyColor, colors = listOf(bodyColor, Color.WHITE)) }
    fun onRule() { val p = sim.player; particles.spawn(ring, p.x, p.y, 0f, 0f, 0.6f, 0.6f, 6f, Color(1f, 1f, 1f, 0.9f)); particles.burst(confetti, p.x, p.y, 14, 6f, 0.9f, 0.22f, Color.WHITE, -6f * sim.effects.gravitySign, confettiColors) }
    fun onCheckpoint(x: Float) {
        val down = sim.phases.lastOrNull { it.x <= x }?.let { RuleEffects.of(it.rules).gravitySign < 0 } == true
        particles.burst(starP, x, if (down) Physics.CEIL - 1.5f else 1.5f, 10, 4f, 0.7f, 0.25f, Color.valueOf("73D65C"))
    }
    fun onSpring() { squash = 0.8f }
    fun celebrate() {
        happy = true
        val vw = viewport.worldWidth
        for (i in 0 until 60) particles.spawn(confetti, camX + MathUtils.random(-vw / 2, vw / 2), CAM_Y + viewport.worldHeight / 2, MathUtils.random(-1f, 1f), MathUtils.random(-6f, -2f), 3f, 0.25f, 0.2f, confettiColors[i % confettiColors.size], 0f, MathUtils.random(-400f, 400f))
    }

    // ------------------------------------------------------------------ çizim
    fun render(batch: Batch) {
        viewport.apply()
        batch.projectionMatrix = camera.combined
        batch.begin()
        val vw = viewport.worldWidth; val vh = viewport.worldHeight
        val left = camX - vw / 2; val bottom = CAM_Y - vh / 2; val top = CAM_Y + vh / 2
        batch.setColor(1f, 1f, 1f, 1f)
        theme.drawSky(batch, left - 0.1f, bottom - 0.1f, vw + 0.2f, vh + 0.2f)
        // uzak katman soluk (atmosfer derinliği), yakın katman tam renk; üstüne hafif pus: ön plan okunaklı kalır
        batch.setColor(1f, 1f, 1f, 0.8f)
        if ("far" !in debugSkip) parallax(batch, theme.far, left, vw, -1.2f, 9.5f, 0.12f)
        batch.setColor(1f, 1f, 1f, 1f)
        if ("mid" !in debugSkip) parallax(batch, theme.mid, left, vw, -1.8f, 7.2f, 0.3f)
        // ayrıntılı arka planlı haritalarda pus biraz daha güçlü: karakter ve engeller öne çıkar
        batch.setColor(1f, 1f, 1f, if (map in BUSY_MAPS) 0.36f else 0.24f)
        batch.draw(haze, left - 0.1f, bottom - 0.1f, vw + 0.2f, vh + 0.2f)
        batch.setColor(1f, 1f, 1f, 1f)
        val x0 = left - 1f; val x1 = left + vw + 1f
        for (d in decors) {
            if ("decor" in debugSkip) break
            if (d.x + d.w < x0 || d.x > x1) continue
            if (d.down) batch.draw(d.region, d.x, d.y + d.h, d.w, -d.h) else batch.draw(d.region, d.x, d.y, d.w, d.h)
        }
        drawSolids(batch, x0, x1, bottom, top)
        drawPads(batch, x0, x1)
        drawMarkers(batch, x0, x1)
        drawHazards(batch, x0, x1)
        drawCollectibles(batch, x0, x1)
        if ("particles" !in debugSkip) particles.draw(batch)
        if ("player" !in debugSkip) drawPlayer(batch)
        if (sim.chaserEnabled) {
            // Kaos Duvarı: görselin kendi oranıyla, dikeyde döşenerek; sağ kenarı chaserX'tedir
            val ch = 3.3f
            val cw = ch * chaser.regionWidth / chaser.regionHeight.toFloat()
            var y = bottom - (bottom % ch) - ch
            batch.setColor(0.55f, 0.2f, 0.75f, 0.55f)
            batch.draw(glow, sim.chaserX - cw * 1.4f, bottom - 1f, cw * 2.4f, vh + 2f)
            batch.setColor(1f, 1f, 1f, 1f)
            while (y < top) {
                batch.draw(chaser, sim.chaserX - cw * 2.1f, y + ch * 0.5f, cw, ch)
                batch.draw(chaser, sim.chaserX - cw, y, cw, ch)
                y += ch
            }
        }
        batch.end()
    }

    private fun parallax(batch: Batch, tex: Texture, left: Float, vw: Float, y: Float, h: Float, factor: Float) {
        val texW = h * Theme.PARALLAX_ASPECT
        val u0 = left * factor / texW
        val u1 = u0 + vw / texW
        batch.draw(tex, left, y, vw, h, u0, 1f, u1, 0f)
    }

    /** Bir bölgeyi 1 birimlik karolarla döşer; son sütun kesirli kırpılır. */
    private fun tileRow(batch: Batch, r: TextureRegion, x: Float, w: Float, y: Float, h: Float, x0: Float, x1: Float) {
        var cx = max(x, x + floor((x0 - x).coerceAtLeast(0f)))
        val end = min(x + w, x1)
        while (cx < end) {
            val cw = min(1f, x + w - cx)
            if (cw >= 0.999f) batch.draw(r, cx, y, cw, h)
            else batch.draw(r.texture, cx, y, cw, h, r.u, r.v2, r.u + (r.u2 - r.u) * cw, r.v)
            cx += 1f
        }
    }

    private fun drawSolids(batch: Batch, x0: Float, x1: Float, bottom: Float, top: Float) {
        for (s in sim.solids) {
            if (s.removed || s.x > x1 || s.x + s.w < x0) continue
            val d = s.def
            when (d.type) {
                SolidType.GROUND -> {
                    val yTop = s.y + s.h
                    tileRow(batch, theme.top, s.x, s.w, yTop - 1f, 1f, x0, x1)
                    var y = yTop - 2f
                    while (y > bottom - 1f) { tileRow(batch, theme.fill, s.x, s.w, y, 1f, x0, x1); y -= 1f }
                }
                SolidType.CEILING -> {
                    tileRow(batch, theme.ceil, s.x, s.w, s.y, 1f, x0, x1)
                    var y = s.y + 1f
                    while (y < top + 1f) { tileRow(batch, theme.fill, s.x, s.w, y, 1f, x0, x1); y += 1f }
                }
                SolidType.BLOCK -> drawBlock(batch, s.x, s.y, s.w, s.h)
                SolidType.LOW -> {
                    // dar geçit tavanı: yüzeye bakan tarafta saçaklı karo
                    val facingDown = s.y < Physics.CEIL / 2f
                    var y = s.y
                    while (y < s.y + s.h - 0.01f) {
                        val hh = min(1f, s.y + s.h - y)
                        val r = if ((facingDown && y == s.y) || (!facingDown && y + hh >= s.y + s.h - 0.01f)) theme.low else theme.fill
                        if (!facingDown && r === theme.low) tileRowFlip(batch, r, s.x, s.w, y, hh, x0, x1) else tileRow(batch, r, s.x, s.w, y, hh, x0, x1)
                        y += 1f
                    }
                }
                SolidType.GHOST -> {
                    val alpha = when (s.ghostState) { 0 -> 1f; 1 -> if ((time * 12f).toInt() % 2 == 0) 0.9f else 0.35f; else -> 0.16f }
                    batch.setColor(1f, 1f, 1f, alpha)
                    tileRow(batch, ghost, s.x, s.w, s.y, s.h, x0, x1)
                    batch.setColor(1f, 1f, 1f, 1f)
                }
                SolidType.SHIFT -> tileRow(batch, shift, s.x, s.w, s.y, s.h, x0, x1)
                SolidType.MOVE -> tileRow(batch, move, s.x, s.w, s.y, s.h, x0, x1)
            }
        }
    }

    private fun tileRowFlip(batch: Batch, r: TextureRegion, x: Float, w: Float, y: Float, h: Float, x0: Float, x1: Float) {
        var cx = x
        while (cx < min(x + w, x1)) {
            val cw = min(1f, x + w - cx)
            if (cx + cw > x0) batch.draw(r.texture, cx, y, cw, h, r.u, r.v, r.u + (r.u2 - r.u) * cw, r.v2)
            cx += 1f
        }
    }

    /** Havada duran platform: görselin kendi oranıyla, yürünen yüzey çarpışma kutusunun yüzeyine hizalı.
     *  Tavan tarafındaki (ters yer çekimi) platformlar dikeyde çevrilir. */
    private fun drawBlock(batch: Batch, x: Float, y: Float, w: Float, h: Float) {
        val L = theme.blockL; val M = theme.blockM; val R = theme.blockR
        val unitPx = M.regionWidth.toFloat()
        val vh = max(h, M.regionHeight / unitPx)
        val down = y + h / 2f > Physics.CEIL / 2f
        val yy = if (down) y else y + h - vh
        var capL = min(w / 2f, L.regionWidth / unitPx)
        var capR = min(w / 2f, R.regionWidth / unitPx)
        fun part(r: TextureRegion, px: Float, pw: Float, uFrac0: Float, uFrac1: Float, rh: Float) {
            val u0 = r.u + (r.u2 - r.u) * uFrac0; val u1 = r.u + (r.u2 - r.u) * uFrac1
            val ph = vh * rh
            val py = if (down) y else y + h - ph
            if (down) batch.draw(r.texture, px, py, pw, ph, u0, r.v, u1, r.v2) else batch.draw(r.texture, px, py, pw, ph, u0, r.v2, u1, r.v)
        }
        val rl = L.regionHeight / M.regionHeight.toFloat(); val rr = R.regionHeight / M.regionHeight.toFloat()
        part(L, x, capL, 0f, 1f, rl)
        var cx = x + capL
        while (cx < x + w - capR - 0.001f) {
            val cw = min(1f, x + w - capR - cx)
            part(M, cx, cw, 0f, cw, 1f)
            cx += 1f
        }
        part(R, x + w - capR, capR, 0f, 1f, rr)
    }

    private fun drawPads(batch: Batch, x0: Float, x1: Float) {
        for (p in sim.pads) {
            val d = p.def
            if (d.x > x1 || d.x + d.w < x0) continue
            p.anim = max(0f, p.anim - 1f / 60f)
            when (d.type) {
                PadType.SPRING -> {
                    val r = if (p.anim > 0f) spring1 else spring0
                    val w = d.w + 0.5f
                    val h = min(1.5f, w * r.regionHeight / r.regionWidth.toFloat())
                    if (d.dir > 0) batch.draw(r, d.x - 0.25f, d.y - 0.05f, w, h) else batch.draw(r, d.x - 0.25f, d.y + 0.05f, w, -h)
                }
                PadType.BOOST -> {
                    val pulse = 0.85f + 0.15f * sin(time * 10f)
                    batch.setColor(0.3f, 1f, 0.95f, 0.45f * pulse)
                    batch.draw(glow, d.x - 0.5f, d.y - 0.7f, d.w + 1f, 1.4f)
                    batch.setColor(1f, 1f, 1f, pulse)
                    if (d.dir > 0) batch.draw(boost, d.x - 0.15f, d.y - 0.03f, d.w + 0.3f, 0.66f) else batch.draw(boost, d.x - 0.15f, d.y + 0.03f, d.w + 0.3f, -0.66f)
                    batch.setColor(1f, 1f, 1f, 1f)
                }
            }
        }
    }

    private fun drawMarkers(batch: Batch, x0: Float, x1: Float) {
        for (cp in sim.checkpoints) {
            if (cp < x0 - 1 || cp > x1 + 1) continue
            val passed = sim.lastCheckpointX >= cp
            val ph = sim.phases.lastOrNull { it.x <= cp }
            val down = ph != null && RuleEffects.of(ph.rules).gravitySign < 0
            val r = if (passed) flag1 else flag0
            val fh = 2.1f
            val fw = fh * r.regionWidth / r.regionHeight.toFloat()
            if (down) batch.draw(r, cp - fw * 0.4f, Physics.CEIL, fw, -fh) else batch.draw(r, cp - fw * 0.4f, 0f, fw, fh)
        }
        if (sim.goalX < x1 + 3f && sim.goalX > x0 - 4f) {
            val gh = 3.95f; val gw = gh * goal.regionWidth / goal.regionHeight.toFloat()
            batch.draw(goal, sim.goalX - gw / 2f, -0.05f, gw, gh)
        }
    }

    private fun drawHazards(batch: Batch, x0: Float, x1: Float) {
        for (h in sim.hazards) {
            if (h.removed) continue
            val d = h.def
            h.update(sim.time, sim.player.x)
            when (d.type) {
                HazardType.SPIKE -> {
                    if (h.cx > x1 || h.cx + d.w < x0) continue
                    // kırmızı tehlike parıltısı + iri dişler (karo başına iki diş, karakterin yarı boyunda)
                    val gh = Physics.SPIKE_VISUAL_H * 2.1f
                    batch.setColor(1f, 0.22f, 0.18f, 0.42f + 0.1f * sin(time * 5f + h.cx))
                    batch.draw(glow, h.cx - 0.5f, h.cy - gh / 2f, d.w + 1f, gh)
                    batch.setColor(1f, 1f, 1f, 1f)
                    val teeth = max(2, (d.w / 0.5f + 0.5f).toInt())
                    val cw = d.w / teeth
                    val sh = Physics.SPIKE_VISUAL_H * d.dir
                    for (i in 0 until teeth) batch.draw(spikeCells[i % spikeCells.size], h.cx + i * cw, h.cy - 0.04f * d.dir, cw, sh)
                }
                HazardType.SAW -> {
                    if (h.cx - d.w > x1 || h.cx + d.w < x0) continue
                    val gs = d.w * 3.1f
                    batch.setColor(1f, 0.22f, 0.18f, 0.42f + 0.1f * sin(time * 5f + h.cx))
                    batch.draw(glow, h.cx - gs / 2, h.cy - gs / 2, gs, gs)
                    batch.setColor(1f, 1f, 1f, 1f)
                    // çizilen yarıçap (diş uçları) d.w; isabet yarıçapı d.w * SAW_HIT
                    val s = d.w * 2.08f
                    batch.draw(saw, h.cx - s / 2, h.cy - s / 2, s / 2, s / 2, s, s, 1f, 1f, -sim.time * 400f)
                }
            }
        }
    }

    private fun drawCollectibles(batch: Batch, x0: Float, x1: Float) {
        val p = sim.player
        val finder = sim.finder > 0f
        for (c in sim.collectibles) {
            if (c.collected || c.removed) continue
            if (c.x < x0 - 1 || c.x > x1 + 1) {
                if (finder && (c.kind == CollectKind.STAR || c.kind == CollectKind.PIECE) && c.x > x1 && c.x < x1 + 25f) {
                    // yıldız algılayıcı: ekran kenarında ok
                    val ay = c.y.coerceIn(CAM_Y - 8f, CAM_Y + 8f)
                    batch.setColor(1f, 0.85f, 0.3f, 0.6f + 0.4f * sin(time * 8f))
                    batch.draw(arrow, x1 - 1.8f, ay - 0.35f, 0.7f, 0.7f)
                    batch.setColor(1f, 1f, 1f, 1f)
                }
                continue
            }
            var dx = c.x; var dy = c.y
            if (c.kind == CollectKind.COIN && sim.magnet > 0f) {
                val ddx = p.x - c.x; val ddy = p.y - c.y
                val dist = sqrt(ddx * ddx + ddy * ddy)
                if (dist < 4.5f) { c.drawX += (p.x - c.drawX) * 0.25f; c.drawY += (p.y - c.drawY) * 0.25f; dx = c.drawX; dy = c.drawY }
            }
            val bob = sin(time * 3f + c.x) * 0.08f
            when (c.kind) {
                CollectKind.COIN -> {
                    val f = ((time * 10f + c.x * 1.7f).toInt() % 6)
                    batch.draw(coins[f], dx - 0.39f, dy - 0.39f + bob, 0.78f, 0.78f)
                }
                CollectKind.STAR -> {
                    batch.setColor(1f, 0.95f, 0.6f, 0.5f); batch.draw(glow, dx - 0.9f, dy - 0.9f + bob, 1.8f, 1.8f); batch.setColor(1f, 1f, 1f, 1f)
                    val s = 1.12f + sin(time * 5f) * 0.06f
                    batch.draw(star, dx - 0.5f * s, dy - 0.5f * s + bob, 0.5f * s, 0.5f * s, s, s, 1f, 1f, sin(time * 2f) * 8f)
                }
                CollectKind.PIECE -> {
                    val near = abs(p.x - c.x) < 4f
                    batch.setColor(1f, 1f, 1f, if (finder || near) 1f else 0.3f)
                    batch.draw(piece, dx - 0.58f, dy - 0.58f + bob, 1.16f, 1.16f)
                    batch.setColor(1f, 1f, 1f, 1f)
                }
                CollectKind.POWERUP -> {
                    val icon = a.g("b_" + (c.powerup?.icon ?: "p_shield"))
                    batch.setColor(0.5f, 1f, 0.95f, 0.5f); batch.draw(glow, dx - 1f, dy - 1f + bob, 2f, 2f); batch.setColor(1f, 1f, 1f, 1f)
                    batch.draw(icon, dx - 0.43f, dy - 0.43f + bob, 0.86f, 0.86f)
                    batch.draw(bubble, dx - 0.6f, dy - 0.6f + bob, 1.2f, 1.2f)
                }
            }
        }
    }

    private fun drawPlayer(batch: Batch) {
        val p = sim.player
        val ws = sim.effects.gravitySign
        // Görsel boy PLAYER_VISUAL_H; ayaklar çarpışma kutusunun tabanına oturur (kare boyu = size * 1.45).
        val size = Physics.PLAYER_VISUAL_H * p.scale / 1.45f
        val feet = p.y - ws * p.hh
        var state = when {
            happy -> CharState.HAPPY
            sim.status == SimStatus.DYING -> CharState.DEAD
            else -> CharacterRenderer.stateFor(p.grounded, p.vyUp())
        }
        var alpha = 1f
        var rot = 0f
        var cy = feet + ws * size / 2f
        if (sim.status == SimStatus.DYING) { rot = deathT * 540f; alpha = max(0f, 1f - deathT * 1.2f); cy += deathT * 2f * ws }
        if (p.invuln > 0f && sim.status == SimStatus.RUNNING && (time * 14f).toInt() % 2 == 0) alpha = 0.45f
        if (sim.status == SimStatus.GAME_OVER) return
        if (p.invuln > 1.7f || sim.player.invuln > 0f && sim.powerupsUsed > 0 && p.invuln > 2f) {
            batch.setColor(1f, 0.9f, 0.4f, 0.5f); val mid = cy + ws * (Physics.PLAYER_VISUAL_H * p.scale / 2f - size / 2f); batch.draw(glow, p.x - size * 1.3f, mid - size * 1.3f, size * 2.6f, size * 2.6f); batch.setColor(1f, 1f, 1f, 1f)
        }
        val flipNow = flip < 0f
        character.draw(batch, skin, p.x, cy, size, state, time, flipNow, squash, rot, alpha)
        if (sim.shield > 0) {
            batch.setColor(0.6f, 1f, 0.97f, 0.55f + 0.15f * sin(time * 6f))
            val mid = cy + ws * (Physics.PLAYER_VISUAL_H * p.scale / 2f - size / 2f); val bs = Physics.PLAYER_VISUAL_H * p.scale * 1.12f
            batch.draw(bubble, p.x - bs / 2f, mid - bs / 2f, bs, bs)
            batch.setColor(1f, 1f, 1f, 1f)
        }
        if (p.boost > 0f) {
            batch.setColor(1f, 1f, 1f, 0.6f)
            for (i in 0 until 3) batch.draw(line, p.x - size * (1.1f + i * 0.25f), cy - 0.25f + i * 0.25f, size * 0.9f, 0.12f)
            batch.setColor(1f, 1f, 1f, 1f)
        }
    }
}
