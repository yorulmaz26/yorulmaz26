package com.besaniyelik.dunya.progress

import com.besaniyelik.dunya.endless.ScoreCalculator
import com.besaniyelik.dunya.level.GameContent
import com.besaniyelik.dunya.level.LevelData
import com.besaniyelik.dunya.level.PowerupType

class LevelResult(
    val level: LevelData,
    val completed: Boolean,
    val starsCollected: Int,
    val coinValue: Int,
    val coinCount: Int,
    val deaths: Int,
    val ticks: Int,
    val jumps: Int,
    val rulesSurvived: Int,
    val powerupsUsed: Int,
    val pieceCollected: Boolean,
)

class EndlessResult(
    val score: Int,
    val seconds: Float,
    val distance: Float,
    val coinValue: Int,
    val stars: Int,
    val jumps: Int,
    val rulesSurvived: Int,
    val powerupsUsed: Int,
)

class ResultSummary(
    val stars: Int = 0,
    val firstClear: Boolean = false,
    val newRecord: Boolean = false,
    val xp: Int = 0,
    val coins: Int = 0,
    val levelUps: List<LevelUp> = emptyList(),
    val achievements: List<AchievementDef> = emptyList(),
    val missions: List<MissionProgress> = emptyList(),
    val collectionCompleted: Int? = null,
    val unlocked: List<String> = emptyList(),
    val nextLevelUnlocked: Int? = null,
    val rejected: Boolean = false,
)

/** Oyuncu ilerlemesinin tek giriş noktası. Bütün değişiklikler burada yapılır ve hemen kaydedilir. */
class ProfileService(private val saves: SaveManager, val clock: GameClock = SystemClock) {
    var data: SaveData = saves.load()
        private set

    init {
        MissionManager.refresh(data, clock)
        CharacterManager.autoUnlock(data)
    }

    fun save() = saves.save(data)

    fun refreshDaily() { MissionManager.refresh(data, clock); save() }

    fun onLevelResult(r: LevelResult): ResultSummary {
        val d = data
        MissionManager.refresh(d, clock)
        val lv = r.level
        val st = d.stats
        st.jumps += r.jumps
        st.deaths += r.deaths
        st.rulesSurvived += r.rulesSurvived
        st.powerupsUsed += r.powerupsUsed
        st.playSeconds += r.ticks / 60f
        st.coinsCollected += r.coinValue
        st.starsCollected += r.starsCollected
        val missions = ArrayList<MissionProgress>()
        missions += MissionManager.record(d, MissionType.JUMP, r.jumps)
        missions += MissionManager.record(d, MissionType.COLLECT_COINS, r.coinValue)
        missions += MissionManager.record(d, MissionType.COLLECT_STARS, r.starsCollected)
        missions += MissionManager.record(d, MissionType.SURVIVE_RULES, r.rulesSurvived)
        missions += MissionManager.record(d, MissionType.USE_POWERUPS, r.powerupsUsed)
        d.coins += r.coinValue
        if (!r.completed) {
            save()
            return ResultSummary(coins = r.coinValue, missions = missions)
        }
        val prog = d.levels.getOrPut(lv.id) { LevelProgress() }
        val firstClear = !prog.completed
        val stars = ScoreCalculator.levelStars(true, r.starsCollected, r.coinCount, lv.coinTarget, r.deaths)
        prog.completed = true
        prog.plays++
        prog.stars = maxOf(prog.stars, stars)
        prog.bestCoins = maxOf(prog.bestCoins, r.coinCount)
        prog.bestTimeTicks = if (prog.bestTimeTicks == 0) r.ticks else minOf(prog.bestTimeTicks, r.ticks)
        st.levelsCompleted = d.levels.values.count { it.completed }.toLong()
        if (stars == 3 && r.deaths == 0) st.perfectLevels++
        missions += MissionManager.record(d, MissionType.COMPLETE_LEVELS, 1)
        if (stars == 3) missions += MissionManager.record(d, MissionType.THREE_STAR, 1)
        var next: Int? = null
        if (lv.id < GameContent.LEVEL_COUNT && d.highestUnlocked <= lv.id) { d.highestUnlocked = lv.id + 1; next = lv.id + 1 }
        var coll: Int? = null
        if (r.pieceCollected) {
            missions += MissionManager.record(d, MissionType.COLLECT_PIECE, if (lv.id in d.pieces) 0 else 1)
            coll = CollectionManager.addPiece(d, lv.id)
        }
        val xp = ScoreCalculator.levelXp(lv.difficulty, stars, firstClear)
        val ups = ProgressionManager.addXp(d, xp)
        val unlocked = CharacterManager.autoUnlock(d)
        val ach = AchievementManager.check(d)
        save()
        return ResultSummary(stars, firstClear, false, xp, r.coinValue, ups, ach, missions.distinct(), coll, unlocked, next)
    }

    fun onEndlessResult(r: EndlessResult): ResultSummary {
        val d = data
        MissionManager.refresh(d, clock)
        if (!ScoreCalculator.plausible(r.score, r.seconds, r.distance)) return ResultSummary(rejected = true)
        val st = d.stats
        st.endlessRuns++
        st.jumps += r.jumps
        st.rulesSurvived += r.rulesSurvived
        st.powerupsUsed += r.powerupsUsed
        st.coinsCollected += r.coinValue
        st.starsCollected += r.stars
        st.playSeconds += r.seconds
        val newRecord = r.score > st.endlessBestScore
        st.endlessBestScore = maxOf(st.endlessBestScore, r.score.toLong())
        st.endlessBestSeconds = maxOf(st.endlessBestSeconds, r.seconds)
        st.endlessBestDistance = maxOf(st.endlessBestDistance, r.distance)
        d.endlessRecords.add(EndlessRecord(r.score, r.seconds, r.distance, r.coinValue, clock.epochDay()))
        d.endlessRecords.sortByDescending { it.score }
        while (d.endlessRecords.size > 10) d.endlessRecords.removeAt(d.endlessRecords.size - 1)
        d.coins += r.coinValue
        val missions = ArrayList<MissionProgress>()
        missions += MissionManager.record(d, MissionType.PLAY_ENDLESS, 1)
        missions += MissionManager.record(d, MissionType.ENDLESS_SURVIVE, r.seconds.toInt(), max = true)
        missions += MissionManager.record(d, MissionType.ENDLESS_SCORE, r.score, max = true)
        missions += MissionManager.record(d, MissionType.JUMP, r.jumps)
        missions += MissionManager.record(d, MissionType.COLLECT_COINS, r.coinValue)
        missions += MissionManager.record(d, MissionType.COLLECT_STARS, r.stars)
        missions += MissionManager.record(d, MissionType.SURVIVE_RULES, r.rulesSurvived)
        missions += MissionManager.record(d, MissionType.USE_POWERUPS, r.powerupsUsed)
        val xp = ScoreCalculator.endlessXp(r.score)
        val ups = ProgressionManager.addXp(d, xp)
        val unlocked = CharacterManager.autoUnlock(d)
        val ach = AchievementManager.check(d)
        save()
        return ResultSummary(0, false, newRecord, xp, r.coinValue, ups, ach, missions.distinct(), null, unlocked)
    }

    fun claimMission(id: String): Reward? {
        val r = MissionManager.claim(data, id) ?: return null
        val ups = ProgressionManager.addXp(data, r.xp)
        CharacterManager.autoUnlock(data)
        AchievementManager.check(data)
        save()
        return r.also { if (ups.isNotEmpty()) it.items.add("Seviye ${data.playerLevel}") }
    }

    fun claimLogin(): LoginReward? = RewardManager.claimLogin(data, clock)?.also { AchievementManager.check(data); save() }
    fun loginAvailable() = RewardManager.loginAvailable(data, clock)

    fun buySkin(id: String) = ShopManager.buySkin(data, id).also { if (it == PurchaseResult.OK) { AchievementManager.check(data); save() } }
    fun buyTrail(id: String) = ShopManager.buyTrail(data, id).also { if (it == PurchaseResult.OK) save() }
    fun buyPowerup(t: PowerupType) = ShopManager.buyPowerup(data, t).also { if (it == PurchaseResult.OK) save() }
    fun buyCoinPack(i: Int) = ShopManager.buyCoinPack(data, i).also { if (it == PurchaseResult.OK) save() }
    fun usePowerup(t: PowerupType) = ShopManager.usePowerup(data, t).also { if (it) save() }
    fun selectSkin(id: String) = CharacterManager.selectSkin(data, id).also { if (it) save() }
    fun selectTrail(id: String) = CharacterManager.selectTrail(data, id).also { if (it) save() }

    fun markTutorialSeen(key: String) { if (data.seenTutorials.add(key)) save() }

    fun resetAll() {
        saves.reset()
        data = SaveData()
        MissionManager.refresh(data, clock)
        save()
    }
}
