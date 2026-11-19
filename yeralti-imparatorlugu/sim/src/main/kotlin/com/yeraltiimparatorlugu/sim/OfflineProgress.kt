package com.yeraltiimparatorlugu.sim

class OfflineReport(
    val elapsedSeconds: Long,
    val appliedSeconds: Long,
    val capped: Boolean,
    val clockRollback: Boolean,
    val lines: List<String>,
) {
    val meaningful: Boolean get() = appliedSeconds >= 60 || clockRollback
}

/**
 * Oyuncu yokken sınırlı ve dengeli ilerleme. Yalnızca ekonomi soyut olarak hesaplanır: mantar üretimi,
 * toplama, yavru gelişimi ve yumurtlama. Kazı, inşa, çökme ve ölüm oyuncu yokken gerçekleşmez.
 *
 * Saat koruması: kayıtta görülmüş en ileri zaman tutulur. Telefon saati bunun gerisine alınırsa ilerleme
 * verilmez; ileri alınırsa en fazla [MAX_SECONDS] kadar verilir ve sonraki geri dönüşte telafi edilmez.
 * Tamamen çevrimdışı bir oyunda bu koruma kesin değildir, yalnızca makul düzeydedir.
 */
object OfflineProgress {
    const val MAX_SECONDS = 8L * 3600L
    const val EFFICIENCY = 0.5f
    private const val ROLLBACK_TOLERANCE_MS = 2 * 60 * 1000L

    fun apply(sim: Simulation, savedAtMillis: Long, maxSeenMillis: Long, nowMillis: Long): OfflineReport {
        if (nowMillis + ROLLBACK_TOLERANCE_MS < maxSeenMillis) {
            sim.addLog("Cihaz saati geri alınmış görünüyor; çevrimdışı ilerleme verilmedi.")
            return OfflineReport(0, 0, false, true, listOf("Cihaz saati geri alınmış görünüyor. Çevrimdışı ilerleme uygulanmadı."))
        }
        val elapsed = ((nowMillis - savedAtMillis) / 1000L).coerceAtLeast(0L)
        val applied = minOf(elapsed, MAX_SECONDS)
        if (applied < 60) return OfflineReport(elapsed, 0, false, false, emptyList())
        val lines = advance(sim, applied.toFloat())
        return OfflineReport(elapsed, applied, elapsed > MAX_SECONDS, false, lines)
    }

    /** Saniye cinsinden soyut ilerleme; ayrıca testlerde doğrudan çağrılır. */
    fun advance(sim: Simulation, seconds: Float): List<String> {
        val lines = ArrayList<String>()
        val eff = seconds * EFFICIENCY
        val workers = sim.workerCount
        val r = sim.resources

        // 1) Toplama: işçilerin bir kısmı bilinen kaynaklardan toplar (kapasite ve kaynak miktarıyla sınırlı).
        var gathered = 0
        val gatherBudget = (workers * eff / 45f).toInt()
        var budget = gatherBudget
        for (s in sim.sources.sortedBy { kotlin.math.abs(it.x) }) {
            if (budget <= 0) break
            if (kotlin.math.abs(s.x) > Simulation.KNOWN_RANGE) continue
            val free = sim.freeSpaceFor(s.kind.resource)
            val take = minOf(budget, s.amount - s.reserved, free)
            if (take <= 0) continue
            s.amount -= take
            r.add(s.kind.resource, take)
            if (s.kind.resource.edible) sim.stats.foodGathered += take else sim.stats.materialsGathered += take
            budget -= take
            gathered += take
        }
        sim.sources.removeAll { it.amount <= 0 && it.reserved <= 0 }
        if (gathered > 0) lines.add("Toplayıcılar $gathered birim kaynak getirdi.")

        // 2) Mantar çiftlikleri: tampondaki ve depodaki yapraklarla üretim.
        var mushrooms = 0
        for (farm in sim.rooms.filter { it.type == RoomType.FUNGUS_FARM && it.functional }) {
            val cycles = (eff * sim.roomEfficiency(farm) * farm.level / Simulation.FARM_CYCLE).toInt()
            var leaves = farm.leafBuffer + r[ResourceType.LEAF]
            var done = 0
            while (done < cycles && leaves > 0 && sim.freeSpace(StorageKind.FOOD) >= 2) {
                if (farm.leafBuffer > 0) farm.leafBuffer-- else r.take(ResourceType.LEAF, 1)
                leaves--
                r.add(ResourceType.MUSHROOM, 2)
                mushrooms += 2
                done++
            }
        }
        if (mushrooms > 0) lines.add("Mantar çiftlikleri $mushrooms mantar üretti.")

        // 3) Tüketim: koloni yer ama stok hiçbir zaman %30'un altına düşürülmez; açlıktan ölüm olmaz.
        val need = (workers * seconds * (100f / 260f) / 34f * 0.6f).toInt()
        val floor = (r.edibleTotal() * 0.3f).toInt()
        var eaten = 0
        while (eaten < need && r.edibleTotal() > floor) {
            val t = r.bestEdible() ?: break
            r.take(t, 1); eaten++
        }
        sim.stats.foodEaten += eaten
        for (a in sim.ants) { a.hunger = minOf(a.hunger, 40f); a.energy = maxOf(a.energy, 70f) }
        if (eaten > 0) lines.add("Koloni $eaten birim yiyecek tüketti.")

        // 4) Yavrular: beslenmiş varsayılır (her larva için yiyecek varsa), en fazla 6 doğum.
        var hatched = 0
        val qRoom = sim.rooms.first { it.type == RoomType.QUEEN_CHAMBER }
        for (b in sim.brood.toList()) {
            var t = eff
            while (t > 0f && b in sim.brood) {
                val left = b.stage.duration - b.progress
                if (t < left) { b.progress += t; t = 0f; break }
                t -= left
                b.progress = 0f
                when (b.stage) {
                    BroodStage.EGG -> b.stage = BroodStage.LARVA
                    BroodStage.LARVA -> { if (r.edibleTotal() > 0) { r.bestEdible()?.let { r.take(it, 1) }; b.stage = BroodStage.PUPA } else { t = 0f } }
                    BroodStage.PUPA -> {
                        if (hatched < 6) {
                            sim.brood.remove(b)
                            val room = sim.roomById(b.roomId) ?: qRoom
                            sim.ants.add(Ant(sim.newId(), AntType.WORKER, room.anchorX + 0.5f, room.anchorY + 0.5f))
                            sim.stats.antsBorn++
                            hatched++
                        } else { b.progress = b.stage.duration - 1f; t = 0f }
                    }
                }
            }
            b.fed = maxOf(b.fed, 0.6f)
        }
        if (hatched > 0) lines.add("$hatched yeni işçi doğdu.")

        // 5) Kraliçe: en fazla 4 yumurta.
        var laid = 0
        val maxEggs = minOf(4, (eff / Simulation.LAY_INTERVAL).toInt())
        while (laid < maxEggs && sim.layBlocker() == null) {
            r.bestEdible()?.let { r.take(it, 1) }
            r.bestEdible()?.let { r.take(it, 1) }
            val room = sim.rooms.firstOrNull { it.type == RoomType.EGG_ROOM && sim.brood.count { b -> b.roomId == it.id } < sim.broodCapacity(it) } ?: qRoom
            sim.brood.add(Brood(sim.newId(), BroodStage.EGG, room.id))
            sim.stats.eggsLaid++
            laid++
        }
        if (laid > 0) lines.add("Kraliçe $laid yumurta bıraktı.")

        // 6) Araştırma ve genetik: laboratuvarlar yarı verimle çalışmaya devam eder.
        val labs = sim.rooms.filter { it.type == RoomType.RESEARCH_LAB && it.functional }
        val def = sim.research.currentDef()
        if (def != null && labs.isNotEmpty()) {
            val researchers = minOf(labs.sumOf { it.stationCapacity }, maxOf(1, workers / 5))
            sim.research.progress += researchers * eff * sim.mods.researchMult
            if (sim.research.progress >= def.work) { sim.completeResearch(def); lines.add("Araştırma tamamlandı: ${def.title}") }
            else lines.add("Araştırma ilerledi: ${def.title} %${(sim.research.progress / def.work * 100).toInt()}")
        }
        // 7) Rakipler ekonomilerini sürdürür ama oyuncu yokken baskın yapmaz; akıncılar geri döner.
        sim.rivalSystem.offlineAdvance(sim, seconds)
        for (c in sim.creatures.filter { it.rivalId >= 0 }) { sim.creatures.remove(c); sim.rivalSystem.raiderReturned(sim.rivalSystem.byId(c.rivalId), 0) }
        // 8) Yan koloniler büyür ve kaynak gönderir.
        if (sim.federation.colonies.isNotEmpty()) {
            val before = sim.resources.edibleTotal()
            var left = seconds
            while (left > 0f) { sim.federation.update(sim, minOf(60f, left)); left -= 60f }
            val got = sim.resources.edibleTotal() - before
            if (got > 0) lines.add("Yan koloniler $got yiyecek gönderdi.")
        }
        sim.environment.drainAll()
        sim.environment.weather = Weather.CLEAR

        // 9) Yüzey kaynakları yenilenir, feromonlar söner, takvim ilerler; seferler tamamlanır.
        repeat(minOf(Simulation.MAX_SOURCES, (seconds / 60f).toInt())) { sim.refreshSources() }
        sim.pheromones.update(seconds)
        sim.time += seconds
        sim.restoreInternal(sim.day, 0f, sim.riskySnapshot())
        sim.environment.weatherUntil = sim.time + 240.0
        sim.events.nextAt = maxOf(sim.events.nextAt, sim.time + 180.0)
        val expBefore = sim.expeditions.active.size
        sim.expeditions.update(sim)
        if (sim.expeditions.active.size < expBefore) lines.add("${expBefore - sim.expeditions.active.size} sefer döndü (ayrıntılar günlükte).")
        val hours = (seconds / 3600f)
        sim.addLog("Yokluğunuzda geçen ${"%.1f".format(hours)} saat değerlendirildi.")
        if (lines.isEmpty()) lines.add("Koloni sakin bir şekilde bekledi.")
        lines.add("Kazı, inşa ve savaş yalnızca oyun açıkken olur; oyuncu yokken baskın yapılmaz.")
        return lines
    }
}
