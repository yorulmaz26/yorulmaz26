package com.besaniyelik.dunya

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.utils.Disposable

/** Bir haritanın görsel teması (karo atlası + paralaks arka planlar). Yalnızca o harita oynanırken bellekte tutulur. */
class Theme(val map: Int) : Disposable {
    val atlas = TextureAtlas(Gdx.files.internal("atlas/theme_%02d.atlas".format(map)))
    val far = Texture(Gdx.files.internal("atlas/bg_%02d_far.png".format(map)), true).apply {
        setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.ClampToEdge); setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
    }
    val mid = Texture(Gdx.files.internal("atlas/bg_%02d_mid.png".format(map)), true).apply {
        setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.ClampToEdge); setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
    }
    val top: TextureRegion = atlas.findRegion("top")
    val fill: TextureRegion = atlas.findRegion("fill")
    val ceil: TextureRegion = atlas.findRegion("ceil")
    val blockL: TextureRegion = atlas.findRegion("block_l")
    val blockM: TextureRegion = atlas.findRegion("block_m")
    val blockR: TextureRegion = atlas.findRegion("block_r")
    val low: TextureRegion = atlas.findRegion("low")
    /** Gökyüzü şeridinin orta sütunu (kenar filtrelemesi saydam pikselleri karıştırmasın diye). */
    val sky: TextureRegion = atlas.findRegion("sky").let { TextureRegion(it, 1, 2, 2, it.regionHeight - 4) }
    val decor: List<TextureRegion> = (0 until 6).map { atlas.findRegion("decor_$it") }

    override fun dispose() { atlas.dispose(); far.dispose(); mid.dispose() }
}

/** Bütün ortak varlıklar: atlaslar, yazı tipleri. Yazı tipleri ekran yoğunluğuna göre keskin üretilir. */
class Assets : Disposable {
    val ui = TextureAtlas(Gdx.files.internal("atlas/ui.atlas"))
    val game = TextureAtlas(Gdx.files.internal("atlas/game.atlas"))
    private val regionCache = HashMap<String, TextureRegion>()
    private val patchCache = HashMap<String, NinePatch>()
    private var theme: Theme? = null

    /** UI sanal çözünürlüğü (720x1280) ile gerçek piksel arasındaki oran. */
    val density: Float = (Gdx.graphics.height / 1280f).coerceIn(0.5f, 3f).let { if (it.isNaN() || it <= 0f) 1f else it }

    private val chars = FreeTypeFontGenerator.DEFAULT_CHARS + "çÇğĞıİöÖşŞüÜâÂîÎûÛ•…×→←↑↓★✓–—’“”"
    val f22: BitmapFont
    val f28: BitmapFont
    val f36: BitmapFont
    val f48: BitmapFont
    val f36o: BitmapFont
    val f48o: BitmapFont
    val f72o: BitmapFont

    init {
        val gen = FreeTypeFontGenerator(Gdx.files.internal("fonts/Nunito-ExtraBold.ttf"))
        fun make(size: Int, border: Float = 0f, borderColor: Color = Color(0.15f, 0.22f, 0.35f, 0.55f), shadow: Boolean = false): BitmapFont {
            val p = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                this.size = (size * density).toInt().coerceAtLeast(8)
                characters = chars
                color = Color.WHITE
                if (border > 0f) { borderWidth = border * density; this.borderColor = borderColor; borderStraight = false }
                if (shadow) { shadowOffsetY = (3 * density).toInt(); shadowColor = Color(0.15f, 0.22f, 0.35f, 0.35f) }
                minFilter = Texture.TextureFilter.Linear; magFilter = Texture.TextureFilter.Linear
                incremental = false
            }
            return gen.generateFont(p).apply { data.setScale(1f / density); setUseIntegerPositions(false); data.markupEnabled = true }
        }
        f22 = make(22); f28 = make(28); f36 = make(36); f48 = make(48)
        f36o = make(36, 3f, shadow = true); f48o = make(48, 4f, shadow = true); f72o = make(72, 5f, shadow = true)
        gen.dispose()
    }

    fun ui(name: String): TextureRegion = regionCache.getOrPut("u:$name") { ui.findRegion(name) ?: game.findRegion(name) ?: throw IllegalArgumentException("Bölge yok: $name") }
    fun g(name: String): TextureRegion = regionCache.getOrPut("g:$name") { game.findRegion(name) ?: ui.findRegion(name) ?: throw IllegalArgumentException("Bölge yok: $name") }
    fun has(name: String) = ui.findRegion(name) != null || game.findRegion(name) != null
    fun patch(name: String): NinePatch = patchCache.getOrPut(name) { ui.createPatch(name).apply { scale(1f, 1f) } }

    fun theme(map: Int): Theme {
        val t = theme
        if (t != null && t.map == map) return t
        t?.dispose()
        return Theme(map).also { theme = it }
    }

    override fun dispose() {
        ui.dispose(); game.dispose(); theme?.dispose()
        listOf(f22, f28, f36, f48, f36o, f48o, f72o).forEach { it.dispose() }
    }
}
