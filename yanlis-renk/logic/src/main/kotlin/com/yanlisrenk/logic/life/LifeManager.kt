package com.yanlisrenk.logic.life

import kotlinx.serialization.Serializable

@Serializable
data class LifeState(
    var lives: Int = LifeManager.MAX_LIVES,
    /** Sıradaki canın dolmasına kalan süre (ms). Canlar tamsa sayaç durur. */
    var regenRemaining: Long = LifeManager.REGEN_MS,
    /** Bir sonraki başarısızlıkta can kaybını önleyen kalkan aktif mi? */
    var shieldArmed: Boolean = false,
)

/**
 * Dört can sistemi. Zaman yalnızca güvenilir saatten gelen süre ([credit]) ile ilerler.
 * - Başarısız bölüm 1 can götürür; başarılı bölüm can götürmez.
 * - Her eksik can 15 dakikada, sırayla dolar.
 * - Yeni bir kayıp mevcut sayacı sıfırlamaz.
 */
class LifeManager(val state: LifeState) {
    companion object {
        const val MAX_LIVES = 4
        const val REGEN_MS = 15 * 60_000L
    }

    val lives: Int get() = state.lives
    val isFull: Boolean get() = state.lives >= MAX_LIVES
    val canPlay: Boolean get() = state.lives > 0

    /** Sıradaki cana kalan süre; canlar tamsa 0. */
    val nextLifeIn: Long get() = if (isFull) 0L else state.regenRemaining

    /** Tüm canların dolmasına kalan süre. */
    val fullIn: Long get() = if (isFull) 0L else state.regenRemaining + (MAX_LIVES - 1 - state.lives) * REGEN_MS

    /** Geçen güvenilir süreyi uygular. Dolan can sayısını döndürür. */
    fun credit(ms: Long): Int {
        if (ms <= 0 || isFull) return 0
        var left = ms
        var gained = 0
        while (left > 0 && !isFull) {
            if (left >= state.regenRemaining) {
                left -= state.regenRemaining
                state.lives++
                gained++
                state.regenRemaining = REGEN_MS
            } else {
                state.regenRemaining -= left
                left = 0
            }
        }
        if (isFull) state.regenRemaining = REGEN_MS
        return gained
    }

    /**
     * Başarısız bölüm. Kalkan varsa can yerine kalkan harcanır.
     * @return true → can kaybedildi, false → kalkan korudu
     */
    fun onLevelFailed(): Boolean {
        if (state.shieldArmed) { state.shieldArmed = false; return false }
        if (state.lives <= 0) return true
        if (isFull) state.regenRemaining = REGEN_MS // sayaç şimdi başlar
        state.lives--
        return true
    }

    fun addLives(n: Int) {
        state.lives = minOf(MAX_LIVES, state.lives + n)
        if (isFull) state.regenRemaining = REGEN_MS
    }

    fun refill() = addLives(MAX_LIVES)
}

fun formatMs(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
