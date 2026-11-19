package com.kayipadalar.game.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable
import com.kayipadalar.logic.save.SettingsState

/** Ses efektleri ve müzik. Dosyalar assets/sfx altında (tools/make_audio.py ile sentezlenir). */
class AudioManager(private val settings: SettingsState) : Disposable {
    private val sounds = HashMap<String, Sound>()
    private var music: Music? = null

    init {
        for (n in listOf("tap", "slide", "rotate", "bridge", "combo", "win", "lose", "coin", "hit", "error", "unlock")) {
            val f = Gdx.files.internal("sfx/$n.wav")
            if (f.exists()) runCatching { sounds[n] = Gdx.audio.newSound(f) }
        }
        val m = Gdx.files.internal("sfx/music.wav")
        if (m.exists()) runCatching { music = Gdx.audio.newMusic(m).apply { isLooping = true; volume = 0.35f } }
    }

    fun play(name: String, volume: Float = 0.7f) {
        if (!settings.sound) return
        sounds[name]?.play(volume)
    }

    fun updateMusic() {
        val m = music ?: return
        if (settings.music && !m.isPlaying) m.play()
        if (!settings.music && m.isPlaying) m.pause()
    }

    fun pause() { music?.pause() }

    override fun dispose() {
        sounds.values.forEach { it.dispose() }
        music?.dispose()
    }
}
