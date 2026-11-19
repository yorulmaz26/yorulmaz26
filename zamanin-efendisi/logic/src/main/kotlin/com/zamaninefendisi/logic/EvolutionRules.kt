package com.zamaninefendisi.logic

/**
 * Çağlar arası evrim kuralları. Tamamen deterministiktir; oyuncu kuralları öğrenerek sonucu tahmin edebilir.
 *
 * Bir hücrenin sonraki çağdaki hâli, önceki çağda en fazla [dependencyRadius] uzaklıktaki
 * (Chebyshev) hücrelere bağlıdır. Nedensellik motoru yalnızca bu yarıçaptaki hücreleri yeniden hesaplar.
 */
class EvolutionRules(private val desertSpeed: Int) {

    /** Kum ilerlemesi her adımda 1 hücre yayılır; köy/kasaba kuralı 1 hücre daha bakar. */
    val dependencyRadius: Int = desertSpeed + 1

    fun evolve(prev: EraGrid, p: Pos, memo: SandMemo): CellState {
        val old = prev[p]
        // Zaman dondurma: nesne ve zemini sonraki çağlara olduğu gibi aktarılır.
        if (old.frozen) return old
        val terrain = terrainAfter(prev, p, memo)
        val sandNear = sandWithin1(prev, p, memo)
        val (feature, grew) = when (old.feature) {
            Feature.SAPLING -> Feature.TREE to true
            // Doğa evrimi: en az bir çağ yaşamış ağaç, yanında 2+ bitki varsa geçilmez kadim ormana dönüşür.
            Feature.TREE -> (if (old.level >= 1 && vegetationNeighbors(prev, p) >= 2) Feature.FOREST else Feature.TREE) to true
            Feature.FOREST -> Feature.FOREST to false
            Feature.STUMP -> Feature.NONE to false
            Feature.FIELD -> (if (terrain == Terrain.SAND) Feature.NONE else Feature.FIELD) to false
            Feature.BRIDGE_WOOD -> Feature.BRIDGE_STONE to true
            Feature.BRIDGE_STONE -> Feature.BRIDGE_TECH to true
            Feature.HUT -> when {
                sandNear -> Feature.RUINS to false
                nearWater(prev, p) -> Feature.TOWN to true
                else -> Feature.RUINS to false
            }
            Feature.TOWN -> (if (sandNear) Feature.RUINS else Feature.CITY) to !sandNear
            Feature.PALISADE -> Feature.STONE_WALL to true
            Feature.STONE_WALL -> Feature.ENERGY_WALL to true
            Feature.ENEMY_CAMP -> Feature.ENEMY_FORT to true
            Feature.ENEMY_RUINS -> Feature.NONE to false
            Feature.WATCHTOWER -> Feature.STONE_TOWER to true
            Feature.STONE_TOWER -> Feature.ENERGY_TURRET to true
            else -> old.feature to false
        }
        // Kumlaşan çayırda yaşayan bitki kalmaz (bitki zaten kumlaşmayı engeller; bu yalnızca güvence).
        val finalFeature = if (terrain == Terrain.SAND && feature.vegetation) Feature.NONE else feature
        val level = when {
            grew -> old.level + 1
            finalFeature == old.feature -> old.level
            else -> 0
        }
        val durability = if (finalFeature == old.feature) old.durability else finalFeature.baseDurability
        val cause = old.causeId
        return CellState(terrain, finalFeature, level, durability, cause)
    }

    private fun nearWater(prev: EraGrid, p: Pos): Boolean = around(prev, p).any {
        val c = prev[it]
        c.terrain == Terrain.WATER || c.feature.bridge
    }

    private fun vegetationNeighbors(prev: EraGrid, p: Pos): Int = around(prev, p).count { it != p && prev[it].feature.vegetation }

    private fun vegetationNear(prev: EraGrid, p: Pos): Boolean = around(prev, p).any { prev[it].feature.vegetation }

    /** Sonraki çağda p'nin 1 hücre çevresinde (kendisi dahil) kum var mı? */
    private fun sandWithin1(prev: EraGrid, p: Pos, memo: SandMemo): Boolean =
        around(prev, p).any { terrainAfter(prev, it, memo) == Terrain.SAND }

    fun terrainAfter(prev: EraGrid, p: Pos, memo: SandMemo): Terrain {
        val t = prev[p].terrain
        if (t != Terrain.GRASS) return t
        return if (sandAt(prev, p, desertSpeed, memo)) Terrain.SAND else Terrain.GRASS
    }

    /** Kum yayılımı: çayır, 4 komşusundan biri kumsa ve 1 hücre yakınında bitki yoksa kumlaşır. */
    private fun sandAt(prev: EraGrid, p: Pos, step: Int, memo: SandMemo): Boolean {
        if (!prev.inBounds(p)) return false
        val base = prev[p].terrain
        if (step == 0 || base != Terrain.GRASS) return base == Terrain.SAND
        val key = (step.toLong() shl 40) or (p.x.toLong() shl 20) or p.y.toLong()
        memo.map[key]?.let { return it }
        val result = sandAt(prev, p, step - 1, memo) ||
            (!vegetationNear(prev, p) && p.neighbors4().any { sandAt(prev, it, step - 1, memo) })
        memo.map[key] = result
        return result
    }

    private fun around(g: EraGrid, p: Pos): List<Pos> {
        val out = ArrayList<Pos>(9)
        for (dy in -1..1) for (dx in -1..1) {
            val q = Pos(p.x + dx, p.y + dy)
            if (g.inBounds(q)) out += q
        }
        return out
    }

    /** Bir çağ geçişi için ara hesap belleği. */
    class SandMemo { val map = HashMap<Long, Boolean>() }

    companion object {
        /** Oyuncu inşasının ön koşulu ve sonucu. Ön koşul sağlanmazsa null. */
        fun applyBuild(kind: BuildKind, s: CellState, era: Era): CellState? = when (kind) {
            BuildKind.PLANT ->
                if (s.terrain == Terrain.GRASS && (s.feature == Feature.NONE || s.feature == Feature.STUMP)) s.copy(feature = Feature.SAPLING, level = 0) else null
            BuildKind.CUT ->
                if (s.feature == Feature.TREE || s.feature == Feature.FOREST) s.copy(feature = Feature.STUMP, level = 0) else null
            BuildKind.BRIDGE ->
                if (s.terrain == Terrain.WATER && s.feature == Feature.NONE)
                    s.copy(feature = if (era == Era.FUTURE) Feature.BRIDGE_TECH else Feature.BRIDGE_WOOD, level = 0) else null
            BuildKind.VILLAGE ->
                if (s.terrain == Terrain.GRASS && s.feature == Feature.NONE) s.copy(feature = Feature.HUT, level = 0) else null
            BuildKind.WALL ->
                if ((s.terrain == Terrain.GRASS || s.terrain == Terrain.SAND || s.terrain == Terrain.ROAD) && s.feature == Feature.NONE)
                    s.copy(feature = if (era == Era.FUTURE) Feature.ENERGY_WALL else Feature.PALISADE, level = 0) else null
            BuildKind.DISMANTLE ->
                if (s.feature == Feature.ENEMY_CAMP) s.copy(feature = Feature.ENEMY_RUINS, level = 0) else null
            BuildKind.ACTIVATE ->
                if (s.feature == Feature.SHRINE) s.copy(feature = Feature.SHRINE_ACTIVE) else null
            BuildKind.COLLECT ->
                if (s.feature == Feature.CRYSTAL) s.copy(feature = Feature.NONE, level = 0) else null
            BuildKind.TOWER ->
                if ((s.terrain == Terrain.GRASS || s.terrain == Terrain.SAND || s.terrain == Terrain.ROAD) && s.feature == Feature.NONE)
                    s.copy(feature = if (era == Era.FUTURE) Feature.ENERGY_TURRET else Feature.WATCHTOWER, level = 0) else null
            BuildKind.FREEZE ->
                if (s.feature != Feature.NONE && !s.frozen && s.feature != Feature.CRYSTAL) s.copy(frozen = true) else null
        }?.let { if (kind == BuildKind.FREEZE) it else it.copy(durability = it.feature.baseDurability, frozen = false) }
    }
}
