package com.yeraltiimparatorlugu.sim

import kotlinx.serialization.Serializable
import java.util.Base64

const val SAVE_VERSION = 1

@Serializable
data class ChunkSave(val cx: Int, val cy: Int, val cells: String? = null, val revealed: String? = null)

@Serializable
data class AntSave(
    val id: Int, val type: AntType, val x: Float, val y: Float, val hp: Float, val hunger: Float, val energy: Float,
    val xp: Int, val age: Float, val assignment: TaskCategory? = null, val carryType: ResourceType? = null, val carryAmount: Int = 0,
)

@Serializable
data class BroodSave(val id: Int, val stage: BroodStage, val progress: Float, val fed: Float, val roomId: Int)

@Serializable
data class RoomSave(
    val id: Int, val type: RoomType, val x: Int, val y: Int, val level: Int, val state: RoomState, val buildProgress: Float,
    val maintained: Boolean, val leafBuffer: Int, val growProgress: Float,
)

@Serializable
data class DigOrderSave(val x: Int, val y: Int, val roomId: Int, val progress: Float)

@Serializable
data class PileSave(val x: Int, val y: Int, val type: ResourceType, val amount: Int)

@Serializable
data class SourceSave(val id: Int, val x: Int, val kind: SourceKind, val amount: Int, val maxAmount: Int, val spawnedAt: Double = 0.0)

@Serializable
data class PheromoneSave(val id: Int, val type: PheromoneType, val x: Int, val y: Int, val strength: Float)

@Serializable
data class StatsSave(
    val cellsDug: Int = 0, val foodGathered: Int = 0, val materialsGathered: Int = 0, val foodEaten: Int = 0, val antsBorn: Int = 0,
    val antsDied: Int = 0, val eggsLaid: Int = 0, val roomsBuilt: Int = 0, val collapses: Int = 0, val soilDumped: Int = 0,
    val goalsCompleted: Int = 0, val playTime: Double = 0.0,
)

@Serializable
data class LogSave(val day: Int, val text: String)

/** Kayıt dosyasının tamamı. Yeni alanlar varsayılan değerle eklenir, böylece eski kayıtlar okunabilir. */
@Serializable
data class GameSave(
    val version: Int = SAVE_VERSION,
    val seed: Long,
    val time: Double,
    val nextId: Int,
    val queenLayTimer: Float,
    val surfaceMound: Int,
    val hardRockUnlocked: Boolean = false,
    val lastDay: Int,
    val savedAtMillis: Long,
    val maxSeenMillis: Long,
    val resources: Map<String, Int>,
    val priorities: Map<String, Int>,
    val chunks: List<ChunkSave>,
    val ants: List<AntSave>,
    val brood: List<BroodSave>,
    val rooms: List<RoomSave>,
    val digOrders: List<DigOrderSave>,
    val piles: List<PileSave>,
    val sources: List<SourceSave>,
    val pheromones: List<PheromoneSave>,
    val stats: StatsSave,
    val log: List<LogSave>,
    val goals: List<Goal>,
    val goalsCompleted: Int,
    val nextGoalId: Int,
    val tutorialIndex: Int = 0,
    val risky: List<Long> = emptyList(),
)

object SaveMapper {
    private val b64 = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    private fun packBits(bits: BooleanArray): String {
        val bytes = ByteArray((bits.size + 7) / 8)
        for (i in bits.indices) if (bits[i]) bytes[i shr 3] = (bytes[i shr 3].toInt() or (1 shl (i and 7))).toByte()
        return b64.encodeToString(bytes)
    }

    private fun unpackBits(s: String, size: Int): BooleanArray {
        val bytes = b64d.decode(s)
        return BooleanArray(size) { i -> (i shr 3) < bytes.size && (bytes[i shr 3].toInt() shr (i and 7)) and 1 == 1 }
    }

    fun toSave(sim: Simulation, nowMillis: Long, maxSeenMillis: Long): GameSave = GameSave(
        seed = sim.seed,
        time = sim.time,
        nextId = sim.nextId,
        queenLayTimer = sim.queenLayTimer,
        surfaceMound = sim.surfaceMound,
        hardRockUnlocked = sim.hardRockUnlocked,
        lastDay = sim.day,
        savedAtMillis = nowMillis,
        maxSeenMillis = maxOf(maxSeenMillis, nowMillis),
        resources = sim.resources.toMap(),
        priorities = sim.priorities.toMap(),
        chunks = sim.world.chunksToSave().map { c ->
            ChunkSave(c.cx, c.cy, if (c.modified) b64.encodeToString(c.cells) else null, if (c.anyRevealed) packBits(c.revealed) else null)
        },
        ants = sim.ants.map { AntSave(it.id, it.type, it.x, it.y, it.hp, it.hunger, it.energy, it.xp, it.age, it.assignment, it.carryType, it.carryAmount) },
        brood = sim.brood.map { BroodSave(it.id, it.stage, it.progress, it.fed, it.roomId) },
        rooms = sim.rooms.map { RoomSave(it.id, it.type, it.x, it.y, it.level, it.state, it.buildProgress, it.maintained, it.leafBuffer, it.growProgress) },
        digOrders = sim.digOrders.values.map { DigOrderSave(it.x, it.y, it.roomId, it.progress) },
        piles = sim.piles.values.map { PileSave(it.x, it.y, it.type, it.amount) },
        sources = sim.sources.map { SourceSave(it.id, it.x, it.kind, it.amount, it.maxAmount, it.spawnedAt) },
        pheromones = sim.pheromones.markers.map { PheromoneSave(it.id, it.type, it.x, it.y, it.strength) },
        stats = with(sim.stats) {
            StatsSave(cellsDug, foodGathered, materialsGathered, foodEaten, antsBorn, antsDied, eggsLaid, roomsBuilt, collapses, soilDumped, goalsCompleted, playTime)
        },
        log = sim.log.map { LogSave(it.day, it.text) },
        goals = sim.goals.active.toList(),
        goalsCompleted = sim.goals.completed,
        nextGoalId = sim.goals.nextGoalId,
        tutorialIndex = sim.goals.tutorialIndex,
        risky = sim.riskySnapshot(),
    )

    fun fromSave(s: GameSave): Simulation {
        val sim = Simulation(s.seed)
        sim.time = s.time
        sim.nextId = s.nextId
        sim.queenLayTimer = s.queenLayTimer
        sim.surfaceMound = s.surfaceMound
        sim.hardRockUnlocked = s.hardRockUnlocked
        sim.rng = kotlin.random.Random(s.seed xor (s.time * 1000).toLong())
        sim.resources.loadFrom(s.resources)
        sim.priorities.loadFrom(s.priorities)
        for (c in s.chunks) {
            sim.world.restoreChunk(
                c.cx, c.cy,
                c.cells?.let { b64d.decode(it) }?.takeIf { it.size == CHUNK_SIZE * CHUNK_SIZE },
                c.revealed?.let { unpackBits(it, CHUNK_SIZE * CHUNK_SIZE) },
            )
        }
        for (r in s.rooms) {
            val room = Room(r.id, r.type, r.x, r.y).apply {
                level = r.level; state = r.state; buildProgress = r.buildProgress; maintained = r.maintained
                leafBuffer = r.leafBuffer; growProgress = r.growProgress
            }
            sim.rooms.add(room)
            sim.registerRoomCells(room)
        }
        for (a in s.ants) {
            sim.ants.add(Ant(a.id, a.type, a.x, a.y).apply {
                hp = a.hp; hunger = a.hunger; energy = a.energy; xp = a.xp; age = a.age
                assignment = a.assignment; carryType = a.carryType; carryAmount = a.carryAmount
            })
        }
        for (b in s.brood) sim.brood.add(Brood(b.id, b.stage, b.roomId).apply { progress = b.progress; fed = b.fed })
        for (o in s.digOrders) sim.digOrders[cellKey(o.x, o.y)] = DigOrder(o.x, o.y, o.roomId).apply { progress = o.progress }
        for (p in s.piles) sim.piles[cellKey(p.x, p.y)] = Pile(p.x, p.y, p.type, p.amount)
        for (src in s.sources) sim.sources.add(FoodSource(src.id, src.x, src.kind, src.maxAmount).apply { amount = src.amount; spawnedAt = src.spawnedAt })
        for (m in s.pheromones) sim.pheromones.markers.add(PheromoneMarker(m.id, m.type, m.x, m.y).apply { strength = m.strength })
        with(s.stats) {
            sim.stats.cellsDug = cellsDug; sim.stats.foodGathered = foodGathered; sim.stats.materialsGathered = materialsGathered
            sim.stats.foodEaten = foodEaten; sim.stats.antsBorn = antsBorn; sim.stats.antsDied = antsDied; sim.stats.eggsLaid = eggsLaid
            sim.stats.roomsBuilt = roomsBuilt; sim.stats.collapses = collapses; sim.stats.soilDumped = soilDumped
            sim.stats.goalsCompleted = goalsCompleted; sim.stats.playTime = playTime
        }
        s.log.forEach { sim.log.add(LogEntry(it.day, it.text)) }
        sim.goals.active.addAll(s.goals)
        sim.goals.completed = s.goalsCompleted
        sim.goals.nextGoalId = s.nextGoalId
        sim.goals.tutorialIndex = s.tutorialIndex
        sim.restoreInternal(s.lastDay, 0f, s.risky)
        sim.goals.ensureGoals(sim)
        return sim
    }
}
