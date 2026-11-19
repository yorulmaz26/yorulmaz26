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
import com.badlogic.gdx.utils.Disposable

/** Referans panolarındaki parşömen, ahşap ve altın tonlarına dayalı tek tasarım sistemi. */
object Palette {
    val ink: Color = Color.valueOf("4A2E12")
    val inkSoft: Color = Color.valueOf("7A5630")
    val parchment: Color = Color.valueOf("F7E9C6")
    val parchmentDark: Color = Color.valueOf("E9D3A0")
    val border: Color = Color.valueOf("8B5A2B")
    val gold: Color = Color.valueOf("FFC83D")
    val goldDark: Color = Color.valueOf("B8860B")
    val navy: Color = Color.valueOf("1E3557")
    val green: Color = Color.valueOf("5DAA3B")
    val red: Color = Color.valueOf("D9534F")
    val blue: Color = Color.valueOf("3D7DCA")
    val white: Color = Color.WHITE
    val dim: Color = Color(0.05f, 0.08f, 0.15f, 0.55f)
    val skyTop: Color = Color.valueOf("6EC3F5")
    val skyBottom: Color = Color.valueOf("D8F1FF")
}

class Theme : Disposable {
    val skin = Skin()
    private val textures = ArrayList<Texture>()
    val pixelScale: Float = (Gdx.graphics.height / 1280f).coerceIn(1f, 3f)
    val title: BitmapFont
    val huge: BitmapFont
    val body: BitmapFont
    val small: BitmapFont
    val tiny: BitmapFont
    val white: TextureRegion

    init {
        val chars = FreeTypeFontGenerator.DEFAULT_CHARS + "çğıöşüÇĞİÖŞÜâÂîÎûÛ★☆✓✗←→↑↓•…–—·×+"
        val sans = FreeTypeFontGenerator(Gdx.files.internal("fonts/DejaVuSans-Bold.ttf"))
        val serif = FreeTypeFontGenerator(Gdx.files.internal("fonts/DejaVuSerif-Bold.ttf"))
        fun font(gen: FreeTypeFontGenerator, size: Int, border: Float = 0f, borderColor: Color = Palette.ink, color: Color = Color.WHITE, shadow: Int = 0): BitmapFont {
            val p = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                this.size = (size * pixelScale).toInt()
                characters = chars
                minFilter = Texture.TextureFilter.Linear; magFilter = Texture.TextureFilter.Linear
                this.color = color
                if (border > 0) { borderWidth = border * pixelScale; this.borderColor = borderColor }
                if (shadow > 0) { shadowOffsetX = 0; shadowOffsetY = (shadow * pixelScale).toInt(); shadowColor = Color(0f, 0f, 0f, 0.45f) }
            }
            return gen.generateFont(p).also { it.data.setScale(1f / pixelScale); it.setUseIntegerPositions(false) }
        }
        huge = font(serif, 64, 4f, Color.valueOf("5A2D0C"), Color.valueOf("FFD452"), 4)
        title = font(serif, 38, 3f, Color.valueOf("5A2D0C"), Color.valueOf("FFE08A"), 3)
        body = font(sans, 26)
        small = font(sans, 21)
        tiny = font(sans, 17)
        sans.dispose(); serif.dispose()

        val wp = Pixmap(4, 4, Pixmap.Format.RGBA8888).apply { setColor(Color.WHITE); fill() }
        white = TextureRegion(Texture(wp).also { textures.add(it) }, 1, 1, 2, 2); wp.dispose()
        skin.add("white", white)
        skin.add("parchment", framedPatch(Palette.parchment, Palette.parchmentDark, Palette.border, Color.valueOf("D9B26F")))
        skin.add("navy", framedPatch(Color.valueOf("24406A"), Color.valueOf("172C4C"), Color.valueOf("C99A3B"), Color.valueOf("E8C36A")))
        skin.add("btnWood", buttonPatch(Color.valueOf("D99A46"), Color.valueOf("A8672A"), Color.valueOf("6B3D14")))
        skin.add("btnWoodDown", buttonPatch(Color.valueOf("B97C34"), Color.valueOf("8C5320"), Color.valueOf("6B3D14")))
        skin.add("btnGreen", buttonPatch(Color.valueOf("7CC24E"), Color.valueOf("4E8F2A"), Color.valueOf("2F5A17")))
        skin.add("btnGreenDown", buttonPatch(Color.valueOf("63A83A"), Color.valueOf("3F7A21"), Color.valueOf("2F5A17")))
        skin.add("btnBlue", buttonPatch(Color.valueOf("5B9BE0"), Color.valueOf("2F66AD"), Color.valueOf("1C3F70")))
        skin.add("btnBlueDown", buttonPatch(Color.valueOf("4A86C8"), Color.valueOf("265793"), Color.valueOf("1C3F70")))
        skin.add("btnRed", buttonPatch(Color.valueOf("E86A5F"), Color.valueOf("B23A33"), Color.valueOf("6E1E19")))
        skin.add("btnRedDown", buttonPatch(Color.valueOf("D05A50"), Color.valueOf("962F29"), Color.valueOf("6E1E19")))
        skin.add("btnGrey", buttonPatch(Color.valueOf("C9C2B4"), Color.valueOf("9C9484"), Color.valueOf("6A6458")))
        skin.add("chip", framedPatch(Color(0.12f, 0.2f, 0.33f, 0.88f), Color(0.08f, 0.14f, 0.25f, 0.88f), Color.valueOf("C99A3B"), Color(1f, 1f, 1f, 0.12f), r = 14, size = 48))
        skin.add("bar", framedPatch(Color.valueOf("3B2A18"), Color.valueOf("2A1C0E"), Color.valueOf("6B3D14"), Color(0f, 0f, 0f, 0f), r = 8, size = 24, b = 2))
        skin.add("barFill", framedPatch(Color.valueOf("8EE05A"), Color.valueOf("4E9F2A"), Color(0f, 0f, 0f, 0f), Color(1f, 1f, 1f, 0.3f), r = 8, size = 24, b = 0))
        skin.add("barGold", framedPatch(Color.valueOf("FFE07A"), Color.valueOf("E0A021"), Color(0f, 0f, 0f, 0f), Color(1f, 1f, 1f, 0.3f), r = 8, size = 24, b = 0))
        skin.add("circle", circle(64))

        skin.add("default", Label.LabelStyle(body, Palette.ink))
        skin.add("small", Label.LabelStyle(small, Palette.ink))
        skin.add("tiny", Label.LabelStyle(tiny, Palette.inkSoft))
        skin.add("light", Label.LabelStyle(body, Color.WHITE))
        skin.add("lightSmall", Label.LabelStyle(small, Color.WHITE))
        skin.add("lightTiny", Label.LabelStyle(tiny, Color.WHITE))
        skin.add("title", Label.LabelStyle(title, Color.WHITE))
        skin.add("huge", Label.LabelStyle(huge, Color.WHITE))

        fun tb(up: String, down: String, font: BitmapFont) = TextButton.TextButtonStyle().apply {
            this.up = NinePatchDrawable(skin.getPatch(up)); this.down = NinePatchDrawable(skin.getPatch(down)); this.over = this.up
            this.disabled = NinePatchDrawable(skin.getPatch("btnGrey"))
            this.font = font; fontColor = Color.WHITE; disabledFontColor = Color.valueOf("EEEAE0")
            pressedOffsetY = -2f
        }
        skin.add("default", tb("btnWood", "btnWoodDown", body))
        skin.add("green", tb("btnGreen", "btnGreenDown", body))
        skin.add("blue", tb("btnBlue", "btnBlueDown", body))
        skin.add("red", tb("btnRed", "btnRedDown", body))
        skin.add("smallWood", tb("btnWood", "btnWoodDown", small))
        skin.add("smallGreen", tb("btnGreen", "btnGreenDown", small))
        skin.add("smallBlue", tb("btnBlue", "btnBlueDown", small))
        skin.add("smallRed", tb("btnRed", "btnRedDown", small))
        skin.add("default", ScrollPane.ScrollPaneStyle().apply { vScrollKnob = tinted("bar", Color(1f, 1f, 1f, 0.4f)) })
        skin.add("default", CheckBox.CheckBoxStyle().apply {
            checkboxOff = NinePatchDrawable(skin.getPatch("btnGrey")); checkboxOn = NinePatchDrawable(skin.getPatch("btnGreen"))
            font = body; fontColor = Palette.ink
        })
    }

    fun patch(name: String): Drawable = NinePatchDrawable(skin.getPatch(name))
    fun tinted(name: String, c: Color): Drawable = skin.newDrawable(name, c)

    /** Yuvarlatılmış, dikey degradeli, kenarlıklı ve üstü parlak dokuz parçalı panel. */
    private fun framedPatch(top: Color, bottom: Color, border: Color, highlight: Color, r: Int = 22, size: Int = 72, b: Int = 4): NinePatch {
        val p = Pixmap(size, size, Pixmap.Format.RGBA8888)
        p.blending = Pixmap.Blending.None
        for (y in 0 until size) for (x in 0 until size) {
            val inside = roundInside(x, y, size, r)
            if (!inside) { p.drawPixel(x, y, 0); continue }
            val edge = !roundInside(x - b, y, size, r) || !roundInside(x + b, y, size, r) || !roundInside(x, y - b, size, r) || !roundInside(x, y + b, size, r)
            val c = if (edge && b > 0) border else Color(top).lerp(bottom, y / size.toFloat())
            if (!edge && y in (b + 1)..(b + 3)) c.lerp(Color.WHITE, highlight.a * 2)
            p.drawPixel(x, y, Color.rgba8888(c))
        }
        val t = Texture(p).also { textures.add(it); it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        p.dispose()
        return NinePatch(t, r + 2, r + 2, r + 2, r + 2)
    }

    private fun buttonPatch(top: Color, bottom: Color, border: Color): NinePatch {
        val size = 64; val r = 18; val b = 3; val depth = 6
        val p = Pixmap(size, size, Pixmap.Format.RGBA8888)
        p.blending = Pixmap.Blending.None
        for (y in 0 until size) for (x in 0 until size) {
            if (!roundInside(x, y, size, r)) { p.drawPixel(x, y, 0); continue }
            val edge = !roundInside(x - b, y, size, r) || !roundInside(x + b, y, size, r) || !roundInside(x, y - b, size, r) || !roundInside(x, y + b, size, r)
            val c = when {
                edge -> border
                y >= size - depth - b -> Color(bottom).lerp(border, 0.35f)
                else -> Color(top).lerp(bottom, y / (size - depth).toFloat())
            }
            if (!edge && y in (b + 1)..(b + 4)) c.lerp(Color.WHITE, 0.35f)
            p.drawPixel(x, y, Color.rgba8888(c))
        }
        val t = Texture(p).also { textures.add(it); it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        p.dispose()
        return NinePatch(t, r + 1, r + 1, r, r + depth)
    }

    private fun roundInside(x: Int, y: Int, size: Int, r: Int): Boolean {
        if (x < 0 || y < 0 || x >= size || y >= size) return false
        val cx = when { x < r -> r; x >= size - r -> size - r - 1; else -> x }
        val cy = when { y < r -> r; y >= size - r -> size - r - 1; else -> y }
        val dx = x - cx; val dy = y - cy
        return dx * dx + dy * dy <= r * r
    }

    private fun circle(size: Int): TextureRegion {
        val p = Pixmap(size, size, Pixmap.Format.RGBA8888)
        p.setColor(Color.WHITE); p.fillCircle(size / 2, size / 2, size / 2 - 1)
        val t = Texture(p).also { textures.add(it); it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        p.dispose()
        return TextureRegion(t)
    }

    override fun dispose() {
        skin.dispose()
        textures.forEach { it.dispose() }
    }
}
