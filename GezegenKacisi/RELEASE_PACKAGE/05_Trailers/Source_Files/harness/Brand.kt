import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import com.example.gezegenkacisi.GameView
import com.example.gezegenkacisi.PlanetType
import com.example.gezegenkacisi.Skin
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/** Marka görselleri: oyunun kendi gezegen ve roket çizimleriyle logo, simge ve yazılar. */
class Brand(private val s: Studio) {
    val black: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    val condensed: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    val white = 0xFFEEF0FF.toInt()
    val warm = 0xFFFFB36B.toInt()
    val gold = 0xFFFFD66B.toInt()
    val navy = 0xFF1A1F55.toInt()
    val orangeDeep = 0xFFF07A2A.toInt()
    val goldDeep = 0xFFB9821C.toInt()

    private fun m(name: String, vararg types: Class<*>) =
        GameView::class.java.getDeclaredMethod(name, *types).apply { isAccessible = true }

    /** Oyunun gezegen çizimi (vektör, her çözünürlükte keskin). */
    fun planet(cv: Canvas, x: Float, y: Float, r: Float, type: PlanetType, ringed: Boolean = false) {
        cv.save(); cv.translate(x, y)
        m("paintPlanet", Canvas::class.java, Float::class.javaPrimitiveType!!, PlanetType::class.java, Boolean::class.javaPrimitiveType!!)
            .invoke(s.v, cv, r, type, ringed)
        cv.restore()
    }

    /** Oyunun araç çizimi; [angle] derece (eksi: yukarı), [k] ölçek (oyunda 1 = ~70 birim boy). */
    fun rocket(cv: Canvas, x: Float, y: Float, k: Float, angle: Float, skin: Skin = Skin.CLASSIC, boost: Float = 1f) {
        cv.save(); cv.translate(x, y); cv.rotate(angle)
        m("drawSkinPreview", Canvas::class.java, Skin::class.java, Float::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!,
            Float::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!).invoke(s.v, cv, skin, 0f, 0f, k, boost)
        cv.restore()
    }

    fun spaceBg(cv: Canvas, w: Float, h: Float, top: Int = 0xFF050A24.toInt(), mid: Int = 0xFF0B1A4A.toInt(), bot: Int = 0xFF16306B.toInt(), seed: Int = 7) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w * 0.3f, h, intArrayOf(top, mid, bot), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, p)
        p.shader = RadialGradient(w * 0.72f, h * 0.28f, w * 0.6f, intArrayOf(0x553B4FB8, 0x003B4FB8), null, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, p)
        p.shader = null
        val rnd = Random(seed)
        repeat((w * h / 9000f).toInt().coerceIn(40, 900)) {
            val a = 60 + rnd.nextInt(190)
            p.color = Color.argb(a, 230, 236, 255)
            cv.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, (0.6f + rnd.nextFloat() * 1.9f) * (w / 1000f).coerceAtLeast(0.6f), p)
        }
    }

    /** Başlık yazısı: iki satır (beyaz + turuncu) ve altta küçük ikinci ad. [dark] koyu zemin içindir. */
    fun title(cv: Canvas, cx: Float, top: Float, size: Float, l1: String, l2: String, secondary: String?, dark: Boolean, maxW: Float): Float {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = black; letterSpacing = 0.04f }
        fun fit(t: String, sz: Float): Float { p.textSize = sz; val w = p.measureText(t); return if (w > maxW) sz * maxW / w else sz }
        val s1 = fit(l1, size); val s2 = fit(l2, size)
        var y = top + s1 * 0.78f
        fun line(t: String, sz: Float, color: Int) {
            p.textSize = sz
            p.color = if (dark) Color.argb(110, 0, 0, 0) else Color.argb(40, 20, 24, 70)
            cv.drawText(t, cx, y + sz * 0.07f, p)
            p.color = color
            cv.drawText(t, cx, y, p)
        }
        line(l1, s1, if (dark) white else navy)
        y += s2 * 1.0f
        line(l2, s2, if (dark) warm else orangeDeep)
        if (secondary != null) {
            val q = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = condensed; letterSpacing = 0.32f }
            q.textSize = size * 0.3f
            val w = q.measureText(secondary); if (w > maxW) q.textSize *= maxW / w
            y += size * 0.46f
            q.color = if (dark) gold else goldDeep
            cv.drawText(secondary, cx, y, q)
        }
        return y
    }

    /** Amblem: iki gezegenin arasındaki boşluktan geçen roket (yazısız simge). [size] kare kenarı. */
    fun emblem(cv: Canvas, cx: Float, cy: Float, size: Float, skin: Skin = Skin.CLASSIC) {
        val u = size / 512f
        planet(cv, cx + 150f * u, cy - 150f * u, 128f * u, PlanetType.OCEAN)
        planet(cv, cx - 160f * u, cy + 168f * u, 118f * u, PlanetType.LAVA)
        planet(cv, cx + 176f * u, cy + 150f * u, 34f * u, PlanetType.MOON)
        rocket(cv, cx - 6f * u, cy + 4f * u, 2.55f * u, -38f, skin, 1f)
    }

    fun save(bmp: Bitmap, f: File) {
        f.parentFile?.mkdirs()
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
