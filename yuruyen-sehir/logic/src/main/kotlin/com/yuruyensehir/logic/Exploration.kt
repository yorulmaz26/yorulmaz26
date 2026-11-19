package com.yuruyensehir.logic

import com.yuruyensehir.logic.Resource.*
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
enum class MissionType(val title: String, val description: String, val baseRisk: Double, val extraCost: Amounts = emptyMap()) {
    RESOURCES("Kaynak Bulma", "Hedef bölgenin kaynaklarından toplayıp getirir ve çevresini haritalar.", 0.08),
    RUINS("Eski Yapı Araştırma", "Kalıntılarda antik parça ve bilgi arar. Kalıntı biyomunda verim iki katı.", 0.18, amounts(TOOLS to 5)),
    TRADE_SCOUT("Ticaret Merkezi Keşfi", "Geniş bir alanı haritalar ve oradaki medeniyetlerle tanışır.", 0.10),
    CIVILIZATION("Yeni Medeniyet Bulma", "Hedefin ötesindeki bölgelerde henüz tanınmayan bir medeniyeti arar.", 0.15),
    DANGER("Tehlike Tespiti", "Çevreyi gözler: 10 dakika boyunca yol olayları daha seyrek olur.", 0.06),
    TECH("Nadir Teknoloji Arama", "Teknoloji bileşeni ve hafif malzeme arar. Kalıntı ve kristal vadilerinde verimlidir.", 0.22, amounts(TOOLS to 8)),
}

@Serializable
data class Expedition(
    val id: Int,
    val type: MissionType,
    val target: Cell,
    val team: Int,
    var secondsLeft: Double,
    val totalSeconds: Double,
    val risk: Double,
)

/** Keşif ekipleri: risk ve ödül içeren, çevrimdışı da ilerleyen görevler. */
object ExplorationSystem {
    const val TEAM = 3
    const val MAX_RANGE = 12
    const val MIN_RANGE = 2

    fun center(state: GameState) = state.buildings.firstOrNull { it.type == BuildingType.EXPLORATION_CENTER && CombatSystem.staffed(it) }

    fun slots(state: GameState) = center(state)?.level ?: 0

    fun cost(type: MissionType): Amounts = (amounts(FOOD to 20, WATER to 20) + type.extraCost)

    private operator fun Amounts.plus(o: Amounts): Amounts {
        val m = toMutableMap(); o.forEach { (k, v) -> m[k] = (m[k] ?: 0.0) + v }; return m
    }

    fun risk(state: GameState, type: MissionType, target: Cell): Double {
        val biome = state.world.biomeAt(target)
        var r = type.baseRisk + biome.danger * 0.5 + target.dist(state.travel.position) * 0.012
        center(state)?.let { r -= 0.07 * (it.level - 1) }
        if (ResearchSystem.has(state, TechId.EXPEDITION_GEAR)) r -= 0.1
        if (CivSystem.met(state).any { it.status == Relation.ALLY && it.position.dist(target) <= 4 }) r -= 0.08
        return r.coerceIn(0.03, 0.8)
    }

    fun duration(state: GameState, target: Cell): Double {
        var t = 50.0 + target.dist(state.travel.position) * 18.0
        if (ResearchSystem.has(state, TechId.EXPEDITION_GEAR)) t *= 0.8
        return t
    }

    fun reason(state: GameState, type: MissionType, target: Cell): String? {
        val d = target.dist(state.travel.position)
        return when {
            center(state) == null -> "Keşif ekibi göndermek için çalışan bir Keşif Merkezi gerekli."
            state.expeditions.size >= slots(state) -> "Bütün keşif ekipleri görevde (${slots(state)} ekip)."
            d < MIN_RANGE -> "Hedef çok yakın (en az $MIN_RANGE bölge)."
            d > MAX_RANGE -> "Hedef çok uzak (en fazla $MAX_RANGE bölge)."
            PopulationSystem.freeWorkers(state) < TEAM -> "Ekip için $TEAM boşta yerleşimci gerekli."
            !state.store.canAfford(cost(type)) -> "Erzak yetersiz: ${cost(type).describe()}"
            else -> null
        }
    }

    fun send(state: GameState, type: MissionType, target: Cell): ActionResult {
        reason(state, type, target)?.let { return ActionResult.fail(it) }
        state.store.spend(cost(type))
        val t = duration(state, target)
        state.expeditions += Expedition(state.nextExpeditionId++, type, target, TEAM, t, t, risk(state, type, target))
        state.population.away += TEAM
        state.log("Keşif ekibi yola çıktı: ${type.title} (${target.dist(state.travel.position)} bölge).", LogKind.EXPLORATION)
        return ActionResult.ok("${type.title} ekibi yola çıktı (~${(t / 60).roundToInt().coerceAtLeast(1)} dk).")
    }

    fun step(state: GameState, dt: Double, offline: Boolean) {
        if (state.scoutedSeconds > 0) state.scoutedSeconds = (state.scoutedSeconds - dt).coerceAtLeast(0.0)
        val done = ArrayList<Expedition>()
        for (e in state.expeditions) { e.secondsLeft -= dt; if (e.secondsLeft <= 0) done += e }
        for (e in done) {
            state.expeditions.remove(e)
            state.population.away = (state.population.away - e.team).coerceAtLeast(0)
            resolve(state, e, offline)
            state.stats.expeditionsDone++
        }
    }

    private fun revealAround(state: GameState, c: Cell, r: Int) {
        for (dx in -r..r) for (dy in -r..r) if (dx * dx + dy * dy <= r * r + 1) state.travel.explored += Cell(c.x + dx, c.y + dy).key()
    }

    private fun rnd(state: GameState, lo: Int, hi: Int) = (lo + state.nextRandom() * (hi - lo)).roundToInt().toDouble()

    private fun resolve(state: GameState, e: Expedition, offline: Boolean) {
        val success = state.nextRandom() >= e.risk
        val biome = state.world.biomeAt(e.target)
        val gains = LinkedHashMap<Resource, Double>()
        fun give(r: Resource, v: Double) { val got = state.store.add(r, v); if (got > 0) gains[r] = (gains[r] ?: 0.0) + got }
        if (!success) {
            var msg = "Keşif ekibi (${e.type.title}) zorluklarla karşılaştı ve eli boş döndü."
            if (state.nextRandom() < 0.3 && state.population.count > PopulationSystem.MIN_POPULATION) {
                state.population.count--
                PopulationSystem.releaseExcess(state)
                msg += " Bir keşifçi geri dönmedi."
            }
            revealAround(state, e.target, 1)
            state.log(msg, LogKind.WARNING)
            return
        }
        var extra = ""
        when (e.type) {
            MissionType.RESOURCES -> {
                revealAround(state, e.target, 2)
                val amount = rnd(state, 50, 90)
                biome.yields.forEach { (r, share) -> give(r, amount * share * 1.4) }
            }
            MissionType.RUINS -> {
                revealAround(state, e.target, 2)
                val m = if (biome == Biome.RUINS) 2.0 else 1.0
                give(ANCIENT_PART, rnd(state, 2, 5) * m)
                ResearchSystem.addPoints(state, 15.0 * m); extra = " +${(15 * m).toInt()} bilgi"
            }
            MissionType.TRADE_SCOUT -> {
                revealAround(state, e.target, 3)
                CivSystem.ensureAround(state, e.target)
                val found = CivSystem.meetNear(state, e.target, 4, offline)
                give(GOLD, 15.0)
                if (found > 0) extra = " $found medeniyetle tanışıldı."
            }
            MissionType.CIVILIZATION -> {
                revealAround(state, e.target, 2)
                CivSystem.ensureAround(state, e.target, radius = 2)
                val civ = state.civs.filter { !it.met }.minByOrNull { it.position.dist(e.target) }
                if (civ != null && civ.position.dist(e.target) <= 14) {
                    civ.met = true
                    revealAround(state, civ.position, 1)
                    extra = " Yeni medeniyet bulundu: ${civ.name} (${civ.culture.title})."
                    if (!offline) state.log("Keşif ekibi ${civ.name} ile tanıştı.", LogKind.DIPLOMACY)
                } else extra = " Yakında bilinmeyen medeniyet yok."
            }
            MissionType.DANGER -> {
                revealAround(state, e.target, 3)
                state.scoutedSeconds = 600.0
                extra = " 10 dakika boyunca yol olayları daha seyrek."
            }
            MissionType.TECH -> {
                revealAround(state, e.target, 1)
                val m = if (biome == Biome.RUINS || biome == Biome.CRYSTAL_VALLEY) 2.0 else 1.0
                give(TECH_PART, rnd(state, 1, 3) * m)
                give(LIGHT_MATERIAL, rnd(state, 3, 6) * m)
                give(CRYSTAL, rnd(state, 1, 4) * m)
            }
        }
        state.stats.gathered += gains.values.sum()
        state.log("Keşif ekibi döndü (${e.type.title}, ${biome.title}): " +
            (if (gains.isEmpty()) "kaynak yok." else gains.entries.joinToString { "+${fmt(it.value)} ${it.key.title}" }) + extra, LogKind.EXPLORATION)
    }
}
