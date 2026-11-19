package com.yeraltiimparatorlugu.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimulationTest {
    private fun newSim(seed: Long = 1234) = Simulation.newGame(seed)

    @Test
    fun baslangicKolonisi() {
        val sim = newSim()
        assertEquals(1, sim.ants.count { it.type == AntType.QUEEN })
        assertEquals(6, sim.workerCount)
        assertTrue(sim.rooms.any { it.type == RoomType.QUEEN_CHAMBER && it.functional })
        assertTrue(sim.rooms.any { it.type == RoomType.FOOD_STORAGE && it.functional })
        assertTrue(sim.world.isWalkable(0, 0))
        assertEquals(3, sim.goals.active.size)
    }

    @Test
    fun kaziGercektenYapilirVeToprakDepoyaTasinir() {
        val sim = newSim()
        sim.clearSources()
        val soilBefore = sim.resources[ResourceType.SOIL]
        // Kraliçe odasının solundan yatay tünel.
        val cells = (-3 downTo -10).map { it to 9 }
        cells.forEach { (x, y) -> assertEquals(DigDesignation.OK, sim.designateDig(x, y)) }
        sim.run(240f)
        cells.forEach { (x, y) -> assertTrue(sim.world.isWalkable(x, y), "($x,$y) kazılmalı") }
        assertTrue(sim.digOrders.isEmpty())
        assertTrue(sim.stats.cellsDug >= cells.size)
        assertTrue(sim.resources[ResourceType.SOIL] + sim.stats.soilDumped > soilBefore, "Kazı malzemesi depoya ulaşmalı")
    }

    @Test
    fun erisilemeyenKaziBeklerSonraYapilir() {
        val sim = newSim()
        sim.clearSources()
        // Uzaktaki tek hücre: komşusu açık değil.
        sim.designateDig(-12, 9)
        sim.run(20f)
        assertFalse(sim.world.isWalkable(-12, 9))
        for (x in -3 downTo -11) sim.designateDig(x, 9)
        sim.run(300f)
        assertTrue(sim.world.isWalkable(-12, 9))
    }

    @Test
    fun kazilamazHucrelerReddedilir() {
        val sim = newSim()
        assertEquals(DigDesignation.SURFACE, sim.designateDig(5, -1))
        assertEquals(DigDesignation.NOT_DIGGABLE, sim.designateDig(0, 0))
        val rock = (-200..200).flatMap { x -> (12..70).map { y -> x to y } }.first { (x, y) -> sim.world.get(x, y) == Terrain.ROCK }
        sim.world.reveal(rock.first, rock.second, 0)
        assertEquals(DigDesignation.NEEDS_TECH, sim.designateDig(rock.first, rock.second))
    }

    @Test
    fun toplayicilarYuzeydenYiyecekGetirir() {
        val sim = newSim()
        sim.clearSources()
        val src = sim.addSource(8, SourceKind.DEAD_INSECT, 10)
        val before = sim.resources[ResourceType.FOOD]
        sim.run(120f)
        assertTrue(src.amount < 10, "Kaynak azalmalı")
        assertTrue(sim.stats.foodGathered > 0)
        assertTrue(sim.resources[ResourceType.FOOD] + sim.stats.foodEaten > before)
    }

    @Test
    fun uzakKaynakYalnizcaFeromonlaToplanir() {
        val sim = newSim()
        sim.clearSources()
        val far = sim.addSource(40, SourceKind.SEED_PILE, 10)
        sim.run(60f)
        assertEquals(10, far.amount)
        sim.placePheromone(PheromoneType.FOOD, 40, -1)
        sim.run(120f)
        assertTrue(far.amount < 10)
    }

    @Test
    fun feromonZamanlaSoner() {
        val sim = newSim()
        val m = sim.placePheromone(PheromoneType.RALLY, 0, 8)
        sim.run(60f)
        assertTrue(m.strength in 0.1f..0.9f)
        sim.run(PheromoneType.RALLY.duration)
        assertTrue(sim.pheromones.markers.isEmpty())
    }

    @Test
    fun odaInsasiKaziVeInsaAsamalarindanGecer() {
        val sim = newSim()
        sim.clearSources()
        sim.resources.add(ResourceType.SOIL, 30)
        val cap = sim.capacityOf(StorageKind.MATERIAL)
        val p = sim.checkRoomPlacement(RoomType.MATERIAL_STORAGE, -6, 7)
        assertTrue(p.ok, p.reason)
        val room = assertNotNull(sim.placeRoom(RoomType.MATERIAL_STORAGE, -6, 7))
        assertEquals(RoomState.EXCAVATING, room.state)
        assertTrue(sim.digOrders.values.any { it.roomId == room.id })
        sim.run(400f)
        assertEquals(RoomState.ACTIVE, room.state)
        assertEquals(cap + 80, sim.capacityOf(StorageKind.MATERIAL))
    }

    @Test
    fun odaYerlesimKurallari() {
        val sim = newSim()
        assertFalse(sim.checkRoomPlacement(RoomType.FOOD_STORAGE, -2, 7).ok, "çakışma")
        assertFalse(sim.checkRoomPlacement(RoomType.FOOD_STORAGE, 30, 30).ok, "bağlantısız")
        assertFalse(sim.checkRoomPlacement(RoomType.FOOD_STORAGE, 3, 0).ok, "yüzeye çok yakın")
        assertFalse(sim.checkRoomPlacement(RoomType.QUEEN_CHAMBER, -8, 7).ok, "inşa edilemez")
        sim.resources.set(ResourceType.SOIL, 0)
        assertEquals("Yetersiz kaynak", sim.checkRoomPlacement(RoomType.FOOD_STORAGE, -6, 7).reason)
    }

    @Test
    fun odaIptaliTamIadeEder() {
        val sim = newSim()
        val soil = sim.resources[ResourceType.SOIL]
        val room = sim.placeRoom(RoomType.MATERIAL_STORAGE, -6, 7)!!
        assertEquals(soil - 8, sim.resources[ResourceType.SOIL])
        assertTrue(sim.removeRoom(room))
        assertEquals(soil, sim.resources[ResourceType.SOIL])
        assertTrue(sim.digOrders.isEmpty())
        assertNull(sim.roomAt(-6, 7))
    }

    @Test
    fun komsulukBonusuVerimiArtirir() {
        val sim = newSim()
        val farm = Room(sim.newId(), RoomType.FUNGUS_FARM, 7, 7).also { it.state = RoomState.ACTIVE; sim.rooms.add(it) }
        // Besin deposu x=3..6, çiftlik x=7.. → komşu.
        assertTrue(sim.roomEfficiency(farm) > 1.15f)
        val far = Room(sim.newId(), RoomType.FUNGUS_FARM, 30, 7).also { it.state = RoomState.ACTIVE; sim.rooms.add(it) }
        assertEquals(1f, sim.roomEfficiency(far))
    }

    @Test
    fun mantarCiftligiYapragiMantaraCevirir() {
        val sim = newSim()
        sim.clearSources()
        sim.resources.add(ResourceType.SOIL, 40); sim.resources.add(ResourceType.LEAF, 20); sim.resources.add(ResourceType.WOOD, 10)
        val farm = sim.placeRoom(RoomType.FUNGUS_FARM, 7, 7)
        assertNotNull(farm, sim.checkRoomPlacement(RoomType.FUNGUS_FARM, 7, 7).reason)
        sim.run(700f)
        assertEquals(RoomState.ACTIVE, farm.state)
        assertTrue(sim.resources[ResourceType.MUSHROOM] + sim.piles.values.filter { it.type == ResourceType.MUSHROOM }.sumOf { it.amount } > 0 ||
            sim.stats.foodEaten > 0 && sim.resources[ResourceType.LEAF] < 20 - 10, "Mantar üretilmeli")
    }

    @Test
    fun kralicYumurtlarVeYeniIsciDoğar() {
        val sim = newSim()
        sim.resources.add(ResourceType.FOOD, 40)
        sim.run(420f)
        assertTrue(sim.stats.eggsLaid >= 1)
        assertTrue(sim.stats.antsBorn >= 1, "Yumurta → larva → pupa → işçi")
        assertTrue(sim.workerCount > 6)
    }

    @Test
    fun barinakDoluykenYumurtlamaz() {
        val sim = newSim()
        repeat(2) { sim.ants.add(Ant(sim.newId(), AntType.WORKER, 0.5f, 9.5f)) }
        assertEquals(8, sim.workerCount)
        assertNotNull(sim.layBlocker())
        sim.run(120f)
        assertEquals(0, sim.stats.eggsLaid)
    }

    @Test
    fun oncelikKapaliysaIsYapilmaz() {
        val sim = newSim()
        sim.clearSources()
        sim.addSource(6, SourceKind.DEAD_INSECT, 10)
        sim.priorities.set(TaskCategory.GATHER, 0)
        sim.designateDig(-3, 9)
        sim.run(60f)
        assertEquals(0, sim.stats.foodGathered)
        assertTrue(sim.world.isWalkable(-3, 9))
    }

    @Test
    fun uzmanlarKendiIslerineOncelikVerir() {
        val sim = newSim()
        sim.setSpecialists(TaskCategory.DIG, 2)
        assertEquals(2, sim.specialistCount(TaskCategory.DIG))
        sim.setSpecialists(TaskCategory.DIG, 1)
        assertEquals(1, sim.specialistCount(TaskCategory.DIG))
        val spec = sim.workers.first { it.assignment == TaskCategory.DIG }
        sim.priorities.set(TaskCategory.DIG, 5)
        sim.designateDig(-3, 9)
        val job = sim.chooseJob(spec)
        assertTrue(job is DigJob)
    }

    @Test
    fun genisKumTavanCokmeRiskiTasir() {
        val sim = newSim()
        // Kum tavanlı geniş boşluk oluştur.
        val y = 40
        for (x in 100..110) { sim.world.set(x, y - 1, Terrain.SAND); sim.world.set(x, y, Terrain.TUNNEL) }
        assertEquals(0f, sim.collapseRisk(105, y), "Tek sıra tünel güvenli")
        for (x in 100..110) sim.world.set(x, y + 1, Terrain.TUNNEL)
        assertTrue(sim.collapseRisk(105, y) > 0f, "İki sıra yüksek geniş boşluk riskli")
        sim.world.set(104, y, Terrain.HARD_SOIL); sim.world.set(106, y, Terrain.HARD_SOIL)
        assertEquals(0f, sim.collapseRisk(105, y))
        sim.collapse(102, y)
        assertEquals(Terrain.RUBBLE, sim.world.get(102, y))
        assertEquals(1, sim.stats.collapses)
    }

    @Test
    fun acKarincaYemekYer() {
        val sim = newSim()
        sim.clearSources()
        sim.workers.forEach { it.hunger = 90f }
        val food = sim.resources.edibleTotal()
        sim.run(30f)
        assertTrue(sim.resources.edibleTotal() < food)
        assertTrue(sim.workers.all { it.hunger < 90f })
    }

    @Test
    fun hedefTamamlanincaOdulVerilirVeYenisiGelir() {
        val sim = newSim()
        val goal = sim.goals.active.first { it.kind == GoalKind.DIG }
        val food = sim.resources[ResourceType.FOOD]
        sim.stats.cellsDug += goal.target
        sim.goals.update(sim)
        assertTrue(goal !in sim.goals.active)
        assertEquals(3, sim.goals.active.size)
        assertEquals(food + goal.rewardAmount, sim.resources[ResourceType.FOOD])
        // Ödül kapasiteyi aşmaz.
        sim.resources.set(ResourceType.FOOD, sim.capacityOf(StorageKind.FOOD))
        assertEquals(0, sim.addCapped(ResourceType.FOOD, 10))
    }

    @Test
    fun uzunSureliOyundaKoloniAyaktaKalir() {
        val sim = newSim(99)
        sim.resources.add(ResourceType.SOIL, 40)
        sim.resources.add(ResourceType.LEAF, 20)
        for (x in -3 downTo -14) sim.designateDig(x, 9)
        for (y in 10..16) sim.designateDig(-14, y)
        sim.run(300f)
        sim.placeRoom(RoomType.REST_ROOM, -13, 17) ?: sim.placeRoom(RoomType.REST_ROOM, -18, 8)
        sim.run(1500f)
        assertTrue(sim.workerCount >= 6, "işçi: ${sim.workerCount}, ölen ${sim.stats.antsDied}")
        assertTrue(sim.stats.cellsDug >= 19)
        assertTrue(sim.ants.all { it.x.isFinite() && it.y.isFinite() })
    }
}
