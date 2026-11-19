package com.yuruyensehir.logic

import com.yuruyensehir.logic.Resource.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TradeDiplomacyTest {

    private fun core(seed: Long = 11L): GameCore {
        val c = GameCore.newGame(seed)
        val s = c.state
        s.buildings += PlacedBuilding(s.nextBuildingId++, BuildingType.MATERIAL_STORAGE, 10, 3)
        ResourceSystem.recalcCapacity(s)
        s.store.amounts[GOLD] = 2000.0
        listOf(FOOD, WATER, WOOD, STONE, METAL, CLOTH).forEach { s.store.amounts[it] = 150.0 }
        return c
    }

    /** Başlangıçtaki tüccar loncasını bulur ve şehri yanına koyar. */
    private fun merchantNearby(s: GameState): Civilization {
        val m = s.civs.first { it.culture == Culture.MERCHANT_GUILD }
        s.travel.position = m.position
        CivSystem.meetCheck(s, offline = false)
        return m
    }

    @Test
    fun startingRegionAlwaysHasFriendlyMerchant() {
        val s = core().state
        val m = s.civs.firstOrNull { it.culture == Culture.MERCHANT_GUILD }
        assertNotNull(m)
        assertTrue(m.position.dist(Cell(0, 0)) <= 6)
        assertTrue(m.relation > 0)
        assertFalse(m.met)
    }

    @Test
    fun civGenerationIsDeterministic() {
        val a = core(5).state; val b = core(5).state
        a.travel.position = Cell(30, -20); b.travel.position = Cell(30, -20)
        CivSystem.ensureNearby(a); CivSystem.ensureNearby(b)
        assertEquals(a.civs.map { it.name to it.home }, b.civs.map { it.name to it.home })
    }

    @Test
    fun worldKeepsGeneratingCivsAsYouTravel() {
        val s = core().state
        val n0 = s.civs.size
        for (i in 1..8) { s.travel.position = Cell(i * 12, i * 7); CivSystem.ensureNearby(s) }
        assertTrue(s.civs.size > n0 + 3, "civs=${s.civs.size}")
    }

    @Test
    fun meetingHappensWhenClose() {
        val c = core(); val s = c.state
        val m = s.civs.first { it.culture == Culture.MERCHANT_GUILD }
        s.travel.position = Cell(m.position.x - 2, m.position.y)
        c.step(1.0)
        assertTrue(m.met)
        assertTrue(s.log.any { it.kind == LogKind.DIPLOMACY })
    }

    @Test
    fun pricesReflectBiomeAndCulture() {
        val s = core().state
        val m = merchantNearby(s)
        // Tüccar yiyecek ve su istiyor, alet ve kumaş sunuyor
        assertTrue(TradeSystem.quote(s, m, WATER).buy > TradeSystem.quote(s, m, WOOD).buy * 0.7)
        assertTrue(TradeSystem.quote(s, m, TOOLS).sell < TradeSystem.quote(s, m, TOOLS).buy)
        assertTrue(TradeSystem.biomeFactor(Biome.DESERT, WATER) > TradeSystem.biomeFactor(Biome.LAKE, WATER))
        assertTrue(TradeSystem.biomeFactor(Biome.FOREST, WOOD) < 1.0)
    }

    @Test
    fun sellingLowersPriceAndBuyingRaisesIt() {
        val s = core().state
        val m = merchantNearby(s)
        val p0 = TradeSystem.quote(s, m, WOOD).buy
        repeat(5) { assertTrue(TradeSystem.sell(s, m.id, WOOD).success) }
        val p1 = TradeSystem.quote(s, m, WOOD).buy
        assertTrue(p1 < p0, "p0=$p0 p1=$p1")
        repeat(10) { TradeSystem.buy(s, m.id, WOOD) }
        assertTrue(TradeSystem.quote(s, m, WOOD).buy > p1)
    }

    @Test
    fun buyAndSellMoveGoodsAndGold() {
        val s = core().state
        val m = merchantNearby(s)
        val gold = s.store[GOLD]; val tools = s.store[TOOLS]
        assertTrue(TradeSystem.buy(s, m.id, TOOLS).success)
        assertEquals(tools + 10, s.store[TOOLS], 1e-6)
        assertTrue(s.store[GOLD] < gold)
        val g2 = s.store[GOLD]
        assertTrue(TradeSystem.sell(s, m.id, FOOD).success)
        assertTrue(s.store[GOLD] > g2)
        assertTrue(s.reputation > 20.0)
    }

    @Test
    fun tradeBlockedWhenFarOrAtWar() {
        val s = core().state
        val m = merchantNearby(s)
        s.travel.position = Cell(m.position.x + 9, m.position.y)
        assertFalse(TradeSystem.buy(s, m.id, TOOLS).success)
        s.travel.position = m.position
        assertTrue(DiplomacySystem.declareWar(s, m.id).success)
        assertFalse(TradeSystem.buy(s, m.id, TOOLS).success)
        assertEquals(Relation.ENEMY, m.status)
    }

    @Test
    fun marketExtendsRangeAndImprovesSellPrice() {
        val s = core().state
        val m = merchantNearby(s)
        val sell0 = TradeSystem.quote(s, m, WOOD).sell
        s.stage = 2
        assertTrue(BuildingSystem.place(s, BuildingType.TRADE_MARKET, 11, 6, instant = true).success)
        assertTrue(TradeSystem.hasMarket(s))
        assertEquals(4, TradeSystem.range(s, m))
        assertTrue(TradeSystem.quote(s, m, WOOD).sell > sell0)
    }

    @Test
    fun treatiesRequireRelationAndCost() {
        val s = core().state
        val m = merchantNearby(s)
        m.relation = 10.0
        assertFalse(DiplomacySystem.propose(s, m.id, Treaty.TRADE).success)
        assertFalse(DiplomacySystem.propose(s, m.id, Treaty.ALLIANCE).success)
        assertTrue(DiplomacySystem.propose(s, m.id, Treaty.PASSAGE).success)
        m.relation = 30.0
        val gold = s.store[GOLD]
        assertTrue(DiplomacySystem.propose(s, m.id, Treaty.TRADE).success)
        assertEquals(gold - 60, s.store[GOLD], 1e-6)
        assertEquals(Relation.PARTNER, m.status)
    }

    @Test
    fun giftsAndWantedAidImproveRelations() {
        val s = core().state
        val m = merchantNearby(s)
        val r0 = m.relation
        assertTrue(DiplomacySystem.giftGold(s, m.id).success)
        assertTrue(m.relation > r0)
        val r1 = m.relation
        assertTrue(DiplomacySystem.sendAid(s, m.id, FOOD).success) // yiyecek istedikleri bir kaynak
        assertTrue(m.relation > r1)
    }

    @Test
    fun allianceAngersTraditionalRival() {
        val s = core().state
        val m = merchantNearby(s)
        val rival = Civilization(99, "Şimşek Akıncıları", Culture.STORM_RIDERS, Cell(20, 20), Cell(20, 20), relation = 0.0, met = true)
        s.civs += rival
        m.relation = 70.0
        m.treaties += Treaty.TRADE
        assertTrue(DiplomacySystem.propose(s, m.id, Treaty.ALLIANCE).success)
        assertEquals(Relation.ALLY, m.status)
        assertEquals(-15.0, rival.relation, 1e-6)
    }

    @Test
    fun warCausesRaidsAndPeaceEndsIt() {
        val c = core(); val s = c.state
        val m = merchantNearby(s)
        DiplomacySystem.declareWar(s, m.id)
        assertNotNull(DiplomacySystem.peaceReason(s, m)) // hemen barış yok
        var raided = false
        var t = 0.0
        while (t < 900 && !raided) {
            c.step(1.0); t += 1.0
            val ev = s.pendingEvent
            if (ev != null) { if (ev.kind == "raid") raided = true; EventSystem.resolve(s, ev.options.lastIndex) }
        }
        assertTrue(raided, "savaşta yakındaki düşman baskın yapmalı")
        assertTrue(s.buildings.all { it.hp >= 20.0 })
        while (m.warSeconds < DiplomacySystem.PEACE_WAIT) { c.step(1.0); s.pendingEvent?.let { EventSystem.resolve(s, it.options.lastIndex) } }
        assertNull(DiplomacySystem.peaceReason(s, m))
        assertTrue(DiplomacySystem.makePeace(s, m.id).success)
        assertFalse(m.atWar)
        assertEquals(Relation.ENEMY == m.status, false)
    }

    @Test
    fun caravanReturnsWithGold() {
        val c = core(); val s = c.state
        val m = merchantNearby(s)
        s.stage = 2
        BuildingSystem.place(s, BuildingType.TRADE_MARKET, 11, 6, instant = true)
        assertEquals(1, TradeSystem.caravanSlots(s))
        val payout = TradeSystem.caravanPayout(s, m, FOOD)
        assertTrue(TradeSystem.sendCaravan(s, m.id, FOOD).success)
        assertFalse(TradeSystem.sendCaravan(s, m.id, FOOD).success) // yuva dolu
        val gold = s.store[GOLD]
        repeat(400) { c.step(1.0); s.pendingEvent?.let { EventSystem.resolve(s, it.options.lastIndex) } }
        assertTrue(s.caravans.isEmpty())
        assertTrue(s.store[GOLD] >= gold + payout * 0.6 - 5, "gold=${s.store[GOLD]} before=$gold payout=$payout")
    }

    @Test
    fun civsSurviveSaveRoundTrip() {
        val s = core().state
        val m = merchantNearby(s)
        m.treaties += Treaty.PASSAGE
        s.reputation = 33.0
        val st = MemoryStorage(); val mgr = SaveManager(st)
        mgr.save(s, 5L)
        val l = mgr.load()!!
        assertEquals(s.civs, l.civs)
        assertEquals(33.0, l.reputation, 1e-9)
        assertEquals(s.generatedRegions, l.generatedRegions)
    }

    @Test
    fun versionOneSaveMigratesAndLoads() {
        val s = GameCore.newGame(3).state
        val text = SaveCodec.encode(s)
        // v1 biçimini taklit et: yeni alanları çıkar, sürümü 1 yap, sağlamayı yeniden hesapla
        val root = SaveCodec.json.parseToJsonElement(text).jsonObjectOrThrow()
        val data = root["data"]!!.jsonObjectOrThrow().filterKeys { it !in setOf("civs", "generatedRegions", "nextCivId", "reputation", "caravans") }
        val dataText = kotlinx.serialization.json.JsonObject(data + ("version" to kotlinx.serialization.json.JsonPrimitive(1))).toString()
        val crc = java.util.zip.CRC32().apply { update(dataText.toByteArray(Charsets.UTF_8)) }.value
        val v1 = "{\"crc\":$crc,\"version\":1,\"data\":$dataText}"
        val loaded = SaveCodec.decode(v1)
        assertEquals(SaveCodec.CURRENT_VERSION, loaded.version)
        assertTrue(loaded.civs.isEmpty())
        val core = GameCore(loaded)
        core.step(1.0)
        assertTrue(loaded.civs.isNotEmpty(), "yüklemeden sonra medeniyetler üretilmeli")
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrThrow() = this as kotlinx.serialization.json.JsonObject
}
