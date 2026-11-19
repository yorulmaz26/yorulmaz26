package com.kayipadalar.game.view

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.kayipadalar.game.Assets
import com.kayipadalar.game.ui.Theme
import com.kayipadalar.logic.kingdom.*
import kotlin.math.sin

/** Krallık sahnesi yerleşimi: tüm sprite boyutları ve izometrik konumlar tek yerden yönetilir. */
object Layout {
    const val HOME_W = 780f
    const val ISLAND_W = 380f
    const val DOCK_RX = 660f
    const val DOCK_RY = 500f
    const val HOME_TOP = 0.66f      // ana ada üst yüzeyinin sprite yüksekliğine oranı
    const val ISLAND_TOP = 0.60f
    const val PLOT_SX = 150f        // izometrik arsa aralığı (yatay)
    const val PLOT_SY = 74f         // izometrik arsa aralığı (dikey)
    const val BUILDING_W = 175f
    const val CASTLE_W = 270f
    const val SAT_BUILDING_W = 140f

    fun dockPos(d: Int): Vector2 {
        val a = MathUtils.degreesToRadians * (90f + d * 45f)
        return Vector2(MathUtils.cos(a) * DOCK_RX, MathUtils.sin(a) * DOCK_RY)
    }

    /** Ana adada 3x3 izometrik arsa; 0 = arka orta (Ana Bina). */
    private val homePlots = listOf(-1 to -1, 0 to -1, -1 to 0, 1 to -1, -1 to 1, 0 to 0, 1 to 0, 0 to 1, 1 to 1)
    private val satPlots = listOf(0 to 0, -1 to 0, 1 to 0, 0 to 1)

    fun plotOffset(home: Boolean, plot: Int): Vector2 {
        val (i, j) = (if (home) homePlots else satPlots).getOrElse(plot) { 0 to 0 }
        val s = if (home) 1f else 0.78f
        return Vector2((i - j) * PLOT_SX * s, -(i + j) * PLOT_SY * s)
    }
}

/**
 * Ana krallık görünümü. Adaları, köprüleri, binaları ve etkileşimleri çizer.
 * Dokunmalar [onPlot], [onBuilding], [onCollect], [onIsland], [onRaid], [onDock] ile ekrana iletilir.
 */
class KingdomView(
    private val k: Kingdom,
    private val assets: Assets,
    private val theme: Theme,
    private val now: () -> Long,
) : com.badlogic.gdx.scenes.scene2d.ui.Widget() {
    var onPlot: (Int, Int) -> Unit = { _, _ -> }
    var onBuilding: (BuildingState) -> Unit = {}
    var onCollect: (BuildingState) -> Unit = {}
    var onIsland: (IslandState) -> Unit = {}
    var onRaid: () -> Unit = {}
    var onDock: (Int) -> Unit = {}

    /** Ada taşıma modunda yuvalar vurgulanır. */
    var moveMode: Int? = null
    /** Görev yönlendirme oku: (ada, arsa). */
    var pointer: Pair<Int, Int>? = null

    private var t = 0f
    val cam = PanZoom(0.74f, 0.3f, 1.5f) { x, y -> tap(x, y) }
    private val arrivals = HashMap<Int, Float>()
    private val bursts = ArrayList<Triple<Vector2, Float, String>>()
    private class Floater(val text: String, val icon: String?, var x: Float, var y: Float, var life: Float, val color: Color)
    private val floaters = ArrayList<Floater>()
    private val layout = GlyphLayout()

    init {
        addListener(cam)
        cam.bounds = 900f
    }

    // ------------------------------------------------------------------ Koordinatlar

    fun islandCenter(i: IslandState): Vector2 {
        if (i.id == 0) return Vector2(0f, 0f)
        val target = Layout.dockPos(i.dock)
        val a = arrivals[i.id] ?: return target
        val from = Vector2(target).nor().scl(1700f)
        val e = Interpolation.pow2Out.apply(a)
        return Vector2(from).lerp(target, e).add(0f, sin(a * MathUtils.PI) * 120f)
    }

    private fun islandWidth(i: IslandState) = if (i.id == 0) Layout.HOME_W else Layout.ISLAND_W

    private fun islandRegion(i: IslandState): TextureRegion = assets[k.cfg.island(i.type).sprite]

    private fun islandHeight(i: IslandState): Float {
        val r = islandRegion(i); return islandWidth(i) * r.regionHeight / r.regionWidth.coerceAtLeast(1)
    }

    /** Ada üst yüzeyinin merkezi (dünya koordinatı). */
    fun topCenter(i: IslandState): Vector2 {
        val c = islandCenter(i)
        val h = islandHeight(i)
        val frac = if (i.id == 0) Layout.HOME_TOP else Layout.ISLAND_TOP
        return Vector2(c.x, c.y - h / 2 + h * frac + bob(i))
    }

    private fun bob(i: IslandState) = sin(t * 1.2f + i.id * 1.7f) * 5f

    fun plotPos(i: IslandState, plot: Int): Vector2 = topCenter(i).add(Layout.plotOffset(i.id == 0, plot))

    private fun toScreen(w: Vector2): Vector2 = Vector2(x + width / 2 + cam.panX + w.x * cam.zoom, y + height / 2 + cam.panY + w.y * cam.zoom)
    private fun toWorld(lx: Float, ly: Float): Vector2 = Vector2((lx - width / 2 - cam.panX) / cam.zoom, (ly - height / 2 - cam.panY) / cam.zoom)

    fun focus(w: Vector2, zoom: Float = cam.zoom.coerceAtLeast(0.74f)) {
        cam.zoom = zoom
        cam.panX = -w.x * zoom; cam.panY = -w.y * zoom + 40f
        cam.clamp()
    }

    // ------------------------------------------------------------------ Olaylar

    fun arrive(islandId: Int) { arrivals[islandId] = 0f }

    fun floatText(text: String, icon: String?, world: Vector2, color: Color = Color.WHITE) {
        floaters.add(Floater(text, icon, world.x, world.y, 1.4f, color))
    }

    fun burst(world: Vector2, fx: String) { bursts.add(Triple(world, 0f, fx)) }

    fun buildingAnchor(b: BuildingState): Vector2? = k.island(b.island)?.let { plotPos(it, b.plot) }

    // ------------------------------------------------------------------ Dokunma

    private fun tap(lx: Float, ly: Float) {
        val w = toWorld(lx, ly)
        val n = now()
        moveMode?.let { _ ->
            for (d in 0 until k.r.dockSlots) if (Layout.dockPos(d).dst(w) < 150f) { onDock(d); return }
            moveMode = null; return
        }
        k.s.raid?.let { if (raidPos().dst(w) < 110f) { onRaid(); return } }
        // Toplama baloncukları öncelikli
        for (b in k.s.buildings) if (showBubble(b)) {
            val a = buildingAnchor(b) ?: continue
            if (Vector2(a.x, a.y + bubbleHeight(b)).dst(w) < 55f) { onCollect(b); return }
        }
        // Binalar (önden arkaya)
        val islands = k.s.islands.filter { it.status == IslandStatus.MERGED }
        for (b in k.s.buildings.sortedBy { buildingAnchor(it)?.y ?: 0f }) {
            val a = buildingAnchor(b) ?: continue
            val bw = buildingWidth(b) * 0.5f
            if (w.x in a.x - bw..a.x + bw && w.y in a.y - 30f..a.y + buildingWidth(b) * 0.8f) { onBuilding(b); return }
        }
        // Boş arsalar
        for (i in islands) for (p in 0 until k.plotsOf(i)) if (k.buildingAt(i.id, p) == null) {
            if (plotPos(i, p).dst(w) < 55f) { onPlot(i.id, p); return }
        }
        // Ada gövdesi
        for (i in islands.sortedBy { islandCenter(it).y }) {
            val c = islandCenter(i)
            if (i.id != 0 && Vector2(c).dst(w) < islandWidth(i) * 0.45f) { onIsland(i); return }
        }
    }

    private fun raidPos() = Vector2(-430f, -420f + sin(t * 1.5f) * 10f)

    fun buildingWidth(b: BuildingState): Float = when {
        b.type == "castle" -> Layout.CASTLE_W * (0.85f + 0.03f * b.level)
        b.island == 0 -> Layout.BUILDING_W
        else -> Layout.SAT_BUILDING_W
    }

    private fun buildingRegion(b: BuildingState): TextureRegion {
        val def = k.cfg.building(b.type)
        if (b.level == 0 && b.type == "castle") return assets["buildings/hi_castle"]
        return assets[def.spriteBig?.takeIf { b.level >= 4 } ?: def.sprite]
    }

    private fun showBubble(b: BuildingState): Boolean {
        if (b.busy || b.level < 1) return false
        val ph = k.perHour(b)
        return ph > 0 && b.stored >= maxOf(1.0, ph / 120.0) // ~30 sn'lik üretim birikince
    }

    private fun bubbleHeight(b: BuildingState) = buildingWidth(b) * 0.95f + sin(t * 3f + b.uid) * 5f

    // ------------------------------------------------------------------ Çizim

    override fun act(delta: Float) {
        super.act(delta)
        t += delta
        arrivals.entries.forEach { it.setValue(it.value + delta / 1.6f) }
        arrivals.entries.filter { it.value >= 1f }.map { it.key }.forEach { id ->
            arrivals.remove(id)
            k.island(id)?.let { burst(topCenter(it), "effects/fx_merge2") }
        }
        bursts.replaceAll { Triple(it.first, it.second + delta, it.third) }
        bursts.removeAll { it.second > 0.9f }
        floaters.forEach { it.y += 70f * delta; it.life -= delta }
        floaters.removeAll { it.life <= 0 }
    }

    private fun drawRegion(b: Batch, r: TextureRegion, world: Vector2, w: Float, anchorBottom: Boolean = true, alpha: Float = 1f, tint: Color? = null) {
        val h = w * r.regionHeight / r.regionWidth.coerceAtLeast(1)
        val s = toScreen(world)
        val sw = w * cam.zoom; val sh = h * cam.zoom
        val c = tint ?: Color.WHITE
        b.setColor(c.r, c.g, c.b, alpha)
        b.draw(r, s.x - sw / 2, if (anchorBottom) s.y else s.y - sh / 2, sw, sh)
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        validate()
        val n = now()
        val merged = k.s.islands.filter { it.status == IslandStatus.MERGED }
        val home = merged.first { it.id == 0 }
        val homeC = islandCenter(home)
        val (behind, front) = merged.filter { it.id != 0 }.partition { islandCenter(it).y > homeC.y }

        // Taşıma modunda yuvalar
        moveMode?.let { mid ->
            for (d in 0 until k.r.dockSlots) {
                val p = Layout.dockPos(d)
                val pulse = 0.6f + 0.4f * sin(t * 4f)
                drawRegion(batch, assets["effects/fx_portal1"], p, 260f, anchorBottom = false, alpha = pulse)
            }
        }
        for (i in behind.sortedByDescending { islandCenter(it).y }) { drawIsland(batch, i, n); drawBridge(batch, i) }
        drawIsland(batch, home, n)
        for (i in front.sortedByDescending { islandCenter(it).y }) { drawBridge(batch, i); drawIsland(batch, i, n) }

        // Baskın gemisi
        k.s.raid?.let {
            drawRegion(batch, assets["ships/ship_war"], raidPos(), 200f, anchorBottom = false, tint = Color(1f, 0.75f, 0.75f, 1f))
            drawRegion(batch, assets["icons/alert"], raidPos().add(0f, 120f + sin(t * 6f) * 8f), 64f, anchorBottom = false)
        }
        // Efektler
        for ((p, age, fx) in bursts) drawRegion(batch, assets[fx], p, 220f + age * 120f, anchorBottom = false, alpha = (1f - age / 0.9f).coerceIn(0f, 1f))
        // Görev oku
        pointer?.let { (isl, plot) ->
            k.island(isl)?.let { i ->
                val p = plotPos(i, plot).add(0f, 190f + sin(t * 5f) * 16f)
                val arrow = assets["effects/fx_up0"]
                val s = toScreen(p); val w = 90f * cam.zoom; val h = w * arrow.regionHeight / arrow.regionWidth
                batch.setColor(1f, 1f, 1f, 1f)
                batch.draw(arrow, s.x - w / 2, s.y - h / 2, w / 2, h / 2, w, h, 1f, 1f, 180f)
            }
        }
        // Yazılar
        for (f in floaters) {
            val s = toScreen(Vector2(f.x, f.y))
            val a = (f.life / 1.4f).coerceIn(0f, 1f)
            f.icon?.let { batch.setColor(1f, 1f, 1f, a); batch.draw(assets[it], s.x - 70f, s.y - 18f, 36f, 36f) }
            text(batch, theme.title, f.text, s.x + 10f, s.y, Color(f.color.r, f.color.g, f.color.b, a))
        }
        batch.setColor(Color.WHITE)
    }

    private fun drawBridge(batch: Batch, i: IslandState) {
        if (arrivals.containsKey(i.id)) return
        val home = k.island(0)!!
        val a = topCenter(home); val b = topCenter(i)
        val dir = Vector2(b).sub(a)
        val len = dir.len()
        val start = Vector2(a).add(Vector2(dir).nor().scl(Layout.HOME_W * 0.38f))
        val end = Vector2(b).sub(Vector2(dir).nor().scl(Layout.ISLAND_W * 0.3f))
        val seg = Vector2(end).sub(start)
        val plank = assets["terrain/t17"]
        val steps = (seg.len() / 46f).toInt().coerceAtLeast(1)
        batch.setColor(1f, 1f, 1f, 1f)
        for (s in 0..steps) {
            val p = Vector2(start).add(Vector2(seg).scl(s / steps.toFloat())).sub(0f, 20f)
            val sp = toScreen(p)
            val w = 58f * cam.zoom; val h = w * plank.regionHeight / plank.regionWidth
            batch.draw(plank, sp.x - w / 2, sp.y - h / 2, w, h)
        }
        if (len < 1f) return
    }

    private fun drawIsland(batch: Batch, i: IslandState, n: Long) {
        val c = islandCenter(i).add(0f, bob(i))
        drawRegion(batch, islandRegion(i), c, islandWidth(i), anchorBottom = false)
        if (arrivals.containsKey(i.id)) return
        val home = i.id == 0
        val plots = (0 until k.plotsOf(i)).sortedByDescending { plotPos(i, it).y }
        for (p in plots) {
            val pos = plotPos(i, p)
            val b = k.buildingAt(i.id, p)
            if (b == null) {
                val pulse = 0.75f + 0.25f * sin(t * 3f + p)
                drawRegion(batch, assets["terrain/t1"], pos, if (home) 118f else 96f, anchorBottom = false, alpha = 0.9f)
                text(batch, theme.title, "+", toScreen(pos).x, toScreen(pos).y + 6f * cam.zoom, Color(1f, 0.95f, 0.6f, pulse))
                continue
            }
            drawBuilding(batch, b, pos, n)
        }
    }

    private fun drawBuilding(batch: Batch, b: BuildingState, pos: Vector2, n: Long) {
        val w = buildingWidth(b)
        val base = Vector2(pos.x, pos.y - 34f)
        if (b.level == 0 && b.type != "castle" && b.busy) {
            drawRegion(batch, assets["effects/fx_build1"], base, w * 0.9f)
        } else {
            val ruined = b.type == "castle" && b.level == 0
            drawRegion(batch, buildingRegion(b), base, w, alpha = if (ruined && !b.busy) 0.9f else 1f,
                tint = if (ruined) Color(0.62f, 0.58f, 0.55f, 1f) else null)
        }
        val top = Vector2(pos.x, pos.y + w * 0.78f)
        if (b.busy) {
            val total = (b.busyUntil - b.busyStarted).coerceAtLeast(1)
            val f = ((n - b.busyStarted).toFloat() / total).coerceIn(0f, 1f)
            val s = toScreen(top)
            val bw = 120f * cam.zoom.coerceAtLeast(0.6f)
            theme.patch("bar").draw(batch, s.x - bw / 2, s.y, bw, 20f)
            theme.patch("barGold").draw(batch, s.x - bw / 2 + 2f, s.y + 2f, maxOf(10f, (bw - 4f) * f), 16f)
            text(batch, theme.small, fmtTime((b.busyUntil - n) / 1000), s.x, s.y + 38f, Color.WHITE, outline = true)
            if (b.level > 0) drawRegion(batch, assets["effects/fx_up0"], Vector2(top.x + w * 0.42f, top.y - 40f + sin(t * 4f) * 8f), 46f, anchorBottom = false)
        } else if (b.level > 0) {
            val s = toScreen(Vector2(pos.x + w * 0.38f, pos.y + 6f))
            batch.setColor(Color.WHITE)
            batch.draw(theme.skin.getRegion("circle"), s.x - 16f, s.y - 16f, 32f, 32f)
            batch.setColor(com.kayipadalar.game.ui.Palette.navy)
            batch.draw(theme.skin.getRegion("circle"), s.x - 13f, s.y - 13f, 26f, 26f)
            text(batch, theme.tiny, b.level.toString(), s.x, s.y + 1f, Color.WHITE)
        }
        if (showBubble(b)) {
            val p = Vector2(pos.x, pos.y + bubbleHeight(b))
            val s = toScreen(p)
            val r = 46f * cam.zoom.coerceAtLeast(0.7f)
            batch.setColor(1f, 1f, 1f, 0.95f)
            batch.draw(theme.skin.getRegion("circle"), s.x - r, s.y - r, r * 2, r * 2)
            val res = Res.valueOf(k.cfg.building(b.type).produces!!)
            batch.draw(assets[res.icon], s.x - r * 0.7f, s.y - r * 0.7f, r * 1.4f, r * 1.4f)
        }
        batch.setColor(Color.WHITE)
    }

    private fun fmtTime(sec: Long): String = if (sec >= 60) "${sec / 60}dk ${sec % 60}sn" else "${sec.coerceAtLeast(0)}sn"

    private fun text(batch: Batch, font: BitmapFont, s: String, cx: Float, cy: Float, c: Color, outline: Boolean = false) {
        if (outline) {
            font.color = Color(0f, 0f, 0f, c.a * 0.7f)
            layout.setText(font, s)
            for ((dx, dy) in listOf(-2f to 0f, 2f to 0f, 0f to -2f, 0f to 2f)) font.draw(batch, layout, cx - layout.width / 2 + dx, cy + layout.height / 2 + dy)
        }
        font.color = c
        layout.setText(font, s)
        font.draw(batch, layout, cx - layout.width / 2, cy + layout.height / 2)
        font.color = Color.WHITE
    }
}
