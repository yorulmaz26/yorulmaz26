package com.kayipadalar.logic.kingdom

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/** İşlem sonucu: null = başarılı, aksi hâlde oyuncuya gösterilecek Türkçe hata. */
typealias Err = String?

data class BattleReport(
    val win: Boolean,
    val armyPower: Int,
    val enemyPower: Int,
    val lost: Map<String, Int>,
    val loot: Map<Res, Int>,
    val title: String,
)

data class QuestView(val index: Int, val def: QuestDef, val progress: Int, val done: Boolean)

/**
 * Krallık simülasyonu. Render katmanından bağımsızdır; tüm zaman hesapları dışarıdan verilen
 * güvenilir oyun saatiyle (ms) yapılır, böylece uygulama kapalıyken de üretim ve inşaat ilerler.
 */
class Kingdom(val cfg: KingdomConfig, val s: KingdomSave) {
    val r get() = cfg.rules

    // ------------------------------------------------------------------ Başlangıç

    fun newGame(now: Long) {
        s.res.clear()
        for (x in Res.entries) s.res[x.name] = (cfg.start[x.name] ?: 0).toDouble()
        s.buildings.clear(); s.islands.clear(); s.units.clear(); s.training.clear(); s.research.clear()
        s.researching = null; s.questIndex = 0; s.stats.clear(); s.raid = null
        s.islands.add(IslandState(0, "home", 0, 0f, IslandStatus.MERGED, guard = 0, dock = -1))
        s.buildings.add(BuildingState(s.nextUid++, "castle", 0, island = 0, plot = 0))
        s.islands.addAll(WorldGen.generate(cfg, 6))
        s.pop = 3.0
        s.lastTick = now
        s.nextRaidAt = now + r.raidEveryMinutes * 60_000L
        s.created = true
    }

    // ------------------------------------------------------------------ Sorgular

    fun res(x: Res) = s.res[x.name] ?: 0.0
    fun building(uid: Int) = s.buildings.firstOrNull { it.uid == uid }
    fun island(id: Int) = s.islands.firstOrNull { it.id == id }
    fun castleLevel() = s.buildings.firstOrNull { it.type == "castle" }?.level ?: 0
    fun levelOf(type: String) = s.buildings.filter { it.type == type }.maxOfOrNull { it.level } ?: 0
    fun countOf(type: String) = s.buildings.count { it.type == type && it.level >= 1 }
    fun buildingAt(island: Int, plot: Int) = s.buildings.firstOrNull { it.island == island && it.plot == plot }
    fun mergedIslands() = s.islands.filter { it.status == IslandStatus.MERGED && it.id != 0 }
    fun plotsOf(i: IslandState) = cfg.island(i.type).plots

    fun storageCap(): Double = (r.baseStorage + r.storagePerCastle * castleLevel() +
        r.storagePerWarehouse * s.buildings.filter { it.type == "warehouse" }.sumOf { it.level }).toDouble()

    fun popCap(): Int = r.popPerCastle * maxOf(1, castleLevel()) + s.buildings.filter { it.type == "house" }.sumOf { it.level * cfg.building("house").pop }

    fun armyCount(): Int = s.units.values.sum()
    fun freePop(): Int = floor(s.pop).toInt() - armyCount() - s.training.sumOf { it.count }

    fun builders(): Int = r.builders.getOrElse((castleLevel() - 1).coerceAtLeast(0)) { r.builders.last() }
    fun activeBuilds() = s.buildings.count { it.busy }

    private fun bonusCount(kind: String) = mergedIslands().count { cfg.island(it.type).bonus == kind }

    /** Aynı türden komşu (yan yana yuvalardaki) birleşik adaların sayısı. */
    fun adjacencyPairs(): Int {
        val byDock = mergedIslands().associateBy { it.dock }
        var n = 0
        for ((d, isl) in byDock) {
            val next = byDock[(d + 1) % r.dockSlots]
            if (next != null && next.type == isl.type) n++
        }
        return n
    }

    fun productionMultiplier(res: Res): Double {
        var m = 1.0 + 0.1 * (s.research["farming"] ?: 0)
        m += 0.2 * bonusCount(res.name)
        m += 0.1 * adjacencyPairs()
        return m
    }

    fun perHour(b: BuildingState): Double {
        val def = cfg.building(b.type)
        val p = def.produces ?: return 0.0
        if (b.level <= 0) return 0.0
        return def.perHour * b.level.toDouble().pow(1.15) * productionMultiplier(Res.valueOf(p))
    }

    fun armyPower(): Int {
        val base = cfg.units.sumOf { (s.units[it.id] ?: 0) * it.power }
        val mult = 1.0 + 0.1 * (s.research["military"] ?: 0) + 0.1 * bonusCount("POWER")
        return (base * mult).roundToInt()
    }

    fun defensePower(): Int {
        val towers = s.buildings.filter { it.type == "tower" }.sumOf { it.level } * r.towerDefense
        val mult = 1.0 + 0.2 * bonusCount("DEFENSE")
        return ((towers + armyPower() * r.armyDefensePct / 100.0) * mult).roundToInt()
    }

    fun buildSpeed() = 0.1 * (s.research["building"] ?: 0)

    fun canAfford(cost: Map<Res, Int>) = cost.all { (k, v) -> res(k) >= v }
    private fun pay(cost: Map<Res, Int>) = cost.forEach { (k, v) -> s.res[k.name] = res(k) - v }
    fun add(x: Res, v: Double) {
        val cap = if (x == Res.CRYSTAL) Double.MAX_VALUE else storageCap()
        s.res[x.name] = (res(x) + v).coerceIn(0.0, maxOf(cap, res(x)))
    }

    private fun stat(k: String, v: Long = 1) { s.stats[k] = (s.stats[k] ?: 0) + v }

    fun missing(cost: Map<Res, Int>): String =
        cost.filter { (k, v) -> res(k) < v }.entries.joinToString(", ") { "${(it.value - res(it.key)).roundToInt()} ${it.key.label}" }

    // ------------------------------------------------------------------ Zaman

    /** Geçen süreye göre inşaat, üretim, nüfus, eğitim, araştırma, keşif ve baskınları ilerletir. */
    fun tick(now: Long): List<String> {
        val events = ArrayList<String>()
        val dt = ((now - s.lastTick).coerceAtLeast(0)) / 1000.0
        s.lastTick = now
        for (b in s.buildings) {
            if (b.busy && now >= b.busyUntil) {
                b.level++; b.busyUntil = 0; b.busyStarted = 0
                events.add("${cfg.building(b.type).name} ${b.level}. seviyeye ulaştı!")
            }
            if (!b.busy) {
                val cap = perHour(b) * r.collectCapHours
                b.stored = (b.stored + perHour(b) * dt / 3600.0).coerceAtMost(cap)
            }
        }
        val cap = popCap().toDouble()
        if (s.pop < cap) s.pop = (s.pop + dt / r.popGrowSeconds).coerceAtMost(cap)
        for (job in s.training.toList()) {
            while (job.count > 0 && now >= job.nextAt) {
                s.units[job.unit] = (s.units[job.unit] ?: 0) + 1
                job.count--; job.nextAt += job.each
            }
            if (job.count <= 0) { s.training.remove(job); events.add("${cfg.unit(job.unit).name} eğitimi tamamlandı.") }
        }
        s.researching?.let { j ->
            if (now >= j.endsAt) {
                s.research[j.id] = j.level; s.researching = null; stat("research")
                events.add("${cfg.researchDef(j.id).name} ${j.level}. seviye tamamlandı!")
            }
        }
        for (i in s.islands) if (i.status == IslandStatus.EXPLORING && now >= i.exploreUntil) {
            i.status = IslandStatus.DISCOVERED; stat("explored")
            events.add("Keşif gemisi döndü: ${cfg.island(i.type).name} bulundu!")
        }
        if (castleLevel() >= r.raidFromCastle) {
            if (s.raid == null && now >= s.nextRaidAt) {
                s.raid = Raid(raidPower(), now)
                events.add("Korsan baskını! Krallığını savun.")
            }
            val raid = s.raid
            if (raid != null && now - raid.arrivedAt > 10 * 60_000L) defend(now) // oyuncu yoksa kendiliğinden çözülür
        } else if (now >= s.nextRaidAt) s.nextRaidAt = now + r.raidEveryMinutes * 60_000L
        return events
    }

    fun raidPower(): Int = (20 * 1.65.pow((castleLevel() - r.raidFromCastle).coerceAtLeast(0)) * (1 + 0.12 * (s.stats["raidsWon"] ?: 0))).roundToInt()

    // ------------------------------------------------------------------ İnşaat

    fun buildable(): List<BuildingDef> = cfg.buildings.filter { it.id != "castle" }

    fun canBuild(type: String, islandId: Int, plot: Int): Err {
        val def = cfg.building(type)
        val isl = island(islandId) ?: return "Ada bulunamadı."
        if (isl.status != IslandStatus.MERGED) return "Önce adayı krallığa çek."
        if (plot !in 0 until plotsOf(isl) || buildingAt(islandId, plot) != null) return "Bu arsa dolu."
        if (castleLevel() < maxOf(1, def.unlockCastle)) return "Ana Bina ${maxOf(1, def.unlockCastle)}. seviye gerekli."
        if (def.unique && s.buildings.any { it.type == type }) return "Bu binadan yalnızca bir tane olabilir."
        if (activeBuilds() >= builders()) return "Bütün inşaatçılar meşgul (${builders()})."
        val cost = def.costFor(1)
        if (!canAfford(cost)) return "Eksik: ${missing(cost)}"
        return null
    }

    fun build(type: String, islandId: Int, plot: Int, now: Long): Err {
        canBuild(type, islandId, plot)?.let { return it }
        val def = cfg.building(type)
        pay(def.costFor(1))
        val b = BuildingState(s.nextUid++, type, 0, islandId, plot)
        b.busyStarted = now; b.busyUntil = now + def.timeFor(1, buildSpeed()) * 1000L
        s.buildings.add(b)
        stat("built")
        return null
    }

    fun upgradeCost(b: BuildingState) = cfg.building(b.type).costFor(b.level + 1)
    fun upgradeTime(b: BuildingState) = cfg.building(b.type).timeFor(b.level + 1, buildSpeed())

    fun canUpgrade(b: BuildingState): Err {
        val def = cfg.building(b.type)
        if (b.busy) return "Zaten inşa ediliyor."
        if (b.level >= def.maxLevel) return "En üst seviyede."
        if (b.type != "castle" && b.level + 1 > castleLevel()) return "Önce Ana Bina'yı ${b.level + 1}. seviyeye yükselt."
        if (activeBuilds() >= builders()) return "Bütün inşaatçılar meşgul (${builders()})."
        if (!canAfford(upgradeCost(b))) return "Eksik: ${missing(upgradeCost(b))}"
        return null
    }

    fun upgrade(uid: Int, now: Long): Err {
        val b = building(uid) ?: return "Bina bulunamadı."
        canUpgrade(b)?.let { return it }
        collect(uid)
        pay(upgradeCost(b))
        b.busyStarted = now; b.busyUntil = now + upgradeTime(b) * 1000L
        return null
    }

    fun speedupCost(remainingMs: Long) = ceil((remainingMs / 1000.0) / r.speedupSecondsPerCrystal).toInt().coerceAtLeast(1)

    fun speedupBuilding(uid: Int, now: Long): Err {
        val b = building(uid) ?: return "Bina bulunamadı."
        if (!b.busy) return "Hızlandırılacak bir iş yok."
        val c = speedupCost(b.busyUntil - now)
        if (res(Res.CRYSTAL) < c) return "Eksik: ${c - res(Res.CRYSTAL).toInt()} Kristal"
        s.res[Res.CRYSTAL.name] = res(Res.CRYSTAL) - c
        b.busyUntil = now
        tick(now)
        return null
    }

    /** Üretim binasındaki birikmiş kaynağı depoya alır; alınan miktarı döndürür. */
    fun collect(uid: Int): Int {
        val b = building(uid) ?: return 0
        val p = cfg.building(b.type).produces ?: return 0
        val x = Res.valueOf(p)
        val room = if (x == Res.CRYSTAL) b.stored else (storageCap() - res(x)).coerceAtLeast(0.0)
        val got = floor(minOf(b.stored, room))
        if (got < 1) return 0
        b.stored -= got
        add(x, got)
        stat("collected_$p", got.toLong())
        return got.toInt()
    }

    fun collectAll(): Map<Res, Int> {
        val out = HashMap<Res, Int>()
        for (b in s.buildings) {
            val p = cfg.building(b.type).produces ?: continue
            val g = collect(b.uid)
            if (g > 0) out[Res.valueOf(p)] = (out[Res.valueOf(p)] ?: 0) + g
        }
        return out
    }

    // ------------------------------------------------------------------ Ordu

    fun unitUnlocked(u: UnitDef) = levelOf("barracks") >= u.barracks

    fun canTrain(id: String, count: Int): Err {
        val u = cfg.unit(id)
        if (levelOf("barracks") < 1) return "Önce Kışla kur."
        if (!unitUnlocked(u)) return "Kışla ${u.barracks}. seviye gerekli."
        if (count <= 0) return "Geçersiz sayı."
        if (freePop() < count) return "Yeterli boş nüfus yok (${freePop().coerceAtLeast(0)}). Ev kur veya yükselt."
        val cost = u.cost.entries.associate { Res.valueOf(it.key) to it.value * count }
        if (!canAfford(cost)) return "Eksik: ${missing(cost)}"
        return null
    }

    fun train(id: String, count: Int, now: Long): Err {
        canTrain(id, count)?.let { return it }
        val u = cfg.unit(id)
        pay(u.cost.entries.associate { Res.valueOf(it.key) to it.value * count })
        val speed = 1.0 - 0.05 * (levelOf("barracks") - 1)
        val each = (u.time * 1000L * speed).toLong().coerceAtLeast(500)
        val start = s.training.lastOrNull()?.let { it.nextAt + it.each * (it.count - 1) } ?: now
        s.training.add(TrainJob(id, count, maxOf(start, now) + each, each))
        stat("trained", count.toLong())
        return null
    }

    // ------------------------------------------------------------------ Araştırma

    fun canResearch(id: String): Err {
        val d = cfg.researchDef(id)
        val lv = s.research[id] ?: 0
        val center = levelOf("research")
        if (center < 1) return "Önce Araştırma Merkezi kur."
        if (s.researching != null) return "Zaten bir araştırma sürüyor."
        if (lv >= d.maxLevel) return "En üst seviyede."
        if (lv + 1 > center) return "Araştırma Merkezi ${lv + 1}. seviye gerekli."
        if (!canAfford(d.costFor(lv + 1))) return "Eksik: ${missing(d.costFor(lv + 1))}"
        return null
    }

    fun startResearch(id: String, now: Long): Err {
        canResearch(id)?.let { return it }
        val d = cfg.researchDef(id)
        val lv = (s.research[id] ?: 0) + 1
        pay(d.costFor(lv))
        s.researching = ResearchJob(id, lv, now, now + d.timeFor(lv) * 1000L)
        return null
    }

    // ------------------------------------------------------------------ Harita: keşif, savaş, birleştirme

    fun visibleRing() = levelOf("explore") + 1
    fun exploreCost(i: IslandState): Map<Res, Int> {
        val disc = 1 - 0.1 * (s.research["scouting"] ?: 0)
        return mapOf(Res.FOOD to (60 * i.ring.toDouble().pow(1.6) * disc).roundToInt(), Res.GOLD to (40 * i.ring * disc).roundToInt())
    }
    fun exploreSeconds(i: IslandState) = (20 * i.ring.toDouble().pow(1.5) * (1 - 0.15 * (s.research["scouting"] ?: 0))).roundToInt().coerceAtLeast(5)
    fun exploringCount() = s.islands.count { it.status == IslandStatus.EXPLORING }

    fun canExplore(i: IslandState): Err {
        if (i.status != IslandStatus.HIDDEN) return "Bu ada zaten keşfedildi."
        val lv = levelOf("explore")
        if (lv < 1) return "Önce Keşif Merkezi kur."
        if (i.ring > lv) return "Keşif Merkezi ${i.ring}. seviye gerekli."
        if (exploringCount() >= lv) return "Bütün keşif gemileri yolda."
        if (!canAfford(exploreCost(i))) return "Eksik: ${missing(exploreCost(i))}"
        return null
    }

    fun explore(id: Int, now: Long): Err {
        val i = island(id) ?: return "Ada bulunamadı."
        canExplore(i)?.let { return it }
        pay(exploreCost(i))
        i.status = IslandStatus.EXPLORING; i.exploreStarted = now
        i.exploreUntil = now + exploreSeconds(i) * 1000L
        return null
    }

    fun speedupExplore(id: Int, now: Long): Err {
        val i = island(id) ?: return "Ada bulunamadı."
        if (i.status != IslandStatus.EXPLORING) return "Keşif sürmüyor."
        val c = speedupCost(i.exploreUntil - now)
        if (res(Res.CRYSTAL) < c) return "Eksik: ${c - res(Res.CRYSTAL).toInt()} Kristal"
        s.res[Res.CRYSTAL.name] = res(Res.CRYSTAL) - c
        i.exploreUntil = now; tick(now)
        return null
    }

    /** Korsanlarla savaşır. Güç eşit ya da fazlaysa kazanılır; kayıplar güç oranına bağlıdır. */
    fun attack(id: Int): Pair<Err, BattleReport?> {
        val i = island(id) ?: return "Ada bulunamadı." to null
        if (i.status != IslandStatus.DISCOVERED) return "Bu adada savaşılacak korsan yok." to null
        val power = armyPower()
        if (power <= 0) return "Ordun yok. Kışla'dan asker eğit." to null
        val win = power >= i.guard
        val lossFrac = if (win) (i.guard.toDouble() / power * 0.35).coerceIn(0.05, 0.6) else 0.5
        val lost = applyLosses(lossFrac)
        val loot = if (win) mapOf(Res.GOLD to i.guard * 3, Res.WOOD to i.guard * 2, Res.STONE to i.guard * 2, Res.CRYSTAL to (i.ring * 3)) else emptyMap()
        loot.forEach { (k, v) -> add(k, v.toDouble()) }
        if (win) { i.status = IslandStatus.CLEARED; stat("conquered") }
        return null to BattleReport(win, power, i.guard, lost, loot, if (win) "Zafer!" else "Geri çekildik")
    }

    private fun applyLosses(frac: Double): Map<String, Int> {
        val lost = HashMap<String, Int>()
        for ((k, v) in s.units.entries.map { it.key to it.value }) {
            val l = (v * frac).roundToInt().coerceAtMost(v)
            if (l > 0) { s.units[k] = v - l; lost[k] = l }
        }
        return lost
    }

    fun freeDocks(): List<Int> = (0 until r.dockSlots).filter { d -> mergedIslands().none { it.dock == d } }

    /** Temizlenmiş adayı krallığa çeker ve köprüyle birleştirir. */
    fun merge(id: Int, dock: Int? = null): Err {
        val i = island(id) ?: return "Ada bulunamadı."
        if (i.status != IslandStatus.CLEARED) return if (i.status == IslandStatus.MERGED) "Ada zaten krallıkta." else "Önce adayı korsanlardan kurtar."
        val free = freeDocks()
        if (free.isEmpty()) return "Krallığın çevresinde boş yer yok. Bir adayı ayırabilirsin."
        val d = dock ?: free.first()
        if (d !in free) return "Bu yuva dolu."
        i.status = IslandStatus.MERGED; i.dock = d
        stat("merged")
        return null
    }

    /** Birleşik bir adayı başka bir yuvaya taşır; yuva doluysa iki ada yer değiştirir. */
    fun moveIsland(id: Int, dock: Int): Err {
        val i = island(id) ?: return "Ada bulunamadı."
        if (i.status != IslandStatus.MERGED || i.id == 0) return "Yalnızca birleşik adalar taşınabilir."
        if (dock !in 0 until r.dockSlots) return "Geçersiz yuva."
        val other = mergedIslands().firstOrNull { it.dock == dock }
        other?.dock = i.dock
        i.dock = dock
        return null
    }

    /** Boş bir adayı krallıktan ayırır (haritada temizlenmiş olarak kalır). */
    fun split(id: Int): Err {
        val i = island(id) ?: return "Ada bulunamadı."
        if (i.status != IslandStatus.MERGED || i.id == 0) return "Bu ada ayrılamaz."
        if (s.buildings.any { it.island == id }) return "Üzerinde bina olan ada ayrılamaz."
        i.status = IslandStatus.CLEARED; i.dock = -1
        return null
    }

    // ------------------------------------------------------------------ Baskın

    fun defend(now: Long): BattleReport? {
        val raid = s.raid ?: return null
        val def = defensePower()
        val win = def >= raid.power
        val lost = applyLosses(if (win) (raid.power.toDouble() / maxOf(1, def) * 0.2).coerceIn(0.0, 0.3) else 0.2)
        val loot = HashMap<Res, Int>()
        if (win) {
            loot[Res.GOLD] = 80 * maxOf(1, castleLevel()); loot[Res.CRYSTAL] = 5
            loot.forEach { (k, v) -> add(k, v.toDouble()) }
            stat("raidsWon")
        } else {
            for (x in Res.entries) if (x != Res.CRYSTAL) {
                val l = (res(x) * r.raidLootPct / 100).roundToInt()
                s.res[x.name] = res(x) - l; loot[x] = -l
            }
        }
        s.raid = null
        s.nextRaidAt = now + r.raidEveryMinutes * 60_000L
        return BattleReport(win, def, raid.power, lost, loot, if (win) "Baskın püskürtüldü!" else "Korsanlar yağmaladı")
    }

    // ------------------------------------------------------------------ Pazar

    fun tradeRate() = 0.4 + 0.08 * levelOf("market")
    fun trade(from: Res, to: Res, amount: Int): Err {
        if (levelOf("market") < 1) return "Önce Pazar kur."
        if (from == to || to == Res.CRYSTAL || from == Res.CRYSTAL) return "Bu takas yapılamaz."
        if (res(from) < amount) return "Yeterli ${from.label} yok."
        s.res[from.name] = res(from) - amount
        add(to, amount * tradeRate())
        return null
    }

    // ------------------------------------------------------------------ Görevler

    private fun generatedQuest(j: Int): QuestDef {
        // Görev zinciri bitince sonsuz gelişim hedefleri üretilir.
        val n = j + 1
        return when (j % 4) {
            0 -> QuestDef("Sonsuz Gelişim", QuestType.CONQUER, null, 8 + n * 2, "${8 + n * 2} ada kurtar", "Haritadaki daha uzak adalara saldır.", mapOf("GOLD" to 1000 * n, "CRYSTAL" to 30))
            1 -> QuestDef("Sonsuz Gelişim", QuestType.POWER, null, 300 + n * 250, "Ordu gücünü ${300 + n * 250}'e çıkar", "Güçlü birlikler eğit.", mapOf("FOOD" to 1000 * n))
            2 -> QuestDef("Sonsuz Gelişim", QuestType.DEFEND, null, 2 + n, "${2 + n} baskın püskürt", "Kulelerini yükselt.", mapOf("STONE" to 1000 * n, "CRYSTAL" to 20))
            else -> QuestDef("Sonsuz Gelişim", QuestType.POP, null, 40 + n * 15, "${40 + n * 15} nüfusa ulaş", "Evlerini yükselt.", mapOf("WOOD" to 1000 * n))
        }
    }

    fun questDef(i: Int) = if (i < cfg.quests.size) cfg.quests[i] else generatedQuest(i - cfg.quests.size)

    fun questProgress(q: QuestDef): Int = when (q.type) {
        QuestType.LEVEL -> levelOf(q.target!!)
        QuestType.COUNT -> countOf(q.target!!)
        QuestType.COLLECT -> (s.stats["collected_${q.target}"] ?: 0).toInt()
        QuestType.POP -> floor(s.pop).toInt()
        QuestType.ARMY -> armyCount()
        QuestType.POWER -> armyPower()
        QuestType.EXPLORE -> s.islands.count { it.id != 0 && it.status.ordinal >= IslandStatus.DISCOVERED.ordinal }
        QuestType.CONQUER -> s.islands.count { it.id != 0 && it.status.ordinal >= IslandStatus.CLEARED.ordinal }
        QuestType.MERGE -> mergedIslands().size
        QuestType.RESEARCH -> s.research.values.sum()
        QuestType.DEFEND -> (s.stats["raidsWon"] ?: 0).toInt()
    }

    fun currentQuest(): QuestView {
        val d = questDef(s.questIndex)
        val p = questProgress(d)
        return QuestView(s.questIndex, d, p.coerceAtMost(d.amount), p >= d.amount)
    }

    fun claimQuest(): Pair<Err, Map<Res, Int>> {
        val q = currentQuest()
        if (!q.done) return "Görev henüz tamamlanmadı." to emptyMap()
        val reward = q.def.reward.entries.associate { Res.valueOf(it.key) to it.value }
        reward.forEach { (k, v) -> add(k, v.toDouble()) }
        s.questIndex++
        return null to reward
    }

    /** Krallığın genel gücü (profil/HUD için). */
    fun kingdomScore(): Int = s.buildings.sumOf { it.level * 10 } + armyPower() + mergedIslands().size * 50
}
