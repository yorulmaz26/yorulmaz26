package com.yanlisrenk.logic.level

import com.yanlisrenk.logic.model.Mechanic
import com.yanlisrenk.logic.model.Mechanic.*
import com.yanlisrenk.logic.i18n.L
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Dünya (5 alanlık tema ailesi). [deco] görsel süsleme ailesinin anahtarıdır. */
@Serializable
data class WorldInfo(
    val id: Int,
    /** Türkçe ad (kaynak metin). Gösterimde [name] kullanılır. */
    @SerialName("name") val nameTr: String,
    val deco: String,
    val music: String,
    val skyTop: Int,
    val skyBottom: Int,
    val path: Int,
    val accent1: Int,
    val accent2: Int,
    val accent3: Int,
) {
    /** Seçili dildeki ad. */
    val name: String get() = L.tOr("world.$id.name", nameTr)
}

@Serializable
data class AreaInfo(
    val id: Int,
    val world: Int,
    @SerialName("name") val nameTr: String,
    @SerialName("description") val descriptionTr: String,
    /** Bu alanda öne çıkan (ve ilkse tanıtılan) mekanikler. */
    val focus: List<Mechanic>,
    /** Bu alanda ilk kez öğretilen mekanikler. */
    val introduces: List<Mechanic>,
    @SerialName("finalTitle") val finalTitleTr: String,
    @SerialName("collectible") val collectibleTr: String,
    val rewardGold: Int,
    val rewardDiamonds: Int,
    /** Görsel varyant 0..4 (aynı dünyadaki alanlar farklı görünür). */
    val variant: Int,
    val skyTop: Int,
    val skyBottom: Int,
    val path: Int,
    val accent1: Int,
    val accent2: Int,
    val accent3: Int,
    /** Kapı çerçeve stili 0..9 */
    val gateStyle: Int,
    /** Alan parametreleri */
    val speedMul: Float = 1f,
    val gapMul: Float = 1f,
    val coinDensity: Float = 1f,
) {
    val name: String get() = L.tOr("area.$id.name", nameTr)
    val description: String get() = L.tOr("area.$id.desc", descriptionTr)
    val finalTitle: String get() = L.tOr("area.$id.final", finalTitleTr)
    val collectible: String get() = L.tOr("area.$id.collectible", collectibleTr)
    val firstLevel: Int get() = (id - 1) * 5 + 1
    val lastLevel: Int get() = id * 5
}

object AreaCatalog {
    val worlds: List<WorldInfo> = listOf(
        WorldInfo(1, "Renk Bahçesi", "garden", "garden", 0x9FDBFF, 0xE9F8FF, 0xFFF4D6, 0x73D65C, 0xFFB6C9, 0xFFD54F),
        WorldInfo(2, "Şeker Diyarı", "candy", "candy", 0xFFC6E0, 0xFFF0F6, 0xFFF8FB, 0xFF7FB0, 0x8FE3D9, 0xFFD54F),
        WorldInfo(3, "Bulut Krallığı", "clouds", "sky", 0x8FD3FF, 0xF1FAFF, 0xFFFFFF, 0xB9C9FF, 0xFFE38F, 0xFFB6C9),
        WorldInfo(4, "Buz Vadisi", "ice", "crystal", 0xA9E6FF, 0xF4FCFF, 0xEAF7FF, 0x7FD3F5, 0xC6F1FF, 0x9BB8FF),
        WorldInfo(5, "Çiçek Ormanı", "forest", "garden", 0xB8EFC5, 0xF3FFF3, 0xFFF6DF, 0x58C46A, 0xFF9FC6, 0xFFC94D),
        WorldInfo(6, "Kristal Mağarası", "crystal", "crystal", 0xD6C8FF, 0xF6F2FF, 0xF3EEFF, 0xA98BFF, 0x7FE0E0, 0xFF9FD8),
        WorldInfo(7, "Güneş Çölü", "desert", "garden", 0xFFD9A0, 0xFFF6E3, 0xFFE9C2, 0xFF9F43, 0x6CCB8A, 0xFFD54F),
        WorldInfo(8, "Oyuncak Şehri", "toys", "candy", 0xBFE3FF, 0xFFF9EC, 0xFFFFFF, 0xFF6F6F, 0x5AA9FF, 0xFFD54F),
        WorldInfo(9, "Su Altı Dünyası", "ocean", "sky", 0x7FDDE8, 0xE4FBFF, 0xD9F6F7, 0x3FC1C9, 0xFF9F80, 0xFFE07F),
        WorldInfo(10, "Gökkuşağı Adaları", "rainbow", "carnival", 0xA6E4FF, 0xFFF8E7, 0xFFF3D1, 0xFF8FA3, 0x7FD88A, 0x8FB8FF),
        WorldInfo(11, "Uzay İstasyonu", "space", "space", 0xB9C2FF, 0xEEF0FF, 0xF2F4FF, 0x7C8CFF, 0x58D6D0, 0xFFB86B),
        WorldInfo(12, "Balon Dünyası", "balloons", "sky", 0xA8DEFF, 0xFFF3F8, 0xFFFFFF, 0xFF7FA8, 0x7FCFFF, 0xFFD966),
        WorldInfo(13, "Saat Kulesi", "clocks", "crystal", 0xFFE3B8, 0xFFF9F0, 0xFFF1DC, 0xC98B5A, 0x6FB6E0, 0xFFCF66),
        WorldInfo(14, "Renk Fabrikası", "factory", "space", 0xC9EEF0, 0xF5FFFE, 0xEEF6F6, 0x58D6D0, 0xFF9F43, 0xFF6F91),
        WorldInfo(15, "Sihirli Kütüphane", "library", "crystal", 0xE8D5FF, 0xFFF8EE, 0xFFF1E0, 0xB28AE0, 0xE0A96B, 0x7FD3B8),
        WorldInfo(16, "Yıldız Bahçesi", "stars", "space", 0xC4CCFF, 0xFDF2FF, 0xF6F0FF, 0xFFD54F, 0xB79BFF, 0x87CEFA),
        WorldInfo(17, "Prizma Sarayı", "prism", "final", 0xFFE0F0, 0xF0FBFF, 0xFFFFFF, 0xFF9FD0, 0x87CEFA, 0xB9F2A0),
        WorldInfo(18, "Robot Şehri", "robots", "space", 0xC8DCEB, 0xF4F9FF, 0xEDF3F8, 0x6FA8DC, 0xFFB347, 0x73D65C),
        WorldInfo(19, "Renk Karnavalı", "carnival", "carnival", 0xFFD1E8, 0xFFFBE6, 0xFFF5DA, 0xFF5E8E, 0x58D6D0, 0xFFC93C),
        WorldInfo(20, "Büyük Renk Evreni", "universe", "final", 0xD9C9FF, 0xFFF0F5, 0xFFFFFF, 0x9F7FFF, 0xFF8FB8, 0x58D6D0),
    )

    private class A(
        val name: String,
        val desc: String,
        val focus: List<Mechanic>,
        val finalTitle: String,
        val collectible: String,
        val speedMul: Float = 1f,
        val gapMul: Float = 1f,
        val coins: Float = 1f,
    )

    private val defs: List<A> = listOf(
        // Dünya 1 — Renk Bahçesi
        A("Renk Bahçesi", "Dokun, rengini değiştir ve aynı renkli kapılardan geç.", listOf(SAME), "Bahçe Kapısı", "Gülümseyen Lale", gapMul = 1.25f),
        A("Ters Kapılar", "Eşit değil kapıları farklı renk ister. Bazen hiç dokunmamak en iyisidir!", listOf(DIFF), "Ters Çit", "Ters Papatya", gapMul = 1.2f),
        A("Çift Kapılar", "Kapılar ikişer ikişer geliyor.", listOf(DOUBLE), "İkiz Kemer", "İkiz Kiraz", gapMul = 1.1f),
        A("Hız Yolu", "Hız bölgelerinde kapılar çok daha çabuk yaklaşır.", listOf(SPEED), "Rüzgâr Patikası", "Hızlı Salyangoz"),
        A("Renk Hafızası", "Kapıların rengi yaklaşınca kaybolur.", listOf(MEMORY), "Unutma Beni", "Hafıza Çiçeği"),
        // Dünya 2 — Şeker Diyarı
        A("Kilitli Renkler", "Şeker kilitleri bazı renkleri kullanılmaz yapar.", listOf(LOCK), "Kilitli Kavanoz", "Altın Lolipop"),
        A("Dönen Kapılar", "Renkleri kayan şekerli kapılar.", listOf(ROTATE), "Şeker Çarkı", "Dönen Pamuk Şeker", gapMul = 1.1f),
        A("Gökkuşağı Yolu", "Birden çok rengin geçtiği gökkuşağı kapıları.", listOf(RAINBOW), "Şerit Şeker", "Gökkuşağı Jelibonu", coins = 1.3f),
        A("Zaman Kapısı", "Renkleri sayaçla değişen kapılar.", listOf(TIMED), "Kum Saati Kek", "Saatli Kurabiye", gapMul = 1.1f),
        A("Renk Labirenti", "Her tür kapı karışık ve sık!", listOf(MAZE, SAME, DIFF, RAINBOW), "Şeker Labirenti", "Labirent Bonbonu"),
        // Dünya 3 — Bulut Krallığı
        A("Ayna Dünyası", "Ayna bölgesinde renk sırası tersine döner.", listOf(MIRROR), "Ayna Bulutu", "Ayna Damlası", gapMul = 1.15f),
        A("Gizli Kapılar", "Bulutların arasındaki kapılar ancak yaklaşınca görünür.", listOf(HIDDEN), "Saklı Kale", "Bulut Tüyü"),
        A("Yanıltıcı Renkler", "Yazı bir rengi, boya başka rengi söyler. Simgeye bak!", listOf(STROOP), "Şaşırtan Kule", "Gökkuşağı Tacı", gapMul = 1.2f),
        A("Üçlü Kapılar", "Üç kapı peş peşe.", listOf(TRIPLE), "Üç Bulut Köprüsü", "Üçlü Yıldız"),
        A("Renk Değişim Alanı", "Pedler rengini kendiliğinden değiştirir.", listOf(AUTOSHIFT), "Renk Şelalesi", "Bulut Fırçası"),
        // Dünya 4 — Buz Vadisi
        A("Hareketli Yol", "İki renkli buz kapıları sağa sola kayar.", listOf(SLIDE), "Kayan Buzul", "Kızaklı Penguen", gapMul = 1.1f),
        A("Hafıza Zinciri", "Tabeladaki renk sırasını aklında tut.", listOf(CHAIN), "Buz Zinciri", "Kar Tanesi Kolye"),
        A("Renk Karıştırma", "İki renkli kapılar iki seçenek sunar.", listOf(MIX), "Karışık Buz", "Çift Renkli Kristal", coins = 1.3f),
        A("Hızlı Karar", "Buz üstünde kapılar çok sık!", listOf(QUICK, SAME, DIFF), "Buz Pateni", "Hızlı Kızak", speedMul = 1.05f),
        A("Büyük Renk Sınavı", "İlk 19 alanda öğrendiğin her şey.", listOf(EXAM, SAME, DIFF, DOUBLE, SPEED, MEMORY, LOCK, ROTATE, RAINBOW, TIMED, MIRROR, HIDDEN, STROOP, TRIPLE, AUTOSHIFT, SLIDE, CHAIN, MIX, QUICK), "Buz Sarayı Sınavı", "Sınav Madalyası"),
        // Dünya 5 — Çiçek Ormanı
        A("Yasak Çiçekler", "Üstü çizili renklerin hiçbiriyle geçme.", listOf(NONE), "Yasak Bahçe", "Dikenli Gül"),
        A("Sıradaki Yaprak", "İleri ok kapısı gösterilen rengin bir sonrakini ister.", listOf(NEXT), "Yaprak Sırası", "Altın Yaprak", gapMul = 1.25f),
        A("Geri Sarmaşık", "Geri ok kapısı gösterilen rengin bir öncekini ister.", listOf(PREV), "Sarmaşık Kemer", "Kıvrık Sarmaşık", gapMul = 1.2f),
        A("Yankı Korusu", "Dönen ok kapısı önceki kapıdaki rengini tekrar ister.", listOf(REPEAT), "Yankı Ağacı", "Yankı Mantarı", gapMul = 1.15f),
        A("Orman Bilmecesi", "Yasak, sıradaki, önceki ve yankı kapıları bir arada.", listOf(NONE, NEXT, PREV, REPEAT), "Bilge Meşe", "Bilge Baykuş"),
        // Dünya 6 — Kristal Mağarası
        A("Sahte Kristaller", "Kesik çizgili sahte kapılar seni kandırmasın.", listOf(DECOY, SAME, DIFF), "Sahte Hazine", "Parlak Ametist"),
        A("Titreyen Işıklar", "Kapılar yanıp sönüyor.", listOf(FLICKER), "Titrek Fener", "Fener Böceği"),
        A("Yankılı Mağara", "Gizli yankı kapıları.", listOf(REPEAT, HIDDEN), "Yankı Salonu", "Yankı Kristali"),
        A("Kristal Ayna", "Ayna bölgesinde dönen kapılar.", listOf(MIRROR, ROTATE), "Ayna Kristali", "Prizmatik Taş"),
        A("Derin Damar", "Sahte, titrek ve yasak kapılar derinlerde.", listOf(DECOY, FLICKER, NONE), "Kristal Kalp", "Kristal Kalp"),
        // Dünya 7 — Güneş Çölü
        A("Serap Yolu", "Sahte kapılar ve yanıltıcı renkler: tam bir serap.", listOf(STROOP, DECOY), "Serap Kapısı", "Serap Taşı", gapMul = 1.1f),
        A("Kum Fırtınası", "Sis yüzünden kapıları geç görürsün.", listOf(FOG, SAME, DIFF), "Fırtına Gözü", "Kum Saati"),
        A("Kayan Kumlar", "Kayan kapılar ve hız bölgeleri.", listOf(SLIDE, SPEED), "Kum Kaydırağı", "Kum Kertenkelesi"),
        A("Vaha Altınları", "Renkli altınlarla dolu gökkuşağı vahası.", listOf(COINS, RAINBOW), "Altın Vaha", "Hurma Ağacı", coins = 2f),
        A("Sıcak Rüzgâr", "Dalgalanan hız ve sis.", listOf(WAVE, FOG), "Kavurucu Tepe", "Güneş Pusulası"),
        // Dünya 8 — Oyuncak Şehri
        A("Kurmalı Kapılar", "Zaman kapıları sıradaki rengi istiyor.", listOf(TIMED, NEXT), "Kurmalı Saat", "Kurmalı Fare"),
        A("Blok Kulesi", "Kilitli renkler ve üçlü kapılar.", listOf(TRIPLE, LOCK), "Blok Kale", "Renkli Blok"),
        A("Tren Rayı", "Hızlı tren, çift kapılar ve renk pedleri.", listOf(SPEED, DOUBLE, AUTOSHIFT), "Son İstasyon", "Oyuncak Tren"),
        A("Kukla Tiyatrosu", "Aynada yanıltıcı renkler.", listOf(STROOP, MIRROR), "Perde Arkası", "Kukla Şapkası", gapMul = 1.1f),
        A("Oyuncak Kutusu", "Oyuncak şehrinin tüm sürprizleri.", listOf(TIMED, NEXT, TRIPLE, LOCK, AUTOSHIFT, STROOP), "Dev Oyuncak Kutusu", "Kurmalı Robot"),
        // Dünya 9 — Su Altı Dünyası
        A("Kabarcık Yolu", "Renkli kabarcık altınları ve iki renkli kapılar.", listOf(COINS, MIX), "Kabarcık Kapısı", "Deniz Kabuğu", coins = 2f),
        A("Akıntı", "Dalgalı hız ve kayan kapılar.", listOf(WAVE, SLIDE), "Girdap", "Deniz Atı"),
        A("Mürekkep Bulutu", "Mürekkep sisi ve hafıza kapıları.", listOf(FOG, MEMORY), "Ahtapot Mağarası", "Mürekkep Şişesi"),
        A("Mercan Labirenti", "Yasak renklerle dolu mercan labirenti.", listOf(MAZE, NONE, SAME, DIFF), "Mercan Kalesi", "Pembe Mercan"),
        A("Derin Deniz", "Gizli, titrek ve önceki renk kapıları.", listOf(HIDDEN, FLICKER, PREV), "Batık Gemi", "İnci"),
        // Dünya 10 — Gökkuşağı Adaları
        A("Yedi Renk Köprüsü", "Gökkuşağı ve yasak renk kapıları.", listOf(RAINBOW, NONE), "Gökkuşağı Köprüsü", "Yedi Renk Taşı"),
        A("Ada Sekmesi", "Çift kapılar sıradaki rengi istiyor.", listOf(DOUBLE, NEXT), "Ada Zirvesi", "Sekme Taşı"),
        A("Renk Yağmuru", "Pedler ve renkli altınlar yağmuru.", listOf(AUTOSHIFT, COINS), "Renk Şelalesi", "Yağmur Damlası", coins = 1.8f),
        A("Gökkuşağı Hafızası", "Zincir tabelası ve gökkuşağı kapıları.", listOf(CHAIN, RAINBOW), "Hafıza Adası", "Gökkuşağı Kabuğu"),
        A("Ada Turnuvası", "Adaların tüm kapıları bir arada.", listOf(RAINBOW, NONE, DOUBLE, NEXT, AUTOSHIFT, CHAIN), "Ada Kupası", "Ada Kupası"),
        // Dünya 11 — Uzay İstasyonu
        A("Yerçekimsiz", "Dalgalı hız, hız bölgeleri ve dönen kapılar.", listOf(SPEED, WAVE, ROTATE), "Yörünge", "Mini Uydu"),
        A("Kara Delik", "İki rengi birden kilitleyen kara delik ve ayna.", listOf(LOCK, MIRROR), "Olay Ufku", "Kara Delik Bilyesi"),
        A("Radar", "Gizli kapılar ve zincir tabelaları.", listOf(HIDDEN, CHAIN), "Radar Kulesi", "Radar Çanağı"),
        A("Meteor Yağmuru", "Hızlı karar, çift ve sahte kapılar.", listOf(QUICK, DOUBLE, DECOY), "Meteor Kuşağı", "Meteor Taşı", speedMul = 1.05f),
        A("Uzay Yürüyüşü", "İstasyonun tüm sınavları.", listOf(SPEED, WAVE, ROTATE, LOCK, MIRROR, HIDDEN, CHAIN, QUICK, DECOY), "Kaptan Köşkü", "Astronot Kaskı"),
        // Dünya 12 — Balon Dünyası
        A("Patlayan Balonlar", "Ters kapılar ve renkli balon altınları.", listOf(COINS, DIFF), "Balon Patlatma", "Kırmızı Balon", coins = 1.8f),
        A("Uçan Kapılar", "Kayan ve zamanlı kapılar havada.", listOf(SLIDE, TIMED), "Uçan Kale", "Sepetli Balon"),
        A("Balon Festivali", "İki renkli ve sıradaki renk kapıları.", listOf(MIX, NEXT), "Festival Alanı", "Festival Bayrağı"),
        A("Rüzgârlı Gökyüzü", "Dalgalı hız ve renk pedleri.", listOf(WAVE, AUTOSHIFT), "Rüzgâr Gülü", "Uçurtma"),
        A("Büyük Balon", "Balon dünyasının büyük gösterisi.", listOf(COINS, DIFF, SLIDE, TIMED, MIX, NEXT, WAVE, AUTOSHIFT), "Dev Balon", "Altın Balon"),
        // Dünya 13 — Saat Kulesi
        A("Tik Tak", "Zaman kapıları ve hızlı kararlar.", listOf(TIMED, QUICK), "Tik Tak Kapısı", "Cep Saati"),
        A("Çarklar", "Dönen çark kapıları ve yankılar.", listOf(ROTATE, REPEAT), "Büyük Çark", "Pirinç Çark"),
        A("Geri Sayım", "Gizli zaman kapıları.", listOf(TIMED, HIDDEN), "Geri Sayım Kulesi", "Kum Saati Kolye"),
        A("Ters Zaman", "Ayna bölgesinde önceki renk kapıları.", listOf(MIRROR, PREV), "Zaman Aynası", "Ters Saat"),
        A("Gece Yarısı", "Saat kulesinin on iki vuruşu.", listOf(TIMED, QUICK, ROTATE, REPEAT, HIDDEN, MIRROR, PREV), "Gece Yarısı Çanı", "Kule Çanı"),
        // Dünya 14 — Renk Fabrikası
        A("Boya Bandı", "Renk ayarlama ve kaydırma pedleri.", listOf(AUTOSHIFT, SAME), "Boya Kazanı", "Boya Kovası"),
        A("Kalite Kontrol", "Yasak renkler ve yanıltıcı etiketler.", listOf(NONE, STROOP), "Kontrol Masası", "Kalite Damgası"),
        A("Karışım Kazanı", "Karışım, gökkuşağı ve kilit.", listOf(MIX, RAINBOW, LOCK), "Büyük Kazan", "Renk Tüpü"),
        A("Paketleme", "Üçlü kapılar ve zincir tabelaları.", listOf(TRIPLE, CHAIN), "Paket Bandı", "Hediye Paketi"),
        A("Fabrika Şefi", "Fabrikanın tüm makineleri çalışıyor.", listOf(AUTOSHIFT, NONE, STROOP, MIX, RAINBOW, LOCK, TRIPLE, CHAIN), "Şefin Ofisi", "Şef Şapkası"),
        // Dünya 15 — Sihirli Kütüphane
        A("Okunan Renkler", "Bu sefer yazıya bak: kitap simgesi!", listOf(STROOP), "Okuma Salonu", "Sihirli Kitap", gapMul = 1.1f),
        A("Kayıp Sayfalar", "Hafıza ve zincir kapıları.", listOf(MEMORY, CHAIN), "Kayıp Bölüm", "Yırtık Sayfa"),
        A("Büyülü Kitap", "Sahte, titrek ve yankı kapıları.", listOf(DECOY, FLICKER, REPEAT), "Büyü Kitabı", "Büyülü Tüy Kalem"),
        A("Raflar Arası", "Kayan raflar, sıradaki ve önceki renkler.", listOf(SLIDE, NEXT, PREV), "En Üst Raf", "Kitap Ayracı"),
        A("Büyük Arşiv", "Kütüphanenin tüm bilmeceleri.", listOf(STROOP, MEMORY, CHAIN, DECOY, FLICKER, REPEAT, SLIDE, NEXT, PREV), "Büyük Arşiv", "Bilge Gözlüğü"),
        // Dünya 16 — Yıldız Bahçesi
        A("Kayan Yıldızlar", "Hızlı kararlar ve renkli yıldız altınları.", listOf(QUICK, COINS), "Yıldız Kayması", "Kayan Yıldız", coins = 1.6f),
        A("Takımyıldız", "Zincir tabelaları ve gizli kapılar.", listOf(CHAIN, HIDDEN), "Büyük Ayı", "Takımyıldız Haritası"),
        A("Ay Işığı", "Gece sisi ve yanıp sönen kapılar.", listOf(FOG, FLICKER), "Dolunay", "Ay Taşı"),
        A("Yıldız Tozu", "Pedler, ayna ve kilit bölgeleri.", listOf(AUTOSHIFT, MIRROR, LOCK), "Toz Bulutu", "Yıldız Tozu Şişesi"),
        A("Bahçe Gecesi", "Yıldız bahçesinin gece sınavı.", listOf(QUICK, COINS, CHAIN, HIDDEN, FOG, FLICKER, AUTOSHIFT, MIRROR, LOCK), "Gece Bahçesi", "Gece Çiçeği"),
        // Dünya 17 — Prizma Sarayı
        A("Işık Kırılması", "Dönen kapılar ve yasak renkler.", listOf(ROTATE, NONE), "Kırılma Odası", "Cam Prizma"),
        A("Ayna Salonu", "Aynalar ve yanıltıcı renkler.", listOf(MIRROR, STROOP), "Ayna Salonu", "El Aynası"),
        A("Prizma Kapıları", "İki renkli ve zamanlı kapılar.", listOf(MIX, TIMED), "Prizma Kemeri", "Renk Kırıntısı"),
        A("Taht Odası", "Üçlü, yankı ve sıradaki renk kapıları.", listOf(TRIPLE, REPEAT, NEXT), "Taht", "Prizma Asası"),
        A("Saray Balosu", "Sarayın büyük balosu.", listOf(ROTATE, NONE, MIRROR, STROOP, MIX, TIMED, TRIPLE, REPEAT, NEXT), "Balo Salonu", "Kristal Ayakkabı"),
        // Dünya 18 — Robot Şehri
        A("Kodlanmış Kapılar", "Sıradaki, önceki ve yankı kodları.", listOf(NEXT, PREV, REPEAT), "Kod Kapısı", "Devre Kartı"),
        A("Montaj Hattı", "Pedler, çift kapılar ve hız.", listOf(AUTOSHIFT, DOUBLE, SPEED), "Montaj Robotu", "Altın Vida"),
        A("Sistem Hatası", "Titrek, sahte ve yanıltıcı kapılar.", listOf(FLICKER, DECOY, STROOP), "Hata Ekranı", "Bozuk Ampul"),
        A("Güç Kesintisi", "Kilit, sis ve gizli kapılar.", listOf(LOCK, FOG, HIDDEN), "Jeneratör", "Pil"),
        A("Robot Lideri", "Robot şehrinin ana bilgisayarı.", listOf(NEXT, PREV, REPEAT, AUTOSHIFT, DOUBLE, SPEED, FLICKER, DECOY, STROOP, LOCK, FOG, HIDDEN), "Ana Bilgisayar", "Robot Kalbi"),
        // Dünya 19 — Renk Karnavalı
        A("Konfeti", "Gökkuşağı kapıları ve konfeti altınları.", listOf(COINS, RAINBOW, QUICK), "Konfeti Topu", "Konfeti Kutusu", coins = 1.8f),
        A("Atlıkarınca", "Dönen, kayan ve zamanlı kapılar.", listOf(ROTATE, SLIDE, TIMED), "Atlıkarınca", "Oyuncak At"),
        A("Maskeli Balo", "Maskeli yanıltıcı renkler ve hafıza.", listOf(STROOP, MEMORY), "Maske Salonu", "Karnaval Maskesi"),
        A("Davul Ritmi", "Dalgalı hız ve üçlü kapılar.", listOf(WAVE, TRIPLE), "Davul Sahnesi", "Davul"),
        A("Karnaval Geçidi", "Karnavalın büyük geçit töreni.", listOf(COINS, RAINBOW, QUICK, ROTATE, SLIDE, TIMED, STROOP, MEMORY, WAVE, TRIPLE), "Geçit Töreni", "Karnaval Tacı"),
        // Dünya 20 — Büyük Renk Evreni
        A("Işık Tünelleri", "Hız, gizli kapılar ve zincir tabelaları.", listOf(SPEED, HIDDEN, CHAIN), "Tünel Sonu", "Işık Küresi"),
        A("Neon Hız", "Hızlı karar, ayna ve dönen kapılar.", listOf(QUICK, MIRROR, ROTATE), "Neon Pist", "Neon Halka", speedMul = 1.05f),
        A("Renk Fırtınası", "Bütün hareketli kapılar aynı anda.", listOf(ROTATE, SLIDE, TIMED, WAVE), "Fırtına Merkezi", "Fırtına Kristali"),
        A("Evren Kapısı", "Hafıza dışında her şey.", listOf(NONE, NEXT, PREV, REPEAT, DECOY, FLICKER, LOCK, MIRROR, STROOP, AUTOSHIFT, MIX, TRIPLE), "Evren Kapısı", "Evren Anahtarı"),
        A("Büyük Renk Evreni", "Yanlış Renk'in büyük finali. Her şey bir arada!", Mechanic.entries.filter { it != EXAM && it != MAZE }, "Renklerin Kalbi", "Renk Evreni Kupası"),
    )

    private val variantTints = intArrayOf(0x000000, 0xFFD54F, 0x87CEFA, 0xFFB6C9, 0x73D65C)

    val areas: List<AreaInfo> by lazy { buildAreas() }

    private fun buildAreas(): List<AreaInfo> {
        check(defs.size == 100) { "100 alan gerekli, ${defs.size} var" }
        val learned = HashSet<Mechanic>()
        return defs.mapIndexed { i, d ->
            val id = i + 1
            val w = worlds[(id - 1) / 5]
            val variant = (id - 1) % 5
            val intro = d.focus.filter { it !in learned && it != EXAM && it != MAZE }
            learned.addAll(d.focus)
            val tint = variantTints[variant]
            val amt = if (variant == 0) 0f else 0.18f
            AreaInfo(
                id = id, world = w.id, nameTr = d.name, descriptionTr = d.desc,
                focus = d.focus, introduces = intro, finalTitleTr = d.finalTitle, collectibleTr = d.collectible,
                rewardGold = 150 + id * 12, rewardDiamonds = if (variant == 4) 10 + id / 10 else 1 + id / 25,
                variant = variant,
                skyTop = mix(w.skyTop, tint, amt), skyBottom = mix(w.skyBottom, tint, amt * 0.4f), path = mix(w.path, tint, amt * 0.3f),
                accent1 = rotate(w.accent1, variant), accent2 = rotate(w.accent2, variant), accent3 = rotate(w.accent3, variant),
                gateStyle = (w.id * 3 + variant) % 10,
                speedMul = d.speedMul, gapMul = d.gapMul, coinDensity = d.coins,
            )
        }
    }

    /** Her mekaniğin ilk tanıtıldığı alan. */
    val introArea: Map<Mechanic, Int> by lazy {
        val m = HashMap<Mechanic, Int>()
        areas.forEach { a -> a.focus.forEach { if (it !in m) m[it] = a.id } }
        m
    }

    fun area(id: Int): AreaInfo = areas[id - 1]
    fun world(id: Int): WorldInfo = worlds[id - 1]
    fun areaOfLevel(level: Int): Int = (level - 1) / 5 + 1

    /** Alandan önce öğrenilmiş mekanikler (tekrar havuzu). */
    fun learnedBefore(areaId: Int): List<Mechanic> =
        areas.take(areaId - 1).flatMap { it.focus }.filter { it != EXAM && it != MAZE }.distinct()

    private fun mix(a: Int, b: Int, t: Float): Int {
        val ar = a shr 16 and 255; val ag = a shr 8 and 255; val ab = a and 255
        val br = b shr 16 and 255; val bg = b shr 8 and 255; val bb = b and 255
        val r = (ar + (br - ar) * t).toInt(); val g = (ag + (bg - ag) * t).toInt(); val bl = (ab + (bb - ab) * t).toInt()
        return (r shl 16) or (g shl 8) or bl
    }

    /** Varyanta göre aksan rengini hafifçe döndürür (HSV ton kaydırma). */
    private fun rotate(c: Int, variant: Int): Int {
        if (variant == 0) return c
        val r = (c shr 16 and 255) / 255f; val g = (c shr 8 and 255) / 255f; val b = (c and 255) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
        var h = when {
            d == 0f -> 0f
            max == r -> ((g - b) / d).mod(6f)
            max == g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        val s = if (max == 0f) 0f else d / max
        h = (h + variant * 18f) % 360f
        val cc = max * s; val x = cc * (1 - kotlin.math.abs((h / 60f) % 2 - 1)); val m = max - cc
        val (rr, gg, bb) = when ((h / 60f).toInt()) {
            0 -> Triple(cc, x, 0f); 1 -> Triple(x, cc, 0f); 2 -> Triple(0f, cc, x)
            3 -> Triple(0f, x, cc); 4 -> Triple(x, 0f, cc); else -> Triple(cc, 0f, x)
        }
        return (((rr + m) * 255).toInt() shl 16) or (((gg + m) * 255).toInt() shl 8) or ((bb + m) * 255).toInt()
    }
}
