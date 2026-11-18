package com.example.gezegenkacisi

/** Engel görsellerinin yüzey çeşitleri. */
enum class PlanetType { GAS, ROCKY, ICE, LAVA, OCEAN, MOON, CRYSTAL, ASTEROID }

/** Her haritanın kendine özgü kuralı ve dekoru bu türe göre seçilir. */
enum class MapKind {
    EARTH,    // klasik rota
    ICE,      // çok düşük yer çekimi, kar, buz kristali engeller
    LAVA,     // yerden fırlayan ateş topları, kıvılcımlar
    GAS,      // roketi yukarı/aşağı iten değişken rüzgâr
    ASTEROID  // dönen asteroitler, yukarı aşağı salınan kuleler
}

/**
 * Bir harita: görünüm (arka plan, engel çeşitleri, müzik) ve kendine özgü oynanış.
 * Çarpanlar 1 iken fizik orijinal Gezegen Kaçışı gibidir.
 */
class GameMap(
    val kind: MapKind,
    val nameRes: Int,
    val descRes: Int,
    val tipRes: Int,                 // "Hazır" ekranında gösterilen ipucu
    val bgColors: IntArray,          // arka plan gradyanı: üst, orta, alt
    val nebula: IntArray,            // bulutsu renkleri
    val starTint: Int,               // renkli yıldızların tonu
    val accent: Int,                 // menüdeki kart rengi
    val mainTypes: List<PlanetType>, // boşluk kenarındaki büyük engeller
    val moonTypes: List<PlanetType>, // kuleyi tamamlayan küçük engeller
    val ringChance: Float,           // büyük gezegenin halkalı olma olasılığı
    val gravityMul: Float = 1f,      // yer çekimi çarpanı
    val flapMul: Float = 1f,         // itiş çarpanı
    val speedMul: Float = 1f,        // kaydırma hızı çarpanı
    val planetMul: Float = 1f,       // büyük engel boyutu çarpanı
    val musicTheme: Int = 0          // SoundManager'daki müzik teması
)

/**
 * Zorluk seviyeleri. Haritanın çarpanlarıyla birlikte uygulanır.
 * [hazard] haritanın özel tehlikesinin (ateş topu sıklığı, rüzgâr gücü, salınım) şiddetidir.
 */
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
    val swayAmp: Float,        // asteroit kuşağında salınım genliği
    val hazard: Float          // harita tehlikesinin şiddeti
) {
    EASY(R.string.diff_easy, 0xFF4CD98A.toInt(), 390f, 320f, 2f, 165f, 1.5f, 25, 410f, 150f, 35f, 0.55f),
    NORMAL(R.string.diff_normal, 0xFFFFC14D.toInt(), 305f, 238f, 3.2f, 210f, 3f, 30, 335f, 235f, 65f, 1f),
    HARD(R.string.diff_hard, 0xFFFF5B5B.toInt(), 250f, 192f, 4f, 252f, 4f, 40, 292f, 320f, 95f, 1.5f)
}

object Maps {
    private fun c(v: Long) = v.toInt()

    val ALL: List<GameMap> = listOf(
        GameMap(
            MapKind.EARTH, R.string.map_earth, R.string.map_earth_desc, R.string.map_earth_tip,
            bgColors = intArrayOf(c(0xFF050A24), c(0xFF0B1A4A), c(0xFF0F2E5C)),
            nebula = intArrayOf(c(0xFF2B6BFF), c(0xFF2BB8FF), c(0xFF3B5BFF)),
            starTint = c(0xFFBFD4FF),
            accent = c(0xFF4F8BFF),
            mainTypes = listOf(PlanetType.OCEAN, PlanetType.OCEAN, PlanetType.ROCKY, PlanetType.OCEAN, PlanetType.GAS),
            moonTypes = listOf(PlanetType.MOON),
            ringChance = 0f,
            musicTheme = 0
        ),
        GameMap(
            MapKind.ICE, R.string.map_ice, R.string.map_ice_desc, R.string.map_ice_tip,
            bgColors = intArrayOf(c(0xFF020F18), c(0xFF06283A), c(0xFF0C4A5A)),
            nebula = intArrayOf(c(0xFF2BFFD0), c(0xFF6FF2FF), c(0xFF2B9BFF)),
            starTint = c(0xFFCFF4FF),
            accent = c(0xFF5FE6FF),
            mainTypes = listOf(PlanetType.CRYSTAL),
            moonTypes = listOf(PlanetType.CRYSTAL, PlanetType.CRYSTAL, PlanetType.ICE),
            ringChance = 0f,
            gravityMul = 0.68f,
            flapMul = 0.8f,
            speedMul = 0.9f,
            musicTheme = 1
        ),
        GameMap(
            MapKind.LAVA, R.string.map_lava, R.string.map_lava_desc, R.string.map_lava_tip,
            bgColors = intArrayOf(c(0xFF1A0303), c(0xFF3A0808), c(0xFF6A1E05)),
            nebula = intArrayOf(c(0xFFFF3B1F), c(0xFFFF8A2B), c(0xFFB3122E)),
            starTint = c(0xFFFFC08A),
            accent = c(0xFFFF5A1F),
            mainTypes = listOf(PlanetType.LAVA),
            moonTypes = listOf(PlanetType.LAVA, PlanetType.ROCKY),
            ringChance = 0f,
            gravityMul = 1.1f,
            flapMul = 1.04f,
            speedMul = 1.15f,
            musicTheme = 2
        ),
        GameMap(
            MapKind.GAS, R.string.map_gas, R.string.map_gas_desc, R.string.map_gas_tip,
            bgColors = intArrayOf(c(0xFF12041F), c(0xFF2E0E4A), c(0xFF4A1050)),
            nebula = intArrayOf(c(0xFFC23BFF), c(0xFFFF4BC8), c(0xFF7B3BFF)),
            starTint = c(0xFFFFD6F5),
            accent = c(0xFFD05BFF),
            mainTypes = listOf(PlanetType.GAS),
            moonTypes = listOf(PlanetType.GAS),
            ringChance = 1f,
            planetMul = 1.25f,
            musicTheme = 3
        ),
        GameMap(
            MapKind.ASTEROID, R.string.map_asteroid, R.string.map_asteroid_desc, R.string.map_asteroid_tip,
            bgColors = intArrayOf(c(0xFF0A0A0C), c(0xFF17171A), c(0xFF2A2218)),
            nebula = intArrayOf(c(0xFF8A7B5B), c(0xFF5B5B6B), c(0xFFB89B6B)),
            starTint = c(0xFFE6DAC3),
            accent = c(0xFFC8A060),
            mainTypes = listOf(PlanetType.ASTEROID),
            moonTypes = listOf(PlanetType.ASTEROID),
            ringChance = 0f,
            musicTheme = 4
        )
    )
}
