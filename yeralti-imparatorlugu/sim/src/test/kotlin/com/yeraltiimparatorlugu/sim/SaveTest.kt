package com.yeraltiimparatorlugu.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaveTest {
    private val t0 = 1_800_000_000_000L

    @Test
    fun kaydetVeYukleDurumuKorur() {
        val sim = Simulation.newGame(77)
        sim.designateDig(-3, 9)
        sim.designateDig(-4, 9)
        sim.run(90f)
        sim.placeRoom(RoomType.MATERIAL_STORAGE, -8, 7)
        sim.placePheromone(PheromoneType.DIG, -5, 9)
        sim.priorities.set(TaskCategory.BUILD, 1)
        sim.setSpecialists(TaskCategory.GATHER, 2)
        val storage = MemoryStorage()
        val mgr = SaveManager(storage)
        mgr.save(sim, t0)

        val loaded = assertNotNull(SaveManager(storage).load(t0 + 5_000)).sim
        assertEquals(sim.seed, loaded.seed)
        assertEquals(sim.time, loaded.time, 0.001)
        assertEquals(sim.ants.size, loaded.ants.size)
        assertEquals(sim.rooms.map { it.type to it.state }, loaded.rooms.map { it.type to it.state })
        assertEquals(sim.resources.toMap(), loaded.resources.toMap())
        assertEquals(1, loaded.priorities[TaskCategory.BUILD])
        assertEquals(2, loaded.specialistCount(TaskCategory.GATHER))
        assertEquals(sim.digOrders.keys, loaded.digOrders.keys)
        assertEquals(sim.pheromones.markers.size, loaded.pheromones.markers.size)
        assertEquals(sim.goals.active.map { it.id }, loaded.goals.active.map { it.id })
        assertTrue(loaded.world.isWalkable(-3, 9))
        assertTrue(loaded.world.isRevealed(-3, 9))
        for (x in -20..20) for (y in 0..20) assertEquals(sim.world.get(x, y), loaded.world.get(x, y))
        assertNotNull(loaded.roomAt(-8, 7))
        // Yüklenen oyun çalışmaya devam eder.
        loaded.run(30f)
    }

    @Test
    fun bozukKayittaYedektenYuklenir() {
        val storage = MemoryStorage()
        val mgr = SaveManager(storage)
        val sim = Simulation.newGame(5)
        mgr.save(sim, t0)
        sim.run(10f)
        mgr.save(sim, t0 + 10_000)
        storage.files["koloni.json"] = storage.files["koloni.json"]!!.let { it.substring(0, it.length / 2) }
        val res = assertNotNull(SaveManager(storage).load(t0 + 20_000))
        assertTrue(res.usedBackup)
        assertEquals(0.0, res.sim.time, 0.01)
    }

    @Test
    fun ikiKopyaDaBozuksaNullDoner() {
        val storage = MemoryStorage()
        storage.files["koloni.json"] = "çöp"
        storage.files["koloni.json.bak"] = "{}"
        assertNull(SaveManager(storage).load(t0))
        assertFalse(SaveManager(MemoryStorage()).hasSave())
    }

    @Test
    fun cevrimdisiIlerlemeSinirli() {
        val storage = MemoryStorage()
        val sim = Simulation.newGame(8)
        SaveManager(storage).save(sim, t0)
        val res = SaveManager(storage).load(t0 + 48L * 3600_000L)!!
        assertTrue(res.offline.capped)
        assertEquals(OfflineProgress.MAX_SECONDS, res.offline.appliedSeconds)
        assertTrue(res.sim.workerCount >= 6, "Oyuncu yokken koloni yok olmamalı")
        assertTrue(res.sim.ants.none { it.hp <= 0f })
    }

    @Test
    fun saatGeriAlinirsaIlerlemeVerilmez() {
        val storage = MemoryStorage()
        val sim = Simulation.newGame(8)
        val mgr = SaveManager(storage)
        // Oyuncu saati ileri alıp kaydetti, sonra saati geri aldı.
        mgr.save(sim, t0 + 10L * 3600_000L)
        val res = SaveManager(storage).load(t0)!!
        assertTrue(res.offline.clockRollback)
        assertEquals(0, res.offline.appliedSeconds)
    }

    @Test
    fun cevrimdisiCiftlikUretir() {
        val sim = Simulation.newGame(8)
        Room(sim.newId(), RoomType.FUNGUS_FARM, 7, 7).also { it.state = RoomState.ACTIVE; it.leafBuffer = 10; sim.rooms.add(it); sim.registerRoomCells(it) }
        val before = sim.resources[ResourceType.MUSHROOM]
        val lines = OfflineProgress.advance(sim, 3600f)
        assertTrue(sim.resources[ResourceType.MUSHROOM] > before, lines.joinToString())
    }
}
