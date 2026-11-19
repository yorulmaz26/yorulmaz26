package com.kayipadalar.logic.engine

import com.kayipadalar.logic.level.ObjectiveDef
import com.kayipadalar.logic.level.ObjectiveType.*
import com.kayipadalar.logic.model.*

object Objectives {

    fun allSatisfied(b: Board) = b.def.objectives.all { satisfied(b, it) }

    private fun enemiesAlive(b: Board) = b.units.filter { it.alive && it.owner == Owner.ENEMY }

    fun satisfied(b: Board, o: ObjectiveDef): Boolean = progress(b, o) >= 1.0

    /** 0..1 arası ilerleme; arayüz ve çözücü sezgiseli için. */
    fun progress(b: Board, o: ObjectiveDef): Double {
        val g = Rules.grid(b)
        val net by lazy { Rules.castleNetwork(b, g) }
        fun inNet(id: String?): Boolean {
            val i = b.islands.indexOfFirst { it.id == id }
            return i >= 0 && b.islands[i].alive && net[i]
        }
        fun ratio(a: Int, t: Int) = if (t <= 0) 1.0 else minOf(1.0, a.toDouble() / t)
        return when (o.type) {
            CONNECT_ALL -> {
                val all = b.islands.indices.filter { b.islands[it].alive && b.islands[it].owner != Owner.ENEMY }
                ratio(all.count { net[it] }, all.size)
            }
            GATHER -> ratio(b.res[Res.valueOf(o.res ?: "WOOD").ordinal], o.amount)
            PROTECT_CASTLE, SURVIVE -> if (b.castleHp <= 0) 0.0 else ratio(b.turn, o.amount)
            CAPTURE -> {
                val t = b.island(o.target ?: "") ?: return 0.0
                if (t.owner == Owner.PLAYER) 1.0 else {
                    val enemiesThere = if (b.unitOn(t.id)?.owner == Owner.ENEMY) 0.0 else 0.4
                    val pi = b.islands.indexOf(t)
                    val near = b.units.filter { it.alive && it.owner == Owner.PLAYER }.minOfOrNull { u ->
                        val d = Rules.hopDistances(b, g, b.islands.indexOfFirst { it.id == u.islandId }, u.type == UnitType.FLYER)
                        if (d[pi] == Int.MAX_VALUE) 20 else d[pi]
                    } ?: 20
                    minOf(0.95, enemiesThere + 0.5 / (1 + near))
                }
            }
            CONNECT_TARGET -> if (inNet(o.target)) 1.0 else partialConnect(b, o.target)
            RESCUE -> {
                val v = b.islands.filter { it.type == IslandType.VILLAGE }
                ratio(v.count { it.collected }, v.size)
            }
            COLLECT_CRYSTALS -> {
                val all = b.islands.filter { it.type == IslandType.CRYSTAL }
                val need = if (o.amount > 0) o.amount else all.size
                ratio(all.count { it.collected }, need)
            }
            DELIVER -> {
                val t = b.island(o.target ?: "") ?: return 0.0
                val d = t.pos.manhattan(Cell(o.x, o.y))
                if (d == 0) 1.0 else 0.9 / (1 + d)
            }
            TRAP_ENEMY -> {
                val en = enemiesAlive(b).filter { it.type != UnitType.BOSS }
                if (en.isEmpty()) 1.0 else {
                    val castles = Rules.castleIndices(b)
                    val trapped = en.count { u ->
                        val d = Rules.hopDistances(b, g, b.islands.indexOfFirst { it.id == u.islandId }, u.type == UnitType.FLYER)
                        castles.all { d[it] == Int.MAX_VALUE } && b.units.none { p -> p.alive && p.owner == Owner.PLAYER && d[b.islands.indexOfFirst { it.id == p.islandId }] != Int.MAX_VALUE }
                    }
                    ratio(trapped, en.size)
                }
            }
            PORTAL_CHAIN -> {
                val p = b.islands.indices.filter { b.islands[it].alive && b.islands[it].type == IslandType.PORTAL }
                if (p.isEmpty()) 1.0 else {
                    // Portallar yalnızca birbirine değil, en az birinin kaleye fiziksel köprüyle bağlanması gerekir.
                    ratio(p.count { net[it] }, p.size)
                }
            }
            ESCAPE -> {
                val e = b.islands.firstOrNull { it.type == IslandType.EXIT } ?: return 0.0
                if (inNet(e.id)) 1.0 else partialConnect(b, e.id)
            }
            TRADE_ROUTE -> {
                val t = b.islands.indices.firstOrNull { b.islands[it].type == IslandType.TRADE && b.islands[it].alive } ?: return 0.0
                if (!net[t]) return partialConnect(b, b.islands[t].id) * 0.5
                val types = b.islands.indices.filter { net[it] && b.islands[it].type.isProducer && b.islands[it].type != IslandType.TRADE }
                    .map { b.islands[it].type }.toSet().size
                0.5 + 0.5 * ratio(types, maxOf(1, o.amount))
            }
            MAKE_COMBO -> if ((o.combo ?: "") in b.combosTriggered) 1.0 else 0.0
            DEFEAT_BOSS -> {
                val boss = b.bossUnit ?: return 1.0
                if (!boss.alive) 1.0 else 1.0 - boss.hp.toDouble() / boss.maxHp
            }
            DEFEAT_ALL -> {
                val total = b.units.count { it.owner == Owner.ENEMY }
                ratio(b.units.count { it.owner == Owner.ENEMY && !it.alive }, total)
            }
        }
    }

    /** Hedef adanın kale ağına en yakın ızgara mesafesine göre kısmi ilerleme. */
    private fun partialConnect(b: Board, id: String?): Double {
        val t = b.island(id ?: "") ?: return 0.0
        val net = Rules.castleNetwork(b)
        val d = b.islands.indices.filter { net[it] }.minOfOrNull { b.islands[it].pos.manhattan(t.pos) } ?: 10
        return 0.8 / (1 + d)
    }
}
