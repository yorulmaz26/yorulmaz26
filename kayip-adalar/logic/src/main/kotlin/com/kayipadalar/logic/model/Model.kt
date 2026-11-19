package com.kayipadalar.logic.model

import kotlinx.serialization.Serializable

/** Izgara yönleri. y aşağı doğru artar (kuzey = y-1). */
enum class Dir(val dx: Int, val dy: Int, val bit: Int) {
    N(0, -1, 1), E(1, 0, 2), S(0, 1, 4), W(-1, 0, 8);

    val opposite: Dir get() = entries[(ordinal + 2) % 4]
    val clockwise: Dir get() = entries[(ordinal + 1) % 4]

    companion object {
        fun parse(c: Char): Dir = when (c.uppercaseChar()) {
            'N' -> N; 'E' -> E; 'S' -> S; 'W' -> W
            else -> throw IllegalArgumentException("Geçersiz yön: $c")
        }
    }
}

@Serializable
data class Cell(val x: Int, val y: Int) {
    operator fun plus(d: Dir) = Cell(x + d.dx, y + d.dy)
    fun step(d: Dir, n: Int) = Cell(x + d.dx * n, y + d.dy * n)
    fun manhattan(o: Cell) = kotlin.math.abs(x - o.x) + kotlin.math.abs(y - o.y)
}

/** Köprü bağlantı noktaları 4 bitlik maske ile tutulur. */
object Ports {
    fun has(mask: Int, d: Dir) = mask and d.bit != 0
    fun rotateCw(mask: Int): Int {
        var r = 0
        for (d in Dir.entries) if (has(mask, d)) r = r or d.clockwise.bit
        return r
    }
    fun parse(s: String): Int = s.fold(0) { acc, c -> acc or Dir.parse(c).bit }
    fun format(mask: Int): String = Dir.entries.filter { has(mask, it) }.joinToString("") { it.name }
    fun count(mask: Int) = Integer.bitCount(mask and 15)
}

enum class Owner { PLAYER, NEUTRAL, ENEMY }

/**
 * Ada türleri. Görünen adlar yerelleştirme dosyasından ("island.<ad>") gelir.
 * [producesRes] bu ada kale ağına bağlıyken her turda ürettiği seviye-içi kaynağı belirtir.
 */
enum class IslandType(val producesRes: Res? = null, val produceAmount: Int = 0) {
    CASTLE,
    FOREST(Res.WOOD, 1),
    MINE(Res.STONE, 1),
    FARM(Res.FOOD, 1),
    WATER,
    FIRE,
    ICE,
    WIND,
    ELECTRIC,
    CRYSTAL(Res.CRYSTAL, 1),
    MILITARY,
    FORT,
    TRADE(Res.GOLD, 1),
    POISON,
    FOG,
    TIME,
    GRAVITY,
    PORTAL,
    VOLCANO,
    LIFE,
    ANCIENT,
    VILLAGE,
    EXIT,
    ENEMY_FORT,
    BURNT;

    val isProducer get() = producesRes != null
}

/** Seviye içi ve kalıcı kaynaklar. */
enum class Res { GOLD, WOOD, STONE, IRON, FOOD, CRYSTAL, ENERGY, ANCIENT_SHARD, STARDUST, ROYAL_SEAL }

enum class UnitType { SWORD, ARCHER, SHIELD, MAGE, ENGINEER, SCOUT, FLYER, SIEGE, HEALER, GUARDIAN, BOSS }

enum class AiBehavior { AGGRESSIVE, DEFENSIVE, TRAPPER, RESOURCER, STRATEGIST }

/** Oyuncunun kullanabileceği eylem türleri (kademeli açılır). */
enum class Mechanic { SLIDE, ROTATE, SWAP, RAISE, JUMP, UNIT_MOVE, WAIT }

/** Çalışma zamanı ada durumu. Kopyalanabilir olması arama/çözücü için önemlidir. */
data class Island(
    val id: String,
    var type: IslandType,
    var pos: Cell,
    var ports: Int,
    var owner: Owner = Owner.NEUTRAL,
    var height: Int = 0,
    var fixed: Boolean = false,
    var hidden: Boolean = false,
    var frozen: Int = 0,
    var anchored: Int = 0,
    var crumble: Int = -1,
    var alive: Boolean = true,
    var collected: Boolean = false,
) {
    val movable get() = !fixed && frozen <= 0 && anchored <= 0 && !hidden && alive
}

data class Troop(
    val id: String,
    val type: UnitType,
    val owner: Owner,
    var islandId: String,
    var hp: Int,
    val maxHp: Int,
    var atk: Int,
    var def: Int,
    val range: Int,
    val moves: Int,
    var stun: Int = 0,
    var alive: Boolean = true,
    var acted: Boolean = false,
)
