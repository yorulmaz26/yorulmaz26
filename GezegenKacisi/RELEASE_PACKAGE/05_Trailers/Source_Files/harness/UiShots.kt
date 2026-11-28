import android.app.Activity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Yeni arayüzün denetim görüntüleri: her dilde ana ekran, ayarlar, can penceresi, bölüm girişi, oyun sonu. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiShots {
    private val out = File(System.getProperty("shots.dir"))
    private val langs = (System.getProperty("langs") ?: "en,tr").split(",")

    @Test fun screens() {
        for (lang in langs) {
            val act = Robolectric.buildActivity(Activity::class.java).create().start().get()
            val s = Studio(act, 1080, 2400, lang, StudioAds(banner = 0, privacy = true), mapOf("coins" to 640, "level_max" to 23))
            val d = File(out, lang)
            s.call("finishSplash"); s.step(0.6f); s.shot(File(d, "01_hub.png"))
            s.state("SETTINGS"); s.step(0.2f); s.shot(File(d, "02_settings.png"))
            s.call("toHub"); s.step(0.2f)
            val lives = s.get("lives")!!
            lives.javaClass.getMethod("spend").invoke(lives); lives.javaClass.getMethod("spend").invoke(lives)
            s.call("openLevel", 24); s.step(0.4f); s.shot(File(d, "03_ready_lives.png"))
            lives.javaClass.getMethod("spend").invoke(lives); lives.javaClass.getMethod("spend").invoke(lives)
            s.call("openLevel", 24); s.step(0.4f); s.shot(File(d, "04_no_lives.png"))
            s.call("enterLevels"); s.step(0.4f); s.shot(File(d, "05_levels.png"))
            s.state("MARKET"); s.step(0.3f); s.shot(File(d, "06_market.png"))
            s.call("toMenu"); s.step(0.3f); s.shot(File(d, "07_menu.png"))
            s.call("startReady"); s.call("onPress", true); s.step(0.4f); s.set("runCoins", 6); s.call("crash"); s.step(1.6f)
            s.shot(File(d, "08_over.png"))
            println("$lang tamam")
        }
    }
}
