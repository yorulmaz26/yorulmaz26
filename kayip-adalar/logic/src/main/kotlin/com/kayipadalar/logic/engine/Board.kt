package com.kayipadalar.logic.engine

import com.kayipadalar.logic.data.GameConfig
import com.kayipadalar.logic.level.LevelDef
import com.kayipadalar.logic.level.ObjectiveType
import com.kayipadalar.logic.model.*

enum class Status { PLAYING, WON, LOST }

enum class LoseReason { NONE, OUT_OF_MOVES, CASTLE_DESTROYED, CASTLE_FELL, EXIT_FELL, ARMY_LOST }

/** Seviyeye uygulanan dış değiştiriciler (araştırma, binalar, mücadele kuralları). */
data class LevelModifiers(
    val bonusEnergy: Int = 0,
    val bonusCastleHp: Int = 0,
    val unitAtk: Int = 0,
    val unitDef: Int = 0,
    val unitHp: Int = 0,
    val comboDamage: Int = 0,
    val crystalEnergy: Int = 0,
    val moveLimitDelta: Int = 0,
    val enemyDoubleTurn: Boolean = false,
    val noStartEnergy: Boolean = false,
    val castleHpPct: Int = 100,
    val unitLevels: Map<UnitType, Int> = emptyMap(),
)

class SplitRng(var state: Long) {
    fun next(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
    fun nextInt(n: Int): Int = if (n <= 1) 0 else ((next() ushr 1) % n).toInt()
}

/** Bir bölümün tam çalışma zamanı durumu. [copy] derin kopya üretir (çözücü için). */
class Board private constructor(
    val def: LevelDef,
    val cfg: GameConfig,
    val width: Int,
    val height: Int,
    val blocked: Set<Cell>,
    val wind: Map<Cell, Dir>,
    val targets: Set<Cell>,
    val mechanics: Set<Mechanic>,
    val mods: LevelModifiers,
) {
    val islands = ArrayList<Island>()
    val units = ArrayList<Troop>()
    var movesUsed = 0
    var moveLimit = 0
    var turn = 0
    var energy = 0
    var castleHp = 0
    var castleMaxHp = 0
    val res = IntArray(Res.entries.size)
    val pairsTriggered = HashSet<String>()
    val combosTriggered = HashSet<String>()
    var bridgesFormed = 0
    var enemiesDefeated = 0
    var islandsMoved = 0
    var rotations = 0
    var status = Status.PLAYING
    var loseReason = LoseReason.NONE
    lateinit var rng: SplitRng
    var lastMovedId: String? = null
    var lastMovedPrevPos: Cell? = null
    var lastMovedPrevPorts: Int = 0
    var unitCounter = 0
    var edges: Set<Long> = emptySet()
    val homes = HashMap<String, String>()
    var aiBehavior = AiBehavior.AGGRESSIVE
    var bossDamageTaken = 0

    fun island(id: String): Island? = islands.firstOrNull { it.id == id }
    fun islandAt(c: Cell): Island? = islands.firstOrNull { it.alive && it.pos == c }
    fun unit(id: String): Troop? = units.firstOrNull { it.id == id }
    fun unitOn(islandId: String): Troop? = units.firstOrNull { it.alive && it.islandId == islandId }
    fun inBounds(c: Cell) = c.x in 0 until width && c.y in 0 until height
    fun isFree(c: Cell) = inBounds(c) && c !in blocked && islandAt(c) == null
    fun has(m: Mechanic) = m in mechanics
    val bossUnit: Troop? get() = def.boss?.let { b -> unit(b.unitId) }
    fun hasObjective(t: ObjectiveType) = def.objectives.any { it.type == t }
    val movesLeft get() = moveLimit - movesUsed

    fun copy(): Board {
        val b = Board(def, cfg, width, height, blocked, wind, targets, mechanics, mods)
        islands.forEach { b.islands.add(it.copy()) }
        units.forEach { b.units.add(it.copy()) }
        b.movesUsed = movesUsed; b.moveLimit = moveLimit; b.turn = turn; b.energy = energy
        b.castleHp = castleHp; b.castleMaxHp = castleMaxHp
        res.copyInto(b.res)
        b.pairsTriggered.addAll(pairsTriggered); b.combosTriggered.addAll(combosTriggered)
        b.bridgesFormed = bridgesFormed; b.enemiesDefeated = enemiesDefeated
        b.islandsMoved = islandsMoved; b.rotations = rotations
        b.status = status; b.loseReason = loseReason; b.rng = SplitRng(rng.state)
        b.lastMovedId = lastMovedId; b.lastMovedPrevPos = lastMovedPrevPos; b.lastMovedPrevPorts = lastMovedPrevPorts
        b.unitCounter = unitCounter; b.edges = edges; b.homes.putAll(homes); b.aiBehavior = aiBehavior
        b.bossDamageTaken = bossDamageTaken
        return b
    }

    /** Arama için durum özeti (konum, yön, birimler). */
    fun stateKey(): String {
        val sb = StringBuilder()
        for (i in islands) {
            if (!i.alive) { sb.append('x'); continue }
            sb.append(i.pos.x).append(',').append(i.pos.y).append(',').append(i.ports).append(',').append(i.height)
                .append(if (i.hidden) 'h' else '.').append(i.type.ordinal).append('g').append(i.group).append(';')
        }
        for (u in units) if (u.alive) sb.append(u.islandId).append(':').append(u.hp).append(';')
        sb.append(energy).append('|').append(castleHp)
        return sb.toString()
    }

    companion object {
        fun create(def: LevelDef, cfg: GameConfig, mods: LevelModifiers = LevelModifiers()): Board {
            val b = Board(
                def, cfg, def.width, def.height,
                def.blocked.map { Cell(it[0], it[1]) }.toSet(),
                def.features.filter { it.kind == "WIND" }.associate { Cell(it.x, it.y) to Dir.parse(it.dir[0]) },
                (def.features.filter { it.kind == "TARGET" }.map { Cell(it.x, it.y) } +
                    def.objectives.filter { it.type == ObjectiveType.DELIVER }.map { Cell(it.x, it.y) }).toSet(),
                def.mechanics.map { Mechanic.valueOf(it) }.toSet(),
                mods,
            )
            for (d in def.islands) {
                b.islands.add(
                    Island(
                        id = d.id, type = IslandType.valueOf(d.type), pos = Cell(d.x, d.y),
                        ports = Ports.parse(d.ports), owner = Owner.valueOf(d.owner), height = d.height,
                        fixed = d.fixed, hidden = d.hidden, frozen = d.frozen, crumble = d.crumble,
                    )
                )
            }
            for (u in def.units) {
                val type = UnitType.valueOf(u.type)
                val st = cfg.stats(type)
                val owner = Owner.valueOf(u.owner)
                val lvl = if (owner == Owner.PLAYER) (mods.unitLevels[type] ?: 0) else 0
                val bonusAtk = if (owner == Owner.PLAYER) mods.unitAtk + lvl else 0
                val bonusDef = if (owner == Owner.PLAYER) mods.unitDef + lvl / 2 else 0
                val bonusHp = if (owner == Owner.PLAYER) mods.unitHp + lvl else 0
                val hp = st.hp + u.hpBonus + bonusHp
                b.units.add(
                    Troop(
                        id = u.id, type = type, owner = owner, islandId = u.island, hp = hp, maxHp = hp,
                        atk = st.atk + u.atkBonus + bonusAtk, def = st.def + bonusDef, range = st.range, moves = st.moves,
                    )
                )
                b.homes[u.id] = u.island
            }
            b.moveLimit = maxOf(1, def.moveLimit + mods.moveLimitDelta)
            b.energy = if (mods.noStartEnergy) 0 else def.startEnergy + mods.bonusEnergy
            b.castleMaxHp = maxOf(1, (def.castleHp + mods.bonusCastleHp) * mods.castleHpPct / 100)
            b.castleHp = b.castleMaxHp
            b.rng = SplitRng(def.seed)
            b.aiBehavior = AiBehavior.valueOf(def.ai)
            Rules.initialize(b)
            return b
        }
    }
}
