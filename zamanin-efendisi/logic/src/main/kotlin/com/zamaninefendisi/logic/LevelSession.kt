package com.zamaninefendisi.logic

import java.util.ArrayDeque

enum class SessionStatus { PLAYING, WON, LOST }

class Enemy(val id: Int, var pos: Pos, var hp: Int, val kind: EnemyKind) {
    val anchor: Pos = pos
    var strategy: EnemyStrategy = kind.strategy
    var frozenTurns: Int = 0
    var attackBonus: Int = 0
    var phaseCooldown: Int = 0
    val attack: Int get() = kind.attack + attackBonus
}

/** Müttefik birlik (düşman çağındaki savaş alanında). */
class AllyUnit(val id: Int, val type: UnitType, var pos: Pos) {
    var hp: Int = type.hp
}

/** Oynanış sırasında oluşan olaylar. Çizim katmanı animasyonlarını bunlardan üretir. */
sealed class GameEvent {
    data class HeroMoved(val from: Pos, val to: Pos) : GameEvent()
    data class EraSwitched(val from: Era, val to: Era) : GameEvent()
    data class CellsChanged(val changed: Map<Era, Set<Pos>>) : GameEvent()
    data class EnemyMoved(val id: Int, val from: Pos, val to: Pos) : GameEvent()
    data class EnemySpawned(val id: Int, val at: Pos) : GameEvent()
    data class EnemyDefeated(val id: Int, val at: Pos, val trapped: Boolean) : GameEvent()
    data class EnemyDamaged(val id: Int, val at: Pos, val amount: Int) : GameEvent()
    data class HeroDamaged(val by: Int, val hpLeft: Int) : GameEvent()
    data class UnitMoved(val id: Int, val from: Pos, val to: Pos) : GameEvent()
    data class UnitDamaged(val id: Int, val at: Pos, val amount: Int) : GameEvent()
    data class UnitLost(val id: Int, val at: Pos) : GameEvent()
    data class Shot(val from: Pos, val to: Pos) : GameEvent()
    data class WallDamaged(val at: Pos) : GameEvent()
    data class Paradox(val text: String) : GameEvent()
    data class WoodChanged(val delta: Int) : GameEvent()
    data class EnergyChanged(val delta: Int) : GameEvent()
    data class Combo(val count: Int) : GameEvent()
    data class Message(val text: String) : GameEvent()
}

enum class JournalKind { ACTION, CAUSALITY, PARADOX, BATTLE, INFO }

data class JournalEntry(val turn: Int, val era: Era, val kind: JournalKind, val text: String)

/** Arayüzde bir hücreye dokunulduğunda sunulan seçenek. */
data class ActionOption(val label: String, val action: Action, val enabled: Boolean, val reason: String? = null)

/** "Geleceği önceden görme": bir eylemin çağlardaki sonuçları. */
data class Foresight(val changes: List<Triple<Era, Pos, Pair<CellState, CellState>>>, val notes: List<String>)

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
    var freezes: Int = level.freezes; private set
    var turn: Int = 0; private set
    var actionsUsed: Int = 0; private set
    var status: SessionStatus = SessionStatus.PLAYING; private set
    var failReason: String? = null; private set
    var enemiesDefeated: Int = 0; private set
    var paradoxCount: Int = 0; private set
    /** Zaman Zinciri Kombosu: kısa sürede üç farklı çağda yapılan kalıcı değişiklikler. */
    var combos: Int = 0; private set
    private val recentBuilds = ArrayList<Pair<Int, Era>>()

    private val _enemies = ArrayList<Enemy>()
    val enemies: List<Enemy> get() = _enemies
    private var nextEnemyId = 0
    private val _units = ArrayList<AllyUnit>()
    val units: List<AllyUnit> get() = _units
    private var commandedUnit: Int = -1

    private val _actions = ArrayList<Action>()
    val actions: List<Action> get() = _actions
    private val _journal = ArrayList<JournalEntry>()
    val journal: List<JournalEntry> get() = _journal

    private val battleEra: Era get() = level.enemyEra
    private val defaultEnemy: EnemyKind get() = level.enemyKindOverride ?: if (level.enemyEra == Era.FUTURE) EnemyKind.ROBOT else EnemyKind.RAIDER

    init {
        require(level.startEra in level.eras)
        level.enemies.forEach { spawn(it.pos, it.kind ?: defaultEnemy) }
        level.units.forEachIndexed { i, u ->
            require(level.enemyEra in u.type.eras) { "${u.type.title} ${level.enemyEra.title} çağına uyumlu değil" }
            _units += AllyUnit(i, u.type, u.pos)
        }
        log(JournalKind.INFO, "${level.name}: ${heroEra.locative} göreve başladın.")
    }

    // ---------------------------------------------------------------- Sorgular

    fun cell(era: Era, p: Pos): CellState = timeline.grid(era)[p]
    fun inBounds(p: Pos) = p.x in 0 until level.width && p.y in 0 until level.height
    fun enemyAt(p: Pos): Enemy? = _enemies.firstOrNull { it.pos == p }
    fun unitAt(p: Pos): AllyUnit? = _units.firstOrNull { it.pos == p }
    fun unit(id: Int): AllyUnit? = _units.firstOrNull { it.id == id }
    val enemiesVisible: Boolean get() = heroEra == level.enemyEra

    /** Kahramanın yürüyebileceği hücre (düşman/birlik yok, geçilebilir). */
    fun canWalk(era: Era, p: Pos): Boolean {
        if (!inBounds(p) || !cell(era, p).walkable) return false
        if (era == battleEra && (enemyAt(p) != null || unitAt(p) != null)) return false
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

    /** Birliğin bu tur ulaşabileceği hücreler (hareket puanı kadar adım). */
    fun unitReach(u: AllyUnit): Set<Pos> {
        val out = HashSet<Pos>()
        val dist = HashMap<Pos, Int>()
        val q = ArrayDeque<Pos>()
        q.add(u.pos); dist[u.pos] = 0
        while (q.isNotEmpty()) {
            val c = q.poll()
            val d = dist[c]!!
            if (d >= u.type.move) continue
            for (n in c.neighbors4()) {
                if (n in dist || !inBounds(n) || !cell(battleEra, n).walkable) continue
                if (enemyAt(n) != null || unitAt(n) != null || (heroEra == battleEra && heroPos == n)) continue
                dist[n] = d + 1; q.add(n); out += n
            }
        }
        return out
    }

    /** Birliğin saldırabileceği hedefler: düşmanlar ve (kuşatma için) düşman kalesi. */
    fun unitTargets(u: AllyUnit): Set<Pos> {
        val out = HashSet<Pos>()
        for (e in _enemies) if (e.pos.manhattan(u.pos) in u.type.minRange..u.type.maxRange) out += e.pos
        for (f in timeline.grid(battleEra).find(Feature.ENEMY_FORT)) if (f.manhattan(u.pos) in u.type.minRange..u.type.maxRange) out += f
        return out
    }

    /** Hücreye dokunulduğunda gösterilecek seçenekler. */
    fun options(target: Pos): List<ActionOption> {
        if (status != SessionStatus.PLAYING || !inBounds(target)) return emptyList()
        val out = ArrayList<ActionOption>()
        val dist = target.manhattan(heroPos)
        if (dist == 1 && heroEra == battleEra) enemyAt(target)?.let {
            out += ActionOption("Saldır: ${it.kind.title}", Action.Attack(target), true)
        }
        if (dist == 1 && canWalk(heroEra, target)) out += ActionOption("Yürü", Action.Move(target), true)
        if (dist <= 1) for (kind in level.allowed[heroEra].orEmpty()) {
            val a = Action.Build(kind, target)
            val err = validate(a)
            val label = buildLabel(kind)
            if (err == null) out += ActionOption(label, a, true)
            else if (EvolutionRules.applyBuild(kind, cell(heroEra, target), heroEra) != null) out += ActionOption(label, a, false, err)
        }
        return out
    }

    private fun buildLabel(kind: BuildKind): String = when {
        kind.freezeCost > 0 -> "${kind.title} (1 hak)"
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
                heroEra == battleEra && enemyAt(a.to) != null -> "Orada bir düşman var"
                heroEra == battleEra && unitAt(a.to) != null -> "Orada bir müttefik birlik var"
                !cell(heroEra, a.to).walkable -> "${cell(heroEra, a.to).displayName} geçilemez"
                else -> null
            }
            is Action.SwitchEra -> when {
                a.era == heroEra -> "Zaten ${a.era.locative}sın"
                a.era !in level.eras -> "${a.era.title} bu bölümde kapalı"
                energy == 0 -> "Zaman enerjin tükendi"
                !cell(a.era, heroPos).walkable -> "Bu konum ${a.era.locative} kapalı: ${cell(a.era, heroPos).displayName}"
                a.era == battleEra && (enemyAt(heroPos) != null || unitAt(heroPos) != null) -> "${a.era.locative} bu hücre dolu"
                else -> null
            }
            is Action.Build -> validateBuild(a)
            is Action.Attack -> when {
                heroEra != battleEra -> "Bu çağda düşman yok"
                a.at.manhattan(heroPos) != 1 -> "Düşman yanında olmalı"
                enemyAt(a.at) == null -> "Orada düşman yok"
                else -> null
            }
            is Action.UnitMove -> {
                val u = unit(a.unit)
                when {
                    u == null -> "Birlik yok"
                    heroEra != battleEra -> "Birliklere yalnızca ${battleEra.locative} komuta edebilirsin"
                    a.to !in unitReach(u) -> "${u.type.title} oraya ulaşamaz (hareket ${u.type.move})"
                    else -> null
                }
            }
            is Action.UnitAttack -> {
                val u = unit(a.unit)
                when {
                    u == null -> "Birlik yok"
                    heroEra != battleEra -> "Birliklere yalnızca ${battleEra.locative} komuta edebilirsin"
                    a.at !in unitTargets(u) -> "Hedef menzil dışında (${u.type.minRange}–${u.type.maxRange})"
                    else -> null
                }
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
            BuildKind.TOWER -> "Kule boş araziye kurulur"
            BuildKind.DISMANTLE -> "Burada düşman kampı yok"
            BuildKind.ACTIVATE -> "Burada uyuyan mühür yok"
            BuildKind.COLLECT -> "Burada kristal yok"
            BuildKind.FREEZE -> if (s.frozen) "Bu nesnenin zamanı zaten donmuş" else "Dondurulacak bir nesne yok"
        }
        if (kind.woodCost > 0 && wood < kind.woodCost) return "Yeterli odun yok (${kind.woodCost} gerekli)"
        if (kind.freezeCost > 0 && freezes < kind.freezeCost) return "Dondurma hakkın kalmadı"
        if (a.at == heroPos && !result.walkable) return "Durduğun yere bunu kuramazsın"
        if (heroEra == battleEra && (enemyAt(a.at) != null || unitAt(a.at) != null) && !result.walkable) return "Dolu bir hücreye inşa edemezsin"
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
        commandedUnit = -1
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
            is Action.Build -> applyChange(a.kind, a.at, events)
            is Action.Attack -> damageEnemy(enemyAt(a.at)!!, 1, events, "Zaman Muhafızı")
            is Action.UnitMove -> {
                val u = unit(a.unit)!!
                events += GameEvent.UnitMoved(u.id, u.pos, a.to)
                u.pos = a.to
                commandedUnit = u.id
            }
            is Action.UnitAttack -> {
                val u = unit(a.unit)!!
                commandedUnit = u.id
                unitStrike(u, a.at, events)
            }
            Action.Wait -> log(JournalKind.ACTION, "Bir tur bekledin.")
        }
        if (level.hasEnemies) enemyPhase(events)
        evaluate()
        return events
    }

    private fun applyChange(kind: BuildKind, at: Pos, events: MutableList<GameEvent>) {
        val before = timeline.eras.associateWith { cell(it, at) }
        if (kind.woodCost != 0) { wood -= kind.woodCost; events += GameEvent.WoodChanged(-kind.woodCost) }
        if (kind.energyGain != 0) energy?.let { energy = it + kind.energyGain; events += GameEvent.EnergyChanged(kind.energyGain) }
        if (kind.freezeCost != 0) freezes -= kind.freezeCost
        val (rec, result) = timeline.addChange(heroEra, at, kind, turn)
        events += GameEvent.CellsChanged(result.changed)
        log(JournalKind.ACTION, "${heroEra.locative} $at ${kind.verb}.")
        for (era in timeline.eras) if (era > heroEra) {
            val now = cell(era, at)
            val old = before[era]!!
            if (now != old) log(JournalKind.CAUSALITY, "Kelebek etkisi: ${era.locative} $at artık ${now.displayName.ifEmpty { "boş" }} (önceden ${old.displayName.ifEmpty { "boş" }}).")
        }
        val wider = result.changed.entries.filter { it.key > heroEra }.sumOf { (_, cells) -> cells.count { it != at } }
        if (wider > 0) log(JournalKind.CAUSALITY, "Bu değişiklik ileri çağlarda $wider hücreyi daha etkiledi.")
        handleVoided(result.voided, events)
        if (rec.voided) error("Yeni değişiklik anında geçersiz olamaz")
        if (kind.playerChoosable) trackCombo(events)
        trapEnemies(events)
    }

    /** Zaman Zinciri: son 6 tur içinde üç farklı çağda kalıcı değişiklik yapmak bir kombo oluşturur. */
    private fun trackCombo(events: MutableList<GameEvent>) {
        if (timeline.eras.size < 3) return
        recentBuilds += turn to heroEra
        recentBuilds.removeAll { turn - it.first > 6 }
        if (recentBuilds.map { it.second }.toSet().size >= 3) {
            combos++
            recentBuilds.clear()
            events += GameEvent.Combo(combos)
            log(JournalKind.CAUSALITY, "Zaman Zinciri Kombosu! Üç çağda art arda değişiklik yaptın (toplam $combos).")
        }
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
            if (c.kind.freezeCost > 0) freezes += c.kind.freezeCost
            val text = "Paradoks! ${c.era.locative} ${c.turn}. turda ${c.pos} hücresinde yaptığın \"${c.kind.title}\" artık hiç gerçekleşmemiş oldu." +
                (if (c.kind.woodCost > 0) " Harcadığın odun geri döndü." else if (c.kind.woodCost < 0) " Kazandığın odun buharlaştı." else "") +
                (if (c.kind.energyGain > 0) " Kazandığın enerji buharlaştı." else "") +
                (if (c.kind.freezeCost > 0) " Dondurma hakkın geri döndü." else "")
            events += GameEvent.Paradox(text)
            log(JournalKind.PARADOX, text, c.era)
        }
    }

    private fun trapEnemies(events: MutableList<GameEvent>) {
        // Kahramanın durduğu hücre zaman içinde geçilmez olduysa (ör. köprüsü yıkıldıysa) akıntıya kapılır.
        if (!cell(heroEra, heroPos).walkable) lose("Zaman Muhafızı'nın durduğu ${heroEra.title} hücresi zaman içinde yok oldu.")
        if (!timeline.has(battleEra)) return
        val it = _enemies.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (!cell(battleEra, e.pos).walkable) {
                it.remove(); enemiesDefeated++
                events += GameEvent.EnemyDefeated(e.id, e.pos, trapped = true)
                log(JournalKind.BATTLE, "Zaman tuzağı: ${e.kind.title} ${e.pos} hücresinde zamanın içinde kayboldu.", battleEra)
            }
        }
        val ut = _units.iterator()
        while (ut.hasNext()) {
            val u = ut.next()
            if (!cell(battleEra, u.pos).walkable) {
                ut.remove()
                events += GameEvent.UnitLost(u.id, u.pos)
                log(JournalKind.PARADOX, "${u.type.title} ${u.pos} hücresinde zaman değişince kayboldu!", battleEra)
            }
        }
    }

    // ---------------------------------------------------------------- Savaş

    private fun damageFormula(attack: Int, defense: Int) = maxOf(1, attack - defense)

    private fun damageEnemy(e: Enemy, attack: Int, events: MutableList<GameEvent>, by: String, freeze: Boolean = false) {
        val dmg = damageFormula(attack, e.kind.defense)
        e.hp -= dmg
        events += GameEvent.EnemyDamaged(e.id, e.pos, dmg)
        if (freeze && e.hp > 0) { e.frozenTurns = 2; log(JournalKind.BATTLE, "${e.kind.title} 2 tur boyunca zamanın içinde dondu.", battleEra) }
        if (e.hp <= 0) {
            _enemies.remove(e); enemiesDefeated++
            events += GameEvent.EnemyDefeated(e.id, e.pos, trapped = false)
            log(JournalKind.BATTLE, "$by, ${e.kind.title} birliğini yendi ${e.pos}.", battleEra)
        }
    }

    private fun unitStrike(u: AllyUnit, at: Pos, events: MutableList<GameEvent>) {
        events += GameEvent.Shot(u.pos, at)
        val e = enemyAt(at)
        if (e != null) {
            damageEnemy(e, u.type.attack, events, u.type.title, freeze = u.type.ability == UnitAbility.FREEZE_SHOT)
            return
        }
        val c = cell(battleEra, at)
        if (c.feature == Feature.ENEMY_FORT) {
            val amount = if (u.type.ability == UnitAbility.SIEGE) u.type.attack * 3 else 1
            val r = timeline.addDamage(at, amount)
            events += GameEvent.WallDamaged(at)
            events += GameEvent.CellsChanged(r.changed)
            handleVoided(r.voided, events)
            if (cell(battleEra, at).feature != Feature.ENEMY_FORT) log(JournalKind.BATTLE, "${u.type.title} düşman kalesini yıktı!", battleEra)
        }
    }

    private fun damageAlly(target: Any, attack: Int, events: MutableList<GameEvent>, by: String) {
        when (target) {
            is AllyUnit -> {
                var dmg = damageFormula(attack, target.type.defense)
                if (target.type.ability == UnitAbility.SHIELD) dmg = maxOf(0, dmg - 1)
                if (_units.any { it !== target && it.type.ability == UnitAbility.GUARD && it.pos.manhattan(target.pos) == 1 }) dmg = maxOf(0, dmg - 1)
                target.hp -= dmg
                events += GameEvent.UnitDamaged(target.id, target.pos, dmg)
                if (target.hp <= 0) {
                    _units.remove(target)
                    events += GameEvent.UnitLost(target.id, target.pos)
                    log(JournalKind.BATTLE, "$by, ${target.type.title} birliğini yendi.", battleEra)
                }
            }
            else -> {
                var dmg = attack
                if (_units.any { it.type.ability == UnitAbility.GUARD && it.pos.manhattan(heroPos) == 1 }) dmg = maxOf(0, dmg - 1)
                heroHp -= dmg
                events += GameEvent.HeroDamaged(dmg, heroHp)
                log(JournalKind.BATTLE, "$by sana saldırdı! Kalan can: $heroHp.", battleEra)
                if (heroHp <= 0) lose("Zaman Muhafızı yenildi.")
            }
        }
    }

    // ---------------------------------------------------------------- Düşman yapay zekâsı

    private fun spawn(p: Pos, kind: EnemyKind): Enemy {
        val e = Enemy(nextEnemyId++, p, kind.hp, kind)
        _enemies += e
        return e
    }

    private fun alliesInRange(from: Pos, range: Int): List<Any> {
        val out = ArrayList<Any>()
        if (heroEra == battleEra && heroPos.manhattan(from) in 1..range) out += HeroTarget
        _units.filter { it.pos.manhattan(from) in 1..range }.sortedWith(compareBy({ it.hp }, { it.id })).forEach { out += it }
        return out
    }

    private object HeroTarget

    private fun enemyPhase(events: MutableList<GameEvent>) {
        if (!timeline.has(battleEra) || status != SessionStatus.PLAYING) return
        val era = battleEra
        // 1) Müttefik nöbeti: bu tur komuta edilmeyen birlikler menzildeki en zayıf düşmana kendiliğinden saldırır.
        for (u in _units.sortedBy { it.id }) {
            if (u.id == commandedUnit || u !in _units) continue
            val target = _enemies.filter { it.pos.manhattan(u.pos) in u.type.minRange..u.type.maxRange }.minWithOrNull(compareBy({ it.hp }, { it.id }))
                ?: continue
            unitStrike(u, target.pos, events)
        }
        // 2) Kuleler ateş eder.
        for (t in timeline.grid(era).positions().filter { cell(era, it).feature.isTower }.toList()) {
            val f = cell(era, t).feature
            val target = _enemies.filter { it.pos.manhattan(t) <= f.towerRange }.minWithOrNull(compareBy({ it.pos.manhattan(t) }, { it.id })) ?: continue
            events += GameEvent.Shot(t, target.pos)
            damageEnemy(target, f.towerDamage, events, f.title)
        }
        trapEnemies(events)
        val targets = level.protect.toSet()
        val fortsStanding = timeline.grid(era).count(Feature.ENEMY_FORT) > 0
        for (e in _enemies.sortedBy { it.id }) {
            if (status != SessionStatus.PLAYING) break
            if (e !in _enemies) continue
            if (e.frozenTurns > 0) { e.frozenTurns--; continue }
            // Taktik değişimi: kalesi yıkılan savunmacı öfkelenir.
            if (e.strategy == EnemyStrategy.DEFENSIVE && !fortsStanding && level.spawnEvery > 0) e.strategy = EnemyStrategy.AGGRESSIVE
            if (e.strategy == EnemyStrategy.TECH && turn % 3 == 0 && e.attackBonus < 3) {
                e.attackBonus++
                log(JournalKind.BATTLE, "${e.kind.title} kendini yükseltti: saldırı ${e.attack}.", era)
            }
            actEnemy(e, targets, events)
        }
        if (status != SessionStatus.PLAYING) return
        if (level.spawnEvery > 0 && turn % level.spawnEvery == 0) {
            for (fort in timeline.grid(era).find(Feature.ENEMY_FORT)) {
                if (_enemies.size >= level.maxEnemies) break
                val at = fort.neighbors4().firstOrNull { inBounds(it) && cell(era, it).walkable && enemyAt(it) == null && unitAt(it) == null && !(heroEra == era && it == heroPos) } ?: continue
                val e = spawn(at, defaultEnemy)
                events += GameEvent.EnemySpawned(e.id, at)
                log(JournalKind.BATTLE, "Düşman kalesinden yeni bir ${e.kind.title.lowercase()} çıktı $at.", era)
            }
        }
    }

    private fun moveEnemy(e: Enemy, to: Pos, events: MutableList<GameEvent>) {
        events += GameEvent.EnemyMoved(e.id, e.pos, to)
        e.pos = to
    }

    private fun strike(e: Enemy, events: MutableList<GameEvent>): Boolean {
        val t = alliesInRange(e.pos, e.kind.range).firstOrNull() ?: return false
        if (e.kind.range > 1) events += GameEvent.Shot(e.pos, if (t is AllyUnit) t.pos else heroPos)
        damageAlly(t, e.attack, events, e.kind.title)
        return true
    }

    private fun actEnemy(e: Enemy, targets: Set<Pos>, events: MutableList<GameEvent>) {
        val era = battleEra
        when (e.strategy) {
            EnemyStrategy.DEFENSIVE -> {
                if (strike(e, events)) return
                val near = buildList {
                    if (heroEra == era && heroPos.manhattan(e.anchor) <= 3) add(heroPos)
                    _units.filter { it.pos.manhattan(e.anchor) <= 3 }.forEach { add(it.pos) }
                }.toSet()
                val goal = if (near.isNotEmpty()) near else if (e.pos != e.anchor) setOf(e.anchor) else return
                enemyStep(e, goal, false)?.let { if (!occupiedByAlly(it)) moveEnemy(e, it, events) }
            }
            EnemyStrategy.RESOURCE -> {
                val g = timeline.grid(era)
                val loot = g.positions().filter { g[it].feature in LOOT }.toSet()
                if (loot.isEmpty()) { e.strategy = EnemyStrategy.AGGRESSIVE; actEnemy(e, targets, events); return }
                if (e.pos in loot || e.pos.neighbors4().any { it in loot }) {
                    val victim = if (e.pos in loot) e.pos else e.pos.neighbors4().first { it in loot }
                    val what = g[victim].displayName
                    raze(victim, events)
                    log(JournalKind.BATTLE, "${e.kind.title} $victim hücresindeki ${what.lowercase()} yağmaladı!", era)
                    return
                }
                if (strike(e, events)) return
                enemyStep(e, loot, false)?.let { if (!occupiedByAlly(it) && it !in loot) moveEnemy(e, it, events) }
            }
            EnemyStrategy.PARADOX -> {
                val g = timeline.grid(era)
                val made = g.positions().filter { g[it].causeId >= 0 && g[it].feature in SABOTAGE }.toSet()
                if (made.isEmpty()) { aggressive(e, targets, events); return }
                val adj = e.pos.neighbors4().firstOrNull { it in made && enemyAt(it) == null && unitAt(it) == null && !(heroEra == era && heroPos == it) }
                if (adj != null) {
                    val what = g[adj].displayName
                    raze(adj, events)
                    log(JournalKind.PARADOX, "${e.kind.title} senin yaptığın ${what.lowercase()} yapısını zaman çizgisinden sildi! $adj", era)
                    return
                }
                if (strike(e, events)) return
                enemyStep(e, made, throughWalls = false)?.let { if (!occupiedByAlly(it) && it !in made) moveEnemy(e, it, events) }
            }
            EnemyStrategy.TECH -> {
                // Arayı aç: bitişikteyse geri çekil, sonra ateş et.
                val adjacentFoe = alliesInRange(e.pos, 1).isNotEmpty()
                if (adjacentFoe) {
                    val away = e.pos.neighbors4().filter { inBounds(it) && cell(era, it).walkable && enemyAt(it) == null && !occupiedByAlly(it) && it !in targets }
                        .maxByOrNull { p -> alliesDistance(p) }
                    if (away != null && alliesDistance(away) > 1) { moveEnemy(e, away, events); strike(e, events); return }
                }
                if (strike(e, events)) return
                aggressive(e, targets, events)
            }
            EnemyStrategy.TIME_MANIPULATOR -> {
                aggressive(e, targets, events)
                if (status != SessionStatus.PLAYING || e !in _enemies) return
                if (heroEra != era) aggressive(e, targets, events) // sen yokken zaman onun için hızlı akar
            }
            EnemyStrategy.AGGRESSIVE -> aggressive(e, targets, events)
        }
    }

    private fun alliesDistance(p: Pos): Int {
        val ds = ArrayList<Int>()
        if (heroEra == battleEra) ds += p.manhattan(heroPos)
        _units.forEach { ds += p.manhattan(it.pos) }
        return ds.minOrNull() ?: 99
    }

    private fun occupiedByAlly(p: Pos) = (heroEra == battleEra && heroPos == p) || unitAt(p) != null

    private fun raze(p: Pos, events: MutableList<GameEvent>) {
        val r = timeline.addRaze(p)
        events += GameEvent.WallDamaged(p)
        events += GameEvent.CellsChanged(r.changed)
        handleVoided(r.voided, events)
        trapEnemies(events)
    }

    /** Saldırgan davranış (Alan 1–5 düşmanlarının davranışıyla aynıdır). */
    private fun aggressive(e: Enemy, targets: Set<Pos>, events: MutableList<GameEvent>) {
        val era = battleEra
        if (strike(e, events)) return
        val goalSet = if (targets.isNotEmpty()) targets else if (heroEra == era) setOf(heroPos) else _units.map { it.pos }.toSet()
        if (goalSet.isEmpty()) return
        val step = enemyStep(e, goalSet, throughWalls = false)
        if (step != null) {
            // Zaman Muhafızı ya da bir birlik o hücredeyse düşman içeri giremez.
            if (occupiedByAlly(step)) return
            if (step in targets) {
                moveEnemy(e, step, events)
                lose("${e.kind.title} korunan ${cell(era, step).displayName} hücresine ulaştı.")
                return
            }
            moveEnemy(e, step, events)
            return
        }
        // Yol yoksa: zaman manipülatörü duvarın üstünden faz atlar.
        if (e.strategy == EnemyStrategy.TIME_MANIPULATOR && e.phaseCooldown <= 0) {
            val jump = phaseJump(e, goalSet)
            if (jump != null) {
                moveEnemy(e, jump, events)
                e.phaseCooldown = 3
                log(JournalKind.BATTLE, "${e.kind.title} zamanı bükerek engelin öbür yanına geçti! $jump", era)
                return
            }
        }
        if (e.phaseCooldown > 0) e.phaseCooldown--
        // Kırılabilir bir duvar ya da kuleye yüklen; o da yoksa aynı çağdaki Zaman Muhafızı'nın ya da birliklerin peşine düş.
        val wallStep = enemyStep(e, goalSet, throughWalls = true)
        if (wallStep == null || !cell(era, wallStep).feature.damageable || cell(era, wallStep).feature == Feature.ENEMY_FORT) {
            val chaseGoals = buildSet { if (heroEra == era) add(heroPos); _units.forEach { add(it.pos) } } - goalSet
            if (chaseGoals.isNotEmpty()) {
                val chase = enemyStep(e, chaseGoals, throughWalls = false)
                if (chase != null && !occupiedByAlly(chase)) moveEnemy(e, chase, events)
            }
            return
        }
        val r = timeline.addDamage(wallStep, e.attack)
        events += GameEvent.WallDamaged(wallStep)
        events += GameEvent.CellsChanged(r.changed)
        handleVoided(r.voided, events)
        if (!cell(era, wallStep).feature.let { it.isWall || it.isTower }) log(JournalKind.BATTLE, "${e.kind.title} $wallStep hücresindeki yapıyı yıktı!", era)
    }

    /** En fazla 2 hücre uzaktaki, hedefe en yakın boş hücreye sıçrama (engelleri yok sayar). */
    private fun phaseJump(e: Enemy, goals: Set<Pos>): Pos? {
        val era = battleEra
        var best: Pos? = null
        var bestD = Int.MAX_VALUE
        val cur = goals.minOf { it.manhattan(e.pos) }
        for (dy in -2..2) for (dx in -2..2) {
            val p = Pos(e.pos.x + dx, e.pos.y + dy)
            if (p == e.pos || p.manhattan(e.pos) > 2 || !inBounds(p)) continue
            if (!cell(era, p).walkable || enemyAt(p) != null || occupiedByAlly(p) || p in goals) continue
            val d = goals.minOf { it.manhattan(p) }
            if (d < cur && (d < bestD || (d == bestD && (p.y < best!!.y || (p.y == best.y && p.x < best.x))))) { best = p; bestD = d }
        }
        return best
    }

    /** Düşman için BFS: hedef kümesine giden en kısa yolun ilk adımı. */
    private fun enemyStep(e: Enemy, goals: Set<Pos>, throughWalls: Boolean): Pos? {
        val era = battleEra
        val g = timeline.grid(era)
        fun passable(p: Pos): Boolean {
            if (!inBounds(p)) return false
            if (p in goals) return true
            val c = g[p]
            val ok = c.walkable || (throughWalls && c.feature.damageable && c.feature != Feature.ENEMY_FORT)
            return ok && _enemies.none { it !== e && it.pos == p } && unitAt(p) == null
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
            if (c.voided || !c.kind.playerChoosable) continue
            val later = timeline.eras.filter { it > c.era }
            val effects = later.map { era -> "${era.title}: ${cell(era, c.pos).displayName.ifEmpty { "boş" }}" }
            out += "${c.era.locative} ${c.kind.verb}" + if (effects.isNotEmpty()) " → ${effects.joinToString(" · ")}" else ""
        }
        return out
    }

    /** Geleceği önceden görme: eylem yapılmadan önce dünyanın hangi hücrelerinin nasıl değişeceğini hesaplar. */
    fun foresee(a: Action): Foresight? {
        if (validate(a) != null) return null
        val sim = replay(level, _actions + a)
        val changes = ArrayList<Triple<Era, Pos, Pair<CellState, CellState>>>()
        for (era in timeline.eras) {
            val g0 = timeline.grid(era); val g1 = sim.timeline.grid(era)
            for (p in g0.positions()) if (g0[p].feature != g1[p].feature || g0[p].terrain != g1[p].terrain) changes += Triple(era, p, g0[p] to g1[p])
        }
        val notes = ArrayList<String>()
        if (sim.paradoxCount > paradoxCount) notes += "Bu eylem bir paradoks yaratacak."
        if (sim.enemiesDefeated > enemiesDefeated) notes += "Düşman kaybı: ${sim.enemiesDefeated - enemiesDefeated}"
        if (sim.status == SessionStatus.WON) notes += "Bu eylemle bölüm kazanılır!"
        if (sim.status == SessionStatus.LOST) notes += "Dikkat: bu eylemden sonra bölüm kaybedilir (${sim.failReason})."
        return Foresight(changes, notes)
    }

    companion object {
        private val LOOT = setOf(Feature.FIELD, Feature.HUT, Feature.TOWN, Feature.CITY)
        private val SABOTAGE = setOf(
            Feature.BRIDGE_WOOD, Feature.BRIDGE_STONE, Feature.BRIDGE_TECH, Feature.PALISADE, Feature.STONE_WALL, Feature.ENERGY_WALL,
            Feature.HUT, Feature.TOWN, Feature.CITY, Feature.SAPLING, Feature.TREE, Feature.WATCHTOWER, Feature.STONE_TOWER, Feature.ENERGY_TURRET,
        )

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
