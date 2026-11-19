package com.besaniyelik.dunya.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable
import com.besaniyelik.dunya.progress.Settings

enum class Sfx(val file: String, val vol: Float = 1f) {
    JUMP("jump", 0.5f), COIN("coin", 0.45f), STAR("star", 0.8f), RULE("rule", 0.8f), WARN("warn", 0.5f),
    COMPLETE("complete", 0.9f), FAIL("fail", 0.8f), CLICK("click", 0.6f), REWARD("reward", 0.8f), SPRING("spring", 0.7f),
    POWERUP("powerup", 0.7f), CHECKPOINT("checkpoint", 0.7f), LAND("land", 0.35f), DEATH("death", 0.7f), BOOST("boost", 0.6f), SHIELD("shield", 0.7f),
}

/** Ses efektleri ve müzik; ayarlardan ses ve müzik ayrı ayrı kapatılabilir. */
class AudioManager(private val settings: () -> Settings) : Disposable {
    private val sounds = HashMap<Sfx, Sound>()
    private var music: Music? = null
    private var musicName: String? = null
    private val lastPlay = HashMap<Sfx, Long>()

    init {
        for (s in Sfx.entries) {
            try { sounds[s] = Gdx.audio.newSound(Gdx.files.internal("sfx/${s.file}.ogg")) } catch (e: Exception) { Gdx.app.error("Ses", "${s.file}: ${e.message}") }
        }
    }

    fun play(s: Sfx, pitch: Float = 1f) {
        val st = settings()
        if (!st.sound) return
        val now = System.currentTimeMillis()
        if (now - (lastPlay[s] ?: 0) < 35) return // aynı efekti üst üste bindirme
        lastPlay[s] = now
        sounds[s]?.play(s.vol * st.soundVolume, pitch, 0f)
    }

    /** Harita müziği: "map_01".."map_10", "menu", "endless". */
    fun music(name: String) {
        val st = settings()
        if (musicName == name && music != null) { applyVolume(); if (st.music && music?.isPlaying == false) music?.play(); return }
        music?.stop(); music?.dispose()
        musicName = name
        music = try { Gdx.audio.newMusic(Gdx.files.internal("music/$name.ogg")).apply { isLooping = true } } catch (e: Exception) { null }
        applyVolume()
        if (st.music) music?.play()
    }

    fun applyVolume() {
        val st = settings()
        music?.volume = st.musicVolume * 0.7f
        if (!st.music) music?.pause() else if (music?.isPlaying == false) music?.play()
    }

    fun pause() { music?.pause() }
    fun resume() { if (settings().music) music?.play() }

    override fun dispose() {
        sounds.values.forEach { it.dispose() }
        music?.dispose()
    }
}
