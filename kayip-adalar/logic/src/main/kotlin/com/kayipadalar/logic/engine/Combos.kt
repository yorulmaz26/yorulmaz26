package com.kayipadalar.logic.engine

import com.kayipadalar.logic.model.*

/** Element kombinasyonları ve aynı tür ada zincirleri (rezonans / zincirleme reaksiyon). */
object Combos {
    const val RESONANCE = "RESONANCE"
    const val CHAIN_REACTION = "CHAIN_REACTION"

    private val nonResonant = setOf(IslandType.CASTLE, IslandType.BURNT, IslandType.ENEMY_FORT, IslandType.EXIT, IslandType.VILLAGE)

    fun onBridge(b: Board, a: Island, c: Island, ev: MutableList<GameEvent>) {
        val combo = b.cfg.comboFor(a.type, c.type) ?: return
        b.combosTriggered.add(combo.id)
        ev.add(GameEvent.Combo(combo.id, a.id, c.id))
        val first = if (a.type.name == combo.a) a else c
        for (e in combo.effects) applyEffect(b, e.kind, e.value, e.radius, e.res, e.to, listOf(a, c), first, ev)
    }

    fun applyEffect(
        b: Board, kind: String, value: Int, radius: Int, res: String?, to: String?,
        origin: List<Island>, first: Island?, ev: MutableList<GameEvent>,
    ) {
        fun near(isl: Island?) = isl != null && origin.any { it.pos.manhattan(isl.pos) <= radius }
        val bonus = b.mods.comboDamage
        when (kind) {
            "ADD_MOVES" -> { b.moveLimit += value; ev.add(GameEvent.Info("info.moves", value)) }
            "ADD_ENERGY" -> b.energy += value
            "ADD_RES" -> res?.let { b.res[Res.valueOf(it).ordinal] += value; ev.add(GameEvent.Produced(Res.valueOf(it), value, origin.first().id)) }
            "DAMAGE_ENEMIES" -> b.units.filter { it.alive && it.owner == Owner.ENEMY && it.type != UnitType.BOSS && near(b.island(it.islandId)) }
                .forEach { Combat.damage(b, it, value + bonus, ev, "combo") }
            "STUN_ENEMIES" -> b.units.filter { it.alive && it.owner == Owner.ENEMY && it.type != UnitType.BOSS && near(b.island(it.islandId)) }
                .forEach { it.stun = maxOf(it.stun, value) }
            "BOSS_DAMAGE" -> Combat.damageBoss(b, value + bonus, ev)
            "HEAL_UNITS" -> b.units.filter { it.alive && it.owner == Owner.PLAYER && near(b.island(it.islandId)) }.forEach {
                val h = minOf(value, it.maxHp - it.hp); if (h > 0) { it.hp += h; ev.add(GameEvent.Healed(it.id, h)) }
            }
            "CASTLE_HEAL" -> b.castleHp = minOf(b.castleMaxHp, b.castleHp + value)
            "TRANSFORM" -> if (first != null && to != null) {
                first.type = IslandType.valueOf(to); ev.add(GameEvent.Transformed(first.id, to))
            }
            "THAW" -> b.islands.filter { it.alive && it.frozen > 0 && near(it) }.forEach { it.frozen = 0; ev.add(GameEvent.Thawed(it.id)) }
            "REVEAL" -> b.islands.filter { it.alive && it.hidden && near(it) }.forEach { it.hidden = false; ev.add(GameEvent.Revealed(it.id)) }
        }
    }

    /** Aynı türden 3 ada bağlantısı rezonans, 4+ zincirleme reaksiyon tetikler (tür başına bir kez). */
    fun checkResonance(b: Board, g: IntArray, ev: MutableList<GameEvent>) {
        val seen = BooleanArray(b.islands.size)
        val r = b.cfg.rules
        for (start in b.islands.indices) {
            val s = b.islands[start]
            if (seen[start] || !s.alive || s.hidden || s.type in nonResonant || s.owner == Owner.ENEMY) continue
            val comp = ArrayList<Int>()
            val q = ArrayDeque<Int>(); q.add(start); seen[start] = true
            while (q.isNotEmpty()) {
                val i = q.removeFirst(); comp.add(i)
                for (j in Rules.physicalNeighbors(b, g, i)) if (!seen[j] && b.islands[j].type == s.type) { seen[j] = true; q.add(j) }
            }
            if (comp.size >= 3 && b.pairsTriggered.add("T3:${s.type}")) {
                b.combosTriggered.add(RESONANCE)
                ev.add(GameEvent.TripleCombo(s.type.name, 3, s.id))
                b.moveLimit += r.tripleBonusMoves
                val res = s.type.producesRes
                if (res != null) b.res[res.ordinal] += r.tripleBonusRes else b.energy += 1
            }
            if (comp.size >= 4 && b.pairsTriggered.add("T4:${s.type}")) {
                b.combosTriggered.add(CHAIN_REACTION)
                ev.add(GameEvent.TripleCombo(s.type.name, comp.size, s.id))
                b.energy += r.chainEnergy
                b.units.filter { it.alive && it.owner == Owner.ENEMY && it.type != UnitType.BOSS }.forEach { Combat.damage(b, it, r.chainDamage, ev, "chain") }
                Combat.damageBoss(b, r.chainDamage * 2, ev)
            }
        }
    }
}
