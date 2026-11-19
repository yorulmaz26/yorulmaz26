package com.emirzinciri.robotkomutan.play

import com.emirzinciri.robotkomutan.logic.command.CommandType
import com.emirzinciri.robotkomutan.logic.command.Instr
import com.emirzinciri.robotkomutan.logic.command.Program
import com.emirzinciri.robotkomutan.logic.command.ProgramException
import com.emirzinciri.robotkomutan.logic.level.LevelData
import com.emirzinciri.robotkomutan.logic.sim.Outcome
import com.emirzinciri.robotkomutan.logic.sim.SimEvent
import com.emirzinciri.robotkomutan.logic.sim.Simulation
import com.emirzinciri.robotkomutan.logic.sim.StepResult
import com.emirzinciri.robotkomutan.logic.sim.WorldState

/**
 * Bir bölüm oturumu: komut zincirlerini düzenleme, simülasyonu oynatma, duraklatma, tek adım ve geri adım.
 * Görsel sistemden bağımsızdır; ekran yalnızca bu sınıfın durumunu çizer.
 */
class PlaySession(val level: LevelData, initial: Map<String, List<String>>) {

    enum class Mode { EDIT, RUNNING, PAUSED, DONE }

    val robotIds: List<String> = level.def.robots.map { it.id }.sorted()
    val programs: Map<String, MutableList<Instr>> = robotIds.associateWith { id ->
        initial[id].orEmpty().mapNotNull { runCatching { Instr.decode(it) }.getOrNull() }
            .filter { it.type in level.def.commands || it.type == CommandType.END }
            .toMutableList()
    }

    var mode = Mode.EDIT; private set
    var sim: Simulation? = null; private set
    /** Bir önceki adımın anlık görüntüsü (çizimde ara değer için). */
    var from: WorldState = WorldState.initial(level, robotIds.associateWith { 0 }); private set
    val current: WorldState get() = sim?.state ?: from
    var lastEvents: List<SimEvent> = emptyList(); private set
    /** Adım animasyonundaki ilerleme 0..1. */
    var t = 1f; private set
    /** Simülasyon sonucu belli; animasyon bitince gösterilir. */
    var pendingOutcome: Outcome? = null; private set
    var shownOutcome: Outcome? = null; private set
    /** Son hatalı kartlar: robot -> kart indeksleri (çarpma ve başarısız komutlar). */
    val errorCards = HashMap<String, MutableSet<Int>>()
    var compileError: Pair<String, ProgramException>? = null; private set
    var stepping = false

    var listener: ((StepResult) -> Unit)? = null

    val totalCards: Int get() = programs.values.sumOf { p -> p.count { it.type != CommandType.END } }
    fun cardsOf(robot: String) = programs.getValue(robot).count { it.type != CommandType.END }
    val maxCards: Int get() = level.def.maxCommands
    val canAddCard: Boolean get() = totalCards < maxCards

    fun tokens(): Map<String, List<String>> = programs.mapValues { (_, p) -> p.map { it.encode() } }

    // ------------------------------------------------------------------ düzenleme

    /** Herhangi bir düzenleme çalışan simülasyonu sıfırlar. */
    private fun edited() {
        if (mode != Mode.EDIT) reset()
        errorCards.clear()
        compileError = null
    }

    fun insert(robot: String, index: Int, ins: Instr): Boolean {
        if (ins.type != CommandType.END && !canAddCard) return false
        edited()
        val p = programs.getValue(robot)
        p.add(index.coerceIn(0, p.size), ins)
        return true
    }

    /** Blok açan kart eklenince eşleşen 'Blok sonu' da eklenir (sınırdan sayılmaz). */
    fun insertWithBlock(robot: String, index: Int, ins: Instr): Boolean {
        if (!insert(robot, index, ins)) return false
        if (ins.type.opensBlock) programs.getValue(robot).add((index + 1).coerceAtMost(programs.getValue(robot).size), Instr(CommandType.END))
        return true
    }

    fun remove(robot: String, index: Int): Instr? {
        val p = programs.getValue(robot)
        if (index !in p.indices) return null
        edited()
        val removed = p.removeAt(index)
        // Bloğu açan kart silinirse eşleşen 'Blok sonu' da silinir.
        if (removed.type.opensBlock) {
            var depth = 0
            for (i in index until p.size) {
                val t = p[i].type
                if (t.opensBlock) depth++
                if (t == CommandType.END) { if (depth == 0) { p.removeAt(i); break } else depth-- }
            }
        }
        return removed
    }

    fun move(robot: String, from: Int, to: Int) {
        val p = programs.getValue(robot)
        if (from !in p.indices) return
        edited()
        val ins = p.removeAt(from)
        p.add(to.coerceIn(0, p.size), ins)
    }

    fun duplicate(robot: String, index: Int): Boolean {
        val p = programs.getValue(robot)
        if (index !in p.indices) return false
        val ins = p[index]
        if (ins.type == CommandType.END) return false
        return insertWithBlock(robot, index + 1, ins)
    }

    fun setArg(robot: String, index: Int, arg: Int) {
        val p = programs.getValue(robot)
        val ins = p.getOrNull(index) ?: return
        if (!ins.type.hasArg) return
        edited()
        p[index] = Instr(ins.type, arg.coerceIn(ins.type.argRange))
    }

    fun clear(robot: String) { edited(); programs.getValue(robot).clear() }

    /** Komut zincirini başka bir robota kopyalar (sınırı aşmazsa). */
    fun copyProgram(fromRobot: String, toRobot: String): Boolean {
        val src = programs.getValue(fromRobot)
        val extra = src.count { it.type != CommandType.END } - cardsOf(toRobot)
        if (totalCards + extra > maxCards) return false
        edited()
        programs.getValue(toRobot).apply { clear(); addAll(src) }
        return true
    }

    // ------------------------------------------------------------------ çalıştırma

    /** Düzenleme modundan simülasyonu hazırlar. Zincir hatalıysa false. */
    private fun prepare(): Boolean {
        val compiled = HashMap<String, Program>()
        for ((id, p) in programs) {
            val r = Program.compile(p)
            val err = r.exceptionOrNull()
            if (err is ProgramException) { compileError = id to err; return false }
            compiled[id] = r.getOrThrow()
        }
        compileError = null
        val s = Simulation(level, compiled)
        sim = s
        from = s.state.copy()
        lastEvents = emptyList()
        errorCards.clear()
        pendingOutcome = null
        shownOutcome = null
        t = 1f
        return true
    }

    val isFresh: Boolean get() = sim == null || (sim!!.state.tick == 0)

    fun play(): Boolean {
        when (mode) {
            Mode.EDIT -> { if (!prepare()) return false; mode = Mode.RUNNING; stepping = false }
            Mode.PAUSED -> { mode = Mode.RUNNING; stepping = false }
            else -> return false
        }
        return true
    }

    fun pause() { if (mode == Mode.RUNNING) mode = Mode.PAUSED }

    /** Bir adım ilerler (duraklatılmışken). */
    fun stepOnce(): Boolean {
        if (mode == Mode.EDIT) { if (!prepare()) return false; mode = Mode.PAUSED }
        if (mode != Mode.PAUSED) return false
        if (t < 1f) return false
        advance()
        stepping = true
        return true
    }

    fun stepBack(): Boolean {
        val s = sim ?: return false
        if (mode != Mode.PAUSED || t < 1f) return false
        if (!s.stepBack()) return false
        from = s.state.copy()
        lastEvents = emptyList()
        pendingOutcome = null
        t = 1f
        return true
    }

    fun reset() {
        sim = null
        mode = Mode.EDIT
        from = WorldState.initial(level, robotIds.associateWith { 0 })
        lastEvents = emptyList()
        pendingOutcome = null
        shownOutcome = null
        t = 1f
    }

    /** Sonuç ekranı kapandıktan sonra düzenlemeye dönerken: hatalı kart işaretleri korunur. */
    fun backToEdit() {
        val keep = HashMap(errorCards)
        reset()
        errorCards.putAll(keep)
    }

    private fun advance() {
        val s = sim ?: return
        from = s.state.copy()
        val r = s.step()
        lastEvents = r.events
        for (e in r.events) when (e) {
            is SimEvent.Bumped -> errorCards.getOrPut(e.robot) { HashSet() } += e.pc
            is SimEvent.CommandFailed -> errorCards.getOrPut(e.robot) { HashSet() } += e.pc
            is SimEvent.OutOfEnergy -> errorCards.getOrPut(e.robot) { HashSet() } += e.pc
            else -> Unit
        }
        (r.outcome as? Outcome.Fail)?.let { f -> val who = f.robot; if (who != null && f.pc >= 0) errorCards.getOrPut(who) { HashSet() } += f.pc }
        t = 0f
        pendingOutcome = r.outcome
        listener?.invoke(r)
    }

    /**
     * Zamanı ilerletir. Bir adım animasyonu bittiğinde sonraki adımı çalıştırır.
     * @return animasyon bitip sonuç gösterilmeye hazırsa sonuç.
     */
    fun update(dt: Float, tickDuration: Float): Outcome? {
        if (mode == Mode.EDIT || mode == Mode.DONE) return null
        if (t < 1f) {
            t = minOf(1f, t + dt / tickDuration)
            if (t < 1f) return null
        }
        pendingOutcome?.let { o ->
            mode = Mode.DONE
            shownOutcome = o
            return o
        }
        if (mode == Mode.RUNNING) advance()
        return null
    }

    /** Çalışan kart: robot -> indeks. */
    fun activePc(robot: String): Int = if (sim == null) -1 else current.robots.firstOrNull { it.id == robot }?.activePc ?: -1
}
