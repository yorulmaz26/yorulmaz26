package com.emirzinciri.robotkomutan.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Disposable

/** Bütün çalışma zamanı varlıkları: fontlar, yuvarlak köşeli paneller, ikonlar ve sesler. */
class Assets(density: Float) : Disposable {
    private val k = density.coerceIn(1f, 4f)
    private val disposables = ArrayList<Disposable>()

    val fontSmall: BitmapFont
    val font: BitmapFont
    val fontBold: BitmapFont
    val fontTitle: BitmapFont
    val fontBig: BitmapFont
    val fontHuge: BitmapFont

    val icons = Icons((96 * k).toInt().coerceAtMost(256))
    val white: TextureRegion

    private val patches = HashMap<Int, NinePatch>()
    private val rings = HashMap<Int, NinePatch>()

    init {
        val chars = FreeTypeFontGenerator.DEFAULT_CHARS + "çğıöşüÇĞİÖŞÜâÂîÎûÛ…–—’‘“”•×·→←↑↓±≤≥"
        fun gen(file: String, size: Int): BitmapFont {
            val g = FreeTypeFontGenerator(Gdx.files.internal("fonts/$file"))
            val p = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                this.size = (size * k).toInt()
                characters = chars
                minFilter = Texture.TextureFilter.Linear
                magFilter = Texture.TextureFilter.Linear
                kerning = true
            }
            val f = g.generateFont(p)
            g.dispose()
            f.data.setScale(1f / k)
            f.setUseIntegerPositions(false)
            disposables += f
            return f
        }
        fontSmall = gen("Nunito-SemiBold.ttf", 20)
        font = gen("Nunito-SemiBold.ttf", 25)
        fontBold = gen("Nunito-ExtraBold.ttf", 25)
        fontTitle = gen("Nunito-ExtraBold.ttf", 34)
        fontBig = gen("Nunito-ExtraBold.ttf", 48)
        fontHuge = gen("Nunito-ExtraBold.ttf", 76)

        val wp = Pixmap(4, 4, Pixmap.Format.RGBA8888).apply { setColor(Color.WHITE); fill() }
        val wt = Texture(wp); wp.dispose(); disposables += wt
        white = TextureRegion(wt, 1, 1, 2, 2)
        disposables += icons
    }

    /** Yuvarlak köşeli beyaz dokuz parça; renk çizimde verilir. */
    fun patch(radius: Int): NinePatch = patches.getOrPut(radius) { makePatch(radius, false) }
    fun ring(radius: Int): NinePatch = rings.getOrPut(radius) { makePatch(radius, true) }

    private fun makePatch(radius: Int, ring: Boolean): NinePatch {
        val r = (radius * k).toInt().coerceAtLeast(2)
        val size = r * 2 + 4
        val p = Pixmap(size, size, Pixmap.Format.RGBA8888)
        p.setColor(0f, 0f, 0f, 0f); p.fill()
        p.setColor(Color.WHITE)
        p.fillCircle(r, r, r); p.fillCircle(size - r - 1, r, r); p.fillCircle(r, size - r - 1, r); p.fillCircle(size - r - 1, size - r - 1, r)
        p.fillRectangle(r, 0, size - 2 * r, size); p.fillRectangle(0, r, size, size - 2 * r)
        if (ring) {
            val t = (3 * k).toInt().coerceAtLeast(2)
            p.blending = Pixmap.Blending.None
            p.setColor(0f, 0f, 0f, 0f)
            val ri = r - t
            p.fillCircle(r, r, ri); p.fillCircle(size - r - 1, r, ri); p.fillCircle(r, size - r - 1, ri); p.fillCircle(size - r - 1, size - r - 1, ri)
            p.fillRectangle(r, t, size - 2 * r, size - 2 * t); p.fillRectangle(t, r, size - 2 * t, size - 2 * r)
        }
        val tex = Texture(p).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        p.dispose(); disposables += tex
        return NinePatch(TextureRegion(tex), r + 1, r + 1, r + 1, r + 1).apply { scale(1f / k, 1f / k) }
    }

    /** Gölgeli, yuvarlak köşeli renkli panel. */
    fun panel(color: Color, radius: Int = 22, shadow: Float = 5f, outline: Color? = null): Drawable =
        RoundDrawable(patch(radius), color, shadow, outline?.let { ring(radius) to it })

    fun icon(name: String): TextureRegionDrawable = TextureRegionDrawable(icons.named(name))

    fun label(text: CharSequence, font: BitmapFont = this.font, color: Color = Theme.ink): Label =
        Label(text, Label.LabelStyle(font, color))

    val scrollStyle = ScrollPane.ScrollPaneStyle()

    // ---------------------------------------------------------------- sesler

    private val sounds = HashMap<String, Sound?>()
    var soundOn = true

    fun play(name: String, volume: Float = 1f, pitch: Float = 1f) {
        if (!soundOn) return
        val s = sounds.getOrPut(name) {
            val f = Gdx.files.internal("sounds/$name.wav")
            if (f.exists()) runCatching { Gdx.audio.newSound(f) }.getOrNull()?.also { disposables += it } else null
        }
        s?.play(volume, pitch, 0f)
    }

    override fun dispose() = disposables.forEach { it.dispose() }
}

class RoundDrawable(
    private val patch: NinePatch,
    val color: Color,
    private val shadow: Float,
    private val outline: Pair<NinePatch, Color>?,
) : BaseDrawable() {
    init {
        val pad = patch.padLeft.coerceAtMost(14f)
        leftWidth = 14f; rightWidth = 14f; topHeight = 10f; bottomHeight = 10f
        if (pad < 0) leftWidth = pad
    }

    override fun draw(batch: Batch, x: Float, y: Float, width: Float, height: Float) {
        val old = batch.packedColor
        val a = batch.color.a
        if (shadow > 0f) {
            batch.setColor(Theme.shadow.r, Theme.shadow.g, Theme.shadow.b, Theme.shadow.a * a)
            patch.draw(batch, x, y - shadow, width, height)
        }
        batch.setColor(color.r, color.g, color.b, color.a * a)
        patch.draw(batch, x, y, width, height)
        outline?.let { (ring, c) ->
            batch.setColor(c.r, c.g, c.b, c.a * a)
            ring.draw(batch, x, y, width, height)
        }
        batch.packedColor = old
    }
}
