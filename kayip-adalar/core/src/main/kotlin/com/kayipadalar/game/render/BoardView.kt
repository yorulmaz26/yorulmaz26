package com.kayipadalar.game.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.kayipadalar.game.ui.Palette
import com.kayipadalar.game.ui.Theme
import com.kayipadalar.logic.engine.*
import com.kayipadalar.logic.model.*
import com.kayipadalar.logic.tr
import kotlin.math.abs
import kotlin.math.sin

/** Seçili cosmetik renkleri. */
data class Cosmetics(
    val islandTint: Color? = null,
    val castle: Color? = null,
    val bridge: Color = Color.valueOf("C49A6C"),
    val unitCostume: Color? = null,
    val victory: Color = Color.valueOf("FF7043"),
)

/**
 * İzometrik oyun tahtası. Kural motorundaki [Board] durumunu çizer, dokunma/kaydırma girdisini
 * [Action]'a çevirir ve motor olaylarını animasyonlara dönüştürür.
 */
class BoardView(
    var board: Board,
    private val shapes: ShapeRenderer,
    private val theme: Theme,
    private val cosmetics: Cosmetics,
    private val onAction: (Action) -> Unit,
    private val onSelect: (Island?) -> Unit,
    private val fast: () -> Boolean,
) : Actor() {

    enum class Mode { NORMAL, SWAP, JUMP, UNIT, MERGE }

    var mode = Mode.NORMAL
    var selectedId: String? = null
    var hint: Action? = null
    var inputLocked = false

    private var time = 0f
    private var tw = 120f
    private var th = 60f
    private var ox = 0f
    private var oy = 0f

    private val visPos = HashMap<String, Vector2>()
    private val visHeight = HashMap<String, Float>()
    private val rotAnim = HashMap<String, Float>()
    private val bridgeBirth = HashMap<String, Float>()
    private val unitVis = HashMap<String, Vector2>()
    private val flash = HashMap<String, Float>()
    private val falling = ArrayList<Pair<Island, Float>>()
    private var shake = 0f

    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val max: Float, val color: Color, val size: Float, val gravity: Float)
    private val particles = ArrayList<Particle>()

    private class Floater(val text: String, var x: Float, var y: Float, var life: Float, val color: Color, val big: Boolean)
    private val floaters = ArrayList<Floater>()
    private var banner: String? = null
    private var bannerLife = 0f
    private val layout = GlyphLayout()

    var zoom = 1f
    private var panX = 0f
    private var panY = 0f

    fun cycleZoom() {
        zoom = when { zoom < 1.25f -> 1.5f; zoom < 1.75f -> 2f; else -> 1f }
        if (zoom == 1f) { panX = 0f; panY = 0f }
        clampPan()
    }

    private fun clampPan() {
        val mx = width * (zoom - 1f) / 2f + 40f; val my = height * (zoom - 1f) / 2f + 40f
        panX = panX.coerceIn(-mx, mx); panY = panY.coerceIn(-my, my)
    }

    init {
        addListener(object : InputListener() {
            val px = FloatArray(2); val py = FloatArray(2); val down = BooleanArray(2)
            var downX = 0f; var downY = 0f; var downIsland: String? = null
            var pinch = false; var pinchStart = 1f; var zoomStart = 1f; var panned = false
            fun dist() = Vector2.dst(px[0], py[0], px[1], py[1])
            override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                if (inputLocked || pointer > 1) return false
                px[pointer] = x; py[pointer] = y; down[pointer] = true
                if (pointer == 1 && down[0]) { pinch = true; pinchStart = dist().coerceAtLeast(1f); zoomStart = zoom; return true }
                if (pointer == 0) { downX = x; downY = y; downIsland = islandAtLocal(x, y)?.id; panned = false; pinch = false }
                return true
            }
            override fun touchDragged(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                if (pointer > 1) return
                val dx = x - px[pointer]; val dy = y - py[pointer]
                px[pointer] = x; py[pointer] = y
                if (pinch && down[0] && down[1]) {
                    zoom = (zoomStart * dist() / pinchStart).coerceIn(1f, 2.4f); clampPan(); return
                }
                if (pointer == 0 && downIsland == null && zoom > 1f) { panX += dx; panY += dy; clampPan(); panned = true }
            }
            override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
                if (pointer > 1) return
                down[pointer] = false
                if (pinch) { if (!down[0] && !down[1]) pinch = false; return }
                if (inputLocked || pointer != 0 || panned) return
                val dx = x - downX; val dy = y - downY
                if (dx * dx + dy * dy > 34f * 34f && downIsland != null && mode == Mode.NORMAL) {
                    val d = when { dx > 0 && dy > 0 -> Dir.N; dx > 0 -> Dir.E; dy < 0 -> Dir.S; else -> Dir.W }
                    select(downIsland)
                    onAction(Action.Slide(downIsland!!, d))
                    return
                }
                tap(x, y)
            }
        })
    }

    fun reset(b: Board) {
        board = b
        visPos.clear(); visHeight.clear(); rotAnim.clear(); bridgeBirth.clear(); unitVis.clear()
        particles.clear(); floaters.clear(); falling.clear(); banner = null
        selectedId = null; mode = Mode.NORMAL; hint = null
        zoom = 1f; panX = 0f; panY = 0f
    }

    // ------------------------------------------------------------------ Geometri

    private var tw0 = 120f

    private fun layoutGrid() {
        val n = (board.width + board.height).toFloat()
        tw0 = minOf(2f * (width - 20f) / n, 4f * (height - 120f) / n, 175f)
        ox = width / 2f - (board.width - board.height) * tw0 / 4f
        oy = height / 2f + n * tw0 / 8f - tw0 / 4f + 30f
        tw = tw0 * zoom
        th = tw / 2f
    }

    private fun cellX(gx: Float, gy: Float) = x + width / 2f + (ox + (gx - gy) * tw0 / 2f - width / 2f) * zoom + panX
    private fun cellY(gx: Float, gy: Float) = y + height / 2f + (oy - (gx + gy) * tw0 / 4f - height / 2f) * zoom + panY
    private fun lift(h: Float) = 10f + h * th * 0.45f

    private fun islandScreen(i: Island): Vector2 {
        val v = visPos.getOrPut(i.id) { Vector2(i.pos.x.toFloat(), i.pos.y.toFloat()) }
        val h = visHeight.getOrPut(i.id) { i.height.toFloat() }
        val bob = sin(time * 1.6f + (i.id.hashCode() % 17)) * 3f
        return Vector2(cellX(v.x, v.y), cellY(v.x, v.y) + lift(h) + bob + shakeOffset())
    }

    private fun shakeOffset() = if (shake > 0) sin(time * 60f) * 6f * shake else 0f

    private fun islandAtLocal(lx: Float, ly: Float): Island? {
        val sx = lx + x; val sy = ly + y
        return board.islands.filter { it.alive }.sortedByDescending { it.pos.x + it.pos.y }.firstOrNull { i ->
            val p = islandScreen(i)
            abs(sx - p.x) / (tw / 2f) + abs(sy - p.y) / (th / 2f) <= 1.15f
        }
    }

    private fun gridDirOffset(gx: Float, gy: Float) = Vector2((gx - gy) * tw / 2f, -(gx + gy) * th / 2f)

    // ------------------------------------------------------------------ Girdi

    private fun select(id: String?) {
        selectedId = id
        onSelect(id?.let { board.island(it) })
    }

    private fun arrowTargets(): List<Pair<Action, Vector2>> {
        val sel = selectedId?.let { board.island(it) } ?: return emptyList()
        if (!sel.alive) return emptyList()
        val out = ArrayList<Pair<Action, Vector2>>()
        when (mode) {
            Mode.NORMAL -> if (board.has(Mechanic.SLIDE)) for (d in Dir.entries) {
                val a = Action.Slide(sel.id, d)
                if (Rules.validate(board, a) == null) {
                    val c = sel.pos + d
                    out.add(a to Vector2(cellX(c.x.toFloat(), c.y.toFloat()), cellY(c.x.toFloat(), c.y.toFloat()) + 10f))
                }
            }
            Mode.JUMP -> for (d in Dir.entries) {
                val a = Action.Jump(sel.id, d)
                if (Rules.validate(board, a) == null) {
                    val c = sel.pos.step(d, 2)
                    out.add(a to Vector2(cellX(c.x.toFloat(), c.y.toFloat()), cellY(c.x.toFloat(), c.y.toFloat()) + 10f))
                }
            }
            else -> {}
        }
        return out
    }

    private fun tap(lx: Float, ly: Float) {
        val sx = lx + x; val sy = ly + y
        for ((a, p) in arrowTargets()) {
            if (Vector2.dst(sx, sy, p.x, p.y) < tw * 0.3f) { onAction(a); return }
        }
        val hit = islandAtLocal(lx, ly)
        when (mode) {
            Mode.SWAP -> {
                val sel = selectedId
                if (hit != null && sel != null && hit.id != sel) { onAction(Action.Swap(sel, hit.id)); mode = Mode.NORMAL; return }
                mode = Mode.NORMAL
            }
            Mode.MERGE -> {
                val sel = selectedId
                if (hit != null && sel != null && hit.id != sel) { onAction(Action.Merge(sel, hit.id)); mode = Mode.NORMAL; return }
                mode = Mode.NORMAL
            }
            Mode.UNIT -> {
                val u = selectedId?.let { board.unitOn(it) }
                if (hit != null && u != null && u.owner == Owner.PLAYER) { onAction(Action.MoveUnit(u.id, hit.id)); mode = Mode.NORMAL; return }
                mode = Mode.NORMAL
            }
            Mode.JUMP -> mode = Mode.NORMAL
            Mode.NORMAL -> {}
        }
        select(if (hit?.id == selectedId) null else hit?.id)
    }

    // ------------------------------------------------------------------ Olaylar → animasyon

    fun onEvents(events: List<GameEvent>, sfx: (String) -> Unit) {
        val speed = if (fast()) 1.6f else 1f
        for (e in events) when (e) {
            is GameEvent.IslandMoved -> if (e.pushed) flash[e.id] = 0.5f
            is GameEvent.IslandRotated -> { rotAnim[e.id] = 1f; sfx("rotate") }
            is GameEvent.BridgeFormed -> {
                bridgeBirth[key(e.a, e.b)] = time
                mid(e.a, e.b)?.let { burst(it.x, it.y, 10, Color.valueOf("FFE082"), 90f) }
                sfx("bridge")
            }
            is GameEvent.BridgeBroken -> mid(e.a, e.b)?.let { burst(it.x, it.y, 6, Color.valueOf("BCAAA4"), 60f) }
            is GameEvent.Combo -> {
                showBanner(tr("combo.${e.comboId}"))
                mid(e.a, e.b)?.let { burst(it.x, it.y, 40, comboColor(e.comboId), 260f) }
                sfx("combo")
            }
            is GameEvent.TripleCombo -> { showBanner(tr(if (e.size >= 4) "combo.CHAIN_REACTION" else "combo.RESONANCE")); at(e.at)?.let { burst(it.x, it.y, 36, Color.valueOf("FFD54F"), 240f) }; sfx("combo") }
            is GameEvent.Produced -> at(e.islandId)?.let { float("+${e.amount} ${tr("res.${e.res}")}", it.x, it.y + th * 0.8f, Color.valueOf("2E7D32")) }
            is GameEvent.Burned -> at(e.id)?.let { burst(it.x, it.y + 20f, 24, Color.valueOf("FF7043"), 160f); float(tr("island.BURNT"), it.x, it.y + th, Palette.danger) }
            is GameEvent.Thawed -> at(e.id)?.let { burst(it.x, it.y, 16, Color.valueOf("B3E5FC"), 120f) }
            is GameEvent.Revealed -> at(e.id)?.let { burst(it.x, it.y, 18, Color.WHITE, 140f) }
            is GameEvent.Collapsed -> board.island(e.id)?.let { falling.add(it.copy() to 0f) }
            is GameEvent.Collected -> at(e.id)?.let {
                burst(it.x, it.y + 20f, 22, Color.valueOf("FFF176"), 160f)
                float(when (e.kind) { "village" -> "Halk kurtarıldı!"; "crystal" -> "Kristal toplandı!"; "time" -> "+Hamle"; else -> "Kadim güç!" }, it.x, it.y + th, Color.valueOf("6A1B9A"))
                sfx("coin")
            }
            is GameEvent.Transformed -> at(e.id)?.let { burst(it.x, it.y, 20, Color.valueOf("9E9E9E"), 120f) }
            is GameEvent.UnitMoved -> {}
            is GameEvent.Attack -> { flash[e.target] = 1f; at(e.targetIsland)?.let { float("-${e.damage}", it.x + 18f, it.y + th, Palette.danger, true) }; sfx("hit") }
            is GameEvent.CastleHit -> { shake = 0.4f / speed; castle()?.let { float("-${e.damage} Kale", it.x, it.y + th * 1.2f, Palette.danger, true) }; sfx("hit") }
            is GameEvent.Healed -> board.unit(e.unitId)?.let { u -> at(u.islandId)?.let { float("+${e.amount}", it.x - 18f, it.y + th, Palette.success) } }
            is GameEvent.UnitDied -> at(e.islandId)?.let { burst(it.x, it.y + 20f, 26, Color.valueOf("ECEFF1"), 150f) }
            is GameEvent.UnitSpawned -> at(e.islandId)?.let { burst(it.x, it.y + 20f, 14, Color.valueOf("EF9A9A"), 110f) }
            is GameEvent.Captured -> { showBanner("Üs ele geçirildi!"); at(e.islandId)?.let { burst(it.x, it.y, 40, Color.valueOf("64B5F6"), 220f) } }
            is GameEvent.BossAbility -> { showBanner("${board.def.boss?.name ?: "Patron"}: ${bossAbilityName(e.ability)}"); e.target?.let { t -> at(t)?.let { burst(it.x, it.y, 20, Color.valueOf("CE93D8"), 140f) } } }
            is GameEvent.EnemyTrick -> at(e.target)?.let { float(if (e.kind == "ROTATE") "Tuzak: köprü çevrildi!" else "Tuzak: ada kilitlendi!", it.x, it.y + th, Palette.danger) }
            is GameEvent.Eruption -> at(e.id)?.let { burst(it.x, it.y + 30f, 30, Color.valueOf("FF5722"), 200f) }
            is GameEvent.IslandHeight -> {}
            is GameEvent.Merged -> { mid(e.a, e.b)?.let { burst(it.x, it.y, 16, Color.valueOf("FFD54F"), 120f) }; sfx("bridge") }
            is GameEvent.SplitOff -> at(e.id)?.let { burst(it.x, it.y, 10, Color.valueOf("BCAAA4"), 90f) }
            is GameEvent.Info -> {}
        }
    }

    fun bossAbilityName(a: String) = when (a) {
        "LOCK" -> "Ada kilidi"; "FREEZE" -> "Dondurma"; "BURN" -> "Köprü yakma"; "FOG" -> "Sis perdesi"
        "PUSH" -> "Fırtına itişi"; "REWIND" -> "Zamanı geri sar"; "TELEPORT" -> "Işınlama"; "RESHAPE" -> "Harita bükme"
        "SUMMON" -> "Asker çağırma"; else -> a
    }

    fun victory() {
        for (i in 0 until 90) {
            val c = if (i % 3 == 0) cosmetics.victory else listOf(Color.valueOf("FFD54F"), Color.valueOf("4FC3F7"), Color.valueOf("81C784"), Color.valueOf("F48FB1"))[i % 4]
            particles.add(Particle(x + MathUtils.random(width), y + height + 20f, MathUtils.random(-60f, 60f), MathUtils.random(-260f, -80f), 2.6f, 2.6f, c, MathUtils.random(5f, 10f), -40f))
        }
    }

    private fun comboColor(id: String) = when (id) {
        "STEAM", "FROZEN_PASS", "HEALING_SPRING" -> Color.valueOf("B3E5FC")
        "FLAME_STORM", "TOXIC_FUME", "FORGE" -> Color.valueOf("FF8A65")
        "ELECTRIC_FIELD", "ENERGY_STORM", "STORM_SURGE" -> Color.valueOf("FFF176")
        "POISON_CLOUD" -> Color.valueOf("AED581")
        else -> Color.valueOf("CE93D8")
    }

    private fun key(a: String, b: String) = if (a < b) "$a|$b" else "$b|$a"
    private fun at(id: String): Vector2? = board.island(id)?.let { islandScreen(it) }
    private fun castle(): Vector2? = board.islands.firstOrNull { it.type == IslandType.CASTLE }?.let { islandScreen(it) }
    private fun mid(a: String, b: String): Vector2? {
        val p = at(a) ?: return null; val q = at(b) ?: return null
        return Vector2((p.x + q.x) / 2, (p.y + q.y) / 2)
    }

    private fun burst(px: Float, py: Float, n: Int, c: Color, speed: Float) {
        repeat(n) {
            val a = MathUtils.random(MathUtils.PI2)
            val v = MathUtils.random(0.3f, 1f) * speed
            particles.add(Particle(px, py, MathUtils.cos(a) * v, MathUtils.sin(a) * v + 40f, 0.8f, 0.8f, c, MathUtils.random(3f, 7f), 260f))
        }
    }

    private fun float(text: String, px: Float, py: Float, c: Color, big: Boolean = false) {
        floaters.add(Floater(text, px, py, 1.3f, c, big))
    }

    fun showBanner(text: String) { banner = text; bannerLife = 1.6f }

    // ------------------------------------------------------------------ Güncelleme ve çizim

    override fun act(delta: Float) {
        super.act(delta)
        time += delta
        val speed = if (fast()) 18f else 11f
        for (i in board.islands) {
            val v = visPos.getOrPut(i.id) { Vector2(i.pos.x.toFloat(), i.pos.y.toFloat()) }
            v.lerp(Vector2(i.pos.x.toFloat(), i.pos.y.toFloat()), (delta * speed).coerceAtMost(1f))
            val h = visHeight.getOrPut(i.id) { i.height.toFloat() }
            visHeight[i.id] = h + (i.height - h) * (delta * speed).coerceAtMost(1f)
        }
        rotAnim.entries.forEach { it.setValue((it.value - delta * (if (fast()) 9f else 6f)).coerceAtLeast(0f)) }
        flash.entries.forEach { it.setValue((it.value - delta * 3f).coerceAtLeast(0f)) }
        if (shake > 0) shake -= delta
        for (p in particles) { p.x += p.vx * delta; p.y += p.vy * delta; p.vy -= p.gravity * delta; p.life -= delta }
        particles.removeAll { it.life <= 0 }
        for (f in floaters) { f.y += 50f * delta; f.life -= delta }
        floaters.removeAll { it.life <= 0 }
        falling.replaceAll { it.first to it.second + delta }
        falling.removeAll { it.second > 1.2f }
        if (bannerLife > 0) bannerLife -= delta
    }

    private val clipArea = com.badlogic.gdx.math.Rectangle()
    private val scissor = com.badlogic.gdx.math.Rectangle()

    override fun draw(batch: Batch, parentAlpha: Float) {
        layoutGrid()
        batch.end()
        clipArea.set(x, y, width, height)
        com.badlogic.gdx.scenes.scene2d.utils.ScissorStack.calculateScissors(stage.camera, batch.transformMatrix, clipArea, scissor)
        val clipped = com.badlogic.gdx.scenes.scene2d.utils.ScissorStack.pushScissors(scissor)
        val sr = shapes
        sr.projectionMatrix = batch.projectionMatrix
        sr.begin(ShapeRenderer.ShapeType.Filled)
        drawGround(sr)
        drawWorldShapes(sr)
        drawOverlays(sr)
        for (p in particles) {
            sr.color = Color(p.color.r, p.color.g, p.color.b, (p.life / p.max).coerceIn(0f, 1f))
            sr.circle(p.x, p.y, p.size, 8)
        }
        sr.end()
        batch.begin()
        drawTexts(batch)
        batch.flush()
        if (clipped) com.badlogic.gdx.scenes.scene2d.utils.ScissorStack.popScissors()
    }

    private fun drawGround(sr: ShapeRenderer) {
        // Izgara: yumuşak bulut hücreleri, engeller ve özel hücreler.
        for (gy in 0 until board.height) for (gx in 0 until board.width) {
            val cx = cellX(gx.toFloat(), gy.toFloat()); val cy = cellY(gx.toFloat(), gy.toFloat())
            val c = Cell(gx, gy)
            val hw = tw / 2 * 0.94f; val hh = th / 2 * 0.94f
            sr.color = Color(1f, 1f, 1f, 0.28f)
            sr.triangle(cx - hw, cy, cx, cy + hh, cx + hw, cy); sr.triangle(cx - hw, cy, cx, cy - hh, cx + hw, cy)
            if (c in board.blocked) {
                sr.color = Color.valueOf("9E9E9E")
                sr.triangle(cx - tw * 0.2f, cy - th * 0.05f, cx + tw * 0.22f, cy - th * 0.05f, cx + tw * 0.02f, cy + th * 0.55f + sin(time + gx) * 2f)
                sr.color = Color.valueOf("BDBDBD")
                sr.triangle(cx - tw * 0.2f, cy - th * 0.05f, cx + tw * 0.02f, cy + th * 0.55f, cx - tw * 0.05f, cy - th * 0.05f)
            }
            board.wind[c]?.let { d ->
                val o = gridDirOffset(d.dx.toFloat(), d.dy.toFloat())
                val ph = (time * 1.5f) % 1f
                sr.color = Color(0.3f, 0.75f, 0.6f, 0.85f)
                val bx = cx - o.x * 0.3f + o.x * 0.25f * ph; val by = cy - o.y * 0.3f + o.y * 0.25f * ph
                sr.rectLine(bx, by, bx + o.x * 0.35f, by + o.y * 0.35f, 6f)
                val tip = Vector2(bx + o.x * 0.45f, by + o.y * 0.45f)
                val perp = Vector2(-o.y, o.x).nor().scl(10f)
                sr.triangle(tip.x, tip.y, bx + o.x * 0.3f + perp.x, by + o.y * 0.3f + perp.y, bx + o.x * 0.3f - perp.x, by + o.y * 0.3f - perp.y)
            }
            if (c in board.targets) {
                val pulse = 0.6f + 0.4f * sin(time * 4f)
                sr.color = Color(1f, 0.84f, 0.2f, 0.55f * pulse)
                sr.triangle(cx - hw, cy, cx, cy + hh, cx + hw, cy); sr.triangle(cx - hw, cy, cx, cy - hh, cx + hw, cy)
                star(sr, cx, cy + 4f, tw * 0.13f, Color.valueOf("FFB300"))
            }
        }
    }

    private fun star(sr: ShapeRenderer, cx: Float, cy: Float, r: Float, c: Color) {
        sr.color = c
        for (i in 0 until 5) {
            val a1 = MathUtils.PI / 2 + i * MathUtils.PI2 / 5
            val a2 = a1 + MathUtils.PI2 / 10; val a0 = a1 - MathUtils.PI2 / 10
            sr.triangle(cx, cy, cx + MathUtils.cos(a1) * r, cy + MathUtils.sin(a1) * r, cx + MathUtils.cos(a2) * r * 0.45f, cy + MathUtils.sin(a2) * r * 0.45f)
            sr.triangle(cx, cy, cx + MathUtils.cos(a1) * r, cy + MathUtils.sin(a1) * r, cx + MathUtils.cos(a0) * r * 0.45f, cy + MathUtils.sin(a0) * r * 0.45f)
        }
    }

    private fun edgeMid(i: Island, d: Dir, center: Vector2, angleOffset: Float = 0f): Vector2 {
        val baseAngle = MathUtils.atan2(d.dy.toFloat(), d.dx.toFloat()) - angleOffset
        val o = gridDirOffset(MathUtils.cos(baseAngle), MathUtils.sin(baseAngle))
        return Vector2(center.x + o.x * 0.46f, center.y + o.y * 0.46f)
    }

    private fun drawWorldShapes(sr: ShapeRenderer) {
        val g = Rules.grid(board)
        data class Item(val order: Float, val draw: () -> Unit)
        val items = ArrayList<Item>()
        val alive = board.islands.filter { it.alive }
        val bridgeColor = cosmetics.bridge
        for ((idx, a) in board.islands.withIndex()) {
            if (!a.alive) continue
            for (j in Rules.physicalNeighbors(board, g, idx)) {
                if (j < idx) continue
                val b = board.islands[j]
                val k = key(a.id, b.id)
                val order = (a.pos.x + a.pos.y + b.pos.x + b.pos.y) / 2f
                items.add(Item(order) {
                    val pa = islandScreen(a); val pb = islandScreen(b)
                    val da = Dir.entries.first { a.pos + it == b.pos }
                    val ea = edgeMid(a, da, pa); val eb = edgeMid(b, da.opposite, pb)
                    val birth = bridgeBirth[k]
                    val grow = if (birth == null) 1f else Interpolation.pow2Out.apply(((time - birth) / 0.3f).coerceIn(0f, 1f))
                    val mx = (ea.x + eb.x) / 2; val my = (ea.y + eb.y) / 2
                    val sx = mx + (ea.x - mx) * grow; val sy = my + (ea.y - my) * grow
                    val tx = mx + (eb.x - mx) * grow; val ty = my + (eb.y - my) * grow
                    sr.color = Color(bridgeColor.r * 0.7f, bridgeColor.g * 0.7f, bridgeColor.b * 0.7f, 1f)
                    sr.rectLine(sx, sy - 4f, tx, ty - 4f, tw * 0.17f)
                    sr.color = bridgeColor
                    sr.rectLine(sx, sy, tx, ty, tw * 0.15f)
                    sr.color = Color(1f, 1f, 1f, 0.35f)
                    sr.rectLine(sx, sy + 3f, tx, ty + 3f, 2f)
                })
            }
        }
        for (i in alive) items.add(Item((i.pos.x + i.pos.y).toFloat()) { drawIsland(sr, i) })
        for ((i, t) in falling) items.add(Item(-1f) {
            val p = Vector2(cellX(i.pos.x.toFloat(), i.pos.y.toFloat()), cellY(i.pos.x.toFloat(), i.pos.y.toFloat()) - t * t * 500f)
            IslandArt.body(sr, p.x, p.y, tw, th, IslandArt.topColors[i.type]!!, (1f - t / 1.2f).coerceIn(0f, 1f))
        })
        items.sortBy { it.order }
        items.forEach { it.draw() }
        // Portal bağlantıları
        val portals = alive.filter { it.type == IslandType.PORTAL && !it.hidden && Rules.physicalNeighbors(board, g, board.islands.indexOf(it)).isNotEmpty() }
        if (portals.size >= 2) {
            for (pi in portals.indices) for (pj in pi + 1 until portals.size) {
                val p = islandScreen(portals[pi]); val q = islandScreen(portals[pj])
                val n = 14
                for (s in 0 until n) {
                    if ((s + (time * 6).toInt()) % 2 == 0) continue
                    val t0 = s / n.toFloat(); val t1 = (s + 1) / n.toFloat()
                    val arc = { t: Float -> sin(t * MathUtils.PI) * 50f }
                    sr.color = Color(0.92f, 0.4f, 0.75f, 0.8f)
                    sr.rectLine(p.x + (q.x - p.x) * t0, p.y + 30f + (q.y - p.y) * t0 + arc(t0), p.x + (q.x - p.x) * t1, p.y + 30f + (q.y - p.y) * t1 + arc(t1), 4f)
                }
            }
        }
    }

    private fun drawIsland(sr: ShapeRenderer, i: Island) {
        val p = islandScreen(i)
        // gölge
        sr.color = Color(0.2f, 0.3f, 0.5f, 0.12f)
        val vp = visPos[i.id]!!
        sr.ellipse(cellX(vp.x, vp.y) - tw * 0.3f, cellY(vp.x, vp.y) - th * 0.75f, tw * 0.6f, th * 0.35f)
        if (i.hidden) {
            sr.color = Color(1f, 1f, 1f, 0.95f)
            sr.circle(p.x - tw * 0.18f, p.y, tw * 0.2f, 16); sr.circle(p.x + tw * 0.16f, p.y + 4f, tw * 0.22f, 16); sr.circle(p.x, p.y + th * 0.25f, tw * 0.2f, 16)
            return
        }
        var top = IslandArt.topColors[i.type] ?: Color.WHITE
        cosmetics.islandTint?.let { if (i.type != IslandType.ENEMY_FORT) top = Color(top).lerp(it, 0.3f) }
        val fl = flash[i.id] ?: 0f
        if (fl > 0) top = Color(top).lerp(Color.WHITE, fl * 0.6f)
        IslandArt.body(sr, p.x, p.y, tw, th, top)
        // bağlantı noktası uçları
        val rot = (rotAnim[i.id] ?: 0f) * MathUtils.PI / 2f
        for (d in Dir.entries) if (Ports.has(i.ports, d)) {
            val m = edgeMid(i, d, p, rot)
            val o = Vector2(m.x - p.x, m.y - p.y).scl(0.34f)
            sr.color = Color(cosmetics.bridge.r * 0.55f, cosmetics.bridge.g * 0.55f, cosmetics.bridge.b * 0.55f, 1f)
            sr.rectLine(m.x - o.x * 0.9f, m.y - o.y * 0.9f - 2f, m.x + o.x, m.y + o.y - 2f, tw * 0.16f)
            sr.color = cosmetics.bridge
            sr.rectLine(m.x - o.x * 0.9f, m.y - o.y * 0.9f, m.x + o.x, m.y + o.y, tw * 0.13f)
            sr.color = Color.valueOf("FFF3C4")
            sr.circle(m.x + o.x, m.y + o.y, tw * 0.05f, 10)
            sr.color = Color.valueOf("8D6E63")
            sr.circle(m.x + o.x, m.y + o.y, tw * 0.022f, 8)
        }
        IslandArt.decor(sr, i.type, p.x, p.y - th * 0.05f, tw, th, time, if (i.type == IslandType.CASTLE) cosmetics.castle else null, i.collected)
        if (i.frozen > 0) {
            sr.color = Color(0.75f, 0.92f, 1f, 0.55f)
            sr.triangle(p.x - tw / 2 * 0.9f, p.y, p.x, p.y + th / 2 * 0.9f, p.x + tw / 2 * 0.9f, p.y)
            sr.triangle(p.x - tw / 2 * 0.9f, p.y, p.x, p.y - th / 2 * 0.9f, p.x + tw / 2 * 0.9f, p.y)
            sr.color = Color.WHITE
            for (k in 0 until 3) { val a = k * MathUtils.PI / 3; sr.rectLine(p.x - MathUtils.cos(a) * 12f, p.y + 30f - MathUtils.sin(a) * 12f, p.x + MathUtils.cos(a) * 12f, p.y + 30f + MathUtils.sin(a) * 12f, 3f) }
        }
        if (i.anchored > 0) {
            sr.color = Color.valueOf("78909C")
            sr.circle(p.x + tw * 0.28f, p.y + th * 0.1f, 10f, 10)
            sr.color = Color.valueOf("ECEFF1"); sr.circle(p.x + tw * 0.28f, p.y + th * 0.1f, 5f, 10)
        }
        val u = board.unitOn(i.id)
        if (u != null) drawUnit(sr, u, p)
        if (i.id == selectedId) {
            val pulse = 0.6f + 0.4f * sin(time * 6f)
            IslandArt.outline(sr, p.x, p.y, tw, th, Color(1f, 0.8f, 0.1f, pulse), 5f)
        }
    }

    private fun drawUnit(sr: ShapeRenderer, u: Troop, islandPos: Vector2) {
        val target = Vector2(islandPos.x + tw * 0.2f, islandPos.y + th * 0.15f)
        val v = unitVis.getOrPut(u.id) { Vector2(target) }
        v.lerp(target, 0.2f)
        val body = when {
            u.type == UnitType.BOSS -> Color.valueOf("8E24AA")
            u.owner == Owner.PLAYER -> Color.valueOf("1E88E5")
            else -> Color.valueOf("E53935")
        }
        val s = tw / 140f * (if (u.type == UnitType.BOSS) 1.6f else 1f)
        val fl = flash[u.id] ?: 0f
        sr.color = Color(0f, 0f, 0f, 0.15f); sr.ellipse(v.x - 14 * s, v.y - 6 * s, 28 * s, 10 * s)
        sr.color = if (fl > 0) Color(body).lerp(Color.WHITE, fl) else body
        sr.rect(v.x - 10 * s, v.y, 20 * s, 22 * s)
        sr.color = Color.valueOf("FFE0BD"); sr.circle(v.x, v.y + 30 * s, 9 * s, 12)
        val hat = if (u.owner == Owner.PLAYER) (cosmetics.unitCostume ?: Color.valueOf("FFC107")) else Color.valueOf("424242")
        sr.color = hat; sr.triangle(v.x - 10 * s, v.y + 34 * s, v.x + 10 * s, v.y + 34 * s, v.x, v.y + 46 * s)
        when (u.type) {
            UnitType.SWORD -> { sr.color = Color.valueOf("CFD8DC"); sr.rectLine(v.x + 12 * s, v.y + 4 * s, v.x + 20 * s, v.y + 30 * s, 3 * s) }
            UnitType.ARCHER -> { sr.color = Color.valueOf("8D6E63"); sr.arc(v.x + 14 * s, v.y + 16 * s, 12 * s, -90f, 180f, 8) }
            UnitType.SHIELD -> { sr.color = Color.valueOf("90A4AE"); sr.circle(v.x - 12 * s, v.y + 12 * s, 9 * s, 10) }
            UnitType.MAGE -> { sr.color = Color.valueOf("AB47BC"); sr.circle(v.x + 15 * s, v.y + 28 * s, 5 * s, 8) }
            UnitType.HEALER -> { sr.color = Color.valueOf("EC407A"); sr.rect(v.x + 11 * s, v.y + 12 * s, 9 * s, 3 * s); sr.rect(v.x + 14 * s, v.y + 9 * s, 3 * s, 9 * s) }
            UnitType.FLYER -> { sr.color = Color.WHITE; sr.triangle(v.x - 10 * s, v.y + 18 * s, v.x - 26 * s, v.y + 28 * s, v.x - 10 * s, v.y + 8 * s); sr.triangle(v.x + 10 * s, v.y + 18 * s, v.x + 26 * s, v.y + 28 * s, v.x + 10 * s, v.y + 8 * s) }
            UnitType.SIEGE -> { sr.color = Color.valueOf("6D4C41"); sr.rect(v.x - 18 * s, v.y - 2 * s, 36 * s, 6 * s) }
            UnitType.GUARDIAN -> { sr.color = Color.valueOf("FFD54F"); sr.circle(v.x, v.y + 12 * s, 6 * s, 10) }
            UnitType.ENGINEER -> { sr.color = Color.valueOf("FFB300"); sr.rect(v.x + 10 * s, v.y + 6 * s, 4 * s, 16 * s) }
            UnitType.SCOUT -> { sr.color = Color.valueOf("4CAF50"); sr.rect(v.x - 10 * s, v.y + 18 * s, 20 * s, 3 * s) }
            UnitType.BOSS -> { sr.color = Color.valueOf("FFD54F"); sr.triangle(v.x - 9 * s, v.y + 38 * s, v.x + 9 * s, v.y + 38 * s, v.x, v.y + 50 * s) }
        }
        if (u.stun > 0) { sr.color = Color.valueOf("FFF176"); for (k in 0 until 3) sr.circle(v.x + MathUtils.cos(time * 4 + k * 2.1f) * 12 * s, v.y + 50 * s + MathUtils.sin(time * 4 + k * 2.1f) * 4 * s, 3 * s, 6) }
        // can çubuğu
        val w = 34f * s
        sr.color = Color(0f, 0f, 0f, 0.25f); sr.rect(v.x - w / 2, v.y - 10 * s, w, 5f)
        sr.color = if (u.owner == Owner.PLAYER) Palette.success else Palette.danger
        sr.rect(v.x - w / 2, v.y - 10 * s, w * (u.hp.toFloat() / u.maxHp).coerceIn(0f, 1f), 5f)
    }

    private fun drawOverlays(sr: ShapeRenderer) {
        // Seçim okları
        val pulse = 0.65f + 0.35f * sin(time * 5f)
        for ((a, p) in arrowTargets()) {
            val dir = when (a) { is Action.Slide -> a.dir; is Action.Jump -> a.dir; else -> continue }
            val o = gridDirOffset(dir.dx.toFloat(), dir.dy.toFloat()).nor()
            val perp = Vector2(-o.y, o.x)
            val r = tw * 0.15f
            sr.color = Color(Palette.primary.r, Palette.primary.g, Palette.primary.b, 0.35f * pulse); sr.circle(p.x, p.y, r * 1.45f, 24)
            sr.color = Color(1f, 1f, 1f, 0.95f); sr.circle(p.x, p.y, r * 1.15f, 24)
            sr.color = Palette.primary
            sr.triangle(p.x + o.x * r * 0.85f, p.y + o.y * r * 0.85f, p.x - o.x * r * 0.5f + perp.x * r * 0.7f, p.y - o.y * r * 0.5f + perp.y * r * 0.7f,
                p.x - o.x * r * 0.5f - perp.x * r * 0.7f, p.y - o.y * r * 0.5f - perp.y * r * 0.7f)
        }
        val sel = selectedId?.let { board.island(it) }
        if (sel != null && mode == Mode.SWAP) for (d in Dir.entries) board.islandAt(sel.pos + d)?.let { o ->
            if (Rules.validate(board, Action.Swap(sel.id, o.id)) == null) { val p = islandScreen(o); IslandArt.outline(sr, p.x, p.y, tw, th, Color(0.3f, 0.6f, 1f, pulse), 5f) }
        }
        if (sel != null && mode == Mode.MERGE) for (d in Dir.entries) board.islandAt(sel.pos + d)?.let { o ->
            if (Rules.validate(board, Action.Merge(sel.id, o.id)) == null) { val p = islandScreen(o); IslandArt.outline(sr, p.x, p.y, tw, th, Color(1f, 0.75f, 0.1f, pulse), 5f) }
        }
        // Birleşik ada zincirleri
        for (a in board.islands) {
            if (!a.alive || a.group == 0) continue
            for (d in listOf(Dir.E, Dir.S)) {
                val o = board.islandAt(a.pos + d) ?: continue
                if (o.group != a.group) continue
                val p = islandScreen(a); val q = islandScreen(o)
                for (k in 1..3) {
                    val t = k / 4f
                    val cx = p.x + (q.x - p.x) * t; val cy = p.y + (q.y - p.y) * t - th * 0.32f
                    sr.color = Color.valueOf("B8860B"); sr.circle(cx, cy, tw * 0.05f, 10)
                    sr.color = Color.valueOf("FFD54F"); sr.circle(cx, cy, tw * 0.03f, 10)
                }
            }
        }
        if (sel != null && mode == Mode.UNIT) {
            val u = board.unitOn(sel.id)
            if (u != null) for (t in board.islands) if (t.alive && !t.hidden) {
                val kind = Rules.unitMoveKind(board, u, t.id) ?: continue
                val p = islandScreen(t)
                IslandArt.outline(sr, p.x, p.y, tw, th, if (kind == "attack") Color(0.95f, 0.3f, 0.3f, pulse) else Color(0.3f, 0.8f, 0.4f, pulse), 6f)
            }
        }
        // İpucu / öğretici işaretçisi
        val h = hint
        if (h != null) {
            val id = when (h) { is Action.Slide -> h.id; is Action.Rotate -> h.id; is Action.Swap -> h.a; is Action.Raise -> h.id; is Action.Jump -> h.id; is Action.MoveUnit -> board.unit(h.unitId)?.islandId; Action.Wait -> null; is Action.Merge -> h.a; is Action.Split -> h.id }
            id?.let { board.island(it) }?.let { isl ->
                val p = islandScreen(isl)
                val r = tw * (0.55f + 0.08f * sin(time * 6f))
                sr.color = Color(1f, 0.55f, 0.1f, 0.85f)
                for (k in 0 until 24) {
                    if (k % 2 == 0) continue
                    val a0 = k * MathUtils.PI2 / 24 + time; val a1 = (k + 1) * MathUtils.PI2 / 24 + time
                    sr.rectLine(p.x + MathUtils.cos(a0) * r, p.y + MathUtils.sin(a0) * r * 0.55f, p.x + MathUtils.cos(a1) * r, p.y + MathUtils.sin(a1) * r * 0.55f, 5f)
                }
                val dir = when (h) { is Action.Slide -> h.dir; is Action.Jump -> h.dir; else -> null }
                if (dir != null) {
                    val o = gridDirOffset(dir.dx.toFloat(), dir.dy.toFloat())
                    val bounce = 0.55f + 0.15f * sin(time * 7f)
                    val tip = Vector2(p.x + o.x * bounce, p.y + o.y * bounce)
                    val n = Vector2(o).nor(); val perp = Vector2(-n.y, n.x).scl(16f)
                    sr.color = Color(1f, 0.5f, 0.05f, 0.95f)
                    sr.rectLine(p.x + o.x * 0.2f, p.y + o.y * 0.2f, tip.x - n.x * 14f, tip.y - n.y * 14f, 9f)
                    sr.triangle(tip.x + n.x * 8f, tip.y + n.y * 8f, tip.x - n.x * 18f + perp.x, tip.y - n.y * 18f + perp.y, tip.x - n.x * 18f - perp.x, tip.y - n.y * 18f - perp.y)
                }
            }
        }
    }

    private fun drawTexts(batch: Batch) {
        val small = theme.small
        for (i in board.islands) {
            if (!i.alive || i.hidden) continue
            val p = islandScreen(i)
            if (i.crumble > 0) text(batch, small, "↓${i.crumble}", p.x - tw * 0.3f, p.y + th * 0.4f, Palette.danger)
            if (i.frozen in 1..20) text(batch, small, "${i.frozen}", p.x, p.y + th * 0.9f, Color.valueOf("0277BD"))
            if (i.height > 0) text(batch, small, "↑${i.height}", p.x - tw * 0.36f, p.y - th * 0.1f, Color.valueOf("5E35B1"))
        }
        for (i in board.islands) if (i.hidden && i.alive) { val p = islandScreen(i); text(batch, theme.title, "?", p.x, p.y + 20f, Palette.textMuted) }
        for (f in floaters) text(batch, if (f.big) theme.body else small, f.text, f.x, f.y, Color(f.color.r, f.color.g, f.color.b, (f.life / 1.3f).coerceIn(0f, 1f)))
        val b = banner
        if (b != null && bannerLife > 0) {
            val a = (bannerLife / 0.4f).coerceAtMost(1f)
            text(batch, theme.title, b, x + width / 2 + 2f, y + height * 0.82f - 2f, Color(0f, 0f, 0f, 0.25f * a))
            text(batch, theme.title, b, x + width / 2, y + height * 0.82f, Color(0.95f, 0.45f, 0.1f, a))
        }
    }

    private fun text(batch: Batch, font: BitmapFont, s: String, cx: Float, cy: Float, c: Color) {
        font.color = c
        layout.setText(font, s)
        font.draw(batch, layout, cx - layout.width / 2, cy + layout.height / 2)
        font.color = Color.WHITE
    }
}
