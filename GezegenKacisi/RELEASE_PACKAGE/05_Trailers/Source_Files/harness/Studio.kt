import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import com.example.gezegenkacisi.AdsBridge
import com.example.gezegenkacisi.GameView
import java.io.File
import java.io.FileOutputStream

/** Sahte reklam: video her zaman hazır; banner yok (mağaza görselleri reklamsız). */
class StudioAds(val banner: Int = 0, val privacy: Boolean = false) : AdsBridge {
    var visible = false
    override fun setBannerVisible(visible: Boolean) { this.visible = visible }
    override val bannerHeightPx: Int get() = if (visible) banner else 0
    override val bannerSlotPx: Int get() = banner
    override fun interstitialReady() = false
    override fun showInterstitial(onDone: () -> Unit) = false
    override fun rewardedReady() = true
    override fun showRewarded(onReward: () -> Unit, onDone: () -> Unit): Boolean { onReward(); onDone(); return true }
    override fun privacyOptionsRequired() = privacy
    override fun showPrivacyOptions() {}
}

/**
 * Oyunun kendi çizim koduyla, istenen çözünürlükte ve dilde ekran üretir (Robolectric, gerçek Android grafik).
 * [prefs] ile kayıt (para, bölüm, araç...) önceden yazılabilir.
 */
class Studio(
    val act: Activity, val w: Int, val h: Int, lang: String,
    val ads: AdsBridge = StudioAds(), prefs: Map<String, Any> = emptyMap()
) {
    val v: GameView

    init {
        val ed = act.getSharedPreferences("gezegen_kacisi", Context.MODE_PRIVATE).edit().clear()
        ed.putString("language", lang)
        for ((k, x) in prefs) when (x) {
            is Int -> ed.putInt(k, x); is Long -> ed.putLong(k, x); is Boolean -> ed.putBoolean(k, x)
            is String -> ed.putString(k, x)
        }
        ed.commit()
        v = GameView(act)
        act.setContentView(v)
        v.ads = ads
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        set("netBaseMs", -1L)
        set("netRetry", 99999f)
    }

    fun call(name: String, vararg args: Any?): Any? {
        val m = GameView::class.java.declaredMethods.first { it.name == name && it.parameterCount == args.size }
        m.isAccessible = true
        return m.invoke(v, *args)
    }
    fun set(name: String, value: Any?) { val f = GameView::class.java.getDeclaredField(name); f.isAccessible = true; f.set(v, value) }
    fun get(name: String): Any? { val f = GameView::class.java.getDeclaredField(name); f.isAccessible = true; return f.get(v) }
    fun state(n: String) {
        val cls = Class.forName("com.example.gezegenkacisi.GameView\$State")
        set("state", cls.enumConstants.first { (it as Enum<*>).name == n })
    }
    fun stateName() = (get("state") as Enum<*>).name
    fun step(sec: Float, fps: Int = 60) { var t = 0f; val dt = 1f / fps; while (t < sec - 1e-4f) { call("update", dt); t += dt } }
    fun render(): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        return bmp
    }
    fun save(bmp: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    fun shot(file: File) = save(render(), file)
}
