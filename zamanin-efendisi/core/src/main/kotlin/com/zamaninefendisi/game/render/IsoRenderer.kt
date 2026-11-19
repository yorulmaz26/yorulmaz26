package com.zamaninefendisi.game.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.zamaninefendisi.game.ui.Theme
import com.zamaninefendisi.logic.Era
import com.zamaninefendisi.logic.Feature
import com.zamaninefendisi.logic.LevelSession
import com.zamaninefendisi.logic.Pos
import com.zamaninefendisi.logic.Terrain
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** İzometrik yerleşim: hücre (x,y) → ekran merkezi ve dokunma → hücre. */
class IsoLayout(val ox: Float, val oy: Float, val tw: Float) {
    val th = tw / 2f
    val thick = tw * 0.16f

    fun cx(x: Float, y: Float) = ox + (x - y) * tw / 2f
    fun cy(x: Float, y: Float) = oy - (x + y) * th / 2f

    fun pick(wx: Float, wy: Float): Pos {
        val a = (wx - ox) / (tw / 2f)
        val b = (oy - wy) / (th / 2f)
        return Pos(((a + b) / 2f).roundToInt(), ((b - a) / 2f).roundToInt())
    }

    companion object {
        /** Haritayı verilen dikdörtgene sığdırır. */
        fun fit(w: Int, h: Int, rx: Float, ry: Float, rw: Float, rh: Float): IsoLayout {
            val n = (w + h).toFloat()
            val tw = minOf(rw * 0.97f / (n / 2f), rh / (n / 4f + 1.05f), 150f)
            val th = tw / 2f
            val thick = tw * 0.16f
            val ox = rx + rw / 2f - (w - h) * tw / 4f
            val head = tw * 0.85f
            val top = th / 2f + head
            val bottom = (n - 2f) * th / 2f + th / 2f + thick
            val oy = ry + rh / 2f - (top - bottom) / 2f
            return IsoLayout(ox, oy, tw)
        }
    }
}

/** Çizimle ilgili animasyon durumu (oyun mantığından bağımsız; yalnızca görsel). */
class WorldAnim {
    var time = 0f
    val pops = HashMap<Pos, Float>()
    var transition = 1f
    var transitionFrom: Pos = Pos(0, 0)
    var heroX = 0f; var heroY = 0f
    val enemyVis = HashMap<Int, FloatArray>()
    val unitVis = HashMap<Int, FloatArray>()
    /** Atış animasyonları: x1, y1, x2, y2, kalan süre. */
    val shots = ArrayList<FloatArray>()
    var selectedUnit: Int = -1
    var reach: Set<Pos> = emptySet()
    var targets: Set<Pos> = emptySet()
    val fading = ArrayList<FloatArray>() // x, y, life
    var selected: Pos? = null
    var shake = 0f

    fun update(dt: Float, s: LevelSession) {
        time += dt
        val it = pops.entries.iterator()
        while (it.hasNext()) { val e = it.next(); e.setValue(e.value - dt); if (e.value <= 0f) it.remove() }
        if (transition < 1f) transition = (transition + dt / 0.55f).coerceAtMost(1f)
        val k = (dt * 14f).coerceAtMost(1f)
        heroX += (s.heroPos.x - heroX) * k
        heroY += (s.heroPos.y - heroY) * k
        val alive = HashSet<Int>()
        for (e in s.enemies) {
            alive += e.id
            val v = enemyVis.getOrPut(e.id) { floatArrayOf(e.pos.x.toFloat(), e.pos.y.toFloat()) }
            v[0] += (e.pos.x - v[0]) * k; v[1] += (e.pos.y - v[1]) * k
        }
        enemyVis.keys.retainAll(alive)
        val aliveU = HashSet<Int>()
        for (u in s.units) {
            aliveU += u.id
            val v = unitVis.getOrPut(u.id) { floatArrayOf(u.pos.x.toFloat(), u.pos.y.toFloat()) }
            v[0] += (u.pos.x - v[0]) * k; v[1] += (u.pos.y - v[1]) * k
        }
        unitVis.keys.retainAll(aliveU)
        shots.removeAll { it[4] -= dt; it[4] <= 0f }
        fading.removeAll { it[2] -= dt; it[2] <= 0f }
        if (shake > 0f) shake = (shake - dt).coerceAtLeast(0f)
    }

    fun snap(s: LevelSession) {
        heroX = s.heroPos.x.toFloat(); heroY = s.heroPos.y.toFloat()
        enemyVis.clear()
        for (e in s.enemies) enemyVis[e.id] = floatArrayOf(e.pos.x.toFloat(), e.pos.y.toFloat())
        unitVis.clear()
        for (u in s.units) unitVis[u.id] = floatArrayOf(u.pos.x.toFloat(), u.pos.y.toFloat())
    }

    fun startTransition(from: Pos) { transition = 0f; transitionFrom = from }
}

/** Renk paleti: her çağ farklı fakat hepsi aydınlık. */
object Palette {
    private fun c(h: String) = Color.valueOf(h)
    class TileColors(val top: Color, val left: Color, val right: Color, val detail: Color)

    private val cache = HashMap<String, TileColors>()

    fun tile(era: Era, t: Terrain): TileColors = cache.getOrPut("$era$t") {
        val soilL = if (era == Era.FUTURE) c("BCCBE6") else c("B88757")
        val soilR = if (era == Era.FUTURE) c("D6E0F4") else c("CC9C69")
        when (t) {
            Terrain.GRASS -> when (era) {
                Era.PAST -> TileColors(c("8FDC6B"), soilL, soilR, c("74C552"))
                Era.PRESENT -> TileColors(c("80CF5F"), soilL, soilR, c("67B748"))
                Era.FUTURE -> TileColors(c("93E7CB"), soilL, soilR, c("74D4B4"))
            }
            Terrain.SAND -> TileColors(if (era == Era.FUTURE) c("F7E9C0") else c("F2D88E"), c("D2AE62"), c("E2C27A"), c("E3C473"))
            Terrain.WATER -> when (era) {
                Era.PAST -> TileColors(c("77CDF3"), c("4E9FD0"), c("5EB2E0"), c("B9E7FB"))
                Era.PRESENT -> TileColors(c("55B7EB"), c("3B8FC6"), c("4AA3D8"), c("A8DDF8"))
                Era.FUTURE -> TileColors(c("8ADFFF"), c("5BBDE8"), c("6FCDF3"), c("E1F8FF"))
            }
            Terrain.MOUNTAIN -> when (era) {
                Era.FUTURE -> TileColors(c("CDD0EC"), c("A4A8CF"), c("B7BBDD"), c("FFFFFF"))
                else -> TileColors(c("A9A08F"), c("857B69"), c("978D7B"), c("FFFFFF"))
            }
            Terrain.ROAD -> TileColors(if (era == Era.FUTURE) c("E6EBF8") else c("DDBF91"), soilL, soilR, c("C9A774"))
        }
    }
}

/**
 * İzometrik dünya çizimi (ShapeRenderer, dolu mod). Bütün nesneler kodla çizilir; gerçek
 * oyun durumundan (CausalityEngine ızgaraları) okunur, böylece görsel değişim oyun mantığıyla bire birdir.
 */
class IsoRenderer(private val sr: ShapeRenderer) {
    private lateinit var L: IsoLayout
    private var era: Era = Era.PAST
    private val tmp = Color()

    fun draw(s: LevelSession, viewEra: Era, layout: IsoLayout, anim: WorldAnim, markers: Set<Pos>, protect: Set<Pos>) {
        L = layout
        era = viewEra
        val g = s.timeline.grid(viewEra)
        val w = g.width; val h = g.height
        val adj = if (anim.transition >= 1f && s.heroEra == viewEra) s.heroPos.neighbors4().filter { s.canWalk(viewEra, it) }.toSet() else emptySet()
        for (sum in 0..(w + h - 2)) {
            for (x in 0 until w) {
                val y = sum - x
                if (y !in 0 until h) continue
                val p = Pos(x, y)
                val cell = g[p]
                // Çağ geçişinde hücreler kahramandan dalga hâlinde belirir.
                var k = 1f
                if (anim.transition < 1f) {
                    val d = p.manhattan(anim.transitionFrom) * 0.045f
                    k = Interpolation.swingOut.apply(((anim.transition * 0.55f - d) / 0.3f).coerceIn(0f, 1f))
                }
                if (k <= 0.01f) continue
                val pop = anim.pops[p]?.let { 1f + 0.25f * sin((0.35f - it) / 0.35f * MathUtils.PI) } ?: 1f
                val cx = L.cx(x.toFloat(), y.toFloat())
                val cy = L.cy(x.toFloat(), y.toFloat()) - (1f - k) * L.tw * 0.6f
                drawTile(cx, cy, cell.terrain, p, anim.time, k)
                if (p in adj) diamondOutline(cx, cy + lift(cell.terrain), 0.86f, Color(1f, 1f, 1f, 0.55f), 2.5f)
                if (p == anim.selected) diamondOutline(cx, cy + lift(cell.terrain), 0.95f, Color(1f, 0.85f, 0.2f, 1f), 4f)
                if (p in protect) diamondOutline(cx, cy + lift(cell.terrain), 0.7f, Color(0.55f, 0.42f, 0.88f, 0.9f), 3f)
                if (p in anim.reach) diamondOutline(cx, cy + lift(cell.terrain), 0.8f, Color(0.25f, 0.55f, 0.95f, 0.95f), 3.5f)
                if (p in anim.targets) diamondOutline(cx, cy + lift(cell.terrain), 0.8f, Color(0.92f, 0.3f, 0.3f, 0.95f), 3.5f)
                drawFeature(cx, cy + lift(cell.terrain), cell.feature, cell.level, cell.durability, p, anim.time, k * pop)
                if (cell.frozen) drawFrost(cx, cy + lift(cell.terrain), anim.time + p.x)
                if (p in markers) drawFlag(cx, cy, anim.time)
                // Bu hücredeki varlıklar
                if (viewEra == s.level.enemyEra) {
                    for (e in s.enemies) {
                        val v = anim.enemyVis[e.id] ?: continue
                        if (v[0].roundToInt() == x && v[1].roundToInt() == y) {
                            val ex = L.cx(v[0], v[1]); val ey = L.cy(v[0], v[1])
                            drawEnemy(e.kind, ex, ey, anim.time + e.id, k, e.frozenTurns > 0)
                            if (e.kind.hp > 1) hpBar(ex, ey + L.tw * 0.66f, e.hp, e.kind.hp, C.enemy)
                        }
                    }
                    for (u in s.units) {
                        val v = anim.unitVis[u.id] ?: continue
                        if (v[0].roundToInt() == x && v[1].roundToInt() == y) {
                            val ux = L.cx(v[0], v[1]); val uy = L.cy(v[0], v[1])
                            if (u.id == anim.selectedUnit) { sr.color = tmp.set(1f, 0.85f, 0.2f, 0.9f); sr.ellipse(ux - L.tw * 0.2f, uy - L.tw * 0.08f, L.tw * 0.4f, L.tw * 0.16f, 18) }
                            drawUnit(u.type, ux, uy, anim.time + u.id, k)
                            hpBar(ux, uy + L.tw * 0.66f, u.hp, u.type.hp, C.ally)
                        }
                    }
                }
                if (s.heroEra == viewEra && anim.heroX.roundToInt() == x && anim.heroY.roundToInt() == y) {
                    val hx = L.cx(anim.heroX, anim.heroY); val hy = L.cy(anim.heroX, anim.heroY)
                    drawHero(hx, hy + lift(g[s.heroPos].terrain).coerceAtLeast(0f), anim.time, k, s.heroEra)
                }
            }
        }
        for (sh in anim.shots) {
            val t = 1f - sh[4] / 0.35f
            val x1 = L.cx(sh[0], sh[1]); val y1 = L.cy(sh[0], sh[1]) + L.tw * 0.35f
            val x2 = L.cx(sh[2], sh[3]); val y2 = L.cy(sh[2], sh[3]) + L.tw * 0.3f
            sr.color = tmp.set(1f, 0.95f, 0.6f, 0.5f * (1f - t))
            sr.rectLine(x1, y1, x2, y2, 2f)
            sr.color = tmp.set(1f, 0.8f, 0.2f, 1f)
            sr.circle(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, L.tw * 0.04f, 10)
        }
        for (f in anim.fading) {
            val a = f[2] / 0.6f
            sr.color = tmp.set(1f, 1f, 1f, a * 0.8f)
            sr.circle(L.cx(f[0], f[1]), L.cy(f[0], f[1]) + L.tw * 0.25f, L.tw * 0.35f * (1.6f - a), 20)
        }
    }

    private fun lift(t: Terrain) = if (t == Terrain.WATER) -L.thick * 0.35f else 0f

    // ------------------------------------------------------------ Zemin

    private fun drawTile(cx: Float, cy: Float, t: Terrain, p: Pos, time: Float, k: Float) {
        val col = Palette.tile(era, t)
        val hw = L.tw / 2f * k; val hh = L.th / 2f * k
        val top = cy + lift(t)
        val th = L.thick * k
        // Yan yüzler (yüzen ada görünümü)
        quad(cx - hw, cy, cx, cy - hh, cx, cy - hh - th, cx - hw, cy - th, col.left)
        quad(cx, cy - hh, cx + hw, cy, cx + hw, cy - th, cx, cy - hh - th, col.right)
        if (t == Terrain.WATER) {
            quad(cx - hw, cy, cx, cy - hh, cx, top - hh, cx - hw, top, Theme2.waterEdge)
            quad(cx, cy - hh, cx + hw, cy, cx + hw, top, cx, top - hh, Theme2.waterEdge2)
        }
        diamond(cx, top, hw, hh, col.top)
        val seed = p.x * 73856093 xor p.y * 19349663
        when (t) {
            Terrain.GRASS -> {
                sr.color = col.detail
                for (i in 0..2) {
                    val rx = ((seed shr (i * 5)) and 31) / 31f - 0.5f
                    val ry = ((seed shr (i * 5 + 3)) and 31) / 31f - 0.5f
                    sr.ellipse(cx + rx * hw * 0.9f - 5f, top + ry * hh * 0.9f - 2f, 10f * k, 5f * k, 8)
                }
                if (era == Era.FUTURE) { sr.color = tmp.set(1f, 1f, 1f, 0.35f); sr.rectLine(cx - hw * 0.5f, top, cx, top + hh * 0.5f, 2f) }
            }
            Terrain.SAND -> {
                sr.color = col.detail
                for (i in 0..3) {
                    val rx = ((seed shr (i * 4)) and 15) / 15f - 0.5f
                    val ry = ((seed shr (i * 4 + 2)) and 15) / 15f - 0.5f
                    sr.circle(cx + rx * hw, top + ry * hh, 2.5f * k, 6)
                }
            }
            Terrain.WATER -> {
                sr.color = col.detail
                val o = sin(time * 2f + p.x + p.y) * hw * 0.15f
                sr.rectLine(cx - hw * 0.35f + o, top + hh * 0.1f, cx - hw * 0.05f + o, top + hh * 0.25f, 2.5f)
                sr.rectLine(cx + hw * 0.05f - o, top - hh * 0.25f, cx + hw * 0.35f - o, top - hh * 0.1f, 2.5f)
            }
            Terrain.MOUNTAIN -> {
                val mh = L.tw * 0.55f * k
                tri(cx - hw * 0.8f, top, cx, top - hh * 0.8f, cx, top + mh, Theme.darker(col.top, 0.85f))
                tri(cx, top - hh * 0.8f, cx + hw * 0.8f, top, cx, top + mh, col.top)
                tri(cx - hw * 0.25f, top + mh * 0.68f, cx + hw * 0.25f, top + mh * 0.68f, cx, top + mh, col.detail)
            }
            Terrain.ROAD -> {
                sr.color = col.detail
                sr.rectLine(cx - hw * 0.5f, top + hh * 0.5f, cx + hw * 0.5f, top - hh * 0.5f, 3f)
            }
        }
    }

    // ------------------------------------------------------------ Nesneler

    private fun drawFeature(cx: Float, cy: Float, f: Feature, level: Int, durability: Int, p: Pos, time: Float, k: Float) {
        val u = L.tw * k
        when (f) {
            Feature.NONE -> {}
            Feature.SAPLING -> {
                sr.color = C.trunk; sr.rectLine(cx, cy, cx, cy + u * 0.22f, 4f)
                sr.color = C.leaf; sr.circle(cx - u * 0.07f, cy + u * 0.2f, u * 0.08f, 12); sr.circle(cx + u * 0.07f, cy + u * 0.24f, u * 0.08f, 12)
                sr.color = C.leafLight; sr.circle(cx, cy + u * 0.3f, u * 0.07f, 12)
            }
            Feature.TREE -> tree(cx, cy, u, if (era == Era.FUTURE) C.leafFuture else C.leaf)
            Feature.FOREST -> {
                tree(cx - u * 0.16f, cy + u * 0.04f, u * 0.8f, C.leafDark)
                tree(cx + u * 0.16f, cy + u * 0.04f, u * 0.8f, C.leaf)
                tree(cx, cy - u * 0.06f, u * 0.9f, C.leafLight)
            }
            Feature.STUMP -> {
                sr.color = C.trunk; sr.ellipse(cx - u * 0.08f, cy - u * 0.02f, u * 0.16f, u * 0.1f, 12)
                sr.rect(cx - u * 0.08f, cy + u * 0.02f, u * 0.16f, u * 0.05f)
                sr.color = C.wood; sr.ellipse(cx - u * 0.08f, cy + u * 0.03f, u * 0.16f, u * 0.08f, 12)
            }
            Feature.FIELD -> {
                diamond(cx, cy, u * 0.36f, u * 0.18f, C.field)
                sr.color = C.fieldLine
                for (i in -1..1) sr.rectLine(cx - u * 0.2f + i * u * 0.1f, cy - u * 0.05f + i * u * 0.05f, cx + u * 0.0f + i * u * 0.1f, cy + u * 0.05f + i * u * 0.05f, 2f)
            }
            Feature.BRIDGE_WOOD -> {
                box(cx, cy + 2f, 0.78f, 0.5f, u * 0.06f, C.wood, C.trunk, C.woodDark)
                sr.color = C.trunk
                for (i in -2..2) sr.rectLine(cx - u * 0.16f + i * u * 0.07f, cy + u * 0.05f + i * u * 0.035f + 2f, cx + u * 0.04f + i * u * 0.07f, cy + u * 0.15f + i * u * 0.035f + 2f, 1.5f)
            }
            Feature.BRIDGE_STONE -> {
                box(cx, cy + 2f, 0.84f, 0.56f, u * 0.09f, C.stone, C.stoneDark, C.stoneMid)
                sr.color = C.stoneDark; sr.circle(cx - u * 0.12f, cy - u * 0.02f, u * 0.05f, 10)
            }
            Feature.BRIDGE_TECH -> {
                val glow = 0.6f + 0.4f * sin(time * 3f)
                box(cx, cy + 2f, 0.84f, 0.5f, u * 0.05f, C.techWhite, C.techBlue, C.techLight)
                sr.color = tmp.set(0.3f, 0.85f, 1f, glow); sr.rectLine(cx - u * 0.3f, cy + u * 0.1f, cx + u * 0.3f, cy - u * 0.06f, 3f)
            }
            Feature.HUT -> house(cx, cy, u * 0.9f, C.hutWall, C.roofRed)
            Feature.TOWN -> {
                house(cx - u * 0.14f, cy + u * 0.05f, u * 0.8f, C.townWall, C.roofBlue)
                house(cx + u * 0.14f, cy + u * 0.03f, u * 0.75f, C.hutWall, C.roofRed)
                house(cx, cy - u * 0.08f, u * 0.85f, C.townWall, C.roofOrange)
            }
            Feature.CITY -> {
                tower(cx - u * 0.15f, cy + u * 0.04f, u, 0.55f, C.cityA)
                tower(cx + u * 0.15f, cy + u * 0.02f, u, 0.75f, C.cityB)
                tower(cx, cy - u * 0.1f, u, 0.95f, C.cityC)
            }
            Feature.RUINS, Feature.ENEMY_RUINS -> {
                box(cx - u * 0.1f, cy, 0.22f, 0.22f, u * 0.14f, C.stone, C.stoneDark, C.stoneMid)
                box(cx + u * 0.12f, cy - u * 0.03f, 0.18f, 0.18f, u * 0.07f, C.stone, C.stoneDark, C.stoneMid)
            }
            Feature.PALISADE -> {
                val dmg = durability < f.baseDurability
                for (i in -2..2) {
                    val px = cx + i * u * 0.09f; val py = cy - i * u * 0.045f
                    box(px, py, 0.12f, 0.12f, u * (if (dmg && i % 2 == 0) 0.14f else 0.28f), C.wood, C.trunk, C.woodDark)
                }
            }
            Feature.STONE_WALL -> {
                box(cx, cy, 0.8f, 0.34f, u * 0.3f, C.stone, C.stoneDark, C.stoneMid)
                for (i in -1..1) box(cx + i * u * 0.2f, cy - i * u * 0.1f + u * 0.3f, 0.14f, 0.14f, u * 0.08f, C.stone, C.stoneDark, C.stoneMid)
            }
            Feature.ENERGY_WALL -> {
                val a = 0.55f + 0.25f * sin(time * 4f + p.x)
                box(cx, cy, 0.8f, 0.2f, u * 0.08f, C.techWhite, C.techBlue, C.techLight)
                sr.color = tmp.set(0.35f, 0.85f, 1f, a)
                quad(cx - u * 0.36f, cy + u * 0.1f, cx + u * 0.36f, cy - u * 0.08f, cx + u * 0.36f, cy + u * 0.4f, cx - u * 0.36f, cy + u * 0.58f, sr.color)
            }
            Feature.CASTLE -> castle(cx, cy, u, time)
            Feature.ENEMY_CAMP -> {
                tri(cx - u * 0.22f, cy - u * 0.02f, cx + u * 0.05f, cy - u * 0.1f, cx - u * 0.05f, cy + u * 0.3f, C.enemyDark)
                tri(cx + u * 0.05f, cy - u * 0.1f, cx + u * 0.2f, cy + u * 0.02f, cx - u * 0.05f, cy + u * 0.3f, C.enemy)
                flagPole(cx + u * 0.18f, cy + u * 0.05f, u * 0.35f, C.enemy, time)
            }
            Feature.ENEMY_FORT -> {
                box(cx, cy, 0.7f, 0.7f, u * 0.32f, C.enemyStone, C.enemyDark, C.enemyStoneDark)
                box(cx - u * 0.2f, cy + u * 0.02f, 0.2f, 0.2f, u * 0.5f, C.enemyStone, C.enemyDark, C.enemyStoneDark)
                box(cx + u * 0.2f, cy + u * 0.02f, 0.2f, 0.2f, u * 0.5f, C.enemyStone, C.enemyDark, C.enemyStoneDark)
                flagPole(cx, cy + u * 0.32f, u * 0.32f, C.enemy, time)
            }
            Feature.WATCHTOWER -> {
                box(cx, cy, 0.3f, 0.3f, u * 0.5f, C.wood, C.trunk, C.woodDark)
                tri(cx - u * 0.2f, cy + u * 0.5f, cx, cy + u * 0.42f, cx, cy + u * 0.72f, Theme.darker(C.roofRed, 0.85f))
                tri(cx, cy + u * 0.42f, cx + u * 0.2f, cy + u * 0.5f, cx, cy + u * 0.72f, C.roofRed)
            }
            Feature.STONE_TOWER -> {
                box(cx, cy, 0.36f, 0.36f, u * 0.6f, C.stone, C.stoneDark, C.stoneMid)
                for (i in -1..1) box(cx + i * u * 0.09f, cy - i * u * 0.045f + u * 0.6f, 0.1f, 0.1f, u * 0.08f, C.stone, C.stoneDark, C.stoneMid)
                flagPole(cx, cy + u * 0.68f, u * 0.25f, Theme.blue, time)
            }
            Feature.ENERGY_TURRET -> {
                box(cx, cy, 0.34f, 0.34f, u * 0.4f, C.techWhite, C.techBlue, C.techLight)
                val g = 0.6f + 0.4f * sin(time * 4f)
                sr.color = tmp.set(0.35f, 0.85f, 1f, g); sr.circle(cx, cy + u * 0.52f, u * 0.09f, 16)
                sr.color = tmp.set(1f, 1f, 1f, 0.9f); sr.circle(cx - u * 0.02f, cy + u * 0.54f, u * 0.03f, 8)
            }
            Feature.CRYSTAL -> {
                val fy = cy + u * 0.22f + sin(time * 2.5f + p.x) * u * 0.04f
                sr.color = tmp.set(0f, 0f, 0f, 0.12f); sr.ellipse(cx - u * 0.1f, cy - u * 0.03f, u * 0.2f, u * 0.07f, 12)
                tri(cx - u * 0.09f, fy, cx, fy - u * 0.14f, cx, fy + u * 0.16f, C.crystalDark)
                tri(cx, fy - u * 0.14f, cx + u * 0.09f, fy, cx, fy + u * 0.16f, C.crystal)
                sr.color = tmp.set(1f, 1f, 1f, 0.7f); sr.circle(cx - u * 0.02f, fy + u * 0.05f, u * 0.02f, 6)
            }
            Feature.SHRINE, Feature.SHRINE_ACTIVE -> {
                box(cx, cy, 0.34f, 0.34f, u * 0.32f, C.stone, C.stoneDark, C.stoneMid)
                val active = f == Feature.SHRINE_ACTIVE
                val glow = 0.7f + 0.3f * sin(time * 3f)
                sr.color = if (active) tmp.set(1f, 0.8f, 0.25f, glow) else tmp.set(0.55f, 0.7f, 0.95f, 0.9f)
                val gy = cy + u * 0.46f + sin(time * 2f) * u * 0.03f
                tri(cx - u * 0.08f, gy, cx + u * 0.08f, gy, cx, gy + u * 0.16f, sr.color)
                tri(cx - u * 0.08f, gy, cx + u * 0.08f, gy, cx, gy - u * 0.12f, sr.color)
            }
        }
        if (level >= 2 && f != Feature.NONE) {
            sr.color = C.levelDot
            for (i in 0 until minOf(level, 4)) sr.circle(cx - u * 0.3f + i * u * 0.06f, cy - u * 0.16f, u * 0.018f, 6)
        }
    }

    private fun tree(cx: Float, cy: Float, u: Float, leaf: Color) {
        box(cx, cy, 0.1f, 0.1f, u * 0.2f, C.trunk, C.woodDark, C.trunk)
        sr.color = Theme.darker(leaf, 0.85f); sr.circle(cx, cy + u * 0.3f, u * 0.17f, 16)
        sr.color = leaf; sr.circle(cx - u * 0.04f, cy + u * 0.34f, u * 0.14f, 16)
        sr.color = Theme.lighter(leaf, 0.3f); sr.circle(cx - u * 0.07f, cy + u * 0.38f, u * 0.06f, 12)
    }

    private fun house(cx: Float, cy: Float, u: Float, wall: Color, roof: Color) {
        val wh = u * 0.18f
        box(cx, cy, 0.34f, 0.34f, wh, wall, Theme.darker(wall, 0.8f), Theme.darker(wall, 0.9f))
        val hw = L.tw / 2f * 0.34f * (u / L.tw); val hh = hw / 2f
        val top = cy + wh
        val apex = top + u * 0.17f
        tri(cx - hw * 1.1f, top, cx, top - hh * 1.1f, cx, apex, Theme.darker(roof, 0.82f))
        tri(cx, top - hh * 1.1f, cx + hw * 1.1f, top, cx, apex, roof)
        sr.color = C.window; sr.rect(cx + hw * 0.3f, cy + wh * 0.3f, hw * 0.3f, wh * 0.35f)
    }

    private fun tower(cx: Float, cy: Float, u: Float, hf: Float, col: Color) {
        box(cx, cy, 0.22f, 0.22f, u * hf * 0.6f, col, Theme.darker(col, 0.82f), Theme.darker(col, 0.92f))
        sr.color = C.techLight; sr.rect(cx + u * 0.01f, cy + u * hf * 0.15f, u * 0.03f, u * hf * 0.35f)
    }

    private fun castle(cx: Float, cy: Float, u: Float, time: Float) {
        box(cx, cy, 0.72f, 0.72f, u * 0.3f, C.castle, C.castleDark, C.castleMid)
        box(cx - u * 0.24f, cy + u * 0.02f, 0.22f, 0.22f, u * 0.52f, C.castle, C.castleDark, C.castleMid)
        box(cx + u * 0.24f, cy + u * 0.02f, 0.22f, 0.22f, u * 0.52f, C.castle, C.castleDark, C.castleMid)
        box(cx, cy - u * 0.14f, 0.24f, 0.24f, u * 0.62f, C.castle, C.castleDark, C.castleMid)
        val glow = 0.75f + 0.25f * sin(time * 2.5f)
        sr.color = tmp.set(0.55f, 0.42f, 0.95f, glow); sr.circle(cx, cy + u * 0.68f, u * 0.07f, 16)
        sr.color = tmp.set(1f, 0.85f, 0.3f, 1f); sr.circle(cx, cy + u * 0.68f, u * 0.035f, 12)
    }

    private fun drawFlag(cx: Float, cy: Float, time: Float) {
        flagPole(cx - L.tw * 0.26f, cy + L.th * 0.05f, L.tw * 0.5f, Theme.gold, time)
    }

    private fun flagPole(x: Float, y: Float, h: Float, col: Color, time: Float) {
        sr.color = C.trunk; sr.rectLine(x, y, x, y + h, 3f)
        val wave = sin(time * 5f) * h * 0.04f
        tri(x, y + h, x, y + h * 0.68f, x + h * 0.42f, y + h * 0.84f + wave, col)
    }

    private fun drawHero(cx: Float, cy: Float, time: Float, k: Float, era: Era) {
        val u = L.tw * k
        val bob = sin(time * 4f) * u * 0.02f
        sr.color = tmp.set(0f, 0f, 0f, 0.18f); sr.ellipse(cx - u * 0.16f, cy - u * 0.06f, u * 0.32f, u * 0.13f, 16)
        val cloak = Theme.eraColor(era)
        tri(cx - u * 0.15f, cy, cx + u * 0.15f, cy, cx, cy + u * 0.42f + bob, Theme.darker(cloak, 0.85f))
        tri(cx, cy - u * 0.02f, cx + u * 0.15f, cy, cx, cy + u * 0.42f + bob, cloak)
        sr.color = C.skin; sr.circle(cx, cy + u * 0.46f + bob, u * 0.085f, 16)
        sr.color = C.hood; sr.arc(cx, cy + u * 0.47f + bob, u * 0.095f, 0f, 180f, 12)
        sr.color = Theme.gold
        tri(cx - u * 0.05f, cy + u * 0.26f + bob, cx + u * 0.05f, cy + u * 0.26f + bob, cx, cy + u * 0.2f + bob, Theme.gold)
        tri(cx - u * 0.05f, cy + u * 0.14f + bob, cx + u * 0.05f, cy + u * 0.14f + bob, cx, cy + u * 0.2f + bob, Theme.gold)
    }

    private fun hpBar(cx: Float, cy: Float, hp: Int, max: Int, col: Color) {
        val w = L.tw * 0.36f; val h = L.tw * 0.05f
        sr.color = tmp.set(1f, 1f, 1f, 0.9f); sr.rect(cx - w / 2 - 2f, cy - 2f, w + 4f, h + 4f)
        sr.color = tmp.set(0.85f, 0.83f, 0.9f, 1f); sr.rect(cx - w / 2, cy, w, h)
        sr.color = col; sr.rect(cx - w / 2, cy, w * (hp.coerceAtLeast(0).toFloat() / max), h)
    }

    private fun drawFrost(cx: Float, cy: Float, time: Float) {
        val a = 0.35f + 0.1f * sin(time * 2f)
        sr.color = tmp.set(0.75f, 0.92f, 1f, a)
        sr.triangle(cx - L.tw * 0.42f, cy, cx, cy + L.th * 0.42f, cx + L.tw * 0.42f, cy)
        sr.triangle(cx - L.tw * 0.42f, cy, cx, cy - L.th * 0.42f, cx + L.tw * 0.42f, cy)
        sr.color = tmp.set(1f, 1f, 1f, 0.9f)
        val r = L.tw * 0.12f; val sx = cx + L.tw * 0.26f; val sy = cy + L.tw * 0.32f
        for (i in 0 until 3) {
            val ang = i * MathUtils.PI / 3f
            sr.rectLine(sx - MathUtils.cos(ang) * r, sy - MathUtils.sin(ang) * r, sx + MathUtils.cos(ang) * r, sy + MathUtils.sin(ang) * r, 2f)
        }
    }

    private fun drawUnit(type: com.zamaninefendisi.logic.UnitType, cx: Float, cy: Float, time: Float, k: Float) {
        val u = L.tw * k
        val bob = sin(time * 3.5f) * u * 0.015f
        sr.color = tmp.set(0f, 0f, 0f, 0.18f); sr.ellipse(cx - u * 0.15f, cy - u * 0.05f, u * 0.3f, u * 0.12f, 14)
        val body = when (type) {
            com.zamaninefendisi.logic.UnitType.MAGE -> C.allyMage
            com.zamaninefendisi.logic.UnitType.MECH, com.zamaninefendisi.logic.UnitType.ROBOT, com.zamaninefendisi.logic.UnitType.ENERGY -> C.allyTech
            else -> C.ally
        }
        when (type) {
            com.zamaninefendisi.logic.UnitType.MECH, com.zamaninefendisi.logic.UnitType.ROBOT -> {
                box(cx, cy + u * 0.04f, if (type == com.zamaninefendisi.logic.UnitType.MECH) 0.4f else 0.3f, 0.3f, u * 0.28f, C.robotTop, body, C.robotMid)
                box(cx, cy + u * 0.32f + bob, 0.2f, 0.2f, u * 0.12f, C.robotTop, body, C.robotMid)
                sr.color = C.crystal; sr.circle(cx + u * 0.04f, cy + u * 0.38f + bob, u * 0.025f, 8)
            }
            com.zamaninefendisi.logic.UnitType.ENERGY -> {
                tri(cx - u * 0.12f, cy, cx + u * 0.12f, cy, cx, cy + u * 0.3f, body)
                val g = 0.6f + 0.4f * sin(time * 5f)
                sr.color = tmp.set(0.4f, 0.9f, 1f, g); sr.circle(cx, cy + u * 0.4f + bob, u * 0.09f, 14)
            }
            else -> {
                tri(cx - u * 0.14f, cy, cx + u * 0.14f, cy, cx, cy + u * 0.4f + bob, Theme.darker(body, 0.85f))
                tri(cx, cy - u * 0.02f, cx + u * 0.14f, cy, cx, cy + u * 0.4f + bob, body)
                sr.color = C.skin; sr.circle(cx, cy + u * 0.44f + bob, u * 0.075f, 14)
            }
        }
        when (type) {
            com.zamaninefendisi.logic.UnitType.KNIGHT -> {
                sr.color = C.stoneMid; sr.circle(cx - u * 0.13f, cy + u * 0.18f, u * 0.09f, 14)
                sr.color = C.techWhite; sr.circle(cx - u * 0.13f, cy + u * 0.18f, u * 0.05f, 10)
                sr.color = C.stoneDark; sr.arc(cx, cy + u * 0.45f + bob, u * 0.085f, 0f, 180f, 10)
            }
            com.zamaninefendisi.logic.UnitType.ARCHER -> {
                sr.color = C.trunk; sr.arc(cx + u * 0.12f, cy + u * 0.22f, u * 0.14f, -70f, 140f, 12)
                sr.color = C.ally; sr.circle(cx + u * 0.12f, cy + u * 0.22f, u * 0.1f, 10)
                sr.color = C.trunk; sr.rectLine(cx + u * 0.16f, cy + u * 0.09f, cx + u * 0.16f, cy + u * 0.35f, 1.5f)
            }
            com.zamaninefendisi.logic.UnitType.WARRIOR -> { sr.color = C.trunk; sr.rectLine(cx + u * 0.15f, cy + u * 0.05f, cx + u * 0.2f, cy + u * 0.35f, 4f) }
            com.zamaninefendisi.logic.UnitType.ENGINEER -> {
                box(cx + u * 0.17f, cy + u * 0.02f, 0.14f, 0.14f, u * 0.1f, C.wood, C.trunk, C.woodDark)
                sr.color = C.stoneDark; sr.rectLine(cx + u * 0.1f, cy + u * 0.28f, cx + u * 0.24f, cy + u * 0.36f, 3f)
            }
            com.zamaninefendisi.logic.UnitType.SOLDIER -> { sr.color = C.stoneDark; sr.rectLine(cx + u * 0.08f, cy + u * 0.25f, cx + u * 0.28f, cy + u * 0.3f, 3f) }
            com.zamaninefendisi.logic.UnitType.MAGE -> {
                tri(cx - u * 0.09f, cy + u * 0.48f + bob, cx + u * 0.09f, cy + u * 0.48f + bob, cx + u * 0.02f, cy + u * 0.66f + bob, C.allyMage)
                sr.color = C.trunk; sr.rectLine(cx + u * 0.16f, cy, cx + u * 0.16f, cy + u * 0.5f, 2f)
                sr.color = tmp.set(0.7f, 0.9f, 1f, 0.7f + 0.3f * sin(time * 4f)); sr.circle(cx + u * 0.16f, cy + u * 0.52f, u * 0.04f, 10)
            }
            else -> {}
        }
    }

    private fun drawEnemy(kind: com.zamaninefendisi.logic.EnemyKind, cx: Float, cy: Float, time: Float, k: Float, frozen: Boolean) {
        when (kind) {
            com.zamaninefendisi.logic.EnemyKind.ROBOT -> drawRobot(cx, cy, time, k)
            com.zamaninefendisi.logic.EnemyKind.TECHNICIAN -> {
                drawRobot(cx, cy, time, k)
                val u = L.tw * k
                sr.color = C.crystal; sr.rectLine(cx + u * 0.08f, cy + u * 0.44f, cx + u * 0.14f, cy + u * 0.6f, 2f); sr.circle(cx + u * 0.14f, cy + u * 0.61f, u * 0.025f, 8)
            }
            com.zamaninefendisi.logic.EnemyKind.BRUTE -> drawRaider(cx, cy, time, k * 1.25f, C.enemyDark, C.enemyStoneDark)
            com.zamaninefendisi.logic.EnemyKind.ARCHER -> {
                drawRaider(cx, cy, time, k, C.enemy, C.enemyDark, spear = false)
                val u = L.tw * k
                sr.color = C.trunk; sr.arc(cx + u * 0.12f, cy + u * 0.22f, u * 0.13f, -70f, 140f, 12)
                sr.color = C.enemy; sr.circle(cx + u * 0.12f, cy + u * 0.22f, u * 0.09f, 10)
            }
            com.zamaninefendisi.logic.EnemyKind.PLUNDERER -> {
                drawRaider(cx, cy, time, k, C.plunder, C.trunk, spear = false)
                val u = L.tw * k
                sr.color = C.field; sr.circle(cx - u * 0.14f, cy + u * 0.2f, u * 0.08f, 12)
            }
            com.zamaninefendisi.logic.EnemyKind.PHASER -> drawRaider(cx, cy, time, k, C.phaser, C.allyMage, spear = false)
            com.zamaninefendisi.logic.EnemyKind.SABOTEUR -> drawRaider(cx, cy, time, k, C.saboteur, C.ink, spear = false)
            else -> drawRaider(cx, cy, time, k)
        }
        if (frozen) {
            val u = L.tw * k
            sr.color = tmp.set(0.75f, 0.92f, 1f, 0.55f); sr.circle(cx, cy + u * 0.25f, u * 0.25f, 18)
        }
    }

    private fun drawRobot(cx: Float, cy: Float, time: Float, k: Float) {
        val u = L.tw * k
        val hover = sin(time * 3f) * u * 0.03f
        sr.color = tmp.set(0f, 0f, 0f, 0.18f); sr.ellipse(cx - u * 0.13f, cy - u * 0.05f, u * 0.26f, u * 0.1f, 14)
        box(cx, cy + u * 0.06f + hover, 0.32f, 0.32f, u * 0.24f, C.robotTop, C.robotDark, C.robotMid)
        box(cx, cy + u * 0.32f + hover, 0.22f, 0.22f, u * 0.12f, C.robotTop, C.robotDark, C.robotMid)
        val blink = if ((time * 1.3f) % 3f < 0.15f) 0.2f else 1f
        sr.color = tmp.set(1f, 0.35f, 0.35f, blink); sr.circle(cx + u * 0.04f, cy + u * 0.38f + hover, u * 0.025f, 8)
        sr.color = C.enemy; sr.rectLine(cx, cy + u * 0.44f + hover, cx, cy + u * 0.54f + hover, 2f)
        sr.circle(cx, cy + u * 0.55f + hover, u * 0.02f, 8)
    }

    private fun drawRaider(cx: Float, cy: Float, time: Float, k: Float, body: Color = C.enemy, head: Color = C.enemyDark, spear: Boolean = true) {
        val u = L.tw * k
        val bob = abs(sin(time * 5f)) * u * 0.02f
        sr.color = tmp.set(0f, 0f, 0f, 0.18f); sr.ellipse(cx - u * 0.14f, cy - u * 0.05f, u * 0.28f, u * 0.11f, 14)
        tri(cx - u * 0.13f, cy, cx + u * 0.13f, cy, cx, cy + u * 0.36f + bob, body)
        sr.color = head; sr.circle(cx, cy + u * 0.4f + bob, u * 0.075f, 14)
        if (spear) {
            sr.color = C.trunk; sr.rectLine(cx + u * 0.16f, cy - u * 0.02f, cx + u * 0.16f, cy + u * 0.5f, 2.5f)
            tri(cx + u * 0.13f, cy + u * 0.5f, cx + u * 0.19f, cy + u * 0.5f, cx + u * 0.16f, cy + u * 0.58f, C.stone)
        }
    }

    // ------------------------------------------------------------ İlkel şekiller

    /** Hücre merkezinde, hücre ölçeğinin [fx] x [fy] kesri kadar taban alanına sahip, [h] yüksekliğinde izometrik kutu. */
    private fun box(cx: Float, cy: Float, fx: Float, fy: Float, h: Float, top: Color, left: Color, right: Color) {
        val hx = L.tw / 2f * (fx + fy) / 2f
        val hy = L.th / 2f * (fx + fy) / 2f
        quad(cx - hx, cy, cx, cy - hy, cx, cy - hy + h, cx - hx, cy + h, left)
        quad(cx, cy - hy, cx + hx, cy, cx + hx, cy + h, cx, cy - hy + h, right)
        diamond(cx, cy + h, hx, hy, top)
    }

    private fun diamond(cx: Float, cy: Float, hw: Float, hh: Float, col: Color) {
        sr.color = col
        sr.triangle(cx - hw, cy, cx, cy + hh, cx + hw, cy)
        sr.triangle(cx - hw, cy, cx, cy - hh, cx + hw, cy)
    }

    private fun diamondOutline(cx: Float, cy: Float, f: Float, col: Color, width: Float) {
        val hw = L.tw / 2f * f; val hh = L.th / 2f * f
        sr.color = col
        sr.rectLine(cx - hw, cy, cx, cy + hh, width); sr.rectLine(cx, cy + hh, cx + hw, cy, width)
        sr.rectLine(cx + hw, cy, cx, cy - hh, width); sr.rectLine(cx, cy - hh, cx - hw, cy, width)
    }

    private fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, col: Color) {
        sr.color = col
        sr.triangle(x1, y1, x2, y2, x3, y3)
    }

    private fun quad(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float, col: Color) {
        sr.color = col
        sr.triangle(x1, y1, x2, y2, x3, y3)
        sr.triangle(x1, y1, x3, y3, x4, y4)
    }

    private object C {
        private fun c(h: String) = Color.valueOf(h)
        val trunk = c("8A5A36"); val wood = c("C98C55"); val woodDark = c("A26D3F")
        val leaf = c("4FB54A"); val leafLight = c("8EDB64"); val leafDark = c("3C9A44"); val leafFuture = c("4FCB9A")
        val field = c("F2C94C"); val fieldLine = c("D9A93A")
        val stone = c("D8D6E0"); val stoneMid = c("BEBBCB"); val stoneDark = c("A09CB2")
        val techWhite = c("F6FAFF"); val techBlue = c("A9C8EE"); val techLight = c("C9E9FF")
        val hutWall = c("F5DDB0"); val townWall = c("FFF1DB"); val roofRed = c("E2654F"); val roofBlue = c("5B9BE0"); val roofOrange = c("F0943C")
        val window = c("7BC4F0")
        val cityA = c("E9F3FF"); val cityB = c("D5E8FF"); val cityC = c("F4ECFF")
        val castle = c("F3EAFF"); val castleMid = c("DCCCF6"); val castleDark = c("BFA8EA")
        val enemy = c("E2574C"); val enemyDark = c("9E3A3A"); val enemyStone = c("C9A79A"); val enemyStoneDark = c("A88476")
        val skin = c("FFD7B0"); val hood = c("FFFFFF"); val levelDot = c("FFFFFF")
        val robotTop = c("F2F4FA"); val robotMid = c("C9CEDF"); val robotDark = c("9EA5BF")
        val crystal = c("7FE3F0"); val crystalDark = c("4FB8D9")
        val ally = c("4F8BE8"); val allyMage = c("9B6CE6"); val allyTech = c("6FA8F0")
        val plunder = c("C98A4E"); val phaser = c("C59BF0"); val saboteur = c("6E4A9E"); val ink = c("3B3355")
    }
}

private object Theme2 {
    val waterEdge = Color.valueOf("9FD8F2")
    val waterEdge2 = Color.valueOf("B5E3F7")
}

