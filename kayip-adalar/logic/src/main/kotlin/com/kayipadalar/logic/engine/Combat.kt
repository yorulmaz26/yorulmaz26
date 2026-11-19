package com.kayipadalar.logic.engine

import com.kayipadalar.logic.model.*

/** Tur tabanlı taktiksel savaş, düşman yapay zekâsı ve patron yetenekleri. */
object Combat {

    // ---------- Hasar ----------

    fun damage(b: Board, u: Troop, amount: Int, ev: MutableList<GameEvent>, source: String) {
        if (!u.alive || amount <= 0) return
        u.hp -= amount
        ev.add(GameEvent.Attack(source, u.id, amount, u.islandId))
        if (u.type == UnitType.BOSS) b.bossDamageTaken += amount
        if (u.hp <= 0) kill(b, u, ev)
    }

    fun kill(b: Board, u: Troop, ev: MutableList<GameEvent>) {
        if (!u.alive) return
        u.alive = false; u.hp = 0
        if (u.owner == Owner.ENEMY) b.enemiesDefeated++
        ev.add(GameEvent.UnitDied(u.id, u.islandId))
    }

    fun damageBoss(b: Board, amount: Int, ev: MutableList<GameEvent>) {
        val boss = b.bossUnit ?: return
        if (boss.alive) damage(b, boss, amount, ev, "seal")
    }

    private fun islandOf(b: Board, u: Troop) = b.island(u.islandId)

    fun computeDamage(b: Board, att: Troop, tgt: Troop): Int {
        val ai = islandOf(b, att); val ti = islandOf(b, tgt)
        var atk = att.atk
        var def = if (att.type == UnitType.MAGE) 0 else tgt.def
        if (ai != null && ti != null) {
            if (ai.height > ti.height) atk += 1
            if (ti.height > ai.height) def += 1
            if (ai.type == IslandType.MILITARY && att.owner == Owner.PLAYER) atk += 1
            if (ti.type == IslandType.FORT) def += 2
        }
        return maxOf(1, atk - def)
    }

    fun strike(b: Board, att: Troop, tgt: Troop, ev: MutableList<GameEvent>) {
        val dmg = computeDamage(b, att, tgt)
        if (!tgt.alive) return
        tgt.hp -= dmg
        ev.add(GameEvent.Attack(att.id, tgt.id, dmg, tgt.islandId))
        if (tgt.type == UnitType.BOSS) b.bossDamageTaken += dmg
        if (tgt.hp <= 0) kill(b, tgt, ev)
    }

    private fun idx(b: Board, id: String) = b.islands.indexOfFirst { it.id == id }

    /** Menzil içindeki karşı taraf birimleri (bağlantı adımlarıyla). */
    fun targetsInRange(b: Board, g: IntArray, u: Troop, extraRange: Int = 0): List<Troop> {
        val from = idx(b, u.islandId); if (from < 0) return emptyList()
        val ai = b.islands[from]
        var range = u.range + extraRange
        if (u.type == UnitType.ARCHER && ai.height >= 1) range += 1
        val d = Rules.hopDistances(b, g, from, u.type == UnitType.FLYER)
        return b.units.filter { o ->
            o.alive && o.owner != u.owner && o.owner != Owner.NEUTRAL &&
                idx(b, o.islandId).let { it >= 0 && d[it] <= range }
        }
    }

    // ---------- Oyuncu birimleri (otomatik savunma ateşi) ----------

    fun playerPhase(b: Board, ev: MutableList<GameEvent>) {
        val g = Rules.grid(b)
        for (u in b.units.toList()) {
            if (!u.alive || u.owner != Owner.PLAYER) continue
            if (u.stun > 0) { u.stun--; u.acted = false; continue }
            if (u.acted) { u.acted = false; continue }
            val t = targetsInRange(b, g, u).minByOrNull { it.hp } ?: continue
            strike(b, u, t, ev)
        }
    }

    // ---------- Düşman yapay zekâsı ----------

    fun enemyPhase(b: Board, ev: MutableList<GameEvent>) {
        val behavior = b.aiBehavior
        when (behavior) {
            AiBehavior.TRAPPER -> if (b.turn % b.cfg.rules.trapperEvery == 0) trapperTrick(b, ev)
            AiBehavior.RESOURCER -> resourcerGrowth(b, ev)
            else -> {}
        }
        for (u in b.units.toList()) {
            if (!u.alive || u.owner != Owner.ENEMY || u.type == UnitType.BOSS) continue
            if (u.stun > 0) { u.stun--; continue }
            val mode = when (behavior) {
                AiBehavior.RESOURCER -> if (b.turn >= 6) AiBehavior.AGGRESSIVE else AiBehavior.DEFENSIVE
                AiBehavior.TRAPPER -> AiBehavior.DEFENSIVE
                else -> behavior
            }
            actUnit(b, u, mode, ev)
        }
    }

    private fun castleIdx(b: Board) = Rules.castleIndices(b)

    private fun tryAttack(b: Board, g: IntArray, u: Troop, ev: MutableList<GameEvent>, strategist: Boolean): Boolean {
        val targets = targetsInRange(b, g, u)
        if (targets.isNotEmpty()) {
            val t = if (strategist) targets.maxByOrNull { priority(b, u, it) }!! else targets.minByOrNull { it.hp }!!
            strike(b, u, t, ev)
            return true
        }
        // Kaleye saldırı
        val from = idx(b, u.islandId)
        val d = Rules.hopDistances(b, g, from, u.type == UnitType.FLYER)
        for (c in castleIdx(b)) {
            val castle = b.islands[c]
            if (d[c] <= u.range && b.unitOn(castle.id)?.owner != Owner.PLAYER) {
                val dmg = if (u.type == UnitType.SIEGE) u.atk * 2 else u.atk
                b.castleHp -= dmg
                ev.add(GameEvent.CastleHit(u.id, dmg))
                return true
            }
        }
        return false
    }

    private fun priority(b: Board, u: Troop, t: Troop): Int {
        val dmg = computeDamage(b, u, t)
        var p = dmg * 10 - t.hp
        if (dmg >= t.hp) p += 100
        if (t.type == UnitType.HEALER || t.type == UnitType.ARCHER || t.type == UnitType.MAGE) p += 15
        return p
    }

    /** Hedeflere (kale + oyuncu birimleri) birim içermeyen adalar üzerinden mesafe alanı. */
    private fun distanceField(b: Board, g: IntArray, flyer: Boolean): IntArray {
        val dist = IntArray(b.islands.size) { Int.MAX_VALUE }
        val q = ArrayDeque<Int>()
        for (c in castleIdx(b)) { dist[c] = 0; q.add(c) }
        b.units.filter { it.alive && it.owner == Owner.PLAYER }.forEach { pu ->
            val i = idx(b, pu.islandId); if (i >= 0 && dist[i] != 0) { dist[i] = 0; q.add(i) }
        }
        while (q.isNotEmpty()) {
            val i = q.removeFirst()
            for (j in Rules.links(b, g, i, flyer)) {
                if (dist[j] != Int.MAX_VALUE) continue
                dist[j] = dist[i] + 1
                if (b.unitOn(b.islands[j].id) == null) q.add(j)
            }
        }
        return dist
    }

    private fun freeNeighbors(b: Board, g: IntArray, u: Troop): List<Int> {
        val from = idx(b, u.islandId)
        if (from < 0) return emptyList()
        return Rules.links(b, g, from, u.type == UnitType.FLYER).filter { b.unitOn(b.islands[it].id) == null && !b.islands[it].hidden }
    }

    private fun moveTo(b: Board, u: Troop, j: Int, ev: MutableList<GameEvent>) {
        val from = u.islandId
        u.islandId = b.islands[j].id
        ev.add(GameEvent.UnitMoved(u.id, from, u.islandId))
    }

    private fun actUnit(b: Board, u: Troop, mode: AiBehavior, ev: MutableList<GameEvent>) {
        var g = Rules.grid(b)
        val strategist = mode == AiBehavior.STRATEGIST
        if (tryAttack(b, g, u, ev, strategist)) return
        repeat(maxOf(1, u.moves)) {
            if (!u.alive) return
            g = Rules.grid(b)
            val choice: Int? = when (mode) {
                AiBehavior.AGGRESSIVE -> {
                    val df = distanceField(b, g, u.type == UnitType.FLYER)
                    val cur = df[idx(b, u.islandId)]
                    freeNeighbors(b, g, u).filter { df[it] < cur }.minByOrNull { df[it] }
                }
                AiBehavior.DEFENSIVE -> {
                    val home = b.homes[u.id]?.let { idx(b, it) } ?: -1
                    val d = if (home >= 0) Rules.hopDistances(b, g, home) else null
                    val threat = d != null && b.units.any { p -> p.alive && p.owner == Owner.PLAYER && idx(b, p.islandId).let { it >= 0 && d[it] <= 2 } }
                    if (threat) {
                        val df = distanceField(b, g, u.type == UnitType.FLYER)
                        val cur = df[idx(b, u.islandId)]
                        freeNeighbors(b, g, u).filter { df[it] < cur && d!![it] <= 2 }.minByOrNull { df[it] }
                    } else if (home >= 0 && b.islands[home].id != u.islandId) {
                        val dh = Rules.hopDistances(b, g, home) { b.unitOn(b.islands[it].id) == null }
                        val cur = dh[idx(b, u.islandId)]
                        freeNeighbors(b, g, u).filter { dh[it] < cur }.minByOrNull { dh[it] }
                    } else null
                }
                AiBehavior.STRATEGIST -> strategistChoice(b, g, u)
                else -> null
            }
            if (choice == null) return
            moveTo(b, u, choice, ev)
            if (tryAttack(b, Rules.grid(b), u, ev, strategist)) return
        }
    }

    /** Stratejist: her aday konumu saldırı fırsatı, tehlike ve kaleye mesafeye göre puanlar. */
    private fun strategistChoice(b: Board, g: IntArray, u: Troop): Int? {
        val df = distanceField(b, g, u.type == UnitType.FLYER)
        val curIdx = idx(b, u.islandId)
        fun score(j: Int): Int {
            val saved = u.islandId
            u.islandId = b.islands[j].id
            val g2 = Rules.grid(b)
            val opp = targetsInRange(b, g2, u).maxOfOrNull { priority(b, u, it) } ?: 0
            val danger = b.units.filter { it.alive && it.owner == Owner.PLAYER }.sumOf { p ->
                if (targetsInRange(b, g2, p).any { it.id == u.id }) computeDamage(b, p, u) else 0
            }
            u.islandId = saved
            val dist = if (df[j] == Int.MAX_VALUE) 30 else df[j]
            val lethal = if (danger >= u.hp) 60 else 0
            return opp * 2 - danger * 3 - lethal - dist * 4
        }
        val stay = score(curIdx)
        val best = freeNeighbors(b, g, u).maxByOrNull { score(it) } ?: return null
        return if (score(best) > stay) best else null
    }

    /** Tuzakçı: kale ağını en çok küçültecek tarafsız adayı döndürür ya da son hareket ettirilen adayı sabitler. */
    private fun trapperTrick(b: Board, ev: MutableList<GameEvent>) {
        if (b.units.none { it.alive && it.owner == Owner.ENEMY }) return
        val g = Rules.grid(b)
        val net = Rules.castleNetwork(b, g)
        val baseSize = net.count { it }
        var bestId: String? = null
        var bestSize = baseSize
        for ((i, isl) in b.islands.withIndex()) {
            if (!net[i] || !isl.movable || isl.type == IslandType.CASTLE || isl.owner != Owner.NEUTRAL) continue
            if (b.unitOn(isl.id) != null) continue
            val old = isl.ports
            isl.ports = Ports.rotateCw(old)
            val size = Rules.castleNetwork(b).count { it }
            isl.ports = old
            if (size < bestSize) { bestSize = size; bestId = isl.id }
        }
        if (bestId != null) {
            val isl = b.island(bestId)!!
            isl.ports = Ports.rotateCw(isl.ports)
            ev.add(GameEvent.EnemyTrick("ROTATE", isl.id))
            return
        }
        val last = b.lastMovedId?.let { b.island(it) }
        if (last != null && last.alive && last.type != IslandType.CASTLE && last.anchored == 0) {
            if (b.units.any { it.alive && it.owner == Owner.PLAYER && it.type == UnitType.GUARDIAN && it.islandId == last.id }) return
            last.anchored = 2
            ev.add(GameEvent.EnemyTrick("ANCHOR", last.id))
        }
    }

    /** Kaynakçı: üssü üretim adasına bağlıysa düzenli olarak yeni asker üretir. */
    private fun resourcerGrowth(b: Board, ev: MutableList<GameEvent>) {
        val r = b.cfg.rules
        if (b.turn % r.resourcerSpawnEvery != 0) return
        if (b.units.count { it.alive && it.owner == Owner.ENEMY } >= r.resourcerMaxUnits) return
        val g = Rules.grid(b)
        for ((i, fort) in b.islands.withIndex()) {
            if (!fort.alive || fort.type != IslandType.ENEMY_FORT) continue
            val nb = Rules.links(b, g, i)
            if (nb.none { b.islands[it].type.isProducer }) continue
            val spot = (listOf(i) + nb).firstOrNull { b.unitOn(b.islands[it].id) == null } ?: continue
            val st = b.cfg.stats(UnitType.SWORD)
            val id = "e_spawn${b.unitCounter++}"
            val bonus = b.turn / 6
            b.units.add(Troop(id, UnitType.SWORD, Owner.ENEMY, b.islands[spot].id, st.hp, st.hp, st.atk + bonus, st.def, st.range, st.moves))
            b.homes[id] = fort.id
            ev.add(GameEvent.UnitSpawned(id, b.islands[spot].id))
            return
        }
    }

    // ---------- Patron ----------

    fun bossPhase(b: Board, ev: MutableList<GameEvent>) {
        val def = b.def.boss ?: return
        val boss = b.bossUnit ?: return
        if (!boss.alive) return
        if (boss.stun > 0) { boss.stun--; return }
        val g = Rules.grid(b)
        tryAttack(b, g, boss, ev, true)
        if (b.turn % def.every != 0) return
        val pct = boss.hp * 100 / maxOf(1, boss.maxHp)
        val phase = def.phases.filter { pct <= it.hpBelowPct }.minByOrNull { it.hpBelowPct } ?: def.phases.firstOrNull() ?: return
        if (phase.abilities.isEmpty()) return
        val ability = phase.abilities[(b.turn / def.every) % phase.abilities.size]
        useAbility(b, ability, ev)
    }

    private fun candidates(b: Board): List<Island> = b.islands.filter {
        it.alive && it.movable && it.type != IslandType.CASTLE && it.owner != Owner.ENEMY && it.type != IslandType.ENEMY_FORT &&
            b.units.none { u -> u.alive && u.owner == Owner.PLAYER && u.type == UnitType.GUARDIAN && u.islandId == it.id }
    }

    fun useAbility(b: Board, ability: String, ev: MutableList<GameEvent>) {
        val c = candidates(b)
        fun pick(list: List<Island>) = if (list.isEmpty()) null else list[b.rng.nextInt(list.size)]
        when (ability) {
            "LOCK" -> pick(c)?.let { it.anchored = 2; ev.add(GameEvent.BossAbility(ability, it.id)) }
            "FREEZE" -> pick(c)?.let { it.frozen = 2; ev.add(GameEvent.BossAbility(ability, it.id)) }
            "BURN" -> {
                val net = Rules.castleNetwork(b)
                pick(c.filter { net[b.islands.indexOf(it)] })?.let { it.ports = Ports.rotateCw(it.ports); ev.add(GameEvent.BossAbility(ability, it.id)) }
            }
            "FOG" -> {
                val net = Rules.castleNetwork(b)
                val far = c.filter { isl -> !net[b.islands.indexOf(isl)] && Dir.entries.none { d -> b.islandAt(isl.pos + d)?.let { o -> net[b.islands.indexOf(o)] } == true } }
                pick(far)?.let { it.hidden = true; ev.add(GameEvent.BossAbility(ability, it.id)) }
            }
            "PUSH" -> pick(c)?.let { isl ->
                val dirs = Dir.entries.filter { b.isFree(isl.pos + it) }
                if (dirs.isNotEmpty()) {
                    val d = dirs[b.rng.nextInt(dirs.size)]
                    val from = isl.pos; isl.pos = isl.pos + d
                    ev.add(GameEvent.IslandMoved(isl.id, from, isl.pos, pushed = true))
                    ev.add(GameEvent.BossAbility(ability, isl.id))
                }
            }
            "REWIND" -> {
                val id = b.lastMovedId ?: return
                val isl = b.island(id) ?: return
                val prev = b.lastMovedPrevPos ?: return
                if (!isl.alive || isl.fixed) return
                if (prev == isl.pos || b.isFree(prev)) {
                    val from = isl.pos
                    isl.pos = prev; isl.ports = b.lastMovedPrevPorts
                    ev.add(GameEvent.IslandMoved(isl.id, from, prev, pushed = true))
                    ev.add(GameEvent.BossAbility(ability, isl.id))
                    b.lastMovedId = null
                }
            }
            "TELEPORT" -> {
                val us = b.units.filter { it.alive && it.owner == Owner.PLAYER }
                if (us.isEmpty()) return
                val u = us[b.rng.nextInt(us.size)]
                val spots = b.islands.filter { it.alive && !it.hidden && b.unitOn(it.id) == null && it.owner != Owner.ENEMY }
                pick(spots)?.let { val from = u.islandId; u.islandId = it.id; ev.add(GameEvent.UnitMoved(u.id, from, it.id)); ev.add(GameEvent.BossAbility(ability, it.id)) }
            }
            "RESHAPE" -> if (c.size >= 2) {
                val a = c[b.rng.nextInt(c.size)]
                val rest = c.filter { it !== a }
                val o = rest[b.rng.nextInt(rest.size)]
                val p = a.pos; a.pos = o.pos; o.pos = p
                ev.add(GameEvent.IslandMoved(a.id, o.pos, a.pos, true)); ev.add(GameEvent.IslandMoved(o.id, a.pos, o.pos, true))
                ev.add(GameEvent.BossAbility(ability, a.id))
            }
            "SUMMON" -> {
                val boss = b.bossUnit ?: return
                if (b.units.count { it.alive && it.owner == Owner.ENEMY } >= b.cfg.rules.resourcerMaxUnits) return
                val g = Rules.grid(b)
                val bi = idx(b, boss.islandId)
                val spot = Rules.links(b, g, bi).firstOrNull { b.unitOn(b.islands[it].id) == null } ?: return
                val st = b.cfg.stats(UnitType.SWORD)
                val id = "e_sum${b.unitCounter++}"
                b.units.add(Troop(id, UnitType.SWORD, Owner.ENEMY, b.islands[spot].id, st.hp, st.hp, st.atk, st.def, st.range, st.moves))
                b.homes[id] = boss.islandId
                ev.add(GameEvent.UnitSpawned(id, b.islands[spot].id)); ev.add(GameEvent.BossAbility(ability, b.islands[spot].id))
            }
        }
    }
}
