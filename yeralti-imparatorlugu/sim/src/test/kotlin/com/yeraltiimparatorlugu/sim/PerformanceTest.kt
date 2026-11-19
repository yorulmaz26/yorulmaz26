package com.yeraltiimparatorlugu.sim

import kotlin.test.Test
import kotlin.test.assertTrue

class PerformanceTest {
    /** 300 karınca, canlılar, sel ve tüm sistemler açıkken adım süresi mobil 60 FPS bütçesinin çok altında kalmalı. */
    @Test
    fun yuzlerceKarincaHizliSimuleEdilir() {
        val sim = Simulation.newGame(31)
        sim.resources.set(ResourceType.FOOD, 80)
        sim.completeAllResearch(*ResearchTree.all.map { it.id }.toTypedArray())
        for (x in -3 downTo -40) sim.designateDig(x, 9)
        for (x in 7..40) sim.designateDig(x, 9)
        for (y in 10..30) { sim.designateDig(-20, y); sim.designateDig(20, y) }
        repeat(250) { i -> sim.ants.add(Ant(sim.newId(), AntType.WORKER, -1.5f + (i % 9), 9.5f)) }
        repeat(50) { i -> sim.ants.add(Ant(sim.newId(), AntType.SOLDIER, -1.5f + (i % 9), 9.5f)) }
        sim.run(60f)
        repeat(10) { sim.spawnCreature(CreatureKind.SPIDER, -30 + it * 6, 9) }
        sim.environment.forceWeather(sim, Weather.STORM, 1e6)
        sim.run(5f)
        val steps = (60f / Simulation.STEP).toInt()
        val t0 = System.nanoTime()
        repeat(steps) { sim.step(Simulation.STEP) }
        val perStep = (System.nanoTime() - t0) / 1e6 / steps
        println("300 karınca + canlılar + sel: adım başına ${"%.3f".format(perStep)} ms, su hücresi ${sim.environment.water.size}, kazılan ${sim.stats.cellsDug}")
        assertTrue(perStep < 8.0, "Adım başına $perStep ms")
    }
}
