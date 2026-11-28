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
class PilotDebug {
    @Test fun dbg() {
        val act = Robolectric.buildActivity(Activity::class.java).create().start().get()
        val s = Studio(act, 1080, 1920, "en", StudioAds(), mapOf("map" to 0, "difficulty" to 1))
        s.call("finishSplash"); s.step(0.2f)
        s.call("toMenu"); s.step(0.1f)
        s.call("startReady"); s.step(0.3f)
        println("state=" + s.stateName())
        s.call("onPress", true)
        println("state after press=" + s.stateName() + " y=" + s.get("rocketY") + " vy=" + s.get("rocketVy"))
        val p = Pilot(s); p.debug = true
        for (i in 0 until 120) {
            val tap = s.stateName() == "PLAYING" && p.decide(1f / 60f)
            if (tap) s.call("onPress", true)
            s.call("update", 1f / 60f)
            if (i % 30 == 0) println("gaps=" + p.gaps()); if (i % 6 == 0) println("i=$i st=${s.stateName()} y=${s.get("rocketY")} vy=${s.get("rocketVy")} tap=$tap speed=${s.get("speed")} grav=${s.get("gravMul")} wind=${s.get("wind")} paused=${s.get("paused")}")
        }
    }
}
