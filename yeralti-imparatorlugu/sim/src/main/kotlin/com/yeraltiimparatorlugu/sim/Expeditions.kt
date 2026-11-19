package com.yeraltiimparatorlugu.sim

/** Yüzey biyomları: dünya x ekseninde bantlar halinde değişir ve kaynak türlerini belirler. */
enum class Biome(val title: String, val sources: List<SourceKind>) {
    MEADOW("Çimenlik", listOf(SourceKind.LEAF_BUSH, SourceKind.SEED_PILE, SourceKind.DEAD_INSECT, SourceKind.DEW)),
    FOREST("Orman", listOf(SourceKind.TWIGS, SourceKind.LEAF_BUSH, SourceKind.RESIN_DROP, SourceKind.DEAD_INSECT)),
    ROCKY("Kayalık bölge", listOf(SourceKind.PEBBLES, SourceKind.DEAD_INSECT, SourceKind.DEW)),
    DESERT("Çöl", listOf(SourceKind.SEED_PILE, SourceKind.PEBBLES, SourceKind.DEAD_INSECT)),
    SWAMP("Bataklık", listOf(SourceKind.DEW, SourceKind.LEAF_BUSH, SourceKind.DEAD_INSECT)),
    FLOWERS("Çiçek bahçesi", listOf(SourceKind.SEED_PILE, SourceKind.LEAF_BUSH, SourceKind.DEW)),
    HUMAN_RUINS("Terk edilmiş insan yapıları", listOf(SourceKind.CRUMBS, SourceKind.PEBBLES, SourceKind.TWIGS)),
    POND("Su kaynağı", listOf(SourceKind.DEW, SourceKind.LEAF_BUSH)),
    ;

    companion object {
        /** Yuvanın çevresi her zaman çimenliktir; uzaklaştıkça tohuma bağlı biyomlar gelir. */
        fun at(seed: Long, x: Int): Biome {
            if (kotlin.math.abs(x) <= 20) return MEADOW
            val band = Math.floorDiv(x, 36)
            return entries[(Hash.mix(seed, band, 0, 55) ushr 3).mod(entries.size.toLong()).toInt()]
        }
    }
}

/** Uzak keşif bölgesi. Bölgeler sonsuzdur: her tamamlanan bölge bir sonrakini açar. */
class Region(val index: Int, val name: String, val biome: Biome, val distanceMinutes: Int, val danger: Int)

class Expedition(val id: Int, val regionIndex: Int, val members: List<AntSave>, val startedAt: Double, val duration: Double) {
    val power: Float get() = members.sumOf { (it.type.attack + it.type.defense + 2).toDouble() }.toFloat()
}

/** Keşif seferleri; oyuncu yokken de ilerler. */
class ExpeditionSystem {
    val active = ArrayList<Expedition>()
    var regionsCompleted = 0
    val discoveredCreatures = LinkedHashSet<CreatureKind>()

    private val adjectives = listOf("Sisli", "Kızıl", "Yankılı", "Unutulmuş", "Gölgeli", "Parlak", "Rüzgârlı", "Derin", "Kadim", "Sessiz")

    fun region(seed: Long, index: Int): Region {
        val biome = Biome.entries[(Hash.mix(seed, index, 3, 71) ushr 3).mod(Biome.entries.size.toLong()).toInt()]
        val adj = adjectives[(Hash.mix(seed, index, 5, 71) ushr 3).mod(adjectives.size.toLong()).toInt()]
        return Region(index, "$adj ${biome.title}", biome, 3 + index * 2, (1 + index / 2).coerceAtMost(9))
    }

    fun slots(sim: Simulation) = sim.rooms.filter { it.type == RoomType.EXPLORATION_CENTER && it.functional }.sumOf { it.level }

    fun availableRegions(seed: Long): List<Region> = (0..regionsCompleted + 2).map { region(seed, it) }

    fun start(sim: Simulation, regionIndex: Int, size: Int): String? {
        if (slots(sim) <= active.size) return "Boş sefer yeri yok (Keşif Merkezi gerekli)"
        if (regionIndex > regionsCompleted + 2) return "Bölge henüz bilinmiyor"
        val candidates = sim.ants.filter { it.type.caste != Caste.QUEEN && it.job !is ExpeditionMarker }
            .sortedByDescending { if (it.type == AntType.SCOUT) 100f else it.type.attack + it.type.defense }
        if (candidates.size - size < 4) return "Kolonide en az 4 karınca kalmalı"
        val food = size * 4
        if (sim.resources.edibleTotal() < food) return "Erzak yetersiz ($food yiyecek)"
        var f = food
        while (f > 0) { val t = sim.resources.bestEdible() ?: break; sim.resources.take(t, 1); f-- }
        val team = candidates.take(size)
        for (a in team) sim.removeAntQuietly(a)
        val r = region(sim.seed, regionIndex)
        val dur = r.distanceMinutes * 60.0 / sim.mods.expeditionSpeed
        active.add(Expedition(sim.newId(), regionIndex, team.map { SaveMapper.antToSave(it) }, sim.time, dur))
        sim.addLog("${team.size} karıncalık sefer ${r.name} bölgesine yola çıktı.")
        return null
    }

    fun update(sim: Simulation) {
        val done = active.filter { sim.time >= it.startedAt + it.duration }
        for (e in done) { active.remove(e); complete(sim, e) }
    }

    private fun complete(sim: Simulation, e: Expedition) {
        val r = region(sim.seed, e.regionIndex)
        val rng = sim.rng
        val chance = (e.power / (r.danger * 9f + 4f)).coerceIn(0.15f, 0.97f)
        val success = rng.nextFloat() < chance
        val lost = if (success) (e.members.size * r.danger * 0.03f * rng.nextFloat()).toInt() else (e.members.size * (0.3f + 0.1f * r.danger)).toInt().coerceAtMost(e.members.size - 1)
        val survivors = e.members.shuffled(rng).drop(lost)
        val q = sim.rooms.first { it.type == RoomType.QUEEN_CHAMBER }
        for (a in survivors) sim.ants.add(SaveMapper.antFromSave(a).apply { x = q.anchorX + 0.5f; y = q.anchorY + 0.5f; prevX = x; prevY = y; hunger = 40f; xp += 5 })
        sim.stats.antsDied += lost
        val lines = ArrayList<String>()
        if (success) {
            val mult = 1 + e.regionIndex / 2
            val gains = HashMap<ResourceType, Int>()
            for (s in r.biome.sources) gains[s.resource] = (gains[s.resource] ?: 0) + (4 + rng.nextInt(6)) * mult
            gains[ResourceType.FOOD] = (gains[ResourceType.FOOD] ?: 0) + 8 * mult
            if (rng.nextFloat() < 0.35f + e.regionIndex * 0.04f) gains[ResourceType.GENETIC] = 1 + e.regionIndex / 3
            if (rng.nextFloat() < 0.3f) gains[ResourceType.FOSSIL] = 1
            if (e.regionIndex >= 3 && rng.nextFloat() < 0.4f) gains[ResourceType.RARE_MINERAL] = 1
            for ((t, n) in gains) { val got = sim.addCapped(t, n); if (got > 0) lines.add("+$got ${t.title}") }
            val creature = CreatureKind.entries[rng.nextInt(CreatureKind.entries.size)]
            if (discoveredCreatures.add(creature)) lines.add("Yeni canlı kaydedildi: ${creature.title}")
            sim.research.progress += 10f + e.regionIndex * 5f
            sim.coins += 2 + e.regionIndex
            if (e.regionIndex == regionsCompleted) { regionsCompleted++; lines.add("Yeni bölgeler haritaya eklendi") }
            sim.stats.expeditionsSucceeded++
            sim.addLog("Sefer ${r.name} bölgesinden döndü: ${lines.joinToString(", ")}" + if (lost > 0) " ($lost kayıp)" else "")
            sim.notify("Sefer başarıyla döndü: ${r.name}")
        } else {
            sim.addLog("Sefer ${r.name} bölgesinde başarısız oldu; $lost karınca kayıp.")
            sim.notify("Sefer başarısız döndü ($lost kayıp)")
        }
        sim.sound(SoundCue.RESEARCH)
    }
}

/** Sefere çıkmış karıncaları işaretlemek için boş iş (karınca listede tutulmaz; ek güvenlik). */
object ExpeditionMarker : Job(null) {
    override fun update(sim: Simulation, ant: Ant, dt: Float) = JobStatus.RUNNING
}
