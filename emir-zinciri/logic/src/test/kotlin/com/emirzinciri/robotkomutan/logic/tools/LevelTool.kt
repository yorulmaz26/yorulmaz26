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
            if (def.robots.size != 1) return@map def
            val t0 = System.currentTimeMillis()
            val sol = Solver(LevelData(def), maxNodes = 3_000_000).solve(def.maxCommands)
            if (sol == null) {
                println("Bölüm ${def.id} '${def.name}': ÇÖZÜM YOK (≤${def.maxCommands} kart)")
                return@map def
            }
            val tokens = mapOf(def.robots.single().id to sol.cards)
            val out = Simulation.create(LevelData(def), tokens).getOrThrow().runToEnd()
            check(out is Outcome.Win) { "Çözüm doğrulanamadı: ${def.id}" }
            val third = def.third ?: if (def.energy) StarRule(StarRuleType.ENERGY_USED, out.stats.energyUsed) else null
            println("Bölüm %3d %-26s par=%2d adım=%2d enerji=%2d sınır=%2d düğüm=%7d %4dms  %s".format(
                def.id, def.name, sol.cards.size, out.stats.ticks, out.stats.energyUsed, def.maxCommands,
                sol.explored, System.currentTimeMillis() - t0, sol.cards.joinToString(" ") { it.take(4) }))
            def.copy(par = sol.cards.size, solution = tokens, third = third)
        }
        file.writeText(LevelParser.json.encodeToString(AreaFile.serializer(), af.copy(levels = updated)) + "\n")
    }
}
