package com.yeraltiimparatorlugu.sim

import kotlin.random.Random

enum class MoveResult { ARRIVED, MOVING, WAITING, UNREACHABLE }

enum class DigDesignation(val message: String?) {
    OK(null),
    ALREADY("Zaten işaretli"),
    NOT_DIGGABLE("Burası zaten açık"),
    NEEDS_TECH("Kaya: Sert Kaya Kazma araştırması veya Tünel Uzmanı gerekir"),
    SURFACE("Yüzey kazılamaz"),
    ROOM("Oda alanı"),
}

class Placement(val ok: Boolean, val reason: String?, val digCells: Int)

class ColonyStats {
    var cellsDug = 0
    var foodGathered = 0
    var materialsGathered = 0
    var foodEaten = 0
    var antsBorn = 0
    var antsDied = 0
    var eggsLaid = 0
    var roomsBuilt = 0
    var collapses = 0
    var soilDumped = 0
    var goalsCompleted = 0
    var playTime = 0.0
    var maxDepth = 0
    var enemiesKilled = 0
    var rivalsDefeated = 0
    var raidsRepelled = 0
    var expeditionsSucceeded = 0
    var produced = 0
    var researchDone = 0
    var antsTrained = 0
}

class LogEntry(val day: Int, val text: String)

/**
 * Koloni simülasyonu: sabit zaman adımıyla ([STEP]) ilerler, çizimden bağımsızdır ve tamamen test edilebilir.
 * Ekonomi, kazı, ekosistem, savaş, rakipler ve süreklilik sistemleri burada bir araya gelir.
 */
class Simulation(val seed: Long) {
    companion object {
        const val STEP = 1f / 30f
        const val DAY_LENGTH = 600.0
        const val KNOWN_RANGE = 28
        const val SOURCE_MARGIN = 18
        const val MAX_SOURCES = 13
        const val MAX_NEAR_SOURCES = 8
        const val FAR_SOURCE_LIFETIME = 420f
        const val LAY_INTERVAL = 45f
        const val MAX_BUILDERS = 3
        const val FARM_CYCLE = 20f
        const val PATH_BUDGET_PER_STEP = 10
        const val BASE_FOOD_CAPACITY = 20
        const val BASE_MATERIAL_CAPACITY = 50
        const val BASE_WATER_CAPACITY = 20
        const val MAX_CREATURES = 45
        const val ROYAL_WORK = 40f

        fun newGame(seed: Long): Simulation = Simulation(seed).also { it.setupStart() }
    }

    val world = World(seed)
    val pheromones = PheromoneField()
    val pathfinder = Pathfinder(world, pheromones)
    val ants = ArrayList<Ant>()
    val brood = ArrayList<Brood>()
    val rooms = ArrayList<Room>()
    private val roomCells = HashMap<Long, Int>()
    val digOrders = LinkedHashMap<Long, DigOrder>()
    val fillOrders = LinkedHashMap<Long, FillOrder>()
    val piles = LinkedHashMap<Long, Pile>()
    val corpses = ArrayList<Corpse>()
    val sources = ArrayList<FoodSource>()
    val creatures = ArrayList<Creature>()
    val resources = ResourceStore()
    val priorities = TaskPriorities()
    val stats = ColonyStats()
    val log = ArrayList<LogEntry>()
    val goals = GoalSystem()
    var mods = Modifiers()
        private set
    val research = ResearchSystem()
    val evolution = EvolutionSystem()
    val genetics = GeneticsSystem()
    val environment = Environment()
    val events = EventSystem()
    val rivalSystem = RivalSystem()
    val expeditions = ExpeditionSystem()
    val museum = MuseumSystem()
    val achievements = AchievementSystem()
    val empireGoal = EmpireGoal()
    val federation = FederationSystem()
    val market = MarketSystem()
    val orders = arrayOf(MilitaryOrder(), MilitaryOrder())
    var retreatWounded = true

    val notifications = ArrayDeque<String>()
    val sounds = ArrayDeque<SoundCue>()

    var time = 0.0
    var nextId = 1
    var rng = Random(seed)
    var queenLayTimer = 0f
    var surfaceMound = 0
    val dumpX = 3
    var coins = 0
    var difficulty = Difficulty.RELAXED
    var knownLeft = -KNOWN_RANGE
    var knownRight = KNOWN_RANGE
    var reputation = 0
        private set

    private var accumulator = 0f
    private var pathBudget = PATH_BUDGET_PER_STEP
    private var sourceTimer = 0f
    private var collapseTimer = 0f
    private var slowTimer = 0f
    private var waterSourceTimer = 0f
    private var creatureTimer = 0f
    private var lastDay = 1
    private val occupancy = HashMap<Long, Int>()
    private val riskyCells = HashSet<Long>()
    private val entrances = HashSet<Long>()
    private val seeps = HashSet<Long>()
    private val roomFlood = HashMap<Int, Float>()
    private var lastHitSound = 0.0

    val alpha: Float get() = (accumulator / STEP).coerceIn(0f, 1f)
    val day: Int get() = (time / DAY_LENGTH).toInt() + 1
    val dayFraction: Float get() = ((time % DAY_LENGTH) / DAY_LENGTH).toFloat()
    val season: Season get() = environment.season(day)
    val queen: Ant? get() = ants.firstOrNull { it.type == AntType.QUEEN }
    val workers: List<Ant> get() = ants.filter { it.type.caste == Caste.WORKER }
    val workerCount: Int get() = ants.count { it.type.caste == Caste.WORKER }
    val militaryCount: Int get() = ants.count { it.type.military }
    val population: Int get() = ants.count { it.type.caste != Caste.QUEEN }

    fun newId() = nextId++

    // ================================================================ Başlangıç

    private fun setupStart() {
        for (y in 0..6) world.set(0, y, Terrain.TUNNEL)
        val queenRoom = Room(newId(), RoomType.QUEEN_CHAMBER, -2, 7).also { it.state = RoomState.ACTIVE }
        val food = Room(newId(), RoomType.FOOD_STORAGE, 3, 7).also { it.state = RoomState.ACTIVE }
        for (r in listOf(queenRoom, food)) {
            rooms.add(r)
            r.forEachCell { x, y -> world.set(x, y, Terrain.TUNNEL); roomCells[cellKey(x, y)] = r.id }
        }
        world.reveal(0, 3, 4); world.reveal(0, 8, 7); world.reveal(5, 8, 5)
        ants.add(Ant(newId(), AntType.QUEEN, queenRoom.anchorX + 0.5f, queenRoom.anchorY + 0.5f))
        repeat(6) { i -> ants.add(Ant(newId(), AntType.WORKER, -1.5f + i, 9.5f).also { it.hunger = 10f + i * 4f }) }
        resources.set(ResourceType.FOOD, 30); resources.set(ResourceType.SEED, 8); resources.set(ResourceType.SOIL, 16)
        resources.set(ResourceType.LEAF, 8); resources.set(ResourceType.WOOD, 4); resources.set(ResourceType.WATER, 6)
        addSource(-7, SourceKind.LEAF_BUSH, 14)
        addSource(9, SourceKind.DEAD_INSECT, 10)
        addSource(14, SourceKind.SEED_PILE, 12)
        addSource(-16, SourceKind.TWIGS, 10)
        repeat(3) { spawnRandomSource(far = it == 0) }
        rivalSystem.init(this)
        rebuildWaterSources()
        recomputeModifiers()
        addLog("Koloni kuruldu. Kraliçe ve 6 işçi yeni yuvalarında.")
        goals.ensureGoals(this)
    }

    // ================================================================ Zaman

    fun update(delta: Float, speed: Int = 1) {
        if (speed <= 0) return
        accumulator += minOf(delta, 0.25f) * speed
        var steps = 0
        while (accumulator >= STEP && steps < 12 * speed) { step(STEP); accumulator -= STEP; steps++ }
    }

    fun step(dt: Float) {
        time += dt
        stats.playTime += dt
        pathBudget = PATH_BUDGET_PER_STEP
        rebuildOccupancy()
        pheromones.update(dt)
        environment.update(this, dt)
        updateRooms(dt)
        updateQueen(dt)
        updateBrood(dt)
        for (ant in ants.toTypedArray()) updateAnt(ant, dt)
        updateCreatures(dt)
        rivalSystem.update(this, dt)
        federation.update(this, dt)
        events.update(this)
        sourceTimer += dt
        if (sourceTimer >= 14f) { sourceTimer = 0f; refreshSources() }
        collapseTimer += dt
        if (collapseTimer >= 4f) { collapseTimer = 0f; checkCollapses() }
        waterSourceTimer += dt
        if (waterSourceTimer >= 30f) { waterSourceTimer = 0f; rebuildWaterSources() }
        creatureTimer += dt
        if (creatureTimer >= 60f) { creatureTimer = 0f; ambientCreatures() }
        slowTimer += dt
        if (slowTimer >= 1f) { slowTick(slowTimer); slowTimer = 0f }
        if (day != lastDay) { lastDay = day; onNewDay() }
        goals.update(this)
    }

    /** Saniyede bir çalışan yavaş sistemler. */
    private fun slowTick(dt: Float) {
        expeditions.update(this)
        for (r in rooms) {
            var sum = 0f
            r.forEachCell { x, y -> sum += environment.waterAt(x, y) }
            val f = sum / (r.width * r.height)
            if (f > 0.01f) roomFlood[r.id] = f else roomFlood.remove(r.id)
        }
        // Sel yavruya zarar verir
        if (!mods.waterproof) {
            val drowned = brood.filter { b -> (roomFlood[b.roomId] ?: 0f) > 0.5f && rng.nextFloat() < 0.02f * dt }
            if (drowned.isNotEmpty()) { brood.removeAll(drowned.toSet()); addLog("Sel ${drowned.size} yavruyu yok etti."); notify("Sel yavru odasına ulaştı!") }
        }
        // Hastalık yayılması ve cesetler
        val vent = rooms.any { it.type == RoomType.VENTILATION && it.functional }
        val sick = ants.filter { it.sick }
        for (s in sick) for (o in ants) if (!o.sick && o !== s && o.type.caste != Caste.QUEEN && o.cellX == s.cellX && o.cellY == s.cellY && rng.nextFloat() < (if (vent) 0.03f else 0.07f) * dt) o.sick = true
        for (c in corpses) c.age += dt
        if (corpses.count { it.age > 180f } >= 3 && rng.nextFloat() < 0.01f * dt) {
            ants.filter { it.type.caste != Caste.QUEEN }.randomOrNull(rng)?.let { it.sick = true; notify("Temizlenmeyen cesetler hastalık yaydı") }
        }
        reputation = Reputation.of(this)
        achievements.update(this)
        empireGoal.update(this)
        // Genetik araştırma tamamlanmış mı (istasyon yoksa ilerlemez)
    }

    private fun rebuildOccupancy() {
        occupancy.clear()
        for (a in ants) {
            if (a.type == AntType.QUEEN || a.activity == AntActivity.RESTING) continue
            val k = cellKey(a.cellX, a.cellY)
            occupancy[k] = (occupancy[k] ?: 0) + 1
        }
    }

    // ================================================================ Değiştiriciler

    fun recomputeModifiers() {
        val m = Modifiers()
        for (id in research.completed) ResearchTree.get(id)?.effect?.invoke(m)
        evolution.apply(m)
        for (t in genetics.unlocked) t.effect(m)
        mods = m
        pheromones.durationMultiplier = m.pheromoneDuration
        pheromones.radiusBonus = m.pheromoneRadius
    }

    val hardRockUnlocked: Boolean get() = mods.hardRock

    fun isUnlocked(researchId: String?) = research.isDone(researchId)

    fun knownMin() = knownLeft - mods.knownRangeBonus
    fun knownMax() = knownRight + mods.knownRangeBonus
    fun isKnown(x: Int) = x in knownMin()..knownMax()

    // ================================================================ Karınca davranışı

    private fun updateAnt(ant: Ant, dt: Float) {
        ant.prevX = ant.x
        ant.prevY = ant.y
        ant.age += dt
        if (ant.attackCooldown > 0f) ant.attackCooldown -= dt
        if (ant.type == AntType.QUEEN) {
            val maxHp = ant.maxHp(mods)
            if (ant.hp < maxHp) ant.hp = (ant.hp + dt * 0.3f).coerceAtMost(maxHp)
            return
        }
        val military = ant.type.military
        val working = ant.activity != AntActivity.IDLE && ant.activity != AntActivity.RESTING && ant.activity != AntActivity.GUARDING
        ant.hunger = (ant.hunger + dt * (100f / 260f) * difficulty.hungerMult * (if (military) 1.3f else 1f)).coerceAtMost(100f)
        if (ant.activity != AntActivity.RESTING) ant.energy = (ant.energy - dt * (if (working) 0.33f else 0.12f)).coerceAtLeast(0f)
        ant.starving = ant.hunger >= 100f
        val water = environment.waterAt(ant.cellX, ant.cellY)
        if (ant.starving) ant.hp -= dt * 0.15f
        if (ant.sick) ant.hp -= dt * 0.04f
        if (water > 0.85f) ant.hp -= dt * 0.5f
        if (ant.hp <= 0f) {
            killAnt(ant, when { ant.starving -> "açlıktan"; water > 0.85f -> "boğularak"; ant.sick -> "hastalıktan"; else -> "yaralanarak" })
            return
        }
        if (!ant.starving && !ant.sick && ant.hp < ant.maxHp(mods)) ant.hp = (ant.hp + dt * 0.05f).coerceAtMost(ant.maxHp(mods))

        val job = ant.job
        if (ant.eatCooldown > 0f) ant.eatCooldown -= dt
        val busyFight = job is FightJob || job is MarchJob
        val threat = if (!military && job !is FightJob && job !is FleeJob) nearestHostile(ant.x, ant.y, if (mods.alarm) 5f else 3f) else null
        when {
            threat != null && ant.cellY >= -1 -> switchJob(ant, FleeJob())
            !busyFight && job !is EatJob && ant.hunger >= 65f && resources.edibleTotal() > 0 && ant.eatCooldown <= 0f -> switchJob(ant, EatJob())
            !busyFight && job !is EatJob && job !is RestJob && ant.energy < 15f -> switchJob(ant, RestJob(restRoomFor(ant)))
            !busyFight && job !is RestJob && job !is EatJob && (ant.sick || (military && retreatWounded && ant.hp < ant.maxHp(mods) * 0.4f)) && infirmary() != null && job !is HealJob ->
                switchJob(ant, RestJob(infirmary()))
            job == null || ((job is WanderJob || job is GuardJob) && ant.waitTimer <= 0f) -> {
                ant.waitTimer = 1f
                val chosen = if (military) chooseMilitaryJob(ant) else chooseJob(ant)
                if (chosen != null) switchJob(ant, chosen) else if (job == null) switchJob(ant, wanderJob(ant))
            }
        }
        if (ant.waitTimer > 0f) ant.waitTimer -= dt
        // Bitişikteki düşmana karşılık ver (kaçan işçiler de).
        if (ant.job !is FightJob) {
            val adj = nearestHostile(ant.x, ant.y, 1.15f)
            if (adj != null) antAttacks(ant, adj)
        }

        val current = ant.job ?: return
        when (current.update(this, ant, dt)) {
            JobStatus.RUNNING -> {}
            JobStatus.DONE -> { current.cancel(this, ant); if (current.category != null) ant.xp++; ant.job = null; ant.activity = AntActivity.IDLE }
            JobStatus.FAILED -> {
                current.cancel(this, ant); ant.job = null; ant.activity = AntActivity.IDLE; ant.clearPath()
                if (current is EatJob) ant.eatCooldown = 6f
            }
        }
    }

    private fun switchJob(ant: Ant, job: Job) {
        ant.job?.cancel(this, ant)
        ant.clearPath()
        ant.job = job
    }

    private fun wanderJob(ant: Ant): Job {
        val rally = pheromones.nearest(PheromoneType.RALLY, ant.cellX, ant.cellY)
        val cx = rally?.x ?: ant.cellX
        val cy = rally?.y ?: ant.cellY
        val r = rally?.radius ?: 4
        repeat(8) {
            val x = cx + rng.nextInt(-r, r + 1)
            val y = cy + rng.nextInt(-r, r + 1)
            if (world.isWalkable(x, y) && (rally != null || y >= 0 || ant.cellY < 0) && world.get(x, y) != Terrain.RIVAL_TUNNEL) return WanderJob(x, y, 1f + rng.nextFloat() * 2f)
        }
        return WanderJob(ant.cellX, ant.cellY, 1.5f)
    }

    private class Candidate(val score: Float, val make: () -> Job)

    fun chooseJob(ant: Ant): Job? {
        if (ant.carryAmount > 0 && ant.carryType != null) return DeliverJob()
        ant.assignment?.let { spec -> if (priorities[spec] > 0) findCandidate(ant, spec)?.let { return it.make() } }
        for (group in priorities.orderedGroups()) {
            var best: Candidate? = null
            for (cat in group) {
                val c = findCandidate(ant, cat) ?: continue
                if (best == null || c.score < best.score) best = c
            }
            if (best != null) return best.make()
        }
        return null
    }

    private fun blocked(x: Int, y: Int) = pheromones.strengthAt(PheromoneType.AVOID, x, y) > 0f || pheromones.strengthAt(PheromoneType.DANGER, x, y) > 0f

    private fun findCandidate(ant: Ant, cat: TaskCategory): Candidate? {
        val ax = ant.cellX
        val ay = ant.cellY
        return when (cat) {
            TaskCategory.DIG -> {
                var best: DigOrder? = null
                var bestScore = Float.MAX_VALUE
                val tunneler = ant.type == AntType.TUNNELER
                for (o in digOrders.values) {
                    if (o.reservedBy != -1 || o.unreachableUntil > time) continue
                    if (!hasWalkableNeighbor(o.x, o.y)) continue
                    val t = world.get(o.x, o.y)
                    if (t.needsHardRockTech && !mods.hardRock && !tunneler) continue
                    if (blocked(o.x, o.y)) continue
                    var s = manhattan(ax, ay, o.x, o.y).toFloat() - pheromones.strengthAt(PheromoneType.DIG, o.x, o.y) * 30f
                    if (o.roomId != -1) s -= 4f
                    if (s < bestScore) { bestScore = s; best = o }
                }
                best?.let { o -> Candidate(bestScore) { o.reservedBy = ant.id; DigJob(o) } }
            }
            TaskCategory.REPAIR -> {
                if (resources[ResourceType.SOIL] < 2) return null
                val o = fillOrders.values.filter { it.reservedBy == -1 && it.unreachableUntil <= time && hasWalkableNeighborExcept(it.x, it.y) }
                    .minByOrNull { manhattan(ax, ay, it.x, it.y) } ?: return null
                Candidate(manhattan(ax, ay, o.x, o.y).toFloat() - 6f) { o.reservedBy = ant.id; FillJob(o) }
            }
            TaskCategory.HAUL -> {
                var best: Pile? = null
                var bestScore = Float.MAX_VALUE
                for (p in piles.values) {
                    if (p.reservedBy != -1 || p.unreachableUntil > time) continue
                    if (p.type != ResourceType.SOIL && freeSpaceFor(p.type) <= 0) continue
                    if (blocked(p.x, p.y)) continue
                    val s = manhattan(ax, ay, p.x, p.y).toFloat() - pheromones.strengthAt(PheromoneType.CARRY, p.x, p.y) * 30f
                    if (s < bestScore) { bestScore = s; best = p }
                }
                best?.let { p -> Candidate(bestScore) { p.reservedBy = ant.id; HaulJob(p) } }
            }
            TaskCategory.CLEAN -> {
                val c = corpses.filter { it.reservedBy == -1 && !blocked(it.x, it.y) }.minByOrNull { manhattan(ax, ay, it.x, it.y) } ?: return null
                Candidate(manhattan(ax, ay, c.x, c.y).toFloat() - pheromones.strengthAt(PheromoneType.CARRY, c.x, c.y) * 30f) { c.reservedBy = ant.id; CleanJob(c) }
            }
            TaskCategory.GATHER -> {
                var best: FoodSource? = null
                var bestScore = Float.MAX_VALUE
                val foodLow = resources.edibleTotal() < 15
                for (s in sources) {
                    if (s.amount - s.reserved <= 0 || s.unreachableUntil > time) continue
                    if (freeSpaceFor(s.kind.resource) <= 0) continue
                    val foodMark = pheromones.strengthAt(PheromoneType.FOOD, s.x, -1)
                    if (!isKnown(s.x) && foodMark <= 0f) continue
                    if (blocked(s.x, -1)) continue
                    var score = manhattan(ax, ay, s.x, -1).toFloat() - foodMark * 40f
                    if (foodLow && s.kind.resource.edible) score -= 12f
                    if (score < bestScore) { bestScore = score; best = s }
                }
                best?.let { s ->
                    Candidate(bestScore) {
                        val amount = minOf(ant.carryCapacity(mods), s.amount - s.reserved)
                        s.reserved += amount
                        GatherJob(s, amount)
                    }
                }
            }
            TaskCategory.BUILD -> {
                val r = rooms.filter { (it.state == RoomState.CONSTRUCTING || it.state == RoomState.UPGRADING) && it.builders < MAX_BUILDERS }
                    .minByOrNull { manhattan(ax, ay, it.anchorX, it.anchorY) } ?: return null
                Candidate(manhattan(ax, ay, r.anchorX, r.anchorY).toFloat()) { r.builders++; BuildJob(r) }
            }
            TaskCategory.PRODUCE -> {
                val station = rooms.filter { it.functional && it.type != RoomType.RESEARCH_LAB && it.type != RoomType.GENETICS_LAB && it.type.isStation && it.workers < it.stationCapacity && stationHasWork(it) }
                    .minByOrNull { manhattan(ax, ay, it.anchorX, it.anchorY) }
                val stationC = station?.let { st -> Candidate(manhattan(ax, ay, st.anchorX, st.anchorY).toFloat() + 2f) { st.workers++; StationJob(st, TaskCategory.PRODUCE) } }
                val available = resources[ResourceType.LEAF] - rooms.sumOf { it.supplyReserved }
                val farm = if (available <= 0) null else rooms.filter { it.type == RoomType.FUNGUS_FARM && it.functional && it.leafBuffer + it.supplyReserved < farmLeafCapacity(it) }
                    .minByOrNull { manhattan(ax, ay, it.anchorX, it.anchorY) }
                val farmC = farm?.let { f ->
                    val amount = minOf(ant.carryCapacity(mods), farmLeafCapacity(f) - f.leafBuffer - f.supplyReserved, available)
                    if (amount <= 0) null else Candidate(manhattan(ax, ay, f.anchorX, f.anchorY).toFloat()) { f.supplyReserved += amount; SupplyFarmJob(f, amount) }
                }
                listOfNotNull(stationC, farmC).minByOrNull { it.score }
            }
            TaskCategory.RESEARCH -> {
                val st = rooms.filter { it.functional && (it.type == RoomType.RESEARCH_LAB || it.type == RoomType.GENETICS_LAB) && it.workers < it.stationCapacity && stationHasWork(it) }
                    .minByOrNull { manhattan(ax, ay, it.anchorX, it.anchorY) } ?: return null
                Candidate(manhattan(ax, ay, st.anchorX, st.anchorY).toFloat()) { st.workers++; StationJob(st, TaskCategory.RESEARCH) }
            }
            TaskCategory.NURSE -> {
                if (ant.type == AntType.MEDIC || pheromones.markers.any { it.type == PheromoneType.HELP }) {
                    val patient = ants.filter { it !== ant && it.type != AntType.QUEEN && (it.sick || it.hp < it.maxHp(mods) * 0.7f) && it.cellY >= -1 &&
                        (ant.type == AntType.MEDIC || pheromones.strengthAt(PheromoneType.HELP, it.cellX, it.cellY) > 0f) && ants.none { o -> (o.job as? HealJob)?.patient === it } }
                        .minByOrNull { manhattan(ax, ay, it.cellX, it.cellY) }
                    if (patient != null) return Candidate(manhattan(ax, ay, patient.cellX, patient.cellY).toFloat() - 15f) { HealJob(patient) }
                }
                if (resources.edibleTotal() <= 0) return null
                val b = brood.filter { it.stage == BroodStage.LARVA && it.fed < 0.4f && it.nurseReservedBy == -1 }.minByOrNull { it.fed } ?: return null
                val room = roomById(b.roomId) ?: return null
                Candidate(manhattan(ax, ay, room.anchorX, room.anchorY).toFloat() - 10f) { b.nurseReservedBy = ant.id; NurseJob(b) }
            }
            TaskCategory.EXPLORE -> {
                if (ant.type != AntType.SCOUT && ant.assignment != TaskCategory.EXPLORE && priorities[TaskCategory.EXPLORE] > 2) {
                    // İşçiler yalnızca keşif önceliği yüksekse keşfe çıkar; keşifçiler her zaman.
                    if (pheromones.markers.none { it.type == PheromoneType.EXPLORE }) return null
                }
                val mark = pheromones.nearest(PheromoneType.EXPLORE, ax, ay)
                if (mark != null) {
                    val cell = nearestWalkable(mark.x, mark.y, 4) ?: return null
                    return Candidate(manhattan(ax, ay, keyX(cell), keyY(cell)).toFloat()) { ExploreJob(keyX(cell), keyY(cell)) }
                }
                val right = knownMax() <= -knownMin()
                val tx = if (right) minOf(knownMax() + 8, 600) else maxOf(knownMin() - 8, -600)
                Candidate(manhattan(ax, ay, tx, -1).toFloat() * 0.3f + 20f) { ExploreJob(tx, -1) }
            }
            TaskCategory.DEFENSE -> {
                val enemy = creatures.filter { it.kind.hostile && it.cellY >= 0 && !it.retreating }.minByOrNull { manhattan(ax, ay, it.cellX, it.cellY) } ?: return null
                val d = manhattan(ax, ay, enemy.cellX, enemy.cellY)
                if (d > 14) return null
                Candidate(d.toFloat()) { FightJob(enemy) }
            }
        }
    }

    /** Askerî sınıfların kararı: emir, tehlike ve feromonlara göre. */
    fun chooseMilitaryJob(ant: Ant): Job {
        val order = orders[ant.squad.coerceIn(0, 1)]
        val q = rooms.first { it.type == RoomType.QUEEN_CHAMBER }
        if (ant.type == AntType.ROYAL_GUARD) {
            val e = nearestHostile(q.anchorX + 0.5f, q.anchorY + 0.5f, 10f)
            return if (e != null) FightJob(e) else GuardJob(q.x + rng.nextInt(q.width), q.anchorY, false)
        }
        if (order.kind == OrderKind.RETREAT) {
            val b = rooms.firstOrNull { it.type == RoomType.BARRACKS && it.functional } ?: q
            val c = floorCell(b); return GuardJob(keyX(c), keyY(c), false)
        }
        if (order.kind == OrderKind.RAID_RIVAL) {
            val r = rivalSystem.byId(order.targetId)
            if (r != null && r.alive) return MarchJob(r.id)
            order.kind = OrderKind.DEFEND
        }
        val enemy: Creature? = when (order.kind) {
            OrderKind.TARGET -> creatures.firstOrNull { it.id == order.targetId } ?: run { order.kind = OrderKind.DEFEND; null }
            OrderKind.ATTACK -> creatures.filter { it.kind.hostile && (it.cellY >= 0 || isKnown(it.cellX)) }.minByOrNull { manhattan(ant.cellX, ant.cellY, it.cellX, it.cellY) }
            else -> {
                val anchor = militaryAnchor(ant, order)
                creatures.filter { c ->
                    c.kind.hostile && (manhattan(c.cellX, c.cellY, keyX(anchor), keyY(anchor)) <= 12 || manhattan(c.cellX, c.cellY, ant.cellX, ant.cellY) <= 8 ||
                        pheromones.strengthAt(PheromoneType.ATTACK, c.cellX, c.cellY) > 0f || pheromones.strengthAt(PheromoneType.DANGER, c.cellX, c.cellY) > 0f ||
                        (c.cellY >= 0 && order.kind == OrderKind.DEFEND && manhattan(c.cellX, c.cellY, q.anchorX, q.anchorY) <= 25))
                }.minByOrNull { manhattan(ant.cellX, ant.cellY, it.cellX, it.cellY) }
            }
        }
        if (enemy != null) return FightJob(enemy)
        val danger = pheromones.nearest(PheromoneType.DANGER, ant.cellX, ant.cellY)
        val anchor = if (danger != null && order.kind == OrderKind.DEFEND) (nearestWalkable(danger.x, danger.y, 4) ?: militaryAnchor(ant, order)) else militaryAnchor(ant, order)
        val spot = nearestWalkable(keyX(anchor) + rng.nextInt(-2, 3), keyY(anchor) + rng.nextInt(-1, 2), 3) ?: anchor
        return GuardJob(keyX(spot), keyY(spot), order.kind == OrderKind.AMBUSH)
    }

    private fun militaryAnchor(ant: Ant, order: MilitaryOrder): Long {
        val defMark = pheromones.nearest(PheromoneType.DEFENSE, ant.cellX, ant.cellY)
        if (defMark != null && order.kind == OrderKind.DEFEND) return nearestWalkable(defMark.x, defMark.y, 4) ?: cellKey(defMark.x, defMark.y)
        return when (order.kind) {
            OrderKind.GUARD, OrderKind.RALLY, OrderKind.AMBUSH -> nearestWalkable(order.x, order.y, 4) ?: cellKey(order.x, order.y)
            else -> {
                val room = (if (ant.type == AntType.DEFENDER) rooms.firstOrNull { it.type == RoomType.DEFENSE_ROOM && it.functional } else null)
                    ?: rooms.firstOrNull { it.type == RoomType.DEFENSE_ROOM && it.functional }
                    ?: rooms.firstOrNull { it.type == RoomType.BARRACKS && it.functional }
                    ?: rooms.first { it.type == RoomType.QUEEN_CHAMBER }
                floorCell(room)
            }
        }
    }

    fun nearestWalkable(x: Int, y: Int, r: Int): Long? {
        if (world.isWalkable(x, y)) return cellKey(x, y)
        for (d in 1..r) for (dy in -d..d) for (dx in -d..d) {
            if (kotlin.math.abs(dx) != d && kotlin.math.abs(dy) != d) continue
            if (world.isWalkable(x + dx, y + dy)) return cellKey(x + dx, y + dy)
        }
        return null
    }

    fun nearestHostile(x: Float, y: Float, radius: Float): Creature? {
        var best: Creature? = null
        var bd = radius * radius
        for (c in creatures) {
            if (!c.kind.hostile || c.hp <= 0f) continue
            val dx = c.x - x
            val dy = c.y - y
            val d = dx * dx + dy * dy
            if (d <= bd) { bd = d; best = c }
        }
        return best
    }

    fun setOrder(squad: Int, kind: OrderKind, x: Int = 0, y: Int = 0, targetId: Int = -1) {
        val o = orders[squad.coerceIn(0, 1)]
        o.kind = kind; o.x = x; o.y = y; o.targetId = targetId
        for (a in ants) if (a.type.military && a.squad == squad && a.job !is EatJob && a.job !is RestJob) { a.job?.cancel(this, a); a.job = null; a.clearPath(); a.ambushReady = false }
        addLog("Birlik ${if (squad == 0) "A" else "B"}: ${kind.title} emri verildi.")
    }

    /** "Destek gönder": diğer birlikten 3 askeri bu birliğe aktarır. */
    fun sendSupport(squad: Int): Int {
        val other = 1 - squad
        val moved = ants.filter { it.type.military && it.squad == other && it.type != AntType.ROYAL_GUARD }.take(3)
        moved.forEach { it.squad = squad; it.job?.cancel(this, it); it.job = null; it.clearPath() }
        return moved.size
    }

    fun squadSize(squad: Int) = ants.count { it.type.military && it.squad == squad }

    // ================================================================ Hareket

    fun moveAnt(ant: Ant, tx: Int, ty: Int, dt: Float): MoveResult {
        val target = cellKey(tx, ty)
        val cx = tx + 0.5f
        val cy = ty + 0.5f
        var speed = ant.type.speed * mods.speedMult
        val w = environment.waterAt(ant.cellX, ant.cellY)
        if (w > 0.3f) speed *= 1f - 0.5f * w
        if (ant.carryingCorpse) speed *= 0.8f
        if (ant.cellX == tx && ant.cellY == ty && ant.path == null) {
            val dx = cx - ant.x
            val dy = cy - ant.y
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            val stepLen = speed * dt
            if (d <= stepLen || d < 0.05f) { ant.x = cx; ant.y = cy; return MoveResult.ARRIVED }
            ant.x += dx / d * stepLen; ant.y += dy / d * stepLen
            ant.walkPhase += dt * 12f
            return MoveResult.MOVING
        }
        if (ant.path == null || ant.pathTarget != target) {
            if (pathBudget <= 0) return MoveResult.WAITING
            pathBudget--
            val p = pathfinder.find(ant.cellX, ant.cellY, tx, ty) ?: run { ant.clearPath(); return MoveResult.UNREACHABLE }
            ant.path = p; ant.pathIndex = 0; ant.pathTarget = target
        }
        val path = ant.path!!
        var remaining = speed * dt * trafficFactor(ant)
        if (ant.starving) remaining *= 0.6f
        while (remaining > 0f && ant.pathIndex * 2 < path.size) {
            val nx = path[ant.pathIndex * 2]
            val ny = path[ant.pathIndex * 2 + 1]
            if (!world.isWalkable(nx, ny)) { ant.clearPath(); return MoveResult.WAITING }
            val px = nx + 0.5f
            val py = ny + 0.5f
            val dx = px - ant.x
            val dy = py - ant.y
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            if (d > 0.001f) ant.facing = kotlin.math.atan2(dy, dx)
            if (d <= remaining) { ant.x = px; ant.y = py; remaining -= d; ant.pathIndex++ } else { ant.x += dx / d * remaining; ant.y += dy / d * remaining; remaining = 0f }
        }
        ant.walkPhase += dt * 12f
        if (ant.pathIndex * 2 >= path.size) { ant.clearPath(); return if (ant.cellX == tx && ant.cellY == ty) MoveResult.ARRIVED else MoveResult.MOVING }
        return MoveResult.MOVING
    }

    private fun trafficFactor(ant: Ant): Float {
        val path = ant.path ?: return 1f
        if (ant.pathIndex * 2 >= path.size) return 1f
        val nx = path[ant.pathIndex * 2]
        val ny = path[ant.pathIndex * 2 + 1]
        if (ny < 0 || roomCells.containsKey(cellKey(nx, ny))) return 1f
        val n = occupancy[cellKey(nx, ny)] ?: 0
        return if (n <= 1) 1f else 1f / (1f + 0.35f * mods.trafficMult * (n - 1))
    }

    fun trafficAt(x: Int, y: Int): Int = occupancy[cellKey(x, y)] ?: 0

    private fun moveCreature(c: Creature, tx: Int, ty: Int, dt: Float): MoveResult {
        val target = cellKey(tx, ty)
        if (c.cellX == tx && c.cellY == ty && c.path == null) return MoveResult.ARRIVED
        if (c.path == null || c.pathTarget != target) {
            if (pathBudget <= -4) return MoveResult.WAITING
            pathBudget--
            val p = pathfinder.find(c.cellX, c.cellY, tx, ty, 9000, colony = false) ?: run { c.clearPath(); return MoveResult.UNREACHABLE }
            c.path = p; c.pathIndex = 0; c.pathTarget = target
        }
        val path = c.path!!
        var speed = c.kind.speed * (if (c.slowed) 0.5f else 1f)
        val w = environment.waterAt(c.cellX, c.cellY)
        if (w > 0.3f) speed *= 1f - 0.5f * w
        var remaining = speed * dt
        while (remaining > 0f && c.pathIndex * 2 < path.size) {
            val nx = path[c.pathIndex * 2]
            val ny = path[c.pathIndex * 2 + 1]
            if (!world.isWalkable(nx, ny)) { c.clearPath(); return MoveResult.WAITING }
            val dx = nx + 0.5f - c.x
            val dy = ny + 0.5f - c.y
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            if (d > 0.001f) c.facing = kotlin.math.atan2(dy, dx)
            if (d <= remaining) { c.x = nx + 0.5f; c.y = ny + 0.5f; remaining -= d; c.pathIndex++ } else { c.x += dx / d * remaining; c.y += dy / d * remaining; remaining = 0f }
        }
        c.walkPhase += dt * 10f
        if (c.pathIndex * 2 >= path.size) { c.clearPath(); return MoveResult.ARRIVED }
        return MoveResult.MOVING
    }

    // ================================================================ Canlılar ve savaş

    fun spawnCreature(kind: CreatureKind, x: Int, y: Int, rivalId: Int = -1): Creature {
        val c = Creature(newId(), kind, x + 0.5f, y + 0.5f, rivalId)
        creatures.add(c)
        expeditions.discoveredCreatures.add(kind)
        return c
    }

    fun targetForRaider(c: Creature) {
        val goal = if (c.kind == CreatureKind.TERMITE) storageRoomFor(StorageKind.MATERIAL, 0, 0) else storageRoomFor(StorageKind.FOOD, 0, 0)
        goal?.let { c.goalX = it.anchorX; c.goalY = it.anchorY; c.hasGoal = true }
    }

    private fun updateCreatures(dt: Float) {
        if (creatures.isEmpty()) return
        val drought = environment.weather == Weather.DROUGHT
        for (c in creatures.toTypedArray()) {
            c.prevX = c.x; c.prevY = c.y
            c.lifetime += dt
            if (c.attackCooldown > 0f) c.attackCooldown -= dt
            c.slowed = false
            val room = roomAt(c.cellX, c.cellY)
            if (c.kind.hostile && room != null && room.type == RoomType.TRAP_ROOM && room.functional) {
                c.slowed = true
                c.hp -= 4f * room.level * dt
            }
            if (environment.waterAt(c.cellX, c.cellY) > 0.85f && c.kind != CreatureKind.WORM && c.kind != CreatureKind.CAVE_SALAMANDER) c.hp -= 0.5f * dt
            if (c.hp <= 0f) { killCreature(c, null); continue }
            if (!c.kind.hostile) { passiveCreature(c, dt); continue }
            val detect = c.kind.detect * (if (drought && c.kind == CreatureKind.SPIDER) 1.6f else 1f)
            val prey = nearestAnt(c.x, c.y, if (c.retreating) 1.2f else detect)
            if (prey != null) {
                val dx = prey.x - c.x
                val dy = prey.y - c.y
                if (dx * dx + dy * dy <= 1.3f) { creatureAttacks(c, prey); continue }
                c.thinkTimer -= dt
                if (c.thinkTimer <= 0f) { c.thinkTimer = 1.2f; c.clearPath() }
                if (moveCreature(c, prey.cellX, prey.cellY, dt) == MoveResult.UNREACHABLE) c.stuckTime += dt
                continue
            }
            if (c.hasGoal) {
                val res = moveCreature(c, c.goalX, c.goalY, dt)
                if (res == MoveResult.UNREACHABLE) c.stuckTime += dt else if (res == MoveResult.MOVING) c.stuckTime = 0f
                if (res == MoveResult.ARRIVED) {
                    if (c.retreating) { despawnRaider(c); continue }
                    // Yağma
                    val want = if (c.kind == CreatureKind.TERMITE) listOf(ResourceType.WOOD) else ResourceType.entries.filter { it.edible }
                    var stolen = 0
                    for (t in want) stolen += resources.take(t, 3 - stolen).also { if (stolen >= 3) return@also }
                    c.stolen += stolen
                    if (stolen > 0) notify("${c.kind.title} depodan $stolen birim çaldı!")
                    beginRetreat(c)
                }
                if (c.stuckTime > 60f) { c.stuckTime = 0f; if (c.retreating) despawnRaider(c) else beginRetreat(c) }
            } else wanderCreature(c, dt)
            if (c.lifetime > 900f && c.rivalId < 0) creatures.remove(c)
        }
    }

    private fun beginRetreat(c: Creature) {
        c.retreating = true
        val r = rivalSystem.byId(c.rivalId)
        c.goalX = r?.let { if (it.x > 0) minOf(it.x, 60) else maxOf(it.x, -60) } ?: (if (c.x > 0) 60 else -60)
        c.goalY = -1
        c.hasGoal = true
        c.clearPath()
    }

    private fun despawnRaider(c: Creature) {
        creatures.remove(c)
        rivalSystem.raiderReturned(rivalSystem.byId(c.rivalId), c.stolen)
    }

    private fun passiveCreature(c: Creature, dt: Float) {
        wanderCreature(c, dt)
        if (c.kind == CreatureKind.WORM && c.lifetime > 300f) creatures.remove(c)
    }

    private fun wanderCreature(c: Creature, dt: Float) {
        c.thinkTimer -= dt
        if (c.thinkTimer <= 0f || !c.hasGoal) {
            c.thinkTimer = 4f + rng.nextFloat() * 4f
            val n = nearestWalkable(c.cellX + rng.nextInt(-5, 6), c.cellY + rng.nextInt(-3, 4), 2)
            if (n != null) { c.goalX = keyX(n); c.goalY = keyY(n); c.hasGoal = c.rivalId >= 0; c.clearPath() }
            if (c.rivalId < 0) { c.hasGoal = false; moveCreature(c, keyX(n ?: cellKey(c.cellX, c.cellY)), keyY(n ?: cellKey(c.cellX, c.cellY)), dt); return }
        }
        if (c.pathTarget != Long.MIN_VALUE) moveCreature(c, keyX(c.pathTarget), keyY(c.pathTarget), dt)
    }

    private fun nearestAnt(x: Float, y: Float, radius: Float): Ant? {
        var best: Ant? = null
        var bd = radius * radius
        for (a in ants) {
            val dx = a.x - x
            val dy = a.y - y
            val d = dx * dx + dy * dy
            if (d <= bd) { bd = d; best = a }
        }
        return best
    }

    private fun defenseBonus(ant: Ant): Float {
        var m = mods.defenseMult
        if (rooms.any { it.type == RoomType.DEFENSE_ROOM && it.functional && it.distanceTo(ant.cellX, ant.cellY) <= 3 }) m *= 1.3f
        if (rooms.any { it.type == RoomType.FORTRESS && it.functional }) m *= 1.15f
        return m
    }

    private fun attackBonus(): Float = mods.attackMult * (if (rooms.any { it.type == RoomType.FORTRESS && it.functional }) 1.15f else 1f)

    fun antAttacks(ant: Ant, c: Creature) {
        if (ant.attackCooldown > 0f || c.hp <= 0f) return
        ant.attackCooldown = 1f
        var atk = ant.type.attack * attackBonus() * (1f + ant.level * 0.05f)
        if (ant.ambushReady) { atk *= 2f; ant.ambushReady = false }
        val dmg = maxOf(0.5f, atk * (0.8f + rng.nextFloat() * 0.4f) - c.kind.defense)
        c.hp -= dmg
        c.lastHit = time
        if (time - lastHitSound > 0.4) { lastHitSound = time; sound(SoundCue.HIT) }
        if (c.hp <= 0f) { ant.xp += 3; killCreature(c, ant) }
    }

    private fun creatureAttacks(c: Creature, ant: Ant) {
        if (c.attackCooldown > 0f) return
        c.attackCooldown = 1f
        val def = ant.type.defense * defenseBonus(ant)
        val dmg = maxOf(0.5f, c.kind.attack * c.attackMult * (0.8f + rng.nextFloat() * 0.4f) - def)
        ant.hp -= dmg
        ant.lastHit = time
        if (ant.type == AntType.QUEEN) {
            if (ant.hp < 20f) { ant.hp = 20f; notify("Kraliçe saldırı altında! Askerleri Kraliçe Odasına gönderin.") }
            return
        }
        if (ant.hp <= 0f) killAnt(ant, "${c.kind.title.lowercaseTr()} saldırısında")
    }

    fun killCreature(c: Creature, killer: Ant?) {
        if (!creatures.remove(c)) return
        for ((t, n) in c.kind.loot) addPile(c.cellX, maxOf(c.cellY, -1), t, n)
        if (rng.nextFloat() < c.kind.geneticChance) addPile(c.cellX, maxOf(c.cellY, -1), ResourceType.GENETIC, 1)
        if (c.cellY >= 0) corpses.add(Corpse(newId(), c.cellX, c.cellY, c.kind.title))
        if (c.kind.hostile) {
            stats.enemiesKilled++
            coins += 1
            if (killer != null) notify("${c.kind.title} etkisiz hâle getirildi")
        }
        val r = rivalSystem.byId(c.rivalId)
        if (r != null) {
            relationChange(r, -2)
            if (creatures.none { it.rivalId == r.id }) {
                stats.raidsRepelled++
                coins += 3
                addLog("${r.name} baskını püskürtüldü!")
                notify("Baskın püskürtüldü!")
                sound(SoundCue.VICTORY)
            }
        }
    }

    fun relationChange(r: RivalColony, delta: Int) { r.relation = (r.relation + delta).coerceIn(-100, 100) }

    /** Rakip yuvaya kuşatma (2 saniyede bir tur). */
    fun siege(ant: Ant, r: RivalColony, dt: Float) {
        if (time - r.lastSiege < 2.0) return
        r.lastSiege = time
        r.discovered = true
        val attackers = ants.filter { (it.job as? MarchJob)?.rivalId == r.id && it.cellY == -1 && kotlin.math.abs(it.cellX - r.x) <= 2 }
        if (attackers.isEmpty()) return
        val atk = attackers.sumOf { (it.type.attack * attackBonus()).toDouble() }.toFloat()
        val def = r.soldiers * 4f * r.personality.defense + r.defense * 0.3f + 2f
        val kills = (atk / 14f * (0.6f + rng.nextFloat() * 0.8f)).toInt()
        r.soldiers = (r.soldiers - kills).coerceAtLeast(0)
        if (r.soldiers == 0) r.defense = (r.defense - atk * 0.3f).coerceAtLeast(0f)
        for (a in attackers) {
            val dmg = (def * 0.12f / attackers.size * (0.6f + rng.nextFloat() * 0.8f) - a.type.defense * defenseBonus(a) * 0.2f).coerceAtLeast(0.3f)
            a.hp -= dmg
            a.lastHit = time
            if (a.hp <= 0f) killAnt(a, "${r.name} kuşatmasında")
        }
        if (r.treaty != Treaty.WAR) { r.treaty = Treaty.WAR; relationChange(r, -30) }
        if (r.soldiers == 0 && r.defense <= 0f) {
            val n = attackers.size
            val food = addCapped(ResourceType.FOOD, minOf(r.food.toInt(), 8 * n))
            r.food = (r.food - food).coerceAtLeast(0f)
            val gen = addCapped(ResourceType.GENETIC, 1 + rng.nextInt(2))
            addCapped(ResourceType.MINERAL, 3 + rng.nextInt(4))
            r.population = (r.population * 0.55f).toInt()
            r.defense = 5f
            r.defeats++
            relationChange(r, -40)
            stats.rivalsDefeated++
            coins += 10
            addLog("${r.name} yenildi! Ganimet: $food yiyecek, $gen genetik örnek.")
            notify("Zafer! ${r.name} yenildi.")
            sound(SoundCue.VICTORY)
            if (r.population < 4) {
                r.alive = false
                r.respawnAt = time + DAY_LENGTH * 3
                addLog("${r.name} dağıldı; bölgesi boşaldı.")
            }
            for (s in 0..1) if (orders[s].kind == OrderKind.RAID_RIVAL && orders[s].targetId == r.id) setOrder(s, OrderKind.DEFEND)
        }
    }

    /** Ortam canlıları: derin tünellerde avcılar, yağmurda solucanlar. */
    private fun ambientCreatures() {
        if (creatures.size >= MAX_CREATURES || season == Season.WINTER) return
        if (environment.weather == Weather.RAIN || environment.weather == Weather.STORM) {
            if (rng.nextFloat() < 0.35f) randomTunnelCell(1, 8)?.let { spawnCreature(CreatureKind.WORM, keyX(it), keyY(it)) }
        }
        if (stats.maxDepth > 30 && rng.nextFloat() < 0.12f) {
            randomTunnelCell(30, 999, farFromQueen = 15)?.let { spawnCreature(if (keyY(it) > 55) CreatureKind.CAVE_HUNTER else CreatureKind.CENTIPEDE, keyX(it), keyY(it)) }
        }
    }

    private fun randomTunnelCell(minY: Int, maxY: Int, farFromQueen: Int = 0): Long? {
        val q = rooms.first { it.type == RoomType.QUEEN_CHAMBER }
        val cells = ArrayList<Long>()
        for (c in world.chunksToSave()) {
            if (!c.modified) continue
            for (i in c.cells.indices) {
                if (Terrain.of(c.cells[i].toInt()) != Terrain.TUNNEL) continue
                val x = c.cx * CHUNK_SIZE + i % CHUNK_SIZE
                val y = c.cy * CHUNK_SIZE + i / CHUNK_SIZE
                if (y < minY || y > maxY || roomCells.containsKey(cellKey(x, y))) continue
                if (manhattan(x, y, q.anchorX, q.anchorY) < farFromQueen) continue
                cells.add(cellKey(x, y))
            }
        }
        return cells.randomOrNull(rng)
    }

    fun migration() {
        val rainy = environment.weather == Weather.RAIN || environment.weather == Weather.STORM
        when {
            rainy -> { repeat(3) { randomTunnelCell(0, 10)?.let { spawnCreature(CreatureKind.WORM, keyX(it), keyY(it)) } }; notify("Solucanlar tünellere girdi: kolay av!") }
            rng.nextBoolean() -> {
                val side = if (rng.nextBoolean()) 1 else -1
                repeat(3) { i -> spawnCreature(CreatureKind.TERMITE, side * (30 + i), -1).also { targetForRaider(it) } }
                notify("Termit sürüsü odun deposuna yöneliyor!")
            }
            else -> {
                val cell = randomTunnelCell(6, 999, farFromQueen = 12)
                if (cell != null) { spawnCreature(if (rng.nextBoolean()) CreatureKind.SPIDER else CreatureKind.CENTIPEDE, keyX(cell), keyY(cell)); notify("Bir avcı tünellere göç etti!") }
                else repeat(2) { randomTunnelCell(0, 20)?.let { c -> spawnCreature(CreatureKind.BEETLE_LARVA, keyX(c), keyY(c)) } }
            }
        }
        addLog("Canlı göçü yaşandı.")
    }

    fun rareFind() {
        // Kazılmış alanın yakınında gizli bir hücreyi nadir damara çevirip görünür yapar.
        val base = randomTunnelCell(8, 999) ?: randomTunnelCell(0, 999)
        if (base == null) { addLog("Keşifçiler yüzeyde parlak taşlar buldu."); addCapped(ResourceType.MINERAL, 4); return }
        for (attempt in 0 until 30) {
            val x = keyX(base) + rng.nextInt(-4, 5)
            val y = keyY(base) + rng.nextInt(-3, 4)
            if (y < 2 || world.isWalkable(x, y) || roomCells.containsKey(cellKey(x, y))) continue
            world.set(x, y, if (y > 25) Terrain.RARE_VEIN else Terrain.MINERAL)
            world.reveal(x, y, 1)
            notify("Nadir kaynak keşfi: yakınlarda parlak bir damar bulundu!")
            addLog("Nadir kaynak keşfedildi (x:$x, derinlik:$y).")
            return
        }
    }

    // ================================================================ Keşif

    fun exploreSurface(x: Int) {
        if (x > knownRight) knownRight = x
        if (x < knownLeft) knownLeft = x
        for (r in rivalSystem.rivals) {
            if (!r.discovered && r.alive && kotlin.math.abs(r.x - x) < 25) {
                r.discovered = true
                addLog("Keşifçiler ${r.name} yuvasını buldu (${r.personality.title}).")
                notify("Yeni rakip keşfedildi: ${r.name}")
            }
        }
    }

    fun explored(ant: Ant, x: Int, y: Int) {
        if (y >= 0) world.reveal(x, y, 6 + mods.revealBonus) else exploreSurface(x)
        ant.xp += 2
    }

    // ================================================================ Kazı ve dolgu

    fun hasWalkableNeighbor(x: Int, y: Int): Boolean = DIRS4.any { world.isWalkable(x + it[0], y + it[1]) }

    private fun hasWalkableNeighborExcept(x: Int, y: Int): Boolean = DIRS4.any { world.isWalkable(x + it[0], y + it[1]) }

    fun bestStandCell(x: Int, y: Int, fromX: Int, fromY: Int): Long? {
        var best: Long? = null
        var bestD = Int.MAX_VALUE
        for (d in DIRS4) {
            val nx = x + d[0]
            val ny = y + d[1]
            if (!world.isWalkable(nx, ny)) continue
            val dd = manhattan(nx, ny, fromX, fromY)
            if (dd < bestD) { bestD = dd; best = cellKey(nx, ny) }
        }
        return best
    }

    fun isOrderActive(o: DigOrder): Boolean = digOrders[cellKey(o.x, o.y)] === o && !world.isWalkable(o.x, o.y)

    fun designateDig(x: Int, y: Int): DigDesignation {
        if (y < 0) return DigDesignation.SURFACE
        val key = cellKey(x, y)
        if (digOrders.containsKey(key)) return DigDesignation.ALREADY
        val t = world.get(x, y)
        if (!t.diggable) return DigDesignation.NOT_DIGGABLE
        if (world.isRevealed(x, y) && t.needsHardRockTech && !mods.hardRock && ants.none { it.type == AntType.TUNNELER }) return DigDesignation.NEEDS_TECH
        fillOrders.remove(key)
        digOrders[key] = DigOrder(x, y)
        return DigDesignation.OK
    }

    fun cancelDig(x: Int, y: Int): Boolean {
        val o = digOrders[cellKey(x, y)] ?: return fillOrders.remove(cellKey(x, y)) != null
        if (o.roomId != -1) return false
        digOrders.remove(cellKey(x, y))
        return true
    }

    /** "Tüneli kapat": oda dışındaki yürünebilir hücreyi doldurma emri. */
    fun designateFill(x: Int, y: Int): String? {
        if (y < 0) return "Yüzey doldurulamaz"
        if (!world.isWalkable(x, y)) return "Burası zaten dolu"
        if (roomCells.containsKey(cellKey(x, y))) return "Oda hücreleri doldurulamaz"
        if (fillOrders.containsKey(cellKey(x, y))) return null
        fillOrders[cellKey(x, y)] = FillOrder(x, y)
        return null
    }

    fun completeFill(order: FillOrder) {
        fillOrders.remove(cellKey(order.x, order.y))
        if (!world.isWalkable(order.x, order.y)) return
        world.set(order.x, order.y, Terrain.FILL)
        environment.water.remove(cellKey(order.x, order.y))
        piles.remove(cellKey(order.x, order.y))?.let { addPile(order.x, order.y - 1, it.type, it.amount) }
        for (a in ants) if (a.cellX == order.x && a.cellY == order.y) pushOut(a)
        for (c in creatures) if (c.cellX == order.x && c.cellY == order.y) { nearestWalkable(order.x, order.y, 2)?.let { k -> c.x = keyX(k) + 0.5f; c.y = keyY(k) + 0.5f }; c.clearPath() }
        if (order.y == 0) entrances.remove(cellKey(order.x, order.y))
        addLog("Tünel kapatıldı (x:${order.x}, derinlik:${order.y}).")
    }

    private fun pushOut(a: Ant) {
        val n = DIRS4.firstOrNull { world.isWalkable(a.cellX + it[0], a.cellY + it[1]) }
        if (n != null) { a.x = a.cellX + n[0] + 0.5f; a.y = a.cellY + n[1] + 0.5f } else { a.x = a.cellX + 0.5f; a.y = a.cellY - 0.5f }
        a.clearPath()
    }

    fun completeDig(order: DigOrder, ant: Ant) {
        val t = world.get(order.x, order.y)
        world.set(order.x, order.y, Terrain.TUNNEL)
        world.reveal(order.x, order.y, 3 + mods.revealBonus)
        digOrders.remove(cellKey(order.x, order.y))
        stats.cellsDug++
        if (order.y > stats.maxDepth) stats.maxDepth = order.y
        if (order.y == 0) entrances.add(cellKey(order.x, order.y))
        if (DIRS4.any { world.get(order.x + it[0], order.y + it[1]) == Terrain.WATER_SOIL }) seeps.add(cellKey(order.x, order.y))
        sound(SoundCue.DIG)
        val type = t.yieldType
        var amount = t.yieldAmount * (if (t == Terrain.FOSSIL) mods.fossilMult else 1)
        when (t) {
            Terrain.ANCIENT_NEST -> {
                val roll = rng.nextFloat()
                val loot = when { roll < 0.4f -> ResourceType.FOOD; roll < 0.7f -> ResourceType.SEED; roll < 0.88f -> ResourceType.FOSSIL; else -> ResourceType.GENETIC }
                addPile(order.x, order.y, loot, if (loot.edible) 6 else 1)
                notify("Eski yuva kalıntısında ${loot.title.lowercaseTr()} bulundu")
            }
            Terrain.CREATURE_DEN -> {
                val kind = when { order.y > 55 -> CreatureKind.CAVE_HUNTER; rng.nextBoolean() -> CreatureKind.SPIDER; else -> CreatureKind.CENTIPEDE }
                spawnCreature(kind, order.x, order.y)
                notify("Bir ${kind.title.lowercaseTr()} yuvası açıldı! Askerleri gönderin.")
                addLog("Canlı yuvası açıldı: ${kind.title}.")
                sound(SoundCue.ALERT)
            }
            Terrain.WATER_POCKET -> {
                environment.addWater(order.x, order.y, 1f)
                for (d in DIRS4) if (world.isWalkable(order.x + d[0], order.y + d[1])) environment.addWater(order.x + d[0], order.y + d[1], 0.8f)
                for (dx in -2..2) for (dy in -2..2) if (world.get(order.x + dx, order.y + dy) == Terrain.WATER_POCKET && rng.nextFloat() < 0.4f) world.set(order.x + dx, order.y + dy, Terrain.TUNNEL).also { environment.addWater(order.x + dx, order.y + dy, 1f) }
                if (rng.nextFloat() < 0.25f) spawnCreature(CreatureKind.CAVE_SALAMANDER, order.x, order.y)
                notify("Yeraltı gölü delindi: su tünellere boşalıyor!")
            }
            Terrain.FOSSIL -> { addLog("Fosil bulundu! (${order.x}, ${order.y})"); notify("Fosil bulundu!") }
            Terrain.RARE_VEIN -> notify("Nadir mineral çıkarıldı!")
            Terrain.SOFT_SOIL -> if (rng.nextFloat() < 0.02f) spawnCreature(CreatureKind.BEETLE_LARVA, order.x, order.y)
            else -> {}
        }
        if (DIRS4.any { world.get(order.x + it[0], order.y + it[1]) == Terrain.RIVAL_TUNNEL }) {
            val r = rivalSystem.rivals.minByOrNull { kotlin.math.abs(it.x - order.x) }
            r?.discovered = true
            notify("Rakip tünellerine bağlandınız! Bağlantıyı Doldur aracıyla kapatabilirsiniz.")
            addLog("Kazı ${r?.name ?: "rakip"} tünellerine ulaştı.")
        }
        if (type != null && amount > 0 && t != Terrain.ANCIENT_NEST && t != Terrain.CREATURE_DEN) {
            val carry = minOf(ant.carryCapacity(mods), amount)
            ant.carryType = type
            ant.carryAmount = carry
            amount -= carry
            if (amount > 0) addPile(order.x, order.y, type, amount)
        }
        for (dx in -6..6) for (dy in -1..0) if (world.isWalkable(order.x + dx, order.y + dy)) riskyCells.add(cellKey(order.x + dx, order.y + dy))
        if (order.roomId != -1) roomById(order.roomId)?.let { checkExcavation(it) }
    }

    fun collapseRisk(x: Int, y: Int, assumeOpenX: Int? = null, assumeOpenY: Int? = null): Float {
        if (y < 1) return 0f
        if (roomAt(x, y) != null) return 0f
        val limit = when (world.get(x, y - 1)) {
            Terrain.SAND -> 2
            Terrain.SOFT_SOIL, Terrain.RUBBLE -> 3
            else -> return 0f
        }
        fun openCell(xx: Int, yy: Int) = (xx == assumeOpenX && yy == (assumeOpenY ?: y)) || world.isWalkable(xx, yy)
        fun column(xx: Int) = openCell(xx, y) && openCell(xx, y + 1) && roomAt(xx, y) == null && roomAt(xx, y + 1) == null
        if (!column(x)) return 0f
        var span = 1
        var i = x - 1
        while (span < 14 && column(i)) { span++; i-- }
        i = x + 1
        while (span < 14 && column(i)) { span++; i++ }
        return if (span > limit) ((span - limit) * 0.25f * mods.collapseMult * difficulty.collapseMult).coerceIn(0f, 1f) else 0f
    }

    fun collapseCandidates(): List<Long> = riskyCells.filter { collapseRisk(keyX(it), keyY(it)) > 0f }

    private fun checkCollapses() {
        if (riskyCells.isEmpty()) return
        val it = riskyCells.iterator()
        val toCollapse = ArrayList<Long>()
        while (it.hasNext()) {
            val k = it.next()
            val r = collapseRisk(keyX(k), keyY(k))
            if (r <= 0f) { it.remove(); continue }
            if (rng.nextFloat() < 0.05f * r) toCollapse.add(k)
        }
        for (k in toCollapse.take(1)) collapse(keyX(k), keyY(k))
    }

    fun collapse(x: Int, y: Int) {
        world.set(x, y, Terrain.RUBBLE)
        riskyCells.remove(cellKey(x, y))
        piles.remove(cellKey(x, y))
        environment.water.remove(cellKey(x, y))
        stats.collapses++
        for (a in ants.toTypedArray()) {
            if (a.cellX == x && a.cellY == y) {
                a.hp -= 8f
                pushOut(a)
                if (a.hp <= 0f) killAnt(a, "çökme altında")
            }
        }
        digOrders[cellKey(x, y)] = DigOrder(x, y)
        addLog("Tünel çöktü (x:$x, derinlik:$y). Moloz kazı listesine eklendi.")
        notify("Tünel çöktü! Geniş boşlukları odalarla destekleyin.")
        sound(SoundCue.ALERT)
    }

    // ================================================================ Su kaynakları

    fun entranceCells(): Set<Long> = entrances
    fun seepCells(): Set<Long> = seeps

    private fun rebuildWaterSources() {
        entrances.clear(); seeps.clear()
        for (c in world.chunksToSave()) {
            if (!c.modified) continue
            for (i in c.cells.indices) {
                val t = Terrain.of(c.cells[i].toInt())
                if (!t.walkable || t == Terrain.RIVAL_TUNNEL) continue
                val x = c.cx * CHUNK_SIZE + i % CHUNK_SIZE
                val y = c.cy * CHUNK_SIZE + i / CHUNK_SIZE
                if (y == 0) entrances.add(cellKey(x, y))
                if (DIRS4.any { world.get(x + it[0], y + it[1]) == Terrain.WATER_SOIL }) seeps.add(cellKey(x, y))
            }
        }
    }

    // ================================================================ Kaynak ve depo

    fun capacityOf(kind: StorageKind): Int {
        var cap = when (kind) { StorageKind.FOOD -> BASE_FOOD_CAPACITY; StorageKind.MATERIAL -> BASE_MATERIAL_CAPACITY; StorageKind.WATER -> BASE_WATER_CAPACITY }.toFloat()
        for (r in rooms) {
            if (!r.functional) continue
            val m = (if (r.maintained) 1f else 0.5f) * (1f - floodPenalty(r))
            cap += when {
                kind == StorageKind.FOOD && r.type == RoomType.FOOD_STORAGE -> 60 * r.level * m
                kind == StorageKind.MATERIAL && r.type == RoomType.MATERIAL_STORAGE -> 80 * r.level * m
                kind == StorageKind.WATER && r.type == RoomType.WATER_STORAGE -> 60 * r.level * m
                else -> 0f
            }
        }
        return (cap * mods.storageMult).toInt()
    }

    fun freeSpace(kind: StorageKind): Int = capacityOf(kind) - resources.totalOf(kind)

    fun freeSpaceFor(type: ResourceType): Int {
        val free = freeSpace(type.storage)
        if (type != ResourceType.SOIL) return free
        return minOf(free, (capacityOf(StorageKind.MATERIAL) * 0.6f).toInt() - resources[ResourceType.SOIL])
    }

    fun addCapped(type: ResourceType, amount: Int): Int {
        val n = minOf(amount, freeSpaceFor(type).coerceAtLeast(0))
        resources.add(type, n)
        return n
    }

    fun storageRoomFor(kind: StorageKind, fromX: Int, fromY: Int): Room? {
        val type = when (kind) { StorageKind.FOOD -> RoomType.FOOD_STORAGE; StorageKind.MATERIAL -> RoomType.MATERIAL_STORAGE; StorageKind.WATER -> RoomType.WATER_STORAGE }
        return rooms.filter { it.type == type && it.functional }.minByOrNull { manhattan(fromX, fromY, it.anchorX, it.anchorY) }
            ?: rooms.firstOrNull { it.type == RoomType.QUEEN_CHAMBER }
    }

    fun floorCell(room: Room): Long = cellKey(room.x + rng.nextInt(room.width), room.anchorY)

    fun depositCarried(ant: Ant) {
        val type = ant.carryType ?: return
        val add = minOf(ant.carryAmount, freeSpaceFor(type))
        if (add > 0) {
            resources.add(type, add)
            ant.carryAmount -= add
            if (type.edible) stats.foodGathered += add else stats.materialsGathered += add
        }
        if (ant.carryAmount <= 0) { ant.carryAmount = 0; ant.carryType = null }
    }

    fun dumpCarriedSoil(ant: Ant) {
        stats.soilDumped += ant.carryAmount
        surfaceMound += ant.carryAmount
        ant.carryAmount = 0
        ant.carryType = null
    }

    fun dropCarried(ant: Ant) {
        val type = ant.carryType ?: return
        if (ant.carryAmount > 0) addPile(ant.cellX, ant.cellY, type, ant.carryAmount)
        ant.carryAmount = 0
        ant.carryType = null
    }

    fun addPile(x: Int, y: Int, type: ResourceType, amount: Int) {
        var px = x
        var py = y
        val existing = piles[cellKey(px, py)]
        if (existing != null && existing.type != type) {
            val n = DIRS4.firstOrNull { world.isWalkable(x + it[0], y + it[1]) && piles[cellKey(x + it[0], y + it[1])].let { p -> p == null || p.type == type } }
            if (n != null) { px = x + n[0]; py = y + n[1] }
        }
        val p = piles[cellKey(px, py)]
        if (p != null && p.type == type) p.amount += amount else if (p == null) piles[cellKey(px, py)] = Pile(px, py, type, amount)
    }

    fun pickUpPile(pile: Pile, ant: Ant) {
        if (piles[cellKey(pile.x, pile.y)] !== pile) return
        val take = minOf(ant.carryCapacity(mods), pile.amount)
        ant.carryType = pile.type
        ant.carryAmount = take
        pile.amount -= take
        pile.reservedBy = -1
        if (pile.amount <= 0) piles.remove(cellKey(pile.x, pile.y))
    }

    // ================================================================ Yüzey kaynakları

    fun addSource(x: Int, kind: SourceKind, amount: Int): FoodSource = FoodSource(newId(), x, kind, amount).also { it.spawnedAt = time; sources.add(it) }

    fun refreshSources() {
        sources.removeAll { !isKnown(it.x) && it.reserved == 0 && it.amount == it.maxAmount && time - it.spawnedAt > FAR_SOURCE_LIFETIME }
        val near = sources.filter { isKnown(it.x) }
        val nearEdible = near.count { it.kind.resource.edible }
        val chance = season.sourceMult * (if (environment.weather == Weather.DROUGHT) 0.5f else 1f).let { if (it < 1f) 1f - (1f - it) * mods.seasonPenaltyMult else it }
        if (rng.nextFloat() > chance) return
        if (nearEdible < 3) spawnRandomSource(far = false, edibleOnly = true)
        else if (near.size < MAX_NEAR_SOURCES + (knownMax() - knownMin()) / 30) spawnRandomSource(far = false)
        else if (sources.size < MAX_SOURCES + (knownMax() - knownMin()) / 20) spawnRandomSource(far = true)
    }

    fun spawnRandomSource(far: Boolean = rng.nextFloat() < 0.3f, edibleOnly: Boolean = false, bonus: Float = 1f) {
        val lo = knownMin()
        val hi = knownMax()
        repeat(12) {
            val x = if (far) (if (rng.nextBoolean()) hi + 1 + rng.nextInt(SOURCE_MARGIN) else lo - 1 - rng.nextInt(SOURCE_MARGIN)) else rng.nextInt(lo, hi + 1)
            if (kotlin.math.abs(x) < 4 || sources.any { kotlin.math.abs(it.x - x) < 3 }) return@repeat
            val biome = Biome.at(seed, x)
            val pool = biome.sources.filter { !edibleOnly || it.resource.edible }.ifEmpty { listOf(SourceKind.DEAD_INSECT, SourceKind.SEED_PILE) }
            val kind = pool[rng.nextInt(pool.size)]
            val seasonMult = season.sourceMult.let { if (it < 1f) 1f - (1f - it) * mods.seasonPenaltyMult else it }
            val drought = if (environment.weather == Weather.DROUGHT) 0.5f else 1f
            val amount = ((6 + rng.nextInt(10) + kotlin.math.abs(x) / 6) * seasonMult * drought * mods.sourceYieldMult * bonus).toInt().coerceAtLeast(3)
            addSource(x, kind, if (kind == SourceKind.RESIN_DROP || kind == SourceKind.PEBBLES) maxOf(2, amount / 3) else amount)
            return
        }
    }

    fun onSourceGathered(source: FoodSource) {
        if (source.amount <= 0 && source.reserved <= 0) sources.remove(source)
    }

    // ================================================================ Odalar

    fun roomById(id: Int): Room? = rooms.firstOrNull { it.id == id }

    fun roomAt(x: Int, y: Int): Room? = roomCells[cellKey(x, y)]?.let { roomById(it) }

    fun roomUnlocked(type: RoomType): Boolean = type.buildable && isUnlocked(type.requires)

    fun checkRoomPlacement(type: RoomType, x: Int, y: Int): Placement {
        if (!type.buildable) return Placement(false, "Bu oda inşa edilemez", 0)
        if (!isUnlocked(type.requires)) return Placement(false, "Araştırma gerekli: ${ResearchTree.get(type.requires!!)?.title}", 0)
        if (y < 1) return Placement(false, "Odalar yüzeyin en az 1 hücre altında olmalı", 0)
        var dig = 0
        var connected = false
        for (yy in y until y + type.height) for (xx in x until x + type.width) {
            if (roomCells.containsKey(cellKey(xx, yy))) return Placement(false, "Başka bir odayla çakışıyor", 0)
            val t = world.get(xx, yy)
            if (t.needsHardRockTech && !mods.hardRock) return Placement(false, "Alanda kaya var", 0)
            if (t == Terrain.RIVAL_TUNNEL) return Placement(false, "Rakip tüneli üzerine inşa edilemez", 0)
            if (t.walkable) connected = true else dig++
        }
        if (!connected) {
            for (yy in y - 1..y + type.height) for (xx in x - 1..x + type.width) {
                val edge = yy == y - 1 || yy == y + type.height || xx == x - 1 || xx == x + type.width
                if (!edge) continue
                if ((yy == y - 1 || yy == y + type.height) && (xx == x - 1 || xx == x + type.width)) continue
                if (world.isWalkable(xx, yy) || digOrders.containsKey(cellKey(xx, yy))) connected = true
            }
        }
        if (!connected) return Placement(false, "Bir tünele bağlı olmalı", dig)
        if (!resources.hasAll(type.cost)) return Placement(false, "Yetersiz kaynak", dig)
        return Placement(true, null, dig)
    }

    /**
     * Oda için iyi bir yer önerir: kraliçe odasına yakın, az kazı gerektiren, komşuluk bonusu alan geçerli bir alan.
     * Kaynak yetersizliği öneriyi engellemez (oyuncu yerini görebilsin diye).
     */
    fun suggestRoomPlacement(type: RoomType): Pair<Int, Int>? {
        val q = rooms.first { it.type == RoomType.QUEEN_CHAMBER }
        var best: Pair<Int, Int>? = null
        var bestScore = Float.MAX_VALUE
        val bonusTypes = type.adjacency.keys
        for (y in 1..34) for (x in -34..34) {
            val p = checkRoomPlacement(type, x, y)
            if (!p.ok && p.reason != "Yetersiz kaynak") continue
            var score = p.digCells * 1.2f + manhattan(x + type.width / 2, y, q.anchorX, q.anchorY) * 0.8f
            val probe = Room(-1, type, x, y)
            if (rooms.any { it.type in bonusTypes && it.isAdjacentTo(probe) }) score -= 25f
            if (rooms.any { it.isAdjacentTo(probe) }) score -= 4f
            var water = false
            probe.forEachCell { xx, yy -> if (environment.waterAt(xx, yy) > 0.1f || DIRS4.any { world.get(xx + it[0], yy + it[1]) == Terrain.WATER_POCKET }) water = true }
            if (water) score += 40f
            if (score < bestScore) { bestScore = score; best = x to y }
        }
        return best
    }

    fun placeRoom(type: RoomType, x: Int, y: Int): Room? {
        if (!checkRoomPlacement(type, x, y).ok) return null
        resources.pay(type.cost)
        val room = Room(newId(), type, x, y)
        rooms.add(room)
        room.forEachCell { xx, yy ->
            val key = cellKey(xx, yy)
            roomCells[key] = room.id
            digOrders.remove(key)
            fillOrders.remove(key)
            if (!world.isWalkable(xx, yy)) digOrders[key] = DigOrder(xx, yy, room.id)
        }
        checkExcavation(room)
        return room
    }

    private fun checkExcavation(room: Room) {
        if (room.state != RoomState.EXCAVATING) return
        var done = true
        room.forEachCell { xx, yy -> if (!world.isWalkable(xx, yy)) done = false }
        if (done) room.state = RoomState.CONSTRUCTING
    }

    fun completeConstruction(room: Room) {
        if (room.state == RoomState.UPGRADING) {
            room.level++
            addLog("${room.type.title} seviye ${room.level} oldu.")
            notify("${room.type.title} geliştirildi (Sv. ${room.level})")
        } else {
            stats.roomsBuilt++
            addLog("${room.type.title} tamamlandı.")
            notify("${room.type.title} tamamlandı")
        }
        room.state = RoomState.ACTIVE
        room.buildProgress = 0f
        room.forEachCell { xx, yy -> riskyCells.remove(cellKey(xx, yy)) }
        sound(SoundCue.BUILD)
    }

    fun canUpgrade(room: Room): Boolean = room.state == RoomState.ACTIVE && room.level < room.type.maxLevel && resources.hasAll(room.type.upgradeCost(room.level + 1))

    fun upgradeRoom(room: Room): Boolean {
        if (!canUpgrade(room)) return false
        resources.pay(room.type.upgradeCost(room.level + 1))
        room.state = RoomState.UPGRADING
        room.buildProgress = 0f
        return true
    }

    fun removeRoom(room: Room): Boolean {
        if (room.type == RoomType.QUEEN_CHAMBER) return false
        val active = room.functional
        resources.refund(room.type.cost, if (active) 0.5f else 1f)
        rooms.remove(room)
        room.forEachCell { xx, yy ->
            roomCells.remove(cellKey(xx, yy))
            val o = digOrders[cellKey(xx, yy)]
            if (o != null && o.roomId == room.id) digOrders.remove(cellKey(xx, yy))
        }
        val queenRoom = rooms.first { it.type == RoomType.QUEEN_CHAMBER }
        for (b in brood) if (b.roomId == room.id) b.roomId = queenRoom.id
        if (room.type == RoomType.FUNGUS_FARM && room.leafBuffer > 0) addPile(room.anchorX, room.anchorY, ResourceType.LEAF, room.leafBuffer)
        for (a in ants) {
            val j = a.job
            if ((j is BuildJob && j.room === room) || (j is SupplyFarmJob && j.farm === room) || (j is RestJob && j.room === room) || (j is StationJob && j.room === room)) {
                j.cancel(this, a); a.job = null; a.clearPath()
            }
        }
        for (kind in StorageKind.entries) {
            var over = -freeSpace(kind)
            if (over <= 0) continue
            for (t in ResourceType.entries.filter { it.storage == kind }) {
                if (over <= 0) break
                val n = resources.take(t, over)
                if (n > 0) addPile(queenRoom.anchorX, queenRoom.anchorY, t, n)
                over -= n
            }
        }
        addLog("${room.type.title} ${if (active) "yıkıldı" else "iptal edildi"}.")
        return true
    }

    fun floodPenalty(room: Room): Float = if (mods.waterproof) 0f else ((roomFlood[room.id] ?: 0f) * 0.7f).coerceAtMost(0.7f)

    fun roomFloodLevel(room: Room): Float = roomFlood[room.id] ?: 0f

    fun roomEfficiency(room: Room): Float {
        if (!room.functional) return 0f
        var bonus = 0f
        for ((t, b) in room.type.adjacency) if (rooms.any { it.type == t && it.functional && it.isAdjacentTo(room) }) bonus += b
        return (if (room.maintained) 1f else 0.5f) * (1f + bonus) * (1f - floodPenalty(room))
    }

    fun farmLeafCapacity(room: Room) = 10 * room.level

    fun farmRate(room: Room): Float = roomEfficiency(room) * room.level * mods.farmMult / FARM_CYCLE * 2f

    private fun updateRooms(dt: Float) {
        for (r in rooms) {
            if (r.type == RoomType.FUNGUS_FARM && r.functional && r.leafBuffer > 0) {
                val out = piles[cellKey(r.anchorX, r.anchorY)]
                if (out != null && out.amount >= 12) continue
                r.growProgress += dt * roomEfficiency(r) * r.level * mods.farmMult / FARM_CYCLE
                while (r.growProgress >= 1f && r.leafBuffer > 0) {
                    r.growProgress -= 1f
                    r.leafBuffer--
                    addPile(r.anchorX, r.anchorY, ResourceType.MUSHROOM, 2)
                    stats.produced += 2
                }
            }
        }
    }

    // ---------------------------------------------------------------- İstasyonlar

    fun stationHasWork(room: Room): Boolean = when (room.type) {
        RoomType.RESEARCH_LAB -> research.current != null
        RoomType.GENETICS_LAB -> genetics.current != null
        RoomType.WORKSHOP -> resources.hasAll(room.recipe.input) && room.recipe.output.all { (t, _) -> freeSpaceFor(t) > 0 }
        RoomType.ROYAL_CENTER -> resources[ResourceType.ENZYME] >= 2 && resources.edibleTotal() >= 10 && freeSpaceFor(ResourceType.ROYAL_ESSENCE) > 0
        RoomType.TRAINING_ROOM -> room.trainClass?.let { room.trainCount > 0 && canTrain(it) == null } ?: false
        else -> false
    }

    /** İstasyonda [amount] iş birimi çalışır. Yapılacak iş kalmadıysa false döner. */
    fun stationWork(room: Room, ant: Ant, amount: Float): Boolean {
        if (!stationHasWork(room)) return false
        val eff = roomEfficiency(room)
        when (room.type) {
            RoomType.RESEARCH_LAB -> {
                val def = research.currentDef() ?: return false
                val archive = 1f + rooms.filter { it.type == RoomType.ARCHIVE && it.functional }.sumOf { it.level } * 0.1f
                val boost = if (time < research.boostUntil) 1.5f else 1f
                research.progress += amount * eff * mods.researchMult * archive * boost
                if (research.progress >= def.work) completeResearch(def)
            }
            RoomType.GENETICS_LAB -> {
                val t = genetics.current ?: return false
                genetics.progress += amount * eff
                if (genetics.progress >= t.work) {
                    genetics.unlocked.add(t); genetics.current = null; genetics.progress = 0f
                    recomputeModifiers()
                    addLog("Genetik özellik geliştirildi: ${t.title}"); notify("Genetik özellik: ${t.title}"); sound(SoundCue.RESEARCH)
                }
            }
            RoomType.WORKSHOP -> {
                room.stationProgress += amount * eff
                if (room.stationProgress >= room.recipe.work) {
                    room.stationProgress = 0f
                    resources.pay(room.recipe.input)
                    for ((t, n) in room.recipe.output) addCapped(t, n)
                    stats.produced += room.recipe.output.values.sum()
                }
            }
            RoomType.ROYAL_CENTER -> {
                room.stationProgress += amount * eff
                if (room.stationProgress >= ROYAL_WORK) {
                    room.stationProgress = 0f
                    resources.take(ResourceType.ENZYME, 2)
                    var n = 5
                    while (n > 0) { val t = resources.bestEdible() ?: break; resources.take(t, 1); n-- }
                    addCapped(ResourceType.ROYAL_ESSENCE, 1)
                    stats.produced++
                    notify("Kraliyet özü üretildi")
                }
            }
            RoomType.TRAINING_ROOM -> {
                val cls = room.trainClass ?: return false
                if (ant.type != AntType.WORKER) return false // yalnızca sıradan işçiler eğitilir
                room.stationProgress += amount * eff
                if (room.stationProgress >= cls.trainTime) {
                    room.stationProgress = 0f
                    if (!resources.pay(cls.trainCost)) return false
                    ant.becomeType(cls)
                    if (cls.military) ant.squad = if (squadSize(0) <= squadSize(1)) 0 else 1
                    room.trainCount--
                    stats.antsTrained++
                    addLog("Bir işçi ${cls.title} olarak eğitildi.")
                    notify("Yeni ${cls.title.lowercaseTr()} hazır")
                    return false
                }
            }
            else -> return false
        }
        return true
    }

    /** Sınıf eğitimi için engel (null: eğitilebilir). */
    fun canTrain(cls: AntType): String? = when {
        !cls.trainable -> "Bu sınıf eğitilemez"
        !isUnlocked(cls.requires) -> "Araştırma gerekli: ${ResearchTree.get(cls.requires!!)?.title}"
        cls.requiresRoom != null && rooms.none { it.type == cls.requiresRoom && it.functional } -> "${cls.requiresRoom.title} gerekli"
        !resources.hasAll(cls.trainCost) -> "Yetersiz kaynak"
        else -> null
    }

    fun setTraining(room: Room, cls: AntType?, count: Int) {
        room.trainClass = cls
        room.trainCount = count.coerceIn(0, 20)
        room.stationProgress = 0f
    }

    // ---------------------------------------------------------------- Araştırma, evrim, genetik

    fun startResearch(def: ResearchDef): String? {
        if (research.current != null) return "Şu an başka bir araştırma sürüyor"
        if (!research.available(def)) return "Önce gerekli araştırmaları tamamlayın"
        if (rooms.none { it.type == RoomType.RESEARCH_LAB && it.functional }) return "Araştırma Laboratuvarı gerekli"
        if (!resources.pay(def.cost)) return "Yetersiz kaynak"
        research.current = def.id
        research.progress = 0f
        addLog("Araştırma başladı: ${def.title}")
        return null
    }

    fun cancelResearch() {
        val def = research.currentDef() ?: return
        resources.refund(def.cost, 0.5f)
        research.current = null
        research.progress = 0f
    }

    internal fun completeResearch(def: ResearchDef) {
        research.completed.add(def.id)
        research.current = null
        research.progress = 0f
        stats.researchDone++
        recomputeModifiers()
        addLog("Araştırma tamamlandı: ${def.title}")
        notify("Araştırma tamamlandı: ${def.title}")
        sound(SoundCue.RESEARCH)
    }

    fun startTrait(t: GeneticTrait): String? {
        if (genetics.current != null) return "Şu an başka bir özellik geliştiriliyor"
        if (t in genetics.unlocked) return "Zaten geliştirildi"
        if (rooms.none { it.type == RoomType.GENETICS_LAB && it.functional }) return "Genetik Araştırma Odası gerekli"
        if (resources.take(ResourceType.GENETIC, 0) < 0 || resources[ResourceType.GENETIC] < t.samples) return "${t.samples} genetik örnek gerekli"
        resources.take(ResourceType.GENETIC, t.samples)
        genetics.current = t
        genetics.progress = 0f
        return null
    }

    fun evolve(path: EvolutionPath): String? {
        if (evolution.points(reputation) <= 0) return "Evrim puanı yok (itibar kazanın)"
        val r = evolution.ranks[path.ordinal]
        if (r >= 5) return "Bu yol en üst kademede"
        if (r >= 2 && rooms.none { it.type == RoomType.ROYAL_CENTER && it.functional }) return "3. kademe ve sonrası için Kraliyet Merkezi gerekli"
        evolution.ranks[path.ordinal]++
        recomputeModifiers()
        addLog("Evrim: ${path.title} kademe ${r + 1}")
        sound(SoundCue.RESEARCH)
        return null
    }

    // ---------------------------------------------------------------- Diplomasi

    private fun tradeRoom() = rooms.any { it.type == RoomType.TRADE_ROOM && it.functional }

    fun gift(r: RivalColony): String? {
        if (!r.discovered) return "Koloni henüz keşfedilmedi"
        if (resources.edibleTotal() < 15) return "15 yiyecek gerekli"
        var n = 15
        while (n > 0) { val t = resources.bestEdible() ?: break; resources.take(t, 1); n-- }
        relationChange(r, 12)
        addLog("${r.name} kolonisine hediye gönderildi.")
        return null
    }

    fun proposeCeasefire(r: RivalColony): String? {
        if (!r.discovered) return "Koloni henüz keşfedilmedi"
        if (r.treaty != Treaty.WAR && r.relation >= -15) return "Zaten savaş hâlinde değilsiniz"
        val accept = r.relation > -60 && (r.strength < rivalSystem.playerPower(this) * 1.4f || rng.nextFloat() < 0.3f)
        if (!accept) { relationChange(r, -3); return "${r.name} ateşkesi reddetti" }
        r.treaty = Treaty.CEASEFIRE
        r.treatyUntil = time + DAY_LENGTH * 3
        r.relation = maxOf(r.relation, -10)
        addLog("${r.name} ile 3 günlük ateşkes imzalandı.")
        return null
    }

    fun proposeTrade(r: RivalColony): String? {
        if (!r.discovered) return "Koloni henüz keşfedilmedi"
        if (!tradeRoom()) return "Ticaret Odası gerekli"
        if (r.treaty == Treaty.WAR) return "Savaştaki koloniyle ticaret olmaz"
        if (r.relation < 10) return "İlişki en az 10 olmalı"
        r.treaty = Treaty.TRADE
        addLog("${r.name} ile ticaret anlaşması yapıldı.")
        return null
    }

    fun proposeAlliance(r: RivalColony): String? {
        if (r.treaty != Treaty.TRADE) return "Önce ticaret anlaşması gerekli"
        if (r.relation < 60) return "İlişki en az 60 olmalı"
        r.treaty = Treaty.ALLIANCE
        addLog("${r.name} ile ittifak kuruldu!")
        notify("İttifak kuruldu: ${r.name}")
        return null
    }

    fun declareWar(r: RivalColony) {
        r.treaty = Treaty.WAR
        relationChange(r, -60)
        addLog("${r.name} kolonisine savaş ilan edildi.")
    }

    fun trade(r: RivalColony, offer: TradeOffer): String? {
        if (r.treaty != Treaty.TRADE && r.treaty != Treaty.ALLIANCE) return "Ticaret anlaşması gerekli"
        if (!tradeRoom()) return "Ticaret Odası gerekli"
        if (r.tradesDay != day) { r.tradesDay = day; r.tradesCount = 0 }
        if (r.tradesCount >= 3) return "Bugünkü ticaret hakkı doldu"
        if (resources[offer.give] < offer.giveAmount) return "Yetersiz ${offer.give.title}"
        if (freeSpaceFor(offer.get) < offer.getAmount) return "Depoda yer yok"
        resources.take(offer.give, offer.giveAmount)
        resources.add(offer.get, offer.getAmount)
        r.tradesCount++
        relationChange(r, 2)
        return null
    }

    // ================================================================ Nüfus ve üreme

    fun housing(): Int = rooms.filter { it.functional }.sumOf {
        when (it.type) {
            RoomType.QUEEN_CHAMBER -> 8 * it.level
            RoomType.REST_ROOM -> 10 * it.level
            RoomType.BARRACKS -> 8 * it.level
            RoomType.ELITE_QUARTERS -> 4 * it.level
            else -> 0
        }
    }

    fun broodCapacity(room: Room): Int = if (!room.functional) 0 else when (room.type) {
        RoomType.QUEEN_CHAMBER -> 2 * room.level
        RoomType.EGG_ROOM -> 6 * room.level
        RoomType.LARVA_ROOM -> 4 * room.level
        else -> 0
    }

    private fun broodIn(room: Room) = brood.count { it.roomId == room.id }

    private fun eggRoom(): Room? = rooms.filter { it.type == RoomType.EGG_ROOM && broodIn(it) < broodCapacity(it) }.firstOrNull()
        ?: rooms.firstOrNull { it.type == RoomType.QUEEN_CHAMBER && broodIn(it) < broodCapacity(it) }

    fun layBlocker(): String? = when {
        queen == null -> "Kraliçe yok"
        resources.edibleTotal() < 6 -> "Yiyecek yetersiz (en az 6)"
        population + brood.size >= housing() -> "Barınak dolu: Dinlenme Odası inşa edin"
        eggRoom() == null -> "Yumurta yeri dolu: Yumurta Odası inşa edin"
        else -> null
    }

    val layInterval: Float get() = LAY_INTERVAL * mods.layIntervalMult

    private fun updateQueen(dt: Float) {
        val q = queen ?: return
        if (layBlocker() != null) return
        val eff = (q.hp / q.maxHp(mods)) * (if (resources.edibleTotal() < 15) 0.6f else 1f) * environment.climateFactor(mods).coerceAtLeast(0.5f)
        queenLayTimer += dt * eff
        if (queenLayTimer >= layInterval) {
            queenLayTimer = 0f
            var cost = 2
            while (cost > 0) { val t = resources.bestEdible() ?: break; resources.take(t, 1); cost-- }
            val room = eggRoom() ?: return
            brood.add(Brood(newId(), BroodStage.EGG, room.id))
            stats.eggsLaid++
        }
    }

    fun broodSpeed(b: Brood): Float {
        val room = roomById(b.roomId)
        val base = when {
            room == null -> 1f
            b.stage == BroodStage.EGG && room.type == RoomType.EGG_ROOM -> roomEfficiency(room)
            b.stage != BroodStage.EGG && room.type == RoomType.LARVA_ROOM -> 1.5f * roomEfficiency(room)
            else -> 1f
        }
        return base * mods.broodMult * environment.climateFactor(mods)
    }

    private fun updateBrood(dt: Float) {
        val it = brood.iterator()
        val hatched = ArrayList<Brood>()
        while (it.hasNext()) {
            val b = it.next()
            if (b.stage == BroodStage.LARVA) {
                b.fed = (b.fed - dt / 70f).coerceAtLeast(0f)
                if (b.fed <= 0f) continue
            }
            b.progress += dt * broodSpeed(b)
            if (b.progress >= b.stage.duration) {
                b.progress = 0f
                when (b.stage) {
                    BroodStage.EGG -> {
                        b.stage = BroodStage.LARVA
                        b.fed = 0.5f
                        val larvaRoom = rooms.firstOrNull { r -> r.type == RoomType.LARVA_ROOM && broodIn(r) < broodCapacity(r) }
                        if (larvaRoom != null) b.roomId = larvaRoom.id
                    }
                    BroodStage.LARVA -> b.stage = BroodStage.PUPA
                    BroodStage.PUPA -> { hatched.add(b); it.remove() }
                }
            }
        }
        for (b in hatched) {
            val room = roomById(b.roomId) ?: rooms.first { r -> r.type == RoomType.QUEEN_CHAMBER }
            ants.add(Ant(newId(), AntType.WORKER, room.anchorX + 0.5f, room.anchorY + 0.5f).also { it.hp = it.maxHp(mods) })
            stats.antsBorn++
            if (stats.antsBorn == 1 || stats.antsBorn % 5 == 0) addLog("Yeni işçi doğdu. Toplam doğum: ${stats.antsBorn}")
            notify("Yeni bir işçi doğdu")
            sound(SoundCue.BIRTH)
        }
    }

    fun killAnt(ant: Ant, reason: String) {
        ant.job?.cancel(this, ant)
        ant.job = null
        dropCarried(ant)
        if (!ants.remove(ant)) return
        stats.antsDied++
        if (ant.cellY >= 0) corpses.add(Corpse(newId(), ant.cellX, ant.cellY, ant.type.title))
        addLog("Bir ${ant.type.title.lowercaseTr()} $reason öldü.")
        notify("Bir ${ant.type.title.lowercaseTr()} $reason öldü!")
        sound(SoundCue.DEATH)
    }

    /** Sefer veya yan koloni için koloniden ayrılma (ölüm sayılmaz). */
    fun removeAntQuietly(ant: Ant) {
        ant.job?.cancel(this, ant)
        ant.job = null
        dropCarried(ant)
        ants.remove(ant)
    }

    private fun infirmary(): Room? = rooms.firstOrNull { it.type == RoomType.INFIRMARY && it.functional }

    private fun restRoomFor(ant: Ant): Room? {
        val resting = HashMap<Int, Int>()
        for (a in ants) { val j = a.job; if (j is RestJob && j.room != null) resting[j.room.id] = (resting[j.room.id] ?: 0) + 1 }
        val type = if (ant.type.military) RoomType.BARRACKS else RoomType.REST_ROOM
        return rooms.filter { (it.type == type || (ant.type.military && it.type == RoomType.ELITE_QUARTERS)) && it.functional && (resting[it.id] ?: 0) < 6 * it.level }
            .minByOrNull { manhattan(ant.cellX, ant.cellY, it.anchorX, it.anchorY) }
            ?: if (ant.type.military) rooms.filter { it.type == RoomType.REST_ROOM && it.functional }.minByOrNull { manhattan(ant.cellX, ant.cellY, it.anchorX, it.anchorY) } else null
    }

    private fun onNewDay() {
        var unpaid = 0
        for (r in rooms) {
            if (!r.functional || r.type.maintenanceSoil <= 0) continue
            val need = r.type.maintenanceSoil * r.level
            r.maintained = resources.take(ResourceType.SOIL, need) >= need
            if (!r.maintained) unpaid++
        }
        addLog("Gün $day (${season.title}) başladı. Nüfus: $population, yavru: ${brood.size}.")
        if ((day - 1) % Environment.DAYS_PER_SEASON == 0) { addLog("Mevsim değişti: ${season.title}."); notify("${season.title} geldi") }
        if (unpaid > 0) notify("$unpaid oda bakımsız kaldı (toprak malzemesi yetersiz)")
        refreshSources()
    }

    fun dailyMaintenance(): Int = rooms.filter { it.functional }.sumOf { it.type.maintenanceSoil * it.level }

    // ================================================================ Uzmanlık, feromon, bilgi

    fun specialistCount(cat: TaskCategory) = ants.count { it.assignment == cat }

    fun setSpecialists(cat: TaskCategory, count: Int) {
        val current = workers.filter { it.assignment == cat }
        if (current.size > count) current.sortedBy { it.xp }.take(current.size - count).forEach { it.assignment = null }
        else if (current.size < count) workers.filter { it.assignment == null }.sortedByDescending { it.xp }.take(count - current.size).forEach { it.assignment = cat }
    }

    fun pheromoneUnlocked(type: PheromoneType) = isUnlocked(type.requires)

    fun placePheromone(type: PheromoneType, x: Int, y: Int): PheromoneMarker? {
        if (!pheromoneUnlocked(type)) return null
        return pheromones.place(newId(), type, x, y)
    }

    fun addLog(text: String) {
        log.add(LogEntry(day, text))
        if (log.size > 300) log.removeAt(0)
    }

    fun notify(text: String) {
        notifications.addLast(text)
        while (notifications.size > 6) notifications.removeFirst()
    }

    fun sound(cue: SoundCue) {
        sounds.addLast(cue)
        while (sounds.size > 8) sounds.removeFirst()
    }

    fun foodConsumptionPerMinute(): Float = (workerCount + militaryCount * 1.3f) * 60f * (100f / 260f) / 34f

    class CellInfo(
        val x: Int, val y: Int, val terrain: Terrain, val revealed: Boolean, val designated: Boolean, val fillDesignated: Boolean,
        val room: Room?, val estimatedSeconds: Float, val risks: List<String>, val water: Float,
    )

    fun cellInfo(x: Int, y: Int): CellInfo {
        val t = world.get(x, y)
        val revealed = world.isRevealed(x, y)
        val risks = ArrayList<String>()
        if (y >= 0 && t.diggable) {
            val r = maxOf(collapseRisk(x, y, x, y), collapseRisk(x, y - 1, x, y))
            if (r > 0f) risks.add("Çökme riski %${(r * 100).toInt()}: geniş boşluk, gevşek tavan")
            if (revealed && t.needsHardRockTech && !mods.hardRock) risks.add("Kazılamaz: Sert Kaya Kazma araştırması veya Tünel Uzmanı gerekli")
            if (!hasWalkableNeighbor(x, y)) risks.add("Şu an erişilemez: önce yanına tünel açın")
            if (revealed && t == Terrain.WATER_POCKET) risks.add("Sel riski: kazılırsa su tünellere boşalır")
            if (revealed && t == Terrain.CREATURE_DEN) risks.add("Tehlike: içinde uyuyan bir canlı var")
            if (DIRS4.any { world.get(x + it[0], y + it[1]) == Terrain.WATER_SOIL }) risks.add("Su sızıntısı: yağmurda bu tünele su sızar")
            if (DIRS4.any { world.get(x + it[0], y + it[1]) == Terrain.RIVAL_TUNNEL }) risks.add("Rakip tünellerine bağlantı açar")
        } else if (y >= 1 && t.walkable) {
            val r = collapseRisk(x, y)
            if (r > 0f) risks.add("Çökme riski %${(r * 100).toInt()}")
        }
        val est = if (t.diggable) t.digWork / maxOf(0.5f, mods.digSpeed) else 0f
        return CellInfo(x, y, t, revealed, digOrders.containsKey(cellKey(x, y)), fillOrders.containsKey(cellKey(x, y)), roomAt(x, y), est, risks, environment.waterAt(x, y))
    }

    internal fun restoreInternal(lastDayValue: Int, sourceT: Float, risky: Collection<Long>) {
        lastDay = lastDayValue
        sourceTimer = sourceT
        riskyCells.clear()
        riskyCells.addAll(risky)
    }

    internal fun afterLoad() {
        rebuildWaterSources()
        recomputeModifiers()
        reputation = Reputation.of(this)
    }

    internal fun riskySnapshot(): List<Long> = riskyCells.toList()

    internal fun registerRoomCells(room: Room) { room.forEachCell { xx, yy -> roomCells[cellKey(xx, yy)] = room.id } }
}

/** Türkçe küçük harf (İ → i, I → ı). */
fun String.lowercaseTr(): String = this.replace('İ', 'i').replace('I', 'ı').lowercase(java.util.Locale.forLanguageTag("tr"))
