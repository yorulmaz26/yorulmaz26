package com.yeraltiimparatorlugu.sim

import kotlin.test.Test
import kotlin.test.assertTrue

class PerformanceTest {
    /** 300 işçiyle bir dakikalık simülasyon, mobil 60 FPS bütçesinin çok altında kalmalı. */
    @Test
    fun yuzlerceKarincaHizliSimuleEdilir() {
        val sim = Simulation.newGame(31)
        sim.resources.set(ResourceType.FOOD, 80)
        for (x in -3 downTo -40) sim.designateDig(x, 9)
        for (x in 7..40) sim.designateDig(x, 9)
        for (y in 10..30) { sim.designateDig(-20, y); sim.designateDig(20, y) }
        repeat(294) { i -> sim.ants.add(Ant(sim.newId(), AntType.WORKER, -1.5f + (i % 9), 9.5f)) }
        sim.run(5f) // ısınma
        val steps = (60f / Simulation.STEP).toInt()
        val t0 = System.nanoTime()
        repeat(steps) { sim.step(Simulation.STEP) }
        val ms = (System.nanoTime() - t0) / 1e6
        val perStep = ms / steps
        println("300 karınca: adım başına ${"%.3f".format(perStep)} ms, kazılan ${sim.stats.cellsDug}")
        // 30 Hz simülasyon: adım başına 16 ms çerçevenin küçük bir kısmı olmalı (CI makineleri için cömert sınır).
        assertTrue(perStep < 8.0, "Adım başına $perStep ms")
    }
}
