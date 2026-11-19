package com.yeraltiimparatorlugu.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.yeraltiimparatorlugu.sim.Ant
import com.yeraltiimparatorlugu.sim.AntActivity
import com.yeraltiimparatorlugu.sim.AntType
import com.yeraltiimparatorlugu.sim.Biome
import com.yeraltiimparatorlugu.sim.BroodStage
import com.yeraltiimparatorlugu.sim.Creature
import com.yeraltiimparatorlugu.sim.CreatureKind
import com.yeraltiimparatorlugu.sim.Hash
import com.yeraltiimparatorlugu.sim.MarketItem
import com.yeraltiimparatorlugu.sim.MarketSlot
import com.yeraltiimparatorlugu.sim.OrderKind
import com.yeraltiimparatorlugu.sim.ResourceType
import com.yeraltiimparatorlugu.sim.Room
import com.yeraltiimparatorlugu.sim.RoomState
import com.yeraltiimparatorlugu.sim.RoomType
import com.yeraltiimparatorlugu.sim.Season
import com.yeraltiimparatorlugu.sim.Simulation
import com.yeraltiimparatorlugu.sim.StorageKind
import com.yeraltiimparatorlugu.sim.Terrain
import com.yeraltiimparatorlugu.sim.Weather
import kotlin.math.cos
import kotlin.math.sin

/**
 * Yeraltı kesit görünümü (dünya birimi = 1 hücre; hücre (x, y) ekranda (x, -y-1) dikdörtgeni).
 * Katmanlar: gökyüzü ve paralaks tepeler → yüzey → toprak karoları → yuvarlatılmış tünel kenarları ve gölgeler →
 * odalar (desen, ışık, eşya) → su → varlıklar → hava → ekran vinyeti.
 */
class WorldRenderer(private val art: Art, private val settings: Settings) {
    val camera = OrthographicCamera()
    private val tmp = Color()
    private val tmp2 = Color()
    private var anim = 0f
    private val screen = Matrix4()
    private var screenW = 1f
    private var screenH = 1f

    private val edgeTop = art.r("edge_top")
    private val edgeSide = art.r("edge_side")
    private val edgeBottom = art.r("edge_bottom")
    private val cornerFill = art.r("corner_fill")
    private val cornerRim = art.r("corner_rim")
    private val lipLight = art.r("lip_light")
    private val rimDark = art.r("rim_dark")
    private val water = art.r("water")
    private val waterTop = art.r("water_top")
    private val glow = art.r("glow")
    private val soft = art.r("soft")
    private val disc = art.r("disc")
    private val ring = art.r("ring")
    private val stripes = art.r("stripes")
    private val vignette = art.r("vignette")
    private val sky = art.r("sky")
    private val hillsFar = art.r("hills_far")
    private val hillsNear = art.r("hills_near")
    private val grass = art.r("grass_strip")
    private val snowStrip = art.r("snow_strip")
    private val sandStrip = art.r("sand_strip")
    private val patterns = mapOf("dots" to art.r("pattern_dots"), "stripes" to art.r("pattern_stripes"), "bricks" to art.r("pattern_bricks"))

    fun resize(w: Int, h: Int) {
        camera.viewportWidth = 12f
        camera.viewportHeight = 12f * h / w.coerceAtLeast(1)
        camera.update()
        screenW = w.toFloat(); screenH = h.toFloat()
        screen.setToOrtho2D(0f, 0f, screenW, screenH)
    }

    fun render(batch: SpriteBatch, sim: Simulation, view: ViewState, delta: Float) {
        anim += delta
        camera.update()
        batch.projectionMatrix = camera.combined
        batch.begin()
        val halfW = camera.viewportWidth * camera.zoom / 2f
        val halfH = camera.viewportHeight * camera.zoom / 2f
        val left = camera.position.x - halfW
        val right = camera.position.x + halfW
        val top = camera.position.y + halfH
        val bottom = camera.position.y - halfH
        val x0 = MathUtils.floor(left) - 1
        val x1 = MathUtils.ceil(right) + 1
        val y0 = MathUtils.floor(-top) - 1
        val y1 = MathUtils.ceil(-bottom) + 1

        drawSky(batch, sim, left, right, top)
        drawUnderground(batch, sim, x0, x1, maxOf(0, y0), y1)
        drawRooms(batch, sim, x0, x1, y0, y1)
        drawWater(batch, sim, x0, x1, maxOf(0, y0), y1)
        drawSurface(batch, sim, left, right, x0, x1)
        drawOrders(batch, sim, x0, x1, y0, y1)
        drawPiles(batch, sim, x0, x1, y0, y1)
        drawPheromones(batch, sim)
        drawCreatures(batch, sim, left - 2, right + 2, -top - 2, -bottom + 2)
        drawAnts(batch, sim, left - 2, right + 2, -top - 2, -bottom + 2)
        drawMilitaryMarkers(batch, sim)
        drawWeather(batch, sim, left, right, top)
        drawOverlay(batch, sim, view)
        // Ekran vinyeti: kenarları hafifçe karartır, görünümü toplar.
        if (!settings.lowQuality) {
            batch.projectionMatrix = screen
            batch.setColor(1f, 1f, 1f, 0.85f)
            batch.draw(vignette, 0f, 0f, screenW, screenH)
        }
        batch.setColor(Color.WHITE)
        batch.end()
    }

    private fun rect(batch: SpriteBatch, r: TextureRegion, x: Float, y: Float, w: Float, h: Float, c: Color = Color.WHITE, a: Float = c.a) {
        batch.setColor(c.r, c.g, c.b, a)
        batch.draw(r, x, y, w, h)
    }

    private fun cell(batch: SpriteBatch, r: TextureRegion, x: Int, y: Int, c: Color = Color.WHITE, a: Float = c.a) = rect(batch, r, x.toFloat(), -y - 1f, 1f, 1f, c, a)

    private fun bar(batch: SpriteBatch, x: Float, y: Float, w: Float, frac: Float, c: Color) {
        rect(batch, art.white, x - 0.02f, y - 0.02f, w + 0.04f, 0.13f, Palette.uiText, 0.55f)
        rect(batch, art.white, x, y, w * frac.coerceIn(0f, 1f), 0.09f, c, 1f)
    }

    // ---------------------------------------------------------------- Gökyüzü ve yüzey

    private fun drawSky(batch: SpriteBatch, sim: Simulation, left: Float, right: Float, top: Float) {
        if (top <= -0.5f) return
        val f = sim.dayFraction
        val w = sim.environment.weather
        val dusk = MathUtils.clamp(kotlin.math.abs(f - 0.5f) * 2.2f - 0.5f, 0f, 0.6f)
        tmp.set(Color.WHITE)
        when (w) {
            Weather.STORM -> tmp.set(0.72f, 0.78f, 0.84f, 1f)
            Weather.RAIN, Weather.CLOUDY -> tmp.set(0.85f, 0.89f, 0.93f, 1f)
            Weather.DROUGHT -> tmp.set(1f, 0.93f, 0.78f, 1f)
            else -> {}
        }
        tmp.lerp(1f, 0.82f, 0.68f, 1f, dusk * 0.5f)
        val skyTop = maxOf(top, 1f)
        rect(batch, sky, left - 1, 0f, right - left + 2, skyTop + 0.5f, tmp)
        if (!settings.lowQuality && (w == Weather.CLEAR || w == Weather.DROUGHT || w == Weather.CLOUDY)) {
            val sunX = MathUtils.lerp(left - 2f, right + 2f, f)
            val sunY = 5.5f + sin(f * MathUtils.PI) * 3.5f
            rect(batch, art.r("sun"), sunX - 2f, sunY - 2f, 4f, 4f)
        }
        // Paralaks tepeler
        drawParallax(batch, hillsFar, 0.55f, 8f, 2.5f, left, right, tmp)
        drawParallax(batch, hillsNear, 0.78f, 8f, 2f, left, right, tmp)
        if (!settings.lowQuality) {
            val cloud = art.r("cloud")
            val n = if (w == Weather.CLEAR || w == Weather.DROUGHT) 3 else 6
            val cTint = if (w == Weather.STORM) Palette.hex("D2DAE2") else Color.WHITE
            for (i in 0 until n) {
                val span = 70f
                val cx = ((i * 23.7f + anim * 0.3f) % span) + MathUtils.floor((camera.position.x - span / 2) / span) * span
                for (k in 0..1) rect(batch, cloud, cx + k * span, 4.2f + (i % 3) * 1.4f, 4.2f, 2.1f, cTint, 0.95f)
            }
        }
    }

    private fun drawParallax(batch: SpriteBatch, r: TextureRegion, factor: Float, tileW: Float, h: Float, left: Float, right: Float, tint: Color) {
        val offset = camera.position.x * (1f - factor)
        var x = MathUtils.floor((left - offset) / tileW) * tileW + offset
        while (x < right) { rect(batch, r, x, 0f, tileW + 0.02f, h, tint); x += tileW }
    }

    private fun variant(x: Int, y: Int) = ((Hash.mix(7, x, y, 1) ushr 3) % 3).toInt()

    private fun drawSurface(batch: SpriteBatch, sim: Simulation, left: Float, right: Float, x0: Int, x1: Int) {
        val season = sim.season
        val autumn = Palette.hex("E8C070")
        for (x in x0..x1) {
            val biome = Biome.at(sim.seed, x)
            val strip = when { season == Season.WINTER -> snowStrip; biome == Biome.DESERT -> sandStrip; else -> grass }
            val tint = if (season == Season.AUTUMN && strip === grass) autumn else Color.WHITE
            rect(batch, strip, x.toFloat(), -0.32f, 1.01f, 0.63f, tint)
        }
        if (!settings.lowQuality) for (x in x0..x1) {
            if (kotlin.math.abs(x) < 6) continue
            val biome = Biome.at(sim.seed, x)
            val d = Hash.unit(sim.seed, x, 0, 33)
            val winter = season == Season.WINTER
            when (biome) {
                Biome.FOREST -> if (d < 0.16) rect(batch, art.r(if (d < 0.08) "pine" else "tree"), x - 0.8f, 0.05f, 2.5f, 3.5f, if (season == Season.AUTUMN) autumn else if (winter) Palette.hex("E6EEF2") else Color.WHITE)
                Biome.ROCKY -> if (d < 0.2) rect(batch, art.r("rock"), x.toFloat(), 0.05f, 1.7f, 1f)
                Biome.DESERT -> if (d < 0.08) rect(batch, art.r("cactus"), x.toFloat(), 0.05f, 1.1f, 1.8f)
                Biome.SWAMP, Biome.POND -> { if (d < 0.08) rect(batch, art.r("pond"), x.toFloat(), -0.05f, 3.4f, 0.75f); else if (d < 0.22) rect(batch, art.r("reeds"), x.toFloat(), 0.05f, 0.9f, 1.7f) }
                Biome.FLOWERS -> if (d < 0.5 && !winter) rect(batch, art.r("flower_${(d * 6).toInt() % 3}"), x + 0.3f, 0.08f, 0.45f, 0.8f)
                Biome.HUMAN_RUINS -> if (d < 0.08) rect(batch, art.r("bricks"), x.toFloat(), 0.05f, 2f, 1.1f) else if (d < 0.13) rect(batch, art.r("can"), x.toFloat(), 0.05f, 1.2f, 1f)
                Biome.MEADOW -> if (d < 0.1 && !winter) rect(batch, art.r("flower_${(d * 30).toInt() % 3}"), x + 0.5f, 0.08f, 0.42f, 0.75f) else if (d in 0.1..0.15 && !winter) rect(batch, art.r("bush"), x.toFloat(), 0.05f, 1.7f, 1.1f)
            }
        }
        // Yuva girişi ve yüzeye atılan toprak
        val s = minOf(3.2f, 1.2f + kotlin.math.sqrt(sim.surfaceMound.toFloat()) * 0.1f)
        rect(batch, art.r("mound"), sim.dumpX + 0.5f - s, -0.05f, s * 2f, s * 0.7f)
        rect(batch, art.r("home_flag"), -0.9f, 0.1f, 0.7f, 1.1f)
        if (MarketItem.DECOR_GARDEN in sim.market.decorations) for (i in 0..3) rect(batch, art.r("flower_${i % 3}"), -3.8f + i * 0.55f, 0.08f, 0.45f, 0.8f)
        if (MarketItem.DECOR_LAMPS in sim.market.decorations) { rect(batch, art.r("lamp"), -1.9f, 0f, 0.8f, 0.8f); rect(batch, art.r("lamp"), 1.3f, 0f, 0.8f, 0.8f) }
        if (MarketItem.DECOR_STATUE in sim.market.decorations) rect(batch, art.r("statue"), -6.5f, 0.05f, 1.2f, 1.8f)
        for (r in sim.rivalSystem.rivals) {
            if (!r.alive || !r.discovered || r.x < x0 - 3 || r.x > x1 + 3) continue
            rect(batch, art.r("mound"), r.x - 2f, -0.05f, 4f, 1.4f, Palette.hex("E6C6A8"))
            rect(batch, art.r("rival_flag"), r.x - 0.2f, 0.6f, 0.8f, 1.3f)
        }
        for (bx in listOf(sim.knownMin(), sim.knownMax())) if (bx in x0..x1) rect(batch, art.r("flag"), bx.toFloat(), 0.1f, 0.55f, 0.85f, Palette.accent, 0.85f)
        for (src in sim.sources) {
            if (src.x < x0 || src.x > x1) continue
            val scale = 0.6f + 0.4f * (src.amount.toFloat() / src.maxAmount)
            rect(batch, art.sources[src.kind]!!, src.x + 0.5f - 0.75f * scale, -0.05f, 1.5f * scale, 1.5f * scale, Color.WHITE, if (sim.isKnown(src.x)) 1f else 0.55f)
        }
    }

    // ---------------------------------------------------------------- Yeraltı

    private fun depthTint(y: Int): Float = if (settings.lowQuality) 1f else 1f - minOf(0.18f, y / 700f)

    private fun solidColor(sim: Simulation, x: Int, y: Int): Color {
        if (y < 0) return Palette.grass
        return if (sim.world.isRevealed(x, y)) Palette.terrain(sim.world.get(x, y)) else Palette.unexplored
    }

    private fun drawUnderground(batch: SpriteBatch, sim: Simulation, x0: Int, x1: Int, y0: Int, y1: Int) {
        val w = sim.world
        for (y in y0..y1) {
            val dt = depthTint(y)
            tmp.set(dt, dt, dt, 1f)
            for (x in x0..x1) {
                val t = w.get(x, y)
                val open = t.walkable && !(t == Terrain.RIVAL_TUNNEL && !w.isRevealed(x, y))
                if (open) {
                    val room = sim.roomAt(x, y)
                    when {
                        room != null -> { val rc = roomColor(sim, room.type); tmp2.set(rc.r * dt, rc.g * dt, rc.b * dt, 1f); cell(batch, art.tunnel[variant(x, y)], x, y, tmp2) }
                        t == Terrain.RIVAL_TUNNEL -> cell(batch, art.tunnel[variant(x, y)], x, y, Palette.terrain(Terrain.RIVAL_TUNNEL))
                        else -> cell(batch, art.tunnel[variant(x, y)], x, y, tmp)
                    }
                } else {
                    val tex = if (w.isRevealed(x, y)) art.tiles[t]?.get(variant(x, y)) ?: art.unexplored[0] else art.unexplored[variant(x, y)]
                    cell(batch, tex, x, y, tmp)
                }
            }
        }
        // İkinci geçiş: açıklık kenarları (gölge, ışık dudağı, yuvarlak köşeler)
        if (settings.lowQuality) return
        for (y in y0..y1) for (x in x0..x1) {
            val open = isOpen(sim, x, y)
            val fx = x.toFloat()
            val top = -y.toFloat()
            if (open) {
                val upSolid = !isOpen(sim, x, y - 1)
                val downSolid = !isOpen(sim, x, y + 1)
                val leftSolid = !isOpen(sim, x - 1, y)
                val rightSolid = !isOpen(sim, x + 1, y)
                if (upSolid) rect(batch, edgeTop, fx, top - 0.32f, 1f, 0.32f)
                if (downSolid) rect(batch, edgeBottom, fx, top - 1f, 1f, 0.2f)
                if (leftSolid) rect(batch, edgeSide, fx, top - 1f, 0.22f, 1f)
                if (rightSolid) batch.draw(edgeSide.also { batch.setColor(Color.WHITE) }, fx + 1f, top - 1f, -0.22f, 1f)
                val cs = 0.4f
                if (upSolid && leftSolid) corner(batch, solidColor(sim, x, y - 1), fx, top, cs, false, false)
                if (upSolid && rightSolid) corner(batch, solidColor(sim, x, y - 1), fx + 1f, top, cs, true, false)
                if (downSolid && leftSolid) corner(batch, solidColor(sim, x, y + 1), fx, top - 1f, cs, false, true)
                if (downSolid && rightSolid) corner(batch, solidColor(sim, x, y + 1), fx + 1f, top - 1f, cs, true, true)
            } else if (y >= 0) {
                if (isOpen(sim, x, y + 1)) rect(batch, lipLight, fx, top - 1f, 1f, 0.16f)
                if (y > 0 && isOpen(sim, x, y - 1)) rect(batch, rimDark, fx, top - 0.1f, 1f, 0.1f)
            }
        }
    }

    private fun isOpen(sim: Simulation, x: Int, y: Int): Boolean {
        if (y < 0) return false
        val t = sim.world.get(x, y)
        return t.walkable && !(t == Terrain.RIVAL_TUNNEL && !sim.world.isRevealed(x, y))
    }

    /** Yuvarlak tünel köşesi: köşeye komşu toprağın renginde çeyrek dolgu. */
    private fun corner(batch: SpriteBatch, c: Color, x: Float, y: Float, s: Float, flipX: Boolean, flipY: Boolean) {
        // (x, y): köşenin hücre kenarındaki noktası. Çizim sol-üst köşe içindir; ayna ile diğer köşeler.
        val w = if (flipX) -s else s
        val h = if (flipY) -s else s
        val yy = if (flipY) y + s else y - s
        batch.setColor(c.r * 0.95f, c.g * 0.95f, c.b * 0.95f, 1f)
        batch.draw(cornerFill, x, yy, w, h)
        batch.setColor(1f, 0.96f, 0.86f, 0.5f)
        batch.draw(cornerRim, x, yy, w, h)
    }

    private fun roomColor(sim: Simulation, t: RoomType): Color {
        val base = Palette.room(t)
        return when (sim.market.equipped[MarketSlot.ROOM_STYLE]) {
            MarketItem.ROOMS_PASTEL -> tmp2.set(base).lerp(Color.WHITE, 0.35f)
            MarketItem.ROOMS_HONEY -> tmp2.set(base).lerp(Palette.hex("FFD27A"), 0.35f)
            else -> tmp2.set(base)
        }.let { Color(it) }
    }

    private fun pattern(t: RoomType): TextureRegion = when (t) {
        RoomType.QUEEN_CHAMBER, RoomType.EGG_ROOM, RoomType.LARVA_ROOM, RoomType.REST_ROOM, RoomType.INFIRMARY, RoomType.ROYAL_CENTER -> patterns["dots"]!!
        RoomType.BARRACKS, RoomType.DEFENSE_ROOM, RoomType.FORTRESS, RoomType.ELITE_QUARTERS, RoomType.TRAP_ROOM, RoomType.TRAINING_ROOM -> patterns["bricks"]!!
        else -> patterns["stripes"]!!
    }

    private fun drawRooms(batch: SpriteBatch, sim: Simulation, x0: Int, x1: Int, y0: Int, y1: Int) {
        for (r in sim.rooms) {
            if (r.x > x1 || r.x + r.width < x0 || r.y > y1 || r.y + r.height < y0) continue
            val rx = r.x.toFloat()
            val ry = -(r.y + r.height).toFloat()
            val pat = pattern(r.type)
            r.forEachCell { cx, cy -> if (sim.world.isWalkable(cx, cy)) cell(batch, pat, cx, cy, Color.WHITE, 0.5f) }
            if (r.functional && !settings.lowQuality) {
                val gs = maxOf(r.width, r.height) * 1.25f
                val flicker = 0.55f + sin(anim * 1.3f + r.id) * 0.04f
                rect(batch, glow, rx + r.width / 2f - gs / 2f, ry + r.height / 2f - gs / 2f + 0.3f, gs, gs, Color.WHITE, flicker)
            }
            if (!r.functional || r.state == RoomState.UPGRADING) {
                if (r.state != RoomState.UPGRADING) rect(batch, stripes, rx, ry, r.width.toFloat(), r.height.toFloat(), Palette.orange, 0.2f)
                val p = if (r.state == RoomState.EXCAVATING) excavationProgress(sim, r) else r.buildProgress / r.workRequired
                bar(batch, rx + 0.4f, ry + r.height / 2f, r.width - 0.8f, p, Palette.accent)
                rect(batch, art.icon(if (r.state == RoomState.EXCAVATING) "dig" else "build"), rx + r.width / 2f - 0.35f, ry + r.height / 2f + 0.2f, 0.7f, 0.7f, Palette.uiText, 0.75f)
            }
            if (r.functional) drawFurniture(batch, sim, r, rx, ry)
        }
    }

    private fun excavationProgress(sim: Simulation, r: Room): Float {
        var open = 0
        r.forEachCell { x, y -> if (sim.world.isWalkable(x, y)) open++ }
        return open.toFloat() / (r.width * r.height)
    }

    private fun drawFurniture(batch: SpriteBatch, sim: Simulation, r: Room, rx: Float, ry: Float) {
        val floor = ry + 0.05f
        val cx = rx + r.width / 2f
        fun put(name: String, x: Float, y: Float, w: Float, h: Float, c: Color = Color.WHITE) = rect(batch, art.r(name), x, y, w, h, c)
        fun res(t: ResourceType, x: Float, y: Float, s: Float) = rect(batch, art.icons[t]!!, x, y, s, s)
        when (r.type) {
            RoomType.QUEEN_CHAMBER -> put("queen_bed", cx - 1.6f, floor, 3.2f, 0.95f)
            RoomType.FOOD_STORAGE, RoomType.MATERIAL_STORAGE, RoomType.WATER_STORAGE -> {
                val kind = when (r.type) { RoomType.FOOD_STORAGE -> StorageKind.FOOD; RoomType.WATER_STORAGE -> StorageKind.WATER; else -> StorageKind.MATERIAL }
                val types = ResourceType.entries.filter { it.storage == kind && sim.resources[it] > 0 }
                val fill = sim.resources.totalOf(kind).toFloat() / sim.capacityOf(kind).coerceAtLeast(1)
                val shelfY = floor + r.height * 0.52f
                put("shelf", rx + 0.15f, shelfY, r.width - 0.3f, 0.38f)
                val perRow = (r.width * 2)
                val n = (fill * perRow * 2).toInt().coerceIn(if (types.isEmpty()) 0 else 1, perRow * 2)
                for (i in 0 until n) {
                    val t = types[i % types.size]
                    val onShelf = i >= perRow
                    res(t, rx + 0.08f + (i % perRow) * 0.48f, if (onShelf) shelfY + 0.22f else floor, 0.5f)
                }
            }
            RoomType.REST_ROOM, RoomType.BARRACKS, RoomType.ELITE_QUARTERS, RoomType.INFIRMARY -> {
                val tint = when (r.type) { RoomType.BARRACKS -> Palette.hex("F7C8C0"); RoomType.ELITE_QUARTERS -> Palette.hex("EBC8F0"); else -> Color.WHITE }
                val n = minOf(r.width - 1, 1 + r.level)
                for (i in 0 until n) put("bed", rx + 0.2f + i * (r.width - 0.4f) / n, floor, 1.1f, 0.55f, tint)
                if (r.type == RoomType.INFIRMARY) put("cross", cx - 0.35f, floor + 0.9f, 0.7f, 0.7f)
                if (r.type == RoomType.BARRACKS) put("banner", rx + r.width - 0.7f, floor + 0.4f, 0.5f, 0.75f)
            }
            RoomType.FUNGUS_FARM -> {
                val n = minOf(r.leafBuffer, r.width * 3)
                for (i in 0 until n) {
                    val grow = if (i == 0) 0.6f + r.growProgress * 0.4f else 1f
                    val s = 0.55f * grow
                    res(ResourceType.MUSHROOM, rx + 0.1f + (i % (r.width * 2)) * 0.48f, floor + (i / (r.width * 2)) * 0.6f, s)
                }
                if (r.leafBuffer == 0) rect(batch, art.icons[ResourceType.LEAF]!!, cx - 0.35f, floor + 0.6f, 0.7f, 0.7f, Color.WHITE, 0.4f)
            }
            RoomType.RESEARCH_LAB -> put("desk", cx - 1f, floor, 2f, 1f)
            RoomType.GENETICS_LAB -> { put("desk", cx - 1f, floor, 2f, 1f); res(ResourceType.GENETIC, cx + 0.6f, floor + 0.6f, 0.6f) }
            RoomType.ARCHIVE -> { put("scroll", rx + 0.2f, floor, 1f, 0.75f); put("scroll", rx + 1.3f, floor + 0.2f, 1f, 0.75f) }
            RoomType.WORKSHOP -> { put("anvil", cx - 0.6f, floor, 1.2f, 0.75f); r.recipe.output.keys.forEach { res(it, cx + 0.7f, floor, 0.6f) } }
            RoomType.TRAP_ROOM -> put("trap_spikes", rx + 0.1f, floor, r.width - 0.2f, 0.4f)
            RoomType.DEFENSE_ROOM, RoomType.FORTRESS -> { put("banner", rx + 0.3f, floor, 0.7f, 1.05f); put("banner", rx + r.width - 1f, floor, 0.7f, 1.05f) }
            RoomType.VENTILATION -> {
                batch.setColor(Color.WHITE)
                batch.draw(art.r("fan"), cx - 0.6f, floor + 0.7f, 0.6f, 0.6f, 1.2f, 1.2f, 1f, 1f, anim * 120f)
            }
            RoomType.HUMIDITY_CONTROL -> { res(ResourceType.WATER, cx - 0.4f, floor, 0.8f); batch.setColor(Color.WHITE); batch.draw(art.r("fan"), cx + 0.4f, floor + 0.6f, 0.3f, 0.3f, 0.6f, 0.6f, 1f, 1f, anim * 90f) }
            RoomType.EXPLORATION_CENTER -> put("globe", cx - 0.5f, floor, 1f, 1f)
            RoomType.TRADE_ROOM -> { res(ResourceType.FOOD, rx + 0.4f, floor, 0.6f); put("coin", cx - 0.3f, floor, 0.6f, 0.6f); res(ResourceType.MINERAL, rx + r.width - 1f, floor, 0.6f) }
            RoomType.FOSSIL_MUSEUM -> {
                val shown = sim.museum.displayed(sim).size
                for (i in 0 until minOf(shown, r.width - 1)) { put("pedestal", rx + 0.2f + i, floor, 0.75f, 0.75f); res(ResourceType.FOSSIL, rx + 0.3f + i, floor + 0.6f, 0.55f) }
                if (shown == 0) put("pedestal", cx - 0.4f, floor, 0.75f, 0.75f)
            }
            RoomType.ROYAL_CENTER -> { put("crown", cx - 0.6f, floor + 1.1f, 1.2f, 0.8f); res(ResourceType.ROYAL_ESSENCE, cx - 0.45f, floor, 0.9f) }
            RoomType.TRAINING_ROOM -> { put("banner", rx + 0.3f, floor, 0.6f, 0.9f); r.trainClass?.let { c -> rect(batch, art.antSoldier[0], cx - 0.5f, floor + 0.1f, 1f, 1f, antTint(sim, c)) } }
            else -> {}
        }
        var i = 0
        for (b in sim.brood) {
            if (b.roomId != r.id) continue
            val bx = rx + 0.15f + (i % (r.width * 2)) * 0.48f
            val by = floor + (if (r.type == RoomType.QUEEN_CHAMBER) 0.95f else 0.05f) + (i / (r.width * 2)) * 0.45f
            when (b.stage) {
                BroodStage.EGG -> rect(batch, art.egg, bx, by, 0.32f, 0.4f)
                BroodStage.LARVA -> rect(batch, art.larva, bx, by + if (settings.lowQuality) 0f else sin(anim * 3f + b.id) * 0.03f, 0.5f, 0.32f, if (b.fed < 0.3f) Palette.hex("FFC8B8") else Color.WHITE)
                BroodStage.PUPA -> rect(batch, art.pupa, bx, by, 0.5f, 0.32f)
            }
            i++
        }
    }

    private fun drawWater(batch: SpriteBatch, sim: Simulation, x0: Int, x1: Int, y0: Int, y1: Int) {
        if (sim.environment.water.isEmpty()) return
        for (y in y0..y1) for (x in x0..x1) {
            val a = sim.environment.waterAt(x, y)
            if (a <= 0.02f) continue
            val wave = if (settings.lowQuality) 0f else sin(anim * 3f + x * 0.8f) * 0.03f
            val h = (a + wave).coerceIn(0.06f, 1f)
            rect(batch, water, x.toFloat(), -y - 1f, 1f, h, Color.WHITE, 0.5f + 0.35f * a)
            if (a < 0.97f || sim.environment.waterAt(x, y - 1) < 0.05f) rect(batch, waterTop, x.toFloat(), -y - 1f + h - 0.1f, 1f, 0.12f)
        }
    }

    private fun drawOrders(batch: SpriteBatch, sim: Simulation, x0: Int, x1: Int, y0: Int, y1: Int) {
        val pulse = if (settings.lowQuality) 0.5f else 0.45f + sin(anim * 4f) * 0.1f
        for (o in sim.digOrders.values) {
            if (o.x < x0 || o.x > x1 || o.y < y0 || o.y > y1) continue
            cell(batch, stripes, o.x, o.y, if (o.roomId == -1) Palette.orange else Palette.accent, pulse)
            if (o.progress > 0f) bar(batch, o.x + 0.12f, -o.y - 0.85f, 0.76f, o.progress / sim.world.get(o.x, o.y).digWork.coerceAtLeast(0.1f), Palette.yellow)
        }
        for (o in sim.fillOrders.values) {
            if (o.x < x0 || o.x > x1 || o.y < y0 || o.y > y1) continue
            cell(batch, art.tiles[Terrain.FILL]!![0], o.x, o.y, Color.WHITE, 0.45f)
            cell(batch, stripes, o.x, o.y, Palette.uiText, 0.25f)
            if (o.progress > 0f) bar(batch, o.x + 0.12f, -o.y - 0.85f, 0.76f, o.progress / 3f, Palette.yellow)
        }
        for (c in sim.corpses) if (c.x in x0..x1 && c.y in y0..y1) rect(batch, art.corpse, c.x + 0.1f, -c.y - 0.95f, 0.8f, 0.5f)
    }

    private fun drawPiles(batch: SpriteBatch, sim: Simulation, x0: Int, x1: Int, y0: Int, y1: Int) {
        for (p in sim.piles.values) {
            if (p.x < x0 || p.x > x1 || p.y < y0 || p.y > y1) continue
            val n = minOf(3, 1 + p.amount / 4)
            for (i in 0 until n) rect(batch, art.icons[p.type]!!, p.x + 0.05f + i * 0.24f, -p.y - 0.98f + (i % 2) * 0.12f, 0.5f, 0.5f)
        }
    }

    private fun drawPheromones(batch: SpriteBatch, sim: Simulation) {
        val fx = sim.market.equipped[MarketSlot.PHEROMONE_FX]
        for (m in sim.pheromones.markers) {
            val c = if (fx == MarketItem.FX_RAINBOW) tmp.fromHsv((anim * 60f + m.id * 40f) % 360f, 0.55f, 0.95f).also { it.a = 1f } else Palette.pheromone(m.type)
            val r = m.radius + 0.5f
            val cx = m.x + 0.5f
            val cy = -m.y - 0.5f
            rect(batch, soft, cx - r, cy - r, r * 2, r * 2, c, 0.16f * m.strength + 0.05f)
            val pr = if (settings.lowQuality) r * 0.8f else r * (0.3f + ((anim * 0.45f + m.id * 0.13f) % 1f) * 0.7f)
            rect(batch, ring, cx - pr, cy - pr, pr * 2, pr * 2, c, (if (fx == MarketItem.FX_SPARKLE) 0.9f else 0.55f) * m.strength * (1f - pr / r * 0.6f))
            rect(batch, disc, cx - 0.28f, cy - 0.28f, 0.56f, 0.56f, Color.WHITE, 0.95f)
            rect(batch, disc, cx - 0.22f, cy - 0.22f, 0.44f, 0.44f, c, 1f)
        }
    }

    // ---------------------------------------------------------------- Varlıklar

    fun antTint(sim: Simulation, t: AntType): Color = when (t) {
        AntType.WORKER -> when (sim.market.equipped[MarketSlot.ANT_SKIN]) {
            MarketItem.SKIN_RED -> Palette.hex("B4462A")
            MarketItem.SKIN_GOLD -> Palette.hex("E0AE3A")
            MarketItem.SKIN_FOREST -> Palette.hex("5E8A3A")
            else -> Palette.hex("8A5232")
        }
        AntType.GATHERER -> Palette.hex("8A7A3A")
        AntType.DIGGER -> Palette.hex("B0602A")
        AntType.CARRIER -> Palette.hex("B89A60")
        AntType.NURSE -> Palette.hex("B06A7A")
        AntType.SCOUT -> Palette.hex("3E8A84")
        AntType.MEDIC -> Palette.hex("D0607E")
        AntType.ENGINEER -> Palette.hex("5E7090")
        AntType.TUNNELER -> Palette.hex("C07020")
        AntType.SOLDIER -> Palette.hex("A8382A")
        AntType.HEAVY_SOLDIER -> Palette.hex("70403A")
        AntType.DEFENDER -> Palette.hex("4A5E9A")
        AntType.ELITE -> Palette.hex("8A3A9A")
        AntType.ROYAL_GUARD -> Palette.hex("C8962A")
        AntType.QUEEN -> Color.WHITE
    }

    private fun drawAnts(batch: SpriteBatch, sim: Simulation, xMin: Float, xMax: Float, yMin: Float, yMax: Float) {
        val a = sim.alpha
        for (ant in sim.ants) {
            val x = MathUtils.lerp(ant.prevX, ant.x, a)
            val y = MathUtils.lerp(ant.prevY, ant.y, a)
            if (x < xMin || x > xMax || y < yMin || y > yMax) continue
            drawAnt(batch, sim, ant, x, -y)
        }
    }

    private fun drawAnt(batch: SpriteBatch, sim: Simulation, ant: Ant, x: Float, y: Float) {
        val queen = ant.type == AntType.QUEEN
        val military = ant.type.military
        val size = when { queen -> 2.1f; ant.type == AntType.HEAVY_SOLDIER || ant.type == AntType.ROYAL_GUARD -> 1.2f; military -> 1.08f; ant.type == AntType.SCOUT -> 0.85f; else -> 0.92f }
        val moving = (ant.activity != AntActivity.IDLE && ant.activity != AntActivity.RESTING && ant.activity != AntActivity.GUARDING) || ant.path != null
        val striped = sim.market.equipped[MarketSlot.WORKER_LOOK] == MarketItem.LOOK_STRIPES
        val frames = when { queen -> art.queen; military -> art.antSoldier; ant.type == AntType.SCOUT -> art.antScout; striped -> art.antStriped; else -> art.antWorker }
        val frame = if (moving) frames[(ant.walkPhase.toInt()) % frames.size] else frames[0]
        val rot = if (queen) 0f else -ant.facing * MathUtils.radiansToDegrees
        tmp.set(if (queen) Color.WHITE else antTint(sim, ant.type))
        when {
            sim.time - ant.lastHit < 0.2 -> tmp.lerp(Palette.red, 0.7f)
            ant.starving -> tmp.lerp(Palette.hex("FFB0A0"), 0.4f)
            ant.sick -> tmp.lerp(Palette.hex("9BD45A"), 0.45f)
            ant.activity == AntActivity.RESTING -> tmp.lerp(Palette.hex("C8D4FF"), 0.3f)
        }
        batch.setColor(tmp)
        batch.draw(frame, x - size / 2, y - size / 2, size / 2, size / 2, size, size, 1f, 1f, rot)
        if (queen) {
            val crown = when (sim.market.equipped[MarketSlot.QUEEN]) { MarketItem.QUEEN_FLOWER -> "crown_flower"; MarketItem.QUEEN_CRYSTAL -> "crown_crystal"; else -> "crown" }
            rect(batch, art.r(crown), x + 0.35f, y + 0.25f, 0.6f, 0.4f)
        }
        val carry = ant.carryType
        if (carry != null && ant.carryAmount > 0) rect(batch, art.icons[carry]!!, x + cos(ant.facing) * 0.42f - 0.2f, y - sin(ant.facing) * 0.42f - 0.2f, 0.4f, 0.4f)
        if (ant.carryingCorpse) rect(batch, art.corpse, x + cos(ant.facing) * 0.35f - 0.3f, y - sin(ant.facing) * 0.35f - 0.18f, 0.6f, 0.38f)
        val maxHp = ant.maxHp(sim.mods)
        if (ant.hp < maxHp * 0.9f) bar(batch, x - 0.35f, y + size * 0.42f, 0.7f, ant.hp / maxHp, if (military) Palette.orange else Palette.green)
        if (military && ant.squad == 1) rect(batch, disc, x + 0.28f, y + 0.22f, 0.18f, 0.18f, Palette.hex("6B7FD7"))
        if (ant.activity == AntActivity.DIGGING && !settings.lowQuality) {
            val hx = x + cos(ant.facing) * 0.55f
            val hy = y - sin(ant.facing) * 0.55f
            for (k in 0..3) {
                val t = (anim * 2.5f + k * 0.25f + ant.id * 0.1f) % 1f
                rect(batch, disc, hx + (k - 1.5f) * 0.1f + sin(t * 6f + k) * 0.06f, hy + t * 0.35f, 0.1f, 0.1f, Palette.terrain(Terrain.SOFT_SOIL), 1f - t)
            }
        }
    }

    private fun drawCreatures(batch: SpriteBatch, sim: Simulation, xMin: Float, xMax: Float, yMin: Float, yMax: Float) {
        val a = sim.alpha
        for (c in sim.creatures) {
            val x = MathUtils.lerp(c.prevX, c.x, a)
            val y = -MathUtils.lerp(c.prevY, c.y, a)
            if (x < xMin || x > xMax || -y < yMin || -y > yMax) continue
            drawCreature(batch, sim, c, x, y)
        }
    }

    private fun drawCreature(batch: SpriteBatch, sim: Simulation, c: Creature, x: Float, y: Float) {
        val rival = c.kind == CreatureKind.RIVAL_SOLDIER || c.kind == CreatureKind.RIVAL_ELITE
        val frames = if (rival) art.antSoldier else art.creatures[c.kind]!!
        val size = when (c.kind) { CreatureKind.SPIDER, CreatureKind.CAVE_HUNTER -> 1.6f; CreatureKind.CENTIPEDE -> 1.5f; CreatureKind.BEETLE_LARVA -> 0.8f; CreatureKind.RIVAL_ELITE -> 1.2f; else -> 1.05f }
        val f = frames[(c.walkPhase.toInt()) % frames.size]
        tmp.set(if (rival) Palette.creature(c.kind) else Color.WHITE)
        if (sim.time - c.lastHit < 0.2) tmp.lerp(Palette.red, 0.6f)
        if (c.kind.hostile && !settings.lowQuality) rect(batch, soft, x - size * 0.7f, y - size * 0.7f, size * 1.4f, size * 1.4f, Palette.red, 0.22f + sin(anim * 6f) * 0.08f)
        batch.setColor(tmp)
        batch.draw(f, x - size / 2, y - size / 2, size / 2, size / 2, size, size, 1f, 1f, -c.facing * MathUtils.radiansToDegrees)
        if (c.hp < c.maxHp) bar(batch, x - 0.4f, y + size * 0.42f, 0.8f, c.hp / c.maxHp, Palette.red)
    }

    private fun drawMilitaryMarkers(batch: SpriteBatch, sim: Simulation) {
        for ((i, o) in sim.orders.withIndex()) {
            if (o.kind != OrderKind.GUARD && o.kind != OrderKind.RALLY && o.kind != OrderKind.AMBUSH) continue
            if (sim.squadSize(i) == 0) continue
            rect(batch, art.r("flag"), o.x + 0.15f, -o.y - 1f, 0.6f, 0.95f, if (i == 0) Palette.red else Palette.hex("6B7FD7"))
        }
    }

    private fun drawWeather(batch: SpriteBatch, sim: Simulation, left: Float, right: Float, top: Float) {
        val w = sim.environment.weather
        val winter = sim.season == Season.WINTER
        if (w != Weather.RAIN && w != Weather.STORM && !(winter && w == Weather.CLOUDY)) return
        if (top < -0.5f) return
        val n = if (settings.lowQuality) 25 else if (w == Weather.STORM) 120 else 60
        val span = right - left
        val dropR = art.r("drop")
        val snowR = art.r("snow")
        for (i in 0 until n) {
            val hx = Hash.unit(3, i, 0, 9).toFloat()
            val speed = if (winter) 1.2f else 10f
            val yy = top - ((Hash.unit(3, i, 1, 9).toFloat() * (top + 1f) + anim * speed) % (top + 1f))
            val xx = left + ((hx * span + anim * (if (winter) 0.5f else 1.8f)) % span)
            if (yy < 0f) continue
            if (winter) rect(batch, snowR, xx, yy, 0.22f, 0.22f) else rect(batch, dropR, xx, yy, 0.08f, 0.45f, Color.WHITE, 0.85f)
        }
    }

    private fun drawOverlay(batch: SpriteBatch, sim: Simulation, view: ViewState) {
        view.selectedCell?.let { (x, y) ->
            val c = Palette.accent
            val p = 0.06f
            rect(batch, art.white, x.toFloat(), -y - 1f, 1f, p, c); rect(batch, art.white, x.toFloat(), -y - p, 1f, p, c)
            rect(batch, art.white, x.toFloat(), -y - 1f, p, 1f, c); rect(batch, art.white, x + 1f - p, -y - 1f, p, 1f, c)
        }
        val ghost = view.ghostType
        if (ghost != null && view.ghostX != null) {
            val gx = view.ghostX!!
            val gy = view.ghostY!!
            val ok = sim.checkRoomPlacement(ghost, gx, gy).ok
            val c = if (ok) Palette.green else Palette.red
            val pulse = 0.3f + sin(anim * 5f) * 0.06f
            rect(batch, art.white, gx.toFloat(), -(gy + ghost.height).toFloat(), ghost.width.toFloat(), ghost.height.toFloat(), c, pulse)
            rect(batch, stripes, gx.toFloat(), -(gy + ghost.height).toFloat(), ghost.width.toFloat(), ghost.height.toFloat(), Color.WHITE, 0.25f)
            rect(batch, art.icon(if (ok) "check" else "close"), gx + ghost.width / 2f - 0.4f, -(gy + ghost.height / 2f) - 0.4f, 0.8f, 0.8f, Color.WHITE, 0.95f)
        }
    }
}
