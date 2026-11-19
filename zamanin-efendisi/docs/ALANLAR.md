# 100 Alan · 500 Bölüm Tasarımı

Her alan 5 bölümdür: **1** yeni özelliği tanıtır · **2** tek başına kullandırır · **3** önceki mekaniklerle birleştirir ·
**4** zor stratejik problem · **5** alan patronu / özel final. Her 25. bölüm özel patron, her 50. bölüm büyük dünya finalidir.

100 ayrı karmaşık sistem yazmak yerine çeşitlilik; 20 temel mekaniğin (README §Mekanikler) yeni etkileşimleri,
çevresel kurallar (`EvolutionRules`, `CausalLink`) ve özel hedeflerle (`Goal`) üretilir. Durum sütunu kodda neyin
gerçekten oynanabilir olduğunu gösterir.

**Alan 1–7 elle tasarlandı.** Alan 8–100'ün bölümleri, alanın temasına göre seçilen şablon karışımı (su/köprü,
çöl/tarla, köy/şehir, orman, savunma) ve kurallarla (enerji kısıtı, hızlı çöl, kalabalık düşman, Gelecek hedefi, daralan
tur sınırı) bölüm numarasından tohumlanarak üretilir; her birinin çözümü üretim sırasında oyunun kendi simülasyonuyla
sentezlenip doğrulanır ve `LevelSolvabilityTest` 500 bölümün hepsini yeniden doğrular. Tabloda "açılan özellik" olarak
yazan özel mekaniklerin (ör. gelgit, faz atlama, paralel çizgiler) çoğu henüz kendi kuralı olarak kodlanmadı; bu
alanlar şimdilik temel mekaniklerin kombinasyonlarıyla oynanır.

| Alan | Bölümler | Ad | Açılan özellik / kural | Durum |
|---|---|---|---|---|
| 1 | 1–5 | İlk Kırılma | Geçmiş ve Şimdi, köprü evrimi, çöl yayılımı, köy→kasaba, çit→taş sur, zaman tuzağı, ilk patron | **Oynanabilir** |
| 2 | 6–10 | Geleceğin Kapısı | Üçüncü çağ: Gelecek, kadim orman, Gelecek robotları, ilk robot fabrikası patronu | **Oynanabilir** |
| 3 | 11–15 | Kayıp Orman | Doğa evrimi: ormanı önlemek için kesmek, üç çağlık çöl, şehir için ağaç seçimi, orman kalkanı, yaşlı ağaç patronu | **Oynanabilir** |
| 4 | 16–20 | Taş Köprüler | Yapı evrimi: taş köprü, iki şehir, enerji kalkanı, zaman mühürleri (çağlar arası bağlantı), Taş Kral patronu | **Oynanabilir** |
| 5 | 21–25 | Enerji Vadisi | Zaman enerjisi, kristaller ve kristal paradoksu · 25: Enerji Lordu (özel patron) | **Oynanabilir** |
| 6 | 26–30 | Donmuş Yıllar | Zaman dondurma: tarihî yapıyı koruma, ormanı durdurma, kampı küçük tutma, fidanı fidan bırakma · Buz Devri patronu | **Oynanabilir** |
| 7 | 31–35 | Yıkılmış Krallık | Müttefik birlikler, savunma kuleleri, yağmacı/ajan/faz hırsızı/savunmacı okçu/teknoloji uzmanı · Yıkılmış Kral patronu | **Oynanabilir** |
| 8 | 36–40 | Kayıp Teknoloji | Teknoloji aktarımı: Gelecek planını Geçmiş'te kurma | Oynanabilir (üretilmiş) |
| 9 | 41–45 | Sonsuz Döngü | Geri sarma: son 3 turu enerjiyle geri alma (bölüm içi mekanik) | Oynanabilir (üretilmiş) |
| 10 | 46–50 | Paradoks Kalesi | İlk büyük paradoks · 50: **1. dünya finali** | Oynanabilir (üretilmiş) |
| 11 | 51–55 | İki Gelecek | Alternatif gelecek: aynı geçmişten iki Gelecek seçimi | Oynanabilir (üretilmiş) |
| 12 | 56–60 | Demir Medeniyet | Maden → sanayi → enerji santrali zinciri | Oynanabilir (üretilmiş) |
| 13 | 61–65 | Zaman Tüccarları | Çağlar arası ticaret: kaynağı bir çağdan diğerine taşıma | Oynanabilir (üretilmiş) |
| 14 | 66–70 | Gölgelerin Çağı | Gizli düşmanlar: yalnız bir çağda görünen birlikler | Oynanabilir (üretilmiş) |
| 15 | 71–75 | Çöküş Noktası | Zaman fırtınası: her N turda bölge çağ değiştirir · 75: özel patron | Oynanabilir (üretilmiş) |
| 16 | 76–80 | Kayıp Ordular | Çağlar arası birlikler, yaşlanan ordular | Oynanabilir (üretilmiş) |
| 17 | 81–85 | Çifte Zaman | Zaman kopyası: önceki tur hareketinin yankısı | Oynanabilir (üretilmiş) |
| 18 | 86–90 | Nedensellik Labirenti | Zincirleme bağlantılar (aynı çağ içi `CausalLink` ağları) | Oynanabilir (üretilmiş) |
| 19 | 91–95 | Sonsuz Şehir | Medeniyet evrimi: şehir türü kararlara göre değişir | Oynanabilir (üretilmiş) |
| 20 | 96–100 | Zamanın Kalbi | Bütün temel mekanikler · 100: **2. dünya finali** | Oynanabilir (üretilmiş) |
| 21 | 101–105 | Gelgit Kıyısı | Gelgit: su seviyesi çağlara göre yükselir/çekilir | Oynanabilir (üretilmiş) |
| 22 | 106–110 | Batık Liman | Batık yapılar Gelecek'te mercan resifine evrilir (yeni geçit) | Oynanabilir (üretilmiş) |
| 23 | 111–115 | Fener Zinciri | Işık hattı: fenerler birbirini çağlar boyunca tetikler | Oynanabilir (üretilmiş) |
| 24 | 116–120 | Korsan Takımadası | Gemiyle hareket eden akıncılar, kıyı surları | Oynanabilir (üretilmiş) |
| 25 | 121–125 | Mercan Krallığı | Doğa + yapı evrimi su altında · 125: özel patron | Oynanabilir (üretilmiş) |
| 26 | 126–130 | Buzul Geçidi | Buz: Geçmiş'te donmuş nehir yürünür, Şimdi'de erir | Oynanabilir (üretilmiş) |
| 27 | 131–135 | Kar Fırtınası | Görüş sınırı: yalnız kahramanın 2 hücre çevresi görünür | Oynanabilir (üretilmiş) |
| 28 | 136–140 | Kristal Mağaralar | Kristal yansıması: bir çağdaki eylem simetrik hücreye yansır | Oynanabilir (üretilmiş) |
| 29 | 141–145 | Yeraltı Nehri | İki katlı harita: yüzey ve mağara katmanı | Oynanabilir (üretilmiş) |
| 30 | 146–150 | Dondurulmuş İmparator | Dondurma + geri sarma patronu · 150: **3. dünya finali** | Oynanabilir (üretilmiş) |
| 31 | 151–155 | Rüzgâr Değirmenleri | Rüzgâr yönü çağa göre döner; değirmen enerjisi | Oynanabilir (üretilmiş) |
| 32 | 156–160 | Uçan Adalar | Yükseklik: adalar Gelecek'te havalanır, köprüler kopar | Oynanabilir (üretilmiş) |
| 33 | 161–165 | Bulut Bahçeleri | Yağmur bulutu: bitki büyümesini 1 çağ hızlandırır | Oynanabilir (üretilmiş) |
| 34 | 166–170 | Fırtına Kuleleri | Yıldırım: her 3 turda en yüksek yapıya çarpar | Oynanabilir (üretilmiş) |
| 35 | 171–175 | Gökyüzü Limanı | Hava gemisiyle çağlar arası ticaret · 175: özel patron | Oynanabilir (üretilmiş) |
| 36 | 176–180 | Güneş Saati | Gölge mekaniği: tura bağlı gölge hücreleri geçilmez | Oynanabilir (üretilmiş) |
| 37 | 181–185 | Enerji Ağları | Enerji hattı: santralden hedefe kesintisiz bağlantı | Oynanabilir (üretilmiş) |
| 38 | 186–190 | Robot Atölyesi | Gelecek'te robot üretimi; Geçmiş'te hammadde kesme | Oynanabilir (üretilmiş) |
| 39 | 191–195 | Mekanik Koruyucular | Zaman muhafızı dostu robot birlikleri | Oynanabilir (üretilmiş) |
| 40 | 196–200 | Saat Kulesi | Bütün gökyüzü mekanikleri · 200: **4. dünya finali** | Oynanabilir (üretilmiş) |
| 41 | 201–205 | Kızıl Çöl | Hızlı çöl (3 hücre/çağ) ve vaha kuralı | Oynanabilir (üretilmiş) |
| 42 | 206–210 | Kum Saatleri | Kum saati nesneleri: tur sayısına göre çağ değiştirir | Oynanabilir (üretilmiş) |
| 43 | 211–215 | Kayıp Vaha | Kayıp medeniyet: kurtarılan köy Gelecek'te müttefik olur | Oynanabilir (üretilmiş) |
| 44 | 216–220 | Piramit Yolu | Gizli geçitler: Geçmiş'te açılan mühür Şimdi'de kapı | Oynanabilir (üretilmiş) |
| 45 | 221–225 | Firavunun Mührü | Mühür zincirleri ve tuzaklar · 225: özel patron | Oynanabilir (üretilmiş) |
| 46 | 226–230 | Kervan Yolları | Ticaret rotası koruma: kervan her tur ilerler | Oynanabilir (üretilmiş) |
| 47 | 231–235 | Seraplar | Sahte hedefler: yalnız bir çağda gerçek olan yapılar | Oynanabilir (üretilmiş) |
| 48 | 236–240 | Bakır Şehirler | Metal yapılar Gelecek'te paslanır (dayanıklılık düşer) | Oynanabilir (üretilmiş) |
| 49 | 241–245 | Su Kemerleri | Su taşıma: kemerler kasabaları büyütür | Oynanabilir (üretilmiş) |
| 50 | 246–250 | Çöl İmparatorluğu | Büyük çöl finali · 250: **5. dünya finali** | Oynanabilir (üretilmiş) |
| 51 | 251–255 | Volkan Eteği | Lav: Geçmiş'te akan lav Şimdi'de verimli toprak olur | Oynanabilir (üretilmiş) |
| 52 | 256–260 | Kül Ormanı | Yangın yayılımı: ağaç zincirleri boyunca ilerler | Oynanabilir (üretilmiş) |
| 53 | 261–265 | Obsidyen Kale | Kırılmaz yapılar ve zaman kilidi | Oynanabilir (üretilmiş) |
| 54 | 266–270 | Ateş Ritüeli | Kısıtlı eylem türleri: her tur yalnız bir kategori | Oynanabilir (üretilmiş) |
| 55 | 271–275 | Ejder Yuvası | Patron, çağlar arasında yer değiştirir · 275: özel patron | Oynanabilir (üretilmiş) |
| 56 | 276–280 | Jeotermal Vadi | Enerji üretimi + nedensellik zinciri | Oynanabilir (üretilmiş) |
| 57 | 281–285 | Deprem Hattı | Fay çizgisi: her çağda harita bölünür | Oynanabilir (üretilmiş) |
| 58 | 286–290 | Mağma Madenleri | Maden açma: Şimdi'de sanayi, Gelecek'te çökme | Oynanabilir (üretilmiş) |
| 59 | 291–295 | Külden Doğuş | Yıkılmış şehirleri Geçmiş'ten yeniden kurma | Oynanabilir (üretilmiş) |
| 60 | 296–300 | Ateşin Kalbi | Volkan finali · 300: **6. dünya finali** | Oynanabilir (üretilmiş) |
| 61 | 301–305 | Paralel Bahçeler | Paralel zaman çizgisi: iki Şimdi aynı anda | Oynanabilir (üretilmiş) |
| 62 | 306–310 | Ayna Şehri | Ayna çağlar: Gelecek'te sağ-sol yer değiştirir | Oynanabilir (üretilmiş) |
| 63 | 311–315 | Yankılar Vadisi | Zaman yankısı: önceki eylem 2 tur sonra tekrarlanır | Oynanabilir (üretilmiş) |
| 64 | 316–320 | Kelebek Bahçesi | Kelebek etkisi zincirleri: tek fidan, 5 sonuç | Oynanabilir (üretilmiş) |
| 65 | 321–325 | Çatallanan Yol | Tarihî kararlar: 3 seçenekli bölüm dalları · 325: özel patron | Oynanabilir (üretilmiş) |
| 66 | 326–330 | Zaman Kaçakları | Beklenmedik konumlarda beliren düşmanlar | Oynanabilir (üretilmiş) |
| 67 | 331–335 | Kopya Ordusu | Zaman kopyası ile çift hamle bulmacaları | Oynanabilir (üretilmiş) |
| 68 | 336–340 | Paradoks Kütüphanesi | Paradoksları kasıtlı kullanma (geri ödeme stratejisi) | Oynanabilir (üretilmiş) |
| 69 | 341–345 | Kilitli Anlar | Zaman kilidi + dondurma birleşimi | Oynanabilir (üretilmiş) |
| 70 | 346–350 | Sonsuzluk Sarayı | Paralel çizgiler finali · 350: **7. dünya finali** | Oynanabilir (üretilmiş) |
| 71 | 351–355 | Teknoloji Şafağı | Teknoloji aktarımı ileri seviye: plan ağacı | Oynanabilir (üretilmiş) |
| 72 | 356–360 | Işık Şehri | Işık geçitleri ağları | Oynanabilir (üretilmiş) |
| 73 | 361–365 | Enerji Fırtınası | Zaman fırtınası + enerji kısıtı | Oynanabilir (üretilmiş) |
| 74 | 366–370 | Yapay Orman | Gelecek'te dikilen yapay ağaçlar (geriye etki yok) | Oynanabilir (üretilmiş) |
| 75 | 371–375 | Makine Tanrısı | Gelecek teknolojisi uzmanı patron · 375: özel patron | Oynanabilir (üretilmiş) |
| 76 | 376–380 | Kuantum Köprüler | Zaman köprüleri: aynı hücre iki çağda birleşir | Oynanabilir (üretilmiş) |
| 77 | 381–385 | Zaman Birleştirme | Farklı çağ özelliklerini özel alanlarda birleştirme | Oynanabilir (üretilmiş) |
| 78 | 386–390 | Ters Akış | Özel alanlarda Gelecek→Geçmiş bağlantısı (döngü denetimiyle) | Oynanabilir (üretilmiş) |
| 79 | 391–395 | Kronos Laboratuvarı | Yaşlandırma: nesneleri hızla geliştirme/eskitme | Oynanabilir (üretilmiş) |
| 80 | 396–400 | Geleceğin Tahtı | Teknoloji finali · 400: **8. dünya finali** | Oynanabilir (üretilmiş) |
| 81 | 401–405 | Kayıp Hafıza | Zaman günlüğü bulmacaları: geçmiş kararları hatırlama | Oynanabilir (üretilmiş) |
| 82 | 406–410 | Unutulmuş Tanrılar | Kadim mühürler, çok koşullu bağlantılar | Oynanabilir (üretilmiş) |
| 83 | 411–415 | Yıldız Haritası | Gök cisimleri çağlara göre hizalanır | Oynanabilir (üretilmiş) |
| 84 | 416–420 | Kara Delik Kıyısı | Zaman yavaşlaması: bölgede hareket 2 tur sürer | Oynanabilir (üretilmiş) |
| 85 | 421–425 | Sonsuz Merdiven | Döngüsel harita: kenarlar birbirine bağlı · 425: özel patron | Oynanabilir (üretilmiş) |
| 86 | 426–430 | Zaman Mahkemesi | Paradoks yaratıcısı düşman: senin değişikliklerini boşa çıkarır | Oynanabilir (üretilmiş) |
| 87 | 431–435 | Kırık Saatler | Rastgele olmayan zaman olayları takvimi | Oynanabilir (üretilmiş) |
| 88 | 436–440 | Ebedi Savaş | Altı düşman stratejisinin birlikte kullanımı | Oynanabilir (üretilmiş) |
| 89 | 441–445 | Çekirdek Parçaları | Çok hedefli bölümler: 3 parça, 3 çağ | Oynanabilir (üretilmiş) |
| 90 | 446–450 | Düşman Muhafızlar | Rakip zaman muhafızı da çağ değiştirir · 450: **9. dünya finali** | Oynanabilir (üretilmiş) |
| 91 | 451–455 | Işığın Sınırı | Bütün evrim zincirleri, en uzun nedensellik | Oynanabilir (üretilmiş) |
| 92 | 456–460 | Hiçliğin Kıyısı | Silinen hücreler: paradoks alanı genişler | Oynanabilir (üretilmiş) |
| 93 | 461–465 | Son Medeniyet | Medeniyet evrimi + kayıp medeniyet müttefikleri | Oynanabilir (üretilmiş) |
| 94 | 466–470 | Zamanın Örsü | Zaman birleştirme ustalık bölümleri | Oynanabilir (üretilmiş) |
| 95 | 471–475 | Üç Taht | Her çağda ayrı patron · 475: özel patron | Oynanabilir (üretilmiş) |
| 96 | 476–480 | Kadim Çekirdek | Çekirdek onarımı: tüm çağlarda eşzamanlı hedef | Oynanabilir (üretilmiş) |
| 97 | 481–485 | Sonsuz Fırtına | Zaman fırtınası + paralel çizgi | Oynanabilir (üretilmiş) |
| 98 | 486–490 | Son Paradoks | En karmaşık paradoks zincirleri | Oynanabilir (üretilmiş) |
| 99 | 491–495 | Zamanın Kapısı | Bütün mekanikler, sınırlı enerji ve hamle | Oynanabilir (üretilmiş) |
| 100 | 496–500 | Zamanın Efendisi | Büyük final · 500: **10. dünya finali**, ardından Yeni Zaman Döngüsü (prestij) | Oynanabilir (üretilmiş) |

## Bölüm hedefi çeşitliliği
15 hedef türü (`Goal`) alanlara yayılır: hedefe ulaşma, belirli hücrelerde nesne, nesne sayısı, nesnenin yokluğu,
düşmanları yenme, hayatta kalma — bunlar kodda hazırdır; kaynak eşiği, paradoks çözme, üç çağda güvenli yol,
hamle sınırı, bilim insanını kurtarma, çekirdek onarma, alternatif gelecek açma ve düşman zaman kalesini ele geçirme
sonraki aşamalarda `Goal` alt sınıfları olarak eklenecektir.

## Çözülebilirlik
Her bölüm `LevelDef.solution` ile doğrulanmış bir çözüm taşır. `LevelSolvabilityTest` her bölüm için çözümün
geçerli olduğunu, par değerini aşmadığını, her adımda artımlı nedensellik hesabının sıfırdan hesapla aynı olduğunu
ve oynatmanın deterministik olduğunu doğrular.
