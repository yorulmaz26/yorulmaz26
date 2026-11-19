package com.kayipadalar.logic.meta

import com.kayipadalar.logic.save.ClockState

/** Platformdan alınan zaman örneği. [mono]: önyüklemeden beri geçen ms (Android: SystemClock.elapsedRealtime). */
data class TimeSample(val wall: Long, val mono: Long, val boot: Long)

fun interface TimeSource { fun now(): TimeSample }

/**
 * Manipülasyona dayanıklı oyun saati.
 *
 * - Aynı önyükleme içinde geçen süre monoton saatten ölçülür; cihaz saatini ileri almak işe yaramaz.
 * - Yeniden başlatma sonrası (monoton saat sıfırlandığında) duvar saatine güvenilir; geriye giden
 *   saat süre kazandırmaz ama zamanlayıcıları da kilitlemez (sıfır kabul edilir).
 * - Tamamen çevrimdışı bir oyunda yeniden başlatma + saat ileri alma kombinasyonunu %100 engellemek
 *   mümkün değildir; bu durumda tek seferlik kazanç [maxOfflineGapMs] ile sınırlandırılır.
 */
class TrustedClock(private val state: ClockState, private val source: TimeSource, private val maxOfflineGapMs: Long = 3L * 24 * 3600 * 1000) {

    /** Güvenilir oyun zamanını günceller ve döndürür (ms). */
    fun now(): Long {
        val s = source.now()
        if (state.lastBoot == -1L && state.lastWall == 0L) {
            state.trusted = s.wall
            remember(s)
            return state.trusted
        }
        val delta: Long = if (s.boot == state.lastBoot && s.mono >= state.lastMono) {
            val monoDelta = s.mono - state.lastMono
            val wallDelta = s.wall - state.lastWall
            if (wallDelta - monoDelta > 5 * 60_000L) state.suspicious++
            monoDelta
        } else {
            val wallDelta = s.wall - state.lastWall
            when {
                wallDelta < 0 -> { state.suspicious++; 0L }
                wallDelta > maxOfflineGapMs -> maxOfflineGapMs
                else -> wallDelta
            }
        }
        state.trusted += delta
        remember(s)
        return state.trusted
    }

    /** Güvenilir zamanın duvar saati karşılığı (gün anahtarları için); geriye alınmış saati yok sayar. */
    fun wallNow(): Long = maxOf(source.now().wall, state.lastWall)

    private fun remember(s: TimeSample) {
        state.lastWall = s.wall
        state.lastMono = s.mono
        state.lastBoot = s.boot
    }
}
