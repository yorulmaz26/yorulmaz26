package com.example.gezegenkacisi

/** Engel görsellerinin yüzey çeşitleri. */
enum class PlanetType { GAS, ROCKY, ICE, LAVA, OCEAN, MOON, CRYSTAL, ASTEROID, DARK, TOXIC }

/** Haritanın dekoru (arka plan, atmosfer parçacıkları) bu türe göre seçilir. */
enum class MapKind { EARTH, ICE, LAVA, GAS, ASTEROID, BLACKHOLE, STORM, METEOR, TOXIC, CORE }

/** Oynanışı değiştiren mekanikler. Harita kendi mekaniklerini getirir, bölümler yenilerini ekler. */
enum class Mech {
    WIND,      // roketi yukarı/aşağı iten değişken rüzgâr
    FIRE,      // yerden fırlayan ateş topları
    METEOR,    // yukarıdan düşen meteorlar
    SWAY,      // kuleler yukarı aşağı salınır
    PULL,      // kara delik: roket ekranın ortasına çekilir
    LIGHTNING, // yukarıdan inen yıldırım
    SPIN       // engeller döner (görsel)
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
    val mechs: Set<Mech> = emptySet(),
    val gravityMul: Float = 1f,      // yer çekimi çarpanı
    val flapMul: Float = 1f,         // itiş çarpanı
    val speedMul: Float = 1f,        // kaydırma hızı çarpanı
    val planetMul: Float = 1f,       // büyük engel boyutu çarpanı
    val hazardMul: Float = 1f,       // haritanın tehlikelerinin şiddeti
    val musicTheme: Int = 0          // SoundManager'daki müzik teması
)

/** Bir oyunun zorluk ayarları (sonsuz modda seviyeden, bölüm modunda bölüm numarasından gelir). */
data class DiffParams(
    val startGap: Float,       // başlangıçtaki geçiş boşluğu
    val minGap: Float,         // en dar geçiş boşluğu
    val gapShrink: Float,      // her puanda boşluğun daralması
    val baseSpeed: Float,      // başlangıç kaydırma hızı
    val speedPerScore: Float,  // her puanda hız artışı
    val speedCapScore: Int,    // hız bu puandan sonra artmaz
    val spacing: Float,        // iki kule arası mesafe
    val gapJump: Float,        // art arda iki boşluk arasındaki en büyük dikey fark
    val swayAmp: Float,        // salınım genliği
    val hazard: Float          // tehlikelerin şiddeti (ateş topu sıklığı, rüzgâr gücü...)
)

/** Sonsuz mod ve çok oyunculu için zorluk seviyeleri. */
enum class Difficulty(val nameRes: Int, val color: Int, val params: DiffParams) {
    EASY(R.string.diff_easy, 0xFF4CD98A.toInt(), DiffParams(390f, 320f, 2f, 165f, 1.5f, 25, 410f, 150f, 35f, 0.55f)),
    NORMAL(R.string.diff_normal, 0xFFFFC14D.toInt(), DiffParams(305f, 238f, 3.2f, 210f, 3f, 30, 335f, 235f, 65f, 1f)),
    HARD(R.string.diff_hard, 0xFFFF5B5B.toInt(), DiffParams(250f, 192f, 4f, 252f, 4f, 40, 292f, 320f, 95f, 1.5f))
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
            mechs = setOf(Mech.FIRE),
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
            mechs = setOf(Mech.WIND),
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
            mechs = setOf(Mech.SWAY, Mech.SPIN),
            musicTheme = 4
        ),
        GameMap(
            MapKind.BLACKHOLE, R.string.map_blackhole, R.string.map_blackhole_desc, R.string.map_blackhole_tip,
            bgColors = intArrayOf(c(0xFF07020E), c(0xFF120624), c(0xFF1E0A1E)),
            nebula = intArrayOf(c(0xFFFF9A3B), c(0xFF8A3BFF), c(0xFFFF5BC8)),
            starTint = c(0xFFFFD9A8),
            accent = c(0xFFFF9A3B),
            mainTypes = listOf(PlanetType.DARK, PlanetType.DARK, PlanetType.MOON),
            moonTypes = listOf(PlanetType.DARK, PlanetType.MOON),
            ringChance = 0.3f,
            mechs = setOf(Mech.PULL),
            musicTheme = 5
        ),
        GameMap(
            MapKind.STORM, R.string.map_storm, R.string.map_storm_desc, R.string.map_storm_tip,
            bgColors = intArrayOf(c(0xFF040714), c(0xFF0C1430), c(0xFF151A3A)),
            nebula = intArrayOf(c(0xFF5B7BFF), c(0xFF9BC8FF), c(0xFF7B5BFF)),
            starTint = c(0xFFD6E4FF),
            accent = c(0xFF8FB8FF),
            mainTypes = listOf(PlanetType.GAS, PlanetType.ICE),
            moonTypes = listOf(PlanetType.ICE, PlanetType.MOON),
            ringChance = 0.25f,
            mechs = setOf(Mech.LIGHTNING),
            speedMul = 1.05f,
            musicTheme = 6
        ),
        GameMap(
            MapKind.METEOR, R.string.map_meteor, R.string.map_meteor_desc, R.string.map_meteor_tip,
            bgColors = intArrayOf(c(0xFF120806), c(0xFF24120C), c(0xFF3A1E10)),
            nebula = intArrayOf(c(0xFFFF8A4B), c(0xFFC86A3B), c(0xFFFFB86B)),
            starTint = c(0xFFFFE0C0),
            accent = c(0xFFFF8A4B),
            mainTypes = listOf(PlanetType.ROCKY, PlanetType.ASTEROID),
            moonTypes = listOf(PlanetType.ASTEROID, PlanetType.ROCKY),
            ringChance = 0f,
            mechs = setOf(Mech.METEOR, Mech.SPIN),
            musicTheme = 7
        ),
        GameMap(
            MapKind.TOXIC, R.string.map_toxic, R.string.map_toxic_desc, R.string.map_toxic_tip,
            bgColors = intArrayOf(c(0xFF020D06), c(0xFF06200E), c(0xFF123214)),
            nebula = intArrayOf(c(0xFF7BFF3B), c(0xFF3BFF9B), c(0xFFC8FF3B)),
            starTint = c(0xFFD8FFC0),
            accent = c(0xFF7BE83B),
            mainTypes = listOf(PlanetType.TOXIC),
            moonTypes = listOf(PlanetType.TOXIC, PlanetType.MOON),
            ringChance = 0.2f,
            mechs = setOf(Mech.WIND, Mech.SWAY),
            hazardMul = 0.7f,
            musicTheme = 8
        ),
        GameMap(
            MapKind.CORE, R.string.map_core, R.string.map_core_desc, R.string.map_core_tip,
            bgColors = intArrayOf(c(0xFF05061A), c(0xFF120F38), c(0xFF2A1A3A)),
            nebula = intArrayOf(c(0xFFFFD66B), c(0xFF6B8BFF), c(0xFFFF6BD6)),
            starTint = c(0xFFFFF0C0),
            accent = c(0xFFFFD66B),
            mainTypes = listOf(PlanetType.CRYSTAL, PlanetType.GAS, PlanetType.LAVA, PlanetType.OCEAN),
            moonTypes = listOf(PlanetType.MOON, PlanetType.CRYSTAL, PlanetType.DARK),
            ringChance = 0.3f,
            mechs = setOf(Mech.SWAY, Mech.FIRE),
            hazardMul = 0.75f,
            speedMul = 1.05f,
            musicTheme = 9
        )
    )
}
