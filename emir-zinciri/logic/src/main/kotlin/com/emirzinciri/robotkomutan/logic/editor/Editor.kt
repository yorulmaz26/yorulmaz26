package com.emirzinciri.robotkomutan.logic.editor

import com.emirzinciri.robotkomutan.logic.command.CommandType
import com.emirzinciri.robotkomutan.logic.grid.Dir
import com.emirzinciri.robotkomutan.logic.level.EnemyDef
import com.emirzinciri.robotkomutan.logic.level.EnemyType
import com.emirzinciri.robotkomutan.logic.level.LevelDef
import com.emirzinciri.robotkomutan.logic.level.ObjectiveDef
import com.emirzinciri.robotkomutan.logic.level.ObjectiveType
import com.emirzinciri.robotkomutan.logic.level.RobotDef

/** Editör fırçaları. */
enum class Brush(val title: String, val char: Char?) {
    FLOOR("Zemin", '.'), WALL("Duvar", '#'), VOID("Boşluk", ' '), PIT("Çukur", 'o'), EXIT("Çıkış", 'E'),
    BOX("Kutu", 'B'), TARGET("Hedef", 'x'), PLATE("Plaka", 'p'), SWITCH("Anahtar", 's'), DOOR("Kapı", 'D'),
    CELL("Enerji", 'c'), CHARGER("Şarj", 'Q'), CONVEYOR("Bant", '>'), LASER("Lazer", 'L'),
    ROBOT("Robot", null), PATROL("Devriye", null), ERASE("Sil", null),
}

/**
 * Harita editörü modeli: hücre ızgarası, robot ve düşman yerleşimi. [toLevel] oynanabilir bir bölüm üretir.
 * Kurallar: en az bir robot ve bir çıkış; en fazla 3 robot.
 */
class EditorModel(var width: Int = 7, var height: Int = 7) {
    var name = "Benim Haritam"
    var grid: Array<CharArray> = Array(height) { CharArray(width) { '.' } }
    val robots = LinkedHashMap<Pair<Int, Int>, Dir>()
    val patrols = LinkedHashMap<Pair<Int, Int>, Dir>()
    val laserDir = HashMap<Pair<Int, Int>, Int>()

    fun resize(w: Int, h: Int) {
        val ng = Array(h) { y -> CharArray(w) { x -> grid.getOrNull(y)?.getOrNull(x) ?: '.' } }
        grid = ng; width = w; height = h
        robots.keys.removeAll { it.first >= w || it.second >= h }
        patrols.keys.removeAll { it.first >= w || it.second >= h }
    }

    /** Fırçayı uygular; aynı hücreye tekrar dokunmak yönü döndürür (robot, devriye, bant, lazer). */
    fun paint(x: Int, y: Int, brush: Brush) {
        if (x !in 0 until width || y !in 0 until height) return
        val p = x to y
        when (brush) {
            Brush.ROBOT -> {
                if (p in robots) robots[p] = robots.getValue(p).right()
                else if (robots.size < 3) { robots[p] = Dir.E; patrols.remove(p); if (!walkable(grid[y][x])) grid[y][x] = '.' }
            }
            Brush.PATROL -> if (p in patrols) patrols[p] = patrols.getValue(p).right() else { patrols[p] = Dir.W; robots.remove(p); if (!walkable(grid[y][x])) grid[y][x] = '.' }
            Brush.ERASE -> { robots.remove(p); patrols.remove(p); grid[y][x] = '.' }
            Brush.CONVEYOR -> grid[y][x] = when (grid[y][x]) { '>' -> 'v'; 'v' -> '<'; '<' -> '^'; '^' -> '>'; else -> '>' }
            Brush.LASER -> { if (grid[y][x] == 'L') laserDir[p] = ((laserDir[p] ?: 1) + 1) % 4 else { grid[y][x] = 'L'; laserDir[p] = 1 }; robots.remove(p); patrols.remove(p) }
            else -> {
                val c = brush.char!!
                grid[y][x] = c
                if (!walkable(c)) { robots.remove(p); patrols.remove(p) }
            }
        }
    }

    private fun walkable(c: Char) = c in ".ExpQ><v^cBX".toSet() && c != 'B'

    fun validate(): String? = when {
        robots.isEmpty() -> "En az bir robot yerleştir."
        grid.none { r -> 'E' in r } -> "En az bir çıkış yerleştir."
        grid.sumOf { r -> r.count { it == 'E' } } < robots.size -> "Her robot için bir çıkış gerekli."
        grid.sumOf { r -> r.count { it == 'x' } } > grid.sumOf { r -> r.count { it == 'B' } } -> "Hedef sayısı kutu sayısından fazla."
        else -> null
    }

    fun toLevel(id: Int): LevelDef {
        val map = grid.map { String(it) }
        val chars = map.joinToString("")
        val cmds = linkedSetOf(CommandType.FORWARD, CommandType.TURN_LEFT, CommandType.TURN_RIGHT, CommandType.BACK, CommandType.WAIT)
        if ('B' in chars) { cmds += CommandType.PUSH; cmds += CommandType.PICK; cmds += CommandType.DROP; cmds += CommandType.PULL }
        if ('o' in chars || ' ' in chars) cmds += CommandType.JUMP
        if ('s' in chars) cmds += CommandType.PRESS
        if ('c' in chars || 'Q' in chars) cmds += CommandType.COLLECT
        if (patrols.isNotEmpty()) { cmds += CommandType.ATTACK; cmds += CommandType.SHIELD }
        if (robots.size > 1) { cmds += CommandType.SEND; cmds += CommandType.WAIT_MSG }
        cmds += CommandType.REPEAT
        val obj = mutableListOf(ObjectiveDef(ObjectiveType.REACH_EXIT))
        if ('x' in chars) obj += ObjectiveDef(ObjectiveType.BOXES_ON_TARGETS)
        return LevelDef(
            id = id, area = 1, name = name, goalText = obj.joinToString(" · ") { it.type.title }, map = map,
            robots = robots.entries.mapIndexed { i, (p, d) -> RobotDef(('A' + i).toString(), p.first, p.second, d) },
            enemies = patrols.entries.mapIndexed { i, (p, d) -> EnemyDef("P$i", EnemyType.PATROL, p.first, p.second, d, hp = 1) },
            commands = cmds.sortedBy { it.ordinal }, maxCommands = 30, par = 30, objectives = obj,
            energy = 'c' in chars || 'Q' in chars, values = laserDir.mapKeys { "${it.key.first},${it.key.second}" }, maxTicks = 120,
        )
    }

    companion object {
        fun from(def: LevelDef): EditorModel {
            val m = EditorModel(def.width, def.height)
            m.name = def.name
            m.grid = Array(def.height) { y -> CharArray(def.width) { x -> def.map[y].getOrElse(x) { ' ' } } }
            def.robots.forEach { m.robots[it.x to it.y] = it.dir }
            def.enemies.forEach { m.patrols[it.x to it.y] = it.dir }
            def.values.forEach { (k, v) -> val (x, y) = k.split(',').map(String::toInt); if (m.grid[y][x] == 'L') m.laserDir[x to y] = v }
            return m
        }
    }
}
