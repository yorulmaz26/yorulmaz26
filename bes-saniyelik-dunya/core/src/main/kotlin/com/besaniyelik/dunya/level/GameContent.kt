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

    /** 100 alan: her harita 10 alandan, her alan 5 bölümden oluşur. Her alanın kendi fikri ve engel karışımı vardır. */
    val stages: List<StageIdea> = listOf(
        // Harita 1
        area("İlk Adımlar", "Dokun ve zıpla, boşlukları geç.", "G ST", "", 0),
        area("Engel Tepesi", "Dikenler, testereler ve yaylar.", "SP SA SW", "", 0),
        area("Ters Dönüş", "Yer çekimi ilk kez tersine dönüyor.", "G SP", "REVERSE_GRAVITY", 0),
        area("Tavan Yürüyüşü", "Tavanda koşmaya alış.", "G ST SP", "REVERSE_GRAVITY", 0),
        area("Hız Kazan", "Hızlı dünyada erken zıpla.", "G SA", "FAST", 0),
        area("Rüzgâr Gibi", "Hızlı dünyada dikenler sıklaşıyor.", "SP SG", "FAST", 0),
        area("Kaygan Yollar", "Buzda hızın yavaşça artar.", "G ST", "ICE", 0),
        area("Buzlu Tepeler", "Buzlu platformlarda dengede kal.", "F G", "ICE", 0),
        area("Kaybolan Köprüler", "Mor platformlar kaybolmadan geç.", "GB G", "GHOST", 0),
        area("Vadi Kapısı", "Yeşil Vadi'de öğrendiğin her şey.", "GB SP F", "GHOST", 0),
        // Harita 2
        area("Yaylı Bulutlar", "Yüksek duvarları zıplayarak aş.", "BW G", "BOUNCY", 0),
        area("Bulut Sıçrayışı", "Geniş boşluklar ve yaylar.", "BW SW", "BOUNCY", 0),
        area("Minik Adımlar", "Küçülünce dar geçitlerden geç.", "LT OH", "SMALL", 0),
        area("Bulut Tünelleri", "Bulutların altındaki dar yollar.", "LT G", "SMALL", 0),
        area("Ayna Kontrol", "Karakter kendi zıplar, basılı tutarak dur.", "OH G", "REVERSE_CONTROL", 0),
        area("Ters Esinti", "Dikenlerin altında basılı tut.", "OH SP", "REVERSE_CONTROL", 0),
        area("Ağır Çekim", "Yavaş zamanda hareketli engelleri izle.", "MS MP", "SLOW", 0),
        area("Sakin Rüzgâr", "Yavaş zamanda dönen testereler.", "MS SA", "SLOW", 0),
        area("Kayan Adalar", "Oklu platformlar yukarı aşağı kayar.", "SB F", "SHIFTING", 0),
        area("Gökyüzü Kapısı", "Kayan adalar ve büyük boşluklar.", "SB G", "SHIFTING", 0),
        // Harita 3
        area("Kum Pisti", "Hız oklarıyla uzun boşluklar.", "BG G", "FAST", 0),
        area("Hız Tünelleri", "Arka arkaya hız okları.", "BG SP", "FAST", 0),
        area("Testere Kanyonu", "Dönen testerelerin ritmini yakala.", "SA MS", "FAST", 0),
        area("Serap Vadisi", "Hareketli platformlar ve serap.", "MP F", "FAST REVERSE_GRAVITY", 0),
        area("Kum Basamakları", "Basamaklarda doğru yükseklikte zıpla.", "STA ST", "FAST", 0),
        area("Kaktüs Bahçesi", "Dikenli kaktüs yolları.", "SP SG CG", "FAST", 0),
        area("Çifte Kural", "İki kural aynı anda etkin!", "SG G", "FAST REVERSE_GRAVITY", 2),
        area("Ters Kervan", "Ters yer çekimi ve hız birlikte.", "G SP", "REVERSE_GRAVITY FAST", 2),
        area("Vaha Koşusu", "Kaygan vaha ve hız okları.", "F BG", "FAST ICE", 2),
        area("Çöl Fırtınası", "Hız, testere ve dikenler bir arada.", "MS BG SP", "FAST", 2),
        // Harita 4
        area("Kar Patikası", "Karlı yollarda kaymaya alış.", "G ST", "ICE", 0),
        area("Buz Köprüleri", "Kaygan platformlarda dengeni koru.", "F G", "ICE", 0),
        area("Kristal Dikenler", "Tavandaki kristallere dikkat.", "SP CG", "ICE", 0),
        area("Buzul Mağarası", "Alçak buz tavanların altından geç.", "OH LT", "ICE SMALL", 0),
        area("Kaygan Tavan", "Buz ve ters yer çekimi birlikte.", "G SP", "ICE REVERSE_GRAVITY", 2),
        area("Kartopu Tepesi", "Buzlu merdivenler ve dikenler.", "STA SG", "ICE", 0),
        area("Donmuş Saatler", "Buz ve yavaş zaman birlikte.", "MP MS", "ICE SLOW", 2),
        area("Buz Sarayı", "Kristal salonlarda zıpla.", "F SW", "ICE SLOW", 0),
        area("Çığ Yolu", "Basamaklar ve dikenler arasında kay.", "SG STA", "ICE", 2),
        area("Kraliçenin Kapısı", "Buz Krallığı'nın son sınavı.", "MP CG SP", "ICE SLOW", 2),
        // Harita 5
        area("Işıklı Mantarlar", "Yaylı mantarlar ve hayalet köprüler.", "SW GB", "GHOST", 0),
        area("Gölge Köprüler", "Uzun hayalet köprülerde ritmi yakala.", "GB F", "GHOST", 0),
        area("Ateş Böceği Yolu", "Hayalet platformlar ve dikenler.", "GB SP", "GHOST", 0),
        area("Büyülü Testereler", "Testereler ve kaybolan zeminler.", "MS GB", "GHOST SLOW", 0),
        area("Sisli Koru", "Sisin içindeki dar yollar.", "LT OH", "GHOST SMALL", 0),
        area("Hayalet Hızı", "Hızlı dünyada hayalet platformlar.", "GB BG", "GHOST FAST", 2),
        area("Kovuk Geçidi", "Ağaç kovuklarından geç.", "LT G", "SMALL GHOST", 2),
        area("Fısıltı Bayırı", "Ters dünyada hayalet köprüler.", "GB G", "GHOST REVERSE_GRAVITY", 2),
        area("Orman Labirenti", "Dar geçitler ve gizli yollar.", "OH LT GB", "GHOST SMALL", 2),
        area("Ormanın Kalbi", "Hayalet Ormanı'nın bütün sırları.", "GB MS SG", "GHOST", 2),
        // Harita 6
        area("Yaylı Çatılar", "Yaylarla çatıdan çatıya.", "SW ST", "BOUNCY", 0),
        area("Gökdelen Atlayışı", "Dev duvarları zıplayarak aş.", "BW G", "BOUNCY", 0),
        area("Balkon Sırası", "Yüzen balkonlar arasında zıpla.", "F BW", "BOUNCY", 0),
        area("Hareketli Vinçler", "Hareketli platformlara zamanında bin.", "MP F", "BOUNCY SHIFTING", 0),
        area("Kayan Asansörler", "Kayan platformlarla yüksel.", "SB MP", "SHIFTING", 0),
        area("Yaylı Meydan", "Yaylar ve testereler.", "SW SA", "BOUNCY FAST", 0),
        area("Ters Çatılar", "Zıplayan ve ters dünya birlikte.", "SW G", "BOUNCY REVERSE_GRAVITY", 2),
        area("Gece Pazarı", "Hızlı dünyada kayan tezgâhlar.", "SB BG", "SHIFTING FAST", 2),
        area("Neon Cadde", "Merdivenler, dikenler ve dev duvarlar.", "STA SG BW", "BOUNCY", 2),
        area("Şehir Koşusu", "Şehrin bütün engelleri.", "BW SB SP", "BOUNCY SHIFTING", 2),
        // Harita 7
        area("Dev Çiçekler", "Çiçeklerin altındaki dar yollardan geç.", "LT ST", "SMALL", 0),
        area("Mantar Geçitleri", "Mantarların altında küçül.", "LT OH", "SMALL", 0),
        area("Çiy Damlası", "Damlaların arasında zıpla.", "F LT", "SMALL", 0),
        area("Karınca Yolu", "Minik ve dikkatli ol.", "LT SP", "SMALL REVERSE_GRAVITY", 0),
        area("Yaprak Köprüsü", "Kayan yapraklar ve dar geçitler.", "SB LT", "SMALL SHIFTING", 0),
        area("Polen Fırtınası", "Küçük ve hızlı!", "LT G", "SMALL FAST", 2),
        area("Uğur Böceği", "Testereler ve dar geçitler.", "LT SA", "SMALL SLOW", 2),
        area("Kök Tüneli", "Ters dünyada dar geçitler.", "LT G", "SMALL REVERSE_GRAVITY", 2),
        area("Gizli Bahçe", "Hayalet yapraklar arasında.", "GB LT", "SMALL GHOST", 2),
        area("Minik Kraliyet", "Minik Dünya'nın büyük sınavı.", "LT SB SG", "SMALL", 2),
        // Harita 8
        area("Ayna Yolu", "Ters kontrolle yol al.", "OH G", "REVERSE_CONTROL", 0),
        area("Yansıyan Dikenler", "Dikenlerin altında basılı tut.", "SP OH", "REVERSE_CONTROL", 0),
        area("Prizma Salonu", "Işık kırılır, sen dikkatli ol.", "SA OH", "REVERSE_CONTROL", 0),
        area("Kristal Köprü", "Kristal platformlarda ters kontrol.", "F OH", "REVERSE_CONTROL SLOW", 0),
        area("Gümüş Koridor", "Yavaş zaman ve ters kontrol.", "OH MS", "REVERSE_CONTROL SLOW", 2),
        area("Gökkuşağı Kırığı", "Hızlı aynalar.", "G SP", "REVERSE_CONTROL FAST", 2),
        area("Ters Ayna", "Ters kontrol ve ters yer çekimi!", "OH SP", "REVERSE_CONTROL REVERSE_GRAVITY", 2),
        area("Sırlı Kubbe", "Buzlu aynalar.", "G OH", "REVERSE_CONTROL ICE", 2),
        area("Ayna Labirenti", "Yansımaların arasında kaybolma.", "OH MS G", "REVERSE_CONTROL", 2),
        area("Aynanın Ötesi", "Ayna Diyarı'nın son sınavı.", "OH SG MS", "REVERSE_CONTROL REVERSE_GRAVITY", 2),
        // Harita 9
        area("Saat Kuleleri", "Kulelerin ritmine uy.", "MP STA", "SLOW", 0),
        area("Dişli Testereler", "Dönen dişlilerden kaç.", "MS SA", "SLOW", 0),
        area("Sarkaç Köprüsü", "Sallanan platformlar.", "MP F", "SLOW SHIFTING", 0),
        area("Zaman Köprüleri", "Kayan köprülerde zamanlama.", "SB MP", "SHIFTING SLOW", 0),
        area("Kum Saati", "Hızlı ve yavaş sırayla.", "SG BG", "FAST SLOW", 0),
        area("Zemberek Atölyesi", "Yaylar ve dişliler.", "SW MS", "SHIFTING FAST", 2),
        area("Yavaş Ters", "Yavaş zaman ve ters yer çekimi.", "G MS", "SLOW REVERSE_GRAVITY", 2),
        area("Takvim Meydanı", "Merdivenler ve kayan basamaklar.", "STA SB", "SHIFTING SLOW", 2),
        area("Çark Ustası", "Dişliler ve dar geçitler.", "MS LT", "SLOW SMALL", 2),
        area("Saat Mekanizması", "Bütün dişliler dönüyor!", "SB MS SG", "SHIFTING SLOW", 3),
        // Harita 10
        area("Kaos Başlıyor", "Bütün dünyalar karışıyor.", "G SP F", "", 2),
        area("Renkli Kıyı", "Yaylar ve hayalet köprüler.", "SW GB", "", 2),
        area("Karışık Kurallar", "Her aşamada farklı bir sürpriz.", "GB LT OH", "", 2),
        area("Portal Adası", "Kayan adalar ve hız okları.", "SB BG", "", 2),
        area("Üçlü Kaos", "Üç kural aynı anda!", "SG MS", "", 3),
        area("Girdap", "Hızlı dönen testereler.", "MS SA BG", "", 3),
        area("Fırtına Adaları", "Kayan adalar ve dev duvarlar.", "SB BW BG", "", 3),
        area("Efsane Zirve", "Merdivenler ve dikenler.", "STA SG CG", "", 3),
        area("Son Durak", "Her şeyden biraz.", "GB LT MP", "", 3),
        area("Kaosun Kalbi", "Öğrendiğin her şeyi kullan!", "ALL", "", 3),
    )

    private fun area(title: String, desc: String, pats: String, fav: String, combo: Int): StageIdea {
        val map = mapOf("G" to Pattern.GAP, "SP" to Pattern.SPIKES, "ST" to Pattern.STEP, "F" to Pattern.FLOATING, "SA" to Pattern.SAW,
            "MS" to Pattern.MOVING_SAW, "MP" to Pattern.MOVING_PLATFORM, "SW" to Pattern.SPRING_WALL, "BG" to Pattern.BOOST_GAP,
            "GB" to Pattern.GHOST_BRIDGE, "SB" to Pattern.SHIFT_BRIDGE, "LT" to Pattern.LOW_TUNNEL, "BW" to Pattern.BOUNCY_WALL,
            "OH" to Pattern.OVERHEAD, "CG" to Pattern.CEILING_GAP, "STA" to Pattern.STAIRS, "SG" to Pattern.SPIKE_GAP)
        val patterns = if (pats == "ALL") Pattern.entries.toList() else pats.split(" ").map { map.getValue(it) }
        val favored = fav.split(" ").filter { it.isNotBlank() }.map { Mechanic.valueOf(it) }
        return StageIdea(title, desc, patterns, favored, combo)
    }

    const val LEVELS_PER_AREA = 5
    const val AREA_COUNT = 100
    fun areaNumber(levelId: Int): Int = (levelId - 1) / LEVELS_PER_AREA + 1

    fun mapOf(levelId: Int): MapDef = maps[(levelId - 1) / LEVELS_PER_MAP]
    fun stageOf(levelId: Int): StageIdea = stages[(levelId - 1) / LEVELS_PER_AREA]

    fun kindOf(levelId: Int): LevelKind = when {
        levelId == 500 -> LevelKind.GRAND_FINAL
        levelId % 50 == 0 -> LevelKind.FINAL
        levelId % LEVELS_PER_AREA == 0 -> LevelKind.CHALLENGE // her alanın son bölümü meydan okuma
        levelId == 1 || (levelId <= 100 && levelId % 10 == 1) -> LevelKind.TUTORIAL
        else -> LevelKind.NORMAL
    }

    /** Harita içindeki bölümlerde gizli koleksiyon parçası (5 adet). */
    fun hasPiece(levelId: Int): Boolean {
        val idx = (levelId - 1) % LEVELS_PER_MAP + 1
        return idx in setOf(8, 18, 28, 38, 48)
    }

    fun pieceIndex(levelId: Int): Int = listOf(8, 18, 28, 38, 48).indexOf((levelId - 1) % LEVELS_PER_MAP + 1)

    /**
     * Sürekli hedef zorluk (1..10), "testere dişi" eğrisi:
     *  - her harita bir öncekinden 0,68 daha zor bir tabandan başlar ve harita boyunca 1,45 artar; yani yeni haritanın
     *    ilk bölümleri önceki haritanın sonundan belirgin biçimde kolaydır (yeni tema + nefes alma),
     *  - her alanın 5 bölümü kendi içinde hafifçe zorlaşır, 5. bölüm (meydan okuma) bir basamak yukarıdadır,
     *  - yeni mekaniğin tanıtıldığı bölümler kolay, harita finalleri zordur.
     * Dökümhane (LevelForge) her bölümü üretirken ÖLÇÜLEN zorluğu bu hedefe oturtur.
     */
    fun targetDifficulty(levelId: Int): Float {
        if (levelId >= LEVEL_COUNT) return 9.5f
        val map = (levelId - 1) / LEVELS_PER_MAP
        val inMap = (levelId - 1) % LEVELS_PER_MAP
        val inArea = inMap % LEVELS_PER_AREA
        var d = 1f + map * 0.68f + inMap / (LEVELS_PER_MAP - 1f) * 1.45f
        d += (inArea - 2) * 0.05f
        d += when (kindOf(levelId)) {
            LevelKind.CHALLENGE -> 0.45f
            LevelKind.FINAL, LevelKind.GRAND_FINAL -> 0.7f
            LevelKind.TUTORIAL -> -0.5f
            LevelKind.NORMAL -> 0f
        }
        return d.coerceIn(1f, 9.5f)
    }

    /** Bölümün zorluğu (1..10): hedef zorluğun yuvarlanmış hali (arayüz ve XP için). */
    fun difficulty(levelId: Int): Int = Math.round(targetDifficulty(levelId)).coerceIn(1, 10)

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
