package com.yeraltiimparatorlugu.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResearchAndRoomsTest {
    @Test
    fun yirmiBesOdaTuruVar() {
        assertEquals(25, RoomType.entries.size)
        assertEquals(15, AntType.entries.size)
        assertEquals(10, PheromoneType.entries.size)
        assertEquals(6, ResearchCategory.entries.size)
        assertTrue(ResearchTree.all.groupBy { it.category }.all { it.value.size >= 4 })
        // Tüm araştırma önkoşulları geçerli ve döngüsüz.
        val ids = ResearchTree.all.map { it.id }.toSet()
        ResearchTree.all.forEach { d -> d.prereqs.forEach { assertTrue(it in ids, "${d.id} -> $it") } }
        RoomType.entries.mapNotNull { it.requires }.forEach { assertTrue(it in ids, it) }
        AntType.entries.mapNotNull { it.requires }.forEach { assertTrue(it in ids, it) }
    }

    @Test
    fun arastirmaLaboratuvardaIsciCalismasiylaIlerler() {
        val sim = Simulation.newGame(5)
        sim.richResources()
        sim.instantRoom(RoomType.RESEARCH_LAB, -8, 8)
        sim.priorities.set(TaskCategory.RESEARCH, 1)
        val def = ResearchTree.get("dig_tools")!!
        assertNull(sim.startResearch(def))
        assertNotNull(sim.startResearch(ResearchTree.get("ph_lasting")!!), "Aynı anda tek araştırma")
        sim.run(200f)
        assertTrue("dig_tools" in sim.research.completed, "ilerleme ${sim.research.progress}")
        assertEquals(1.25f, sim.mods.digSpeed, 0.001f)
    }

    @Test
    fun arastirmasizOdaInsaEdilemez() {
        val sim = Simulation.newGame(5)
        sim.richResources()
        val p = sim.checkRoomPlacement(RoomType.BARRACKS, -8, 7)
        assertFalse(p.ok)
        assertTrue(p.reason!!.startsWith("Araştırma gerekli"))
        sim.completeAllResearch("cb_castes", "mil_soldiers")
        assertTrue(sim.checkRoomPlacement(RoomType.BARRACKS, -7, 7).ok, sim.checkRoomPlacement(RoomType.BARRACKS, -7, 7).reason)
    }

    @Test
    fun sertKayaArastirmasiKayayiKazdirir() {
        val sim = Simulation.newGame(5)
        val rock = (-200..200).flatMap { x -> (12..60).map { y -> x to y } }.first { (x, y) -> sim.world.get(x, y) == Terrain.ROCK }
        sim.world.reveal(rock.first, rock.second, 0)
        assertEquals(DigDesignation.NEEDS_TECH, sim.designateDig(rock.first, rock.second))
        sim.completeAllResearch("dig_tools", "dig_rock")
        assertEquals(DigDesignation.OK, sim.designateDig(rock.first, rock.second))
    }

    @Test
    fun egitimOdasiIsciyiUzmanSinifaCevirir() {
        val sim = Simulation.newGame(6)
        sim.richResources()
        sim.completeAllResearch("cb_castes")
        val room = sim.instantRoom(RoomType.TRAINING_ROOM, -8, 8)
        sim.setTraining(room, AntType.GATHERER, 2)
        sim.priorities.set(TaskCategory.PRODUCE, 1)
        sim.run(120f)
        assertEquals(2, sim.ants.count { it.type == AntType.GATHERER }, "eğitilen: ${sim.stats.antsTrained}")
        assertTrue(sim.ants.filter { it.type == AntType.GATHERER }.all { it.assignment == TaskCategory.GATHER })
    }

    @Test
    fun askerEgitimiKislaGerektirir() {
        val sim = Simulation.newGame(6)
        sim.richResources()
        sim.completeAllResearch("cb_castes", "mil_soldiers")
        assertEquals("Asker Kışlası gerekli", sim.canTrain(AntType.SOLDIER))
        sim.instantRoom(RoomType.BARRACKS, 8, 9)
        assertNull(sim.canTrain(AntType.SOLDIER))
    }

    @Test
    fun atolyeOdunuRecineyeCevirir() {
        val sim = Simulation.newGame(6)
        sim.richResources()
        sim.completeAllResearch("prod_fungus", "prod_workshop")
        val ws = sim.instantRoom(RoomType.WORKSHOP, -8, 8)
        ws.recipe = Recipe.RESIN
        val resin = sim.resources[ResourceType.RESIN]
        sim.priorities.set(TaskCategory.PRODUCE, 1)
        sim.run(90f)
        assertTrue(sim.resources[ResourceType.RESIN] > resin)
    }

    @Test
    fun evrimPuanItibarlaGelir() {
        val sim = Simulation.newGame(6)
        assertNotNull(sim.evolve(EvolutionPath.WAR))
        sim.stats.cellsDug = 20000 // itibar
        sim.run(1.5f)
        assertTrue(sim.evolution.points(sim.reputation) > 0)
        assertNull(sim.evolve(EvolutionPath.WAR))
        assertNull(sim.evolve(EvolutionPath.PRODUCTION))
        assertTrue(sim.mods.attackMult > 1f && sim.mods.gatherMult > 1f, "hibrit evrim")
        assertNull(sim.evolve(EvolutionPath.WAR))
        assertEquals("3. kademe ve sonrası için Kraliyet Merkezi gerekli", sim.evolve(EvolutionPath.WAR))
    }

    @Test
    fun genetikOzellikOrneklerleGelistirilir() {
        val sim = Simulation.newGame(6)
        sim.richResources()
        sim.completeAllResearch("cb_castes", "cb_medicine", "prod_fungus", "prod_workshop", "cb_genetics")
        sim.instantRoom(RoomType.GENETICS_LAB, -8, 8)
        sim.priorities.set(TaskCategory.RESEARCH, 1)
        assertNull(sim.startTrait(GeneticTrait.THICK_SHELL))
        sim.run(150f)
        assertTrue(GeneticTrait.THICK_SHELL in sim.genetics.unlocked)
        assertTrue(sim.mods.defenseMult > 1.1f)
    }
}

class EcosystemTest {
    @Test
    fun mevsimlerSirayla() {
        val env = Environment()
        assertEquals(Season.SPRING, env.season(1))
        assertEquals(Season.SUMMER, env.season(4))
        assertEquals(Season.AUTUMN, env.season(7))
        assertEquals(Season.WINTER, env.season(10))
        assertEquals(Season.SPRING, env.season(13))
        assertEquals(2, env.year(13))
    }

    @Test
    fun suAsagiAkarVeZeminceEmilir() {
        val sim = Simulation.newGame(7)
        for (y in 20..30) sim.world.set(50, y, Terrain.TUNNEL)
        sim.environment.addWater(50, 20, 1f)
        sim.environment.weather = Weather.CLEAR
        sim.environment.weatherUntil = 1e9
        sim.run(3f)
        val top = sim.environment.waterAt(50, 20)
        val bottom = (25..30).sumOf { sim.environment.waterAt(50, it).toDouble() }
        assertTrue(top < 0.5f && bottom > 0.3, "su aşağı inmeli: üst=$top alt=$bottom")
        sim.run(240f)
        assertTrue(sim.environment.water.isEmpty(), "zemin suyu emmeli")
    }

    @Test
    fun saganakGiristenSuSokar() {
        val sim = Simulation.newGame(7)
        sim.environment.forceWeather(sim, Weather.STORM, 1e6)
        sim.run(30f)
        assertTrue(sim.environment.water.isNotEmpty())
        assertTrue((0..12).any { sim.environment.waterAt(0, it) > 0f })
    }

    @Test
    fun suDeposuSelSuyunuToplar() {
        val sim = Simulation.newGame(7)
        sim.completeAllResearch("eco_water")
        val room = sim.instantRoom(RoomType.WATER_STORAGE, -8, 8)
        val before = sim.resources[ResourceType.WATER]
        room.forEachCell { x, y -> sim.environment.addWater(x, y, 0.8f) }
        sim.environment.weatherUntil = 1e9
        sim.run(10f)
        assertTrue(sim.resources[ResourceType.WATER] > before)
    }

    @Test
    fun iklimYavruGelisiminiEtkiler() {
        val env = Environment()
        val m = Modifiers()
        env.colonyTemp = 25f; env.humidity = 60f
        val ideal = env.climateFactor(m)
        env.colonyTemp = 5f; env.humidity = 20f
        val bad = env.climateFactor(m)
        assertTrue(ideal > 1f && bad < 0.6f, "$ideal $bad")
        m.coldTolerance = 0.5f; m.climateTolerance = 0.5f
        assertTrue(env.climateFactor(m) > bad)
    }

    @Test
    fun tumDunyaOlaylariCalisir() {
        val sim = Simulation.newGame(8)
        sim.richResources()
        for (x in -3 downTo -12) { sim.world.set(x, 9, Terrain.TUNNEL); sim.world.set(x, 10, Terrain.TUNNEL) }
        sim.time = Simulation.DAY_LENGTH * 4
        for (k in WorldEventKind.entries) { sim.events.trigger(sim, k); sim.run(2f) }
        assertTrue(sim.events.history.map { it.second }.toSet().size == WorldEventKind.entries.size)
        assertTrue(sim.ants.any { it.sick } || sim.stats.antsDied > 0)
    }

    @Test
    fun biyomlarDegisir() {
        val set = (-400..400 step 36).map { Biome.at(42, it) }.toSet()
        assertTrue(set.size >= 4, "$set")
        assertEquals(Biome.MEADOW, Biome.at(42, 5))
    }
}

class CombatTest {
    @Test
    fun askerlerTehlikeliCanliyiYener() {
        val sim = Simulation.newGame(9)
        sim.richResources()
        repeat(4) { sim.ants.add(Ant(sim.newId(), AntType.SOLDIER, 0.5f + it, 9.5f)) }
        val spider = sim.spawnCreature(CreatureKind.SPIDER, -2, 9)
        sim.run(40f)
        assertTrue(spider !in sim.creatures, "örümcek yenilmeli")
        assertEquals(1, sim.stats.enemiesKilled)
        assertTrue(sim.corpses.isNotEmpty())
    }

    @Test
    fun ilkGunlerdeBaskinYokVeBaskinBoyutuSinirli() {
        val sim = Simulation.newGame(10)
        sim.run(Simulation.DAY_LENGTH.toFloat() * 2)
        assertTrue(sim.creatures.none { it.rivalId >= 0 }, "korunma süresi")
        val r = sim.rivalSystem.rivals.first()
        r.soldiers = 100
        sim.rivalSystem.launchRaid(sim, r, force = true)
        val raiders = sim.creatures.count { it.rivalId == r.id }
        assertTrue(raiders in 2..5, "zayıf koloniye büyük baskın olmamalı: $raiders")
    }

    @Test
    fun baskinPuskurtulur() {
        val sim = Simulation.newGame(11)
        sim.richResources()
        repeat(6) { sim.ants.add(Ant(sim.newId(), AntType.HEAVY_SOLDIER, 0.5f, 8.5f)) }
        val r = sim.rivalSystem.rivals.first()
        r.soldiers = 6
        sim.rivalSystem.launchRaid(sim, r, force = true)
        sim.run(240f)
        assertTrue(sim.creatures.none { it.rivalId == r.id })
        assertTrue(sim.stats.enemiesKilled > 0 || sim.stats.raidsRepelled > 0)
        assertTrue(sim.queen != null)
    }

    @Test
    fun tunelKapatmaYoluKeser() {
        val sim = Simulation.newGame(12)
        sim.richResources()
        assertNull(sim.designateFill(0, 3))
        sim.priorities.set(TaskCategory.REPAIR, 1)
        sim.run(60f)
        assertEquals(Terrain.FILL, sim.world.get(0, 3))
        assertNull(sim.pathfinder.find(0, -1, 0, 8))
        assertNotNull(sim.designateFill(0, -1))
        assertNotNull(sim.designateFill(0, 8), "oda hücresi doldurulamaz")
    }

    @Test
    fun rakibeSeferVeZafer() {
        val sim = Simulation.newGame(13)
        sim.richResources()
        val r = sim.rivalSystem.rivals.minByOrNull { kotlin.math.abs(it.x) }!!
        r.soldiers = 1; r.defense = 2f; r.population = 6
        repeat(8) { sim.ants.add(Ant(sim.newId(), AntType.ELITE, 0.5f, 8.5f).also { a -> a.squad = 0 }) }
        sim.setOrder(0, OrderKind.RAID_RIVAL, targetId = r.id)
        sim.run(240f)
        assertTrue(sim.stats.rivalsDefeated >= 1, "rakip yenilmeli (asker ${r.soldiers}, savunma ${r.defense})")
        assertEquals(OrderKind.DEFEND, sim.orders[0].kind)
        assertEquals(Treaty.WAR, r.treaty)
    }

    @Test
    fun tuzakOdasiDusmaniYaralar() {
        val sim = Simulation.newGame(14)
        sim.completeAllResearch("mil_traps")
        val trap = sim.instantRoom(RoomType.TRAP_ROOM, -8, 9)
        sim.ants.removeAll { it.type != AntType.QUEEN }
        val c = sim.spawnCreature(CreatureKind.TERMITE, trap.x + 1, trap.y)
        c.hasGoal = false
        sim.run(5f)
        assertTrue(c.hp < CreatureKind.TERMITE.hp)
    }
}

class DiplomacyAndProgressTest {
    @Test
    fun diplomasiAdimlari() {
        val sim = Simulation.newGame(15)
        sim.richResources()
        val r = sim.rivalSystem.rivals.first { it.personality == RivalPersonality.HOARDER }
        assertNotNull(sim.gift(r), "keşfedilmemiş")
        r.discovered = true
        val rel = r.relation
        assertNull(sim.gift(r))
        assertEquals(rel + 12, r.relation)
        assertEquals("Ticaret Odası gerekli", sim.proposeTrade(r))
        sim.completeAllResearch("prod_carry", "prod_storage", "prod_trade")
        sim.instantRoom(RoomType.TRADE_ROOM, -8, 8)
        r.relation = 30
        assertNull(sim.proposeTrade(r))
        val offer = sim.rivalSystem.offers(r).first()
        sim.resources.set(offer.give, offer.giveAmount + 5)
        sim.resources.set(offer.get, 0)
        val got = sim.resources[offer.get]
        assertNull(sim.trade(r, offer))
        assertEquals(got + offer.getAmount, sim.resources[offer.get])
        r.relation = 70
        assertNull(sim.proposeAlliance(r))
        assertEquals(Treaty.ALLIANCE, r.treaty)
        sim.declareWar(r)
        assertEquals(Treaty.WAR, r.treaty)
        assertTrue(r.hostile)
    }

    @Test
    fun rakipEkonomisiBuyur() {
        val sim = Simulation.newGame(16)
        val before = sim.rivalSystem.rivals.sumOf { it.population }
        sim.run(600f)
        assertTrue(sim.rivalSystem.rivals.sumOf { it.population } > before)
    }

    @Test
    fun seferDonerVeOdulGetirir() {
        val sim = Simulation.newGame(17)
        sim.richResources()
        repeat(6) { sim.ants.add(Ant(sim.newId(), AntType.WORKER, 0.5f, 9.5f)) }
        assertNotNull(sim.expeditions.start(sim, 0, 4), "Keşif merkezi gerekir")
        sim.completeAllResearch("dig_tools", "dig_survey")
        sim.instantRoom(RoomType.EXPLORATION_CENTER, -8, 8)
        val pop = sim.population
        assertNull(sim.expeditions.start(sim, 0, 4))
        assertEquals(pop - 4, sim.population)
        sim.run(200f)
        assertTrue(sim.expeditions.active.isEmpty())
        assertTrue(sim.population >= pop - 4 + 1)
    }

    @Test
    fun fosilMuzesiKatalogu() {
        val sim = Simulation.newGame(18)
        sim.resources.set(ResourceType.FOSSIL, 5)
        assertEquals("Önce Fosil Müzesi inşa edin", sim.museum.examine(sim))
        sim.completeAllResearch("dig_tools", "dig_survey", "dig_paleo")
        sim.instantRoom(RoomType.FOSSIL_MUSEUM, -9, 8)
        repeat(5) { sim.museum.examine(sim) }
        assertTrue(sim.museum.found.isNotEmpty())
        assertEquals(0, sim.resources[ResourceType.FOSSIL])
        assertTrue(sim.museum.displayed(sim).size <= 4)
    }

    @Test
    fun yanKoloniKaynakGonderir() {
        val sim = Simulation.newGame(19)
        sim.richResources()
        repeat(10) { sim.ants.add(Ant(sim.newId(), AntType.WORKER, 0.5f, 9.5f)) }
        assertNotNull(sim.federation.found(sim))
        sim.resources.set(ResourceType.FOOD, 60); sim.resources.set(ResourceType.ROYAL_ESSENCE, 3)
        sim.completeAllResearch("cb_castes", "cb_fertility", "cb_medicine", "prod_fungus", "prod_workshop", "cb_genetics", "cb_royal")
        sim.instantRoom(RoomType.ROYAL_CENTER, -9, 8)
        assertNull(sim.federation.found(sim))
        val c = sim.federation.colonies.single()
        c.food = 200f
        val food = sim.resources.edibleTotal()
        sim.resources.set(ResourceType.FOOD, 0)
        sim.run(185f)
        assertTrue(c.totalSent > 0, "gönderim: ${c.totalSent} (eski stok $food)")
    }

    @Test
    fun marketJetonlaCalisir() {
        val sim = Simulation.newGame(20)
        assertTrue(sim.market.buy(sim, MarketItem.SKIN_RED).startsWith("Yetersiz"))
        sim.coins = 100
        sim.market.buy(sim, MarketItem.SKIN_RED)
        assertEquals(80, sim.coins)
        assertTrue(sim.market.isEquipped(MarketItem.SKIN_RED))
        assertEquals("Zaten sahipsiniz", sim.market.buy(sim, MarketItem.SKIN_RED))
        sim.market.buy(sim, MarketItem.BOOST_RESEARCH)
        assertTrue(sim.research.boostUntil > sim.time)
        assertFalse(OfflinePurchaseBackend.realMoneyAvailable)
    }

    @Test
    fun basarimVeImparatorlukHedefi() {
        val sim = Simulation.newGame(21)
        sim.stats.cellsDug = 12
        sim.run(1.2f)
        assertTrue(Achievement.FIRST_TUNNEL in sim.achievements.unlocked)
        assertTrue(sim.coins >= Achievement.FIRST_TUNNEL.coins)
        sim.empireGoal.kind = EmpireGoalKind.TERRITORY
        sim.stats.cellsDug = 250
        sim.run(1.2f)
        assertEquals(1, sim.empireGoal.tier)
        assertTrue(Reputation.title(sim.reputation).isNotEmpty())
    }

    @Test
    fun hastalikSaglikOdasindaIyilesir() {
        val sim = Simulation.newGame(22)
        sim.richResources()
        sim.completeAllResearch("cb_castes", "cb_medicine")
        sim.instantRoom(RoomType.INFIRMARY, -8, 8)
        sim.workers.take(3).forEach { it.sick = true }
        sim.run(120f)
        assertTrue(sim.ants.count { it.sick } < 3)
    }

    @Test
    fun kesifciYuzeyMenziliniGenisletir() {
        val sim = Simulation.newGame(23)
        sim.ants.add(Ant(sim.newId(), AntType.SCOUT, 0.5f, 9.5f).also { it.assignment = TaskCategory.EXPLORE })
        val before = sim.knownMax() - sim.knownMin()
        sim.run(120f)
        assertTrue(sim.knownMax() - sim.knownMin() > before)
    }
}

class SaveV2Test {
    @Test
    fun yeniSistemlerKaydaYazilir() {
        val sim = Simulation.newGame(30)
        sim.richResources()
        sim.completeAllResearch("dig_tools", "cb_castes")
        sim.research.current = "dig_rock"; sim.research.progress = 12f
        sim.coins = 77
        sim.market.owned.add(MarketItem.SKIN_GOLD); sim.market.equip(MarketItem.SKIN_GOLD)
        sim.museum.found.add(FossilKind.TRILOBITE)
        sim.spawnCreature(CreatureKind.SPIDER, 30, 30)
        sim.environment.addWater(0, 3, 0.5f)
        sim.designateFill(0, 2)
        sim.rivalSystem.rivals.first().relation = 42
        sim.setOrder(1, OrderKind.GUARD, 3, 9)
        sim.evolution.ranks[2] = 1
        sim.empireGoal.kind = EmpireGoalKind.POPULATION
        val st = MemoryStorage()
        SaveManager(st).save(sim, 1_800_000_000_000L)
        val l = SaveManager(st).load(1_800_000_001_000L)!!.sim
        assertEquals(sim.research.completed, l.research.completed)
        assertEquals("dig_rock", l.research.current)
        assertEquals(77, l.coins)
        assertTrue(l.market.isEquipped(MarketItem.SKIN_GOLD))
        assertTrue(FossilKind.TRILOBITE in l.museum.found)
        assertEquals(1, l.creatures.size)
        assertTrue(l.environment.waterAt(0, 3) > 0f)
        assertTrue(l.fillOrders.isNotEmpty())
        assertEquals(42, l.rivalSystem.rivals.first().relation)
        assertEquals(OrderKind.GUARD, l.orders[1].kind)
        assertEquals(1, l.evolution.ranks[2])
        assertEquals(EmpireGoalKind.POPULATION, l.empireGoal.kind)
        assertEquals(sim.rivalSystem.rivals.map { it.x }, l.rivalSystem.rivals.map { it.x })
        l.run(20f)
    }

    @Test
    fun surum1KaydiYuklenir() {
        // Sürüm 1 biçimi: yeni alanların hiçbiri yok.
        val sim = Simulation.newGame(31)
        val json = kotlinx.serialization.json.Json { encodeDefaults = false }
        val save = SaveMapper.toSave(sim, 1_800_000_000_000L, 0L).copy(version = 1, rivals = emptyList())
        val obj = json.parseToJsonElement(json.encodeToString(GameSave.serializer(), save)).let { it as kotlinx.serialization.json.JsonObject }
        val v1 = kotlinx.serialization.json.JsonObject(obj.filterKeys { it !in setOf("rivals", "researchDone", "coins", "environment", "orders") })
        val payload = v1.toString()
        val crc = java.util.zip.CRC32().apply { update(payload.toByteArray()) }.value
        val env = kotlinx.serialization.json.buildJsonObject {
            put("format", kotlinx.serialization.json.JsonPrimitive("yeralti-save")); put("version", kotlinx.serialization.json.JsonPrimitive(1))
            put("crc", kotlinx.serialization.json.JsonPrimitive(crc)); put("payload", kotlinx.serialization.json.JsonPrimitive(payload))
        }
        val st = MemoryStorage()
        st.files["koloni.json"] = env.toString()
        val loaded = SaveManager(st).load(1_800_000_001_000L)!!.sim
        assertEquals(RivalPersonality.entries.size, loaded.rivalSystem.rivals.size, "rakipler yeniden kurulur")
        assertEquals(sim.ants.size, loaded.ants.size)
        loaded.run(10f)
    }

    @Test
    fun cevrimdisiBaskinOlmazArastirmaIlerler() {
        val sim = Simulation.newGame(32)
        sim.richResources()
        sim.instantRoom(RoomType.RESEARCH_LAB, -8, 8)
        sim.research.current = "dig_tools"
        val r = sim.rivalSystem.rivals.first()
        r.raidTimer = 1f; r.relation = -90; r.treaty = Treaty.WAR
        sim.spawnCreature(CreatureKind.RIVAL_SOLDIER, 40, -1, r.id)
        sim.time = Simulation.DAY_LENGTH * 5
        val lines = OfflineProgress.advance(sim, 3 * 3600f)
        assertTrue(sim.creatures.none { it.rivalId >= 0 })
        assertTrue(r.raidTimer >= Simulation.DAY_LENGTH / 2)
        assertTrue("dig_tools" in sim.research.completed || sim.research.progress > 0f, lines.joinToString())
    }
}

class EndlessGameTest {
    /** Bütün sistemler açıkken uzun oyun: çökme, istisna veya koloninin yok olması olmamalı. */
    @Test
    fun ikiOyunGunuTumSistemlerleKararli() {
        val sim = Simulation.newGame(77)
        sim.richResources()
        sim.completeAllResearch(*ResearchTree.all.map { it.id }.toTypedArray())
        sim.instantRoom(RoomType.REST_ROOM, -8, 8)
        sim.instantRoom(RoomType.BARRACKS, 8, 9)
        sim.instantRoom(RoomType.INFIRMARY, -9, 12)
        sim.instantRoom(RoomType.TRAINING_ROOM, 9, 13)
        for (x in -3 downTo -20) sim.designateDig(x, 16)
        for (y in 17..40) sim.designateDig(-20, y)
        repeat(4) { sim.ants.add(Ant(sim.newId(), AntType.SOLDIER, 0.5f, 9.5f)) }
        sim.ants.add(Ant(sim.newId(), AntType.SCOUT, 0.5f, 9.5f).also { it.assignment = TaskCategory.EXPLORE })
        sim.time = Simulation.DAY_LENGTH * 3
        sim.rivalSystem.rivals.forEach { it.raidTimer = 60f * (it.id) }
        for (k in WorldEventKind.entries) sim.events.trigger(sim, k)
        sim.run(Simulation.DAY_LENGTH.toFloat() * 2)
        assertNotNull(sim.queen)
        assertTrue(sim.population >= 4, "nüfus ${sim.population}, ölen ${sim.stats.antsDied}")
        assertTrue(sim.ants.all { it.x.isFinite() && it.y.isFinite() })
        assertTrue(sim.creatures.all { it.x.isFinite() })
    }
}

class EaseTest {
    @Test
    fun odaYeriOnerisiGecerliVeKomsulukGozetir() {
        val sim = Simulation.newGame(40)
        val p = sim.suggestRoomPlacement(RoomType.EGG_ROOM)
        kotlin.test.assertNotNull(p)
        assertTrue(sim.checkRoomPlacement(RoomType.EGG_ROOM, p.first, p.second).ok)
        val probe = Room(-1, RoomType.EGG_ROOM, p.first, p.second)
        assertTrue(sim.rooms.any { it.type == RoomType.QUEEN_CHAMBER && it.isAdjacentTo(probe) }, "Kraliçe odasına komşu olmalı: $p")
        // Önerilen yere gerçekten kurulabilir.
        assertNotNull(sim.placeRoom(RoomType.EGG_ROOM, p.first, p.second))
    }

    @Test
    fun danismanSorunlariOnerir() {
        val sim = Simulation.newGame(41)
        sim.resources.set(ResourceType.FOOD, 0); sim.resources.set(ResourceType.SEED, 0); sim.resources.set(ResourceType.MUSHROOM, 0)
        val a = Advisor.advice(sim)
        assertTrue(a.any { it.id == "food" && it.level == AdviceLevel.DANGER })
        repeat(2) { sim.ants.add(Ant(sim.newId(), AntType.WORKER, 0.5f, 9.5f)) }
        sim.resources.set(ResourceType.FOOD, 30)
        assertTrue(Advisor.advice(sim).any { it.id == "housing" && it.action is AdviceAction.Build })
    }

    @Test
    fun rahatZorlukBaskiniGeciktirir() {
        val sim = Simulation.newGame(42)
        assertEquals(Difficulty.RELAXED, sim.difficulty)
        sim.run(Simulation.DAY_LENGTH.toFloat() * 4.5f)
        assertTrue(sim.creatures.none { it.rivalId >= 0 }, "Rahat modda ilk 6 gün baskın yok")
    }
}
