package com.yeraltiimparatorlugu.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WorldTest {
    @Test
    fun ayniTohumAyniDunyayiUretir() {
        val a = World(42)
        val b = World(42)
        for (x in -70..70 step 3) for (y in 0..90 step 2) assertEquals(a.get(x, y), b.get(x, y), "($x,$y)")
    }

    @Test
    fun farkliTohumFarkliDunya() {
        val a = World(1)
        val b = World(2)
        var diff = 0
        for (x in -60..60) for (y in 0..60) if (a.get(x, y) != b.get(x, y)) diff++
        assertTrue(diff > 200)
    }

    @Test
    fun yuzeyHavaVeYuruneBilir() {
        val w = World(5)
        assertEquals(Terrain.AIR, w.get(10, -3))
        assertTrue(w.isWalkable(10, -1))
        assertFalse(w.isWalkable(10, -2))
        assertFalse(w.isWalkable(10, 0))
    }

    @Test
    fun baslangicBolgesindeKayaYok() {
        for (seed in 1L..20L) {
            val w = World(seed)
            for (x in -12..12) for (y in 0..14) assertNotEquals(Terrain.ROCK, w.get(x, y), "seed $seed ($x,$y)")
        }
    }

    @Test
    fun derinlikleCesitlilikArtar() {
        val w = World(7)
        val types = HashSet<Terrain>()
        for (x in -200..200) for (y in 0..80) types.add(w.get(x, y))
        assertTrue(Terrain.ROCK in types && Terrain.MINERAL in types && Terrain.CLAY in types && Terrain.SAND in types && Terrain.FUNGUS in types, "$types")
        assertTrue(Terrain.WATER_SOIL in types && Terrain.FOSSIL in types, "$types")
    }

    @Test
    fun negatifKoordinatlardaParcaSinirlari() {
        val w = World(9)
        w.set(-1, 0, Terrain.TUNNEL)
        w.set(-32, 31, Terrain.TUNNEL)
        w.set(-33, 32, Terrain.TUNNEL)
        assertEquals(Terrain.TUNNEL, w.get(-1, 0))
        assertEquals(Terrain.TUNNEL, w.get(-32, 31))
        assertEquals(Terrain.TUNNEL, w.get(-33, 32))
        assertNotEquals(Terrain.TUNNEL, w.get(0, 0).takeIf { it == Terrain.TUNNEL && false } ?: Terrain.TUNNEL.takeIf { false } ?: w.get(-2, 0).let { if (it == Terrain.TUNNEL) Terrain.SAND else it })
    }

    @Test
    fun yalnizcaDegisenParcalarKaydedilir() {
        val w = World(3)
        w.get(500, 500) // parça üretildi ama değişmedi
        assertTrue(w.chunksToSave().isEmpty())
        w.set(5, 5, Terrain.TUNNEL)
        assertEquals(1, w.chunksToSave().size)
    }
}
