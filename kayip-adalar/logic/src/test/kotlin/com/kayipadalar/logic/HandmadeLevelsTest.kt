package com.kayipadalar.logic

import com.kayipadalar.logic.engine.*
import org.junit.Assert.*
import org.junit.Test

class HandmadeLevelsTest {
    @Test
    fun area1LevelsAreSolvableWithinLimits() {
        val area = TestAssets.area(1)
        assertEquals(5, area.levels.size)
        for (lv in area.levels) {
            val r = Solver.beam(lv, TestAssets.config, width = 3000)
            println("Bölüm ${lv.id}: ${r?.solution?.size} hamle -> ${r?.solution}")
            assertNotNull("Bölüm ${lv.id} çözülemedi", r)
            val best = r!!.solution.size
            assertTrue("Bölüm ${lv.id}: 3 yıldız sınırı (${lv.star3}) en iyi çözümden ($best) küçük", lv.star3 >= best)
            assertTrue("Bölüm ${lv.id}: hamle sınırı çok dar", lv.moveLimit >= best + 2)
            assertEquals(Status.WON, Solver.replay(lv, TestAssets.config, r.solution).status)
        }
    }
}
