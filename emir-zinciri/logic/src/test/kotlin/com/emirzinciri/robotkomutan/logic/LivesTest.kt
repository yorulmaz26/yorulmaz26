package com.emirzinciri.robotkomutan.logic

import com.emirzinciri.robotkomutan.logic.progress.GameClock
import com.emirzinciri.robotkomutan.logic.progress.LivesState
import com.emirzinciri.robotkomutan.logic.progress.LivesSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeClock(var wall: Long = 1_700_000_000_000, var mono: Long = 50_000, var boot: String = "boot-1") : GameClock {
    override fun wallMs() = wall
    override fun monotonicMs() = mono
    override fun bootId() = boot
    fun advance(ms: Long) { wall += ms; mono += ms }
}

class LivesTest {
    private val min = 60_000L

    private fun setup(): Pair<LivesSystem, FakeClock> {
        val c = FakeClock()
        return LivesSystem(LivesState(), c).also { it.update() } to c
    }

    @Test fun startsFullAndTimerStopped() {
        val (l, _) = setup()
        assertEquals(4, l.lives)
        assertNull(l.msToNext())
    }

    @Test fun regeneratesOneLifePer15MinutesSequentially() {
        val (l, c) = setup()
        repeat(3) { l.loseLife() }
        assertEquals(1, l.lives)
        c.advance(14 * min); assertEquals(1, l.lives)
        c.advance(1 * min); assertEquals(2, l.lives)
        c.advance(15 * min); assertEquals(3, l.lives)
        c.advance(100 * min); assertEquals(4, l.lives)
        assertNull(l.msToNext())
    }

    @Test fun timerStartsWhenFirstLifeLost() {
        val (l, c) = setup()
        c.advance(60 * min)
        l.loseLife()
        assertEquals(15 * min, l.msToNext())
        c.advance(5 * min)
        assertEquals(10 * min, l.msToNext())
        assertEquals(10 * min, l.msToFull())
    }

    @Test fun cannotGoBelowZero() {
        val (l, _) = setup()
        repeat(4) { assertTrue(l.loseLife()) }
        assertFalse(l.loseLife())
        assertEquals(0, l.lives)
        assertEquals(60 * min, l.msToFull())
    }

    @Test fun movingClockForwardDoesNotRefillWithinSameBoot() {
        val (l, c) = setup()
        repeat(4) { l.loseLife() }
        c.wall += 24 * 60 * min // kullanıcı saati 1 gün ileri aldı
        assertEquals(0, l.lives)
        assertTrue(l.state.clockTamperSeen)
        c.mono += 15 * min      // gerçekte 15 dakika geçti
        assertEquals(1, l.lives)
    }

    @Test fun movingClockBackwardNeverLocksLives() {
        val (l, c) = setup()
        l.loseLife()
        c.wall -= 7 * 24 * 60 * min
        c.mono += 15 * min
        assertEquals(4, l.lives)
    }

    @Test fun rebootUsesWallClockAndIgnoresNegativeDelta() {
        val (l, c) = setup()
        repeat(2) { l.loseLife() }
        // Cihaz yeniden başladı: monotonik saat sıfırlandı, 20 dakika geçmiş.
        c.boot = "boot-2"; c.mono = 1_000; c.wall += 20 * min
        assertEquals(3, l.lives)
        // Yeniden başlatma + geri alınmış saat: süre kazanılmaz ama kaybedilmez de.
        c.boot = "boot-3"; c.mono = 2_000; c.wall -= 60 * min
        assertEquals(3, l.lives)
        // Ardından normal akış monotonik saatle devam eder.
        c.mono += 10 * min; c.wall += 10 * min
        assertEquals(4, l.lives)
    }

    @Test fun processRestartWithinSameBootUsesMonotonic() {
        val state = LivesState()
        val c = FakeClock()
        LivesSystem(state, c).apply { update(); loseLife() }
        c.advance(16 * min)
        assertEquals(4, LivesSystem(state, c).lives) // yeni nesne, aynı kayıt
    }

    @Test fun format() {
        assertEquals("15:00", LivesSystem.format(15 * min))
        assertEquals("00:01", LivesSystem.format(1))
    }
}
