package com.yeraltiimparatorlugu.sim

enum class WorldEventKind(val title: String) {
    FLOOD("Sel"), DROUGHT("Kuraklık"), FOOD_BOOM("Besin bolluğu"), DISEASE("Hastalık"), QUAKE("Toprak kayması"),
    RIVAL_RAID("Rakip baskını"), RARE_FIND("Nadir kaynak keşfi"), MIGRATION("Canlı göçü"),
}

/** Rastgele dünya olayları: yaklaşık günde bir, mevsime göre ağırlıklı seçilir ve gerçek sonuçlar doğurur. */
class EventSystem {
    var nextAt = Simulation.DAY_LENGTH * 1.5
    val history = ArrayList<Pair<Int, WorldEventKind>>()

    fun update(sim: Simulation) {
        if (sim.time < nextAt) return
        nextAt = sim.time + Simulation.DAY_LENGTH * (0.7 + sim.rng.nextDouble() * 0.8)
        val season = sim.environment.season(sim.day)
        val weights = WorldEventKind.entries.associateWith { k ->
            when (k) {
                WorldEventKind.FLOOD -> if (season == Season.SPRING || season == Season.AUTUMN) 3f else 1f
                WorldEventKind.DROUGHT -> when (season) { Season.SUMMER -> 3f; Season.WINTER -> 0f; else -> 0.3f }
                WorldEventKind.FOOD_BOOM -> if (season == Season.WINTER) 0.5f else 2f
                WorldEventKind.DISEASE -> if (sim.population > 12) 1.2f + sim.corpses.size * 0.3f else 0.2f
                WorldEventKind.QUAKE -> 0.8f
                WorldEventKind.RIVAL_RAID -> if (sim.day > sim.difficulty.graceDays && sim.rivalSystem.rivals.any { it.hostile }) 1.5f else 0f
                WorldEventKind.RARE_FIND -> 1.2f
                WorldEventKind.MIGRATION -> if (season == Season.WINTER) 0.3f else 1.5f
            }
        }
        var roll = sim.rng.nextFloat() * weights.values.sum()
        val kind = weights.entries.first { roll -= it.value; roll <= 0f }.key
        trigger(sim, kind)
    }

    fun trigger(sim: Simulation, kind: WorldEventKind) {
        history.add(sim.day to kind)
        if (history.size > 60) history.removeAt(0)
        when (kind) {
            WorldEventKind.FLOOD -> sim.environment.forceWeather(sim, Weather.STORM, 240.0)
            WorldEventKind.DROUGHT -> sim.environment.forceWeather(sim, Weather.DROUGHT, Simulation.DAY_LENGTH)
            WorldEventKind.FOOD_BOOM -> {
                repeat(5) { sim.spawnRandomSource(far = false, edibleOnly = true, bonus = 1.6f) }
                sim.notify("Besin bolluğu! Yüzeyde bol yiyecek belirdi.")
                sim.addLog("Besin bolluğu yaşandı.")
            }
            WorldEventKind.DISEASE -> {
                val n = maxOf(1, sim.population / 6)
                sim.ants.filter { it.type.caste != Caste.QUEEN }.shuffled(sim.rng).take(n).forEach { it.sick = true }
                sim.notify("Hastalık yayılıyor: $n karınca hasta. Sağlık Odası ve Şifacılar iyileştirir.")
                sim.addLog("Kolonide hastalık başladı ($n karınca).")
            }
            WorldEventKind.QUAKE -> {
                val risky = sim.collapseCandidates()
                val chosen = risky.shuffled(sim.rng).take(2)
                if (chosen.isEmpty()) sim.addLog("Hafif bir toprak kayması oldu; tüneller sağlam kaldı.")
                for (k in chosen) sim.collapse(keyX(k), keyY(k))
                sim.notify("Toprak kayması! Desteksiz boşluklar tehlikede.")
            }
            WorldEventKind.RIVAL_RAID -> sim.rivalSystem.rivals.filter { it.hostile }.randomOrNull(sim.rng)?.let { sim.rivalSystem.launchRaid(sim, it, force = true) }
            WorldEventKind.RARE_FIND -> sim.rareFind()
            WorldEventKind.MIGRATION -> sim.migration()
        }
    }
}
