package com.example.gezegenkacisi

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Oyunun tüm sesleri. Hiçbir ses dosyası kullanılmaz: efektler ve müzik açılışta kodla sentezlenir.
 *  - Efektler WAV olarak önbelleğe yazılıp SoundPool ile çalınır (düşük gecikme).
 *  - Müzik her harita için ayrı bir döngü olarak üretilir ve AudioTrack ile sonsuz döngüde çalınır.
 * Ağır işler arka plan iş parçacığında yapılır, oyun döngüsü beklemez.
 */
class SoundManager(context: Context) {

    enum class Sfx { FLAP, SCORE, CRASH, CLICK, SWIPE, START, RECORD, GAME_OVER, FIRE, COIN }

    private companion object {
        const val RATE = 22050
        const val TAU = (2.0 * PI).toFloat()
        const val MUSIC_VOLUME = 0.42f
        fun midiHz(n: Int): Float = 440f * 2f.pow((n - 69) / 12f)
    }

    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    private val lock = Any()

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val sampleIds = IntArray(Sfx.entries.size)
    private val loadedIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val volumes = floatArrayOf(0.55f, 0.7f, 1f, 0.6f, 0.5f, 0.8f, 0.9f, 0.85f, 0.6f, 0.65f)

    private var music: AudioTrack? = null
    private var musicTheme = -1
    private var wantedTheme = 0
    private var foreground = false
    private var lastFlapMs = 0L

    @Volatile
    var enabled = true
        set(value) {
            field = value
            updateMusicPlayback()
        }

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedIds.add(sampleId)
        }
        worker.execute { loadEffects() }
    }

    // ---------------------------------------------------------------------
    // Genel arayüz
    // ---------------------------------------------------------------------

    fun play(sfx: Sfx) {
        val id = sampleIds[sfx.ordinal]
        if (!enabled || id == 0 || id !in loadedIds) return
        var rate = 1f
        if (sfx == Sfx.FLAP) {
            // Art arda dokunuşlarda ses tekdüze olmasın
            val now = System.currentTimeMillis()
            if (now - lastFlapMs < 45) return
            lastFlapMs = now
            rate = 0.92f + Random.nextFloat() * 0.16f
        }
        val v = volumes[sfx.ordinal]
        pool.play(id, v, v, 1, 0, rate)
    }

    /** Haritanın müziğini seçer; gerekiyorsa arka planda üretir. */
    fun setMusicTheme(theme: Int) {
        synchronized(lock) {
            wantedTheme = theme
            if (theme == musicTheme && music != null) return
        }
        worker.execute { buildMusic(theme) }
    }

    fun onResume() {
        synchronized(lock) { foreground = true }
        updateMusicPlayback()
    }

    fun onPause() {
        synchronized(lock) { foreground = false }
        updateMusicPlayback()
    }

    fun release() {
        synchronized(lock) {
            foreground = false
            music?.let { runCatching { it.stop() }; it.release() }
            music = null
            musicTheme = -1
        }
        worker.shutdownNow()
        pool.release()
    }

    private fun updateMusicPlayback() {
        synchronized(lock) {
            val track = music ?: return
            runCatching {
                if (enabled && foreground) {
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
                } else if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Efektler
    // ---------------------------------------------------------------------

    private fun loadEffects() {
        val dir = File(appContext.cacheDir, "sfx").apply { mkdirs() }
        for (sfx in Sfx.entries) {
            val data = when (sfx) {
                Sfx.FLAP -> synthFlap()
                Sfx.SCORE -> synthScore()
                Sfx.CRASH -> synthCrash()
                Sfx.CLICK -> synthClick()
                Sfx.SWIPE -> synthSwipe()
                Sfx.START -> synthArp(intArrayOf(72, 76, 79, 84), 0.07f, 0.35f, 0.5f)
                Sfx.RECORD -> synthArp(intArrayOf(72, 76, 79, 84, 88, 91), 0.09f, 0.6f, 0.55f)
                Sfx.GAME_OVER -> synthArp(intArrayOf(67, 63, 60, 55), 0.13f, 0.45f, 0.5f)
                Sfx.FIRE -> synthFire()
                Sfx.COIN -> synthCoin()
            }
            val file = File(dir, "${sfx.name.lowercase()}.wav")
            runCatching {
                writeWav(file, toPcm(data))
                sampleIds[sfx.ordinal] = pool.load(file.absolutePath, 1)
            }
        }
    }

    private fun buf(seconds: Float) = FloatArray((seconds * RATE).toInt())

    /** Roket itişi: kısa, alçak frekanslı "fışşş". */
    private fun synthFlap(): FloatArray {
        val out = buf(0.2f)
        var lp = 0f
        var phase = 0f
        for (i in out.indices) {
            val t = i / RATE.toFloat()
            val env = min(1f, t / 0.012f) * exp(-t * 16f)
            val noise = Random.nextFloat() * 2f - 1f
            val k = 0.08f + 0.25f * exp(-t * 20f)
            lp += (noise - lp) * k
            phase += TAU * (170f - 300f * t) / RATE
            out[i] = (lp * 1.6f + sin(phase) * 0.45f) * env
        }
        return out
    }

    /** Puan: iki notalı parlak çan. */
    private fun synthScore(): FloatArray {
        val out = buf(0.42f)
        addBell(out, 0f, midiHz(83), 0.4f, 9f)
        addBell(out, 0.075f, midiHz(88), 0.45f, 7f)
        return out
    }

    /** Patlama: kararan gürültü ve derin bir gümbürtü. */
    private fun synthCrash(): FloatArray {
        val out = buf(1.2f)
        var lp = 0f
        var lp2 = 0f
        var phase = 0f
        for (i in out.indices) {
            val t = i / RATE.toFloat()
            val env = min(1f, t / 0.004f) * exp(-t * 3.6f)
            val noise = Random.nextFloat() * 2f - 1f
            val k = 0.04f + 0.5f * exp(-t * 9f)
            lp += (noise - lp) * k
            lp2 += (lp - lp2) * 0.3f
            phase += TAU * (95f * exp(-t * 2.5f) + 30f) / RATE
            val boom = sin(phase) * exp(-t * 5f)
            out[i] = (lp2 * 2.2f + boom * 0.9f) * env
        }
        return out
    }

    /** Para: kısa, parlak iki nota. */
    private fun synthCoin(): FloatArray {
        val out = buf(0.3f)
        addBell(out, 0f, midiHz(88), 0.45f, 16f, triangle = true)
        addBell(out, 0.06f, midiHz(95), 0.5f, 11f, triangle = true)
        return out
    }

    /** Ateş topu fırlaması: yükselen hışırtılı "fuu". */
    private fun synthFire(): FloatArray {
        val out = buf(0.5f)
        var lp = 0f
        var phase = 0f
        for (i in out.indices) {
            val t = i / RATE.toFloat()
            val env = min(1f, t / 0.05f) * exp(-t * 5f)
            val noise = Random.nextFloat() * 2f - 1f
            lp += (noise - lp) * (0.05f + 0.3f * t)
            phase += TAU * (90f + 260f * t) / RATE
            out[i] = (lp * 1.8f + sin(phase) * 0.35f) * env
        }
        return out
    }

    private fun synthClick(): FloatArray {
        val out = buf(0.06f)
        var phase = 0f
        for (i in out.indices) {
            val t = i / RATE.toFloat()
            phase += TAU * 1250f / RATE
            out[i] = (if (sin(phase) > 0f) 0.5f else -0.5f) * exp(-t * 70f)
        }
        return out
    }

    private fun synthSwipe(): FloatArray {
        val out = buf(0.16f)
        var phase = 0f
        for (i in out.indices) {
            val t = i / RATE.toFloat()
            phase += TAU * (420f + 2600f * t) / RATE
            out[i] = sin(phase) * min(1f, t / 0.01f) * exp(-t * 18f) * 0.7f
        }
        return out
    }

    /** Ardışık notalar (başlangıç, rekor, oyun bitti). */
    private fun synthArp(notes: IntArray, step: Float, tail: Float, level: Float): FloatArray {
        val out = buf(step * notes.size + tail)
        notes.forEachIndexed { i, n ->
            val last = i == notes.size - 1
            addBell(out, i * step, midiHz(n), level, if (last) 4.5f else 10f, triangle = true)
        }
        return out
    }

    private fun addBell(
        out: FloatArray, start: Float, hz: Float, level: Float, decay: Float, triangle: Boolean = false
    ) {
        val s0 = (start * RATE).toInt()
        var phase = 0f
        for (i in s0 until out.size) {
            val t = (i - s0) / RATE.toFloat()
            val env = min(1f, t / 0.004f) * exp(-t * decay)
            if (env < 0.0005f && t > 0.05f) break
            phase += TAU * hz / RATE
            if (phase > TAU) phase -= TAU
            val v = if (triangle) tri(phase) * 0.8f + sin(phase * 2f) * 0.15f
            else sin(phase) + sin(phase * 2.01f) * 0.25f + sin(phase * 3.02f) * 0.08f
            out[i] += v * env * level
        }
    }

    // ---------------------------------------------------------------------
    // Müzik
    // ---------------------------------------------------------------------

    /**
     * Tema: akor dizisi (her akor bir ölçü), tempo, çalgılar ve ritim.
     * [arpWave] 0 = sinüs (yumuşak), 1 = üçgen (çan), 2 = kare (sert, sentez)
     * [bass] 0 = yok, 1 = dörtlük, 2 = sekizlik (sürükleyici), 3 = senkoplu
     * [drums] 0 = yok, 1 = hafif zil, 2 = davul + zil, 3 = davul + trampet + zil
     */
    private class Theme(
        val bpm: Float,
        val chords: Array<IntArray>,
        val arp: IntArray,
        val arpLevel: Float,
        val arpOct: Int,
        val arpWave: Int,
        val arpDecay: Float,
        val arpSteps: Int,
        val padLevel: Float,
        val padTremolo: Float,
        val bass: Int,
        val drums: Int
    )

    private val themes = arrayOf(
        // Dünya Yörüngesi: Am - F - C - G, orta tempo, sakin ve umutlu
        Theme(
            96f, arrayOf(intArrayOf(57, 60, 64), intArrayOf(53, 57, 60), intArrayOf(48, 55, 64), intArrayOf(55, 59, 62)),
            intArrayOf(0, 1, 2, 1, 2, 3, 2, 1), 0.16f, 12, 0, 7f, 8, 0.09f, 0f, 1, 1
        ),
        // Buz Kuşağı: Dmaj7 - Bm7 - Gmaj7 - A, çok yavaş, yüksekten çan sesleri, davulsuz
        Theme(
            66f, arrayOf(intArrayOf(50, 57, 61, 66), intArrayOf(47, 54, 57, 62), intArrayOf(43, 54, 59, 62), intArrayOf(45, 52, 57, 61)),
            intArrayOf(3, 1, 2, 0, 3, 2, 1, 2), 0.2f, 24, 1, 2.2f, 8, 0.07f, 0f, 0, 0
        ),
        // Lav Denizi: Em - C - D - B, hızlı, sert sentez, davul ve sekizlik bas
        Theme(
            136f, arrayOf(intArrayOf(52, 55, 59), intArrayOf(48, 52, 55), intArrayOf(50, 54, 57), intArrayOf(47, 51, 54)),
            intArrayOf(0, 0, 2, 0, 1, 0, 2, 1), 0.12f, 12, 2, 12f, 8, 0.03f, 0f, 2, 3
        ),
        // Gaz Devleri: F - G - Em - Am, yavaş, dalgalanan geniş pad, seyrek notalar
        Theme(
            78f, arrayOf(intArrayOf(53, 57, 60, 64), intArrayOf(55, 59, 62, 66), intArrayOf(52, 55, 59, 62), intArrayOf(57, 60, 64, 67)),
            intArrayOf(0, 2, 1, 3, 2, 1, 3, 2), 0.12f, 12, 0, 3f, 4, 0.13f, 5f, 1, 0
        ),
        // Asteroit Kuşağı: Dm - Bb - F - C, ritmik, üçgen dalga, senkoplu bas ve davul
        Theme(
            112f, arrayOf(intArrayOf(50, 53, 57), intArrayOf(46, 50, 53), intArrayOf(53, 57, 60), intArrayOf(48, 52, 55)),
            intArrayOf(0, 1, 2, 0, 1, 2, 1, 2), 0.14f, 12, 1, 9f, 8, 0.05f, 0f, 3, 2
        ),
        // Kara Delik: Cm - Ab - Fm - G, ağır ve derin, yavaş vuruşlu
        Theme(
            70f, arrayOf(intArrayOf(48, 51, 55), intArrayOf(44, 48, 51), intArrayOf(41, 44, 48), intArrayOf(43, 47, 50)),
            intArrayOf(0, 2, 1, 2, 0, 2, 1, 2), 0.1f, 12, 1, 4f, 4, 0.12f, 2f, 1, 2
        ),
        // Elektrik Fırtınası: Am - G - F - E, hızlı, sert sentez, tam davul
        Theme(
            144f, arrayOf(intArrayOf(57, 60, 64), intArrayOf(55, 59, 62), intArrayOf(53, 57, 60), intArrayOf(52, 56, 59)),
            intArrayOf(0, 2, 1, 2, 0, 2, 1, 0), 0.11f, 12, 2, 14f, 8, 0.03f, 0f, 2, 3
        ),
        // Meteor Yağmuru: Gm - Eb - Bb - F, orta tempo, üçgen dalga
        Theme(
            120f, arrayOf(intArrayOf(55, 58, 62), intArrayOf(51, 55, 58), intArrayOf(46, 50, 53), intArrayOf(53, 57, 60)),
            intArrayOf(2, 1, 0, 1, 2, 1, 0, 2), 0.13f, 12, 1, 10f, 8, 0.05f, 0f, 3, 3
        ),
        // Zehirli Bulutsu: Em(add9) - F#dim - Em - D#aug, tuhaf ve dalgalı
        Theme(
            92f, arrayOf(intArrayOf(52, 55, 59, 66), intArrayOf(54, 57, 60), intArrayOf(52, 55, 59), intArrayOf(51, 55, 59)),
            intArrayOf(0, 3, 1, 2, 3, 0, 2, 1), 0.12f, 12, 0, 6f, 8, 0.08f, 6f, 3, 1
        ),
        // Galaksi Merkezi: C - G - Am - F, destansı final, geniş pad ve davul
        Theme(
            100f, arrayOf(intArrayOf(48, 55, 60, 64), intArrayOf(43, 55, 59, 62), intArrayOf(45, 52, 57, 60), intArrayOf(41, 53, 57, 60)),
            intArrayOf(0, 1, 2, 3, 2, 1, 2, 3), 0.13f, 24, 1, 5f, 8, 0.1f, 0f, 1, 2
        )
    )

    private fun buildMusic(theme: Int) {
        synchronized(lock) { if (theme != wantedTheme) return }
        val pcm = toPcm(synthMusic(themes[theme.coerceIn(0, themes.size - 1)]))
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
                .also {
                    it.write(pcm, 0, pcm.size)
                    it.setLoopPoints(0, pcm.size, -1)
                    it.setVolume(MUSIC_VOLUME)
                }
        }.getOrNull() ?: return

        synchronized(lock) {
            if (theme != wantedTheme || worker.isShutdown) {
                track.release()
                return
            }
            music?.let { runCatching { it.stop() }; it.release() }
            music = track
            musicTheme = theme
        }
        updateMusicPlayback()
    }

    /** Dört ölçülük kesintisiz döngü: pad, bas, arpej ve temaya göre davul. */
    private fun synthMusic(th: Theme): FloatArray {
        val beat = 60f / th.bpm
        val bar = beat * 4f
        val total = (bar * th.chords.size * RATE).toInt()
        val out = FloatArray(total)

        th.chords.forEachIndexed { ci, chord ->
            val barStart = ci * bar
            // Pad: her akor notası iki hafif farklı akortlu osilatörle
            if (th.padLevel > 0f) for (n in chord) {
                addTone(
                    out, barStart, bar + 0.6f, midiHz(n), th.padLevel,
                    attack = 0.5f, release = 0.6f, detune = true, tremolo = th.padTremolo
                )
            }
            // Bas
            val root = midiHz(chord[0] - 12)
            when (th.bass) {
                1 -> for (b in 0 until 4) addPluck(out, barStart + b * beat, root, 0.22f, 5f, beat * 0.95f, 0)
                2 -> for (e in 0 until 8) {
                    val hz = if (e % 4 == 3) midiHz(chord[0]) else root
                    addPluck(out, barStart + e * beat / 2f, hz, 0.2f, 9f, beat * 0.45f, 2)
                }
                3 -> for (pos in floatArrayOf(0f, 0.75f, 1.5f, 2.5f, 3f)) {
                    addPluck(out, barStart + pos * beat, root, 0.24f, 7f, beat * 0.6f, 1)
                }
            }
            // Arpej
            val step = 4f / th.arpSteps
            for (k in 0 until th.arpSteps) {
                val idx = th.arp[k % th.arp.size] % chord.size
                addPluck(
                    out, barStart + k * step * beat, midiHz(chord[idx] + th.arpOct), th.arpLevel,
                    th.arpDecay, beat * step * 0.95f + (if (th.arpDecay < 4f) beat else 0f), th.arpWave
                )
            }
            // Ritim
            if (th.drums >= 1) for (b in 0 until 4) addHat(out, barStart + b * beat + beat / 2f, 0.05f)
            if (th.drums >= 2) {
                val kicks = if (th.drums == 3) intArrayOf(0, 1, 2, 3) else intArrayOf(0, 2)
                for (b in kicks) addKick(out, barStart + b * beat, 0.5f)
                if (th.drums == 2) addKick(out, barStart + 2.5f * beat, 0.35f)
            }
            if (th.drums >= 3) for (b in intArrayOf(1, 3)) addSnare(out, barStart + b * beat, 0.22f)
        }
        return out
    }

    private fun addKick(out: FloatArray, start: Float, level: Float) {
        val n = out.size
        val s0 = (start * RATE).toInt()
        var phase = 0f
        for (i in 0 until (0.3f * RATE).toInt()) {
            val t = i / RATE.toFloat()
            phase += TAU * (45f + 110f * exp(-t * 30f)) / RATE
            out[(s0 + i) % n] += sin(phase) * exp(-t * 11f) * level
        }
    }

    private fun addSnare(out: FloatArray, start: Float, level: Float) {
        val n = out.size
        val s0 = (start * RATE).toInt()
        var phase = 0f
        var lp = 0f
        for (i in 0 until (0.2f * RATE).toInt()) {
            val t = i / RATE.toFloat()
            phase += TAU * 190f / RATE
            val noise = Random.nextFloat() * 2f - 1f
            lp += (noise - lp) * 0.5f
            out[(s0 + i) % n] += (lp * exp(-t * 18f) + sin(phase) * 0.4f * exp(-t * 30f)) * level
        }
    }

    /** Döngü sonundan taşan örnekler başa sarılır, böylece döngü dikişsiz olur. */
    private fun addTone(
        out: FloatArray, start: Float, dur: Float, hz: Float, level: Float,
        attack: Float, release: Float, detune: Boolean, tremolo: Float = 0f
    ) {
        val n = out.size
        val s0 = (start * RATE).toInt()
        val len = (dur * RATE).toInt()
        var p1 = 0f
        var p2 = 0f
        val d1 = TAU * hz / RATE
        val d2 = TAU * hz * (if (detune) 1.006f else 1f) / RATE
        for (i in 0 until len) {
            val t = i / RATE.toFloat()
            var env = min(1f, t / attack) * min(1f, (dur - t) / release).coerceAtLeast(0f)
            if (tremolo > 0f) env *= 0.7f + 0.3f * sin(TAU * tremolo * (start + t) / 4f)
            p1 += d1; if (p1 > TAU) p1 -= TAU
            p2 += d2; if (p2 > TAU) p2 -= TAU
            val v = tri(p1) * 0.6f + sin(p2) * 0.4f
            out[(s0 + i) % n] += v * env * level
        }
    }

    private fun addPluck(
        out: FloatArray, start: Float, hz: Float, level: Float, decay: Float, dur: Float, wave: Int = 0
    ) {
        val n = out.size
        val s0 = (start * RATE).toInt()
        val len = (dur * RATE).toInt()
        var phase = 0f
        val d = TAU * hz / RATE
        for (i in 0 until len) {
            val t = i / RATE.toFloat()
            val env = min(1f, t / 0.006f) * exp(-t * decay) * min(1f, (dur - t) / 0.03f).coerceAtLeast(0f)
            phase += d; if (phase > TAU) phase -= TAU
            val v = when (wave) {
                1 -> tri(phase) + sin(phase * 3f) * 0.1f
                2 -> sin(phase) + sin(phase * 3f) / 3f + sin(phase * 5f) / 5f + sin(phase * 7f) / 7f
                else -> sin(phase) + sin(phase * 2f) * 0.2f
            }
            out[(s0 + i) % n] += v * env * level
        }
    }

    private fun addHat(out: FloatArray, start: Float, level: Float) {
        val n = out.size
        val s0 = (start * RATE).toInt()
        var prev = 0f
        for (i in 0 until (0.06f * RATE).toInt()) {
            val t = i / RATE.toFloat()
            val noise = Random.nextFloat() * 2f - 1f
            val hp = noise - prev // basit yüksek geçiren süzgeç: tiz hışırtı
            prev = noise
            out[(s0 + i) % n] += hp * exp(-t * 60f) * level
        }
    }

    // ---------------------------------------------------------------------
    // Yardımcılar
    // ---------------------------------------------------------------------

    /** Üçgen dalga, faz 0..2π. */
    private fun tri(phase: Float): Float {
        val x = phase / TAU
        return 4f * abs(x - (x + 0.5f).toInt().toFloat()) - 1f
    }

    /** Tepe değerine göre normalleştirip 16 bit'e çevirir. */
    private fun toPcm(data: FloatArray): ShortArray {
        var peak = 0.0001f
        for (v in data) peak = max(peak, abs(v))
        val g = 0.85f / peak
        return ShortArray(data.size) { (data[it] * g * 32767f).toInt().coerceIn(-32768, 32767).toShort() }
    }

    private fun writeWav(file: File, pcm: ShortArray) {
        val dataBytes = pcm.size * 2
        val bb = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        bb.put("data".toByteArray()).putInt(dataBytes)
        for (s in pcm) bb.putShort(s)
        FileOutputStream(file).use { it.write(bb.array()) }
    }
}
