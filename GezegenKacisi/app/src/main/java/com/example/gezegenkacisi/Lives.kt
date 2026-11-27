package com.example.gezegenkacisi

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

/**
 * Bölüm modu canları: en fazla [MAX], her can [REGEN_MS] sürede (20 dk, [GameRules]) dolar.
 *
 * Süre nasıl ölçülür (saat hilesine karşı):
 *  - Telefon açık kaldığı sürece süre, telefonun saatinden (kullanıcı değiştirebilir) değil, açılıştan beri
 *    işleyen ve değiştirilemeyen sayaçtan ([Clock.elapsed]) ölçülür. Uygulama kapalıyken, zorla
 *    durdurulduğunda ya da internet yokken de doğru sayar. Saati/tarihi/saat dilimini değiştirmek etkisizdir.
 *  - Telefon yeniden başlatılınca bu sayaç sıfırlanır. Kapalı kalınan süre:
 *      * internet saati ([Clock.trustedNow]) varsa onunla tam doğru hesaplanır,
 *      * yoksa telefon saatiyle **geçici olarak** sayılır (çevrim dışı oyuncu cezalandırılmasın) ve en fazla
 *        canları doldurmaya yetecek kadar verilir. İnternet saati sonradan alınınca hesap doğrulanır:
 *        eksik sayılan süre eklenir, saati ileri alarak fazladan kazanılan süre geri alınır (canlar
 *        harcandıysa sonraki dolumlardan düşülür).
 *  - Kayıtlar imzalanır; elle değiştirilmiş kayıt fark edilirse canlar sıfırdan dolmaya başlar.
 *
 * Tamamen çevrim dışı bir oyunda kusursuz koruma mümkün değildir: hiç internete bağlanmayan biri saati ileri
 * alıp telefonu yeniden başlatarak en fazla bir kez dolum kazanabilir. Uygulama verisini silmek de (para ve
 * ilerleme ile birlikte) canları ilk kurulumdaki gibi doldurur.
 */
class Lives(private val store: Store, private val clock: Clock) {

    /** Kalıcı kayıt (oyunda SharedPreferences). [putAll] tüm değerleri tek seferde yazmalıdır. */
    interface Store {
        fun getLong(key: String, def: Long): Long
        fun contains(key: String): Boolean
        fun putAll(values: Map<String, Long>)
    }

    interface Clock {
        /** Telefon açıldığından beri geçen süre (ms, uykuda da işler); kullanıcı değiştiremez. */
        fun elapsed(): Long
        /** Kaçıncı açılış (yoksa -1). Yeniden başlatmayı anlamak için. */
        fun bootCount(): Int
        /** İnternetten doğrulanmış şimdiki zaman (UTC ms) ya da bilinmiyorsa -1. */
        fun trustedNow(): Long
        /** Telefonun saati (UTC ms). Kullanıcı değiştirebilir; yalnızca geçici hesapta kullanılır. */
        fun wallNow(): Long
    }

    companion object {
        val MAX = GameRules.MAX_LIVES
        val REGEN_MS = GameRules.LIFE_REGEN_MS

        private const val K_LIVES = "lives_count"
        private const val K_PROGRESS = "lives_progress"     // sıradaki cana doğru biriken süre
        private const val K_ELAPSED = "lives_elapsed"       // son kayıttaki sayaç değeri
        private const val K_BOOT = "lives_boot"
        private const val K_TRUSTED = "lives_trusted"       // son kayıttaki doğrulanmış zaman (-1: yok)
        private const val K_PENDING = "lives_pending"       // yeniden başlatmadan önceki doğrulanmış zaman (-1: yok)
        private const val K_INIT = "lives_init"
        private const val K_PCRED = "lives_pcred"           // doğrulama beklerken verilen süre
        private const val K_WALL = "lives_wall"             // son kayıttaki telefon saati
        private const val K_DEBT = "lives_debt"             // geri alınacak, fazladan verilmiş süre
        private const val K_SIG = "lives_sig"               // kayıt imzası

        private val SIGNED = listOf(K_LIVES, K_PROGRESS, K_ELAPSED, K_BOOT, K_TRUSTED, K_PENDING, K_PCRED, K_WALL, K_DEBT)
        private val KEY = byteArrayOf(
            0x4D, 0x4C, 0x53, 0x2D, 0x47, 0x4B, 0x2D, 0x6C, 0x69, 0x76, 0x65, 0x73,
            0x2D, 0x76, 0x33, 0x2E, 0x31, 0x9A.toByte(), 0x17, 0xE2.toByte(), 0x5C, 0x08
        )

        /** Kayıt imzası (HMAC-SHA256'nın ilk 8 baytı). */
        internal fun sign(values: Map<String, Long>): Long {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(KEY, "HmacSHA256"))
            val text = SIGNED.joinToString("|") { "$it=${values[it] ?: 0L}" }
            val h = mac.doFinal(text.toByteArray(Charsets.UTF_8))
            var v = 0L
            for (i in 0 until 8) v = (v shl 8) or (h[i].toLong() and 0xFF)
            return if (v == Long.MIN_VALUE) 1L else v
        }
    }

    var count: Int = MAX
        private set
    private var progress = 0L
    private var lastElapsed = 0L
    private var lastBoot = -1
    private var lastTrusted = -1L
    private var pendingTrusted = -1L
    private var pendingCredited = 0L
    private var lastWall = 0L
    private var debt = 0L
    private var gained = 0

    /** Kayıt elle değiştirilmiş bulundu (bu açılışta). */
    var tamperDetected = false
        private set

    init {
        if (store.getLong(K_INIT, 0L) == 0L) {
            // İlk yükleme: canlar dolu
            resetClockRefs()
            count = MAX
            progress = 0L
            lastTrusted = clock.trustedNow()
            save(init = true)
        } else {
            val values = SIGNED.associateWith { store.getLong(it, 0L) }
            val legacy = !store.contains(K_SIG) // 2.2 ve öncesi: imza yoktu, kayıt olduğu gibi alınır
            if (!legacy && store.getLong(K_SIG, 0L) != sign(values)) {
                // Kayıt elle değiştirilmiş: canlar sıfırdan dolmaya başlar
                tamperDetected = true
                resetClockRefs()
                count = 0
                progress = 0L
                lastTrusted = -1L
            } else {
                count = store.getLong(K_LIVES, MAX.toLong()).toInt().coerceIn(0, MAX)
                progress = store.getLong(K_PROGRESS, 0L).coerceIn(0L, REGEN_MS - 1)
                lastElapsed = store.getLong(K_ELAPSED, 0L)
                lastBoot = store.getLong(K_BOOT, -1L).toInt()
                lastTrusted = store.getLong(K_TRUSTED, -1L)
                pendingTrusted = store.getLong(K_PENDING, -1L)
                pendingCredited = store.getLong(K_PCRED, 0L).coerceAtLeast(0L)
                debt = store.getLong(K_DEBT, 0L).coerceIn(0L, MAX * REGEN_MS)
                // Eski sürümlerde telefon saati kaydedilmiyordu: şimdiki saat başlangıç kabul edilir
                lastWall = if (store.contains(K_WALL)) store.getLong(K_WALL, 0L) else clock.wallNow()
                if (count >= MAX) progress = 0L
            }
        }
        update()
    }

    private fun resetClockRefs() {
        lastElapsed = clock.elapsed()
        lastBoot = clock.bootCount()
        lastWall = clock.wallNow()
        pendingTrusted = -1L
        pendingCredited = 0L
        debt = 0L
    }

    /** Geçen süreyi hesaba katar; dolan canları ekler. Sık çağrılabilir. */
    fun update() {
        val e = clock.elapsed()
        val boot = clock.bootCount()
        val wall = clock.wallNow()
        val now = clock.trustedNow()
        val sameBoot = (boot == lastBoot || boot < 0 || lastBoot < 0) && e >= lastElapsed
        if (sameBoot) {
            val delta = e - lastElapsed
            give(delta)
            // Bu oturumda internet saati yoksa son doğrulanmış zamanı değiştirilemeyen sayaçla ilerlet
            if (now < 0L && lastTrusted >= 0L && pendingTrusted < 0L) lastTrusted += delta
        } else {
            // Yeniden başlatıldı. Doğrulanmış bir zaman biliniyorsa, kapalı kalınan süre onunla doğrulanacak
            if (pendingTrusted < 0L && lastTrusted >= 0L) {
                pendingTrusted = lastTrusted
                pendingCredited = 0L
            }
            // Açılıştan beri geçen süre güvenilir
            give(e)
            // Kapalı kalınan süre: telefon saatine göre, geçici olarak ve en fazla canları dolduracak kadar
            val off = wall - lastWall - e
            if (off > 0L) give(min(off, needMs()))
        }
        lastElapsed = e
        lastBoot = boot
        lastWall = wall

        if (now >= 0L) {
            if (pendingTrusted >= 0L) {
                // Gerçek geçen süre - bu arada verilen süre
                val diff = (now - pendingTrusted) - pendingCredited
                if (diff > 0L) {
                    credit(diff)
                } else if (-diff > GameRules.LIFE_CLOCK_TOLERANCE_MS) {
                    clawBack(-diff) // telefon saati ileri alınarak fazladan verilmiş süre
                }
                pendingTrusted = -1L
                pendingCredited = 0L
            }
            lastTrusted = now
        }
        save()
    }

    /** Süre verir; doğrulama bekleniyorsa verilen süreyi not eder. */
    private fun give(ms: Long) {
        if (ms <= 0L) return
        credit(ms)
        if (pendingTrusted >= 0L) pendingCredited += ms
    }

    private fun credit(ms: Long) {
        if (ms <= 0L) return
        var m = ms
        if (debt > 0L) {
            val d = min(debt, m)
            debt -= d
            m -= d
            if (m <= 0L) return
        }
        if (count >= MAX) {
            progress = 0L
            return
        }
        var p = progress + m
        while (p >= REGEN_MS && count < MAX) {
            count++
            gained++
            p -= REGEN_MS
        }
        progress = if (count >= MAX) 0L else p
    }

    /** Fazladan verilmiş süreyi geri alır: önce birikmiş süreden, sonra canlardan; yetmezse borç olur. */
    private fun clawBack(ms: Long) {
        var total = count * REGEN_MS + progress - ms
        if (total < 0L) {
            debt += -total
            total = 0L
        }
        count = (total / REGEN_MS).toInt().coerceAtMost(MAX)
        progress = if (count >= MAX) 0L else total % REGEN_MS
    }

    /** Canları doldurmak için gereken süre (borç dahil). */
    private fun needMs(): Long =
        if (count >= MAX) 0L else (MAX - count) * REGEN_MS - progress + debt

    /** Bir can harcar. Can yoksa false döner. */
    fun spend(): Boolean {
        update()
        if (count <= 0) return false
        if (count == MAX) progress = 0L // dolum sayacı şimdi başlar
        count--
        save()
        return true
    }

    /** Bir can verir (bölüm geçilince harcanan can geri gelir; reklam ya da para ile can). En fazla [MAX]. */
    fun refund(): Boolean {
        update()
        if (count >= MAX) return false
        count++
        if (count >= MAX) progress = 0L
        save()
        return true
    }

    /** Son çağrıdan beri süre dolarak kazanılan can sayısı (dolum animasyonu için). */
    fun consumeGained(): Int {
        val g = gained
        gained = 0
        return g
    }

    /** Sıradaki cana kalan süre (ms); canlar doluysa 0. */
    fun msToNext(): Long = if (count >= MAX) 0L else (REGEN_MS - progress + debt).coerceAtLeast(0L)

    /** Kalan süre "dd:ss" (bir saatten uzunsa "s:dd:ss") biçiminde. */
    fun nextText(): String {
        val s = (msToNext() + 999L) / 1000L
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    private fun save(init: Boolean = false) {
        val values = linkedMapOf(
            K_LIVES to count.toLong(),
            K_PROGRESS to progress,
            K_ELAPSED to lastElapsed,
            K_BOOT to lastBoot.toLong(),
            K_TRUSTED to lastTrusted,
            K_PENDING to pendingTrusted,
            K_PCRED to pendingCredited,
            K_WALL to lastWall,
            K_DEBT to debt
        )
        val out = HashMap<String, Long>(values)
        out[K_SIG] = sign(values)
        if (init) out[K_INIT] = 1L
        store.putAll(out)
    }
}
