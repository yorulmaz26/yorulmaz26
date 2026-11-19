package com.yuruyensehir.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable
import com.yuruyensehir.logic.AudioSynth
import com.yuruyensehir.logic.GameState
import com.yuruyensehir.logic.SoundId

/**
 * Sesler ilk açılışta kodla sentezlenip yerel önbelleğe WAV olarak yazılır, sonra yüklenir.
 * Ses aygıtı yoksa (ör. sunucu ortamı) sessizce devre dışı kalır.
 */
class AudioManager : Disposable {
    private val sounds = HashMap<SoundId, Sound>()
    private var music: Music? = null
    var available = false
        private set
    private var lastLogSize = -1
    private var lastShots = 0
    private var hitCooldown = 0f
    private var stepTimer = 0f

    fun init() {
        try {
            val dir = Gdx.files.local("ses_onbellek_v1")
            dir.mkdirs()
            for (id in SoundId.entries) {
                val f = dir.child("${id.name.lowercase()}.wav")
                if (!f.exists()) f.writeBytes(AudioSynth.wav(AudioSynth.render(id)), false)
                sounds[id] = Gdx.audio.newSound(f)
            }
            val m = dir.child("muzik.wav")
            if (!m.exists()) m.writeBytes(AudioSynth.wav(AudioSynth.music()), false)
            music = Gdx.audio.newMusic(m).apply { isLooping = true }
            available = true
        } catch (e: Throwable) {
            Gdx.app.log("Ses", "Ses kullanılamıyor: ${e.message}")
            available = false
        }
    }

    fun play(state: GameState, id: SoundId, volume: Float = 1f) {
        if (!available || !state.settings.sound) return
        sounds[id]?.play((state.settings.soundVolume * volume).coerceIn(0f, 1f))
    }

    /** Her karede çağrılır: müziği ayarlara uydurur, günlük ve savaş olaylarına ses bağlar. */
    fun update(state: GameState, dt: Float, walking: Boolean) {
        if (!available) return
        music?.let { m ->
            if (state.settings.music) {
                m.volume = state.settings.musicVolume
                if (!m.isPlaying) m.play()
            } else if (m.isPlaying) m.pause()
        }
        if (lastLogSize < 0) lastLogSize = state.log.size
        if (state.log.size != lastLogSize) {
            val newKinds = if (state.log.size > lastLogSize) state.log.takeLast(state.log.size - lastLogSize).map { it.kind } else emptyList()
            newKinds.mapNotNull { AudioSynth.forLog(it) }.distinct().take(1).forEach { play(state, it, 0.8f) }
            lastLogSize = state.log.size
        }
        hitCooldown -= dt
        val shots = state.shots.size
        if (shots > lastShots && hitCooldown <= 0f) { play(state, SoundId.HIT, 0.5f); hitCooldown = 0.18f }
        lastShots = shots
        if (state.pendingEvent != null && pendingSeen != state.pendingEvent) { pendingSeen = state.pendingEvent; play(state, SoundId.ALERT) }
        if (walking) {
            stepTimer += dt
            if (stepTimer > 0.9f) { stepTimer = 0f; play(state, SoundId.STEP, 0.35f) }
        }
    }

    private var pendingSeen: Any? = null

    fun pause() { music?.pause() }

    override fun dispose() {
        sounds.values.forEach { it.dispose() }
        music?.dispose()
    }
}
