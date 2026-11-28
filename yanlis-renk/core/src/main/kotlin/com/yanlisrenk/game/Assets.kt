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
            "info", "clock", "medal", "flag", "palette", "trail", "magic", "box", "plus", "minus", "film", "globe")
        val UI = listOf("panel", "button", "pill", "circle", "ring", "glow", "particle", "shadow", "shape_circle", "shape_square",
            "shape_triangle", "shape_star", "sym_equal", "sym_notequal", "sym_next", "sym_prev", "sym_repeat", "sym_question",
            "sym_word", "sym_paint", "gradient_v", "white")
        val SFX = listOf("tap", "gate_ok", "gate_fail", "coin", "diamond", "life_refill", "level_complete", "area_complete", "unlock",
            "purchase", "achievement", "button", "countdown", "go", "combo", "star", "whoosh", "shield")
        val MUSIC = listOf("menu", "garden", "candy", "sky", "crystal", "space", "carnival", "final", "endless")
        /** Kullanıcı görselleri: (doku adı, yol) */
        val ART = listOf(
            "ui2_button" to "ui2/button.png", "ui2_panel" to "ui2/panel.png", "ui2_logo" to "ui2/logo.png", "ui2_logo_en" to "ui2/logo_en.png", "ui2_logo_tr" to "ui2/logo_tr.png",
            "ui2_stars" to "ui2/stars_reward.png", "ui2_chest" to "ui2/chest.png",
            "obj_coin" to "objects/coin.png", "obj_diamond" to "objects/diamond.png", "obj_shield" to "objects/shield.png",
            "obj_pad" to "objects/pad_shift.png", "obj_gate_hidden" to "objects/gate_hidden.png",
            "gate_normal_body" to "gates/normal_body.png", "gate_normal_deco" to "gates/normal_deco.png",
            "gate_ters_body" to "gates/ters_body.png", "gate_ters_deco" to "gates/ters_deco.png",
            "gate_zaman_body" to "gates/zaman_body.png", "gate_zaman_deco" to "gates/zaman_deco.png",
            "gate_sahte_body" to "gates/sahte_body.png", "gate_sahte_deco" to "gates/sahte_deco.png",
            "gate_posts_deco" to "gates/posts_deco.png", "gate_normal_body_full" to "gates/normal_body_full.png",
        )
        /** Görseli olabilecek tüm karakterler (dosyası olmayanlar vektör çizimle kalır). */
        val SPRITE_CHARS: List<String> get() = com.yanlisrenk.logic.shop.ShopCatalog.characters.map { it.id }
        /** Desteklenen tüm diller (tr, en, de, it, fr, es) için gereken karakterler. */
        const val LATIN_EXTRA = "çÇğĞıİöÖşŞüÜâÂîÎûÛäÄëËïÏßẞàÀáÁãÃèÈéÉêÊìÌíÍòÒóÓôÔùÙúÚñÑœŒæÆÿŸ¡¿«»‹›„“”‘’·°\u00A0"
        const val CHARS = FreeTypeFontGenerator.DEFAULT_CHARS + LATIN_EXTRA + "★•→←–—…₺€"
        /** Dev başlık yazı tipi yalnızca büyük harf + rakam + noktalama içerir (bellek tasarrufu). */
        const val HUGE_CHARS = "\u0000ABCÇDEFGĞHIİJKLMNOÖPQRSŞTUÜVWXYZÄËÏẞÀÁÂÃÈÉÊÌÍÎÒÓÔÙÚÛÑŒÆŸ0123456789!?¡¿.,:;-–+×%/'’«» \u00A0"
    }

    fun load() {
        for (n in ICONS) loadTex(n, "icons/$n.png")
        for (n in UI) loadTex(n, "ui/$n.png")
        for (n in SFX) runCatching { sounds[n] = Gdx.audio.newSound(Gdx.files.internal("sfx/$n.ogg")) }
        // kullanıcı tarafından üretilen görseller (varsa)
        for (n in ART) if (Gdx.files.internal(n.second).exists()) loadTex(n.first, n.second)
        for (c in SPRITE_CHARS) if (Gdx.files.internal("chars/$c.png").exists()) loadTex("sprite_$c", "chars/$c.png")
        buildFonts()
    }

    fun has(name: String) = textures.containsKey(name)

    /** Karakterin görsel (sprite) hâli varsa döndürür. */
    fun characterSprite(id: String): TextureRegion? = regions["sprite_$id"]

    private val lazyTex = HashMap<String, Texture?>()

    /** Büyük arka planlar gerektiğinde yüklenir; [keep] dışındakiler bellekten atılır. */
    fun background(path: String): Texture? {
        lazyTex[path]?.let { return it }
        if (lazyTex.containsKey(path)) return null
        val t = if (Gdx.files.internal(path).exists()) runCatching {
            Texture(Gdx.files.internal(path)).also { it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        }.getOrNull() else null
        // en fazla 3 büyük doku tut
        if (lazyTex.size >= 3) lazyTex.entries.firstOrNull { it.key != path }?.let { e -> e.value?.dispose(); lazyTex.remove(e.key) }
        lazyTex[path] = t
        return t
    }

    /** Ölçeklenmiş dokuz-parça (yüksek çözünürlüklü görseller için). */
    fun nineScaled(name: String, l: Int, r: Int, t: Int, b: Int, scale: Float, tint: Color): NinePatchDrawable {
        val np = NinePatch(region(name), l, r, t, b)
        np.scale(scale, scale)
        np.color = tint
        val d = NinePatchDrawable(np)
        // en küçük boyut yalnızca köşeler kadar olsun (yüksek çözünürlüklü görselin orta kısmı sayılmasın)
        d.minWidth = (l + r) * scale
        d.minHeight = (t + b) * scale
        return d
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
        fontHuge = make(96, 4f, Color.WHITE, HUGE_CHARS)
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
        lazyTex.values.forEach { it?.dispose() }
        sounds.values.forEach { it.dispose() }
        music.values.forEach { it.dispose() }
        listOf(fontS, fontM, fontL, fontXL, fontHuge, fontWorld).forEach { it.dispose() }
        fontPacker?.dispose()
    }
}
