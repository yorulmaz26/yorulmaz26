package com.emirzinciri.robotkomutan.logic.progress

import kotlinx.serialization.Serializable

/**
 * Zaman kaynağı. Android'de: duvar saati = System.currentTimeMillis(), monotonik = SystemClock.elapsedRealtime()
 * (derin uykuyu da sayar, kullanıcı değiştiremez), açılış kimliği = Settings.Global.BOOT_COUNT.
 */
interface GameClock {
    fun wallMs(): Long
    fun monotonicMs(): Long
    /** Cihaz her yeniden başladığında değişen kimlik. Bilinmiyorsa işlem başına rastgele bir değer verilebilir. */
    fun bootId(): String
}

@Serializable
data class LivesState(
    var lives: Int = LivesSystem.MAX_LIVES,
    /** Sıradaki cana doğru biriken süre. */
    var progressMs: Long = 0,
    var lastWallMs: Long = 0,
    var lastMonoMs: Long = 0,
    var lastBootId: String = "",
    /** Son güncellemede duvar saatiyle monotonik saat arasında tutarsızlık görüldüyse true (bilgi amaçlı). */
    var clockTamperSeen: Boolean = false,
)

/**
 * Dört can sistemi: her eksik can 15 dakikada, sırayla dolar; canlar doluyken sayaç durur.
 *
 * Saat hilesine karşı koruma:
 *  - Aynı açılış (boot) içinde geçen süre yalnızca monotonik saatten ölçülür; telefon saatini ileri almak can doldurmaz,
 *    geri almak can kaybettirmez.
 *  - Cihaz yeniden başlatıldıysa monotonik saat sıfırlanır; bu durumda duvar saati farkı kullanılır, negatifse 0 sayılır.
 *
 * Sınır (belgelenmiştir): tamamen çevrimdışı bir oyunda "saati ileri al + cihazı yeniden başlat" kombinasyonu
 * tespit edilemez. Meşru oyuncunun canı hiçbir durumda kalıcı olarak kilitlenmez: süre yalnızca ileriye birikir.
 */
class LivesSystem(val state: LivesState, private val clock: GameClock) {

    /** Geçen süreyi güvenilir biçimde hesaplayıp canları günceller. Her okumadan önce çağrılır. */
    fun update() {
        val wall = clock.wallMs()
        val mono = clock.monotonicMs()
        val boot = clock.bootId()
        val s = state
        if (s.lastWallMs == 0L && s.lastMonoMs == 0L && s.lastBootId.isEmpty()) {
            remember(wall, mono, boot); return
        }
        val delta: Long = if (boot == s.lastBootId && mono >= s.lastMonoMs) {
            val monoDelta = mono - s.lastMonoMs
            val wallDelta = wall - s.lastWallMs
            if (kotlin.math.abs(wallDelta - monoDelta) > TAMPER_TOLERANCE_MS) s.clockTamperSeen = true
            monoDelta
        } else {
            (wall - s.lastWallMs).coerceAtLeast(0)
        }
        addTime(delta)
        remember(wall, mono, boot)
    }

    private fun remember(wall: Long, mono: Long, boot: String) {
        state.lastWallMs = wall; state.lastMonoMs = mono; state.lastBootId = boot
    }

    private fun addTime(delta: Long) {
        val s = state
        if (s.lives >= MAX_LIVES) { s.lives = MAX_LIVES; s.progressMs = 0; return }
        s.progressMs += delta
        while (s.progressMs >= REGEN_MS && s.lives < MAX_LIVES) {
            s.lives++; s.progressMs -= REGEN_MS
        }
        if (s.lives >= MAX_LIVES) s.progressMs = 0
    }

    val lives: Int get() { update(); return state.lives }

    /** Sıradaki cana kalan süre; canlar doluysa null. */
    fun msToNext(): Long? { update(); return if (state.lives >= MAX_LIVES) null else REGEN_MS - state.progressMs }

    /** Bütün canların dolmasına kalan süre; doluysa 0. */
    fun msToFull(): Long {
        update()
        val missing = MAX_LIVES - state.lives
        return if (missing <= 0) 0 else REGEN_MS - state.progressMs + (missing - 1) * REGEN_MS
    }

    /** Başarısız denemede bir can düşer. Canlar doluyken düşerse sayaç şimdi başlar. */
    fun loseLife(): Boolean {
        update()
        if (state.lives <= 0) return false
        if (state.lives == MAX_LIVES) state.progressMs = 0
        state.lives--
        return true
    }

    /** Ödül olarak can verir (kapasiteyi aşmaz). */
    fun grant(n: Int) {
        update()
        state.lives = (state.lives + n).coerceAtMost(MAX_LIVES)
        if (state.lives == MAX_LIVES) state.progressMs = 0
    }

    companion object {
        const val MAX_LIVES = 4
        const val REGEN_MS = 15 * 60 * 1000L
        const val TAMPER_TOLERANCE_MS = 2 * 60 * 1000L

        fun format(ms: Long): String {
            val total = (ms + 999) / 1000
            return "%02d:%02d".format(total / 60, total % 60)
        }
    }
}
