package com.besaniyelik.dunya.tools

import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.utils.GdxNativesLoader
import com.besaniyelik.dunya.level.*
import com.besaniyelik.dunya.sim.*

object Probe {
    @JvmStatic
    fun main(args: Array<String>) {
        GdxNativesLoader.load(); Box2D.init()
        val id = args.getOrElse(0) { "1" }.toInt()
        val d = LevelGenerator.generate(id, 0)
        println("solids=${d.data.solids.size} hazards=${d.data.hazards.size} phases=${d.data.phases.size} goal=${d.data.goalX}")
        // düz koşu: hiç dokunmadan ne kadar gider
        val sim = Simulation(d.data, SimConfig(solver = true, powerups = false))
        var t0 = System.nanoTime()
        var n = 0
        while (!sim.failed && sim.status != SimStatus.COMPLETE && n < 3000) { sim.step(false); n++ }
        println("no-input: ticks=$n x=${sim.player.x} y=${sim.player.y} cause=${sim.deathCause} grounded=${sim.player.grounded} ${(System.nanoTime()-t0)/1e6}ms")
        sim.dispose()
        t0 = System.nanoTime()
        val r = LevelForge.trySolve(d.data)
        println("solve: ${r.success} nodes=${r.nodes} ticks=${r.ticks} ${r.reason} ${(System.nanoTime()-t0)/1e6}ms")
        for (cfg in listOf(SimConfig(solver = true, powerups = false, strict = true), SimConfig(solver = true, powerups = false), SimConfig(hearts=1, powerups = false))) {
            val s2 = Simulation(d.data, cfg)
            val ok = LevelSolver.replay(s2, r.toggles)
            println("replay strict=${cfg.strict} solver=${cfg.solver}: $ok tick=${s2.tick} x=${s2.player.x} cause=${s2.deathCause}")
            s2.dispose()
        }
        run {
            val a = Simulation(d.data, SimConfig(solver = true, powerups = false, strict = true))
            val b = Simulation(d.data, SimConfig(solver = true, powerups = false, strict = true))
            val ta = InputTrack(r.toggles); val tb = InputTrack(r.toggles)
            var printed = 0
            for (tick in 0 until 1200) {
                if (tick % 4 == 0) { val sn = b.snapshot(); b.restore(sn) }
                a.step(ta.at(tick)); b.step(tb.at(tick))
                val dx = a.player.x - b.player.x; val dy = a.player.y - b.player.y
                if ((kotlin.math.abs(dx) > 1e-4 || kotlin.math.abs(dy) > 1e-4) && printed < 6) {
                    printed++
                    println("diverge tick=$tick a=(${a.player.x},${a.player.y},${a.player.vy},g=${a.player.grounded}) b=(${b.player.x},${b.player.y},${b.player.vy},g=${b.player.grounded})")
                }
                if (a.failed || b.failed) { println("fail a=${a.failed} b=${b.failed} tick=$tick"); break }
            }
        }
        t0 = System.nanoTime()
        val tr = LevelForge.trace(d.data, r.toggles)
        println("trace: ${tr?.size} ${(System.nanoTime()-t0)/1e6}ms")
        t0 = System.nanoTime()
        val f = LevelForge.forge(id) { println(it) }
        println("forge: att=${f.report.attempts} fair=${f.report.fair} coins=${f.report.coins} ${(System.nanoTime()-t0)/1e6}ms")
    }
}
