package com.yeraltiimparatorlugu.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import com.yeraltiimparatorlugu.sim.Ant
import com.yeraltiimparatorlugu.sim.AntActivity
import com.yeraltiimparatorlugu.sim.AntType
import com.yeraltiimparatorlugu.sim.BroodStage
import com.yeraltiimparatorlugu.sim.Hash
import com.yeraltiimparatorlugu.sim.ResourceType
import com.yeraltiimparatorlugu.sim.Room
import com.yeraltiimparatorlugu.sim.RoomState
import com.yeraltiimparatorlugu.sim.RoomType
import com.yeraltiimparatorlugu.sim.Simulation
import com.yeraltiimparatorlugu.sim.StorageKind
import com.yeraltiimparatorlugu.sim.Terrain
import kotlin.math.cos
import kotlin.math.sin

/**
 * Yeraltı kesit görünümü. Dünya birimi = 1 hücre. Hücre (x, y) ekranda (x, -y-1) dikdörtgenine çizilir
 * (derinlik aşağı doğru artar). Yalnızca kamerada görünen hücreler çizilir.
 */
class WorldRenderer(private val art: Art, private val settings: Settings) {
    val camera = OrthographicCamera()
    private val tmp = Color()
    private var anim = 0f

    fun resize(w: Int, h: Int) {
        val cellsWide = 13f
        camera.viewportWidth = cellsWide
        camera.viewportHeight = cellsWide * h / w.coerceAtLeast(1)
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
        drawSurface(batch, sim, left, right, x0, x1)
        drawOrders(batch, sim, x0, x1, y0, y1)
        drawPiles(batch, sim, x0, x1, y0, y1)
        drawPheromones(batch, sim)
        drawAnts(batch, sim, left - 2, right + 2, -top - 2, -bottom + 2)
        drawOverlay(batch, sim, view)
        batch.setColor(Color.WHITE)
        batch.end()
    }

    private fun rect(batch: SpriteBatch, r: TextureRegion, x: Float, y: Float, w: Float, h: Float, c: Color, a: Float = c.a) {
        batch.setColor(c.r, c.g, c.b, a)
        batch.draw(r, x, y, w, h)
    }

    /** Hücre (x, y)'nin ekran dikdörtgeni: sol-alt köşe (x, -y-1). */
    private fun cell(batch: SpriteBatch, r: TextureRegion, x: Int, y: Int, c: Color = Color.WHITE, a: Float = c.a) =
        rect(batch, r, x.toFloat(), -y - 1f, 1f, 1f, c, a)

    private fun drawSky(batch: SpriteBatch, sim: Simulation, left: Float, right: Float, top: Float) {
        if (top <= 0f) return
        // Gün içinde gökyüzü tonu: öğle açık mavi, akşam turuncumsu (koyu gece yok).
        val f = sim.dayFraction
        val warm = MathUtils.clamp(1f - kotlin.math.abs(f - 0.5f) * 2.4f, 0f, 1f)
        val bands = 10
        val h = top / bands
        for (i in 0 until bands) {
            val t = i / (bands - 1f)
            tmp.set(Palette.skyBottom).lerp(Palette.skyTop, t)
            tmp.lerp(Palette.hex("FFD7A8"), (1f - warm) * 0.35f * (1f - t))
            rect(batch, art.white, left - 1, i * h, right - left + 2, h + 0.01f, tmp, 1f)
        }
        if (!settings.lowQuality) {
            val sunX = MathUtils.lerp(left - 2f, right + 2f, f)
            val sunY = 6f + sin(f * MathUtils.PI) * 4f
            rect(batch, art.sun, sunX - 1.5f, sunY - 1.5f, 3f, 3f, Color.WHITE)
            for (i in 0..3) {
                val cx = ((i * 23.7f + anim * 0.25f) % 60f) - 30f + MathUtils.floor(left / 60f) * 60f
                for (k in 0..1) rect(batch, art.cloud, cx + k * 60f, 4f + i * 1.7f, 4.5f, 2.2f, Color.WHITE)
            }
        }
    }

    private fun variant(x: Int, y: Int) = ((Hash.mix(7, x, y, 1) ushr 3) % 3).toInt()

    private fun depthTint(y: Int): Float = if (settings.lowQuality) 1f else 1f - minOf(0.16f, y / 900f)

    private fun drawUnderground(batch: SpriteBatch, sim: Simulation, x0: Int, x1: Int, y0: Int, y1: Int) {
        val w = sim.world
        for (y in y0..y1) {
            val dt = depthTint(y)
            tmp.set(dt, dt, dt, 1f)
            for (x in x0..x1) {
                val t = w.get(x, y)
                if (t.walkable) {
                    val room = sim.roomAt(x, y)
                    if (room != null) {
                        val rc = Palette.room(room.type)
                        cell(batch, art.tunnel[variant(x, y)], x, y, Color(rc.r * dt, rc.g * dt, rc.b * dt, 1f))
                    } else {
                        cell(batch, art.tunnel[variant(x, y)], x, y, tmp)
                    }
                    // Tavan gölgesi ve yan duvar gölgesi: kesite derinlik hissi verir.
                    if (!w.isWalkable(x, y - 1)) rect(batch, art.white, x.toFloat(), -y - 0.22f, 1f, 0.22f, Palette.tunnelShade, 0.55f)
                    if (!w.isWalkable(x - 1, y)) rect(batch, art.white, x.toFloat(), -y - 1f, 0.1f, 1f, Palette.tunnelShade, 0.4f)
                    if (!w.isWalkable(x + 1, y)) rect(batch, art.white, x + 0.9f, -y - 1f, 0.1f, 1f, Palette.tunnelShade, 0.25f)
                } else {
                    val tex = if (w.isRevealed(x, y)) art.tiles[t]!![variant(x, y)] else art.unexplored[variant(x, y)]
                    cell(batch, tex, x, y, tmp)
                    // Açıklığa bakan kenar: aydınlık bir şerit (izometrik kesit kenarı).
                    if (w.isWalkable(x, y + 1) && y + 1 >= 0) rect(batch, art.white, x.toFloat(), -y - 1f, 1f, 0.12f, Color.WHITE, 0.35f)
                }
            }
        }
    }

    private fun drawRooms(batch: SpriteBatch, sim: Simulation, x0: Int, x1: Int, y0: Int, y1: Int) {
        for (r in sim.rooms) {
            if (r.x > x1 || r.x + r.width < x0 || r.y > y1 || r.y + r.height < y0) continue
            val c = Palette.room(r.type)
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
                rect(batch, art.white, rx + 0.3f, ry + r.height - 0.45f, r.width - 0.6f, 0.16f, Palette.uiText, 0.35f)
                rect(batch, art.white, rx + 0.3f, ry + r.height - 0.45f, (r.width - 0.6f) * p.coerceIn(0f, 1f), 0.16f, Palette.accent, 1f)
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
            RoomType.QUEEN_CHAMBER -> {
                rect(batch, art.bed, rx + r.width / 2f - 1.3f, floor, 2.6f, 0.7f, Palette.hex("FFB59A"))
            }
            RoomType.FOOD_STORAGE, RoomType.MATERIAL_STORAGE -> {
                val kind = if (r.type == RoomType.FOOD_STORAGE) StorageKind.FOOD else StorageKind.MATERIAL
                val types = ResourceType.entries.filter { it.storage == kind && sim.resources[it] > 0 }
                val fill = sim.resources.totalOf(kind).toFloat() / sim.capacityOf(kind).coerceAtLeast(1)
                val n = (fill * r.width * 2 * r.height).toInt().coerceIn(if (types.isEmpty()) 0 else 1, r.width * 2 * (r.height - 1))
                for (i in 0 until n) {
                    val t = types[i % types.size]
                    val col = i % (r.width * 2)
                    val row = i / (r.width * 2)
                    rect(batch, art.icons[t]!!, rx + 0.05f + col * 0.48f, floor + row * 0.42f, 0.48f, 0.48f, Color.WHITE)
                }
            }
            RoomType.REST_ROOM -> for (i in 0 until minOf(r.width, 2 + r.level)) rect(batch, art.bed, rx + 0.15f + i * (r.width - 0.3f) / (2 + r.level), floor, 0.9f, 0.45f, Color.WHITE)
            RoomType.FUNGUS_FARM -> {
                val n = minOf(r.leafBuffer, r.width * 3)
                for (i in 0 until n) {
                    val grow = if (i == 0) 0.6f + r.growProgress * 0.4f else 1f
                    val s = 0.42f * grow
                    rect(batch, art.mushroom, rx + 0.2f + (i % (r.width * 2)) * 0.45f, floor + (i / (r.width * 2)) * 0.5f, s, s, Color.WHITE)
                }
                if (r.leafBuffer == 0) rect(batch, art.icons[ResourceType.LEAF]!!, rx + r.width / 2f - 0.3f, floor + 0.6f, 0.6f, 0.6f, Color.WHITE, 0.35f)
            }
            else -> {}
        }
        // Yavrular
        var i = 0
        for (b in sim.brood) {
            if (b.roomId != r.id) continue
            val col = i % (r.width * 2)
            val row = i / (r.width * 2)
            val bx = rx + 0.15f + col * 0.48f
            val by = floor + 0.55f + row * 0.4f
            when (b.stage) {
                BroodStage.EGG -> rect(batch, art.egg, bx, by, 0.3f, 0.3f, Color.WHITE)
                BroodStage.LARVA -> {
                    val wob = if (settings.lowQuality) 0f else sin(anim * 3f + b.id) * 0.03f
                    rect(batch, art.larva, bx, by + wob, 0.45f, 0.3f, if (b.fed < 0.3f) Palette.hex("FFD0C0") else Color.WHITE)
                }
                BroodStage.PUPA -> rect(batch, art.pupa, bx, by, 0.45f, 0.3f, Color.WHITE)
            }
            i++
        }
    }

    private fun drawSurface(batch: SpriteBatch, sim: Simulation, left: Float, right: Float, x0: Int, x1: Int) {
        rect(batch, art.white, left - 1, -0.12f, right - left + 2, 0.24f, Palette.grass, 1f)
        rect(batch, art.white, left - 1, -0.18f, right - left + 2, 0.07f, Palette.grassDark, 1f)
        if (!settings.lowQuality) for (x in x0..x1) {
            val v = variant(x, -1)
            rect(batch, art.tuft, x + v * 0.2f, 0.05f, 0.8f, 0.4f, Color.WHITE)
            if (v == 0 && (x % 5 == 0)) rect(batch, art.flower, x + 0.5f, 0.05f, 0.35f, 0.55f, Color.WHITE)
        }
        // Giriş tüneli işareti ve yüzeye atılan toprak yığını
        if (sim.surfaceMound > 0) {
            val s = minOf(3.5f, 0.6f + kotlin.math.sqrt(sim.surfaceMound.toFloat()) * 0.12f)
            rect(batch, art.circle, sim.dumpX + 0.5f - s, -s * 0.35f, s * 2f, s * 0.9f, Palette.resource(ResourceType.SOIL))
        }
        for (s in sim.sources) {
            if (s.x < x0 || s.x > x1) continue
            val scale = 0.55f + 0.45f * (s.amount.toFloat() / s.maxAmount)
            rect(batch, art.sources[s.kind]!!, s.x + 0.5f - 0.7f * scale, 0f, 1.4f * scale, 1.4f * scale, Color.WHITE)
        }
    }

    private fun drawOrders(batch: SpriteBatch, sim: Simulation, x0: Int, x1: Int, y0: Int, y1: Int) {
        val pulse = if (settings.lowQuality) 0.5f else 0.42f + sin(anim * 4f) * 0.1f
        for (o in sim.digOrders.values) {
            if (o.x < x0 || o.x > x1 || o.y < y0 || o.y > y1) continue
            cell(batch, art.stripes, o.x, o.y, if (o.roomId == -1) Palette.orange else Palette.accent, pulse)
            if (o.progress > 0f) {
                val work = sim.world.get(o.x, o.y).digWork.coerceAtLeast(0.1f)
                rect(batch, art.white, o.x + 0.1f, -o.y - 0.9f, 0.8f * (o.progress / work).coerceIn(0f, 1f), 0.12f, Palette.yellow, 1f)
            }
        }
    }

    private fun drawPiles(batch: SpriteBatch, sim: Simulation, x0: Int, x1: Int, y0: Int, y1: Int) {
        for (p in sim.piles.values) {
            if (p.x < x0 || p.x > x1 || p.y < y0 || p.y > y1) continue
            val n = minOf(3, 1 + p.amount / 4)
            for (i in 0 until n) rect(batch, art.icons[p.type]!!, p.x + 0.1f + i * 0.22f, -p.y - 0.95f + (i % 2) * 0.1f, 0.45f, 0.45f, Color.WHITE)
        }
    }

    private fun drawPheromones(batch: SpriteBatch, sim: Simulation) {
        for (m in sim.pheromones.markers) {
            val c = Palette.pheromone(m.type)
            val r = m.type.radius + 0.5f
            val cx = m.x + 0.5f
            val cy = -m.y - 0.5f
            rect(batch, art.circle, cx - r, cy - r, r * 2, r * 2, c, 0.13f * m.strength + 0.04f)
            val pr = if (settings.lowQuality) r else r * (0.35f + ((anim * 0.5f + m.id * 0.13f) % 1f) * 0.65f)
            rect(batch, art.ring, cx - pr, cy - pr, pr * 2, pr * 2, c, 0.5f * m.strength + 0.1f)
            rect(batch, art.circle, cx - 0.25f, cy - 0.25f, 0.5f, 0.5f, c, 0.9f)
        }
    }

    private fun drawAnts(batch: SpriteBatch, sim: Simulation, xMin: Float, xMax: Float, yMin: Float, yMax: Float) {
        val a = sim.alpha
        for (ant in sim.ants) {
            val x = MathUtils.lerp(ant.prevX, ant.x, a)
            val y = MathUtils.lerp(ant.prevY, ant.y, a)
            if (x < xMin || x > xMax || y < yMin || y > yMax) continue
            drawAnt(batch, ant, x, -y)
        }
    }

    private fun drawAnt(batch: SpriteBatch, ant: Ant, x: Float, y: Float) {
        val queen = ant.type == AntType.QUEEN
        val size = if (queen) 1.5f else 0.78f
        val moving = ant.activity != AntActivity.IDLE && ant.activity != AntActivity.RESTING || ant.path != null
        val frames = if (queen) art.queenFrames else art.antFrames
        val frame = if (moving) frames[(ant.walkPhase.toInt()) % frames.size] else frames[0]
        val rot = if (queen) 0f else -ant.facing * MathUtils.radiansToDegrees
        val tint = when {
            ant.starving -> Palette.hex("FFB0A0")
            ant.activity == AntActivity.RESTING -> Palette.hex("C8D4FF")
            else -> Color.WHITE
        }
        batch.setColor(tint)
        batch.draw(frame, x - size / 2, y - size / 2, size / 2, size / 2, size, size, 1f, 1f, rot)
        if (queen) rect(batch, art.crown, x + 0.05f, y + 0.28f, 0.42f, 0.28f, Color.WHITE)
        val carry = ant.carryType
        if (carry != null && ant.carryAmount > 0) {
            val hx = x + cos(ant.facing) * 0.36f
            val hy = y - sin(ant.facing) * 0.36f
            rect(batch, art.icons[carry]!!, hx - 0.18f, hy - 0.18f, 0.36f, 0.36f, Color.WHITE)
        }
        if (ant.activity == AntActivity.DIGGING && !settings.lowQuality) {
            val hx = x + cos(ant.facing) * 0.5f
            val hy = y - sin(ant.facing) * 0.5f
            for (k in 0..2) {
                val t = (anim * 3f + k * 0.33f + ant.id * 0.1f) % 1f
                rect(batch, art.circle, hx + (k - 1) * 0.12f, hy + t * 0.3f, 0.09f, 0.09f, Palette.resource(ResourceType.SOIL), 1f - t)
            }
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
