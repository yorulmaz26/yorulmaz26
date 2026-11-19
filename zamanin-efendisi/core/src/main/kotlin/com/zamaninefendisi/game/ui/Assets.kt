package com.zamaninefendisi.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Disposable
import kotlin.math.max

/** Yazı tipleri ve çizim kaynakları. Bütün arayüz grafikleri kod ile üretilir (harici görsel dosyası gerekmez). */
class Assets : Disposable {
    private val textures = ArrayList<Texture>()
    private val fonts = ArrayList<BitmapFont>()

    lateinit var white: TextureRegion; private set
    lateinit var fontS: BitmapFont; private set
    lateinit var fontM: BitmapFont; private set
    lateinit var fontL: BitmapFont; private set
    lateinit var fontXL: BitmapFont; private set
    lateinit var round: NinePatch; private set
    lateinit var roundSmall: NinePatch; private set
    lateinit var circle: TextureRegion; private set

    fun load() {
        val pm = Pixmap(4, 4, Pixmap.Format.RGBA8888).apply { setColor(Color.WHITE); fill() }
        white = TextureRegion(tex(pm), 1, 1, 2, 2)
        round = roundPatch(28)
        roundSmall = roundPatch(14)
        circle = TextureRegion(tex(circlePixmap(64)))

        // Ekran yoğunluğuna göre keskin yazı: sanal 1280 yüksekliğe göre ölçekle üret, sonra geri küçült.
        val scale = max(1f, Gdx.graphics.height / 1280f).coerceAtMost(3f)
        val gen = FreeTypeFontGenerator(Gdx.files.internal("fonts/Inter-Bold.otf"))
        fun make(size: Int): BitmapFont {
            val p = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                this.size = (size * scale).toInt()
                characters = CHARS
                minFilter = Texture.TextureFilter.Linear
                magFilter = Texture.TextureFilter.Linear
                incremental = false
            }
            return gen.generateFont(p).also {
                it.data.setScale(1f / scale)
                it.setUseIntegerPositions(false)
                it.data.markupEnabled = false
                fonts += it
            }
        }
        fontS = make(22)
        fontM = make(28)
        fontL = make(38)
        fontXL = make(60)
        gen.dispose()
    }

    private fun tex(pm: Pixmap): Texture {
        val t = Texture(pm).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        pm.dispose()
        textures += t
        return t
    }

    private fun circlePixmap(size: Int): Pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888).apply {
        setColor(Color.WHITE); fillCircle(size / 2, size / 2, size / 2 - 1)
    }

    private fun roundPatch(r: Int): NinePatch {
        val size = r * 2 + 2
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pm.setColor(Color.WHITE)
        pm.fillCircle(r, r, r); pm.fillCircle(size - r - 1, r, r)
        pm.fillCircle(r, size - r - 1, r); pm.fillCircle(size - r - 1, size - r - 1, r)
        pm.fillRectangle(r, 0, size - 2 * r, size)
        pm.fillRectangle(0, r, size, size - 2 * r)
        return NinePatch(tex(pm), r, r, r, r)
    }

    fun panel(color: Color, small: Boolean = false): Drawable =
        NinePatchDrawable(if (small) roundSmall else round).tint(color)

    fun solid(color: Color): Drawable = TextureRegionDrawable(white).tint(color)

    override fun dispose() {
        fonts.forEach { it.dispose() }
        textures.forEach { it.dispose() }
    }

    companion object {
        const val CHARS = FreeTypeFontGenerator.DEFAULT_CHARS + "çÇğĞıİöÖşŞüÜâÂîÎûÛ·→←↺★☆…—–’‘“”•×✓‹›"
    }
}
