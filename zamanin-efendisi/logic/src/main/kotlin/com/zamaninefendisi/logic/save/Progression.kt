package com.zamaninefendisi.logic.save

import com.zamaninefendisi.logic.Action
import com.zamaninefendisi.logic.LevelSession
import com.zamaninefendisi.logic.SessionStatus
import com.zamaninefendisi.logic.levels.LevelCatalog

data class Reward(val gold: Int, val crystals: Int, val xp: Int, val firstClear: Boolean, val newStars: Int, val levelUp: Boolean)

/** Zaman Ustası seviyesi: her seviye bir öncekinden 50 deneyim puanı daha fazla ister. */
object MasterLevel {
    fun xpFor(level: Int): Int = (1 until level).sumOf { 100 + 50 * (it - 1) }
    fun levelOf(xp: Int): Int { var l = 1; while (xp >= xpFor(l + 1)) l++; return l }
    fun progress(xp: Int): Float { val l = levelOf(xp); val a = xpFor(l); val b = xpFor(l + 1); return (xp - a).toFloat() / (b - a) }
}

/** Kayıt verisi üzerindeki oyun kuralları (bölüm kilidi, ödüller, canlar). */
class Progression(private val saves: SaveSystem, clock: GameClock) {
    val lifeSystem = LifeSystem(clock)
    var data: SaveData = saves.load()
        private set

    init {
        data = data.copy(lives = lifeSystem.update(data.lives))
        persist()
    }

    fun persist() = saves.save(data)

    /** Yükleme sırasında kayıt sorunu yaşandıysa oyuncuya gösterilecek açıklama. */
    fun lastProblem(): String? = saves.lastLoadProblem

    fun update(f: (SaveData) -> SaveData) { data = f(data); persist() }

    fun progress(levelId: Int): LevelProgress = data.levels[levelId] ?: LevelProgress()

    fun isUnlocked(levelId: Int): Boolean = levelId == 1 || progress(levelId - 1).completed || progress(levelId).completed

    fun isCompleted(levelId: Int) = progress(levelId).completed

    fun totalStars(): Int = data.levels.values.sumOf { it.stars }

    fun refreshLives(): LivesState { data = data.copy(lives = lifeSystem.update(data.lives)); return data.lives }

    /** Ana bölüm denemesi can gerektirir; tamamlanmış bölümlerin tekrarı ücretsizdir. */
    fun needsLife(levelId: Int) = !isCompleted(levelId)

    fun canStart(levelId: Int): Boolean = isUnlocked(levelId) && (!needsLife(levelId) || refreshLives().lives > 0)

    fun markIntroSeen(levelId: Int) = update { it.copy(seenIntros = it.seenIntros + levelId) }

    fun saveInProgress(levelId: Int, actions: List<Action>) =
        update { it.copy(inProgress = if (actions.isEmpty()) null else InProgress(levelId, actions)) }

    fun clearInProgress() = update { it.copy(inProgress = null) }

    fun recordAttemptStart(levelId: Int) = update {
        val p = progress(levelId)
        it.copy(levels = it.levels + (levelId to p.copy(attempts = p.attempts + 1)))
    }

    fun recordFail(session: LevelSession) {
        val needLife = needsLife(session.level.id)
        update {
            it.copy(
                lives = if (needLife) lifeSystem.consume(it.lives) else lifeSystem.update(it.lives),
                stats = mergeStats(it.stats, session).copy(levelsFailed = it.stats.levelsFailed + 1),
                inProgress = null,
            )
        }
    }

    fun recordWin(session: LevelSession, nowMs: Long): Reward {
        check(session.status == SessionStatus.WON)
        val id = session.level.id
        val old = progress(id)
        val stars = session.stars()
        val first = !old.completed
        val newStars = (stars - old.stars).coerceAtLeast(0)
        val gold = if (first) 30 + 10 * stars else 5 + 10 * newStars
        val crystals = newStars + if (first && session.level.id % 5 == 0) 3 else 0
        val xp = if (first) 40 + 10 * stars else 10 + 10 * newStars
        val levelBefore = MasterLevel.levelOf(data.xp)
        val summary = session.causalSummary().take(6).map { SavedJournalEntry(id, "Bölüm $id · ${session.level.name}: $it", nowMs) }
        update {
            val st = mergeStats(it.stats, session)
            it.copy(
                levels = it.levels + (id to old.copy(
                    stars = maxOf(old.stars, stars),
                    bestActions = minOf(old.bestActions, session.actionsUsed),
                    completions = old.completions + 1,
                )),
                gold = it.gold + gold,
                crystals = it.crystals + crystals,
                xp = it.xp + xp,
                journal = (summary + it.journal).take(300),
                stats = st.copy(
                    levelsWon = st.levelsWon + 1,
                    fewestActionsWin = if (st.fewestActionsWin == 0) session.actionsUsed else minOf(st.fewestActionsWin, session.actionsUsed),
                ),
                inProgress = null,
            )
        }
        return Reward(gold, crystals, xp, first, newStars, MasterLevel.levelOf(data.xp) > levelBefore)
    }

    private fun mergeStats(s: Stats, session: LevelSession) = s.copy(
        eraSwitches = s.eraSwitches + session.actions.count { it is Action.SwitchEra },
        buildings = s.buildings + session.timeline.changes.count { !it.voided },
        paradoxes = s.paradoxes + session.paradoxCount,
        enemiesDefeated = s.enemiesDefeated + session.enemiesDefeated,
    )

    fun resetAll() {
        val keepSettings = data.settings
        data = SaveData(settings = keepSettings, lives = lifeSystem.refill(LivesState()))
        persist()
    }

    fun nextPlayable(): Int = LevelCatalog.levels.firstOrNull { !isCompleted(it.id) }?.id ?: LevelCatalog.lastId
}
