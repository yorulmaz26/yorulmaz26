package com.emirzinciri.robotkomutan.logic

import com.emirzinciri.robotkomutan.logic.editor.Brush
import com.emirzinciri.robotkomutan.logic.editor.EditorModel
import com.emirzinciri.robotkomutan.logic.factory.BuildingType
import com.emirzinciri.robotkomutan.logic.level.LevelData
import com.emirzinciri.robotkomutan.logic.meta.Collection
import com.emirzinciri.robotkomutan.logic.meta.Route
import com.emirzinciri.robotkomutan.logic.progress.MemorySaveStorage
import com.emirzinciri.robotkomutan.logic.progress.Progression
import com.emirzinciri.robotkomutan.logic.progress.SaveData
import com.emirzinciri.robotkomutan.logic.progress.SaveManager
import com.emirzinciri.robotkomutan.logic.sim.Outcome
import com.emirzinciri.robotkomutan.logic.sim.Simulation
import com.emirzinciri.robotkomutan.logic.sim.Solver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LateSystemsTest {
    @Test fun editorBuildsPlayableSolvableLevel() {
        val m = EditorModel(5, 3)
        assertEquals("En az bir robot yerleştir.", m.validate())
        m.paint(0, 1, Brush.ROBOT)
        m.paint(4, 1, Brush.EXIT)
        m.paint(2, 1, Brush.WALL)
        assertNull(m.validate())
        val def = m.toLevel(30_000)
        val sol = assertNotNull(Solver(LevelData(def)).solve(12))
        assertIs<Outcome.Win>(Simulation.create(LevelData(def), sol.programs.ifEmpty { mapOf("A" to sol.cards) }).getOrThrow().runToEnd())
        // Robot yönü dokunarak döner; editöre geri yükleme kaybetmez
        m.paint(0, 1, Brush.ROBOT)
        val back = EditorModel.from(m.toLevel(1))
        assertEquals(m.robots, back.robots)
        assertEquals(m.grid.map { String(it) }, back.grid.map { String(it) })
    }

    @Test fun expeditionsUseTrustedTimeAndGiveDeterministicRewards() {
        val save = SaveData()
        val clock = FakeClock()
        val p = Progression(save, clock)
        val ex = p.expeditions
        assertTrue(ex.canStart(Route.NEAR)!!.contains("Keşif"))
        save.factory.buildings[BuildingType.EXPLORATION] = 1
        save.wallet.energy = 100
        assertTrue(ex.start(Route.NEAR))
        assertEquals(80, save.wallet.energy)
        assertFalse(ex.start(Route.NEAR)) // tek yuva
        val e = ex.active.single()
        clock.wall += 10 * 3_600_000L // saat ileri: aynı açılışta işe yaramaz
        ex.update()
        assertFalse(ex.isDone(e))
        clock.mono += 31 * 60_000L
        ex.update()
        assertTrue(ex.isDone(e))
        val gold = save.wallet.gold
        val r = assertNotNull(ex.claim(e))
        assertEquals(gold + r.gold + (if (r.duplicate) 25L * (r.item?.rarity ?: 0) else 0), save.wallet.gold)
        assertNull(ex.claim(e))
        assertEquals(100, Collection.items.size)
    }

    @Test fun museumProducesGoldFromCollection() {
        val save = SaveData()
        val clock = FakeClock()
        val p = Progression(save, clock)
        p.factory.update()
        save.factory.buildings[BuildingType.MUSEUM] = 2
        save.meta.collection += (0 until 10)
        clock.advance(3_600_000L)
        p.factory.update()
        assertEquals(10, save.wallet.gold) // 0,5 × 2 × 10 / saat
    }

    @Test fun newFieldsSurviveSave() {
        val s = SaveData()
        s.meta.collection += 5; s.meta.hardCleared += 7; s.meta.prestige = 1
        s.meta.customLevels += EditorModel(5, 5).apply { paint(0, 0, Brush.ROBOT); paint(4, 4, Brush.EXIT) }.toLevel(30_001)
        val m = SaveManager(MemorySaveStorage()); m.save(s)
        val b = m.load().data
        assertEquals(s.meta.collection, b.meta.collection); assertEquals(1, b.meta.customLevels.size); assertEquals(1, b.meta.prestige)
    }
}
