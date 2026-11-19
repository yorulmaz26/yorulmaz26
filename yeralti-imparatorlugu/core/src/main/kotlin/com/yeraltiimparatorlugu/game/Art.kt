package com.yeraltiimparatorlugu.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.utils.Disposable
import com.yeraltiimparatorlugu.sim.CreatureKind
import com.yeraltiimparatorlugu.sim.ResourceType
import com.yeraltiimparatorlugu.sim.SourceKind
import com.yeraltiimparatorlugu.sim.Terrain

/**
 * Görseller `tools/art` altındaki SVG üreticisiyle çizilip tek bir atlasa (`assets/art/game.atlas`) paketlenir;
 * yazı tipi Nunito'dur (SIL OFL). Bu sınıf atlası yükler ve bölgelere adla erişim sağlar.
 */
class Art(uiScale: Float) : Disposable {
    private val atlas = TextureAtlas(Gdx.files.internal("art/game.atlas"))

    fun r(name: String): TextureRegion = atlas.findRegion(name) ?: error("Atlasta yok: $name")
    fun has(name: String) = atlas.findRegion(name) != null
    fun nine(name: String): NinePatch = atlas.createPatch(name) ?: error("9 parçalı yok: $name")

    val white = r("white")
    val tiles = HashMap<Terrain, Array<TextureRegion>>()
    val unexplored = Array(3) { r("tile_UNEXPLORED_$it") }
    val tunnel = Array(3) { r("tile_TUNNEL_$it") }
    val antWorker = Array(4) { r("ant_worker_$it") }
    val antSoldier = Array(4) { r("ant_soldier_$it") }
    val antScout = Array(4) { r("ant_scout_$it") }
    val antStriped = Array(4) { r("ant_striped_$it") }
    val queen = Array(2) { r("queen_$it") }
    val creatures = HashMap<CreatureKind, Array<TextureRegion>>()
    val icons = HashMap<ResourceType, TextureRegion>()
    val sources = HashMap<SourceKind, TextureRegion>()
    val egg = r("egg")
    val larva = r("larva")
    val pupa = r("pupa")
    val corpse = r("corpse")

    init {
        for (t in Terrain.entries) if (has("tile_${t.name}_0")) tiles[t] = Array(3) { r("tile_${t.name}_$it") }
        for (k in CreatureKind.entries) if (has("creature_${k.name}_0")) creatures[k] = Array(2) { r("creature_${k.name}_$it") }
        for (res in ResourceType.entries) icons[res] = r("res_${res.name}")
        for (s in SourceKind.entries) sources[s] = r("src_${s.name}")
        atlas.textures.forEach { it.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear) }
    }

    fun icon(name: String): TextureRegion = r("ic_$name")

    val fontSmall: BitmapFont
    val font: BitmapFont
    val fontBold: BitmapFont
    val fontBig: BitmapFont
    val fontHuge: BitmapFont

    init {
        val reg = FreeTypeFontGenerator(Gdx.files.internal("fonts/Nunito-SemiBold.ttf"))
        val bold = FreeTypeFontGenerator(Gdx.files.internal("fonts/Nunito-ExtraBold.ttf"))
        fun make(g: FreeTypeFontGenerator, px: Int): BitmapFont {
            val p = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                size = (px * uiScale).toInt().coerceAtLeast(8)
                characters = FreeTypeFontGenerator.DEFAULT_CHARS + "çÇğĞıİöÖşŞüÜâÂîÎûÛ•×…–—−’“”°½›"
                minFilter = Texture.TextureFilter.Linear
                magFilter = Texture.TextureFilter.Linear
                color = Color.WHITE
                kerning = true
            }
            return g.generateFont(p).apply { data.setScale(1f / uiScale); setUseIntegerPositions(false); data.markupEnabled = true }
        }
        fontSmall = make(reg, 21)
        font = make(reg, 25)
        fontBold = make(bold, 25)
        fontBig = make(bold, 33)
        fontHuge = make(bold, 44)
        reg.dispose()
        bold.dispose()
    }

    override fun dispose() {
        atlas.dispose()
        fontSmall.dispose(); font.dispose(); fontBold.dispose(); fontBig.dispose(); fontHuge.dispose()
    }
}
