package com.yanlisrenk.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Disposable

/** Tüm görsel ve ses varlıkları. Yazı tipleri ekran yoğunluğuna göre üretilir (Türkçe karakterler dahil). */
class Assets : Disposable {
    private val textures = HashMap<String, Texture>()
    private val regions = HashMap<String, TextureRegion>()
    val sounds = HashMap<String, Sound>()
    val music = HashMap<String, Music>()

    /** Müzik gerektiğinde yüklenir (Android'de her Music bir MediaPlayer açar). */
    fun music(name: String): Music? = music[name] ?: runCatching {
        Gdx.audio.newMusic(Gdx.files.internal("music/$name.ogg")).also { it.isLooping = true; music[name] = it }
    }.getOrNull()

    fun releaseMusic(keep: Set<String>) {
        val drop = music.keys.filter { it !in keep }
        drop.forEach { music.remove(it)?.dispose() }
    }

    lateinit var fontS: BitmapFont
    lateinit var fontM: BitmapFont
    lateinit var fontL: BitmapFont
    lateinit var fontXL: BitmapFont
    lateinit var fontHuge: BitmapFont
    /** Dünya içinde (kapı yazıları) kullanılan yazı tipi. */
    lateinit var fontWorld: BitmapFont
    private var fontPacker: com.badlogic.gdx.graphics.g2d.PixmapPacker? = null

    companion object {
        val ICONS = listOf("heart_full", "heart_empty", "coin", "diamond", "star_full", "star_empty", "lock", "play", "pause", "settings",
            "shop", "trophy", "missions", "gift", "profile", "back", "close", "check", "music_on", "music_off", "sound_on", "sound_off",
            "infinity", "map", "calendar", "crown", "shield", "bolt", "puzzle", "sparkle", "refresh", "home", "character", "costume",
            "info", "clock", "medal", "flag", "palette", "trail", "magic", "box", "plus", "minus")
        val UI = listOf("panel", "button", "pill", "circle", "ring", "glow", "particle", "shadow", "shape_circle", "shape_square",
            "shape_triangle", "shape_star", "sym_equal", "sym_notequal", "sym_next", "sym_prev", "sym_repeat", "sym_question",
            "sym_word", "sym_paint", "gradient_v", "white")
        val SFX = listOf("tap", "gate_ok", "gate_fail", "coin", "diamond", "life_refill", "level_complete", "area_complete", "unlock",
            "purchase", "achievement", "button", "countdown", "go", "combo", "star", "whoosh", "shield")
        val MUSIC = listOf("menu", "garden", "candy", "sky", "crystal", "space", "carnival", "final", "endless")
        const val CHARS = FreeTypeFontGenerator.DEFAULT_CHARS + "çÇğĞıİöÖşŞüÜâÂîÎûÛ★•→←–—’“”…₺"
    }

    fun load() {
        for (n in ICONS) loadTex(n, "icons/$n.png")
        for (n in UI) loadTex(n, "ui/$n.png")
        for (n in SFX) runCatching { sounds[n] = Gdx.audio.newSound(Gdx.files.internal("sfx/$n.ogg")) }
        buildFonts()
    }

    private fun loadTex(name: String, path: String) {
        // GLES 2.0'da 2'nin kuvveti olmayan dokularda mipmap güvenilir değildir → yalnızca POT dokularda mipmap
        val pm = com.badlogic.gdx.graphics.Pixmap(Gdx.files.internal(path))
        val pot = com.badlogic.gdx.math.MathUtils.isPowerOfTwo(pm.width) && com.badlogic.gdx.math.MathUtils.isPowerOfTwo(pm.height)
        val t = Texture(pm, pot)
        pm.dispose()
        t.setFilter(if (pot) Texture.TextureFilter.MipMapLinearLinear else Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        textures[name] = t
        regions[name] = TextureRegion(t)
    }

    /** UI sanal çözünürlüğü 720x1280; yazı tipleri gerçek piksel yoğunluğunda üretilip ölçeklenir. */
    private fun buildFonts() {
        val gen = FreeTypeFontGenerator(Gdx.files.internal("fonts/Nunito-ExtraBold.ttf"))
        val scale = (maxOf(Gdx.graphics.height / 1280f, Gdx.graphics.width / 720f)).coerceIn(0.5f, 2.5f)
        // Tüm boyutlar 1024'lük sayfalara paketlenir (aksi hâlde büyük yazı tipleri 4096'lık doku ister)
        val packer = com.badlogic.gdx.graphics.g2d.PixmapPacker(1024, 1024, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888, 2, false)
        fontPacker = packer
        fun make(size: Int, border: Float = 0f, borderColor: Color = Color.WHITE, chars: String = CHARS): BitmapFont {
            val p = FreeTypeFontGenerator.FreeTypeFontParameter()
            p.size = (size * scale).toInt().coerceAtLeast(8)
            p.characters = chars
            p.packer = packer
            p.minFilter = Texture.TextureFilter.Linear
            p.magFilter = Texture.TextureFilter.Linear
            p.kerning = true
            if (border > 0f) { p.borderWidth = border * scale; p.borderColor = borderColor; p.borderStraight = false }
            val f = gen.generateFont(p)
            f.data.setScale(1f / scale)
            f.setUseIntegerPositions(false)
            f.data.markupEnabled = true
            return f
        }
        fontS = make(24)
        fontM = make(32)
        fontL = make(44)
        fontXL = make(64)
        fontHuge = make(96, 4f, Color.WHITE, "\u0000ABCÇDEFGĞHIİJKLMNOÖPRSŞTUÜVYZXWQ0123456789!?.,:-+×%/ ")
        fontWorld = make(40)
        gen.dispose()
    }

    fun tex(name: String): Texture = textures[name] ?: textures["white"]!!
    fun region(name: String): TextureRegion = regions[name] ?: regions["white"]!!
    fun drawable(name: String): TextureRegionDrawable = TextureRegionDrawable(region(name))

    fun nine(name: String, l: Int, r: Int, t: Int, b: Int, tint: Color): NinePatchDrawable {
        val np = NinePatch(region(name), l, r, t, b)
        np.color = tint
        val d = NinePatchDrawable(np)
        return d
    }

    override fun dispose() {
        textures.values.forEach { it.dispose() }
        sounds.values.forEach { it.dispose() }
        music.values.forEach { it.dispose() }
        listOf(fontS, fontM, fontL, fontXL, fontHuge, fontWorld).forEach { it.dispose() }
        fontPacker?.dispose()
    }
}
