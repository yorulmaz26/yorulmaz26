package com.zamaninefendisi.logic.save

import com.zamaninefendisi.logic.Action
import com.zamaninefendisi.logic.LevelSession
import com.zamaninefendisi.logic.SessionStatus
import com.zamaninefendisi.logic.levels.LevelCatalog
import com.zamaninefendisi.logic.meta.BuildingType
import com.zamaninefendisi.logic.meta.Kingdom
import com.zamaninefendisi.logic.meta.ResearchTree
import com.zamaninefendisi.logic.meta.Resource
import com.zamaninefendisi.logic.meta.SafeElapsed
import com.zamaninefendisi.logic.meta.Shop
import com.zamaninefendisi.logic.meta.ShopCategory
import com.zamaninefendisi.logic.meta.has
import com.zamaninefendisi.logic.meta.minus
import com.zamaninefendisi.logic.meta.plus
import kotlin.math.roundToInt

data class Reward(
    val gains: Map<Resource, Int>,
    val xp: Int,
    val firstClear: Boolean,
    val newStars: Int,
    val levelUp: Boolean,
    val comboGold: Int = 0,
) {
    val gold: Int get() = gains[Resource.GOLD] ?: 0
    val crystals: Int get() = gains[Resource.CRYSTAL] ?: 0
}

/** Zaman Ustası seviyesi: her seviye bir öncekinden 50 deneyim puanı daha fazla ister. */
object MasterLevel {
    fun xpFor(level: Int): Int = (1 until level).sumOf { 100 + 50 * (it - 1) }
    fun levelOf(xp: Int): Int { var l = 1; while (xp >= xpFor(l + 1)) l++; return l }
    fun progress(xp: Int): Float { val l = levelOf(xp); val a = xpFor(l); val b = xpFor(l + 1); return (xp - a).toFloat() / (b - a) }
}

/** Kayıt verisi üzerindeki oyun kuralları (bölüm kilidi, ödüller, canlar, kale, araştırma, market). */
class Progression(private val saves: SaveSystem, private val clock: GameClock, tzOffsetMs: () -> Long = { 0L }) {
    val lifeSystem = LifeSystem(clock)
    val continuity = ContinuityManager(this, clock, tzOffsetMs)
    var data: SaveData = saves.load()
        private set

    init {
        data = data.copy(lives = lifeSystem.update(data.lives))
        tickProduction()
        persist()
        continuity.tick()
    }

    fun persist() = saves.save(data)

    /** Yükleme sırasında kayıt sorunu yaşandıysa oyuncuya gösterilecek açıklama. */
    fun lastProblem(): String? = saves.lastLoadProblem

    fun update(f: (SaveData) -> SaveData) { data = f(data); persist() }

    // ------------------------------------------------------------------ Bölümler ve canlar

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

    fun amount(r: Resource): Int = data.wallet[r] ?: 0

    /** Kale ve araştırmadan gelen savaş bonusu (bölüm simülasyonuna verilir). */
    fun heroHpBonus(): Int = ResearchTree.heroHpBonus(data.research, data.buildings[BuildingType.ACADEMY] ?: 0)
    fun foresightCharges(): Int? = ResearchTree.foresightCharges(data.research, data.buildings[BuildingType.TECH_CENTER] ?: 0)
    fun hasGuide(): Boolean = ResearchTree.GUIDE in data.research

    /** Zaman Rehberi: 1 kristal harcar. */
    fun spendGuide(): Boolean {
        if (!hasGuide() || amount(Resource.CRYSTAL) < 1) return false
        update { it.copy(wallet = it.wallet.minus(mapOf(Resource.CRYSTAL to 1))) }
        return true
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
        val lv = session.level
        val id = lv.id
        val old = progress(id)
        val stars = session.stars()
        val first = !old.completed
        val newStars = (stars - old.stars).coerceAtLeast(0)
        val research = data.research
        val combat = lv.hasEnemies
        val paradoxBonus = if (ResearchTree.PARADOX_SHIELD in research && session.paradoxCount > 0) 1.2 else 1.0
        val goldMult = (1.0 + (if (ResearchTree.GOLD_10 in research) 0.1 else 0.0) + 0.05 * (data.buildings[BuildingType.ARCHIVE] ?: 0)) * paradoxBonus
        val comboGold = 10 * session.combos
        var gold = (if (first) 30 + 10 * stars else 5 + 10 * newStars) * goldMult
        if (combat) {
            gold += 5 * (data.buildings[BuildingType.DEFENSE_TOWER] ?: 0)
            if (ResearchTree.SPOILS in research) gold += 3 * session.enemiesDefeated
        }
        val science = ((if (first) 5 + 5 * stars else 2) * (if (ResearchTree.SCIENCE_10 in research) 1.1 else 1.0) * paradoxBonus).roundToInt()
        val gains = HashMap<Resource, Int>()
        gains[Resource.GOLD] = gold.roundToInt() + comboGold
        gains[Resource.SCIENCE] = science
        gains[Resource.CRYSTAL] = newStars + if (first && id % 5 == 0) 3 else 0
        if (first) {
            gains[Resource.WOOD] = 10 + 2 * lv.area
            gains[Resource.STONE] = 6 + 2 * lv.area
            gains[Resource.IRON] = 2 + lv.area
            if (id % 5 == 0) gains[Resource.SHARD] = 1
            if (id % 25 == 0) gains[Resource.SEAL] = 1
        }
        val paradoxEnergy = session.paradoxCount * (if (ResearchTree.PARADOX_HARVEST in research) 3 else 1)
        if (paradoxEnergy > 0) gains[Resource.PARADOX] = paradoxEnergy
        val xpMult = 1.0 + 0.05 * (data.buildings[BuildingType.MUSEUM] ?: 0) + (if (combat && ResearchTree.VETERAN in research) 0.25 else 0.0)
        val xp = ((if (first) 40 + 10 * stars else 10 + 10 * newStars) * xpMult).roundToInt()
        val levelBefore = MasterLevel.levelOf(data.xp)
        val summary = session.causalSummary().take(6).map { SavedJournalEntry(id, "Bölüm $id · ${lv.name}: $it", nowMs) }
        update {
            val st = mergeStats(it.stats, session)
            it.copy(
                levels = it.levels + (id to old.copy(
                    stars = maxOf(old.stars, stars),
                    bestActions = minOf(old.bestActions, session.actionsUsed),
                    completions = old.completions + 1,
                )),
                wallet = it.wallet.plus(gains),
                xp = it.xp + xp,
                journal = (summary + it.journal).take(300),
                stats = st.copy(
                    levelsWon = st.levelsWon + 1,
                    fewestActionsWin = if (st.fewestActionsWin == 0) session.actionsUsed else minOf(st.fewestActionsWin, session.actionsUsed),
                ),
                inProgress = null,
            )
        }
        continuity.onLevelWon(session)
        return Reward(gains.filterValues { it > 0 }, xp, first, newStars, MasterLevel.levelOf(data.xp) > levelBefore, comboGold)
    }

    private fun mergeStats(s: Stats, session: LevelSession) = s.copy(
        bestCombo = maxOf(s.bestCombo, session.combos),
        unitsLost = s.unitsLost + (session.level.units.size - session.units.size),
        eraSwitches = s.eraSwitches + session.actions.count { it is Action.SwitchEra },
        buildings = s.buildings + session.timeline.changes.count { !it.voided },
        paradoxes = s.paradoxes + session.paradoxCount,
        enemiesDefeated = s.enemiesDefeated + session.enemiesDefeated,
    )

    // ------------------------------------------------------------------ Zaman Kalesi

    /** Geçen süreye göre üretimi biriktirir; Zaman Otomatı araştırıldıysa doğrudan cüzdana ekler. */
    fun tickProduction() {
        val st = data.production
        val nowWall = clock.wallMs(); val nowEl = clock.elapsedMs(); val boot = clock.bootId()
        val delta = SafeElapsed.delta(st.lastWallMs, st.lastElapsedMs, st.bootId, nowWall, nowEl, boot)
        var pending = Kingdom.accumulate(data.buildings, data.research, st.pending, delta)
        var wallet = data.wallet
        if (ResearchTree.AUTO_COLLECT in data.research) {
            val whole = pending.mapValues { it.value.toInt() }.filterValues { it > 0 }
            wallet = wallet.plus(whole)
            pending = pending.mapValues { it.value - it.value.toInt() }
        }
        data = data.copy(wallet = wallet, production = ProductionState(pending, nowWall, nowEl, boot))
    }

    /** Biriken üretimi toplar; toplanan miktarları döner. */
    fun collectProduction(): Map<Resource, Int> {
        tickProduction()
        val whole = data.production.pending.mapValues { it.value.toInt() }.filterValues { it > 0 }
        data = data.copy(
            wallet = data.wallet.plus(whole),
            production = data.production.copy(pending = data.production.pending.mapValues { it.value - it.value.toInt() }),
        )
        persist()
        if (whole.isNotEmpty()) continuity.event(com.zamaninefendisi.logic.continuity.QuestType.COLLECT_PRODUCTION)
        return whole
    }

    fun upgradeBuilding(b: BuildingType): Boolean {
        tickProduction()
        val chk = Kingdom.canUpgrade(b, data.buildings, data.wallet)
        if (!chk.ok) return false
        val next = (data.buildings[b] ?: 0) + 1
        update { it.copy(wallet = it.wallet.minus(b.costFor(next)), buildings = it.buildings + (b to next)) }
        continuity.event(com.zamaninefendisi.logic.continuity.QuestType.UPGRADE_BUILDING)
        return true
    }

    fun research(id: String): Boolean {
        val n = ResearchTree.node(id) ?: return false
        val chk = ResearchTree.canResearch(n, data.research, data.buildings[BuildingType.LAB] ?: 0, data.wallet)
        if (!chk.ok) return false
        update { it.copy(wallet = it.wallet.minus(n.cost), research = it.research + id) }
        return true
    }

    // ------------------------------------------------------------------ Market

    fun buy(itemId: String): String? {
        val item = Shop.item(itemId) ?: return "Ürün bulunamadı"
        if (item.requiresResearch != null && item.requiresResearch !in data.research) return "Bu ürün için araştırma gerekli"
        if (!item.consumable && itemId in data.owned) return "Zaten sahipsin"
        val price = Shop.priceFor(item, data.buildings[BuildingType.TRADE] ?: 0)
        if (!data.wallet.has(price)) return "Yetersiz kaynak"
        update {
            var w = it.wallet.minus(price)
            if (item.consumable) w = w.plus(item.grant)
            it.copy(
                wallet = w,
                owned = if (item.consumable) it.owned else it.owned + itemId,
                equipped = if (item.consumable) it.equipped else it.equipped + (item.category to itemId),
                continuity = it.continuity.copy(shopBought = it.continuity.shopBought + 1),
            )
        }
        return null
    }

    fun equip(itemId: String): Boolean {
        val item = Shop.item(itemId) ?: return false
        if (item.consumable || itemId !in data.owned) return false
        update { it.copy(equipped = it.equipped + (item.category to itemId)) }
        return true
    }

    fun equipped(c: ShopCategory): String? = data.equipped[c]

    fun kingdomPower(): Int = Kingdom.power(data.buildings)

    fun resetAll() {
        val keepSettings = data.settings
        data = SaveData(settings = keepSettings, lives = lifeSystem.refill(LivesState()))
        tickProduction()
        persist()
    }

    fun nextPlayable(): Int = LevelCatalog.ids.firstOrNull { !isCompleted(it) } ?: LevelCatalog.lastId
}
