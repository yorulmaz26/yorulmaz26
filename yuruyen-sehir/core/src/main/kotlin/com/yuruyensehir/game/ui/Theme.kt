package com.yuruyensehir.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Disposable
import com.yuruyensehir.logic.Biome
import com.yuruyensehir.logic.Resource

/** Açık, canlı ve sıcak renk paleti. */
object Palette {
    val sky = Color.valueOf("BFE8F5")
    val skyTop = Color.valueOf("8FD3F0")
    val turquoise = Color.valueOf("2EC4B6")
    val turquoiseDark = Color.valueOf("1A9C90")
    val green = Color.valueOf("7BC86C")
    val greenDark = Color.valueOf("4E9F45")
    val cream = Color.valueOf("FFF6E5")
    val creamDark = Color.valueOf("F3E3C3")
    val gold = Color.valueOf("F6C445")
    val goldDark = Color.valueOf("D9A21B")
    val brown = Color.valueOf("B98B5E")
    val brownDark = Color.valueOf("7A5434")
    val orange = Color.valueOf("FF9F43")
    val red = Color.valueOf("EE6C4D")
    val text = Color.valueOf("4A3826")
    val textSoft = Color.valueOf("7D6750")
    val white = Color.WHITE
    val panel = Color.valueOf("FFF9EEF5")
    val shade = Color(0.2f, 0.25f, 0.3f, 0.35f)

    fun resource(r: Resource): Color = when (r) {
        Resource.FOOD -> Color.valueOf("8BC34A")
        Resource.WATER -> Color.valueOf("4FC3F7")
        Resource.WOOD -> Color.valueOf("A1724E")
        Resource.STONE -> Color.valueOf("A0A4AA")
        Resource.METAL -> Color.valueOf("7E8EA3")
        Resource.CLOTH -> Color.valueOf("F48FB1")
        Resource.ENERGY -> Color.valueOf("FFD54F")
        Resource.GOLD -> Color.valueOf("F6B93B")
        Resource.TOOLS -> Color.valueOf("8D6E63")
        Resource.WASTE -> Color.valueOf("9E9D89")
        Resource.CRYSTAL -> Color.valueOf("B388FF")
        Resource.RARE_ORE -> Color.valueOf("E57373")
        Resource.ANCIENT_PART -> Color.valueOf("C5A46D")
        Resource.TECH_PART -> Color.valueOf("26C6DA")
        Resource.LIGHT_MATERIAL -> Color.valueOf("E0F2F1")
        Resource.CARE_ESSENCE -> Color.valueOf("81C784")
    }

    fun biome(b: Biome): Color = when (b) {
        Biome.PLAINS -> Color.valueOf("A8DC7A")
        Biome.FOREST -> Color.valueOf("6DBE6A")
        Biome.DESERT -> Color.valueOf("F2D68A")
        Biome.MOUNTAINS -> Color.valueOf("B9A894")
        Biome.SWAMP -> Color.valueOf("8FB78A")
        Biome.SNOW -> Color.valueOf("EEF6FB")
        Biome.VOLCANIC -> Color.valueOf("C98A6B")
        Biome.CRYSTAL_VALLEY -> Color.valueOf("C9B8F2")
        Biome.RUINS -> Color.valueOf("D6C6A2")
        Biome.LAKE -> Color.valueOf("8ED3EE")
        Biome.STORMLANDS -> Color.valueOf("A9B7C9")
        Biome.MYSTIC_FOREST -> Color.valueOf("7FC7A8")
    }
}

/** Kodla üretilen arayüz teması (harici skin dosyası gerekmez). */
class Theme : Disposable {
    val uiScale: Float = (Gdx.graphics.width / 720f).coerceIn(1f, 3f)
    private val textures = ArrayList<Texture>()
    val skin = Skin()

    val fontSmall: BitmapFont
    val font: BitmapFont
    val fontTitle: BitmapFont
    val fontBig: BitmapFont
    val white: TextureRegion

    init {
        val gen = FreeTypeFontGenerator(Gdx.files.internal("fonts/Nunito-Bold.ttf"))
        fun make(px: Int): BitmapFont {
            val p = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                size = (px * uiScale).toInt()
                characters = FreeTypeFontGenerator.DEFAULT_CHARS + "ğĞşŞıİçÇöÖüÜâÂîÎûÛ•…›–—≤≥"
                minFilter = Texture.TextureFilter.Linear
                magFilter = Texture.TextureFilter.Linear
                incremental = false
            }
            return gen.generateFont(p).apply {
                data.setScale(1f / uiScale)
                setUseIntegerPositions(false)
                data.markupEnabled = true
            }
        }
        fontSmall = make(19)
        font = make(23)
        fontTitle = make(29)
        fontBig = make(40)
        gen.dispose()

        val px = Pixmap(4, 4, Pixmap.Format.RGBA8888).apply { setColor(Color.WHITE); fill() }
        val wt = Texture(px).also { textures += it }
        px.dispose()
        white = TextureRegion(wt, 1, 1, 2, 2)
        skin.add("white", TextureRegionDrawable(white), Drawable::class.java)

        val cpm = Pixmap(64, 64, Pixmap.Format.RGBA8888).apply { setColor(Color.WHITE); fillCircle(32, 32, 31) }
        val ct = Texture(cpm).also { textures += it; it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        cpm.dispose()
        skin.add("circle", TextureRegionDrawable(TextureRegion(ct)), Drawable::class.java)

        skin.add("round", roundedPatch(18), NinePatch::class.java)
        skin.add("roundSmall", roundedPatch(10), NinePatch::class.java)
        skin.add("pill", roundedPatch(26), NinePatch::class.java)

        skin.add("small", Label.LabelStyle(fontSmall, Palette.text))
        skin.add("default", Label.LabelStyle(font, Palette.text))
        skin.add("title", Label.LabelStyle(fontTitle, Palette.text))
        skin.add("big", Label.LabelStyle(fontBig, Palette.text))
        skin.add("light", Label.LabelStyle(font, Color.WHITE))
        skin.add("lightSmall", Label.LabelStyle(fontSmall, Color.WHITE))
        skin.add("soft", Label.LabelStyle(fontSmall, Palette.textSoft))

        fun btn(name: String, up: Color, down: Color, fontColor: Color, f: BitmapFont = font, checked: Color? = null) {
            skin.add(name, TextButton.TextButtonStyle().apply {
                this.up = tinted("round", up)
                this.down = tinted("round", down)
                this.over = tinted("round", up.cpy().lerp(Color.WHITE, 0.12f))
                this.disabled = tinted("round", Color.valueOf("DDD5C8"))
                if (checked != null) this.checked = tinted("round", checked)
                this.font = f
                this.fontColor = fontColor
                this.disabledFontColor = Color.valueOf("A69886")
            })
        }
        btn("default", Palette.cream, Palette.creamDark, Palette.text, checked = Palette.gold)
        btn("primary", Palette.turquoise, Palette.turquoiseDark, Color.WHITE)
        btn("gold", Palette.gold, Palette.goldDark, Palette.text)
        btn("danger", Palette.red, Color.valueOf("C9533A"), Color.WHITE)
        btn("green", Palette.green, Palette.greenDark, Color.WHITE)
        btn("nav", Color.valueOf("FFFDF7"), Palette.creamDark, Palette.text, fontSmall, checked = Palette.gold)
        btn("chip", Color.valueOf("FFFFFFCC"), Palette.creamDark, Palette.text, fontSmall, checked = Palette.turquoise)

        skin.add("default", ScrollPane.ScrollPaneStyle())
    }

    private fun roundedPatch(r: Int): NinePatch {
        val size = r * 2 + 2
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pm.setColor(Color.WHITE)
        pm.fillCircle(r, r, r)
        pm.fillCircle(size - r - 1, r, r)
        pm.fillCircle(r, size - r - 1, r)
        pm.fillCircle(size - r - 1, size - r - 1, r)
        pm.fillRectangle(r, 0, size - 2 * r, size)
        pm.fillRectangle(0, r, size, size - 2 * r)
        val t = Texture(pm).also { textures += it }
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        pm.dispose()
        return NinePatch(t, r, r, r, r).apply {
            // Daha küçük ekranlarda köşeler orantılı küçülsün
            scale(0.5f, 0.5f)
        }
    }

    fun tinted(patch: String, color: Color): Drawable = NinePatchDrawable(skin.get(patch, NinePatch::class.java)).tint(color)
    fun circle(color: Color): Drawable = (skin.getDrawable("circle") as TextureRegionDrawable).tint(color)
    fun solid(color: Color): Drawable = (skin.getDrawable("white") as TextureRegionDrawable).tint(color)

    override fun dispose() {
        skin.dispose()
        fontSmall.dispose(); font.dispose(); fontTitle.dispose(); fontBig.dispose()
        textures.forEach { it.dispose() }
    }
}
