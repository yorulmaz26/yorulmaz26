package com.emirzinciri.robotkomutan.logic.sim

import com.emirzinciri.robotkomutan.logic.command.CommandType
import com.emirzinciri.robotkomutan.logic.command.CommandType.*
import com.emirzinciri.robotkomutan.logic.command.Instr
import com.emirzinciri.robotkomutan.logic.command.Program
import com.emirzinciri.robotkomutan.logic.grid.Pos
import com.emirzinciri.robotkomutan.logic.level.LevelData
import com.emirzinciri.robotkomutan.logic.level.ObjectiveType
import com.emirzinciri.robotkomutan.logic.level.EnemyType
import com.emirzinciri.robotkomutan.logic.level.RobotClass
import com.emirzinciri.robotkomutan.logic.level.Tile

/**
 * Deterministik komut simülasyon motoru. Grafikten tamamen bağımsızdır.
 *
 * Bir adımın sırası:
 *  1. Zamanlayıcılar bir azalır.
 *  2. Her robot (kimlik sırasıyla) sıradaki kartını alır; akış kartları (Tekrar, Koşul, Blok sonu) adım harcamaz.
 *  3. Enerji ön koşulu kontrol edilir ve enerji düşülür.
 *  4. Dönüşler uygulanır; hareket talepleri toplanır.
 *  5. Hareket çakışmaları çözülür: duvar/kapı/kutu kontrolü, aynı hücreye yönelenler, yer değiştirme,
 *     duran robotun hücresine giriş (zincir halinde sabit noktaya kadar).
 *  6. Hareketler, itme/çekme ve düşmeler uygulanır.
 *  7. Eylemler (al, bırak, bas, topla, aktar, mesaj) hareket sonrası duruma göre robot sırasıyla işlenir.
 *  8. Mesajlar teslim edilir; plakalar, anahtarlar, zamanlayıcılar ve soketlerden kapı durumları hesaplanır.
 *  9. Kazanma/kaybetme koşulları kontrol edilir.
 *
 * Animasyon hızı sonucu etkilemez: motor yalnızca [step] çağrıldığında ilerler.
 */
class Simulation(val level: LevelData, programs: Map<String, Program>) {

    private val programs: Map<String, Program> =
        level.def.robots.associate { it.id to (programs[it.id] ?: Program.EMPTY) }

    var state: WorldState = WorldState.initial(level, this.programs.mapValues { it.value.size })
        private set

    var outcome: Outcome? = null
        private set

    private val history = ArrayList<WorldState>()
    private val lostByLaser = HashSet<String>()
    private val destroyed = HashSet<String>()
    /** Bu adımda bant bir şey taşıdı mı (programlar bitse de dünya hareket ediyor demektir). */
    private var envMoved = false
    private val totalCards = this.programs.values.sumOf { it.cardCount }
    private val energyOn = level.def.energy

    val canStepBack: Boolean get() = history.isNotEmpty()

    fun program(robotId: String): Program = programs.getValue(robotId)

    /** Çözücü ve araçlar için: simülasyonu verilen bir dünya durumundan sürdürür. */
    internal fun replaceState(s: WorldState) {
        state = s
        outcome = null
        history.clear()
    }

    /** Bir mantıksal adım ilerletir. Sonuç belliyse tekrar çağrı bir şey yapmaz. */
    fun step(): StepResult {
        outcome?.let { return StepResult(state.tick, emptyList(), it) }
        history.add(state.copy())
        val events = ArrayList<SimEvent>()
        val s = state
        s.tick++

        // 1. Zamanlayıcılar
        val timerIt = s.timers.entries.iterator()
        while (timerIt.hasNext()) {
            val e = timerIt.next()
            e.setValue(e.value - 1)
            if (e.value <= 0) timerIt.remove()
        }

        val startPos = s.robots.associateWith { it.pos }
        val startBoxes = if (level.hasConveyors) s.boxes.toSet() else emptySet()

        // 2-4. Kart seçimi, enerji, dönüşler ve hareket talepleri
        val moves = ArrayList<MoveIntent>()
        val actions = ArrayList<Pair<RobotState, Int>>()
        for (r in s.robots) {
            r.activePc = -1
            if (r.status != RobotStatus.RUNNING) continue
            val pc = fetch(r, events)
            if (pc < 0) continue
            val ins = programs.getValue(r.id).instrs[pc]
            if (ins.type == WAIT_MSG) {
                if (r.inbox <= 0) {
                    r.waitingForMessage = true
                    r.activePc = pc
                    continue
                }
                r.inbox--
                r.waitingForMessage = false
                events += SimEvent.MessageReceived(r.id)
            }
            if (energyOn && ins.type.energyCost > r.energy) {
                r.status = RobotStatus.NO_ENERGY
                r.activePc = pc
                events += SimEvent.OutOfEnergy(r.id, pc)
                continue
            }
            if (energyOn) {
                r.energy -= ins.type.energyCost
                r.energyUsed += ins.type.energyCost
            }
            r.activePc = pc
            r.pc = pc + 1
            events += SimEvent.Executed(r.id, pc)
            when (ins.type) {
                TURN_LEFT -> { r.dir = r.dir.left(); events += SimEvent.Turned(r.id) }
                TURN_RIGHT -> { r.dir = r.dir.right(); events += SimEvent.Turned(r.id) }
                WAIT, WAIT_MSG -> Unit
                FORWARD -> moves += MoveIntent(r, pc, r.pos + r.dir)
                BACK -> moves += MoveIntent(r, pc, r.pos + r.dir.opposite())
                JUMP -> {
                    if (r.carrying) fail(r, pc, "Kutu taşırken zıplanamaz.", events)
                    else moves += MoveIntent(r, pc, r.pos.step(r.dir, 2), jump = true)
                }
                PUSH -> {
                    val front = r.pos + r.dir
                    if (front !in s.boxes) fail(r, pc, "Önünde itilecek kutu yok.", events)
                    else moves += MoveIntent(r, pc, front, boxFrom = front, boxTo = front + r.dir)
                }
                PULL -> {
                    val front = r.pos + r.dir
                    if (front !in s.boxes && r.type == RobotClass.MAGNET) actions += r to pc
                    else if (front !in s.boxes) fail(r, pc, "Önünde çekilecek kutu yok.", events)
                    else if (r.carrying) fail(r, pc, "Kutu taşırken çekilemez.", events)
                    else moves += MoveIntent(r, pc, r.pos + r.dir.opposite(), boxFrom = front, boxTo = r.pos)
                }
                else -> actions += r to pc
            }
        }

        // 5-6. Hareket çözümü ve uygulaması
        resolveMoves(moves, events)

        // 7. Eylemler
        val sentBy = ArrayList<RobotState>()
        for ((r, pc) in actions) {
            if (!r.alive) continue
            act(r, pc, programs.getValue(r.id).instrs[pc], events, sentBy)
        }

        // 7b. Bantlar: adım başında bantta olup kendi hareket etmeyen robot ve kutular bir hücre taşınır.
        envMoved = false
        if (level.hasConveyors) conveyors(startPos, startBoxes, events)

        // 7c. Düşmanlar (kimlik sırasıyla), ardından kalkanlar bir adım azalır.
        if (s.enemies.isNotEmpty()) enemyTurn(events)
        for (r in s.robots) if (r.shield > 0) r.shield--

        // 8. Mesaj teslimi ve ortam
        for (sender in sentBy) for (o in s.robots) if (o !== sender && o.alive) o.inbox++
        updateDoors(events)
        lasers(events)
        for (r in s.robots) {
            if (!r.alive) continue
            if (level.tile(r.pos) == Tile.EXIT) { if (r.arrivedTick < 0) r.arrivedTick = s.tick } else r.arrivedTick = -1
        }

        // 9. Sonuç
        for (e in events) if (e is SimEvent.RobotLost && e.laser) lostByLaser += e.robot
        outcome = evaluate()
        return StepResult(s.tick, events, outcome)
    }

    /** Sonuç belli olana kadar çalıştırır (en fazla bölümün adım sınırı kadar). */
    fun runToEnd(): Outcome {
        while (outcome == null) step()
        return outcome!!
    }

    /** Son adımı geri alır (zaman geri sarma / adım adım hata ayıklama). */
    fun stepBack(): Boolean {
        if (history.isEmpty()) return false
        state = history.removeAt(history.lastIndex)
        outcome = null
        lostByLaser.clear()
        destroyed.clear()
        return true
    }

    // ---------------------------------------------------------------- kart seçimi

    /** Akış kartlarını çözerek robotun bu adımda çalıştıracağı kartın indeksini döndürür; program bittiyse -1. */
    private fun fetch(r: RobotState, events: MutableList<SimEvent>): Int {
        val prog = programs.getValue(r.id)
        var guard = 0
        while (true) {
            if (++guard > MAX_FLOW_STEPS) {
                // Yalnızca akış kartlarından oluşan sonsuz döngü: robot güvenle durdurulur.
                r.status = RobotStatus.DONE
                events += SimEvent.CommandFailed(r.id, r.pc.coerceIn(0, prog.size - 1), "Bu döngü hiçbir iş yapmıyor.")
                return -1
            }
            if (r.pc >= prog.size) {
                r.status = RobotStatus.DONE
                events += SimEvent.ProgramFinished(r.id)
                return -1
            }
            val ins = prog.instrs[r.pc]
            when (ins.type) {
                REPEAT -> { r.loopLeft[r.pc] = ins.arg; r.pc++ }
                IF_CLEAR, IF_BLOCKED -> {
                    val clear = canEnter(r.pos + r.dir)
                    val run = if (ins.type == IF_CLEAR) clear else !clear
                    r.pc = if (run) r.pc + 1 else prog.blockEnd[r.pc] + 1
                }
                END -> {
                    val open = prog.blockStart[r.pc]
                    if (prog.instrs[open].type == REPEAT) {
                        r.loopLeft[open]--
                        r.pc = if (r.loopLeft[open] > 0) open + 1 else r.pc + 1
                    } else r.pc++
                }
                STOP -> {
                    r.activePc = r.pc
                    r.status = RobotStatus.DONE
                    events += SimEvent.Executed(r.id, r.pc)
                    events += SimEvent.ProgramFinished(r.id)
                    return -1
                }
                else -> return r.pc
            }
        }
    }

    // ---------------------------------------------------------------- hareket

    private class MoveIntent(
        val robot: RobotState,
        val pc: Int,
        val target: Pos,
        val jump: Boolean = false,
        val boxFrom: Pos? = null,
        val boxTo: Pos? = null,
    ) {
        var blocked = false
        var reason = ""
    }

    private fun resolveMoves(moves: List<MoveIntent>, events: MutableList<SimEvent>) {
        if (moves.isEmpty()) return
        val s = state

        // a) Statik engeller (adım başındaki duruma göre)
        for (m in moves) {
            val why = staticBlock(m)
            if (why != null) { m.blocked = true; m.reason = why }
        }
        // b) Aynı hücreye yönelen robotlar ve kutular
        val claims = HashMap<Pos, Int>()
        for (m in moves) if (!m.blocked) {
            claims.merge(m.target, 1, Int::plus)
            m.boxTo?.let { claims.merge(it, 1, Int::plus) }
        }
        for (m in moves) if (!m.blocked) {
            if (claims.getValue(m.target) > 1 || (m.boxTo != null && claims.getValue(m.boxTo) > 1)) {
                m.blocked = true; m.reason = "Başka bir robotla aynı hücreye girmeye çalıştı."
            }
        }
        // c) Dolu hücreye giriş ve yer değiştirme (sabit noktaya kadar)
        val byRobot = moves.associateBy { it.robot }
        var changed = true
        while (changed) {
            changed = false
            for (m in moves) {
                if (m.blocked) continue
                val occ = s.robotAt(m.target) ?: continue
                if (occ === m.robot) continue
                val om = byRobot[occ]
                when {
                    om == null || om.blocked -> {
                        m.blocked = true; m.reason = "Önünde başka bir robot var."; changed = true
                    }
                    om.target == m.robot.pos -> {
                        m.blocked = true; om.blocked = true
                        m.reason = "Robotlar yer değiştiremez."; om.reason = m.reason; changed = true
                    }
                }
            }
        }
        // d) Uygula
        for (m in moves) {
            val r = m.robot
            if (m.blocked) {
                r.bumps++
                events += SimEvent.Bumped(r.id, m.target, m.pc, m.reason)
                continue
            }
            if (m.boxFrom != null && m.boxTo != null) {
                s.boxes.remove(m.boxFrom)
                when (level.tile(m.boxTo)) {
                    Tile.PIT -> if (m.boxTo in s.filledPits) s.boxes.add(m.boxTo) else {
                        s.filledPits.add(m.boxTo); events += SimEvent.PitFilled(m.boxTo)
                    }
                    Tile.VOID -> events += SimEvent.BoxLost(m.boxTo)
                    else -> s.boxes.add(m.boxTo)
                }
                events += SimEvent.BoxMoved(m.boxFrom, m.boxTo)
            }
            val from = r.pos
            r.pos = m.target
            events += SimEvent.Moved(r.id, from, m.target, m.jump)
            if (isHole(m.target) && r.type != RobotClass.FLYER) {
                r.status = RobotStatus.LOST
                events += SimEvent.RobotLost(r.id, m.target)
            }
        }
    }

    /** Hareketin adım başındaki dünyaya göre statik olarak engellenip engellenmediği. */
    private fun staticBlock(m: MoveIntent): String? {
        val s = state
        if (m.jump) {
            val mid = m.robot.pos + m.robot.dir
            val mt = level.tile(mid)
            if (mt == Tile.WALL || mt.isConsole) return "Duvarın üstünden zıplanamaz."
            if (mt.isDoor && mid !in s.openDoors) return "Kapalı kapının üstünden zıplanamaz."
        }
        solidReason(m.target)?.let { return it }
        if (m.target in s.boxes && m.boxFrom != m.target) return "Önünde kutu var. 'İt' veya 'Nesne al' kullan."
        if (s.enemyAt(m.target) != null) return "Önünde düşman var. 'Saldır' kullan."
        if (m.boxTo != null) {
            val bt = level.tile(m.boxTo)
            if (bt == Tile.WALL || bt.isConsole) return "Kutu duvara dayandı."
            if (bt.isDoor && m.boxTo !in s.openDoors) return "Kutu kapalı kapıya dayandı."
            if (m.boxTo in s.boxes) return "Kutunun arkasında başka bir kutu var."
            if (s.enemyAt(m.boxTo) != null) return "Kutunun arkasında düşman var."
            val atBoxTo = s.robotAt(m.boxTo)
            if (atBoxTo != null && atBoxTo !== m.robot) return "Kutunun arkasında bir robot var."
        }
        return null
    }

    /** Hücreye girişi kalıcı olarak engelleyen durum (duvar, panel, kapalı kapı). */
    private fun solidReason(p: Pos): String? {
        val t = level.tile(p)
        return when {
            t == Tile.WALL -> "Duvara çarptı."
            t.isConsole -> "Panelin üstüne çıkılamaz."
            t.isDoor && p !in state.openDoors -> "Kapı kapalı."
            else -> null
        }
    }

    private fun isHole(p: Pos): Boolean {
        val t = level.tile(p)
        return t == Tile.VOID || (t == Tile.PIT && p !in state.filledPits)
    }

    /** "Önü boşsa" koşulu: hücreye güvenle girilebilir mi (çukur ve boşluk güvenli sayılmaz). */
    private fun canEnter(p: Pos): Boolean =
        solidReason(p) == null && p !in state.boxes && state.robotAt(p) == null && state.enemyAt(p) == null && !isHole(p)

    // ---------------------------------------------------------------- eylemler

    private fun fail(r: RobotState, pc: Int, reason: String, events: MutableList<SimEvent>) {
        events += SimEvent.CommandFailed(r.id, pc, reason)
    }

    private fun act(r: RobotState, pc: Int, ins: Instr, events: MutableList<SimEvent>, sentBy: MutableList<RobotState>) {
        val s = state
        val front = r.pos + r.dir
        when (ins.type) {
            PICK -> when {
                r.type == RobotClass.FLYER -> fail(r, pc, "Uçan robot kutu taşıyamaz.", events)
                r.carrying -> fail(r, pc, "Robot zaten bir kutu taşıyor.", events)
                front !in s.boxes -> fail(r, pc, "Önünde alınacak kutu yok.", events)
                else -> { s.boxes.remove(front); r.carrying = true; events += SimEvent.BoxPicked(r.id, front) }
            }
            DROP -> {
                val t = level.tile(front)
                when {
                    !r.carrying -> fail(r, pc, "Robot kutu taşımıyor.", events)
                    solidReason(front) != null -> fail(r, pc, "Kutu buraya bırakılamaz.", events)
                    front in s.boxes || s.robotAt(front) != null -> fail(r, pc, "Önündeki hücre dolu.", events)
                    t == Tile.VOID -> fail(r, pc, "Kutu boşluğa bırakılamaz.", events)
                    t == Tile.PIT && front !in s.filledPits -> {
                        r.carrying = false; s.filledPits.add(front)
                        events += SimEvent.BoxDropped(r.id, front); events += SimEvent.PitFilled(front)
                    }
                    else -> { r.carrying = false; s.boxes.add(front); events += SimEvent.BoxDropped(r.id, front) }
                }
            }
            PRESS -> when (level.tile(front)) {
                Tile.SWITCH -> {
                    val ch = level.channel(front)
                    if (!s.toggled.add(ch)) s.toggled.remove(ch)
                    events += SimEvent.Pressed(r.id, front, ch)
                }
                Tile.TIMER -> {
                    val ch = level.channel(front)
                    s.timers[ch] = level.timerDuration(front)
                    events += SimEvent.Pressed(r.id, front, ch)
                }
                else -> fail(r, pc, "Önünde basılacak düğme yok.", events)
            }
            COLLECT -> {
                val cell = s.cells[r.pos]
                when {
                    cell != null -> {
                        s.cells.remove(r.pos)
                        val gain = if (energyOn) minOf(cell, r.maxEnergy - r.energy) else cell
                        if (energyOn) r.energy += gain
                        events += SimEvent.EnergyCollected(r.id, gain)
                    }
                    level.tile(r.pos) == Tile.CHARGER -> {
                        val gain = r.maxEnergy - r.energy
                        if (energyOn) r.energy = r.maxEnergy
                        events += SimEvent.EnergyCollected(r.id, gain)
                    }
                    else -> fail(r, pc, "Burada toplanacak enerji yok.", events)
                }
            }
            TRANSFER -> {
                val target = s.robotAt(front)
                when {
                    level.tile(front) == Tile.SOCKET -> {
                        val need = level.socketNeed(front) - (s.socketCharge[front] ?: 0)
                        val amount = minOf(TRANSFER_AMOUNT, need, if (energyOn) r.energy else TRANSFER_AMOUNT)
                        if (need <= 0) fail(r, pc, "Soket zaten dolu.", events)
                        else if (amount <= 0) fail(r, pc, "Aktarılacak enerji yok.", events)
                        else {
                            if (energyOn) r.energy -= amount
                            r.energyUsed += amount
                            s.socketCharge[front] = (s.socketCharge[front] ?: 0) + amount
                            events += SimEvent.EnergyTransferred(r.id, front, amount)
                            if (s.socketCharge.getValue(front) >= level.socketNeed(front)) events += SimEvent.SocketPowered(front)
                        }
                    }
                    target != null -> {
                        val amount = minOf(TRANSFER_AMOUNT, r.energy, target.maxEnergy - target.energy)
                        if (amount <= 0) fail(r, pc, "Aktarılacak enerji yok ya da hedef dolu.", events)
                        else {
                            r.energy -= amount; target.energy += amount
                            events += SimEvent.EnergyTransferred(r.id, front, amount)
                        }
                    }
                    else -> fail(r, pc, "Önünde soket veya robot yok.", events)
                }
            }
            SEND -> { sentBy += r; events += SimEvent.MessageSent(r.id) }
            PULL -> magnetPull(r, pc, events)
            ATTACK -> {
                val e = s.enemyAt(front)
                if (r.stats.attack == 0) fail(r, pc, "${r.type.title} saldıramaz; kalkanıyla korur.", events)
                else if (e == null) fail(r, pc, "Önünde saldırılacak düşman yok.", events)
                else hitEnemy(e, r.stats.attack, r.id, events)
            }
            SHIELD -> { r.shield = r.stats.shieldTicks; events += SimEvent.ShieldUp(r.id) }
            else -> error("Beklenmeyen eylem: ${ins.type}")
        }
    }

    // ---------------------------------------------------------------- ortam

    private fun channelActive(ch: Int): Boolean = Environment.channelActive(level, state, ch)

    private val sockets = level.sockets
    private val doors = level.doors
    private val exits = level.exits
    private val targets = level.targets

    /**
     * Bant hareketi: aynı çakışma kuralları (duvar, kapalı kapı, kutu, aynı hücre, duran robot) uygulanır.
     * Bant boşluğa taşırsa robot düşer, kutu kaybolur.
     */
    private fun conveyors(startPos: Map<RobotState, Pos>, startBoxes: Set<Pos>, events: MutableList<SimEvent>) {
        val s = state
        class Carry(val robot: RobotState?, val from: Pos, val to: Pos) { var blocked = false }
        val list = ArrayList<Carry>()
        for (r in s.robots) {
            if (!r.alive || r.pos != startPos[r]) continue
            val d = level.tile(r.pos).conveyorDir ?: continue
            list += Carry(r, r.pos, r.pos + d)
        }
        for (b in s.boxes) {
            if (b !in startBoxes) continue
            val d = level.tile(b).conveyorDir ?: continue
            list += Carry(null, b, b + d)
        }
        if (list.isEmpty()) return
        val moversFrom = list.associateBy { it.from }
        for (c in list) if (solidReason(c.to) != null || s.enemyAt(c.to) != null) c.blocked = true
        val claims = list.filter { !it.blocked }.groupingBy { it.to }.eachCount()
        for (c in list) if (!c.blocked && claims.getValue(c.to) > 1) c.blocked = true
        var changed = true
        while (changed) {
            changed = false
            for (c in list) {
                if (c.blocked) continue
                val occupied = s.robotAt(c.to) != null || c.to in s.boxes
                if (!occupied) continue
                val other = moversFrom[c.to]
                if (other == null || other.blocked || other.to == c.from) { c.blocked = true; changed = true }
            }
        }
        val moving = list.filter { !it.blocked }
        if (moving.isNotEmpty()) envMoved = true
        for (c in moving) if (c.robot == null) s.boxes.remove(c.from)
        for (c in moving) {
            val r = c.robot
            if (r == null) {
                if (level.tile(c.to) == Tile.VOID) events += SimEvent.BoxLost(c.to)
                else if (level.tile(c.to) == Tile.PIT && c.to !in s.filledPits) { s.filledPits += c.to; events += SimEvent.PitFilled(c.to) }
                else s.boxes += c.to
                events += SimEvent.BoxMoved(c.from, c.to)
            } else {
                r.pos = c.to
                events += SimEvent.Moved(r.id, c.from, c.to, conveyor = true)
                if (isHole(c.to) && r.type != RobotClass.FLYER) { r.status = RobotStatus.LOST; events += SimEvent.RobotLost(r.id, c.to) }
            }
        }
    }

    /** Manyetik robot: 2–3 hücre ötedeki ilk kutuyu bir hücre kendine doğru çeker (robot yerinde kalır). */
    private fun magnetPull(r: RobotState, pc: Int, events: MutableList<SimEvent>) {
        val s = state
        var p = r.pos + r.dir
        for (i in 1..3) {
            if (solidReason(p) != null || s.robotAt(p) != null || s.enemyAt(p) != null) break
            if (p in s.boxes) {
                val to = p + r.dir.opposite()
                if (to == r.pos || solidReason(to) != null || to in s.boxes || s.robotAt(to) != null || s.enemyAt(to) != null) break
                s.boxes.remove(p)
                if (level.tile(to) == Tile.PIT && to !in s.filledPits) { s.filledPits += to; events += SimEvent.PitFilled(to) }
                else if (level.tile(to) == Tile.VOID) events += SimEvent.BoxLost(to)
                else s.boxes += to
                events += SimEvent.BoxMoved(p, to)
                return
            }
            p += r.dir
        }
        fail(r, pc, "Mıknatıs menzilinde çekilebilecek kutu yok.", events)
    }

    // ---------------------------------------------------------------- savaş

    private fun hitEnemy(e: EnemyState, dmg: Int, by: String?, events: MutableList<SimEvent>) {
        e.hp = (e.hp - dmg).coerceAtLeast(0)
        events += SimEvent.EnemyHit(e.id, e.pos, dmg, by)
        if (!e.alive) events += SimEvent.EnemyDefeated(e.id, e.pos)
    }

    private fun damage(e: EnemyState, r: RobotState, dmg: Int, ranged: Boolean, events: MutableList<SimEvent>) {
        val blocked = r.shield > 0
        events += SimEvent.EnemyAttack(e.id, r.id, dmg, blocked, ranged)
        if (blocked) return
        r.hp -= dmg
        if (r.hp <= 0) {
            r.hp = 0
            r.status = RobotStatus.LOST
            destroyed += r.id
            events += SimEvent.RobotDestroyed(r.id, r.pos)
        }
    }

    private fun enemyBlocked(p: Pos): Boolean =
        solidReason(p) != null || p in state.boxes || isHole(p) || state.robotAt(p) != null || state.enemyAt(p) != null

    private fun moveEnemy(e: EnemyState, to: Pos, events: MutableList<SimEvent>) {
        events += SimEvent.EnemyMoved(e.id, e.pos, to)
        e.pos = to
    }

    /** Yanındaki robota döner ve saldırır (N, D, G, B sırasıyla ilk bulunan). */
    /** Düşmanların görebildiği robot (görünmez robotlar hariç). */
    private fun visibleRobotAt(p: Pos): RobotState? = state.robotAt(p)?.takeIf { it.type != RobotClass.GHOST }

    private fun attackAdjacent(e: EnemyState, dmg: Int, events: MutableList<SimEvent>): Boolean {
        for (d in com.emirzinciri.robotkomutan.logic.grid.Dir.entries) {
            val r = visibleRobotAt(e.pos + d) ?: continue
            e.dir = d
            damage(e, r, dmg, false, events)
            return true
        }
        return false
    }

    /** En yakın robota bir adım (Manhattan; eşitlikte kimlik sırası; önce farkı büyük eksen, eşitse x). */
    private fun chaseStep(e: EnemyState, events: MutableList<SimEvent>) {
        val target = state.robots.filter { it.alive && it.type != RobotClass.GHOST }
            .minWithOrNull(compareBy({ kotlin.math.abs(it.pos.x - e.pos.x) + kotlin.math.abs(it.pos.y - e.pos.y) }, { it.id })) ?: return
        val dx = target.pos.x - e.pos.x; val dy = target.pos.y - e.pos.y
        val hx = if (dx > 0) com.emirzinciri.robotkomutan.logic.grid.Dir.E else com.emirzinciri.robotkomutan.logic.grid.Dir.W
        val hy = if (dy > 0) com.emirzinciri.robotkomutan.logic.grid.Dir.S else com.emirzinciri.robotkomutan.logic.grid.Dir.N
        val order = when {
            dx == 0 -> listOf(hy)
            dy == 0 -> listOf(hx)
            kotlin.math.abs(dx) >= kotlin.math.abs(dy) -> listOf(hx, hy)
            else -> listOf(hy, hx)
        }
        for (d in order) {
            val to = e.pos + d
            if (!enemyBlocked(to)) { e.dir = d; moveEnemy(e, to, events); return }
        }
    }

    /** Düşman yapay zekâsı: her düşman tanımlı, deterministik bir kurala göre bir eylem yapar. */
    private fun enemyTurn(events: MutableList<SimEvent>) {
        for (e in state.enemies) {
            if (!e.alive) continue
            when (e.type) {
                EnemyType.PATROL -> {
                    val front = e.pos + e.dir
                    val r = visibleRobotAt(front)
                    when {
                        r != null -> damage(e, r, 1, false, events)
                        enemyBlocked(front) -> e.dir = e.dir.opposite()
                        else -> moveEnemy(e, front, events)
                    }
                }
                EnemyType.TURRET -> {
                    var p = e.pos + e.dir
                    for (i in 1..e.range) {
                        val r = visibleRobotAt(p)
                        if (r != null) { damage(e, r, 1, true, events); break }
                        val t = level.tile(p)
                        if (t == Tile.WALL || t.isConsole || (t.isDoor && p !in state.openDoors) || p in state.boxes || state.enemyAt(p) != null) break
                        p += e.dir
                    }
                }
                EnemyType.CHASER -> if (!attackAdjacent(e, 1, events)) chaseStep(e, events)
                EnemyType.BOSS -> {
                    val phase2 = e.hp * 2 <= e.maxHp
                    if (phase2) {
                        if (!attackAdjacent(e, 2, events)) chaseStep(e, events)
                    } else {
                        val r = visibleRobotAt(e.pos + e.dir)
                        if (r != null) damage(e, r, 2, false, events) else e.dir = e.dir.right()
                    }
                }
            }
        }
    }

    /** Işın içinde kalan robotlar kaybolur. */
    private fun lasers(events: MutableList<SimEvent>) {
        if (level.lasers.isEmpty()) return
        val hit = Environment.beams(level, state).values.flatten().toSet()
        for (r in state.robots) if (r.alive && r.pos in hit) {
            r.status = RobotStatus.LOST
            events += SimEvent.RobotLost(r.id, r.pos, laser = true)
        }
        for (e in state.enemies) if (e.alive && e.pos in hit) { e.hp = 0; events += SimEvent.EnemyDefeated(e.id, e.pos) }
    }

    private fun updateDoors(events: MutableList<SimEvent>) {
        val s = state
        val active = HashMap<Int, Boolean>()
        for (d in doors) {
            val on = active.getOrPut(level.channel(d)) { channelActive(level.channel(d)) }
            val shouldOpen = if (level.tile(d) == Tile.DOOR) on else !on
            val isOpen = d in s.openDoors
            if (shouldOpen && !isOpen) {
                s.openDoors.add(d); events += SimEvent.DoorChanged(d, true)
            } else if (!shouldOpen && isOpen && d !in s.boxes && s.robotAt(d) == null) {
                // Kapı üzerinde robot veya kutu varken kapanmaz.
                s.openDoors.remove(d); events += SimEvent.DoorChanged(d, false)
            }
        }
    }

    // ---------------------------------------------------------------- sonuç

    fun stats(): RunStats = RunStats(
        ticks = state.tick,
        energyUsed = state.robots.sumOf { it.energyUsed },
        bumps = state.robots.sumOf { it.bumps },
        cards = totalCards,
    )

    fun objectivesMet(): Boolean = level.def.objectives.all { o ->
        val s = state
        when (o.type) {
            ObjectiveType.REACH_EXIT -> {
                val ids = o.robots.ifEmpty { s.robots.map { it.id } }
                ids.all { id -> val r = s.robot(id); r.alive && r.pos in exits }
            }
            ObjectiveType.BOXES_ON_TARGETS -> targets.all { it in s.boxes }
            ObjectiveType.COLLECT_ALL_ENERGY -> s.cells.isEmpty()
            ObjectiveType.POWER_SOCKETS -> sockets.all { (s.socketCharge[it] ?: 0) >= level.socketNeed(it) }
            ObjectiveType.SYNC_EXIT -> s.robots.all { it.alive && it.pos in exits } && s.robots.map { it.arrivedTick }.distinct().size == 1
            ObjectiveType.DEFEAT_ENEMIES -> s.enemies.none { it.alive }
        }
    }

    private fun evaluate(): Outcome? {
        val s = state
        if (objectivesMet()) return Outcome.Win(stats())
        s.robots.firstOrNull { it.status == RobotStatus.LOST }?.let {
            val byLaser = lostByLaser.contains(it.id)
            if (it.id in destroyed) return Outcome.Fail(FailReason.DESTROYED, "${it.id} robotu düşman saldırısıyla yok edildi.", stats(), it.id, it.activePc)
            return if (byLaser) Outcome.Fail(FailReason.LASER, "${it.id} robotu lazer ışınına girdi.", stats(), it.id, it.activePc)
            else Outcome.Fail(FailReason.ROBOT_LOST, "${it.id} robotu düştü.", stats(), it.id, it.activePc)
        }
        val running = s.robots.filter { it.status == RobotStatus.RUNNING }
        if (running.isEmpty() && envMoved && s.tick < level.def.maxTicks) return null
        if (running.isEmpty()) {
            s.robots.firstOrNull { it.status == RobotStatus.NO_ENERGY }?.let {
                return Outcome.Fail(FailReason.NO_ENERGY, "${it.id} robotunun enerjisi bu komut için yetmedi.", stats(), it.id, it.activePc)
            }
            return Outcome.Fail(FailReason.PROGRAM_ENDED, unmetDetail(), stats())
        }
        if (running.all { it.waitingForMessage && it.inbox == 0 }) {
            val w = running.first()
            return Outcome.Fail(FailReason.DEADLOCK, "Mesaj bekleyen robotlara kimse sinyal göndermiyor.", stats(), w.id, w.activePc)
        }
        if (s.tick >= level.def.maxTicks) return Outcome.Fail(FailReason.TIMEOUT, "${level.def.maxTicks} adım içinde bitmedi.", stats())
        return null
    }

    private fun unmetDetail(): String {
        val s = state
        for (o in level.def.objectives) {
            when (o.type) {
                ObjectiveType.REACH_EXIT -> {
                    val ids = o.robots.ifEmpty { s.robots.map { it.id } }
                    val missing = ids.filter { s.robot(it).pos !in exits }
                    if (missing.isNotEmpty()) return if (s.robots.size == 1) "Robot çıkışa ulaşamadı." else "Çıkışa ulaşamayan: ${missing.joinToString()}"
                }
                ObjectiveType.BOXES_ON_TARGETS -> {
                    val left = targets.count { it !in s.boxes }
                    if (left > 0) return "$left hedefte kutu yok."
                }
                ObjectiveType.COLLECT_ALL_ENERGY -> if (s.cells.isNotEmpty()) return "${s.cells.size} enerji hücresi toplanmadı."
                ObjectiveType.POWER_SOCKETS -> {
                    val left = sockets.count { (s.socketCharge[it] ?: 0) < level.socketNeed(it) }
                    if (left > 0) return "$left soket dolmadı."
                }
                ObjectiveType.DEFEAT_ENEMIES -> {
                    val left = s.enemies.count { it.alive }
                    if (left > 0) return "$left düşman hâlâ ayakta."
                }
                ObjectiveType.SYNC_EXIT -> {
                    if (s.robots.all { it.pos in exits }) return "Robotlar çıkışlara farklı adımlarda vardı; aynı anda varmalılar."
                    return "Bütün robotlar çıkışlara ulaşmalı."
                }
            }
        }
        return "Hedef tamamlanmadı."
    }

    companion object {
        const val TRANSFER_AMOUNT = 3
        const val MAX_FLOW_STEPS = 1000

        /** Programları derleyip simülasyonu hazırlar; hatalı zincir varsa hatayı döndürür. */
        fun create(level: LevelData, tokens: Map<String, List<String>>): Result<Simulation> {
            val programs = HashMap<String, Program>()
            for ((id, list) in tokens) {
                programs[id] = Program.parse(list).getOrElse { return Result.failure(it) }
            }
            return Result.success(Simulation(level, programs))
        }

        fun commandTypeOf(token: String): CommandType = Instr.decode(token).type
    }
}
