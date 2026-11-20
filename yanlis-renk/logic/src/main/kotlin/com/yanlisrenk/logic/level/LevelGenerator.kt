package com.yanlisrenk.logic.level

import com.yanlisrenk.logic.model.ColorCycle
import com.yanlisrenk.logic.model.GameColor
import com.yanlisrenk.logic.model.GateData
import com.yanlisrenk.logic.model.LevelData
import com.yanlisrenk.logic.model.Mechanic
import com.yanlisrenk.logic.model.Mechanic.*
import com.yanlisrenk.logic.model.Motion
import com.yanlisrenk.logic.model.PadData
import com.yanlisrenk.logic.model.PadType
import com.yanlisrenk.logic.model.PickupData
import com.yanlisrenk.logic.model.PickupType
import com.yanlisrenk.logic.model.RuleType
import com.yanlisrenk.logic.model.SignData
import com.yanlisrenk.logic.model.ZoneData
import com.yanlisrenk.logic.model.ZoneType
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.random.Random

/**
 * Tohumlu, belirlenimci bölüm üreteci. Her bölüm önce zaman alanında planlanır
 * (kapılar arası süre = tepki + dokunuş + pay), sonra konuma çevrilir ve
 * [LevelValidator] ile doğrulanır. Doğrulanamayan plan daha geniş boşluklarla yeniden üretilir.
 */
object LevelGenerator {
    val ZONE_MECHS = setOf(SPEED, LOCK, MIRROR, FOG, WAVE)
    private val GATE_MECHS = listOf(SAME, DIFF, DOUBLE, MEMORY, ROTATE, RAINBOW, TIMED, MAZE, HIDDEN, STROOP, TRIPLE,
        AUTOSHIFT, SLIDE, CHAIN, MIX, NONE, NEXT, PREV, REPEAT, DECOY, FLICKER)

    class Params(
        val id: Int,
        val area: Int,
        val index: Int,
        val isFinal: Boolean,
        /** 0..1 genel zorluk */
        val d: Float,
        val speed: Float,
        val reaction: Float,
        val gateCount: Int,
        val mechanics: List<Mechanic>,
        /** Bölümün öğrettiği yeni mekanikler (giriş bölümü). */
        val intro: List<Mechanic>,
        val seed: Long,
        val gapMul: Float = 1f,
        val coinDensity: Float = 1f,
        val coloredCoins: Boolean = true,
        val diamond: Boolean = false,
        val endless: Boolean = false,
        val shieldChance: Float = 0f,
        val wordBias: Float = 0.5f,
        /** >0 ise oyuncunun görüş mesafesi bu kadardır (ör. bölünmüş ekran); doğrulama buna göre yapılır. */
        val viewDist: Float = 0f,
    )

    var debug = false
    var fallbackCount = 0
        private set

    // ---------- 500 bölümün parametreleri ----------

    fun paramsFor(levelId: Int): Params {
        val area = AreaCatalog.area(AreaCatalog.areaOfLevel(levelId))
        val index = (levelId - 1) % 5
        val isFinal = index == 4
        val d = (levelId - 1) / 499f
        val speed = (4.4f + 3.3f * d.pow(0.85f)) * area.speedMul * (if (isFinal) 1.03f else 1f)
        val reaction = 0.62f - 0.26f * d.pow(0.7f)
        val baseCount = 7 + index * 2 + levelId / 22
        val count = minOf(34, baseCount) + if (isFinal) 6 else 0
        val rng = Random(levelId * 7919L + 17)
        val focus = area.focus.flatMap { if (it == EXAM) emptyList() else listOf(it) }
        val learned = AreaCatalog.learnedBefore(area.id)
        val mechs = LinkedHashSet<Mechanic>()
        mechs.add(SAME)
        if (DIFF in learned || DIFF in focus) mechs.add(DIFF)
        val focusTake = when {
            area.focus.size <= 3 -> focus
            isFinal -> focus
            else -> focus.shuffled(rng).take(2 + index)
        }
        mechs.addAll(focusTake)
        val reviewCount = when (index) { 0 -> 0; 1 -> 1; 2 -> 1; 3 -> 2; else -> 3 }
        val recent = learned.takeLast(8).filter { it !in mechs }
        val older = learned.dropLast(8).filter { it !in mechs }
        val review = (recent.shuffled(rng).take(reviewCount) + older.shuffled(rng).take(if (index >= 3) 1 else 0))
        mechs.addAll(review)
        val intro = if (index == 0) area.introduces else emptyList()
        return Params(
            id = levelId, area = area.id, index = index, isFinal = isFinal, d = d, speed = speed,
            reaction = reaction, gateCount = count, mechanics = mechs.toList(), intro = intro,
            seed = levelId * 104729L + 3, gapMul = area.gapMul * (if (index == 0) 1.12f else 1f),
            coinDensity = area.coinDensity, coloredCoins = levelId > 5,
            diamond = isFinal || levelId % 13 == 0,
            wordBias = if (area.id == 71) 0.85f else 0.5f,
        )
    }

    fun generateLevel(levelId: Int): LevelData = generate(paramsFor(levelId))

    // ---------- üretim ----------

    fun generate(p: Params): LevelData {
        var slack = 1f
        for (attempt in 0 until 8) {
            val lvl = tryGenerate(p, Random(p.seed + attempt * 31L), slack)
            val finished = finishLevel(lvl, p)
            if (finished != null) return finished
            if (debug) println("L${p.id} deneme $attempt: " + LevelValidator.validate(lvl).reason)
            slack *= 1.18f
        }
        fallbackCount++
        val simple = Params(p.id, p.area, p.index, p.isFinal, p.d * 0.5f, p.speed * 0.9f, p.reaction, p.gateCount,
            listOf(SAME, DIFF), emptyList(), p.seed + 999, 1.6f, p.coinDensity, p.coloredCoins, p.diamond, p.endless)
        for (attempt in 0 until 20) {
            val lvl = tryGenerate(simple, Random(simple.seed + attempt), 1.5f + attempt * 0.2f)
            finishLevel(lvl.copy(mechanics = p.mechanics), p)?.let { return it }
        }
        error("Bölüm ${p.id} üretilemedi")
    }

    /** Doğrular, yıldız hedeflerini ve çözümü ekler. */
    fun finishLevel(lvl: LevelData, p: Params): LevelData? {
        val starts = if (p.endless) (0..3).toList() else listOf(lvl.startColor)
        val lasts = if (p.endless) listOf(0, 1, 2, 3) else listOf(-1)
        val r0 = LevelValidator.validate(lvl, startColors = starts, startLastGate = lasts)
        if (!r0.ok) return null
        if (p.endless) return lvl
        // önemsiz bölüm olmasın: en az kapıların dörtte biri kadar dokunuş gereksin
        if (r0.minTaps < maxOf(2, lvl.gates.size / 4)) return null
        val maxCoins = r0.maxCoins
        val target = if (maxCoins <= 1) maxCoins else ceil(maxCoins * 0.7f).toInt()
        val r = LevelValidator.validate(lvl, coinTargetRequired = target)
        if (!r.ok) return null
        val slackTaps = when { p.id <= 25 -> 3; p.d < 0.5f -> 2; else -> 1 }
        val difficulty = (1 + 80 * p.d + p.mechanics.size * 2 + (if (p.isFinal) 6 else 0) + p.index).toInt().coerceIn(1, 100)
        return lvl.copy(
            coinTarget = target, tapPar = r.chosenTaps + slackTaps, solution = r.gateColors,
            minTaps = r0.minTaps, maxCoins = maxCoins, difficulty = difficulty,
        )
    }

    private class Plan(
        val gate: GateData,
        val time: Float,
        val target: Int,
        /** Hareketli kapıda varışta hedef rengin indeksi */
        val motionIdx: Int = -1,
        val padBefore: PadData? = null,
        val padTime: Float = 0f,
        val chainGroupStart: Boolean = false,
        val chainGroup: Int = -1,
    )

    private class ZonePlan(val type: ZoneType, val from: Int, val to: Int, val value: Float, val colors: List<Int> = emptyList())

    private fun tryGenerate(p: Params, rng: Random, slackScale: Float): LevelData {
        val mechs = p.mechanics.toMutableList()
        val has = { m: Mechanic -> m in mechs }
        val quick = has(QUICK) || has(MAZE)
        val n = p.gateCount
        val react = p.reaction
        val tap = LevelValidator.TAP_GAP
        val margin = LevelValidator.MARGIN

        // --- bölge planı (gate index aralıkları) ---
        val zones = ArrayList<ZonePlan>()
        val speedUsed = BooleanArray(n)
        fun pickRange(minLen: Int, maxLen: Int, used: BooleanArray?): IntRange? {
            repeat(12) {
                val len = rng.nextInt(minLen, maxLen + 1).coerceAtMost(n - 2)
                if (len < 2) return null
                val a = rng.nextInt(1, maxOf(2, n - len))
                val b = minOf(n - 1, a + len - 1)
                if (used == null || (a..b).none { used[it] }) return a..b
            }
            return null
        }
        val zoneRepeats = if (p.isFinal) 2 else 1
        if (has(SPEED)) repeat(zoneRepeats + if (p.intro.contains(SPEED)) 1 else 0) {
            pickRange(3, 6, speedUsed)?.let { r ->
                r.forEach { speedUsed[it] = true }
                val mult = if (p.intro.contains(SPEED)) 1.3f else 1.3f + 0.3f * rng.nextFloat() + 0.1f * p.d
                zones.add(ZonePlan(ZoneType.SPEED, r.first, r.last, mult))
            }
        }
        if (has(WAVE)) pickRange(6, 10, speedUsed)?.let { r ->
            r.forEach { speedUsed[it] = true }
            var i = r.first
            var fast = true
            while (i <= r.last) {
                val e = minOf(r.last, i + 1)
                zones.add(ZonePlan(ZoneType.SPEED, i, e, if (fast) 1.35f + 0.15f * p.d else 0.8f))
                fast = !fast; i = e + 1
            }
        }
        val lockUsed = BooleanArray(n)
        if (has(LOCK)) repeat(zoneRepeats) {
            pickRange(3, 6, lockUsed)?.let { r ->
                r.forEach { lockUsed[it] = true }
                val cnt = if (p.area == 52 || (p.d > 0.5f && rng.nextFloat() < 0.35f)) 2 else 1
                val locked = (0..3).shuffled(rng).take(cnt)
                zones.add(ZonePlan(ZoneType.LOCK, r.first, r.last, 1f, locked))
            }
        }
        val mirUsed = BooleanArray(n)
        if (has(MIRROR)) repeat(zoneRepeats) {
            pickRange(3, 7, mirUsed)?.let { r -> r.forEach { mirUsed[it] = true }; zones.add(ZonePlan(ZoneType.MIRROR, r.first, r.last, 1f)) }
        }
        val fogUsed = BooleanArray(n)
        if (has(FOG)) repeat(zoneRepeats) {
            pickRange(4, 8, fogUsed)?.let { r -> r.forEach { fogUsed[it] = true }; zones.add(ZonePlan(ZoneType.FOG, r.first, r.last, 0f)) }
        }
        fun speedMultAt(i: Int): Float = zones.firstOrNull { it.type == ZoneType.SPEED && i in it.from..it.to }?.value ?: 1f
        fun maskAt(i: Int): Int = zones.firstOrNull { it.type == ZoneType.LOCK && i in it.from..it.to }?.colors?.fold(0) { m, c -> m or (1 shl c) } ?: 0
        fun dirAt(i: Int): Int = if (zones.any { it.type == ZoneType.MIRROR && i in it.from..it.to }) -1 else 1

        // --- kapı mekanik havuzu ---
        val gatePool = mechs.filter { it in GATE_MECHS }.toMutableList()
        if (gatePool.isEmpty()) gatePool.add(SAME)
        val weights = gatePool.map { m ->
            var w = when (m) { SAME -> 1.0f; DIFF -> 0.9f; else -> 1.6f }
            if (m in p.intro) w *= 2.2f
            if (m == CHAIN || m == TRIPLE || m == DOUBLE || m == MAZE) w *= 0.6f
            w
        }
        fun pickMech(): Mechanic {
            val tot = weights.sum()
            var r = rng.nextFloat() * tot
            for (i in gatePool.indices) { r -= weights[i]; if (r <= 0f) return gatePool[i] }
            return gatePool.last()
        }

        val plans = ArrayList<Plan>()
        var pc = rng.nextInt(4)
        val startColor = pc
        var lastGate = -1
        var t = if (p.endless) react + 3 * tap + margin + 0.6f else 2.6f + react
        var chainId = 0

        fun slack(): Float {
            var s = (0.35f + 0.8f * rng.nextFloat()) * (1f - 0.5f * p.d) * p.gapMul * slackScale
            if (quick) s *= 0.45f
            return s
        }

        fun need(target: Int, from: Int, i: Int): Int {
            val k = ColorCycle.tapsTo(from, target, dirAt(i), maskAt(i))
            return if (k < 0) 99 else k
        }

        /** Geçerli maske içinden en az dokunuşla ulaşılan rengi seçer. */
        fun bestOf(valid: Int, from: Int, i: Int): Pair<Int, Int> {
            var best = -1; var bk = 99
            for (c in 0..3) if (valid and (1 shl c) != 0) { val k = need(c, from, i); if (k < bk) { bk = k; best = c } }
            return best to bk
        }

        fun randomTarget(from: Int, i: Int, maxK: Int, preferChange: Float = 0.75f): Int {
            val opts = (0..3).filter { need(it, from, i) <= maxK }
            val change = opts.filter { it != from }
            return if (change.isNotEmpty() && rng.nextFloat() < preferChange) change.random(rng) else opts.random(rng)
        }

        var i = 0
        while (i < n) {
            // kilit bölgesine girerken renk zorla değişir
            if (i > 0 && maskAt(i) != maskAt(i - 1) && maskAt(i) != 0) pc = ColorCycle.forceUnlocked(pc, maskAt(i))
            val introPhase = p.intro.isNotEmpty() && i < 2
            val m = if (introPhase) (if (SAME in p.intro || DIFF !in mechs) SAME else DIFF) else pickMech()
            val zoneStartExtra = if (i > 0 && (maskAt(i) != maskAt(i - 1) || dirAt(i) != dirAt(i - 1))) react * 0.8f + 0.25f else 0f
            val mask = maskAt(i)
            val unlocked = 0xF and mask.inv()

            fun singleGate(kind: Mechanic, maxK: Int, short: Boolean): Plan? {
                var gate: GateData
                var target: Int
                var motionIdx = -1
                var extra = 0f
                when (kind) {
                    SAME, QUICK, COINS, MAZE -> {
                        target = randomTarget(pc, i, maxK)
                        gate = GateData(0f, RuleType.SAME, listOf(target))
                    }
                    DIFF -> {
                        val base = if (rng.nextFloat() < 0.6f) pc else (0..3).random(rng)
                        gate = GateData(0f, RuleType.DIFF, listOf(base))
                        val (c, k) = bestOf(gate.validMask(0f, lastGate) and unlocked, pc, i)
                        if (c < 0 || k > maxK) return null
                        target = c
                    }
                    RAINBOW, MIX -> {
                        val cnt = if (kind == MIX) 2 else 2 + rng.nextInt(2)
                        val set = (0..3).shuffled(rng).take(cnt)
                        gate = GateData(0f, RuleType.ANY, set.sorted(), split = kind == MIX)
                        val (c, k) = bestOf(gate.validMask(0f, lastGate) and unlocked, pc, i)
                        if (c < 0 || k > maxK) return null
                        target = c
                    }
                    NONE -> {
                        val set = ((listOf(pc) + (0..3).filter { it != pc }.shuffled(rng).take(1))).sorted()
                        gate = GateData(0f, RuleType.NONE, set)
                        val (c, k) = bestOf(gate.validMask(0f, lastGate) and unlocked, pc, i)
                        if (c < 0 || k > maxK) return null
                        target = c
                    }
                    NEXT, PREV -> {
                        target = randomTarget(pc, i, maxK, 0.6f)
                        val base = GameColor.norm(if (kind == NEXT) target - 1 else target + 1)
                        gate = GateData(0f, if (kind == NEXT) RuleType.NEXT else RuleType.PREV, listOf(base))
                        extra = 0.25f
                    }
                    REPEAT -> {
                        if (lastGate < 0) return null
                        target = lastGate
                        if (need(target, pc, i) > maxK) return null
                        gate = GateData(0f, RuleType.REPEAT)
                    }
                    DECOY -> {
                        target = pc
                        gate = GateData(0f, RuleType.FREE, listOf((0..3).random(rng)))
                    }
                    STROOP -> {
                        target = randomTarget(pc, i, maxK)
                        val readWord = rng.nextFloat() < p.wordBias
                        val other = (0..3).filter { it != target }.random(rng)
                        gate = if (readWord) GateData(0f, RuleType.SAME, listOf(other), word = target, readBy = 1)
                        else GateData(0f, RuleType.SAME, listOf(target), word = other, readBy = 0)
                        extra = 0.2f
                    }
                    MEMORY -> {
                        target = randomTarget(pc, i, maxK)
                        gate = GateData(0f, RuleType.SAME, listOf(target), memoryDist = 0f) // mesafe konumdan sonra
                    }
                    HIDDEN -> {
                        target = randomTarget(pc, i, maxK)
                        gate = if (rng.nextFloat() < 0.3f && DIFF in mechs) {
                            val g = GateData(0f, RuleType.DIFF, listOf(pc))
                            val (c, _) = bestOf(g.validMask(0f, -1) and unlocked, pc, i); target = c; g
                        } else GateData(0f, RuleType.SAME, listOf(target))
                        extra = 0.15f
                    }
                    FLICKER -> {
                        target = randomTarget(pc, i, maxK)
                        gate = GateData(0f, RuleType.SAME, listOf(target), flicker = true)
                    }
                    ROTATE, SLIDE, TIMED -> {
                        val cols = when (kind) {
                            ROTATE -> (0..3).shuffled(rng)
                            SLIDE -> (0..3).shuffled(rng).take(2)
                            else -> (0..3).shuffled(rng).take(2 + rng.nextInt(3))
                        }.filter { unlocked and (1 shl it) != 0 }
                        if (cols.size < 2) return null
                        val diff = p.d > 0.4f && DIFF in mechs && rng.nextFloat() < 0.3f
                        val motion = when (kind) { ROTATE -> Motion.ROTATE; SLIDE -> Motion.SLIDE; else -> Motion.TIMED }
                        val seg = when (kind) {
                            ROTATE -> 0.75f + 0.5f * rng.nextFloat()
                            SLIDE -> 0.9f + 0.6f * rng.nextFloat()
                            else -> 0.85f + 0.7f * rng.nextFloat()
                        } * (1f - 0.25f * p.d)
                        val idxOpts = cols.indices.filter { need(cols[it], pc, i) <= maxK || diff }
                        if (idxOpts.isEmpty()) return null
                        motionIdx = idxOpts.random(rng)
                        gate = GateData(0f, if (diff) RuleType.DIFF else RuleType.SAME, cols, motion = motion, segDur = seg)
                        if (diff) {
                            val (c, k) = bestOf(gate.validMask(0f, -1).let { 0xF and (1 shl cols[motionIdx]).inv() } and unlocked, pc, i)
                            if (c < 0 || k > maxK) return null
                            target = c
                        } else target = cols[motionIdx]
                        extra = 0.3f
                    }
                    else -> {
                        target = randomTarget(pc, i, maxK)
                        gate = GateData(0f, RuleType.SAME, listOf(target))
                    }
                }
                if (unlocked and (1 shl target) == 0) return null
                val k = need(target, pc, i)
                if (k > maxK) return null
                val gap = react + maxOf(0, k - 1) * tap + margin + extra +
                    (if (short) 0.06f + 0.18f * rng.nextFloat() else slack()) + zoneStartExtra
                t += gap
                return Plan(gate, t, target, motionIdx)
            }

            fun commit(pl: Plan) {
                plans.add(pl)
                pc = pl.target
                lastGate = pl.target
                i++
            }

            fun singleRetry(kind: Mechanic, maxK: Int, short: Boolean): Plan {
                val saveT = t
                repeat(6) {
                    singleGate(kind, maxK, short)?.let { return it }
                    t = saveT
                }
                t = saveT
                return singleGate(SAME, maxK, short) ?: run {
                    t += react + 3 * tap + margin + slack()
                    Plan(GateData(0f, RuleType.FREE, listOf(pc)), t, pc)
                }
            }

            when (m) {
                DOUBLE, TRIPLE -> {
                    val cnt = if (m == DOUBLE) 2 else 3
                    commit(singleRetry(listOf(SAME, DIFF).filter { it in mechs }.random(rng), 3, false))
                    repeat(cnt - 1) {
                        if (i < n) {
                            val inner = listOf(SAME, DIFF, RAINBOW, MIX).filter { it in mechs || it == SAME }.random(rng)
                            commit(singleRetry(inner, 1, true))
                        }
                    }
                }
                MAZE -> {
                    repeat(3) {
                        if (i < n) commit(singleRetry(listOf(SAME, DIFF, RAINBOW).filter { it in mechs || it == SAME }.random(rng), 2, it > 0))
                    }
                }
                CHAIN -> {
                    val cnt = minOf(n - i, 3 + (if (p.d > 0.5f) 1 else 0))
                    val groupId = chainId++
                    t += 0.9f // tabela için ek mesafe
                    repeat(cnt) { j ->
                        val pl = singleRetry(SAME, 3, false)
                        commit(Plan(pl.gate.copy(chain = true), pl.time, pl.target, chainGroupStart = j == 0, chainGroup = groupId))
                    }
                }
                AUTOSHIFT -> {
                    // ped: önceki kapıdan kısa süre sonra rengi değiştirir
                    val padTime = t + 0.35f + 0.2f * rng.nextFloat()
                    val pad = if (rng.nextFloat() < 0.4f && (p.area == 66 || p.d > 0.3f)) PadData(0f, PadType.SET, (0..3).random(rng))
                    else PadData(0f, PadType.SHIFT, 1 + if (rng.nextFloat() < 0.3f) 1 else 0)
                    val before = pc
                    pc = ColorCycle.forceUnlocked(if (pad.type == PadType.SHIFT) GameColor.norm(pc + pad.value) else pad.value, mask)
                    t = padTime
                    val pl = singleRetry(listOf(SAME, DIFF, RAINBOW).filter { it in mechs || it == SAME }.random(rng), 3, false)
                    // pedden sonra tam tepki süresi bırak
                    val minAfter = react + 2 * tap + margin + 0.25f
                    val time = maxOf(pl.time, padTime + minAfter)
                    t = time
                    commit(Plan(pl.gate, time, pl.target, pl.motionIdx, pad, padTime))
                }
                else -> commit(singleRetry(m, 3, false))
            }
        }

        // --- zaman → konum ---
        val v = p.speed
        val ys = FloatArray(plans.size)
        var prevT = 0f
        var prevY = 0f
        for (k in plans.indices) {
            val dt = plans[k].time - prevT
            val mPrev = if (k == 0) 1f else speedMultAt(k - 1)
            val mult = (mPrev + speedMultAt(k)) * 0.5f
            ys[k] = prevY + dt * v * mult
            prevT = plans[k].time; prevY = ys[k]
        }
        fun mid(a: Int, frac: Float): Float = if (a <= 0) ys[0] * frac else ys[a - 1] + (ys[a] - ys[a - 1]) * frac

        val zoneData = ArrayList<ZoneData>()
        val fogDist = v * (react + 0.9f)
        for (z in zones) {
            val startFrac = if (z.type == ZoneType.SPEED) 0.5f else 0.2f
            val y1 = mid(z.from, startFrac)
            val y2 = if (z.to + 1 < plans.size) mid(z.to + 1, 0.5f) else ys[z.to] + v
            zoneData.add(
                when (z.type) {
                    ZoneType.SPEED -> ZoneData(y1, y2, ZoneType.SPEED, z.value)
                    ZoneType.LOCK -> ZoneData(y1, y2, ZoneType.LOCK, 1f, z.colors.sorted())
                    ZoneType.MIRROR -> ZoneData(y1, y2, ZoneType.MIRROR)
                    ZoneType.FOG -> ZoneData(y1, y2, ZoneType.FOG, fogDist * speedMultAt(z.from))
                }
            )
        }
        val tl = Timeline.build(v, zoneData)

        // --- kapıları yerleştir ---
        val gates = ArrayList<GateData>()
        val pads = ArrayList<PadData>()
        val signs = ArrayList<SignData>()
        val pickups = ArrayList<PickupData>()
        val chainMembers = HashMap<Int, MutableList<Int>>()
        for (k in plans.indices) {
            val pl = plans[k]
            var g = pl.gate.copy(y = ys[k])
            if (g.motion != Motion.STATIC) {
                val arr = tl.timeAt(g.y)
                val nSeg = g.colors.size
                val ph = (pl.motionIdx + 0.5f) - arr / g.segDur
                g = g.copy(phase = ((ph % nSeg) + nSeg) % nSeg)
            }
            gates.add(g)
            if (pl.padBefore != null) {
                val py = (ys.getOrElse(k - 1) { 0f }).let { it + (tl.yAt(pl.padTime) - it).coerceAtLeast(0.6f) }
                pads.add(pl.padBefore.copy(y = minOf(py, ys[k] - 0.8f)))
            }
            if (pl.chainGroup >= 0) chainMembers.getOrPut(pl.chainGroup) { mutableListOf() }.add(k)
            // altınlar
            if (k > 0 || p.endless) {
                val prevY0 = if (k == 0) 0f else ys[k - 1]
                val gapY = ys[k] - prevY0
                val chance = 0.42f * p.coinDensity
                if (gapY > 2.2f && rng.nextFloat() < chance) {
                    val manyCoins = COINS in mechs && rng.nextFloat() < 0.6f
                    val cnt = if (manyCoins) 3 else 1
                    val baseColor = if (!p.coloredCoins) -1 else if (manyCoins && rng.nextFloat() < 0.5f) (0..3).random(rng) else pl.target
                    for (c in 0 until cnt) {
                        val y = prevY0 + gapY * (0.62f + c * 0.1f)
                        if (y < ys[k] - 0.7f) pickups.add(PickupData(y, PickupType.COIN, baseColor))
                    }
                }
            }
        }
        // gizli ve hafıza mesafeleri (kapı türüne göre)
        for (k in plans.indices) {
            val pl = plans[k]
            val kind = pl.gate
            val ve = v * speedMultAt(k)
            val kTaps = 3
            if (k < gates.size) {
                val g = gates[k]
                val isHidden = HIDDEN in mechs && pl.gate.rule != RuleType.FREE && !g.chain && rng.nextFloat() < (if (HIDDEN in p.intro) 0.55f else 0.3f) && g.motion == Motion.STATIC
                if (isHidden) gates[k] = g.copy(revealDist = ve * (react + (kTaps - 1) * tap + margin + 0.45f + 0.4f * rng.nextFloat()))
                val isMem = MEMORY in mechs && !g.chain && g.rule == RuleType.SAME && g.motion == Motion.STATIC && !isHidden &&
                    rng.nextFloat() < (if (MEMORY in p.intro) 0.6f else 0.35f)
                if (isMem) gates[k] = gates[k].copy(memoryDist = ve * (0.9f + 0.8f * rng.nextFloat() + 0.4f * p.d))
            }
        }
        // zincir tabelaları
        for ((_, members) in chainMembers) {
            val first = members.first()
            val prevY0 = if (first == 0) 0f else ys[first - 1]
            val sy = prevY0 + (ys[first] - prevY0) * 0.35f
            signs.add(SignData(sy, members))
        }
        // elmas / kalkan
        if (p.diamond && gates.size > 4) {
            val k = gates.size / 2
            pickups.add(PickupData((ys[k - 1] + ys[k]) * 0.5f + 0.3f, PickupType.DIAMOND))
        }
        if (p.shieldChance > 0f && rng.nextFloat() < p.shieldChance && gates.size > 3) {
            val k = 1 + rng.nextInt(gates.size - 2)
            pickups.add(PickupData((ys[k - 1] + ys[k]) * 0.5f - 0.4f, PickupType.SHIELD))
        }
        pickups.sortBy { it.y }
        val length = ys.last() + v * 2.2f
        return LevelData(
            id = p.id, area = p.area, index = p.index, isFinal = p.isFinal, speed = v, length = length,
            difficulty = 1, startColor = startColor, reaction = react, gates = gates, pickups = pickups,
            zones = zoneData, pads = pads.sortedBy { it.y }, signs = signs.sortedBy { it.y }, mechanics = p.mechanics,
            fogDist = p.viewDist,
        )
    }

    // ---------- Ustalık modu ----------

    /**
     * Tamamlanmış bölümün zor sürümü: %15 daha hızlı ve bazı kapılar hafıza kapısına dönüşür.
     * Hareketli kapıların fazı yeni hızla yeniden hizalanır. Doğrulanamazsa kapılar aralanır.
     */
    fun mastery(level: LevelData): LevelData {
        val rng = Random(level.id * 31L + 5)
        for (mul in floatArrayOf(1.15f, 1.1f, 1.06f, 1.0f)) {
        var spread = 1f
        for (attempt in 0 until 10) {
            val speed = level.speed * mul
            val memChance = if (mul > 1.1f) 0.35f else 0.15f
            val gates0 = level.gates.map { g ->
                val gg = g.copy(y = g.y * spread, revealDist = g.revealDist * 1.15f, memoryDist = g.memoryDist * 1.15f)
                if (gg.rule == RuleType.SAME && gg.motion == Motion.STATIC && gg.memoryDist == 0f && !gg.chain && gg.revealDist == 0f && rng.nextFloat() < memChance)
                    gg.copy(memoryDist = speed * 1.1f) else gg
            }
            val zones = level.zones.map { it.copy(y1 = it.y1 * spread, y2 = it.y2 * spread, value = if (it.type == ZoneType.FOG) it.value * 1.15f else it.value) }
            val oldTl = Timeline.build(level.speed, level.zones)
            val newTl = Timeline.build(speed, zones)
            val gates = gates0.mapIndexed { idx, g ->
                if (g.motion == Motion.STATIC) g else {
                    val oldG = level.gates[idx]
                    val idxAtArrival = oldG.motionIndex(oldTl.timeAt(oldG.y))
                    val arr = newTl.timeAt(g.y)
                    val n = g.colors.size
                    val ph = (idxAtArrival + 0.5f) - arr / g.segDur
                    g.copy(phase = ((ph % n) + n) % n)
                }
            }
            val m = level.copy(
                id = level.id, speed = speed, gates = gates, zones = zones,
                length = level.length * spread,
                pickups = level.pickups.map { it.copy(y = it.y * spread) },
                pads = level.pads.map { it.copy(y = it.y * spread) },
                signs = level.signs.map { it.copy(y = it.y * spread) },
                fogDist = level.fogDist,
            )
            val r0 = LevelValidator.validate(m)
            if (r0.ok) {
                val target = if (r0.maxCoins <= 1) r0.maxCoins else ceil(r0.maxCoins * 0.7f).toInt()
                val r = LevelValidator.validate(m, coinTargetRequired = target)
                if (r.ok) return m.copy(coinTarget = target, tapPar = r.chosenTaps + 1, solution = r.gateColors, minTaps = r0.minTaps, maxCoins = r0.maxCoins,
                    difficulty = minOf(100, level.difficulty + 10))
            }
            spread *= 1.1f
        }
        }
        error("Ustalık sürümü üretilemedi: ${level.id}")
    }
}
