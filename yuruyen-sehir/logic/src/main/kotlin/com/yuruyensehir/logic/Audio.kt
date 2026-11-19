package com.yuruyensehir.logic

import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Oyundaki sesler. Hepsi kodla sentezlenir; harici ses dosyası gerekmez. */
enum class SoundId { CLICK, BUILD, COMPLETE, COIN, ALERT, HIT, VICTORY, LEVEL_UP, STEP, ERROR }

/**
 * Basit sentezleyici: sinüs/üçgen dalgalar, gürültü ve zarf ile 16 bit mono WAV üretir.
 * Render katmanından bağımsızdır ve test edilebilir.
 */
object AudioSynth {
    const val RATE = 22050

    private fun env(t: Double, dur: Double, attack: Double = 0.01, release: Double = 0.6): Double {
        val a = (t / attack).coerceAtMost(1.0)
        val r = exp(-t / (dur * release))
        return a * r
    }

    private fun tri(p: Double): Double { val x = (p / (2 * PI)) % 1.0; return 4 * kotlin.math.abs(x - 0.5) - 1 }

    fun tone(out: FloatArray, start: Double, freq: Double, dur: Double, vol: Double, triangle: Boolean = false, slide: Double = 0.0) {
        val s0 = (start * RATE).toInt()
        val n = (dur * RATE).toInt()
        var phase = 0.0
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= out.size) break
            val t = i.toDouble() / RATE
            val f = freq + slide * t / dur
            phase += 2 * PI * f / RATE
            val w = if (triangle) tri(phase) else sin(phase)
            out[idx] += (w * vol * env(t, dur)).toFloat()
        }
    }

    fun noise(out: FloatArray, start: Double, dur: Double, vol: Double, seed: Int = 1) {
        var x = seed * 1103515245 + 12345
        val s0 = (start * RATE).toInt()
        val n = (dur * RATE).toInt()
        var lp = 0.0
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= out.size) break
            x = x * 1103515245 + 12345
            val r = ((x ushr 16) and 0x7fff) / 16384.0 - 1.0
            lp += (r - lp) * 0.25
            out[idx] += (lp * vol * env(i.toDouble() / RATE, dur, 0.002, 0.4)).toFloat()
        }
    }

    fun render(id: SoundId): FloatArray {
        val dur = when (id) { SoundId.VICTORY, SoundId.LEVEL_UP -> 1.0; SoundId.ALERT -> 0.6; SoundId.COMPLETE -> 0.5; else -> 0.25 }
        val o = FloatArray((dur * RATE).toInt())
        when (id) {
            SoundId.CLICK -> tone(o, 0.0, 880.0, 0.06, 0.25)
            SoundId.BUILD -> { noise(o, 0.0, 0.12, 0.35); tone(o, 0.0, 180.0, 0.15, 0.3, triangle = true) }
            SoundId.COMPLETE -> { tone(o, 0.0, 523.25, 0.18, 0.3); tone(o, 0.12, 659.25, 0.18, 0.3); tone(o, 0.24, 783.99, 0.25, 0.3) }
            SoundId.COIN -> { tone(o, 0.0, 987.77, 0.08, 0.25); tone(o, 0.07, 1318.5, 0.16, 0.25) }
            SoundId.ALERT -> { tone(o, 0.0, 440.0, 0.25, 0.3, triangle = true); tone(o, 0.28, 349.23, 0.3, 0.3, triangle = true) }
            SoundId.HIT -> { noise(o, 0.0, 0.08, 0.4, 7); tone(o, 0.0, 220.0, 0.1, 0.2, slide = -120.0) }
            SoundId.VICTORY -> listOf(523.25, 659.25, 783.99, 1046.5).forEachIndexed { i, f -> tone(o, i * 0.15, f, 0.4, 0.25, triangle = true) }
            SoundId.LEVEL_UP -> listOf(392.0, 523.25, 659.25, 783.99).forEachIndexed { i, f -> tone(o, i * 0.1, f, 0.5, 0.22) }
            SoundId.STEP -> { noise(o, 0.0, 0.1, 0.25, 3); tone(o, 0.0, 70.0, 0.12, 0.35) }
            SoundId.ERROR -> tone(o, 0.0, 200.0, 0.2, 0.3, triangle = true, slide = -60.0)
        }
        return o
    }

    /** Sakin, pentatonik bir döngü müziği (yaklaşık 19 sn, uçtan uca dikişsiz). */
    fun music(): FloatArray {
        val bpm = 100.0
        val beat = 60.0 / bpm
        val bars = 8
        val total = bars * 4 * beat
        val o = FloatArray((total * RATE).toInt())
        val scale = listOf(261.63, 293.66, 329.63, 392.0, 440.0, 523.25, 587.33, 659.25)
        val chords = listOf(listOf(130.81, 196.0), listOf(110.0, 164.81), listOf(146.83, 220.0), listOf(98.0, 146.83))
        var seed = 7
        fun rnd(n: Int): Int { seed = seed * 1103515245 + 12345; return ((seed ushr 16) and 0x7fff) % n }
        for (bar in 0 until bars) {
            val ch = chords[bar % chords.size]
            for (f in ch) tone(o, bar * 4 * beat, f, 4 * beat * 0.98, 0.08, triangle = true)
            var pos = 0.0
            var note = 2 + rnd(3)
            while (pos < 4 * beat - 1e-6) {
                val len = if (rnd(4) == 0) beat else beat / 2
                note = (note + rnd(3) - 1).coerceIn(0, scale.lastIndex)
                if (rnd(6) != 0) tone(o, bar * 4 * beat + pos, scale[note], len * 1.6, 0.09)
                pos += len
            }
        }
        // Döngü noktasında tıkırtı olmasın: kısa yumuşatma
        val fade = (0.05 * RATE).toInt()
        for (i in 0 until fade) { val k = i.toFloat() / fade; o[i] *= k; o[o.size - 1 - i] *= k }
        return o
    }

    fun wav(samples: FloatArray): ByteArray {
        var peak = 0f
        for (v in samples) peak = maxOf(peak, kotlin.math.abs(v))
        val gain = if (peak > 0.95f) 0.95f / peak else 1f
        val data = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i] * gain * 32767).toInt().coerceIn(-32768, 32767)
            data[i * 2] = (v and 0xff).toByte(); data[i * 2 + 1] = ((v shr 8) and 0xff).toByte()
        }
        val out = ByteArrayOutputStream(44 + data.size)
        fun i32(v: Int) { out.write(v and 0xff); out.write((v shr 8) and 0xff); out.write((v shr 16) and 0xff); out.write((v shr 24) and 0xff) }
        fun i16(v: Int) { out.write(v and 0xff); out.write((v shr 8) and 0xff) }
        out.write("RIFF".toByteArray()); i32(36 + data.size); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); i32(16); i16(1); i16(1); i32(RATE); i32(RATE * 2); i16(2); i16(16)
        out.write("data".toByteArray()); i32(data.size); out.write(data)
        return out.toByteArray()
    }

    /** Günlük kaydına göre çalınacak ses (sesler oyun olaylarına mantığa dokunmadan bağlanır). */
    fun forLog(kind: LogKind): SoundId? = when (kind) {
        LogKind.BUILD -> SoundId.COMPLETE
        LogKind.STAGE -> SoundId.LEVEL_UP
        LogKind.BATTLE -> SoundId.VICTORY
        LogKind.WARNING -> SoundId.ALERT
        LogKind.DIPLOMACY -> SoundId.COIN
        LogKind.RESEARCH -> SoundId.LEVEL_UP
        LogKind.CARRIER, LogKind.EXPLORATION, LogKind.TRAVEL -> SoundId.COMPLETE
        else -> null
    }
}
