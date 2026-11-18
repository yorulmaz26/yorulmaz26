package com.example.gezegenkacisi

/**
 * Bölüm modu canları: en fazla [MAX], her can [REGEN_MS] sürede dolar.
 *
 * Saat hilesine karşı süre telefonun saatinden (kullanıcı değiştirebilir) değil,
 * telefon açıldığından beri işleyen ve değiştirilemeyen sayaçtan ([Clock.elapsed]) ölçülür.
 *
 * Telefon yeniden başlatılınca bu sayaç sıfırlanır. O zaman:
 *  - açıldıktan sonra geçen süre yine sayaçtan sayılır,
 *  - telefonun kapalı kaldığı süre yalnızca internetten alınan gerçek saatle ([Clock.trustedNow])
 *    doğrulanabilirse eklenir. Telefonun kendi saati hiçbir zaman can kazandırmaz.
 */
class Lives(private val store: Store, private val clock: Clock) {

    /** Kalıcı kayıt (oyunda SharedPreferences). */
    interface Store {
        fun getLong(key: String, def: Long): Long
        fun putLong(key: String, value: Long)
    }

    interface Clock {
        /** Telefon açıldığından beri geçen süre (ms); kullanıcı değiştiremez. */
        fun elapsed(): Long
        /** Kaçıncı açılış (yoksa -1). Yeniden başlatmayı anlamak için. */
        fun bootCount(): Int
        /** İnternetten doğrulanmış şimdiki zaman (ms) ya da bilinmiyorsa -1. */
        fun trustedNow(): Long
    }

    companion object {
        const val MAX = 4
        const val REGEN_MS = 15 * 60 * 1000L

        private const val K_LIVES = "lives_count"
        private const val K_PROGRESS = "lives_progress"     // sıradaki cana doğru biriken süre
        private const val K_ELAPSED = "lives_elapsed"       // son kayıttaki sayaç değeri
        private const val K_BOOT = "lives_boot"
        private const val K_TRUSTED = "lives_trusted"       // son kayıttaki doğrulanmış zaman (-1: yok)
        private const val K_PENDING = "lives_pending"       // yeniden başlatmadan önceki doğrulanmış zaman (-1: yok)
        private const val K_INIT = "lives_init"
        private const val K_PCRED = "lives_pcred"         // doğrulama beklerken sayaçtan sayılan süre
    }

    var count: Int = MAX
        private set
    private var progress = 0L
    private var lastElapsed = 0L
    private var lastBoot = -1
    private var lastTrusted = -1L
    private var pendingTrusted = -1L
    private var pendingCredited = 0L

    init {
        if (store.getLong(K_INIT, 0L) == 0L) {
            // İlk yükleme: canlar dolu
            count = MAX
            progress = 0L
            lastElapsed = clock.elapsed()
            lastBoot = clock.bootCount()
            lastTrusted = clock.trustedNow()
            store.putLong(K_INIT, 1L)
            save()
        } else {
            count = store.getLong(K_LIVES, MAX.toLong()).toInt().coerceIn(0, MAX)
            progress = store.getLong(K_PROGRESS, 0L).coerceIn(0L, REGEN_MS)
            lastElapsed = store.getLong(K_ELAPSED, 0L)
            lastBoot = store.getLong(K_BOOT, -1L).toInt()
            lastTrusted = store.getLong(K_TRUSTED, -1L)
            pendingTrusted = store.getLong(K_PENDING, -1L)
            pendingCredited = store.getLong(K_PCRED, 0L)
        }
        update()
    }

    /** Geçen süreyi hesaba katar; dolan canları ekler. Sık çağrılabilir. */
    fun update() {
        val e = clock.elapsed()
        val boot = clock.bootCount()
        val sameBoot = (boot == lastBoot || boot < 0 || lastBoot < 0) && e >= lastElapsed
        val delta: Long
        if (sameBoot) {
            delta = e - lastElapsed
        } else {
            // Yeniden başlatıldı: açılıştan beri geçen süre güvenilir
            delta = e
            // Kapalı kalınan süre ancak internet saatiyle doğrulanınca eklenecek
            if (pendingTrusted < 0L && lastTrusted >= 0L) {
                pendingTrusted = lastTrusted
                pendingCredited = 0L
            }
        }
        lastElapsed = e
        lastBoot = boot
        credit(delta)
        if (pendingTrusted >= 0L) pendingCredited += delta

        val now = clock.trustedNow()
        if (now >= 0L) {
            if (pendingTrusted >= 0L) {
                // Gerçek geçen süre - açılıştan beri zaten sayılan süre = telefonun kapalı kaldığı süre
                val off = (now - pendingTrusted) - pendingCredited
                if (off > 0L) credit(off)
                pendingTrusted = -1L
                pendingCredited = 0L
            }
            lastTrusted = now
        }
        save()
    }

    private fun credit(ms: Long) {
        if (ms <= 0L) return
        if (count >= MAX) {
            progress = 0L
            return
        }
        var p = progress + ms
        while (p >= REGEN_MS && count < MAX) {
            count++
            p -= REGEN_MS
        }
        progress = if (count >= MAX) 0L else p
    }

    /** Bir can harcar. Can yoksa false döner. */
    fun spend(): Boolean {
        update()
        if (count <= 0) return false
        if (count == MAX) progress = 0L // dolum sayacı şimdi başlar
        count--
        save()
        return true
    }

    /** Harcanan canı geri verir (bölüm geçildiğinde). */
    fun refund() {
        update()
        if (count < MAX) count++
        if (count >= MAX) progress = 0L
        save()
    }

    /** Sıradaki cana kalan süre (ms); canlar doluysa 0. */
    fun msToNext(): Long = if (count >= MAX) 0L else (REGEN_MS - progress).coerceAtLeast(0L)

    /** Kalan süre "mm:ss" biçiminde. */
    fun nextText(): String {
        val s = (msToNext() + 999L) / 1000L
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun save() {
        store.putLong(K_LIVES, count.toLong())
        store.putLong(K_PROGRESS, progress)
        store.putLong(K_ELAPSED, lastElapsed)
        store.putLong(K_BOOT, lastBoot.toLong())
        store.putLong(K_TRUSTED, lastTrusted)
        store.putLong(K_PENDING, pendingTrusted)
        store.putLong(K_PCRED, pendingCredited)
    }
}
