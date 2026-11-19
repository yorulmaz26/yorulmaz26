package com.yuruyensehir.logic

import com.yuruyensehir.logic.Resource.*
import kotlinx.serialization.Serializable
import kotlin.math.floor

/**
 * Biyomlar. [moveFactor]: taşıyıcı hız çarpanı, [danger]: olay ve hasar riski (0-1),
 * [forage]: dinlenirken taşıyıcının kendini besleme oranı, [waterAvail]: su bulunabilirliği,
 * [roughness]: dengesiz yükün yarattığı risk çarpanı, [yields]: toplayıcıların topladığı kaynak oranları.
 */
@Serializable
enum class Biome(
    val title: String,
    val moveFactor: Double,
    val danger: Double,
    val forage: Double,
    val waterAvail: Double,
    val roughness: Double,
    val yields: Amounts,
    val hint: String,
) {
    PLAINS("Yeşil Ova", 1.0, 0.05, 1.0, 0.8, 0.5, amounts(FOOD to 0.35, WOOD to 0.35, STONE to 0.3), "Güvenli ve bereketli."),
    FOREST("Orman", 0.8, 0.25, 0.8, 0.7, 0.8, amounts(WOOD to 0.7, FOOD to 0.3), "Odun ve yiyecek bol; yırtıcılar dolaşır."),
    DESERT("Çöl", 0.85, 0.3, 0.05, 0.1, 0.6, amounts(STONE to 0.55, RARE_ORE to 0.1, METAL to 0.35), "Su çok az; nadir maden bulunur."),
    MOUNTAINS("Dağlar", 0.5, 0.35, 0.2, 0.5, 1.8, amounts(STONE to 0.55, METAL to 0.4, RARE_ORE to 0.05), "Mineral bol; ağır şehirler zorlanır."),
    SWAMP("Bataklık", 0.55, 0.3, 0.5, 1.0, 1.2, amounts(WATER to 0.5, WOOD to 0.35, CARE_ESSENCE to 0.15), "Yavaş ve nemli; şifalı otlar yetişir."),
    SNOW("Kar Bölgesi", 0.6, 0.35, 0.1, 0.6, 1.0, amounts(WATER to 0.6, CRYSTAL to 0.1, STONE to 0.3), "Soğuk taşıyıcıyı yorar."),
    VOLCANIC("Volkanik Alan", 0.65, 0.55, 0.0, 0.1, 1.4, amounts(METAL to 0.55, RARE_ORE to 0.25, STONE to 0.2), "Zengin cevher, yüksek tehlike."),
    CRYSTAL_VALLEY("Kristal Vadisi", 0.8, 0.3, 0.3, 0.5, 0.9, amounts(CRYSTAL to 0.5, STONE to 0.4, ENERGY to 0.1), "Parlayan kristaller."),
    RUINS("Eski Medeniyet Kalıntıları", 0.75, 0.4, 0.2, 0.3, 1.0, amounts(ANCIENT_PART to 0.25, TECH_PART to 0.1, METAL to 0.4, STONE to 0.25), "Eski çağın sırları."),
    LAKE("Büyük Göl Kıyısı", 0.9, 0.1, 0.9, 1.0, 0.5, amounts(WATER to 0.6, FOOD to 0.4), "Bol su ve balık."),
    STORMLANDS("Fırtına Bölgesi", 0.6, 0.6, 0.2, 0.7, 1.5, amounts(CRYSTAL to 0.3, ENERGY to 0.4, METAL to 0.3), "Şimşekler enerji bırakır; fırtınalar hasar verir."),
    MYSTIC_FOREST("Gizemli Orman", 0.7, 0.4, 0.7, 0.8, 0.9, amounts(CARE_ESSENCE to 0.3, WOOD to 0.5, CRYSTAL to 0.1, FOOD to 0.1), "Kadim ağaçlar fısıldar."),
}

@Serializable
data class Cell(val x: Int, val y: Int) {
    fun key(): Long = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
    fun dist(o: Cell): Int = maxOf(kotlin.math.abs(x - o.x), kotlin.math.abs(y - o.y))
    companion object {
        fun fromKey(k: Long) = Cell((k shr 32).toInt(), k.toInt())
    }
}

/**
 * Tohum tabanlı, sonsuz ve deterministik dünya üreticisi.
 * Biyomlar 16x16'lık parçalar (chunk) halinde hesaplanır ve önbelleğe alınır.
 */
class WorldGenerator(val seed: Long) {
    private val cache = object : LinkedHashMap<Long, Array<Biome>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Array<Biome>>?) = size > 256
    }

    fun biomeAt(x: Int, y: Int): Biome {
        val cx = Math.floorDiv(x, CHUNK)
        val cy = Math.floorDiv(y, CHUNK)
        val key = Cell(cx, cy).key()
        val chunk = cache.getOrPut(key) { generateChunk(cx, cy) }
        return chunk[Math.floorMod(y, CHUNK) * CHUNK + Math.floorMod(x, CHUNK)]
    }

    fun biomeAt(c: Cell) = biomeAt(c.x, c.y)

    private fun generateChunk(cx: Int, cy: Int): Array<Biome> =
        Array(CHUNK * CHUNK) { i -> computeBiome(cx * CHUNK + i % CHUNK, cy * CHUNK + i / CHUNK) }

    internal fun computeBiome(x: Int, y: Int): Biome {
        // Başlangıç çevresi her zaman yumuşak ova olsun.
        if (x * x + y * y <= 5) return Biome.PLAINS
        val temp = fbm(x * 0.09, y * 0.09, 11) // sıcaklık
        val moist = fbm(x * 0.11, y * 0.11, 23) // nem
        val height = fbm(x * 0.07, y * 0.07, 37)
        val special = fbm(x * 0.21, y * 0.21, 51)
        val rare = hash01(x, y, 77)
        return when {
            special > 0.84 && rare < 0.5 -> Biome.CRYSTAL_VALLEY
            special < 0.14 && rare < 0.45 -> Biome.RUINS
            height > 0.74 -> if (temp < 0.28) Biome.SNOW else Biome.MOUNTAINS
            height < 0.17 -> Biome.LAKE
            temp > 0.70 && moist < 0.45 -> if (height > 0.62) Biome.VOLCANIC else Biome.DESERT
            temp < 0.12 -> Biome.SNOW
            moist > 0.76 && temp > 0.45 -> Biome.SWAMP
            moist > 0.66 && special > 0.62 -> Biome.STORMLANDS
            moist > 0.58 -> if (special < 0.35) Biome.MYSTIC_FOREST else Biome.FOREST
            else -> Biome.PLAINS
        }
    }

    /** Hücredeki toplam toplanabilir kaynak miktarı (tükenir). */
    fun depositSize(c: Cell): Double = 220.0 + 260.0 * hash01(c.x, c.y, 91)

    fun hash01(x: Int, y: Int, salt: Int): Double {
        var h = seed xor (x.toLong() * 0x9E3779B97F4A7C15uL.toLong()) xor (y.toLong() * 0xC2B2AE3D27D4EB4FuL.toLong()) xor (salt.toLong() * 0x165667B19E3779F9L)
        h = (h xor (h ushr 33)) * -0xae502812aa7333L
        h = (h xor (h ushr 33)) * -0x3b314601e57a13adL
        h = h xor (h ushr 33)
        return (h ushr 11).toDouble() / (1L shl 53).toDouble()
    }

    private fun valueNoise(x: Double, y: Double, salt: Int): Double {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        val sx = fx * fx * (3 - 2 * fx)
        val sy = fy * fy * (3 - 2 * fy)
        val a = hash01(x0, y0, salt)
        val b = hash01(x0 + 1, y0, salt)
        val c = hash01(x0, y0 + 1, salt)
        val d = hash01(x0 + 1, y0 + 1, salt)
        val top = a + (b - a) * sx
        val bottom = c + (d - c) * sx
        return top + (bottom - top) * sy
    }

    private fun fbm(x: Double, y: Double, salt: Int): Double {
        var sum = 0.0
        var amp = 0.6
        var freq = 1.0
        var norm = 0.0
        repeat(3) { o ->
            sum += valueNoise(x * freq, y * freq, salt + o * 7) * amp
            norm += amp
            amp *= 0.5
            freq *= 2.1
        }
        // Değer gürültüsü 0.5 çevresinde toplanır; biyom çeşitliliği için dağılımı genişlet.
        return (0.5 + (sum / norm - 0.5) * 1.7).coerceIn(0.0, 1.0)
    }

    companion object {
        const val CHUNK = 16
    }
}
