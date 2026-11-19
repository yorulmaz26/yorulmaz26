package com.kayipadalar.logic.engine

import com.kayipadalar.logic.level.ObjectiveType
import com.kayipadalar.logic.model.*

/**
 * Ada hareketi, bağlantı ve çevre kuralları. Render katmanından tamamen bağımsızdır.
 * Her geçerli oyuncu eylemi bir tur ilerletir: eylem → çevre → bağlantılar/kombolar →
 * üretim → zafer kontrolü → savaş/düşman → durum etkileri → yenilgi/zafer kontrolü.
 */
object Rules {

    // ---------- Bağlantı (köprü) hesapları ----------

    fun grid(b: Board): IntArray {
        val g = IntArray(b.width * b.height) { -1 }
        b.islands.forEachIndexed { i, isl -> if (isl.alive && b.inBounds(isl.pos)) g[isl.pos.y * b.width + isl.pos.x] = i }
        return g
    }

    private fun at(b: Board, g: IntArray, c: Cell): Int = if (b.inBounds(c)) g[c.y * b.width + c.x] else -1

    fun bridged(a: Island, c: Island, d: Dir): Boolean =
        a.alive && c.alive && !a.hidden && !c.hidden &&
            Ports.has(a.ports, d) && Ports.has(c.ports, d.opposite) &&
            kotlin.math.abs(a.height - c.height) <= 1

    /** Fiziksel köprüler (komşu ve uyumlu bağlantı noktaları). */
    fun physicalNeighbors(b: Board, g: IntArray, idx: Int): List<Int> {
        val a = b.islands[idx]
        if (!a.alive || a.hidden) return emptyList()
        val out = ArrayList<Int>(4)
        for (d in Dir.entries) {
            val j = at(b, g, a.pos + d)
            if (j >= 0 && bridged(a, b.islands[j], d)) out.add(j)
        }
        return out
    }

    /** Fiziksel köprüler + portal bağlantıları. [flyer] için komşu her ada bağlıdır. */
    fun links(b: Board, g: IntArray, idx: Int, flyer: Boolean = false): List<Int> {
        val a = b.islands[idx]
        if (!a.alive || a.hidden) return emptyList()
        val out = ArrayList<Int>(6)
        if (flyer) {
            for (d in Dir.entries) {
                val j = at(b, g, a.pos + d)
                if (j >= 0 && !b.islands[j].hidden) out.add(j)
            }
        } else out.addAll(physicalNeighbors(b, g, idx))
        // Portallar yalnızca en az bir fiziksel köprüye sahipken (enerjili) birbirine bağlanır.
        if (a.type == IslandType.PORTAL && physicalNeighbors(b, g, idx).isNotEmpty()) {
            b.islands.forEachIndexed { j, o ->
                if (j != idx && o.alive && !o.hidden && o.type == IslandType.PORTAL && j !in out &&
                    physicalNeighbors(b, g, j).isNotEmpty()
                ) out.add(j)
            }
        }
        return out
    }

    fun edgeKey(i: Int, j: Int): Long = if (i < j) i.toLong() * 4096 + j else j.toLong() * 4096 + i

    fun computeEdges(b: Board, g: IntArray = grid(b)): Set<Long> {
        val s = HashSet<Long>()
        for (i in b.islands.indices) for (j in physicalNeighbors(b, g, i)) s.add(edgeKey(i, j))
        return s
    }

    fun castleIndices(b: Board): List<Int> =
        b.islands.indices.filter { b.islands[it].alive && b.islands[it].type == IslandType.CASTLE && b.islands[it].owner == Owner.PLAYER }

    /** Kale ağına bağlı adalar (portallar dahil). */
    fun castleNetwork(b: Board, g: IntArray = grid(b)): BooleanArray {
        val seen = BooleanArray(b.islands.size)
        val q = ArrayDeque<Int>()
        for (c in castleIndices(b)) { seen[c] = true; q.add(c) }
        while (q.isNotEmpty()) {
            val i = q.removeFirst()
            for (j in links(b, g, i)) if (!seen[j] && b.islands[j].owner != Owner.ENEMY) { seen[j] = true; q.add(j) }
        }
        return seen
    }

    fun hopDistances(b: Board, g: IntArray, from: Int, flyer: Boolean = false, passable: (Int) -> Boolean = { true }): IntArray {
        val dist = IntArray(b.islands.size) { Int.MAX_VALUE }
        dist[from] = 0
        val q = ArrayDeque<Int>(); q.add(from)
        while (q.isNotEmpty()) {
            val i = q.removeFirst()
            if (i != from && !passable(i)) continue
            for (j in links(b, g, i, flyer)) if (dist[j] == Int.MAX_VALUE) { dist[j] = dist[i] + 1; q.add(j) }
        }
        return dist
    }

    // ---------- Başlatma ----------

    fun initialize(b: Board) {
        val g = grid(b)
        b.edges = computeEdges(b, g)
        for (e in b.edges) b.pairsTriggered.add("E$e")
        reveal(b, ArrayList())
        collect(b, ArrayList(), silent = true)
    }

    // ---------- Oyuncu eylemleri ----------

    private fun fail(msg: String) = ActionResult(false, msg)

    fun validate(b: Board, a: Action): String? {
        if (b.status != Status.PLAYING) return "error.finished"
        if (b.movesLeft <= 0) return "error.noMoves"
        fun movable(id: String): Island? {
            val i = b.island(id) ?: return null
            if (!i.alive || i.owner == Owner.ENEMY || i.type == IslandType.ENEMY_FORT) return null
            return if (i.movable) i else null
        }
        when (a) {
            is Action.Slide -> {
                if (!b.has(Mechanic.SLIDE)) return "error.locked"
                val i = movable(a.id) ?: return "error.notMovable"
                if (i.group != 0) {
                    val members = groupOf(b, i)
                    if (members.any { !it.movable }) return "error.notMovable"
                    if (members.any { m -> val c = m.pos + a.dir; !(b.isFree(c) || members.any { it.pos == c }) }) return "error.blocked"
                } else if (!b.isFree(i.pos + a.dir)) return "error.blocked"
            }
            is Action.Merge -> {
                if (!b.has(Mechanic.MERGE)) return "error.locked"
                val i = movable(a.a) ?: return "error.notMovable"
                val j = movable(a.b) ?: return "error.notMovable"
                if (i.pos.manhattan(j.pos) != 1) return "error.notAdjacent"
                if (i.group != 0 && i.group == j.group) return "error.merged"
            }
            is Action.Split -> {
                if (!b.has(Mechanic.MERGE)) return "error.locked"
                val i = b.island(a.id) ?: return "error.invalid"
                if (i.group == 0) return "error.notMerged"
            }
            is Action.Rotate -> {
                if (!b.has(Mechanic.ROTATE)) return "error.locked"
                movable(a.id) ?: return "error.notMovable"
            }
            is Action.Swap -> {
                if (!b.has(Mechanic.SWAP)) return "error.locked"
                val i = movable(a.a) ?: return "error.notMovable"
                val j = movable(a.b) ?: return "error.notMovable"
                if (i.group != 0 || j.group != 0) return "error.merged"
                if (i.pos.manhattan(j.pos) != 1) return "error.notAdjacent"
                if (b.energy < b.cfg.rules.costs.swapEnergy) return "error.noEnergy"
            }
            is Action.Raise -> {
                if (!b.has(Mechanic.RAISE)) return "error.locked"
                val i = movable(a.id) ?: return "error.notMovable"
                if (a.up && i.height >= b.cfg.rules.maxHeight) return "error.maxHeight"
                if (!a.up && i.height <= 0) return "error.minHeight"
                if (b.energy < b.cfg.rules.costs.raiseEnergy) return "error.noEnergy"
            }
            is Action.Jump -> {
                if (!b.has(Mechanic.JUMP)) return "error.locked"
                val i = movable(a.id) ?: return "error.notMovable"
                if (i.group != 0) return "error.merged"
                if (!b.isFree(i.pos.step(a.dir, 2))) return "error.blocked"
                if (b.energy < b.cfg.rules.costs.jumpEnergy) return "error.noEnergy"
            }
            is Action.MoveUnit -> {
                if (!b.has(Mechanic.UNIT_MOVE)) return "error.locked"
                val u = b.unit(a.unitId) ?: return "error.noUnit"
                if (!u.alive || u.owner != Owner.PLAYER) return "error.noUnit"
                if (u.stun > 0) return "error.stunned"
                if (unitMoveKind(b, u, a.islandId) == null) return "error.unreachable"
            }
            Action.Wait -> if (!b.has(Mechanic.WAIT)) return "error.locked"
        }
        return null
    }

    /** "move" veya "attack" ya da erişilemezse null. */
    fun unitMoveKind(b: Board, u: Troop, targetId: String): String? {
        val g = grid(b)
        val from = b.islands.indexOfFirst { it.id == u.islandId }
        val to = b.islands.indexOfFirst { it.id == targetId }
        if (from < 0 || to < 0 || from == to) return null
        val target = b.islands[to]
        if (!target.alive || target.hidden) return null
        val occupant = b.unitOn(targetId)
        val flyer = u.type == UnitType.FLYER
        if (occupant != null) {
            if (occupant.owner == Owner.PLAYER) return null
            val d = hopDistances(b, g, from, flyer)
            return if (d[to] <= u.range) "attack" else null
        }
        val d = hopDistances(b, g, from, flyer) { b.unitOn(b.islands[it].id) == null }
        return if (d[to] <= u.moves) "move" else null
    }

    fun apply(b: Board, a: Action): ActionResult {
        validate(b, a)?.let { return fail(it) }
        val ev = ArrayList<GameEvent>()
        val costs = b.cfg.rules.costs
        when (a) {
            is Action.Slide -> {
                val i = b.island(a.id)!!
                remember(b, i)
                if (i.group != 0) {
                    for (m in groupOf(b, i)) {
                        val from = m.pos
                        m.pos = m.pos + a.dir
                        ev.add(GameEvent.IslandMoved(m.id, from, m.pos))
                        b.islandsMoved++
                    }
                } else {
                    val from = i.pos
                    i.pos = i.pos + a.dir
                    ev.add(GameEvent.IslandMoved(i.id, from, i.pos))
                    applyWind(b, i, ev)
                    b.islandsMoved++
                }
            }
            is Action.Merge -> {
                val i = b.island(a.a)!!; val j = b.island(a.b)!!
                val target = if (i.group != 0) i.group else if (j.group != 0) j.group else (b.islands.maxOf { it.group } + 1)
                val old = setOf(i.group, j.group).filter { it != 0 && it != target }
                b.islands.forEach { if (it.group in old) it.group = target }
                i.group = target; j.group = target
                ev.add(GameEvent.Merged(i.id, j.id))
            }
            is Action.Split -> {
                val i = b.island(a.id)!!
                val g = i.group
                i.group = 0
                val rest = b.islands.filter { it.group == g }
                if (rest.size < 2) rest.forEach { it.group = 0 }
                ev.add(GameEvent.SplitOff(i.id))
            }
            is Action.Rotate -> {
                val i = b.island(a.id)!!
                remember(b, i)
                i.ports = Ports.rotateCw(i.ports)
                ev.add(GameEvent.IslandRotated(i.id))
                b.rotations++
            }
            is Action.Swap -> {
                val i = b.island(a.a)!!; val j = b.island(a.b)!!
                remember(b, i)
                val p = i.pos; i.pos = j.pos; j.pos = p
                b.energy -= costs.swapEnergy
                ev.add(GameEvent.IslandMoved(i.id, j.pos, i.pos)); ev.add(GameEvent.IslandMoved(j.id, i.pos, j.pos))
                b.islandsMoved += 2
            }
            is Action.Raise -> {
                val i = b.island(a.id)!!
                i.height += if (a.up) 1 else -1
                b.energy -= costs.raiseEnergy
                ev.add(GameEvent.IslandHeight(i.id, i.height))
            }
            is Action.Jump -> {
                val i = b.island(a.id)!!
                remember(b, i)
                val from = i.pos
                i.pos = i.pos.step(a.dir, 2)
                b.energy -= costs.jumpEnergy
                ev.add(GameEvent.IslandMoved(i.id, from, i.pos))
                applyWind(b, i, ev)
                b.islandsMoved++
            }
            is Action.MoveUnit -> {
                val u = b.unit(a.unitId)!!
                if (unitMoveKind(b, u, a.islandId) == "attack") {
                    Combat.strike(b, u, b.unitOn(a.islandId)!!, ev)
                } else {
                    val from = u.islandId
                    u.islandId = a.islandId
                    ev.add(GameEvent.UnitMoved(u.id, from, a.islandId))
                }
                u.acted = true
            }
            Action.Wait -> ev.add(GameEvent.Info("info.wait"))
        }
        b.movesUsed++
        b.turn++
        endTurn(b, ev)
        return ActionResult(true, null, ev)
    }

    fun groupOf(b: Board, i: Island): List<Island> =
        if (i.group == 0) listOf(i) else b.islands.filter { it.alive && it.group == i.group }

    private fun remember(b: Board, i: Island) {
        b.lastMovedId = i.id; b.lastMovedPrevPos = i.pos; b.lastMovedPrevPorts = i.ports
    }

    fun applyWind(b: Board, i: Island, ev: MutableList<GameEvent>) {
        var guard = 0
        while (guard++ < 12) {
            val d = b.wind[i.pos] ?: return
            val next = i.pos + d
            if (!b.isFree(next)) return
            val from = i.pos
            i.pos = next
            ev.add(GameEvent.IslandMoved(i.id, from, next, pushed = true))
        }
    }

    // ---------- Tur sonu ----------

    private fun endTurn(b: Board, ev: MutableList<GameEvent>) {
        applyGravity(b, ev)
        updateWorld(b, ev)
        produce(b, ev)
        if (Objectives.allSatisfied(b)) { b.status = Status.WON; return }

        Combat.playerPhase(b, ev)
        Combat.enemyPhase(b, ev)
        if (b.mods.enemyDoubleTurn) Combat.enemyPhase(b, ev)
        Combat.bossPhase(b, ev)
        updateWorld(b, ev)
        statusEffects(b, ev)
        updateWorld(b, ev)

        checkLose(b)
        if (b.status != Status.PLAYING) return
        if (Objectives.allSatisfied(b)) { b.status = Status.WON; return }
        if (b.movesLeft <= 0) { b.status = Status.LOST; b.loseReason = LoseReason.OUT_OF_MOVES }
    }

    fun checkLose(b: Board) {
        val castles = b.islands.filter { it.type == IslandType.CASTLE && it.owner == Owner.PLAYER }
        if (castles.isNotEmpty() && castles.none { it.alive }) { lose(b, LoseReason.CASTLE_FELL); return }
        if (b.castleHp <= 0) { lose(b, LoseReason.CASTLE_DESTROYED); return }
        if (b.hasObjective(ObjectiveType.ESCAPE) && b.islands.any { it.type == IslandType.EXIT && !it.alive }) {
            lose(b, LoseReason.EXIT_FELL); return
        }
        val needsArmy = b.hasObjective(ObjectiveType.CAPTURE) || b.hasObjective(ObjectiveType.DEFEAT_ALL)
        if (needsArmy && b.units.none { it.alive && it.owner == Owner.PLAYER } && !Objectives.allSatisfied(b)) {
            lose(b, LoseReason.ARMY_LOST)
        }
    }

    private fun lose(b: Board, r: LoseReason) { b.status = Status.LOST; b.loseReason = r }

    fun applyGravity(b: Board, ev: MutableList<GameEvent>) {
        val range = b.cfg.rules.gravityRange
        for (gIsl in b.islands) {
            if (!gIsl.alive || gIsl.type != IslandType.GRAVITY || gIsl.hidden) continue
            for (d in Dir.entries) {
                for (k in 2..range) {
                    val c = gIsl.pos.step(d, k)
                    if (!b.inBounds(c) || c in b.blocked) break
                    val o = b.islandAt(c) ?: continue
                    if (o.movable && o.group == 0 && o.type != IslandType.CASTLE && o.owner != Owner.ENEMY) {
                        val to = gIsl.pos.step(d, k - 1)
                        if (b.isFree(to)) {
                            o.pos = to
                            ev.add(GameEvent.IslandMoved(o.id, c, to, pushed = true))
                        }
                    }
                    break
                }
            }
        }
    }

    /** Köprüleri günceller, kombo/tehlike/keşif/toplama kurallarını uygular. */
    fun updateWorld(b: Board, ev: MutableList<GameEvent>) {
        repeat(3) {
            reveal(b, ev)
            val g = grid(b)
            val newEdges = computeEdges(b, g)
            for (e in newEdges) if (e !in b.edges) {
                val i = (e / 4096).toInt(); val j = (e % 4096).toInt()
                val a = b.islands[i]; val c = b.islands[j]
                ev.add(GameEvent.BridgeFormed(a.id, c.id))
                b.bridgesFormed++
                if (b.pairsTriggered.add("E$e")) Combos.onBridge(b, a, c, ev)
            }
            for (e in b.edges) if (e !in newEdges) {
                val i = (e / 4096).toInt(); val j = (e % 4096).toInt()
                ev.add(GameEvent.BridgeBroken(b.islands[i].id, b.islands[j].id))
            }
            b.edges = newEdges
            hazards(b, g, ev)
            Combos.checkResonance(b, g, ev)
            collect(b, ev, silent = false)
        }
    }

    fun reveal(b: Board, ev: MutableList<GameEvent>) {
        var changed = true
        var guard = 0
        while (changed && guard++ < 10) {
            changed = false
            val g = grid(b)
            val net = castleNetwork(b, g)
            val scouts = b.units.filter { it.alive && it.owner == Owner.PLAYER && it.type == UnitType.SCOUT }.mapNotNull { b.island(it.islandId) }
            val fogLights = b.islands.filterIndexed { i, isl -> net[i] && isl.type == IslandType.FOG }
            for ((idx, h) in b.islands.withIndex()) {
                if (!h.alive || !h.hidden) continue
                val near = Dir.entries.any { d ->
                    val j = at(b, g, h.pos + d)
                    j >= 0 && j != idx && net[j]
                } || scouts.any { it.pos.manhattan(h.pos) <= 2 } || fogLights.any { it.pos.manhattan(h.pos) <= 2 }
                if (near) { h.hidden = false; ev.add(GameEvent.Revealed(h.id)); changed = true }
            }
        }
    }

    private fun hazards(b: Board, g: IntArray, ev: MutableList<GameEvent>) {
        for ((idx, isl) in b.islands.withIndex()) {
            if (!isl.alive) continue
            val nb = physicalNeighbors(b, g, idx).map { b.islands[it] }
            // Yangın: ateş veya volkana bağlı orman/çiftlik, yanında su yoksa yanar.
            if ((isl.type == IslandType.FOREST || isl.type == IslandType.FARM) &&
                nb.any { it.type == IslandType.FIRE || it.type == IslandType.VOLCANO } &&
                nb.none { it.type == IslandType.WATER }
            ) {
                isl.type = IslandType.BURNT
                ev.add(GameEvent.Burned(isl.id))
            }
            // Erime: donmuş ada ateş/volkan bağlantısıyla çözülür.
            if (isl.frozen > 0 && nb.any { it.type == IslandType.FIRE || it.type == IslandType.VOLCANO }) {
                isl.frozen = 0
                ev.add(GameEvent.Thawed(isl.id))
            }
        }
    }

    fun collect(b: Board, ev: MutableList<GameEvent>, silent: Boolean) {
        val g = grid(b)
        val net = castleNetwork(b, g)
        val r = b.cfg.rules
        for ((i, isl) in b.islands.withIndex()) {
            if (!net[i] || isl.collected || !isl.alive) continue
            when (isl.type) {
                IslandType.VILLAGE -> { isl.collected = true; if (!silent) ev.add(GameEvent.Collected(isl.id, "village")) }
                IslandType.CRYSTAL -> {
                    isl.collected = true
                    if (!silent) {
                        ev.add(GameEvent.Collected(isl.id, "crystal"))
                        b.def.boss?.let { if (it.sealDamage > 0) Combat.damageBoss(b, it.sealDamage, ev) }
                    }
                }
                IslandType.TIME -> {
                    isl.collected = true
                    if (!silent) { b.moveLimit += r.timeBonusMoves; ev.add(GameEvent.Collected(isl.id, "time")) }
                }
                IslandType.ANCIENT -> {
                    isl.collected = true
                    if (!silent) {
                        b.energy += r.ancientEnergy
                        b.units.filter { it.alive && it.owner == Owner.PLAYER }.forEach { it.hp = it.maxHp }
                        ev.add(GameEvent.Collected(isl.id, "ancient"))
                        b.def.boss?.let { if (it.sealDamage > 0) Combat.damageBoss(b, it.sealDamage * 2, ev) }
                    }
                }
                else -> {}
            }
        }
    }

    private fun produce(b: Board, ev: MutableList<GameEvent>) {
        val g = grid(b)
        val net = castleNetwork(b, g)
        val producersInNet = b.islands.filterIndexed { i, it -> net[i] && it.alive && it.type.isProducer && it.type != IslandType.TRADE }
        for ((i, isl) in b.islands.withIndex()) {
            if (!net[i] || !isl.alive || isl.owner == Owner.ENEMY) continue
            val r = isl.type.producesRes ?: continue
            var amt = isl.type.produceAmount
            if (isl.type == IslandType.FOREST && physicalNeighbors(b, g, i).any { b.islands[it].type == IslandType.WATER }) amt *= 2
            if (isl.type == IslandType.TRADE && producersInNet.isEmpty()) continue
            b.res[r.ordinal] += amt
            ev.add(GameEvent.Produced(r, amt, isl.id))
            if (isl.type == IslandType.CRYSTAL) b.energy += 1 + b.mods.crystalEnergy
        }
        b.units.filter { it.alive && it.owner == Owner.PLAYER && it.type == UnitType.ENGINEER }.forEach { b.energy += 1 }
    }

    private fun statusEffects(b: Board, ev: MutableList<GameEvent>) {
        val g = grid(b)
        val r = b.cfg.rules
        for ((idx, isl) in b.islands.withIndex()) {
            if (!isl.alive) continue
            val affected = (physicalNeighbors(b, g, idx) + idx).map { b.islands[it].id }
            when (isl.type) {
                IslandType.POISON -> for (id in affected) b.unitOn(id)?.let { Combat.damage(b, it, r.poisonDamage, ev, "poison") }
                IslandType.LIFE -> {
                    for (id in affected) b.unitOn(id)?.let { u ->
                        if (u.owner == Owner.PLAYER && u.hp < u.maxHp) {
                            val h = minOf(r.lifeHeal, u.maxHp - u.hp); u.hp += h; ev.add(GameEvent.Healed(u.id, h))
                        }
                    }
                    if (b.turn % 2 == 0 && affected.any { id -> b.island(id)?.type == IslandType.CASTLE } && b.castleHp < b.castleMaxHp) b.castleHp++
                }
                IslandType.VOLCANO -> if (b.turn % r.volcanoEvery == 0) {
                    ev.add(GameEvent.Eruption(isl.id))
                    for (id in affected) b.unitOn(id)?.let { Combat.damage(b, it, r.volcanoDamage, ev, "volcano") }
                }
                IslandType.ICE -> b.unitOn(isl.id)?.let { if (it.owner == Owner.ENEMY && it.stun == 0) it.stun = 1 }
                else -> {}
            }
        }
        b.units.filter { it.alive && it.owner == Owner.PLAYER && it.type == UnitType.HEALER }.forEach { h ->
            val from = b.islands.indexOfFirst { it.id == h.islandId }
            if (from >= 0) for (j in links(b, g, from)) b.unitOn(b.islands[j].id)?.let { u ->
                if (u.owner == Owner.PLAYER && u.hp < u.maxHp) { u.hp++; ev.add(GameEvent.Healed(u.id, 1)) }
            }
        }
        for (isl in b.islands) {
            if (!isl.alive) continue
            if (isl.frozen > 0) isl.frozen--
            if (isl.anchored > 0) isl.anchored--
            if (isl.crumble > 0) {
                isl.crumble--
                if (isl.crumble == 0) {
                    isl.alive = false
                    isl.group = 0
                    ev.add(GameEvent.Collapsed(isl.id))
                    b.unitOn(isl.id)?.let { Combat.kill(b, it, ev) }
                }
            }
        }
        // Ele geçirme
        for (isl in b.islands) {
            if (isl.alive && isl.type == IslandType.ENEMY_FORT) {
                val u = b.unitOn(isl.id)
                if (u != null && u.owner == Owner.PLAYER) {
                    isl.type = IslandType.FORT; isl.owner = Owner.PLAYER
                    ev.add(GameEvent.Captured(isl.id))
                }
            }
        }
    }

    // ---------- Arama için olası eylemler ----------

    fun legalActions(b: Board): List<Action> {
        if (b.status != Status.PLAYING) return emptyList()
        val out = ArrayList<Action>()
        val e = b.energy
        val c = b.cfg.rules.costs
        for (i in b.islands) {
            if (!i.alive || !i.movable || i.owner == Owner.ENEMY || i.type == IslandType.ENEMY_FORT) continue
            if (b.has(Mechanic.SLIDE)) for (d in Dir.entries) {
                val a = Action.Slide(i.id, d)
                if (if (i.group == 0) b.isFree(i.pos + d) else validate(b, a) == null) out.add(a)
            }
            if (b.has(Mechanic.MERGE)) {
                for (d in listOf(Dir.E, Dir.S)) b.islandAt(i.pos + d)?.let { o -> val m = Action.Merge(i.id, o.id); if (validate(b, m) == null) out.add(m) }
                if (i.group != 0) out.add(Action.Split(i.id))
            }
            if (b.has(Mechanic.ROTATE) && i.ports != 0 && i.ports != 15) out.add(Action.Rotate(i.id))
            if (b.has(Mechanic.RAISE) && e >= c.raiseEnergy) {
                if (i.height < b.cfg.rules.maxHeight) out.add(Action.Raise(i.id, true))
                if (i.height > 0) out.add(Action.Raise(i.id, false))
            }
            if (b.has(Mechanic.JUMP) && e >= c.jumpEnergy) for (d in Dir.entries) if (b.isFree(i.pos.step(d, 2))) out.add(Action.Jump(i.id, d))
            if (b.has(Mechanic.SWAP) && e >= c.swapEnergy) for (d in listOf(Dir.E, Dir.S)) {
                val o = b.islandAt(i.pos + d)
                if (o != null && o.movable && o.owner != Owner.ENEMY && o.type != IslandType.ENEMY_FORT && o.type != i.type) out.add(Action.Swap(i.id, o.id))
            }
        }
        if (b.has(Mechanic.UNIT_MOVE)) for (u in b.units) {
            if (!u.alive || u.owner != Owner.PLAYER || u.stun > 0) continue
            for (t in b.islands) if (t.alive && !t.hidden && t.id != u.islandId && unitMoveKind(b, u, t.id) != null) out.add(Action.MoveUnit(u.id, t.id))
        }
        if (b.has(Mechanic.WAIT)) out.add(Action.Wait)
        return out
    }

    /** Yıldız sayısı: savunma bölümlerinde kale canına, diğerlerinde hamle sayısına göre. */
    fun stars(b: Board): Int {
        if (b.status != Status.WON) return 0
        val hpMode = b.hasObjective(ObjectiveType.SURVIVE) || b.hasObjective(ObjectiveType.PROTECT_CASTLE)
        return if (hpMode) {
            val pct = b.castleHp * 100 / maxOf(1, b.castleMaxHp)
            when { pct >= b.def.star3 -> 3; pct >= b.def.star2 -> 2; else -> 1 }
        } else when {
            b.movesUsed <= b.def.star3 -> 3
            b.movesUsed <= b.def.star2 -> 2
            else -> 1
        }
    }
}
