package com.zamaninefendisi.logic.continuity

import com.zamaninefendisi.logic.Action
import com.zamaninefendisi.logic.BuildKind
import com.zamaninefendisi.logic.EnemySpawn
import com.zamaninefendisi.logic.Era
import com.zamaninefendisi.logic.Feature
import com.zamaninefendisi.logic.Goal
import com.zamaninefendisi.logic.LevelDef
import com.zamaninefendisi.logic.LevelSession
import com.zamaninefendisi.logic.Pos
import com.zamaninefendisi.logic.SessionStatus
import kotlin.random.Random

/** Üretilen bölümün ek kuralları (Sonsuz mod anomalileri, Haftalık Kriz kuralları). */
data class GenRules(
    val energyLimited: Boolean = false,
    val desertSpeed: Int = 1,
    val extraEnemies: Int = 0,
    val slack: Int = 6,
    val futureGoal: Boolean = false,
)

/**
 * Prosedürel bölüm üretici. Her şablon bir harita kurar, çözümü oyunun kendi simülasyonu üzerinde adım adım
 * sentezler ve sonucu doğrular: yalnızca gerçekten kazanılabilen bölümler döner. Aynı tohum her zaman aynı bölümü verir.
 */
object LevelGenerator {
    enum class Template(val title: String) { BRIDGE("Kırık Geçit"), DESERT("Kum Fırtınası"), VILLAGE("Kayıp Köy"), DEFENSE("Akıncı Dalgası"), FOREST("Kadim Engel") }

    class Generated(val level: LevelDef, val template: Template)

    fun generate(id: Int, seed: Long, rules: GenRules = GenRules(), name: String? = null, only: Template? = null): Generated {
        for (attempt in 0 until 80) {
            val rnd = Random(seed * 1_000_003 + attempt)
            val t = only ?: Template.entries[rnd.nextInt(Template.entries.size)]
            val g = runCatching { build(t, id, rnd, rules, name) }.getOrNull()
            if (g != null) return g
        }
        return build(Template.BRIDGE, id, Random(seed), rules.copy(energyLimited = false), name) ?: error("Üretici başarısız")
    }

    // ------------------------------------------------------------------ Ortak yardımcılar

    private class Draft(val w: Int, val h: Int) {
        val g = Array(h) { CharArray(w) { 'G' } }
        operator fun set(p: Pos, c: Char) { g[p.y][p.x] = c }
        operator fun get(p: Pos): Char = g[p.y][p.x]
        fun rows(): List<String> = g.map { String(it) }
        fun free(rnd: Random, filter: (Pos) -> Boolean): Pos? {
            val cand = (0 until h).flatMap { y -> (0 until w).map { x -> Pos(x, y) } }.filter { this[it] == 'G' && filter(it) }
            return if (cand.isEmpty()) null else cand[rnd.nextInt(cand.size)]
        }
    }

    private fun def(
        id: Int, name: String, intro: String, d: Draft, eras: List<Era>, start: Era, allowed: Map<Era, Set<BuildKind>>, goals: List<Goal>,
        solution: List<Action>, par: Int, maxTurns: Int, wood: Int = 0, desert: Int = 0, energy: Int? = null,
        enemies: List<EnemySpawn> = emptyList(), protect: List<Pos> = emptyList(),
    ) = LevelDef(
        id = id, area = 0, name = name, intro = intro, map = d.rows(), eras = eras, startEra = start, startWood = wood, allowed = allowed,
        goals = goals, maxTurns = maxTurns, par = par, desertSpeed = desert, enemies = enemies, protect = protect, energy = energy, solution = solution,
    )

    /** Çözümü simülasyon üzerinde kurar: yürüme yolları oyunun kendi yol bulucusuyla hesaplanır. */
    private class Synth(level: LevelDef) {
        val s = LevelSession(level)
        val actions = ArrayList<Action>()
        fun act(a: Action): Boolean { if (s.validate(a) != null) return false; s.perform(a); actions += a; return true }
        fun walkTo(p: Pos): Boolean { val path = s.pathTo(p) ?: return false; for (q in path) if (!act(Action.Move(q))) return false; return true }
        /** Hedef hücrenin komşusuna (ya da kendisine, [allowSelf]) yürür. */
        fun walkNear(target: Pos, allowSelf: Boolean = false): Boolean {
            if (s.heroPos.manhattan(target) <= (if (allowSelf) 1 else 1) && (allowSelf || s.heroPos != target)) return true
            val opts = target.neighbors4().filter { s.inBounds(it) && s.canWalk(s.heroEra, it) }
                .mapNotNull { n -> s.pathTo(n)?.let { n to it.size } }.minByOrNull { it.second } ?: return false
            return walkTo(opts.first)
        }
    }

    private fun finish(id: Int, name: String, intro: String, t: Template, base: LevelDef, actions: List<Action>, slack: Int, energyLimited: Boolean): Generated? {
        // Enerji anomalisi: çözümdeki geçiş sayısı kadar enerji.
        val switches = actions.count { it is Action.SwitchEra }
        val energy = if (energyLimited) switches else null
        val probe = LevelSession.replay(base, actions)
        if (probe.status != SessionStatus.WON) return null
        val par = probe.actionsUsed
        val survive = base.goals.any { it is Goal.Survive }
        val lv = LevelDef(
            id = id, area = 0, name = name, intro = intro, map = base.map, eras = base.eras, startEra = base.startEra, startWood = base.startWood,
            allowed = base.allowed, goals = base.goals, maxTurns = if (survive) base.maxTurns else actions.size + slack,
            par = par, desertSpeed = base.desertSpeed, overrides = base.overrides, links = base.links, enemies = base.enemies,
            enemyEra = base.enemyEra, spawnEvery = base.spawnEvery, protect = base.protect, energy = energy, solution = actions,
        )
        val check = LevelSession.replay(lv, actions)
        if (check.status != SessionStatus.WON || !check.timeline.matchesFullRebuild()) return null
        return Generated(lv, t)
    }

    private fun build(t: Template, id: Int, rnd: Random, rules: GenRules, name: String?): Generated? = when (t) {
        Template.BRIDGE -> bridge(id, rnd, rules, name ?: t.title)
        Template.DESERT -> desert(id, rnd, rules, name ?: t.title)
        Template.VILLAGE -> village(id, rnd, rules, name ?: t.title)
        Template.DEFENSE -> defense(id, rnd, rules, name ?: t.title)
        Template.FOREST -> forest(id, rnd, rules, name ?: t.title)
    }

    // ------------------------------------------------------------------ Şablonlar

    private fun bridge(id: Int, rnd: Random, rules: GenRules, name: String): Generated? {
        val w = 7; val h = 7 + rnd.nextInt(2)
        val d = Draft(w, h)
        val rx = 2 + rnd.nextInt(3)
        for (y in 0 until h) d[Pos(rx, y)] = 'W'
        repeat(rnd.nextInt(4)) { d.free(rnd) { true }?.let { d[it] = 'M' } }
        val hero = d.free(rnd) { it.x < rx } ?: return null
        d[hero] = '@'
        val castle = d.free(rnd) { it.x > rx } ?: return null
        d[castle] = 'C'
        val trees = (0 until 1 + rnd.nextInt(2)).mapNotNull { d.free(rnd) { it.x < rx }?.also { p -> d[p] = 'T' } }
        if (trees.isEmpty()) return null
        val eras = if (rules.futureGoal || rnd.nextBoolean()) Era.entries.toList() else listOf(Era.PAST, Era.PRESENT)
        val goalEra = eras.last()
        val base = def(
            id, name, "Nehir zaman çizgisini ikiye bölmüş. Geçmiş'te kurulan köprü ileri çağlarda güçlenerek yaşar.", d, eras, goalEra,
            mapOf(Era.PAST to setOf(BuildKind.CUT, BuildKind.BRIDGE)) + eras.drop(1).associateWith { setOf(BuildKind.CUT) },
            listOf(Goal.Reach(goalEra, castle, "${goalEra.locative} Zaman Kalesi'ne ulaş")), emptyList(), 1, 99,
        )
        val sy = Synth(base)
        if (!sy.act(Action.SwitchEra(Era.PAST))) return null
        val tree = trees.minByOrNull { it.manhattan(hero) }!!
        if (!sy.walkNear(tree)) return null
        if (!sy.act(Action.Build(BuildKind.CUT, tree))) return null
        val bank = (0 until h).map { Pos(rx - 1, it) }.filter { sy.s.canWalk(Era.PAST, it) && sy.s.cell(Era.PAST, Pos(rx + 1, it.y)).walkable }
            .minByOrNull { sy.s.pathTo(it)?.size ?: 999 } ?: return null
        if (!sy.walkTo(bank)) return null
        if (!sy.act(Action.Build(BuildKind.BRIDGE, Pos(rx, bank.y)))) return null
        if (!sy.act(Action.SwitchEra(goalEra))) return null
        if (!sy.walkTo(castle)) return null
        return finish(id, name, base.intro, Template.BRIDGE, base, sy.actions, rules.slack, rules.energyLimited)
    }

    private fun desert(id: Int, rnd: Random, rules: GenRules, name: String): Generated? {
        val w = 7; val h = 8
        val d = Draft(w, h)
        val speed = rules.desertSpeed.coerceIn(1, 2)
        for (y in 0 until h) d[Pos(0, y)] = 'S'
        val fx = speed
        val rows = (0 until h).shuffled(rnd).take(2 + rnd.nextInt(2)).sorted()
        if (rows.zipWithNext().any { (a, b) -> b - a < 2 }) return null
        val fields = rows.map { Pos(fx, it) }
        fields.forEach { d[it] = 'F' }
        val hero = d.free(rnd) { it.x >= 4 } ?: return null
        d[hero] = '@'
        val eras = listOf(Era.PAST, Era.PRESENT)
        val base = def(
            id, name, "Çöl her çağda ilerliyor. Geçmiş'te dikilen fidanlar tarlaları korur.", d, eras, Era.PRESENT,
            mapOf(Era.PAST to setOf(BuildKind.PLANT), Era.PRESENT to setOf(BuildKind.PLANT)),
            listOf(Goal.FeatureAt(Era.PRESENT, fields, Feature.FIELD, "Şimdi'de bütün tarlaları koru")), emptyList(), 1, 99, desert = speed,
        )
        val sy = Synth(base)
        if (!sy.act(Action.SwitchEra(Era.PAST))) return null
        for (f in fields) {
            if (f in sy.s.level.goals.flatMap { (it as Goal.FeatureAt).cells }.filter { sy.s.cell(Era.PRESENT, it).feature == Feature.FIELD }) continue
            val spot = Pos(f.x + 1, f.y)
            if (!sy.walkNear(spot, allowSelf = true)) return null
            if (sy.s.heroPos == spot) { if (!sy.act(Action.Build(BuildKind.PLANT, spot))) return null }
            else if (!sy.act(Action.Build(BuildKind.PLANT, spot))) return null
        }
        return finish(id, name, base.intro, Template.DESERT, base, sy.actions, rules.slack, rules.energyLimited)
    }

    private fun village(id: Int, rnd: Random, rules: GenRules, name: String): Generated? {
        val w = 7; val h = 7
        val d = Draft(w, h)
        val rx = 4 + rnd.nextInt(2)
        for (y in 0 until h) d[Pos(rx, y)] = 'W'
        val hero = d.free(rnd) { it.x < rx - 1 } ?: return null
        d[hero] = '@'
        repeat(3) { d.free(rnd) { it.x < rx - 1 && it.manhattan(hero) > 1 }?.let { d[it] = 'T' } }
        val eras = if (rules.futureGoal) Era.entries.toList() else listOf(Era.PAST, Era.PRESENT)
        val goalEra = eras.last()
        val feat = if (goalEra == Era.FUTURE) Feature.CITY else Feature.TOWN
        val base = def(
            id, name, "Nehir kıyısına Geçmiş'te kurulan köy ileri çağlarda büyür. Köy 3 odun ister.", d, eras, Era.PRESENT,
            mapOf(Era.PAST to setOf(BuildKind.CUT, BuildKind.VILLAGE)) + eras.drop(1).associateWith { setOf(BuildKind.CUT) },
            listOf(Goal.FeatureCount(goalEra, feat, 1, "${goalEra.locative} bir ${feat.title.lowercase()} yükselsin")), emptyList(), 1, 99, wood = 1,
        )
        val sy = Synth(base)
        if (!sy.act(Action.SwitchEra(Era.PAST))) return null
        val tree = (0 until h).flatMap { y -> (0 until w).map { Pos(it, y) } }.filter { sy.s.cell(Era.PAST, it).feature == Feature.TREE }
            .minByOrNull { it.manhattan(sy.s.heroPos) } ?: return null
        if (!sy.walkNear(tree)) return null
        if (!sy.act(Action.Build(BuildKind.CUT, tree))) return null
        val site = (0 until h).map { Pos(rx - 1, it) }.filter { sy.s.cell(Era.PAST, it).feature == Feature.NONE && sy.s.cell(Era.PAST, it).terrain.walkable }
            .minByOrNull { it.manhattan(sy.s.heroPos) } ?: return null
        if (!sy.walkNear(site)) return null
        if (!sy.act(Action.Build(BuildKind.VILLAGE, site))) return null
        return finish(id, name, base.intro, Template.VILLAGE, base, sy.actions, rules.slack, rules.energyLimited)
    }

    private fun defense(id: Int, rnd: Random, rules: GenRules, name: String): Generated? {
        val w = 7; val h = 9
        val d = Draft(w, h)
        val gapRow = 4
        val gapStart = 1 + rnd.nextInt(3)
        val gapW = 2
        for (x in 0 until w) if (x !in gapStart until gapStart + gapW) d[Pos(x, gapRow)] = 'M'
        val castle = Pos(3, h - 1); d[castle] = 'C'
        val hero = Pos(gapStart, gapRow + 1); d[hero] = '@'
        val enemies = (0 until 2 + rules.extraEnemies).map { EnemySpawn(Pos((it * 3 + rnd.nextInt(2)) % w, 0)) }.distinct()
        val turns = 10 + rnd.nextInt(3)
        val base = def(
            id, name, "Akıncılar geçide yürüyor. Geçmiş'te kurulan çit Şimdi'de yıkılmaz bir taş surdur.", d, listOf(Era.PAST, Era.PRESENT), Era.PRESENT,
            mapOf(Era.PAST to setOf(BuildKind.WALL), Era.PRESENT to setOf(BuildKind.WALL)),
            listOf(Goal.Survive(turns, "Zaman Kalesi'ni $turns tur koru")), emptyList(), 1, turns, wood = gapW,
            enemies = enemies, protect = listOf(castle),
        )
        val sy = Synth(base)
        if (!sy.act(Action.SwitchEra(Era.PAST))) return null
        for (x in gapStart until gapStart + gapW) {
            val cell = Pos(x, gapRow)
            val stand = Pos(x, gapRow + 1)
            if (sy.s.heroPos != stand && !sy.walkTo(stand)) return null
            if (!sy.act(Action.Build(BuildKind.WALL, cell))) return null
        }
        while (sy.s.status == SessionStatus.PLAYING && sy.actions.size < turns + 2) if (!sy.act(Action.Wait)) break
        return finish(id, name, base.intro, Template.DEFENSE, base, sy.actions, rules.slack, false)
    }

    private fun forest(id: Int, rnd: Random, rules: GenRules, name: String): Generated? {
        val w = 7; val h = 7
        val d = Draft(w, h)
        val row = 2 + rnd.nextInt(2)
        for (x in 0 until w) d[Pos(x, row)] = if (x == 0 || x == w - 1) 'M' else 'T'
        for (x in 0 until w) if (x == 0 || x == w - 1) d[Pos(x, row - 1)] = 'M'
        val castle = Pos(1 + rnd.nextInt(w - 2), 0); d[castle] = 'C'
        val hero = Pos(1 + rnd.nextInt(w - 2), h - 1); d[hero] = '@'
        val eras = Era.entries.toList()
        // Şimdi'de kuzey bölge sular altında: yol yalnızca Gelecek'te açıktır.
        val present = (0 until h).map { y -> String(CharArray(w) { x -> if (y == row - 1 && x in 1 until w - 1) 'W' else '.' }) }
        val future = (0 until h).map { y -> String(CharArray(w) { x -> if (y == row - 1 && x in 1 until w - 1) 'G' else '.' }) }
        val lv0 = LevelDef(
            id = id, area = 0, name = name, intro = "Ağaç sırası Gelecek'te geçilmez bir kadim ormana dönüşecek. Doğru ağacı kesersen yol açık kalır.",
            map = d.rows(), eras = eras, startEra = Era.PRESENT, allowed = mapOf(Era.PAST to setOf(BuildKind.CUT), Era.PRESENT to setOf(BuildKind.CUT), Era.FUTURE to emptySet()),
            goals = listOf(Goal.Reach(Era.FUTURE, castle, "Gelecek'te Zaman Kalesi'ne ulaş")), maxTurns = 99, par = 1,
            overrides = mapOf(Era.PRESENT to present, Era.FUTURE to future), solution = emptyList(),
        )
        val sy = Synth(lv0)
        val target = Pos(castle.x.coerceIn(2, w - 3), row)
        if (!sy.walkNear(target)) return null
        if (!sy.act(Action.Build(BuildKind.CUT, target))) return null
        if (!sy.act(Action.SwitchEra(Era.FUTURE))) return null
        if (!sy.walkTo(castle)) return null
        return finish(id, name, lv0.intro, Template.FOREST, lv0, sy.actions, rules.slack, rules.energyLimited)
    }
}
