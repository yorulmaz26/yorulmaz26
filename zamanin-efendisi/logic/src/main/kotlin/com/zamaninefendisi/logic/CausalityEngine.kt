package com.zamaninefendisi.logic

import java.util.EnumMap
import java.util.PriorityQueue

internal data class EraPos(val era: Era, val pos: Pos)

/**
 * Nedensellik motoru: her çağ için bağımsız ama birbirine bağlı dünya durumları tutar.
 *
 * - Geçmiş = bölüm haritası + Geçmiş değişiklikleri
 * - Şimdi  = evrim(Geçmiş) + tarihî olaylar + bağlantılar + Şimdi değişiklikleri + hasar
 * - Gelecek = evrim(Şimdi) + ...
 *
 * Bir değişiklik yapıldığında yalnızca etkilenen hücreler yeniden hesaplanır: değişen hücrenin
 * [EvolutionRules.dependencyRadius] yarıçapındaki sonraki çağ hücreleri, gerçekten değişenler
 * üzerinden zincirleme ilerler. Gelecek'teki değişiklikler geçmişi etkilemez.
 */
class CausalityEngine(val level: LevelDef) {
    val eras: List<Era> = level.eras.sorted()
    private val map = level.parsed
    private val rules = EvolutionRules(level.desertSpeed)
    private val grids = EnumMap<Era, EraGrid>(Era::class.java).apply {
        eras.forEach { put(it, EraGrid(it, map.width, map.height)) }
    }

    private val _changes = ArrayList<ChangeRecord>()
    val changes: List<ChangeRecord> get() = _changes
    private val changesAt = HashMap<EraPos, MutableList<ChangeRecord>>()

    /** Saldırıların yapılara verdiği hasar (yalnızca düşman çağında). */
    private val damage = HashMap<Pos, Int>()
    /** Yağmalanan/sabote edilen hücreler (yalnızca düşman çağında; sonraki çağlara evrilerek yansır). */
    private val razed = HashSet<Pos>()

    private val linksByTarget: Map<EraPos, List<CausalLink>> = level.links.groupBy { EraPos(it.targetEra, it.target) }
    private val linksBySource: Map<EraPos, List<CausalLink>> = level.links.groupBy { EraPos(it.sourceEra, it.source) }
    private val rank: Map<EraPos, Int>

    /** Toplam yeniden hesaplanan hücre sayısı (performans ölçümü ve testler için). */
    var recomputedCells: Long = 0
        private set

    init {
        val report = CausalGraph.analyze(level)
        require(report.errors.isEmpty()) { "Bölüm ${level.id} nedensellik hatası: ${report.errors.joinToString()}" }
        rank = report.rank
        rebuildAll()
    }

    fun has(era: Era): Boolean = era in grids
    fun grid(era: Era): EraGrid = grids[era] ?: error("${era.title} bu bölümde yok")

    fun changesAt(era: Era, p: Pos): List<ChangeRecord> = changesAt[EraPos(era, p)].orEmpty()

    /** Yeni oyuncu değişikliği ekler ve etkilerini ileri çağlara yayar. */
    fun addChange(era: Era, pos: Pos, kind: BuildKind, turn: Int): Pair<ChangeRecord, RecomputeResult> {
        val rec = ChangeRecord(_changes.size, era, pos, kind, turn)
        _changes += rec
        changesAt.getOrPut(EraPos(era, pos)) { ArrayList() } += rec
        if (era == level.enemyEra) { damage.remove(pos); razed.remove(pos) }
        return rec to recompute(era, setOf(pos))
    }

    /** Düşman saldırısı: düşman çağındaki duvara hasar verir. */
    fun addDamage(pos: Pos, amount: Int): RecomputeResult {
        damage[pos] = (damage[pos] ?: 0) + amount
        return recompute(level.enemyEra, setOf(pos))
    }

    /** Düşman yağması/sabotajı: hücredeki yapı yıkılır. */
    fun addRaze(pos: Pos): RecomputeResult {
        razed += pos
        return recompute(level.enemyEra, setOf(pos))
    }

    fun damageAt(pos: Pos): Int = damage[pos] ?: 0

    class RecomputeResult(val changed: Map<Era, Set<Pos>>, val voided: List<ChangeRecord>) {
        val any: Boolean get() = changed.values.any { it.isNotEmpty() }
    }

    fun recompute(startEra: Era, cells: Set<Pos>): RecomputeResult {
        val changed = EnumMap<Era, MutableSet<Pos>>(Era::class.java)
        val voided = ArrayList<ChangeRecord>()
        val pendingLinks = EnumMap<Era, MutableSet<Pos>>(Era::class.java)
        var dirty: Set<Pos> = cells
        for (era in eras) {
            if (era < startEra) continue
            val grid = grid(era)
            val work = HashSet<Pos>(dirty)
            pendingLinks[era]?.let { work += it }
            if (work.isEmpty()) { changed[era] = HashSet(); dirty = emptySet(); continue }
            val queue = PriorityQueue<Pos>(compareBy<Pos>({ rank[EraPos(era, it)] ?: 0 }, { it.y }, { it.x }))
            queue.addAll(work)
            val done = HashSet<Pos>()
            val memo = EvolutionRules.SandMemo()
            val changedHere = HashSet<Pos>()
            while (queue.isNotEmpty()) {
                val p = queue.poll()
                if (!done.add(p)) continue
                val s = computeCell(era, p, memo, voided, mutate = true)
                if (s != grid[p]) {
                    grid[p] = s
                    changedHere += p
                    linksBySource[EraPos(era, p)]?.forEach { link ->
                        if (link.targetEra == era) { done.remove(link.target); queue.add(link.target) }
                        else pendingLinks.getOrPut(link.targetEra) { HashSet() } += link.target
                    }
                }
            }
            changed[era] = changedHere
            dirty = expand(changedHere, rules.dependencyRadius)
        }
        return RecomputeResult(changed, voided)
    }

    private fun expand(cells: Set<Pos>, r: Int): Set<Pos> {
        if (cells.isEmpty()) return emptySet()
        val out = HashSet<Pos>()
        for (c in cells) for (dy in -r..r) for (dx in -r..r) {
            val q = Pos(c.x + dx, c.y + dy)
            if (q.x in 0 until map.width && q.y in 0 until map.height) out += q
        }
        return out
    }

    private fun computeCell(era: Era, p: Pos, memo: EvolutionRules.SandMemo, voided: MutableList<ChangeRecord>, mutate: Boolean): CellState {
        recomputedCells++
        val idx = eras.indexOf(era)
        var s = if (idx == 0) map.base(p) else rules.evolve(grid(eras[idx - 1]), p, memo)
        map.overrides[era]?.get(p)?.let { s = it }
        linksByTarget[EraPos(era, p)]?.forEach { link ->
            if (grid(link.sourceEra)[link.source].feature == link.whenFeature) {
                s = s.copy(terrain = link.setTerrain ?: s.terrain, feature = link.setFeature ?: s.feature)
                    .let { it.copy(durability = it.feature.baseDurability) }
            }
        }
        changesAt[EraPos(era, p)]?.forEach { c ->
            if (c.voided) return@forEach
            val r = EvolutionRules.applyBuild(c.kind, s, era)
            if (r == null) {
                if (mutate) { c.voided = true; voided += c } else throw IllegalStateException("Kayıt tutarsız: ${c.id}")
            } else s = r.copy(causeId = c.id)
        }
        if (era == level.enemyEra) {
            if (p in razed) s = razedState(s)
            val d = damage[p]
            if (d != null && s.feature.damageable) {
                val left = s.feature.baseDurability - d
                s = if (left <= 0) s.copy(feature = destroyedForm(s.feature), durability = 0, level = 0, frozen = false) else s.copy(durability = left)
            }
        }
        return s
    }

    private fun destroyedForm(f: Feature): Feature = when {
        f == Feature.ENEMY_FORT -> Feature.ENEMY_RUINS
        f.isTower -> Feature.RUINS
        else -> Feature.NONE
    }

    private fun razedState(s: CellState): CellState = when (s.feature) {
        Feature.HUT, Feature.TOWN, Feature.CITY, Feature.WATCHTOWER, Feature.STONE_TOWER, Feature.ENERGY_TURRET -> s.copy(feature = Feature.RUINS, level = 0, frozen = false)
        Feature.FIELD, Feature.SAPLING, Feature.TREE, Feature.PALISADE, Feature.STONE_WALL, Feature.ENERGY_WALL,
        Feature.BRIDGE_WOOD, Feature.BRIDGE_STONE, Feature.BRIDGE_TECH, Feature.SHRINE_ACTIVE -> s.copy(
            feature = if (s.feature == Feature.SHRINE_ACTIVE) Feature.SHRINE else Feature.NONE, level = 0, frozen = false,
        )
        else -> s
    }

    fun rebuildAll() {
        for (era in eras) {
            val g = grid(era)
            val memo = EvolutionRules.SandMemo()
            for (p in orderedPositions(era)) g[p] = computeCell(era, p, memo, ArrayList(), mutate = true)
        }
    }

    private fun orderedPositions(era: Era): List<Pos> =
        grid(era).positions().sortedWith(compareBy<Pos>({ rank[EraPos(era, it)] ?: 0 }, { it.y }, { it.x })).toList()

    /** Sıfırdan bağımsız hesaplama yapıp artımlı sonuçla karşılaştırır (determinizm doğrulaması). */
    fun matchesFullRebuild(): Boolean {
        val fresh = EnumMap<Era, EraGrid>(Era::class.java)
        for (era in eras) {
            val g = EraGrid(era, map.width, map.height)
            fresh[era] = g
        }
        // Geçici olarak taze ızgaralarla hesapla.
        val saved = EnumMap(grids)
        try {
            for (era in eras) grids[era] = fresh[era]
            for (era in eras) {
                val memo = EvolutionRules.SandMemo()
                for (p in orderedPositions(era)) fresh[era]!![p] = computeCell(era, p, memo, ArrayList(), mutate = false)
            }
        } finally {
            for (era in eras) grids[era] = saved[era]
        }
        return eras.all { fresh[it]!!.contentEquals(grid(it)) }
    }

    fun signature(): Int = eras.fold(7) { acc, e -> acc * 131 + grid(e).signature() }
}

/** Nedensellik bağlantı grafiğinin doğrulaması: geriye dönük bağlantılar ve döngüler. */
object CausalGraph {
    class Report internal constructor(val errors: List<String>, internal val rank: Map<EraPos, Int>)

    fun analyze(level: LevelDef): Report {
        val errors = ArrayList<String>()
        val w = level.width
        val h = level.height
        fun inside(p: Pos) = p.x in 0 until w && p.y in 0 until h
        for (l in level.links) {
            if (l.sourceEra !in level.eras || l.targetEra !in level.eras) errors += "${l.id}: bölümde olmayan çağ"
            if (!inside(l.source) || !inside(l.target)) errors += "${l.id}: harita dışı"
            if (l.targetEra < l.sourceEra) errors += "${l.id}: geriye dönük nedensellik (${l.sourceEra.title} → ${l.targetEra.title})"
        }
        // Aynı çağ içindeki bağlantılar döngü oluşturabilir: DFS ile tespit et, sıralama (rank) üret.
        val edges = HashMap<EraPos, MutableList<EraPos>>()
        for (l in level.links) if (l.sourceEra == l.targetEra) {
            edges.getOrPut(EraPos(l.sourceEra, l.source)) { ArrayList() } += EraPos(l.targetEra, l.target)
        }
        val rank = HashMap<EraPos, Int>()
        val state = HashMap<EraPos, Int>() // 1 = ziyarette, 2 = bitti
        fun dfs(n: EraPos, depth: Int): Boolean {
            when (state[n]) {
                1 -> return false
                2 -> { if ((rank[n] ?: 0) >= depth) return true }
            }
            state[n] = 1
            rank[n] = maxOf(rank[n] ?: 0, depth)
            for (m in edges[n].orEmpty()) if (!dfs(m, depth + 1)) return false
            state[n] = 2
            return true
        }
        for (n in edges.keys) {
            if (!dfs(n, rank[n] ?: 0)) { errors += "Nedensellik döngüsü: ${n.era.title} ${n.pos}"; break }
        }
        return Report(errors, rank)
    }

    fun validate(level: LevelDef): List<String> = analyze(level).errors
}
