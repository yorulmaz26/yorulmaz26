package com.besaniyelik.dunya.tools

import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.utils.GdxNativesLoader
import com.besaniyelik.dunya.level.*
import com.besaniyelik.dunya.sim.*

object Probe2 {
    @JvmStatic
    fun main(args: Array<String>) {
        GdxNativesLoader.load(); Box2D.init()
        LevelRepository.reader = { java.io.File("/home/user/yorulmaz26/bes-saniyelik-dunya/assets/$it").readText() }
        for (id in args[0].split(",").map { it.toInt() }) {
            val lv = LevelRepository.level(id)
            val tg = lv.solution.toIntArray()
            println("== $id ${lv.name} toggles=${tg.size}")
            for (i in tg.indices step 2) for (s in intArrayOf(2, -2)) {
                val t2 = tg.copyOf(); t2[i] += s; if (i + 1 < t2.size) t2[i + 1] += s
                val sim = Simulation(lv, SimConfig(hearts = 1, powerups = false))
                val ok = LevelSolver.replay(sim, t2)
                if (!ok) {
                    val ph = sim.activeRules
                    val near = sim.solids.filter { it.x < sim.player.x + 2 && it.x + it.w > sim.player.x - 2 }.map { it.def.type }.distinct()
                    val hz = sim.hazards.filter { kotlin.math.abs(it.cx - sim.player.x) < 2.5f }.map { it.def.type }
                    println("  jump@${tg[i]} shift=$s -> ${sim.deathCause} x=${"%.1f".format(sim.player.x)} y=${"%.1f".format(sim.player.y)} rules=$ph solids=$near haz=$hz")
                }
                sim.dispose()
            }
        }
    }
}
