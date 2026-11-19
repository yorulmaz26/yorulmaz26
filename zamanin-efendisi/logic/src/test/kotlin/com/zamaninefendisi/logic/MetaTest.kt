package com.zamaninefendisi.logic

import com.zamaninefendisi.logic.levels.LevelCatalog
import com.zamaninefendisi.logic.meta.*
import com.zamaninefendisi.logic.save.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaTest {
    private val hour = 3_600_000L

    private fun progression(c: FakeClock = FakeClock()) = Progression(SaveSystem(InMemoryStorage()), c) to c

    @Test
    fun productionAccumulatesUsingMonotonicClockAndRespectsStorage() {
        val (p, c) = progression()
        c.pass(2 * hour)
        val got = p.collectProduction()
        assertEquals(24, got[Resource.GOLD]) // Ticaret Merkezi sv1: 12/saat
        assertEquals(20, got[Resource.WOOD]) // Atölye sv1: 10/saat
        // Saat ileri alınsa da aynı açılışta üretim artmaz.
        c.wall += 100 * hour
        assertTrue(p.collectProduction().isEmpty())
        // Depo sınırı
        c.pass(1000 * hour)
        val capped = p.collectProduction()
        assertEquals(Kingdom.storageCap(0, emptySet()), capped[Resource.GOLD])
    }

    @Test
    fun buildingsRequireCoreLevelAndResources() {
        val (p, _) = progression()
        assertFalse(p.upgradeBuilding(BuildingType.LAB)) // kaynak yok
        p.update { it.copy(wallet = mapOf(Resource.GOLD to 5000, Resource.WOOD to 500, Resource.STONE to 500, Resource.IRON to 500, Resource.CRYSTAL to 20, Resource.ENERGY to 500)) }
        assertTrue(p.upgradeBuilding(BuildingType.LAB))
        assertEquals(1, p.data.buildings[BuildingType.LAB])
        assertFalse("Çekirdek sv1 iken laboratuvar sv2 olamaz", p.upgradeBuilding(BuildingType.LAB))
        assertTrue(p.upgradeBuilding(BuildingType.CORE))
        assertTrue(p.upgradeBuilding(BuildingType.LAB))
        assertEquals(2, p.data.buildings[BuildingType.LAB])
        assertTrue(p.kingdomPower() > Kingdom.power(Kingdom.starting))
    }

    @Test
    fun researchNeedsPrerequisitesAndLab() {
        val (p, _) = progression()
        p.update { it.copy(wallet = mapOf(Resource.GOLD to 9999, Resource.SCIENCE to 9999, Resource.ENERGY to 999, Resource.CRYSTAL to 99), buildings = it.buildings + (BuildingType.LAB to 1)) }
        assertFalse(p.research(ResearchTree.GUIDE))
        assertTrue(p.research(ResearchTree.FORESIGHT_1))
        assertFalse("Laboratuvar sv2 gerekli", p.research(ResearchTree.GUIDE))
        p.update { it.copy(buildings = it.buildings + (BuildingType.LAB to 2)) }
        assertTrue(p.research(ResearchTree.GUIDE))
        assertEquals(4, p.foresightCharges())
        assertTrue(p.hasGuide())
    }

    @Test
    fun researchBonusesApplyToRewardsAndCombat() {
        val (p, c) = progression()
        p.update { it.copy(research = setOf(ResearchTree.GOLD_10, ResearchTree.HP_1)) }
        val l1 = LevelCatalog.level(1)!!
        val r = p.recordWin(LevelSession.replay(l1, l1.solution), c.wallMs())
        assertEquals(66, r.gold)
        assertEquals(1, p.heroHpBonus())
        val combat = LevelCatalog.level(5)!!
        assertEquals(combat.heroHp + 1, LevelSession(combat, p.heroHpBonus()).heroHp)
        assertEquals(l1.heroHp, LevelSession(l1, p.heroHpBonus()).heroHp)
    }

    @Test
    fun shopBuyEquipAndPacks() {
        val (p, _) = progression()
        assertNotNull(p.buy("castle_sun"))
        p.update { it.copy(wallet = mapOf(Resource.GOLD to 1000)) }
        assertNull(p.buy("castle_sun"))
        assertEquals("castle_sun", p.equipped(ShopCategory.CASTLE_SKIN))
        assertNotNull("İkinci kez alınamaz", p.buy("castle_sun"))
        assertTrue(p.equip("castle_classic"))
        val before = p.amount(Resource.WOOD)
        assertNull(p.buy("pack_builder"))
        assertEquals(before + 60, p.amount(Resource.WOOD))
        assertNotNull("Araştırma gerekli", p.buy("castle_paradox"))
        assertEquals(ShopCategory.entries.size - 1, Shop.defaults().size)
    }

    @Test
    fun migratesVersion1Wallet() {
        val v1 = """{"version":1,"gold":250,"crystals":7,"xp":90,"levels":{"1":{"stars":3,"completions":1}}}"""
        val d = SaveSystem(InMemoryStorage()).decode(v1)!!
        assertEquals(SAVE_VERSION, d.version)
        assertEquals(250, d.wallet[Resource.GOLD])
        assertEquals(7, d.wallet[Resource.CRYSTAL])
        assertEquals(90, d.xp)
        assertEquals(Kingdom.starting, d.buildings)
    }
}
