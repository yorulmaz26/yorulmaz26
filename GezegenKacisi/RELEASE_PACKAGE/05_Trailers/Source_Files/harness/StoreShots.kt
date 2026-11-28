import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreShots {
    private val media = File(System.getProperty("media.dir"))
    private val storeDir = File(System.getProperty("store.dir"))
    private val langs = (System.getProperty("langs") ?: "tr,en,de,it,fr,es").split(",")
    private val parts = (System.getProperty("parts") ?: "phone,tablet,feature,social").split(",")

    private fun jpg(b: Bitmap, f: File) { f.parentFile?.mkdirs(); FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.JPEG, 93, it) } }
    private fun png(b: Bitmap, f: File) { f.parentFile?.mkdirs(); FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    private fun copy(b: Bitmap) = b.copy(Bitmap.Config.ARGB_8888, false)

    /** 8 gerçek oyun ekranı ([w]x[h]), mağaza sırasına göre. */
    private fun captures(act: Activity, lang: String, w: Int, h: Int): List<Bitmap> {
        val sc = Scenes(act, w, h, lang)
        val out = ArrayList<Bitmap>()
        var grab: Bitmap? = null
        // 1 Dünya Yörüngesi oynanış
        sc.endless(0, 1, 9f, 0.5f, { s, f, _ -> if (f == 14) grab = copy(s.render()) }); out.add(grab!!)
        // 2 Buz Kuşağı: dokunuştan hemen sonra (alev uzun)
        grab = null
        sc.endless(1, 1, 6f, 2f, { s, f, tp -> if (tp && f > 6 && grab == null) { s.call("update", 1f / 30f); grab = copy(s.render()) } }); out.add(grab ?: error("ice"))
        // 3 Elektrik Fırtınası: yıldırım çakarken
        grab = null; var last: Bitmap? = null
        sc.endless(6, 1, 4f, 5f, { s, f, _ ->
            @Suppress("UNCHECKED_CAST") val bolts = s.get("bolts") as List<Any>
            val active = bolts.any { b -> val fw = b.javaClass.getDeclaredField("warn").apply { isAccessible = true }; val fl = b.javaClass.getDeclaredField("life").apply { isAccessible = true }
                (fw.get(b) as Float) <= 0f && (fl.get(b) as Float) > 0.15f }
            if (active && grab == null) grab = copy(s.render())
            if (f == 140) last = copy(s.render())
        }); out.add(grab ?: last!!)
        // 4 Harita seçimi (Gaz Devleri)
        run { val s = sc.studio(); s.call("toMenu"); s.call("setMap", 3, false); s.step(0.8f); out.add(copy(s.render())) }
        // 5 Market
        sc.shop(1.6f, 0.6f, 6) { s, f, _ -> if (f == 44) grab = copy(s.render()) }; out.add(grab!!)
        // 6 Kara Delik
        sc.endless(5, 1, 7f, 0.5f, { s, f, _ -> if (f == 14) grab = copy(s.render()) }); out.add(grab!!)
        // 7 Bölüm geçildi
        sc.levelWin(16, 4f, { s, f, _ -> if (f == 105) grab = copy(s.render()) }); out.add(grab!!)
        // 8 2 oyuncu bölünmüş ekran
        sc.multiplayer(1.2f, 3, { s, f, _ -> if (f == 30) grab = copy(s.render()) }); out.add(grab!!)
        return out
    }

    private val bgTop = intArrayOf(0xFF0B1A4A.toInt(), 0xFF0A2840.toInt(), 0xFF3A1206.toInt(), 0xFF1E1240.toInt(), 0xFF2A1F14.toInt(),
        0xFF120826.toInt(), 0xFF101A44.toInt(), 0xFF1A1030.toInt())

    /** Mağaza görseli: üstte başlık/alt başlık, altta oyunun gerçek ekranı (esnetilmeden, yuvarlak köşeli). */
    private fun phoneCard(b: Brand, game: Bitmap, head: String, subline: String, i: Int): Bitmap {
        val W = 1080f; val H = 1920f
        val out = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        b.spaceBg(cv, W, H, top = bgTop[i % bgTop.size], mid = 0xFF0B1240.toInt(), bot = 0xFF05081C.toInt(), seed = 20 + i)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.typeface = b.black; p.textAlign = Paint.Align.CENTER; p.letterSpacing = 0.01f
        p.textSize = 86f
        val maxW = 980f
        // Başlık en fazla 2 satır
        val words = head.split(" "); val lines = ArrayList<String>(); var cur = ""
        for (wd in words) { val t = if (cur.isEmpty()) wd else "$cur $wd"; if (p.measureText(t) > maxW && cur.isNotEmpty()) { lines.add(cur); cur = wd } else cur = t }
        if (cur.isNotEmpty()) lines.add(cur)
        val widest = lines.maxOf { p.measureText(it) }; if (widest > maxW) p.textSize *= maxW / widest
        var y = if (lines.size == 1) 150f else 118f
        for (l in lines) { p.color = Color.argb(120, 0, 0, 0); cv.drawText(l, 540f, y + 6f, p); p.color = b.white; cv.drawText(l, 540f, y, p); y += p.textSize * 1.08f }
        if (subline.isNotBlank()) {
            p.typeface = b.medium; p.textSize = 44f; p.letterSpacing = 0f
            if (p.measureText(subline) > maxW) p.textSize *= maxW / p.measureText(subline)
            p.color = b.gold
            cv.drawText(subline, 540f, y + 14f, p)
        }
        // Oyun ekranı
        val gw = game.width.toFloat(); val gh = game.height.toFloat()
        val left = (W - gw) / 2f; val top = H - gh - 50f
        val r = RectF(left, top, left + gw, top + gh)
        p.shader = null; p.color = Color.argb(90, 255, 179, 107)
        cv.drawRoundRect(RectF(r.left - 8f, r.top - 8f, r.right + 8f, r.bottom + 8f), 52f, 52f, p)
        p.color = Color.WHITE
        p.shader = BitmapShader(game, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(android.graphics.Matrix().apply { setTranslate(left, top) }) }
        cv.drawRoundRect(r, 44f, 44f, p)
        p.shader = null
        return out
    }

    @Test fun store() {
        val act = Robolectric.buildActivity(Activity::class.java).create().start().get()
        for (lang in langs) {
            val j = JSONObject(File(storeDir, "$lang.json").readText())
            val caps = j.getJSONArray("screenshotCaptions")
            val brandStudio = Studio(act, 1080, 1920, lang); brandStudio.call("finishSplash"); brandStudio.step(0.2f)
            val b = Brand(brandStudio)
            val up = lang.uppercase()
            if ("phone" in parts || "compose" in parts) {
                val games = if ("compose" in parts) (1..8).map { android.graphics.BitmapFactory.decodeFile(File(media, "store/raw/$up/${lang}_game_%02d.png".format(it)).path) }
                    else captures(act, lang, 864, 1536)
                games.forEachIndexed { i, g ->
                    val c = caps.getJSONObject(i)
                    jpg(phoneCard(b, g, c.getString("headline"), c.optString("subline"), i), File(media, "store/phone/$up/${lang}_phone_%02d.jpg".format(i + 1)))
                    if ("phone" in parts) png(g, File(media, "store/raw/$up/${lang}_game_%02d.png".format(i + 1)))
                }
                println("$lang telefon tamam")
            }
            if ("tablet" in parts && (lang == "tr" || lang == "en")) {
                for ((dirName, w, h) in listOf(Triple("Tablet_7_Inch", 1200, 1920), Triple("Tablet_10_Inch", 1600, 2560))) {
                    val sc = Scenes(act, w, h, lang)
                    var g: Bitmap? = null
                    sc.endless(0, 1, 8f, 0.5f, { s, f, _ -> if (f == 14) g = copy(s.render()) }); jpg(g!!, File(media, "store/$dirName/${lang}_${dirName.lowercase()}_01.jpg"))
                    run { val s = sc.studio(); s.step(0.6f); jpg(s.render(), File(media, "store/$dirName/${lang}_${dirName.lowercase()}_02.jpg")) }
                    run { val s = sc.studio(); s.call("toMenu"); s.call("setMap", 2, false); s.step(0.8f); jpg(s.render(), File(media, "store/$dirName/${lang}_${dirName.lowercase()}_03.jpg")) }
                    run { val s = sc.studio(); s.call("enterLevels"); s.step(0.6f); jpg(s.render(), File(media, "store/$dirName/${lang}_${dirName.lowercase()}_04.jpg")) }
                    sc.endless(6, 1, 6f, 0.5f, { s, f, _ -> if (f == 14) g = copy(s.render()) }); jpg(g!!, File(media, "store/$dirName/${lang}_${dirName.lowercase()}_05.jpg"))
                }
                println("$lang tablet tamam")
            }
            if ("feature" in parts && (lang == "tr" || lang == "en")) {
                // 2048x1000 dünya görüntüsü (sonra 1024x500'e küçültülür)
                val sc = Scenes(act, 2048, 1000, lang)
                var g: Bitmap? = null
                sc.endless(3, 1, 9f, 0.5f, { s, f, _ -> if (f == 10) g = copy(Shooter(s).world(true)) })
                png(g!!, File(media, "store/feature/bg_${lang}.png"))
                // Sosyal medya arka planları
                val land = Scenes(act, 2560, 1440, lang); var gl: Bitmap? = null
                land.endless(2, 1, 8f, 0.5f, { s, f, _ -> if (f == 10) gl = copy(Shooter(s).world(true)) }); png(gl!!, File(media, "store/feature/bg_land_${lang}.png"))
                val port = Scenes(act, 1080, 1920, lang); var gp: Bitmap? = null
                port.endless(9, 1, 8f, 0.5f, { s, f, _ -> if (f == 10) gp = copy(Shooter(s).world(true)) }); png(gp!!, File(media, "store/feature/bg_port_${lang}.png"))
                val sq = Scenes(act, 1080, 1080, lang); var gs: Bitmap? = null
                sq.endless(6, 1, 8f, 0.5f, { s, f, _ -> if (f == 10) gs = copy(Shooter(s).world(true)) }); png(gs!!, File(media, "store/feature/bg_square_${lang}.png"))
                println("$lang arka planlar tamam")
            }
        }
    }
}
