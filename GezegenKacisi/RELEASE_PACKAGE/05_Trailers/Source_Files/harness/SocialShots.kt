import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
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
class SocialShots {
    private val media = File(System.getProperty("media.dir"))
    private val storeDir = File(System.getProperty("store.dir"))

    private fun png(b: Bitmap, f: File) { f.parentFile?.mkdirs(); FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    private fun jpg(b: Bitmap, f: File) { f.parentFile?.mkdirs(); FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) } }

    /** Arka planı esnetmeden kırparak doldurur (center-crop). */
    private fun cover(cv: Canvas, bg: Bitmap, w: Int, h: Int, focusX: Float = 0.5f) {
        val k = maxOf(w / bg.width.toFloat(), h / bg.height.toFloat())
        val sw = (w / k).toInt(); val sh = (h / k).toInt()
        val sx = ((bg.width - sw) * focusX).toInt().coerceIn(0, bg.width - sw); val sy = (bg.height - sh) / 2
        cv.drawBitmap(bg, Rect(sx, sy, sx + sw, sy + sh), Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun text(cv: Canvas, b: Brand, t: String, x: Float, y: Float, size: Float, color: Int, maxW: Float, black: Boolean = true, align: Paint.Align = Paint.Align.CENTER) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = if (black) b.black else b.medium; textAlign = align; textSize = size }
        if (p.measureText(t) > maxW) p.textSize *= maxW / p.measureText(t)
        p.color = Color.argb(140, 0, 0, 0); cv.drawText(t, x, y + size * 0.05f, p)
        p.color = color; cv.drawText(t, x, y, p)
    }

    private fun pill(cv: Canvas, b: Brand, t: String, cx: Float, cy: Float, size: Float, maxW: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = b.black; textAlign = Paint.Align.CENTER; textSize = size }
        if (p.measureText(t) > maxW - size) p.textSize *= (maxW - size) / p.measureText(t)
        val w = p.measureText(t) + p.textSize * 1.6f; val h = p.textSize * 2f
        p.color = 0xFFFFB36B.toInt(); cv.drawRoundRect(RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), h / 2, h / 2, p)
        p.color = 0xFF1A1006.toInt(); cv.drawText(t, cx, cy + p.textSize * 0.36f, p)
    }

    private fun shade(cv: Canvas, w: Float, h: Float, vertical: Boolean, a0: Int, a1: Int) {
        val p = Paint()
        p.shader = if (vertical) LinearGradient(0f, 0f, 0f, h, Color.argb(a0, 3, 5, 20), Color.argb(a1, 3, 5, 20), Shader.TileMode.CLAMP)
                   else LinearGradient(0f, 0f, w, 0f, Color.argb(a0, 3, 5, 20), Color.argb(a1, 3, 5, 20), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, p)
    }

    @Test fun social() {
        val act = Robolectric.buildActivity(Activity::class.java).create().start().get()
        for (lang in listOf("tr", "en")) {
            val j = JSONObject(File(storeDir, "$lang.json").readText())
            val s = Studio(act, 1080, 1920, lang); s.call("finishSplash"); s.step(0.2f)
            val b = Brand(s)
            val tr = lang == "tr"
            val l1 = if (tr) "GEZEGEN" else "PLANET"; val l2 = if (tr) "KAÇIŞI" else "GAP"; val sec = if (tr) "PLANET GAP" else "GEZEGEN KAÇIŞI"
            val tag = j.getString("featureGraphicTagline")
            val slogan = j.getString("slogan")
            val pre = j.getString("ctaPrelaunch")
            val soon = if (tr) "ÇOK YAKINDA GOOGLE PLAY’DE" else "COMING SOON TO GOOGLE PLAY"
            val feats = if (tr) listOf("500 BÖLÜM", "10 DÜNYA", "2-4 OYUNCU") else listOf("500 LEVELS", "10 WORLDS", "2-4 PLAYERS")
            val bgLand = BitmapFactory.decodeFile(File(media, "store/feature/bg_land_$lang.png").path)
            val bgWide = BitmapFactory.decodeFile(File(media, "store/feature/bg_$lang.png").path)
            val bgPort = BitmapFactory.decodeFile(File(media, "store/feature/bg_port_$lang.png").path)
            val bgSq = BitmapFactory.decodeFile(File(media, "store/feature/bg_square_$lang.png").path)
            val phone1 = BitmapFactory.decodeFile(File(media, "store/phone/${lang.uppercase()}/${lang}_phone_01.jpg").path)

            fun landscape(w: Int, h: Int, bg: Bitmap, file: File, withTag: Boolean) {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val cv = Canvas(bmp)
                cover(cv, bg, w, h, 0.62f)
                shade(cv, w.toFloat(), h.toFloat(), false, 225, 0)
                val k = h / 500f
                val cx = w * 0.27f
                cv.save(); cv.scale(k, k)
                b.title(cv, cx / k, 72f, 120f, l1, l2, sec, true, w * 0.46f / k)
                cv.restore()
                if (withTag) text(cv, b, tag, cx, h * 0.9f, 34f * k, 0xFFFFD66B.toInt(), w * 0.48f, black = false)
                if (file.name.endsWith(".jpg")) jpg(bmp, file) else png(bmp, file)
            }
            // Mağaza kapak görseli 1024x500 (24 bit, şeffaflıksız)
            run {
                val big = Bitmap.createBitmap(2048, 1000, Bitmap.Config.ARGB_8888)
                landscape(2048, 1000, bgWide, File(media, "tmp_fg.png"), true)
                val src = BitmapFactory.decodeFile(File(media, "tmp_fg.png").path)
                val fg = Bitmap.createScaledBitmap(src, 1024, 500, true)
                val opaque = Bitmap.createBitmap(1024, 500, Bitmap.Config.RGB_565).also { Canvas(it).drawBitmap(fg, 0f, 0f, null) }
                png(opaque.copy(Bitmap.Config.ARGB_8888, false), File(media, "store/feature_graphic_$lang.png"))
                big.recycle()
            }
            val dir = File(media, "social/${if (tr) "Turkish" else "English"}")
            landscape(1280, 720, bgLand, File(dir, "youtube_cover_1280x720.jpg"), true)
            landscape(1200, 630, bgLand, File(dir, "facebook_1200x630.jpg"), true)

            fun portrait(w: Int, h: Int, file: File, kind: String) {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val cv = Canvas(bmp)
                cover(cv, if (w == h) bgSq else bgPort, w, h)
                shade(cv, w.toFloat(), h.toFloat(), true, 200, 60)
                val cx = w / 2f
                when (kind) {
                    "post" -> {
                        b.emblem(cv, cx, h * 0.27f, w * 0.42f)
                        b.title(cv, cx, h * 0.48f, w * 0.15f, l1, l2, sec, true, w * 0.8f)
                        text(cv, b, slogan, cx, h * 0.93f, w * 0.045f, 0xFFFFD66B.toInt(), w * 0.9f, black = false)
                    }
                    "portrait" -> {
                        val gw = w * 0.56f; val gh = gw * 1920f / 1080f
                        val r = RectF(w * 0.40f, h - gh - 40f, w * 0.40f + gw, h - 40f)
                        cv.drawBitmap(phone1, null, r, Paint(Paint.FILTER_BITMAP_FLAG))
                        b.title(cv, w * 0.21f, h * 0.16f, w * 0.085f, l1, l2, null, true, w * 0.36f)
                        feats.forEachIndexed { i, f -> text(cv, b, f, w * 0.21f, h * 0.55f + i * w * 0.09f, w * 0.045f, 0xFFFFB36B.toInt(), w * 0.36f) }
                    }
                    else -> {
                        b.emblem(cv, cx, h * 0.25f, w * 0.62f)
                        b.title(cv, cx, h * 0.42f, w * 0.17f, l1, l2, sec, true, w * 0.86f)
                        when (kind) {
                            "story" -> { text(cv, b, tag, cx, h * 0.72f, w * 0.05f, 0xFFFFD66B.toInt(), w * 0.88f, black = false); pill(cv, b, pre, cx, h * 0.82f, w * 0.055f, w * 0.88f) }
                            "reels" -> { pill(cv, b, if (tr) "30 SN OYNANIŞ" else "30S GAMEPLAY", cx, h * 0.76f, w * 0.05f, w * 0.8f) }
                            "tiktok" -> { text(cv, b, j.getJSONArray("trailerCaptions").getString(0), cx, h * 0.74f, w * 0.075f, 0xFFFFFFFF.toInt(), w * 0.88f) }
                            "poster" -> {
                                feats.forEachIndexed { i, f -> text(cv, b, f, cx, h * 0.69f + i * h * 0.045f, w * 0.055f, 0xFFFFB36B.toInt(), w * 0.8f) }
                                pill(cv, b, soon, cx, h * 0.87f, w * 0.042f, w * 0.92f)
                            }
                        }
                        text(cv, b, "MAGIC LUMOS SOFTWARE", cx, h * 0.965f, w * 0.03f, 0xFFFFD66B.toInt(), w * 0.8f, black = false)
                    }
                }
                jpg(bmp, file)
            }
            portrait(1080, 1080, File(dir, "instagram_post_1080x1080.jpg"), "post")
            portrait(1080, 1350, File(dir, "instagram_portrait_1080x1350.jpg"), "portrait")
            portrait(1080, 1920, File(dir, "instagram_story_1080x1920.jpg"), "story")
            portrait(1080, 1920, File(dir, "reels_cover_1080x1920.jpg"), "reels")
            portrait(1080, 1920, File(dir, "tiktok_cover_1080x1920.jpg"), "tiktok")
            portrait(1080, 1920, File(dir, "launch_poster_1080x1920.jpg"), "poster")
            println("$lang sosyal tamam")
        }
    }
}
