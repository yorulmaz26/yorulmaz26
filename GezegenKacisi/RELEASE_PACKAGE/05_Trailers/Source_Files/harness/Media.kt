import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.example.gezegenkacisi.GameView
import java.io.BufferedOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.min

/** Ham RGBA kareleri ffmpeg'e borulayarak H.264 MP4 yazar (diske ara kare yazılmaz). */
class VideoOut(val file: File, val w: Int, val h: Int, val fps: Int = 30) {
    private val proc: Process
    private val os: BufferedOutputStream
    private val buf = ByteBuffer.allocate(w * h * 4)
    var frames = 0
        private set

    init {
        file.parentFile?.mkdirs()
        proc = ProcessBuilder(
            "ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", System.getProperty("rawfmt") ?: "bgra", "-s", "${w}x$h", "-r", "$fps",
            "-i", "-", "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p",
            "-profile:v", "high", "-level", "4.1", "-movflags", "+faststart", file.path
        ).redirectErrorStream(true).redirectOutput(File(file.path + ".log")).start()
        os = BufferedOutputStream(proc.outputStream, 1 shl 22)
    }

    fun frame(b: Bitmap) {
        buf.clear()
        b.copyPixelsToBuffer(buf)
        os.write(buf.array(), 0, w * h * 4)
        frames++
    }

    fun close(): Int {
        os.close()
        return proc.waitFor()
    }
}

/** Ses olayları: zaman (sn) ve efekt adı; Python tarafında müzikle karıştırılır. */
class SoundLog {
    val events = ArrayList<Pair<Double, String>>()
    fun add(t: Double, name: String) { events.add(t to name) }
    fun json(offset: Double = 0.0) = events.joinToString(",", "[", "]") { "{\"t\":%.3f,\"s\":\"%s\"}".format(it.first + offset, it.second) }
}

/** GameView çizimi: tam ekran (arayüz dahil) ya da yalnızca dünya (arayüzsüz, arka plan görselleri için). */
class Shooter(val s: Studio) {
    private fun m(name: String) = GameView::class.java.getDeclaredMethod(name, Canvas::class.java).apply { isAccessible = true }
    private val worldParts = listOf("drawStars", "drawShootingStar", "drawMotes", "drawColumns", "drawFireballs").map { m(it) }
    private val particles = GameView::class.java.declaredMethods.first { it.name == "drawParticles" && it.parameterCount == 2 }.apply { isAccessible = true }
    private val rocket = GameView::class.java.declaredMethods.first { it.name == "drawRocket" && it.parameterCount == 6 }.apply { isAccessible = true }
    private val shock = m("drawShockwave")

    fun full(): Bitmap = s.render()

    fun world(withRocket: Boolean = true): Bitmap {
        val bmp = Bitmap.createBitmap(s.w, s.h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val bg = s.get("bgBitmap") as Bitmap?
        if (bg != null) cv.drawBitmap(bg, 0f, 0f, null) else cv.drawColor(0xFF060920.toInt())
        val scale = (s.get("scale") as Number).toFloat()
        cv.save(); cv.scale(scale, scale)
        for (p in worldParts) p.invoke(s.v, cv)
        particles.invoke(s.v, cv, s.get("myId"))
        if (withRocket) rocket.invoke(s.v, cv, s.get("rocketX"), s.get("rocketY"), s.get("rocketAngle"), s.get("boost"), s.get("myId"))
        shock.invoke(s.v, cv)
        cv.restore()
        return bmp
    }
}

/** Videonun üstüne çizilen yazılar ve dokunma işareti (oyun görüntüsünü değiştirmez, üstüne biner). */
object Overlay {
    private val black = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Alt-orta bölgede yuvarlak köşeli koyu kutu içinde büyük yazı; [a] 0..1 görünürlük. */
    fun caption(bmp: Bitmap, text: String, a: Float, yFrac: Float = 0.74f, size: Float = 76f) {
        if (text.isBlank() || a <= 0f) return
        val cv = Canvas(bmp)
        val w = bmp.width.toFloat()
        p.typeface = black; p.textAlign = Paint.Align.CENTER; p.letterSpacing = 0.02f
        p.textSize = size * w / 1080f
        val maxW = w * 0.86f
        // İki satıra böl (uzunsa)
        val words = text.split(" ")
        val lines = ArrayList<String>()
        var cur = ""
        for (wd in words) {
            val t = if (cur.isEmpty()) wd else "$cur $wd"
            if (p.measureText(t) > maxW && cur.isNotEmpty()) { lines.add(cur); cur = wd } else cur = t
        }
        if (cur.isNotEmpty()) lines.add(cur)
        val widest = lines.maxOf { p.measureText(it) }
        if (widest > maxW) p.textSize *= maxW / widest
        val lh = p.textSize * 1.12f
        val cy = bmp.height * yFrac
        val bw = min(w * 0.94f, lines.maxOf { p.measureText(it) } + p.textSize * 1.1f)
        val bh = lh * lines.size + p.textSize * 0.62f
        val r = RectF(w / 2f - bw / 2f, cy - bh / 2f, w / 2f + bw / 2f, cy + bh / 2f)
        p.color = Color.argb((205 * a).toInt(), 6, 9, 32)
        cv.drawRoundRect(r, bh * 0.3f, bh * 0.3f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 4f * w / 1080f
        p.color = Color.argb((230 * a).toInt(), 255, 179, 107)
        cv.drawRoundRect(r, bh * 0.3f, bh * 0.3f, p)
        p.style = Paint.Style.FILL
        var y = cy - bh / 2f + p.textSize * 0.31f + lh * 0.82f
        for (l in lines) {
            p.color = Color.argb((255 * a).toInt(), 0, 0, 0)
            cv.drawText(l, w / 2f, y + p.textSize * 0.05f, p)
            p.color = Color.argb((255 * a).toInt(), 255, 255, 255)
            cv.drawText(l, w / 2f, y, p)
            y += lh
        }
    }

    /** Dokunma işareti: ekranın sağ altında genişleyen halka ([age] sn). */
    fun tap(bmp: Bitmap, age: Float) {
        if (age < 0f || age > 0.4f) return
        val cv = Canvas(bmp)
        val w = bmp.width.toFloat()
        val k = age / 0.4f
        val x = w * 0.76f; val y = bmp.height * 0.86f
        p.style = Paint.Style.STROKE
        p.strokeWidth = (10f - 6f * k) * w / 1080f
        p.color = Color.argb((220 * (1f - k)).toInt(), 255, 255, 255)
        cv.drawCircle(x, y, (40f + 70f * k) * w / 1080f, p)
        p.style = Paint.Style.FILL
        p.color = Color.argb((150 * (1f - k)).toInt(), 255, 255, 255)
        cv.drawCircle(x, y, 30f * w / 1080f, p)
    }

    /** Tüm kareyi siyaha/beyaza karartma (geçişler). */
    fun fade(bmp: Bitmap, a: Float, color: Int = Color.BLACK) {
        if (a <= 0f) return
        Canvas(bmp).drawColor(Color.argb((255 * a.coerceIn(0f, 1f)).toInt(), Color.red(color), Color.green(color), Color.blue(color)))
    }
}
