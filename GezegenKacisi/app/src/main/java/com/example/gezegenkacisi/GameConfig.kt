package com.example.gezegenkacisi


/** Gezegen yüzey çeşitleri. */
enum class PlanetType { GAS, ROCKY, ICE, LAVA, OCEAN, MOON }

/**
 * Bir harita: görünüm (arka plan, gezegen çeşitleri, müzik) ve küçük bir oynanış farkı.
 * Çarpanlar 1 iken oyun orijinal Gezegen Kaçışı gibi davranır.
 */
class GameMap(
    val nameRes: Int,
    val descRes: Int,
    val bgColors: IntArray,          // arka plan gradyanı: üst, orta, alt
    val nebula: IntArray,            // bulutsu renkleri
    val starTint: Int,               // renkli yıldızların tonu
    val accent: Int,                 // menüdeki kart rengi
    val mainTypes: List<PlanetType>, // boşluk kenarındaki büyük gezegenler
    val moonTypes: List<PlanetType>, // kuleyi tamamlayan uydular
    val ringChance: Float,           // büyük gezegenin halkalı olma olasılığı
    val gravityMul: Float = 1f,      // yer çekimi çarpanı
    val flapMul: Float = 1f,         // itiş çarpanı
    val speedMul: Float = 1f,        // kaydırma hızı çarpanı
    val planetMul: Float = 1f,       // büyük gezegen boyutu çarpanı
    val sway: Boolean = false,       // kuleler yukarı aşağı salınır mı
    val musicTheme: Int = 0          // SoundManager'daki müzik teması
)

/** Zorluk seviyeleri. Haritanın çarpanlarıyla birlikte uygulanır. */
enum class Difficulty(
    val nameRes: Int,
    val color: Int,
    val startGap: Float,       // başlangıçtaki geçiş boşluğu
    val minGap: Float,         // en dar geçiş boşluğu
    val gapShrink: Float,      // her puanda boşluğun daralması
    val baseSpeed: Float,      // başlangıç kaydırma hızı
    val speedPerScore: Float,  // her puanda hız artışı
    val speedCapScore: Int,    // hız bu puandan sonra artmaz
    val spacing: Float,        // iki kule arası mesafe
    val gapJump: Float,        // art arda iki boşluk arasındaki en büyük dikey fark
    val swayAmp: Float         // asteroit kuşağında salınım genliği
) {
    EASY(R.string.diff_easy, 0xFF4CD98A.toInt(), 345f, 280f, 2.5f, 185f, 2.2f, 30, 365f, 170f, 45f),
    NORMAL(R.string.diff_normal, 0xFFFFC14D.toInt(), 300f, 230f, 3.5f, 210f, 3f, 30, 330f, 240f, 65f),
    HARD(R.string.diff_hard, 0xFFFF5B5B.toInt(), 262f, 205f, 4f, 238f, 3.6f, 35, 305f, 300f, 85f)
}

object Maps {
    private fun c(v: Long) = v.toInt()

    val ALL: List<GameMap> = listOf(
        GameMap(
            R.string.map_earth, R.string.map_earth_desc,
            bgColors = intArrayOf(c(0xFF060920), c(0xFF0E1140), c(0xFF1C0E3A)),
            nebula = intArrayOf(c(0xFF7B3BFF), c(0xFF2BB8FF), c(0xFFFF4B9A), c(0xFF3B5BFF), c(0xFF9B3BFF)),
            starTint = c(0xFFBFD4FF),
            accent = c(0xFF5B7BFF),
            mainTypes = listOf(
                PlanetType.GAS, PlanetType.ROCKY, PlanetType.ICE, PlanetType.LAVA,
                PlanetType.OCEAN, PlanetType.GAS, PlanetType.OCEAN, PlanetType.ROCKY
            ),
            moonTypes = listOf(PlanetType.MOON, PlanetType.MOON, PlanetType.MOON, PlanetType.ROCKY, PlanetType.ICE),
            ringChance = 0.35f,
            musicTheme = 0
        ),
        GameMap(
            R.string.map_ice, R.string.map_ice_desc,
            bgColors = intArrayOf(c(0xFF03121F), c(0xFF0A2A44), c(0xFF0D3B4F)),
            nebula = intArrayOf(c(0xFF2BB8FF), c(0xFF6FF2FF), c(0xFF3B7BFF), c(0xFFA6E6FF), c(0xFF2B6BFF)),
            starTint = c(0xFFCFF4FF),
            accent = c(0xFF4FC8F2),
            mainTypes = listOf(PlanetType.ICE, PlanetType.ICE, PlanetType.OCEAN, PlanetType.ICE),
            moonTypes = listOf(PlanetType.ICE, PlanetType.MOON, PlanetType.ICE),
            ringChance = 0.3f,
            gravityMul = 0.82f,
            flapMul = 0.88f,
            speedMul = 0.95f,
            musicTheme = 1
        ),
        GameMap(
            R.string.map_lava, R.string.map_lava_desc,
            bgColors = intArrayOf(c(0xFF1A0505), c(0xFF33090A), c(0xFF4A1A06)),
            nebula = intArrayOf(c(0xFFFF4B1F), c(0xFFFF9A2B), c(0xFFB3122E), c(0xFFFF6A3D), c(0xFF8A1F0F)),
            starTint = c(0xFFFFD2A6),
            accent = c(0xFFFF6A2B),
            mainTypes = listOf(PlanetType.LAVA, PlanetType.LAVA, PlanetType.ROCKY),
            moonTypes = listOf(PlanetType.LAVA, PlanetType.ROCKY, PlanetType.MOON),
            ringChance = 0f,
            gravityMul = 1.05f,
            speedMul = 1.12f,
            musicTheme = 2
        ),
        GameMap(
            R.string.map_gas, R.string.map_gas_desc,
            bgColors = intArrayOf(c(0xFF0F0520), c(0xFF26104A), c(0xFF3A0F45)),
            nebula = intArrayOf(c(0xFFC23BFF), c(0xFFFF4BC8), c(0xFF7B3BFF), c(0xFFFF8FD8), c(0xFF5B2BFF)),
            starTint = c(0xFFFFD6F5),
            accent = c(0xFFC25BFF),
            mainTypes = listOf(PlanetType.GAS),
            moonTypes = listOf(PlanetType.GAS, PlanetType.MOON, PlanetType.MOON),
            ringChance = 0.65f,
            planetMul = 1.2f,
            musicTheme = 3
        ),
        GameMap(
            R.string.map_asteroid, R.string.map_asteroid_desc,
            bgColors = intArrayOf(c(0xFF07100F), c(0xFF0F1F22), c(0xFF1F1A14)),
            nebula = intArrayOf(c(0xFF2BFFB8), c(0xFF8A7B5B), c(0xFF2B8AFF), c(0xFFB89B6B), c(0xFF1FA08A)),
            starTint = c(0xFFE6DAC3),
            accent = c(0xFF3FD6B0),
            mainTypes = listOf(PlanetType.ROCKY, PlanetType.MOON, PlanetType.ROCKY),
            moonTypes = listOf(PlanetType.MOON, PlanetType.ROCKY),
            ringChance = 0f,
            sway = true,
            musicTheme = 4
        )
    )
}
