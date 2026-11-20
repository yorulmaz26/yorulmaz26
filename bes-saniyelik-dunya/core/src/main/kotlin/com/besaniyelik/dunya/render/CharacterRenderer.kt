package com.besaniyelik.dunya.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Affine2
import com.badlogic.gdx.math.MathUtils
import com.besaniyelik.dunya.Assets
import com.besaniyelik.dunya.progress.Skin
import kotlin.math.abs
import kotlin.math.sin

enum class CharState { RUN, JUMP, FALL, HURT, DEAD, HAPPY, IDLE }

fun rgb(c: Int, a: Float = 1f) = Color(((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f, a)

/** Karakteri parçalardan (gövde, desen, gözler, ağız, ayaklar, aksesuar) çizer; 20 görünümün hepsi aynı iskeleti kullanır. */
class CharacterRenderer(private val a: Assets) {
    private val body = a.g("ch_body"); private val shine = a.g("ch_shine"); private val belly = a.g("ch_belly")
    private val eye = a.g("ch_eye"); private val pupil = a.g("ch_pupil"); private val eyeHappy = a.g("ch_eye_happy"); private val eyeX = a.g("ch_eye_x")
    private val cheek = a.g("ch_cheek"); private val foot = a.g("ch_foot")
    private val mouths = listOf("smile", "open", "o", "sad", "grin").associateWith { a.g("ch_mouth_$it") }
    private val tmp = Affine2()
    private val base = Affine2()
    private val white = Color(1f, 1f, 1f, 1f)

    /**
     * @param cx,cy merkez (dünya birimi), size gövde boyutu (dünya birimi), flip ters yer çekimi.
     */
    fun draw(batch: Batch, skin: Skin, cx: Float, cy: Float, size: Float, state: CharState, t: Float,
             flip: Boolean = false, squash: Float = 0f, rot: Float = 0f, alpha: Float = 1f, look: Float = 1f) {
        val frames = a.heroFrames(skin.id)
        if (frames != null) { drawSprite(batch, frames, cx, cy, size, state, t, flip, squash, rot, alpha); return }
        val k = size / 160f
        var sx = 1f; var sy = 1f
        var bob = 0f
        when (state) {
            CharState.RUN -> { bob = abs(sin(t * 14f)) * 6f; sy = 1f + sin(t * 28f) * 0.03f; sx = 2f - sy }
            CharState.JUMP -> { sy = 1.12f; sx = 0.9f }
            CharState.FALL -> { sy = 0.96f; sx = 1.04f }
            CharState.HAPPY -> { bob = abs(sin(t * 8f)) * 18f; sy = 1f + sin(t * 16f) * 0.05f; sx = 2f - sy }
            CharState.IDLE -> { sy = 1f + sin(t * 3f) * 0.025f; sx = 2f - sy }
            else -> {}
        }
        if (squash > 0f) { sy *= 1f - squash * 0.28f; sx *= 1f + squash * 0.22f }
        base.idt()
        base.translate(cx, cy)
        base.rotate(rot)
        base.scale(k * sx, k * sy * (if (flip) -1f else 1f))
        base.translate(0f, bob)

        val bodyCol = rgb(skin.body, alpha); val bellyCol = rgb(skin.belly, alpha); val acc = rgb(skin.accent, alpha)
        val footCol = Color(bodyCol).mul(0.85f, 0.85f, 0.85f, 1f)
        // ayaklar
        val phase = if (state == CharState.RUN) sin(t * 14f) * 10f else 0f
        part(batch, foot, -34f, -78f + (if (state == CharState.RUN) phase.coerceAtLeast(0f) else 0f), 44f, 28f, footCol)
        part(batch, foot, 34f, -78f + (if (state == CharState.RUN) (-phase).coerceAtLeast(0f) else 0f), 44f, 28f, footCol)
        // aksesuar arka (kulaklar vb. gövde arkasında daha sevimli durur)
        val accName = skin.accessory
        val back = accName in setOf("cat_ears", "bunny_ears", "bee_antenna", "antenna", "halo", "headphones")
        if (back) accessory(batch, accName, alpha)
        part(batch, body, 0f, 0f, 160f, 160f, bodyCol)
        part(batch, belly, 0f, 0f, 160f, 160f, bellyCol.also { it.a = 0.92f * alpha })
        when (skin.pattern) {
            "plain", "belly" -> {}
            "rainbow" -> part(batch, a.g("pat_rainbow"), 0f, 0f, 160f, 160f, white.also { it.a = alpha })
            else -> part(batch, a.g("pat_${skin.pattern}"), 0f, 0f, 160f, 160f, Color(acc).also { it.a = 0.85f * alpha })
        }
        part(batch, shine, 0f, 0f, 160f, 160f, white.also { it.a = alpha })
        // yüz
        val lx = look * 6f
        val blink = (t % 3.7f) < 0.12f
        when (state) {
            CharState.HURT, CharState.DEAD -> {
                part(batch, eyeX, -30f, 16f, 44f, 44f, white.also { it.a = alpha }); part(batch, eyeX, 30f, 16f, 44f, 44f, white)
            }
            CharState.HAPPY -> {
                part(batch, eyeHappy, -30f, 18f, 48f, 40f, white.also { it.a = alpha }); part(batch, eyeHappy, 30f, 18f, 48f, 40f, white)
            }
            else -> {
                val eyH = if (blink) 8f else 56f
                part(batch, eye, -28f, 16f, 48f, eyH, white.also { it.a = alpha }); part(batch, eye, 28f, 16f, 48f, eyH, white)
                if (!blink) {
                    val py = if (state == CharState.FALL) -8f else if (state == CharState.JUMP) 8f else 0f
                    part(batch, pupil, -28f + lx, 12f + py, 28f, 28f, white); part(batch, pupil, 28f + lx, 12f + py, 28f, 28f, white)
                }
            }
        }
        part(batch, cheek, -52f, -12f, 30f, 20f, white.also { it.a = alpha }); part(batch, cheek, 52f, -12f, 30f, 20f, white)
        val m = when (state) { CharState.JUMP -> "open"; CharState.FALL -> "o"; CharState.HURT, CharState.DEAD -> "sad"; CharState.HAPPY -> "grin"; else -> "smile" }
        part(batch, mouths[m]!!, 0f, -26f, 64f, 48f, white)
        if (!back) accessory(batch, accName, alpha)
        when (accName) {
            "scarf" -> part(batch, a.g("acc_scarf_front"), 0f, -50f, 160f, 70f, white)
            "star_glasses" -> part(batch, a.g("acc_glasses_front"), 0f, 16f, 160f, 70f, white)
            "headphones" -> part(batch, a.g("acc_headphone_cups"), 0f, 12f, 200f, 70f, white)
            "helmet" -> part(batch, a.g("acc_helmet_glass"), 0f, 0f, 180f, 180f, white)
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }

    /**
     * Kare animasyonlu karakter. Sayfa düzeni: 0-5 koşu, 6 kalkış, 7 yükseliş, 8 tepe, 9 düşüş, 10 iniş,
     * 11 hasar, 12 yenilgi, 13 sevinç, 14 güç, 15 bekleme. Kareler ayak çizgisine (alt orta) hizalıdır.
     * (cx, cy) ve size eski parçalı çizimle aynı anlamdadır: size kenarlı kutunun merkezi ve boyutu; ayaklar kutunun altında.
     */
    private fun drawSprite(batch: Batch, frames: List<TextureRegion>, cx: Float, cy: Float, size: Float, state: CharState, t: Float,
                           flip: Boolean, squash: Float, rot: Float, alpha: Float) {
        val idx = when (state) {
            CharState.RUN -> ((t * 13f).toInt() % 6)
            CharState.JUMP -> 7
            CharState.FALL -> if (squash > 0.35f) 10 else 9
            CharState.HURT -> 11
            CharState.DEAD -> 12
            CharState.HAPPY -> 13
            CharState.IDLE -> 15
        }.let { if (state == CharState.RUN && squash > 0.45f) 10 else it }
        val r = frames[idx] ?: frames[0]
        val k = size * 1.45f / HERO_REF_PX
        val sq = 1f - squash * 0.12f
        val w = r.regionWidth * k * (2f - sq)
        val h = r.regionHeight * k * sq
        val sign = if (flip) -1f else 1f
        val feetY = cy - sign * size / 2f
        batch.setColor(1f, 1f, 1f, alpha)
        if (rot != 0f) {
            batch.draw(r, cx - w / 2f, feetY, w / 2f, (cy - feetY), w, h * sign, 1f, 1f, rot)
        } else {
            batch.draw(r, cx - w / 2f, feetY, w, h * sign)
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }

    private fun accessory(batch: Batch, name: String, alpha: Float) {
        if (name == "none" || name == "scarf" || name == "star_glasses") return
        val r = a.g("acc_$name")
        part(batch, r, 0f, 40f + 65f, 180f, 130f, white.also { it.a = alpha })
    }

    /** Gövde yerel koordinatlarında (160 birimlik gövde, merkez orijin) parça çizer. */
    private fun part(batch: Batch, r: TextureRegion, x: Float, y: Float, w: Float, h: Float, c: Color) {
        tmp.set(base)
        tmp.translate(x - w / 2f, y - h / 2f)
        batch.color = c
        batch.draw(r, w, h, tmp)
    }

    fun drawIcon(batch: Batch, skin: Skin, cx: Float, cy: Float, size: Float, t: Float, state: CharState = CharState.IDLE) =
        draw(batch, skin, cx, cy, size, state, t)

    companion object {
        /** tools/art/user_art.py HERO_FRAME_H ile aynı: bekleme/koşu karesinin piksel yüksekliği. */
        const val HERO_REF_PX = 200f

        fun stateFor(grounded: Boolean, vyUp: Float): CharState = when {
            grounded -> CharState.RUN
            vyUp > 1f -> CharState.JUMP
            else -> CharState.FALL
        }
        fun lerpAngle(a: Float, b: Float, t: Float) = MathUtils.lerpAngleDeg(a, b, t)
    }
}
