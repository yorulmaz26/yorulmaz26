package com.kayipadalar.logic

import com.kayipadalar.logic.data.GameConfig
import com.kayipadalar.logic.data.GameJson
import com.kayipadalar.logic.engine.*
import com.kayipadalar.logic.level.*
import com.kayipadalar.logic.meta.*
import com.kayipadalar.logic.model.IslandType
import com.kayipadalar.logic.model.Res
import com.kayipadalar.logic.model.UnitType
import com.kayipadalar.logic.save.*

/** Bölüm dosyalarını yükler; eksik alan dosyası varsa aynı üreticiyle anında üretir. */
class LevelRepository(private val read: (String) -> String?, private val generator: LevelGenerator) {
    private val cache = HashMap<Int, AreaFile>()

    @Synchronized
    fun area(n: Int): AreaFile = cache.getOrPut(n) {
        val text = read("levels/area_%03d.json".format(n))
        if (text != null) GameJson.decodeFromString(AreaFile.serializer(), text)
        else {
            val spec = AreaCatalog.area(n)
            AreaFile(n, spec.name, spec.theme, spec.newFeature?.name ?: "", (spec.firstLevel until spec.firstLevel + 5).map { generator.generateMain(it) })
        }
    }

    fun level(id: Int): LevelDef = area(AreaCatalog.areaOfLevel(id)).levels[(id - 1) % 5]
}

/** Özel mücadele kuralları: tamamlanmış bölümleri farklı kurallarla tekrar oynatır. */
enum class Challenge(val title: String, val desc: String) {
    TIGHT("Kusursuz Plan", "Hamle sınırı en iyi çözüm kadar."),
    FAST_ENEMY("Hızlı Düşman", "Düşmanlar her turda iki kez hareket eder."),
    NO_ENERGY("Enerjisiz Başlangıç", "Başlangıç enerjisi yok."),
    FRAGILE("Kırılgan Kale", "Kale canı yarıya iner."),
    NO_BONUS("Saf Strateji", "Araştırma ve bina bonusları devre dışı.");
}

enum class SessionKind { MAIN, CHALLENGE, ENDLESS, DAILY, DEFENSE, ERA }

class PlaySession(val kind: SessionKind, val def: LevelDef, val board: Board, val challenge: Challenge? = null, val replay: Boolean = false) {
    var finished = false
}

data class Outcome(
    val won: Boolean,
    val stars: Int,
    val rewards: Map<String, Long>,
    val firstClear: Boolean,
    val newBest: Boolean,
    val collectionItem: String?,
    val lifeLost: Boolean,
    val unlockedNext: Boolean,
    val message: String? = null,
)

enum class PlayBlock { OK, LOCKED, NO_LIVES }

class GameCore(
    val rules: GameConfig,
    val meta: MetaConfig,
    private val saveSystem: SaveSystem,
    timeSource: TimeSource,
    readAsset: (String) -> String?,
) {
    val loadResult: LoadResult = saveSystem.load()
    val save: SaveData = loadResult.data
    val clock = TrustedClock(save.clock, timeSource)
    val wallet = Wallet(save)
    val bonuses = Bonuses(meta, save)
    val kingdom = KingdomSystem(meta, save, wallet, bonuses)
    val research = ResearchSystem(meta, save, wallet, bonuses)
    val units = UnitSystem(save, wallet, rules)
    val lives = LivesSystem(meta, save)
    val reputation = ReputationSystem(meta, save)
    val collection = CollectionSystem(meta, save)
    val shop = ShopSystem(meta, save, wallet, { reputation.level() }, { collection.grantRandom(clock.now()) })
    val quests = QuestSystem(meta, save, wallet, bonuses)
    val achievements = AchievementSystem(meta, save, wallet)
    val expeditions = ExpeditionSystem(meta, save, wallet, bonuses, collection)
    val events = EventSystem(meta, save, wallet)
    val generator = LevelGenerator(rules)
    val levels = LevelRepository(readAsset, generator)
    val purchases: PurchaseProvider = NoRealMoneyPurchases

    init {
        val now = clock.now()
        if (loadResult.source == LoadResult.Source.NEW) {
            save.createdAtWall = clock.wallNow()
            save.res[Res.GOLD.name] = 200
            save.res[Res.WOOD.name] = 50
            save.res[Res.FOOD.name] = 50
            save.res[Res.STONE.name] = 30
            save.lastIdleCollect = now
            save.lives.regenStart = now
            save.defense.lastAttackAt = now
        }
        tick()
    }

    fun now() = clock.now()

    fun tick() {
        val now = clock.now()
        lives.update(now)
        quests.refresh(clock.wallNow(), now)
    }

    fun persist() = saveSystem.save(save)

    private fun stat(key: String, amount: Long) {
        if (amount <= 0) return
        save.stats[key] = (save.stats[key] ?: 0) + amount
        quests.record(key, amount)
    }

    // ------------------------------------------------------------ Bölüm akışı

    fun record(levelId: Int) = save.levels[levelId]
    fun isCompleted(levelId: Int) = (save.levels[levelId]?.completions ?: 0) > 0
    fun totalStars() = save.levels.values.sumOf { it.stars }

    fun playBlock(levelId: Int): PlayBlock = when {
        levelId > save.highestUnlocked -> PlayBlock.LOCKED
        isCompleted(levelId) -> PlayBlock.OK // tamamlanan bölümler serbest tekrar
        lives.lives(now()) <= 0 -> PlayBlock.NO_LIVES
        else -> PlayBlock.OK
    }

    fun modifiers(challenge: Challenge? = null, def: LevelDef? = null): LevelModifiers {
        val noBonus = challenge == Challenge.NO_BONUS
        val unitLv = UnitType.entries.associateWith { units.level(it) }
        var m = if (noBonus) LevelModifiers() else LevelModifiers(
            bonusEnergy = bonuses.int("START_ENERGY"),
            bonusCastleHp = bonuses.int("CASTLE_HP"),
            unitAtk = bonuses.int("UNIT_ATK"),
            unitDef = bonuses.int("UNIT_DEF"),
            unitHp = bonuses.int("UNIT_HP"),
            comboDamage = bonuses.int("COMBO_DAMAGE"),
            crystalEnergy = bonuses.int("CRYSTAL_ENERGY"),
            moveLimitDelta = bonuses.int("MOVE_LIMIT").coerceAtMost(1),
            unitLevels = unitLv,
        )
        when (challenge) {
            Challenge.TIGHT -> if (def != null) m = m.copy(moveLimitDelta = def.solution.size - def.moveLimit)
            Challenge.FAST_ENEMY -> m = m.copy(enemyDoubleTurn = true)
            Challenge.NO_ENERGY -> m = m.copy(noStartEnergy = true, bonusEnergy = 0)
            Challenge.FRAGILE -> m = m.copy(castleHpPct = 50)
            else -> {}
        }
        return m
    }

    /** Bölüm için gerçekten anlamlı ve kaydedilmiş çözümle hâlâ kazanılabilir mücadeleler. */
    fun availableChallenges(levelId: Int): List<Challenge> {
        if (!isCompleted(levelId)) return emptyList()
        val def = levels.level(levelId)
        if (def.solution.isEmpty()) return emptyList()
        val hasEnemy = def.units.any { it.owner == "ENEMY" }
        return Challenge.entries.filter { c ->
            val meaningful = when (c) {
                Challenge.TIGHT -> def.moveLimit > def.solution.size
                Challenge.FAST_ENEMY, Challenge.FRAGILE -> hasEnemy
                Challenge.NO_ENERGY -> def.startEnergy > 0 || bonuses.int("START_ENERGY") > 0
                Challenge.NO_BONUS -> true
            }
            meaningful && Solver.replay(def, rules, def.solution, modifiers(c, def)).status == Status.WON
        }
    }

    fun startLevel(levelId: Int, challenge: Challenge? = null): PlaySession? {
        if (playBlock(levelId) != PlayBlock.OK) return null
        val def = levels.level(levelId)
        val board = Board.create(def, rules, modifiers(challenge, def))
        save.levels.getOrPut(levelId) { LevelRecord() }.attempts++
        stat(Stat.LEVELS_PLAYED, 1)
        def.islands.forEach { save.discoveredTypes.add(it.type) }
        return PlaySession(if (challenge != null) SessionKind.CHALLENGE else SessionKind.MAIN, def, board, challenge, replay = isCompleted(levelId))
    }

    private fun absorbBoardStats(b: Board) {
        stat(Stat.BRIDGES, b.bridgesFormed.toLong())
        stat(Stat.COMBOS, b.combosTriggered.size.toLong())
        stat(Stat.ISLANDS_MOVED, b.islandsMoved.toLong())
        stat(Stat.ROTATIONS, b.rotations.toLong())
        stat(Stat.ENEMIES, b.enemiesDefeated.toLong())
        b.combosTriggered.forEach { save.discoveredCombos.add(it) }
        if (b.def.boss != null && b.bossUnit?.alive == false) stat(Stat.BOSSES, 1)
    }

    private fun goldFor(levelId: Int, stars: Int): Long {
        val e = meta.economy
        val base = e.levelGoldBase + levelId * e.levelGoldPerLevel + levels.level(levelId).rewards.gold
        val starMul = 1.0 + (stars - 1).coerceAtLeast(0) * e.starBonusPct / 100.0
        val bonus = 1.0 + bonuses.total("GOLD_REWARD_PCT") / 100.0
        return (base * starMul * bonus).toLong()
    }

    /** Oturumu sonlandırır; ödülleri, canları ve ilerlemeyi işler. Birden fazla çağrılırsa yalnızca ilki işlenir. */
    fun finish(session: PlaySession): Outcome {
        if (session.finished) return Outcome(false, 0, emptyMap(), false, false, null, false, false, "already")
        session.finished = true
        val b = session.board
        absorbBoardStats(b)
        val won = b.status == Status.WON
        return when (session.kind) {
            SessionKind.MAIN, SessionKind.CHALLENGE -> finishMain(session, won)
            SessionKind.ENDLESS -> finishEndless(session, won)
            SessionKind.DAILY -> finishDaily(session, won)
            SessionKind.DEFENSE -> finishDefense(session, won)
            SessionKind.ERA -> finishEra(session, won)
        }.also { persist() }
    }

    private fun finishMain(s: PlaySession, won: Boolean): Outcome {
        val id = s.def.id
        val rec = save.levels.getOrPut(id) { LevelRecord() }
        val first = won && rec.completions == 0
        if (!won) {
            val lose = !s.replay && s.kind == SessionKind.MAIN
            if (lose) lives.loseLife(now())
            return Outcome(false, 0, emptyMap(), false, false, null, lose, false)
        }
        val stars = Rules.stars(s.board)
        val rewards = LinkedHashMap<String, Long>()
        var item: String? = null
        if (s.kind == SessionKind.CHALLENGE) {
            val c = s.challenge!!
            val firstMedal = rec.challenges.add(c.name)
            val g = meta.economy.challengeGold.toLong() * (if (firstMedal) 2 else 1)
            rewards[Res.GOLD.name] = g
            if (firstMedal) { rewards[Res.STARDUST.name] = 1; save.reputationXp += 10 }
            stat(Stat.CHALLENGES, 1)
        } else {
            val gold = goldFor(id, stars)
            if (first) {
                rewards[Res.GOLD.name] = gold
                val r = s.def.rewards
                mapOf(Res.WOOD to r.wood, Res.STONE to r.stone, Res.IRON to r.iron, Res.FOOD to r.food, Res.CRYSTAL to r.crystal,
                    Res.ANCIENT_SHARD to r.ancientShard, Res.STARDUST to r.stardust, Res.ROYAL_SEAL to r.royalSeal)
                    .forEach { (k, v) -> if (v > 0) rewards[k.name] = v.toLong() }
                if ((now() xor id.toLong()).mod(100L) < r.collectionChancePct) item = collection.grantRandom(now() + id)
                save.reputationXp += 5L * stars + (if (s.def.boss != null) 50 else 0)
            } else {
                rewards[Res.GOLD.name] = gold * meta.economy.replayRewardPct / 100
            }
            val improved = (stars - rec.stars).coerceAtLeast(0)
            if (improved > 0) stat(Stat.STARS, improved.toLong())
            if (stars == 3 && rec.stars < 3) stat(Stat.PERFECT_WINS, 1)
        }
        rewards.forEach { (k, v) -> wallet.add(k, v) }
        val newBest = rec.bestMoves == 0 || s.board.movesUsed < rec.bestMoves
        if (newBest) rec.bestMoves = s.board.movesUsed
        rec.stars = maxOf(rec.stars, stars)
        rec.completions++
        if (first) stat(Stat.LEVELS_COMPLETED, 1)
        var unlocked = false
        if (id == save.highestUnlocked && id < AreaCatalog.LEVELS + 1) { save.highestUnlocked = id + 1; unlocked = true }
        return Outcome(true, stars, rewards, first, newBest, item, false, unlocked)
    }

    // ------------------------------------------------------------ Sonsuz Takımadalar

    fun startEndless(newRun: Boolean): PlaySession {
        val e = save.endless
        if (newRun || !e.active) { e.active = true; e.current = 1; e.runSeed = now() }
        val def = generator.generateEndless(e.current, e.runSeed)
        return PlaySession(SessionKind.ENDLESS, def, Board.create(def, rules, modifiers()))
    }

    private fun finishEndless(s: PlaySession, won: Boolean): Outcome {
        val e = save.endless
        if (!won) {
            e.active = false
            return Outcome(false, 0, emptyMap(), false, false, null, false, false, "endless.over")
        }
        val gold = meta.economy.endlessGoldPerStage.toLong() * e.current
        wallet.add(Res.GOLD.name, gold)
        stat(Stat.ENDLESS_STAGES, 1)
        val newBest = e.current > e.best
        if (newBest) e.best = e.current
        e.current++
        var item: String? = null
        if (e.current % 10 == 0) item = collection.grantRandom(e.runSeed + e.current)
        return Outcome(true, Rules.stars(s.board), mapOf(Res.GOLD.name to gold), false, newBest, item, false, false)
    }

    // ------------------------------------------------------------ Günlük bulmaca

    fun dailyKey(): Long = Math.floorDiv(clock.wallNow() + 3 * 3600_000L, 86_400_000L)

    fun startDaily(): PlaySession {
        val def = generator.generateDaily(dailyKey())
        return PlaySession(SessionKind.DAILY, def, Board.create(def, rules, LevelModifiers()))
    }

    private fun finishDaily(s: PlaySession, won: Boolean): Outcome {
        if (!won) return Outcome(false, 0, emptyMap(), false, false, null, false, false)
        val key = dailyKey()
        val prev = save.dailyPuzzleBest[key]
        val moves = s.board.movesUsed
        val rewards = HashMap<String, Long>()
        if (prev == null) {
            rewards[Res.GOLD.name] = meta.economy.dailyPuzzleGold.toLong()
            rewards[Res.CRYSTAL.name] = 3
            stat(Stat.DAILY_PUZZLES, 1)
        }
        rewards.forEach { (k, v) -> wallet.add(k, v) }
        val newBest = prev == null || moves < prev
        if (newBest) save.dailyPuzzleBest[key] = moves
        return Outcome(true, Rules.stars(s.board), rewards, prev == null, newBest, null, false, false)
    }

    // ------------------------------------------------------------ Krallık savunması

    fun defenseReady(): Boolean = save.highestUnlocked > 46 &&
        now() - save.defense.lastAttackAt >= meta.economy.defenseEveryHours * 3_600_000L

    fun msToDefense(): Long = (save.defense.lastAttackAt + meta.economy.defenseEveryHours * 3_600_000L - now()).coerceAtLeast(0)

    fun garrison(): List<UnitType> = save.defense.garrison.mapNotNull { runCatching { UnitType.valueOf(it) }.getOrNull() }.filter { units.unlocked(it) }

    fun setGarrison(list: List<UnitType>) {
        save.defense.garrison.clear()
        save.defense.garrison.addAll(list.filter { units.unlocked(it) }.take(4).map { it.name })
    }

    fun startDefense(): PlaySession? {
        if (!defenseReady()) return null
        val castleHp = 10 + bonuses.int("CASTLE_HP")
        val def = generator.generateDefense(save.defense.attackIndex, garrison(), castleHp, save.highestUnlocked)
        return PlaySession(SessionKind.DEFENSE, def, Board.create(def, rules, modifiers().copy(bonusCastleHp = 0)))
    }

    private fun finishDefense(s: PlaySession, won: Boolean): Outcome {
        if (!won) return Outcome(false, 0, emptyMap(), false, false, null, false, false, "defense.retry")
        val d = save.defense
        d.wins++; d.attackIndex++; d.lastAttackAt = now()
        val rewards = mapOf(Res.GOLD.name to meta.economy.defenseGold.toLong() + d.attackIndex * 10, Res.IRON.name to 15L, Res.STONE.name to 25L)
        rewards.forEach { (k, v) -> wallet.add(k, v) }
        stat(Stat.DEFENSES, 1)
        save.reputationXp += 15
        return Outcome(true, Rules.stars(s.board), rewards, false, false, null, false, false)
    }

    // ------------------------------------------------------------ Yeni Çağ (prestij)

    val eraUnlocked get() = save.highestUnlocked > AreaCatalog.LEVELS

    /** Çağın 10 mücadelesi: tamamlanmış bölümlerden deterministik seçim + mücadele kuralı. */
    fun eraChallenges(): List<Pair<Int, Challenge>> {
        val rnd = java.util.Random(save.era * 7919L + 13)
        val out = ArrayList<Pair<Int, Challenge>>()
        var guard = 0
        while (out.size < 10 && guard++ < 400) {
            val id = 1 + rnd.nextInt(AreaCatalog.LEVELS)
            if (out.any { it.first == id }) continue
            val ch = availableChallenges(id).let { if (it.isEmpty()) null else it[rnd.nextInt(it.size)] } ?: continue
            out.add(id to ch)
        }
        return out
    }

    fun eraKey(levelId: Int, c: Challenge) = "ERA${save.era}_${c.name}"
    fun eraDone(levelId: Int, c: Challenge) = save.levels[levelId]?.challenges?.contains(eraKey(levelId, c)) == true

    fun startEra(levelId: Int, c: Challenge): PlaySession? {
        if (!eraUnlocked) return null
        val def = levels.level(levelId)
        return PlaySession(SessionKind.ERA, def, Board.create(def, rules, modifiers(c, def)), c)
    }

    private fun finishEra(s: PlaySession, won: Boolean): Outcome {
        if (!won) return Outcome(false, 0, emptyMap(), false, false, null, false, false)
        save.levels.getOrPut(s.def.id) { LevelRecord() }.challenges.add(eraKey(s.def.id, s.challenge!!))
        wallet.add(Res.STARDUST.name, 1)
        stat(Stat.CHALLENGES, 1)
        return Outcome(true, Rules.stars(s.board), mapOf(Res.STARDUST.name to 1L), false, false, null, false, false)
    }

    fun canAdvanceEra() = eraUnlocked && eraChallenges().let { list -> list.isNotEmpty() && list.all { (id, c) -> eraDone(id, c) } }

    fun advanceEra(): Boolean {
        if (!canAdvanceEra()) return false
        save.era++
        wallet.add(Res.ROYAL_SEAL.name, 2)
        save.reputationXp += 200
        return true
    }

    // ------------------------------------------------------------ İstatistik kaydeden işlemler

    fun upgradeBuilding(id: String): String? = kingdom.upgrade(id).also { if (it == null) { stat(Stat.BUILDINGS, 1); save.reputationXp += 5; persist() } }
    fun doResearch(id: String): String? = research.research(id).also { if (it == null) { save.reputationXp += 10; persist() } }
    fun upgradeUnit(t: UnitType): String? = units.upgrade(t).also { if (it == null) persist() }
    fun buy(id: String): Pair<String?, String?> = shop.buy(id).also { if (it.first == null) persist() }
    fun startExpedition(id: String): String? = expeditions.start(id, now()).also { if (it == null) persist() }
    fun claimExpedition(run: ExpeditionRun): Pair<String?, ExpeditionSystem.Loot?> =
        expeditions.claim(run, now()).also { if (it.first == null) { quests.record(Stat.EXPEDITIONS, 1); persist() } }
    fun collectIdle(): Map<Res, Long> = kingdom.collectIdle(now()).also { persist() }
    fun resolveEvent(i: Int): String? = events.resolve(i).also { if (it == null) persist() }
    fun claimAchievement(id: String): String? = achievements.claim(id).also { if (it == null) persist() }
    fun claimQuest(p: QuestProgress, weekly: Boolean): String? = quests.claim(p, weekly).also { if (it == null) persist() }
    fun trade(from: String, to: String, amount: Int): String? = kingdom.trade(from, to, amount).also { if (it == null) persist() }

    // ------------------------------------------------------------ Yardımcılar

    fun discoveredCombo(id: String) = id in save.discoveredCombos
    fun discoveredType(t: IslandType) = t.name in save.discoveredTypes
    fun levelRewardPreview(levelId: Int): Long = goldFor(levelId, 3)
}
