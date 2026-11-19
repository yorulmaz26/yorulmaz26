package com.zamaninefendisi.logic.save

/** Platform saat kaynağı. Android'de [elapsedMs] = SystemClock.elapsedRealtime(), [bootId] = açılış sayacı. */
interface GameClock {
    fun wallMs(): Long
    fun elapsedMs(): Long
    fun bootId(): String
}

/**
 * Dört can sistemi: her can 15 dakikada, sırayla yenilenir; canlar doluyken zamanlayıcı durur.
 *
 * Saat hilesine karşı: aynı cihaz açılışı içinde ([GameClock.bootId] değişmediyse) geçen süre
 * monotonik saatten ölçülür; telefonun saatini ileri almak hiçbir şey kazandırmaz. Cihaz yeniden
 * başlatıldıysa monotonik saat sıfırlanır; o zaman duvar saati farkı kullanılır (geri alınmış saat
 * oyuncuyu cezalandırmaz, en az açılıştan beri geçen süre kadar ilerleme sayılır). Tamamen çevrimdışı
 * bir oyunda yeniden başlatma + saat değiştirme birleşimini %100 engellemek mümkün değildir; en fazla
 * canları doldurmaya yetecek kadar süre kazandırabilir.
 */
class LifeSystem(private val clock: GameClock) {

    /** Geçen süreyi hesaplayıp canları günceller ve yeni ölçüm noktasını kaydeder. */
    fun update(s: LivesState): LivesState {
        val nowWall = clock.wallMs()
        val nowElapsed = clock.elapsedMs()
        val boot = clock.bootId()
        if (s.lastWallMs == 0L && s.lastElapsedMs == 0L) {
            return s.copy(lastWallMs = nowWall, lastElapsedMs = nowElapsed, bootId = boot)
        }
        val delta = if (boot == s.bootId && nowElapsed >= s.lastElapsedMs) {
            nowElapsed - s.lastElapsedMs
        } else {
            // Yeniden başlatma: en az açılıştan beri geçen süre, en çok duvar saati farkı.
            maxOf(nowElapsed, (nowWall - s.lastWallMs).coerceAtLeast(0))
        }
        return advance(s, delta).copy(lastWallMs = nowWall, lastElapsedMs = nowElapsed, bootId = boot)
    }

    private fun advance(s: LivesState, deltaMs: Long): LivesState {
        if (s.lives >= MAX_LIVES) return s.copy(lives = MAX_LIVES, regenRemainingMs = 0)
        var lives = s.lives
        var remaining = if (s.regenRemainingMs <= 0) LIFE_REGEN_MS else s.regenRemainingMs
        var d = deltaMs
        while (d > 0 && lives < MAX_LIVES) {
            if (d >= remaining) {
                d -= remaining
                lives++
                remaining = LIFE_REGEN_MS
            } else {
                remaining -= d
                d = 0
            }
        }
        return s.copy(lives = lives, regenRemainingMs = if (lives >= MAX_LIVES) 0 else remaining)
    }

    /** Bir can harcar. Canlar doluyken harcanırsa yenilenme zamanlayıcısı o an başlar. */
    fun consume(s: LivesState): LivesState {
        val cur = update(s)
        if (cur.lives <= 0) return cur
        val wasFull = cur.lives >= MAX_LIVES
        return cur.copy(lives = cur.lives - 1, regenRemainingMs = if (wasFull) LIFE_REGEN_MS else cur.regenRemainingMs)
    }

    fun refill(s: LivesState): LivesState = update(s).copy(lives = MAX_LIVES, regenRemainingMs = 0)
}
