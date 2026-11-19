package com.emirzinciri.robotkomutan.logic.tools

import com.emirzinciri.robotkomutan.logic.level.AreaFile
import com.emirzinciri.robotkomutan.logic.level.LevelData
import com.emirzinciri.robotkomutan.logic.level.LevelParser
import com.emirzinciri.robotkomutan.logic.level.StarRule
import com.emirzinciri.robotkomutan.logic.level.StarRuleType
import com.emirzinciri.robotkomutan.logic.sim.Outcome
import com.emirzinciri.robotkomutan.logic.sim.Simulation
import com.emirzinciri.robotkomutan.logic.sim.Solver
import java.io.File

/**
 * Bölüm tasarım aracı: tek robotlu bölümleri çözücüyle çözer, "par" ve referans çözümü yazar.
 * Kullanım: ./gradlew :logic:levelTool --args="1 5"   (alan aralığı)
 */
fun main(args: Array<String>) {
    val dir = File(System.getProperty("levels.dir"))
    val from = args.getOrNull(0)?.toInt() ?: 1
    val to = args.getOrNull(1)?.toInt() ?: from
    for (area in from..to) {
        val file = File(dir, "area_%03d.json".format(area))
        val af = LevelParser.json.decodeFromString(AreaFile.serializer(), file.readText())
        val updated = af.levels.map { def ->
            val t0 = System.currentTimeMillis()
            val level = LevelData(def)
            val sol = Solver(level, maxNodes = 4_000_000).solve(def.maxCommands)
            val hand = def.solution.takeIf { it.isNotEmpty() }?.let { h ->
                val o = Simulation.create(level, h).getOrNull()?.runToEnd()
                if (o is Outcome.Win) h to o else { println("  Bölüm ${def.id}: elle yazılan çözüm kazanmıyor: $o"); null }
            }
            val fromSolver = sol?.let { s -> (if (s.programs.isEmpty()) mapOf(def.robots.single().id to s.cards) else s.programs) }
                ?.let { p -> p to (Simulation.create(level, p).getOrThrow().runToEnd() as Outcome.Win) }
            val best = listOfNotNull(hand, fromSolver).minByOrNull { it.second.stats.cards }
            if (best == null) {
                println("Bölüm ${def.id} '${def.name}': ÇÖZÜM YOK (≤${def.maxCommands} kart, ${System.currentTimeMillis() - t0}ms)")
                return@map def
            }
            val (tokens, out) = best
            val third = def.third ?: when {
                def.energy -> StarRule(StarRuleType.ENERGY_USED, out.stats.energyUsed)
                def.robots.size > 1 -> StarRule(StarRuleType.TICKS, out.stats.ticks)
                else -> null
            }
            val src = if (best === hand) "el" else "çözücü"
            println("Bölüm %3d %-24s par=%2d (çözücü=%s, el=%s) adım=%2d sınır=%2d düğüm=%7d %5dms".format(
                def.id, def.name, out.stats.cards, sol?.cards?.size ?: "-", hand?.second?.stats?.cards ?: "-",
                out.stats.ticks, def.maxCommands, sol?.explored ?: -1, System.currentTimeMillis() - t0))
            tokens.forEach { (id, c) -> println("      $id [$src]: ${c.joinToString(" ")}") }
            def.copy(par = out.stats.cards, solution = tokens, third = third)
        }
        file.writeText(LevelParser.json.encodeToString(AreaFile.serializer(), af.copy(levels = updated)) + "\n")
    }
}
