package com.yuruyensehir.logic

import com.yuruyensehir.logic.Resource.*
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.roundToInt

data class RouteOption(
    val name: String,
    val cells: List<Cell>,
    val estMinutes: Double,
    val foodCost: Double,
    val waterCost: Double,
    val avgDanger: Double,
    val biomes: Map<Biome, Int>,
) {
    val distance get() = cells.size
    val dangerLabel get() = when {
        avgDanger < 0.15 -> "Düşük"
        avgDanger < 0.32 -> "Orta"
        avgDanger < 0.45 -> "Yüksek"
        else -> "Çok yüksek"
    }
}

object TravelSystem {
    const val MAX_RANGE = 14

    fun revealRadius(state: GameState): Int {
        var r = 2
        state.buildings.firstOrNull { it.type == BuildingType.MAP_CENTER && !it.underConstruction && it.workers > 0 }?.let { r += it.def.revealBonus * it.level }
        if (ResearchSystem.has(state, TechId.STAR_CHARTS)) r += 1
        return r + Bonuses.reveal(state)
    }

    fun reveal(state: GameState, center: Cell) {
        val r = revealRadius(state)
        for (dx in -r..r) for (dy in -r..r) {
            if (dx * dx + dy * dy <= r * r + 1) state.travel.explored += Cell(center.x + dx, center.y + dy).key()
        }
    }

    fun isExplored(state: GameState, c: Cell) = c.key() in state.travel.explored

    private fun lineCells(from: Cell, to: Cell): List<Cell> {
        val out = ArrayList<Cell>()
        var x = from.x; var y = from.y
        val dx = abs(to.x - x); val dy = abs(to.y - y)
        val sx = if (to.x > x) 1 else -1; val sy = if (to.y > y) 1 else -1
        var err = dx - dy
        while (x != to.x || y != to.y) {
            val e2 = 2 * err
            if (e2 > -dy) { err -= dy; x += sx }
            if (e2 < dx) { err += dx; y += sy }
            out += Cell(x, y)
        }
        return out
    }

    private fun stepCost(state: GameState, c: Cell, safe: Boolean): Double {
        val b = state.world.biomeAt(c)
        val unknown = if (isExplored(state, c)) 0.0 else 0.4
        return if (safe) 1.0 + b.danger * 9.0 + unknown * 2 + (1.0 / b.moveFactor - 1.0) * 0.6
        else 1.0 / CarrierSystem.terrainFactor(state, b)
    }

    /** 8 yönlü A*; [safe] ise tehlikeden kaçınır, değilse en hızlı yolu arar. */
    private fun aStar(state: GameState, from: Cell, to: Cell, safe: Boolean): List<Cell> {
        val minX = minOf(from.x, to.x) - 6; val maxX = maxOf(from.x, to.x) + 6
        val minY = minOf(from.y, to.y) - 6; val maxY = maxOf(from.y, to.y) + 6
        val g = HashMap<Long, Double>()
        val parent = HashMap<Long, Long>()
        val open = PriorityQueue<Pair<Double, Cell>>(compareBy { it.first })
        g[from.key()] = 0.0
        open += 0.0 to from
        val minStep = if (safe) 1.0 else 1.0
        while (open.isNotEmpty()) {
            val (_, cur) = open.poll()
            if (cur == to) break
            val gc = g[cur.key()] ?: continue
            for (dx in -1..1) for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue
                val n = Cell(cur.x + dx, cur.y + dy)
                if (n.x !in minX..maxX || n.y !in minY..maxY) continue
                val ng = gc + stepCost(state, n, safe)
                if (ng < (g[n.key()] ?: Double.MAX_VALUE)) {
                    g[n.key()] = ng
                    parent[n.key()] = cur.key()
                    open += (ng + n.dist(to) * minStep) to n
                }
            }
        }
        if (to.key() !in parent) return lineCells(from, to)
        val path = ArrayList<Cell>()
        var k = to.key()
        while (k != from.key()) {
            path += Cell.fromKey(k)
            k = parent[k] ?: break
        }
        path.reverse()
        return path
    }

    fun evaluate(state: GameState, name: String, cells: List<Cell>): RouteOption {
        val report = WeightBalanceSystem.compute(state)
        val speed = CarrierSystem.speed(state, report).coerceAtLeast(0.05)
        var minutes = 0.0
        var danger = 0.0
        val biomes = LinkedHashMap<Biome, Int>()
        var food = 0.0
        var water = 0.0
        val loadFactor = 0.6 + 0.8 * report.loadRatio.coerceAtMost(2.0)
        val needs = CarrierSystem.needMultiplier(state)
        for (c in cells) {
            val b = state.world.biomeAt(c)
            val m = 1.0 / (speed * CarrierSystem.terrainFactor(state, b))
            minutes += m
            danger += b.danger
            biomes[b] = (biomes[b] ?: 0) + 1
            val thirst = if (b == Biome.DESERT || b == Biome.VOLCANIC) 1.5 else 1.0
            food += m * (5.0 * loadFactor * needs * CarrierSystem.FOOD_PER_POINT + state.population.count * PopulationSystem.FOOD_PER_POP)
            water += m * (5.5 * loadFactor * needs * thirst * CarrierSystem.WATER_PER_POINT + state.population.count * PopulationSystem.WATER_PER_POP)
        }
        return RouteOption(name, cells, minutes, food, water, if (cells.isEmpty()) 0.0 else danger / cells.size, biomes)
    }

    fun planRoutes(state: GameState, target: Cell): List<RouteOption> {
        val from = state.travel.position
        if (target == from) return emptyList()
        val fast = evaluate(state, "Hızlı Rota", aStar(state, from, target, safe = false))
        val safe = evaluate(state, "Güvenli Rota", aStar(state, from, target, safe = true))
        return if (fast.cells == safe.cells) listOf(fast.copy(name = "Önerilen Rota")) else listOf(fast, safe)
    }

    fun canTarget(state: GameState, target: Cell): String? = when {
        target == state.travel.position -> "Şehir zaten burada."
        target.dist(state.travel.position) > MAX_RANGE -> "Hedef çok uzak (en fazla $MAX_RANGE bölge)."
        !isExplored(state, target) -> "Önce bu bölgeyi keşfetmelisiniz."
        else -> null
    }

    fun start(state: GameState, route: RouteOption): ActionResult {
        if (route.cells.isEmpty()) return ActionResult.fail("Geçersiz rota.")
        canTarget(state, route.cells.last())?.let { return ActionResult.fail(it) }
        if (state.carrier.forcedRest) return ActionResult.fail("${state.carrier.name} çok yorgun; önce dinlenmeli.")
        if (state.store[FOOD] < route.foodCost * 0.5 || state.store[WATER] < route.waterCost * 0.5)
            return ActionResult.fail("Yetersiz erzak: bu yolculuk için en az ${fmt(route.foodCost * 0.5)} yiyecek ve ${fmt(route.waterCost * 0.5)} su gerekli.")
        val t = state.travel
        t.route.clear()
        t.route += route.cells
        t.routeIndex = 0
        t.progress = 0.0
        t.routeName = route.name
        state.carrier.resting = false
        val dest = route.cells.last()
        state.log("Yolculuk başladı: ${route.name}, ${route.distance} bölge, hedef ${state.world.biomeAt(dest).title}.", LogKind.TRAVEL)
        return ActionResult.ok("${state.carrier.name} yola çıktı!")
    }

    fun stop(state: GameState): ActionResult {
        val t = state.travel
        if (!t.traveling) return ActionResult.fail("Şehir zaten duruyor.")
        t.route.clear(); t.routeIndex = 0; t.progress = 0.0
        state.log("Yolculuk durduruldu; şehir ${state.world.biomeAt(t.position).title} bölgesinde kamp kurdu.", LogKind.TRAVEL)
        return ActionResult.ok("Yolculuk durduruldu.")
    }

    fun remainingMinutes(state: GameState): Double {
        val t = state.travel
        if (!t.traveling) return 0.0
        val report = WeightBalanceSystem.compute(state)
        val speed = CarrierSystem.speed(state, report).coerceAtLeast(0.05)
        var m = 0.0
        for (i in t.routeIndex until t.route.size) {
            val f = 1.0 / (speed * CarrierSystem.terrainFactor(state, state.world.biomeAt(t.route[i])))
            m += if (i == t.routeIndex) f * (1 - t.progress) else f
        }
        return m
    }

    fun step(state: GameState, dt: Double, report: BalanceReport) {
        val t = state.travel
        if (!t.traveling || !CarrierSystem.canMove(state) || state.pendingEvent != null || state.battle != null) return
        val next = t.route[t.routeIndex]
        val biome = state.world.biomeAt(next)
        t.progress += CarrierSystem.speed(state, report) * CarrierSystem.terrainFactor(state, biome) * dt / 60.0
        if (t.progress >= 1.0) {
            t.progress = 0.0
            enterCell(state, next, report)
        }
    }

    private fun enterCell(state: GameState, cell: Cell, report: BalanceReport) {
        val t = state.travel
        t.position = cell
        t.routeIndex++
        t.distanceTraveled++
        reveal(state, cell)
        CarrierSystem.addXp(state, 10.0)
        val biome = state.world.biomeAt(cell)
        stabilityCheck(state, biome, report)
        if (t.routeIndex >= t.route.size) {
            t.route.clear(); t.routeIndex = 0; t.progress = 0.0
            state.log("Varış: ${biome.title}. ${biome.hint}", LogKind.TRAVEL)
            CarrierSystem.addXp(state, 15.0)
        }
        CivSystem.onEnterCell(state, cell)
        EventSystem.maybeTrigger(state, biome)
    }

    /** Dengesiz veya aşırı yüklü şehirlerde zorlu arazide yük kayması olabilir. */
    private fun stabilityCheck(state: GameState, biome: Biome, report: BalanceReport) {
        var chance = (1.0 - report.balance) * biome.roughness * 0.35
        if (report.overloaded) chance += 0.12 * biome.roughness
        if (state.nextRandom() >= chance) return
        var dmg = 6.0 + state.nextRandom() * 8.0
        if (ResearchSystem.has(state, TechId.SHOCK_ABSORBERS)) dmg *= 0.6
        val hit = EventSystem.damageBuildings(state, 1, dmg, heavySide = report)
        if (hit.isNotEmpty()) state.log("Yük kayması! ${hit.joinToString()} hasar aldı. Dengeyi iyileştirin.", LogKind.WARNING)
    }
}

object EventSystem {
    const val COOLDOWN = 90.0

    fun damageMultiplier(state: GameState): Double {
        var m = 1.0 - 0.10 * state.carrier.upgrade(CarrierUpgrade.DEFENSE)
        if (ResearchSystem.has(state, TechId.STRONG_FOUNDATIONS)) m *= 0.7
        if (ResearchSystem.has(state, TechId.WATCHMEN)) m *= 0.75
        return (m * Bonuses.damage(state)).coerceAtLeast(0.15)
    }

    /**
     * Rastgele binalara adil hasar verir: can %20'nin altına düşmez, şehir asla yok olmaz.
     * [heavySide] verilirse ağırlık merkezine yakın taraftaki binalar seçilir.
     */
    fun damageBuildings(state: GameState, count: Int, amount: Double, heavySide: BalanceReport? = null): List<String> {
        val candidates = state.buildings.filter { !it.underConstruction }.toMutableList()
        if (candidates.isEmpty()) return emptyList()
        if (heavySide != null) {
            candidates.sortByDescending { (it.centerX - CityGrid.CENTER_X) * heavySide.offsetX + (it.centerY - CityGrid.CENTER_Y) * heavySide.offsetY }
            while (candidates.size > 3) candidates.removeAt(candidates.size - 1)
        }
        val hit = ArrayList<String>()
        repeat(count.coerceAtMost(candidates.size)) {
            val b = candidates.removeAt((state.nextRandom() * candidates.size).toInt().coerceAtMost(candidates.size - 1))
            b.hp = (b.hp - amount * damageMultiplier(state)).coerceAtLeast(20.0)
            hit += b.def.title
        }
        return hit
    }

    fun maybeTrigger(state: GameState, biome: Biome) {
        if (state.pendingEvent != null || state.eventCooldown > 0 || state.battle != null) return
        var chance = 0.10 + biome.danger * 0.30
        if (state.scoutedSeconds > 0) chance *= 0.5
        if (state.nextRandom() >= chance) return
        state.pendingEvent = create(state, biome)
        state.eventCooldown = COOLDOWN
    }

    fun create(state: GameState, biome: Biome): PendingEvent {
        val pool = ArrayList<Pair<String, Double>>()
        pool += "find" to 3.0
        pool += "caravan" to 2.0
        pool += "nomads" to 1.5
        pool += "sickness" to 1.0
        pool += "signal" to 0.8
        pool += "storm" to (0.5 + 3.0 * if (biome == Biome.STORMLANDS || biome == Biome.SNOW || biome == Biome.MOUNTAINS || biome == Biome.DESERT) 1.0 else 0.0)
        pool += "predators" to if (biome == Biome.FOREST || biome == Biome.MYSTIC_FOREST || biome == Biome.SWAMP) 2.5 else 0.4
        pool += "ruins" to if (biome == Biome.RUINS || biome == Biome.MYSTIC_FOREST) 3.0 else 0.3
        pool += "tech" to if (biome == Biome.RUINS || biome == Biome.CRYSTAL_VALLEY) 2.0 else 0.1
        pool += "bandits" to (0.3 + 2.5 * biome.danger)
        pool += "mech" to if (biome == Biome.RUINS || biome == Biome.STORMLANDS || biome == Biome.CRYSTAL_VALLEY) 1.5 else 0.0
        val total = pool.sumOf { it.second }
        var r = state.nextRandom() * total
        var kind = pool.first().first
        for ((k, w) in pool) { if (r < w) { kind = k; break }; r -= w }
        val rnd = { lo: Int, hi: Int -> (lo + state.nextRandom() * (hi - lo)).roundToInt() }
        return when (kind) {
            "storm" -> PendingEvent(kind, "Fırtına Yaklaşıyor!", "Ufukta kara bulutlar toplandı. ${state.carrier.name} huzursuz.", listOf(
                EventOption("Bezleri ger", cost = amounts(Resource.CLOTH to 15), effect = "damage=1x5"),
                EventOption("Göğüs ger", effect = "damage=2x16"),
                EventOption("Sığınağa çekil (yolculuk durur)", effect = "stop;rest"),
            ))
            "find" -> {
                val main = biome.yields.maxByOrNull { it.value }?.key ?: Resource.WOOD
                val amt = rnd(30, 70).toDouble()
                PendingEvent(kind, "Kaynak Bulundu", "Keşifçiler yol kenarında ${main.title.lowercase()} yatağı buldu.", listOf(
                    EventOption("Topla", reward = amounts(main to amt), effect = "fatigue=8"),
                    EventOption("Devam et"),
                ))
            }
            "caravan" -> PendingEvent(kind, "Ticaret Kervanı", "Gezgin tüccarlar alışveriş teklif ediyor.", listOf(
                EventOption("Su al", cost = amounts(GOLD to 30), reward = amounts(WATER to 70)),
                EventOption("Yiyecek sat", cost = amounts(FOOD to 50), reward = amounts(GOLD to 45)),
                EventOption("Alet al", cost = amounts(GOLD to 40), reward = amounts(TOOLS to 12)),
                EventOption("Selamla geç"),
            ))
            "nomads" -> {
                val n = rnd(2, 4)
                PendingEvent(kind, "Göçebe Topluluk", "$n kişilik yorgun bir aile şehrinize katılmak istiyor.", listOf(
                    EventOption("Kabul et", cost = amounts(FOOD to 25), effect = "pop=$n"),
                    EventOption("Erzak ver", cost = amounts(FOOD to 20, WATER to 20), effect = "research=10;trust=3"),
                    EventOption("Geri çevir"),
                ))
            }
            "sickness" -> PendingEvent(kind, "Taşıyıcı Hastalığı", "${state.carrier.name} öksürüyor ve halsiz görünüyor.", listOf(
                EventOption("Şifalı bakım", cost = amounts(FOOD to 30, WATER to 30), effect = "health=5"),
                EventOption("Bakım Özü kullan", cost = amounts(CARE_ESSENCE to 4), effect = "health=20;trust=5"),
                EventOption("Kendiliğinden geçsin", effect = "health=-20;trust=-8"),
            ))
            "signal" -> PendingEvent(kind, "Gizemli Sinyal", "Uzaklarda parıldayan bir ışık yanıp sönüyor.", listOf(
                EventOption("İzini sür", reward = amounts(CRYSTAL to rnd(4, 9), TECH_PART to 2), effect = "fatigue=15"),
                EventOption("Görmezden gel"),
            ))
            "predators" -> {
                val n = CombatSystem.scaledCount(state, 2, biome.danger)
                PendingEvent(kind, "Yırtıcı Sürü", "$n aç yırtıcı taşıyıcının sırtına tırmanmaya çalışıyor.", listOf(
                    EventOption("Savun", effect = "battle=BEAST:$n"),
                    EventOption("Yiyecekle oyala", cost = amounts(FOOD to 35)),
                ))
            }
            "bandits" -> {
                val n = CombatSystem.scaledCount(state, 2, biome.danger)
                PendingEvent(kind, "Haydut Çetesi", "$n haydut depolarınıza göz dikti. Savunma gücünüz ${CombatSystem.defenseRating(state).toInt()}.", listOf(
                    EventOption("Savun", effect = "battle=BANDIT:$n"),
                    EventOption("Haraç öde", cost = amounts(GOLD to n * 10)),
                    EventOption("Hızla uzaklaş", effect = "fatigue=25"),
                ))
            }
            "mech" -> {
                val n = CombatSystem.scaledCount(state, 1, biome.danger)
                PendingEvent(kind, "Mekanik Gözcüler", "Eski çağdan kalma $n mekanik gözcü uyandı ve kulelerinize yöneliyor.", listOf(
                    EventOption("Savun", effect = "battle=MECH:$n"),
                    EventOption("Hızla uzaklaş", effect = "fatigue=30"),
                ))
            }
            "ruins" -> PendingEvent(kind, "Antik Kalıntı", "Toprağın altından eski bir yapının kubbesi görünüyor.", listOf(
                EventOption("Kazı yap", reward = amounts(ANCIENT_PART to rnd(3, 6), STONE to 30), effect = "research=25;fatigue=10;artifact=0.25"),
                EventOption("Geç"),
            ))
            else -> PendingEvent("tech", "Nadir Teknoloji", "Eski bir makinenin parçaları kristallerin arasında parlıyor.", listOf(
                EventOption("Sök", cost = amounts(TOOLS to 15), reward = amounts(TECH_PART to 5, LIGHT_MATERIAL to 10)),
                EventOption("Bırak"),
            ))
        }
    }

    fun resolve(state: GameState, optionIndex: Int): ActionResult {
        val ev = state.pendingEvent ?: return ActionResult.fail("Bekleyen olay yok.")
        val opt = ev.options.getOrNull(optionIndex) ?: return ActionResult.fail("Geçersiz seçim.")
        if (!state.store.canAfford(opt.cost)) return ActionResult.fail("Yetersiz kaynak: ${opt.cost.describe()}")
        state.store.spend(opt.cost)
        val notes = ArrayList<String>()
        opt.reward.forEach { (r, v) -> val got = state.store.add(r, v); if (got > 0) notes += "+${fmt(got)} ${r.title}" }
        for (part in opt.effect.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
            val (key, value) = if ('=' in part) part.substringBefore('=') to part.substringAfter('=') else part to ""
            when (key) {
                "damage" -> {
                    val n = value.substringBefore('x').toInt(); val amt = value.substringAfter('x').toDouble()
                    val hit = damageBuildings(state, n, amt)
                    if (hit.isNotEmpty()) notes += "Hasar: ${hit.joinToString()}"
                }
                "stop" -> if (state.travel.traveling) TravelSystem.stop(state)
                "rest" -> state.carrier.resting = true
                "pop" -> { state.population.count += value.toInt(); notes += "+$value nüfus"; PopulationSystem.autoAssign(state) }
                "research" -> { ResearchSystem.addPoints(state, value.toDouble()); notes += "+$value bilgi" }
                "trust" -> state.carrier.trust = (state.carrier.trust + value.toDouble()).coerceIn(0.0, 100.0)
                "health" -> state.carrier.health = (state.carrier.health + value.toDouble()).coerceIn(CarrierSystem.HEALTH_FLOOR, CarrierSystem.maxHealth(state))
                "fatigue" -> state.carrier.fatigue = (state.carrier.fatigue + value.toDouble()).coerceIn(0.0, 100.0)
                "artifact" -> CollectionSystem.tryFindArtifact(state, value.toDouble())?.let { notes += "Antik eser: ${it.title}" }
                "battle" -> {
                    val spec = CombatSystem.parseSpec(value.substringBefore('@'))
                    val civId = if ('@' in value) value.substringAfter('@').toIntOrNull() ?: -1 else -1
                    CombatSystem.start(state, ev.title, spec, civId)
                }
                "rel" -> CivSystem.civ(state, value.substringBefore(':').toInt())?.let { c ->
                    c.relation = (c.relation + value.substringAfter(':').toDouble()).coerceIn(-100.0, 100.0)
                }
            }
        }
        state.pendingEvent = null
        state.stats.eventsResolved++
        val summary = "${ev.title}: ${opt.label}" + if (notes.isNotEmpty()) " (${notes.joinToString(", ")})" else ""
        state.log(summary, LogKind.EVENT)
        return ActionResult.ok(summary)
    }
}
