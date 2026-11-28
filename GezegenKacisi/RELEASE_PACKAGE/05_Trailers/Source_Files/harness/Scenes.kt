import android.app.Activity
import android.graphics.Bitmap
import com.example.gezegenkacisi.Difficulty
import com.example.gezegenkacisi.GameView
import com.example.gezegenkacisi.NetLink
import org.robolectric.Robolectric
import kotlin.random.Random

/**
 * Gerçek oyun sahneleri. Her oynanış sahnesi önce kaydedilmeden (kuru) oynanır; otomatik pilot istenen süre
 * boyunca yanmadan uçabiliyorsa aynı dünya tohumu ve pilot tohumuyla bu kez kaydedilerek yeniden oynanır
 * (oyun belirlenimli olduğu için aynı sonuç çıkar). Çarpışma, puan ve fizik oyunun kendisine aittir.
 */
class Scenes(val act: Activity, val w: Int, val h: Int, val lang: String) {
    val fps = 30
    private val sub = 2 // 60 Hz simülasyon, 30 fps kayıt

    fun studio(prefs: Map<String, Any> = emptyMap()): Studio {
        val s = Studio(act, w, h, lang, StudioAds(), prefs + mapOf("coins" to 2500, "level_max" to 140))
        s.call("finishSplash"); s.step(0.2f)
        s.set("hubFade", 0f)
        return s
    }

    private fun resetSeed(s: Studio, seed: Long) {
        GameView::class.java.getDeclaredMethod("resetGame", Long::class.javaPrimitiveType).apply { isAccessible = true }.invoke(s.v, seed)
    }

    private fun startEndless(s: Studio, map: Int, diff: Int, seed: Long) {
        s.call("toMenu")
        s.call("setMap", map, false)
        s.call("selectDifficulty", Difficulty.entries[diff])
        s.call("startReady")
        resetSeed(s, seed)
        s.step(0.2f)
        s.call("onPress", true)
    }

    private fun startLevel(s: Studio, n: Int, seed: Long) {
        s.call("openLevel", n)
        resetSeed(s, seed)
        s.step(0.2f)
        s.call("onPress", true)
    }

    /** Pilotla [sec] saniye oynar; [frame] null değilse her kayıt karesinde çağrılır. Yanmazsa true. */
    private fun play(s: Studio, pilot: Pilot, sec: Float, frame: ((Bitmap?, Int, Boolean) -> Unit)?,
                     until: (() -> Boolean)? = null): Boolean {
        val dt = 1f / (fps * sub)
        val total = (sec * fps).toInt()
        for (f in 0 until total) {
            var tapped = false
            for (k in 0 until sub) {
                val st = s.stateName()
                if (st == "PLAYING" && pilot.decide(dt)) { s.call("onPress", true); tapped = true }
                s.call("update", dt)
                val now = s.stateName()
                if (now == "DYING" || now == "OVER") return false
            }
            frame?.invoke(null, f, tapped)
            if (until != null && until()) return true
        }
        return true
    }

    /** Sonsuz mod oynanış sahnesi: [pre] saniye kaydetmeden ısınır, sonra [sec] saniye kaydeder. */
    fun endless(map: Int, diff: Int, pre: Float, sec: Float, out: (Studio, Int, Boolean) -> Unit, tries: Int = 40): Long {
        for (i in 0 until tries) {
            val seed = 1000L * map + 77L * i + diff
            // Kuru deneme
            val dry = studio()
            startEndless(dry, map, diff, seed)
            val pd = Pilot(dry).apply { rng = Random(seed) }
            if (!play(dry, pd, pre + sec + 0.6f, null)) continue
            // Kayıt (aynı tohumlarla)
            val s = studio()
            startEndless(s, map, diff, seed)
            val p = Pilot(s).apply { rng = Random(seed) }
            if (!play(s, p, pre, null)) continue
            var ok = true
            play(s, p, sec, { _, f, tapped -> out(s, f, tapped) }) .also { ok = it }
            if (ok) return seed
            println("UYARI: kayıt kuru denemeden farklı çıktı (map=$map seed=$seed)")
        }
        error("sahne bulunamadı map=$map")
    }

    /** Bölüm sonu: kaydetmeden hedefin 1 altına kadar oynar, son geçidi ve "BÖLÜM GEÇİLDİ" ekranını kaydeder. */
    fun levelWin(level: Int, sec: Float, out: (Studio, Int, Boolean) -> Unit, tries: Int = 40): Long {
        for (i in 0 until tries) {
            val seed = 5000L + 31L * i
            val target = com.example.gezegenkacisi.Levels.info(level).target
            val dry = studio()
            startLevel(dry, level, seed)
            val pd = Pilot(dry).apply { rng = Random(seed) }
            if (!play(dry, pd, 120f, null) { (dry.get("score") as Int) >= target - 1 }) continue
            if (!play(dry, pd, 6f, null) { dry.stateName() == "LEVEL_DONE" }) continue
            if (dry.stateName() != "LEVEL_DONE") continue
            val s = studio()
            startLevel(s, level, seed)
            val p = Pilot(s).apply { rng = Random(seed) }
            if (!play(s, p, 120f, null) { (s.get("score") as Int) >= target - 1 }) continue
            // Son geçide kadar ~1,5 sn önce kayda başlansın: hedefin 1 altına ulaştıktan sonra kayıt
            var f = 0
            val total = (sec * fps).toInt()
            val dt = 1f / (fps * sub)
            var failed = false
            while (f < total) {
                var tapped = false
                for (k in 0 until sub) {
                    if (s.stateName() == "PLAYING" && p.decide(dt)) { s.call("onPress", true); tapped = true }
                    s.call("update", dt)
                    val st = s.stateName()
                    if (st == "DYING" || st == "OVER") { failed = true }
                }
                if (failed) break
                out(s, f, tapped)
                f++
            }
            if (!failed) return seed
            println("UYARI: bölüm sonu kaydı farklı çıktı")
        }
        error("bölüm sonu sahnesi bulunamadı")
    }

    /** Harita seçimi: her [every] saniyede bir sonraki haritaya kayar (sonsuz mod menüsü). */
    fun mapCarousel(sec: Float, every: Float, startMap: Int, out: (Studio, Int, Boolean) -> Unit) {
        val s = studio()
        s.call("toMenu"); s.call("setMap", startMap, false)
        s.call("selectDifficulty", Difficulty.NORMAL)
        s.step(0.4f)
        val dt = 1f / fps
        val total = (sec * fps).toInt()
        var next = every * 0.6f
        for (f in 0 until total) {
            val t = f * dt
            var swiped = false
            if (t >= next) { s.call("changeMap", 1); next += every; swiped = true }
            s.step(dt, fps)
            out(s, f, swiped)
        }
    }

    /** Market: kartlara bakar, sonra bir aracı satın alıp seçer (gerçek dokunuşla). */
    fun shop(sec: Float, buyAt: Float, card: Int, out: (Studio, Int, Boolean) -> Unit) {
        val s = studio(mapOf("skins" to "NIGHT,EMERALD", "skin" to "EMERALD"))
        s.state("MARKET")
        s.step(0.3f)
        val dt = 1f / fps
        val total = (sec * fps).toInt()
        var bought = false
        for (f in 0 until total) {
            var ev = false
            if (!bought && f * dt >= buyAt) {
                @Suppress("UNCHECKED_CAST")
                val cards = s.get("marketCards") as Array<android.graphics.RectF>
                val r = cards[card]
                s.set("downX", r.centerX()); s.set("downY", r.centerY())
                s.call("marketTap", r.centerX(), r.centerY())
                bought = true; ev = true
            }
            s.step(dt, fps)
            out(s, f, ev)
        }
    }

    /**
     * 2 oyunculu bölünmüş ekran: iki gerçek oyun örneği aynı tohumla aynı maçı oynar, konumlarını ağdaki
     * gibi "S" mesajlarıyla birbirine iletir (yalnızca ağ taşıması yerine doğrudan çağrı).
     */
    fun multiplayer(sec: Float, map: Int, out: (Studio, Int, Boolean) -> Unit, tries: Int = 30) {
        fun setup(seed: Long): Pair<Studio, Studio> {
            val actB = Robolectric.buildActivity(Activity::class.java).create().start().get()
            val a = studio(mapOf("map" to map, "difficulty" to 1, "mp_full" to false))
            val b = Studio(actB, w, h, lang, StudioAds(), mapOf("map" to map, "difficulty" to 1, "mp_full" to false))
            b.call("finishSplash")
            for ((st, id) in listOf(a to 0, b to 1)) {
                st.set("net", NetLink(st.act))
                st.set("myId", id)
                st.set("mpFull", false)
                st.call("setMap", map, false)
                st.call("beginMatch", seed, listOf(0, 1))
            }
            return a to b
        }
        fun exchange(a: Studio, b: Studio) {
            for ((from, to) in listOf(a to b, b to a)) {
                val id = from.get("myId") as Int
                if (!(from.get("myAlive") as Boolean)) continue
                val msg = listOf("S", "$id", "${((from.get("rocketY") as Float) * 10f).toInt()}", "${(from.get("rocketAngle") as Float).toInt()}",
                    "${((from.get("boost") as Float) * 100f).toInt()}", "${from.get("score")}")
                to.call("handleMessage", 0, msg)
            }
        }
        val pre = 3.4f // geri sayım + ilk geçitler
        for (i in 0 until tries) {
            val seed = 9000L + i
            var ok = true
            for (record in listOf(false, true)) {
                val (a, b) = setup(seed)
                val pa = Pilot(a).apply { rng = Random(seed) }
                val pb = Pilot(b).apply { rng = Random(seed * 7 + 1) }
                val dt = 1f / (fps * sub)
                var tSim = 0.0
                val total = ((pre + sec) * fps).toInt()
                for (f in 0 until total) {
                    for (k in 0 until sub) {
                        tSim += 1.0 / (fps * sub)
                        for ((st, p) in listOf(a to pa, b to pb)) {
                            // Maç saati: gerçek zaman yerine kare sayısından (+2 ms pay: adım sınırında belirsizlik olmasın)
                            st.set("mpStartNs", System.nanoTime() - ((tSim - 3.0 + 0.002) * 1e9).toLong())
                            if (st.stateName() == "MP_PLAYING" && (st.get("myAlive") as Boolean) && p.decide(dt)) st.call("onPress", true)
                            st.call("update", dt)
                        }
                        exchange(a, b)
                    }
                    val alive = (a.get("myAlive") as Boolean) && (b.get("myAlive") as Boolean)
                    if (!alive) { ok = false; break }
                    if (record && f >= (pre * fps).toInt()) out(a, f - (pre * fps).toInt(), false)
                }
                if (!ok) break
            }
            if (ok) return
        }
        error("çok oyunculu sahne bulunamadı")
    }
}
