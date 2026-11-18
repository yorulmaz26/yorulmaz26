package com.yanlisrenk.logic.progress

import com.yanlisrenk.logic.save.MissionSlot
import kotlin.random.Random

/** İstatistik anahtarları (SaveData.stats). */
object Stat {
    const val LEVELS_PLAYED = "levels_played"
    const val GATES = "gates"
    const val COINS = "coins_collected"
    const val TAPS = "taps"
    const val ENDLESS_RUNS = "endless_runs"
    const val ENDLESS_BEST = "endless_best"
    const val MAX_COMBO = "max_combo"
    const val GOLD_EARNED = "gold_earned"
    const val MISSIONS_DONE = "missions_done"
    const val LOGIN_DAYS = "login_days"
    const val LOGIN_STREAK = "login_streak"
    const val FINALS = "finals"
    const val MASTERY_PLAYED = "mastery_played"
    const val PURCHASES = "purchases"
    const val FLAWLESS = "flawless"
    // türetilmiş (SaveData'dan hesaplanır)
    const val LEVELS_DONE = "#levels_done"
    const val STARS = "#stars"
    const val PERFECT = "#perfect"
    const val AREAS = "#areas"
    const val WORLDS = "#worlds"
    const val CHARACTERS = "#characters"
    const val MASTERY_STARS = "#mastery_stars"
    const val PLAYER_LEVEL = "#player_level"
    const val COLLECTION = "#collection"
}

/** Görev şablonu. [max] → tek seferde ulaşılan değer (ör. tek koşuda puan). */
data class MissionTemplate(val id: String, val title: String, val stat: String, val base: Int, val perLevel: Float, val max: Boolean = false, val icon: String)

object Missions {
    val templates = listOf(
        MissionTemplate("levels", "%d bölüm tamamla", "m_levels", 3, 0.08f, icon = "flag"),
        MissionTemplate("stars", "%d yıldız kazan", "m_stars", 5, 0.2f, icon = "star_full"),
        MissionTemplate("gates", "%d kapıdan geç", "m_gates", 40, 2f, icon = "check"),
        MissionTemplate("coins", "%d renkli altın topla", "m_coins", 15, 0.5f, icon = "coin"),
        MissionTemplate("endless_runs", "Sonsuz modda %d koşu yap", "m_endless_runs", 2, 0.03f, icon = "infinity"),
        MissionTemplate("endless_score", "Sonsuz modda tek koşuda %d puan yap", "m_endless_score", 150, 8f, max = true, icon = "trophy"),
        MissionTemplate("perfect", "%d bölümü ★★★ ile bitir", "m_perfect", 2, 0.05f, icon = "crown"),
        MissionTemplate("taps", "%d kez renk değiştir", "m_taps", 60, 3f, icon = "palette"),
        MissionTemplate("combo", "Sonsuz modda %d kombo yap", "m_combo", 15, 0.6f, max = true, icon = "bolt"),
        MissionTemplate("flawless", "%d bölümü ilk denemede bitir", "m_flawless", 2, 0.04f, icon = "shield"),
        MissionTemplate("mastery", "%d ustalık bölümü tamamla", "m_mastery", 1, 0.03f, icon = "medal"),
    )
    private val byId = templates.associateBy { it.id }
    fun template(id: String) = byId[id]!!

    fun title(slot: MissionSlot): String = template(slot.templateId).title.format(slot.target)

    /** Günlük 3 görev (gün anahtarına göre tohumlu). */
    fun daily(dayKey: Long, playerLevel: Int, masteryUnlocked: Boolean): MutableList<MissionSlot> =
        pick(Random(dayKey * 31 + 7), 3, playerLevel, 1f, masteryUnlocked, 60, 0, 30)

    /** Haftalık 5 görev. */
    fun weekly(weekKey: Long, playerLevel: Int, masteryUnlocked: Boolean): MutableList<MissionSlot> =
        pick(Random(weekKey * 131 + 11), 5, playerLevel, 5f, masteryUnlocked, 300, 5, 120)

    private fun pick(rng: Random, n: Int, lvl: Int, scale: Float, mastery: Boolean, gold: Int, dia: Int, xp: Int): MutableList<MissionSlot> {
        val pool = templates.filter { mastery || it.id != "mastery" }.shuffled(rng).take(n)
        return pool.map { t ->
            val raw = (t.base + t.perLevel * lvl) * (if (t.max) 1f + (scale - 1f) * 0.35f else scale)
            val target = roundNice(raw.toInt().coerceAtLeast(1))
            MissionSlot(t.id, target, rewardGold = gold + lvl * 4, rewardDiamonds = dia + if (rng.nextFloat() < 0.25f) 1 else 0, rewardXp = xp)
        }.toMutableList()
    }

    private fun roundNice(v: Int): Int = when {
        v >= 100 -> (v + 5) / 10 * 10
        v >= 20 -> (v + 2) / 5 * 5
        else -> v
    }
}

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val stat: String,
    val target: Long,
    val gold: Int,
    val diamonds: Int,
    val icon: String,
    /** Ödül olarak açılan ürün (opsiyonel). */
    val unlockItem: String? = null,
)

object Achievements {
    private fun tier(prefix: String, title: String, desc: String, stat: String, icon: String, targets: List<Long>, goldBase: Int, unlockAt: Map<Long, String> = emptyMap()): List<Achievement> =
        targets.mapIndexed { i, t ->
            Achievement("${prefix}_$t", "$title ${roman(i + 1)}", desc.format(t), stat, t, goldBase * (i + 1), (i + 1) * 2, icon, unlockAt[t])
        }

    val all: List<Achievement> by lazy {
        val list = ArrayList<Achievement>()
        list += tier("lvl", "Bölüm Ustası", "%d bölüm tamamla.", Stat.LEVELS_DONE, "flag", listOf(1, 5, 10, 25, 50, 100, 200, 300, 400, 500), 60,
            mapOf(500L to "char_kral"))
        list += tier("star", "Yıldız Avcısı", "Toplam %d yıldız topla.", Stat.STARS, "star_full", listOf(10, 30, 60, 100, 200, 400, 700, 1000, 1250, 1500), 70)
        list += tier("area", "Alan Kâşifi", "%d alanı tamamla.", Stat.AREAS, "map", listOf(1, 5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100), 80)
        list += tier("endless", "Sonsuz Koşucu", "Sonsuz modda tek koşuda %d puan yap.", Stat.ENDLESS_BEST, "infinity", listOf(100, 250, 500, 1000, 2000, 3500, 5000, 7500, 10000, 15000), 60)
        list += tier("combo", "Kombo Kralı", "Sonsuz modda %d kombo yap.", Stat.MAX_COMBO, "bolt", listOf(10, 25, 50, 75, 100), 70)
        list += tier("gold", "Altın Biriktirici", "Toplam %d altın kazan.", Stat.GOLD_EARNED, "coin", listOf(500, 2000, 5000, 10000, 25000, 50000, 100000), 0)
        list += tier("chars", "Karakter Koleksiyoncusu", "%d karaktere sahip ol.", Stat.CHARACTERS, "character", listOf(3, 5, 10, 15, 20, 25, 30), 90)
        list += tier("gates", "Kapı Geçici", "Toplam %d kapıdan geç.", Stat.GATES, "check", listOf(100, 500, 1000, 2500, 5000, 10000, 25000), 50)
        list += tier("perfect", "Mükemmeliyetçi", "%d bölümü ★★★ ile bitir.", Stat.PERFECT, "crown", listOf(10, 50, 100, 250, 500), 100)
        list += tier("missions", "Görev Adamı", "%d görev tamamla.", Stat.MISSIONS_DONE, "missions", listOf(5, 25, 50, 100), 80)
        list += tier("mastery", "Ustalık Yolu", "%d ustalık yıldızı kazan.", Stat.MASTERY_STARS, "medal", listOf(5, 25, 100, 250), 120)
        list += tier("login", "Sadık Oyuncu", "%d gün oyuna gir.", Stat.LOGIN_DAYS, "calendar", listOf(3, 7, 30), 60)
        list += tier("plvl", "Tecrübe", "Oyuncu seviyesi %d'e ulaş.", Stat.PLAYER_LEVEL, "profile", listOf(5, 10, 20, 35, 50), 100)
        list += tier("coll", "Hazine Avcısı", "%d koleksiyon parçası topla.", Stat.COLLECTION, "box", listOf(10, 50, 100), 150)
        list += tier("flawless", "Hatasız", "%d bölümü ilk denemede bitir.", Stat.FLAWLESS, "shield", listOf(10, 50, 150), 90)
        // dünyalar
        com.yanlisrenk.logic.level.AreaCatalog.worlds.forEach { w ->
            list += Achievement("world_${w.id}", w.name, "${w.name} dünyasının 5 alanını tamamla.", "#world_${w.id}", 1, 200 + w.id * 20, 5, "trophy")
        }
        list
    }
    private val byId by lazy { all.associateBy { it.id } }
    fun get(id: String) = byId[id]

    private fun roman(i: Int) = arrayOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII").getOrElse(i - 1) { i.toString() }
}

object PlayerLevels {
    const val MAX_LEVEL = 99
    fun xpForNext(level: Int): Long = 120L + (level - 1) * 60L
    /** Seviye ödülü: altın + her 5 seviyede elmas. */
    fun reward(level: Int): Pair<Int, Int> = (100 + level * 20) to (if (level % 5 == 0) 5 + level / 5 else 0)
}

data class DailyReward(val day: Int, val gold: Int = 0, val diamonds: Int = 0, val item: String? = null, val itemCount: Int = 0, val label: String)

object DailyRewards {
    val cycle = listOf(
        DailyReward(1, gold = 150, label = "150 Altın"),
        DailyReward(2, gold = 250, label = "250 Altın"),
        DailyReward(3, item = "boost_shield", itemCount = 1, label = "Can Kalkanı"),
        DailyReward(4, gold = 400, label = "400 Altın"),
        DailyReward(5, diamonds = 5, label = "5 Elmas"),
        DailyReward(6, item = "life_full", itemCount = 1, label = "Tam Can"),
        DailyReward(7, gold = 500, diamonds = 15, label = "500 Altın + 15 Elmas"),
    )
}

/** Haftalık mücadele: haftaya göre sabit tohumlu sonsuz koşu + ödül basamakları. */
object WeeklyChallenge {
    val tiers = listOf(200 to (200 to 0), 500 to (300 to 3), 1000 to (500 to 6), 2000 to (800 to 12))
    val themes = listOf(
        "Ayna Haftası" to listOf(com.yanlisrenk.logic.model.Mechanic.MIRROR),
        "Hafıza Haftası" to listOf(com.yanlisrenk.logic.model.Mechanic.MEMORY, com.yanlisrenk.logic.model.Mechanic.CHAIN),
        "Hız Haftası" to listOf(com.yanlisrenk.logic.model.Mechanic.SPEED, com.yanlisrenk.logic.model.Mechanic.WAVE),
        "Ters Kapı Haftası" to listOf(com.yanlisrenk.logic.model.Mechanic.DIFF, com.yanlisrenk.logic.model.Mechanic.NONE),
        "Hareket Haftası" to listOf(com.yanlisrenk.logic.model.Mechanic.ROTATE, com.yanlisrenk.logic.model.Mechanic.TIMED, com.yanlisrenk.logic.model.Mechanic.SLIDE),
        "Bilmece Haftası" to listOf(com.yanlisrenk.logic.model.Mechanic.NEXT, com.yanlisrenk.logic.model.Mechanic.PREV, com.yanlisrenk.logic.model.Mechanic.REPEAT),
        "Gizem Haftası" to listOf(com.yanlisrenk.logic.model.Mechanic.HIDDEN, com.yanlisrenk.logic.model.Mechanic.FLICKER, com.yanlisrenk.logic.model.Mechanic.DECOY),
    )
    fun theme(weekKey: Long) = themes[Math.floorMod(weekKey, themes.size.toLong()).toInt()]
}
