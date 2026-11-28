import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.gezegenkacisi.GameView
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TrailerShots {
    private val media = File(System.getProperty("media.dir"))
    private val storeDir = File(System.getProperty("store.dir"))
    private val langs = (System.getProperty("langs") ?: "en,tr").split(",")

    private fun captions(lang: String): JSONObject = JSONObject(File(storeDir, "$lang.json").readText())

    @Test fun trailers() {
        val act = Robolectric.buildActivity(Activity::class.java).create().start().get()
        exportAudio(act)
        for (lang in langs) {
            val j = captions(lang)
            val caps = j.getJSONArray("trailerCaptions").let { a -> (0 until a.length()).map { a.getString(it) } }
            val dir = File(media, "trailer/$lang")
            val sc = Scenes(act, 1080, 1920, lang)
            val log = SoundLog()
            val skipMain = System.getProperty("skipMain")?.split(",")?.contains(lang) == true
            val vid = VideoOut(File(dir, if (skipMain) "unused.mp4" else "main_27s.mp4"), 1080, 1920, 30)
            val beats = listOf(0f, 3f, 8f, 15f, 22f, 27f)
            var tBase = 0f
            var lastTap = -9f
            fun emit(s: Studio, f: Int, tapped: Boolean, tapRing: Boolean, yFrac: Float = 0.74f) {
                val t = tBase + f / 30f
                val bmp = s.render()
                if (tapped) lastTap = t
                if (tapRing) Overlay.tap(bmp, t - lastTap)
                val beat = beats.indexOfLast { t >= it }.coerceIn(0, 4)
                val bStart = beats[beat]; val bEnd = beats[beat + 1]
                val a = minOf(1f, (t - bStart) / 0.25f, (bEnd - t) / 0.25f).coerceIn(0f, 1f)
                Overlay.caption(bmp, caps[beat], a, yFrac)
                vid.frame(bmp)
            }
            // Puan/para/olay sesleri
            var lastScore = 0; var lastCoins = -1; var lastState = ""
            fun sounds(s: Studio, f: Int, tapped: Boolean) {
                val t = (tBase + f / 30f).toDouble()
                if (tapped) log.add(t, "flap")
                val sc2 = s.get("score") as Int
                if (sc2 > lastScore) log.add(t, "score")
                lastScore = sc2
                val c = (s.get("runCoins") as Int) + (s.get("coins") as Int)
                if (lastCoins >= 0 && c > lastCoins) log.add(t, "coin")
                lastCoins = c
                val st = s.stateName()
                if (st == "LEVEL_DONE" && lastState != "LEVEL_DONE") log.add(t, "record")
                lastState = st
            }
            fun reset() { lastScore = 0; lastCoins = -1; lastState = "" }

            // 0–3 sn: Lav Denizi (dikkat çekici açılış)
            reset(); sc.endless(2, 1, 6f, 3f, { s, f, tp -> if (f == 0) { lastScore = s.get("score") as Int }; sounds(s, f, tp); emit(s, f, tp, false) }); tBase = 3f
            // 3–8 sn: Dünya Yörüngesi, dokun ve uç
            reset(); sc.endless(0, 1, 2f, 5f, { s, f, tp -> if (f == 0) { lastScore = s.get("score") as Int }; sounds(s, f, tp); emit(s, f, tp, true) }); tBase = 8f
            // 8–11,5 sn: Elektrik Fırtınası (yıldırım)
            reset(); sc.endless(6, 1, 5f, 3.5f, { s, f, tp -> if (f == 0) { lastScore = s.get("score") as Int }; sounds(s, f, tp); emit(s, f, tp, false) }); tBase = 11.5f
            // 11,5–15 sn: Meteor Yağmuru
            reset(); sc.endless(7, 0, 5f, 3.5f, { s, f, tp -> if (f == 0) { lastScore = s.get("score") as Int }; sounds(s, f, tp); emit(s, f, tp, false) }); tBase = 15f
            // 15–17,5 sn: 10 dünya (harita seçimi)
            sc.mapCarousel(2.5f, 0.55f, 0) { s, f, ev -> if (ev) log.add((tBase + f / 30f).toDouble(), "swipe"); emit(s, f, false, false, 0.92f) }; tBase = 17.5f
            // 17,5–19,5 sn: market, bir araç satın alınır
            sc.shop(2f, 0.9f, 5) { s, f, ev -> if (ev) { log.add((tBase + f / 30f).toDouble(), "coin") }; emit(s, f, false, false, 0.92f) }; tBase = 19.5f
            // 19,5–22 sn: 2 oyuncu bölünmüş ekran
            reset(); sc.multiplayer(2.5f, 3, { s, f, _ -> emit(s, f, false, false) }); tBase = 22f
            // 22–27 sn: bölüm geçildi
            reset(); sc.levelWin(16, 5f, { s, f, tp -> if (f == 0) { lastScore = s.get("score") as Int }; sounds(s, f, tp); emit(s, f, tp, false) })
            val code = vid.close()
            println("$lang ana video: ${vid.frames} kare, ffmpeg=$code")

            // 27–30 sn: kapanış (3 sürüm)
            val cta = mapOf("play" to "", "social_pre" to j.getString("ctaPrelaunch"), "social_post" to j.getString("ctaPostlaunch"))
            for ((name, text) in cta) closing(act, lang, text, File(dir, "closing_$name.mp4"))
            log.add(27.15, "start")
            File(dir, "events.json").writeText(log.json())
            println("$lang tamam")
        }
    }

    /** Kapanış kartı: oyun dünyası (Galaksi Merkezi) arka planda yavaşça akar, üstünde iki dilli logo ve marka. */
    private fun closing(act: Activity, lang: String, cta: String, file: File) {
        val sc = Scenes(act, 1080, 1920, lang)
        val s = sc.studio(mapOf("map" to 9))
        s.call("toMenu"); s.call("setMap", 9, false); s.step(1.5f)
        val sh = Shooter(s)
        val b = Brand(s)
        val tr = lang == "tr"
        val vid = VideoOut(file, 1080, 1920, 30)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (f in 0 until 90) {
            s.step(1f / 30f, 30)
            val bmp = sh.world(withRocket = false)
            val cv = Canvas(bmp)
            val k = (f / 12f).coerceIn(0f, 1f)
            cv.drawColor(Color.argb((150 * k).toInt(), 3, 5, 20))
            cv.save()
            val sc2 = 0.92f + 0.08f * k
            cv.scale(sc2, sc2, 540f, 800f)
            val alpha = (255 * k).toInt()
            cv.saveLayerAlpha(0f, 0f, 1080f, 1920f, alpha)
            b.emblem(cv, 540f, 560f, 560f)
            if (tr) b.title(cv, 540f, 900f, 168f, "GEZEGEN", "KAÇIŞI", "PLANET GAP", true, 960f)
            else b.title(cv, 540f, 900f, 168f, "PLANET", "GAP", "GEZEGEN KAÇIŞI", true, 960f)
            cv.restore(); cv.restore()
            if (cta.isNotEmpty() && f >= 18) {
                val a2 = ((f - 18) / 10f).coerceIn(0f, 1f)
                p.typeface = b.black; p.textAlign = Paint.Align.CENTER; p.textSize = 66f; p.letterSpacing = 0.04f
                val tw = minOf(p.measureText(cta) + 120f, 1000f)
                if (p.measureText(cta) > 880f) p.textSize *= 880f / p.measureText(cta)
                val r = RectF(540f - tw / 2f, 1360f, 540f + tw / 2f, 1500f)
                p.color = Color.argb((255 * a2).toInt(), 255, 179, 107)
                cv.drawRoundRect(r, 70f, 70f, p)
                p.color = Color.argb((255 * a2).toInt(), 26, 16, 6)
                cv.drawText(cta, 540f, r.centerY() + p.textSize * 0.35f, p)
            }
            p.typeface = b.condensed; p.textAlign = Paint.Align.CENTER; p.textSize = 40f; p.letterSpacing = 0.25f
            p.color = Color.argb((230 * k).toInt(), 255, 214, 107)
            cv.drawText("MAGIC LUMOS SOFTWARE", 540f, 1730f, p)
            vid.frame(bmp)
        }
        vid.close()
    }

    /** Oyunun kodla sentezlenen müziği ve efektleri (telif sorunu olmayan, oyuna ait sesler) WAV olarak. */
    private fun exportAudio(act: Activity) {
        val dir = File(media, "trailer/audio")
        dir.mkdirs()
        val s = Studio(act, 540, 960, "en")
        val snd = s.get("sound")!!
        val cls = snd.javaClass
        fun call(name: String, vararg a: Any): FloatArray {
            val m = cls.declaredMethods.first { it.name == name && it.parameterCount == a.size }
            m.isAccessible = true
            return m.invoke(snd, *a) as FloatArray
        }
        val themesF = cls.getDeclaredField("themes").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val themes = themesF.get(snd) as Array<Any>
        for (i in listOf(2, 6, 9)) wav(File(dir, "music_theme$i.wav"), call("synthMusic", themes[i]))
        wav(File(dir, "flap.wav"), call("synthFlap"))
        wav(File(dir, "score.wav"), call("synthScore"))
        wav(File(dir, "coin.wav"), call("synthCoin"))
        wav(File(dir, "swipe.wav"), call("synthSwipe"))
        wav(File(dir, "click.wav"), call("synthClick"))
        wav(File(dir, "start.wav"), call("synthArp", intArrayOf(72, 76, 79, 84), 0.07f, 0.35f, 0.5f))
        wav(File(dir, "record.wav"), call("synthArp", intArrayOf(72, 76, 79, 84, 88, 91), 0.09f, 0.6f, 0.55f))
    }

    private fun wav(f: File, data: FloatArray, rate: Int = 22050) {
        val pcm = ByteBuffer.allocate(data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (v in data) pcm.putShort((v.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        val body = pcm.array()
        val hdr = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        hdr.put("RIFF".toByteArray()); hdr.putInt(36 + body.size); hdr.put("WAVE".toByteArray())
        hdr.put("fmt ".toByteArray()); hdr.putInt(16); hdr.putShort(1); hdr.putShort(1); hdr.putInt(rate); hdr.putInt(rate * 2)
        hdr.putShort(2); hdr.putShort(16); hdr.put("data".toByteArray()); hdr.putInt(body.size)
        FileOutputStream(f).use { it.write(hdr.array()); it.write(body) }
    }
}
