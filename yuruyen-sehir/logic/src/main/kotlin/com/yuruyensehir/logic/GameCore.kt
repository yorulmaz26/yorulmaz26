package com.yuruyensehir.logic

import com.yuruyensehir.logic.Resource.*

/** Medeniyet aşamaları: bölüm değil, şehrin gelişimini gösteren unvanlar. */
object StageSystem {
    val titles = listOf(
        "Küçük Göçebe Yerleşim", "Gezgin Köy", "Hareketli Kasaba", "Göçebe Şehir",
        "Büyük Yürüyen Şehir", "Gelişmiş Medeniyet", "Hareketli İmparatorluk",
    )
    val thresholds = listOf(0, 45, 110, 220, 380, 600, 900)

    fun title(stage: Int) = titles[stage.coerceIn(0, titles.lastIndex)]

    fun score(state: GameState): Int {
        val buildings = state.buildings.filter { !it.underConstruction }.sumOf { it.level }
        return state.population.count * 2 + buildings * 3 + state.research.completed.size * 6 +
            state.travel.distanceTraveled / 2 + (state.carrier.level - 1) * 8 +
            state.civs.count { it.met } * 4 + state.civs.sumOf { it.treaties.size } * 3 +
            state.stats.battlesWon * 3 + state.stats.expeditionsDone * 2 +
            state.sideCities.sumOf { 8 + it.level * 2 } + state.collection.species.size * 3 + state.collection.artifacts.size * 3 + state.research.masteryLevel * 4
    }

    fun nextThreshold(stage: Int): Int? = thresholds.getOrNull(stage + 1)

    fun check(state: GameState) {
        val s = score(state)
        while (state.stage < titles.lastIndex && s >= thresholds[state.stage + 1]) {
            state.stage++
            state.log("Medeniyetiniz yeni bir aşamaya ulaştı: ${title(state.stage)}! Yeni yapılar açıldı.", LogKind.STAGE)
            state.cityVersion++
        }
    }
}

data class OfflineReport(val seconds: Double, val gained: Map<Resource, Double>, val travelPaused: Boolean)

/** Oyunun ana döngüsü. Render katmanı yalnızca bu sınıfla konuşur. */
class GameCore(val state: GameState) {
    private var accumulator = 0.0
    var lastBalance: BalanceReport = WeightBalanceSystem.compute(state)
        private set

    init {
        ResourceSystem.recalcCapacity(state)
    }

    /** Gerçek zaman [realDt] saniye; oyun hızına göre sabit adımlarla ilerler. */
    fun update(realDt: Double) {
        val speed = state.settings.gameSpeed
        if (speed <= 0) return
        accumulator += realDt.coerceAtMost(0.5) * speed
        while (accumulator >= STEP) {
            accumulator -= STEP
            step(STEP)
        }
    }

    fun step(dt: Double, offline: Boolean = false) {
        state.gameSeconds += dt
        BuildingSystem.tickConstruction(state, dt)
        ProductionSystem.step(state, dt)
        PopulationSystem.step(state, dt, offline)
        val report = WeightBalanceSystem.compute(state)
        lastBalance = report
        CarrierSystem.step(state, dt, report, offline)
        if (!offline) TravelSystem.step(state, dt, report)
        if (!offline) CombatSystem.step(state, dt)
        CivSystem.step(state, dt, offline)
        ExplorationSystem.step(state, dt, offline)
        FleetSystem.step(state, dt, offline)
        GoalSystem.refresh(state)
        AchievementSystem.check(state, offline)
        state.eventCooldown = (state.eventCooldown - dt).coerceAtLeast(0.0)
        StageSystem.check(state)
    }

    /**
     * Çevrimdışı ilerleme: üretim, inşaat ve araştırma sürer; yolculuk ve olaylar durur,
     * taşıyıcı dinlenir. En fazla [MAX_OFFLINE_SECONDS] hesaba katılır. Saat geri alınmışsa ilerleme olmaz.
     */
    fun applyOffline(nowEpochMs: Long): OfflineReport? {
        val last = state.lastSavedEpochMs
        if (last <= 0) return null
        val elapsed = ((nowEpochMs - last) / 1000.0).coerceIn(0.0, MAX_OFFLINE_SECONDS)
        if (elapsed < 30) return null
        val before = Resource.entries.associateWith { state.store[it] }
        val wasTraveling = state.travel.traveling
        var left = elapsed
        while (left > 0) {
            val dt = minOf(OFFLINE_STEP, left)
            step(dt, offline = true)
            left -= dt
        }
        val gained = Resource.entries.associateWith { state.store[it] - (before[it] ?: 0.0) }.filterValues { kotlin.math.abs(it) >= 0.5 }
        state.log("Siz yokken ${(elapsed / 60).toInt()} dakika geçti; şehir çalışmaya devam etti.", LogKind.INFO)
        return OfflineReport(elapsed, gained, wasTraveling)
    }

    companion object {
        const val STEP = 0.25
        const val OFFLINE_STEP = 20.0
        const val MAX_OFFLINE_SECONDS = 8 * 3600.0

        fun newGame(seed: Long = System.nanoTime()): GameCore {
            val state = GameState(seed = seed)
            state.store.amounts.putAll(
                mapOf(FOOD to 120.0, WATER to 120.0, WOOD to 230.0, STONE to 120.0, METAL to 25.0, CLOTH to 30.0, GOLD to 60.0, ENERGY to 10.0)
            )
            // Başlangıç yapıları: şehir merkezi ve iki depo (bilerek biraz arkada, denge öğretici olsun).
            state.buildings += PlacedBuilding(state.nextBuildingId++, BuildingType.CITY_CENTER, 8, 5)
            state.buildings += PlacedBuilding(state.nextBuildingId++, BuildingType.FOOD_STORAGE, 6, 5)
            state.buildings += PlacedBuilding(state.nextBuildingId++, BuildingType.WATER_STORAGE, 6, 6)
            state.stats.buildingsBuilt = 3
            ResourceSystem.recalcCapacity(state)
            state.store.clampToCapacity()
            TravelSystem.reveal(state, state.travel.position)
            CivSystem.ensureNearby(state)
            state.log("Büyük Çöküş'ün ardından ${state.carrier.name} adlı ${state.carrier.species} sırtında küçük topluluğunuzla yola çıktınız.", LogKind.STAGE)
            state.log("İpucu: Tarla ve Su Toplama Tesisi kurarak işe başlayın. Ağırlık dengesine dikkat edin!", LogKind.INFO)
            return GameCore(state)
        }
    }
}
