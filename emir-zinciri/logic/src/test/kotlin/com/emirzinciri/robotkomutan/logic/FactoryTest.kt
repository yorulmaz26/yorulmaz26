package com.emirzinciri.robotkomutan.logic

import com.emirzinciri.robotkomutan.logic.factory.BuildingType
import com.emirzinciri.robotkomutan.logic.factory.Factory
import com.emirzinciri.robotkomutan.logic.factory.FactoryResult
import com.emirzinciri.robotkomutan.logic.factory.FactoryState
import com.emirzinciri.robotkomutan.logic.factory.Research
import com.emirzinciri.robotkomutan.logic.factory.Resource
import com.emirzinciri.robotkomutan.logic.level.LevelDef
import com.emirzinciri.robotkomutan.logic.level.RobotDef
import com.emirzinciri.robotkomutan.logic.progress.MemorySaveStorage
import com.emirzinciri.robotkomutan.logic.progress.Progression
import com.emirzinciri.robotkomutan.logic.progress.SaveData
import com.emirzinciri.robotkomutan.logic.progress.SaveManager
import com.emirzinciri.robotkomutan.logic.progress.Wallet
import com.emirzinciri.robotkomutan.logic.sim.Outcome
import com.emirzinciri.robotkomutan.logic.sim.RunStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FactoryTest {
    private val hour = 3_600_000L

    private fun factory(wallet: Wallet = Wallet(), clock: FakeClock = FakeClock()) =
        Factory(FactoryState(), wallet, clock).also { it.update() } to clock

    @Test fun startingFactoryProducesEnergyAndMetal() {
        val (f, c) = factory()
        c.advance(hour)
        val r = f.update()
        // Enerji santrali 30/sa, metal işleme 8 enerji harcayıp 12 metal üretir.
        assertEquals(12, f.wallet.metal)
        assertEquals(22, f.wallet.energy)
        assertEquals(60, r.minutes)
    }

    @Test fun clockForwardWithinSameBootGivesNothing() {
        val (f, c) = factory()
        c.wall += 100 * hour
        f.update()
        assertEquals(0, f.wallet.metal)
    }

    @Test fun offlineProductionIsCappedByHoursAndStorage() {
        val (f, c) = factory()
        c.advance(1000 * hour)
        val r = f.update()
        assertTrue(r.capped)
        assertEquals(8 * 12L, f.wallet.metal) // 8 saat sınırı
        assertTrue(f.wallet.energy <= f.capacity(Resource.ENERGY)!!)
    }

    @Test fun metalWorksStallsWithoutEnergy() {
        val (f, c) = factory()
        f.state.buildings[BuildingType.ENERGY_PLANT] = 0
        c.advance(hour)
        f.update()
        assertEquals(0, f.wallet.metal)
        assertEquals(0, f.wallet.energy)
    }

    @Test fun upgradeChargesCostAndRespectsRules() {
        val (f, _) = factory(Wallet(gold = 10_000, metal = 10_000, chips = 100))
        assertIs<FactoryResult.Locked>(f.upgrade(BuildingType.CHIP_LAB)) // Komuta Merkezi 2 gerekli
        val cost = f.upgradeCost(BuildingType.COMMAND_CENTER)!!
        assertEquals(FactoryResult.Ok, f.upgrade(BuildingType.COMMAND_CENTER))
        assertEquals(10_000 - cost.getValue(Resource.GOLD), f.wallet.gold)
        assertEquals(FactoryResult.Ok, f.upgrade(BuildingType.CHIP_LAB))
        assertIs<FactoryResult.Locked>(f.upgrade(BuildingType.ROBOT_PRODUCTION)) // ileriki aşama
        // Bina seviyesi Komuta Merkezi + 2'yi geçemez
        repeat(3) { f.upgrade(BuildingType.ENERGY_PLANT) }
        assertEquals(4, f.level(BuildingType.ENERGY_PLANT))
        assertIs<FactoryResult.Locked>(f.upgrade(BuildingType.ENERGY_PLANT))
    }

    @Test fun notEnoughResources() {
        val (f, _) = factory(Wallet(gold = 1))
        val r = f.upgrade(BuildingType.ENERGY_PLANT)
        assertIs<FactoryResult.Missing>(r)
        assertEquals(1, f.wallet.gold)
    }

    @Test fun chipLabConsumesMetal() {
        val (f, c) = factory(Wallet(metal = 100))
        f.state.buildings[BuildingType.CHIP_LAB] = 1
        c.advance(2 * hour)
        f.update()
        assertEquals(2, f.wallet.chips)
        assertEquals(100 + 24 - 10, f.wallet.metal)
    }

    @Test fun researchChainAndEffects() {
        val save = SaveData()
        val p = Progression(save, FakeClock())
        val f = p.factory
        save.wallet.tech = 1000; save.wallet.chips = 100
        assertIs<FactoryResult.Locked>(f.research(Research.CMD_1)) // Araştırma Merkezi yok
        f.state.buildings[BuildingType.RESEARCH_CENTER] = 3
        assertIs<FactoryResult.Locked>(f.research(Research.CMD_2)) // önceki kademe şart
        assertEquals(FactoryResult.Ok, f.research(Research.CMD_1))
        assertIs<FactoryResult.Locked>(f.research(Research.MIL_1))
        // %10 altın bonusu bölüm ödülüne uygulanır
        val def = LevelDef(id = 1, area = 1, name = "x", map = listOf(".E"), robots = listOf(RobotDef("A", 0, 0)), commands = emptyList(), maxCommands = 3, par = 1)
        val res = p.onWin(def, Outcome.Win(RunStats(1, 0, 0, 1)), 3)
        val base = Progression.firstClearReward(def).gold + 15
        assertEquals(base * 110 / 100, res.reward.gold)
        // Depo araştırması kapasiteyi artırır
        val before = f.capacity(Resource.METAL)!!
        f.research(Research.ENG_1)
        assertEquals(before * 125 / 100, f.capacity(Resource.METAL))
    }

    @Test fun tradeConvertsMetal() {
        val (f, _) = factory(Wallet(metal = 120))
        assertIs<FactoryResult.Locked>(f.trade(Resource.METAL))
        f.state.buildings[BuildingType.TRADE_CENTER] = 1
        assertEquals(FactoryResult.Ok, f.trade(Resource.METAL, 2))
        assertEquals(20, f.wallet.metal)
        assertEquals(40, f.wallet.gold)
        assertIs<FactoryResult.Missing>(f.trade(Resource.METAL))
    }

    @Test fun factorySurvivesSaveRoundTrip() {
        val save = SaveData()
        save.factory.buildings[BuildingType.STORAGE] = 3
        save.factory.research += Research.NRG_1
        val m = SaveManager(MemorySaveStorage())
        m.save(save)
        val back = m.load().data
        assertEquals(3, back.factory.buildings[BuildingType.STORAGE])
        assertTrue(Research.NRG_1 in back.factory.research)
    }
}
