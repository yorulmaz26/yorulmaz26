package com.zamaninefendisi.logic.save

import com.zamaninefendisi.logic.Action
import com.zamaninefendisi.logic.BuildKind
import com.zamaninefendisi.logic.Era
import com.zamaninefendisi.logic.LevelDef
import com.zamaninefendisi.logic.LevelSession
import com.zamaninefendisi.logic.SessionStatus
import com.zamaninefendisi.logic.continuity.*
import com.zamaninefendisi.logic.levels.LevelCatalog
import com.zamaninefendisi.logic.meta.BuildingType
import com.zamaninefendisi.logic.meta.ResearchTree
import com.zamaninefendisi.logic.meta.Resource
import com.zamaninefendisi.logic.meta.SafeElapsed
import com.zamaninefendisi.logic.meta.minus
import com.zamaninefendisi.logic.meta.plus

/** Süreklilik sistemlerinin kuralları: görevler, günlük/haftalık mücadeleler, sonsuz mod, seferler, koleksiyon, başarımlar, prestij. */
class ContinuityManager(private val p: Progression, private val clock: GameClock, private val tzOffsetMs: () -> Long) {
    private val c get() = p.data.continuity
    private fun set(f: (ContinuityState) -> ContinuityState) = p.update { it.copy(continuity = f(it.continuity)) }

    fun today(): Int = GameCalendar.day(clock.wallMs(), tzOffsetMs())
    fun thisWeek(): Int = GameCalendar.week(today())

    // ------------------------------------------------------------------ Zaman akışı (seferler, rastgele olaylar)

    /** Seferleri ve oyun süresini manipülasyona dayanıklı saatle ilerletir; gerekirse rastgele olay üretir. */
    fun tick(): TimeEvent? {
        val nowW = clock.wallMs(); val nowE = clock.elapsedMs(); val boot = clock.bootId()
        val d = SafeElapsed.delta(c.expClockWall, c.expClockElapsed, c.expClockBoot, nowW, nowE, boot)
        ensurePeriods()
        var ev: TimeEvent? = null
        set { st ->
            val exps = st.expeditions.map { it.copy(progressMs = minOf(it.durationMs, it.progressMs + d)) }
            var s2 = st.copy(expeditions = exps, playMs = st.playMs + d, expClockWall = nowW, expClockElapsed = nowE, expClockBoot = boot)
            if (s2.playMs - s2.lastEventPlayMs >= EVENT_INTERVAL && st.expClockWall != 0L) {
                ev = TimeEvent.entries[s2.eventCounter % TimeEvent.entries.size]
                s2 = s2.copy(lastEventPlayMs = s2.playMs, eventCounter = s2.eventCounter + 1, lastEvent = ev)
            }
            s2
        }
        ev?.let { applyEvent(it) }
        return ev
    }

    private fun applyEvent(e: TimeEvent) {
        when (e) {
            TimeEvent.STORM -> p.update { d -> d.copy(production = d.production.copy(pending = d.production.pending.mapValues { it.value * 1.5 })) }
            TimeEvent.MERCHANT -> if (p.amount(Resource.GOLD) >= 60) p.update { d -> d.copy(wallet = d.wallet.minus(mapOf(Resource.GOLD to 60)).plus(mapOf(Resource.CRYSTAL to 2))) }
            TimeEvent.CRYSTAL_RAIN -> p.update { d -> d.copy(wallet = d.wallet.plus(mapOf(Resource.CRYSTAL to 3))) }
            TimeEvent.VISITOR -> grantItem(c.eventCounter * 131L, null)
            TimeEvent.DISCOVERY -> p.update { d -> d.copy(wallet = d.wallet.plus(mapOf(Resource.SCIENCE to 40))) }
            TimeEvent.RIFT -> set { st -> st.copy(expeditions = st.expeditions.map { it.copy(progressMs = minOf(it.durationMs, it.progressMs + 3_600_000L)) }) }
        }
    }

    fun consumeLastEvent(): TimeEvent? { val e = c.lastEvent; if (e != null) set { it.copy(lastEvent = null) }; return e }

    // ------------------------------------------------------------------ Görevler

    fun ensurePeriods() {
        val day = today(); val week = thisWeek()
        if (c.dailyQuests.period != day || c.weeklyQuests.period != week) set { st ->
            st.copy(
                dailyQuests = if (st.dailyQuests.period == day) st.dailyQuests else Quests.daily(day, clock.wallMs()),
                weeklyQuests = if (st.weeklyQuests.period == week) st.weeklyQuests else Quests.weekly(week, clock.wallMs()),
            )
        }
    }

    fun event(type: QuestType, n: Int = 1) {
        if (n <= 0) return
        ensurePeriods()
        set { st ->
            fun bump(b: QuestBoard) = b.copy(progress = b.progress + (type to ((b.progress[type] ?: 0) + n)))
            st.copy(dailyQuests = bump(st.dailyQuests), weeklyQuests = bump(st.weeklyQuests))
        }
    }

    fun claimQuest(weekly: Boolean, index: Int): Boolean {
        ensurePeriods()
        val board = if (weekly) c.weeklyQuests else c.dailyQuests
        val q = board.quests.getOrNull(index) ?: return false
        if (index in board.claimed || (board.progress[q.type] ?: 0) < q.target) return false
        val bonus = 1.0 + 0.1 * (p.data.buildings[BuildingType.QUEST_CENTER] ?: 0)
        val reward = q.reward.mapValues { (it.value * bonus).toInt() }
        val took = clock.wallMs() - board.startedWallMs
        p.update { it.copy(wallet = it.wallet.plus(reward)) }
        set { st ->
            val nb = board.copy(claimed = board.claimed + index)
            st.copy(
                dailyQuests = if (weekly) st.dailyQuests else nb,
                weeklyQuests = if (weekly) nb else st.weeklyQuests,
                questsClaimedTotal = st.questsClaimedTotal + 1,
                fastestQuestMs = if (took > 0 && (st.fastestQuestMs == 0L || took < st.fastestQuestMs)) took else st.fastestQuestMs,
            )
        }
        return true
    }

    fun onLevelWon(s: LevelSession) {
        event(QuestType.WIN_LEVELS)
        if (s.stars() == 3) event(QuestType.THREE_STARS)
        event(QuestType.SWITCH_ERAS, s.actions.count { it is Action.SwitchEra })
        event(QuestType.MAKE_CHANGES, s.timeline.changes.count { !it.voided && it.kind.playerChoosable })
        event(QuestType.DEFEAT_ENEMIES, s.enemiesDefeated)
        event(QuestType.PARADOXES, s.paradoxCount)
        if (s.level.id % 5 == 0 && s.level.id < 100_000) grantItem(s.level.id * 7L, Era.entries[(s.level.id / 5) % 3])
    }

    // ------------------------------------------------------------------ Günlük bulmaca ve haftalık kriz

    fun dailyLevel(day: Int = today()): LevelDef =
        LevelGenerator.generate(200_000 + (day and 0xFFFF), day * 31L + 7, GenRules(slack = 8), name = "Günün Bulmacası").level

    fun recordDaily(s: LevelSession, day: Int = today()): Map<Resource, Int> {
        if (s.status != SessionStatus.WON) return emptyMap()
        val first = day !in c.dailyClaimed
        set { st -> st.copy(dailyBest = st.dailyBest + (day to minOf(st.dailyBest[day] ?: Int.MAX_VALUE, s.actionsUsed)), dailyClaimed = st.dailyClaimed + day) }
        if (!first) return emptyMap()
        event(QuestType.DAILY_PUZZLE)
        val reward = mapOf(Resource.GOLD to 80, Resource.CRYSTAL to 1, Resource.SCIENCE to 20)
        p.update { it.copy(wallet = it.wallet.plus(reward)) }
        grantItem(day * 13L, null)
        return reward
    }

    class Crisis(val title: String, val rule: String, val rules: GenRules)

    fun weeklyCrisis(week: Int = thisWeek()): Crisis = when (Math.floorMod(week, 4)) {
        0 -> Crisis("Kıt Zaman", "Zaman enerjisi yalnızca çözüm kadar: her geçiş sayılı.", GenRules(energyLimited = true, slack = 4))
        1 -> Crisis("Kızgın Kumlar", "Çöl iki kat hızlı ilerliyor.", GenRules(desertSpeed = 2, slack = 5))
        2 -> Crisis("Akıncı Seli", "Düşmanlar kalabalık: her dalgada fazladan akıncı.", GenRules(extraEnemies = 2, slack = 4))
        else -> Crisis("Daralan An", "Tur sınırı neredeyse çözüm kadar kısa ve hedef Gelecek'te.", GenRules(slack = 2, futureGoal = true))
    }

    fun weeklyLevel(week: Int = thisWeek()): LevelDef {
        val cr = weeklyCrisis(week)
        return LevelGenerator.generate(300_000 + (week and 0xFFFF), week * 97L + 5, cr.rules, name = "Haftalık Kriz: ${cr.title}").level
    }

    fun recordWeekly(s: LevelSession, week: Int = thisWeek()): Map<Resource, Int> {
        if (s.status != SessionStatus.WON || week in c.weeklyClaimed) return emptyMap()
        set { it.copy(weeklyClaimed = it.weeklyClaimed + week) }
        val reward = mapOf(Resource.GOLD to 250, Resource.CRYSTAL to 4, Resource.SEAL to 1)
        p.update { it.copy(wallet = it.wallet.plus(reward)) }
        grantItem(week * 17L, Era.FUTURE)
        return reward
    }

    // ------------------------------------------------------------------ Sonsuz Zaman Modu

    fun startEndless(): EndlessRun {
        val run = EndlessRun(seed = clock.wallMs() xor 0x5DEECE66DL, stage = 1)
        set { it.copy(endlessRun = run) }
        return run
    }

    fun endlessLevel(run: EndlessRun): LevelDef {
        val n = run.stage
        val r = kotlin.random.Random(run.seed + n)
        val rules = GenRules(
            energyLimited = n % 3 == 0,
            desertSpeed = if (n >= 6 && r.nextBoolean()) 2 else 1,
            extraEnemies = n / 5,
            slack = maxOf(2, 8 - n / 3),
            futureGoal = n >= 4 && r.nextBoolean(),
        )
        return LevelGenerator.generate(100_000 + n, run.seed * 31 + n, rules, name = "Sonsuz Zaman · Aşama $n").level
    }

    fun endlessResult(s: LevelSession): EndlessRun? {
        val run = c.endlessRun ?: return null
        if (s.status == SessionStatus.WON) {
            event(QuestType.ENDLESS_STAGES)
            val reward = mapOf(Resource.GOLD to 15 + 5 * run.stage, Resource.SCIENCE to 3 + run.stage)
            p.update { it.copy(wallet = it.wallet.plus(reward), xp = it.xp + 10 + 2 * run.stage) }
            val next = run.copy(stage = run.stage + 1)
            set { it.copy(endlessRun = next, endlessBest = maxOf(it.endlessBest, run.stage)) }
            if (run.stage % 5 == 0) grantItem(run.seed + run.stage, null)
            return next
        }
        set { it.copy(endlessRun = null, endlessBest = maxOf(it.endlessBest, run.stage - 1)) }
        return null
    }

    // ------------------------------------------------------------------ Zaman seferleri

    fun expeditionSlots(): Int = maxOf(1, p.data.buildings[BuildingType.PORTAL] ?: 0)

    fun startExpedition(kind: ExpeditionKind): String? {
        tick()
        if (c.expeditions.size >= expeditionSlots()) return "Bütün sefer yuvaları dolu (Zaman Portalı'nı geliştir)"
        set { it.copy(expeditions = it.expeditions + Expedition(it.expeditionCounter, kind), expeditionCounter = it.expeditionCounter + 1) }
        return null
    }

    fun claimExpedition(id: Int): Pair<Map<Resource, Int>, CollectionItem?>? {
        tick()
        val e = c.expeditions.firstOrNull { it.id == id && it.done } ?: return null
        p.update { it.copy(wallet = it.wallet.plus(e.kind.reward)) }
        set { st -> st.copy(expeditions = st.expeditions.filter { it.id != id }, expeditionsDone = st.expeditionsDone + 1) }
        event(QuestType.EXPEDITION)
        val roll = kotlin.random.Random(id * 7919L + e.kind.ordinal).nextInt(100)
        val item = if (roll < e.kind.itemChance) grantItem(id * 104729L, e.kind.era) else null
        return e.kind.reward to item
    }

    // ------------------------------------------------------------------ Koleksiyon

    fun grantItem(seed: Long, era: Era?): CollectionItem? {
        val item = CivCollection.roll(c.collection, seed, era) ?: return null
        set { it.copy(collection = it.collection + item.id) }
        return item
    }

    // ------------------------------------------------------------------ Alternatif tarih görevleri

    fun altTask(level: LevelDef): AltTaskKind = when {
        level.hasEnemies && level.id % 2 == 0 && level.units.isEmpty() -> AltTaskKind.PEACEFUL
        else -> listOf(AltTaskKind.FLAWLESS, AltTaskKind.FEW_SWITCHES, AltTaskKind.NO_WAIT)[level.id % 3]
    }

    fun altMet(level: LevelDef, s: LevelSession): Boolean {
        if (s.status != SessionStatus.WON) return false
        return when (altTask(level)) {
            AltTaskKind.FLAWLESS -> s.paradoxCount == 0 && s.stars() == 3
            AltTaskKind.FEW_SWITCHES -> s.actions.count { it is Action.SwitchEra } <= level.solution.count { it is Action.SwitchEra }
            AltTaskKind.PEACEFUL -> s.actions.none { it is Action.Attack }
            AltTaskKind.NO_WAIT -> s.actions.none { it == Action.Wait } && s.actionsUsed <= level.par
        }
    }

    fun recordAlt(level: LevelDef, s: LevelSession): Boolean {
        val key = "alt_${level.id}"
        if (key in c.altDone || !altMet(level, s)) return false
        set { it.copy(altDone = it.altDone + key) }
        p.update { it.copy(wallet = it.wallet.plus(mapOf(Resource.SHARD to 1, Resource.GOLD to 50))) }
        grantItem(level.id * 31L, null)
        return true
    }

    // ------------------------------------------------------------------ Prestij: Yeni Zaman Döngüsü

    fun prestigeAvailable(): Boolean = LevelCatalog.ids.all { p.isCompleted(it) }

    /** Döngü kuralı: tur sınırı çözüme yaklaşır, kale bonusları devre dışıdır. */
    fun prestigeLevel(level: LevelDef): LevelDef {
        val n = c.prestige + 1
        val survive = level.goals.any { it is com.zamaninefendisi.logic.Goal.Survive }
        val tight = if (survive) level.maxTurns else minOf(level.maxTurns, level.solution.size + maxOf(2, 7 - n))
        return level.rebrand(name = "${level.name} · Döngü $n", intro = "Yeni Zaman Döngüsü $n: aynı tarih, daha az zaman. " + level.intro, maxTurns = tight, hints = emptyList())
    }

    fun recordPrestige(levelId: Int, s: LevelSession): Boolean {
        if (s.status != SessionStatus.WON) return false
        val done = c.cycleDone + levelId
        if (LevelCatalog.ids.all { it in done }) {
            set { it.copy(prestige = it.prestige + 1, cycleDone = emptySet()) }
            p.update { it.copy(wallet = it.wallet.plus(mapOf(Resource.SEAL to 3, Resource.CRYSTAL to 10))) }
            return true
        }
        set { it.copy(cycleDone = done) }
        return false
    }

    // ------------------------------------------------------------------ Krallık Savunması

    fun defenseLevel(wave: Int): LevelDef {
        val g = LevelGenerator.generate(400_000 + wave, wave * 977L + 1, GenRules(extraEnemies = wave / 2, slack = 3), name = "Krallık Savunması · Dalga $wave", only = LevelGenerator.Template.DEFENSE).level
        val towers = p.data.buildings[BuildingType.DEFENSE_TOWER] ?: 0
        return LevelDef(
            id = g.id, area = 0, name = g.name, intro = "Farklı çağlardan gelen akıncılar kaleye saldırıyor. Savunma Kulesi seviyen kadar fazladan odunla kule ve sur kur.",
            map = g.map, eras = g.eras, startEra = g.startEra, startWood = g.startWood + 2 * towers,
            allowed = g.allowed.mapValues { it.value + BuildKind.TOWER }, goals = g.goals, maxTurns = g.maxTurns, par = g.par,
            enemies = g.enemies, protect = g.protect, solution = g.solution,
        )
    }

    fun recordDefense(wave: Int, s: LevelSession): Boolean {
        if (s.status != SessionStatus.WON) { set { it.copy(defenseWave = 0) }; return false }
        event(QuestType.DEFENSE_WAVES)
        set { it.copy(defenseWave = wave, defenseBest = maxOf(it.defenseBest, wave)) }
        p.update { it.copy(wallet = it.wallet.plus(mapOf(Resource.GOLD to 30 + 10 * wave, Resource.STONE to 10 + 2 * wave))) }
        return true
    }

    // ------------------------------------------------------------------ Başarımlar

    fun checkAchievements(): List<Achievement> {
        val d = p.data
        val st = d.stats
        fun areaDone(a: Int) = LevelCatalog.area(a)?.ids?.all { p.isCompleted(it) } == true
        val ok = mapOf(
            "first_win" to (st.levelsWon >= 1), "area1" to areaDone(1), "area5" to areaDone(5), "area7" to areaDone(7),
            "stars30" to (p.totalStars() >= 30), "stars90" to (p.totalStars() >= 90), "switch100" to (st.eraSwitches >= 100),
            "changes200" to (st.buildings >= 200), "paradox10" to (st.paradoxes >= 10), "enemies50" to (st.enemiesDefeated >= 50),
            "combo5" to (st.bestCombo >= 2), "core3" to ((d.buildings[BuildingType.CORE] ?: 0) >= 3), "core5" to ((d.buildings[BuildingType.CORE] ?: 0) >= 5),
            "research5" to (d.research.size >= 5), "research20" to (d.research.size >= ResearchTree.nodes.size),
            "collect25" to (c.collection.size >= 25), "collect75" to (c.collection.size >= 75), "collect150" to (c.collection.size >= CivCollection.items.size),
            "set1" to CivCollection.completedSets(c.collection).isNotEmpty(), "endless10" to (c.endlessBest >= 10), "endless25" to (c.endlessBest >= 25),
            "daily7" to (c.dailyClaimed.size >= 7), "weekly1" to c.weeklyClaimed.isNotEmpty(), "exped5" to (c.expeditionsDone >= 5),
            "defense5" to (c.defenseBest >= 5), "alt5" to (c.altDone.size >= 5), "prestige1" to (c.prestige >= 1),
            "master10" to (MasterLevel.levelOf(d.xp) >= 10), "shop5" to (c.shopBought >= 5), "quests10" to (c.questsClaimedTotal >= 10),
        )
        val fresh = Achievements.all.filter { ok[it.id] == true && it.id !in c.achievements }
        if (fresh.isNotEmpty()) {
            set { it.copy(achievements = it.achievements + fresh.map { a -> a.id }) }
            p.update { it.copy(wallet = it.wallet.plus(mapOf(Resource.CRYSTAL to fresh.sumOf { a -> a.reward }))) }
        }
        return fresh
    }

    companion object {
        const val EVENT_INTERVAL = 3 * 3_600_000L
    }
}
