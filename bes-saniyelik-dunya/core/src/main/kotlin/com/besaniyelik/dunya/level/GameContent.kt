package com.besaniyelik.dunya.level

import com.besaniyelik.dunya.sim.Mechanic

/** Bölüm desenleri (engel düzenleri). */
enum class Pattern {
    GAP, SPIKES, STEP, FLOATING, SAW, MOVING_SAW, MOVING_PLATFORM, SPRING_WALL, BOOST_GAP,
    GHOST_BRIDGE, SHIFT_BRIDGE, LOW_TUNNEL, BOUNCY_WALL, OVERHEAD, CEILING_GAP, STAIRS, SPIKE_GAP,
}

class MapDef(
    val id: Int,
    val name: String,
    val subtitle: String,
    /** 101+ bölümlerde öne çıkan mekanikler. */
    val focus: List<Mechanic>,
    val nouns: List<String>,
    val adjectives: List<String>,
) {
    val firstLevel get() = (id - 1) * 50 + 1
    val lastLevel get() = id * 50
}

class StageIdea(
    val title: String,
    val desc: String,
    val patterns: List<Pattern>,
    val favored: List<Mechanic> = emptyList(),
    val combo: Int = 0,
)

object GameContent {
    const val LEVEL_COUNT = 500
    const val MAP_COUNT = 10
    const val LEVELS_PER_MAP = 50
    const val PIECES_PER_MAP = 5

    val maps = listOf(
        MapDef(1, "Yeşil Vadi", "Güneşli çayırlar ve sevimli tepeler", listOf(Mechanic.NORMAL),
            listOf("Çayır", "Tepe", "Patika", "Bahçe", "Koru", "Dere", "Yamaç", "Vadi", "Bayır", "Çimenlik"),
            listOf("Güneşli", "Çiçekli", "Yemyeşil", "Neşeli", "Rüzgârlı", "Sakin", "Mis Kokulu", "Parlak", "Serin", "Tatlı")),
        MapDef(2, "Ters Gökyüzü", "Bulutlar, uçan adalar ve ters dönen dünya", listOf(Mechanic.REVERSE_GRAVITY, Mechanic.BOUNCY),
            listOf("Bulut", "Ada", "Gökkuşağı", "Rüzgâr", "Kule", "Köprü", "Yıldızlık", "Balon", "Esinti", "Gökyolu"),
            listOf("Pamuk", "Uçan", "Ters", "Pembe", "Süzülen", "Yüksek", "Hafif", "Mavi", "Dönen", "Sisli")),
        MapDef(3, "Hız Çölü", "Altın kumlar ve hız tünelleri", listOf(Mechanic.FAST, Mechanic.REVERSE_GRAVITY),
            listOf("Kum Tepesi", "Vaha", "Kanyon", "Serap", "Tünel", "Kervan Yolu", "Kaktüs", "Kum Fırtınası", "Pist", "Çöl"),
            listOf("Sıcak", "Altın", "Hızlı", "Uçsuz", "Turuncu", "Kızgın", "Rüzgârlı", "Coşkulu", "Gizemli", "Parıltılı")),
        MapDef(4, "Buz Krallığı", "Kaygan buzlar, karlar ve kristaller", listOf(Mechanic.ICE, Mechanic.SLOW),
            listOf("Buzul", "Kristal", "Kar Tepesi", "Saray", "Mağara", "Göl", "Çığ", "Kartopu", "Kule", "Vadi"),
            listOf("Buzlu", "Kristal", "Karlı", "Parlak", "Kaygan", "Soğuk", "Beyaz", "Masmavi", "Donmuş", "Işıltılı")),
        MapDef(5, "Hayalet Ormanı", "Işıklı mantarlar ve kaybolan platformlar", listOf(Mechanic.GHOST, Mechanic.SMALL),
            listOf("Mantar", "Koru", "Patika", "Ağaç Evi", "Ateş Böceği", "Çalı", "Sis", "Pınar", "Yaprak", "Kovuk"),
            listOf("Büyülü", "Işıklı", "Gizemli", "Mor", "Parıldayan", "Sessiz", "Fısıldayan", "Renkli", "Saklı", "Rüya")),
        MapDef(6, "Zıplayan Şehir", "Yaylı çatılar ve hareketli yapılar", listOf(Mechanic.BOUNCY, Mechanic.SHIFTING),
            listOf("Çatı", "Meydan", "Cadde", "Gökdelen", "Park", "Köprü", "Vinç", "Balkon", "Sokak", "Kule"),
            listOf("Yaylı", "Renkli", "Hareketli", "Yüksek", "Neşeli", "Kalabalık", "Parlak", "Hızlı", "Şen", "Modern")),
        MapDef(7, "Minik Dünya", "Dev çiçekler, büyük mantarlar ve küçük geçitler", listOf(Mechanic.SMALL, Mechanic.GHOST),
            listOf("Çiçek", "Mantar", "Yaprak", "Karınca Yolu", "Damla", "Polen", "Sap", "Taş", "Kök", "Böcek Köyü"),
            listOf("Dev", "Minik", "Pembe", "Gizli", "Sarı", "Kocaman", "Sevimli", "Yeşil", "Dar", "Tatlı")),
        MapDef(8, "Ayna Diyarı", "Parlak kristaller, aynalar ve yansımalar", listOf(Mechanic.REVERSE_CONTROL, Mechanic.REVERSE_GRAVITY),
            listOf("Ayna", "Yansıma", "Kristal", "Prizma", "Salon", "Koridor", "Işık", "Kubbe", "Gökkuşağı", "Labirent"),
            listOf("Parlak", "Ters", "Yansıyan", "Kristal", "Işıltılı", "Gümüş", "Renkli", "Büyülü", "Sırlı", "Şeffaf")),
        MapDef(9, "Zaman Şehri", "Saat kuleleri, dişliler ve mekanizmalar", listOf(Mechanic.SLOW, Mechanic.SHIFTING, Mechanic.FAST),
            listOf("Saat Kulesi", "Dişli", "Sarkaç", "Mekanizma", "Kum Saati", "Takvim", "Zemberek", "Çark", "Meydan", "Atölye"),
            listOf("Tik Taklı", "Bronz", "Eski", "Yavaş", "Dönen", "Altın", "Zamansız", "Çalışkan", "Gizemli", "Parlak")),
        MapDef(10, "Kaos Adaları", "Bütün dünyaların birleştiği renkli adalar", Mechanic.entries.filter { it != Mechanic.NORMAL },
            listOf("Ada", "Girdap", "Kaos", "Fırtına", "Gökkuşağı", "Kıyı", "Portal", "Zirve", "Liman", "Son Durak"),
            listOf("Karışık", "Renkli", "Çılgın", "Uçan", "Efsane", "Coşkulu", "Büyük", "Şaşırtıcı", "Destansı", "Parlak")),
    )

    val stages: List<StageIdea> = listOf(
        // Harita 1
        StageIdea("Temel Koşu", "Zıplamayı, uzun zıplamayı ve engelleri öğren.", listOf(Pattern.GAP, Pattern.SPIKES, Pattern.STEP)),
        StageIdea("Ters Dönüş", "Yer çekimi tersine dönüyor!", listOf(Pattern.GAP, Pattern.SPIKES), listOf(Mechanic.REVERSE_GRAVITY)),
        StageIdea("Hız Kazan", "Hızlı dünyada erken zıpla.", listOf(Pattern.GAP, Pattern.SAW, Pattern.SPIKES), listOf(Mechanic.FAST)),
        StageIdea("Kaygan Yollar", "Buzda hızın yavaşça artar.", listOf(Pattern.GAP, Pattern.STEP, Pattern.FLOATING), listOf(Mechanic.ICE)),
        StageIdea("Kaybolan Köprüler", "Mor platformlar kaybolmadan geç.", listOf(Pattern.GHOST_BRIDGE, Pattern.GAP), listOf(Mechanic.GHOST)),
        // Harita 2
        StageIdea("Yaylı Bulutlar", "Yüksek duvarları zıplayan dünyayla aş.", listOf(Pattern.BOUNCY_WALL, Pattern.GAP, Pattern.SPRING_WALL), listOf(Mechanic.BOUNCY)),
        StageIdea("Minik Adımlar", "Küçülünce dar geçitlerden geç.", listOf(Pattern.LOW_TUNNEL, Pattern.OVERHEAD), listOf(Mechanic.SMALL)),
        StageIdea("Ayna Kontrol", "Karakter kendi zıplar, basılı tutarak dur.", listOf(Pattern.OVERHEAD, Pattern.GAP, Pattern.SPIKES), listOf(Mechanic.REVERSE_CONTROL)),
        StageIdea("Ağır Çekim", "Yavaş zamanda hareketli engelleri izle.", listOf(Pattern.MOVING_SAW, Pattern.MOVING_PLATFORM), listOf(Mechanic.SLOW)),
        StageIdea("Kayan Adalar", "Oklu platformlar yukarı aşağı kayıyor.", listOf(Pattern.SHIFT_BRIDGE, Pattern.FLOATING), listOf(Mechanic.SHIFTING)),
        // Harita 3
        StageIdea("Hız Tünelleri", "Hız oklarıyla geniş boşlukları geç.", listOf(Pattern.BOOST_GAP, Pattern.GAP), listOf(Mechanic.FAST)),
        StageIdea("Testere Kanyonu", "Dönen testerelerin ritmini yakala.", listOf(Pattern.SAW, Pattern.MOVING_SAW), listOf(Mechanic.FAST)),
        StageIdea("Kum Basamakları", "Basamaklarda doğru yükseklikte zıpla.", listOf(Pattern.STAIRS, Pattern.STEP), listOf(Mechanic.FAST, Mechanic.REVERSE_GRAVITY)),
        StageIdea("Çifte Kural", "İki kural aynı anda etkin!", listOf(Pattern.SPIKE_GAP, Pattern.GAP), listOf(Mechanic.FAST), combo = 2),
        StageIdea("Çöl Fırtınası", "Hız, testere ve dikenler bir arada.", listOf(Pattern.MOVING_SAW, Pattern.BOOST_GAP, Pattern.SPIKES), listOf(Mechanic.FAST), combo = 2),
        // Harita 4
        StageIdea("Buz Köprüleri", "Kaygan platformlarda dengeni koru.", listOf(Pattern.FLOATING, Pattern.GAP), listOf(Mechanic.ICE)),
        StageIdea("Kristal Dikenler", "Tavandaki kristallere dikkat.", listOf(Pattern.SPIKES, Pattern.CEILING_GAP), listOf(Mechanic.ICE)),
        StageIdea("Kaygan Tavan", "Buz ve ters yer çekimi birlikte.", listOf(Pattern.GAP, Pattern.SPIKES), listOf(Mechanic.ICE, Mechanic.REVERSE_GRAVITY), combo = 2),
        StageIdea("Donmuş Saatler", "Buz ve yavaş zaman birlikte.", listOf(Pattern.MOVING_PLATFORM, Pattern.MOVING_SAW), listOf(Mechanic.ICE, Mechanic.SLOW), combo = 2),
        StageIdea("Çığ Yolu", "Basamaklar ve dikenler arasında kay.", listOf(Pattern.SPIKE_GAP, Pattern.STAIRS), listOf(Mechanic.ICE)),
        // Harita 5
        StageIdea("Işıklı Mantarlar", "Yaylı mantarlar ve hayalet köprüler.", listOf(Pattern.SPRING_WALL, Pattern.GHOST_BRIDGE), listOf(Mechanic.GHOST)),
        StageIdea("Gölge Köprüler", "Uzun hayalet köprülerde ritmi yakala.", listOf(Pattern.GHOST_BRIDGE, Pattern.FLOATING), listOf(Mechanic.GHOST)),
        StageIdea("Büyülü Testereler", "Testereler ve kaybolan zeminler.", listOf(Pattern.MOVING_SAW, Pattern.GHOST_BRIDGE), listOf(Mechanic.GHOST, Mechanic.SLOW)),
        StageIdea("Hayalet Hızı", "Hızlı dünyada hayalet platformlar.", listOf(Pattern.GHOST_BRIDGE, Pattern.BOOST_GAP), listOf(Mechanic.GHOST, Mechanic.FAST), combo = 2),
        StageIdea("Orman Labirenti", "Dar geçitler ve gizli yollar.", listOf(Pattern.OVERHEAD, Pattern.LOW_TUNNEL, Pattern.GHOST_BRIDGE), listOf(Mechanic.GHOST, Mechanic.SMALL), combo = 2),
        // Harita 6
        StageIdea("Yaylı Çatılar", "Yaylarla çatıdan çatıya.", listOf(Pattern.SPRING_WALL, Pattern.STEP), listOf(Mechanic.BOUNCY)),
        StageIdea("Gökdelen Atlayışı", "Dev duvarları zıplayarak aş.", listOf(Pattern.BOUNCY_WALL, Pattern.GAP), listOf(Mechanic.BOUNCY)),
        StageIdea("Hareketli Vinçler", "Hareketli platformlara zamanında bin.", listOf(Pattern.MOVING_PLATFORM, Pattern.FLOATING), listOf(Mechanic.BOUNCY, Mechanic.SHIFTING)),
        StageIdea("Ters Çatılar", "Zıplayan ve ters dünya birlikte.", listOf(Pattern.SPRING_WALL, Pattern.GAP), listOf(Mechanic.BOUNCY, Mechanic.REVERSE_GRAVITY), combo = 2),
        StageIdea("Şehir Koşusu", "Şehrin bütün engelleri.", listOf(Pattern.STAIRS, Pattern.SPIKE_GAP, Pattern.BOUNCY_WALL), listOf(Mechanic.BOUNCY), combo = 2),
        // Harita 7
        StageIdea("Dev Çiçekler", "Çiçeklerin altındaki dar yollardan geç.", listOf(Pattern.LOW_TUNNEL, Pattern.STEP), listOf(Mechanic.SMALL)),
        StageIdea("Mantar Geçitleri", "Mantarların altında eğil.", listOf(Pattern.LOW_TUNNEL, Pattern.OVERHEAD), listOf(Mechanic.SMALL)),
        StageIdea("Karınca Yolu", "Minik ve dikkatli ol.", listOf(Pattern.LOW_TUNNEL, Pattern.SPIKES), listOf(Mechanic.SMALL, Mechanic.REVERSE_GRAVITY)),
        StageIdea("Minik Hız", "Küçük ve hızlı!", listOf(Pattern.LOW_TUNNEL, Pattern.GAP), listOf(Mechanic.SMALL, Mechanic.FAST), combo = 2),
        StageIdea("Kayan Yapraklar", "Kayan yapraklar ve dar geçitler.", listOf(Pattern.SHIFT_BRIDGE, Pattern.LOW_TUNNEL), listOf(Mechanic.SMALL, Mechanic.SHIFTING), combo = 2),
        // Harita 8
        StageIdea("Ayna Yolu", "Ters kontrolle yol al.", listOf(Pattern.OVERHEAD, Pattern.GAP), listOf(Mechanic.REVERSE_CONTROL)),
        StageIdea("Yansıyan Dikenler", "Dikenlerin altında basılı tut.", listOf(Pattern.SPIKES, Pattern.OVERHEAD), listOf(Mechanic.REVERSE_CONTROL)),
        StageIdea("Kristal Köprü", "Kristal platformlarda ters kontrol.", listOf(Pattern.FLOATING, Pattern.OVERHEAD), listOf(Mechanic.REVERSE_CONTROL, Mechanic.SLOW)),
        StageIdea("Ters Ayna", "Ters kontrol ve ters yer çekimi!", listOf(Pattern.OVERHEAD, Pattern.SPIKES), listOf(Mechanic.REVERSE_CONTROL, Mechanic.REVERSE_GRAVITY), combo = 2),
        StageIdea("Ayna Labirenti", "Yansımaların arasında kaybolma.", listOf(Pattern.OVERHEAD, Pattern.MOVING_SAW, Pattern.GAP), listOf(Mechanic.REVERSE_CONTROL), combo = 2),
        // Harita 9
        StageIdea("Saat Kuleleri", "Kulelerin ritmine uy.", listOf(Pattern.MOVING_PLATFORM, Pattern.STAIRS), listOf(Mechanic.SLOW)),
        StageIdea("Dişli Testereler", "Dönen dişlilerden kaç.", listOf(Pattern.MOVING_SAW, Pattern.SAW), listOf(Mechanic.SLOW, Mechanic.FAST)),
        StageIdea("Zaman Köprüleri", "Kayan köprülerde zamanlama.", listOf(Pattern.SHIFT_BRIDGE, Pattern.MOVING_PLATFORM), listOf(Mechanic.SHIFTING, Mechanic.SLOW)),
        StageIdea("Yavaş Ters", "Yavaş zaman ve ters yer çekimi.", listOf(Pattern.GAP, Pattern.MOVING_SAW), listOf(Mechanic.SLOW, Mechanic.REVERSE_GRAVITY), combo = 2),
        StageIdea("Saat Mekanizması", "Bütün dişliler dönüyor!", listOf(Pattern.SHIFT_BRIDGE, Pattern.MOVING_SAW, Pattern.SPIKE_GAP), listOf(Mechanic.SHIFTING, Mechanic.SLOW), combo = 2),
        // Harita 10
        StageIdea("Kaos Başlıyor", "Bütün dünyalar karışıyor.", listOf(Pattern.GAP, Pattern.SPIKES, Pattern.FLOATING), combo = 2),
        StageIdea("Karışık Kurallar", "Her aşamada farklı bir sürpriz.", listOf(Pattern.GHOST_BRIDGE, Pattern.LOW_TUNNEL, Pattern.OVERHEAD), combo = 2),
        StageIdea("Üçlü Kaos", "Üç kural aynı anda!", listOf(Pattern.SPIKE_GAP, Pattern.MOVING_SAW), combo = 3),
        StageIdea("Fırtına Adaları", "Kayan adalar ve dev duvarlar.", listOf(Pattern.SHIFT_BRIDGE, Pattern.BOUNCY_WALL, Pattern.BOOST_GAP), combo = 3),
        StageIdea("Son Kaos", "Öğrendiğin her şeyi kullan!", Pattern.entries.toList(), combo = 3),
    )

    fun mapOf(levelId: Int): MapDef = maps[(levelId - 1) / LEVELS_PER_MAP]
    fun stageOf(levelId: Int): StageIdea = stages[(levelId - 1) / 10]

    fun kindOf(levelId: Int): LevelKind = when {
        levelId == 500 -> LevelKind.GRAND_FINAL
        levelId % 50 == 0 -> LevelKind.FINAL
        levelId % 10 == 0 -> LevelKind.CHALLENGE
        levelId == 1 || (levelId <= 100 && levelId % 10 == 1) -> LevelKind.TUTORIAL
        else -> LevelKind.NORMAL
    }

    /** Harita içindeki bölümlerde gizli koleksiyon parçası (5 adet). */
    fun hasPiece(levelId: Int): Boolean {
        val idx = (levelId - 1) % LEVELS_PER_MAP + 1
        return idx in setOf(8, 18, 28, 38, 48)
    }

    fun pieceIndex(levelId: Int): Int = listOf(8, 18, 28, 38, 48).indexOf((levelId - 1) % LEVELS_PER_MAP + 1)

    /** Bölümün zorluğu (1..10). */
    fun difficulty(levelId: Int): Int {
        val map = (levelId - 1) / 50
        val inMap = (levelId - 1) % 50
        val stage = inMap / 10
        val inStage = inMap % 10
        val base = 1.0 + map * 0.85 + stage * 0.25 + inStage * 0.08
        val bonus = when (kindOf(levelId)) {
            LevelKind.CHALLENGE -> 1.0
            LevelKind.FINAL, LevelKind.GRAND_FINAL -> 1.2
            LevelKind.TUTORIAL -> -0.8
            LevelKind.NORMAL -> 0.0
        }
        return (base + bonus).toInt().coerceIn(1, 10)
    }

    fun levelName(levelId: Int, rngSeed: Long): String {
        val kind = kindOf(levelId)
        val m = mapOf(levelId)
        return when (kind) {
            LevelKind.GRAND_FINAL -> "Büyük Final: Kaosun Kalbi"
            LevelKind.FINAL -> "${m.name} Finali"
            else -> {
                val r = java.util.Random(rngSeed * 31 + levelId)
                val a = m.adjectives[r.nextInt(m.adjectives.size)]
                val n = m.nouns[(levelId * 7 + r.nextInt(3)) % m.nouns.size]
                val base = "$a $n"
                if (kind == LevelKind.CHALLENGE) "Meydan Okuma: $base" else base
            }
        }
    }
}
