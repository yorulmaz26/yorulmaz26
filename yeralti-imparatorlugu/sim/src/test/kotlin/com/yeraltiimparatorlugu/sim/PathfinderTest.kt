package com.yeraltiimparatorlugu.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathfinderTest {
    @Test
    fun yalnizcaKazilmisHucrelerdenGecer() {
        val w = World(11)
        val pf = Pathfinder(w, PheromoneField())
        for (x in 0..10) w.set(x, 5, Terrain.TUNNEL)
        for (y in 0..5) w.set(0, y, Terrain.TUNNEL)
        val p = assertNotNull(pf.find(0, -1, 10, 5))
        for (i in 0 until p.size / 2) assertTrue(w.isWalkable(p[i * 2], p[i * 2 + 1]))
        assertEquals(10, p[p.size - 2]); assertEquals(5, p[p.size - 1])
        assertEquals(16, p.size / 2) // 6 aşağı + 10 yana
    }

    @Test
    fun kapaliAlanaYolYok() {
        val w = World(11)
        val pf = Pathfinder(w, PheromoneField())
        w.set(20, 20, Terrain.TUNNEL)
        assertNull(pf.find(0, -1, 20, 20))
    }

    @Test
    fun kacinmaFeromonuYoluUzatir() {
        val w = World(11)
        val ph = PheromoneField()
        val pf = Pathfinder(w, ph)
        // İki paralel yol: üst (y=3) kısa, alt (y=6) uzun.
        for (x in 0..10) { w.set(x, 3, Terrain.TUNNEL); w.set(x, 6, Terrain.TUNNEL) }
        for (y in 3..6) { w.set(0, y, Terrain.TUNNEL); w.set(10, y, Terrain.TUNNEL) }
        val direct = pf.find(0, 3, 10, 3)!!
        assertTrue(direct.size / 2 == 10)
        ph.place(1, PheromoneType.AVOID, 5, 3)
        val around = pf.find(0, 3, 10, 3)!!
        assertTrue(around.size / 2 > 10, "Kaçınma bölgesinin etrafından dolaşmalı")
    }
}
