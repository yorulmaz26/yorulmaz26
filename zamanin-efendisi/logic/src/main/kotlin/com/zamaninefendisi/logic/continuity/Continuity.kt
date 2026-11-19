package com.zamaninefendisi.logic.continuity

import com.zamaninefendisi.logic.Era
import com.zamaninefendisi.logic.meta.Resource
import kotlinx.serialization.Serializable
import kotlin.random.Random

// ====================================================================== Takvim

object GameCalendar {
    private const val DAY = 86_400_000L
    /** Yerel güne göre gün numarası (çevrimdışı; cihaz saatine dayanır). */
    fun day(wallMs: Long, tzOffsetMs: Long): Int = Math.floorDiv(wallMs + tzOffsetMs, DAY).toInt()
    /** Pazartesi başlangıçlı hafta numarası. */
    fun week(day: Int): Int = Math.floorDiv(day + 3, 7)
}

// ====================================================================== Görevler

enum class QuestType(val title: String) {
    WIN_LEVELS("bölüm kazan"),
    THREE_STARS("bölümü 3 yıldızla bitir"),
    SWITCH_ERAS("zaman geçişi yap"),
    MAKE_CHANGES("kalıcı değişiklik yap"),
    DEFEAT_ENEMIES("düşman yen"),
    PARADOXES("paradoks yaşa"),
    COLLECT_PRODUCTION("kale üretimini topla"),
    UPGRADE_BUILDING("bina geliştir"),
    EXPEDITION("zaman seferini tamamla"),
    DAILY_PUZZLE("Günlük Zaman Bulmacası'nı çöz"),
    ENDLESS_STAGES("Sonsuz Mod aşaması geç"),
    DEFENSE_WAVES("Krallık Savunması dalgası geç"),
}

@Serializable
data class Quest(val type: QuestType, val target: Int, val reward: Map<Resource, Int>)

@Serializable
data class QuestBoard(val period: Int = Int.MIN_VALUE, val quests: List<Quest> = emptyList(), val progress: Map<QuestType, Int> = emptyMap(), val claimed: Set<Int> = emptySet(), val startedWallMs: Long = 0)

object Quests {
    private val dailyPool = listOf(
        QuestType.WIN_LEVELS to 2, QuestType.THREE_STARS to 1, QuestType.SWITCH_ERAS to 8, QuestType.MAKE_CHANGES to 6, QuestType.DEFEAT_ENEMIES to 4,
        QuestType.COLLECT_PRODUCTION to 2, QuestType.UPGRADE_BUILDING to 1, QuestType.DAILY_PUZZLE to 1, QuestType.ENDLESS_STAGES to 2, QuestType.PARADOXES to 1,
        QuestType.EXPEDITION to 1, QuestType.DEFENSE_WAVES to 1,
    )

    fun daily(day: Int, wallMs: Long): QuestBoard {
        val r = Random(day * 7919L + 11)
        val picks = dailyPool.shuffled(r).take(3)
        return QuestBoard(day, picks.map { (t, n) -> Quest(t, n, mapOf(Resource.GOLD to 40 + 10 * n.coerceAtMost(5), Resource.SCIENCE to 10)) }, startedWallMs = wallMs)
    }

    fun weekly(week: Int, wallMs: Long): QuestBoard {
        val r = Random(week * 104729L + 3)
        val picks = dailyPool.shuffled(r).take(3)
        return QuestBoard(week, picks.map { (t, n) -> Quest(t, n * 5, mapOf(Resource.GOLD to 200, Resource.CRYSTAL to 3, Resource.SHARD to 1)) }, startedWallMs = wallMs)
    }
}

// ====================================================================== Zaman seferleri

enum class ExpeditionKind(val era: Era, val title: String, val hours: Int, val reward: Map<Resource, Int>, val itemChance: Int) {
    PAST_SHORT(Era.PAST, "Taş Devri Keşfi", 1, mapOf(Resource.WOOD to 40, Resource.STONE to 20, Resource.GOLD to 30), 30),
    PRESENT_MID(Era.PRESENT, "Krallık Kervanı", 4, mapOf(Resource.GOLD to 140, Resource.IRON to 25, Resource.SCIENCE to 30), 60),
    FUTURE_LONG(Era.FUTURE, "Yıldız Şehri Seferi", 8, mapOf(Resource.ENERGY to 120, Resource.CRYSTAL to 2, Resource.SCIENCE to 80, Resource.SHARD to 1), 100),
}

@Serializable
data class Expedition(val id: Int, val kind: ExpeditionKind, val progressMs: Long = 0) {
    val durationMs: Long get() = kind.hours * 3_600_000L
    val done: Boolean get() = progressMs >= durationMs
}

// ====================================================================== Koleksiyon ve müze

class CollectionItem(val id: String, val era: Era, val set: String, val title: String)

/** 150 parçalık Medeniyet Koleksiyonu: 3 çağ × 5 set × 10 eser. */
object CivCollection {
    private val sets = mapOf(
        Era.PAST to listOf(
            "İlk Ateş" to listOf("Çakmaktaşı", "Kemik İğne", "Ateş Taşı", "Kil Kandil", "Mağara Boyası", "Ocak Taşı", "Kuru Ot Demeti", "Reçine Meşale", "Kömür Kalemi", "Ateş Bekçisi Kolyesi"),
            "Avcılar" to listOf("Taş Mızrak", "Kemik Olta", "Deri Sadak", "Yay Kirişi", "Av Düdüğü", "Tuzak İpi", "Boynuz Bıçak", "Kürk Pelerin", "Ok Ucu", "Avcı Totemi"),
            "Toprak Ustaları" to listOf("Kil Testi", "Tahıl Taşı", "Ahşap Saban", "Tohum Kesesi", "Hasat Orağı", "Değirmen Taşı", "Su Kabağı", "Örme Sepet", "Toprak Mühür", "Bereket Heykelciği"),
            "Köy Hayatı" to listOf("Kerpiç Tuğla", "Dokuma Tezgâhı", "Bakır Halka", "Taş Kapı Tokmağı", "Ahşap Kaşık", "Yün Yumağı", "Tahta Oyuncak", "Kil Düdük", "Ocak Kepçesi", "Köy Davulu"),
            "Kadim Ritüeller" to listOf("Güneş Diski", "Ay Taşı", "Ruh Maskesi", "Yıldız Haritası", "Kurban Sunağı", "Şaman Asası", "Kemik Zar", "Rüya Tılsımı", "Mezar Taşı", "Zaman Tohumu"),
        ),
        Era.PRESENT to listOf(
            "Şövalyeler" to listOf("Yaldızlı Miğfer", "Arma Kalkanı", "Mahmuz", "Zincir Zırh", "Mızrak Bayrağı", "Kılıç Kını", "At Zırhı", "Şövalye Mührü", "Turnuva Kupası", "Yemin Yüzüğü"),
            "Tüccarlar" to listOf("Altın Terazi", "Baharat Sandığı", "İpek Topu", "Kervan Feneri", "Gümüş Sikke", "Hesap Defteri", "Liman Haritası", "Mühürlü Mektup", "Pusula", "Lonca Rozeti"),
            "Kale Ustaları" to listOf("Mancınık Modeli", "Kale Planı", "Taş Kalıbı", "Demir Menteşe", "Sur Anahtarı", "Burç Çanı", "Gözcü Dürbünü", "Asma Köprü Zinciri", "Kuşatma Kulesi", "Usta Gönyesi"),
            "Âlimler" to listOf("Usturlap", "El Yazması", "Kum Saati", "Mürekkep Hokkası", "Simya Şişesi", "Gök Küresi", "Tüy Kalem", "Mercek", "Harita Atlası", "Bilge Cübbesi"),
            "Sanatçılar" to listOf("Ud", "Fresk Parçası", "Mozaik Taşı", "Hat Levhası", "Çini Tabak", "Kabartma Pano", "Minyatür", "Tezhip Fırçası", "Kristal Vazo", "Ozan Sazı"),
        ),
        Era.FUTURE to listOf(
            "Işık Mimarları" to listOf("Işık Prizması", "Holo Plan", "Foton Tuğla", "Kristal Kiriş", "Işık Geçidi Çekirdeği", "Ayna Panel", "Parıltı Lambası", "Kuantum Cetvel", "Yansıma Küresi", "Mimar Hologramı"),
            "Robot Atölyesi" to listOf("Dişli Kalp", "Servo Kol", "Robot Göz", "Pil Hücresi", "Mantık Çipi", "Manyetik Ayak", "Antenli Kask", "Yağlama Kutusu", "Kod Kristali", "Mini Koruyucu"),
            "Enerji Bilimi" to listOf("Plazma Şişesi", "Rüzgâr Kanatçığı", "Güneş Yaprağı", "Füzyon Çekirdeği", "Enerji Bobini", "Akım Ölçer", "İyon Taşı", "Işın Lensi", "Statik Küre", "Sonsuz Pil"),
            "Gök Kâşifleri" to listOf("Yıldız Pusulası", "Uzay Tohumu", "Göktaşı Parçası", "Yörünge Haritası", "Hafif Kanat", "Nebula Tozu", "Uydu Modeli", "Kuyruklu Yıldız Kristali", "Sessizlik Kaskı", "Kâşif Rozeti"),
            "Zaman Bekçileri" to listOf("Paradoks Pusulası", "Donmuş An", "Çift Saat", "Çatlak Kum Saati", "Yankı Kristali", "Zincir Mühür", "Ters Akış Taşı", "Zaman Anahtarı", "Kayıp Saniye", "Çekirdek Parçası"),
        ),
    )

    val items: List<CollectionItem> = sets.flatMap { (era, list) ->
        list.flatMapIndexed { si, (setName, names) -> names.mapIndexed { i, n -> CollectionItem("c_${era.ordinal}_${si}_$i", era, setName, n) } }
    }

    fun item(id: String) = items.firstOrNull { it.id == id }
    fun setsOf(era: Era): List<String> = items.filter { it.era == era }.map { it.set }.distinct()

    /** Sahip olunmayan eserlerden tohuma göre birini seçer (hepsi toplandıysa null). */
    fun roll(owned: Set<String>, seed: Long, era: Era? = null): CollectionItem? {
        val pool = items.filter { it.id !in owned && (era == null || it.era == era) }.ifEmpty { items.filter { it.id !in owned } }
        if (pool.isEmpty()) return null
        return pool[Random(seed).nextInt(pool.size)]
    }

    fun completedSets(owned: Set<String>): List<String> = items.groupBy { "${it.era.ordinal}/${it.set}" }.filter { (_, l) -> l.all { it.id in owned } }.keys.toList()
}

object Museum {
    val halls = listOf(
        "Giriş Salonu" to 0, "Geçmiş Salonu" to 10, "Krallık Salonu" to 30, "Gelecek Salonu" to 60, "Paradoks Galerisi" to 100, "Zaman Çekirdeği Salonu" to 150,
    )

    /** Salon açık mı: yeterli eser ve yeterli Tarih Müzesi seviyesi. */
    fun hallOpen(index: Int, items: Int, museumLevel: Int): Boolean = items >= halls[index].second && museumLevel >= index
}

// ====================================================================== Başarımlar

class Achievement(val id: String, val title: String, val description: String, val reward: Int)

object Achievements {
    val all = listOf(
        Achievement("first_win", "İlk Onarım", "İlk bölümü tamamla", 1),
        Achievement("area1", "İlk Kırılma Kapandı", "Alan 1'i tamamla", 2),
        Achievement("area5", "Enerji Ustası", "Alan 5'i tamamla", 3),
        Achievement("area7", "Krallığın Kurtarıcısı", "Alan 7'yi tamamla", 5),
        Achievement("stars30", "Yıldız Toplayıcı", "30 yıldız topla", 2),
        Achievement("stars90", "Yıldız Avcısı", "90 yıldız topla", 5),
        Achievement("switch100", "Çağ Gezgini", "100 zaman geçişi yap", 2),
        Achievement("changes200", "Tarih Yazarı", "200 kalıcı değişiklik yap", 3),
        Achievement("paradox10", "Paradoks Bilgini", "10 paradoks yaşa", 2),
        Achievement("enemies50", "Muhafız", "50 düşman yen", 3),
        Achievement("combo5", "Zincirci", "Tek bölümde 2 kombo yap ya da toplam en yüksek kombon 2 olsun", 2),
        Achievement("core3", "Büyüyen Kale", "Zaman Çekirdeği'ni seviye 3'e çıkar", 2),
        Achievement("core5", "Zaman Sarayı", "Zaman Çekirdeği'ni seviye 5'e çıkar", 5),
        Achievement("research5", "Araştırmacı", "5 araştırma tamamla", 2),
        Achievement("research20", "Zaman Bilgesi", "Bütün araştırmaları tamamla", 8),
        Achievement("collect25", "Koleksiyoncu", "25 eser topla", 2),
        Achievement("collect75", "Küratör", "75 eser topla", 4),
        Achievement("collect150", "Medeniyet Arşivcisi", "150 eserin hepsini topla", 10),
        Achievement("set1", "Tam Set", "Bir koleksiyon setini tamamla", 2),
        Achievement("endless10", "Sonsuz Yolcu", "Sonsuz Mod'da 10. aşamaya ulaş", 3),
        Achievement("endless25", "Zamanın Ötesi", "Sonsuz Mod'da 25. aşamaya ulaş", 6),
        Achievement("daily7", "Sadık Muhafız", "7 Günlük Zaman Bulmacası çöz", 3),
        Achievement("weekly1", "Kriz Yöneticisi", "Bir Haftalık Zaman Krizi'ni çöz", 2),
        Achievement("exped5", "Kâşif", "5 zaman seferi tamamla", 2),
        Achievement("defense5", "Kale Kalkanı", "Krallık Savunması'nda 5. dalgayı geç", 3),
        Achievement("alt5", "Alternatif Tarihçi", "5 alternatif tarih görevi tamamla", 3),
        Achievement("prestige1", "Yeni Döngü", "Bir Zaman Döngüsü'nü tamamla", 10),
        Achievement("master10", "Zaman Ustası", "Zaman Ustası seviye 10'a ulaş", 4),
        Achievement("shop5", "Zevk Sahibi", "Marketten 5 ürün al", 2),
        Achievement("quests10", "Görev Adamı", "10 görev ödülü topla", 3),
    )
}

// ====================================================================== Rastgele zaman olayları

enum class TimeEvent(val title: String, val text: String) {
    STORM("Zaman Fırtınası", "Bir zaman fırtınası kaleyi sardı: biriken üretim %50 arttı!"),
    MERCHANT("Kayıp Tüccar", "Başka bir çağdan gelen tüccar 60 altına 2 Zaman Kristali bıraktı."),
    CRYSTAL_RAIN("Kristal Yağmuru", "Gökyüzünden kristaller yağdı: +3 Zaman Kristali."),
    VISITOR("Geçmişten Gelen Ziyaretçi", "Bir ziyaretçi müzene bir eser bağışladı."),
    DISCOVERY("Teknoloji Keşfi", "Laboratuvarda beklenmedik bir keşif: +40 Bilim Puanı."),
    RIFT("Zaman Çatlağı", "Bir zaman çatlağı süren seferlerini 1 saat ileri taşıdı."),
}

// ====================================================================== Alternatif tarih görevleri

enum class AltTaskKind(val title: String, val description: String) {
    FLAWLESS("Kusursuz Zaman", "Hiç paradoks yaşamadan 3 yıldızla kazan."),
    FEW_SWITCHES("Az Geçiş", "Çözümün gerektirdiğinden fazla zaman geçişi yapmadan kazan."),
    PEACEFUL("Savaştan Önce", "Zaman Muhafızı hiç saldırmadan kazan: şehri savaş başlamadan kurtar."),
    NO_WAIT("Kesintisiz", "Hiç beklemeden ve par değerini aşmadan kazan."),
}

// ====================================================================== Kalıcı süreklilik durumu

@Serializable
data class EndlessRun(val seed: Long, val stage: Int)

@Serializable
data class ContinuityState(
    val endlessBest: Int = 0,
    val endlessRun: EndlessRun? = null,
    val dailyBest: Map<Int, Int> = emptyMap(),
    val dailyClaimed: Set<Int> = emptySet(),
    val weeklyClaimed: Set<Int> = emptySet(),
    val dailyQuests: QuestBoard = QuestBoard(),
    val weeklyQuests: QuestBoard = QuestBoard(),
    val questsClaimedTotal: Int = 0,
    val fastestQuestMs: Long = 0,
    val expeditions: List<Expedition> = emptyList(),
    val expeditionCounter: Int = 0,
    val expeditionsDone: Int = 0,
    val expClockWall: Long = 0,
    val expClockElapsed: Long = 0,
    val expClockBoot: String = "",
    val collection: Set<String> = emptySet(),
    val achievements: Set<String> = emptySet(),
    val altDone: Set<String> = emptySet(),
    val prestige: Int = 0,
    val cycleDone: Set<Int> = emptySet(),
    val defenseBest: Int = 0,
    val defenseWave: Int = 0,
    val shopBought: Int = 0,
    val playMs: Long = 0,
    val lastEventPlayMs: Long = 0,
    val eventCounter: Int = 0,
    val lastEvent: TimeEvent? = null,
)
