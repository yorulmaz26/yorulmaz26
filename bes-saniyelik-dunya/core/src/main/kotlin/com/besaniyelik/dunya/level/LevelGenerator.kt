package com.besaniyelik.dunya.level

import com.besaniyelik.dunya.sim.*
import java.util.Random
import kotlin.math.max
import kotlin.math.min

/** Kural aşaması planı ve geometrisi üretilmiş, henüz toplanabilirleri yerleştirilmemiş bölüm taslağı. */
class LevelDraft(
    val data: LevelData,
    val features: List<Feature>,
    val coinHints: List<Vec>,
    val phaseRanges: List<Pair<Float, Float>>,
)

object PhasePlanner {
    /** Bölüm için her 5 saniyelik aşamanın kurallarını planlar. */
    fun plan(levelId: Int, count: Int, rng: Random): List<List<Mechanic>> {
        val kind = GameContent.kindOf(levelId)
        val d = GameContent.difficulty(levelId)
        val learned = Mechanic.unlockedAt(levelId).filter { it != Mechanic.NORMAL }
        if (levelId <= 10 || learned.isEmpty()) return List(count) { listOf(Mechanic.NORMAL) }
        val stage = GameContent.stageOf(levelId)
        val map = GameContent.mapOf(levelId)
        val out = ArrayList<List<Mechanic>>()

        if (levelId <= 100) {
            // Öğrenme dönemi: en yeni mekanik odakta, öncekiler tek tek tekrar edilir
            val focus = learned.maxBy { it.unlockLevel }
            val older = learned.filter { it != focus }
            for (i in 0 until count) {
                val rules: List<Mechanic> = when {
                    kind == LevelKind.TUTORIAL -> if (i % 2 == 1) listOf(focus) else listOf(Mechanic.NORMAL)
                    i == 0 -> listOf(Mechanic.NORMAL)
                    i % 2 == 1 -> listOf(focus)
                    older.isNotEmpty() && rng.nextFloat() < 0.6f -> {
                        val o = older[rng.nextInt(older.size)]
                        // 81+: iki kurallı kısa kombinasyonlar da başlar
                        if (levelId > 85 && rng.nextFloat() < 0.35f && RuleCompatibility.isValid(listOf(o, focus), d)) listOf(o, focus) else listOf(o)
                    }
                    else -> listOf(Mechanic.NORMAL)
                }
                out.add(rules)
            }
            return out
        }

        val maxCombo = when {
            kind == LevelKind.GRAND_FINAL -> 3
            levelId > 350 -> 3
            levelId > 200 -> if (kind == LevelKind.CHALLENGE || kind == LevelKind.FINAL) 3 else 2
            else -> 2
        }
        val favored = (stage.favored + map.focus).filter { it != Mechanic.NORMAL }.distinct()
        var prev: List<Mechanic> = listOf(Mechanic.NORMAL)
        // Büyük final: her mekanik en az bir kez görünür
        val mustShow = if (kind == LevelKind.GRAND_FINAL || kind == LevelKind.FINAL) ArrayDeque(learned.shuffled(rng)) else ArrayDeque()
        for (i in 0 until count) {
            if (i == 0) { out.add(listOf(Mechanic.NORMAL)); continue }
            var rules: List<Mechanic>? = null
            for (attempt in 0 until 40) {
                val first = if (mustShow.isNotEmpty() && attempt < 20) mustShow.first()
                else if (favored.isNotEmpty() && rng.nextFloat() < 0.6f) favored[rng.nextInt(favored.size)]
                else learned[rng.nextInt(learned.size)]
                val set = mutableListOf(first)
                val wantCombo = when {
                    stage.combo >= 2 -> min(maxCombo, stage.combo - (if (rng.nextFloat() < 0.35f) 1 else 0))
                    else -> if (rng.nextFloat() < 0.25f + d * 0.04f) 2 else 1
                }.coerceAtMost(maxCombo)
                var guard = 0
                while (set.size < wantCombo && guard++ < 20) {
                    val m = if (rng.nextFloat() < 0.5f && favored.isNotEmpty()) favored[rng.nextInt(favored.size)] else learned[rng.nextInt(learned.size)]
                    if (m !in set && RuleCompatibility.isValid(set + m, d)) set.add(m)
                }
                if (!RuleCompatibility.isValid(set, d)) continue
                if (set.toSet() == prev.toSet()) continue
                rules = set
                break
            }
            val r = rules ?: listOf(Mechanic.NORMAL)
            if (mustShow.isNotEmpty() && mustShow.first() in r) mustShow.removeFirst()
            // ara sıra nefes aldıran normal aşama
            val final = if (i > 1 && rng.nextFloat() < 0.1f && kind == LevelKind.NORMAL && prev != listOf(Mechanic.NORMAL)) listOf(Mechanic.NORMAL) else r
            out.add(final)
            prev = final
        }
        return out
    }
}

object LevelGenerator {
    private fun phaseCount(levelId: Int, rng: Random): Int {
        val map = (levelId - 1) / 50
        return when (GameContent.kindOf(levelId)) {
            LevelKind.GRAND_FINAL -> 20
            LevelKind.FINAL -> 13 + map / 2
            LevelKind.CHALLENGE -> 9 + map / 3
            LevelKind.TUTORIAL -> 6
            LevelKind.NORMAL -> if (levelId <= 10) 5 + (levelId - 1) / 3 else 6 + map / 3 + rng.nextInt(3)
        }
    }

    /** Eğitim bölümleri (1-10) için elle seçilmiş desen dizileri. */
    private fun tutorialPatterns(levelId: Int): List<Pattern>? = when (levelId) {
        1 -> listOf(Pattern.GAP, Pattern.GAP, Pattern.GAP)
        2 -> listOf(Pattern.STEP, Pattern.GAP, Pattern.STEP)
        3 -> listOf(Pattern.SPIKES, Pattern.GAP, Pattern.SPIKES)
        4 -> listOf(Pattern.FLOATING, Pattern.SPIKES, Pattern.FLOATING)
        5 -> listOf(Pattern.SAW, Pattern.GAP, Pattern.SAW)
        6 -> listOf(Pattern.MOVING_PLATFORM, Pattern.SPIKES, Pattern.MOVING_PLATFORM)
        7 -> listOf(Pattern.SPRING_WALL, Pattern.GAP, Pattern.SPRING_WALL)
        8 -> listOf(Pattern.BOOST_GAP, Pattern.SPIKES, Pattern.BOOST_GAP)
        9 -> listOf(Pattern.OVERHEAD, Pattern.CEILING_GAP, Pattern.OVERHEAD)
        else -> null
    }

    private val generalPatterns = listOf(
        Pattern.GAP, Pattern.SPIKES, Pattern.STEP, Pattern.FLOATING, Pattern.SAW, Pattern.MOVING_SAW,
        Pattern.MOVING_PLATFORM, Pattern.SPRING_WALL, Pattern.BOOST_GAP, Pattern.OVERHEAD, Pattern.CEILING_GAP,
        Pattern.STAIRS, Pattern.SPIKE_GAP,
    )

    /** Eğitim aşamasında henüz tanıtılmamış desenler kullanılmaz. */
    private fun patternUnlocked(p: Pattern, levelId: Int): Boolean = when (p) {
        Pattern.GAP, Pattern.STEP -> true
        Pattern.SPIKES, Pattern.SPIKE_GAP -> levelId >= 3
        Pattern.FLOATING -> levelId >= 4
        Pattern.SAW -> levelId >= 5
        Pattern.MOVING_PLATFORM -> levelId >= 6
        Pattern.SPRING_WALL -> levelId >= 7
        Pattern.BOOST_GAP -> levelId >= 8
        Pattern.OVERHEAD, Pattern.CEILING_GAP -> levelId >= 9
        Pattern.MOVING_SAW -> levelId >= 14
        Pattern.STAIRS -> levelId >= 16
        else -> true
    }

    /** Tavandan zemine (ya da tersi) süzülürken kat edilen yatay yol: uçuş süresi dünya zamanında sabittir (~0,67 sn). */
    private fun flipFlight(e: RuleEffects, speedMul: Float): Float =
        0.673f * Physics.RUN_SPEED * speedMul * (if (e.ice) Physics.ICE_SPEED_MUL else 1f) + Physics.PLAYER_W

    /**
     * Kural çizgisinden SONRAKİ düz tampon (karo): oyuncunun yeni kuralı fark edip tepki vermesi için süre
     * (zorlukla kısalır, mekaniğin tanıtıldığı bölümde uzundur) + yer çekimi dönüyorsa karşı yüzeye iniş mesafesi.
     */
    fun bufferIn(prev: RuleEffects, next: RuleEffects, speedNext: Float, t: Float, intro: Boolean = false, speedMul: Float = 1f): Float {
        var react = if (intro) 1.5f else (1.05f - 0.4f * t).coerceAtLeast(0.6f)
        if (prev.sizeScale != next.sizeScale || prev.reverseControl != next.reverseControl) react += 0.25f
        val base = react * speedNext
        return if (prev.gravitySign != next.gravitySign) flipFlight(next, speedMul) + base else base
    }

    /** Kural çizgisinden ÖNCEKİ düz tampon: son engelin üstünden atlayan oyuncu çizgiden önce yere insin. */
    fun bufferOut(prev: RuleEffects, speedPrev: Float, speedMul: Float = 1f): Float {
        val reach = Physics.RUN_SPEED * speedMul * (if (prev.ice) Physics.ICE_SPEED_MUL else 1f) * 2f * Physics.JUMP_V * prev.jumpMul / Physics.GRAVITY
        return max(0.5f * speedPrev, 0.6f * reach)
    }

    /**
     * Bir aşamanın engel bölgesini [zx0, zx1] desenlerle doldurur. Kurala özgü desenler önce denenir; sığmayan desen
     * aşamayı boş bırakmaz (sıradaki denenir), bölgeden taşan desen geri alınır.
     */
    fun fillPhase(b: LevelBuilder, ctx: PatternCtx, zx0: Float, zx1: Float, dedicated: List<Pattern>, rest: () -> Float, coinChance: Float,
                  pick: (avail: Float, tried: Set<Pattern>) -> Pattern?) {
        val sign = ctx.sign
        var cx = zx0
        val queue = ArrayList(dedicated)
        val tried = HashSet<Pattern>()
        var guard = 0
        var first = true
        while (cx < zx1 - 4f && guard++ < 18) {
            // aşamanın ilk deseninden önce ayrıca dinlenme boşluğu gerekmez: giriş tamponu zaten düzdür
            val r = if (first) 0.5f else rest()
            val avail = zx1 - (cx + r)
            var p: Pattern? = null
            while (queue.isNotEmpty() && p == null) { val q = queue.removeAt(0); if (Patterns.minLength(q) <= avail && q !in tried) p = q }
            if (p == null) p = pick(avail, tried)
            if (p == null) break
            val mark = b.mark()
            b.ground(sign, cx, cx + r)
            if (r > 2.5f && ctx.chance(coinChance)) b.coinLine(sign, cx + 0.5f, cx + r - 0.5f, Physics.PLAYER_H * ctx.e.sizeScale / 2f)
            val end = Patterns.place(p, ctx, cx + r, zx1)
            if (end > zx1 + 0.01f) { b.rollback(mark); tried.add(p); continue }
            tried.clear()
            first = false
            cx = end
        }
        b.ground(sign, cx, zx1)
    }

    fun generate(levelId: Int, attempt: Int, tShift: Float = 0f): LevelDraft {
        val seed = levelId * 7919L + attempt * 104729L
        val rng = Random(seed)
        val kind = GameContent.kindOf(levelId)
        val map = GameContent.mapOf(levelId)
        val stage = GameContent.stageOf(levelId)
        val target = GameContent.targetDifficulty(levelId)
        // Desen yoğunluğu parametresi: hedef zorluktan türetilir; dökümhane ölçülen zorluğa göre kaydırır (tShift).
        val t = ((target - 1f) / 8.5f + tShift).coerceIn(0f, 1.6f)
        val diff = GameContent.difficulty(levelId)
        val count = phaseCount(levelId, rng)
        val plan = PhasePlanner.plan(levelId, count, rng)
        val effects = plan.map { RuleEffects.of(it) }
        val b = LevelBuilder()
        val intro = kind == LevelKind.TUTORIAL

        // Aşama sınırları
        val bounds = FloatArray(count + 1)
        var x = 0f
        for (i in 0 until count) {
            bounds[i] = x
            val len = Physics.RULE_PERIOD * Physics.realSpeed(effects[i]) * (if (i == 0) 1.15f else 1f)
            x += len
        }
        bounds[count] = x
        // Bitiş düzlüğü: son kuraldan sonra dünya her zaman Normal'e döner; karakter kapıya yerde, normal boyda varır.
        val goalX = x + Physics.FINISH_LEN

        // Tavan gereken aralıklar
        val needCeil = BooleanArray(count) { effects[it].gravitySign < 0 }
        fun ceilAt(i: Int) = needCeil[i] || (i > 0 && needCeil[i - 1]) || (i + 1 < count && needCeil[i + 1])

        // Başlangıç ve bitiş zemini (geniş ekranlarda dünyanın kenarı görünmesin)
        b.ground(1, -14f, 0f)
        b.ground(1, x, goalX + 30f)
        if (ceilAt(0)) b.ground(-1, -14f, 0f)
        if (needCeil[count - 1]) b.ground(-1, x, goalX + 30f)

        val tutorial = tutorialPatterns(levelId)
        var tutorialIdx = 0
        val phaseRanges = ArrayList<Pair<Float, Float>>()
        for (i in 0 until count) {
            val e = effects[i]
            val sp = Physics.realSpeed(e)
            val x0 = bounds[i]
            val x1 = bounds[i + 1]
            val bufIn = if (i == 0) (11f - 1.5f * t).coerceAtLeast(9f) else bufferIn(effects[i - 1], e, sp, t, intro)
            val bufOut = bufferOut(e, sp)
            val zx0 = x0 + bufIn
            val zx1 = x1 - bufOut
            phaseRanges.add(x0 to x1)
            val sign = e.gravitySign
            // Diğer yüzey
            if (sign > 0) { if (ceilAt(i)) b.ground(-1, x0, x1) } else b.ground(1, x0, x1)
            // Aktif yüzey: tampon bölgeler düz
            b.ground(sign, x0, zx0)
            b.ground(sign, zx1, x1)
            val ctx = PatternCtx(b, rng, e, t)
            if (zx1 - zx0 < 4f) { b.ground(sign, zx0, zx1); continue }
            if (tutorial != null) {
                val queue = ArrayList<Pattern>()
                if (i >= 1 || levelId > 1) queue.add(tutorial[tutorialIdx++ % tutorial.size])
                if (i >= 2) queue.add(tutorial[tutorialIdx++ % tutorial.size])
                fillPhase(b, ctx, zx0, zx1, queue, { 3f }, 0.5f) { _, _ -> null }
            } else {
                fillPhase(b, ctx, zx0, zx1, Patterns.dedicated(e), { ctx.lerp(3.2f, 1.3f, 1.0f) * ctx.rnd(0.8f, 1.3f) }, 0.5f) { avail, tried ->
                    pick(ctx, stage, levelId, avail, kind, tried)
                }
            }
        }
        b.finalizeGround()

        // Kontrol noktaları: düzenli aralıklarla, kural çizgisinden hemen sonraki düz tamponda; son kontrol noktasından
        // kapıya en fazla iki kural aşaması kalır (kapının önünde ölmek oyuncuyu çok geriye atmasın).
        val cps = ArrayList<Float>()
        val cpEvery = when (kind) { LevelKind.CHALLENGE -> 4; LevelKind.FINAL, LevelKind.GRAND_FINAL -> 3; else -> 2 }
        var i = cpEvery
        var lastCp = 0
        while (i < count) { cps.add(r2(bounds[i] + 1.2f)); lastCp = i; i += cpEvery }
        if (count - lastCp > 2) cps.add(r2(bounds[count - 2] + 1.2f))

        val planned = (0 until count).map { PhaseDef(r2(bounds[it]), plan[it]) }
        val normal = listOf(Mechanic.NORMAL)
        val phases = if (plan.last() == normal) planned else planned + PhaseDef(r2(x), normal)
        val hints = buildHints(levelId, b.features, phases)
        val mechanics = plan.flatten().filter { it != Mechanic.NORMAL }.distinct()
        val tutorialMech = if (kind == LevelKind.TUTORIAL && levelId > 1) Mechanic.entries.firstOrNull { it.unlockLevel == levelId } else null
        val data = LevelData(
            id = levelId, map = map.id, indexInMap = (levelId - 1) % 50 + 1,
            name = GameContent.levelName(levelId, 0), kind = kind, difficulty = diff,
            idea = stage.title, start = Vec(2f, Physics.PLAYER_H / 2f + 0.01f), goalX = r2(goalX),
            solids = b.solids, hazards = b.hazards, pads = b.pads,
            checkpoints = cps, phases = phases, mechanics = mechanics, tutorial = tutorialMech,
            hints = hints, chaser = kind == LevelKind.FINAL || kind == LevelKind.GRAND_FINAL, seed = seed,
            features = b.features.sortedBy { it.x }.map { FeatureMark(it.pattern, r2(it.x)) },
        )
        return LevelDraft(data, b.features.toList(), b.coinHints.toList(), phaseRanges)
    }

    private fun pick(c: PatternCtx, stage: StageIdea, levelId: Int, avail: Float, kind: LevelKind, tried: Set<Pattern> = emptySet()): Pattern? {
        val candidates = ArrayList<Pair<Pattern, Float>>()
        for (p in generalPatterns + stage.patterns) {
            if (p in tried || !Patterns.applicable(p, c.e) || !patternUnlocked(p, levelId)) continue
            if (Patterns.minLength(p) > avail) continue
            // hız pedinden sonra kural çizgisine kadar hızın sönmesi için yer kalmalı
            if (p == Pattern.BOOST_GAP && avail < 15f) continue
            var w = 1f
            if (p in stage.patterns) w += 2.5f
            // Yüksek zorlukta hassasiyet isteyen desenler ağırlık kazanır, kolay desenler azalır
            if (c.t > 0.6f) {
                val k = (c.t - 0.6f).coerceAtMost(0.8f)
                when (p) {
                    Pattern.SPIKES, Pattern.SAW, Pattern.SPIKE_GAP, Pattern.CEILING_GAP, Pattern.FLOATING -> w *= 1f + 2.2f * k
                    Pattern.GAP, Pattern.STEP, Pattern.BOOST_GAP, Pattern.MOVING_SAW -> w *= 1f - 0.8f * k
                    else -> {}
                }
            }
            if (c.e.reverseControl && (p == Pattern.FLOATING || p == Pattern.STAIRS || p == Pattern.STEP)) w *= 0.3f
            if (c.e.sizeScale < 1f && (p == Pattern.SPRING_WALL || p == Pattern.STAIRS)) w *= 0.3f
            candidates.add(p to w)
        }
        if (candidates.isEmpty()) return null
        val total = candidates.sumOf { it.second.toDouble() }.toFloat()
        var r = c.rng.nextFloat() * total
        for ((p, w) in candidates) { r -= w; if (r <= 0f) return p }
        return candidates.last().first
    }

    private fun buildHints(levelId: Int, features: List<Feature>, phases: List<PhaseDef>): List<HintDef> {
        val out = ArrayList<HintDef>()
        fun first(p: Pattern) = features.firstOrNull { it.pattern == p }
        when (levelId) {
            1 -> {
                out.add(HintDef(3f, "Karakterin kendiliğinden koşar.", icon = "i_run"))
                first(Pattern.GAP)?.let { out.add(HintDef(it.x + 0.3f, "Şimdi ekrana dokun ve zıpla!", waitTap = true, icon = "i_tap")) }
            }
            2 -> first(Pattern.STEP)?.let { out.add(HintDef(it.x - 1.5f, "Basılı tutarsan daha yükseğe zıplarsın!", icon = "i_hold")) }
            3 -> first(Pattern.SPIKES)?.let { out.add(HintDef(it.x - 1.5f, "Dikenlere değme, üstünden atla!", icon = "i_spike")) }
            4 -> first(Pattern.FLOATING)?.let { out.add(HintDef(it.x - 1.5f, "Platformdan platforma zıpla!", icon = "i_tap")) }
            5 -> first(Pattern.SAW)?.let { out.add(HintDef(it.x - 1.5f, "Testerelerin üzerinden atla!", icon = "i_saw")) }
            6 -> first(Pattern.MOVING_PLATFORM)?.let { out.add(HintDef(it.x - 1.5f, "Hareketli platforma zamanında zıpla!", icon = "i_tap")) }
            7 -> first(Pattern.SPRING_WALL)?.let { out.add(HintDef(it.x - 1.5f, "Yaylar seni yükseğe fırlatır!", icon = "i_spring")) }
            8 -> first(Pattern.BOOST_GAP)?.let { out.add(HintDef(it.x - 1.5f, "Hız okları seni hızlandırır!", icon = "i_boost")) }
            9 -> {
                first(Pattern.OVERHEAD)?.let { out.add(HintDef(it.x - 1.5f, "Üstünde diken varken zıplama!", icon = "i_spike")) }
                first(Pattern.CEILING_GAP)?.let { out.add(HintDef(it.x - 1.5f, "Kısa dokun: alçak zıpla!", icon = "i_tap")) }
            }
            10 -> out.add(HintDef(3f, "Meydan okuma! Öğrendiklerini göster.", icon = "i_star"))
        }
        val m = Mechanic.entries.firstOrNull { it.unlockLevel == levelId && levelId > 1 }
        if (m != null) {
            val ph = phases.firstOrNull { m in it.rules }
            if (ph != null) {
                out.add(HintDef(ph.x - 4f, "Dikkat! ${m.title} geliyor.", icon = m.icon))
                val text = when (m) {
                    Mechanic.REVERSE_GRAVITY -> "Tavanda da dokununca zıplarsın!"
                    Mechanic.FAST -> "Her şey hızlı! Biraz erken zıpla."
                    Mechanic.ICE -> "Buzda yavaş yavaş hızlanırsın."
                    Mechanic.GHOST -> "Mor platformlar yanıp sönünce kaybolur!"
                    Mechanic.BOUNCY -> "Şimdi çok daha yükseğe zıplıyorsun!"
                    Mechanic.SMALL -> "Küçüldün! Dar geçitlerden geçebilirsin."
                    Mechanic.REVERSE_CONTROL -> "Kendin zıplıyorsun! Dikenlerin altında basılı tut."
                    Mechanic.SLOW -> "Zaman yavaşladı, acele etme."
                    Mechanic.SHIFTING -> "Oklu platformlar yukarı aşağı kayar."
                    Mechanic.NORMAL -> ""
                }
                out.add(HintDef(ph.x + 1.5f, text, waitTap = m == Mechanic.REVERSE_CONTROL, icon = m.icon))
            }
        }
        return out.sortedBy { it.x }
    }
}
