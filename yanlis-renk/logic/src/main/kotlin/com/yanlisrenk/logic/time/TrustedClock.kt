package com.yanlisrenk.logic.time

import kotlinx.serialization.Serializable

/** Platform saatleri. Android: System.currentTimeMillis, SystemClock.elapsedRealtime, Settings.Global.BOOT_COUNT. */
interface PlatformClock {
    /** Duvar saati (kullanıcı değiştirebilir). */
    fun wallMillis(): Long
    /** Cihaz açılışından beri geçen monoton süre (uyku dahil; kullanıcı değiştiremez). */
    fun elapsedMillis(): Long
    /** Açılış sayacı; bilinmiyorsa -1. */
    fun bootCount(): Int
}

@Serializable
data class ClockState(
    /** Son işlemde gerçek zamanın en iyi tahmini (epoch ms). */
    var anchor: Long = 0L,
    /** anchor'ın alındığı andaki monoton süre. */
    var anchorElapsed: Long = 0L,
    var bootCount: Int = -1,
    /** anchor sunucu zamanından mı türetildi? */
    var verified: Boolean = false,
    /** Son kaydedilen duvar saati (yalnızca bilgi/teşhis). */
    var lastWall: Long = 0L,
    /** Yeniden başlatma sonrası henüz doğrulanamamış süre bekleniyor mu? */
    var rebootPending: Boolean = false,
    /** Bekleyen yeniden başlatma öncesi anchor doğrulanmış mıydı? */
    var pendingFromVerified: Boolean = false,
    /** Günlük işlemler için asla geri gitmeyen esnek zaman. */
    var lenientDayNow: Long = 0L,
    var initialized: Boolean = false,
    /** Algılanan saat oynamaları (yalnızca teşhis; oyuncu cezalandırılmaz). */
    var wallJumps: Int = 0,
)

/**
 * Saat hilesine dayanıklı güvenilir zaman.
 *
 * - Aynı açılış içinde geçen süre yalnızca monoton saatten ([PlatformClock.elapsedMillis]) hesaplanır;
 *   duvar saatini ileri/geri almak can yenilemeyi hızlandırmaz ya da yavaşlatmaz.
 * - Yeniden başlatma algılanırsa (açılış sayacı değişti ya da monoton saat geriledi) yalnızca
 *   kesin alt sınır olan "açılıştan beri geçen süre" hemen verilir. Kalan süre doğrulanamaz;
 *   internet bulunup sunucu zamanı alınınca ([syncServer]) eksik kalan süre geriye dönük verilir.
 * - Tamamen çevrimdışı bir cihazda yüzde yüz koruma mümkün değildir (ör. root ile monoton saat
 *   değiştirilebilir); bu sınır ARCHITECTURE.md'de belgelenmiştir.
 */
class TrustedClock(private val platform: PlatformClock, val state: ClockState) {

    /** Son çağrıdan beri geçen güvenilir süreyi döndürür ve durumu günceller. */
    fun tick(): Long {
        val e = platform.elapsedMillis()
        val boot = platform.bootCount()
        val wall = platform.wallMillis()
        if (!state.initialized) {
            state.anchor = wall
            state.anchorElapsed = e
            state.bootCount = boot
            state.lastWall = wall
            state.lenientDayNow = wall
            state.initialized = true
            return 0L
        }
        val sameBoot = e >= state.anchorElapsed && (boot < 0 || state.bootCount < 0 || boot == state.bootCount)
        val delta: Long
        if (sameBoot) {
            delta = e - state.anchorElapsed
            // duvar saati monotondan çok saptıysa sadece teşhis için say
            val expectedWall = state.lastWall + delta
            if (kotlin.math.abs(wall - expectedWall) > 5 * 60_000L) state.wallJumps++
        } else {
            // yeniden başlatma: açılıştan beri geçen süre kesin alt sınırdır
            delta = e
            if (!state.rebootPending) {
                state.rebootPending = true
                state.pendingFromVerified = state.verified
            }
            state.verified = false
        }
        state.anchor += delta
        state.anchorElapsed = e
        state.bootCount = boot
        state.lastWall = wall
        updateLenient(wall)
        return delta
    }

    private fun updateLenient(wall: Long) {
        // günlük ödüller için: doğrulanmamışsa duvar saatine en fazla 36 saat önden gitme izni ver
        val strict = state.anchor
        val candidate = if (state.verified) strict else maxOf(strict, minOf(wall, strict + 36L * 3_600_000L))
        state.lenientDayNow = maxOf(state.lenientDayNow, candidate)
    }

    /** Güvenilir şimdi (epoch ms). */
    fun now(): Long = state.anchor

    /** Günlük görev / giriş ödülü için kullanılan, asla geri gitmeyen zaman. */
    fun dayNow(): Long = state.lenientDayNow

    /**
     * Sunucu zamanı alındığında çağrılır. Yeniden başlatma sonrası doğrulanamayan süre,
     * önceki anchor doğrulanmışsa geriye dönük olarak döndürülür (can yenilemeye eklenir).
     * Tick ile aynı anda çağrıldığı varsayılır.
     */
    fun syncServer(serverNow: Long): Long {
        tick()
        var credit = 0L
        val diff = serverNow - state.anchor
        if (state.rebootPending && state.pendingFromVerified && diff > 0) credit = diff
        state.anchor = serverNow
        state.verified = true
        state.rebootPending = false
        state.pendingFromVerified = false
        state.lenientDayNow = maxOf(state.lenientDayNow, serverNow)
        return credit
    }

    val isVerified: Boolean get() = state.verified
    val hasUnverifiedGap: Boolean get() = state.rebootPending
}
