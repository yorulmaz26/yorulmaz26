package com.zamaninefendisi.game.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable

/** Kısa ses efektleri (assets/sounds klasöründeki wav dosyaları). Ayarlardan kapatılabilir. */
class AudioManager(private val enabled: () -> Boolean) : Disposable {
    private val sounds = HashMap<String, Sound>()
    private val names = listOf("click", "move", "build", "switch", "win", "lose", "hit", "paradox", "grow")

    fun load() {
        for (n in names) {
            val f = Gdx.files.internal("sounds/$n.wav")
            if (f.exists()) try { sounds[n] = Gdx.audio.newSound(f) } catch (e: Exception) { Gdx.app.error("Ses", "$n yüklenemedi", e) }
        }
    }

    fun play(name: String, volume: Float = 0.6f, pitch: Float = 1f) {
        if (!enabled()) return
        sounds[name]?.play(volume, pitch, 0f)
    }

    override fun dispose() { sounds.values.forEach { it.dispose() }; sounds.clear() }
}
