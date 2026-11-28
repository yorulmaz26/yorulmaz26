package com.besaniyelik.dunya.progress

import com.besaniyelik.dunya.GameConfig

/**
 * Can yenilenmesi için zaman kaynağı. Çevrimdışı bir oyunda saat değişikliğine karşı tam koruma mümkün değildir;
 * burada şu katmanlar kullanılır:
 *  - Uygulama açıkken ve cihaz yeniden başlatılmadan kapatılıp açıldığında MONOTONİK saat (Android: elapsedRealtime,
 *    uykuyu da sayar, kullanıcı değiştiremez) esas alınır: telefon saatini ileri/geri almak hiçbir şey kazandırmaz.
 *  - Cihaz yeniden başlatıldıysa (açılış sayacı değişti ya da monotonik saat geriye gitti) yalnızca UTC duvar saati
 *    kullanılabilir; geriye gitmişse süre 0 sayılır (oyuncu cezalandırılmaz, yalnızca kazanmaz), ileri gitmişse
 *    en fazla canları doldurmaya yetecek süre sayılır.
 *  - Saat dilimi değişikliği etkisizdir (UTC milisaniye kullanılır).
 */
interface TimeSource {
    /** Duvar saati, UTC epoch milisaniye (kullanıcı değiştirebilir). */
    fun wallMs(): Long
    /** Monotonik saat, milisaniye (kullanıcı değiştiremez; cihaz yeniden başlayınca sıfırlanır). */
    fun monoMs(): Long
    /** Cihazın kaçıncı kez açıldığı; bilinmiyorsa -1. */
    fun bootCount(): Int = -1
}

object SystemTimeSource : TimeSource {
    override fun wallMs(): Long = System.currentTimeMillis()
    override fun monoMs(): Long = System.nanoTime() / 1_000_000L
}

/** Kalıcı can hesapları (saf mantık; ekran ve kayıt bilmez). */
object LifeManager {
    val max: Int get() = GameConfig.MAX_LIVES
    val regenMs: Long get() = GameConfig.LIFE_REGEN_SECONDS * 1000L

    /** Kayıttan gelen değerleri güvenli aralığa çeker (negatif can, sınır üstü can, bozuk süre olmaz). */
    fun sanitize(s: LivesState) {
        s.lives = s.lives.coerceIn(0, max)
        s.progressMs = s.progressMs.coerceIn(0L, regenMs - 1)
        if (s.lives >= max) s.progressMs = 0L
    }

    /**
     * Son denetim noktasından bu yana geçen GÜVENİLİR süreyi canlara işler ve denetim noktasını şimdiye taşır.
     * Kazanılan can sayısını döndürür.
     */
    fun update(s: LivesState, ts: TimeSource): Int {
        val wall = ts.wallMs(); val mono = ts.monoMs(); val boot = ts.bootCount()
        sanitize(s)
        if (s.lastWallMs <= 0L) { checkpoint(s, wall, mono, boot); return 0 }
        val elapsed = trustedElapsed(s, wall, mono, boot)
        checkpoint(s, wall, mono, boot)
        return credit(s, elapsed)
    }

    fun sameBoot(s: LivesState, mono: Long, boot: Int): Boolean =
        if (boot >= 0 || s.lastBoot >= 0) boot == s.lastBoot && mono >= s.lastMonoMs
        else mono >= s.lastMonoMs

    fun trustedElapsed(s: LivesState, wall: Long, mono: Long, boot: Int): Long {
        val e = if (sameBoot(s, mono, boot)) mono - s.lastMonoMs else wall - s.lastWallMs
        return e.coerceIn(0L, regenMs * max)
    }

    private fun checkpoint(s: LivesState, wall: Long, mono: Long, boot: Int) {
        s.lastWallMs = wall; s.lastMonoMs = mono; s.lastBoot = boot
    }

    fun credit(s: LivesState, elapsedMs: Long): Int {
        if (s.lives >= max) { s.progressMs = 0L; return 0 }
        s.progressMs += elapsedMs.coerceAtLeast(0L)
        var gained = 0
        while (s.progressMs >= regenMs && s.lives < max) { s.lives++; s.progressMs -= regenMs; gained++ }
        if (s.lives >= max) s.progressMs = 0L
        return gained
    }

    /** Bir can harcar (ör. bölüm denemesi başlarken). Can yoksa false. Süren yenilenme sayacı sıfırlanmaz. */
    fun consume(s: LivesState): Boolean {
        if (s.lives <= 0) return false
        s.lives--
        return true
    }

    /** Harcanan canı geri verir (bölüm geçildi ya da hiç kalp kaybetmeden çıkıldı); en fazla sınıra kadar. */
    fun refund(s: LivesState) = add(s, 1)

    fun add(s: LivesState, n: Int) {
        s.lives = (s.lives + n).coerceIn(0, max)
        if (s.lives >= max) s.progressMs = 0L
    }

    fun fill(s: LivesState) { s.lives = max; s.progressMs = 0L }

    /** Bir sonraki cana kalan süre (ms); canlar doluysa null (sayaç durur). */
    fun msToNext(s: LivesState): Long? = if (s.lives >= max) null else (regenMs - s.progressMs).coerceAtLeast(0L)

    /** Canların tamamen dolmasına kalan süre (ms); doluysa null. */
    fun msToFull(s: LivesState): Long? = msToNext(s)?.let { it + (max - s.lives - 1) * regenMs }

    /** "19:42" ya da bir saatten uzunsa "1:05:09". */
    fun clock(ms: Long): String {
        val total = (ms + 999) / 1000
        val h = total / 3600; val m = (total % 3600) / 60; val sec = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }
}

/**
 * Oyunun can servisi: kayıt + zaman kaynağı + kurallar. Bölüm denemesi başlarken 1 can harcanır; bölüm geçilirse ya
 * da denemeden hiç kalp kaybetmeden çıkılırsa can geri verilir. Böylece yalnızca BAŞARISIZ denemeler can götürür ve
 * deneme sırasında uygulamayı zorla kapatmak canı kurtarmaz. Sonsuz Dünya ve ilk [GameConfig.FREE_LEVELS] bölüm
 * can harcamaz.
 */
class LifeService(private val profile: ProfileService, private val time: TimeSource) {
    private val s get() = profile.data.lives
    private var acc = 0f
    /** Can sayısı değişince (yenilenme, reklam, elmas) çağrılır: göstergeler canlandırma yapar. */
    var onChanged: ((gained: Int) -> Unit)? = null

    val enabled get() = GameConfig.LIVES_ENABLED
    val lives get() = s.lives
    val max get() = LifeManager.max
    val full get() = s.lives >= LifeManager.max

    /** Zamanı işler; kazanılan can varsa kaydeder. */
    fun update(): Int {
        val g = LifeManager.update(s, time)
        if (g > 0) { profile.save(); onChanged?.invoke(g) }
        return g
    }

    /** Her karede çağrılır; saniyede bir günceller. */
    fun tick(dt: Float) { acc += dt; if (acc >= 1f) { acc = 0f; update() } }

    fun msToNext(): Long? { return LifeManager.msToNext(s) }
    fun msToFull(): Long? = LifeManager.msToFull(s)

    /** Bu bölüm can harcar mı? */
    fun costs(levelId: Int): Boolean = enabled && levelId > GameConfig.FREE_LEVELS

    /** Deneme başlatılabilir mi? Gerekirse 1 can harcar ve kaydeder. Harcandıysa [Start.spent] true. */
    fun tryStart(levelId: Int): Start {
        if (!costs(levelId)) return Start(true, false)
        update()
        if (!LifeManager.consume(s)) return Start(false, false)
        profile.save(); onChanged?.invoke(0)
        return Start(true, true)
    }

    fun refund() { LifeManager.refund(s); profile.save(); onChanged?.invoke(0) }

    fun add(n: Int) { update(); LifeManager.add(s, n); profile.save(); onChanged?.invoke(n) }

    /** Canları elmasla doldurur. Yeterli elmas yoksa ya da canlar doluysa false. */
    fun refillWithGems(): Boolean {
        update()
        if (full || profile.data.gems < GameConfig.LIFE_REFILL_GEMS) return false
        profile.data.gems -= GameConfig.LIFE_REFILL_GEMS
        LifeManager.fill(s)
        profile.save(); onChanged?.invoke(1)
        return true
    }

    class Start(val ok: Boolean, val spent: Boolean)
}
