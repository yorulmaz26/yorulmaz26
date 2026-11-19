package com.emirzinciri.robotkomutan.logic.sim

import com.emirzinciri.robotkomutan.logic.grid.Dir
import com.emirzinciri.robotkomutan.logic.grid.Pos
import com.emirzinciri.robotkomutan.logic.level.LevelData
import com.emirzinciri.robotkomutan.logic.level.RobotClass
import com.emirzinciri.robotkomutan.logic.level.EnemyType
import com.emirzinciri.robotkomutan.logic.level.Tile
import com.emirzinciri.robotkomutan.logic.level.stats

enum class RobotStatus { RUNNING, DONE, NO_ENERGY, LOST }

/** Bir robotun simülasyon içindeki değişebilir durumu. */
class RobotState(
    val id: String,
    val type: RobotClass,
    var pos: Pos,
    var dir: Dir,
    var energy: Int,
    val maxEnergy: Int,
    programSize: Int,
) {
    var carrying = false
    var pc = 0
    var status = RobotStatus.RUNNING
    /** Tekrar bloklarının kalan tur sayıları (blok başlangıç indeksine göre). */
    var loopLeft = IntArray(programSize)
    var inbox = 0
    /** Bu adımda çalışan kartın indeksi (-1: yok). Arayüz vurgulaması için. */
    var activePc = -1
    var waitingForMessage = false
    var energyUsed = 0
    var bumps = 0
    /** Çıkışa vardığı adım (senkron varış hedefi için); çıkışta değilse -1. */
    var arrivedTick = -1
    val stats = type.stats()
    var hp = stats.hp
    /** Kalan kalkan adımı. */
    var shield = 0

    val alive get() = status != RobotStatus.LOST

    fun copy(): RobotState = RobotState(id, type, pos, dir, energy, maxEnergy, 0).also {
        it.carrying = carrying; it.pc = pc; it.status = status; it.loopLeft = loopLeft.copyOf()
        it.inbox = inbox; it.activePc = activePc; it.waitingForMessage = waitingForMessage
        it.energyUsed = energyUsed; it.bumps = bumps; it.arrivedTick = arrivedTick; it.hp = hp; it.shield = shield
    }

    fun signature(): String =
        "$id@${pos.x},${pos.y},$dir,e$energy,c$carrying,pc$pc,$status,i$inbox,u$energyUsed,b$bumps,a$arrivedTick,h$hp,s$shield,${loopLeft.joinToString(".")}"
}

/** Düşman robotun durumu. */
class EnemyState(val id: String, val type: EnemyType, var pos: Pos, var dir: Dir, var hp: Int, val maxHp: Int, val range: Int) {
    val alive get() = hp > 0
    fun copy() = EnemyState(id, type, pos, dir, hp, maxHp, range)
    fun signature() = "$id@${pos.x},${pos.y},$dir,h$hp"
}

/** Bütün dünyanın bir adımlık anlık görüntüsü. Kopyalanabilir; geri sarma ve test için kullanılır. */
class WorldState(
    val robots: List<RobotState>,
    val boxes: MutableSet<Pos>,
    val cells: MutableMap<Pos, Int>,
    val filledPits: MutableSet<Pos>,
    val toggled: MutableSet<Int>,
    val timers: MutableMap<Int, Int>,
    val socketCharge: MutableMap<Pos, Int>,
    val openDoors: MutableSet<Pos>,
    val enemies: List<EnemyState> = emptyList(),
) {
    var tick = 0

    fun robot(id: String) = robots.first { it.id == id }
    fun robotAt(p: Pos): RobotState? = robots.firstOrNull { it.alive && it.pos == p }
    fun enemyAt(p: Pos): EnemyState? = enemies.firstOrNull { it.alive && it.pos == p }

    fun copy(): WorldState = WorldState(
        robots.map { it.copy() }, boxes.toMutableSet(), cells.toMutableMap(), filledPits.toMutableSet(),
        toggled.toMutableSet(), timers.toMutableMap(), socketCharge.toMutableMap(), openDoors.toMutableSet(),
        enemies.map { it.copy() },
    ).also { it.tick = tick }

    /** Determinizm testleri için durumun eksiksiz, sıralı metin özeti. */
    fun signature(): String = buildString {
        append("t$tick|")
        robots.forEach { append(it.signature()).append('|') }
        append("B").append(boxes.sortedWith(posOrder)).append('|')
        append("C").append(cells.toSortedMap(posOrder)).append('|')
        append("P").append(filledPits.sortedWith(posOrder)).append('|')
        append("T").append(toggled.sorted()).append(timers.toSortedMap()).append('|')
        append("S").append(socketCharge.toSortedMap(posOrder)).append('|')
        append("D").append(openDoors.sortedWith(posOrder))
        enemies.forEach { append('|').append(it.signature()) }
    }

    companion object {
        val posOrder: Comparator<Pos> = compareBy({ it.y }, { it.x })

        fun initial(level: LevelData, programSizes: Map<String, Int>): WorldState {
            val robots = level.def.robots.sortedBy { it.id }.map {
                RobotState(it.id, it.type, Pos(it.x, it.y), it.dir, it.energy, it.maxEnergy, programSizes[it.id] ?: 0)
            }
            val doors = level.allPositions.filter { level.tile(it) == Tile.DOOR_INV }.toMutableSet()
            return WorldState(
                robots, level.initialBoxes.toMutableSet(), level.initialCells.toMutableMap(), mutableSetOf(),
                mutableSetOf(), mutableMapOf(), mutableMapOf(), doors,
                level.def.enemies.sortedBy { it.id }.map {
                    val hp = if (it.hp > 0) it.hp else it.type.defaultHp
                    EnemyState(it.id, it.type, Pos(it.x, it.y), it.dir, hp, hp, it.range)
                },
            )
        }
    }
}
