package com.yeraltiimparatorlugu.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
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
 * Yeraltı kesit görünümü. Dünya birimi = 1 hücre; hücre (x, y) ekranda (x, -y-1) dikdörtgenine çizilir.
 * Yalnızca kamerada görünen hücreler ve varlıklar çizilir; tüm dokular tek atlastadır.
 */
class WorldRenderer(private val art: Art, private val settings: Settings) {
    val camera = OrthographicCamera()
    private val tmp = Color()
    private val tmp2 = Color()
    private var anim = 0f

    fun resize(w: Int, h: Int) {
        camera.viewportWidth = 13f
        camera.viewportHeight = 13f * h / w.coerceAtLeast(1)
        camera.update()
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
        drawRain(batch, sim, left, right, top)
        drawOverlay(batch, sim, view)
        batch.setColor(Color.WHITE)
        batch.end()
    }

    private fun rect(batch: SpriteBatch, r: TextureRegion, x: Float, y: Float, w: Float, h: Float, c: Color, a: Float = c.a) {
        batch.setColor(c.r, c.g, c.b, a)
        batch.draw(r, x, y, w, h)
    }

    private fun cell(batch: SpriteBatch, r: TextureRegion, x: Int, y: Int, c: Color = Color.WHITE, a: Float = c.a) =
        rect(batch, r, x.toFloat(), -y - 1f, 1f, 1f, c, a)

    private fun bar(batch: SpriteBatch, x: Float, y: Float, w: Float, frac: Float, c: Color) {
        rect(batch, art.white, x, y, w, 0.09f, Palette.uiText, 0.45f)
        rect(batch, art.white, x, y, w * frac.coerceIn(0f, 1f), 0.09f, c, 1f)
    }

    // ---------------------------------------------------------------- Gökyüzü ve hava

    private fun drawSky(batch: SpriteBatch, sim: Simulation, left: Float, right: Float, top: Float) {
        if (top <= 0f) return
        val f = sim.dayFraction
        val warm = MathUtils.clamp(1f - kotlin.math.abs(f - 0.5f) * 2.4f, 0f, 1f)
        val weather = sim.environment.weather
        val bands = 10
        val h = top / bands
        for (i in 0 until bands) {
            val t = i / (bands - 1f)
            tmp.set(Palette.skyBottom).lerp(Palette.skyTop, t)
            tmp.lerp(Palette.hex("FFD7A8"), (1f - warm) * 0.35f * (1f - t))
            when (weather) {
                Weather.STORM -> tmp.lerp(Palette.hex("B8C4CE"), 0.55f)
                Weather.RAIN, Weather.CLOUDY -> tmp.lerp(Palette.hex("C8D4DC"), 0.35f)
                Weather.DROUGHT -> tmp.lerp(Palette.hex("FFE7A8"), 0.4f)
                else -> {}
            }
            rect(batch, art.white, left - 1, i * h, right - left + 2, h + 0.01f, tmp, 1f)
        }
        if (!settings.lowQuality) {
            if (weather == Weather.CLEAR || weather == Weather.DROUGHT) {
                val sunX = MathUtils.lerp(left - 2f, right + 2f, f)
                val sunY = 6f + sin(f * MathUtils.PI) * 4f
                rect(batch, art.sun, sunX - 1.5f, sunY - 1.5f, 3f, 3f, Color.WHITE)
            }
            val clouds = if (weather == Weather.CLEAR || weather == Weather.DROUGHT) 3 else 6
            for (i in 0..clouds) {
                val cx = ((i * 23.7f + anim * 0.25f) % 60f) - 30f + MathUtils.floor(left / 60f) * 60f
                val tint = if (weather == Weather.STORM) Palette.hex("DCE3E8") else Color.WHITE
                for (k in 0..1) rect(batch, art.cloud, cx + k * 60f, 4f + (i % 4) * 1.7f, 4.5f, 2.2f, tint)
            }
        }
    }

    private fun drawRain(batch: SpriteBatch, sim: Simulation, left: Float, right: Float, top: Float) {
        val w = sim.environment.weather
        val winter = sim.season == Season.WINTER
        if (w != Weather.RAIN && w != Weather.STORM && !(winter && w == Weather.CLOUDY)) return
        if (top < -0.5f) return
        val n = if (settings.lowQuality) 25 else if (w == Weather.STORM) 110 else 55
        val span = right - left
        for (i in 0 until n) {
            val hx = Hash.unit(3, i, 0, 9).toFloat()
            val speed = if (winter) 1.5f else 9f
            val yy = top - ((Hash.unit(3, i, 1, 9).toFloat() * (top + 1f) + anim * speed) % (top + 1f))
            val xx = left + ((hx * span + anim * (if (winter) 0.6f else 1.5f)) % span)
            if (yy < 0f) continue
            if (winter) rect(batch, art.snow, xx, yy, 0.24f, 0.24f, Palette.hex("E8F2FF"), 1f)
            else rect(batch, art.drop, xx, yy, 0.06f, 0.4f, Color.WHITE, 0.8f)
        }
    }

    // ---------------------------------------------------------------- Yeraltı

    private fun variant(x: Int, y: Int) = ((Hash.mix(7, x, y, 1) ushr 3) % 3).toInt()

    private fun depthTint(y: Int): Float = if (settings.lowQuality) 1f else 1f - minOf(0.16f, y / 900f)

    private fun roomColor(sim: Simulation, t: RoomType): Color {
        val base = Palette.room(t)
        return when (sim.market.equipped[MarketSlot.ROOM_STYLE]) {
            MarketItem.ROOMS_PASTEL -> tmp2.set(base).lerp(Color.WHITE, 0.35f)
            MarketItem.ROOMS_HONEY -> tmp2.set(base).lerp(Palette.hex("FFD27A"), 0.35f)
            else -> tmp2.set(base)
        }
    }

    private fun drawUnderground(batch: SpriteBatch, sim: Simulation, x0: Int, x1: Int, y0: Int, y1: Int) {
        val w = sim.world
        val rivalTunnel = Palette.terrain(Terrain.RIVAL_TUNNEL)
        for (y in y0..y1) {
            val dt = depthTint(y)
            tmp.set(dt, dt, dt, 1f)
            for (x in x0..x1) {
                val t = w.get(x, y)
                if (t.walkable) {
                    val room = sim.roomAt(x, y)
                    when {
                        room != null -> { val rc = roomColor(sim, room.type); cell(batch, art.tunnel[variant(x, y)], x, y, Color(rc.r * dt, rc.g * dt, rc.b * dt, 1f)) }
                        t == Terrain.RIVAL_TUNNEL && !w.isRevealed(x, y) -> { cell(batch, art.unexplored[variant(x, y)], x, y, tmp); continue }
                        t == Terrain.RIVAL_TUNNEL -> cell(batch, art.tunnel[variant(x, y)], x, y, rivalTunnel)
                        else -> cell(batch, art.tunnel[variant(x, y)], x, y, tmp)
                    }
                    if (!w.isWalkable(x, y - 1)) rect(batch, art.white, x.toFloat(), -y - 0.22f, 1f, 0.22f, Palette.tunnelShade, 0.55f)
                    if (!w.isWalkable(x - 1, y)) rect(batch, art.white, x.toFloat(), -y - 1f, 0.1f, 1f, Palette.tunnelShade, 0.4f)
                    if (!w.isWalkable(x + 1, y)) rect(batch, art.white, x + 0.9f, -y - 1f, 0.1f, 1f, Palette.tunnelShade, 0.25f)
                } else {
                    val tex = if (w.isRevealed(x, y)) art.tiles[t]!![variant(x, y)] else art.unexplored[variant(x, y)]
                    cell(batch, tex, x, y, tmp)
                    if (w.isWalkable(x, y + 1) && (w.get(x, y + 1) != Terrain.RIVAL_TUNNEL || w.isRevealed(x, y + 1))) rect(batch, art.white, x.toFloat(), -y - 1f, 1f, 0.12f, Color.WHITE, 0.35f)
                }
            }
        }
    }

    private fun drawWater(batch: SpriteBatch, sim: Simulation, x0: Int, x1: Int, y0: Int, y1: Int) {
        val water = sim.environment.water
        if (water.isEmpty()) return
        val c = Palette.hex("5FB8E6")
        for (y in y0..y1) for (x in x0..x1) {
            val a = sim.environment.waterAt(x, y)
            if (a <= 0.02f) continue
            val wave = if (settings.lowQuality) 0f else sin(anim * 3f + x * 0.8f) * 0.03f
            val h = (a + wave).coerceIn(0.05f, 1f)
            rect(batch, art.white, x.toFloat(), -y - 1f, 1f, h, c, 0.35f + 0.35f * a)
            rect(batch, art.white, x.toFloat(), -y - 1f + h - 0.05f, 1f, 0.05f, Color.WHITE, 0.5f)
        }
    }

    private fun drawRooms(batch: SpriteBatch, sim: Simulation, x0: Int, x1: Int, y0: Int, y1: Int) {
        for (r in sim.rooms) {
            if (r.x > x1 || r.x + r.width < x0 || r.y > y1 || r.y + r.height < y0) continue
            val c = roomColor(sim, r.type)
            val border = Color(c.r * 0.7f, c.g * 0.62f, c.b * 0.55f, 1f)
            val rx = r.x.toFloat()
            val ry = -(r.y + r.height).toFloat()
            val bw = 0.08f
            rect(batch, art.white, rx, ry, r.width.toFloat(), bw, border)
            rect(batch, art.white, rx, ry + r.height - bw, r.width.toFloat(), bw, border)
            rect(batch, art.white, rx, ry, bw, r.height.toFloat(), border)
            rect(batch, art.white, rx + r.width - bw, ry, bw, r.height.toFloat(), border)
            if (!r.functional || r.state == RoomState.UPGRADING) {
                if (r.state != RoomState.UPGRADING) rect(batch, art.stripes, rx, ry, r.width.toFloat(), r.height.toFloat(), Palette.orange, 0.18f)
                val p = if (r.state == RoomState.EXCAVATING) excavationProgress(sim, r) else r.buildProgress / r.workRequired
                bar(batch, rx + 0.3f, ry + r.height - 0.45f, r.width - 0.6f, p, Palette.accent)
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
        val floor = ry + 0.12f
        when (r.type) {
            RoomType.QUEEN_CHAMBER -> rect(batch, art.bed, rx + r.width / 2f - 1.3f, floor, 2.6f, 0.7f, Palette.hex("FFB59A"))
            RoomType.FOOD_STORAGE, RoomType.MATERIAL_STORAGE, RoomType.WATER_STORAGE -> {
                val kind = when (r.type) { RoomType.FOOD_STORAGE -> StorageKind.FOOD; RoomType.WATER_STORAGE -> StorageKind.WATER; else -> StorageKind.MATERIAL }
                val types = ResourceType.entries.filter { it.storage == kind && sim.resources[it] > 0 }
                val fill = sim.resources.totalOf(kind).toFloat() / sim.capacityOf(kind).coerceAtLeast(1)
                val n = (fill * r.width * 2 * r.height).toInt().coerceIn(if (types.isEmpty()) 0 else 1, r.width * 2 * (r.height - 1).coerceAtLeast(1))
                for (i in 0 until n) {
                    val t = types[i % types.size]
                    rect(batch, art.icons[t]!!, rx + 0.05f + (i % (r.width * 2)) * 0.48f, floor + (i / (r.width * 2)) * 0.42f, 0.48f, 0.48f, Color.WHITE)
                }
            }
            RoomType.REST_ROOM, RoomType.BARRACKS, RoomType.ELITE_QUARTERS, RoomType.INFIRMARY -> {
                val tint = when (r.type) { RoomType.BARRACKS -> Palette.hex("F2B8AE"); RoomType.INFIRMARY -> Palette.hex("FFD0D0"); RoomType.ELITE_QUARTERS -> Palette.hex("E8B8E0"); else -> Color.WHITE }
                for (i in 0 until minOf(r.width, 2 + r.level)) rect(batch, art.bed, rx + 0.15f + i * (r.width - 0.3f) / (2 + r.level), floor, 0.9f, 0.45f, tint)
            }
            RoomType.FUNGUS_FARM -> {
                val n = minOf(r.leafBuffer, r.width * 3)
                for (i in 0 until n) {
                    val grow = if (i == 0) 0.6f + r.growProgress * 0.4f else 1f
                    val s = 0.42f * grow
                    rect(batch, art.mushroom, rx + 0.2f + (i % (r.width * 2)) * 0.45f, floor + (i / (r.width * 2)) * 0.5f, s, s, Color.WHITE)
                }
                if (r.leafBuffer == 0) rect(batch, art.icons[ResourceType.LEAF]!!, rx + r.width / 2f - 0.3f, floor + 0.6f, 0.6f, 0.6f, Color.WHITE, 0.35f)
            }
            RoomType.RESEARCH_LAB, RoomType.GENETICS_LAB, RoomType.ARCHIVE -> {
                val ic = if (r.type == RoomType.GENETICS_LAB) ResourceType.GENETIC else ResourceType.LEAF
                for (i in 0 until r.width) rect(batch, art.icons[ic]!!, rx + 0.25f + i, floor, 0.5f, 0.5f, Color.WHITE, if (sim.research.current != null || r.type != RoomType.RESEARCH_LAB) 1f else 0.45f)
            }
            RoomType.WORKSHOP -> r.recipe.output.keys.forEach { rect(batch, art.icons[it]!!, rx + r.width / 2f - 0.35f, floor, 0.7f, 0.7f, Color.WHITE) }
            RoomType.ROYAL_CENTER -> rect(batch, art.icons[ResourceType.ROYAL_ESSENCE]!!, rx + r.width / 2f - 0.5f, floor, 1f, 1f, Color.WHITE)
            RoomType.FOSSIL_MUSEUM -> { val n = sim.museum.displayed(sim).size; for (i in 0 until n) rect(batch, art.icons[ResourceType.FOSSIL]!!, rx + 0.1f + i * 0.6f, floor, 0.55f, 0.55f, Color.WHITE) }
            RoomType.TRAP_ROOM -> for (i in 0 until r.width * 2) rect(batch, art.white, rx + 0.1f + i * 0.5f, floor, 0.08f, 0.35f, Palette.uiText, 0.6f)
            RoomType.DEFENSE_ROOM, RoomType.FORTRESS -> rect(batch, art.flag, rx + r.width / 2f - 0.3f, floor, 0.6f, 0.9f, Palette.hex("6B7FD7"))
            RoomType.VENTILATION -> { val a = anim * 4f; rect(batch, art.ring, rx + r.width / 2f - 0.6f, floor + 0.6f + sin(a) * 0.05f, 1.2f, 1.2f, Palette.accent, 0.5f) }
            RoomType.HUMIDITY_CONTROL -> rect(batch, art.icons[ResourceType.WATER]!!, rx + r.width / 2f - 0.4f, floor, 0.8f, 0.8f, Color.WHITE)
            RoomType.EXPLORATION_CENTER -> rect(batch, art.flag, rx + 0.4f, floor, 0.6f, 0.9f, Palette.accent)
            RoomType.TRADE_ROOM -> { rect(batch, art.icons[ResourceType.FOOD]!!, rx + 0.5f, floor, 0.5f, 0.5f, Color.WHITE); rect(batch, art.icons[ResourceType.MINERAL]!!, rx + 1.5f, floor, 0.5f, 0.5f, Color.WHITE) }
            RoomType.TRAINING_ROOM -> r.trainClass?.let { rect(batch, art.soldierFrames[0], rx + r.width / 2f - 0.4f, floor + 0.1f, 0.8f, 0.8f, antTint(sim, it)) }
            else -> {}
        }
        var i = 0
        for (b in sim.brood) {
            if (b.roomId != r.id) continue
            val bx = rx + 0.15f + (i % (r.width * 2)) * 0.48f
            val by = floor + 0.55f + (i / (r.width * 2)) * 0.4f
            when (b.stage) {
                BroodStage.EGG -> rect(batch, art.egg, bx, by, 0.3f, 0.3f, Color.WHITE)
                BroodStage.LARVA -> rect(batch, art.larva, bx, by + if (settings.lowQuality) 0f else sin(anim * 3f + b.id) * 0.03f, 0.45f, 0.3f, if (b.fed < 0.3f) Palette.hex("FFD0C0") else Color.WHITE)
                BroodStage.PUPA -> rect(batch, art.pupa, bx, by, 0.45f, 0.3f, Color.WHITE)
            }
            i++
        }
    }

    // ---------------------------------------------------------------- Yüzey

    private fun drawSurface(batch: SpriteBatch, sim: Simulation, left: Float, right: Float, x0: Int, x1: Int) {
        val season = sim.season
        for (x in x0..x1) {
            val biome = Biome.at(sim.seed, x)
            rect(batch, art.white, x.toFloat(), -0.12f, 1.02f, 0.24f, Palette.ground(biome, season), 1f)
            if (season == Season.WINTER) rect(batch, art.white, x.toFloat(), 0.02f, 1.02f, 0.18f, Color.WHITE, 0.95f)
        }
        rect(batch, art.white, left - 1, -0.18f, right - left + 2, 0.07f, Palette.grassDark, 1f)
        if (!settings.lowQuality) for (x in x0..x1) {
            val v = variant(x, -1)
            val biome = Biome.at(sim.seed, x)
            if (biome != Biome.DESERT && biome != Biome.ROCKY && season != Season.WINTER) rect(batch, art.tuft, x + v * 0.2f, 0.05f, 0.8f, 0.4f, if (season == Season.WINTER) Palette.hex("D8E8D0") else Color.WHITE)
            val deco = Hash.unit(sim.seed, x, 0, 33)
            if (kotlin.math.abs(x) < 6) continue
            when (biome) {
                Biome.FOREST -> if (deco < 0.18) rect(batch, art.tree, x - 0.5f, 0f, 2.2f, 3.3f, if (season == Season.AUTUMN) Palette.hex("F2C46A") else Color.WHITE)
                Biome.ROCKY -> if (deco < 0.2) rect(batch, art.rock, x.toFloat(), 0f, 1.4f, 0.9f, Color.WHITE)
                Biome.DESERT -> if (deco < 0.08) rect(batch, art.cactus, x.toFloat(), 0f, 0.8f, 1.3f, Color.WHITE)
                Biome.SWAMP, Biome.POND -> { if (deco < 0.1) rect(batch, art.pond, x.toFloat(), -0.05f, 3f, 0.6f, Color.WHITE); if (deco in 0.1..0.25) rect(batch, art.reed, x.toFloat(), 0f, 0.6f, 1.2f, Color.WHITE) }
                Biome.FLOWERS -> if (deco < 0.5 && season != Season.WINTER) rect(batch, art.flower, x + 0.3f, 0.05f, 0.4f, 0.6f, Color.WHITE)
                Biome.HUMAN_RUINS -> if (deco < 0.12) rect(batch, art.brick, x.toFloat(), 0f, 1.3f, 0.8f, Color.WHITE)
                Biome.MEADOW -> if (deco < 0.12 && season != Season.WINTER) rect(batch, art.flower, x + 0.5f, 0.05f, 0.35f, 0.55f, Color.WHITE)
            }
        }
        if (sim.surfaceMound > 0) {
            val s = minOf(3.5f, 0.6f + kotlin.math.sqrt(sim.surfaceMound.toFloat()) * 0.12f)
            rect(batch, art.circle, sim.dumpX + 0.5f - s, -s * 0.35f, s * 2f, s * 0.9f, Palette.resource(ResourceType.SOIL))
        }
        // Market dekorasyonları
        if (MarketItem.DECOR_GARDEN in sim.market.decorations) for (i in 0..3) rect(batch, art.flower, -3.5f + i * 0.5f, 0.05f, 0.4f, 0.6f, Color.WHITE)
        if (MarketItem.DECOR_LAMPS in sim.market.decorations) { rect(batch, art.lamp, -1.2f, 0f, 0.7f, 0.7f, Color.WHITE); rect(batch, art.lamp, 1.5f, 0f, 0.7f, 0.7f, Color.WHITE) }
        if (MarketItem.DECOR_STATUE in sim.market.decorations) rect(batch, art.statue, -6f, 0f, 1.2f, 1.6f, Color.WHITE)
        // Rakip yuvaları
        for (r in sim.rivalSystem.rivals) {
            if (!r.alive || !r.discovered || r.x < x0 - 3 || r.x > x1 + 3) continue
            rect(batch, art.circle, r.x - 1.5f, -0.6f, 3f, 1.4f, Palette.hex("B98A5E"))
            rect(batch, art.flag, r.x - 0.1f, 0.4f, 0.7f, 1.1f, Palette.creature(CreatureKind.RIVAL_SOLDIER))
        }
        // Bilinen menzil sınırları
        for (bx in listOf(sim.knownMin(), sim.knownMax())) if (bx in x0..x1) rect(batch, art.flag, bx.toFloat(), 0.1f, 0.5f, 0.8f, Palette.accent, 0.8f)
        for (s in sim.sources) {
            if (s.x < x0 || s.x > x1) continue
            val scale = 0.55f + 0.45f * (s.amount.toFloat() / s.maxAmount)
            rect(batch, art.sources[s.kind]!!, s.x + 0.5f - 0.7f * scale, 0f, 1.4f * scale, 1.4f * scale, Color.WHITE, if (sim.isKnown(s.x)) 1f else 0.55f)
        }
    }

    private fun drawOrders(batch: SpriteBatch, sim: Simulation, x0: Int, x1: Int, y0: Int, y1: Int) {
        val pulse = if (settings.lowQuality) 0.5f else 0.42f + sin(anim * 4f) * 0.1f
        for (o in sim.digOrders.values) {
            if (o.x < x0 || o.x > x1 || o.y < y0 || o.y > y1) continue
            cell(batch, art.stripes, o.x, o.y, if (o.roomId == -1) Palette.orange else Palette.accent, pulse)
            if (o.progress > 0f) bar(batch, o.x + 0.1f, -o.y - 0.9f, 0.8f, o.progress / sim.world.get(o.x, o.y).digWork.coerceAtLeast(0.1f), Palette.yellow)
        }
        for (o in sim.fillOrders.values) {
            if (o.x < x0 || o.x > x1 || o.y < y0 || o.y > y1) continue
            cell(batch, art.stripes, o.x, o.y, Palette.terrain(Terrain.FILL), 0.75f)
            if (o.progress > 0f) bar(batch, o.x + 0.1f, -o.y - 0.9f, 0.8f, o.progress / 3f, Palette.yellow)
        }
        for (c in sim.corpses) if (c.x in x0..x1 && c.y in y0..y1) rect(batch, art.corpse, c.x + 0.15f, -c.y - 0.9f, 0.7f, 0.5f, Color.WHITE, 0.9f)
    }

    private fun drawPiles(batch: SpriteBatch, sim: Simulation, x0: Int, x1: Int, y0: Int, y1: Int) {
        for (p in sim.piles.values) {
            if (p.x < x0 || p.x > x1 || p.y < y0 || p.y > y1) continue
            val n = minOf(3, 1 + p.amount / 4)
            for (i in 0 until n) rect(batch, art.icons[p.type]!!, p.x + 0.1f + i * 0.22f, -p.y - 0.95f + (i % 2) * 0.1f, 0.45f, 0.45f, Color.WHITE)
        }
    }

    private fun drawPheromones(batch: SpriteBatch, sim: Simulation) {
        val fx = sim.market.equipped[MarketSlot.PHEROMONE_FX]
        for (m in sim.pheromones.markers) {
            val c = if (fx == MarketItem.FX_RAINBOW) tmp.fromHsv((anim * 60f + m.id * 40f) % 360f, 0.6f, 0.95f).also { it.a = 1f } else Palette.pheromone(m.type)
            val r = m.radius + 0.5f
            val cx = m.x + 0.5f
            val cy = -m.y - 0.5f
            rect(batch, art.circle, cx - r, cy - r, r * 2, r * 2, c, 0.13f * m.strength + 0.04f)
            val pr = if (settings.lowQuality) r else r * (0.35f + ((anim * 0.5f + m.id * 0.13f) % 1f) * 0.65f)
            val ringA = (if (fx == MarketItem.FX_SPARKLE) 0.85f else 0.5f) * m.strength + 0.1f
            rect(batch, art.ring, cx - pr, cy - pr, pr * 2, pr * 2, c, ringA)
            rect(batch, art.circle, cx - 0.25f, cy - 0.25f, 0.5f, 0.5f, c, 0.9f)
        }
    }

    // ---------------------------------------------------------------- Varlıklar

    fun antTint(sim: Simulation, t: AntType): Color = when (t) {
        AntType.WORKER -> when (sim.market.equipped[MarketSlot.ANT_SKIN]) {
            MarketItem.SKIN_RED -> Palette.hex("9A3A22")
            MarketItem.SKIN_GOLD -> Palette.hex("C99A2E")
            MarketItem.SKIN_FOREST -> Palette.hex("4E6B2E")
            else -> Palette.hex("5A3620")
        }
        AntType.GATHERER -> Palette.hex("6A5A2A")
        AntType.DIGGER -> Palette.hex("8A4A20")
        AntType.CARRIER -> Palette.hex("9A7A4A")
        AntType.NURSE -> Palette.hex("8A4A5A")
        AntType.SCOUT -> Palette.hex("2E6A66")
        AntType.MEDIC -> Palette.hex("B04A6A")
        AntType.ENGINEER -> Palette.hex("4A5A70")
        AntType.TUNNELER -> Palette.hex("A05A1A")
        AntType.SOLDIER -> Palette.hex("8A2A1A")
        AntType.HEAVY_SOLDIER -> Palette.hex("5A3030")
        AntType.DEFENDER -> Palette.hex("3A4A7A")
        AntType.ELITE -> Palette.hex("6A2A7A")
        AntType.ROYAL_GUARD -> Palette.hex("A0761A")
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
        val size = when { queen -> 1.5f; ant.type == AntType.HEAVY_SOLDIER || ant.type == AntType.ROYAL_GUARD -> 1.0f; military -> 0.9f; else -> 0.78f }
        val moving = (ant.activity != AntActivity.IDLE && ant.activity != AntActivity.RESTING && ant.activity != AntActivity.GUARDING) || ant.path != null
        val striped = sim.market.equipped[MarketSlot.WORKER_LOOK] == MarketItem.LOOK_STRIPES
        val frames = when { queen -> art.queenFrames; military -> art.soldierFrames; striped -> art.grayStripedAnt; else -> art.grayAnt }
        val frame = if (moving) frames[(ant.walkPhase.toInt()) % frames.size] else frames[0]
        val rot = if (queen) 0f else -ant.facing * MathUtils.radiansToDegrees
        tmp.set(antTint(sim, ant.type))
        when {
            sim.time - ant.lastHit < 0.2 -> tmp.lerp(Palette.red, 0.7f)
            ant.starving -> tmp.lerp(Palette.hex("FFB0A0"), 0.4f)
            ant.sick -> tmp.lerp(Palette.hex("9BD45A"), 0.45f)
            ant.activity == AntActivity.RESTING -> tmp.lerp(Palette.hex("C8D4FF"), 0.3f)
        }
        batch.setColor(tmp)
        batch.draw(frame, x - size / 2, y - size / 2, size / 2, size / 2, size, size, 1f, 1f, rot)
        if (queen) {
            val crown = when (sim.market.equipped[MarketSlot.QUEEN]) { MarketItem.QUEEN_FLOWER -> art.crownFlower; MarketItem.QUEEN_CRYSTAL -> art.crownCrystal; else -> art.crown }
            rect(batch, crown, x + 0.05f, y + 0.28f, 0.42f, 0.28f, Color.WHITE)
        }
        val carry = ant.carryType
        if (carry != null && ant.carryAmount > 0) {
            rect(batch, art.icons[carry]!!, x + cos(ant.facing) * 0.36f - 0.18f, y - sin(ant.facing) * 0.36f - 0.18f, 0.36f, 0.36f, Color.WHITE)
        }
        if (ant.carryingCorpse) rect(batch, art.corpse, x + cos(ant.facing) * 0.3f - 0.25f, y - sin(ant.facing) * 0.3f - 0.15f, 0.5f, 0.35f, Color.WHITE)
        val maxHp = ant.maxHp(sim.mods)
        if (ant.hp < maxHp * 0.9f) bar(batch, x - 0.35f, y + size * 0.45f, 0.7f, ant.hp / maxHp, if (military) Palette.orange else Palette.green)
        if (military && ant.squad == 1) rect(batch, art.circle, x + 0.25f, y + 0.2f, 0.16f, 0.16f, Palette.hex("6B7FD7"))
        if (ant.activity == AntActivity.DIGGING && !settings.lowQuality) {
            val hx = x + cos(ant.facing) * 0.5f
            val hy = y - sin(ant.facing) * 0.5f
            for (k in 0..2) {
                val t = (anim * 3f + k * 0.33f + ant.id * 0.1f) % 1f
                rect(batch, art.circle, hx + (k - 1) * 0.12f, hy + t * 0.3f, 0.09f, 0.09f, Palette.resource(ResourceType.SOIL), 1f - t)
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
        val frames = art.creatures[c.kind]!!
        val size = when (c.kind) { CreatureKind.SPIDER, CreatureKind.CAVE_HUNTER -> 1.3f; CreatureKind.CENTIPEDE -> 1.2f; CreatureKind.BEETLE_LARVA -> 0.7f; else -> 0.9f }
        val f = frames[(c.walkPhase.toInt()) % 2]
        val tint = if (sim.time - c.lastHit < 0.2) Palette.hex("FFB0A0") else Color.WHITE
        batch.setColor(tint)
        batch.draw(f, x - size / 2, y - size / 2, size / 2, size / 2, size, size, 1f, 1f, -c.facing * MathUtils.radiansToDegrees)
        if (c.kind.hostile && !settings.lowQuality) rect(batch, art.ring, x - size * 0.6f, y - size * 0.6f, size * 1.2f, size * 1.2f, Palette.red, 0.35f + sin(anim * 6f) * 0.1f)
        if (c.hp < c.maxHp) bar(batch, x - 0.4f, y + size * 0.45f, 0.8f, c.hp / c.maxHp, Palette.red)
    }

    private fun drawMilitaryMarkers(batch: SpriteBatch, sim: Simulation) {
        for ((i, o) in sim.orders.withIndex()) {
            if (o.kind != OrderKind.GUARD && o.kind != OrderKind.RALLY && o.kind != OrderKind.AMBUSH) continue
            if (sim.squadSize(i) == 0) continue
            val c = if (i == 0) Palette.red else Palette.hex("6B7FD7")
            rect(batch, art.flag, o.x + 0.1f, -o.y - 1f, 0.7f, 1f, c)
        }
    }

    private fun drawOverlay(batch: SpriteBatch, sim: Simulation, view: ViewState) {
        view.selectedCell?.let { (x, y) ->
            val c = Palette.accent
            rect(batch, art.white, x.toFloat(), -y - 1f, 1f, 0.07f, c); rect(batch, art.white, x.toFloat(), -y - 0.07f, 1f, 0.07f, c)
            rect(batch, art.white, x.toFloat(), -y - 1f, 0.07f, 1f, c); rect(batch, art.white, x + 0.93f, -y - 1f, 0.07f, 1f, c)
        }
        val ghost = view.ghostType
        if (ghost != null && view.ghostX != null) {
            val gx = view.ghostX!!
            val gy = view.ghostY!!
            val ok = sim.checkRoomPlacement(ghost, gx, gy).ok
            val c = if (ok) Palette.green else Palette.red
            rect(batch, art.white, gx.toFloat(), -(gy + ghost.height).toFloat(), ghost.width.toFloat(), ghost.height.toFloat(), c, 0.32f)
            rect(batch, art.stripes, gx.toFloat(), -(gy + ghost.height).toFloat(), ghost.width.toFloat(), ghost.height.toFloat(), Color.WHITE, 0.25f)
        }
    }
}
