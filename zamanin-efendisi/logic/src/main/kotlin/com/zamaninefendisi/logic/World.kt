package com.zamaninefendisi.logic

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max

/** Üç zaman katmanı. Sıralama nedensellik yönünü belirler: Geçmiş → Şimdi → Gelecek. */
enum class Era(val title: String) {
    PAST("Geçmiş"), PRESENT("Şimdi"), FUTURE("Gelecek");

    val next: Era? get() = entries.getOrNull(ordinal + 1)
    val previous: Era? get() = entries.getOrNull(ordinal - 1)

    /** "Geçmiş'te", "Şimdi'de", "Gelecek'te" */
    val locative: String get() = when (this) {
        PAST -> "Geçmiş'te"
        PRESENT -> "Şimdi'de"
        FUTURE -> "Gelecek'te"
    }
}

@Serializable
data class Pos(val x: Int, val y: Int) {
    fun neighbors4(): List<Pos> = listOf(Pos(x, y - 1), Pos(x + 1, y), Pos(x, y + 1), Pos(x - 1, y))
    fun chebyshev(o: Pos): Int = max(abs(x - o.x), abs(y - o.y))
    fun manhattan(o: Pos): Int = abs(x - o.x) + abs(y - o.y)
    override fun toString(): String = "($x,$y)"
}

enum class Terrain(val title: String, val walkable: Boolean) {
    GRASS("Çayır", true),
    SAND("Kum", true),
    WATER("Nehir", false),
    MOUNTAIN("Dağ", false),
    ROAD("Yol", true),
}

/**
 * Hücre üzerindeki nesne. Nesneler çağdan çağa [EvolutionRules] ile evrilir.
 * [blocks]: kahraman ve düşmanlar için geçilmez. [bridge]: suyu geçilebilir kılar.
 */
enum class Feature(
    val title: String,
    val blocks: Boolean = false,
    val bridge: Boolean = false,
    val vegetation: Boolean = false,
    val baseDurability: Int = 0,
) {
    NONE(""),
    SAPLING("Fidan", vegetation = true),
    TREE("Ağaç", vegetation = true),
    FOREST("Kadim Orman", vegetation = true),
    STUMP("Kütük"),
    FIELD("Tarla"),
    BRIDGE_WOOD("Ahşap Köprü", bridge = true),
    BRIDGE_STONE("Taş Köprü", bridge = true),
    BRIDGE_TECH("Işık Geçidi", bridge = true),
    HUT("Köy"),
    TOWN("Kasaba"),
    CITY("Şehir"),
    RUINS("Harabe"),
    PALISADE("Ahşap Çit", blocks = true, baseDurability = 2),
    STONE_WALL("Taş Sur", blocks = true, baseDurability = UNBREAKABLE),
    ENERGY_WALL("Enerji Kalkanı", blocks = true, baseDurability = UNBREAKABLE),
    CASTLE("Zaman Kalesi"),
    ENEMY_CAMP("Düşman Kampı", blocks = true),
    ENEMY_FORT("Düşman Kalesi", blocks = true),
    ENEMY_RUINS("Yıkık Kamp"),
    SHRINE("Zaman Mührü", blocks = true),
    SHRINE_ACTIVE("Uyanmış Mühür", blocks = true),
    ;

    val isWall: Boolean get() = this == PALISADE || this == STONE_WALL || this == ENERGY_WALL
}

const val UNBREAKABLE = 99

/**
 * Bir çağdaki tek bir hücrenin durumu.
 * [level]: gelişim seviyesi (her evrimde artar), [durability]: dayanıklılık,
 * [causeId]: bu durumu doğuran oyuncu değişikliğinin kimliği (-1: doğal/başlangıç).
 */
data class CellState(
    val terrain: Terrain,
    val feature: Feature = Feature.NONE,
    val level: Int = 0,
    val durability: Int = feature.baseDurability,
    val causeId: Int = -1,
) {
    val walkable: Boolean get() = (terrain.walkable || feature.bridge) && !feature.blocks
    val displayName: String get() = if (feature == Feature.NONE) terrain.title else feature.title
}

/** Benzersiz nesne kimliği: çağ + konum. */
fun objectId(era: Era, p: Pos): String = "${era.name.lowercase()}-${p.x}-${p.y}"

class EraGrid(val era: Era, val width: Int, val height: Int) {
    private val cells = arrayOfNulls<CellState>(width * height)

    fun inBounds(p: Pos): Boolean = p.x in 0 until width && p.y in 0 until height
    operator fun get(p: Pos): CellState = cells[p.y * width + p.x] ?: error("Hücre hesaplanmadı: $p")
    fun getOrNull(p: Pos): CellState? = if (inBounds(p)) cells[p.y * width + p.x] else null
    operator fun set(p: Pos, s: CellState) { cells[p.y * width + p.x] = s }

    fun positions(): Sequence<Pos> = sequence { for (y in 0 until height) for (x in 0 until width) yield(Pos(x, y)) }
    fun count(f: Feature): Int = positions().count { this[it].feature == f }
    fun find(f: Feature): List<Pos> = positions().filter { this[it].feature == f }.toList()

    fun contentEquals(o: EraGrid): Boolean = positions().all { this[it] == o[it] }

    /** Durumun deterministik özeti (kayıt/yükleme ve test karşılaştırmaları için). */
    fun signature(): Int = positions().fold(17) { acc, p -> 31 * acc + this[p].hashCode() }
}
