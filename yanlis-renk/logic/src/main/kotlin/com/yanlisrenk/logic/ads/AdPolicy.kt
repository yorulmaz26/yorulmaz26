package com.yanlisrenk.logic.ads

import kotlinx.serialization.Serializable

/** Reklam sıklığı durumu (kayıtta saklanır). */
@Serializable
data class AdState(
    var lastInterstitialAt: Long = 0L,
    var breaksSinceInterstitial: Int = 0,
    var rewardDay: Long = -1L,
    var rewardedToday: Int = 0,
    var interstitialsShown: Long = 0L,
    var rewardedWatched: Long = 0L,
    /** Bu molada ödüllü reklam izlendi mi? (aynı molada ikinci reklam gösterilmez) */
    var rewardedThisBreak: Boolean = false,
)

/**
 * Oyuncuyu rahatsız etmeyen reklam kuralları.
 * - Geçiş reklamı yalnızca bölüm sonu molalarında, ilk [FREE_LEVELS] bölüm boyunca hiç,
 *   sonra en az [BREAKS_BETWEEN] molada bir ve en az [MIN_GAP_MS] arayla.
 * - Ödüllü reklam tamamen isteğe bağlıdır; günde en fazla [REWARDED_PER_DAY].
 */
object AdPolicy {
    const val FREE_LEVELS = 8
    const val BREAKS_BETWEEN = 3
    const val MIN_GAP_MS = 150_000L
    const val REWARDED_PER_DAY = 6

    /** Bir bölüm/koşu sonu molası. Geçiş reklamı gösterilmeli mi? */
    fun onBreak(s: AdState, now: Long, completedLevels: Int): Boolean {
        s.breaksSinceInterstitial++
        val skipForRewarded = s.rewardedThisBreak
        s.rewardedThisBreak = false
        if (skipForRewarded) return false
        if (completedLevels < FREE_LEVELS) return false
        if (s.breaksSinceInterstitial < BREAKS_BETWEEN) return false
        if (s.lastInterstitialAt > 0L && now - s.lastInterstitialAt < MIN_GAP_MS) return false
        return true
    }

    fun interstitialShown(s: AdState, now: Long) {
        s.lastInterstitialAt = now
        s.breaksSinceInterstitial = 0
        s.interstitialsShown++
    }

    fun rewardedLeft(s: AdState, day: Long): Int = if (s.rewardDay != day) REWARDED_PER_DAY else (REWARDED_PER_DAY - s.rewardedToday).coerceAtLeast(0)

    fun rewardedWatched(s: AdState, day: Long) {
        if (s.rewardDay != day) { s.rewardDay = day; s.rewardedToday = 0 }
        s.rewardedToday++
        s.rewardedWatched++
        s.rewardedThisBreak = true
    }
}
