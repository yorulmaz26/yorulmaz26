package com.kayipadalar.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable

/**
 * Oyun sprite'ları: assets/atlas/game.atlas (tools/cut_sprites.py + :desktop:packSprites ile üretilir).
 * Eksik bir sprite istenirse hata kaydedilir ve boş (saydam) bölge döner; kodla şekil çizilmez.
 */
class Assets : Disposable {
    val manager = AssetManager()
    private var atlas: TextureAtlas? = null
    private val cache = HashMap<String, TextureRegion>()
    private val missing = HashSet<String>()
    private lateinit var empty: TextureRegion

    fun queue() { manager.load(ATLAS, TextureAtlas::class.java) }
    fun update(): Boolean = manager.update()
    val progress get() = manager.progress

    fun finish() {
        manager.finishLoading()
        atlas = manager.get(ATLAS, TextureAtlas::class.java)
        empty = TextureRegion(atlas!!.textures.first(), 0, 0, 1, 1)
    }

    fun has(name: String) = atlas?.findRegion(name) != null

    operator fun get(name: String): TextureRegion = cache.getOrPut(name) {
        atlas?.findRegion(name) ?: run {
            if (missing.add(name)) Gdx.app.error("Assets", "Eksik sprite: $name")
            empty
        }
    }

    val missingSprites: Set<String> get() = missing

    override fun dispose() = manager.dispose()

    companion object { const val ATLAS = "atlas/game.atlas" }
}
