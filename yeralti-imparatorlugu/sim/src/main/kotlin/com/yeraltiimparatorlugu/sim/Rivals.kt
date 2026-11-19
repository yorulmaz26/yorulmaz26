package com.yeraltiimparatorlugu.sim

enum class RivalPersonality(val title: String, val gather: Float, val breed: Float, val militarism: Float, val defense: Float, val raidDays: Float, val baseRelation: Int) {
    AGGRESSIVE("Saldırgan koloni", 1f, 1f, 1.6f, 0.8f, 1.3f, -30),
    DEFENSIVE("Savunmacı koloni", 1f, 0.9f, 0.8f, 1.8f, 4f, 0),
    BREEDER("Hızlı çoğalan koloni", 1.1f, 1.7f, 0.9f, 0.8f, 2.5f, -10),
    HOARDER("Kaynak odaklı koloni", 1.6f, 0.9f, 0.7f, 1.1f, 3.5f, 10),
    TRAPPER("Tuzak kullanan koloni", 1f, 1f, 0.9f, 1.6f, 3f, -5),
    BURROWER("Yeraltı uzmanı koloni", 1.1f, 1f, 1.1f, 1.2f, 2.5f, -10),
    SURFACE("Yüzey hâkimi koloni", 1.4f, 1.1f, 1.2f, 0.8f, 2f, -15),
    ELITE("Elit asker kolonisi", 0.9f, 0.7f, 1.4f, 1.3f, 2.5f, -20),
}

enum class Treaty(val title: String) { WAR("Savaş"), NEUTRAL("Tarafsız"), CEASEFIRE("Ateşkes"), TRADE("Ticaret"), ALLIANCE("İttifak") }

class RivalColony(val id: Int, var name: String, val personality: RivalPersonality, var x: Int) {
    var population = 14
    var soldiers = 3
    var food = 40f
    var defense = 10f
    var techLevel = 1
    var relation = personality.baseRelation
    var treaty = Treaty.NEUTRAL
    var treatyUntil = 0.0
    var discovered = false
    var raidTimer = 0f
    var alive = true
    var respawnAt = 0.0
    var raidsLaunched = 0
    var defeats = 0
    var tradesDay = 0
    var tradesCount = 0
    var lastSiege = 0.0

    val strength: Float get() = soldiers * 5f + defense * 0.5f + population * 0.5f
    val hostile: Boolean get() = alive && (treaty == Treaty.WAR || (treaty == Treaty.NEUTRAL && relation < -15))
}

class TradeOffer(val give: ResourceType, val giveAmount: Int, val get: ResourceType, val getAmount: Int)

/**
 * Rakip koloniler: kendi ekonomileri (yiyecek, nüfus, asker, savunma) soyut olarak simüle edilir, oyuncunun gücüne
 * göre ölçeklenir. Düşmanca olanlar baskın düzenler; baskınlar oyuncunun savunmasına göre sınırlandırılır ve ilk günlerde
 * yapılmaz. Oyuncu yokken baskın olmaz.
 */
class RivalSystem {
    companion object {
        const val GRACE_DAYS = 3
        private val namesA = listOf("Kızıl", "Altın", "Kara", "Yeşil", "Gümüş", "Kehribar", "Bakır", "Çakıl", "Yosun", "Diken")
        private val namesB = listOf("Çene", "Kalkan", "Pençe", "Yaprak", "Tepe", "Kök", "Mızrak", "Çığ", "Kum", "Dal")

        /** Rakip yuvalarının x konumları (dünya üretimi de rakip tünellerini buraya çizer). */
        fun nestPositions(seed: Long): IntArray = IntArray(RivalPersonality.entries.size) { i ->
            val side = if (i % 2 == 0) 1 else -1
            side * (70 + (i / 2) * 42 + (Hash.unit(seed, i, 0, 91) * 16).toInt())
        }
    }

    val rivals = ArrayList<RivalColony>()
    private var econTimer = 0f

    fun init(sim: Simulation) {
        rivals.clear()
        val xs = nestPositions(sim.seed)
        val pers = RivalPersonality.entries.shuffled(kotlin.random.Random(sim.seed))
        for ((i, p) in pers.withIndex()) {
            val r = RivalColony(i + 1, randomName(sim), p, xs[i])
            r.raidTimer = (RivalSystem.GRACE_DAYS + p.raidDays) * Simulation.DAY_LENGTH.toFloat() * (0.8f + sim.rng.nextFloat() * 0.5f)
            rivals.add(r)
        }
    }

    private fun randomName(sim: Simulation) = "${namesA[sim.rng.nextInt(namesA.size)]} ${namesB[sim.rng.nextInt(namesB.size)]} Kolonisi"

    fun byId(id: Int) = rivals.firstOrNull { it.id == id }

    fun playerPower(sim: Simulation): Float {
        var p = 0f
        for (a in sim.ants) if (a.type.military) p += a.type.attack * sim.mods.attackMult + a.type.defense * sim.mods.defenseMult * 0.5f
        p += sim.workerCount * 0.6f
        p += sim.rooms.filter { it.functional && (it.type == RoomType.DEFENSE_ROOM || it.type == RoomType.TRAP_ROOM || it.type == RoomType.FORTRESS) }.sumOf { it.level * 4 }
        return p
    }

    fun update(sim: Simulation, dt: Float) {
        econTimer += dt
        if (econTimer >= 10f) { economy(sim, econTimer); econTimer = 0f }
        for (r in rivals) {
            if (!r.alive) {
                if (sim.time >= r.respawnAt) respawn(sim, r)
                continue
            }
            if (r.treaty != Treaty.WAR && r.treaty != Treaty.NEUTRAL && sim.time > r.treatyUntil && r.treaty == Treaty.CEASEFIRE) {
                r.treaty = Treaty.NEUTRAL
                sim.addLog("${r.name} ile ateşkes sona erdi.")
            }
            r.raidTimer -= dt
            if (r.raidTimer <= 0f) {
                r.raidTimer = r.personality.raidDays * Simulation.DAY_LENGTH.toFloat() * (0.7f + sim.rng.nextFloat() * 0.6f)
                if (r.hostile && sim.day > GRACE_DAYS && !(sim.environment.season(sim.day) == Season.WINTER && r.personality != RivalPersonality.AGGRESSIVE)) {
                    launchRaid(sim, r)
                }
            }
        }
    }

    /** Soyut ekonomi: toplama, tüketim, büyüme, asker ve savunma; oyuncuya göre ölçeklenir. */
    private fun economy(sim: Simulation, dt: Float) {
        val season = sim.environment.season(sim.day)
        val player = playerPower(sim)
        for (r in rivals) {
            if (!r.alive) continue
            val p = r.personality
            r.food += r.population * 0.05f * p.gather * season.sourceMult * dt / 10f
            r.food -= (r.population + r.soldiers * 1.5f) * 0.03f * dt / 10f
            // Oyuncudan çok güçlü olan rakip yavaşlar (dengeli zorluk).
            val cap = player * 1.5f + 30f + sim.day * 2f
            val growthOk = r.strength < cap
            if (r.food > r.population * 2f && growthOk && sim.rng.nextFloat() < 0.25f * p.breed) { r.population++; r.food -= 4f }
            if (r.food > 10f && growthOk && sim.rng.nextFloat() < 0.12f * p.militarism && r.soldiers < r.population / 2) { r.soldiers++; r.food -= 5f }
            if (growthOk && sim.rng.nextFloat() < 0.08f * p.defense) r.defense += 1f
            if (r.food < 0f) { r.food = 0f; if (r.population > 4 && sim.rng.nextFloat() < 0.3f) r.population-- }
            if (sim.rng.nextFloat() < 0.002f) r.techLevel++
            // İlişki kişiliğin temel değerine doğru yavaşça döner.
            if (sim.rng.nextFloat() < 0.1f) r.relation += Integer.signum(p.baseRelation - r.relation)
            // Çok zayıflayan koloni göç eder.
            if (r.population < 4 && sim.rng.nextFloat() < 0.05f) relocate(sim, r)
            // Müttefikler günlük hediye gönderir.
            if (r.treaty == Treaty.ALLIANCE && sim.rng.nextFloat() < dt / Simulation.DAY_LENGTH.toFloat() * 2f) {
                val n = sim.addCapped(ResourceType.FOOD, 6 + r.population / 4)
                if (n > 0) sim.notify("${r.name} müttefik hediyesi gönderdi: +$n yiyecek")
            }
        }
    }

    private fun relocate(sim: Simulation, r: RivalColony) {
        val side = if (r.x > 0) 1 else -1
        r.x = side * (kotlin.math.abs(r.x) + 30 + sim.rng.nextInt(40))
        r.population += 6
        r.food += 20f
        sim.addLog("${r.name} zayıfladı ve yeni bir bölgeye taşındı.")
    }

    private fun respawn(sim: Simulation, r: RivalColony) {
        r.alive = true
        r.name = randomName(sim)
        r.population = 10 + sim.day / 2
        r.soldiers = 2 + sim.day / 6
        r.food = 40f
        r.defense = 8f + sim.day
        r.relation = r.personality.baseRelation
        r.treaty = Treaty.NEUTRAL
        r.discovered = false
        sim.addLog("Eski bir rakip bölgesinde yeni bir koloni kuruldu: ${r.name}.")
    }

    /** Baskın: sayısı oyuncunun savunmasıyla sınırlıdır; hiçbir baskın koloniyi bir anda silecek büyüklükte olmaz. */
    fun launchRaid(sim: Simulation, r: RivalColony, force: Boolean = false) {
        val defense = playerPower(sim)
        val maxBySafety = (2 + defense / 7f).toInt()
        var count = minOf(r.soldiers / 2 + 1, maxBySafety, 12)
        if (force) count = maxOf(count, 2)
        if (count < 2 && !force) return
        r.soldiers = (r.soldiers - count).coerceAtLeast(0)
        r.raidsLaunched++
        r.discovered = true
        val kind = if (r.personality == RivalPersonality.ELITE) CreatureKind.RIVAL_ELITE else CreatureKind.RIVAL_SOLDIER
        // Akıncılar yüzeyde, oyuncuya doğru bir mesafede belirir (yolculuk süresi uyarı zamanı verir).
        val dir = if (r.x > 0) 1 else -1
        val startX = dir * minOf(kotlin.math.abs(r.x), 60)
        repeat(count) { i ->
            val c = sim.spawnCreature(kind, startX + dir * (i % 3), -1, r.id)
            c.attackMult = 1f + r.techLevel * 0.05f
            sim.targetForRaider(c)
        }
        sim.relationChange(r, -5)
        sim.notify("${r.name} baskına geçti! $count akıncı yaklaşıyor. Askerleri toplayın, tünelleri kapatın.")
        sim.addLog("${r.name} $count akıncıyla baskın düzenledi.")
        sim.sound(SoundCue.ALERT)
    }

    /** Çevrimdışı: ekonomi ilerler ama baskın olmaz; dönüşte en az yarım gün baskın gelmez. */
    fun offlineAdvance(sim: Simulation, seconds: Float) {
        var left = seconds
        var n = 0
        while (left > 0f && n < 60) { economy(sim, minOf(60f, left)); left -= 60f; n++ }
        for (r in rivals) r.raidTimer = maxOf(r.raidTimer - seconds, Simulation.DAY_LENGTH.toFloat() / 2f)
    }

    fun raiderReturned(r: RivalColony?, stolen: Int) {
        if (r == null) return
        r.soldiers++
        r.food += stolen
    }

    fun offers(r: RivalColony): List<TradeOffer> = when (r.personality) {
        RivalPersonality.HOARDER -> listOf(TradeOffer(ResourceType.FOOD, 12, ResourceType.MINERAL, 4), TradeOffer(ResourceType.LEAF, 10, ResourceType.RESIN, 1), TradeOffer(ResourceType.SOIL, 20, ResourceType.SEED, 8))
        RivalPersonality.ELITE -> listOf(TradeOffer(ResourceType.FOOD, 25, ResourceType.RARE_MINERAL, 1), TradeOffer(ResourceType.MUSHROOM, 12, ResourceType.GENETIC, 1))
        RivalPersonality.BURROWER -> listOf(TradeOffer(ResourceType.FOOD, 15, ResourceType.MINERAL, 5), TradeOffer(ResourceType.WOOD, 8, ResourceType.FOSSIL, 1))
        RivalPersonality.SURFACE -> listOf(TradeOffer(ResourceType.MINERAL, 3, ResourceType.FOOD, 12), TradeOffer(ResourceType.SOIL, 15, ResourceType.LEAF, 10))
        else -> listOf(TradeOffer(ResourceType.SOIL, 15, ResourceType.FOOD, 8), TradeOffer(ResourceType.MUSHROOM, 8, ResourceType.WOOD, 6), TradeOffer(ResourceType.FOOD, 15, ResourceType.WATER, 10))
    }
}
