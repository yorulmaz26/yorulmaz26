package com.zamaninefendisi.logic

import com.zamaninefendisi.logic.levels.LevelCatalog
import org.junit.Test

class DebugSim {
    fun dump(s: LevelSession): String {
        val sb = StringBuilder()
        for (era in s.timeline.eras) {
            sb.append("-- ${era.title}\n")
            val g = s.timeline.grid(era)
            for (y in 0 until g.height) {
                for (x in 0 until g.width) {
                    val p = Pos(x, y)
                    val c = g[p]
                    val ch = when {
                        s.heroEra == era && s.heroPos == p -> '@'
                        era == s.level.enemyEra && s.enemyAt(p) != null -> 'e'
                        else -> when (c.feature) {
                            Feature.NONE -> when (c.terrain) { Terrain.GRASS -> '.'; Terrain.SAND -> ':'; Terrain.WATER -> '~'; Terrain.MOUNTAIN -> '^'; Terrain.ROAD -> '=' }
                            Feature.TREE, Feature.FOREST -> 'T'; Feature.SAPLING -> 't'; Feature.FIELD -> 'F'; Feature.STUMP -> ','
                            Feature.BRIDGE_WOOD -> 'b'; Feature.BRIDGE_STONE -> 'B'; Feature.HUT -> 'h'; Feature.TOWN -> 'V'; Feature.RUINS -> 'r'
                            Feature.PALISADE -> 'p'; Feature.STONE_WALL -> '#'; Feature.CASTLE -> 'C'; Feature.ENEMY_CAMP -> 'E'; Feature.ENEMY_FORT -> 'K'
                            else -> '?'
                        }
                    }
                    sb.append(ch)
                }
                sb.append('\n')
            }
        }
        return sb.toString()
    }

    @Test
    fun simulate() {
        val id = System.getProperty("simLevel")?.toIntOrNull() ?: return
        val level = LevelCatalog.level(id)!!
        val s = LevelSession(level)
        println(dump(s))
        for (a in level.solution) {
            val err = s.validate(a)
            println(">> $a  ${err ?: ""}")
            if (err != null) break
            val ev = s.perform(a)
            println("turn=${s.turn} wood=${s.wood} hp=${s.heroHp} status=${s.status} ${s.failReason ?: ""} enemies=${s.enemies.map { it.pos }}")
            ev.filterNot { it is GameEvent.CellsChanged }.forEach { println("   $it") }
            println(dump(s))
        }
        s.journal.forEach { println("J ${it.turn} ${it.kind} ${it.text}") }
    }
}
