package com.emirzinciri.robotkomutan.logic.sim

import com.emirzinciri.robotkomutan.logic.command.CommandType
import com.emirzinciri.robotkomutan.logic.command.Instr
import com.emirzinciri.robotkomutan.logic.command.Program
import com.emirzinciri.robotkomutan.logic.level.LevelData

/**
 * En az toplam kartlı çözümü arar (Dijkstra; her adımda her robot bir kart çalıştırır ya da programını bitirir).
 * Akış ve mesaj kartları kullanılmaz: bulunan değer bu kartlar olmadan kesin optimumdur.
 * Tek robotta sade genişlik öncelikli aramaya eşdeğerdir. Bölüm aracı ve doğrulama testleri kullanır.
 */
class Solver(private val level: LevelData, private val maxNodes: Int = 400_000) {

    data class Solution(val cards: List<String>, val explored: Int, val programs: Map<String, List<String>> = emptyMap())

    private val ids = level.def.robots.map { it.id }.sorted()
    private val commands: List<CommandType> = level.def.commands.filter {
        !it.isFlow && it != CommandType.SEND && it != CommandType.WAIT_MSG
    }
    private val single = commands.associateWith { Program.compile(listOf(Instr(it))).getOrThrow() }
    private val empty = Program.EMPTY

    /** Her robot için seçenek: bir kart ya da (null) program bitti. */
    private class Node(val state: WorldState, val ended: BooleanArray, val parent: Node?, val choice: Array<CommandType?>?, val cost: Int) {
        fun programs(ids: List<String>): Map<String, List<String>> {
            val out = ids.associateWith { ArrayList<String>() }
            var n: Node? = this
            val chain = ArrayList<Array<CommandType?>>()
            while (n?.choice != null) { chain += n.choice!!; n = n.parent }
            for (c in chain.asReversed()) c.forEachIndexed { i, t -> if (t != null) out.getValue(ids[i]) += t.name }
            return out
        }
    }

    fun solve(maxCards: Int = level.def.maxCommands): Solution? {
        val start = WorldState.initial(level, ids.associateWith { 1 })
        val buckets = ArrayList<ArrayDeque<Node>>()
        fun push(n: Node) { while (buckets.size <= n.cost) buckets += ArrayDeque<Node>(); buckets[n.cost].addLast(n) }
        val best = HashMap<String, Int>()
        val root = Node(start, BooleanArray(ids.size), null, null, 0)
        push(root)
        best[key(start, root.ended)] = 0
        var explored = 0
        var cost = 0
        val options = commands.map<CommandType, CommandType?> { it } + listOf(null)
        while (cost < buckets.size) {
            val node = buckets[cost].removeFirstOrNull() ?: run { cost++; null } ?: continue
            if (node.cost > maxCards) return null
            if ((best[key(node.state, node.ended)] ?: Int.MAX_VALUE) < node.cost) continue
            // Bütün seçenek kombinasyonları
            val n = ids.size
            val idx = IntArray(n)
            while (true) {
                val choice = Array(n) { i -> if (node.ended[i]) null else options[idx[i]] }
                val add = choice.count { it != null }
                val valid = add > 0 && node.cost + add <= maxCards
                if (valid) {
                    explored++
                    if (explored > maxNodes) return null
                    val r = expand(node, choice)
                    if (r != null) {
                        val (child, won) = r
                        if (won) {
                            val progs = child.programs(ids)
                            return Solution(progs.values.flatten(), explored, progs)
                        }
                        val k = key(child.state, child.ended)
                        if ((best[k] ?: Int.MAX_VALUE) > child.cost) { best[k] = child.cost; push(child) }
                    }
                }
                // sonraki kombinasyon (bitmiş robotlar sabit)
                var i = 0
                while (i < n) {
                    if (!node.ended[i] && ++idx[i] < options.size) break
                    idx[i] = 0; i++
                }
                if (i == n) break
            }
        }
        return null
    }

    private fun expand(node: Node, choice: Array<CommandType?>): Pair<Node, Boolean>? {
        val programs = HashMap<String, Program>()
        ids.forEachIndexed { i, id -> programs[id] = choice[i]?.let { single.getValue(it) } ?: empty }
        val sim = Simulation(level, programs)
        val st = node.state.copy()
        st.robots.forEach { it.pc = 0; it.loopLeft = IntArray(1); if (it.status == RobotStatus.DONE) it.status = RobotStatus.RUNNING }
        sim.replaceState(st)
        val res = sim.step()
        if (res.events.any { it is SimEvent.Bumped || it is SimEvent.CommandFailed || it is SimEvent.OutOfEnergy }) return null
        val out = res.outcome
        val ended = BooleanArray(ids.size) { node.ended[it] || choice[it] == null }
        val child = Node(sim.state, ended, node, choice, node.cost + choice.count { it != null })
        if (out is Outcome.Win) return child to true
        if (out is Outcome.Fail && (out.reason == FailReason.ROBOT_LOST || out.reason == FailReason.LASER)) return null
        if (ended.all { it }) {
            // Bütün programlar bitti: bantlar hâlâ taşıyorsa sonuç ortaya çıkana kadar sürdür.
            if (out != null || !level.hasConveyors) return null
            return if (sim.runToEnd() is Outcome.Win) child to true else null
        }
        return child to false
    }

    /** Eşdeğerlik anahtarı: adım sayısı ve istatistikler hariç dünya durumu. */
    private fun key(s: WorldState, ended: BooleanArray): String = buildString {
        s.robots.forEachIndexed { i, r ->
            append(r.pos.x).append(',').append(r.pos.y).append(r.dir).append(r.energy).append(r.carrying).append(r.status)
            append(if (ended[i]) 'E' else 'R').append(if (r.arrivedTick >= 0) 'A' else '-').append('|')
        }
        append(s.boxes.sortedWith(WorldState.posOrder)).append(s.cells.keys.sortedWith(WorldState.posOrder))
        append(s.filledPits.sortedWith(WorldState.posOrder)).append(s.toggled.sorted()).append(s.timers.toSortedMap())
        append(s.socketCharge.toSortedMap(WorldState.posOrder)).append(s.openDoors.sortedWith(WorldState.posOrder))
    }
}
