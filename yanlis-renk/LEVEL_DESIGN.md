# Yanlış Renk — Bölüm Tasarımı

## Genel yapı

* **20 dünya × 5 alan × 5 bölüm = 500 bölüm.** Her alanın 5. bölümü alan finalidir (daha uzun, alanın tüm mekanikleri + önceki alanlardan tekrar).
* Her bölüm veridir (`android/assets/levels/area_XXX.json`): kapı dizisi, toplanabilirler, bölgeler, pedler, tabelalar, hız, tepki süresi, yıldız hedefleri ve **doğrulayıcının bulduğu bir çözüm**.
* Toplam kapı sayısı: **11527**.

## Kapı kuralları (renk + sembol)

| Kural | Simge | Geçmek için |
|---|---|---|
| AYNI | eşittir (=) | kapıyla aynı renk |
| FARKLI | eşit değil (≠), çapraz şeritler | kapının renginden farklı renk |
| GÖKKUŞAĞI / KARIŞIM | eşittir, çok renkli / ikiye bölünmüş | gösterilen renklerden biri |
| YASAK | eşit değil, üstü çizili renkler | gösterilenlerin hiçbiri |
| SIRADAKİ | ileri ok | gösterilen rengin bir sonraki rengi |
| ÖNCEKİ | geri ok | gösterilen rengin bir önceki rengi |
| YANKI | dönen ok | bir önceki kapıdan geçtiğin renk |
| SAHTE | kesik çizgi, "SAHTE" | her renk |

Hareket türleri: sabit, **dönen** (renkler kayar), **kayan** (iki renk sağa sola), **zamanlı** (sayaç halkalı renk değişimi). Görünürlük değiştiriciler: **gizli**, **hafıza** (yaklaşınca renk gizlenir), **zincir** (renkler yalnızca tabelada), **yanıp sönen**, **sis**. Bölgeler: **hız** (çarpan/yavaşlama/dalga), **ayna** (renk sırası ters), **kilit** (renk(ler) kullanılamaz, dokunuş atlar). **Pedler** rengi kendiliğinden kaydırır ya da ayarlar. Yanıltıcı kapıda boya ve yazı farklıdır; sağdaki rozet (fırça = boya, kitap = yazı) hangisine bakılacağını **açıkça** söyler.

Renk körlüğü: her renk ayrıca bir şekille gösterilir (Kırmızı daire, Mavi kare, Yeşil üçgen, Sarı yıldız) — kapılarda, altınlarda, pedlerde, tabelalarda ve karakterin üzerindeki rozette.

## Zorluk eğrisi

* Hız 4,4 → ~7,7 birim/sn (alan çarpanlarıyla), tepki varsayımı 0,62 sn → 0,36 sn.
* Kapı sayısı: `7 + 2·sıra + bölüm/22` (en fazla 34) + finalde 6.
* Zorluk yalnızca hızla artmaz: mekanik sayısı, hafıza/zincir (bellek), dönen/zamanlı (zamanlama), sis/gizli (refleks), yanıltıcı/sıradaki/önceki/yankı (düşünme) birikerek eklenir.
* Giriş bölümünde yeni mekanik önce iki temel kapıyla, geniş aralıklarla ve ekranda açıklama kartıyla öğretilir.

## Doğrulama (her bölüm için kanıt)

`LevelValidator` bölümü bir insan gibi çözer:
* Kapı gereksinimi ancak görünür olduğunda (ekran, sis, gizlilik, tabela) öğrenilebilir; hareketli kapılar için +0,3 sn, yanıp sönenler için +0,45 sn düşünme payı.
* İlk dokunuş için tepki süresi, her ek dokunuş için 0,16 sn; ped/kilit rengi değiştirince yeniden tepki süresi.
* Durum (renk, önceki kapı rengi) üzerinde dinamik programlama; her durumda (altın, dokunuş) Pareto kümesi.
* Böylece **★2 (altın hedefi) ve ★3 (dokunuş sınırı) aynı anda ulaşılabilir** olduğu kanıtlanır; hedefler buna göre konur.
* Önemsiz bölüm reddedilir: en az `max(2, kapı/4)` dokunuş gerekir; hiçbir bölüm dokunmadan geçilemez (testli).
* Üretilen plan doğrulanamazsa boşluklar %18 açılarak yeniden denenir (yedek basit üretime düşen bölüm sayısı: **0**).
* Ustalık sürümleri (%15'e kadar hızlı + hafıza kapıları) da aynı doğrulayıcıdan geçer.

## Yıldızlar

★ bölümü bitir · ★ altın hedefi (en çok toplanabilenin %70'i) · ★ dokunuş sınırı (en iyi çözüm + 1–3).

## Bölümlerde mekanik kullanımı (bölüm sayısı)

| Mekanik | Bölüm |
|---|---|
| SAME | 500 |
| DIFF | 495 |
| NEXT | 131 |
| PREV | 112 |
| DECOY | 108 |
| REPEAT | 105 |
| FLICKER | 105 |
| COINS | 98 |
| WAVE | 95 |
| FOG | 90 |
| STROOP | 84 |
| TIMED | 75 |
| MIRROR | 72 |
| LOCK | 71 |
| AUTOSHIFT | 70 |
| CHAIN | 70 |
| ROTATE | 68 |
| QUICK | 68 |
| SPEED | 67 |
| RAINBOW | 67 |
| HIDDEN | 67 |
| DOUBLE | 66 |
| NONE | 66 |
| SLIDE | 62 |
| TRIPLE | 59 |
| MIX | 58 |
| MEMORY | 50 |
| MAZE | 7 |

## 100 alanın tamamı

| Alan | Dünya | Ad | Odak mekanikler | İlk kez öğretilen | Final | Bölümler | Kapı sayıları | Hız | Koleksiyon parçası |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Renk Bahçesi | Renk Bahçesi | SAME | SAME | Bahçe Kapısı | 1–5 | 7/9/11/13/21 | 4.4–4.6 | Gülümseyen Lale |
| 2 | Renk Bahçesi | Ters Kapılar | DIFF | DIFF | Ters Çit | 6–10 | 7/9/11/13/21 | 4.5–4.6 | Ters Papatya |
| 3 | Renk Bahçesi | Çift Kapılar | DOUBLE | DOUBLE | İkiz Kemer | 11–15 | 7/9/11/13/21 | 4.5–4.7 | İkiz Kiraz |
| 4 | Renk Bahçesi | Hız Yolu | SPEED | SPEED | Rüzgâr Patikası | 16–20 | 7/9/11/13/21 | 4.6–4.7 | Hızlı Salyangoz |
| 5 | Renk Bahçesi | Renk Hafızası | MEMORY | MEMORY | Unutma Beni | 21–25 | 7/10/12/14/22 | 4.6–4.8 | Hafıza Çiçeği |
| 6 | Şeker Diyarı | Kilitli Renkler | LOCK | LOCK | Kilitli Kavanoz | 26–30 | 8/10/12/14/22 | 4.7–4.8 | Altın Lolipop |
| 7 | Şeker Diyarı | Dönen Kapılar | ROTATE | ROTATE | Şeker Çarkı | 31–35 | 8/10/12/14/22 | 4.7–4.9 | Dönen Pamuk Şeker |
| 8 | Şeker Diyarı | Gökkuşağı Yolu | RAINBOW | RAINBOW | Şerit Şeker | 36–40 | 8/10/12/14/22 | 4.7–4.9 | Gökkuşağı Jelibonu |
| 9 | Şeker Diyarı | Zaman Kapısı | TIMED | TIMED | Kum Saati Kek | 41–45 | 8/10/12/15/23 | 4.8–5.0 | Saatli Kurabiye |
| 10 | Şeker Diyarı | Renk Labirenti | MAZE, SAME, DIFF, RAINBOW | — | Şeker Labirenti | 46–50 | 9/11/13/15/23 | 4.8–5.0 | Labirent Bonbonu |
| 11 | Bulut Krallığı | Ayna Dünyası | MIRROR | MIRROR | Ayna Bulutu | 51–55 | 9/11/13/15/23 | 4.9–5.0 | Ayna Damlası |
| 12 | Bulut Krallığı | Gizli Kapılar | HIDDEN | HIDDEN | Saklı Kale | 56–60 | 9/11/13/15/23 | 4.9–5.1 | Bulut Tüyü |
| 13 | Bulut Krallığı | Yanıltıcı Renkler | STROOP | STROOP | Şaşırtan Kule | 61–65 | 9/11/13/15/23 | 4.9–5.1 | Gökkuşağı Tacı |
| 14 | Bulut Krallığı | Üçlü Kapılar | TRIPLE | TRIPLE | Üç Bulut Köprüsü | 66–70 | 10/12/14/16/24 | 5.0–5.2 | Üçlü Yıldız |
| 15 | Bulut Krallığı | Renk Değişim Alanı | AUTOSHIFT | AUTOSHIFT | Renk Şelalesi | 71–75 | 10/12/14/16/24 | 5.0–5.2 | Bulut Fırçası |
| 16 | Buz Vadisi | Hareketli Yol | SLIDE | SLIDE | Kayan Buzul | 76–80 | 10/12/14/16/24 | 5.1–5.2 | Kızaklı Penguen |
| 17 | Buz Vadisi | Hafıza Zinciri | CHAIN | CHAIN | Buz Zinciri | 81–85 | 10/12/14/16/24 | 5.1–5.3 | Kar Tanesi Kolye |
| 18 | Buz Vadisi | Renk Karıştırma | MIX | MIX | Karışık Buz | 86–90 | 10/12/15/17/25 | 5.1–5.3 | Çift Renkli Kristal |
| 19 | Buz Vadisi | Hızlı Karar | QUICK, SAME, DIFF | QUICK | Buz Pateni | 91–95 | 11/13/15/17/25 | 5.4–5.6 | Hızlı Kızak |
| 20 | Buz Vadisi | Büyük Renk Sınavı | EXAM, SAME, DIFF, DOUBLE, SPEED, MEMORY, LOCK, ROTATE, RAINBOW, TIMED, MIRROR, HIDDEN, STROOP, TRIPLE, AUTOSHIFT, SLIDE, CHAIN, MIX, QUICK | — | Buz Sarayı Sınavı | 96–100 | 11/13/15/17/25 | 5.2–5.4 | Sınav Madalyası |
| 21 | Çiçek Ormanı | Yasak Çiçekler | NONE | NONE | Yasak Bahçe | 101–105 | 11/13/15/17/25 | 5.2–5.4 | Dikenli Gül |
| 22 | Çiçek Ormanı | Sıradaki Yaprak | NEXT | NEXT | Yaprak Sırası | 106–110 | 11/13/15/17/26 | 5.3–5.5 | Altın Yaprak |
| 23 | Çiçek Ormanı | Geri Sarmaşık | PREV | PREV | Sarmaşık Kemer | 111–115 | 12/14/16/18/26 | 5.3–5.5 | Kıvrık Sarmaşık |
| 24 | Çiçek Ormanı | Yankı Korusu | REPEAT | REPEAT | Yankı Ağacı | 116–120 | 12/14/16/18/26 | 5.3–5.5 | Yankı Mantarı |
| 25 | Çiçek Ormanı | Orman Bilmecesi | NONE, NEXT, PREV, REPEAT | — | Bilge Meşe | 121–125 | 12/14/16/18/26 | 5.4–5.6 | Bilge Baykuş |
| 26 | Kristal Mağarası | Sahte Kristaller | DECOY, SAME, DIFF | DECOY | Sahte Hazine | 126–130 | 12/14/16/18/26 | 5.4–5.6 | Parlak Ametist |
| 27 | Kristal Mağarası | Titreyen Işıklar | FLICKER | FLICKER | Titrek Fener | 131–135 | 12/15/17/19/27 | 5.5–5.6 | Fener Böceği |
| 28 | Kristal Mağarası | Yankılı Mağara | REPEAT, HIDDEN | — | Yankı Salonu | 136–140 | 13/15/17/19/27 | 5.5–5.7 | Yankı Kristali |
| 29 | Kristal Mağarası | Kristal Ayna | MIRROR, ROTATE | — | Ayna Kristali | 141–145 | 13/15/17/19/27 | 5.5–5.7 | Prizmatik Taş |
| 30 | Kristal Mağarası | Derin Damar | DECOY, FLICKER, NONE | — | Kristal Kalp | 146–150 | 13/15/17/19/27 | 5.6–5.7 | Kristal Kalp |
| 31 | Güneş Çölü | Serap Yolu | STROOP, DECOY | — | Serap Kapısı | 151–155 | 13/15/17/20/28 | 5.6–5.8 | Serap Taşı |
| 32 | Güneş Çölü | Kum Fırtınası | FOG, SAME, DIFF | FOG | Fırtına Gözü | 156–160 | 14/16/18/20/28 | 5.6–5.8 | Kum Saati |
| 33 | Güneş Çölü | Kayan Kumlar | SLIDE, SPEED | — | Kum Kaydırağı | 161–165 | 14/16/18/20/28 | 5.7–5.9 | Kum Kertenkelesi |
| 34 | Güneş Çölü | Vaha Altınları | COINS, RAINBOW | COINS | Altın Vaha | 166–170 | 14/16/18/20/28 | 5.7–5.9 | Hurma Ağacı |
| 35 | Güneş Çölü | Sıcak Rüzgâr | WAVE, FOG | WAVE | Kavurucu Tepe | 171–175 | 14/16/18/20/28 | 5.7–5.9 | Güneş Pusulası |
| 36 | Oyuncak Şehri | Kurmalı Kapılar | TIMED, NEXT | — | Kurmalı Saat | 176–180 | 15/17/19/21/29 | 5.8–6.0 | Kurmalı Fare |
| 37 | Oyuncak Şehri | Blok Kulesi | TRIPLE, LOCK | — | Blok Kale | 181–185 | 15/17/19/21/29 | 5.8–6.0 | Renkli Blok |
| 38 | Oyuncak Şehri | Tren Rayı | SPEED, DOUBLE, AUTOSHIFT | — | Son İstasyon | 186–190 | 15/17/19/21/29 | 5.8–6.0 | Oyuncak Tren |
| 39 | Oyuncak Şehri | Kukla Tiyatrosu | STROOP, MIRROR | — | Perde Arkası | 191–195 | 15/17/19/21/29 | 5.9–6.1 | Kukla Şapkası |
| 40 | Oyuncak Şehri | Oyuncak Kutusu | TIMED, NEXT, TRIPLE, LOCK, AUTOSHIFT, STROOP | — | Dev Oyuncak Kutusu | 196–200 | 15/17/20/22/30 | 5.9–6.1 | Kurmalı Robot |
| 41 | Su Altı Dünyası | Kabarcık Yolu | COINS, MIX | — | Kabarcık Kapısı | 201–205 | 16/18/20/22/30 | 5.9–6.1 | Deniz Kabuğu |
| 42 | Su Altı Dünyası | Akıntı | WAVE, SLIDE | — | Girdap | 206–210 | 16/18/20/22/30 | 5.9–6.2 | Deniz Atı |
| 43 | Su Altı Dünyası | Mürekkep Bulutu | FOG, MEMORY | — | Ahtapot Mağarası | 211–215 | 16/18/20/22/30 | 6.0–6.2 | Mürekkep Şişesi |
| 44 | Su Altı Dünyası | Mercan Labirenti | MAZE, NONE, SAME, DIFF | — | Mercan Kalesi | 216–220 | 16/18/20/22/31 | 6.0–6.2 | Pembe Mercan |
| 45 | Su Altı Dünyası | Derin Deniz | HIDDEN, FLICKER, PREV | — | Batık Gemi | 221–225 | 17/19/21/23/31 | 6.0–6.3 | İnci |
| 46 | Gökkuşağı Adaları | Yedi Renk Köprüsü | RAINBOW, NONE | — | Gökkuşağı Köprüsü | 226–230 | 17/19/21/23/31 | 6.1–6.3 | Yedi Renk Taşı |
| 47 | Gökkuşağı Adaları | Ada Sekmesi | DOUBLE, NEXT | — | Ada Zirvesi | 231–235 | 17/19/21/23/31 | 6.1–6.3 | Sekme Taşı |
| 48 | Gökkuşağı Adaları | Renk Yağmuru | AUTOSHIFT, COINS | — | Renk Şelalesi | 236–240 | 17/19/21/23/31 | 6.1–6.4 | Yağmur Damlası |
| 49 | Gökkuşağı Adaları | Gökkuşağı Hafızası | CHAIN, RAINBOW | — | Hafıza Adası | 241–245 | 17/20/22/24/32 | 6.2–6.4 | Gökkuşağı Kabuğu |
| 50 | Gökkuşağı Adaları | Ada Turnuvası | RAINBOW, NONE, DOUBLE, NEXT, AUTOSHIFT, CHAIN | — | Ada Kupası | 246–250 | 18/20/22/24/32 | 6.2–6.4 | Ada Kupası |
| 51 | Uzay İstasyonu | Yerçekimsiz | SPEED, WAVE, ROTATE | — | Yörünge | 251–255 | 18/20/22/24/32 | 6.2–6.4 | Mini Uydu |
| 52 | Uzay İstasyonu | Kara Delik | LOCK, MIRROR | — | Olay Ufku | 256–260 | 18/20/22/24/32 | 6.3–6.5 | Kara Delik Bilyesi |
| 53 | Uzay İstasyonu | Radar | HIDDEN, CHAIN | — | Radar Kulesi | 261–265 | 18/20/22/25/33 | 6.3–6.5 | Radar Çanağı |
| 54 | Uzay İstasyonu | Meteor Yağmuru | QUICK, DOUBLE, DECOY | — | Meteor Kuşağı | 266–270 | 19/21/23/25/33 | 6.6–6.9 | Meteor Taşı |
| 55 | Uzay İstasyonu | Uzay Yürüyüşü | SPEED, WAVE, ROTATE, LOCK, MIRROR, HIDDEN, CHAIN, QUICK, DECOY | — | Kaptan Köşkü | 271–275 | 19/21/23/25/33 | 6.4–6.6 | Astronot Kaskı |
| 56 | Balon Dünyası | Patlayan Balonlar | COINS, DIFF | — | Balon Patlatma | 276–280 | 19/21/23/25/33 | 6.4–6.6 | Kırmızı Balon |
| 57 | Balon Dünyası | Uçan Kapılar | SLIDE, TIMED | — | Uçan Kale | 281–285 | 19/21/23/25/33 | 6.4–6.6 | Sepetli Balon |
| 58 | Balon Dünyası | Balon Festivali | MIX, NEXT | — | Festival Alanı | 286–290 | 20/22/24/26/34 | 6.4–6.7 | Festival Bayrağı |
| 59 | Balon Dünyası | Rüzgârlı Gökyüzü | WAVE, AUTOSHIFT | — | Rüzgâr Gülü | 291–295 | 20/22/24/26/34 | 6.5–6.7 | Uçurtma |
| 60 | Balon Dünyası | Büyük Balon | COINS, DIFF, SLIDE, TIMED, MIX, NEXT, WAVE, AUTOSHIFT | — | Dev Balon | 296–300 | 20/22/24/26/34 | 6.5–6.7 | Altın Balon |
| 61 | Saat Kulesi | Tik Tak | TIMED, QUICK | — | Tik Tak Kapısı | 301–305 | 20/22/24/26/34 | 6.5–6.8 | Cep Saati |
| 62 | Saat Kulesi | Çarklar | ROTATE, REPEAT | — | Büyük Çark | 306–310 | 20/22/25/27/35 | 6.6–6.8 | Pirinç Çark |
| 63 | Saat Kulesi | Geri Sayım | TIMED, HIDDEN | — | Geri Sayım Kulesi | 311–315 | 21/23/25/27/35 | 6.6–6.8 | Kum Saati Kolye |
| 64 | Saat Kulesi | Ters Zaman | MIRROR, PREV | — | Zaman Aynası | 316–320 | 21/23/25/27/35 | 6.6–6.9 | Ters Saat |
| 65 | Saat Kulesi | Gece Yarısı | TIMED, QUICK, ROTATE, REPEAT, HIDDEN, MIRROR, PREV | — | Gece Yarısı Çanı | 321–325 | 21/23/25/27/35 | 6.7–6.9 | Kule Çanı |
| 66 | Renk Fabrikası | Boya Bandı | AUTOSHIFT, SAME | — | Boya Kazanı | 326–330 | 21/23/25/27/36 | 6.7–6.9 | Boya Kovası |
| 67 | Renk Fabrikası | Kalite Kontrol | NONE, STROOP | — | Kontrol Masası | 331–335 | 22/24/26/28/36 | 6.7–6.9 | Kalite Damgası |
| 68 | Renk Fabrikası | Karışım Kazanı | MIX, RAINBOW, LOCK | — | Büyük Kazan | 336–340 | 22/24/26/28/36 | 6.8–7.0 | Renk Tüpü |
| 69 | Renk Fabrikası | Paketleme | TRIPLE, CHAIN | — | Paket Bandı | 341–345 | 22/24/26/28/36 | 6.8–7.0 | Hediye Paketi |
| 70 | Renk Fabrikası | Fabrika Şefi | AUTOSHIFT, NONE, STROOP, MIX, RAINBOW, LOCK, TRIPLE, CHAIN | — | Şefin Ofisi | 346–350 | 22/24/26/28/36 | 6.8–7.0 | Şef Şapkası |
| 71 | Sihirli Kütüphane | Okunan Renkler | STROOP | — | Okuma Salonu | 351–355 | 22/25/27/29/37 | 6.8–7.1 | Sihirli Kitap |
| 72 | Sihirli Kütüphane | Kayıp Sayfalar | MEMORY, CHAIN | — | Kayıp Bölüm | 356–360 | 23/25/27/29/37 | 6.9–7.1 | Yırtık Sayfa |
| 73 | Sihirli Kütüphane | Büyülü Kitap | DECOY, FLICKER, REPEAT | — | Büyü Kitabı | 361–365 | 23/25/27/29/37 | 6.9–7.1 | Büyülü Tüy Kalem |
| 74 | Sihirli Kütüphane | Raflar Arası | SLIDE, NEXT, PREV | — | En Üst Raf | 366–370 | 23/25/27/29/37 | 6.9–7.2 | Kitap Ayracı |
| 75 | Sihirli Kütüphane | Büyük Arşiv | STROOP, MEMORY, CHAIN, DECOY, FLICKER, REPEAT, SLIDE, NEXT, PREV | — | Büyük Arşiv | 371–375 | 23/25/27/30/38 | 7.0–7.2 | Bilge Gözlüğü |
| 76 | Yıldız Bahçesi | Kayan Yıldızlar | QUICK, COINS | — | Yıldız Kayması | 376–380 | 24/26/28/30/38 | 7.0–7.2 | Kayan Yıldız |
| 77 | Yıldız Bahçesi | Takımyıldız | CHAIN, HIDDEN | — | Büyük Ayı | 381–385 | 24/26/28/30/38 | 7.0–7.3 | Takımyıldız Haritası |
| 78 | Yıldız Bahçesi | Ay Işığı | FOG, FLICKER | — | Dolunay | 386–390 | 24/26/28/30/38 | 7.0–7.3 | Ay Taşı |
| 79 | Yıldız Bahçesi | Yıldız Tozu | AUTOSHIFT, MIRROR, LOCK | — | Toz Bulutu | 391–395 | 24/26/28/30/38 | 7.1–7.3 | Yıldız Tozu Şişesi |
| 80 | Yıldız Bahçesi | Bahçe Gecesi | QUICK, COINS, CHAIN, HIDDEN, FOG, FLICKER, AUTOSHIFT, MIRROR, LOCK | — | Gece Bahçesi | 396–400 | 25/27/29/31/39 | 7.1–7.3 | Gece Çiçeği |
| 81 | Prizma Sarayı | Işık Kırılması | ROTATE, NONE | — | Kırılma Odası | 401–405 | 25/27/29/31/39 | 7.1–7.4 | Cam Prizma |
| 82 | Prizma Sarayı | Ayna Salonu | MIRROR, STROOP | — | Ayna Salonu | 406–410 | 25/27/29/31/39 | 7.2–7.4 | El Aynası |
| 83 | Prizma Sarayı | Prizma Kapıları | MIX, TIMED | — | Prizma Kemeri | 411–415 | 25/27/29/31/39 | 7.2–7.4 | Renk Kırıntısı |
| 84 | Prizma Sarayı | Taht Odası | TRIPLE, REPEAT, NEXT | — | Taht | 416–420 | 25/27/30/32/40 | 7.2–7.5 | Prizma Asası |
| 85 | Prizma Sarayı | Saray Balosu | ROTATE, NONE, MIRROR, STROOP, MIX, TIMED, TRIPLE, REPEAT, NEXT | — | Balo Salonu | 421–425 | 26/28/30/32/40 | 7.3–7.5 | Kristal Ayakkabı |
| 86 | Robot Şehri | Kodlanmış Kapılar | NEXT, PREV, REPEAT | — | Kod Kapısı | 426–430 | 26/28/30/32/40 | 7.3–7.5 | Devre Kartı |
| 87 | Robot Şehri | Montaj Hattı | AUTOSHIFT, DOUBLE, SPEED | — | Montaj Robotu | 431–435 | 26/28/30/32/40 | 7.3–7.6 | Altın Vida |
| 88 | Robot Şehri | Sistem Hatası | FLICKER, DECOY, STROOP | — | Hata Ekranı | 436–440 | 26/28/30/32/40 | 7.3–7.6 | Bozuk Ampul |
| 89 | Robot Şehri | Güç Kesintisi | LOCK, FOG, HIDDEN | — | Jeneratör | 441–445 | 27/29/31/33/40 | 7.4–7.6 | Pil |
| 90 | Robot Şehri | Robot Lideri | NEXT, PREV, REPEAT, AUTOSHIFT, DOUBLE, SPEED, FLICKER, DECOY, STROOP, LOCK, FOG, HIDDEN | — | Ana Bilgisayar | 446–450 | 27/29/31/33/40 | 7.4–7.6 | Robot Kalbi |
| 91 | Renk Karnavalı | Konfeti | COINS, RAINBOW, QUICK | — | Konfeti Topu | 451–455 | 27/29/31/33/40 | 7.4–7.7 | Konfeti Kutusu |
| 92 | Renk Karnavalı | Atlıkarınca | ROTATE, SLIDE, TIMED | — | Atlıkarınca | 456–460 | 27/29/31/33/40 | 7.5–7.7 | Oyuncak At |
| 93 | Renk Karnavalı | Maskeli Balo | STROOP, MEMORY | — | Maske Salonu | 461–465 | 27/30/32/34/40 | 7.5–7.7 | Karnaval Maskesi |
| 94 | Renk Karnavalı | Davul Ritmi | WAVE, TRIPLE | — | Davul Sahnesi | 466–470 | 28/30/32/34/40 | 7.5–7.8 | Davul |
| 95 | Renk Karnavalı | Karnaval Geçidi | COINS, RAINBOW, QUICK, ROTATE, SLIDE, TIMED, STROOP, MEMORY, WAVE, TRIPLE | — | Geçit Töreni | 471–475 | 28/30/32/34/40 | 7.5–7.8 | Karnaval Tacı |
| 96 | Büyük Renk Evreni | Işık Tünelleri | SPEED, HIDDEN, CHAIN | — | Tünel Sonu | 476–480 | 28/30/32/34/40 | 7.6–7.8 | Işık Küresi |
| 97 | Büyük Renk Evreni | Neon Hız | QUICK, MIRROR, ROTATE | — | Neon Pist | 481–485 | 28/30/32/34/40 | 8.0–8.2 | Neon Halka |
| 98 | Büyük Renk Evreni | Renk Fırtınası | ROTATE, SLIDE, TIMED, WAVE | — | Fırtına Merkezi | 486–490 | 29/31/33/34/40 | 7.6–7.9 | Fırtına Kristali |
| 99 | Büyük Renk Evreni | Evren Kapısı | NONE, NEXT, PREV, REPEAT, DECOY, FLICKER, LOCK, MIRROR, STROOP, AUTOSHIFT, MIX, TRIPLE | — | Evren Kapısı | 491–495 | 29/31/33/34/40 | 7.6–7.9 | Evren Anahtarı |
| 100 | Büyük Renk Evreni | Büyük Renk Evreni | SAME, DIFF, DOUBLE, SPEED, MEMORY, LOCK, ROTATE, RAINBOW, TIMED, MIRROR, HIDDEN, STROOP, TRIPLE, AUTOSHIFT, SLIDE, CHAIN, MIX, QUICK, NONE, NEXT, PREV, REPEAT, DECOY, FLICKER, FOG, COINS, WAVE | — | Renklerin Kalbi | 496–500 | 29/31/33/34/40 | 7.7–7.9 | Renk Evreni Kupası |
