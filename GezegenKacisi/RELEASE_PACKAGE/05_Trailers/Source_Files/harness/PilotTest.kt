import android.app.Activity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PilotTest {
    @Test fun survive() {
        val act = Robolectric.buildActivity(Activity::class.java).create().start().get()
        for (map in 0 until 10) for (diff in listOf(1, 2)) {
            val s = Studio(act, 1080, 1920, "en", StudioAds(), mapOf("map" to map, "difficulty" to diff))
            s.call("finishSplash"); s.step(0.2f)
            s.call("toMenu"); s.step(0.1f)
            s.call("startReady"); s.step(0.3f)
            s.call("onPress", true)
            val p = Pilot(s)
            val ok = p.fly(40f)
            println("map=$map diff=$diff ok=$ok score=${s.get("score")} taps=${p.taps}")
        }
    }
}
