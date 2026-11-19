package com.emirzinciri.robotkomutan.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.emirzinciri.robotkomutan.logic.grid.Dir
import com.emirzinciri.robotkomutan.logic.grid.Pos
import com.emirzinciri.robotkomutan.logic.level.LevelData
import com.emirzinciri.robotkomutan.logic.level.Tile
import com.emirzinciri.robotkomutan.logic.sim.RobotState
import com.emirzinciri.robotkomutan.logic.sim.RobotStatus
import com.emirzinciri.robotkomutan.logic.sim.SimEvent
import com.emirzinciri.robotkomutan.logic.sim.WorldState
import com.emirzinciri.robotkomutan.ui.Assets
import com.emirzinciri.robotkomutan.ui.Theme
import kotlin.math.abs
import kotlin.math.sin

/**
 * İzometrik çizici. Mantık kare ızgaradadır; çizim (x - y, x + y) dönüşümüyle elmas hücrelere yapılır.
 * İki dünya anlık görüntüsü ([from] → [to]) arasında [t] ile ara değer üretir; simülasyonu asla değiştirmez.
 */
class IsoRenderer(private val assets: Assets) {

    private val shapes = ShapeRenderer()
    /** Oyuncunun seçtiği robot görünümü (ilk robot için). */
    var primaryColor: Color? = null
    private val layout = GlyphLayout()

    // Yerleşim
    var tw = 64f; private set
    private var th = 32f
    private var ox = 0f
    private var oy = 0f

    /** Haritayı verilen alana sığdırır. */
    fun fit(level: LevelData, x: Float, y: Float, w: Float, h: Float) {
        val n = (level.width + level.height).toFloat()
        val wallH = 0.62f
        tw = minOf(w / (n / 2f + 0.4f), h / (n / 4f + wallH + 0.9f), 150f)
        th = tw / 2f
        // Izgaranın ekran sınırları: sx ∈ [-(h)·tw/2, w·tw/2], sy ∈ [-(w+h)·th/2, 0]
        val minSx = -level.height * tw / 2f
        val maxSx = level.width * tw / 2f
        ox = x + w / 2f - (minSx + maxSx) / 2f
        val gridH = n * th / 2f + wallH * tw
        oy = y + h / 2f + gridH / 2f - wallH * tw * 0.35f
    }

    /** Hücre merkezinin ekran konumu (z = 0). */
    fun sx(gx: Float, gy: Float) = ox + (gx - gy) * tw / 2f
    fun sy(gx: Float, gy: Float) = oy - (gx + gy + 1f) * th / 2f

    // ------------------------------------------------------------------ çizim

    class Frame(
        val level: LevelData,
        val from: WorldState,
        val to: WorldState,
        val t: Float,
        val events: List<SimEvent>,
        val time: Float,
        val showGrid: Boolean,
        val focusRobot: String?,
        val energyOn: Boolean,
    )

    private data class Drawable(val depth: Float, val order: Int, val draw: () -> Unit)
    private val list = ArrayList<Drawable>()

    fun draw(batch: Batch, f: Frame, parentAlpha: Float = 1f) {
        batch.end()
        shapes.projectionMatrix = batch.projectionMatrix
        shapes.transformMatrix = batch.transformMatrix
        com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        drawFloors(f)
        list.clear()
        collectObjects(f)
        list.sortWith(compareBy<Drawable>({ it.depth }, { it.order }))
        for (d in list) d.draw()
        drawBeams(f)
        shapes.end()
        batch.begin()
        drawLabels(batch, f)
    }

    private val tmp = Color()

    private fun c(color: Color, alpha: Float = 1f): Color = tmp.set(color).also { it.a = color.a * alpha }

    private fun diamond(cx: Float, cy: Float, w: Float, h: Float, color: Color) {
        shapes.color = color
        shapes.triangle(cx - w / 2, cy, cx, cy + h / 2, cx + w / 2, cy)
        shapes.triangle(cx - w / 2, cy, cx, cy - h / 2, cx + w / 2, cy)
    }

    /** Ayak izi (fw × fw hücre) olan dik prizma: üst, sol (+y) ve sağ (+x) yüzler. */
    private fun block(cx: Float, cy: Float, fw: Float, z0: Float, z1: Float, top: Color, left: Color, right: Color) {
        val w = tw * fw; val h = th * fw
        val wx = cx - w / 2; val ex = cx + w / 2; val sy = cy - h / 2
        shapes.color = left
        shapes.triangle(wx, cy + z1, cx, sy + z1, cx, sy + z0)
        shapes.triangle(wx, cy + z1, cx, sy + z0, wx, cy + z0)
        shapes.color = right
        shapes.triangle(cx, sy + z1, ex, cy + z1, ex, cy + z0)
        shapes.triangle(cx, sy + z1, ex, cy + z0, cx, sy + z0)
        diamond(cx, cy + z1, w, h, top)
    }

    private fun shadeOf(color: Color, f: Float) = Color(color.r * f, color.g * f, color.b * f, color.a)

    private fun drawFloors(f: Frame) {
        val lv = f.level
        val slab = tw * 0.09f
        // Önce yan yüzler (derinlik sırası), sonra üstler.
        for (d in 0..(lv.width + lv.height)) for (x in 0 until lv.width) {
            val y = d - x
            if (y !in 0 until lv.height) continue
            val p = Pos(x, y)
            val tile = lv.tile(p)
            if (tile == Tile.VOID) continue
            val cx = sx(x.toFloat(), y.toFloat()); val cy = sy(x.toFloat(), y.toFloat())
            val top = when {
                tile == Tile.PIT && p !in f.to.filledPits -> Theme.pitColor
                f.showGrid && (x + y) % 2 == 1 -> Theme.floorTopAlt
                else -> Theme.floorTop
            }
            block(cx, cy, 1f, -slab, 0f, top, Theme.floorSide, shadeOf(Theme.floorSide, 0.88f))
            when (tile) {
                Tile.PIT -> {
                    if (p in f.to.filledPits) {
                        diamond(cx, cy, tw * 0.86f, th * 0.86f, Theme.mix(Theme.boxColor, Theme.floorTop, 0.45f))
                    } else {
                        diamond(cx, cy, tw * 0.78f, th * 0.78f, shadeOf(Theme.pitColor, 0.62f))
                        diamond(cx, cy - th * 0.08f, tw * 0.56f, th * 0.56f, shadeOf(Theme.pitColor, 0.45f))
                    }
                }
                Tile.EXIT -> {
                    val pulse = 0.5f + 0.5f * sin(f.time * 3f)
                    diamond(cx, cy, tw * 0.9f, th * 0.9f, Theme.mix(Theme.exitColor, Theme.white, 0.55f))
                    diamond(cx, cy, tw * (0.62f + 0.08f * pulse), th * (0.62f + 0.08f * pulse), Theme.exitColor)
                    diamond(cx, cy, tw * 0.3f, th * 0.3f, Theme.mix(Theme.exitColor, Theme.white, 0.6f))
                }
                Tile.TARGET -> {
                    diamond(cx, cy, tw * 0.82f, th * 0.82f, Theme.targetColor)
                    diamond(cx, cy, tw * 0.6f, th * 0.6f, top)
                    diamond(cx, cy, tw * 0.22f, th * 0.22f, Theme.targetColor)
                }
                Tile.PLATE -> {
                    val pressed = f.to.boxes.contains(p) || f.to.robots.any { it.alive && it.pos == p }
                    val z = if (pressed) 1f else tw * 0.05f
                    if (lv.channel(p) in lv.andChannels) diamond(cx, cy, tw * 0.9f, th * 0.9f, Theme.purple)
                    block(cx, cy, 0.66f, 0f, z, Theme.plateColor, shadeOf(Theme.plateColor, 0.8f), shadeOf(Theme.plateColor, 0.7f))
                }
                Tile.CONV_N, Tile.CONV_E, Tile.CONV_S, Tile.CONV_W -> drawConveyor(tile.conveyorDir!!, x, y, cx, cy, f.time)
                Tile.CHARGER -> {
                    diamond(cx, cy, tw * 0.8f, th * 0.8f, Theme.mix(Theme.energyColor, Theme.white, 0.5f))
                    diamond(cx, cy, tw * 0.5f, th * 0.5f, Theme.energyColor)
                    diamond(cx, cy, tw * 0.28f, th * 0.28f, Theme.white)
                }
                else -> Unit
            }
        }
    }

    private val convBase = Theme.rgb(0x7E8CA8)
    private val convStripe = Theme.rgb(0xFFD66B)

    /** Bant: koyu şerit ve akış yönünde kayan oklar. */
    private fun drawConveyor(d: Dir, x: Int, y: Int, cx: Float, cy: Float, time: Float) {
        diamond(cx, cy, tw * 0.92f, th * 0.92f, convBase)
        val gx = x.toFloat(); val gy = y.toFloat()
        val phase = (time * 1.2f) % 1f
        for (k in 0..1) {
            val o = ((k * 0.5f + phase) % 1f) - 0.5f
            val px = gx + d.dx * o * 0.8f; val py = gy + d.dy * o * 0.8f
            val ax = sx(px, py); val ay = sy(px, py)
            val (fx, fy) = dirScreen(dirAngle(d)); val (sx2, sy2) = dirScreen(dirAngle(d) + 90f)
            shapes.color = convStripe
            shapes.triangle(ax + fx * 0.18f, ay + fy * 0.18f, ax - fx * 0.08f + sx2 * 0.18f, ay - fy * 0.08f + sy2 * 0.18f,
                ax - fx * 0.08f - sx2 * 0.18f, ay - fy * 0.08f - sy2 * 0.18f)
        }
    }

    private fun drawBeams(f: Frame) {
        val beams = com.emirzinciri.robotkomutan.logic.sim.Environment.beams(f.level, f.to)
        val flicker = 0.75f + 0.25f * sin(f.time * 18f)
        for ((src, cells) in beams) {
            if (cells.isEmpty()) continue
            val last = cells.last()
            val d = f.level.laserDir(src)
            val z = tw * 0.2f
            val x1 = sx(src.x.toFloat(), src.y.toFloat()); val y1 = sy(src.x.toFloat(), src.y.toFloat()) + z
            val ex = last.x + d.dx * 0.45f; val ey = last.y + d.dy * 0.45f
            val x2 = sx(ex, ey); val y2 = sy(ex, ey) + z
            shapes.color = c(Theme.red, 0.25f * flicker)
            shapes.rectLine(x1, y1, x2, y2, tw * 0.12f)
            shapes.color = c(Theme.red, 0.9f * flicker)
            shapes.rectLine(x1, y1, x2, y2, tw * 0.035f)
            for (p in cells) diamond(sx(p.x.toFloat(), p.y.toFloat()), sy(p.x.toFloat(), p.y.toFloat()), tw * 0.5f, th * 0.5f, c(Theme.red, 0.12f))
        }
    }

    private fun collectObjects(f: Frame) {
        val lv = f.level
        for (p in lv.allPositions) {
            val tile = lv.tile(p)
            val depth = (p.x + p.y).toFloat()
            val cx = sx(p.x.toFloat(), p.y.toFloat()); val cy = sy(p.x.toFloat(), p.y.toFloat())
            when {
                tile == Tile.WALL -> list += Drawable(depth, 0) {
                    block(cx, cy, 1f, 0f, tw * 0.42f, Theme.wallTop, Theme.wallSide, shadeOf(Theme.wallSide, 0.86f))
                    diamond(cx, cy + tw * 0.42f, tw * 0.7f, th * 0.7f, Theme.mix(Theme.wallTop, Theme.white, 0.18f))
                }
                tile.isDoor -> list += Drawable(depth, 0) { drawDoor(f, p, cx, cy) }
                tile.isConsole -> list += Drawable(depth, 0) { drawConsole(f, p, tile, cx, cy) }
            }
        }
        // Enerji hücreleri
        for ((p, _) in f.to.cells) {
            val cx = sx(p.x.toFloat(), p.y.toFloat()); val cy = sy(p.x.toFloat(), p.y.toFloat())
            list += Drawable((p.x + p.y).toFloat(), 1) {
                val bob = sin(f.time * 2.6f + p.x) * tw * 0.04f
                diamond(cx, cy, tw * 0.36f, th * 0.36f, Color(0f, 0f, 0f, 0.1f))
                block(cx, cy, 0.22f, tw * 0.16f + bob, tw * 0.36f + bob, Theme.mix(Theme.energyColor, Theme.white, 0.4f), Theme.energyColor, shadeOf(Theme.energyColor, 0.85f))
            }
        }
        // Toplanmakta olan hücreler: kısa bir parlama
        for (e in f.events) if (e is SimEvent.EnergyCollected) {
            val r = f.to.robots.firstOrNull { it.id == e.robot } ?: continue
            if (r.pos !in f.from.cells) continue
            val cx = sx(r.pos.x.toFloat(), r.pos.y.toFloat()); val cy = sy(r.pos.x.toFloat(), r.pos.y.toFloat())
            list += Drawable((r.pos.x + r.pos.y).toFloat(), 3) {
                val k = 1f - f.t
                block(cx, cy, 0.22f * k, tw * (0.4f + f.t * 0.6f), tw * (0.6f + f.t * 0.6f), Theme.mix(Theme.energyColor, Theme.white, 0.4f), Theme.energyColor, Theme.energyColor)
            }
        }
        // Kutular (hareket olaylarıyla ara değer)
        val moving = HashMap<Pos, Pos>()
        for (e in f.events) if (e is SimEvent.BoxMoved) moving[e.to] = e.from
        for (b in f.to.boxes) {
            val from = moving[b] ?: b
            val e = Interpolation.smooth.apply(f.t)
            val gx = MathUtils.lerp(from.x.toFloat(), b.x.toFloat(), e)
            val gy = MathUtils.lerp(from.y.toFloat(), b.y.toFloat(), e)
            list += Drawable(gx + gy, 2) { drawBox(sx(gx, gy), sy(gx, gy), 0f) }
        }
        // Çukura düşen / boşluğa düşen kutular
        for (e in f.events) {
            val (from, to, lost) = when (e) {
                is SimEvent.BoxMoved -> if (e.to !in f.to.boxes) Triple(e.from, e.to, true) else continue
                else -> continue
            }
            if (!lost) continue
            val k = Interpolation.smooth.apply(f.t)
            val gx = MathUtils.lerp(from.x.toFloat(), to.x.toFloat(), minOf(1f, k * 1.6f))
            val gy = MathUtils.lerp(from.y.toFloat(), to.y.toFloat(), minOf(1f, k * 1.6f))
            val drop = if (k > 0.6f) (k - 0.6f) / 0.4f else 0f
            list += Drawable(gx + gy, 2) { drawBox(sx(gx, gy), sy(gx, gy), -drop * tw * 0.5f, 1f - drop) }
        }
        // Bırakılan kutular yerine "iniyor" görünümü ekleme: kutu zaten f.to.boxes'ta.
        // Robotlar
        f.to.robots.forEachIndexed { i, r -> collectRobot(f, i, r) }
        // Düşmanlar
        for (e in f.to.enemies) collectEnemy(f, e)
    }

    private fun drawBox(cx: Float, cy: Float, z: Float, alpha: Float = 1f) {
        val top = c(Theme.mix(Theme.boxColor, Theme.white, 0.25f), alpha).cpy()
        val left = c(Theme.boxColor, alpha).cpy()
        val right = c(shadeOf(Theme.boxColor, 0.82f), alpha).cpy()
        block(cx, cy, 0.66f, z, z + tw * 0.36f, top, left, right)
        diamond(cx, cy + z + tw * 0.36f, tw * 0.4f, th * 0.4f, c(Theme.mix(Theme.boxColor, Theme.white, 0.1f), alpha).cpy())
    }

    private fun drawDoor(f: Frame, p: Pos, cx: Float, cy: Float) {
        val wasOpen = p in f.from.openDoors
        val isOpen = p in f.to.openDoors
        val k = Interpolation.smooth.apply(f.t)
        val open = when {
            wasOpen == isOpen -> if (isOpen) 1f else 0f
            isOpen -> k
            else -> 1f - k
        }
        val color = if (f.level.tile(p) == Tile.DOOR) Theme.doorColor else Theme.doorInvColor
        val h = tw * 0.5f * (1f - open) + tw * 0.04f
        // Çerçeve
        block(cx, cy, 1f, 0f, tw * 0.04f, Theme.mix(color, Theme.white, 0.55f), shadeOf(color, 0.8f), shadeOf(color, 0.7f))
        val a = 0.55f + 0.4f * (1f - open)
        block(cx, cy, 0.8f, 0f, h, c(Theme.mix(color, Theme.white, 0.3f), a).cpy(), c(color, a).cpy(), c(shadeOf(color, 0.82f), a).cpy())
        if (open < 0.5f) {
            // Yatay çizgiler: kapalı kapıyı belirginleştirir
            for (i in 1..2) {
                val z = h * i / 3f
                diamond(cx, cy + z, tw * 0.8f, th * 0.8f, c(Theme.white, 0.18f).cpy())
            }
        }
    }

    private fun drawConsole(f: Frame, p: Pos, tile: Tile, cx: Float, cy: Float) {
        val base = Theme.mix(Theme.wallTop, Theme.white, 0.35f)
        block(cx, cy, 0.72f, 0f, tw * 0.3f, base, Theme.mix(Theme.wallSide, Theme.white, 0.2f), Theme.mix(Theme.wallSide, Theme.white, 0.1f))
        val ch = f.level.channel(p)
        val top = cy + tw * 0.3f
        when (tile) {
            Tile.SWITCH -> {
                val on = ch in f.to.toggled
                val col = if (on) Theme.green else Theme.red
                block(cx, top - th * 0.02f, 0.34f, 0f, tw * 0.07f, Theme.mix(col, Theme.white, 0.3f), col, shadeOf(col, 0.85f))
            }
            Tile.TIMER -> {
                val left = f.to.timers[ch] ?: 0
                val col = if (left > 0) Theme.yellow else Theme.orange
                block(cx, top, 0.34f, 0f, tw * 0.07f, Theme.mix(col, Theme.white, 0.3f), col, shadeOf(col, 0.85f))
            }
            Tile.LASER -> {
                val on = !com.emirzinciri.robotkomutan.logic.sim.Environment.channelActive(f.level, f.to, ch)
                val col = if (on) Theme.red else Theme.inkFaint
                block(cx, top, 0.36f, 0f, tw * 0.12f, Theme.mix(col, Theme.white, 0.3f), col, shadeOf(col, 0.85f))
                val (dx, dy) = dirScreen(dirAngle(f.level.laserDir(p)))
                shapes.color = col
                shapes.circle(cx + dx * 0.36f, top + tw * 0.06f + dy * 0.36f, tw * 0.05f, 10)
            }
            Tile.SOCKET -> {
                val need = f.level.socketNeed(p)
                val have = minOf(need, f.to.socketCharge[p] ?: 0)
                val col = if (have >= need) Theme.green else Theme.energyColor
                diamond(cx, top, tw * 0.5f, th * 0.5f, Theme.mix(Theme.ink, Theme.white, 0.55f))
                val k = have.toFloat() / need
                if (k > 0f) diamond(cx, top, tw * 0.5f * k, th * 0.5f * k, col)
            }
            else -> Unit
        }
    }

    // ------------------------------------------------------------------ robotlar

    private fun dirAngle(d: Dir) = when (d) { Dir.E -> 0f; Dir.S -> 90f; Dir.W -> 180f; Dir.N -> 270f }

    private fun collectRobot(f: Frame, index: Int, r: RobotState) {
        val prev = f.from.robots.firstOrNull { it.id == r.id } ?: r
        val e = Interpolation.smooth.apply(f.t)
        var gx = MathUtils.lerp(prev.pos.x.toFloat(), r.pos.x.toFloat(), e)
        var gy = MathUtils.lerp(prev.pos.y.toFloat(), r.pos.y.toFloat(), e)
        var z = 0f
        var alpha = 1f
        var scale = 1f
        val evs = f.events.filter {
            when (it) {
                is SimEvent.Bumped -> it.robot == r.id
                is SimEvent.Moved -> it.robot == r.id
                is SimEvent.RobotLost -> it.robot == r.id
                else -> false
            }
        }
        for (ev in evs) when (ev) {
            is SimEvent.Bumped -> {
                val k = sin(f.t * MathUtils.PI) * 0.2f
                gx += (ev.at.x - r.pos.x) * k; gy += (ev.at.y - r.pos.y) * k
            }
            is SimEvent.Moved -> if (ev.jump) z = sin(f.t * MathUtils.PI) * tw * 0.45f
            is SimEvent.RobotLost -> {
                val k = ((f.t - 0.5f) / 0.5f).coerceIn(0f, 1f)
                z -= k * tw * 0.8f; alpha = 1f - k; scale = 1f - 0.3f * k
            }
            else -> Unit
        }
        if (r.status == RobotStatus.LOST && evs.none { it is SimEvent.RobotLost }) return
        val a0 = dirAngle(prev.dir)
        var a1 = dirAngle(r.dir)
        if (a1 - a0 > 180f) a1 -= 360f
        if (a0 - a1 > 180f) a1 += 360f
        val angle = MathUtils.lerp(a0, a1, e)
        val carrying = r.carrying || (prev.carrying && f.t < 0.5f)
        val color = if (index == 0 && primaryColor != null) primaryColor!! else Theme.robotColors[index % Theme.robotColors.size]
        val dim = r.status == RobotStatus.NO_ENERGY
        val focus = f.focusRobot == null || f.focusRobot == r.id
        val shielded = r.shield > 0 || f.events.any { it is SimEvent.ShieldUp && it.robot == r.id }
        val hurt = f.events.any { it is SimEvent.EnemyAttack && it.robot == r.id && !it.blocked }
        list += Drawable(gx + gy + 0.01f, 5) {
            val ox = if (hurt) sin(f.t * 40f) * tw * 0.03f * (1f - f.t) else 0f
            drawRobot(sx(gx, gy) + ox, sy(gx, gy), z, angle, if (hurt && f.t < 0.5f) Theme.red else color, carrying, alpha * (if (focus) 1f else 0.85f), scale, dim, f.time + index)
            if (shielded) {
                val cx = sx(gx, gy); val cy = sy(gx, gy)
                shapes.color = c(Theme.blue, 0.22f + 0.08f * sin(f.time * 6f))
                shapes.ellipse(cx - tw * 0.42f, cy - th * 0.2f, tw * 0.84f, tw * 0.95f, 24)
            }
        }
    }

    private val enemyColors = mapOf(
        com.emirzinciri.robotkomutan.logic.level.EnemyType.PATROL to Theme.rgb(0xD9485F),
        com.emirzinciri.robotkomutan.logic.level.EnemyType.TURRET to Theme.rgb(0x8B5CC7),
        com.emirzinciri.robotkomutan.logic.level.EnemyType.CHASER to Theme.rgb(0xE5652E),
        com.emirzinciri.robotkomutan.logic.level.EnemyType.BOSS to Theme.rgb(0x5B3A8C),
    )

    private fun collectEnemy(f: Frame, e: com.emirzinciri.robotkomutan.logic.sim.EnemyState) {
        val prev = f.from.enemies.firstOrNull { it.id == e.id } ?: e
        val k = Interpolation.smooth.apply(f.t)
        val gx = MathUtils.lerp(prev.pos.x.toFloat(), e.pos.x.toFloat(), k)
        val gy = MathUtils.lerp(prev.pos.y.toFloat(), e.pos.y.toFloat(), k)
        val dying = !e.alive && prev.alive
        if (!e.alive && !dying) return
        val a0 = dirAngle(prev.dir); var a1 = dirAngle(e.dir)
        if (a1 - a0 > 180f) a1 -= 360f
        if (a0 - a1 > 180f) a1 += 360f
        val angle = MathUtils.lerp(a0, a1, k)
        val hit = f.events.any { it is SimEvent.EnemyHit && it.enemy == e.id }
        val attack = f.events.firstOrNull { it is SimEvent.EnemyAttack && it.enemy == e.id } as SimEvent.EnemyAttack?
        val color = enemyColors.getValue(e.type)
        list += Drawable(gx + gy + 0.01f, 5) {
            val alpha = if (dying) 1f - k else 1f
            val shake = if (hit) sin(f.t * 40f) * tw * 0.03f * (1f - f.t) else 0f
            val cx = sx(gx, gy) + shake; val cy = sy(gx, gy)
            val big = if (e.type == com.emirzinciri.robotkomutan.logic.level.EnemyType.BOSS) 1.25f else 1f
            diamond(cx, cy, tw * 0.62f * big, th * 0.62f * big, Color(0.1f, 0.15f, 0.3f, 0.18f * alpha))
            val top = c(Theme.mix(color, Theme.white, if (hit) 0.6f else 0.2f), alpha).cpy()
            val left = c(color, alpha).cpy(); val right = c(shadeOf(color, 0.78f), alpha).cpy()
            val bh = tw * (if (e.type == com.emirzinciri.robotkomutan.logic.level.EnemyType.TURRET) 0.22f else 0.3f) * big
            block(cx, cy, 0.6f * big, tw * 0.03f, bh, top, left, right)
            block(cx, cy, 0.42f * big, bh, bh + tw * 0.14f * big, c(Theme.ink, alpha).cpy(), c(Theme.mix(Theme.ink, color, 0.3f), alpha).cpy(), c(Theme.ink, alpha * 0.9f).cpy())
            // Kırmızı göz bandı baktığı yöne
            val (fx, fy) = dirScreen(angle); val (px, py) = dirScreen(angle + 90f)
            val ey = cy + bh + tw * 0.07f * big
            shapes.color = c(Theme.yellow, alpha)
            shapes.rectLine(cx + fx * 0.3f * big - px * 0.14f, ey + fy * 0.3f * big - py * 0.14f, cx + fx * 0.3f * big + px * 0.14f, ey + fy * 0.3f * big + py * 0.14f, tw * 0.035f)
            if (e.type == com.emirzinciri.robotkomutan.logic.level.EnemyType.BOSS) {
                // Taç
                val tz = cy + bh + tw * 0.14f * big
                shapes.color = c(Theme.yellow, alpha)
                for (i in -1..1) shapes.triangle(cx + i * tw * 0.1f - tw * 0.05f, tz, cx + i * tw * 0.1f + tw * 0.05f, tz, cx + i * tw * 0.1f, tz + tw * 0.1f)
            }
            if (e.type == com.emirzinciri.robotkomutan.logic.level.EnemyType.TURRET) {
                shapes.color = c(Theme.ink, alpha)
                shapes.rectLine(cx, cy + bh * 0.7f, cx + fx * 0.55f, cy + bh * 0.7f + fy * 0.55f, tw * 0.06f)
            }
        }
        // Taret atış izi
        if (attack != null && attack.ranged) {
            val r = f.to.robots.firstOrNull { it.id == attack.robot }
            if (r != null) list += Drawable(999f, 0) {
                val x1 = sx(e.pos.x.toFloat(), e.pos.y.toFloat()); val y1 = sy(e.pos.x.toFloat(), e.pos.y.toFloat()) + tw * 0.2f
                val x2 = sx(r.pos.x.toFloat(), r.pos.y.toFloat()); val y2 = sy(r.pos.x.toFloat(), r.pos.y.toFloat()) + tw * 0.3f
                val a = (1f - f.t)
                shapes.color = c(Theme.purple, a)
                shapes.rectLine(x1, y1, MathUtils.lerp(x1, x2, minOf(1f, f.t * 3f)), MathUtils.lerp(y1, y2, minOf(1f, f.t * 3f)), tw * 0.04f)
            }
        }
    }

    private fun dirScreen(angle: Float): Pair<Float, Float> {
        val rad = angle * MathUtils.degreesToRadians
        val gx = MathUtils.cos(rad); val gy = MathUtils.sin(rad)
        return ((gx - gy) * tw / 2f) to (-(gx + gy) * th / 2f)
    }

    fun drawRobot(cx: Float, cy: Float, z: Float, angle: Float, color: Color, carrying: Boolean, alpha: Float, scale: Float, dim: Boolean, time: Float) {
        val body = if (dim) Theme.mix(color, Theme.inkFaint, 0.6f) else color
        // Gölge
        diamond(cx, cy, tw * 0.62f * scale, th * 0.62f * scale, Color(0.1f, 0.15f, 0.3f, 0.16f * alpha))
        // Yön oku (zeminde)
        val (dx, dy) = dirScreen(angle)
        val tipX = cx + dx * 0.62f; val tipY = cy + dy * 0.62f
        val (px, py) = dirScreen(angle + 90f)
        shapes.color = c(Theme.mix(color, Theme.white, 0.25f), alpha * 0.9f)
        shapes.triangle(tipX, tipY, cx + dx * 0.36f + px * 0.16f, cy + dy * 0.36f + py * 0.16f, cx + dx * 0.36f - px * 0.16f, cy + dy * 0.36f - py * 0.16f)

        val bob = sin(time * 4f) * tw * 0.012f
        val z0 = z + tw * 0.04f + bob
        val bh = tw * 0.23f * scale
        val top = c(Theme.mix(body, Theme.white, 0.28f), alpha).cpy()
        val left = c(body, alpha).cpy()
        val right = c(shadeOf(body, 0.82f), alpha).cpy()
        // Tekerlek tabanı
        block(cx, cy, 0.5f * scale, z0 - tw * 0.04f, z0, c(Theme.ink, alpha * 0.8f).cpy(), c(Theme.ink, alpha * 0.8f).cpy(), c(Theme.ink, alpha * 0.7f).cpy())
        // Gövde
        block(cx, cy, 0.56f * scale, z0, z0 + bh, top, left, right)
        // Kafa
        val hz0 = z0 + bh + tw * 0.02f
        val hh = tw * 0.18f * scale
        val headTop = c(Theme.mix(Theme.white, body, 0.12f), alpha).cpy()
        block(cx, cy, 0.48f * scale, hz0, hz0 + hh, headTop, c(Theme.mix(Theme.white, body, 0.3f), alpha).cpy(), c(Theme.mix(Theme.white, body, 0.42f), alpha).cpy())
        // Gözler: baktığı yönün yüzüne
        val eyeZ = hz0 + hh * 0.52f
        val (fx, fy) = dirScreen(angle)
        val ex = cx + fx * 0.44f * scale; val ey = cy + fy * 0.44f * scale + eyeZ
        val (sxv, syv) = dirScreen(angle + 90f)
        val visible = fy < 0.01f // yüz ekrana dönükse
        val eyeColor = c(if (dim) Theme.inkFaint else Theme.ink, alpha)
        if (visible) {
            val r = tw * 0.045f * scale
            shapes.color = eyeColor
            shapes.circle(ex + sxv * 0.2f, ey + syv * 0.2f, r, 10)
            shapes.circle(ex - sxv * 0.2f, ey - syv * 0.2f, r, 10)
            shapes.color = c(Theme.white, alpha)
            shapes.circle(ex + sxv * 0.2f + r * 0.3f, ey + syv * 0.2f + r * 0.35f, r * 0.35f, 6)
            shapes.circle(ex - sxv * 0.2f + r * 0.3f, ey - syv * 0.2f + r * 0.35f, r * 0.35f, 6)
        } else {
            // Arkadan görünüm: sırt paneli
            shapes.color = c(shadeOf(body, 0.7f), alpha)
            val bx = cx + fx * -0.0f; val by = cy + hz0 + hh * 0.5f
            shapes.circle(bx + fx * 0.1f, by + fy * 0.1f, tw * 0.04f, 8)
        }
        // Başın üstünde yön oku: robot hangi yöne bakarsa baksın görünür.
        run {
            val topY = cy + hz0 + hh
            val tx = cx + fx * 0.34f * scale; val ty = topY + fy * 0.34f * scale
            shapes.color = c(body, alpha)
            shapes.triangle(tx, ty, cx + fx * 0.02f + sxv * 0.2f, topY + fy * 0.02f + syv * 0.2f, cx + fx * 0.02f - sxv * 0.2f, topY + fy * 0.02f - syv * 0.2f)
        }
        // Anten
        val az = hz0 + hh
        shapes.color = c(Theme.ink, alpha * 0.8f)
        shapes.rectLine(cx - fx * 0.12f, cy + az - fy * 0.12f, cx - fx * 0.12f, cy + az - fy * 0.12f + tw * 0.1f, tw * 0.02f)
        shapes.color = c(if (dim) Theme.inkFaint else Theme.yellow, alpha)
        shapes.circle(cx - fx * 0.12f, cy + az - fy * 0.12f + tw * 0.11f, tw * 0.032f, 10)
        if (carrying) drawBox(cx, cy + az + tw * 0.04f, 0f, alpha)
    }

    // ------------------------------------------------------------------ yazılar

    /** Robot üstü enerji etiketleri ve zamanlayıcı sayaçları. */
    private fun drawLabels(batch: Batch, f: Frame) {
        val font = assets.fontSmall
        val old = batch.packedColor
        if (f.energyOn) {
            val e = Interpolation.smooth.apply(f.t)
            for (r in f.to.robots) {
                if (!r.alive) continue
                val prev = f.from.robots.firstOrNull { it.id == r.id } ?: r
                val gx = MathUtils.lerp(prev.pos.x.toFloat(), r.pos.x.toFloat(), e)
                val gy = MathUtils.lerp(prev.pos.y.toFloat(), r.pos.y.toFloat(), e)
                val x = sx(gx, gy); val y = sy(gx, gy) + tw * 0.95f
                pill(batch, x, y, "${r.energy}", if (r.energy <= 1) Theme.red else Theme.ink, "bolt", Theme.energyColor)
            }
        }
        if (f.level.def.enemies.isNotEmpty()) {
            for (r in f.to.robots) if (r.alive) {
                val x = sx(r.pos.x.toFloat(), r.pos.y.toFloat()); val y = sy(r.pos.x.toFloat(), r.pos.y.toFloat()) + tw * (if (f.energyOn) 1.25f else 0.95f)
                pill(batch, x, y, "${r.hp}", if (r.hp <= 1) Theme.red else Theme.ink, "heart", Theme.red)
            }
            for (e in f.to.enemies) if (e.alive) {
                val x = sx(e.pos.x.toFloat(), e.pos.y.toFloat()); val y = sy(e.pos.x.toFloat(), e.pos.y.toFloat()) + tw * 0.85f
                pill(batch, x, y, "${e.hp}", Theme.red, "heart", Theme.purple)
            }
        }
        for (p in f.level.allPositions) {
            val tile = f.level.tile(p)
            if (tile == Tile.TIMER) {
                val left = f.to.timers[f.level.channel(p)] ?: 0
                val label = if (left > 0) "$left" else "${f.level.timerDuration(p)}"
                pill(batch, sx(p.x.toFloat(), p.y.toFloat()), sy(p.x.toFloat(), p.y.toFloat()) + tw * 0.62f, label,
                    if (left > 0) Theme.orange else Theme.inkSoft, null, null)
            } else if (tile == Tile.SOCKET) {
                val need = f.level.socketNeed(p)
                val have = minOf(need, f.to.socketCharge[p] ?: 0)
                pill(batch, sx(p.x.toFloat(), p.y.toFloat()), sy(p.x.toFloat(), p.y.toFloat()) + tw * 0.62f, "$have/$need",
                    if (have >= need) Theme.green else Theme.ink, "bolt", Theme.energyColor)
            }
        }
        for ((p, v) in f.to.cells) {
            val bob = sin(f.time * 2.6f + p.x) * tw * 0.04f
            val x = sx(p.x.toFloat(), p.y.toFloat()); val y = sy(p.x.toFloat(), p.y.toFloat()) + tw * 0.55f + bob
            if (f.energyOn) {
                layout.setText(font, "+$v")
                font.color = Theme.shade(Theme.energyColor, 0.7f)
                font.draw(batch, "+$v", x - layout.width / 2, y + layout.height)
            }
        }
        batch.packedColor = old
    }

    private fun pill(batch: Batch, x: Float, y: Float, text: String, textColor: Color, icon: String?, iconColor: Color?) {
        val font = assets.fontSmall
        layout.setText(font, text)
        val iconW = if (icon != null) 18f else 0f
        val w = layout.width + 18f + iconW
        val h = 28f
        val patch = assets.patch(14)
        batch.setColor(0.15f, 0.2f, 0.32f, 0.15f); patch.draw(batch, x - w / 2, y - 2f, w, h)
        batch.setColor(1f, 1f, 1f, 0.95f); patch.draw(batch, x - w / 2, y, w, h)
        if (icon != null && iconColor != null) {
            batch.color = iconColor
            batch.draw(assets.icons.named(icon), x - w / 2 + 7f, y + 5f, 18f, 18f)
        }
        batch.setColor(1f, 1f, 1f, 1f)
        font.color = textColor
        font.draw(batch, text, x - w / 2 + 9f + iconW, y + h / 2 + layout.height / 2)
    }

    fun dispose() = shapes.dispose()

    companion object {
        fun near(a: Float, b: Float) = abs(a - b) < 0.001f
    }
}
