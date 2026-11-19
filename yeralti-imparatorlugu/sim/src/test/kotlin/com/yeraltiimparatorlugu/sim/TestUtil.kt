package com.yeraltiimparatorlugu.sim

fun Simulation.run(seconds: Float) {
    val steps = (seconds / Simulation.STEP).toInt()
    repeat(steps) { step(Simulation.STEP) }
}

/** Testlerde rastgele yüzey kaynaklarını temizler. */
fun Simulation.clearSources() { sources.clear() }
