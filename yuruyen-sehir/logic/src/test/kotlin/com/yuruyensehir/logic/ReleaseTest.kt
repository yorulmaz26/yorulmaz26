package com.yuruyensehir.logic

import com.yuruyensehir.logic.Resource.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseTest {

    private fun state(): GameState {
        val s = GameCore.newGame(41L).state
        s.store.amounts[GOLD] = 1000.0
        return s
    }

    @Test
    fun buyingCosmeticSpendsGoldAndEquips() {
        val s = state()
        assertTrue(ShopSystem.buy(s, Cosmetic.SKIN_OCEAN).success)
        assertEquals(850.0, s.store[GOLD], 1e-9)
        assertEquals(Cosmetic.SKIN_OCEAN, ShopSystem.equipped(s, CosmeticSlot.CARRIER_SKIN))
        assertFalse(ShopSystem.buy(s, Cosmetic.SKIN_OCEAN).success)
        assertTrue(ShopSystem.unequip(s, CosmeticSlot.CARRIER_SKIN).success)
        assertNull(ShopSystem.equipped(s, CosmeticSlot.CARRIER_SKIN))
        assertTrue(ShopSystem.equip(s, Cosmetic.SKIN_OCEAN).success)
    }

    @Test
    fun cannotBuyWithoutResourcesOrEquipUnowned() {
        val s = state()
        s.store.amounts[GOLD] = 10.0
        assertFalse(ShopSystem.buy(s, Cosmetic.SKIN_GOLDEN).success)
        assertFalse(ShopSystem.equip(s, Cosmetic.SKIN_GOLDEN).success)
        assertEquals(10.0, s.store[GOLD], 1e-9)
    }

    @Test
    fun packIsCheaperThanItems() {
        for (p in CosmeticPack.entries) {
            val sum = p.items.sumOf { it.price[GOLD] ?: 0.0 }
            assertTrue((p.price[GOLD] ?: 0.0) < sum, p.title)
        }
        val s = state()
        assertTrue(ShopSystem.buyPack(s, CosmeticPack.SEA_PACK).success)
        assertTrue(CosmeticPack.SEA_PACK.items.all { ShopSystem.owned(s, it) })
    }

    @Test
    fun cosmeticsDoNotChangeGameplay() {
        val s = state()
        val w = WeightBalanceSystem.compute(s).totalWeight
        val cap = WeightBalanceSystem.capacity(s)
        Cosmetic.entries.forEach { s.cosmetics.owned += it; if (it.slot != CosmeticSlot.COLLECTIBLE) s.cosmetics.equipped[it.slot] = it }
        assertEquals(cap, WeightBalanceSystem.capacity(s), 1e-9)
        assertEquals(w, WeightBalanceSystem.compute(s).totalWeight, 1e-9)
    }

    @Test
    fun customProviderCanReplaceInGameCurrency() {
        val s = state()
        val fake = object : PurchaseProvider {
            override val id = "test"
            var paid = 0
            override fun canPay(state: GameState, price: Amounts) = true
            override fun pay(state: GameState, price: Amounts): Boolean { paid++; return true }
        }
        ShopSystem.provider = fake
        try {
            assertTrue(ShopSystem.buy(s, Cosmetic.FLAG_SUN).success)
            assertEquals(1, fake.paid)
            assertEquals(1000.0, s.store[GOLD], 1e-9)
        } finally { ShopSystem.provider = InGameCurrencyProvider }
    }

    @Test
    fun renameSanitizesInput() {
        val s = state()
        assertTrue(ShopSystem.rename(s, "  Mavi [Kervan]  ", "Pamuk").success)
        assertEquals("Mavi Kervan", s.cityName)
        assertEquals("Pamuk", s.carrier.name)
        assertFalse(ShopSystem.rename(s, "   ", null).success)
        assertTrue(ShopSystem.rename(s, "x".repeat(80), null).success)
        assertEquals(24, s.cityName.length)
    }

    @Test
    fun cosmeticsAndNameSurviveSave() {
        val s = state()
        ShopSystem.buy(s, Cosmetic.TRAIL_FLOWERS)
        ShopSystem.rename(s, "Yeşil Yol", null)
        val st = MemoryStorage(); SaveManager(st).save(s, 1L)
        val l = SaveManager(st).load()!!
        assertEquals(s.cosmetics, l.cosmetics)
        assertEquals("Yeşil Yol", l.cityName)
    }

    @Test
    fun synthesizedSoundsAreValidWav() {
        for (id in SoundId.entries) {
            val w = AudioSynth.wav(AudioSynth.render(id))
            assertEquals("RIFF", String(w, 0, 4))
            assertEquals("WAVE", String(w, 8, 4))
            assertTrue(w.size > 44 + 1000, "$id çok kısa")
        }
        val m = AudioSynth.music()
        assertTrue(m.size > AudioSynth.RATE * 10)
        assertTrue(m.any { kotlin.math.abs(it) > 0.01f })
        assertTrue(m.all { !it.isNaN() })
    }

    @Test
    fun simulationIsFastEnoughForLowEndDevices() {
        val core = GameCore.newGame(5)
        val s = core.state
        s.stage = 4
        s.buildings += PlacedBuilding(s.nextBuildingId++, BuildingType.MATERIAL_STORAGE, 10, 3)
        ResourceSystem.recalcCapacity(s)
        Resource.entries.forEach { s.store.amounts[it] = 400.0 }
        // Büyük bir şehir: bütün uygun hücreleri 1x1 binalarla doldur
        val types = listOf(BuildingType.SMALL_HOUSE, BuildingType.WATER_COLLECTOR, BuildingType.GATHERER, BuildingType.DEFENSE_TOWER, BuildingType.REST_AREA)
        var k = 0
        for (x in 0 until CityGrid.GRID_W) for (y in 0 until CityGrid.GRID_H) {
            if (CityGrid.usable(s, x, y) && s.buildingAt(x, y) == null) s.buildings += PlacedBuilding(s.nextBuildingId++, types[k++ % types.size], x, y)
        }
        s.population.count = 80
        PopulationSystem.autoAssign(s)
        for (i in 0 until 30) { s.travel.position = Cell(i * 12, -i * 9); CivSystem.ensureNearby(s) }
        s.travel.position = Cell(0, 0)
        assertTrue(s.buildings.size > 50)
        val t0 = System.nanoTime()
        repeat(4 * 60 * 5) { core.step(GameCore.STEP); s.pendingEvent?.let { EventSystem.resolve(s, it.options.lastIndex) } } // 5 oyun dakikası
        val ms = (System.nanoTime() - t0) / 1e6
        // 5 dakika = 1200 adım. Cihazda adım başına bütçe 60 FPS'de 16 ms'nin küçük bir kısmı olmalı.
        val perStep = ms / 1200
        println("Adım başına ${"%.3f".format(perStep)} ms (${s.buildings.size} bina, ${s.civs.size} medeniyet)")
        assertTrue(perStep < 2.0, "adım başına $perStep ms")
    }
}

class PremiumTest {
    private fun state() = GameCore.newGame(51L).state.also { it.store.amounts[Resource.GOLD] = 5000.0 }

    @Test
    fun premiumOnlyItemsCannotBeBoughtWithGold() {
        val s = state()
        assertFalse(ShopSystem.buy(s, Cosmetic.SKIN_AURORA).success)
        assertFalse(ShopSystem.owned(s, Cosmetic.SKIN_AURORA))
        assertEquals(5000.0, s.store[Resource.GOLD], 1e-9)
    }

    @Test
    fun grantIsIdempotentAndCosmeticOnly() {
        val s = state()
        val cap = WeightBalanceSystem.capacity(s); val gold = s.store[Resource.GOLD]
        PremiumSystem.grant(s, PremiumProduct.SUPPORTER_PACK)
        PremiumSystem.grant(s, PremiumProduct.SUPPORTER_PACK)
        assertTrue(ShopSystem.owned(s, Cosmetic.SKIN_AURORA))
        assertEquals(Cosmetic.FLAG_STAR, ShopSystem.equipped(s, CosmeticSlot.FLAG))
        assertEquals(1, s.log.count { it.text.contains("Destekçi Paketi etkinleştirildi") })
        assertEquals(cap, WeightBalanceSystem.capacity(s), 1e-9)
        assertEquals(gold, s.store[Resource.GOLD], 1e-9)
    }

    @Test
    fun syncRestoresAndRevokesRefundedWithoutTouchingGoldPurchases() {
        val s = state()
        ShopSystem.buy(s, Cosmetic.SKIN_OCEAN) // altınla
        PremiumSystem.sync(s, listOf("tum_kozmetikler", "destekci_paketi"))
        assertTrue(Cosmetic.entries.all { ShopSystem.owned(s, it) })
        PremiumSystem.sync(s, emptyList()) // iki ürün de iade edildi
        assertFalse(ShopSystem.owned(s, Cosmetic.SKIN_AURORA))
        assertFalse(ShopSystem.owned(s, Cosmetic.ROOF_LAVENDER))
        assertTrue(ShopSystem.owned(s, Cosmetic.SKIN_OCEAN), "altınla alınan korunmalı")
        assertTrue(s.premium.isEmpty())
    }

    @Test
    fun unknownProductIdsAreIgnoredAndStateSaves() {
        val s = state()
        PremiumSystem.sync(s, listOf("bilinmeyen_urun", "destekci_paketi"))
        assertEquals(setOf(PremiumProduct.SUPPORTER_PACK), s.premium)
        val st = MemoryStorage(); SaveManager(st).save(s, 1L)
        assertEquals(s.premium, SaveManager(st).load()!!.premium)
    }
}
