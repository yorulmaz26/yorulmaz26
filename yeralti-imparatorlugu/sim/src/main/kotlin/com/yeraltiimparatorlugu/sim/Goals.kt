package com.yeraltiimparatorlugu.sim

import kotlinx.serialization.Serializable

enum class GoalKind { DIG, GATHER_FOOD, POPULATION, BUILD_ROOM, MUSHROOM_STOCK, ROOMS_TOTAL, UPGRADE_ROOM, RESEARCH }

/** Koloni hedefi. [baseline], hedef verildiği andaki sayaç değeridir; ilerleme farktan ölçülür. */
@Serializable
data class Goal(
    val id: Int,
    val kind: GoalKind,
    val target: Int,
    val roomType: RoomType? = null,
    val baseline: Int = 0,
    val rewardType: ResourceType,
    val rewardAmount: Int,
) {
    val title: String
        get() = when (kind) {
            GoalKind.DIG -> "$target hücre kaz"
            GoalKind.GATHER_FOOD -> "$target birim yiyecek topla"
            GoalKind.POPULATION -> "Nüfusu $target yap"
            GoalKind.BUILD_ROOM -> "${roomType?.title} inşa et"
            GoalKind.MUSHROOM_STOCK -> "Depoda $target mantar biriktir"
            GoalKind.ROOMS_TOTAL -> "Toplam $target çalışan oda"
            GoalKind.UPGRADE_ROOM -> "Bir ${roomType?.title} odasını seviye $target yap"
            GoalKind.RESEARCH -> "$target araştırma tamamla"
        }

    fun progress(sim: Simulation): Int = when (kind) {
        GoalKind.DIG -> sim.stats.cellsDug - baseline
        GoalKind.GATHER_FOOD -> sim.stats.foodGathered - baseline
        GoalKind.POPULATION -> sim.population
        GoalKind.BUILD_ROOM -> if (sim.rooms.any { it.type == roomType && it.functional }) 1 else 0
        GoalKind.MUSHROOM_STOCK -> sim.resources[ResourceType.MUSHROOM]
        GoalKind.ROOMS_TOTAL -> sim.rooms.count { it.functional }
        GoalKind.UPGRADE_ROOM -> sim.rooms.filter { it.type == roomType }.maxOfOrNull { it.level } ?: 0
        GoalKind.RESEARCH -> sim.research.completed.size
    }

    val goalValue: Int get() = if (kind == GoalKind.BUILD_ROOM) 1 else target

    fun isDone(sim: Simulation) = progress(sim) >= goalValue
}

/**
 * Sonsuz hedef üreticisi: önce öğretici hedefler, sonra koloninin durumuna göre zorlaşan hedefler.
 * Tamamlanan hedef ödülünü verir ve yerine yenisi gelir; oyuncunun her zaman bir hedefi olur.
 */
class GoalSystem {
    val active = ArrayList<Goal>()
    var completed = 0
    var nextGoalId = 1
    var tutorialIndex = 0

    fun ensureGoals(sim: Simulation) {
        while (active.size < 3) active.add(generate(sim))
    }

    fun update(sim: Simulation) {
        val done = active.filter { it.isDone(sim) }
        if (done.isEmpty()) return
        for (g in done) {
            active.remove(g)
            completed++
            sim.stats.goalsCompleted++
            val got = sim.addCapped(g.rewardType, g.rewardAmount)
            sim.coins += 3
            sim.addLog("Hedef tamamlandı: ${g.title} (+$got ${g.rewardType.title})")
            sim.notify("Hedef tamamlandı: ${g.title}")
        }
        ensureGoals(sim)
    }

    private fun has(kind: GoalKind, room: RoomType? = null) = active.any { it.kind == kind && it.roomType == room }

    private fun generate(sim: Simulation): Goal {
        val id = nextGoalId++
        val tier = completed
        // Öğretici sıra
        val tutorial = listOf(
            Goal(id, GoalKind.DIG, 8, baseline = sim.stats.cellsDug, rewardType = ResourceType.FOOD, rewardAmount = 10),
            Goal(id, GoalKind.BUILD_ROOM, 1, RoomType.MATERIAL_STORAGE, rewardType = ResourceType.WOOD, rewardAmount = 4),
            Goal(id, GoalKind.GATHER_FOOD, 15, baseline = sim.stats.foodGathered, rewardType = ResourceType.LEAF, rewardAmount = 8),
            Goal(id, GoalKind.BUILD_ROOM, 1, RoomType.REST_ROOM, rewardType = ResourceType.FOOD, rewardAmount = 15),
            Goal(id, GoalKind.BUILD_ROOM, 1, RoomType.FUNGUS_FARM, rewardType = ResourceType.LEAF, rewardAmount = 12),
            Goal(id, GoalKind.BUILD_ROOM, 1, RoomType.RESEARCH_LAB, rewardType = ResourceType.FOOD, rewardAmount = 15),
            Goal(id, GoalKind.RESEARCH, 1, rewardType = ResourceType.WOOD, rewardAmount = 6),
        )
        while (tutorialIndex < tutorial.size) {
            val t = tutorial[tutorialIndex++]
            if (t.kind == GoalKind.BUILD_ROOM && sim.rooms.any { it.type == t.roomType }) continue
            if (has(t.kind, t.roomType)) continue
            return t
        }
        val missing = RoomType.entries.filter { sim.roomUnlocked(it) && sim.rooms.none { r -> r.type == it } && !has(GoalKind.BUILD_ROOM, it) }
        val options = ArrayList<() -> Goal>()
        if (!has(GoalKind.DIG)) options.add { Goal(id, GoalKind.DIG, 15 + tier * 6, baseline = sim.stats.cellsDug, rewardType = ResourceType.FOOD, rewardAmount = 10 + tier * 2) }
        if (!has(GoalKind.GATHER_FOOD)) options.add { Goal(id, GoalKind.GATHER_FOOD, 20 + tier * 8, baseline = sim.stats.foodGathered, rewardType = ResourceType.WOOD, rewardAmount = 4 + tier) }
        if (!has(GoalKind.RESEARCH) && sim.rooms.any { it.type == RoomType.RESEARCH_LAB }) options.add { Goal(id, GoalKind.RESEARCH, sim.research.completed.size + 1, rewardType = ResourceType.MINERAL, rewardAmount = 3 + tier / 3) }
        if (!has(GoalKind.POPULATION)) options.add { Goal(id, GoalKind.POPULATION, sim.population + 2 + tier / 3, rewardType = ResourceType.FOOD, rewardAmount = 15 + tier * 2) }
        if (missing.isNotEmpty()) options.add { Goal(id, GoalKind.BUILD_ROOM, 1, missing[sim.rng.nextInt(missing.size)], rewardType = ResourceType.SOIL, rewardAmount = 15) }
        if (!has(GoalKind.ROOMS_TOTAL)) options.add { Goal(id, GoalKind.ROOMS_TOTAL, sim.rooms.count { it.functional } + 1, rewardType = ResourceType.LEAF, rewardAmount = 10 + tier) }
        if (sim.rooms.any { it.type == RoomType.FUNGUS_FARM } && !has(GoalKind.MUSHROOM_STOCK)) {
            options.add { Goal(id, GoalKind.MUSHROOM_STOCK, minOf(15 + tier * 4, sim.capacityOf(StorageKind.FOOD) / 2), rewardType = ResourceType.MINERAL, rewardAmount = 2 + tier / 2) }
        }
        val upgradable = sim.rooms.filter { it.functional && it.level < it.type.maxLevel && it.type.buildable }
        if (upgradable.isNotEmpty() && !has(GoalKind.UPGRADE_ROOM)) {
            val r = upgradable[sim.rng.nextInt(upgradable.size)]
            options.add { Goal(id, GoalKind.UPGRADE_ROOM, r.level + 1, r.type, rewardType = ResourceType.FOOD, rewardAmount = 20 + tier * 2) }
        }
        if (options.isEmpty()) return Goal(id, GoalKind.DIG, 20 + tier * 8, baseline = sim.stats.cellsDug, rewardType = ResourceType.FOOD, rewardAmount = 10)
        return options[sim.rng.nextInt(options.size)]()
    }
}
