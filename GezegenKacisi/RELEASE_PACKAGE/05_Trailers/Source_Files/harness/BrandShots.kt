import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrandShots {
    private val out = File(System.getProperty("media.dir"))

    @Test fun brand() {
        val act = Robolectric.buildActivity(Activity::class.java).create().start().get()
        val s = Studio(act, 1080, 1920, "en")
        s.call("finishSplash"); s.step(0.3f)
        val b = Brand(s)

        // --- Uygulama simgesi 512 (Play Store) ve uygulama içi büyük sürüm ---
        for (size in listOf(512, 1024)) {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            b.spaceBg(cv, size.toFloat(), size.toFloat(), seed = 3)
            b.emblem(cv, size / 2f, size / 2f, size * 0.98f)
            b.save(bmp, File(out, if (size == 512) "icon/app_icon_512.png" else "icon/app_icon_1024.png"))
        }
        // Yazısız simge (şeffaf)
        run {
            val bmp = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            b.emblem(Canvas(bmp), 512f, 512f, 900f)
            b.save(bmp, File(out, "logos/icon_no_text_transparent.png"))
        }

        // --- Logolar ---
        data class L(val dir: String, val name: String, val l1: String, val l2: String, val sec: String?)
        val logos = listOf(
            L("Turkish", "logo_tr", "GEZEGEN", "KAÇIŞI", null),
            L("English", "logo_en", "PLANET", "GAP", null),
            L("Bilingual", "logo_bilingual_tr_primary", "GEZEGEN", "KAÇIŞI", "PLANET GAP"),
            L("Bilingual", "logo_bilingual_en_primary", "PLANET", "GAP", "GEZEGEN KAÇIŞI"),
        )
        for (l in logos) for (variant in listOf("dark", "light", "transparent_for_dark", "transparent_for_light")) {
            val w = 2400; val h = 1600
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val dark = variant == "dark" || variant == "transparent_for_dark"
            when (variant) {
                "dark" -> b.spaceBg(cv, w.toFloat(), h.toFloat(), seed = 11)
                "light" -> cv.drawColor(0xFFF4F6FF.toInt())
            }
            b.emblem(cv, w / 2f, 372f, 620f)
            b.title(cv, w / 2f, 800f, 320f, l.l1, l.l2, l.sec, dark, w - 260f)
            b.save(bmp, File(out, "logos/${l.dir}/${l.name}_$variant.png"))
            if (variant.startsWith("transparent")) b.save(bmp, File(out, "logos/Transparent/${l.name}_$variant.png"))
        }
        println("marka tamam")
    }
}
