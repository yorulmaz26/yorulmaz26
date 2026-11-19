package com.yanlisrenk.game

import com.badlogic.gdx.audio.Music
import com.yanlisrenk.logic.save.Settings

/** Ses yöneticisi: müzik ve efekt ses düzeyleri ayrı ayrı kontrol edilir. Müzik geçişleri yumuşaktır. */
class Audio(private val assets: Assets, private val settings: () -> Settings) {
    private var current: Music? = null
    private var currentName = ""
    private var fadingOut: Music? = null
    private var fade = 0f

    fun play(name: String, pitch: Float = 1f, volume: Float = 1f) {
        val v = settings().sfxVolume * volume
        if (v <= 0.001f) return
        assets.sounds[name]?.play(v, pitch, 0f)
    }

    fun music(name: String) {
        if (name == currentName) { applyVolume(); return }
        fadingOut?.stop()
        fadingOut = current
        fade = 1f
        currentName = name
        current = assets.music[name]?.also { it.volume = 0f; if (settings().musicVolume > 0.001f) it.play() }
    }

    fun update(dt: Float) {
        val target = settings().musicVolume
        current?.let {
            if (target <= 0.001f) { if (it.isPlaying) it.pause() } else {
                if (!it.isPlaying) it.play()
                it.volume = minOf(target, it.volume + dt * target * 1.5f)
            }
        }
        fadingOut?.let {
            fade -= dt * 1.5f
            if (fade <= 0f) { it.stop(); fadingOut = null } else it.volume = target * fade
        }
    }

    fun applyVolume() { current?.volume = settings().musicVolume }
    fun pause() { current?.pause(); fadingOut?.stop(); fadingOut = null }
    fun resume() { if (settings().musicVolume > 0.001f) current?.play() }
}
