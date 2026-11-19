package com.yeraltiimparatorlugu.sim

/** Tohuma bağlı, tekrar üretilebilir karma ve değer gürültüsü (dünya üretimi için). */
object Hash {
    fun mix(seed: Long, x: Int, y: Int, salt: Int): Long {
        var z = seed xor (x.toLong() * 0x9E3779B97F4A7C15uL.toLong()) xor (y.toLong() * -0x40a7b892e31b1a47L) xor (salt.toLong() * 0x632BE59BD9B4E019L)
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** [0, 1) aralığında deterministik sayı. */
    fun unit(seed: Long, x: Int, y: Int, salt: Int): Double = (mix(seed, x, y, salt) ushr 11).toDouble() / (1L shl 53).toDouble()
}

class ValueNoise(private val seed: Long, private val salt: Int) {
    private fun smooth(t: Double) = t * t * (3 - 2 * t)

    fun sample(x: Double, y: Double): Double {
        val x0 = kotlin.math.floor(x).toInt()
        val y0 = kotlin.math.floor(y).toInt()
        val tx = smooth(x - x0)
        val ty = smooth(y - y0)
        val a = Hash.unit(seed, x0, y0, salt)
        val b = Hash.unit(seed, x0 + 1, y0, salt)
        val c = Hash.unit(seed, x0, y0 + 1, salt)
        val d = Hash.unit(seed, x0 + 1, y0 + 1, salt)
        val top = a + (b - a) * tx
        val bottom = c + (d - c) * tx
        return top + (bottom - top) * ty
    }

    /** Fraktal toplam; sonuç yaklaşık [0, 1]. */
    fun fbm(x: Double, y: Double, octaves: Int = 3): Double {
        var sum = 0.0
        var amp = 1.0
        var freq = 1.0
        var norm = 0.0
        repeat(octaves) {
            sum += sample(x * freq + it * 17.3, y * freq + it * 9.1) * amp
            norm += amp
            amp *= 0.5
            freq *= 2.0
        }
        return sum / norm
    }
}
