package com.kayipadalar.logic.level

/** Alanlarda kademeli tanıtılan özellikler. */
enum class Feature {
    SLIDE, ROTATE, BRIDGES, FOG, FIRE_WATER, ICE, WIND, CRYSTAL_ENERGY, PORTAL, FORT_BATTLE,
    SWAP, DEFENSIVE_AI, JUMP, GRAVITY, CRUMBLE, TRADE, TRAPPER_AI, POISON, TIME, ELECTRIC,
    VOLCANO, RESOURCER_AI, LIFE, STRATEGIST_AI, ANCIENT,
}

enum class MapShape { OPEN, RING, CROSS, SCATTER, CORRIDOR, SPLIT }

data class AreaSpec(
    val index: Int,
    val name: String,
    val theme: String,
    val newFeature: Feature?,
    /** Bu alanda öne çıkan özellikler (yeni özellik + remiks çiftleri). */
    val focus: List<Feature>,
    /** Bu alana kadar açılmış tüm özellikler. */
    val unlocked: List<Feature>,
    val shape: MapShape,
) {
    val firstLevel get() = (index - 1) * 5 + 1
    val bossTier: Int get() = when { index % 10 == 0 -> 2; index % 5 == 0 -> 1; else -> 0 }
}

object AreaCatalog {
    /** Özelliklerin oyuncuya gösterilen Türkçe adları. */
    val FEATURE_NAMES: Map<Feature, String> = mapOf(
        Feature.SLIDE to "Ada kaydırma", Feature.ROTATE to "Ada döndürme", Feature.BRIDGES to "Köprü bağlantıları",
        Feature.FOG to "Gizli adalar", Feature.FIRE_WATER to "Ateş ve su", Feature.ICE to "Dondurma", Feature.WIND to "Rüzgâr itişi",
        Feature.CRYSTAL_ENERGY to "Enerji ve yükseklik", Feature.PORTAL to "Portallar", Feature.FORT_BATTLE to "Kale savaşları",
        Feature.SWAP to "Ada takası", Feature.DEFENSIVE_AI to "Savunmacı düşman", Feature.JUMP to "Ada fırlatma",
        Feature.GRAVITY to "Yerçekimi zinciri", Feature.CRUMBLE to "Çöken adalar", Feature.TRADE to "Ticaret yolları",
        Feature.TRAPPER_AI to "Tuzakçı düşman", Feature.POISON to "Zehir", Feature.TIME to "Zaman adaları",
        Feature.ELECTRIC to "Elektrik zinciri", Feature.VOLCANO to "Volkanlar", Feature.RESOURCER_AI to "Kaynakçı düşman",
        Feature.LIFE to "Yaşam ve şifa", Feature.STRATEGIST_AI to "Stratejist düşman", Feature.ANCIENT to "Kadim güçler",
    )

    const val AREAS = 100
    const val LEVELS = AREAS * 5

    val THEMES = listOf(
        "GREEN", "ROCK", "BRIDGE", "FOG", "FIRE", "ICE", "WIND", "CRYSTAL", "PORTAL", "IRON",
        "CORAL", "DESERT", "AUTUMN", "SAKURA", "STORM", "SWAMP", "GOLD", "MOON", "CLOUD", "JUNGLE",
    )

    private val baseNames = listOf(
        "Yeşil Başlangıç", "Dönen Kayalıklar", "Köprüler Vadisi", "Sisli Takımadalar", "Alev Geçidi",
        "Buzul Krallığı", "Rüzgâr Koridoru", "Kristal Mağaraları", "Kayıp Geçitler", "Demir Kale",
        "Takas Koyu", "Sessiz Kaleler", "Sıçrayan Kayalar", "Çekim Girdabı", "Çöken Köprüler",
        "Tüccar Yolu", "Tuzak Ormanı", "Zehirli Bataklık", "Zaman Kuyusu", "Şimşek Tepeleri",
        "Lav Adaları", "Hasat Ovası", "Yaşam Pınarı", "Usta Strateji Kalesi", "Kadim Mabet",
    )
    private val baseThemes = listOf(
        "GREEN", "ROCK", "BRIDGE", "FOG", "FIRE", "ICE", "WIND", "CRYSTAL", "PORTAL", "IRON",
        "CORAL", "GOLD", "ROCK", "MOON", "AUTUMN", "DESERT", "JUNGLE", "SWAMP", "MOON", "STORM",
        "FIRE", "AUTUMN", "SAKURA", "IRON", "GOLD",
    )
    private val prefixes = listOf(
        "Yükselen", "Parlayan", "Fısıldayan", "Uçan", "Altın", "Gümüş", "Mercan", "Zümrüt",
        "Safir", "Gökkuşağı", "Bulutlu", "Yankılı", "Unutulmuş", "Büyülü", "Rüzgârlı",
    )
    private val suffixes = listOf("Takımadalar", "Geçitler", "Vadiler", "Kıyılar", "Zirveler")

    private val all: List<AreaSpec> by lazy { (1..AREAS).map { build(it) } }

    fun area(index: Int): AreaSpec = all[index - 1]
    fun areaOfLevel(levelId: Int) = (levelId - 1) / 5 + 1

    private fun build(n: Int): AreaSpec {
        val features = Feature.entries
        if (n <= 25) {
            val f = features[n - 1]
            val unlocked = features.take(n)
            val shape = MapShape.entries[if (n <= 2) 0 else (n % MapShape.entries.size)]
            return AreaSpec(n, baseNames[n - 1], baseThemes[n - 1], f, listOf(f), unlocked, shape)
        }
        val p = n - 26
        val pool = features.drop(2) // SLIDE/ROTATE her zaman açık
        val f1 = pool[(p * 7) % pool.size]
        var f2 = pool[(p * 11 + 5) % pool.size]
        if (f2 == f1) f2 = pool[(p * 11 + 6) % pool.size]
        val name = "${prefixes[p % prefixes.size]} ${suffixes[(p / prefixes.size) % suffixes.size]}"
        return AreaSpec(n, name, THEMES[p % THEMES.size], null, listOf(f1, f2), features, MapShape.entries[p % MapShape.entries.size])
    }
}
