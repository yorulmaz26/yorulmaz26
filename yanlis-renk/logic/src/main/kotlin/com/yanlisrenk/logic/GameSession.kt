package com.yanlisrenk.logic

import com.yanlisrenk.logic.level.AreaCatalog
import com.yanlisrenk.logic.life.LifeManager
import com.yanlisrenk.logic.progress.Achievement
import com.yanlisrenk.logic.progress.Achievements
import com.yanlisrenk.logic.progress.DailyRewards
import com.yanlisrenk.logic.progress.Missions
import com.yanlisrenk.logic.progress.PlayerLevels
import com.yanlisrenk.logic.progress.Stat
import com.yanlisrenk.logic.progress.WeeklyChallenge
import com.yanlisrenk.logic.save.MissionSlot
import com.yanlisrenk.logic.save.SaveData
import com.yanlisrenk.logic.save.SaveManager
import com.yanlisrenk.logic.save.Txn
import com.yanlisrenk.logic.shop.Currency
import com.yanlisrenk.logic.shop.ShopCatalog
import com.yanlisrenk.logic.shop.ShopCategory
import com.yanlisrenk.logic.shop.ShopItem
import com.yanlisrenk.logic.time.PlatformClock
import com.yanlisrenk.logic.time.TrustedClock

data class LevelResult(
    val levelId: Int,
    val mastery: Boolean,
    val stars: Int,
    val taps: Int,
    val coins: Int,
    val diamonds: Int,
    val gates: Int,
    val mistakes: Int,
)

data class Reward(
    var gold: Int = 0,
    var diamonds: Int = 0,
    var xp: Int = 0,
    var newStars: Int = 0,
    var firstClear: Boolean = false,
    var areaCompleted: Int = 0,
    var collectible: String? = null,
    var worldCompleted: Int = 0,
    var levelUps: MutableList<Int> = mutableListOf(),
    var achievements: MutableList<Achievement> = mutableListOf(),
    var items: MutableList<String> = mutableListOf(),
)

enum class PurchaseResult { OK, NOT_ENOUGH, ALREADY_OWNED, LOCKED_LEVEL, NOT_FOR_SALE }

/**
 * Oyunun tüm meta sistemlerini (ekonomi, market, can, görev, başarı, ilerleme) tek yerde
 * toplayan, arayüzden bağımsız yönetici. Arayüz yalnızca bu sınıfın metotlarını çağırır.
 */
class GameSession(
    private val saveManager: SaveManager,
    platformClock: PlatformClock,
    /** Yerel saat dilimi farkı (ms) – gün sınırı için. */
    private val tzOffsetMillis: () -> Long = { 3 * 3_600_000L },
) {
    val data: SaveData = saveManager.load()
    val loadOutcome = saveManager.lastOutcome
    val clock = TrustedClock(platformClock, data.clock)
    val lives = LifeManager(data.lives)
    private var dirty = true
    /** Arayüzün göstermesi için yeni açılan başarılar. */
    val pendingAchievementToasts = ArrayList<Achievement>()

    init {
        val d = clock.tick()
        lives.credit(d)
        if (data.createdAt == 0L) data.createdAt = clock.now()
        // önceki oturumda yarım kalan bölüm: başarısız sayılır
        if (data.pendingAttempt != 0) {
            lives.onLevelFailed()
            data.pendingAttempt = 0
        }
        refreshMissions()
        checkAchievements()
        save()
    }

    // ---------- zaman ----------

    fun dayKey(): Long = Math.floorDiv(clock.dayNow() + tzOffsetMillis(), 86_400_000L)
    fun weekKey(): Long = Math.floorDiv(dayKey() + 3, 7L)

    /** Kare başına değil, ~1 sn'de bir çağrılır. Dolan can sayısını döndürür. */
    fun update(): Int {
        val gained = lives.credit(clock.tick())
        if (gained > 0) dirty = true
        refreshMissions()
        return gained
    }

    /** Sunucu zamanı alındı. Yeniden başlatma sonrası doğrulanan süreyi canlara ekler. */
    fun applyServerTime(serverNow: Long): Long {
        val credit = clock.syncServer(serverNow)
        lives.credit(credit)
        dirty = true
        save()
        return credit
    }

    fun save() {
        saveManager.save(data)
        dirty = false
    }

    fun saveIfDirty() { if (dirty) save() }

    // ---------- cüzdan ----------

    val gold get() = data.gold
    val diamonds get() = data.diamonds

    /**
     * Para ekler. [rewardId] verilirse aynı ödül ikinci kez verilmez.
     * @return verildiyse true
     */
    fun credit(currency: Currency, amount: Int, reason: String, rewardId: String? = null): Boolean {
        if (amount <= 0) return false
        if (rewardId != null && !data.grantedRewards.add(rewardId)) return false
        when (currency) {
            Currency.GOLD -> { data.gold += amount; data.addStat(Stat.GOLD_EARNED, amount.toLong()) }
            Currency.DIAMOND -> data.diamonds += amount
        }
        log(currency, amount, reason)
        return true
    }

    /** Harcama; bakiye yetmezse hiçbir şey değişmez. */
    fun spend(currency: Currency, amount: Int, reason: String): Boolean {
        if (amount < 0) return false
        val bal = if (currency == Currency.GOLD) data.gold else data.diamonds
        if (bal < amount) return false
        if (currency == Currency.GOLD) data.gold -= amount else data.diamonds -= amount
        log(currency, -amount, reason)
        return true
    }

    private fun log(c: Currency, amount: Int, reason: String) {
        val bal = if (c == Currency.GOLD) data.gold else data.diamonds
        data.ledger.add(Txn(data.nextTxnId++, c.name, amount, bal, reason, clock.now()))
        while (data.ledger.size > 200) data.ledger.removeAt(0)
        dirty = true
    }

    // ---------- market / envanter ----------

    fun owns(id: String) = id in data.owned
    fun equipped(cat: ShopCategory): ShopItem = ShopCatalog.item(data.equipped[cat.name] ?: "") ?: ShopCatalog.item(ShopCatalog.defaults[cat]!!)!!
    fun count(id: String): Int = data.consumables[id] ?: 0

    fun canBuy(item: ShopItem): PurchaseResult = when {
        item.rewardOnly -> PurchaseResult.NOT_FOR_SALE
        !item.consumable && owns(item.id) -> PurchaseResult.ALREADY_OWNED
        item.requiredLevel > data.playerLevel -> PurchaseResult.LOCKED_LEVEL
        (if (item.currency == Currency.GOLD) data.gold else data.diamonds) < item.price -> PurchaseResult.NOT_ENOUGH
        else -> PurchaseResult.OK
    }

    fun buy(item: ShopItem): PurchaseResult {
        val r = canBuy(item)
        if (r != PurchaseResult.OK) return r
        if (!spend(item.currency, item.price, "satın alma:${item.id}")) return PurchaseResult.NOT_ENOUGH
        if (item.consumable) grantConsumable(item.id, 1) else data.owned.add(item.id)
        data.addStat(Stat.PURCHASES)
        checkAchievements()
        save()
        return PurchaseResult.OK
    }

    fun equip(item: ShopItem): Boolean {
        if (!owns(item.id) || item.category !in ShopCatalog.equipCategories) return false
        data.equipped[item.category.name] = item.id
        save()
        return true
    }

    fun grantConsumable(id: String, n: Int) { data.consumables[id] = count(id) + n; dirty = true }

    /** Tüketilebilir kullan (can doldurma, kalkan, vb.). */
    fun useConsumable(id: String): Boolean {
        if (count(id) <= 0) return false
        when (id) {
            "life_single", "life_single_d" -> { if (lives.isFull) return false; lives.addLives(1) }
            "life_full" -> { if (lives.isFull) return false; lives.refill() }
            "boost_shield" -> { if (lives.state.shieldArmed) return false; lives.state.shieldArmed = true }
            "boost_endless_shield", "boost_magnet" -> {}
            else -> return false
        }
        data.consumables[id] = count(id) - 1
        save()
        return true
    }

    // ---------- bölümler ----------

    fun isLevelUnlocked(id: Int) = id <= data.unlockedLevel
    fun isAreaUnlocked(area: Int) = AreaCatalog.area(area).firstLevel <= data.unlockedLevel
    fun stars(id: Int) = data.levelStars[id] ?: 0
    fun areaStars(area: Int) = (AreaCatalog.area(area).firstLevel..AreaCatalog.area(area).lastLevel).sumOf { stars(it) }
    fun isAreaComplete(area: Int) = (AreaCatalog.area(area).firstLevel..AreaCatalog.area(area).lastLevel).all { stars(it) > 0 }
    fun masteryStars(area: Int) = (AreaCatalog.area(area).firstLevel..AreaCatalog.area(area).lastLevel).count { (data.masteryStars[it] ?: 0) > 0 }
    val currentLevel: Int get() = minOf(500, data.unlockedLevel)

    /** Bölüm başlatılabilir mi? Can yoksa false. */
    fun startLevel(id: Int, mastery: Boolean = false): Boolean {
        if (!lives.canPlay) return false
        if (!mastery && !isLevelUnlocked(id)) return false
        if (mastery && stars(id) == 0) return false
        data.pendingAttempt = id
        data.addStat(Stat.LEVELS_PLAYED)
        save()
        return true
    }

    private val attemptsThisLevel = HashMap<Int, Int>()

    /** Başarısız bölüm / bölümden çıkış. Can kaybedildiyse true. */
    fun failLevel(id: Int, gates: Int = 0, taps: Int = 0, coins: Int = 0): Boolean {
        data.pendingAttempt = 0
        attemptsThisLevel[id] = (attemptsThisLevel[id] ?: 0) + 1
        addPlayStats(gates, taps, coins)
        val lost = lives.onLevelFailed()
        save()
        return lost
    }

    private fun addPlayStats(gates: Int, taps: Int, coins: Int) {
        data.addStat(Stat.GATES, gates.toLong()); data.addStat(Stat.TAPS, taps.toLong()); data.addStat(Stat.COINS, coins.toLong())
        progressMission("m_gates", gates); progressMission("m_taps", taps); progressMission("m_coins", coins)
    }

    fun completeLevel(r: LevelResult): Reward {
        data.pendingAttempt = 0
        val rew = Reward()
        val id = r.levelId
        addPlayStats(r.gates, r.taps, r.coins)
        val flawless = (attemptsThisLevel[id] ?: 0) == 0
        attemptsThisLevel.remove(id)
        if (r.mastery) {
            val old = data.masteryStars[id] ?: 0
            if (r.stars > old) { data.masteryStars[id] = r.stars; rew.newStars = r.stars - old }
            data.addStat(Stat.MASTERY_PLAYED)
            progressMission("m_mastery", 1)
            rew.gold = 20 + r.coins * 2 + rew.newStars * 25
            rew.xp = 30 + rew.newStars * 10
            credit(Currency.GOLD, rew.gold, "ustalık:$id")
            if (credit(Currency.DIAMOND, 3, "ustalık ilk:$id", "mastery:$id:first")) rew.diamonds += 3
        } else {
            val old = stars(id)
            rew.firstClear = old == 0
            if (r.stars > old) { data.levelStars[id] = r.stars; rew.newStars = r.stars - old }
            data.levelBestTaps[id] = minOf(data.levelBestTaps[id] ?: Int.MAX_VALUE, r.taps)
            if (id >= data.unlockedLevel && id < 500) data.unlockedLevel = id + 1
            if (r.stars == 3 && old < 3) progressMission("m_perfect", 1)
            progressMission("m_levels", 1)
            progressMission("m_stars", rew.newStars)
            if (flawless) { data.addStat(Stat.FLAWLESS); progressMission("m_flawless", 1) }
            val lvl = com.yanlisrenk.logic.level.LevelGenerator.paramsFor(id)
            rew.gold = if (rew.firstClear) 25 + id / 4 + r.coins * 2 else 5 + r.coins
            rew.gold += rew.newStars * 15
            rew.xp = (if (rew.firstClear) 40 + id / 5 else 12) + rew.newStars * 10
            credit(Currency.GOLD, rew.gold, "bölüm:$id")
            if (r.diamonds > 0 && credit(Currency.DIAMOND, r.diamonds, "bölüm elmas:$id", "lvl:$id:diamond")) rew.diamonds += r.diamonds
            if (lvl.isFinal) {
                data.addStat(Stat.FINALS)
                if (credit(Currency.DIAMOND, 2, "final:$id", "final:$id")) rew.diamonds += 2
            }
            // alan tamamlandı mı?
            val area = AreaCatalog.areaOfLevel(id)
            if (isAreaComplete(area) && data.areaRewardClaimed.add(area)) {
                val a = AreaCatalog.area(area)
                rew.areaCompleted = area
                credit(Currency.GOLD, a.rewardGold, "alan:$area", "area:$area")
                credit(Currency.DIAMOND, a.rewardDiamonds, "alan:$area", "area:$area:d")
                rew.gold += a.rewardGold; rew.diamonds += a.rewardDiamonds
                data.collection.add(area)
                rew.collectible = a.collectible
                val world = a.world
                val wAreas = AreaCatalog.areas.filter { it.world == world }
                if (wAreas.all { it.id in data.collection } && data.worldSetClaimed.add(world)) {
                    rew.worldCompleted = world
                    if (credit(Currency.DIAMOND, 20, "dünya seti:$world", "world:$world")) rew.diamonds += 20
                }
            }
        }
        addXp(rew.xp, rew)
        rew.achievements.addAll(checkAchievements())
        save()
        return rew
    }

    fun addXp(xp: Int, rew: Reward? = null) {
        data.xp += xp
        while (data.playerLevel < PlayerLevels.MAX_LEVEL && data.xp >= PlayerLevels.xpForNext(data.playerLevel)) {
            data.xp -= PlayerLevels.xpForNext(data.playerLevel)
            data.playerLevel++
            val (g, d) = PlayerLevels.reward(data.playerLevel)
            credit(Currency.GOLD, g, "seviye:${data.playerLevel}", "plevel:${data.playerLevel}")
            credit(Currency.DIAMOND, d, "seviye:${data.playerLevel}", "plevel:${data.playerLevel}:d")
            rew?.levelUps?.add(data.playerLevel)
        }
        dirty = true
    }

    // ---------- sonsuz mod ----------

    data class EndlessReward(val gold: Int, val xp: Int, val newBest: Boolean, val weeklyBest: Boolean, val achievements: List<Achievement>)

    fun completeEndless(score: Int, gates: Int, coins: Int, maxCombo: Int, taps: Int, weekly: Boolean, magnet: Boolean): EndlessReward {
        addPlayStats(gates, taps, coins)
        data.addStat(Stat.ENDLESS_RUNS)
        data.maxStat(Stat.ENDLESS_BEST, score.toLong())
        data.maxStat(Stat.MAX_COMBO, maxCombo.toLong())
        progressMission("m_endless_runs", 1)
        progressMissionMax("m_endless_score", score)
        progressMissionMax("m_combo", maxCombo)
        val newBest = score > data.endlessBest
        if (newBest) { data.endlessBest = score; data.endlessBestGates = gates }
        var weeklyBest = false
        if (weekly) {
            val wk = weekKey()
            if (data.weekly.weekKey != wk) { data.weekly.weekKey = wk; data.weekly.best = 0; data.weekly.attempts = 0; data.weekly.rewardTiersClaimed.clear() }
            data.weekly.attempts++
            if (score > data.weekly.best) { data.weekly.best = score; weeklyBest = true }
        }
        val wk = weekKey()
        data.endlessWeekBest[wk] = maxOf(data.endlessWeekBest[wk] ?: 0, score)
        while (data.endlessWeekBest.size > 12) data.endlessWeekBest.remove(data.endlessWeekBest.keys.min())
        val gold = (coins + score / 40) * (if (magnet) 2 else 1)
        credit(Currency.GOLD, gold, "sonsuz")
        val xp = 10 + gates / 2
        addXp(xp)
        val ach = checkAchievements()
        save()
        return EndlessReward(gold, xp, newBest, weeklyBest, ach)
    }

    /** Haftalık mücadele ödül basamağını al. */
    fun claimWeeklyTier(tier: Int): Boolean {
        val (need, rew) = WeeklyChallenge.tiers.getOrNull(tier) ?: return false
        if (data.weekly.weekKey != weekKey() || data.weekly.best < need) return false
        if (!data.weekly.rewardTiersClaimed.add(tier)) return false
        val id = "weekly:${weekKey()}:$tier"
        credit(Currency.GOLD, rew.first, "haftalık", id)
        credit(Currency.DIAMOND, rew.second, "haftalık", "$id:d")
        save()
        return true
    }

    // ---------- görevler ----------

    fun refreshMissions() {
        val m = data.missions
        val day = dayKey()
        val week = weekKey()
        val masteryOk = data.completedLevels >= 5
        if (m.dailyKey != day) { m.dailyKey = day; m.daily = Missions.daily(day, data.playerLevel, masteryOk); dirty = true }
        if (m.weeklyKey != week) { m.weeklyKey = week; m.weekly = Missions.weekly(week, data.playerLevel, masteryOk); dirty = true }
    }

    private fun slots() = data.missions.daily + data.missions.weekly

    fun progressMission(stat: String, amount: Int) {
        if (amount <= 0) return
        for (s in slots()) if (Missions.template(s.templateId).stat == stat && !s.claimed) s.progress = minOf(s.target, s.progress + amount)
        dirty = true
    }

    private fun progressMissionMax(stat: String, value: Int) {
        for (s in slots()) if (Missions.template(s.templateId).stat == stat && !s.claimed) s.progress = minOf(s.target, maxOf(s.progress, value))
        dirty = true
    }

    fun claimMission(slot: MissionSlot, weekly: Boolean): Boolean {
        if (slot.claimed || slot.progress < slot.target) return false
        val key = if (weekly) data.missions.weeklyKey else data.missions.dailyKey
        val list = if (weekly) data.missions.weekly else data.missions.daily
        val idx = list.indexOf(slot)
        val rid = "mission:${if (weekly) "w" else "d"}:$key:$idx"
        if (rid in data.grantedRewards) return false
        slot.claimed = true
        credit(Currency.GOLD, slot.rewardGold, "görev", rid)
        credit(Currency.DIAMOND, slot.rewardDiamonds, "görev", "$rid:d")
        addXp(slot.rewardXp)
        data.addStat(Stat.MISSIONS_DONE)
        checkAchievements()
        save()
        return true
    }

    val claimableMissions: Int get() = slots().count { !it.claimed && it.progress >= it.target }

    // ---------- günlük giriş ----------

    fun canClaimDaily(): Boolean = dayKey() > data.daily.lastClaimDay

    /** Bugünün ödülünü verir; seri kırıldıysa 1. günden başlar. */
    fun claimDaily(): com.yanlisrenk.logic.progress.DailyReward? {
        val day = dayKey()
        if (day <= data.daily.lastClaimDay) return null
        val st = data.daily
        if (st.lastClaimDay >= 0 && day != st.lastClaimDay + 1) { st.streakIndex = 0; st.currentStreak = 0 }
        val r = DailyRewards.cycle[st.streakIndex]
        val rid = "daily:$day"
        if (!data.grantedRewards.add(rid)) return null
        if (r.gold > 0) credit(Currency.GOLD, r.gold, "günlük giriş")
        if (r.diamonds > 0) credit(Currency.DIAMOND, r.diamonds, "günlük giriş")
        r.item?.let { grantConsumable(it, r.itemCount) }
        st.lastClaimDay = day
        st.streakIndex = (st.streakIndex + 1) % DailyRewards.cycle.size
        st.totalClaims++
        st.currentStreak++
        st.longestStreak = maxOf(st.longestStreak, st.currentStreak)
        data.addStat(Stat.LOGIN_DAYS)
        checkAchievements()
        save()
        return r
    }

    /** Bugün gösterilecek gün indeksi (0..6). */
    fun dailyIndexToday(): Int {
        val st = data.daily
        val day = dayKey()
        return if (st.lastClaimDay >= 0 && day > st.lastClaimDay + 1) 0 else st.streakIndex
    }

    // ---------- başarılar ----------

    fun statValue(stat: String): Long = when {
        stat == Stat.LEVELS_DONE -> data.completedLevels.toLong()
        stat == Stat.STARS -> data.totalStars.toLong()
        stat == Stat.PERFECT -> data.levelStars.values.count { it >= 3 }.toLong()
        stat == Stat.AREAS -> (1..100).count { isAreaComplete(it) }.toLong()
        stat == Stat.CHARACTERS -> data.owned.count { it.startsWith("char_") }.toLong()
        stat == Stat.MASTERY_STARS -> data.masteryStars.values.sum().toLong()
        stat == Stat.PLAYER_LEVEL -> data.playerLevel.toLong()
        stat == Stat.COLLECTION -> data.collection.size.toLong()
        stat.startsWith("#world_") -> {
            val w = stat.removePrefix("#world_").toInt()
            if (AreaCatalog.areas.filter { it.world == w }.all { isAreaComplete(it.id) }) 1 else 0
        }
        else -> data.stat(stat)
    }

    /** Yeni açılan başarıları işaretler ve döndürür. */
    fun checkAchievements(): List<Achievement> {
        val out = ArrayList<Achievement>()
        for (a in Achievements.all) {
            if (a.id in data.achievementsUnlocked) continue
            if (statValue(a.stat) >= a.target) {
                data.achievementsUnlocked[a.id] = clock.now()
                a.unlockItem?.let { data.owned.add(it) }
                out.add(a)
            }
        }
        if (out.isNotEmpty()) { pendingAchievementToasts.addAll(out); dirty = true }
        return out
    }

    fun claimAchievement(a: Achievement): Boolean {
        if (a.id !in data.achievementsUnlocked || a.id in data.achievementsClaimed) return false
        data.achievementsClaimed.add(a.id)
        credit(Currency.GOLD, a.gold, "başarı:${a.id}", "ach:${a.id}")
        credit(Currency.DIAMOND, a.diamonds, "başarı:${a.id}", "ach:${a.id}:d")
        save()
        return true
    }

    val claimableAchievements: Int get() = data.achievementsUnlocked.keys.count { it !in data.achievementsClaimed }

    // ---------- ayarlar ----------

    fun resetProgress() {
        val fresh = saveManager.fresh()
        val keepSettings = data.settings
        // tüm alanları kopyala
        SaveData::class.java.declaredFields.forEach { f ->
            if (java.lang.reflect.Modifier.isStatic(f.modifiers) || f.name == "lives" || f.name == "clock") return@forEach
            f.isAccessible = true; f.set(data, f.get(fresh))
        }
        data.settings = keepSettings
        lives.state.lives = LifeManager.MAX_LIVES
        lives.state.regenRemaining = LifeManager.REGEN_MS
        lives.state.shieldArmed = false
        refreshMissions()
        save()
    }
}
