package com.besaniyelik.dunya.progress

import com.besaniyelik.dunya.level.PowerupType
import kotlinx.serialization.Serializable

/** Kozmetik içeriğin nasıl açılacağı. Hepsi oynayarak elde edilebilir; ücretli güç avantajı yoktur. */
sealed class Unlock {
    data object Default : Unlock()
    data class Coins(val price: Int) : Unlock()
    data class Gems(val price: Int) : Unlock()
    data class PlayerLevel(val level: Int) : Unlock()
    data class Collection(val map: Int) : Unlock()
    data class TotalStars(val stars: Int) : Unlock()

    fun describe(): String = when (this) {
        Default -> "Başlangıç karakteri"
        is Coins -> "$price altın"
        is Gems -> "$price elmas"
        is PlayerLevel -> "Oyuncu seviyesi $level"
        is Collection -> "${com.besaniyelik.dunya.level.GameContent.maps[map - 1].name} koleksiyonu"
        is TotalStars -> "Toplam $stars yıldız"
    }
}

/** Karakter görünümü. Renkler 0xRRGGBB. */
class Skin(
    val id: String,
    val name: String,
    val body: Int,
    val belly: Int,
    val accent: Int,
    val accessory: String,
    val pattern: String,
    val unlock: Unlock,
)

class Trail(val id: String, val name: String, val colors: IntArray, val shape: String, val unlock: Unlock)

class AchievementDef(val id: String, val title: String, val desc: String, val stat: (SaveData) -> Long, val target: Long, val gems: Int)

@Serializable
enum class MissionType(val label: String) {
    COMPLETE_LEVELS("%d bölüm tamamla"),
    COLLECT_COINS("%d altın topla"),
    COLLECT_STARS("%d yıldız topla"),
    ENDLESS_SURVIVE("Sonsuz modda tek koşuda %d saniye dayan"),
    ENDLESS_SCORE("Sonsuz modda %d puan yap"),
    PLAY_ENDLESS("Sonsuz modu %d kez oyna"),
    SURVIVE_RULES("%d kural değişimi atlat"),
    JUMP("%d kez zıpla"),
    USE_POWERUPS("%d güçlendirme kullan"),
    THREE_STAR("%d bölümü 3 yıldızla bitir"),
    COLLECT_PIECE("%d gizli parça bul");

    fun text(target: Int) = label.format(target)
}

class MissionTemplate(val type: MissionType, val targets: IntArray, val coins: Int, val gems: Int, val xp: Int)

class LoginReward(val coins: Int = 0, val gems: Int = 0, val powerups: Map<PowerupType, Int> = emptyMap()) {
    fun describe(): String = buildList {
        if (coins > 0) add("$coins altın")
        if (gems > 0) add("$gems elmas")
        powerups.forEach { (t, n) -> add("$n ${t.title}") }
    }.joinToString(" + ")
}

object Catalog {
    val skins = listOf(
        Skin("pati", "Pati", 0xFFD54F, 0xFFF3C4, 0xFF9F43, "none", "plain", Unlock.Default),
        Skin("damla", "Damla", 0x58D6D0, 0xD6FFFA, 0x2BA8A2, "leaf", "plain", Unlock.Coins(400)),
        Skin("cilek", "Çilek", 0xFF6F91, 0xFFD6E0, 0x5CC96B, "sprout", "dots", Unlock.Coins(700)),
        Skin("nane", "Nane", 0x73D65C, 0xDDFBD2, 0x3E9E3A, "cap", "plain", Unlock.Coins(1000)),
        Skin("bulut", "Bulut", 0xEAF4FF, 0xFFFFFF, 0x87CEFA, "bow", "plain", Unlock.Gems(30)),
        Skin("murdum", "Mürdüm", 0xB388FF, 0xEDE3FF, 0x7C4DFF, "wizard", "stars", Unlock.Gems(60)),
        Skin("gunes", "Güneş", 0xFFB300, 0xFFE9A8, 0xFF7043, "crown", "plain", Unlock.Gems(120)),
        Skin("kedi", "Kedi", 0xFFA06B, 0xFFE2CF, 0xE0703A, "cat_ears", "stripes", Unlock.PlayerLevel(10)),
        Skin("tavsan", "Tavşan", 0xF6E3FF, 0xFFFFFF, 0xFFB6C9, "bunny_ears", "plain", Unlock.PlayerLevel(20)),
        Skin("robot", "Robot", 0x9EC9E8, 0xE3F2FD, 0x5C8DB8, "antenna", "panel", Unlock.PlayerLevel(30)),
        Skin("astronot", "Astronot", 0xF5F7FA, 0xFFFFFF, 0xFF8A65, "helmet", "plain", Unlock.PlayerLevel(40)),
        Skin("papatya", "Papatya", 0xFFF1A6, 0xFFFFFF, 0xFFFFFF, "flower", "plain", Unlock.Collection(1)),
        Skin("kum", "Kum Tanesi", 0xF4C97A, 0xFFF0D2, 0xD9984A, "headband", "dots", Unlock.Collection(3)),
        Skin("penguen", "Penguen", 0x5D8FC9, 0xFFFFFF, 0xFF9F43, "scarf", "belly", Unlock.Collection(4)),
        Skin("mantar", "Mantar", 0xFF8A80, 0xFFF3E6, 0xFFFFFF, "mushroom", "dots", Unlock.Collection(5)),
        Skin("ari", "Arı", 0xFFD740, 0xFFF6C2, 0x4E3B2A, "bee_antenna", "stripes", Unlock.Collection(7)),
        Skin("saatci", "Saatçi", 0xD7A86E, 0xF6E3C6, 0x6D4C41, "top_hat", "plain", Unlock.Collection(9)),
        Skin("kaos", "Kaos", 0xFF7EB6, 0xFFE0F0, 0x58D6D0, "star_glasses", "swirl", Unlock.Collection(10)),
        Skin("yildiz", "Yıldız", 0xFFE082, 0xFFF8DC, 0xFFB300, "halo", "stars", Unlock.TotalStars(300)),
        Skin("gokkusagi", "Gökkuşağı", 0x8FD3FF, 0xFFFFFF, 0xFF6F91, "headphones", "rainbow", Unlock.TotalStars(900)),
    )

    val trails = listOf(
        Trail("toz", "Toz Bulutu", intArrayOf(0xFFFFFF, 0xF2E6D0), "dust", Unlock.Default),
        Trail("kalp", "Kalp İzi", intArrayOf(0xFF6F91, 0xFFB6C9), "heart", Unlock.Coins(600)),
        Trail("yildiz", "Yıldız İzi", intArrayOf(0xFFD54F, 0xFFF3C4), "star", Unlock.Coins(900)),
        Trail("baloncuk", "Baloncuk İzi", intArrayOf(0x87CEFA, 0xD6F0FF), "bubble", Unlock.Gems(25)),
        Trail("nota", "Nota İzi", intArrayOf(0xB388FF, 0x58D6D0), "note", Unlock.Gems(40)),
        Trail("gokkusagi", "Gökkuşağı İzi", intArrayOf(0xFF6F91, 0xFFD54F, 0x73D65C, 0x58D6D0, 0xB388FF), "dot", Unlock.Collection(2)),
        Trail("konfeti", "Konfeti İzi", intArrayOf(0xFF9F43, 0x58D6D0, 0xFFD54F, 0xFF6F91), "confetti", Unlock.Collection(6)),
        Trail("kristal", "Kristal İzi", intArrayOf(0xA7E8FF, 0xE6F9FF, 0xC9B6FF), "crystal", Unlock.Collection(8)),
        Trail("atesbocegi", "Ateş Böceği İzi", intArrayOf(0xFFF59D, 0xC6FF8A), "glow", Unlock.PlayerLevel(50)),
    )

    val shopPowerups: Map<PowerupType, Int> = linkedMapOf(
        PowerupType.SHIELD to 150, PowerupType.MAGNET to 120, PowerupType.DOUBLE_JUMP to 150, PowerupType.INVINCIBLE to 200,
        PowerupType.EXTRA_LIFE to 250, PowerupType.COIN_MULTIPLIER to 150, PowerupType.STAR_FINDER to 120, PowerupType.CHECKPOINT_GUARD to 180,
    )

    /** Elmasla altın paketi (oyun içi dönüşüm, gerçek para yok). */
    val coinPacks = listOf(10 to 500, 25 to 1400, 60 to 3800)

    val loginRewards = listOf(
        LoginReward(coins = 100),
        LoginReward(coins = 150, powerups = mapOf(PowerupType.MAGNET to 1)),
        LoginReward(gems = 5),
        LoginReward(coins = 250, powerups = mapOf(PowerupType.SHIELD to 1)),
        LoginReward(powerups = mapOf(PowerupType.DOUBLE_JUMP to 1, PowerupType.EXTRA_LIFE to 1)),
        LoginReward(gems = 10),
        LoginReward(coins = 500, gems = 20),
    )

    val dailyPool = listOf(
        MissionTemplate(MissionType.COMPLETE_LEVELS, intArrayOf(3, 5), 120, 0, 40),
        MissionTemplate(MissionType.COLLECT_COINS, intArrayOf(50, 100, 150), 100, 0, 30),
        MissionTemplate(MissionType.COLLECT_STARS, intArrayOf(5, 10), 120, 0, 40),
        MissionTemplate(MissionType.ENDLESS_SURVIVE, intArrayOf(30, 60, 90), 150, 0, 50),
        MissionTemplate(MissionType.ENDLESS_SCORE, intArrayOf(1000, 2500), 150, 0, 50),
        MissionTemplate(MissionType.PLAY_ENDLESS, intArrayOf(2, 3), 100, 0, 30),
        MissionTemplate(MissionType.SURVIVE_RULES, intArrayOf(15, 30), 120, 0, 40),
        MissionTemplate(MissionType.JUMP, intArrayOf(100, 200), 80, 0, 30),
        MissionTemplate(MissionType.USE_POWERUPS, intArrayOf(2, 3), 100, 0, 30),
        MissionTemplate(MissionType.THREE_STAR, intArrayOf(1, 2), 150, 0, 50),
    )

    val weeklyPool = listOf(
        MissionTemplate(MissionType.COMPLETE_LEVELS, intArrayOf(20, 30), 600, 15, 200),
        MissionTemplate(MissionType.COLLECT_COINS, intArrayOf(800, 1500), 500, 10, 150),
        MissionTemplate(MissionType.COLLECT_STARS, intArrayOf(40, 60), 600, 15, 200),
        MissionTemplate(MissionType.ENDLESS_SURVIVE, intArrayOf(150, 240), 700, 20, 250),
        MissionTemplate(MissionType.ENDLESS_SCORE, intArrayOf(6000, 12000), 700, 20, 250),
        MissionTemplate(MissionType.SURVIVE_RULES, intArrayOf(150, 300), 600, 15, 200),
        MissionTemplate(MissionType.THREE_STAR, intArrayOf(8, 15), 700, 20, 250),
        MissionTemplate(MissionType.COLLECT_PIECE, intArrayOf(2, 3), 800, 25, 250),
        MissionTemplate(MissionType.JUMP, intArrayOf(1500, 2500), 500, 10, 150),
    )

    private fun mapsCompleted(s: SaveData) = (1..10).count { s.isCompleted(it * 50) }.toLong()

    val achievements: List<AchievementDef> = buildList {
        fun a(id: String, title: String, desc: String, target: Long, gems: Int, stat: (SaveData) -> Long) = add(AchievementDef(id, title, desc, stat, target, gems))
        val lv = longArrayOf(1, 10, 25, 50, 100, 200, 300, 400, 500)
        val lvNames = listOf("İlk Adım", "Isınma Turu", "Yolda", "Kararlı Koşucu", "Yüzler Kulübü", "İki Yüz", "Üç Yüz", "Dört Yüz", "Efsane Koşucu")
        lv.forEachIndexed { i, n -> a("levels_$n", lvNames[i], "$n bölüm tamamla", n, 5 + i * 5) { it.stats.levelsCompleted } }
        val st = longArrayOf(10, 50, 150, 300, 600, 1000, 1500)
        val stNames = listOf("Yıldız Avcısı", "Parıltı", "Yıldız Yağmuru", "Takımyıldızı", "Galaksi", "Samanyolu", "Gökyüzünün Sahibi")
        st.forEachIndexed { i, n -> a("stars_$n", stNames[i], "Toplam $n yıldız kazan", n, 5 + i * 5) { it.totalStars.toLong() } }
        val co = longArrayOf(100, 1000, 5000, 20000)
        val coNames = listOf("Kumbara", "Altın Kesesi", "Hazine Sandığı", "Altın Dağı")
        co.forEachIndexed { i, n -> a("coins_$n", coNames[i], "Toplam $n altın topla", n, 5 + i * 5) { it.stats.coinsCollected } }
        val es = longArrayOf(1000, 5000, 15000, 40000)
        val esNames = listOf("Sonsuza İlk Adım", "Sonsuz Yolcu", "Sonsuz Usta", "Sonsuzluğun Efendisi")
        es.forEachIndexed { i, n -> a("endless_$n", esNames[i], "Sonsuz modda $n puan yap", n, 10 + i * 5) { it.stats.endlessBestScore } }
        val et = longArrayOf(60, 180, 300)
        val etNames = listOf("Bir Dakika", "Üç Dakika", "Beş Dakika")
        et.forEachIndexed { i, n -> a("survive_$n", etNames[i], "Sonsuz modda $n saniye dayan", n, 10 + i * 5) { it.stats.endlessBestSeconds.toLong() } }
        val ju = longArrayOf(100, 1000)
        listOf("Zıp Zıp", "Zıplama Ustası").forEachIndexed { i, t -> a("jumps_${ju[i]}", t, "${ju[i]} kez zıpla", ju[i], 5 + i * 5) { it.stats.jumps } }
        val ru = longArrayOf(50, 500, 2000)
        listOf("Uyum Sağla", "Kural Bükücü", "Kuralların Hakimi").forEachIndexed { i, t -> a("rules_${ru[i]}", t, "${ru[i]} kural değişimi atlat", ru[i], 5 + i * 10) { it.stats.rulesSurvived } }
        val mc = longArrayOf(1, 3, 5, 10)
        listOf("İlk Harita", "Gezgin", "Kâşif", "Bütün Dünyalar").forEachIndexed { i, t -> a("maps_${mc[i]}", t, "${mc[i]} haritayı bitir", mc[i], 10 + i * 10) { s -> mapsCompleted(s) } }
        val pc = longArrayOf(1, 10, 25, 50)
        listOf("Gizli Hazine", "Koleksiyoncu", "Arşivci", "Tam Koleksiyon").forEachIndexed { i, t -> a("pieces_${pc[i]}", t, "${pc[i]} gizli parça bul", pc[i], 5 + i * 10) { it.pieces.size.toLong() } }
        a("perfect_10", "Kusursuz", "10 bölümü hiç ölmeden 3 yıldızla bitir", 10, 15) { it.stats.perfectLevels }
        a("perfect_50", "Mükemmeliyetçi", "50 bölümü hiç ölmeden 3 yıldızla bitir", 50, 30) { it.stats.perfectLevels }
        a("power_10", "Güç Toplayıcı", "10 güçlendirme kullan", 10, 5) { it.stats.powerupsUsed }
        a("power_100", "Güç Ustası", "100 güçlendirme kullan", 100, 15) { it.stats.powerupsUsed }
        a("skins_5", "Gardırop", "5 karakter görünümü aç", 5, 10) { it.ownedSkins.size.toLong() }
        a("skins_10", "Moda İkonu", "10 karakter görünümü aç", 10, 20) { it.ownedSkins.size.toLong() }
        a("skins_20", "Tam Kadro", "20 karakter görünümünün hepsini aç", 20, 40) { it.ownedSkins.size.toLong() }
        a("plevel_10", "Deneyimli", "Oyuncu seviyesi 10'a ulaş", 10, 10) { it.playerLevel.toLong() }
        a("plevel_25", "Usta", "Oyuncu seviyesi 25'e ulaş", 25, 20) { it.playerLevel.toLong() }
        a("plevel_50", "Büyük Usta", "Oyuncu seviyesi 50'ye ulaş", 50, 40) { it.playerLevel.toLong() }
    }

    fun skin(id: String) = skins.firstOrNull { it.id == id } ?: skins[0]
    fun trail(id: String) = trails.firstOrNull { it.id == id } ?: trails[0]
}
