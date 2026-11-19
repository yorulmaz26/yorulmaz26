package com.yeraltiimparatorlugu.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable
import com.yeraltiimparatorlugu.sim.SoundCue
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Ses efektleri açılışta kodla sentezlenir (harici ses dosyası yok): kısa PCM WAV'lar yerel klasöre yazılıp
 * yüklenir. Ses aygıtı yoksa (ör. sunucu ortamı) sessizce devre dışı kalır.
 */
class SoundFx(private val settings: Settings) : Disposable {
    private val sounds = HashMap<SoundCue, Sound>()
    private val lastPlayed = HashMap<SoundCue, Long>()
    private val rate = 22050

    init {
        try {
            if (Gdx.audio != null) for (cue in SoundCue.entries) {
                val f = Gdx.files.local("sfx/${cue.name.lowercase()}.wav")
                f.writeBytes(wav(synth(cue)), false)
                sounds[cue] = Gdx.audio.newSound(f)
            }
        } catch (e: Throwable) {
            Gdx.app?.log("Ses", "Ses devre dışı: ${e.message}")
            sounds.clear()
        }
    }

    fun play(cue: SoundCue) {
        if (!settings.soundOn) return
        val s = sounds[cue] ?: return
        val now = System.currentTimeMillis()
        val gap = when (cue) { SoundCue.DIG -> 220L; SoundCue.HIT -> 120L; else -> 80L }
        if (now - (lastPlayed[cue] ?: 0L) < gap) return
        lastPlayed[cue] = now
        val vol = settings.volume * when (cue) { SoundCue.DIG, SoundCue.CLICK -> 0.35f; SoundCue.HIT -> 0.5f; else -> 0.8f }
        s.play(vol)
    }

    private fun tone(out: FloatArray, start: Float, dur: Float, f0: Float, f1: Float, amp: Float, square: Boolean = false) {
        val s0 = (start * rate).toInt()
        val n = (dur * rate).toInt()
        var phase = 0.0
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= out.size) break
            val t = i.toFloat() / n
            val f = f0 + (f1 - f0) * t
            phase += 2 * PI * f / rate
            val env = minOf(1f, t * 20f) * exp(-3.0 * t).toFloat()
            val v = if (square) (if (sin(phase) >= 0) 0.6f else -0.6f) else sin(phase).toFloat()
            out[idx] += v * amp * env
        }
    }

    private fun noise(out: FloatArray, start: Float, dur: Float, amp: Float) {
        val s0 = (start * rate).toInt()
        val n = (dur * rate).toInt()
        val rnd = java.util.Random(7)
        var last = 0f
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= out.size) break
            val t = i.toFloat() / n
            last = last * 0.75f + (rnd.nextFloat() * 2f - 1f) * 0.25f // basit alçak geçiren
            out[idx] += last * amp * exp(-5.0 * t).toFloat()
        }
    }

    private fun synth(cue: SoundCue): FloatArray {
        val len = when (cue) { SoundCue.CLICK -> 0.05f; SoundCue.DIG, SoundCue.HIT -> 0.12f; SoundCue.RESEARCH, SoundCue.VICTORY -> 0.6f; else -> 0.4f }
        val o = FloatArray((len * rate).toInt())
        when (cue) {
            SoundCue.CLICK -> tone(o, 0f, 0.04f, 1300f, 1100f, 0.4f)
            SoundCue.DIG -> { noise(o, 0f, 0.1f, 0.9f); tone(o, 0f, 0.08f, 180f, 120f, 0.3f) }
            SoundCue.BUILD -> { tone(o, 0f, 0.15f, 523f, 523f, 0.5f); tone(o, 0.13f, 0.25f, 784f, 784f, 0.5f) }
            SoundCue.BIRTH -> tone(o, 0f, 0.35f, 600f, 1050f, 0.45f)
            SoundCue.ALERT -> { tone(o, 0f, 0.15f, 880f, 880f, 0.4f, square = true); tone(o, 0.2f, 0.15f, 880f, 880f, 0.4f, square = true) }
            SoundCue.HIT -> { noise(o, 0f, 0.08f, 0.8f); tone(o, 0f, 0.1f, 160f, 90f, 0.5f) }
            SoundCue.RESEARCH -> listOf(523f, 659f, 784f).forEachIndexed { i, f -> tone(o, i * 0.12f, 0.3f, f, f, 0.4f) }
            SoundCue.DEATH -> tone(o, 0f, 0.38f, 420f, 180f, 0.4f)
            SoundCue.VICTORY -> listOf(523f, 659f, 784f, 1046f).forEachIndexed { i, f -> tone(o, i * 0.11f, 0.3f, f, f, 0.4f) }
        }
        return o
    }

    private fun wav(samples: FloatArray): ByteArray {
        val data = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32000).toInt()
            data[i * 2] = (v and 0xff).toByte(); data[i * 2 + 1] = ((v shr 8) and 0xff).toByte()
        }
        val out = ByteArrayOutputStream()
        fun i32(v: Int) { out.write(v and 0xff); out.write((v shr 8) and 0xff); out.write((v shr 16) and 0xff); out.write((v shr 24) and 0xff) }
        fun i16(v: Int) { out.write(v and 0xff); out.write((v shr 8) and 0xff) }
        out.write("RIFF".toByteArray()); i32(36 + data.size); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); i32(16); i16(1); i16(1); i32(rate); i32(rate * 2); i16(2); i16(16)
        out.write("data".toByteArray()); i32(data.size); out.write(data)
        return out.toByteArray()
    }

    override fun dispose() { sounds.values.forEach { it.dispose() }; sounds.clear() }
}
