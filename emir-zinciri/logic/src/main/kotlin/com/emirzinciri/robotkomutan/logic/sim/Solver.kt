package com.emirzinciri.robotkomutan.logic.sim

import com.emirzinciri.robotkomutan.logic.command.CommandType
import com.emirzinciri.robotkomutan.logic.command.Instr
import com.emirzinciri.robotkomutan.logic.command.Program
import com.emirzinciri.robotkomutan.logic.level.LevelData

/**
 * Tek robotlu bölümler için en az kartlı çözümü bulan genişlik öncelikli arama.
 * Akış kartları (Tekrar/Koşul) kullanılmaz; bu yüzden bulunan değer döngüsüz çözümler için kesin optimumdur.
 * Bölüm doğrulama testleri ve bölüm üretici bunu kullanır.
 */
class Solver(private val level: LevelData, private val maxNodes: Int = 400_000) {

    data class Solution(val cards: List<String>, val explored: Int)

    private val robotId = level.def.robots.single().id
    private val commands: List<CommandType> = level.def.commands.filter {
        !it.isFlow && it != CommandType.SEND && it != CommandType.WAIT_MSG
    }
    private val programs = commands.associateWith { Program.compile(listOf(Instr(it))).getOrThrow() }

    fun solve(maxDepth: Int = level.def.maxCommands): Solution? {
        val start = WorldState.initial(level, mapOf(robotId to 1))
        if (Simulation(level, emptyMap()).objectivesMet()) return Solution(emptyList(), 0)
        val seen = HashSet<String>()
        seen += key(start)
        var frontier = listOf(Node(start, null, null))
        var explored = 0
        for (depth in 1..maxDepth) {
            val next = ArrayList<Node>()
            for (node in frontier) {
                for (c in commands) {
                    explored++
                    if (explored > maxNodes) return null
                    val sim = Simulation(level, mapOf(robotId to programs.getValue(c)))
                    val st = node.state.copy()
                    st.robots.forEach { it.pc = 0; it.loopLeft = IntArray(1); if (it.status == RobotStatus.DONE) it.status = RobotStatus.RUNNING }
                    sim.replaceState(st)
                    val res = sim.step()
                    if (res.events.any { it is SimEvent.Bumped || it is SimEvent.CommandFailed || it is SimEvent.OutOfEnergy }) continue
                    val out = res.outcome
                    val child = Node(sim.state, node, c)
                    if (out is Outcome.Win) return Solution(child.path(), explored)
                    if (out is Outcome.Fail && out.reason == FailReason.ROBOT_LOST) continue
                    if (seen.add(key(sim.state))) next += child
                }
            }
            if (next.isEmpty()) return null
            frontier = next
        }
        return null
    }

    private class Node(val state: WorldState, val parent: Node?, val cmd: CommandType?) {
        fun path(): List<String> {
            val out = ArrayList<String>()
            var n: Node? = this
            while (n?.cmd != null) { out += n.cmd!!.name; n = n.parent }
            return out.reversed()
        }
    }

    /** Arama için eşdeğerlik anahtarı: adım sayısı ve istatistikler hariç dünya durumu. */
    private fun key(s: WorldState): String = buildString {
        for (r in s.robots) append(r.pos.x).append(',').append(r.pos.y).append(r.dir).append(r.energy).append(r.carrying).append(r.status).append('|')
        append(s.boxes.sortedWith(WorldState.posOrder)).append(s.cells.keys.sortedWith(WorldState.posOrder))
        append(s.filledPits.sortedWith(WorldState.posOrder)).append(s.toggled.sorted()).append(s.timers.toSortedMap())
        append(s.socketCharge.toSortedMap(WorldState.posOrder)).append(s.openDoors.sortedWith(WorldState.posOrder))
    }
}
