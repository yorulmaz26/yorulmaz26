package com.kayipadalar.logic.engine

import com.kayipadalar.logic.data.GameConfig
import com.kayipadalar.logic.level.LevelDef

/**
 * Bölüm çözücü. Gerçek kural motorunu (düşman ve patron dahil) kullanarak ışın araması yapar.
 * Bölüm üretici çözülebilirliği bu sınıfla kanıtlar; testler kaydedilen çözümleri yeniden oynatır.
 */
object Solver {

    data class Result(val solution: List<String>, val explored: Int)

    fun heuristic(b: Board): Double {
        var s = 0.0
        for (o in b.def.objectives) s += Objectives.progress(b, o) * 100.0
        val net = Rules.castleNetwork(b)
        s += net.count { it } * 4.0
        s += b.castleHp * 6.0 / maxOf(1, b.castleMaxHp)
        s += b.enemiesDefeated * 6.0
        s += b.units.filter { it.alive && it.owner == com.kayipadalar.logic.model.Owner.PLAYER }.sumOf { it.hp } * 0.5
        s -= b.units.filter { it.alive && it.owner == com.kayipadalar.logic.model.Owner.ENEMY }.sumOf { it.hp } * 0.7
        s += b.energy * 0.3
        s += b.res.sum() * 0.2
        return s
    }

    fun beam(def: LevelDef, cfg: GameConfig, width: Int = 60, maxDepth: Int = def.moveLimit, mods: LevelModifiers = LevelModifiers()): Result? {
        val start = Board.create(def, cfg, mods)
        return beamFrom(start, width, maxDepth)
    }

    fun beamFrom(start: Board, width: Int, maxDepth: Int): Result? {
        data class Node(val b: Board, val path: List<String>, val h: Double)
        var layer = listOf(Node(start, emptyList(), heuristic(start)))
        val seen = HashSet<String>()
        seen.add(start.stateKey())
        var explored = 0
        for (depth in 0 until maxDepth) {
            val next = ArrayList<Node>()
            for (n in layer) {
                for (a in Rules.legalActions(n.b)) {
                    val c = n.b.copy()
                    val r = Rules.apply(c, a)
                    if (!r.ok) continue
                    explored++
                    if (c.status == Status.WON) return Result(n.path + a.encode(), explored)
                    if (c.status == Status.LOST) continue
                    if (!seen.add(c.stateKey() + "#" + c.turn % 6)) continue
                    next.add(Node(c, n.path + a.encode(), heuristic(c)))
                }
            }
            if (next.isEmpty()) return null
            next.sortByDescending { it.h }
            layer = next.take(width)
        }
        return null
    }

    /** Çözümü gerçek motorla oynatır ve son durumu döndürür. */
    fun replay(def: LevelDef, cfg: GameConfig, solution: List<String>, mods: LevelModifiers = LevelModifiers()): Board {
        val b = Board.create(def, cfg, mods)
        for (s in solution) {
            if (b.status != Status.PLAYING) break
            val r = Rules.apply(b, Action.decode(s))
            if (!r.ok) break
        }
        return b
    }
}
