package com.besaniyelik.dunya.sim

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.*
import com.badlogic.gdx.utils.Disposable
import com.besaniyelik.dunya.level.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class SimStatus { RUNNING, DYING, COMPLETE, GAME_OVER }

enum class DeathCause(val title: String) {
    SPIKE("Dikenlere çarptın!"),
    SAW("Testereye yakalandın!"),
    FALL("Boşluğa düştün!"),
    CRUSH("Platformlar arasında sıkıştın!"),
    STUCK("Yolun kapandı, takıldın!"),
    CHASER("Kaos duvarı seni yakaladı!"),
}

/** Simülasyon olayları; ses, parçacık ve arayüz bu olayları dinler. Oyun mantığı arayüzden tamamen bağımsızdır. */
interface SimListener {
    fun onJump(air: Boolean) {}
    fun onLand(impact: Float) {}
    fun onCoin(c: Collectible, value: Int) {}
    fun onStar(c: Collectible) {}
    fun onPiece(c: Collectible) {}
    fun onPowerup(type: PowerupType) {}
    fun onRuleWarning(next: List<Mechanic>) {}
    fun onRuleChange(prev: List<Mechanic>, next: List<Mechanic>) {}
    fun onSpring() {}
    fun onBoost() {}
    fun onCheckpoint(x: Float) {}
    fun onShieldSave() {}
    fun onDeath(cause: DeathCause) {}
    fun onRespawn() {}
    fun onComplete() {}
    fun onGameOver() {}
}

class SimConfig(
    val hearts: Int = 3,
    /** Çözücü modu: ölüm anında başarısızlık, güçlendirmeler devre dışı. */
    val solver: Boolean = false,
    val powerups: Boolean = true,
    val speedMul: Float = 1f,
    /** Katı mod: engel isabet kutuları büyütülür; böylece bulunan çözümde insan için pay kalır. */
    val strict: Boolean = false,
)

/** İçeriği talep üzerine üreten kaynak (Sonsuz Dünya için). */
interface ContentProvider {
    fun ensure(sim: Simulation, untilX: Float)
}

/** Simülasyonun tam anlık görüntüsü: kontrol noktaları ve çözücü tarafından kullanılır. */
class SimSnapshot(
    val tick: Int, val time: Float, val shiftClock: Float, val shiftAmp: Float, val phaseIdx: Int, val warnedPhase: Int,
    val chaserX: Float, val px: Float, val py: Float, val vx: Float, val vy: Float, val scale: Float,
    val grounded: Boolean, val coyote: Float, val buffer: Float, val prevInput: Boolean, val cutAllowed: Boolean,
    val autoTimer: Float, val airJumps: Int, val blocked: Float, val boost: Float, val vxCur: Float, val jumpLock: Int,
    val invuln: Float, val inactiveGhosts: IntArray, val distance: Float,
)

class Simulation(
    level: LevelData?,
    val config: SimConfig = SimConfig(),
    val provider: ContentProvider? = null,
) : Disposable {
    var listener: SimListener = object : SimListener {}

    val world = World(Vector2(0f, -Physics.GRAVITY), false)
    val solids = ArrayList<SolidEntity>()
    val hazards = ArrayList<HazardEntity>()
    val pads = ArrayList<PadEntity>()
    val collectibles = ArrayList<Collectible>()
    val phases = ArrayList<PhaseDef>()
    val checkpoints = ArrayList<Float>()
    private val ghostSolids = ArrayList<SolidEntity>()
    private val movingSolids = ArrayList<SolidEntity>()
    private val movingLastTick = HashMap<SolidEntity, Int>()

    val solidIndex = SpatialIndex<SolidEntity>()
    val hazardIndex = SpatialIndex<HazardEntity>()
    val padIndex = SpatialIndex<PadEntity>()
    val collectIndex = SpatialIndex<Collectible>()

    var goalX = Float.MAX_VALUE
    var levelEnd = 0f
    var startX = 2f
    var startY = 0.4f
    val chaserEnabled = level?.chaser == true

    var tick = 0
    var time = 0f
    var shiftClock = 0f
    var shiftAmp = 0f
    var phaseIdx = 0
    var warnedPhase = -1
    var effects = RuleEffects.DEFAULT
    var activeRules: List<Mechanic> = listOf(Mechanic.NORMAL)
    var chaserX = -100f
    var status = SimStatus.RUNNING
    var failed = false
    var deathCause: DeathCause? = null
    private var dyingTimer = 0f

    // İstatistikler
    var coinCount = 0
    var coinValue = 0
    var starsCollected = 0
    var pieceCollected = false
    var deaths = 0
    var distance = 0f
    var phasesPassed = 0
    var hearts = config.hearts
    var shield = 0
    var guard = false
    var magnet = 0f
    var doubleJump = 0f
    var coinMul = 0f
    var finder = 0f
    var powerupsUsed = 0
    var rulesChanged = 0

    val player = PlayerController(this)
    private var checkpointSnap: SimSnapshot? = null
    var lastCheckpointX = Float.NEGATIVE_INFINITY

    // geçici
    private val tmpSolids = ArrayList<SolidEntity>()
    private val seenSolids = HashSet<SolidEntity>()
    private val tmpHaz = ArrayList<HazardEntity>()
    private val seenHaz = HashSet<HazardEntity>()
    private val tmpPads = ArrayList<PadEntity>()
    private val seenPads = HashSet<PadEntity>()
    private val tmpCol = ArrayList<Collectible>()
    private val seenCol = HashSet<Collectible>()

    init {
        world.setWarmStarting(false)
        world.setContinuousPhysics(false)
        if (level != null) {
            startX = level.start.x; startY = level.start.y
            goalX = level.goalX
            levelEnd = level.goalX + 12f
            addContent(level.solids, level.hazards, level.pads, level.coins, level.stars, level.piece, level.powerups, level.phases, level.checkpoints)
        }
        provider?.ensure(this, startX + 45f)
        player.create(startX, startY)
        applyPhase(0, initial = true)
        chaserX = startX - 9f
        checkpointSnap = snapshot()
    }

    // ---------------------------------------------------------------- içerik

    fun addContent(
        solidDefs: List<SolidDef>, hazardDefs: List<HazardDef>, padDefs: List<PadDef>, coins: List<Vec>, stars: List<Vec>,
        piece: Vec?, powerups: List<PowerupDef>, phaseDefs: List<PhaseDef>, cps: List<Float>,
    ) {
        for (d in solidDefs) {
            val e = SolidEntity(d, solids.size)
            solids.add(e)
            createBody(e)
            val minX = d.x - abs(d.ax)
            val maxX = d.x + d.w + abs(d.ax)
            solidIndex.add(e, minX, maxX)
            if (d.type == SolidType.GHOST) ghostSolids.add(e)
            if (e.moving) movingSolids.add(e)
            levelEnd = max(levelEnd, d.x + d.w)
        }
        for (d in hazardDefs) {
            val e = HazardEntity(d, hazards.size)
            hazards.add(e)
            val r = if (d.type == HazardType.SAW) d.w else 0f
            hazardIndex.add(e, d.x - abs(d.ax) - r, d.x + abs(d.ax) + r + (if (d.type == HazardType.SPIKE) d.w else 0f))
        }
        for (d in padDefs) {
            val e = PadEntity(d, pads.size)
            pads.add(e)
            padIndex.add(e, d.x, d.x + d.w)
        }
        fun addC(c: Collectible) { collectibles.add(c); collectIndex.add(c, c.x - 1f, c.x + 1f) }
        coins.forEach { addC(Collectible(CollectKind.COIN, it.x, it.y, collectibles.size)) }
        stars.forEach { addC(Collectible(CollectKind.STAR, it.x, it.y, collectibles.size)) }
        piece?.let { addC(Collectible(CollectKind.PIECE, it.x, it.y, collectibles.size)) }
        powerups.forEach { addC(Collectible(CollectKind.POWERUP, it.x, it.y, collectibles.size, it.type)) }
        phases.addAll(phaseDefs)
        checkpoints.addAll(cps)
    }

    private fun createBody(e: SolidEntity) {
        val bd = BodyDef().apply {
            type = if (e.moving) BodyDef.BodyType.KinematicBody else BodyDef.BodyType.StaticBody
            position.set(e.x + e.w / 2f, e.y + e.h / 2f)
        }
        val body = world.createBody(bd)
        val shape = PolygonShape()
        shape.setAsBox(e.w / 2f, e.h / 2f)
        val fx = body.createFixture(shape, 0f)
        fx.friction = 0f
        fx.restitution = 0f
        fx.userData = e
        shape.dispose()
        body.userData = e
        e.body = body
    }

    /** Oyuncunun çok gerisinde kalan içeriği siler (yalnızca Sonsuz Dünya). */
    fun pruneBefore(x: Float) {
        for (e in solids) if (!e.removed && e.x + e.w + abs(e.def.ax) < x) {
            e.removed = true
            e.body?.let { world.destroyBody(it) }
            e.body = null
            e.active = false
        }
        for (h in hazards) if (h.def.x + h.def.w + abs(h.def.ax) < x) h.removed = true
        for (c in collectibles) if (c.x < x) c.removed = true
        ghostSolids.removeIf { it.removed }
        movingSolids.removeIf { it.removed }
        movingLastTick.keys.removeIf { it.removed }
        solidIndex.removeBefore(x); hazardIndex.removeBefore(x); padIndex.removeBefore(x); collectIndex.removeBefore(x)
    }

    fun solidsNear(x0: Float, x1: Float): List<SolidEntity> {
        solidIndex.query(x0, x1, seenSolids, tmpSolids); return tmpSolids
    }
    fun hazardsNear(x0: Float, x1: Float): List<HazardEntity> {
        hazardIndex.query(x0, x1, seenHaz, tmpHaz); return tmpHaz
    }
    fun padsNear(x0: Float, x1: Float): List<PadEntity> {
        padIndex.query(x0, x1, seenPads, tmpPads); return tmpPads
    }
    fun collectiblesNear(x0: Float, x1: Float): List<Collectible> {
        collectIndex.query(x0, x1, seenCol, tmpCol); return tmpCol
    }

    // ---------------------------------------------------------------- kurallar

    val currentPhase: PhaseDef? get() = phases.getOrNull(phaseIdx)
    val nextPhase: PhaseDef? get() = phases.getOrNull(phaseIdx + 1)

    /** Bir sonraki kural değişimine kalan süre (saniye, mevcut hızla). */
    fun secondsToNextRule(): Float {
        val n = nextPhase ?: return Float.POSITIVE_INFINITY
        val sp = max(0.5f, Physics.realSpeed(effects, config.speedMul))
        return max(0f, (n.x - player.x) / sp)
    }

    /** Mevcut aşamanın ilerleme oranı (0..1). */
    fun phaseProgress(): Float {
        val c = currentPhase ?: return 0f
        val n = nextPhase ?: return 0f
        return ((player.x - c.x) / (n.x - c.x)).coerceIn(0f, 1f)
    }

    private fun applyPhase(idx: Int, initial: Boolean = false) {
        val prev = activeRules
        phaseIdx = idx
        activeRules = RuleCompatibility.resolve(phases.getOrNull(idx)?.rules ?: listOf(Mechanic.NORMAL))
        val old = effects
        effects = RuleEffects.of(activeRules)
        world.gravity = Vector2(0f, -Physics.GRAVITY * effects.gravitySign)
        player.onEffectsChanged(old, effects, initial)
        if (!initial) {
            rulesChanged++
            phasesPassed++
            listener.onRuleChange(prev, activeRules)
        }
    }

    private fun updatePhase() {
        val px = player.x
        var idx = phaseIdx
        while (idx + 1 < phases.size && px >= phases[idx + 1].x) idx++
        if (idx != phaseIdx) applyPhase(idx)
        val n = nextPhase
        if (n != null && warnedPhase != phaseIdx + 1 && secondsToNextRule() <= Physics.WARNING_TIME) {
            warnedPhase = phaseIdx + 1
            listener.onRuleWarning(RuleCompatibility.resolve(n.rules))
        }
    }

    // ---------------------------------------------------------------- adım

    fun step(input: Boolean) {
        when (status) {
            SimStatus.COMPLETE, SimStatus.GAME_OVER -> return
            SimStatus.DYING -> {
                dyingTimer -= Physics.TICK
                tick++
                if (dyingTimer <= 0f) finishDeath()
                return
            }
            SimStatus.RUNNING -> {}
        }
        provider?.ensure(this, player.x + 45f)
        updatePhase()
        val e = effects
        val dt = Physics.TICK * e.timeScale

        // Değişen platform genliği ve saati
        val sc0 = shiftClock
        val sa0 = shiftAmp
        if (e.shifting) { shiftClock += dt; shiftAmp = min(1f, shiftAmp + dt * 1.6f) } else shiftAmp = max(0f, shiftAmp - dt * 1.6f)
        if (!e.shifting && shiftAmp > 0f) shiftClock += dt

        val px = player.x
        val wx0 = px - 18f
        val wx1 = px + 30f
        // Hareketli platformlar: bir sonraki konumlarına giden hızı ayarla
        for (s in solidsNear(wx0, wx1)) {
            if (!s.moving || s.removed) continue
            val body = s.body ?: continue
            val last = movingLastTick[s]
            if (last == null || last != tick - 1) {
                s.x = s.targetX(time); s.y = s.targetY(time, sc0, sa0)
                body.setTransform(s.x + s.w / 2f, s.y + s.h / 2f, 0f)
            }
            val tx = s.targetX(time + dt)
            val ty = s.targetY(time + dt, shiftClock, shiftAmp)
            body.setLinearVelocity((tx - s.x) / dt, (ty - s.y) / dt)
            movingLastTick[s] = tick
        }
        // Hayalet platformlar
        updateGhosts(e)

        player.preStep(input, dt)
        world.step(dt, 8, 4)
        for (s in solidsNear(wx0, wx1)) {
            if (!s.moving || s.removed) continue
            val b = s.body ?: continue
            s.x = b.position.x - s.w / 2f; s.y = b.position.y - s.h / 2f
        }
        time += dt
        tick++
        player.postStep(dt)
        if (status != SimStatus.RUNNING) return
        interactions(dt)
        if (status != SimStatus.RUNNING) return
        distance = max(distance, player.x - startX)

        // Güçlendirme süreleri
        magnet = max(0f, magnet - Physics.TICK)
        doubleJump = max(0f, doubleJump - Physics.TICK)
        coinMul = max(0f, coinMul - Physics.TICK)
        finder = max(0f, finder - Physics.TICK)
    }

    private fun updateGhosts(e: RuleEffects) {
        for (g in ghostSolids) {
            if (g.removed) continue
            val want: Boolean
            if (e.ghost) {
                val local = ((time + g.def.group * Physics.GHOST_CYCLE * 0.5f) % Physics.GHOST_CYCLE)
                want = local < Physics.GHOST_SOLID + Physics.GHOST_WARN
                g.ghostState = when {
                    local < Physics.GHOST_SOLID -> 0
                    local < Physics.GHOST_SOLID + Physics.GHOST_WARN -> 1
                    else -> 2
                }
            } else {
                want = true; g.ghostState = 0
            }
            if (want != g.active) {
                if (want && player.overlapsRect(g.x, g.y, g.x + g.w, g.y + g.h, -0.02f)) continue // içindeyken geri gelmez
                g.active = want
                g.body?.isActive = want
            }
            if (!g.active) g.ghostState = 2
        }
    }

    private fun interactions(dt: Float) {
        val p = player
        val px = p.x; val py = p.y; val hw = p.hw; val hh = p.hh
        // Sınırlar
        if (py < -4.5f || py > Physics.CEIL + 4.5f) { kill(DeathCause.FALL); return }
        // Engeller
        for (h in hazardsNear(px - 3f, px + 3f)) {
            if (h.removed) continue
            h.update(time)
            if (h.hits(px, py, hw, hh, if (config.strict) -0.1f else 0.08f)) {
                kill(if (h.def.type == HazardType.SAW) DeathCause.SAW else DeathCause.SPIKE)
                if (status != SimStatus.RUNNING || p.invuln > 0f) return
            }
        }
        // Pedler
        for (pad in padsNear(px - 2f, px + 2f)) {
            val d = pad.def
            if (px + hw * 0.6f < d.x || px - hw * 0.6f > d.x + d.w) continue
            when (d.type) {
                PadType.SPRING -> {
                    if (d.dir != effects.gravitySign) continue
                    val feet = if (d.dir > 0) py - hh else py + hh
                    val inside = if (d.dir > 0) feet <= d.y + 0.45f && feet >= d.y - 0.2f else feet >= d.y - 0.45f && feet <= d.y + 0.2f
                    if (inside && p.vyUp() <= 0.5f) {
                        p.springLaunch()
                        pad.anim = 0.4f
                        listener.onSpring()
                    }
                }
                PadType.BOOST -> {
                    val inside = if (d.dir > 0) py - hh <= d.y + 1.6f && py + hh >= d.y else py + hh >= d.y - 1.6f && py - hh <= d.y
                    if (inside) {
                        if (p.boost <= 0f) listener.onBoost()
                        p.boost = Physics.BOOST_TIME
                    }
                }
            }
        }
        // Toplanabilirler
        val reach = if (magnet > 0f) 3.2f else 0f
        for (c in collectiblesNear(px - 4f, px + 4f)) {
            if (c.collected || c.removed) continue
            if (c.kind == CollectKind.POWERUP && !config.powerups) continue
            val r = c.radius + (if (c.kind == CollectKind.COIN) reach else 0f)
            val nx = c.x.coerceIn(px - hw, px + hw)
            val ny = c.y.coerceIn(py - hh, py + hh)
            val dx = c.x - nx; val dy = c.y - ny
            if (dx * dx + dy * dy <= r * r) collect(c)
        }
        // Kontrol noktaları
        if (!config.solver) {
            for (cp in checkpoints) {
                if (cp > lastCheckpointX && px >= cp && p.grounded) {
                    lastCheckpointX = cp
                    checkpointSnap = snapshot()
                    listener.onCheckpoint(cp)
                }
            }
        }
        // Kovalayan duvar
        if (chaserEnabled) {
            chaserX += dt * Physics.RUN_SPEED * 0.9f * (if (effects.ice) 1.15f else 1f) * config.speedMul
            chaserX = max(chaserX, px - 12f)
            if (px - hw < chaserX + 0.3f) { kill(DeathCause.CHASER); return }
        }
        // Bitiş
        if (px >= goalX) {
            status = SimStatus.COMPLETE
            listener.onComplete()
        }
    }

    private fun collect(c: Collectible) {
        c.collected = true
        when (c.kind) {
            CollectKind.COIN -> {
                val v = if (coinMul > 0f) 2 else 1
                coinCount++; coinValue += v
                listener.onCoin(c, v)
            }
            CollectKind.STAR -> { starsCollected++; listener.onStar(c) }
            CollectKind.PIECE -> { pieceCollected = true; listener.onPiece(c) }
            CollectKind.POWERUP -> activatePowerup(c.powerup ?: return)
        }
    }

    fun activatePowerup(t: PowerupType) {
        powerupsUsed++
        when (t) {
            PowerupType.SHIELD -> shield = 1
            PowerupType.MAGNET -> magnet = t.duration
            PowerupType.DOUBLE_JUMP -> { doubleJump = t.duration; player.airJumps = 1 }
            PowerupType.INVINCIBLE -> player.invuln = max(player.invuln, t.duration)
            PowerupType.EXTRA_LIFE -> hearts++
            PowerupType.COIN_MULTIPLIER -> coinMul = t.duration
            PowerupType.STAR_FINDER -> finder = t.duration
            PowerupType.CHECKPOINT_GUARD -> guard = true
        }
        listener.onPowerup(t)
    }

    // ---------------------------------------------------------------- ölüm

    fun kill(cause: DeathCause) {
        if (status != SimStatus.RUNNING) return
        if (config.solver) { failed = true; deathCause = cause; status = SimStatus.DYING; dyingTimer = 1e9f; return }
        val hazardCause = cause == DeathCause.SPIKE || cause == DeathCause.SAW
        if (player.invuln > 0f && hazardCause) return
        if (player.invuln > 0f && cause == DeathCause.FALL) { player.rescue(); return }
        if (shield > 0 && cause != DeathCause.CHASER) {
            shield--
            player.rescue()
            listener.onShieldSave()
            return
        }
        deaths++
        deathCause = cause
        status = SimStatus.DYING
        dyingTimer = Physics.RESPAWN_DELAY
        listener.onDeath(cause)
    }

    private fun finishDeath() {
        if (guard) guard = false else hearts--
        if (hearts <= 0) {
            status = SimStatus.GAME_OVER
            listener.onGameOver()
            return
        }
        respawn()
    }

    /** Son kontrol noktasından devam (oyun bitti ekranından "devam et" için de kullanılır). */
    fun respawn() {
        val snap = checkpointSnap ?: return
        restore(snap)
        player.invuln = max(player.invuln, 1.2f)
        player.prevInput = true
        player.buffer = 0f
        if (chaserEnabled) chaserX = player.x - 9f
        status = SimStatus.RUNNING
        deathCause = null
        listener.onRespawn()
    }

    fun continueAfterGameOver(extraHearts: Int) {
        if (status != SimStatus.GAME_OVER) return
        hearts = extraHearts
        respawn()
    }

    // ---------------------------------------------------------------- anlık görüntü

    fun snapshot(): SimSnapshot {
        val p = player
        val inactive = ghostSolids.filter { !it.active }.map { it.index }.toIntArray()
        return SimSnapshot(
            tick, time, shiftClock, shiftAmp, phaseIdx, warnedPhase, chaserX,
            p.x, p.y, p.vx, p.vy, p.scale, p.grounded, p.coyote, p.buffer, p.prevInput, p.cutAllowed,
            p.autoTimer, p.airJumps, p.blocked, p.boost, p.vxCur, p.jumpLock, p.invuln, inactive, distance,
        )
    }

    fun restore(s: SimSnapshot) {
        tick = s.tick; time = s.time; shiftClock = s.shiftClock; shiftAmp = s.shiftAmp
        warnedPhase = s.warnedPhase; chaserX = s.chaserX; distance = s.distance
        if (s.phaseIdx != phaseIdx) {
            phaseIdx = s.phaseIdx
            activeRules = RuleCompatibility.resolve(phases.getOrNull(phaseIdx)?.rules ?: listOf(Mechanic.NORMAL))
            effects = RuleEffects.of(activeRules)
            world.gravity = Vector2(0f, -Physics.GRAVITY * effects.gravitySign)
        }
        for (g in ghostSolids) {
            val act = s.inactiveGhosts.indexOf(g.index) < 0
            g.active = act
            g.body?.isActive = act
        }
        movingLastTick.clear()
        for (m in movingSolids) {
            m.x = m.targetX(time); m.y = m.targetY(time, shiftClock, shiftAmp)
            m.body?.setTransform(m.x + m.w / 2f, m.y + m.h / 2f, 0f)
            m.body?.setLinearVelocity(0f, 0f)
        }
        player.restore(s, effects)
        status = SimStatus.RUNNING
        failed = false
    }

    override fun dispose() {
        world.dispose()
    }
}
