package com.emirzinciri.robotkomutan.logic.progress

import com.emirzinciri.robotkomutan.logic.level.LevelDef
import com.emirzinciri.robotkomutan.logic.sim.Outcome

data class Reward(val gold: Long = 0, val metal: Long = 0, val chips: Long = 0, val xp: Long = 0) {
    val isEmpty get() = gold == 0L && metal == 0L && chips == 0L && xp == 0L
    operator fun plus(o: Reward) = Reward(gold + o.gold, metal + o.metal, chips + o.chips, xp + o.xp)
}

data class WinResult(
    val stars: Int,
    val previousStars: Int,
    val firstClear: Boolean,
    val reward: Reward,
    val newBestCards: Boolean,
)

/** Bölüm açma, ödül ve can kuralları. Saf mantık: arayüz ve kayıt dosyasından bağımsız test edilir. */
class Progression(val save: SaveData, clock: GameClock) {
    val lives = LivesSystem(save.lives, clock)
    val factory = com.emirzinciri.robotkomutan.logic.factory.Factory(save.factory, save.wallet, clock) { save.meta.collection.size }
    val expeditions = com.emirzinciri.robotkomutan.logic.meta.Expeditions(save, clock)
    val meta = com.emirzinciri.robotkomutan.logic.meta.Meta(save)

    fun isCompleted(levelId: Int) = save.stars(levelId) > 0

    /** Bölüm 1 açıktır; diğerleri bir önceki bölüm tamamlanınca açılır. */
    fun isUnlocked(levelId: Int) = levelId == 1 || isCompleted(levelId - 1)

    /** İlk alan (öğretici) ve daha önce tamamlanan bölümlerin tekrarı can harcamaz. */
    fun costsLife(def: LevelDef) = def.area > 1 && !isCompleted(def.id)

    fun canStart(def: LevelDef) = isUnlocked(def.id) && (!costsLife(def) || lives.lives > 0)

    fun saveDraft(levelId: Int, programs: Map<String, List<String>>) {
        save.drafts[levelId] = programs.toMutableMap()
    }

    fun draft(levelId: Int): Map<String, List<String>> = save.drafts[levelId] ?: emptyMap()

    /** Simülasyon başladığında çağrılır. */
    fun onRunStarted(def: LevelDef) {
        save.record(def.id).attempts++
        save.stats.runs++
    }

    /** Başarısız sonuç görüldüğünde çağrılır. Can düştüyse true. */
    fun onFail(def: LevelDef, ticks: Int): Boolean {
        save.stats.fails++
        meta.count(com.emirzinciri.robotkomutan.logic.meta.Counter.FAILS)
        save.stats.ticksSimulated += ticks
        val costs = costsLife(def)
        if (costs && lives.loseLife()) { save.stats.livesLost++; return true }
        return false
    }

    fun onWin(def: LevelDef, outcome: Outcome.Win, stars: Int): WinResult {
        val rec = save.record(def.id)
        val prev = rec.stars
        val first = rec.completions == 0
        val st = outcome.stats
        val newBest = rec.bestCards == 0 || st.cards < rec.bestCards
        rec.completions++
        rec.stars = maxOf(prev, stars)
        if (newBest) rec.bestCards = st.cards
        if (rec.bestTicks == 0 || st.ticks < rec.bestTicks) rec.bestTicks = st.ticks
        save.stats.wins++
        meta.count(com.emirzinciri.robotkomutan.logic.meta.Counter.WINS)
        save.stats.ticksSimulated += st.ticks

        var reward = Reward()
        if (first) reward += firstClearReward(def)
        val gainedStars = (stars - prev).coerceAtLeast(0)
        if (gainedStars > 0) reward += Reward(gold = 5L * gainedStars * tier(def), xp = 5L * gainedStars)
        if (!first && gainedStars == 0) reward += Reward(gold = 1)
        // Araştırma bonusları (Komut Teknolojisi)
        val gb = factory.bonus(com.emirzinciri.robotkomutan.logic.factory.Effect.GOLD_BONUS)
        val xb = factory.bonus(com.emirzinciri.robotkomutan.logic.factory.Effect.XP_BONUS)
        if (gb > 0 || xb > 0) reward = reward.copy(gold = reward.gold * (100 + gb) / 100, xp = reward.xp * (100 + xb) / 100)
        apply(reward)
        return WinResult(rec.stars, prev, first, reward, newBest && !first)
    }

    fun apply(r: Reward) {
        meta.count(com.emirzinciri.robotkomutan.logic.meta.Counter.GOLD_EARNED, r.gold)
        save.wallet.gold += r.gold
        save.wallet.metal += r.metal
        save.wallet.chips += r.chips
        save.xp += r.xp
    }

    val playerLevel: Int get() = levelForXp(save.xp)

    companion object {
        private fun tier(def: LevelDef) = 1 + (def.id - 1) / 25

        fun firstClearReward(def: LevelDef): Reward {
            val t = tier(def)
            val finale = def.id % 5 == 0
            return Reward(
                gold = 20L * t + if (finale) 30L * t else 0,
                metal = 5L * t + if (finale) 10L * t else 0,
                chips = if (finale) 1L * t else 0,
                xp = 20L + 2L * def.id,
            )
        }

        /** Oyuncu seviyesi: her seviye bir öncekinden biraz daha fazla deneyim ister. */
        fun levelForXp(xp: Long): Int {
            var lvl = 1
            var need = 100L
            var left = xp
            while (left >= need) { left -= need; lvl++; need += 40 }
            return lvl
        }
    }
}
