package com.emirzinciri.robotkomutan.logic.meta

import com.emirzinciri.robotkomutan.logic.factory.BuildingType
import com.emirzinciri.robotkomutan.logic.progress.SaveData
import kotlinx.serialization.Serializable

/** Ömür boyu sayaçlar (olaylarla artar). */
enum class Counter { WINS, FAILS, STARS_EARNED, THREE_STARS, ENEMIES, CARDS_PLACED, BUILD_UPGRADES, RESEARCH, TRADES,
    QUESTS, DAILY_PUZZLES, ENDLESS_FLOORS, GOLD_EARNED, SKINS, BOSSES, EXPEDITIONS, HARD_CLEARS, PRESTIGE }

/** Başarım ve görevlerin ölçtüğü değerler. Bir kısmı kayıttan türetilir. */
enum class Metric(val title: String) {
    LEVELS_COMPLETED("Tamamlanan bölüm"), STARS("Toplam yıldız"), THREE_STAR_LEVELS("Üç yıldızlı bölüm"),
    AREAS_COMPLETED("Tamamlanan alan"), WINS("Başarılı deneme"), FAILS("Başarısız deneme"), ENEMIES("Etkisizleştirilen düşman"),
    CARDS_PLACED("Yerleştirilen kart"), BUILD_UPGRADES("Bina yükseltmesi"), RESEARCH("Araştırma"), TRADES("Ticaret"),
    QUESTS("Tamamlanan görev"), ENDLESS_BEST("Sonsuz Fabrika rekoru"), DAILY_PUZZLES("Günlük bulmaca"),
    GOLD_EARNED("Kazanılan altın"), SKINS("Robot görünümü"), BOSSES("Yenilen patron");
}

@Serializable
data class QuestState(
    val id: String,
    val metric: Metric,
    val target: Long,
    val baseline: Long,
    val gold: Long,
    val chips: Long = 0,
    var claimed: Boolean = false,
)

@Serializable
data class EndlessState(
    var bestFloor: Int = 0,
    var floor: Int = 0,
    var seed: Long = 0,
    var lastDailyDay: Long = -1,
    var dailyBestCards: Int = 0,
)

@Serializable
data class MetaState(
    val counters: MutableMap<Counter, Long> = mutableMapOf(),
    var dailyKey: Long = -1,
    var weeklyKey: Long = -1,
    val daily: MutableList<QuestState> = mutableListOf(),
    val weekly: MutableList<QuestState> = mutableListOf(),
    val achievementsClaimed: MutableSet<String> = mutableSetOf(),
    var endless: EndlessState = EndlessState(),
    val ownedSkins: MutableSet<String> = mutableSetOf("classic"),
    var skin: String = "classic",
    val expeditions: MutableList<Expedition> = mutableListOf(),
    val collection: MutableSet<Int> = mutableSetOf(),
    val setsRewarded: MutableSet<Int> = mutableSetOf(),
    /** Zor kuralla (kart sınırı = par, çarpma yok) tamamlanan bölümler. */
    val hardCleared: MutableSet<Int> = mutableSetOf(),
    /** Yeni Komuta Çağı (prestij) seviyesi. */
    var prestige: Int = 0,
    val customLevels: MutableList<com.emirzinciri.robotkomutan.logic.level.LevelDef> = mutableListOf(),
)

data class Achievement(val id: String, val metric: Metric, val threshold: Long, val title: String, val gold: Long)

/** Görevler, başarımlar ve sayaçlar. Saf mantık; tarih gün/hafta numarası olarak dışarıdan verilir. */
class Meta(private val save: SaveData) {
    val state: MetaState get() = save.meta

    fun count(c: Counter, n: Long = 1) { state.counters.merge(c, n, Long::plus) }
    fun counter(c: Counter) = state.counters[c] ?: 0

    fun value(m: Metric): Long = when (m) {
        Metric.LEVELS_COMPLETED -> save.levels.values.count { it.stars > 0 }.toLong()
        Metric.STARS -> save.totalStars.toLong()
        Metric.THREE_STAR_LEVELS -> save.levels.values.count { it.stars == 3 }.toLong()
        Metric.AREAS_COMPLETED -> save.levels.filter { it.value.stars > 0 }.keys.groupBy { (it - 1) / 5 }.count { it.value.size == 5 }.toLong()
        Metric.WINS -> counter(Counter.WINS)
        Metric.FAILS -> counter(Counter.FAILS)
        Metric.ENEMIES -> counter(Counter.ENEMIES)
        Metric.CARDS_PLACED -> save.stats.cardsPlaced
        Metric.BUILD_UPGRADES -> counter(Counter.BUILD_UPGRADES)
        Metric.RESEARCH -> save.factory.research.size.toLong()
        Metric.TRADES -> save.factory.trades
        Metric.QUESTS -> counter(Counter.QUESTS)
        Metric.ENDLESS_BEST -> state.endless.bestFloor.toLong()
        Metric.DAILY_PUZZLES -> counter(Counter.DAILY_PUZZLES)
        Metric.GOLD_EARNED -> counter(Counter.GOLD_EARNED)
        Metric.SKINS -> (state.ownedSkins.size - 1).toLong()
        Metric.BOSSES -> counter(Counter.BOSSES)
    }

    // ------------------------------------------------------------------ görevler

    private data class Template(val metric: Metric, val text: String, val targets: LongArray, val gold: Long)

    private val dailyPool = listOf(
        Template(Metric.WINS, "%d bölüm kazan", longArrayOf(2, 3, 4), 40),
        Template(Metric.STARS, "%d yıldız topla", longArrayOf(3, 5, 6), 45),
        Template(Metric.CARDS_PLACED, "%d kart yerleştir", longArrayOf(20, 30, 40), 30),
        Template(Metric.BUILD_UPGRADES, "Fabrikada %d yükseltme yap", longArrayOf(1, 2), 50),
        Template(Metric.ENDLESS_BEST, "Sonsuz Fabrika'da %d. kata ulaş", longArrayOf(1, 2, 3), 50),
        Template(Metric.DAILY_PUZZLES, "Günlük bulmacayı çöz", longArrayOf(1), 60),
        Template(Metric.ENEMIES, "%d düşmanı etkisizleştir", longArrayOf(1, 2), 45),
    )
    private val weeklyPool = listOf(
        Template(Metric.WINS, "%d bölüm kazan", longArrayOf(10, 15, 20), 200),
        Template(Metric.STARS, "%d yıldız topla", longArrayOf(15, 25), 220),
        Template(Metric.BUILD_UPGRADES, "Fabrikada %d yükseltme yap", longArrayOf(5, 8), 250),
        Template(Metric.QUESTS, "%d günlük görev tamamla", longArrayOf(6, 10), 250),
        Template(Metric.ENEMIES, "%d düşmanı etkisizleştir", longArrayOf(5, 10), 220),
        Template(Metric.TRADES, "%d ticaret yap", longArrayOf(3, 6), 180),
    )

    fun questText(q: QuestState): String {
        val t = (dailyPool + weeklyPool).firstOrNull { it.metric == q.metric } ?: return q.metric.title
        return if ("%d" in t.text) t.text.format(q.target) else t.text
    }

    /** Gün (epoch günü) ve hafta numarasına göre görevleri yeniler; aynı gün için hep aynı görevler üretilir. */
    fun refresh(dayKey: Long, weekKey: Long) {
        if (state.dailyKey != dayKey) {
            state.dailyKey = dayKey
            state.daily.clear(); state.daily += generate(dailyPool, dayKey * 31 + 7, 3, "d$dayKey")
        }
        if (state.weeklyKey != weekKey) {
            state.weeklyKey = weekKey
            state.weekly.clear(); state.weekly += generate(weeklyPool, weekKey * 131 + 3, 3, "w$weekKey")
        }
    }

    private fun generate(pool: List<Template>, seed: Long, n: Int, prefix: String): List<QuestState> {
        val rnd = java.util.Random(seed)
        val picks = pool.shuffled(rnd).take(n)
        return picks.mapIndexed { i, t ->
            val target = t.targets[rnd.nextInt(t.targets.size)]
            // Rekor tabanlı ölçüm: hedef mevcut rekorun üstüne değil, mutlak kat sayısıdır.
            val base = if (t.metric == Metric.ENDLESS_BEST) 0 else value(t.metric)
            val idx = t.targets.indexOf(target)
            QuestState("$prefix-$i", t.metric, target, base, t.gold * (1 + idx), chips = if (prefix.startsWith("w")) 3L + idx else 0)
        }
    }

    fun progress(q: QuestState): Long = (value(q.metric) - q.baseline).coerceIn(0, q.target)
    fun isDone(q: QuestState) = progress(q) >= q.target

    /** Ödülü verir; tamamlanmamış veya alınmış görevde false. */
    fun claim(q: QuestState): Boolean {
        if (q.claimed || !isDone(q)) return false
        q.claimed = true
        save.wallet.gold += q.gold; save.wallet.chips += q.chips
        count(Counter.GOLD_EARNED, q.gold)
        count(Counter.QUESTS)
        return true
    }

    // ------------------------------------------------------------------ başarımlar

    fun isUnlocked(a: Achievement) = value(a.metric) >= a.threshold
    fun isClaimed(a: Achievement) = a.id in state.achievementsClaimed

    fun claim(a: Achievement): Boolean {
        if (!isUnlocked(a) || !state.achievementsClaimed.add(a.id)) return false
        save.wallet.gold += a.gold
        count(Counter.GOLD_EARNED, a.gold)
        return true
    }

    val claimable: Int get() = achievements.count { isUnlocked(it) && !isClaimed(it) } +
        (state.daily + state.weekly).count { isDone(it) && !it.claimed }

    companion object {
        private val tiers: List<Triple<Metric, String, LongArray>> = listOf(
            Triple(Metric.LEVELS_COMPLETED, "Görev Yolu", longArrayOf(1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50)),
            Triple(Metric.STARS, "Yıldız Avcısı", longArrayOf(3, 10, 25, 50, 75, 100, 125, 150)),
            Triple(Metric.THREE_STAR_LEVELS, "Kusursuz Plan", longArrayOf(1, 5, 10, 20, 30, 40, 50)),
            Triple(Metric.WINS, "Zafer Zinciri", longArrayOf(1, 10, 25, 50, 100, 250, 500)),
            Triple(Metric.ENEMIES, "İsyan Bastırıcı", longArrayOf(1, 5, 10, 25, 50, 100)),
            Triple(Metric.CARDS_PLACED, "Kart Ustası", longArrayOf(10, 50, 100, 250, 500, 1000, 2500)),
            Triple(Metric.BUILD_UPGRADES, "Fabrika Mimarı", longArrayOf(1, 5, 10, 20, 40, 60)),
            Triple(Metric.RESEARCH, "Araştırmacı", longArrayOf(1, 3, 6, 9, 13)),
            Triple(Metric.TRADES, "Tüccar", longArrayOf(1, 10, 50)),
            Triple(Metric.QUESTS, "Görev Adamı", longArrayOf(1, 5, 10, 25, 50, 100)),
            Triple(Metric.ENDLESS_BEST, "Sonsuz Yolcu", longArrayOf(1, 3, 5, 10, 15, 20, 30)),
            Triple(Metric.DAILY_PUZZLES, "Günlük Beyin", longArrayOf(1, 7, 30)),
            Triple(Metric.GOLD_EARNED, "Altın Kasası", longArrayOf(100, 500, 1000, 5000, 10000, 50000)),
            Triple(Metric.SKINS, "Koleksiyoncu", longArrayOf(1, 3, 5, 8)),
            Triple(Metric.AREAS_COMPLETED, "Bölge Kurtarıcı", longArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)),
            Triple(Metric.FAILS, "Hatalardan Ders", longArrayOf(1, 10, 50)),
            Triple(Metric.BOSSES, "Patron Avcısı", longArrayOf(1)),
        )

        private val roman = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI")

        val achievements: List<Achievement> = tiers.flatMap { (m, name, ts) ->
            ts.mapIndexed { i, t ->
                Achievement("${m.name}_$t", m, t, if (ts.size > 1) "$name ${roman[i]}" else name, 20L + 15L * i * (i + 1))
            }
        }

        init { check(achievements.size >= 100) { "En az 100 başarım olmalı: ${achievements.size}" } }
    }
}
