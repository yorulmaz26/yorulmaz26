package com.zamaninefendisi.logic

import java.util.ArrayDeque

enum class SessionStatus { PLAYING, WON, LOST }

class Enemy(val id: Int, var pos: Pos, var hp: Int, val kind: EnemyKind)

/** Oynanış sırasında oluşan olaylar. Çizim katmanı animasyonlarını bunlardan üretir. */
sealed class GameEvent {
    data class HeroMoved(val from: Pos, val to: Pos) : GameEvent()
    data class EraSwitched(val from: Era, val to: Era) : GameEvent()
    data class CellsChanged(val changed: Map<Era, Set<Pos>>) : GameEvent()
    data class EnemyMoved(val id: Int, val from: Pos, val to: Pos) : GameEvent()
    data class EnemySpawned(val id: Int, val at: Pos) : GameEvent()
    data class EnemyDefeated(val id: Int, val at: Pos, val trapped: Boolean) : GameEvent()
    data class HeroDamaged(val by: Int, val hpLeft: Int) : GameEvent()
    data class WallDamaged(val at: Pos) : GameEvent()
    data class Paradox(val text: String) : GameEvent()
    data class WoodChanged(val delta: Int) : GameEvent()
    data class EnergyChanged(val delta: Int) : GameEvent()
    data class Message(val text: String) : GameEvent()
}

enum class JournalKind { ACTION, CAUSALITY, PARADOX, BATTLE, INFO }

data class JournalEntry(val turn: Int, val era: Era, val kind: JournalKind, val text: String)

/** Arayüzde bir hücreye dokunulduğunda sunulan seçenek. */
data class ActionOption(val label: String, val action: Action, val enabled: Boolean, val reason: String? = null)

/**
 * Tek bir bölüm denemesinin tam simülasyonu. Grafikten bağımsızdır.
 * Bütün durum, eylem listesinden deterministik olarak yeniden üretilebilir ([replay]); geri alma bu yolla yapılır.
 */
class LevelSession(val level: LevelDef) {
    val timeline = CausalityEngine(level)

    var heroPos: Pos = level.heroStart; private set
    var heroEra: Era = level.startEra; private set
    var heroHp: Int = level.heroHp; private set
    var wood: Int = level.startWood; private set
    var energy: Int? = level.energy; private set
    var turn: Int = 0; private set
    var actionsUsed: Int = 0; private set
    var status: SessionStatus = SessionStatus.PLAYING; private set
    var failReason: String? = null; private set
    var enemiesDefeated: Int = 0; private set
    var paradoxCount: Int = 0; private set

    private val _enemies = ArrayList<Enemy>()
    val enemies: List<Enemy> get() = _enemies
    private var nextEnemyId = 0

    private val _actions = ArrayList<Action>()
    val actions: List<Action> get() = _actions
    private val _journal = ArrayList<JournalEntry>()
    val journal: List<JournalEntry> get() = _journal

    init {
        require(level.startEra in level.eras)
        level.enemies.forEach { spawn(it.pos, it.kind ?: defaultEnemy) }
        log(JournalKind.INFO, "${level.name}: ${heroEra.locative} göreve başladın.")
    }

    // ---------------------------------------------------------------- Sorgular

    fun cell(era: Era, p: Pos): CellState = timeline.grid(era)[p]
    fun inBounds(p: Pos) = p.x in 0 until level.width && p.y in 0 until level.height
    fun enemyAt(p: Pos): Enemy? = _enemies.firstOrNull { it.pos == p }
    val enemiesVisible: Boolean get() = heroEra == level.enemyEra
    private val defaultEnemy: EnemyKind get() = if (level.enemyEra == Era.FUTURE) EnemyKind.ROBOT else EnemyKind.RAIDER

    fun canWalk(era: Era, p: Pos): Boolean {
        if (!inBounds(p) || !cell(era, p).walkable) return false
        if (era == level.enemyEra && enemyAt(p) != null) return false
        return true
    }

    fun stars(): Int = when {
        status != SessionStatus.WON -> 0
        actionsUsed <= level.par -> 3
        actionsUsed <= level.par + (level.par + 1) / 2 -> 2
        else -> 1
    }

    fun goalsMet(): List<Boolean> = level.goals.map { it.met(this) }

    fun currentHint(): String? = level.hints.firstOrNull { !it.done(this) }?.text

    /** Kahramanın bulunduğu çağda en kısa yürüme yolu (başlangıç hariç). */
    fun pathTo(target: Pos): List<Pos>? {
        if (target == heroPos) return emptyList()
        if (!canWalk(heroEra, target)) return null
        val prev = HashMap<Pos, Pos>()
        val q = ArrayDeque<Pos>()
        q.add(heroPos)
        prev[heroPos] = heroPos
        while (q.isNotEmpty()) {
            val c = q.poll()
            if (c == target) break
            for (n in c.neighbors4()) if (n !in prev && canWalk(heroEra, n)) { prev[n] = c; q.add(n) }
        }
        if (target !in prev) return null
        val path = ArrayList<Pos>()
        var c = target
        while (c != heroPos) { path += c; c = prev[c]!! }
        return path.reversed()
    }

    /** Hücreye dokunulduğunda gösterilecek seçenekler. */
    fun options(target: Pos): List<ActionOption> {
        if (status != SessionStatus.PLAYING || !inBounds(target)) return emptyList()
        val out = ArrayList<ActionOption>()
        val dist = target.manhattan(heroPos)
        if (dist == 1 && heroEra == level.enemyEra) enemyAt(target)?.let {
            out += ActionOption("Saldır: ${it.kind.title}", Action.Attack(target), true)
        }
        if (dist == 1 && canWalk(heroEra, target)) out += ActionOption("Yürü", Action.Move(target), true)
        if (dist <= 1) for (kind in level.allowed[heroEra].orEmpty()) {
            val a = Action.Build(kind, target)
            val err = validate(a)
            val label = buildLabel(kind)
            // Yalnızca hücreye anlamlı olarak uygulanabilecek seçenekleri göster.
            if (err == null) out += ActionOption(label, a, true)
            else if (EvolutionRules.applyBuild(kind, cell(heroEra, target), heroEra) != null) out += ActionOption(label, a, false, err)
        }
        return out
    }

    private fun buildLabel(kind: BuildKind): String = when {
        kind.woodCost > 0 -> "${kind.title} (${kind.woodCost} odun)"
        kind.woodCost < 0 -> "${kind.title} (+${-kind.woodCost} odun)"
        else -> kind.title
    }

    /** Eylem geçerliyse null, değilse Türkçe gerekçe döner. */
    fun validate(a: Action): String? {
        if (status != SessionStatus.PLAYING) return "Bölüm sona erdi"
        return when (a) {
            is Action.Move -> when {
                a.to.manhattan(heroPos) != 1 -> "Yalnızca komşu hücreye yürüyebilirsin"
                !inBounds(a.to) -> "Harita dışı"
                heroEra == level.enemyEra && enemyAt(a.to) != null -> "Orada bir düşman var"
                !cell(heroEra, a.to).walkable -> "${cell(heroEra, a.to).displayName} geçilemez"
                else -> null
            }
            is Action.SwitchEra -> when {
                a.era == heroEra -> "Zaten ${a.era.locative}sın"
                a.era !in level.eras -> "${a.era.title} bu bölümde kapalı"
                energy == 0 -> "Zaman enerjin tükendi"
                !cell(a.era, heroPos).walkable -> "Bu konum ${a.era.locative} kapalı: ${cell(a.era, heroPos).displayName}"
                a.era == level.enemyEra && enemyAt(heroPos) != null -> "${a.era.locative} bu hücrede düşman var"
                else -> null
            }
            is Action.Build -> validateBuild(a)
            is Action.Attack -> when {
                heroEra != level.enemyEra -> "Bu çağda düşman yok"
                a.at.manhattan(heroPos) != 1 -> "Düşman yanında olmalı"
                enemyAt(a.at) == null -> "Orada düşman yok"
                else -> null
            }
            Action.Wait -> null
        }
    }

    private fun validateBuild(a: Action.Build): String? {
        val kind = a.kind
        if (kind !in level.allowed[heroEra].orEmpty()) return "${heroEra.locative} \"${kind.title}\" kullanılamaz"
        if (!inBounds(a.at)) return "Harita dışı"
        if (a.at.manhattan(heroPos) > 1) return "Hedef hücre yanında olmalı"
        val s = cell(heroEra, a.at)
        val result = EvolutionRules.applyBuild(kind, s, heroEra) ?: return when (kind) {
            BuildKind.PLANT -> "Fidan yalnızca boş çayıra dikilir"
            BuildKind.CUT -> "Burada kesilecek ağaç yok"
            BuildKind.BRIDGE -> "Köprü yalnızca nehre kurulur"
            BuildKind.VILLAGE -> "Köy yalnızca boş çayıra kurulur"
            BuildKind.WALL -> "Çit boş araziye kurulur"
            BuildKind.DISMANTLE -> "Burada düşman kampı yok"
            BuildKind.ACTIVATE -> "Burada uyuyan mühür yok"
            BuildKind.COLLECT -> "Burada kristal yok"
        }
        if (kind.woodCost > 0 && wood < kind.woodCost) return "Yeterli odun yok (${kind.woodCost} gerekli)"
        if (a.at == heroPos && !result.walkable) return "Durduğun yere bunu kuramazsın"
        if (heroEra == level.enemyEra && enemyAt(a.at) != null) return "Düşmanın üstüne inşa edemezsin"
        return null
    }

    // ---------------------------------------------------------------- Eylem işleme

    /** Eylemi uygular; geçersizse IllegalArgumentException fırlatır. */
    fun perform(a: Action): List<GameEvent> {
        validate(a)?.let { throw IllegalArgumentException(it) }
        val events = ArrayList<GameEvent>()
        _actions += a
        turn++
        if (a !is Action.Wait) actionsUsed++
        when (a) {
            is Action.Move -> {
                events += GameEvent.HeroMoved(heroPos, a.to)
                heroPos = a.to
                collectHere(events)
            }
            is Action.SwitchEra -> {
                events += GameEvent.EraSwitched(heroEra, a.era)
                log(JournalKind.ACTION, "${heroEra.title} → ${a.era.title} zaman geçişi.")
                heroEra = a.era
                energy = energy?.minus(1)
                collectHere(events)
            }
            is Action.Build -> doBuild(a, events)
            is Action.Attack -> {
                val e = enemyAt(a.at)!!
                e.hp--
                if (e.hp <= 0) {
                    _enemies.remove(e); enemiesDefeated++
                    events += GameEvent.EnemyDefeated(e.id, e.pos, trapped = false)
                    log(JournalKind.BATTLE, "${e.kind.title} yenildi ${e.pos}.")
                }
            }
            Action.Wait -> log(JournalKind.ACTION, "Bir tur bekledin.")
        }
        if (level.hasEnemies) enemyPhase(events)
        evaluate()
        return events
    }

    private fun doBuild(a: Action.Build, events: MutableList<GameEvent>) {
        applyChange(a.kind, a.at, events)
    }

    private fun applyChange(kind: BuildKind, at: Pos, events: MutableList<GameEvent>) {
        val a = Action.Build(kind, at)
        val before = timeline.eras.associateWith { cell(it, a.at) }
        if (a.kind.woodCost != 0) { wood -= a.kind.woodCost; events += GameEvent.WoodChanged(-a.kind.woodCost) }
        if (a.kind.energyGain != 0) energy?.let { energy = it + a.kind.energyGain; events += GameEvent.EnergyChanged(a.kind.energyGain) }
        val (rec, result) = timeline.addChange(heroEra, a.at, a.kind, turn)
        events += GameEvent.CellsChanged(result.changed)
        log(JournalKind.ACTION, "${heroEra.locative} ${a.at} ${a.kind.verb}.")
        // Nedensellik sonuçlarını günlüğe yaz: aynı hücrenin sonraki çağlardaki yeni hâli.
        for (era in timeline.eras) if (era > heroEra) {
            val now = cell(era, a.at)
            val old = before[era]!!
            if (now != old) log(JournalKind.CAUSALITY, "Kelebek etkisi: ${era.locative} ${a.at} artık ${now.displayName.ifEmpty { "boş" }} (önceden ${old.displayName.ifEmpty { "boş" }}).")
        }
        val wider = result.changed.entries.filter { it.key > heroEra }.sumOf { (_, cells) -> cells.count { it != a.at } }
        if (wider > 0) log(JournalKind.CAUSALITY, "Bu değişiklik ileri çağlarda $wider hücreyi daha etkiledi.")
        handleVoided(result.voided, events)
        if (rec.voided) error("Yeni değişiklik anında geçersiz olamaz")
        // Zaman tuzağı: düşmanın bulunduğu hücre ileri çağda geçilmez hâle geldiyse akıncı zamanın içinde kaybolur.
        trapEnemies(events)
    }

    /** Kahramanın bulunduğu hücrede kristal varsa kendiliğinden toplanır (kalıcı bir değişikliktir). */
    private fun collectHere(events: MutableList<GameEvent>) {
        if (cell(heroEra, heroPos).feature == Feature.CRYSTAL) applyChange(BuildKind.COLLECT, heroPos, events)
    }

    private fun handleVoided(voided: List<ChangeRecord>, events: MutableList<GameEvent>) {
        for (c in voided) {
            paradoxCount++
            if (c.kind.woodCost > 0) { wood += c.kind.woodCost; events += GameEvent.WoodChanged(c.kind.woodCost) }
            if (c.kind.woodCost < 0) { val lost = minOf(wood, -c.kind.woodCost); wood -= lost; if (lost > 0) events += GameEvent.WoodChanged(-lost) }
            if (c.kind.energyGain > 0) energy?.let { val lost = minOf(it, c.kind.energyGain); energy = it - lost; if (lost > 0) events += GameEvent.EnergyChanged(-lost) }
            val text = "Paradoks! ${c.era.locative} ${c.turn}. turda ${c.pos} hücresinde yaptığın \"${c.kind.title}\" artık hiç gerçekleşmemiş oldu." +
                (if (c.kind.woodCost > 0) " Harcadığın odun geri döndü." else if (c.kind.woodCost < 0) " Kazandığın odun buharlaştı." else "") +
                (if (c.kind.energyGain > 0) " Kazandığın enerji buharlaştı." else "")
            events += GameEvent.Paradox(text)
            log(JournalKind.PARADOX, text, c.era)
        }
    }

    private fun trapEnemies(events: MutableList<GameEvent>) {
        if (!timeline.has(level.enemyEra)) return
        val it = _enemies.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (!cell(level.enemyEra, e.pos).walkable) {
                it.remove(); enemiesDefeated++
                events += GameEvent.EnemyDefeated(e.id, e.pos, trapped = true)
                log(JournalKind.BATTLE, "Zaman tuzağı: ${e.kind.title} ${e.pos} hücresinde zamanın içinde kayboldu.", level.enemyEra)
            }
        }
    }

    // ---------------------------------------------------------------- Düşman yapay zekâsı

    private fun spawn(p: Pos, kind: EnemyKind): Enemy {
        val e = Enemy(nextEnemyId++, p, kind.hp, kind)
        _enemies += e
        return e
    }

    private fun enemyPhase(events: MutableList<GameEvent>) {
        if (!timeline.has(level.enemyEra) || status != SessionStatus.PLAYING) return
        val era = level.enemyEra
        trapEnemies(events)
        val targets = level.protect.toSet()
        for (e in _enemies.sortedBy { it.id }) {
            if (status != SessionStatus.PLAYING) break
            if (e !in _enemies) continue
            if (heroEra == era && e.pos.manhattan(heroPos) == 1) {
                heroHp -= e.kind.attack
                events += GameEvent.HeroDamaged(e.kind.attack, heroHp)
                log(JournalKind.BATTLE, "${e.kind.title} sana saldırdı! Kalan can: $heroHp.", era)
                if (heroHp <= 0) lose("Zaman Muhafızı yenildi.")
                continue
            }
            val goalSet = if (targets.isNotEmpty()) targets else if (heroEra == era) setOf(heroPos) else emptySet()
            if (goalSet.isEmpty()) continue
            val step = enemyStep(e, goalSet, throughWalls = false)
            if (step != null) {
                // Zaman Muhafızı korunan hücrenin üstünde duruyorsa düşman içeri giremez.
                if (heroEra == era && step == heroPos) continue
                if (step in targets) {
                    events += GameEvent.EnemyMoved(e.id, e.pos, step)
                    e.pos = step
                    lose("${e.kind.title} korunan ${cell(era, step).displayName} hücresine ulaştı.")
                    break
                }
                if (heroEra == era && step == heroPos) continue
                events += GameEvent.EnemyMoved(e.id, e.pos, step)
                e.pos = step
            } else {
                // Yol yoksa kırılabilir bir duvara yüklen; o da yoksa aynı çağdaki Zaman Muhafızı'nın peşine düş.
                val wallStep = enemyStep(e, goalSet, throughWalls = true)
                if (wallStep == null || !cell(era, wallStep).let { it.feature.isWall && it.durability < UNBREAKABLE }) {
                    if (heroEra == era && heroPos !in goalSet) {
                        val chase = enemyStep(e, setOf(heroPos), throughWalls = false)
                        if (chase != null && chase != heroPos) {
                            events += GameEvent.EnemyMoved(e.id, e.pos, chase)
                            e.pos = chase
                        }
                    }
                    continue
                }
                val c = cell(era, wallStep)
                if (c.feature.isWall && c.durability < UNBREAKABLE) {
                    val r = timeline.addDamage(wallStep, e.kind.attack)
                    events += GameEvent.WallDamaged(wallStep)
                    events += GameEvent.CellsChanged(r.changed)
                    handleVoided(r.voided, events)
                    if (!cell(era, wallStep).feature.isWall) log(JournalKind.BATTLE, "${e.kind.title} ${wallStep} hücresindeki çiti yıktı!", era)
                }
            }
        }
        if (status != SessionStatus.PLAYING) return
        if (level.spawnEvery > 0 && turn % level.spawnEvery == 0) {
            for (fort in timeline.grid(era).find(Feature.ENEMY_FORT)) {
                if (_enemies.size >= level.maxEnemies) break
                val at = fort.neighbors4().firstOrNull { canWalk(era, it) && !(heroEra == era && it == heroPos) } ?: continue
                val e = spawn(at, defaultEnemy)
                events += GameEvent.EnemySpawned(e.id, at)
                log(JournalKind.BATTLE, "Düşman kalesinden yeni bir ${e.kind.title.lowercase()} çıktı ${at}.", era)
            }
        }
    }

    /** Düşman için BFS: hedef kümesine giden en kısa yolun ilk adımı. */
    private fun enemyStep(e: Enemy, goals: Set<Pos>, throughWalls: Boolean): Pos? {
        val era = level.enemyEra
        val g = timeline.grid(era)
        fun passable(p: Pos): Boolean {
            if (!inBounds(p)) return false
            if (p in goals) return true
            val c = g[p]
            val ok = c.walkable || (throughWalls && c.feature.isWall && c.durability < UNBREAKABLE)
            return ok && _enemies.none { it !== e && it.pos == p }
        }
        val prev = HashMap<Pos, Pos>()
        val q = ArrayDeque<Pos>()
        q.add(e.pos); prev[e.pos] = e.pos
        var found: Pos? = null
        while (q.isNotEmpty()) {
            val c = q.poll()
            if (c in goals && c != e.pos) { found = c; break }
            for (n in c.neighbors4()) if (n !in prev && passable(n)) { prev[n] = c; q.add(n) }
        }
        var c = found ?: return null
        while (prev[c] != e.pos) c = prev[c]!!
        return c
    }

    // ---------------------------------------------------------------- Sonuç

    private fun lose(reason: String) {
        if (status != SessionStatus.PLAYING) return
        status = SessionStatus.LOST
        failReason = reason
        log(JournalKind.INFO, "Başarısız: $reason")
    }

    private fun evaluate() {
        if (status != SessionStatus.PLAYING) return
        if (level.goals.all { it.met(this) }) {
            status = SessionStatus.WON
            log(JournalKind.INFO, "Zaman dengesi sağlandı! $actionsUsed eylemle kazandın.")
            return
        }
        if (turn >= level.maxTurns) lose("Tur sınırı (${level.maxTurns}) doldu.")
    }

    private fun log(kind: JournalKind, text: String, era: Era = heroEra) {
        _journal += JournalEntry(turn, era, kind, text)
    }

    /** Kalıcı Zaman Günlüğü için bu denemenin önemli nedensellik sonuçlarının özeti. */
    fun causalSummary(): List<String> {
        val out = ArrayList<String>()
        for (c in timeline.changes) {
            if (c.voided) continue
            val later = timeline.eras.filter { it > c.era }
            val effects = later.map { era -> "${era.title}: ${cell(era, c.pos).displayName.ifEmpty { "boş" }}" }
            out += "${c.era.locative} ${c.kind.verb}" + if (effects.isNotEmpty()) " → ${effects.joinToString(" · ")}" else ""
        }
        return out
    }

    companion object {
        /** Eylem listesini baştan oynatır. Geri alma ve kayıttan devam bu yolla deterministik çalışır. */
        fun replay(level: LevelDef, actions: List<Action>): LevelSession {
            val s = LevelSession(level)
            for (a in actions) {
                if (s.status != SessionStatus.PLAYING || s.validate(a) != null) break
                s.perform(a)
            }
            return s
        }
    }
}
