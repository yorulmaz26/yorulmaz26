package com.yeraltiimparatorlugu.sim

import kotlinx.serialization.Serializable
import java.util.Base64

/** Kayıt biçimi sürümü. 2: araştırma, ekosistem, savaş, rakipler ve süreklilik sistemleri eklendi. */
const val SAVE_VERSION = 2

@Serializable data class ChunkSave(val cx: Int, val cy: Int, val cells: String? = null, val revealed: String? = null)

@Serializable
data class AntSave(
    val id: Int, val type: AntType, val x: Float, val y: Float, val hp: Float, val hunger: Float, val energy: Float,
    val xp: Int, val age: Float, val assignment: TaskCategory? = null, val carryType: ResourceType? = null, val carryAmount: Int = 0,
    val sick: Boolean = false, val squad: Int = 0,
)

@Serializable data class BroodSave(val id: Int, val stage: BroodStage, val progress: Float, val fed: Float, val roomId: Int)

@Serializable
data class RoomSave(
    val id: Int, val type: RoomType, val x: Int, val y: Int, val level: Int, val state: RoomState, val buildProgress: Float,
    val maintained: Boolean, val leafBuffer: Int, val growProgress: Float,
    val recipe: Recipe = Recipe.RESIN, val trainClass: AntType? = null, val trainCount: Int = 0, val stationProgress: Float = 0f,
)

@Serializable data class DigOrderSave(val x: Int, val y: Int, val roomId: Int, val progress: Float)
@Serializable data class FillSave(val x: Int, val y: Int, val progress: Float)
@Serializable data class PileSave(val x: Int, val y: Int, val type: ResourceType, val amount: Int)
@Serializable data class CorpseSave(val id: Int, val x: Int, val y: Int, val title: String, val age: Float)
@Serializable data class SourceSave(val id: Int, val x: Int, val kind: SourceKind, val amount: Int, val maxAmount: Int, val spawnedAt: Double = 0.0)
@Serializable data class PheromoneSave(val id: Int, val type: PheromoneType, val x: Int, val y: Int, val strength: Float)

@Serializable
data class CreatureSave(
    val id: Int, val kind: CreatureKind, val x: Float, val y: Float, val hp: Float, val rivalId: Int, val goalX: Int, val goalY: Int,
    val hasGoal: Boolean, val retreating: Boolean, val stolen: Int, val attackMult: Float, val lifetime: Float,
)

@Serializable
data class RivalSave(
    val id: Int, val name: String, val personality: RivalPersonality, val x: Int, val population: Int, val soldiers: Int, val food: Float,
    val defense: Float, val techLevel: Int, val relation: Int, val treaty: Treaty, val treatyUntil: Double, val discovered: Boolean,
    val raidTimer: Float, val alive: Boolean, val respawnAt: Double, val raidsLaunched: Int, val defeats: Int,
)

@Serializable data class ExpeditionSave(val id: Int, val regionIndex: Int, val members: List<AntSave>, val startedAt: Double, val duration: Double)
@Serializable data class SideColonySave(val id: Int, val name: String, val x: Int, val population: Int, val food: Float, val shipmentTimer: Float, val totalSent: Int)
@Serializable data class OrderSave(val kind: OrderKind, val x: Int, val y: Int, val targetId: Int)

@Serializable
data class StatsSave(
    val cellsDug: Int = 0, val foodGathered: Int = 0, val materialsGathered: Int = 0, val foodEaten: Int = 0, val antsBorn: Int = 0,
    val antsDied: Int = 0, val eggsLaid: Int = 0, val roomsBuilt: Int = 0, val collapses: Int = 0, val soilDumped: Int = 0,
    val goalsCompleted: Int = 0, val playTime: Double = 0.0, val maxDepth: Int = 0, val enemiesKilled: Int = 0, val rivalsDefeated: Int = 0,
    val raidsRepelled: Int = 0, val expeditionsSucceeded: Int = 0, val produced: Int = 0, val researchDone: Int = 0, val antsTrained: Int = 0,
)

@Serializable data class LogSave(val day: Int, val text: String)

@Serializable
data class EnvironmentSave(
    val weather: Weather = Weather.CLEAR, val weatherUntil: Double = 300.0, val colonyTemp: Float = 20f, val humidity: Float = 55f,
    val droughtLevel: Float = 0f, val waterKeys: List<Long> = emptyList(), val waterAmounts: List<Float> = emptyList(),
)

/** Kayıt dosyasının tamamı. Yeni alanlar varsayılan değerle eklenir; eski kayıtlar okunabilir. */
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
    // --- Sürüm 2 ---
    val fillOrders: List<FillSave> = emptyList(),
    val corpses: List<CorpseSave> = emptyList(),
    val creatures: List<CreatureSave> = emptyList(),
    val researchDone: List<String> = emptyList(),
    val researchCurrent: String? = null,
    val researchProgress: Float = 0f,
    val researchBoostUntil: Double = 0.0,
    val evolutionRanks: List<Int> = emptyList(),
    val traits: List<GeneticTrait> = emptyList(),
    val traitCurrent: GeneticTrait? = null,
    val traitProgress: Float = 0f,
    val environment: EnvironmentSave = EnvironmentSave(),
    val nextEventAt: Double = Simulation.DAY_LENGTH * 1.5,
    val rivals: List<RivalSave> = emptyList(),
    val expeditions: List<ExpeditionSave> = emptyList(),
    val regionsCompleted: Int = 0,
    val discoveredCreatures: List<CreatureKind> = emptyList(),
    val fossils: List<FossilKind> = emptyList(),
    val fossilDuplicates: Int = 0,
    val achievements: List<Achievement> = emptyList(),
    val empireGoal: EmpireGoalKind? = null,
    val empireTier: Int = 0,
    val sideColonies: List<SideColonySave> = emptyList(),
    val owned: List<MarketItem> = emptyList(),
    val equipped: Map<MarketSlot, MarketItem> = emptyMap(),
    val decorations: List<MarketItem> = emptyList(),
    val orders: List<OrderSave> = emptyList(),
    val retreatWounded: Boolean = true,
    val coins: Int = 0,
    val knownLeft: Int = -Simulation.KNOWN_RANGE,
    val knownRight: Int = Simulation.KNOWN_RANGE,
    val difficulty: Difficulty = Difficulty.NORMAL,
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

    fun antToSave(it: Ant) = AntSave(it.id, it.type, it.x, it.y, it.hp, it.hunger, it.energy, it.xp, it.age, it.assignment, it.carryType, it.carryAmount, it.sick, it.squad)

    fun antFromSave(a: AntSave) = Ant(a.id, a.type, a.x, a.y).apply {
        hp = a.hp; hunger = a.hunger; energy = a.energy; xp = a.xp; age = a.age
        assignment = a.assignment; carryType = a.carryType; carryAmount = a.carryAmount; sick = a.sick; squad = a.squad
    }

    fun toSave(sim: Simulation, nowMillis: Long, maxSeenMillis: Long): GameSave {
        val water = sim.environment.water.entries.toList()
        return GameSave(
            seed = sim.seed, time = sim.time, nextId = sim.nextId, queenLayTimer = sim.queenLayTimer, surfaceMound = sim.surfaceMound,
            lastDay = sim.day, savedAtMillis = nowMillis, maxSeenMillis = maxOf(maxSeenMillis, nowMillis),
            resources = sim.resources.toMap(), priorities = sim.priorities.toMap(),
            chunks = sim.world.chunksToSave().map { c -> ChunkSave(c.cx, c.cy, if (c.modified) b64.encodeToString(c.cells) else null, if (c.anyRevealed) packBits(c.revealed) else null) },
            ants = sim.ants.map { antToSave(it) },
            brood = sim.brood.map { BroodSave(it.id, it.stage, it.progress, it.fed, it.roomId) },
            rooms = sim.rooms.map { RoomSave(it.id, it.type, it.x, it.y, it.level, it.state, it.buildProgress, it.maintained, it.leafBuffer, it.growProgress, it.recipe, it.trainClass, it.trainCount, it.stationProgress) },
            digOrders = sim.digOrders.values.map { DigOrderSave(it.x, it.y, it.roomId, it.progress) },
            piles = sim.piles.values.map { PileSave(it.x, it.y, it.type, it.amount) },
            sources = sim.sources.map { SourceSave(it.id, it.x, it.kind, it.amount, it.maxAmount, it.spawnedAt) },
            pheromones = sim.pheromones.markers.map { PheromoneSave(it.id, it.type, it.x, it.y, it.strength) },
            stats = with(sim.stats) {
                StatsSave(cellsDug, foodGathered, materialsGathered, foodEaten, antsBorn, antsDied, eggsLaid, roomsBuilt, collapses, soilDumped, goalsCompleted, playTime,
                    maxDepth, enemiesKilled, rivalsDefeated, raidsRepelled, expeditionsSucceeded, produced, researchDone, antsTrained)
            },
            log = sim.log.map { LogSave(it.day, it.text) },
            goals = sim.goals.active.toList(), goalsCompleted = sim.goals.completed, nextGoalId = sim.goals.nextGoalId, tutorialIndex = sim.goals.tutorialIndex,
            risky = sim.riskySnapshot(),
            fillOrders = sim.fillOrders.values.map { FillSave(it.x, it.y, it.progress) },
            corpses = sim.corpses.map { CorpseSave(it.id, it.x, it.y, it.title, it.age) },
            creatures = sim.creatures.map { CreatureSave(it.id, it.kind, it.x, it.y, it.hp, it.rivalId, it.goalX, it.goalY, it.hasGoal, it.retreating, it.stolen, it.attackMult, it.lifetime) },
            researchDone = sim.research.completed.toList(), researchCurrent = sim.research.current, researchProgress = sim.research.progress, researchBoostUntil = sim.research.boostUntil,
            evolutionRanks = sim.evolution.ranks.toList(),
            traits = sim.genetics.unlocked.toList(), traitCurrent = sim.genetics.current, traitProgress = sim.genetics.progress,
            environment = with(sim.environment) { EnvironmentSave(weather, weatherUntil, colonyTemp, humidity, droughtLevel, water.map { it.key }, water.map { it.value }) },
            nextEventAt = sim.events.nextAt,
            rivals = sim.rivalSystem.rivals.map { RivalSave(it.id, it.name, it.personality, it.x, it.population, it.soldiers, it.food, it.defense, it.techLevel, it.relation, it.treaty, it.treatyUntil, it.discovered, it.raidTimer, it.alive, it.respawnAt, it.raidsLaunched, it.defeats) },
            expeditions = sim.expeditions.active.map { ExpeditionSave(it.id, it.regionIndex, it.members, it.startedAt, it.duration) },
            regionsCompleted = sim.expeditions.regionsCompleted, discoveredCreatures = sim.expeditions.discoveredCreatures.toList(),
            fossils = sim.museum.found.toList(), fossilDuplicates = sim.museum.duplicates,
            achievements = sim.achievements.unlocked.toList(), empireGoal = sim.empireGoal.kind, empireTier = sim.empireGoal.tier,
            sideColonies = sim.federation.colonies.map { SideColonySave(it.id, it.name, it.x, it.population, it.food, it.shipmentTimer, it.totalSent) },
            owned = sim.market.owned.toList(), equipped = sim.market.equipped.toMap(), decorations = sim.market.decorations.toList(),
            orders = sim.orders.map { OrderSave(it.kind, it.x, it.y, it.targetId) }, retreatWounded = sim.retreatWounded,
            coins = sim.coins, knownLeft = sim.knownLeft, knownRight = sim.knownRight, difficulty = sim.difficulty,
        )
    }

    fun fromSave(s: GameSave): Simulation {
        val sim = Simulation(s.seed)
        sim.time = s.time
        sim.nextId = s.nextId
        sim.queenLayTimer = s.queenLayTimer
        sim.surfaceMound = s.surfaceMound
        sim.rng = kotlin.random.Random(s.seed xor (s.time * 1000).toLong())
        sim.resources.loadFrom(s.resources)
        sim.priorities.loadFrom(s.priorities)
        for (c in s.chunks) {
            sim.world.restoreChunk(c.cx, c.cy, c.cells?.let { b64d.decode(it) }?.takeIf { it.size == CHUNK_SIZE * CHUNK_SIZE }, c.revealed?.let { unpackBits(it, CHUNK_SIZE * CHUNK_SIZE) })
        }
        for (r in s.rooms) {
            val room = Room(r.id, r.type, r.x, r.y).apply {
                level = r.level; state = r.state; buildProgress = r.buildProgress; maintained = r.maintained
                leafBuffer = r.leafBuffer; growProgress = r.growProgress; recipe = r.recipe; trainClass = r.trainClass; trainCount = r.trainCount; stationProgress = r.stationProgress
            }
            sim.rooms.add(room)
            sim.registerRoomCells(room)
        }
        s.ants.forEach { sim.ants.add(antFromSave(it)) }
        for (b in s.brood) sim.brood.add(Brood(b.id, b.stage, b.roomId).apply { progress = b.progress; fed = b.fed })
        for (o in s.digOrders) sim.digOrders[cellKey(o.x, o.y)] = DigOrder(o.x, o.y, o.roomId).apply { progress = o.progress }
        for (o in s.fillOrders) sim.fillOrders[cellKey(o.x, o.y)] = FillOrder(o.x, o.y).apply { progress = o.progress }
        for (p in s.piles) sim.piles[cellKey(p.x, p.y)] = Pile(p.x, p.y, p.type, p.amount)
        for (c in s.corpses) sim.corpses.add(Corpse(c.id, c.x, c.y, c.title).apply { age = c.age })
        for (src in s.sources) sim.sources.add(FoodSource(src.id, src.x, src.kind, src.maxAmount).apply { amount = src.amount; spawnedAt = src.spawnedAt })
        for (m in s.pheromones) sim.pheromones.markers.add(PheromoneMarker(m.id, m.type, m.x, m.y).apply { strength = m.strength })
        for (c in s.creatures) sim.creatures.add(Creature(c.id, c.kind, c.x, c.y, c.rivalId).apply {
            hp = c.hp; goalX = c.goalX; goalY = c.goalY; hasGoal = c.hasGoal; retreating = c.retreating; stolen = c.stolen; attackMult = c.attackMult; lifetime = c.lifetime
        })
        with(s.stats) {
            val st = sim.stats
            st.cellsDug = cellsDug; st.foodGathered = foodGathered; st.materialsGathered = materialsGathered; st.foodEaten = foodEaten
            st.antsBorn = antsBorn; st.antsDied = antsDied; st.eggsLaid = eggsLaid; st.roomsBuilt = roomsBuilt; st.collapses = collapses
            st.soilDumped = soilDumped; st.goalsCompleted = goalsCompleted; st.playTime = playTime; st.maxDepth = maxDepth
            st.enemiesKilled = enemiesKilled; st.rivalsDefeated = rivalsDefeated; st.raidsRepelled = raidsRepelled
            st.expeditionsSucceeded = expeditionsSucceeded; st.produced = produced; st.researchDone = researchDone; st.antsTrained = antsTrained
        }
        s.log.forEach { sim.log.add(LogEntry(it.day, it.text)) }
        sim.goals.active.addAll(s.goals)
        sim.goals.completed = s.goalsCompleted
        sim.goals.nextGoalId = s.nextGoalId
        sim.goals.tutorialIndex = s.tutorialIndex
        sim.research.completed.addAll(s.researchDone.filter { ResearchTree.get(it) != null })
        sim.research.current = s.researchCurrent?.takeIf { ResearchTree.get(it) != null }
        sim.research.progress = s.researchProgress
        sim.research.boostUntil = s.researchBoostUntil
        s.evolutionRanks.forEachIndexed { i, v -> if (i < sim.evolution.ranks.size) sim.evolution.ranks[i] = v }
        sim.genetics.unlocked.addAll(s.traits)
        sim.genetics.current = s.traitCurrent
        sim.genetics.progress = s.traitProgress
        with(sim.environment) {
            weather = s.environment.weather; weatherUntil = s.environment.weatherUntil; colonyTemp = s.environment.colonyTemp
            humidity = s.environment.humidity; droughtLevel = s.environment.droughtLevel
            s.environment.waterKeys.zip(s.environment.waterAmounts).forEach { (k, v) -> water[k] = v }
        }
        sim.events.nextAt = s.nextEventAt
        if (s.rivals.isEmpty()) sim.rivalSystem.init(sim) else for (r in s.rivals) sim.rivalSystem.rivals.add(RivalColony(r.id, r.name, r.personality, r.x).apply {
            population = r.population; soldiers = r.soldiers; food = r.food; defense = r.defense; techLevel = r.techLevel; relation = r.relation
            treaty = r.treaty; treatyUntil = r.treatyUntil; discovered = r.discovered; raidTimer = r.raidTimer; alive = r.alive; respawnAt = r.respawnAt
            raidsLaunched = r.raidsLaunched; defeats = r.defeats
        })
        s.expeditions.forEach { sim.expeditions.active.add(Expedition(it.id, it.regionIndex, it.members, it.startedAt, it.duration)) }
        sim.expeditions.regionsCompleted = s.regionsCompleted
        sim.expeditions.discoveredCreatures.addAll(s.discoveredCreatures)
        sim.museum.found.addAll(s.fossils)
        sim.museum.duplicates = s.fossilDuplicates
        sim.achievements.unlocked.addAll(s.achievements)
        sim.empireGoal.kind = s.empireGoal
        sim.empireGoal.tier = s.empireTier
        s.sideColonies.forEach { sim.federation.colonies.add(SideColony(it.id, it.name, it.x).apply { population = it.population; food = it.food; shipmentTimer = it.shipmentTimer; totalSent = it.totalSent }) }
        sim.market.owned.addAll(s.owned)
        sim.market.equipped.putAll(s.equipped)
        sim.market.decorations.addAll(s.decorations)
        s.orders.forEachIndexed { i, o -> if (i < 2) sim.orders[i].apply { kind = o.kind; x = o.x; y = o.y; targetId = o.targetId } }
        sim.retreatWounded = s.retreatWounded
        sim.coins = s.coins
        sim.difficulty = s.difficulty
        sim.knownLeft = s.knownLeft
        sim.knownRight = s.knownRight
        sim.restoreInternal(s.lastDay, 0f, s.risky)
        sim.afterLoad()
        sim.goals.ensureGoals(sim)
        return sim
    }
}
