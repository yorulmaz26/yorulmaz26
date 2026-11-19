package com.kayipadalar.logic

import com.kayipadalar.logic.engine.*
import com.kayipadalar.logic.level.*
import org.junit.Assert.*
import org.junit.Test

class GeneratorSmokeTest {
    @Test
    fun generatesSolvableLevels() {
        val gen = LevelGenerator(TestAssets.config)
        val ids = (System.getProperty("genIds") ?: "6-60").split('-').let { (it[0].toInt()..it[1].toInt()) }
        for (id in ids) {
            val t0 = System.currentTimeMillis()
            val lv = gen.generateMain(id)
            val b = Solver.replay(lv, TestAssets.config, lv.solution)
            println("L$id ${lv.name} obj=${lv.objectives.map { it.type }} isl=${lv.islands.size} units=${lv.units.size} sol=${lv.solution.size} limit=${lv.moveLimit} mech=${lv.mechanics} ${System.currentTimeMillis() - t0}ms")
            assertEquals(Status.WON, b.status)
        }
    }
}
