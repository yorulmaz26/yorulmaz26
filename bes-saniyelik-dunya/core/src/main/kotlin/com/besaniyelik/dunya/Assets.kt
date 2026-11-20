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
    val far = maskedTexture("bg/%02d_far".format(map))
    val mid = maskedTexture("bg/%02d_mid".format(map))
    /** Boyanmış dikey gökyüzü (varsa); yoksa atlas içindeki renk geçişi şeridi kullanılır. */
    val skyTexture: Texture? = Gdx.files.internal("bg/%02d_sky.jpg".format(map)).takeIf { it.exists() }?.let {
        Texture(it).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
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

    /** Gökyüzünü verilen alanı dolduracak şekilde (oranı koruyarak, ortadan kırparak) çizer. */
    fun drawSky(b: com.badlogic.gdx.graphics.g2d.Batch, x: Float, y: Float, w: Float, h: Float) {
        val t = skyTexture
        if (t == null) { b.draw(sky, x, y, w, h); return }
        val ta = t.width.toFloat() / t.height
        val va = w / h
        var u0 = 0f; var u1 = 1f; var v0 = 0f; var v1 = 1f
        if (va > ta) { val k = ta / va; v0 = (1f - k) / 2f; v1 = v0 + k } else { val k = va / ta; u0 = (1f - k) / 2f; u1 = u0 + k }
        b.draw(t, x, y, w, h, u0, v1, u1, v0)
    }

    override fun dispose() { atlas.dispose(); far.dispose(); mid.dispose(); skyTexture?.dispose() }

    companion object {
        /** Paralaks katmanlarının özgün en-boy oranı (dokular 2'nin kuvveti boyutunda, 2048x512, saklanır). */
        const val PARALLAX_ASPECT = 3.2f

        /** JPG renk + PNG alfa maskesinden saydam doku kurar (APK boyutunu küçük tutmak için). */
        fun maskedTexture(base: String): Texture {
            val rgb = com.badlogic.gdx.graphics.Pixmap(Gdx.files.internal("$base.jpg"))
            val alpha = com.badlogic.gdx.graphics.Pixmap(Gdx.files.internal("${base}_a.png"))
            val w = rgb.width; val h = rgb.height
            val out = com.badlogic.gdx.graphics.Pixmap(w, h, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888)
            out.blending = com.badlogic.gdx.graphics.Pixmap.Blending.None
            val src = rgb.pixels; val al = alpha.pixels; val dst = out.pixels
            val rgbStep = when (rgb.format) { com.badlogic.gdx.graphics.Pixmap.Format.RGB888 -> 3; com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888 -> 4; else -> 0 }
            val aStep = when (alpha.format) { com.badlogic.gdx.graphics.Pixmap.Format.Alpha, com.badlogic.gdx.graphics.Pixmap.Format.Intensity -> 1
                com.badlogic.gdx.graphics.Pixmap.Format.LuminanceAlpha -> 2; com.badlogic.gdx.graphics.Pixmap.Format.RGB888 -> 3; com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888 -> 4; else -> 0 }
            if (rgbStep > 0 && aStep > 0 && alpha.width == w && alpha.height == h) {
                val n = w * h
                val bytes = ByteArray(n * 4)
                for (i in 0 until n) {
                    bytes[i * 4] = src.get(i * rgbStep); bytes[i * 4 + 1] = src.get(i * rgbStep + 1); bytes[i * 4 + 2] = src.get(i * rgbStep + 2)
                    bytes[i * 4 + 3] = al.get(i * aStep)
                }
                dst.position(0); dst.put(bytes); dst.position(0)
            } else {
                for (y in 0 until h) for (x in 0 until w) {
                    val c = rgb.getPixel(x, y)
                    val a = alpha.getPixel(x * alpha.width / w, y * alpha.height / h) ushr 24 and 0xff
                    out.drawPixel(x, y, (c and 0xffffff00.toInt()) or a)
                }
            }
            rgb.dispose(); alpha.dispose()
            return Texture(out, true).apply {
                setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.ClampToEdge)
                setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
                out.dispose()
            }
        }
    }
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

    private val heroAtlases = HashMap<String, TextureAtlas?>()
    private val heroFrames = HashMap<String, List<TextureRegion>?>()

    /** Karakter görünümünün 16 animasyon karesi (yoksa null → parçalı çizim kullanılır). */
    fun heroFrames(skinId: String): List<TextureRegion>? = heroFrames.getOrPut(skinId) {
        val f = Gdx.files.internal("atlas/hero_$skinId.atlas")
        if (!f.exists()) null else {
            val at = TextureAtlas(f)
            heroAtlases[skinId] = at
            (0 until 16).map { at.findRegion("f$it") }
        }
    }

    fun theme(map: Int): Theme {
        val t = theme
        if (t != null && t.map == map) return t
        t?.dispose()
        return Theme(map).also { theme = it }
    }

    override fun dispose() {
        ui.dispose(); game.dispose(); theme?.dispose(); heroAtlases.values.forEach { it?.dispose() }
        listOf(f22, f28, f36, f48, f36o, f48o, f72o).forEach { it.dispose() }
    }
}
