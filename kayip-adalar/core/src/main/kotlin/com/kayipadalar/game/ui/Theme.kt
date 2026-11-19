package com.kayipadalar.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Disposable

/** Açık, renkli tek tasarım sistemi. Tüm ekranlar bu renk ve stilleri kullanır. */
object Palette {
    val skyTop: Color = Color.valueOf("7CC8FF")
    val skyBottom: Color = Color.valueOf("E3F6FF")
    val primary: Color = Color.valueOf("4C8DF6")
    val primaryDown: Color = Color.valueOf("3B74D1")
    val accent: Color = Color.valueOf("FFB547")
    val accentDown: Color = Color.valueOf("E89A2C")
    val success: Color = Color.valueOf("4FBF6A")
    val successDown: Color = Color.valueOf("3DA157")
    val danger: Color = Color.valueOf("F0645A")
    val dangerDown: Color = Color.valueOf("D34C43")
    val panel: Color = Color(1f, 1f, 1f, 0.92f)
    val panelAlt: Color = Color.valueOf("F2F7FF")
    val panelBorder: Color = Color.valueOf("CFE0F7")
    val text: Color = Color.valueOf("2E3A59")
    val textMuted: Color = Color.valueOf("6B7A99")
    val textLight: Color = Color.WHITE
    val disabled: Color = Color.valueOf("C5CEDD")
    val gold: Color = Color.valueOf("F5B700")
    val dim: Color = Color(0.18f, 0.25f, 0.4f, 0.45f)
}

class Theme(textScale: Float = 1f) : Disposable {
    val skin = Skin()
    private val textures = ArrayList<Texture>()
    val pixelScale: Float = (Gdx.graphics.height / 1280f).coerceIn(1f, 3f)
    val huge: BitmapFont
    val title: BitmapFont
    val body: BitmapFont
    val small: BitmapFont
    val white: TextureRegion

    init {
        val gen = FreeTypeFontGenerator(Gdx.files.internal("fonts/DejaVuSans-Bold.ttf"))
        val chars = FreeTypeFontGenerator.DEFAULT_CHARS + "çğıöşüÇĞİÖŞÜâÂîÎûÛ★☆✓✗←→↑↓↻•…–—·♥⚡⏳⏱⌛"
        fun font(size: Int): BitmapFont {
            val p = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                this.size = (size * textScale * pixelScale).toInt()
                characters = chars
                minFilter = Texture.TextureFilter.Linear
                magFilter = Texture.TextureFilter.Linear
                color = Color.WHITE
            }
            return gen.generateFont(p).also {
                it.data.setScale(1f / pixelScale)
                it.setUseIntegerPositions(false)
            }
        }
        huge = font(64); title = font(42); body = font(28); small = font(22)
        gen.dispose()

        val wp = Pixmap(4, 4, Pixmap.Format.RGBA8888).apply { setColor(Color.WHITE); fill() }
        white = TextureRegion(Texture(wp).also { textures.add(it) }, 1, 1, 2, 2)
        wp.dispose()
        skin.add("white", white)
        skin.add("round", roundPatch(64, 22))
        skin.add("roundSmall", roundPatch(32, 10))
        skin.add("circle", circleRegion(64))

        skin.add("huge", huge); skin.add("title", title); skin.add("default-font", body); skin.add("small", small)
        skin.add("default", Label.LabelStyle(body, Palette.text))
        skin.add("title", Label.LabelStyle(title, Palette.text))
        skin.add("huge", Label.LabelStyle(huge, Palette.text))
        skin.add("small", Label.LabelStyle(small, Palette.text))
        skin.add("muted", Label.LabelStyle(small, Palette.textMuted))
        skin.add("light", Label.LabelStyle(body, Palette.textLight))
        skin.add("lightSmall", Label.LabelStyle(small, Palette.textLight))

        fun buttonStyle(up: Color, down: Color, font: BitmapFont = body, fontColor: Color = Palette.textLight) = TextButton.TextButtonStyle().apply {
            this.up = tinted("round", up); this.down = tinted("round", down); this.over = tinted("round", up)
            this.disabled = tinted("round", Palette.disabled)
            this.font = font; this.fontColor = fontColor; this.disabledFontColor = Color.valueOf("8A94A8")
        }
        skin.add("default", buttonStyle(Palette.primary, Palette.primaryDown))
        skin.add("accent", buttonStyle(Palette.accent, Palette.accentDown))
        skin.add("success", buttonStyle(Palette.success, Palette.successDown))
        skin.add("danger", buttonStyle(Palette.danger, Palette.dangerDown))
        skin.add("ghost", buttonStyle(Palette.panelAlt, Palette.panelBorder, body, Palette.text))
        skin.add("smallBtn", buttonStyle(Palette.primary, Palette.primaryDown, small))
        skin.add("smallAccent", buttonStyle(Palette.accent, Palette.accentDown, small))
        skin.add("smallGhost", buttonStyle(Palette.panelAlt, Palette.panelBorder, small, Palette.text))
        skin.add("smallSuccess", buttonStyle(Palette.success, Palette.successDown, small))
        skin.add("tab", buttonStyle(Palette.panelAlt, Palette.primaryDown, small, Palette.text).apply {
            checked = tinted("round", Palette.primary); checkedFontColor = Palette.textLight
        })

        skin.add("default", ScrollPane.ScrollPaneStyle().apply {
            vScrollKnob = tinted("roundSmall", Color(0.3f, 0.45f, 0.7f, 0.35f))
        })
        skin.add("default", CheckBox.CheckBoxStyle().apply {
            checkboxOff = tinted("roundSmall", Palette.disabled)
            checkboxOn = tinted("roundSmall", Palette.success)
            font = body; fontColor = Palette.text
        })
        skin.add("small", CheckBox.CheckBoxStyle().apply {
            checkboxOff = tinted("roundSmall", Palette.disabled)
            checkboxOn = tinted("roundSmall", Palette.success)
            font = small; fontColor = Palette.text; disabledFontColor = Palette.textMuted
        })
        skin.add("default-horizontal", ProgressBar.ProgressBarStyle().apply {
            background = tinted("roundSmall", Color.valueOf("DCE6F5")).also { it.minHeight = 16f }
            knobBefore = tinted("roundSmall", Palette.success).also { it.minHeight = 16f }
        })
    }

    fun tinted(name: String, c: Color): Drawable = skin.newDrawable(name, c)
    val panel: Drawable get() = tinted("round", Palette.panel)
    val panelAlt: Drawable get() = tinted("round", Palette.panelAlt)

    private fun roundPatch(size: Int, r: Int): NinePatch {
        val p = Pixmap(size, size, Pixmap.Format.RGBA8888)
        p.setColor(Color.WHITE)
        p.fillCircle(r, r, r); p.fillCircle(size - r - 1, r, r); p.fillCircle(r, size - r - 1, r); p.fillCircle(size - r - 1, size - r - 1, r)
        p.fillRectangle(r, 0, size - 2 * r, size); p.fillRectangle(0, r, size, size - 2 * r)
        val t = Texture(p).also { textures.add(it); it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        p.dispose()
        return NinePatch(t, r, r, r, r)
    }

    private fun circleRegion(size: Int): TextureRegion {
        val p = Pixmap(size, size, Pixmap.Format.RGBA8888)
        p.setColor(Color.WHITE); p.fillCircle(size / 2, size / 2, size / 2 - 1)
        val t = Texture(p).also { textures.add(it); it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        p.dispose()
        return TextureRegion(t)
    }

    fun drawable(region: TextureRegion) = TextureRegionDrawable(region)
    fun patch(name: String) = NinePatchDrawable(skin.getPatch(name))

    override fun dispose() {
        skin.dispose()
        textures.forEach { it.dispose() }
    }
}
