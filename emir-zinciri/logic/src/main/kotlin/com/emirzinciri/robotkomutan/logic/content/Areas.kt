package com.emirzinciri.robotkomutan.logic.content

/** Bir alanın (5 bölüm) kimliği. [mechanic] o alanın tanıttığı ana yeniliktir. */
data class AreaInfo(val id: Int, val name: String, val mechanic: String, val themeColor: Int) {
    val firstLevel get() = (id - 1) * 5 + 1
    val lastLevel get() = id * 5
    val levelIds get() = firstLevel..lastLevel
    /** Her 25. bölüm özel patron, her 50. bölüm büyük dünya finali. */
    val hasBoss get() = lastLevel % 25 == 0 && lastLevel >= 50
    val isWorldFinal get() = lastLevel % 50 == 0
}

object Areas {
    const val COUNT = 100
    const val LEVELS_PER_AREA = 5

    private val themes = intArrayOf(0x24B8B0, 0xFF9B42, 0x62C78A, 0x9277E8, 0xFFD66B, 0x4FA3E3, 0xF07A8C)

    private val defs: List<Pair<String, String>> = listOf(
        "İlk Komut" to "Temel hareket",
        "Yön Labirenti" to "Dönüş ve engeller",
        "Taşıma Tesisi" to "Nesne taşıma",
        "Kapı Kontrolü" to "Düğmeler ve kapılar",
        "Enerji İstasyonu" to "Enerji yönetimi",
        "İkili Komuta" to "İkinci robot",
        "Bant Fabrikası" to "Hareketli bantlar",
        "Güvenlik Koridoru" to "Lazer engeller",
        "Koordinasyon Merkezi" to "Robot senkronizasyonu",
        "Robot İsyanı" to "İlk büyük patron",
        "Manyetik Tesis" to "Manyetik etkiler",
        "Uçan Birlikler" to "Uçan robot",
        "Döngü Laboratuvarı" to "Tekrar komutu",
        "Karar Merkezi" to "Koşullu komutlar",
        "Robot Ağı" to "Mesajlaşma",
        "Üçlü Komuta" to "Üçüncü robot",
        "Savunma Fabrikası" to "Kalkanlar",
        "Gizli Operasyon" to "Görünmezlik",
        "Zaman Atölyesi" to "Gecikme ve zaman",
        "Merkezi Çekirdek" to "Büyük dünya finali",
        "Kazı Ocakları" to "Kazıcı robot ve tüneller",
        "Ayna Salonu" to "Komut aynası",
        "Yankı Kulesi" to "Komut yankısı",
        "Virüs Karantinası" to "Komut virüsü",
        "Demir Muhafız Kalesi" to "Patron: Demir Muhafız",
        "Senkron Köprüsü" to "Eş zamanlı varış",
        "Gecikme Hattı" to "Gecikmeli komutlar",
        "Birleşim Atölyesi" to "Robot birleşimi",
        "Enerji Zinciri Santrali" to "Uzun enerji zincirleri",
        "Manyetik Fırtına" to "Dünya finali: Manyetik Dev",
        "Dörtlü Komuta" to "Dördüncü robot",
        "Platform Labirenti" to "Hareketli platformlar",
        "Sinyal Kuleleri" to "Uzak sinyal aktarma",
        "Programlı Taretler" to "Programlanabilir taretler",
        "Komut Hafızası Arşivi" to "Komut şablonları",
        "Takas Pazarı" to "Komut takası",
        "Klon Laboratuvarı" to "Robot klonlama",
        "Gizli Sensör Bölgesi" to "Görünmeyen engeller ve tarama",
        "Koşullu Kapılar" to "Robot türüne duyarlı kapılar",
        "Enerji Yutucu Çekirdeği" to "Patron: Enerji Yutucu",
        "Dallanma Ormanı" to "Komut dallandırma",
        "Üretim Hattı Alfa" to "Zincirleme fabrika üretimi",
        "Hurda Deposu" to "Onarım ve mühendis robot",
        "Soğutma Kuleleri" to "Isınan makineler",
        "Buz Fabrikası" to "Kaygan zemin",
        "Lav Dökümhanesi" to "Isı tehlikesi",
        "Rüzgâr Türbinleri" to "Rüzgâr itmesi",
        "Güneş Paneli Tarlası" to "Zamanla dolan enerji",
        "Kuantum Tüneli" to "Işınlanma",
        "Komut Korsanı Gemisi" to "Dünya finali: Komut Korsanı",
        "Beşli Komuta" to "Beşinci robot",
        "Yörünge İstasyonu" to "Düşük yerçekimi zıplaması",
        "Yerçekimsiz Depo" to "Süzülen kutular",
        "Meteor Kalkanı" to "Kalkan zamanlaması",
        "Uydu Ağı" to "Çok kanallı sinyaller",
        "Ay Madeni" to "Kazı ve taşıma zinciri",
        "Kızıl Gezegen Üssü" to "Kum fırtınası görüşü",
        "Kuyruklu Yıldız Rafinerisi" to "Ham madde işleme",
        "Gezegen Kalkanı" to "Üs savunması",
        "Manyetik Dev Yuvası" to "Patron: Manyetik Dev",
        "Derin Deniz Laboratuvarı" to "Basınç ve oksijen enerjisi",
        "Basınç Odaları" to "Sıralı kapı kilitleri",
        "Mercan Ağı" to "Dallanan enerji hatları",
        "Akıntı Bantları" to "Yön değiştiren bantlar",
        "Batık Fabrika" to "Su basan hücreler",
        "Volkan Santrali" to "Periyodik tehlike",
        "Kum Fırtınası Çölü" to "Sınırlı tarama",
        "Vaha Pompaları" to "Kaynak paylaşımı",
        "Kanyon Köprüleri" to "Köprü inşası",
        "Zaman Makinesi Odası" to "Patron: Zaman Makinesi",
        "Saat Kulesi" to "Döngüsel zamanlama",
        "Paralel Hatlar" to "Eş zamanlı iki görev",
        "Geri Sarma Arşivi" to "Zaman geri sarma",
        "Hologram Şehri" to "Sahte duvarlar",
        "Gölge Koridorları" to "Gizlilik ve devriyeler",
        "Gece Vardiyası" to "Işık alanları",
        "Neon Çarşı" to "Ticaret robotları",
        "Robot Stadyumu" to "Takım savaşları",
        "Turnuva Arenası" to "Düşman takımlar",
        "Büyük Arena Finali" to "Dünya finali: Arena Şampiyonu",
        "Altılı Komuta" to "Altıncı robot",
        "Merkez Ağ Geçidi" to "Ağ yönlendirme",
        "Veri Okyanusu" to "Veri paketleri taşıma",
        "Şifre Kasası" to "Kombinasyon kilitleri",
        "Güvenlik Duvarı" to "Komut filtreleri",
        "Yedek Çekirdek" to "Yedekli sistemler",
        "Bellek Sarayı" to "Komut hafızası ustalığı",
        "İşlemci Kalesi" to "Saat sinyali zamanlaması",
        "Önbellek Labirenti" to "Değişen haritalar",
        "Sistem Bekçisi" to "Patron: Sistem Bekçisi",
        "Son Hat" to "Bütün bantlar ve lazerler",
        "Kopya Ordusu" to "Kopyacı robot ustalığı",
        "Zincir Reaksiyonu" to "Uzun zincirleme etkiler",
        "Mutlak Senkron" to "Altı robot eş zamanlı",
        "Kaos Fabrikası" to "Rastgele görünen deterministik olaylar",
        "Son Komuta Kulesi" to "Komutan robot ustalığı",
        "Çekirdek Kapıları" to "Çok aşamalı kilitler",
        "Ana Bellek" to "Bütün mekanikler",
        "Merkezi Yapay Zekâ Eşiği" to "Son hazırlık",
        "Merkezi Yapay Zekâ" to "Büyük final: Merkezi Yapay Zekâ",
    )

    val all: List<AreaInfo> = defs.mapIndexed { i, (n, m) -> AreaInfo(i + 1, n, m, themes[i % themes.size]) }

    init { check(all.size == COUNT) { "Alan sayısı $COUNT olmalı: ${all.size}" } }

    fun of(id: Int): AreaInfo = all[id - 1]
    fun forLevel(levelId: Int): AreaInfo = of((levelId - 1) / LEVELS_PER_AREA + 1)

    /** Tasarım kuralı: bir bölümde aynı anda kontrol edilebilecek en fazla robot. */
    fun maxRobotsFor(levelId: Int): Int = when (levelId) {
        in 1..25 -> 1
        in 26..75 -> 2
        in 76..150 -> 3
        in 151..250 -> 4
        in 251..400 -> 5
        else -> 6
    }
}
