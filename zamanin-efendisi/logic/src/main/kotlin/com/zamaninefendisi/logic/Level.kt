package com.zamaninefendisi.logic

/** Bölüm hedefleri. Bir bölüm, bütün hedefleri aynı anda sağlandığında kazanılır. */
sealed class Goal {
    abstract val text: String
    abstract fun met(s: LevelSession): Boolean
    /** Haritada işaretlenecek hücreler (bayrak/hedef simgesi). */
    open val markers: List<Pair<Era, Pos>> = emptyList()

    /** Kahraman belirtilen çağda hedef hücreye ulaşmalı. */
    data class Reach(val era: Era, val pos: Pos, override val text: String) : Goal() {
        override fun met(s: LevelSession) = s.heroEra == era && s.heroPos == pos
        override val markers get() = listOf(era to pos)
    }

    /** Belirtilen bütün hücrelerde, belirtilen çağda bu nesne bulunmalı. */
    data class FeatureAt(val era: Era, val cells: List<Pos>, val feature: Feature, override val text: String) : Goal() {
        override fun met(s: LevelSession) = cells.all { s.timeline.grid(era)[it].feature == feature }
        override val markers get() = cells.map { era to it }
    }

    /** Belirtilen hücrelerin hiçbirinde bu nesne bulunmamalı (ör. geçidi açık tut). */
    data class NotFeatureAt(val era: Era, val cells: List<Pos>, val feature: Feature, override val text: String) : Goal() {
        override fun met(s: LevelSession) = cells.none { s.timeline.grid(era)[it].feature == feature }
    }

    /** Çağda bu nesneden en az [count] adet bulunmalı. */
    data class FeatureCount(val era: Era, val feature: Feature, val count: Int, override val text: String) : Goal() {
        override fun met(s: LevelSession) = s.timeline.grid(era).count(feature) >= count
    }

    /** Çağda bu nesne hiç bulunmamalı. */
    data class NoFeature(val era: Era, val feature: Feature, override val text: String) : Goal() {
        override fun met(s: LevelSession) = s.timeline.grid(era).count(feature) == 0
    }

    /** Belirtilen sayıda müttefik birlik hayatta kalmalı. */
    data class KeepUnits(val count: Int, override val text: String) : Goal() {
        override fun met(s: LevelSession) = s.units.size >= count
    }

    /** Bütün düşman birlikleri yenilmeli ve düşman kalesi kalmamalı. */
    data class DefeatAll(override val text: String) : Goal() {
        override fun met(s: LevelSession) = s.enemies.isEmpty() && s.timeline.grid(s.level.enemyEra).count(Feature.ENEMY_FORT) == 0
    }

    /** Belirtilen tur sayısı boyunca hayatta kal. */
    data class Survive(val turns: Int, override val text: String) : Goal() {
        override fun met(s: LevelSession) = s.turn >= turns
    }
}

/**
 * Bölüme özgü nedensellik bağlantısı: kaynak hücrede belirli nesne varsa hedef hücre değişir.
 * Örn. Geçmiş'te mühür uyandırılırsa Şimdi'de nehir kuruyup sığ geçit olur.
 */
data class CausalLink(
    val id: String,
    val sourceEra: Era,
    val source: Pos,
    val whenFeature: Feature,
    val targetEra: Era,
    val target: Pos,
    val setTerrain: Terrain? = null,
    val setFeature: Feature? = null,
    val description: String,
)

/** Düşman yapay zekâsı stratejileri. Zorluk can artırarak değil, davranışla yükselir. */
enum class EnemyStrategy(val title: String, val description: String) {
    AGGRESSIVE("Saldırgan", "Korunan hedefe en kısa yoldan saldırır; yolundaki herkese vurur."),
    DEFENSIVE("Savunmacı", "Kalesinin çevresini korur; kalesi yıkılırsa öfkelenip saldırgan olur."),
    RESOURCE("Kaynak Odaklı", "Tarla, köy ve kasabaları yağmalar."),
    TIME_MANIPULATOR("Zaman Manipülatörü", "Yolu kapanınca duvarların üstünden faz atlar; sen başka çağdayken iki kat hızlanır."),
    PARADOX("Paradoks Yaratıcısı", "Senin inşa ettiğin yapıları sabote ederek zaman çizgini bozar."),
    TECH("Teknoloji Uzmanı", "Uzaktan ateş eder, arayı açar ve her 3 turda bir güçlenir."),
}

enum class EnemyKind(
    val title: String,
    val hp: Int,
    val attack: Int,
    val defense: Int = 0,
    val range: Int = 1,
    val strategy: EnemyStrategy = EnemyStrategy.AGGRESSIVE,
) {
    RAIDER("Akıncı", 1, 1),
    ROBOT("Robot Muhafız", 1, 1),
    BRUTE("Ağır Muhafız", 3, 2, defense = 1),
    ARCHER("Düşman Okçu", 2, 1, range = 2, strategy = EnemyStrategy.DEFENSIVE),
    PLUNDERER("Yağmacı", 2, 1, strategy = EnemyStrategy.RESOURCE),
    PHASER("Zaman Hırsızı", 2, 1, strategy = EnemyStrategy.TIME_MANIPULATOR),
    SABOTEUR("Paradoks Ajanı", 2, 1, strategy = EnemyStrategy.PARADOX),
    TECHNICIAN("Teknoloji Uzmanı", 2, 1, range = 2, strategy = EnemyStrategy.TECH),
}

data class EnemySpawn(val pos: Pos, val kind: EnemyKind? = null)

enum class UnitAbility(val title: String, val description: String) {
    SHIELD("Kalkan", "Aldığı her hasar 1 azalır."),
    SIEGE("Kuşatma", "Düşman kalesine ve yapılara 3 kat hasar verir."),
    GUARD("Koruma", "Yanındaki müttefikler 1 daha az hasar alır."),
    FREEZE_SHOT("Zaman Kilidi", "Vurduğu düşmanı 2 tur dondurur."),
}

/** Müttefik birlik türleri. [eras]: birliğin bulunabileceği çağlar (çağ uyumluluğu). */
enum class UnitType(
    val title: String,
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val minRange: Int,
    val maxRange: Int,
    val move: Int,
    val eras: Set<Era>,
    val ability: UnitAbility? = null,
) {
    WARRIOR("İlkel Savaşçı", 3, 2, 0, 1, 1, 2, setOf(Era.PAST)),
    KNIGHT("Şövalye", 5, 2, 2, 1, 1, 2, setOf(Era.PAST, Era.PRESENT), UnitAbility.SHIELD),
    ARCHER("Okçu", 2, 2, 0, 2, 3, 2, setOf(Era.PAST, Era.PRESENT)),
    ENGINEER("Kuşatma Mühendisi", 3, 1, 1, 1, 1, 1, setOf(Era.PRESENT), UnitAbility.SIEGE),
    SOLDIER("Modern Asker", 3, 3, 1, 1, 2, 2, setOf(Era.PRESENT, Era.FUTURE)),
    MECH("Mekanik Koruyucu", 6, 1, 3, 1, 1, 1, setOf(Era.FUTURE), UnitAbility.GUARD),
    ROBOT("Robot Savaşçı", 4, 3, 1, 1, 1, 2, setOf(Era.FUTURE)),
    ENERGY("Enerji Birliği", 2, 4, 0, 2, 3, 1, setOf(Era.FUTURE)),
    MAGE("Zaman Büyücüsü", 2, 1, 0, 1, 2, 2, setOf(Era.PAST, Era.PRESENT, Era.FUTURE), UnitAbility.FREEZE_SHOT),
}

data class UnitSpawn(val type: UnitType, val pos: Pos)

/** Oyun içi öğretici ipucu: koşul sağlanmadığı sürece gösterilir; sıradaki ilk sağlanmamış ipucu görünür. */
class TutorialHint(val text: String, val done: (LevelSession) -> Boolean)

class LevelDef(
    val id: Int,
    val area: Int,
    val name: String,
    val intro: String,
    /** Geçmiş çağın başlangıç haritası (ASCII). Lejant: [LevelMap]. */
    val map: List<String>,
    val eras: List<Era>,
    val startEra: Era,
    val startWood: Int = 0,
    val allowed: Map<Era, Set<BuildKind>>,
    val goals: List<Goal>,
    val maxTurns: Int,
    /** Üç yıldız için en fazla eylem sayısı (Bekle sayılmaz). */
    val par: Int,
    val desertSpeed: Int = 0,
    /** Çağa özgü sabit tarihî olaylar: evrimden sonra uygulanır ('.' = değişiklik yok). */
    val overrides: Map<Era, List<String>> = emptyMap(),
    val links: List<CausalLink> = emptyList(),
    val enemies: List<EnemySpawn> = emptyList(),
    val enemyEra: Era = Era.PRESENT,
    /** Düşman kalesi her kaç turda bir akıncı üretir (0 = üretmez). */
    val spawnEvery: Int = 0,
    /** Kalenin ürettiği düşman türü (null: çağa göre akıncı/robot). */
    val enemyKindOverride: EnemyKind? = null,
    val maxEnemies: Int = 3,
    /** Düşmanların ulaşmaya çalıştığı korunacak hücreler. Ulaşırlarsa bölüm kaybedilir. */
    val protect: List<Pos> = emptyList(),
    val heroHp: Int = 3,
    /** Çağ geçişi için zaman enerjisi (null = sınırsız). */
    val energy: Int? = null,
    /** Müttefik birlikler (düşman çağında). */
    val units: List<UnitSpawn> = emptyList(),
    /** Zamanı dondurma hakkı sayısı. */
    val freezes: Int = 0,
    val hints: List<TutorialHint> = emptyList(),
    /** Doğrulanmış çözüm. Otomatik testler her bölümün bununla kazanıldığını kontrol eder. */
    val solution: List<Action>,
) {
    val parsed: LevelMap by lazy { LevelMap.parse(map, overrides) }
    val width get() = parsed.width
    val height get() = parsed.height
    val heroStart get() = parsed.heroStart
    val hasEnemies: Boolean by lazy {
        val hostile = { c: CellState -> c.feature == Feature.ENEMY_CAMP || c.feature == Feature.ENEMY_FORT }
        enemies.isNotEmpty() || units.isNotEmpty() || parsed.cells.any(hostile) || parsed.overrides.values.any { m -> m.values.any(hostile) }
    }
}

/**
 * ASCII harita çözümleyici.
 * G çayır · S kum · W nehir · M dağ · R yol · T ağaç · F tarla · H köy · C Zaman Kalesi
 * E düşman kampı · K düşman kalesi · X zaman mührü · P ahşap çit · B ahşap köprü · O kadim orman · A yaşlı ağaç
 * * zaman kristali · @ kahraman (çayır)
 */
class LevelMap(
    val width: Int,
    val height: Int,
    val cells: List<CellState>,
    val heroStart: Pos,
    val overrides: Map<Era, Map<Pos, CellState>>,
) {
    fun base(p: Pos): CellState = cells[p.y * width + p.x]

    companion object {
        fun cellFor(ch: Char): CellState? = when (ch) {
            'G', '@' -> CellState(Terrain.GRASS)
            'S' -> CellState(Terrain.SAND)
            'W' -> CellState(Terrain.WATER)
            'M' -> CellState(Terrain.MOUNTAIN)
            'R' -> CellState(Terrain.ROAD)
            'T' -> CellState(Terrain.GRASS, Feature.TREE)
            'O' -> CellState(Terrain.GRASS, Feature.FOREST)
            'A' -> CellState(Terrain.GRASS, Feature.TREE, level = 1)
            'F' -> CellState(Terrain.GRASS, Feature.FIELD)
            'H' -> CellState(Terrain.GRASS, Feature.HUT)
            'V' -> CellState(Terrain.GRASS, Feature.TOWN)
            'C' -> CellState(Terrain.GRASS, Feature.CASTLE)
            'E' -> CellState(Terrain.GRASS, Feature.ENEMY_CAMP)
            'K' -> CellState(Terrain.GRASS, Feature.ENEMY_FORT)
            'X' -> CellState(Terrain.GRASS, Feature.SHRINE)
            'P' -> CellState(Terrain.GRASS, Feature.PALISADE)
            'B' -> CellState(Terrain.WATER, Feature.BRIDGE_WOOD)
            'Q' -> CellState(Terrain.WATER, Feature.BRIDGE_STONE)
            'r' -> CellState(Terrain.GRASS, Feature.RUINS)
            '*' -> CellState(Terrain.GRASS, Feature.CRYSTAL)
            else -> null
        }

        fun parse(rows: List<String>, overrides: Map<Era, List<String>>): LevelMap {
            require(rows.isNotEmpty()) { "Boş harita" }
            val w = rows[0].length
            require(rows.all { it.length == w }) { "Harita satırları eşit uzunlukta olmalı" }
            var hero: Pos? = null
            val cells = ArrayList<CellState>()
            rows.forEachIndexed { y, row ->
                row.forEachIndexed { x, ch ->
                    if (ch == '@') hero = Pos(x, y)
                    cells += cellFor(ch) ?: error("Bilinmeyen harita karakteri '$ch' ($x,$y)")
                }
            }
            val ov = overrides.mapValues { (_, lines) ->
                require(lines.size == rows.size && lines.all { it.length == w }) { "Geçersiz çağ katmanı boyutu" }
                buildMap {
                    lines.forEachIndexed { y, row -> row.forEachIndexed { x, ch -> if (ch != '.') put(Pos(x, y), cellFor(ch) ?: error("'$ch'")) } }
                }
            }
            return LevelMap(w, rows.size, cells, hero ?: error("Haritada kahraman (@) yok"), ov)
        }
    }
}
