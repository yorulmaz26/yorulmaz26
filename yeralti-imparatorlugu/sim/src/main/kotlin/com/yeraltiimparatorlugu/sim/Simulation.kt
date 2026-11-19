package com.yeraltiimparatorlugu.sim

import kotlin.random.Random

enum class MoveResult { ARRIVED, MOVING, WAITING, UNREACHABLE }

enum class DigDesignation(val message: String?) {
    OK(null),
    ALREADY("Zaten işaretli"),
    NOT_DIGGABLE("Burası zaten açık"),
    NEEDS_TECH("Kaya: Sert Kaya Kazma araştırması gerekir"),
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
}

class LogEntry(val day: Int, val text: String)

/**
 * Koloni simülasyonu. Sabit zaman adımıyla ([STEP]) ilerler; çizimden bağımsızdır ve tamamen test edilebilir.
 */
class Simulation(val seed: Long) {
    companion object {
        const val STEP = 1f / 30f
        const val DAY_LENGTH = 600.0
        const val KNOWN_RANGE = 28
        const val SOURCE_RANGE = 46
        const val MAX_SOURCES = 13
        const val MAX_NEAR_SOURCES = 8
        const val FAR_SOURCE_LIFETIME = 420f
        const val LAY_INTERVAL = 45f
        const val MAX_BUILDERS = 3
        const val PATH_BUDGET_PER_STEP = 8
        const val BASE_FOOD_CAPACITY = 20
        const val BASE_MATERIAL_CAPACITY = 50

        /** Başlangıç kolonisi: kraliçe, 6 işçi, Kraliçe Odası, Besin Deposu, giriş tüneli. */
        fun newGame(seed: Long): Simulation {
            val sim = Simulation(seed)
            sim.setupStart()
            return sim
        }
    }

    val world = World(seed)
    val pheromones = PheromoneField()
    val pathfinder = Pathfinder(world, pheromones)
    val ants = ArrayList<Ant>()
    val brood = ArrayList<Brood>()
    val rooms = ArrayList<Room>()
    private val roomCells = HashMap<Long, Int>()
    val digOrders = LinkedHashMap<Long, DigOrder>()
    val piles = LinkedHashMap<Long, Pile>()
    val sources = ArrayList<FoodSource>()
    val resources = ResourceStore()
    val priorities = TaskPriorities()
    val stats = ColonyStats()
    val log = ArrayList<LogEntry>()
    val goals = GoalSystem()

    /** Arayüzde kısa süre gösterilecek bildirimler (tüketilir). */
    val notifications = ArrayDeque<String>()

    var time = 0.0
    var nextId = 1
    var rng = Random(seed)
    var queenLayTimer = 0f
    var surfaceMound = 0
    val dumpX = 3
    var digSpeedMultiplier = 1f
    var hardRockUnlocked = false

    private var accumulator = 0f
    private var pathBudget = PATH_BUDGET_PER_STEP
    private var sourceTimer = 0f
    private var collapseTimer = 0f
    private var lastDay = 1
    private val occupancy = HashMap<Long, Int>()
    private val riskyCells = HashSet<Long>()

    val day: Int get() = (time / DAY_LENGTH).toInt() + 1
    val dayFraction: Float get() = ((time % DAY_LENGTH) / DAY_LENGTH).toFloat()
    val queen: Ant? get() = ants.firstOrNull { it.type == AntType.QUEEN }
    val workers: List<Ant> get() = ants.filter { it.type == AntType.WORKER }
    val workerCount: Int get() = ants.count { it.type == AntType.WORKER }

    fun newId() = nextId++

    // ---------------------------------------------------------------- Başlangıç

    private fun setupStart() {
        for (y in 0..6) world.set(0, y, Terrain.TUNNEL)
        val queenRoom = Room(newId(), RoomType.QUEEN_CHAMBER, -2, 7).also { it.state = RoomState.ACTIVE }
        val food = Room(newId(), RoomType.FOOD_STORAGE, 3, 7).also { it.state = RoomState.ACTIVE }
        for (r in listOf(queenRoom, food)) {
            rooms.add(r)
            r.forEachCell { x, y -> world.set(x, y, Terrain.TUNNEL); roomCells[cellKey(x, y)] = r.id }
        }
        world.reveal(0, 3, 4)
        world.reveal(0, 8, 7)
        world.reveal(5, 8, 5)
        ants.add(Ant(newId(), AntType.QUEEN, queenRoom.anchorX + 0.5f, queenRoom.anchorY + 0.5f))
        repeat(6) { i ->
            ants.add(Ant(newId(), AntType.WORKER, -1.5f + i, 9.5f).also { it.hunger = 10f + i * 4f })
        }
        resources.set(ResourceType.FOOD, 30)
        resources.set(ResourceType.SEED, 8)
        resources.set(ResourceType.SOIL, 16)
        resources.set(ResourceType.LEAF, 8)
        resources.set(ResourceType.WOOD, 4)
        // Yakında birkaç kaynak.
        addSource(-7, SourceKind.LEAF_BUSH, 14)
        addSource(9, SourceKind.DEAD_INSECT, 10)
        addSource(14, SourceKind.SEED_PILE, 12)
        addSource(-16, SourceKind.TWIGS, 10)
        repeat(3) { spawnRandomSource(far = it == 0) }
        addLog("Koloni kuruldu. Kraliçe ve 6 işçi yeni yuvalarında.")
        goals.ensureGoals(this)
    }

    // ---------------------------------------------------------------- Zaman

    /** Gerçek zamanı sabit adımlara böler. [speed] 0 ise duraklatılmıştır. */
    fun update(delta: Float, speed: Int = 1) {
        if (speed <= 0) return
        accumulator += minOf(delta, 0.25f) * speed
        var steps = 0
        while (accumulator >= STEP && steps < 12 * speed) {
            step(STEP)
            accumulator -= STEP
            steps++
        }
    }

    fun step(dt: Float) {
        time += dt
        stats.playTime += dt
        pathBudget = PATH_BUDGET_PER_STEP
        rebuildOccupancy()
        pheromones.update(dt)
        updateRooms(dt)
        updateQueen(dt)
        updateBrood(dt)
        // Yinelenirken liste değişebilir (ölüm, doğum): kopya üzerinde dön.
        for (ant in ants.toTypedArray()) updateAnt(ant, dt)
        sourceTimer += dt
        if (sourceTimer >= 14f) { sourceTimer = 0f; refreshSources() }
        collapseTimer += dt
        if (collapseTimer >= 4f) { collapseTimer = 0f; checkCollapses() }
        if (day != lastDay) { lastDay = day; onNewDay() }
        goals.update(this)
    }

    private fun rebuildOccupancy() {
        occupancy.clear()
        for (a in ants) {
            if (a.type == AntType.QUEEN || a.activity == AntActivity.RESTING) continue
            val k = cellKey(a.cellX, a.cellY)
            occupancy[k] = (occupancy[k] ?: 0) + 1
        }
    }

    // ---------------------------------------------------------------- Karınca davranışı

    private fun updateAnt(ant: Ant, dt: Float) {
        ant.prevX = ant.x
        ant.prevY = ant.y
        ant.age += dt
        if (ant.type == AntType.QUEEN) return
        val working = ant.activity != AntActivity.IDLE && ant.activity != AntActivity.RESTING
        ant.hunger = (ant.hunger + dt * (100f / 260f)).coerceAtMost(100f)
        if (ant.activity != AntActivity.RESTING) ant.energy = (ant.energy - dt * (if (working) 0.33f else 0.12f)).coerceAtLeast(0f)
        ant.starving = ant.hunger >= 100f
        if (ant.starving) {
            ant.hp -= dt * 0.15f
            if (ant.hp <= 0f) { killAnt(ant, "açlıktan"); return }
        } else if (ant.hp < ant.type.maxHp) {
            ant.hp = (ant.hp + dt * 0.05f).coerceAtMost(ant.type.maxHp)
        }

        val job = ant.job
        // İhtiyaçlar işleri böler.
        if (ant.eatCooldown > 0f) ant.eatCooldown -= dt
        if (job !is EatJob && ant.hunger >= 65f && resources.edibleTotal() > 0 && ant.eatCooldown <= 0f) {
            switchJob(ant, EatJob())
        } else if (job !is EatJob && job !is RestJob && ant.energy < 15f) {
            switchJob(ant, RestJob(restRoomFor(ant)))
        } else if (job == null || (job is WanderJob && ant.waitTimer <= 0f)) {
            if (job is WanderJob) ant.waitTimer = 1f
            val chosen = chooseJob(ant)
            if (chosen != null) switchJob(ant, chosen)
            else if (job == null) switchJob(ant, wanderJob(ant))
        }
        if (ant.waitTimer > 0f) ant.waitTimer -= dt

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
        val rally = pheromones.markers.filter { it.type == PheromoneType.RALLY }.minByOrNull { manhattan(it.x, it.y, ant.cellX, ant.cellY) }
        val cx = rally?.x ?: ant.cellX
        val cy = rally?.y ?: ant.cellY
        val r = rally?.type?.radius ?: 4
        repeat(8) {
            val x = cx + rng.nextInt(-r, r + 1)
            val y = cy + rng.nextInt(-r, r + 1)
            if (world.isWalkable(x, y) && (rally != null || y >= 0 || ant.cellY < 0)) return WanderJob(x, y, 1f + rng.nextFloat() * 2f)
        }
        return WanderJob(ant.cellX, ant.cellY, 1.5f)
    }

    private class Candidate(val score: Float, val make: () -> Job)

    /** Öncelik gruplarına göre en uygun işi seçer ve rezerve eder. */
    fun chooseJob(ant: Ant): Job? {
        if (ant.carryAmount > 0 && ant.carryType != null) return DeliverJob()
        ant.assignment?.let { spec ->
            if (priorities[spec] > 0) findCandidate(ant, spec)?.let { return it.make() }
        }
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

    private fun findCandidate(ant: Ant, cat: TaskCategory): Candidate? {
        val ax = ant.cellX
        val ay = ant.cellY
        return when (cat) {
            TaskCategory.DIG -> {
                var best: DigOrder? = null
                var bestScore = Float.MAX_VALUE
                for (o in digOrders.values) {
                    if (o.reservedBy != -1 || o.unreachableUntil > time) continue
                    if (!hasWalkableNeighbor(o.x, o.y)) continue
                    val t = world.get(o.x, o.y)
                    if (t.needsHardRockTech && !hardRockUnlocked) continue
                    if (pheromones.strengthAt(PheromoneType.AVOID, o.x, o.y) > 0f) continue
                    var s = manhattan(ax, ay, o.x, o.y).toFloat() - pheromones.strengthAt(PheromoneType.DIG, o.x, o.y) * 30f
                    if (o.roomId != -1) s -= 4f
                    if (s < bestScore) { bestScore = s; best = o }
                }
                best?.let { o -> Candidate(bestScore) { o.reservedBy = ant.id; DigJob(o) } }
            }
            TaskCategory.HAUL -> {
                var best: Pile? = null
                var bestScore = Float.MAX_VALUE
                for (p in piles.values) {
                    if (p.reservedBy != -1 || p.unreachableUntil > time) continue
                    if (p.type != ResourceType.SOIL && freeSpaceFor(p.type) <= 0) continue
                    if (pheromones.strengthAt(PheromoneType.AVOID, p.x, p.y) > 0f) continue
                    val s = manhattan(ax, ay, p.x, p.y).toFloat()
                    if (s < bestScore) { bestScore = s; best = p }
                }
                best?.let { p -> Candidate(bestScore) { p.reservedBy = ant.id; HaulJob(p) } }
            }
            TaskCategory.GATHER -> {
                var best: FoodSource? = null
                var bestScore = Float.MAX_VALUE
                val foodLow = resources.edibleTotal() < 15
                for (s in sources) {
                    if (s.amount - s.reserved <= 0 || s.unreachableUntil > time) continue
                    if (freeSpaceFor(s.kind.resource) <= 0) continue
                    val foodMark = pheromones.strengthAt(PheromoneType.FOOD, s.x, -1)
                    if (kotlin.math.abs(s.x) > KNOWN_RANGE && foodMark <= 0f) continue
                    if (pheromones.strengthAt(PheromoneType.AVOID, s.x, -1) > 0f) continue
                    var score = manhattan(ax, ay, s.x, -1).toFloat() - foodMark * 40f
                    if (foodLow && s.kind.resource.edible) score -= 12f
                    if (score < bestScore) { bestScore = score; best = s }
                }
                best?.let { s ->
                    Candidate(bestScore) {
                        val amount = minOf(ant.carryCapacity, s.amount - s.reserved)
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
                val available = resources[ResourceType.LEAF] - rooms.sumOf { it.supplyReserved }
                if (available <= 0) return null
                val farm = rooms.filter { it.type == RoomType.FUNGUS_FARM && it.functional && it.leafBuffer + it.supplyReserved < farmLeafCapacity(it) }
                    .minByOrNull { manhattan(ax, ay, it.anchorX, it.anchorY) } ?: return null
                val amount = minOf(ant.carryCapacity, farmLeafCapacity(farm) - farm.leafBuffer - farm.supplyReserved, available)
                if (amount <= 0) return null
                Candidate(manhattan(ax, ay, farm.anchorX, farm.anchorY).toFloat()) { farm.supplyReserved += amount; SupplyFarmJob(farm, amount) }
            }
            TaskCategory.NURSE -> {
                if (resources.edibleTotal() <= 0) return null
                val b = brood.filter { it.stage == BroodStage.LARVA && it.fed < 0.4f && it.nurseReservedBy == -1 }
                    .minByOrNull { it.fed } ?: return null
                val room = roomById(b.roomId) ?: return null
                Candidate(manhattan(ax, ay, room.anchorX, room.anchorY).toFloat() - 10f) { b.nurseReservedBy = ant.id; NurseJob(b) }
            }
            else -> null
        }
    }

    // ---------------------------------------------------------------- Hareket

    /** Karıncayı yol üzerinde ilerletir; yol yoksa veya bütçe bittiyse sonucu bildirir. */
    fun moveAnt(ant: Ant, tx: Int, ty: Int, dt: Float): MoveResult {
        val target = cellKey(tx, ty)
        val cx = tx + 0.5f
        val cy = ty + 0.5f
        if (ant.cellX == tx && ant.cellY == ty && ant.path == null) {
            val dx = cx - ant.x
            val dy = cy - ant.y
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            val stepLen = ant.type.speed * dt
            if (d <= stepLen || d < 0.05f) { ant.x = cx; ant.y = cy; return MoveResult.ARRIVED }
            ant.x += dx / d * stepLen
            ant.y += dy / d * stepLen
            ant.walkPhase += dt * 12f
            return MoveResult.MOVING
        }
        if (ant.path == null || ant.pathTarget != target) {
            if (pathBudget <= 0) return MoveResult.WAITING
            pathBudget--
            val p = pathfinder.find(ant.cellX, ant.cellY, tx, ty) ?: run { ant.clearPath(); return MoveResult.UNREACHABLE }
            ant.path = p
            ant.pathIndex = 0
            ant.pathTarget = target
        }
        val path = ant.path!!
        var remaining = ant.type.speed * dt * trafficFactor(ant)
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
            if (d <= remaining) {
                ant.x = px; ant.y = py; remaining -= d; ant.pathIndex++
            } else {
                ant.x += dx / d * remaining; ant.y += dy / d * remaining; remaining = 0f
            }
        }
        ant.walkPhase += dt * 12f
        if (ant.pathIndex * 2 >= path.size) { ant.clearPath(); return if (ant.cellX == tx && ant.cellY == ty) MoveResult.ARRIVED else MoveResult.MOVING }
        return MoveResult.MOVING
    }

    /** Dar tünellerde kalabalık yavaşlatır; odalar ve yüzey geniş sayılır. */
    private fun trafficFactor(ant: Ant): Float {
        val path = ant.path ?: return 1f
        if (ant.pathIndex * 2 >= path.size) return 1f
        val nx = path[ant.pathIndex * 2]
        val ny = path[ant.pathIndex * 2 + 1]
        if (ny < 0 || roomCells.containsKey(cellKey(nx, ny))) return 1f
        val n = occupancy[cellKey(nx, ny)] ?: 0
        return if (n <= 1) 1f else 1f / (1f + 0.35f * (n - 1))
    }

    fun trafficAt(x: Int, y: Int): Int = occupancy[cellKey(x, y)] ?: 0

    // ---------------------------------------------------------------- Kazı

    fun hasWalkableNeighbor(x: Int, y: Int): Boolean = DIRS4.any { world.isWalkable(x + it[0], y + it[1]) }

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
        if (world.isRevealed(x, y) && t.needsHardRockTech && !hardRockUnlocked) return DigDesignation.NEEDS_TECH
        digOrders[key] = DigOrder(x, y)
        return DigDesignation.OK
    }

    fun cancelDig(x: Int, y: Int): Boolean {
        val o = digOrders[cellKey(x, y)] ?: return false
        if (o.roomId != -1) return false
        digOrders.remove(cellKey(x, y))
        return true
    }

    fun completeDig(order: DigOrder, ant: Ant) {
        val t = world.get(order.x, order.y)
        world.set(order.x, order.y, Terrain.TUNNEL)
        world.reveal(order.x, order.y, 3)
        digOrders.remove(cellKey(order.x, order.y))
        stats.cellsDug++
        val type = t.yieldType
        if (type != null && t.yieldAmount > 0) {
            val carry = minOf(ant.carryCapacity, t.yieldAmount)
            ant.carryType = type
            ant.carryAmount = carry
            if (t.yieldAmount > carry) addPile(order.x, order.y, type, t.yieldAmount - carry)
            if (t == Terrain.FOSSIL) { addLog("Fosil bulundu! (${order.x}, ${order.y})"); notify("Fosil bulundu!") }
            if (t == Terrain.MINERAL && rng.nextFloat() < 0.15f) notify("Mineral damarı kazıldı")
        }
        // Çökme riski olabilecek hücreleri izlemeye al.
        for (dx in -6..6) for (dy in -1..0) {
            if (world.isWalkable(order.x + dx, order.y + dy)) riskyCells.add(cellKey(order.x + dx, order.y + dy))
        }
        // Oda kazısı bittiyse inşaata geç.
        if (order.roomId != -1) roomById(order.roomId)?.let { checkExcavation(it) }
    }

    /**
     * Desteksiz yatay boşluklar, tavanı gevşek zeminse (kum, yumuşak toprak, moloz) çökebilir.
     * İnşa edilmiş odalar desteklidir. [assumeOpenX] verilirse o hücre açık varsayılır (kazı önizlemesi).
     */
    fun collapseRisk(x: Int, y: Int, assumeOpenX: Int? = null, assumeOpenY: Int? = null): Float {
        if (y < 1) return 0f
        if (roomAt(x, y) != null) return 0f
        val limit = when (world.get(x, y - 1)) {
            Terrain.SAND -> 2
            Terrain.SOFT_SOIL, Terrain.RUBBLE -> 3
            else -> return 0f
        }
        fun openCell(xx: Int, yy: Int) = (xx == assumeOpenX && yy == (assumeOpenY ?: y)) || world.isWalkable(xx, yy)
        // Yalnızca en az iki hücre yüksekliğindeki desteksiz boşluklar sayılır; tek sıra tüneller güvenlidir.
        fun column(xx: Int) = openCell(xx, y) && openCell(xx, y + 1) && roomAt(xx, y) == null && roomAt(xx, y + 1) == null
        if (!column(x)) return 0f
        var span = 1
        var i = x - 1
        while (span < 14 && column(i)) { span++; i-- }
        i = x + 1
        while (span < 14 && column(i)) { span++; i++ }
        return if (span > limit) ((span - limit) * 0.25f).coerceAtMost(1f) else 0f
    }

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
        stats.collapses++
        for (a in ants) {
            if (a.cellX == x && a.cellY == y) {
                a.hp -= 8f
                val n = DIRS4.firstOrNull { world.isWalkable(x + it[0], y + it[1]) }
                if (n != null) { a.x = x + n[0] + 0.5f; a.y = y + n[1] + 0.5f } else { a.x = x + 0.5f; a.y = y - 0.5f }
                a.clearPath()
                if (a.hp <= 0f) killAnt(a, "çökme altında")
            }
        }
        // Moloz otomatik olarak kazı listesine eklenir; mahsur kalan karıncalar da kendi tarafından kazabilir.
        digOrders[cellKey(x, y)] = DigOrder(x, y)
        addLog("Tünel çöktü (x:$x, derinlik:$y). Moloz kazı listesine eklendi.")
        notify("Tünel çöktü! Geniş boşlukları odalarla destekleyin.")
    }

    // ---------------------------------------------------------------- Kaynak ve depo

    fun capacityOf(kind: StorageKind): Int {
        var cap = if (kind == StorageKind.FOOD) BASE_FOOD_CAPACITY else BASE_MATERIAL_CAPACITY
        for (r in rooms) {
            if (!r.functional) continue
            val m = if (r.maintained) 1f else 0.5f
            cap += when {
                kind == StorageKind.FOOD && r.type == RoomType.FOOD_STORAGE -> (60 * r.level * m).toInt()
                kind == StorageKind.MATERIAL && r.type == RoomType.MATERIAL_STORAGE -> (80 * r.level * m).toInt()
                else -> 0
            }
        }
        return cap
    }

    fun freeSpace(kind: StorageKind): Int = capacityOf(kind) - resources.totalOf(kind)

    /**
     * Tür bazında boş yer. Toprak malzemesi malzeme deposunun en fazla %60'ını kaplayabilir; fazlası yüzeye atılır.
     * Böylece kazı artığı yaprak ve odun için yer bırakır.
     */
    fun freeSpaceFor(type: ResourceType): Int {
        val free = freeSpace(type.storage)
        if (type != ResourceType.SOIL) return free
        return minOf(free, (capacityOf(StorageKind.MATERIAL) * 0.6f).toInt() - resources[ResourceType.SOIL])
    }

    /** Ödül ve iadeler kapasiteyi aşmaz; sığmayan kısım kaybolur. Eklenen miktarı döndürür. */
    fun addCapped(type: ResourceType, amount: Int): Int {
        val n = minOf(amount, freeSpaceFor(type).coerceAtLeast(0))
        resources.add(type, n)
        return n
    }

    /** En yakın uygun depo; yoksa Kraliçe Odası (küçük yığın). */
    fun storageRoomFor(kind: StorageKind, fromX: Int, fromY: Int): Room? {
        val type = if (kind == StorageKind.FOOD) RoomType.FOOD_STORAGE else RoomType.MATERIAL_STORAGE
        return rooms.filter { it.type == type && it.functional }.minByOrNull { manhattan(fromX, fromY, it.anchorX, it.anchorY) }
            ?: rooms.firstOrNull { it.type == RoomType.QUEEN_CHAMBER }
    }

    /** Odanın taban sırasından rastgele bir hücre (kalabalığı dağıtır). */
    fun floorCell(room: Room): Long {
        val x = room.x + rng.nextInt(room.width)
        return cellKey(x, room.anchorY)
    }

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
        // Aynı hücrede başka türde yığın varsa komşu boş hücreye koy.
        var px = x
        var py = y
        val existing = piles[cellKey(px, py)]
        if (existing != null && existing.type != type) {
            val n = DIRS4.firstOrNull { world.isWalkable(x + it[0], y + it[1]) && piles[cellKey(x + it[0], y + it[1])].let { p -> p == null || p.type == type } }
            if (n != null) { px = x + n[0]; py = y + n[1] }
        }
        val p = piles[cellKey(px, py)]
        if (p != null && p.type == type) p.amount += amount
        else if (p == null) piles[cellKey(px, py)] = Pile(px, py, type, amount)
        else p.amount += 0 // komşu da doluysa kaybolur (çok nadir)
    }

    fun pickUpPile(pile: Pile, ant: Ant) {
        if (piles[cellKey(pile.x, pile.y)] !== pile) return
        val take = minOf(ant.carryCapacity, pile.amount)
        ant.carryType = pile.type
        ant.carryAmount = take
        pile.amount -= take
        pile.reservedBy = -1
        if (pile.amount <= 0) piles.remove(cellKey(pile.x, pile.y))
    }

    // ---------------------------------------------------------------- Yüzey kaynakları

    fun addSource(x: Int, kind: SourceKind, amount: Int): FoodSource = FoodSource(newId(), x, kind, amount).also { sources.add(it) }

    /** Bilinen menzildeki kaynak sayısını korur; dokunulmamış uzak kaynaklar zamanla kaybolur. */
    fun refreshSources() {
        sources.removeAll { it.isFar() && it.reserved == 0 && it.amount == it.maxAmount && time - it.spawnedAt > FAR_SOURCE_LIFETIME }
        val near = sources.filter { !it.isFar() }
        val nearEdible = near.count { it.kind.resource.edible }
        if (nearEdible < 3) spawnRandomSource(far = false, edibleOnly = true)
        else if (near.size < MAX_NEAR_SOURCES) spawnRandomSource(far = false)
        else if (sources.size < MAX_SOURCES) spawnRandomSource(far = true)
    }

    private fun FoodSource.isFar() = kotlin.math.abs(x) > KNOWN_RANGE

    fun spawnRandomSource(far: Boolean = rng.nextFloat() < 0.3f, edibleOnly: Boolean = false) {
        repeat(10) {
            val x = if (far) (KNOWN_RANGE + 1 + rng.nextInt(SOURCE_RANGE - KNOWN_RANGE)) * (if (rng.nextBoolean()) 1 else -1)
            else rng.nextInt(-KNOWN_RANGE, KNOWN_RANGE + 1)
            if (kotlin.math.abs(x) < 4 || sources.any { kotlin.math.abs(it.x - x) < 3 }) return@repeat
            val roll = if (edibleOnly) 0.32f + rng.nextFloat() * 0.42f else rng.nextFloat()
            val kind = when {
                roll < 0.32f -> SourceKind.LEAF_BUSH
                roll < 0.52f -> SourceKind.SEED_PILE
                roll < 0.74f -> SourceKind.DEAD_INSECT
                roll < 0.9f -> SourceKind.TWIGS
                else -> SourceKind.DEW
            }
            val amount = 6 + rng.nextInt(10) + (kotlin.math.abs(x) * 14 / SOURCE_RANGE)
            addSource(x, kind, amount).spawnedAt = time
            return
        }
    }

    fun onSourceGathered(source: FoodSource) {
        if (source.amount <= 0 && source.reserved <= 0) sources.remove(source)
    }

    // ---------------------------------------------------------------- Odalar

    fun roomById(id: Int): Room? = rooms.firstOrNull { it.id == id }

    fun roomAt(x: Int, y: Int): Room? = roomCells[cellKey(x, y)]?.let { roomById(it) }

    fun checkRoomPlacement(type: RoomType, x: Int, y: Int): Placement {
        if (!type.buildable) return Placement(false, "Bu oda inşa edilemez", 0)
        if (y < 1) return Placement(false, "Odalar yüzeyin en az 1 hücre altında olmalı", 0)
        var dig = 0
        var connected = false
        for (yy in y until y + type.height) for (xx in x until x + type.width) {
            if (roomCells.containsKey(cellKey(xx, yy))) return Placement(false, "Başka bir odayla çakışıyor", 0)
            val t = world.get(xx, yy)
            if (t.needsHardRockTech && !hardRockUnlocked) return Placement(false, "Alanda kaya var", 0)
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

    fun placeRoom(type: RoomType, x: Int, y: Int): Room? {
        if (!checkRoomPlacement(type, x, y).ok) return null
        resources.pay(type.cost)
        val room = Room(newId(), type, x, y)
        rooms.add(room)
        room.forEachCell { xx, yy ->
            roomCells[cellKey(xx, yy)] = room.id
            val key = cellKey(xx, yy)
            digOrders.remove(key)
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
    }

    fun canUpgrade(room: Room): Boolean = room.state == RoomState.ACTIVE && room.level < room.type.maxLevel && resources.hasAll(room.type.upgradeCost(room.level + 1))

    fun upgradeRoom(room: Room): Boolean {
        if (!canUpgrade(room)) return false
        resources.pay(room.type.upgradeCost(room.level + 1))
        room.state = RoomState.UPGRADING
        room.buildProgress = 0f
        return true
    }

    /** İnşası bitmemiş odayı iptal eder (tam iade) veya çalışan odayı yıkar (%50 iade). */
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
            if ((j is BuildJob && j.room === room) || (j is SupplyFarmJob && j.farm === room) || (j is RestJob && j.room === room)) {
                j.cancel(this, a); a.job = null; a.clearPath()
            }
        }
        // Taşmayı engelle: kapasite düştüyse fazla yerde yığın olarak kalır.
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

    fun roomEfficiency(room: Room): Float {
        if (!room.functional) return 0f
        var bonus = 0f
        for ((t, b) in room.type.adjacency) if (rooms.any { it.type == t && it.functional && it.isAdjacentTo(room) }) bonus += b
        return (if (room.maintained) 1f else 0.5f) * (1f + bonus)
    }

    fun farmLeafCapacity(room: Room) = 10 * room.level

    /** Mantar çiftliğinin saniyelik mantar üretimi (yaprak varken). */
    fun farmRate(room: Room): Float = roomEfficiency(room) * room.level / 12f * 2f

    private fun updateRooms(dt: Float) {
        for (r in rooms) {
            if (r.type == RoomType.FUNGUS_FARM && r.functional && r.leafBuffer > 0) {
                val out = piles[cellKey(r.anchorX, r.anchorY)]
                if (out != null && out.amount >= 12) continue
                r.growProgress += dt * roomEfficiency(r) * r.level / 12f
                while (r.growProgress >= 1f && r.leafBuffer > 0) {
                    r.growProgress -= 1f
                    r.leafBuffer--
                    addPile(r.anchorX, r.anchorY, ResourceType.MUSHROOM, 2)
                }
            }
        }
    }

    private fun onNewDay() {
        var unpaid = 0
        for (r in rooms) {
            if (!r.functional || r.type.maintenanceSoil <= 0) continue
            val need = r.type.maintenanceSoil * r.level
            r.maintained = resources.take(ResourceType.SOIL, need) >= need
            if (!r.maintained) unpaid++
        }
        addLog("Gün $day başladı. Nüfus: ${workerCount} işçi, ${brood.size} yavru.")
        if (unpaid > 0) notify("$unpaid oda bakımsız kaldı (toprak malzemesi yetersiz)")
        refreshSources()
    }

    fun dailyMaintenance(): Int = rooms.filter { it.functional }.sumOf { it.type.maintenanceSoil * it.level }

    private fun restRoomFor(ant: Ant): Room? {
        val resting = HashMap<Int, Int>()
        for (a in ants) { val j = a.job; if (j is RestJob && j.room != null) resting[j.room.id] = (resting[j.room.id] ?: 0) + 1 }
        return rooms.filter { it.type == RoomType.REST_ROOM && it.functional && (resting[it.id] ?: 0) < 6 * it.level }
            .minByOrNull { manhattan(ant.cellX, ant.cellY, it.anchorX, it.anchorY) }
    }

    // ---------------------------------------------------------------- Nüfus ve üreme

    fun housing(): Int = rooms.filter { it.functional }.sumOf {
        when (it.type) { RoomType.QUEEN_CHAMBER -> 8 * it.level; RoomType.REST_ROOM -> 10 * it.level; else -> 0 }
    }

    fun broodCapacity(room: Room): Int = if (!room.functional) 0 else when (room.type) {
        RoomType.QUEEN_CHAMBER -> 2 * room.level
        RoomType.EGG_ROOM -> 6 * room.level
        RoomType.LARVA_ROOM -> 4 * room.level
        else -> 0
    }

    private fun broodIn(room: Room) = brood.count { it.roomId == room.id }

    /** Yumurta bırakılabilecek oda: önce yumurta odaları, sonra Kraliçe Odası. */
    private fun eggRoom(): Room? = rooms.filter { it.type == RoomType.EGG_ROOM && broodIn(it) < broodCapacity(it) }.firstOrNull()
        ?: rooms.firstOrNull { it.type == RoomType.QUEEN_CHAMBER && broodIn(it) < broodCapacity(it) }

    /** Kraliçenin yumurtlamasını engelleyen neden (null: yumurtlayabilir). */
    fun layBlocker(): String? = when {
        queen == null -> "Kraliçe yok"
        resources.edibleTotal() < 6 -> "Yiyecek yetersiz (en az 6)"
        workerCount + brood.size >= housing() -> "Barınak dolu: Dinlenme Odası inşa edin"
        eggRoom() == null -> "Yumurta yeri dolu: Yumurta Odası inşa edin"
        else -> null
    }

    private fun updateQueen(dt: Float) {
        val q = queen ?: return
        if (layBlocker() != null) return
        val eff = (q.hp / q.type.maxHp) * (if (resources.edibleTotal() < 15) 0.6f else 1f)
        queenLayTimer += dt * eff
        if (queenLayTimer >= LAY_INTERVAL) {
            queenLayTimer = 0f
            var cost = 2
            while (cost > 0) { val t = resources.bestEdible() ?: break; resources.take(t, 1); cost-- }
            val room = eggRoom() ?: return
            brood.add(Brood(newId(), BroodStage.EGG, room.id))
            stats.eggsLaid++
        }
    }

    private fun broodSpeed(b: Brood): Float {
        val room = roomById(b.roomId) ?: return 1f
        return when {
            b.stage == BroodStage.EGG && room.type == RoomType.EGG_ROOM -> roomEfficiency(room)
            b.stage != BroodStage.EGG && room.type == RoomType.LARVA_ROOM -> 1.5f * roomEfficiency(room)
            else -> 1f
        }
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
            ants.add(Ant(newId(), AntType.WORKER, room.anchorX + 0.5f, room.anchorY + 0.5f))
            stats.antsBorn++
            if (stats.antsBorn == 1 || stats.antsBorn % 5 == 0) addLog("Yeni işçi doğdu. Toplam doğum: ${stats.antsBorn}")
            notify("Yeni bir işçi doğdu")
        }
    }

    fun killAnt(ant: Ant, reason: String) {
        ant.job?.cancel(this, ant)
        ant.job = null
        dropCarried(ant)
        ants.remove(ant)
        stats.antsDied++
        addLog("Bir ${if (ant.type == AntType.QUEEN) "kraliçe" else "işçi"} $reason öldü.")
        notify("Bir işçi $reason öldü!")
    }

    // ---------------------------------------------------------------- Uzmanlık ve feromon

    fun specialistCount(cat: TaskCategory) = ants.count { it.assignment == cat }

    /** [cat] uzmanı işçi sayısını [count] yapar (en deneyimliler seçilir). */
    fun setSpecialists(cat: TaskCategory, count: Int) {
        val current = workers.filter { it.assignment == cat }
        if (current.size > count) current.sortedBy { it.xp }.take(current.size - count).forEach { it.assignment = null }
        else if (current.size < count) workers.filter { it.assignment == null }.sortedByDescending { it.xp }
            .take(count - current.size).forEach { it.assignment = cat }
    }

    fun placePheromone(type: PheromoneType, x: Int, y: Int): PheromoneMarker = pheromones.place(newId(), type, x, y)

    // ---------------------------------------------------------------- Bilgi

    fun addLog(text: String) {
        log.add(LogEntry(day, text))
        if (log.size > 200) log.removeAt(0)
    }

    fun notify(text: String) {
        notifications.addLast(text)
        while (notifications.size > 6) notifications.removeFirst()
    }

    fun foodConsumptionPerMinute(): Float = workerCount * 60f * (100f / 260f) / 34f

    class CellInfo(
        val x: Int,
        val y: Int,
        val terrain: Terrain,
        val revealed: Boolean,
        val designated: Boolean,
        val room: Room?,
        val estimatedSeconds: Float,
        val risks: List<String>,
    )

    /** Kazı öncesi hücre analizi: süre tahmini ve riskler. */
    fun cellInfo(x: Int, y: Int): CellInfo {
        val t = world.get(x, y)
        val revealed = world.isRevealed(x, y)
        val risks = ArrayList<String>()
        if (y >= 0 && t.diggable) {
            val r = maxOf(collapseRisk(x, y, x, y), collapseRisk(x, y - 1, x, y))
            if (r > 0f) risks.add("Çökme riski %${(r * 100).toInt()}: geniş boşluk, gevşek tavan")
            if (revealed && t.needsHardRockTech && !hardRockUnlocked) risks.add("Kazılamaz: araştırma gerekli")
            if (!hasWalkableNeighbor(x, y)) risks.add("Şu an erişilemez: önce yanına tünel açın")
        } else if (y >= 1 && t.walkable) {
            val r = collapseRisk(x, y)
            if (r > 0f) risks.add("Çökme riski %${(r * 100).toInt()}")
        }
        val est = if (t.diggable) t.digWork / maxOf(0.5f, digSpeedMultiplier) else 0f
        return CellInfo(x, y, t, revealed, digOrders.containsKey(cellKey(x, y)), roomAt(x, y), est, risks)
    }

    /** Kayıttan yükleme için iç durumu ayarlar. */
    internal fun restoreInternal(lastDayValue: Int, sourceT: Float, risky: Collection<Long>) {
        lastDay = lastDayValue
        sourceTimer = sourceT
        riskyCells.clear()
        riskyCells.addAll(risky)
    }

    internal fun riskySnapshot(): List<Long> = riskyCells.toList()

    internal fun registerRoomCells(room: Room) {
        room.forEachCell { xx, yy -> roomCells[cellKey(xx, yy)] = room.id }
    }
}
