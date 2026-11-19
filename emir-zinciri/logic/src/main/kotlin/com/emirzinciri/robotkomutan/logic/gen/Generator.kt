package com.emirzinciri.robotkomutan.logic.gen

import com.emirzinciri.robotkomutan.logic.command.CommandType
import com.emirzinciri.robotkomutan.logic.command.CommandType.*
import com.emirzinciri.robotkomutan.logic.grid.Dir
import com.emirzinciri.robotkomutan.logic.level.EnemyDef
import com.emirzinciri.robotkomutan.logic.level.EnemyType
import com.emirzinciri.robotkomutan.logic.level.LevelData
import com.emirzinciri.robotkomutan.logic.level.LevelDef
import com.emirzinciri.robotkomutan.logic.level.ObjectiveDef
import com.emirzinciri.robotkomutan.logic.level.ObjectiveType
import com.emirzinciri.robotkomutan.logic.level.RobotDef
import com.emirzinciri.robotkomutan.logic.sim.Solver
import java.util.Random

/**
 * Doğrulanmış bölüm üretici (Sonsuz Fabrika ve Günlük Bulmaca).
 * Rastgele harita kurulur, çözücüyle çözülür; çözümsüz ya da çok kolay olanlar reddedilir.
 * Aynı (kat, tohum) her zaman aynı bölümü üretir. Kat arttıkça harita büyür ve yeni mekanikler eklenir.
 */
object Generator {
    data class Result(val def: LevelDef, val attempts: Int)

    fun minCards(floor: Int) = (3 + floor / 2).coerceAtMost(11)
    fun maxCards(floor: Int) = (9 + floor / 2).coerceAtMost(18)

    fun generate(floor: Int, seed: Long, idBase: Int = 10_000, name: String = "Kat $floor", maxAttempts: Int = 80): Result? {
        for (attempt in 0 until maxAttempts) {
            val rnd = Random(seed * 1_000_003L + floor * 7919L + attempt)
            val def = draft(floor, rnd, idBase + floor, name) ?: continue
            val sol = Solver(LevelData(def), maxNodes = 40_000).solve(maxCards(floor)) ?: continue
            if (sol.cards.size < minCards(floor)) continue
            val cards = sol.cards.size
            return Result(def.copy(par = cards, maxCommands = (cards + 3).coerceAtMost(maxCards(floor) + 2),
                solution = mapOf("A" to sol.cards)), attempt + 1)
        }
        return null
    }

    private fun draft(floor: Int, rnd: Random, id: Int, name: String): LevelDef? {
        val w = 5 + (floor / 4).coerceAtMost(3)
        val h = 5 + ((floor + 2) / 5).coerceAtMost(2)
        val g = Array(h) { CharArray(w) { '.' } }
        // Kenar boşlukları ve duvarlar
        val wallP = 0.16 + 0.01 * floor.coerceAtMost(8)
        for (y in 0 until h) for (x in 0 until w) {
            val r = rnd.nextDouble()
            g[y][x] = when { r < wallP -> '#'; r < wallP + 0.06 -> ' '; else -> '.' }
        }
        fun free(): Pair<Int, Int>? {
            repeat(200) { val x = rnd.nextInt(w); val y = rnd.nextInt(h); if (g[y][x] == '.') return x to y }
            return null
        }
        val (rx, ry) = free() ?: return null
        g[ry][rx] = 'R'
        var exit: Pair<Int, Int>? = null
        repeat(60) { val c = free() ?: return null; if (kotlin.math.abs(c.first - rx) + kotlin.math.abs(c.second - ry) >= 3 + floor / 3) { exit = c; return@repeat } }
        val (ex, ey) = exit ?: return null
        g[ey][ex] = 'E'
        val commands = mutableListOf(FORWARD, TURN_LEFT, TURN_RIGHT, BACK)
        val objectives = mutableListOf(ObjectiveDef(ObjectiveType.REACH_EXIT))
        val channels = HashMap<String, Int>()
        val enemies = ArrayList<EnemyDef>()
        var energy = false
        if (floor >= 3 && rnd.nextBoolean()) { // kutu + hedef
            val b = free(); val t = free()
            if (b != null && t != null) { g[b.second][b.first] = 'B'; g[t.second][t.first] = 'x'; objectives += ObjectiveDef(ObjectiveType.BOXES_ON_TARGETS); commands += PUSH }
        }
        if (floor >= 4 && rnd.nextInt(3) == 0) { // çukur + kutu
            val p = free(); val b = free()
            if (p != null && b != null) { g[p.second][p.first] = 'o'; g[b.second][b.first] = 'B'; if (PUSH !in commands) commands += PUSH }
        }
        if (floor >= 5 && rnd.nextInt(3) == 0) { // anahtar + kapı
            val s = free(); val d = free()
            if (s != null && d != null) { g[s.second][s.first] = 's'; g[d.second][d.first] = 'D'; commands += PRESS }
        }
        if (floor >= 7 && rnd.nextInt(3) == 0) { // plaka + kapı (kanal 2)
            val p = free(); val d = free()
            if (p != null && d != null) {
                g[p.second][p.first] = 'p'; g[d.second][d.first] = 'D'
                channels["${p.first},${p.second}"] = 2; channels["${d.first},${d.second}"] = 2
                commands += WAIT
            }
        }
        var robotEnergy = 10
        if (floor >= 9 && rnd.nextInt(3) == 0) { // enerji kıtlığı
            val c = free()
            if (c != null) { g[c.second][c.first] = 'c'; energy = true; robotEnergy = 4 + rnd.nextInt(3); commands += COLLECT }
        }
        if (floor >= 11 && rnd.nextInt(3) == 0) { // devriye
            val e = free()
            if (e != null) { enemies += EnemyDef("P", EnemyType.PATROL, e.first, e.second, Dir.entries[rnd.nextInt(4)], hp = 1); commands += ATTACK }
        }
        val dir = Dir.entries[rnd.nextInt(4)]
        g[ry][rx] = '.'
        val map = g.map { String(it) }
        return LevelDef(
            id = id, area = 1, name = name, goalText = objectives.joinToString(" · ") { it.type.title }, map = map,
            robots = listOf(RobotDef("A", rx, ry, dir, energy = robotEnergy, maxEnergy = 10)), commands = commands.distinct().sortedBy { it.ordinal },
            maxCommands = maxCards(floor), par = 1, objectives = objectives, energy = energy, channels = channels, enemies = enemies, maxTicks = 80,
        )
    }

    /** Günlük bulmaca: tarih (epoch günü) tohumu, orta zorluk. */
    fun daily(dayKey: Long): Result? = generate(floor = 8 + (dayKey % 5).toInt(), seed = dayKey * 2654435761L, idBase = 20_000, name = "Günlük Komut Bulmacası")

}
