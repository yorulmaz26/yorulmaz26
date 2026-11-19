package com.yuruyensehir.logic

import com.yuruyensehir.logic.Resource.*
import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.roundToInt

// ====================================================================== Taşıyıcı türleri

@Serializable
enum class CarrierSpecies(
    val title: String,
    val trait: String,
    val description: String,
    val capacityMult: Double = 1.0,
    val speedMult: Double = 1.0,
    val damageMult: Double = 1.0,
    val needMult: Double = 1.0,
    val thirstMult: Double = 1.0,
    val forageMult: Double = 1.0,
    /** Sırt alanı farkı (-1 küçük … +1 büyük). */
    val backBonus: Int = 0,
    val adapted: Set<Biome> = emptySet(),
    /** Keşif ekiplerinin bu türü bulabileceği biyomlar. */
    val habitat: Set<Biome> = emptySet(),
    val minStage: Int = 0,
) {
    GIANT_TORTOISE("Dev Kara Kaplumbağası", "Dengeli", "Sakin, dayanıklı ve her işe uygun.", habitat = setOf(Biome.PLAINS)),
    CRYSTAL_HORN("Kristal Boynuzlu Dev", "Yüksek savunma", "Kristal boynuzları saldırıları savuşturur: hasar %40 azalır, biraz yavaştır.",
        speedMult = 0.9, damageMult = 0.6, needMult = 1.1, adapted = setOf(Biome.CRYSTAL_VALLEY), habitat = setOf(Biome.CRYSTAL_VALLEY)),
    LONG_LEGS("Uzun Bacaklı Gezgin", "Hızlı hareket", "Çok hızlı yürür ama sırtı dardır ve az taşır.",
        capacityMult = 0.75, speedMult = 1.45, needMult = 1.05, backBonus = -1, habitat = setOf(Biome.PLAINS, Biome.LAKE)),
    ROCK_BACK("Kaya Sırtlı Dev", "Yüksek taşıma kapasitesi", "Kayalık sırtı geniştir ve çok yük taşır; yavaştır, çok yer.",
        capacityMult = 1.5, speedMult = 0.75, needMult = 1.25, backBonus = 1, adapted = setOf(Biome.MOUNTAINS), habitat = setOf(Biome.MOUNTAINS, Biome.VOLCANIC)),
    FOREST_GUARDIAN("Orman Muhafızı", "Doğa uyumu", "Dinlenirken kendini çok iyi besler; ormanda ve bataklıkta yavaşlamaz.",
        forageMult = 1.7, adapted = setOf(Biome.FOREST, Biome.MYSTIC_FOREST, Biome.SWAMP), habitat = setOf(Biome.FOREST, Biome.MYSTIC_FOREST)),
    DESERT_WANDERER("Çöl Gezgini", "Su tasarrufu", "Susuzluğu yarı yarıya azdır; çölde ve volkanik arazide yavaşlamaz.",
        thirstMult = 0.5, adapted = setOf(Biome.DESERT, Biome.VOLCANIC), habitat = setOf(Biome.DESERT)),
    ICE_WALKER("Buz Yürüyücüsü", "Soğuk iklim", "Karda ve dağda yavaşlamaz, soğuktan yorulmaz.",
        speedMult = 0.95, adapted = setOf(Biome.SNOW, Biome.MOUNTAINS), habitat = setOf(Biome.SNOW)),
    ANCIENT_CARRIER("Kadim Taşıyıcı", "Özel yetenekler", "Efsanevi bir dev: her yönden güçlüdür, yetenekleri bir seviye erken açılır.",
        capacityMult = 1.2, speedMult = 1.1, damageMult = 0.8, needMult = 0.9, backBonus = 1, habitat = setOf(Biome.RUINS), minStage = 4);
}

@Serializable
enum class Artifact(val title: String, val description: String) {
    SUN_COMPASS("Güneş Pusulası", "Yolculuk hızı +%5."),
    SEED_POUCH("Kadim Tohum Kesesi", "Tarla ve sera verimi +%8."),
    MASON_SEAL("Taş Ustasının Mührü", "Bina ağırlıkları %4 azalır."),
    STORM_CRYSTAL("Fırtına Kristali", "Enerji üretimi +%10."),
    SAGE_SCROLL("Bilge Parşömeni", "Bilgi üretimi +%10."),
    GIANT_SCALE("Dev Kalkan Pulu", "Bina hasarı %6 azalır."),
    MERCHANT_SCALES("Tüccar Terazisi", "Satış fiyatları +%5."),
    WATER_SPIRIT_STONE("Su Ruhunun Taşı", "Su toplama +%8."),
    OLD_MAP("Eski Dünya Haritası", "Keşif menzili +1."),
    BONE_FLUTE("Kadim Kemik Flüt", "Taşıyıcının açlık ve susuzluğu %6 azalır."),
}

@Serializable
data class CollectionState(
    val species: MutableSet<CarrierSpecies> = mutableSetOf(CarrierSpecies.GIANT_TORTOISE),
    val artifacts: MutableSet<Artifact> = mutableSetOf(),
)

object CollectionSystem {
    fun has(state: GameState, a: Artifact) = a in state.collection.artifacts

    fun tryFindArtifact(state: GameState, chance: Double, offline: Boolean = false): Artifact? {
        val missing = Artifact.entries.filter { it !in state.collection.artifacts }
        if (missing.isEmpty() || state.nextRandom() >= chance) return null
        val a = missing[(state.nextRandom() * missing.size).toInt().coerceAtMost(missing.size - 1)]
        state.collection.artifacts += a
        if (!offline) state.log("Koleksiyona yeni antik eser eklendi: ${a.title}! ${a.description}", LogKind.STAGE)
        state.cityVersion++
        return a
    }

    /** Keşif ekibi hedef biyomda yaşayan, henüz bilinmeyen bir taşıyıcı türünü arar. */
    fun searchSpecies(state: GameState, biome: Biome, offline: Boolean): CarrierSpecies? {
        val candidates = CarrierSpecies.entries.filter { it !in state.collection.species && state.stage >= it.minStage }
        val local = candidates.filter { biome in it.habitat }
        val found = when {
            local.isNotEmpty() -> local.first()
            candidates.isNotEmpty() && state.nextRandom() < 0.15 -> candidates.filter { it != CarrierSpecies.ANCIENT_CARRIER }.randomOrNullBy(state)
            else -> null
        } ?: return null
        state.collection.species += found
        if (!offline) state.log("Yeni taşıyıcı türü keşfedildi: ${found.title} (${found.trait})!", LogKind.CARRIER)
        state.cityVersion++
        return found
    }

    private fun <T> List<T>.randomOrNullBy(state: GameState): T? =
        if (isEmpty()) null else this[(state.nextRandom() * size).toInt().coerceAtMost(size - 1)]

    fun inUse(state: GameState, s: CarrierSpecies) = state.carrier.kind == s || state.sideCities.any { it.species == s }

    fun switchCost(): Amounts = amounts(FOOD to 200, WATER to 200, CARE_ESSENCE to 10)

    /** Uyumlu yapı aktarımı: bütün binalar yeni sırta sığmalı; şehir sıfırlanmaz, geliştirmeler korunur. */
    fun switchReason(state: GameState, s: CarrierSpecies): String? {
        if (s !in state.collection.species) return "Bu tür henüz keşfedilmedi."
        if (state.carrier.kind == s) return "Zaten bu taşıyıcıdasınız."
        if (state.sideCities.any { it.species == s }) return "Bu tür bir yan şehri taşıyor."
        if (state.battle != null) return "Savaş sırasında taşıyıcı değiştirilemez."
        if (state.travel.traveling) return "Önce yolculuğu durdurun."
        val misfit = misfits(state, s)
        if (misfit.isNotEmpty()) return "Yeni sırta sığmayan binalar: ${misfit.joinToString { it.def.title }}. Önce taşıyın veya sökün."
        if (!state.store.canAfford(switchCost())) return "Yetersiz kaynak: ${switchCost().describe()}"
        return null
    }

    fun misfits(state: GameState, s: CarrierSpecies): List<PlacedBuilding> {
        val old = state.carrier.kind
        state.carrier.kind = s
        val bad = state.buildings.filter { b -> (b.x until b.x + b.w).any { x -> (b.y until b.y + b.h).any { y -> !CityGrid.usable(state, x, y) } } }
        state.carrier.kind = old
        return bad
    }

    fun switchCarrier(state: GameState, s: CarrierSpecies): ActionResult {
        switchReason(state, s)?.let { return ActionResult.fail(it) }
        state.store.spend(switchCost())
        val c = state.carrier
        val old = c.kind
        c.kind = s
        c.health = CarrierSystem.maxHealth(state); c.hunger = 10.0; c.thirst = 10.0; c.fatigue = 0.0; c.energy = 100.0
        c.trust = (c.trust * 0.7).coerceAtLeast(40.0)
        state.cityVersion++
        ResourceSystem.recalcCapacity(state)
        state.log("Şehir ${old.title} sırtından ${s.title} sırtına aktarıldı. Bütün binalar ve geliştirmeler korundu.", LogKind.CARRIER)
        return ActionResult.ok("Yeni taşıyıcı: ${s.title}")
    }
}

// ====================================================================== Uzmanlaşma

@Serializable
enum class Specialization(val title: String, val description: String) {
    ECONOMY("Ekonomi Şehri", "Bütün üretim +%8, satış fiyatları +%10."),
    MILITARY("Askerî Şehir", "Kule hasarı +%20, bina hasarı %15 azalır."),
    EXPLORATION("Keşif Şehri", "Yolculuk hızı +%10, keşif menzili +1, keşif görevi riski azalır."),
    TECHNOLOGY("Teknoloji Şehri", "Bilgi üretimi +%25, enerji üretimi +%15."),
    NATURE("Doğa Şehri", "Yiyecek ve su üretimi +%15, taşıyıcının ihtiyaçları %10 azalır."),
}

object SpecializationSystem {
    fun has(state: GameState, s: Specialization) = s in state.specializations

    fun slots(state: GameState) = when { state.stage >= 4 -> 2; state.stage >= 2 -> 1; else -> 0 }

    fun reason(state: GameState, s: Specialization): String? = when {
        has(state, s) -> "Zaten seçildi."
        state.specializations.size >= slots(state) -> if (slots(state) == 0) "Uzmanlaşma ${StageSystem.title(2)} aşamasında açılır." else "Yeni uzmanlık ${StageSystem.title(4)} aşamasında açılır (hibrit strateji)."
        else -> null
    }

    fun choose(state: GameState, s: Specialization): ActionResult {
        reason(state, s)?.let { return ActionResult.fail(it) }
        state.specializations += s
        state.cityVersion++
        state.log("Medeniyetiniz bir yön seçti: ${s.title}. ${s.description}", LogKind.STAGE)
        return ActionResult.ok("${s.title} seçildi.")
    }
}

/** Uzmanlık, eser ve ustalık gibi kalıcı çarpanların tek noktası. */
object Bonuses {
    fun production(state: GameState, type: BuildingType): Double {
        var m = 1.0 + 0.02 * state.research.masteryLevel
        if (SpecializationSystem.has(state, Specialization.ECONOMY)) m *= 1.08
        val food = type == BuildingType.FARM || type == BuildingType.GREENHOUSE || type == BuildingType.FOOD_FACTORY
        val water = type == BuildingType.WATER_COLLECTOR
        val energy = type == BuildingType.POWER_PLANT || type == BuildingType.MILL
        if (SpecializationSystem.has(state, Specialization.NATURE) && (food || water)) m *= 1.15
        if (SpecializationSystem.has(state, Specialization.TECHNOLOGY) && energy) m *= 1.15
        if (CollectionSystem.has(state, Artifact.SEED_POUCH) && (type == BuildingType.FARM || type == BuildingType.GREENHOUSE)) m *= 1.08
        if (CollectionSystem.has(state, Artifact.STORM_CRYSTAL) && energy) m *= 1.10
        if (CollectionSystem.has(state, Artifact.WATER_SPIRIT_STONE) && water) m *= 1.08
        return m
    }

    fun research(state: GameState): Double {
        var m = 1.0 + 0.02 * state.research.masteryLevel
        if (SpecializationSystem.has(state, Specialization.TECHNOLOGY)) m *= 1.25
        if (CollectionSystem.has(state, Artifact.SAGE_SCROLL)) m *= 1.10
        return m
    }

    fun speed(state: GameState): Double {
        var m = state.carrier.kind.speedMult * FleetSystem.fleetSpeedFactor(state)
        if (SpecializationSystem.has(state, Specialization.EXPLORATION)) m *= 1.10
        if (CollectionSystem.has(state, Artifact.SUN_COMPASS)) m *= 1.05
        return m
    }

    fun damage(state: GameState): Double {
        var m = state.carrier.kind.damageMult
        if (SpecializationSystem.has(state, Specialization.MILITARY)) m *= 0.85
        if (CollectionSystem.has(state, Artifact.GIANT_SCALE)) m *= 0.94
        return m
    }

    fun needs(state: GameState): Double {
        var m = state.carrier.kind.needMult
        if (SpecializationSystem.has(state, Specialization.NATURE)) m *= 0.9
        if (CollectionSystem.has(state, Artifact.BONE_FLUTE)) m *= 0.94
        return m
    }

    fun towerDps(state: GameState) = if (SpecializationSystem.has(state, Specialization.MILITARY)) 1.2 else 1.0
    fun sellPrice(state: GameState): Double {
        var m = 1.0
        if (SpecializationSystem.has(state, Specialization.ECONOMY)) m *= 1.10
        if (CollectionSystem.has(state, Artifact.MERCHANT_SCALES)) m *= 1.05
        return m
    }
    fun reveal(state: GameState): Int =
        (if (SpecializationSystem.has(state, Specialization.EXPLORATION)) 1 else 0) + (if (CollectionSystem.has(state, Artifact.OLD_MAP)) 1 else 0)
    fun buildingWeight(state: GameState) = if (CollectionSystem.has(state, Artifact.MASON_SEAL)) 0.96 else 1.0
}

// ====================================================================== Yan şehirler ve filo

@Serializable
enum class SideSpecialty(val title: String, val description: String, val perWorker: Amounts, val researchPerWorker: Double = 0.0) {
    FARMING("Tarım Şehri", "Yiyecek ve su üretir.", amounts(FOOD to 1.4, WATER to 0.9)),
    MINING("Maden Şehri", "Taş ve metal çıkarır.", amounts(STONE to 0.8, METAL to 0.4)),
    LUMBER("Kereste Şehri", "Odun ve kumaş üretir.", amounts(WOOD to 1.0, CLOTH to 0.25)),
    TRADE("Ticaret Şehri", "Uzak pazarlarla ticaret yapar: altın ve itibar.", amounts(GOLD to 0.6)),
    RESEARCH("Bilim Şehri", "Bilgi puanı üretir.", emptyMap(), researchPerWorker = 0.5),
    GUARD("Muhafız Şehri", "Her 2 yerleşimci savaşta 1 muhafız gönderir.", emptyMap()),
}

@Serializable
data class SideCity(
    val id: Int,
    val name: String,
    val species: CarrierSpecies,
    var specialty: SideSpecialty,
    var level: Int = 1,
    var workers: Int = 6,
    val stockpile: MutableMap<Resource, Double> = mutableMapOf(),
    var convoyTimer: Double = 0.0,
    var autoSend: Boolean = true,
) {
    val capacity: Int get() = 6 + 4 * level
}

object FleetSystem {
    const val FOUND_WORKERS = 6
    val foundCost: Amounts = amounts(WOOD to 300, STONE to 200, FOOD to 200, WATER to 200, CLOTH to 50)

    fun maxSideCities(state: GameState) = (state.stage - 2).coerceAtLeast(0)

    /** Filo en yavaş taşıyıcının hızıyla ilerler. */
    fun fleetSpeedFactor(state: GameState): Double {
        if (state.sideCities.isEmpty()) return 1.0
        val main = state.carrier.kind.speedMult
        val slowest = state.sideCities.minOf { it.species.speedMult }
        return (slowest / main).coerceAtMost(1.0)
    }

    fun foundReason(state: GameState, species: CarrierSpecies): String? = when {
        state.stage < 3 -> "Yan şehir ${StageSystem.title(3)} aşamasında kurulabilir."
        state.sideCities.size >= maxSideCities(state) -> "Şu anki aşamada en fazla ${maxSideCities(state)} yan şehir kurulabilir."
        species !in state.collection.species -> "Bu taşıyıcı türü henüz keşfedilmedi."
        CollectionSystem.inUse(state, species) -> "Bu tür zaten bir şehri taşıyor."
        PopulationSystem.freeWorkers(state) < FOUND_WORKERS -> "Yan şehre taşınacak $FOUND_WORKERS boşta yerleşimci gerekli."
        !state.store.canAfford(foundCost) -> "Yetersiz kaynak: ${foundCost.describe()}"
        else -> null
    }

    fun found(state: GameState, species: CarrierSpecies, specialty: SideSpecialty): ActionResult {
        foundReason(state, species)?.let { return ActionResult.fail(it) }
        state.store.spend(foundCost)
        state.population.count -= FOUND_WORKERS
        val names = listOf("Yeni Umut", "Rüzgârgülü", "Kuzey Yıldızı", "Yeşil Vaha", "Gök Kervanı", "Altın Başak")
        val name = names[(state.nextSideCityId - 1) % names.size]
        state.sideCities += SideCity(state.nextSideCityId++, name, species, specialty)
        state.log("Göçebe filoya yeni bir yan şehir katıldı: $name (${species.title}, ${specialty.title}).", LogKind.STAGE)
        state.cityVersion++
        return ActionResult.ok("$name kuruldu!")
    }

    fun upgradeCost(c: SideCity): Amounts {
        val f = (c.level + 1.0).pow(1.3)
        return amounts(WOOD to (100 * f).roundToInt(), STONE to (70 * f).roundToInt(), TOOLS to (8 * f).roundToInt())
    }

    fun upgrade(state: GameState, id: Int): ActionResult {
        val c = state.sideCities.firstOrNull { it.id == id } ?: return ActionResult.fail("Yan şehir bulunamadı.")
        if (c.level >= 5) return ActionResult.fail("En yüksek seviye.")
        val cost = upgradeCost(c)
        if (!state.store.canAfford(cost)) return ActionResult.fail("Yetersiz kaynak: ${cost.describe()}")
        state.store.spend(cost)
        c.level++
        state.log("${c.name} seviye ${c.level} oldu (kapasite ${c.capacity}).", LogKind.BUILD)
        return ActionResult.ok("${c.name} geliştirildi.")
    }

    /** Yerleşimcileri ana şehirle yan şehir arasında taşır (+ yan şehre, - ana şehre). */
    fun moveWorkers(state: GameState, id: Int, delta: Int): ActionResult {
        val c = state.sideCities.firstOrNull { it.id == id } ?: return ActionResult.fail("Yan şehir bulunamadı.")
        if (delta > 0) {
            if (c.workers + delta > c.capacity) return ActionResult.fail("${c.name} dolu (${c.capacity}).")
            if (PopulationSystem.freeWorkers(state) < delta) return ActionResult.fail("Ana şehirde boşta yerleşimci yok.")
            state.population.count -= delta; c.workers += delta
        } else {
            val n = -delta
            if (c.workers - n < 2) return ActionResult.fail("Yan şehirde en az 2 kişi kalmalı.")
            if (state.population.count + n > PopulationSystem.housing(state)) return ActionResult.fail("Ana şehirde barınak yok.")
            c.workers -= n; state.population.count += n
        }
        return ActionResult.ok("${c.name}: ${c.workers} yerleşimci.")
    }

    fun setSpecialty(state: GameState, id: Int, s: SideSpecialty): ActionResult {
        val c = state.sideCities.firstOrNull { it.id == id } ?: return ActionResult.fail("Yan şehir bulunamadı.")
        if (c.specialty == s) return ActionResult.fail("Zaten ${s.title}.")
        val cost = amounts(GOLD to 50)
        if (!state.store.canAfford(cost)) return ActionResult.fail("Uzmanlık değişimi 50 altın.")
        state.store.spend(cost)
        c.specialty = s
        return ActionResult.ok("${c.name} artık ${s.title}.")
    }

    /** Dakikalık brüt üretim (arayüz). */
    fun output(c: SideCity): Amounts = c.specialty.perWorker.mapValues { it.value * c.workers * (1.0 + 0.5 * (c.level - 1)) }

    fun guards(state: GameState) = state.sideCities.filter { it.specialty == SideSpecialty.GUARD }.sumOf { it.workers / 2 }

    fun step(state: GameState, dt: Double, offline: Boolean) {
        val minutes = dt / 60.0
        for (c in state.sideCities) {
            val mult = 1.0 + 0.5 * (c.level - 1)
            for ((r, v) in c.specialty.perWorker) c.stockpile[r] = (c.stockpile[r] ?: 0.0) + v * c.workers * mult * minutes
            if (c.specialty.researchPerWorker > 0) ResearchSystem.addPoints(state, c.specialty.researchPerWorker * c.workers * mult * minutes)
            if (c.specialty == SideSpecialty.TRADE) state.reputation = (state.reputation + 0.02 * c.workers * minutes).coerceAtMost(100.0)
            // Yerleşimciler önce kendi stoklarından, yoksa ana şehirden beslenir (şehirler arası ticaret).
            for (r in listOf(FOOD, WATER)) {
                var need = c.workers * PopulationSystem.FOOD_PER_POP * minutes
                val own = minOf(c.stockpile[r] ?: 0.0, need)
                c.stockpile[r] = (c.stockpile[r] ?: 0.0) - own
                need -= own
                if (need > 0) state.store.take(r, need)
            }
            c.convoyTimer += dt
            if (c.autoSend && c.convoyTimer >= 30) {
                c.convoyTimer = 0.0
                var sent = 0.0
                for ((r, v) in c.stockpile.toMap()) {
                    if (v < 1) continue
                    val got = state.store.add(r, v)
                    c.stockpile[r] = v - got
                    sent += got
                }
                if (sent > 0) state.stats.convoyGoods += sent
            }
        }
    }

    fun sendSupplies(state: GameState, id: Int): ActionResult {
        val c = state.sideCities.firstOrNull { it.id == id } ?: return ActionResult.fail("Yan şehir bulunamadı.")
        val cost = amounts(FOOD to 40, WATER to 40)
        if (!state.store.canAfford(cost)) return ActionResult.fail("Yetersiz erzak.")
        state.store.spend(cost)
        c.stockpile[FOOD] = (c.stockpile[FOOD] ?: 0.0) + 40
        c.stockpile[WATER] = (c.stockpile[WATER] ?: 0.0) + 40
        return ActionResult.ok("${c.name} şehrine erzak gönderildi.")
    }
}

// ====================================================================== Hedefler

@Serializable
enum class GoalKind(val template: String, val absolute: Boolean = false) {
    BUILD("%d bina inşa et"), UPGRADE("%d bina geliştir"), FOOD("%d yiyecek üret"), WATER("%d su üret"),
    GATHER("%d kaynak topla"), TRADE("%d ticaret yap"), TRAVEL("%d bölge yolculuk et"), BATTLE("%d savaş kazan"),
    EXPEDITION("%d keşif görevi tamamla"), RESEARCH("%d araştırma tamamla"),
    POPULATION("Nüfusu %d kişiye çıkar", absolute = true), CIVS("%d medeniyetle tanış", absolute = true),
    CARRIER_LEVEL("Taşıyıcıyı seviye %d yap", absolute = true);

    fun text(target: Double) = template.replace("%d", fmt(target))
}

@Serializable
data class Goal(val kind: GoalKind, val target: Double, val baseline: Double, val reward: Amounts, val weekly: Boolean, var claimed: Boolean = false)

object GoalSystem {
    fun stat(state: GameState, k: GoalKind): Double = when (k) {
        GoalKind.BUILD -> state.stats.buildingsBuilt.toDouble()
        GoalKind.UPGRADE -> state.stats.upgradesDone.toDouble()
        GoalKind.FOOD -> state.stats.foodProduced
        GoalKind.WATER -> state.stats.waterProduced
        GoalKind.GATHER -> state.stats.gathered
        GoalKind.TRADE -> state.stats.trades.toDouble()
        GoalKind.TRAVEL -> state.travel.distanceTraveled.toDouble()
        GoalKind.BATTLE -> state.stats.battlesWon.toDouble()
        GoalKind.EXPEDITION -> state.stats.expeditionsDone.toDouble()
        GoalKind.RESEARCH -> state.research.completed.size.toDouble() + state.research.masteryLevel
        GoalKind.POPULATION -> (state.population.count + state.sideCities.sumOf { it.workers }).toDouble()
        GoalKind.CIVS -> state.civs.count { it.met }.toDouble()
        GoalKind.CARRIER_LEVEL -> state.carrier.level.toDouble()
    }

    fun progress(state: GameState, g: Goal): Double = if (g.kind.absolute) stat(state, g.kind) else stat(state, g.kind) - g.baseline
    fun done(state: GameState, g: Goal) = progress(state, g) >= g.target

    fun questCenter(state: GameState) = state.buildings.any { it.type == BuildingType.QUEST_CENTER && CombatSystem.staffed(it) }

    private fun available(state: GameState, weekly: Boolean): List<GoalKind> {
        val l = mutableListOf(GoalKind.BUILD, GoalKind.FOOD, GoalKind.WATER, GoalKind.GATHER, GoalKind.TRAVEL)
        if (state.stats.buildingsBuilt > 6) l += GoalKind.UPGRADE
        if (state.civs.any { it.met }) l += GoalKind.TRADE
        if (state.buildings.any { it.def.towerDps > 0 }) l += GoalKind.BATTLE
        if (ExplorationSystem.center(state) != null) l += GoalKind.EXPEDITION
        if (state.buildings.any { it.type == BuildingType.RESEARCH_CENTER }) l += GoalKind.RESEARCH
        if (weekly) l += listOf(GoalKind.POPULATION, GoalKind.CIVS, GoalKind.CARRIER_LEVEL)
        return l
    }

    private fun make(state: GameState, k: GoalKind, weekly: Boolean): Goal {
        val s = 1.0 + state.stage * 0.6
        val w = if (weekly) 5.0 else 1.0
        val target = when (k) {
            GoalKind.BUILD -> (2 * s * w * 0.6).roundToInt().coerceAtLeast(1).toDouble()
            GoalKind.UPGRADE -> (1 * s * w * 0.5).roundToInt().coerceAtLeast(1).toDouble()
            GoalKind.FOOD, GoalKind.WATER -> (80 * s * w / 10).roundToInt() * 10.0
            GoalKind.GATHER -> (50 * s * w / 10).roundToInt() * 10.0
            GoalKind.TRADE -> (2 * s * w * 0.5).roundToInt().coerceAtLeast(1).toDouble()
            GoalKind.TRAVEL -> (3 * s * w * 0.5).roundToInt().coerceAtLeast(2).toDouble()
            GoalKind.BATTLE -> (if (weekly) 3.0 else 1.0)
            GoalKind.EXPEDITION -> (if (weekly) 4.0 else 1.0)
            GoalKind.RESEARCH -> (if (weekly) 3.0 else 1.0)
            GoalKind.POPULATION -> (state.population.count + state.sideCities.sumOf { it.workers } + 6 + state.stage * 2).toDouble()
            GoalKind.CIVS -> (state.civs.count { it.met } + 2).toDouble()
            GoalKind.CARRIER_LEVEL -> (state.carrier.level + 1).toDouble()
        }
        val goldBase = if (weekly) 120.0 else 30.0
        val extra = listOf(WOOD, STONE, TOOLS, CLOTH, METAL)[(state.nextRandom() * 5).toInt().coerceAtMost(4)]
        val reward = amounts(GOLD to (goldBase * s).roundToInt(), extra to ((if (weekly) 60 else 20) * s).roundToInt())
        return Goal(k, target, if (k.absolute) 0.0 else stat(state, k), reward, weekly)
    }

    /** Günlük (her oyun günü) ve haftalık (7 oyun günü) hedefleri yeniler. */
    fun refresh(state: GameState) {
        val day = state.day
        val week = (day - 1) / 7
        if (state.goalsDay != day) {
            state.goals.removeAll { !it.weekly }
            val n = 3 + if (questCenter(state)) 1 else 0
            available(state, false).shuffledBy(state).take(n).forEach { state.goals += make(state, it, false) }
            state.goalsDay = day
            state.cityVersion++
        }
        if (state.goalsWeek != week) {
            state.goals.removeAll { it.weekly }
            available(state, true).shuffledBy(state).take(2).forEach { state.goals += make(state, it, true) }
            state.goalsWeek = week
        }
    }

    private fun <T> List<T>.shuffledBy(state: GameState): List<T> {
        val l = toMutableList()
        for (i in l.indices.reversed()) { val j = (state.nextRandom() * (i + 1)).toInt().coerceAtMost(i); val t = l[i]; l[i] = l[j]; l[j] = t }
        return l
    }

    fun claim(state: GameState, index: Int): ActionResult {
        val g = state.goals.getOrNull(index) ?: return ActionResult.fail("Hedef bulunamadı.")
        if (g.claimed) return ActionResult.fail("Ödül zaten alındı.")
        if (!done(state, g)) return ActionResult.fail("Hedef henüz tamamlanmadı.")
        g.claimed = true
        val mult = if (questCenter(state)) 1.25 else 1.0
        val got = g.reward.mapValues { state.store.add(it.key, it.value * mult) }
        state.reputation = (state.reputation + if (g.weekly) 3.0 else 1.0).coerceAtMost(100.0)
        state.stats.goalsCompleted++
        state.log("Hedef tamamlandı: ${g.kind.text(g.target)}. Ödül: " + got.entries.filter { it.value > 0 }.joinToString { "${fmt(it.value)} ${it.key.title}" }, LogKind.STAGE)
        return ActionResult.ok("Ödül alındı!")
    }
}

// ====================================================================== Başarımlar

enum class Achievement(val title: String, val description: String, val rewardGold: Int, val check: (GameState) -> Boolean) {
    FIRST_STEPS("İlk Adımlar", "5 bina inşa et.", 30, { it.stats.buildingsBuilt >= 5 }),
    BUSY_STREETS("Kalabalık Sokaklar", "25 nüfusa ulaş.", 60, { it.population.count >= 25 }),
    METROPOLIS("Yürüyen Metropol", "Toplam 80 nüfusa ulaş (filo dahil).", 200, { it.population.count + it.sideCities.sumOf { c -> c.workers } >= 80 }),
    WANDERER("Gezgin", "25 bölge yol kat et.", 50, { it.travel.distanceTraveled >= 25 }),
    LONG_ROAD("Uzun Yol", "150 bölge yol kat et.", 200, { it.travel.distanceTraveled >= 150 }),
    TOWN("Kasaba", "Hareketli Kasaba aşamasına ulaş.", 60, { it.stage >= 2 }),
    WALKING_CITY("Büyük Yürüyen Şehir", "Büyük Yürüyen Şehir aşamasına ulaş.", 200, { it.stage >= 4 }),
    EMPIRE("Hareketli İmparatorluk", "Son aşamaya ulaş.", 500, { it.stage >= 6 }),
    DIPLOMAT("Diplomat", "3 medeniyetle tanış.", 50, { it.civs.count { c -> c.met } >= 3 }),
    ALLIANCE("Sarsılmaz Bağ", "Bir ittifak kur.", 100, { it.civs.any { c -> Treaty.ALLIANCE in c.treaties } }),
    MERCHANT("Usta Tüccar", "100 ticaret işlemi yap.", 150, { it.stats.trades >= 100 }),
    DEFENDER("Savunucu", "5 savaş kazan.", 100, { it.stats.battlesWon >= 5 }),
    EXPLORER("Kâşif", "10 keşif görevi tamamla.", 100, { it.stats.expeditionsDone >= 10 }),
    SCHOLAR("Bilge", "Bütün araştırmaları tamamla.", 300, { it.research.completed.size >= TechCatalog.all.size }),
    BOND("Kadim Dostluk", "Taşıyıcıyı seviye 8 yap.", 150, { it.carrier.level >= 8 }),
    ZOOLOGIST("Taşıyıcı Bilimci", "3 taşıyıcı türü keşfet.", 120, { it.collection.species.size >= 3 }),
    FLEET("Göçebe Filo", "İlk yan şehri kur.", 150, { it.sideCities.isNotEmpty() }),
    ARMADA("Büyük Filo", "3 yan şehirli bir filo kur.", 300, { it.sideCities.size >= 3 }),
    COLLECTOR("Koleksiyoncu", "5 antik eser topla.", 150, { it.collection.artifacts.size >= 5 }),
    DILIGENT("Çalışkan", "20 hedef tamamla.", 150, { it.stats.goalsCompleted >= 20 }),
}

object AchievementSystem {
    fun check(state: GameState, offline: Boolean) {
        for (a in Achievement.entries) {
            if (a.name in state.achievements || !a.check(state)) continue
            state.achievements[a.name] = state.day
            state.store.add(GOLD, a.rewardGold.toDouble())
            state.reputation = (state.reputation + 2).coerceAtMost(100.0)
            if (!offline) state.log("Başarım kazanıldı: ${a.title}! (+${a.rewardGold} altın)", LogKind.STAGE)
            state.cityVersion++
        }
    }
}

// ====================================================================== Sonsuz gelişim

object MasterySystem {
    fun available(state: GameState) = state.research.completed.size >= TechCatalog.all.size
    fun cost(level: Int) = 150.0 * 1.25.pow(level)
    fun start(state: GameState): ActionResult {
        if (!available(state)) return ActionResult.fail("Ustalık, bütün araştırmalar bitince açılır.")
        if (state.research.current != null) return ActionResult.fail("Önce mevcut araştırmayı bitirin.")
        state.research.masteryActive = true
        return ActionResult.ok("Ustalık araştırması sürüyor: her seviye üretim ve bilgi +%2.")
    }
}
