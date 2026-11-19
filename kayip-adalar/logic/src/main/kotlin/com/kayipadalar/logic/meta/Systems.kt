package com.kayipadalar.logic.meta

import com.kayipadalar.logic.model.Res
import com.kayipadalar.logic.model.UnitType
import com.kayipadalar.logic.save.ExpeditionRun
import com.kayipadalar.logic.save.QuestProgress
import com.kayipadalar.logic.save.SaveData
import kotlin.math.pow
import kotlin.math.roundToLong

object Stat {
    const val LEVELS_COMPLETED = "levelsCompleted"
    const val PERFECT_WINS = "perfectWins"
    const val BRIDGES = "bridgesFormed"
    const val COMBOS = "combos"
    const val COMBOS_DISCOVERED = "combosDiscovered"
    const val ISLANDS_MOVED = "islandsMoved"
    const val ROTATIONS = "rotations"
    const val ENEMIES = "enemiesDefeated"
    const val BOSSES = "bossesDefeated"
    const val BUILDINGS = "buildingsUpgraded"
    const val RESEARCH = "researchDone"
    const val COLLECTION = "collectionItems"
    const val EXPEDITIONS = "expeditionsDone"
    const val ENDLESS_BEST = "endlessBest"
    const val ENDLESS_STAGES = "endlessStages"
    const val DAILY_PUZZLES = "dailyPuzzles"
    const val GOLD_EARNED = "goldEarned"
    const val CHALLENGES = "challengesWon"
    const val EVENTS = "eventsResolved"
    const val PURCHASES = "shopPurchases"
    const val DEFENSES = "defensesWon"
    const val STARS = "starsEarned"
    const val LEVELS_PLAYED = "levelsPlayed"
}

/** Kaynak cüzdanı yardımcıları. */
class Wallet(private val save: SaveData) {
    fun get(r: String): Long = save.res[r] ?: 0
    fun get(r: Res) = get(r.name)
    fun canAfford(cost: Map<String, Int>) = cost.all { (k, v) -> get(k) >= v }
    fun pay(cost: Map<String, Int>): Boolean {
        if (!canAfford(cost)) return false
        cost.forEach { (k, v) -> save.res[k] = get(k) - v }
        return true
    }
    fun add(k: String, v: Long) {
        if (v == 0L) return
        save.res[k] = maxOf(0, get(k) + v)
        if (k == Res.GOLD.name && v > 0) save.stats[Stat.GOLD_EARNED] = (save.stats[Stat.GOLD_EARNED] ?: 0) + v
    }
    fun addAll(m: Map<String, Int>, pct: Int = 100) = m.forEach { (k, v) -> add(k, v.toLong() * pct / 100) }
}

/** Binalar, araştırma, koleksiyon setleri ve çağlardan gelen kalıcı bonusların toplamı. */
class Bonuses(private val cfg: MetaConfig, private val save: SaveData) {
    fun total(kind: String): Double {
        var sum = 0.0
        for (b in cfg.buildings) if (b.effect.kind == kind) sum += b.effect.value * (save.buildings[b.id] ?: 0)
        for (id in save.research) cfg.research.firstOrNull { it.id == id }?.let { if (it.effect.kind == kind) sum += it.effect.value }
        for (s in cfg.collection) if (s.bonus.kind == kind && (0 until 10).all { "${s.id}#$it" in save.collection }) sum += s.bonus.value
        if (kind == "GOLD_REWARD_PCT") sum += save.era * cfg.economy.eraGoldPct
        if (kind == "CASTLE_HP" && "MIL5" in save.research) sum += 2
        return sum
    }
    fun int(kind: String) = kotlin.math.floor(total(kind) + 1e-9).toInt()
}

class KingdomSystem(private val cfg: MetaConfig, private val save: SaveData, private val wallet: Wallet, private val bonuses: Bonuses) {
    fun level(id: String) = save.buildings[id] ?: 0
    fun cap(id: String): Int {
        val def = cfg.building(id)
        if (id == "CASTLE") return def.maxLevel
        return minOf(def.maxLevel, level("CASTLE") + 1)
    }
    fun isUnlocked(id: String) = save.highestUnlocked > cfg.building(id).unlockLevel
    fun upgradeCost(id: String): Map<String, Int> {
        val def = cfg.building(id)
        val lvl = level(id)
        val disc = 100 - bonuses.int("BUILD_DISCOUNT_PCT")
        return def.baseCost.mapValues { (_, v) -> (v * def.growth.pow(lvl) * disc / 100).roundToLong().toInt() }
    }
    fun canUpgrade(id: String): String? {
        if (!isUnlocked(id)) return "error.buildingLocked"
        if (level(id) >= cap(id)) return if (level(id) >= cfg.building(id).maxLevel) "error.maxLevel" else "error.needCastle"
        if (!wallet.canAfford(upgradeCost(id))) return "error.noResources"
        return null
    }
    fun upgrade(id: String): String? {
        canUpgrade(id)?.let { return it }
        wallet.pay(upgradeCost(id))
        save.buildings[id] = level(id) + 1
        return null
    }

    private val hourly = mapOf(
        "WOOD_PER_HOUR" to listOf(Res.WOOD to 1.0),
        "STONE_PER_HOUR" to listOf(Res.STONE to 1.0, Res.IRON to 0.35),
        "FOOD_PER_HOUR" to listOf(Res.FOOD to 1.0),
        "CRYSTAL_PER_HOUR" to listOf(Res.CRYSTAL to 1.0),
        "GOLD_PER_HOUR" to listOf(Res.GOLD to 1.0),
    )

    /** Saatlik üretim (bonuslar dahil). Başlangıçta binasız krallık da küçük bir taban üretir. */
    fun productionPerHour(): Map<Res, Double> {
        val pct = 1.0 + bonuses.total("PRODUCTION_PCT") / 100.0
        val out = HashMap<Res, Double>()
        out[Res.GOLD] = 10.0; out[Res.WOOD] = 6.0; out[Res.FOOD] = 6.0; out[Res.STONE] = 4.0
        for ((kind, list) in hourly) {
            val v = bonuses.total(kind)
            for ((r, f) in list) out[r] = (out[r] ?: 0.0) + v * f
        }
        if ("MAG4" in save.research) out[Res.CRYSTAL] = (out[Res.CRYSTAL] ?: 0.0) * 1.25
        return out.mapValues { it.value * pct }
    }

    fun storageHours() = cfg.economy.idleCapHoursBase + bonuses.total("STORAGE_HOURS")

    fun pendingIdle(now: Long): Map<Res, Long> {
        if (save.lastIdleCollect == 0L) return emptyMap()
        val hours = minOf(storageHours(), (now - save.lastIdleCollect).coerceAtLeast(0) / 3_600_000.0)
        return productionPerHour().mapValues { (it.value * hours).toLong() }.filterValues { it > 0 }
    }

    fun collectIdle(now: Long): Map<Res, Long> {
        val p = pendingIdle(now)
        p.forEach { (r, v) -> wallet.add(r.name, v) }
        save.lastIdleCollect = now
        return p
    }

    fun tradeRate(): Double = (cfg.economy.tradeBaseRate + level("HARBOR") * 0.04) * (1 + bonuses.total("TRADE_RATE_PCT") / 100)

    /** Limanda kaynak takası: [amount] birim [from] → [to]. */
    fun trade(from: String, to: String, amount: Int): String? {
        if (from == to || amount <= 0) return "error.invalid"
        if (wallet.get(from) < amount) return "error.noResources"
        val gain = (amount * tradeRate()).toLong()
        if (gain <= 0) return "error.invalid"
        wallet.add(from, -amount.toLong()); wallet.add(to, gain)
        return null
    }
}

class ResearchSystem(private val cfg: MetaConfig, private val save: SaveData, private val wallet: Wallet, private val bonuses: Bonuses) {
    fun cost(id: String): Map<String, Int> {
        val disc = 100 - bonuses.int("RESEARCH_DISCOUNT_PCT")
        return cfg.researchNode(id).cost.mapValues { it.value * disc / 100 }
    }
    fun done(id: String) = id in save.research
    fun blockedByExclusive(id: String): Boolean {
        val n = cfg.researchNode(id)
        return n.exclusive != null && cfg.research.any { it.exclusive == n.exclusive && it.id != id && it.id in save.research }
    }
    fun prerequisitesMet(id: String) = cfg.researchNode(id).requires.all { req -> req.split('|').any { it in save.research } }
    fun canResearch(id: String): String? = when {
        done(id) -> "error.done"
        blockedByExclusive(id) -> "error.exclusive"
        !prerequisitesMet(id) -> "error.prereq"
        !wallet.canAfford(cost(id)) -> "error.noResources"
        else -> null
    }
    fun research(id: String): String? {
        canResearch(id)?.let { return it }
        wallet.pay(cost(id)); save.research.add(id)
        return null
    }
}

class UnitSystem(private val save: SaveData, private val wallet: Wallet, private val rules: com.kayipadalar.logic.data.GameConfig) {
    val maxLevel = 5
    fun level(t: UnitType) = save.unitLevels[t.name] ?: 0
    fun unlocked(t: UnitType) = t != UnitType.BOSS && (save.buildings["BARRACKS"] ?: 0) >= rules.stats(t).unlockBarracks
    fun upgradeCost(t: UnitType) = rules.stats(t).upgradeCost.mapValues { it.value * (level(t) + 1) }
    fun upgrade(t: UnitType): String? {
        if (!unlocked(t)) return "error.unitLocked"
        if (level(t) >= maxLevel) return "error.maxLevel"
        if (!wallet.pay(upgradeCost(t))) return "error.noResources"
        save.unitLevels[t.name] = level(t) + 1
        return null
    }
}

class LivesSystem(private val cfg: MetaConfig, private val save: SaveData) {
    private val regenMs get() = cfg.economy.lifeRegenMinutes * 60_000L
    val max get() = cfg.economy.livesMax
    fun update(now: Long) {
        val l = save.lives
        if (l.lives >= max) { l.lives = max; l.regenStart = now; return }
        if (now < l.regenStart) l.regenStart = now
        while (l.lives < max && now - l.regenStart >= regenMs) { l.lives++; l.regenStart += regenMs }
        if (l.lives >= max) l.regenStart = now
    }
    fun lives(now: Long): Int { update(now); return save.lives.lives }
    fun msToNext(now: Long): Long { update(now); return if (save.lives.lives >= max) 0 else regenMs - (now - save.lives.regenStart) }
    fun loseLife(now: Long) {
        update(now)
        if (save.lives.lives >= max) save.lives.regenStart = now
        save.lives.lives = maxOf(0, save.lives.lives - 1)
    }
    fun refill() { save.lives.lives = max }
}

class ShopSystem(private val cfg: MetaConfig, private val save: SaveData, private val wallet: Wallet, private val reputationLevel: () -> Int, private val grantCollectible: () -> String?) {
    fun owned(id: String) = id in save.owned
    fun canBuy(item: ShopItem): String? = when {
        item.kind == "COSMETIC" && owned(item.id) -> "error.owned"
        reputationLevel() < item.reputation -> "error.reputation"
        !wallet.canAfford(item.price) -> "error.noResources"
        else -> null
    }
    /** Satın alır. Koleksiyon ürünlerinde kazanılan öğe kimliği döner. */
    fun buy(id: String): Pair<String?, String?> {
        val item = cfg.shop.firstOrNull { it.id == id } ?: return "error.invalid" to null
        canBuy(item)?.let { return it to null }
        if (item.kind == "COLLECTION" && save.collection.size >= cfg.collectionItems.size) return "error.collectionFull" to null
        wallet.pay(item.price)
        var got: String? = null
        when (item.kind) {
            "COSMETIC" -> { save.owned.add(item.id); save.equipped[item.category] = item.id }
            "BUNDLE" -> wallet.addAll(item.bundle)
            "COLLECTION" -> got = grantCollectible()
        }
        save.stats[Stat.PURCHASES] = (save.stats[Stat.PURCHASES] ?: 0) + 1
        return null to got
    }
    fun equip(id: String): Boolean {
        val item = cfg.shop.firstOrNull { it.id == id } ?: return false
        if (!owned(id)) return false
        save.equipped[item.category] = id
        return true
    }
    fun unequip(category: String) { save.equipped.remove(category) }
    fun equippedColor(category: String): String? = save.equipped[category]?.let { id -> cfg.shop.firstOrNull { it.id == id }?.color }
}

/** Gerçek para ile satın alma için soyutlama. İlk sürümde yalnızca oyun içi para vardır. */
interface PurchaseProvider {
    val available: Boolean
    fun purchase(productId: String, onResult: (Boolean) -> kotlin.Unit)
}

object NoRealMoneyPurchases : PurchaseProvider {
    override val available = false
    override fun purchase(productId: String, onResult: (Boolean) -> kotlin.Unit) = onResult(false)
}

class QuestSystem(private val cfg: MetaConfig, private val save: SaveData, private val wallet: Wallet, private val bonuses: Bonuses) {
    private fun dayKey(wall: Long) = Math.floorDiv(wall + 3 * 3600_000L, 86_400_000L) // Türkiye saati (UTC+3)
    private fun weekKey(wall: Long) = Math.floorDiv(dayKey(wall) + 3, 7L)

    /** Gün/hafta değiştiyse görevleri yeniler. Saat ileri alma istismarına karşı güvenilir zamanla en az N saat beklenir. */
    fun refresh(wall: Long, trusted: Long) {
        val q = save.quests
        val dk = dayKey(wall)
        val minGap = cfg.economy.dailyResetMinHours * 3_600_000L
        if (q.daily.isEmpty() || (dk > q.dailyKey && trusted - q.dailyResetAt >= minGap)) {
            q.dailyKey = dk; q.dailyResetAt = trusted
            q.daily.clear()
            val pool = cfg.quests.filter { it.daily > 0 }
            val rnd = java.util.Random(dk * 31 + 7)
            pool.shuffled(rnd).take(3).forEach { q.daily.add(QuestProgress(it.id, it.daily)) }
        }
        val wk = weekKey(wall)
        if (q.weekly.isEmpty() || wk > q.weeklyKey) {
            q.weeklyKey = wk
            q.weekly.clear()
            val rnd = java.util.Random(wk * 17 + 3)
            cfg.quests.filter { it.weekly > 0 }.shuffled(rnd).take(3).forEach { q.weekly.add(QuestProgress(it.id, it.weekly)) }
        }
    }

    fun record(stat: String, amount: Long) {
        for (list in listOf(save.quests.daily, save.quests.weekly)) for (p in list) {
            val t = cfg.quests.firstOrNull { it.id == p.templateId } ?: continue
            if (t.stat == stat && !p.claimed) p.progress = minOf(p.target, p.progress + amount.toInt())
        }
    }

    fun template(p: QuestProgress) = cfg.quests.first { it.id == p.templateId }
    fun reward(p: QuestProgress, weekly: Boolean) = if (weekly) template(p).weeklyReward else template(p).reward

    fun claim(p: QuestProgress, weekly: Boolean): String? {
        if (p.claimed) return "error.claimed"
        if (p.progress < p.target) return "error.notDone"
        p.claimed = true
        wallet.addAll(reward(p, weekly), 100 + bonuses.int("QUEST_REWARD_PCT"))
        save.reputationXp += if (weekly) 40 else 10
        return null
    }
}

class AchievementSystem(private val cfg: MetaConfig, private val save: SaveData, private val wallet: Wallet) {
    val all = cfg.allAchievements()
    fun value(stat: String): Long = when (stat) {
        Stat.COMBOS_DISCOVERED -> save.discoveredCombos.size.toLong()
        Stat.RESEARCH -> save.research.size.toLong()
        Stat.COLLECTION -> save.collection.size.toLong()
        Stat.ENDLESS_BEST -> save.endless.best.toLong()
        else -> save.stats[stat] ?: 0
    }
    fun unlocked(a: Achievement) = value(a.stat) >= a.target
    fun claimable() = all.filter { unlocked(it) && it.id !in save.achievementsClaimed }
    fun claim(id: String): String? {
        val a = all.firstOrNull { it.id == id } ?: return "error.invalid"
        if (id in save.achievementsClaimed) return "error.claimed"
        if (!unlocked(a)) return "error.notDone"
        save.achievementsClaimed.add(id)
        wallet.add(Res.GOLD.name, a.gold.toLong())
        if (a.stardust > 0) wallet.add(Res.STARDUST.name, a.stardust.toLong())
        save.reputationXp += 25
        return null
    }
}

class CollectionSystem(private val cfg: MetaConfig, private val save: SaveData) {
    /** Henüz sahip olunmayan rastgele bir öğe verir; hepsi toplandıysa null. */
    fun grantRandom(seed: Long): String? {
        val missing = cfg.collectionItems.map { it.first }.filter { it !in save.collection }
        if (missing.isEmpty()) return null
        val id = missing[java.util.Random(seed).nextInt(missing.size)]
        save.collection.add(id)
        save.reputationXp += 15
        if (setOf(id.substringBefore('#')).all { s -> (0 until 10).all { "$s#$it" in save.collection } }) save.reputationXp += 100
        return id
    }
    fun name(id: String) = cfg.collectionItems.firstOrNull { it.first == id }?.second ?: id
    fun setComplete(setId: String) = (0 until 10).all { "$setId#$it" in save.collection }
    fun ownedInSet(setId: String) = (0 until 10).count { "$setId#$it" in save.collection }
}

class ExpeditionSystem(private val cfg: MetaConfig, private val save: SaveData, private val wallet: Wallet, private val bonuses: Bonuses, private val collection: CollectionSystem) {
    fun slots() = cfg.economy.expeditionSlotsBase + bonuses.int("EXPEDITION_SLOTS")
    fun durationMs(def: ExpeditionDef): Long {
        val reduce = (save.buildings["WATCHTOWER"] ?: 0) * 4 // seviye başına %4 kısalma
        return def.minutes * 60_000L * (100 - reduce) / 100
    }
    fun available(def: ExpeditionDef) = save.highestUnlocked > def.unlockLevel
    fun start(id: String, now: Long): String? {
        val def = cfg.expeditions.firstOrNull { it.id == id } ?: return "error.invalid"
        if (!available(def)) return "error.locked"
        if (save.expeditions.size >= slots()) return "error.noSlots"
        if (save.expeditions.any { it.id == id }) return "error.running"
        if (!wallet.pay(def.cost)) return "error.noResources"
        save.expeditions.add(ExpeditionRun(id, now, now + durationMs(def), now xor id.hashCode().toLong()))
        return null
    }
    data class Loot(val res: Map<String, Int>, val item: String?)
    fun claim(run: ExpeditionRun, now: Long): Pair<String?, Loot?> {
        if (now < run.endsAt) return "error.notDone" to null
        val def = cfg.expeditions.first { it.id == run.id }
        val rnd = java.util.Random(run.seed)
        val res = def.rewards.mapValues { (_, v) -> v * (85 + rnd.nextInt(31)) / 100 }
        wallet.addAll(res)
        val item = if (rnd.nextInt(100) < def.collectionChancePct) collection.grantRandom(run.seed * 7 + 1) else null
        save.expeditions.remove(run)
        save.stats[Stat.EXPEDITIONS] = (save.stats[Stat.EXPEDITIONS] ?: 0) + 1
        return null to Loot(res, item)
    }
}

class EventSystem(private val cfg: MetaConfig, private val save: SaveData, private val wallet: Wallet) {
    /** Krallığa girişte bekleme süresi dolmuşsa olasılıkla yeni olay üretir. */
    fun maybeTrigger(now: Long): RandomEventDef? {
        val st = save.events
        st.pending?.let { id -> return cfg.events.firstOrNull { it.id == id } }
        if (save.highestUnlocked < 4) return null
        if (now - st.lastEventAt < cfg.economy.eventCooldownMinutes * 60_000L) return null
        st.counter++
        st.lastEventAt = now
        val rnd = java.util.Random(st.counter * 1337 + save.createdAtWall)
        if (rnd.nextInt(100) >= cfg.economy.eventChancePct) return null
        val e = cfg.events[rnd.nextInt(cfg.events.size)]
        st.pending = e.id
        return e
    }
    fun pending() = save.events.pending?.let { id -> cfg.events.firstOrNull { it.id == id } }
    fun resolve(choiceIndex: Int): String? {
        val e = pending() ?: return "error.invalid"
        val c = e.choices.getOrNull(choiceIndex) ?: return "error.invalid"
        if (!wallet.pay(c.cost)) return "error.noResources"
        wallet.addAll(c.reward)
        save.events.pending = null
        save.stats[Stat.EVENTS] = (save.stats[Stat.EVENTS] ?: 0) + 1
        save.reputationXp += 5
        return null
    }
}

class ReputationSystem(private val cfg: MetaConfig, private val save: SaveData) {
    fun level(): Int = cfg.reputation.indexOfLast { save.reputationXp >= it.xp }.coerceAtLeast(0)
    fun title() = cfg.reputation[level()].title
    fun nextXp(): Int? = cfg.reputation.getOrNull(level() + 1)?.xp
}
