package com.besaniyelik.dunya.progress

import com.besaniyelik.dunya.level.GameContent
import com.besaniyelik.dunya.level.PowerupType
import java.util.Random
import java.util.TimeZone

/** Takvim kaynağı (testlerde sabitlenebilir). Gün = yerel saat dilimine göre epoch günü. */
interface GameClock {
    fun epochDay(): Long
    fun weekKey(): Long = Math.floorDiv(epochDay() + 3, 7L) // Pazartesi başlangıçlı hafta
}

object SystemClock : GameClock {
    override fun epochDay(): Long {
        val now = System.currentTimeMillis()
        return Math.floorDiv(now + TimeZone.getDefault().getOffset(now), 86_400_000L)
    }
}

class Reward(var coins: Int = 0, var gems: Int = 0, var xp: Int = 0, val powerups: MutableMap<PowerupType, Int> = mutableMapOf(), val items: MutableList<String> = mutableListOf()) {
    fun add(o: Reward) { coins += o.coins; gems += o.gems; xp += o.xp; o.powerups.forEach { (k, v) -> powerups[k] = (powerups[k] ?: 0) + v }; items.addAll(o.items) }
    val isEmpty get() = coins == 0 && gems == 0 && xp == 0 && powerups.isEmpty() && items.isEmpty()
}

class LevelUp(val level: Int, val reward: Reward, val text: String)

/** Deneyim ve seviye sistemi. Her seviyede altın, her 5 seviyede elmas+güçlendirme, her 10 seviyede kozmetik. */
object ProgressionManager {
    const val MAX_LEVEL = 99
    fun xpToNext(level: Int): Long = 100L + (level - 1) * 45L

    fun rewardFor(level: Int): Reward {
        val r = Reward(coins = 50 + level * 10)
        if (level % 5 == 0) {
            r.gems += 10 + level / 5 * 2
            r.powerups[PowerupType.SHIELD] = 1
            r.powerups[PowerupType.DOUBLE_JUMP] = 1
        }
        return r
    }

    fun levelUpText(level: Int): String = when {
        Catalog.skins.any { (it.unlock as? Unlock.PlayerLevel)?.level == level } ->
            com.besaniyelik.dunya.i18n.L.t("levelup.skin", "name" to Catalog.skins.first { (it.unlock as? Unlock.PlayerLevel)?.level == level }.name)
        Catalog.trails.any { (it.unlock as? Unlock.PlayerLevel)?.level == level } ->
            com.besaniyelik.dunya.i18n.L.t("levelup.trail", "name" to Catalog.trails.first { (it.unlock as? Unlock.PlayerLevel)?.level == level }.name)
        level % 10 == 0 -> com.besaniyelik.dunya.i18n.L.t("levelup.special10")
        level % 5 == 0 -> com.besaniyelik.dunya.i18n.L.t("levelup.special5")
        else -> com.besaniyelik.dunya.i18n.L.t("levelup.plain")
    }

    /** Sıradan seviye atlama mı (özel ödül yok)? */
    fun isPlainLevelUp(level: Int): Boolean =
        Catalog.skins.none { (it.unlock as? Unlock.PlayerLevel)?.level == level } &&
            Catalog.trails.none { (it.unlock as? Unlock.PlayerLevel)?.level == level } && level % 5 != 0

    fun addXp(d: SaveData, amount: Int): List<LevelUp> {
        val ups = ArrayList<LevelUp>()
        d.xp += amount
        while (d.playerLevel < MAX_LEVEL && d.xp >= xpToNext(d.playerLevel)) {
            d.xp -= xpToNext(d.playerLevel)
            d.playerLevel++
            val r = rewardFor(d.playerLevel)
            RewardManager.apply(d, r)
            ups.add(LevelUp(d.playerLevel, r, levelUpText(d.playerLevel)))
        }
        return ups
    }
}

object RewardManager {
    fun apply(d: SaveData, r: Reward) {
        d.coins += r.coins
        d.gems += r.gems
        r.powerups.forEach { (k, v) -> d.inventory[k] = (d.inventory[k] ?: 0) + v }
    }

    // ---- Günlük giriş (7 günlük döngü; gün kaçırmak ilerlemeyi sıfırlamaz)
    fun loginAvailable(d: SaveData, clock: GameClock): Boolean = d.login.lastClaimDay != clock.epochDay()

    fun claimLogin(d: SaveData, clock: GameClock): LoginReward? {
        if (!loginAvailable(d, clock)) return null
        val r = Catalog.loginRewards[d.login.cycleDay % 7]
        d.coins += r.coins; d.gems += r.gems
        r.powerups.forEach { (k, v) -> d.inventory[k] = (d.inventory[k] ?: 0) + v }
        d.login.lastClaimDay = clock.epochDay()
        d.login.cycleDay = (d.login.cycleDay + 1) % 7
        d.login.totalClaims++
        return r
    }
}

/** Günlük (3) ve haftalık (5) görevler. Görevler tarihe göre tohumlanır, böylece gün içinde sabittir. */
object MissionManager {
    const val DAILY_COUNT = 3
    const val WEEKLY_COUNT = 5

    fun refresh(d: SaveData, clock: GameClock) {
        val day = clock.epochDay()
        if (d.missions.dailyKey != day) {
            d.missions.dailyKey = day
            d.missions.daily = generate(Catalog.dailyPool, DAILY_COUNT, day * 31 + 7, "g").toMutableList()
        }
        val week = clock.weekKey()
        if (d.missions.weeklyKey != week) {
            d.missions.weeklyKey = week
            d.missions.weekly = generate(Catalog.weeklyPool, WEEKLY_COUNT, week * 977 + 3, "h").toMutableList()
        }
    }

    private fun generate(pool: List<MissionTemplate>, n: Int, seed: Long, prefix: String): List<MissionProgress> {
        val rng = Random(seed)
        return pool.shuffled(rng).take(n).mapIndexed { i, t ->
            val target = t.targets[rng.nextInt(t.targets.size)]
            val scale = target.toFloat() / t.targets[0]
            MissionProgress("$prefix$seed-$i", t.type, target, (t.coins * scale).toInt().coerceAtMost(t.coins * 2), t.gems, (t.xp * scale).toInt())
        }
    }

    /** Görev ilerlemesi. [max] true ise ilerleme "en iyi değer" olarak tutulur (ör. tek koşuda dayanma süresi). */
    fun record(d: SaveData, type: MissionType, amount: Int, max: Boolean = false): List<MissionProgress> {
        val completed = ArrayList<MissionProgress>()
        for (m in d.missions.daily + d.missions.weekly) {
            if (m.type != type || m.claimed) continue
            val was = m.done
            m.progress = if (max) maxOf(m.progress, amount) else m.progress + amount
            if (!was && m.done) completed.add(m)
        }
        return completed
    }

    fun claim(d: SaveData, id: String): Reward? {
        val m = (d.missions.daily + d.missions.weekly).firstOrNull { it.id == id } ?: return null
        if (!m.done || m.claimed) return null
        m.claimed = true
        val r = Reward(coins = m.rewardCoins, gems = m.rewardGems, xp = m.rewardXp)
        RewardManager.apply(d, r)
        return r
    }
}

object AchievementManager {
    fun progress(d: SaveData, a: AchievementDef): Long = a.stat(d).coerceAtMost(a.target)

    /** Yeni açılan başarıları kaydeder ve elmas ödüllerini verir. */
    fun check(d: SaveData): List<AchievementDef> {
        val out = ArrayList<AchievementDef>()
        for (a in Catalog.achievements) {
            if (a.id in d.achievements) continue
            if (a.stat(d) >= a.target) {
                d.achievements.add(a.id)
                d.gems += a.gems
                out.add(a)
            }
        }
        return out
    }
}

/** Karakter ve iz kilitleri ile mağaza işlemleri. */
object CharacterManager {
    fun meets(d: SaveData, u: Unlock): Boolean = when (u) {
        Unlock.Default -> true
        is Unlock.Coins, is Unlock.Gems -> false
        is Unlock.PlayerLevel -> d.playerLevel >= u.level
        is Unlock.Collection -> u.map in d.collectionsClaimed
        is Unlock.TotalStars -> d.totalStars >= u.stars
    }

    /** Satın alma gerektirmeyen kilitleri (seviye, koleksiyon, yıldız) otomatik açar. */
    fun autoUnlock(d: SaveData): List<String> {
        val out = ArrayList<String>()
        for (s in Catalog.skins) if (s.id !in d.ownedSkins && meets(d, s.unlock)) { d.ownedSkins.add(s.id); out.add(s.name) }
        for (t in Catalog.trails) if (t.id !in d.ownedTrails && meets(d, t.unlock)) { d.ownedTrails.add(t.id); out.add(t.name) }
        return out
    }

    fun selectSkin(d: SaveData, id: String): Boolean {
        if (id !in d.ownedSkins) return false
        d.selectedSkin = id; return true
    }

    fun selectTrail(d: SaveData, id: String): Boolean {
        if (id !in d.ownedTrails) return false
        d.selectedTrail = id; return true
    }
}

enum class PurchaseResult {
    OK, OWNED, NOT_ENOUGH_COINS, NOT_ENOUGH_GEMS, LOCKED;

    val message: String get() = com.besaniyelik.dunya.i18n.L.t("purchase.$name")
}

object ShopManager {
    private fun pay(d: SaveData, u: Unlock): PurchaseResult = when (u) {
        is Unlock.Coins -> if (d.coins >= u.price) { d.coins -= u.price; PurchaseResult.OK } else PurchaseResult.NOT_ENOUGH_COINS
        is Unlock.Gems -> if (d.gems >= u.price) { d.gems -= u.price; PurchaseResult.OK } else PurchaseResult.NOT_ENOUGH_GEMS
        else -> PurchaseResult.LOCKED
    }

    fun buySkin(d: SaveData, id: String): PurchaseResult {
        val s = Catalog.skins.firstOrNull { it.id == id } ?: return PurchaseResult.LOCKED
        if (id in d.ownedSkins) return PurchaseResult.OWNED
        return pay(d, s.unlock).also { if (it == PurchaseResult.OK) d.ownedSkins.add(id) }
    }

    fun buyTrail(d: SaveData, id: String): PurchaseResult {
        val t = Catalog.trails.firstOrNull { it.id == id } ?: return PurchaseResult.LOCKED
        if (id in d.ownedTrails) return PurchaseResult.OWNED
        return pay(d, t.unlock).also { if (it == PurchaseResult.OK) d.ownedTrails.add(id) }
    }

    fun buyPowerup(d: SaveData, type: PowerupType): PurchaseResult {
        val price = Catalog.shopPowerups[type] ?: return PurchaseResult.LOCKED
        if (d.coins < price) return PurchaseResult.NOT_ENOUGH_COINS
        d.coins -= price
        d.inventory[type] = (d.inventory[type] ?: 0) + 1
        return PurchaseResult.OK
    }

    fun buyCoinPack(d: SaveData, index: Int): PurchaseResult {
        val (gems, coins) = Catalog.coinPacks.getOrNull(index) ?: return PurchaseResult.LOCKED
        if (d.gems < gems) return PurchaseResult.NOT_ENOUGH_GEMS
        d.gems -= gems; d.coins += coins
        return PurchaseResult.OK
    }

    fun usePowerup(d: SaveData, type: PowerupType): Boolean {
        val n = d.inventory[type] ?: 0
        if (n <= 0) return false
        d.inventory[type] = n - 1
        return true
    }
}

/** Gizli koleksiyon parçaları: her haritada 5 parça; tamamlanınca ödül açılır. */
object CollectionManager {
    fun piecesOf(d: SaveData, map: Int): Int = d.pieces.count { (it - 1) / 50 + 1 == map }

    fun rewardName(map: Int): String {
        Catalog.skins.firstOrNull { (it.unlock as? Unlock.Collection)?.map == map }?.let { return com.besaniyelik.dunya.i18n.L.t("collection.reward_skin", "name" to it.name) }
        Catalog.trails.firstOrNull { (it.unlock as? Unlock.Collection)?.map == map }?.let { return com.besaniyelik.dunya.i18n.L.t("collection.reward_trail", "name" to it.name) }
        return com.besaniyelik.dunya.i18n.L.t("collection.reward")
    }

    /** Parça kaydı; harita tamamlandıysa koleksiyonu talep eder. Tamamlanan harita numarasını döndürür. */
    fun addPiece(d: SaveData, levelId: Int): Int? {
        if (!GameContent.hasPiece(levelId) || levelId in d.pieces) return null
        d.pieces.add(levelId)
        val map = (levelId - 1) / 50 + 1
        if (piecesOf(d, map) >= GameContent.PIECES_PER_MAP && map !in d.collectionsClaimed) {
            d.collectionsClaimed.add(map)
            d.gems += 25
            CharacterManager.autoUnlock(d)
            return map
        }
        return null
    }
}
