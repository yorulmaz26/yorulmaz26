package com.yeraltiimparatorlugu.sim

enum class FossilKind(val title: String, val rarity: Int) {
    AMMONITE("Ammonit", 1), TRILOBITE("Trilobit", 1), LEAF_PRINT("Yaprak izi", 1), SNAIL_SHELL("Salyangoz kabuğu", 1),
    FERN("Eğrelti fosili", 2), FISH_SKELETON("Balık iskeleti", 2), AMBER_BEETLE("Kehribar içinde böcek", 2), BONE_SHARD("Kemik parçası", 2),
    GIANT_CENTIPEDE("Dev kırkayak", 3), GEODE("Kristal geode", 3), ANCIENT_QUEEN("Antik karınca kraliçesi", 4), DINO_TOOTH("Dinozor dişi", 4),
}

/** Fosil müzesi: fosil kaynakları incelenir, katalog doldurulur ve müzede sergilenir. */
class MuseumSystem {
    val found = LinkedHashSet<FossilKind>()
    var duplicates = 0

    fun capacity(sim: Simulation) = sim.rooms.filter { it.type == RoomType.FOSSIL_MUSEUM && it.functional }.sumOf { it.level * 4 }

    fun displayed(sim: Simulation): List<FossilKind> = found.sortedByDescending { it.rarity }.take(capacity(sim))

    fun examine(sim: Simulation): String {
        if (sim.resources[ResourceType.FOSSIL] <= 0) return "İncelenecek fosil yok"
        if (capacity(sim) == 0) return "Önce Fosil Müzesi inşa edin"
        sim.resources.take(ResourceType.FOSSIL, 1)
        val roll = sim.rng.nextFloat()
        val rarity = when { roll < 0.5f -> 1; roll < 0.8f -> 2; roll < 0.95f -> 3; else -> 4 }
        val pool = FossilKind.entries.filter { it.rarity == rarity }
        val k = pool[sim.rng.nextInt(pool.size)]
        return if (found.add(k)) {
            sim.addLog("Fosil Müzesi: ${k.title} kataloğa eklendi.")
            "Yeni fosil: ${k.title}!"
        } else { duplicates++; sim.coins += rarity; "${k.title} zaten var (+$rarity jeton)" }
    }
}

/** Uzun vadeli başarımlar. */
enum class Achievement(val title: String, val description: String, val coins: Int, val check: (Simulation) -> Boolean) {
    FIRST_TUNNEL("İlk Tünel", "10 hücre kaz.", 5, { it.stats.cellsDug >= 10 }),
    MINER("Madenci", "500 hücre kaz.", 15, { it.stats.cellsDug >= 500 }),
    DEEP("Derinlere", "40 derinliğe ulaş.", 10, { it.stats.maxDepth >= 40 }),
    ABYSS("Uçurum", "100 derinliğe ulaş.", 25, { it.stats.maxDepth >= 100 }),
    POP20("Kalabalık Yuva", "20 karıncaya ulaş.", 10, { it.population >= 20 }),
    POP100("Yeraltı Şehri", "100 karıncaya ulaş.", 30, { it.population >= 100 }),
    BUILDER("Mimar", "10 oda inşa et.", 10, { it.stats.roomsBuilt >= 10 }),
    ALL_ROOMS("Tam Donanım", "20 farklı oda türü inşa et.", 30, { s -> s.rooms.map { it.type }.toSet().size >= 20 }),
    SCHOLAR("Bilgin", "10 araştırma tamamla.", 15, { it.research.completed.size >= 10 }),
    SAGE("Bilge Koloni", "Tüm araştırmaları tamamla.", 50, { it.research.completed.size >= ResearchTree.all.size }),
    DEFENDER("Kale Bekçisi", "25 düşman yen.", 15, { it.stats.enemiesKilled >= 25 }),
    CONQUEROR("Fatih", "Bir rakip koloniyi yen.", 25, { it.stats.rivalsDefeated >= 1 }),
    EXPLORER("Kâşif", "5 sefer başarıyla tamamla.", 15, { it.stats.expeditionsSucceeded >= 5 }),
    COLLECTOR("Koleksiyoncu", "8 farklı fosil bul.", 20, { it.museum.found.size >= 8 }),
    GENETICIST("Genetikçi", "4 genetik özellik geliştir.", 20, { it.genetics.unlocked.size >= 4 }),
    EVOLVED("Evrim", "5 evrim kademesi al.", 20, { it.evolution.spent >= 5 }),
    DIPLOMAT("Diplomat", "Bir koloniyle ittifak kur.", 20, { s -> s.rivalSystem.rivals.any { it.treaty == Treaty.ALLIANCE } }),
    FEDERATION("Federasyon", "Bir yan koloni kur.", 25, { it.federation.colonies.isNotEmpty() }),
    SURVIVOR("Hayatta Kalan", "Bir yılı tamamla.", 20, { it.day > Environment.DAYS_PER_SEASON * 4 }),
    FARMER("Mantarcı", "Toplam 300 yiyecek topla.", 10, { it.stats.foodGathered >= 300 }),
}

class AchievementSystem {
    val unlocked = LinkedHashSet<Achievement>()

    fun update(sim: Simulation) {
        for (a in Achievement.entries) {
            if (a in unlocked || !a.check(sim)) continue
            unlocked.add(a)
            sim.coins += a.coins
            sim.addLog("Başarım: ${a.title} (+${a.coins} jeton)")
            sim.notify("Başarım kazanıldı: ${a.title}")
            sim.sound(SoundCue.RESEARCH)
        }
    }
}

enum class EmpireGoalKind(val title: String, val unit: String) {
    POPULATION("En büyük nüfus", "karınca"),
    DEFENSE("En güçlü savunma", "savunma gücü"),
    TERRITORY("En geniş toprak", "kazılmış hücre"),
    PRODUCTION("En gelişmiş üretim", "toplam üretim"),
}

/** Oyuncunun seçtiği uzun vadeli imparatorluk hedefi; kademeli ve sınırsız. */
class EmpireGoal {
    var kind: EmpireGoalKind? = null
    var tier = 0

    fun target(): Int = when (kind) {
        EmpireGoalKind.POPULATION -> 25 * (tier + 1) * (tier + 1)
        EmpireGoalKind.DEFENSE -> 40 * (tier + 1) * (tier + 1)
        EmpireGoalKind.TERRITORY -> 200 * (tier + 1) * (tier + 1)
        EmpireGoalKind.PRODUCTION -> 300 * (tier + 1) * (tier + 1)
        null -> 0
    }

    fun progress(sim: Simulation): Int = when (kind) {
        EmpireGoalKind.POPULATION -> sim.population
        EmpireGoalKind.DEFENSE -> sim.rivalSystem.playerPower(sim).toInt()
        EmpireGoalKind.TERRITORY -> sim.stats.cellsDug
        EmpireGoalKind.PRODUCTION -> sim.stats.foodGathered + sim.stats.materialsGathered + sim.stats.produced
        null -> 0
    }

    fun update(sim: Simulation) {
        val k = kind ?: return
        if (progress(sim) >= target()) {
            tier++
            val reward = 10 + tier * 10
            sim.coins += reward
            sim.addCapped(ResourceType.FOOD, 20 * tier)
            sim.addLog("İmparatorluk hedefi (${k.title}) kademe $tier tamamlandı! +$reward jeton")
            sim.notify("İmparatorluk hedefi kademe $tier!")
        }
    }
}

object Reputation {
    val titles = listOf(0 to "Küçük Yuva", 150 to "Gelişen Koloni", 400 to "Yeraltı Kasabası", 900 to "Tünel Şehri", 1800 to "Yeraltı Krallığı", 3500 to "Yeraltı İmparatorluğu", 7000 to "Efsanevi İmparatorluk")

    fun of(sim: Simulation): Int {
        var r = sim.population * 2 + sim.rooms.count { it.functional } * 5 + sim.research.completed.size * 10
        r += sim.stats.enemiesKilled + sim.stats.rivalsDefeated * 40 + sim.museum.displayed(sim).sumOf { 10 + it.rarity * 5 }
        r += sim.achievements.unlocked.size * 10 + sim.stats.cellsDug / 10 + sim.federation.colonies.size * 30
        r += sim.rooms.filter { it.type == RoomType.ARCHIVE && it.functional }.sumOf { it.level * 20 }
        r += sim.expeditions.regionsCompleted * 8 + sim.genetics.unlocked.size * 10
        return r
    }

    fun title(rep: Int): String = titles.last { rep >= it.first }.second
}
