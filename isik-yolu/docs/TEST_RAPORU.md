# Test Raporu — Işık Yolu 1.1.0

Tarih: 18 Kasım 2026 · Komut: `./gradlew :engine:test :app:testDebugUnitTest`

**Sonuç: 43 testin 43'ü geçti, 0 hata.** (500 bölüm sürümü)

| Test sınıfı | Test | Sonuç | Süre |
|---|---|---|---|
| `BeamReflectionTest` | 6 | ✅ | 0,05 sn |
| `ColorFilterTest` | 5 | ✅ | <0,01 sn |
| `PrismTest` | 3 | ✅ | <0,01 sn |
| `DoorKeyTest` | 5 | ✅ | <0,01 sn |
| `GameSessionTest` | 5 | ✅ | 7,3 sn |
| `LevelPackTest` | 3 | ✅ | 0,03 sn |
| `AllLevelsSolvableTest` | 2 | ✅ | 4,5 sn |
| `StarRatingTest` | 2 | ✅ | <0,01 sn |
| `ProgressTest` | 9 | ✅ | <0,01 sn |
| `UiFlowTest` (Robolectric, gerçek çizim) | 3 | ✅ | 94 sn |

## İstenen 10 kontrol

| # | Kontrol | Nasıl doğrulandı | Sonuç |
|---|---|---|---|
| 1 | Derleme testi | `:engine:compileJava`, `:app:assembleDebug`, `:app:assembleRelease` (R8). Lint: 0 hata | ✅ |
| 2 | Bölüm yükleme | `LevelPackTest.allLevelsLoad`: 500 bölüm, kimlikler 1–500, grup eşleşmesi, kaynak/hedef var, çakışan hücre yok, JSON gidiş-dönüş aynı, **kopya düzen yok**. Uygulamada `GameLoader` aynı dosyayı yükler (UiFlowTest) | ✅ |
| 3 | Işın yansıma | `BeamReflectionTest`: 8 yön/ayna birleşimi, yansıma tablosu ve tersinirlik, duvar/blok/kaynakta durma, ızgara kenarı, 4000 rastgele yoğun düzende sonlanma ve döngü yokluğu | ✅ |
| 4 | Renk filtresi | `ColorFilterTest`: 6 filtre rengi, engelleme, tam renk eşleşmesi, hedefte karışım (kırmızı + yeşil = sarı), kesişen ışınlar | ✅ |
| 5 | Prizma | `PrismTest`: 4 yönde beyaz → kırmızı sol / yeşil düz / mavi sağ; tek renkli sapma; prizmadan önce filtre | ✅ |
| 6 | Kapı ve anahtar | `DoorKeyTest`: ışık alan anahtar kapıyı açar, karanlık anahtar açmaz, kapı kendini açamaz (en küçük sabit nokta), zincirleme kapılar, çözücü kapılı bölümü çözer. Ayrıca 9. grup bölümlerinde kapılar başlangıçta kapalı | ✅ |
| 7 | 500 bölümün çözülebilirliği | `AllLevelsSolvableTest`: her bölüm başlangıçta çözülmemiş; BFS çözüm buldu; kayıtlı optimum = BFS optimumu; çözüm yeniden oynatılınca çözülüyor; durum uzayı ipucu sınırının altında. `GameSessionTest.hintsLeadToSolution`: her bölüm yalnızca ipuçlarını izleyerek optimum hamlede bitiyor. `UiFlowTest.allLevelsPlayableThroughUi`: 500 bölüm sırayla, kilitler açılarak, **oyun ekranının dokunma arayüzünden** oynandı ve hepsi 3 ★ aldı | ✅ |
| 8 | Yıldız hesaplama | `StarRatingTest`: eşikler ve monotonluk; arayüz testinde 1500/1500 yıldız | ✅ |
| 9 | Kayıt ve yükleme | `ProgressTest`: JSON gidiş-dönüş, eski 100 bölümlük kaydın yeni sürümde okunması, her 10 bölümde +1 ipucu kuralı, bozuk kayıtta varsayılana dönme, kilit açma, yalnızca iyileşen rekorlar, başarılar, günlük seri, sıfırlama. UiFlowTest: yeni Activity aynı ilerlemeyi SharedPreferences'tan okuyor | ✅ |
| 10 | Android build testi | Debug ve release APK üretildi, `aapt2 dump badging` ile paket bilgisi ve `apksigner verify` ile imza doğrulandı | ✅ |

### Ek testler

- **Oturum** (`GameSessionTest`): sabit ayna dönmez, çözüldükten sonra hamle kabul edilmez, geri alma durumu bit bit geri
  getirir, blok itme ve geri alma, rastgele yanlış hamlelerden sonra ipucuyla çözüm.
- **Günlük bulmaca** (`AllLevelsSolvableTest.dailyPuzzleIsDeterministicAndSolvable`): aynı gün aynı bulmaca,
  ardışık günler farklı, 10 günün hepsi çözülebilir.
- **Arayüz akışı** (`UiFlowTest.allScreensRenderAndFlowWorks`): açılış → menü → öğretici → bölüm 1 dokunarak çözülür →
  tamamlandı penceresi → bölüm seçme → 9 farklı mekanikten bölüm → hamle, geri alma, ipucu penceresi, ipucu kullanımı
  (arka plan iş parçacığı) → duraklatma → günlük bulmaca (oynanıp çözülür) → başarılar → ayarlar → "Nasıl Oynanır?".
  Her adımda ekran görüntüsü alındı: [`docs/ekranlar/`](ekranlar).

## Sınırlar

- Uygulama gerçek bir telefonda veya emülatörde çalıştırılmadı (kapsayıcıda KVM yok). Arayüz Robolectric 4.14.1
  ile, Android 14 çerçevesinin gerçek çizim hattıyla (NATIVE grafik modu) JVM üzerinde çalıştırıldı. Dokunma
  olayları `BoardView.Host` arayüzünden verildi; ham `MotionEvent` sürükleme hareketi otomatik test edilmedi.
- 60 FPS hedefi cihazda ölçülmedi. Çizim döngüsü bellek ayırmayacak şekilde yazıldı (README › Performans).
- Ses çalma Robolectric'te gölgelenir; seslerin cihazda duyulması elle denenmelidir.

## Dünyalara göre zorluk

| Dünya | Bölümler | Optimum hamle (en az–en çok) | Ortalama | En büyük durum uzayı |
|---|---|---|---|---|
| Işık Bahçesi | 1–100 | 1–10 | 4,84 | 44 992 |
| Kristal Mağaralar | 101–200 | 2–10 | 5,80 | 63 488 |
| Gece Bahçesi | 201–300 | 3–12 | 6,90 | 76 160 |
| Yıldız Atölyesi | 301–400 | 5–15 | 8,81 | 76 800 |
| Işık Tapınağı | 401–500 | 6–15 | 9,56 | 79 872 |

Dünya ortalamaları kesin artan (`LevelPackTest.difficultyProgresses`). 12 bölümde (420, 429, 430, 434, 439, 440,
454, 460, 469, 470, 494, 498) hedef zorluk aralığında bölüm bulunamadığı için aralık 1–2 hamle gevşetildi.
İlk 100 bölüm önceki sürümle birebir aynıdır.

İpucu kuralı testi: 3 ipucuyla başlanır, 3 yıldız tek başına ipucu vermez, her 10 tamamlanan bölümde +1;
500 bölüm bitince 3 + 50 = 53 ipucu (arayüz testinde doğrulandı).

## İlk dünyada konulara göre zorluk

| Grup | Konu | Optimum hamle (en az–en çok) | Ortalama |
|---|---|---|---|
| 1 | Temel aynalar | 1–3 | 2,3 |
| 2 | Birden fazla ayna | 3–7 | 4,5 |
| 3 | Engeller | 4–6 | 4,6 |
| 4 | Renkli ışıklar | 3–8 | 4,9 |
| 5 | Renk filtreleri | 3–6 | 4,7 |
| 6 | Prizmalar | 3–6 | 4,6 |
| 7 | Birden fazla hedef | 4–7 | 5,5 |
| 8 | Hareketli bloklar | 3–7 | 5,2 |
| 9 | Kapılar ve anahtarlar | 4–7 | 5,4 |
| 10 | Karma mekanikler | 5–10 | 6,7 |

Her grubun ikinci yarısı ilk yarısından kolay değildir (`LevelPackTest.difficultyProgresses`).
Yeni mekaniğin geldiği grupların ilk bölümleri bilinçli olarak biraz daha kolaydır.

## 500 bölümün doğrulama tablosu

"Kaynak/Hedef" = kaynak sayısı / hedef sayısı. "Durum" = başlangıçtan ulaşılabilen farklı tahta durumu sayısı (BFS).

| # | Ad | Izgara | Kaynak/Hedef | Nesneler | Optimum | Durum | Çözülebilir |
|---|---|---|---|---|---|---|---|
| 1 | İlk Kıvılcım 1 | 5×7 | 1/1 | 1 ayna (1 döner) | 1 | 2 | ✅ |
| 2 | İlk Kıvılcım 2 | 5×7 | 1/1 | 1 ayna (1 döner) | 1 | 2 | ✅ |
| 3 | İlk Kıvılcım 3 | 5×7 | 1/1 | 2 ayna (2 döner) | 2 | 4 | ✅ |
| 4 | İlk Kıvılcım 4 | 5×7 | 1/1 | 2 ayna (2 döner), 1 duvar | 2 | 4 | ✅ |
| 5 | İlk Kıvılcım 5 | 6×8 | 1/1 | 4 ayna (4 döner), 1 duvar | 3 | 16 | ✅ |
| 6 | İlk Kıvılcım 6 | 6×8 | 1/1 | 3 ayna (3 döner), 1 duvar | 2 | 8 | ✅ |
| 7 | İlk Kıvılcım 7 | 6×8 | 1/1 | 4 ayna (4 döner), 2 duvar | 3 | 16 | ✅ |
| 8 | İlk Kıvılcım 8 | 6×8 | 1/1 | 5 ayna (5 döner), 2 duvar | 3 | 32 | ✅ |
| 9 | İlk Kıvılcım 9 | 6×8 | 1/1 | 5 ayna (5 döner), 2 duvar | 3 | 32 | ✅ |
| 10 | İlk Kıvılcım 10 | 6×8 | 1/1 | 5 ayna (5 döner), 3 duvar | 3 | 32 | ✅ |
| 11 | Ayna Labirenti 1 | 6×8 | 1/1 | 5 ayna (5 döner), 1 duvar | 4 | 32 | ✅ |
| 12 | Ayna Labirenti 2 | 6×8 | 1/1 | 4 ayna (4 döner), 1 duvar | 3 | 16 | ✅ |
| 13 | Ayna Labirenti 3 | 6×8 | 1/1 | 4 ayna (4 döner), 1 duvar | 3 | 16 | ✅ |
| 14 | Ayna Labirenti 4 | 6×8 | 1/1 | 5 ayna (5 döner), 1 duvar | 4 | 32 | ✅ |
| 15 | Ayna Labirenti 5 | 6×8 | 1/1 | 7 ayna (7 döner), 2 duvar | 4 | 128 | ✅ |
| 16 | Ayna Labirenti 6 | 6×8 | 1/1 | 6 ayna (6 döner), 2 duvar | 4 | 64 | ✅ |
| 17 | Ayna Labirenti 7 | 6×8 | 1/1 | 7 ayna (7 döner), 2 duvar | 5 | 128 | ✅ |
| 18 | Ayna Labirenti 8 | 6×8 | 1/1 | 8 ayna (8 döner), 2 duvar | 6 | 256 | ✅ |
| 19 | Ayna Labirenti 9 | 6×8 | 1/1 | 9 ayna (9 döner), 3 duvar | 5 | 512 | ✅ |
| 20 | Ayna Labirenti 10 | 6×8 | 1/1 | 10 ayna (10 döner), 3 duvar | 7 | 1024 | ✅ |
| 21 | Gölge Duvarlar 1 | 6×9 | 1/1 | 7 ayna (5 döner), 6 duvar | 4 | 32 | ✅ |
| 22 | Gölge Duvarlar 2 | 6×9 | 1/1 | 6 ayna (5 döner), 7 duvar | 4 | 32 | ✅ |
| 23 | Gölge Duvarlar 3 | 6×9 | 1/1 | 7 ayna (5 döner), 8 duvar | 4 | 32 | ✅ |
| 24 | Gölge Duvarlar 4 | 6×9 | 1/1 | 6 ayna (5 döner), 9 duvar | 4 | 32 | ✅ |
| 25 | Gölge Duvarlar 5 | 6×9 | 1/1 | 8 ayna (7 döner), 10 duvar | 5 | 128 | ✅ |
| 26 | Gölge Duvarlar 6 | 7×9 | 1/1 | 9 ayna (6 döner), 11 duvar | 4 | 64 | ✅ |
| 27 | Gölge Duvarlar 7 | 7×9 | 1/1 | 10 ayna (7 döner), 12 duvar | 5 | 128 | ✅ |
| 28 | Gölge Duvarlar 8 | 7×9 | 1/1 | 10 ayna (7 döner), 13 duvar | 5 | 128 | ✅ |
| 29 | Gölge Duvarlar 9 | 7×9 | 1/1 | 11 ayna (8 döner), 14 duvar | 5 | 256 | ✅ |
| 30 | Gölge Duvarlar 10 | 7×9 | 1/1 | 11 ayna (9 döner), 15 duvar | 6 | 512 | ✅ |
| 31 | Renk Cümbüşü 1 | 7×9 | 2/2 | 5 ayna (5 döner), 2 duvar, renkli ışık | 4 | 32 | ✅ |
| 32 | Renk Cümbüşü 2 | 7×9 | 2/2 | 5 ayna (5 döner), 2 duvar, renkli ışık | 4 | 32 | ✅ |
| 33 | Renk Cümbüşü 3 | 7×9 | 2/2 | 5 ayna (5 döner), 3 duvar, renkli ışık | 3 | 32 | ✅ |
| 34 | Renk Cümbüşü 4 | 7×9 | 2/2 | 5 ayna (5 döner), 3 duvar, renkli ışık | 4 | 32 | ✅ |
| 35 | Renk Cümbüşü 5 | 7×9 | 2/2 | 6 ayna (6 döner), 4 duvar, renkli ışık | 4 | 64 | ✅ |
| 36 | Renk Cümbüşü 6 | 7×9 | 2/1 | 7 ayna (7 döner), 4 duvar, renkli ışık | 4 | 128 | ✅ |
| 37 | Renk Cümbüşü 7 | 7×9 | 2/1 | 7 ayna (7 döner), 5 duvar, renkli ışık | 5 | 128 | ✅ |
| 38 | Renk Cümbüşü 8 | 7×9 | 3/2 | 11 ayna (11 döner), 5 duvar, renkli ışık | 6 | 2048 | ✅ |
| 39 | Renk Cümbüşü 9 | 7×9 | 3/2 | 11 ayna (11 döner), 6 duvar, renkli ışık | 7 | 2048 | ✅ |
| 40 | Renk Cümbüşü 10 | 7×9 | 3/2 | 11 ayna (11 döner), 6 duvar, renkli ışık | 8 | 2048 | ✅ |
| 41 | Süzgeç Atölyesi 1 | 7×9 | 1/1 | 5 ayna (5 döner), 2 duvar, 2 filtre | 4 | 32 | ✅ |
| 42 | Süzgeç Atölyesi 2 | 7×9 | 1/1 | 5 ayna (5 döner), 2 duvar, 2 filtre | 3 | 32 | ✅ |
| 43 | Süzgeç Atölyesi 3 | 7×9 | 1/1 | 4 ayna (4 döner), 3 duvar, 2 filtre | 3 | 16 | ✅ |
| 44 | Süzgeç Atölyesi 4 | 7×9 | 1/1 | 6 ayna (6 döner), 3 duvar, 2 filtre | 4 | 64 | ✅ |
| 45 | Süzgeç Atölyesi 5 | 7×9 | 2/2 | 7 ayna (7 döner), 4 duvar, 4 filtre | 6 | 128 | ✅ |
| 46 | Süzgeç Atölyesi 6 | 7×9 | 2/2 | 6 ayna (6 döner), 4 duvar, 4 filtre | 4 | 64 | ✅ |
| 47 | Süzgeç Atölyesi 7 | 7×9 | 2/2 | 8 ayna (8 döner), 5 duvar, 4 filtre | 6 | 256 | ✅ |
| 48 | Süzgeç Atölyesi 8 | 7×9 | 2/2 | 8 ayna (8 döner), 5 duvar, 4 filtre | 6 | 256 | ✅ |
| 49 | Süzgeç Atölyesi 9 | 7×9 | 2/2 | 7 ayna (7 döner), 6 duvar, 5 filtre | 5 | 128 | ✅ |
| 50 | Süzgeç Atölyesi 10 | 7×9 | 2/2 | 8 ayna (8 döner), 6 duvar, 5 filtre | 6 | 256 | ✅ |
| 51 | Prizma Bahçesi 1 | 7×10 | 1/3 | 3 ayna (3 döner), 2 duvar, 1 prizma | 3 | 8 | ✅ |
| 52 | Prizma Bahçesi 2 | 7×10 | 1/3 | 3 ayna (3 döner), 2 duvar, 1 prizma | 3 | 8 | ✅ |
| 53 | Prizma Bahçesi 3 | 7×10 | 1/3 | 4 ayna (4 döner), 3 duvar, 1 prizma | 4 | 16 | ✅ |
| 54 | Prizma Bahçesi 4 | 7×10 | 1/2 | 5 ayna (5 döner), 3 duvar, 1 filtre, 1 prizma | 4 | 32 | ✅ |
| 55 | Prizma Bahçesi 5 | 7×10 | 1/3 | 6 ayna (6 döner), 4 duvar, 1 prizma | 5 | 64 | ✅ |
| 56 | Prizma Bahçesi 6 | 7×10 | 1/3 | 7 ayna (7 döner), 4 duvar, 1 prizma | 6 | 128 | ✅ |
| 57 | Prizma Bahçesi 7 | 7×10 | 1/2 | 6 ayna (6 döner), 5 duvar, 1 filtre, 1 prizma | 5 | 64 | ✅ |
| 58 | Prizma Bahçesi 8 | 7×10 | 1/3 | 6 ayna (6 döner), 5 duvar, 1 prizma | 5 | 64 | ✅ |
| 59 | Prizma Bahçesi 9 | 7×10 | 1/2 | 8 ayna (8 döner), 6 duvar, 1 filtre, 1 prizma | 5 | 256 | ✅ |
| 60 | Prizma Bahçesi 10 | 7×10 | 1/3 | 8 ayna (8 döner), 6 duvar, 1 prizma | 6 | 256 | ✅ |
| 61 | Çoklu Hedef 1 | 7×10 | 2/2 | 7 ayna (7 döner), 2 duvar | 4 | 128 | ✅ |
| 62 | Çoklu Hedef 2 | 7×10 | 2/2 | 5 ayna (5 döner), 2 duvar | 4 | 32 | ✅ |
| 63 | Çoklu Hedef 3 | 7×10 | 2/2 | 6 ayna (6 döner), 3 duvar | 5 | 64 | ✅ |
| 64 | Çoklu Hedef 4 | 7×10 | 2/2 | 6 ayna (6 döner), 3 duvar | 5 | 64 | ✅ |
| 65 | Çoklu Hedef 5 | 7×10 | 3/3 | 7 ayna (7 döner), 4 duvar | 6 | 128 | ✅ |
| 66 | Çoklu Hedef 6 | 7×10 | 3/3 | 9 ayna (9 döner), 4 duvar | 5 | 512 | ✅ |
| 67 | Çoklu Hedef 7 | 7×10 | 3/5 | 11 ayna (11 döner), 5 duvar, 1 prizma | 7 | 2048 | ✅ |
| 68 | Çoklu Hedef 8 | 7×10 | 3/5 | 11 ayna (11 döner), 5 duvar, 1 prizma | 7 | 2048 | ✅ |
| 69 | Çoklu Hedef 9 | 7×10 | 3/5 | 10 ayna (10 döner), 6 duvar, 1 prizma, renkli ışık | 6 | 1024 | ✅ |
| 70 | Çoklu Hedef 10 | 7×10 | 3/5 | 10 ayna (10 döner), 6 duvar, 1 prizma, renkli ışık | 6 | 1024 | ✅ |
| 71 | Hareketli Taşlar 1 | 7×9 | 1/1 | 3 ayna (3 döner), 8 duvar, 1 blok | 4 | 400 | ✅ |
| 72 | Hareketli Taşlar 2 | 7×9 | 1/1 | 2 ayna (2 döner), 8 duvar, 1 blok | 3 | 204 | ✅ |
| 73 | Hareketli Taşlar 3 | 7×9 | 1/1 | 3 ayna (3 döner), 9 duvar, 1 blok | 4 | 344 | ✅ |
| 74 | Hareketli Taşlar 4 | 7×9 | 1/1 | 3 ayna (3 döner), 9 duvar, 1 blok | 4 | 392 | ✅ |
| 75 | Hareketli Taşlar 5 | 7×9 | 1/1 | 2 ayna (2 döner), 10 duvar, 2 blok | 4 | 4704 | ✅ |
| 76 | Hareketli Taşlar 6 | 7×9 | 2/2 | 5 ayna (5 döner), 10 duvar, 2 blok | 6 | 13920 | ✅ |
| 77 | Hareketli Taşlar 7 | 7×9 | 2/2 | 6 ayna (6 döner), 11 duvar, 2 blok | 7 | 35904 | ✅ |
| 78 | Hareketli Taşlar 8 | 7×9 | 2/2 | 5 ayna (5 döner), 11 duvar, 2 blok | 6 | 24960 | ✅ |
| 79 | Hareketli Taşlar 9 | 7×9 | 2/2 | 6 ayna (6 döner), 12 duvar, 2 blok | 7 | 44992 | ✅ |
| 80 | Hareketli Taşlar 10 | 7×9 | 2/2 | 6 ayna (6 döner), 12 duvar, 2 blok | 7 | 4992 | ✅ |
| 81 | Kilitli Kapılar 1 | 7×9 | 2/2 | 7 ayna (7 döner), 3 duvar, 1 kapı | 4 | 128 | ✅ |
| 82 | Kilitli Kapılar 2 | 7×9 | 2/2 | 5 ayna (5 döner), 3 duvar, 1 kapı | 4 | 32 | ✅ |
| 83 | Kilitli Kapılar 3 | 7×9 | 2/2 | 7 ayna (7 döner), 4 duvar, 1 kapı, renkli ışık | 4 | 128 | ✅ |
| 84 | Kilitli Kapılar 4 | 7×9 | 2/2 | 6 ayna (6 döner), 4 duvar, 1 kapı, renkli ışık | 5 | 64 | ✅ |
| 85 | Kilitli Kapılar 5 | 7×9 | 2/2 | 6 ayna (6 döner), 5 duvar, 1 kapı | 4 | 64 | ✅ |
| 86 | Kilitli Kapılar 6 | 7×9 | 2/2 | 7 ayna (7 döner), 5 duvar, 1 kapı, renkli ışık | 5 | 128 | ✅ |
| 87 | Kilitli Kapılar 7 | 7×9 | 3/3 | 10 ayna (10 döner), 6 duvar, 2 kapı | 7 | 1024 | ✅ |
| 88 | Kilitli Kapılar 8 | 7×9 | 3/3 | 10 ayna (10 döner), 6 duvar, 2 kapı, renkli ışık | 7 | 1024 | ✅ |
| 89 | Kilitli Kapılar 9 | 7×9 | 3/3 | 9 ayna (9 döner), 7 duvar, 2 kapı | 7 | 512 | ✅ |
| 90 | Kilitli Kapılar 10 | 7×9 | 3/3 | 9 ayna (9 döner), 7 duvar, 2 kapı, renkli ışık | 7 | 512 | ✅ |
| 91 | Işığın Ustası 1 | 7×10 | 2/2 | 6 ayna (5 döner), 4 duvar, 1 filtre, 1 blok | 5 | 1600 | ✅ |
| 92 | Işığın Ustası 2 | 7×10 | 2/4 | 7 ayna (6 döner), 4 duvar, 1 prizma, 1 kapı | 5 | 64 | ✅ |
| 93 | Işığın Ustası 3 | 7×10 | 2/1 | 7 ayna (7 döner), 5 duvar, 1 blok, renkli ışık | 7 | 7040 | ✅ |
| 94 | Işığın Ustası 4 | 7×10 | 2/2 | 7 ayna (7 döner), 5 duvar, 2 filtre, 1 kapı | 6 | 128 | ✅ |
| 95 | Işığın Ustası 5 | 7×10 | 1/3 | 6 ayna (6 döner), 6 duvar, 1 filtre, 1 prizma, 1 blok | 6 | 3264 | ✅ |
| 96 | Işığın Ustası 6 | 7×10 | 3/2 | 9 ayna (7 döner), 6 duvar, 1 kapı, renkli ışık | 6 | 128 | ✅ |
| 97 | Işığın Ustası 7 | 7×10 | 2/2 | 7 ayna (7 döner), 7 duvar, 1 filtre, 1 blok, 1 kapı | 7 | 6016 | ✅ |
| 98 | Işığın Ustası 8 | 7×10 | 2/3 | 8 ayna (8 döner), 7 duvar, 1 filtre, 1 prizma, 1 kapı | 7 | 256 | ✅ |
| 99 | Işığın Ustası 9 | 7×10 | 3/2 | 11 ayna (10 döner), 8 duvar, 1 blok, 1 kapı, renkli ışık | 10 | 24576 | ✅ |
| 100 | Işığın Ustası 10 | 7×10 | 3/5 | 8 ayna (8 döner), 8 duvar, 1 prizma, 1 blok, 1 kapı | 8 | 5888 | ✅ |
| 101 | Kristal Kapı 1 | 5×8 | 1/1 | 3 ayna (3 döner), 2 duvar | 2 | 8 | ✅ |
| 102 | Kristal Kapı 2 | 5×8 | 1/1 | 4 ayna (3 döner), 2 duvar | 2 | 8 | ✅ |
| 103 | Kristal Kapı 3 | 5×8 | 1/1 | 5 ayna (5 döner), 2 duvar | 4 | 32 | ✅ |
| 104 | Kristal Kapı 4 | 5×8 | 1/1 | 4 ayna (4 döner), 3 duvar | 3 | 16 | ✅ |
| 105 | Kristal Kapı 5 | 6×9 | 1/1 | 6 ayna (5 döner), 3 duvar | 3 | 32 | ✅ |
| 106 | Kristal Kapı 6 | 6×9 | 1/1 | 5 ayna (5 döner), 3 duvar | 3 | 32 | ✅ |
| 107 | Kristal Kapı 7 | 6×9 | 1/1 | 6 ayna (5 döner), 4 duvar | 3 | 32 | ✅ |
| 108 | Kristal Kapı 8 | 6×9 | 1/1 | 8 ayna (7 döner), 4 duvar | 4 | 128 | ✅ |
| 109 | Kristal Kapı 9 | 6×9 | 1/1 | 7 ayna (7 döner), 4 duvar | 4 | 128 | ✅ |
| 110 | Kristal Kapı 10 | 6×9 | 1/1 | 8 ayna (7 döner), 5 duvar | 4 | 128 | ✅ |
| 111 | Yankı Odası 1 | 6×9 | 1/1 | 6 ayna (6 döner), 3 duvar | 4 | 64 | ✅ |
| 112 | Yankı Odası 2 | 6×9 | 1/1 | 7 ayna (7 döner), 3 duvar | 5 | 128 | ✅ |
| 113 | Yankı Odası 3 | 6×9 | 1/1 | 7 ayna (7 döner), 3 duvar | 5 | 128 | ✅ |
| 114 | Yankı Odası 4 | 6×9 | 1/1 | 8 ayna (8 döner), 3 duvar | 6 | 256 | ✅ |
| 115 | Yankı Odası 5 | 6×9 | 1/1 | 9 ayna (8 döner), 4 duvar | 5 | 256 | ✅ |
| 116 | Yankı Odası 6 | 6×9 | 1/1 | 9 ayna (9 döner), 4 duvar | 6 | 512 | ✅ |
| 117 | Yankı Odası 7 | 6×9 | 1/1 | 9 ayna (9 döner), 4 duvar | 6 | 512 | ✅ |
| 118 | Yankı Odası 8 | 6×9 | 1/1 | 10 ayna (10 döner), 4 duvar | 7 | 1024 | ✅ |
| 119 | Yankı Odası 9 | 6×9 | 1/1 | 11 ayna (11 döner), 5 duvar | 7 | 2048 | ✅ |
| 120 | Yankı Odası 10 | 6×9 | 1/1 | 12 ayna (11 döner), 5 duvar | 7 | 2048 | ✅ |
| 121 | Taş Koridor 1 | 6×10 | 1/1 | 8 ayna (6 döner), 8 duvar | 4 | 64 | ✅ |
| 122 | Taş Koridor 2 | 6×10 | 1/1 | 8 ayna (6 döner), 9 duvar | 4 | 64 | ✅ |
| 123 | Taş Koridor 3 | 6×10 | 1/1 | 8 ayna (6 döner), 10 duvar | 4 | 64 | ✅ |
| 124 | Taş Koridor 4 | 6×10 | 1/1 | 9 ayna (7 döner), 11 duvar | 5 | 128 | ✅ |
| 125 | Taş Koridor 5 | 6×10 | 1/1 | 9 ayna (8 döner), 12 duvar | 5 | 256 | ✅ |
| 126 | Taş Koridor 6 | 7×10 | 1/1 | 11 ayna (8 döner), 13 duvar | 5 | 256 | ✅ |
| 127 | Taş Koridor 7 | 7×10 | 1/1 | 12 ayna (9 döner), 14 duvar | 6 | 512 | ✅ |
| 128 | Taş Koridor 8 | 7×10 | 1/1 | 11 ayna (9 döner), 15 duvar | 6 | 512 | ✅ |
| 129 | Taş Koridor 9 | 7×10 | 1/1 | 13 ayna (11 döner), 16 duvar | 7 | 2048 | ✅ |
| 130 | Taş Koridor 10 | 7×10 | 1/1 | 14 ayna (11 döner), 17 duvar | 7 | 2048 | ✅ |
| 131 | Renkli Damlalar 1 | 7×10 | 2/2 | 7 ayna (6 döner), 4 duvar, renkli ışık | 4 | 64 | ✅ |
| 132 | Renkli Damlalar 2 | 7×10 | 2/2 | 7 ayna (6 döner), 4 duvar, renkli ışık | 4 | 64 | ✅ |
| 133 | Renkli Damlalar 3 | 7×10 | 2/2 | 7 ayna (7 döner), 5 duvar, renkli ışık | 5 | 128 | ✅ |
| 134 | Renkli Damlalar 4 | 7×10 | 2/2 | 8 ayna (7 döner), 5 duvar, renkli ışık | 5 | 128 | ✅ |
| 135 | Renkli Damlalar 5 | 7×10 | 2/2 | 9 ayna (8 döner), 6 duvar, renkli ışık | 5 | 256 | ✅ |
| 136 | Renkli Damlalar 6 | 7×10 | 2/1 | 10 ayna (9 döner), 6 duvar, renkli ışık | 6 | 512 | ✅ |
| 137 | Renkli Damlalar 7 | 7×10 | 2/1 | 11 ayna (11 döner), 7 duvar, renkli ışık | 7 | 2048 | ✅ |
| 138 | Renkli Damlalar 8 | 7×10 | 3/2 | 15 ayna (14 döner), 7 duvar, renkli ışık | 9 | 16384 | ✅ |
| 139 | Renkli Damlalar 9 | 7×10 | 3/2 | 14 ayna (13 döner), 8 duvar, renkli ışık | 9 | 8192 | ✅ |
| 140 | Renkli Damlalar 10 | 7×10 | 3/2 | 15 ayna (12 döner), 8 duvar, renkli ışık | 8 | 4096 | ✅ |
| 141 | Cam Süzgeçler 1 | 7×10 | 1/1 | 7 ayna (6 döner), 4 duvar, 2 filtre | 4 | 64 | ✅ |
| 142 | Cam Süzgeçler 2 | 7×10 | 1/1 | 7 ayna (6 döner), 4 duvar, 2 filtre | 4 | 64 | ✅ |
| 143 | Cam Süzgeçler 3 | 7×10 | 1/1 | 6 ayna (6 döner), 5 duvar, 2 filtre | 4 | 64 | ✅ |
| 144 | Cam Süzgeçler 4 | 7×10 | 1/1 | 7 ayna (7 döner), 5 duvar, 2 filtre | 5 | 128 | ✅ |
| 145 | Cam Süzgeçler 5 | 7×10 | 2/2 | 10 ayna (8 döner), 6 duvar, 4 filtre | 5 | 256 | ✅ |
| 146 | Cam Süzgeçler 6 | 7×10 | 2/2 | 11 ayna (8 döner), 6 duvar, 4 filtre | 5 | 256 | ✅ |
| 147 | Cam Süzgeçler 7 | 7×10 | 2/2 | 11 ayna (9 döner), 7 duvar, 4 filtre | 6 | 512 | ✅ |
| 148 | Cam Süzgeçler 8 | 7×10 | 2/2 | 10 ayna (9 döner), 7 duvar, 4 filtre | 6 | 512 | ✅ |
| 149 | Cam Süzgeçler 9 | 7×10 | 2/2 | 10 ayna (9 döner), 8 duvar, 5 filtre | 6 | 512 | ✅ |
| 150 | Cam Süzgeçler 10 | 7×10 | 2/2 | 10 ayna (10 döner), 8 duvar, 5 filtre | 7 | 1024 | ✅ |
| 151 | Gökkuşağı Kuyusu 1 | 7×11 | 1/3 | 5 ayna (5 döner), 4 duvar, 1 prizma | 4 | 32 | ✅ |
| 152 | Gökkuşağı Kuyusu 2 | 7×11 | 1/3 | 5 ayna (5 döner), 4 duvar, 1 prizma | 4 | 32 | ✅ |
| 153 | Gökkuşağı Kuyusu 3 | 7×11 | 1/3 | 5 ayna (5 döner), 5 duvar, 1 prizma | 4 | 32 | ✅ |
| 154 | Gökkuşağı Kuyusu 4 | 7×11 | 1/2 | 8 ayna (8 döner), 5 duvar, 1 filtre, 1 prizma | 7 | 256 | ✅ |
| 155 | Gökkuşağı Kuyusu 5 | 7×11 | 1/3 | 9 ayna (9 döner), 6 duvar, 1 prizma | 6 | 512 | ✅ |
| 156 | Gökkuşağı Kuyusu 6 | 7×11 | 1/3 | 9 ayna (7 döner), 6 duvar, 1 prizma | 5 | 128 | ✅ |
| 157 | Gökkuşağı Kuyusu 7 | 7×11 | 1/2 | 9 ayna (8 döner), 7 duvar, 1 filtre, 1 prizma | 6 | 256 | ✅ |
| 158 | Gökkuşağı Kuyusu 8 | 7×11 | 1/3 | 9 ayna (8 döner), 7 duvar, 1 prizma | 6 | 256 | ✅ |
| 159 | Gökkuşağı Kuyusu 9 | 7×11 | 1/2 | 9 ayna (9 döner), 8 duvar, 1 filtre, 1 prizma | 6 | 512 | ✅ |
| 160 | Gökkuşağı Kuyusu 10 | 7×11 | 1/3 | 11 ayna (10 döner), 8 duvar, 1 prizma | 7 | 1024 | ✅ |
| 161 | Çifte Parıltı 1 | 7×11 | 2/2 | 8 ayna (8 döner), 4 duvar | 6 | 256 | ✅ |
| 162 | Çifte Parıltı 2 | 7×11 | 2/2 | 8 ayna (7 döner), 4 duvar | 5 | 128 | ✅ |
| 163 | Çifte Parıltı 3 | 7×11 | 2/2 | 8 ayna (8 döner), 5 duvar | 6 | 256 | ✅ |
| 164 | Çifte Parıltı 4 | 7×11 | 2/2 | 8 ayna (7 döner), 5 duvar | 5 | 128 | ✅ |
| 165 | Çifte Parıltı 5 | 7×11 | 3/3 | 10 ayna (10 döner), 6 duvar | 8 | 1024 | ✅ |
| 166 | Çifte Parıltı 6 | 7×11 | 3/3 | 11 ayna (11 döner), 6 duvar | 8 | 2048 | ✅ |
| 167 | Çifte Parıltı 7 | 7×11 | 3/5 | 10 ayna (9 döner), 7 duvar, 1 prizma | 6 | 512 | ✅ |
| 168 | Çifte Parıltı 8 | 7×11 | 3/5 | 13 ayna (12 döner), 7 duvar, 1 prizma | 8 | 4096 | ✅ |
| 169 | Çifte Parıltı 9 | 7×11 | 3/5 | 12 ayna (12 döner), 8 duvar, 1 prizma, renkli ışık | 9 | 4096 | ✅ |
| 170 | Çifte Parıltı 10 | 7×11 | 3/5 | 12 ayna (10 döner), 8 duvar, 1 prizma, renkli ışık | 7 | 1024 | ✅ |
| 171 | Kayan Kayalar 1 | 7×10 | 1/1 | 5 ayna (5 döner), 10 duvar, 1 blok | 5 | 1664 | ✅ |
| 172 | Kayan Kayalar 2 | 7×10 | 1/1 | 5 ayna (4 döner), 10 duvar, 1 blok | 4 | 848 | ✅ |
| 173 | Kayan Kayalar 3 | 7×10 | 1/1 | 4 ayna (3 döner), 11 duvar, 1 blok | 5 | 40 | ✅ |
| 174 | Kayan Kayalar 4 | 7×10 | 1/1 | 5 ayna (5 döner), 11 duvar, 1 blok | 6 | 1664 | ✅ |
| 175 | Kayan Kayalar 5 | 7×10 | 1/1 | 4 ayna (4 döner), 12 duvar, 2 blok | 5 | 21216 | ✅ |
| 176 | Kayan Kayalar 6 | 7×10 | 2/2 | 8 ayna (6 döner), 12 duvar, 2 blok | 6 | 44992 | ✅ |
| 177 | Kayan Kayalar 7 | 7×10 | 2/2 | 6 ayna (5 döner), 13 duvar, 2 blok | 6 | 33120 | ✅ |
| 178 | Kayan Kayalar 8 | 7×10 | 2/2 | 9 ayna (5 döner), 13 duvar, 2 blok | 9 | 24960 | ✅ |
| 179 | Kayan Kayalar 9 | 7×10 | 2/2 | 7 ayna (6 döner), 14 duvar, 2 blok | 6 | 60544 | ✅ |
| 180 | Kayan Kayalar 10 | 7×10 | 2/2 | 10 ayna (7 döner), 14 duvar, 2 blok | 8 | 63488 | ✅ |
| 181 | Mühürlü Geçit 1 | 7×10 | 2/2 | 8 ayna (7 döner), 5 duvar, 1 kapı | 5 | 128 | ✅ |
| 182 | Mühürlü Geçit 2 | 7×10 | 2/2 | 7 ayna (6 döner), 5 duvar, 1 kapı | 4 | 64 | ✅ |
| 183 | Mühürlü Geçit 3 | 7×10 | 2/2 | 8 ayna (7 döner), 6 duvar, 1 kapı, renkli ışık | 5 | 128 | ✅ |
| 184 | Mühürlü Geçit 4 | 7×10 | 2/2 | 10 ayna (10 döner), 6 duvar, 1 kapı, renkli ışık | 6 | 1024 | ✅ |
| 185 | Mühürlü Geçit 5 | 7×10 | 2/2 | 9 ayna (9 döner), 7 duvar, 1 kapı | 7 | 512 | ✅ |
| 186 | Mühürlü Geçit 6 | 7×10 | 2/2 | 10 ayna (10 döner), 7 duvar, 1 kapı, renkli ışık | 5 | 1024 | ✅ |
| 187 | Mühürlü Geçit 7 | 7×10 | 3/3 | 13 ayna (11 döner), 8 duvar, 2 kapı | 8 | 2048 | ✅ |
| 188 | Mühürlü Geçit 8 | 7×10 | 3/3 | 13 ayna (11 döner), 8 duvar, 2 kapı, renkli ışık | 8 | 2048 | ✅ |
| 189 | Mühürlü Geçit 9 | 7×10 | 3/3 | 12 ayna (10 döner), 9 duvar, 2 kapı | 7 | 1024 | ✅ |
| 190 | Mühürlü Geçit 10 | 7×10 | 3/3 | 12 ayna (10 döner), 9 duvar, 2 kapı, renkli ışık | 7 | 1024 | ✅ |
| 191 | Mağaranın Kalbi 1 | 7×11 | 2/2 | 9 ayna (8 döner), 6 duvar, 1 filtre, 1 blok | 7 | 14080 | ✅ |
| 192 | Mağaranın Kalbi 2 | 7×11 | 2/4 | 9 ayna (9 döner), 6 duvar, 1 prizma, 1 kapı | 6 | 512 | ✅ |
| 193 | Mağaranın Kalbi 3 | 7×11 | 2/1 | 10 ayna (8 döner), 7 duvar, 1 blok, renkli ışık | 7 | 14336 | ✅ |
| 194 | Mağaranın Kalbi 4 | 7×11 | 2/2 | 10 ayna (9 döner), 7 duvar, 2 filtre, 1 kapı | 7 | 512 | ✅ |
| 195 | Mağaranın Kalbi 5 | 7×11 | 1/3 | 8 ayna (8 döner), 8 duvar, 1 filtre, 1 prizma, 1 blok | 7 | 13312 | ✅ |
| 196 | Mağaranın Kalbi 6 | 7×11 | 3/2 | 11 ayna (10 döner), 8 duvar, 1 kapı, renkli ışık | 8 | 1024 | ✅ |
| 197 | Mağaranın Kalbi 7 | 7×11 | 2/2 | 10 ayna (9 döner), 9 duvar, 1 filtre, 1 blok, 1 kapı | 8 | 24064 | ✅ |
| 198 | Mağaranın Kalbi 8 | 7×11 | 2/3 | 12 ayna (10 döner), 9 duvar, 1 filtre, 1 prizma, 1 kapı | 8 | 1024 | ✅ |
| 199 | Mağaranın Kalbi 9 | 7×11 | 3/2 | 12 ayna (10 döner), 10 duvar, 1 blok, 1 kapı, renkli ışık | 10 | 27648 | ✅ |
| 200 | Mağaranın Kalbi 10 | 7×11 | 3/5 | 12 ayna (10 döner), 10 duvar, 1 prizma, 1 blok, 1 kapı | 10 | 30720 | ✅ |
| 201 | Ay Işığı 1 | 6×8 | 1/1 | 6 ayna (5 döner), 4 duvar, 2 filtre | 3 | 32 | ✅ |
| 202 | Ay Işığı 2 | 6×8 | 1/1 | 5 ayna (4 döner), 4 duvar, 1 blok | 3 | 560 | ✅ |
| 203 | Ay Işığı 3 | 6×8 | 2/2 | 9 ayna (6 döner), 4 duvar, 1 kapı | 4 | 64 | ✅ |
| 204 | Ay Işığı 4 | 6×8 | 2/2 | 9 ayna (6 döner), 5 duvar | 4 | 64 | ✅ |
| 205 | Ay Işığı 5 | 7×9 | 1/1 | 9 ayna (8 döner), 5 duvar, 2 filtre | 5 | 256 | ✅ |
| 206 | Ay Işığı 6 | 7×9 | 1/1 | 7 ayna (6 döner), 5 duvar, 1 blok | 4 | 3008 | ✅ |
| 207 | Ay Işığı 7 | 7×9 | 2/2 | 11 ayna (10 döner), 6 duvar, 1 kapı | 5 | 1024 | ✅ |
| 208 | Ay Işığı 8 | 7×9 | 2/2 | 13 ayna (11 döner), 6 duvar | 7 | 2048 | ✅ |
| 209 | Ay Işığı 9 | 7×9 | 1/1 | 11 ayna (9 döner), 6 duvar, 2 filtre | 5 | 512 | ✅ |
| 210 | Ay Işığı 10 | 7×9 | 1/1 | 9 ayna (8 döner), 7 duvar, 1 blok | 5 | 1536 | ✅ |
| 211 | Sarmaşık Yolu 1 | 7×9 | 1/1 | 8 ayna (7 döner), 5 duvar, 1 blok | 5 | 6016 | ✅ |
| 212 | Sarmaşık Yolu 2 | 7×9 | 2/2 | 14 ayna (11 döner), 5 duvar, 1 kapı | 6 | 2048 | ✅ |
| 213 | Sarmaşık Yolu 3 | 7×9 | 2/2 | 14 ayna (10 döner), 5 duvar | 7 | 1024 | ✅ |
| 214 | Sarmaşık Yolu 4 | 7×9 | 1/1 | 11 ayna (9 döner), 5 duvar, 2 filtre | 6 | 512 | ✅ |
| 215 | Sarmaşık Yolu 5 | 7×9 | 1/1 | 10 ayna (9 döner), 6 duvar, 1 blok | 7 | 20480 | ✅ |
| 216 | Sarmaşık Yolu 6 | 7×9 | 2/2 | 14 ayna (12 döner), 6 duvar, 1 kapı | 8 | 4096 | ✅ |
| 217 | Sarmaşık Yolu 7 | 7×9 | 2/2 | 16 ayna (13 döner), 6 duvar | 9 | 8192 | ✅ |
| 218 | Sarmaşık Yolu 8 | 7×9 | 1/1 | 13 ayna (11 döner), 6 duvar, 2 filtre | 7 | 2048 | ✅ |
| 219 | Sarmaşık Yolu 9 | 7×9 | 1/1 | 12 ayna (10 döner), 7 duvar, 1 blok | 7 | 40960 | ✅ |
| 220 | Sarmaşık Yolu 10 | 7×9 | 2/2 | 15 ayna (14 döner), 7 duvar, 1 kapı | 9 | 16384 | ✅ |
| 221 | Gölge Çitler 1 | 7×10 | 2/2 | 13 ayna (9 döner), 10 duvar, 1 kapı | 6 | 512 | ✅ |
| 222 | Gölge Çitler 2 | 7×10 | 2/2 | 15 ayna (10 döner), 11 duvar | 7 | 1024 | ✅ |
| 223 | Gölge Çitler 3 | 7×10 | 1/1 | 11 ayna (8 döner), 12 duvar, 2 filtre | 5 | 256 | ✅ |
| 224 | Gölge Çitler 4 | 7×10 | 1/1 | 11 ayna (8 döner), 13 duvar, 1 blok | 6 | 9216 | ✅ |
| 225 | Gölge Çitler 5 | 7×10 | 2/2 | 16 ayna (11 döner), 14 duvar, 1 kapı | 7 | 2048 | ✅ |
| 226 | Gölge Çitler 6 | 8×10 | 2/2 | 18 ayna (10 döner), 15 duvar | 6 | 1024 | ✅ |
| 227 | Gölge Çitler 7 | 8×10 | 1/1 | 15 ayna (11 döner), 16 duvar, 2 filtre | 7 | 2048 | ✅ |
| 228 | Gölge Çitler 8 | 8×10 | 1/1 | 13 ayna (8 döner), 17 duvar, 1 blok | 7 | 10752 | ✅ |
| 229 | Gölge Çitler 9 | 8×10 | 2/2 | 19 ayna (14 döner), 18 duvar, 1 kapı | 9 | 16384 | ✅ |
| 230 | Gölge Çitler 10 | 8×10 | 2/2 | 20 ayna (14 döner), 19 duvar | 9 | 16384 | ✅ |
| 231 | Çiçek Renkleri 1 | 8×10 | 3/3 | 13 ayna (10 döner), 6 duvar, renkli ışık | 6 | 1024 | ✅ |
| 232 | Çiçek Renkleri 2 | 8×10 | 2/2 | 10 ayna (8 döner), 6 duvar, 1 filtre, renkli ışık | 5 | 256 | ✅ |
| 233 | Çiçek Renkleri 3 | 8×10 | 2/2 | 10 ayna (7 döner), 7 duvar, 1 blok, renkli ışık | 5 | 7168 | ✅ |
| 234 | Çiçek Renkleri 4 | 8×10 | 2/2 | 10 ayna (9 döner), 7 duvar, 1 kapı, renkli ışık | 6 | 512 | ✅ |
| 235 | Çiçek Renkleri 5 | 8×10 | 3/3 | 14 ayna (11 döner), 8 duvar, renkli ışık | 7 | 2048 | ✅ |
| 236 | Çiçek Renkleri 6 | 8×10 | 2/1 | 14 ayna (10 döner), 8 duvar, 1 filtre, renkli ışık | 6 | 1024 | ✅ |
| 237 | Çiçek Renkleri 7 | 8×10 | 2/1 | 13 ayna (10 döner), 9 duvar, 1 blok, renkli ışık | 7 | 56320 | ✅ |
| 238 | Çiçek Renkleri 8 | 8×10 | 3/2 | 15 ayna (11 döner), 9 duvar, 1 kapı, renkli ışık | 7 | 2048 | ✅ |
| 239 | Çiçek Renkleri 9 | 8×10 | 3/2 | 18 ayna (13 döner), 10 duvar, 1 kapı, renkli ışık | 8 | 8192 | ✅ |
| 240 | Çiçek Renkleri 10 | 8×10 | 3/2 | 17 ayna (13 döner), 10 duvar, 1 filtre, renkli ışık | 8 | 8192 | ✅ |
| 241 | Sisli Cam 1 | 8×10 | 1/1 | 10 ayna (9 döner), 6 duvar, 3 filtre | 5 | 512 | ✅ |
| 242 | Sisli Cam 2 | 8×10 | 1/1 | 8 ayna (7 döner), 6 duvar, 2 filtre, 1 blok | 6 | 7936 | ✅ |
| 243 | Sisli Cam 3 | 8×10 | 2/2 | 11 ayna (10 döner), 7 duvar, 2 filtre, 1 kapı | 7 | 1024 | ✅ |
| 244 | Sisli Cam 4 | 8×10 | 2/2 | 11 ayna (10 döner), 7 duvar, 2 filtre | 7 | 1024 | ✅ |
| 245 | Sisli Cam 5 | 8×10 | 2/2 | 10 ayna (9 döner), 8 duvar, 5 filtre | 6 | 512 | ✅ |
| 246 | Sisli Cam 6 | 8×10 | 2/2 | 12 ayna (9 döner), 8 duvar, 4 filtre, 1 blok | 6 | 16896 | ✅ |
| 247 | Sisli Cam 7 | 8×10 | 2/2 | 13 ayna (11 döner), 9 duvar, 4 filtre, 1 kapı | 7 | 2048 | ✅ |
| 248 | Sisli Cam 8 | 8×10 | 3/3 | 17 ayna (14 döner), 9 duvar, 4 filtre | 10 | 16384 | ✅ |
| 249 | Sisli Cam 9 | 8×10 | 2/2 | 13 ayna (12 döner), 10 duvar, 6 filtre | 8 | 4096 | ✅ |
| 250 | Sisli Cam 10 | 8×10 | 2/2 | 12 ayna (10 döner), 10 duvar, 5 filtre, 1 blok | 11 | 20480 | ✅ |
| 251 | Çiy Prizması 1 | 8×11 | 1/3 | 9 ayna (8 döner), 6 duvar, 1 prizma, 1 blok | 7 | 17408 | ✅ |
| 252 | Çiy Prizması 2 | 8×11 | 2/4 | 9 ayna (8 döner), 6 duvar, 1 prizma, 1 kapı | 6 | 256 | ✅ |
| 253 | Çiy Prizması 3 | 8×11 | 2/4 | 10 ayna (9 döner), 7 duvar, 1 prizma | 6 | 512 | ✅ |
| 254 | Çiy Prizması 4 | 8×11 | 1/2 | 9 ayna (8 döner), 7 duvar, 2 filtre, 1 prizma | 6 | 256 | ✅ |
| 255 | Çiy Prizması 5 | 8×11 | 1/3 | 9 ayna (8 döner), 8 duvar, 1 prizma, 1 blok | 6 | 14080 | ✅ |
| 256 | Çiy Prizması 6 | 8×11 | 2/4 | 16 ayna (14 döner), 8 duvar, 1 prizma, 1 kapı | 8 | 16384 | ✅ |
| 257 | Çiy Prizması 7 | 8×11 | 2/3 | 14 ayna (10 döner), 9 duvar, 1 filtre, 1 prizma | 7 | 1024 | ✅ |
| 258 | Çiy Prizması 8 | 8×11 | 1/3 | 12 ayna (10 döner), 9 duvar, 1 filtre, 1 prizma | 7 | 1024 | ✅ |
| 259 | Çiy Prizması 9 | 8×11 | 1/2 | 10 ayna (8 döner), 10 duvar, 1 filtre, 1 prizma, 1 blok | 7 | 14592 | ✅ |
| 260 | Çiy Prizması 10 | 8×11 | 2/4 | 14 ayna (13 döner), 10 duvar, 1 prizma, 1 kapı | 9 | 8192 | ✅ |
| 261 | Ateşböcekleri 1 | 8×11 | 2/2 | 12 ayna (9 döner), 6 duvar, 1 kapı | 6 | 512 | ✅ |
| 262 | Ateşböcekleri 2 | 8×11 | 3/3 | 14 ayna (12 döner), 6 duvar | 6 | 4096 | ✅ |
| 263 | Ateşböcekleri 3 | 8×11 | 2/2 | 12 ayna (9 döner), 7 duvar, 2 filtre | 6 | 512 | ✅ |
| 264 | Ateşböcekleri 4 | 8×11 | 2/2 | 12 ayna (10 döner), 7 duvar, 1 blok | 8 | 66560 | ✅ |
| 265 | Ateşböcekleri 5 | 8×11 | 3/3 | 14 ayna (10 döner), 8 duvar, 1 kapı | 7 | 1024 | ✅ |
| 266 | Ateşböcekleri 6 | 8×11 | 3/3 | 16 ayna (11 döner), 8 duvar, 1 kapı | 7 | 2048 | ✅ |
| 267 | Ateşböcekleri 7 | 8×11 | 3/5 | 17 ayna (15 döner), 9 duvar, 1 filtre, 1 prizma | 7 | 32768 | ✅ |
| 268 | Ateşböcekleri 8 | 8×11 | 3/5 | 15 ayna (11 döner), 9 duvar, 1 prizma, 1 blok | 8 | 16384 | ✅ |
| 269 | Ateşböcekleri 9 | 8×11 | 3/5 | 17 ayna (15 döner), 10 duvar, 1 prizma, 1 kapı, renkli ışık | 8 | 32768 | ✅ |
| 270 | Ateşböcekleri 10 | 8×11 | 3/5 | 18 ayna (16 döner), 10 duvar, 1 prizma, 1 kapı, renkli ışık | 11 | 65536 | ✅ |
| 271 | Bahçe Taşları 1 | 8×10 | 2/2 | 11 ayna (10 döner), 12 duvar, 1 blok | 7 | 22528 | ✅ |
| 272 | Bahçe Taşları 2 | 8×10 | 1/1 | 6 ayna (5 döner), 12 duvar, 2 filtre, 1 blok | 7 | 1792 | ✅ |
| 273 | Bahçe Taşları 3 | 8×10 | 1/1 | 7 ayna (5 döner), 13 duvar, 1 blok | 5 | 1856 | ✅ |
| 274 | Bahçe Taşları 4 | 8×10 | 2/2 | 11 ayna (9 döner), 13 duvar, 1 blok, 1 kapı | 8 | 15872 | ✅ |
| 275 | Bahçe Taşları 5 | 8×10 | 2/2 | 10 ayna (6 döner), 14 duvar, 2 blok | 6 | 33792 | ✅ |
| 276 | Bahçe Taşları 6 | 8×10 | 2/2 | 10 ayna (7 döner), 14 duvar, 2 filtre, 2 blok | 6 | 76160 | ✅ |
| 277 | Bahçe Taşları 7 | 8×10 | 2/2 | 12 ayna (8 döner), 15 duvar, 2 blok | 7 | 11520 | ✅ |
| 278 | Bahçe Taşları 8 | 8×10 | 2/2 | 11 ayna (8 döner), 15 duvar, 2 blok, 1 kapı | 7 | 73728 | ✅ |
| 279 | Bahçe Taşları 9 | 8×10 | 3/3 | 13 ayna (7 döner), 16 duvar, 2 blok | 7 | 38400 | ✅ |
| 280 | Bahçe Taşları 10 | 8×10 | 2/2 | 12 ayna (8 döner), 16 duvar, 2 filtre, 2 blok | 8 | 73728 | ✅ |
| 281 | Gizli Kapılar 1 | 8×10 | 2/2 | 12 ayna (10 döner), 7 duvar, 2 filtre, 1 kapı | 6 | 1024 | ✅ |
| 282 | Gizli Kapılar 2 | 8×10 | 2/2 | 12 ayna (7 döner), 7 duvar, 1 blok, 1 kapı | 5 | 3712 | ✅ |
| 283 | Gizli Kapılar 3 | 8×10 | 2/2 | 10 ayna (9 döner), 8 duvar, 1 kapı, renkli ışık | 6 | 512 | ✅ |
| 284 | Gizli Kapılar 4 | 8×10 | 3/3 | 14 ayna (10 döner), 8 duvar, 1 kapı, renkli ışık | 6 | 1024 | ✅ |
| 285 | Gizli Kapılar 5 | 8×10 | 2/2 | 11 ayna (9 döner), 9 duvar, 2 filtre, 1 kapı | 6 | 512 | ✅ |
| 286 | Gizli Kapılar 6 | 8×10 | 2/2 | 12 ayna (10 döner), 9 duvar, 1 blok, 1 kapı, renkli ışık | 7 | 46080 | ✅ |
| 287 | Gizli Kapılar 7 | 8×10 | 3/3 | 17 ayna (14 döner), 10 duvar, 2 kapı | 9 | 16384 | ✅ |
| 288 | Gizli Kapılar 8 | 8×10 | 3/3 | 15 ayna (12 döner), 10 duvar, 2 kapı, renkli ışık | 8 | 4096 | ✅ |
| 289 | Gizli Kapılar 9 | 8×10 | 3/3 | 15 ayna (14 döner), 11 duvar, 2 filtre, 2 kapı | 8 | 16384 | ✅ |
| 290 | Gizli Kapılar 10 | 8×10 | 3/3 | 17 ayna (11 döner), 11 duvar, 1 blok, 2 kapı, renkli ışık | 8 | 55296 | ✅ |
| 291 | Gece Yarısı 1 | 8×11 | 2/2 | 10 ayna (9 döner), 8 duvar, 1 filtre, 1 blok | 7 | 33280 | ✅ |
| 292 | Gece Yarısı 2 | 8×11 | 2/4 | 12 ayna (10 döner), 8 duvar, 1 prizma, 1 kapı | 7 | 1024 | ✅ |
| 293 | Gece Yarısı 3 | 8×11 | 3/2 | 16 ayna (10 döner), 9 duvar, 1 blok, renkli ışık | 9 | 52224 | ✅ |
| 294 | Gece Yarısı 4 | 8×11 | 2/2 | 12 ayna (11 döner), 9 duvar, 3 filtre, 1 kapı | 8 | 2048 | ✅ |
| 295 | Gece Yarısı 5 | 8×11 | 1/3 | 11 ayna (10 döner), 10 duvar, 1 filtre, 1 prizma, 1 blok | 8 | 59392 | ✅ |
| 296 | Gece Yarısı 6 | 8×11 | 3/2 | 14 ayna (12 döner), 10 duvar, 1 kapı, renkli ışık | 8 | 4096 | ✅ |
| 297 | Gece Yarısı 7 | 8×11 | 3/3 | 14 ayna (10 döner), 11 duvar, 1 filtre, 1 blok, 1 kapı | 9 | 29696 | ✅ |
| 298 | Gece Yarısı 8 | 8×11 | 2/3 | 13 ayna (12 döner), 11 duvar, 2 filtre, 1 prizma, 1 kapı | 9 | 4096 | ✅ |
| 299 | Gece Yarısı 9 | 8×11 | 3/2 | 15 ayna (10 döner), 12 duvar, 1 blok, 1 kapı, renkli ışık | 12 | 47104 | ✅ |
| 300 | Gece Yarısı 10 | 8×11 | 3/5 | 15 ayna (11 döner), 12 duvar, 1 prizma, 1 blok, 1 kapı | 10 | 36864 | ✅ |
| 301 | Yıldız Tozu 1 | 6×9 | 1/1 | 10 ayna (8 döner), 6 duvar, 2 filtre | 5 | 256 | ✅ |
| 302 | Yıldız Tozu 2 | 6×9 | 1/1 | 7 ayna (6 döner), 6 duvar, 1 blok | 5 | 2176 | ✅ |
| 303 | Yıldız Tozu 3 | 6×9 | 2/2 | 10 ayna (9 döner), 6 duvar, 1 kapı | 6 | 512 | ✅ |
| 304 | Yıldız Tozu 4 | 6×9 | 2/2 | 12 ayna (11 döner), 7 duvar | 8 | 2048 | ✅ |
| 305 | Yıldız Tozu 5 | 7×10 | 1/1 | 11 ayna (10 döner), 7 duvar, 2 filtre | 6 | 1024 | ✅ |
| 306 | Yıldız Tozu 6 | 7×10 | 1/1 | 8 ayna (6 döner), 7 duvar, 1 blok | 7 | 640 | ✅ |
| 307 | Yıldız Tozu 7 | 7×10 | 2/2 | 13 ayna (11 döner), 8 duvar, 1 kapı | 7 | 2048 | ✅ |
| 308 | Yıldız Tozu 8 | 7×10 | 2/2 | 15 ayna (12 döner), 8 duvar | 7 | 4096 | ✅ |
| 309 | Yıldız Tozu 9 | 7×10 | 1/1 | 14 ayna (12 döner), 8 duvar, 2 filtre | 7 | 4096 | ✅ |
| 310 | Yıldız Tozu 10 | 7×10 | 1/1 | 11 ayna (10 döner), 9 duvar, 1 blok | 7 | 19456 | ✅ |
| 311 | Kuyruklu Yıldız 1 | 7×10 | 1/1 | 11 ayna (10 döner), 7 duvar, 1 blok | 8 | 49152 | ✅ |
| 312 | Kuyruklu Yıldız 2 | 7×10 | 2/2 | 14 ayna (11 döner), 7 duvar, 1 kapı | 7 | 2048 | ✅ |
| 313 | Kuyruklu Yıldız 3 | 7×10 | 2/2 | 15 ayna (13 döner), 7 duvar | 9 | 8192 | ✅ |
| 314 | Kuyruklu Yıldız 4 | 7×10 | 1/1 | 13 ayna (12 döner), 7 duvar, 2 filtre | 8 | 4096 | ✅ |
| 315 | Kuyruklu Yıldız 5 | 7×10 | 1/1 | 12 ayna (10 döner), 8 duvar, 1 blok | 8 | 27648 | ✅ |
| 316 | Kuyruklu Yıldız 6 | 7×10 | 2/2 | 16 ayna (13 döner), 8 duvar, 1 kapı | 8 | 8192 | ✅ |
| 317 | Kuyruklu Yıldız 7 | 7×10 | 2/2 | 15 ayna (14 döner), 8 duvar | 9 | 16384 | ✅ |
| 318 | Kuyruklu Yıldız 8 | 7×10 | 1/1 | 16 ayna (14 döner), 8 duvar, 2 filtre | 9 | 16384 | ✅ |
| 319 | Kuyruklu Yıldız 9 | 7×10 | 1/1 | 12 ayna (10 döner), 9 duvar, 1 blok | 12 | 40960 | ✅ |
| 320 | Kuyruklu Yıldız 10 | 7×10 | 2/2 | 19 ayna (16 döner), 9 duvar, 1 kapı | 10 | 65536 | ✅ |
| 321 | Kara Delik 1 | 7×11 | 2/2 | 18 ayna (11 döner), 12 duvar, 1 kapı | 7 | 2048 | ✅ |
| 322 | Kara Delik 2 | 7×11 | 2/2 | 16 ayna (12 döner), 13 duvar | 8 | 4096 | ✅ |
| 323 | Kara Delik 3 | 7×11 | 1/1 | 14 ayna (11 döner), 14 duvar, 2 filtre | 7 | 2048 | ✅ |
| 324 | Kara Delik 4 | 7×11 | 1/1 | 12 ayna (5 döner), 15 duvar, 1 blok | 8 | 1408 | ✅ |
| 325 | Kara Delik 5 | 7×11 | 2/2 | 18 ayna (13 döner), 16 duvar, 1 kapı | 8 | 8192 | ✅ |
| 326 | Kara Delik 6 | 8×11 | 2/2 | 19 ayna (13 döner), 17 duvar | 8 | 8192 | ✅ |
| 327 | Kara Delik 7 | 8×11 | 1/1 | 18 ayna (15 döner), 18 duvar, 2 filtre | 10 | 32768 | ✅ |
| 328 | Kara Delik 8 | 8×11 | 1/1 | 14 ayna (10 döner), 19 duvar, 1 blok | 9 | 47104 | ✅ |
| 329 | Kara Delik 9 | 8×11 | 2/2 | 21 ayna (15 döner), 20 duvar, 1 kapı | 9 | 32768 | ✅ |
| 330 | Kara Delik 10 | 8×11 | 2/2 | 20 ayna (16 döner), 21 duvar | 10 | 65536 | ✅ |
| 331 | Nebula 1 | 8×11 | 3/3 | 16 ayna (15 döner), 8 duvar, renkli ışık | 10 | 32768 | ✅ |
| 332 | Nebula 2 | 8×11 | 2/2 | 12 ayna (11 döner), 8 duvar, 1 filtre, renkli ışık | 7 | 2048 | ✅ |
| 333 | Nebula 3 | 8×11 | 2/2 | 12 ayna (10 döner), 9 duvar, 1 blok, renkli ışık | 8 | 60416 | ✅ |
| 334 | Nebula 4 | 8×11 | 2/2 | 13 ayna (12 döner), 9 duvar, 1 kapı, renkli ışık | 8 | 4096 | ✅ |
| 335 | Nebula 5 | 8×11 | 3/3 | 16 ayna (14 döner), 10 duvar, renkli ışık | 8 | 16384 | ✅ |
| 336 | Nebula 6 | 8×11 | 2/1 | 15 ayna (14 döner), 10 duvar, 1 filtre, renkli ışık | 8 | 16384 | ✅ |
| 337 | Nebula 7 | 8×11 | 2/1 | 14 ayna (11 döner), 11 duvar, 1 blok, renkli ışık | 9 | 51200 | ✅ |
| 338 | Nebula 8 | 8×11 | 3/2 | 20 ayna (15 döner), 11 duvar, 1 kapı, renkli ışık | 10 | 32768 | ✅ |
| 339 | Nebula 9 | 8×11 | 3/2 | 20 ayna (16 döner), 12 duvar, 1 kapı, renkli ışık | 10 | 65536 | ✅ |
| 340 | Nebula 10 | 8×11 | 3/2 | 18 ayna (16 döner), 12 duvar, 1 filtre, renkli ışık | 10 | 65536 | ✅ |
| 341 | Yıldız Camı 1 | 8×11 | 1/1 | 13 ayna (12 döner), 8 duvar, 3 filtre | 8 | 4096 | ✅ |
| 342 | Yıldız Camı 2 | 8×11 | 1/1 | 10 ayna (9 döner), 8 duvar, 2 filtre, 1 blok | 7 | 33792 | ✅ |
| 343 | Yıldız Camı 3 | 8×11 | 2/2 | 14 ayna (12 döner), 9 duvar, 2 filtre, 1 kapı | 7 | 4096 | ✅ |
| 344 | Yıldız Camı 4 | 8×11 | 2/2 | 13 ayna (12 döner), 9 duvar, 2 filtre | 8 | 4096 | ✅ |
| 345 | Yıldız Camı 5 | 8×11 | 2/2 | 14 ayna (13 döner), 10 duvar, 5 filtre | 9 | 8192 | ✅ |
| 346 | Yıldız Camı 6 | 8×11 | 2/2 | 16 ayna (10 döner), 10 duvar, 4 filtre, 1 blok | 9 | 29696 | ✅ |
| 347 | Yıldız Camı 7 | 8×11 | 2/2 | 16 ayna (14 döner), 11 duvar, 4 filtre, 1 kapı | 9 | 16384 | ✅ |
| 348 | Yıldız Camı 8 | 8×11 | 3/3 | 19 ayna (15 döner), 11 duvar, 4 filtre | 10 | 32768 | ✅ |
| 349 | Yıldız Camı 9 | 8×11 | 2/2 | 16 ayna (14 döner), 12 duvar, 6 filtre | 9 | 16384 | ✅ |
| 350 | Yıldız Camı 10 | 8×11 | 2/2 | 12 ayna (11 döner), 12 duvar, 5 filtre, 1 blok | 10 | 51200 | ✅ |
| 351 | Takımyıldızı 1 | 8×11 | 1/3 | 10 ayna (9 döner), 8 duvar, 1 prizma, 1 blok | 7 | 30208 | ✅ |
| 352 | Takımyıldızı 2 | 8×11 | 2/4 | 13 ayna (11 döner), 8 duvar, 1 prizma, 1 kapı | 8 | 2048 | ✅ |
| 353 | Takımyıldızı 3 | 8×11 | 2/4 | 13 ayna (11 döner), 9 duvar, 1 prizma | 8 | 2048 | ✅ |
| 354 | Takımyıldızı 4 | 8×11 | 1/2 | 12 ayna (11 döner), 9 duvar, 2 filtre, 1 prizma | 8 | 2048 | ✅ |
| 355 | Takımyıldızı 5 | 8×11 | 1/3 | 12 ayna (9 döner), 10 duvar, 1 prizma, 1 blok | 11 | 28672 | ✅ |
| 356 | Takımyıldızı 6 | 8×11 | 2/4 | 14 ayna (12 döner), 10 duvar, 1 prizma, 1 kapı | 8 | 4096 | ✅ |
| 357 | Takımyıldızı 7 | 8×11 | 2/3 | 15 ayna (13 döner), 11 duvar, 1 filtre, 1 prizma | 9 | 8192 | ✅ |
| 358 | Takımyıldızı 8 | 8×11 | 1/3 | 14 ayna (13 döner), 11 duvar, 1 filtre, 1 prizma | 9 | 8192 | ✅ |
| 359 | Takımyıldızı 9 | 8×11 | 1/2 | 11 ayna (9 döner), 12 duvar, 1 filtre, 1 prizma, 1 blok | 9 | 30720 | ✅ |
| 360 | Takımyıldızı 10 | 8×11 | 2/4 | 17 ayna (15 döner), 12 duvar, 1 prizma, 1 kapı | 10 | 32768 | ✅ |
| 361 | Çifte Güneş 1 | 8×11 | 2/2 | 14 ayna (12 döner), 8 duvar, 1 kapı | 8 | 4096 | ✅ |
| 362 | Çifte Güneş 2 | 8×11 | 3/3 | 18 ayna (14 döner), 8 duvar | 10 | 16384 | ✅ |
| 363 | Çifte Güneş 3 | 8×11 | 2/2 | 14 ayna (12 döner), 9 duvar, 2 filtre | 8 | 4096 | ✅ |
| 364 | Çifte Güneş 4 | 8×11 | 2/2 | 13 ayna (9 döner), 9 duvar, 1 blok | 10 | 30720 | ✅ |
| 365 | Çifte Güneş 5 | 8×11 | 3/3 | 14 ayna (13 döner), 10 duvar, 1 kapı | 9 | 8192 | ✅ |
| 366 | Çifte Güneş 6 | 8×11 | 3/3 | 19 ayna (16 döner), 10 duvar, 1 kapı | 11 | 65536 | ✅ |
| 367 | Çifte Güneş 7 | 8×11 | 3/5 | 18 ayna (14 döner), 11 duvar, 1 filtre, 1 prizma | 9 | 16384 | ✅ |
| 368 | Çifte Güneş 8 | 8×11 | 3/5 | 17 ayna (11 döner), 11 duvar, 1 prizma, 1 blok | 10 | 57344 | ✅ |
| 369 | Çifte Güneş 9 | 8×11 | 3/5 | 18 ayna (15 döner), 12 duvar, 1 prizma, 1 kapı, renkli ışık | 10 | 32768 | ✅ |
| 370 | Çifte Güneş 10 | 8×11 | 3/5 | 18 ayna (15 döner), 12 duvar, 1 prizma, 1 kapı, renkli ışık | 10 | 32768 | ✅ |
| 371 | Gezegen Taşları 1 | 8×11 | 2/2 | 13 ayna (8 döner), 14 duvar, 1 blok | 7 | 14080 | ✅ |
| 372 | Gezegen Taşları 2 | 8×11 | 1/1 | 9 ayna (7 döner), 14 duvar, 2 filtre, 1 blok | 8 | 7552 | ✅ |
| 373 | Gezegen Taşları 3 | 8×11 | 1/1 | 8 ayna (7 döner), 15 duvar, 1 blok | 8 | 7296 | ✅ |
| 374 | Gezegen Taşları 4 | 8×11 | 2/2 | 12 ayna (10 döner), 15 duvar, 1 blok, 1 kapı | 8 | 7168 | ✅ |
| 375 | Gezegen Taşları 5 | 8×11 | 2/2 | 11 ayna (8 döner), 16 duvar, 2 blok | 9 | 72192 | ✅ |
| 376 | Gezegen Taşları 6 | 8×11 | 2/2 | 12 ayna (8 döner), 16 duvar, 2 filtre, 2 blok | 9 | 57344 | ✅ |
| 377 | Gezegen Taşları 7 | 8×11 | 2/2 | 14 ayna (8 döner), 17 duvar, 2 blok | 10 | 61440 | ✅ |
| 378 | Gezegen Taşları 8 | 8×11 | 2/2 | 12 ayna (8 döner), 17 duvar, 2 blok, 1 kapı | 11 | 34560 | ✅ |
| 379 | Gezegen Taşları 9 | 8×11 | 3/3 | 16 ayna (8 döner), 18 duvar, 2 blok | 9 | 51200 | ✅ |
| 380 | Gezegen Taşları 10 | 8×11 | 2/2 | 12 ayna (8 döner), 18 duvar, 2 filtre, 2 blok | 10 | 76800 | ✅ |
| 381 | Kozmik Kilit 1 | 8×11 | 2/2 | 15 ayna (11 döner), 9 duvar, 2 filtre, 1 kapı | 7 | 2048 | ✅ |
| 382 | Kozmik Kilit 2 | 8×11 | 2/2 | 12 ayna (10 döner), 9 duvar, 1 blok, 1 kapı | 7 | 61440 | ✅ |
| 383 | Kozmik Kilit 3 | 8×11 | 2/2 | 15 ayna (12 döner), 10 duvar, 1 kapı, renkli ışık | 8 | 4096 | ✅ |
| 384 | Kozmik Kilit 4 | 8×11 | 3/3 | 19 ayna (15 döner), 10 duvar, 1 kapı, renkli ışık | 10 | 32768 | ✅ |
| 385 | Kozmik Kilit 5 | 8×11 | 2/2 | 14 ayna (13 döner), 11 duvar, 2 filtre, 1 kapı | 9 | 8192 | ✅ |
| 386 | Kozmik Kilit 6 | 8×11 | 2/2 | 14 ayna (11 döner), 11 duvar, 1 blok, 1 kapı, renkli ışık | 8 | 24576 | ✅ |
| 387 | Kozmik Kilit 7 | 8×11 | 3/3 | 18 ayna (14 döner), 12 duvar, 2 kapı | 9 | 16384 | ✅ |
| 388 | Kozmik Kilit 8 | 8×11 | 3/3 | 17 ayna (14 döner), 12 duvar, 2 kapı, renkli ışık | 9 | 16384 | ✅ |
| 389 | Kozmik Kilit 9 | 8×11 | 3/3 | 20 ayna (15 döner), 13 duvar, 2 filtre, 2 kapı | 10 | 32768 | ✅ |
| 390 | Kozmik Kilit 10 | 8×11 | 3/3 | 16 ayna (10 döner), 13 duvar, 1 blok, 2 kapı, renkli ışık | 12 | 27648 | ✅ |
| 391 | Süpernova 1 | 8×11 | 2/2 | 14 ayna (10 döner), 10 duvar, 1 filtre, 1 blok | 9 | 59392 | ✅ |
| 392 | Süpernova 2 | 8×11 | 2/4 | 15 ayna (14 döner), 10 duvar, 1 prizma, 1 kapı | 9 | 16384 | ✅ |
| 393 | Süpernova 3 | 8×11 | 3/2 | 17 ayna (11 döner), 11 duvar, 1 blok, renkli ışık | 9 | 28672 | ✅ |
| 394 | Süpernova 4 | 8×11 | 2/2 | 15 ayna (14 döner), 11 duvar, 3 filtre, 1 kapı | 10 | 16384 | ✅ |
| 395 | Süpernova 5 | 8×11 | 1/3 | 10 ayna (8 döner), 12 duvar, 1 filtre, 1 prizma, 1 blok | 10 | 15104 | ✅ |
| 396 | Süpernova 6 | 8×11 | 3/2 | 18 ayna (16 döner), 12 duvar, 1 kapı, renkli ışık | 12 | 65536 | ✅ |
| 397 | Süpernova 7 | 8×11 | 3/3 | 18 ayna (11 döner), 13 duvar, 1 filtre, 1 blok, 1 kapı | 14 | 45056 | ✅ |
| 398 | Süpernova 8 | 8×11 | 2/3 | 16 ayna (15 döner), 13 duvar, 2 filtre, 1 prizma, 1 kapı | 11 | 32768 | ✅ |
| 399 | Süpernova 9 | 8×11 | 3/2 | 16 ayna (11 döner), 14 duvar, 1 blok, 1 kapı, renkli ışık | 11 | 71680 | ✅ |
| 400 | Süpernova 10 | 8×11 | 3/5 | 18 ayna (11 döner), 14 duvar, 1 prizma, 1 blok, 1 kapı | 15 | 63488 | ✅ |
| 401 | Tapınak Kapısı 1 | 6×9 | 1/1 | 12 ayna (10 döner), 8 duvar, 2 filtre | 6 | 1024 | ✅ |
| 402 | Tapınak Kapısı 2 | 6×9 | 1/1 | 8 ayna (6 döner), 8 duvar, 1 blok | 6 | 2176 | ✅ |
| 403 | Tapınak Kapısı 3 | 6×9 | 2/2 | 14 ayna (12 döner), 8 duvar, 1 kapı | 7 | 4096 | ✅ |
| 404 | Tapınak Kapısı 4 | 6×9 | 2/2 | 14 ayna (11 döner), 9 duvar | 7 | 2048 | ✅ |
| 405 | Tapınak Kapısı 5 | 7×10 | 1/1 | 15 ayna (12 döner), 9 duvar, 2 filtre | 7 | 4096 | ✅ |
| 406 | Tapınak Kapısı 6 | 7×10 | 1/1 | 10 ayna (7 döner), 9 duvar, 1 blok | 7 | 5376 | ✅ |
| 407 | Tapınak Kapısı 7 | 7×10 | 2/2 | 16 ayna (13 döner), 10 duvar, 1 kapı | 8 | 8192 | ✅ |
| 408 | Tapınak Kapısı 8 | 7×10 | 2/2 | 17 ayna (14 döner), 10 duvar | 8 | 16384 | ✅ |
| 409 | Tapınak Kapısı 9 | 7×10 | 1/1 | 16 ayna (14 döner), 10 duvar, 2 filtre | 8 | 16384 | ✅ |
| 410 | Tapınak Kapısı 10 | 7×10 | 1/1 | 12 ayna (10 döner), 11 duvar, 1 blok | 9 | 40960 | ✅ |
| 411 | Sonsuz Aynalar 1 | 7×10 | 1/1 | 11 ayna (8 döner), 9 duvar, 1 blok | 10 | 12288 | ✅ |
| 412 | Sonsuz Aynalar 2 | 7×10 | 2/2 | 16 ayna (13 döner), 9 duvar, 1 kapı | 8 | 8192 | ✅ |
| 413 | Sonsuz Aynalar 3 | 7×10 | 2/2 | 17 ayna (13 döner), 9 duvar | 8 | 8192 | ✅ |
| 414 | Sonsuz Aynalar 4 | 7×10 | 1/1 | 17 ayna (14 döner), 9 duvar, 2 filtre | 9 | 16384 | ✅ |
| 415 | Sonsuz Aynalar 5 | 7×10 | 1/1 | 13 ayna (11 döner), 10 duvar, 1 blok | 11 | 24576 | ✅ |
| 416 | Sonsuz Aynalar 6 | 7×10 | 2/2 | 18 ayna (16 döner), 10 duvar, 1 kapı | 10 | 65536 | ✅ |
| 417 | Sonsuz Aynalar 7 | 7×10 | 2/2 | 19 ayna (16 döner), 10 duvar | 10 | 65536 | ✅ |
| 418 | Sonsuz Aynalar 8 | 7×10 | 1/1 | 19 ayna (16 döner), 10 duvar, 2 filtre | 10 | 65536 | ✅ |
| 419 | Sonsuz Aynalar 9 | 7×10 | 1/1 | 15 ayna (11 döner), 11 duvar, 1 blok | 10 | 51200 | ✅ |
| 420 | Sonsuz Aynalar 10 | 7×10 | 2/2 | 19 ayna (16 döner), 11 duvar, 1 kapı | 9 | 65536 | ✅ |
| 421 | Kadim Duvarlar 1 | 7×11 | 2/2 | 18 ayna (13 döner), 14 duvar, 1 kapı | 8 | 8192 | ✅ |
| 422 | Kadim Duvarlar 2 | 7×11 | 2/2 | 19 ayna (15 döner), 15 duvar | 10 | 32768 | ✅ |
| 423 | Kadim Duvarlar 3 | 7×11 | 1/1 | 17 ayna (13 döner), 16 duvar, 2 filtre | 8 | 8192 | ✅ |
| 424 | Kadim Duvarlar 4 | 7×11 | 1/1 | 12 ayna (8 döner), 17 duvar, 1 blok | 9 | 9728 | ✅ |
| 425 | Kadim Duvarlar 5 | 7×11 | 2/2 | 20 ayna (16 döner), 18 duvar, 1 kapı | 10 | 65536 | ✅ |
| 426 | Kadim Duvarlar 6 | 8×11 | 2/2 | 20 ayna (15 döner), 19 duvar | 9 | 32768 | ✅ |
| 427 | Kadim Duvarlar 7 | 8×11 | 1/1 | 21 ayna (16 döner), 20 duvar, 2 filtre | 10 | 65536 | ✅ |
| 428 | Kadim Duvarlar 8 | 8×11 | 1/1 | 16 ayna (11 döner), 21 duvar, 1 blok | 10 | 30720 | ✅ |
| 429 | Kadim Duvarlar 9 | 8×11 | 2/2 | 23 ayna (16 döner), 22 duvar, 1 kapı | 9 | 65536 | ✅ |
| 430 | Kadim Duvarlar 10 | 8×11 | 2/2 | 20 ayna (16 döner), 23 duvar | 9 | 65536 | ✅ |
| 431 | Altın Renkler 1 | 8×11 | 3/3 | 18 ayna (13 döner), 10 duvar, renkli ışık | 8 | 8192 | ✅ |
| 432 | Altın Renkler 2 | 8×11 | 2/2 | 15 ayna (13 döner), 10 duvar, 1 filtre, renkli ışık | 8 | 8192 | ✅ |
| 433 | Altın Renkler 3 | 8×11 | 2/2 | 15 ayna (11 döner), 11 duvar, 1 blok, renkli ışık | 10 | 18432 | ✅ |
| 434 | Altın Renkler 4 | 8×11 | 2/2 | 15 ayna (13 döner), 11 duvar, 1 kapı, renkli ışık | 8 | 8192 | ✅ |
| 435 | Altın Renkler 5 | 8×11 | 3/3 | 20 ayna (15 döner), 12 duvar, renkli ışık | 9 | 32768 | ✅ |
| 436 | Altın Renkler 6 | 8×11 | 2/1 | 18 ayna (15 döner), 12 duvar, 1 filtre, renkli ışık | 9 | 32768 | ✅ |
| 437 | Altın Renkler 7 | 8×11 | 2/1 | 17 ayna (10 döner), 13 duvar, 1 blok, renkli ışık | 10 | 26624 | ✅ |
| 438 | Altın Renkler 8 | 8×11 | 3/2 | 20 ayna (16 döner), 13 duvar, 1 kapı, renkli ışık | 10 | 65536 | ✅ |
| 439 | Altın Renkler 9 | 8×11 | 3/2 | 23 ayna (16 döner), 14 duvar, 1 kapı, renkli ışık | 9 | 65536 | ✅ |
| 440 | Altın Renkler 10 | 8×11 | 3/2 | 20 ayna (16 döner), 14 duvar, 1 filtre, renkli ışık | 9 | 65536 | ✅ |
| 441 | Mabet Camları 1 | 8×11 | 1/1 | 16 ayna (13 döner), 10 duvar, 3 filtre | 8 | 8192 | ✅ |
| 442 | Mabet Camları 2 | 8×11 | 1/1 | 11 ayna (9 döner), 10 duvar, 2 filtre, 1 blok | 11 | 30208 | ✅ |
| 443 | Mabet Camları 3 | 8×11 | 2/2 | 17 ayna (13 döner), 11 duvar, 2 filtre, 1 kapı | 8 | 8192 | ✅ |
| 444 | Mabet Camları 4 | 8×11 | 2/2 | 17 ayna (15 döner), 11 duvar, 2 filtre | 10 | 32768 | ✅ |
| 445 | Mabet Camları 5 | 8×11 | 2/2 | 17 ayna (14 döner), 12 duvar, 5 filtre | 9 | 16384 | ✅ |
| 446 | Mabet Camları 6 | 8×11 | 2/2 | 16 ayna (11 döner), 12 duvar, 4 filtre, 1 blok | 10 | 32768 | ✅ |
| 447 | Mabet Camları 7 | 8×11 | 2/2 | 18 ayna (16 döner), 13 duvar, 4 filtre, 1 kapı | 10 | 65536 | ✅ |
| 448 | Mabet Camları 8 | 8×11 | 3/3 | 20 ayna (16 döner), 13 duvar, 4 filtre | 10 | 65536 | ✅ |
| 449 | Mabet Camları 9 | 8×11 | 2/2 | 18 ayna (16 döner), 14 duvar, 6 filtre | 10 | 65536 | ✅ |
| 450 | Mabet Camları 10 | 8×11 | 2/2 | 15 ayna (11 döner), 14 duvar, 5 filtre, 1 blok | 13 | 45056 | ✅ |
| 451 | Işık Sunağı 1 | 8×11 | 1/3 | 11 ayna (9 döner), 10 duvar, 1 prizma, 1 blok | 8 | 2560 | ✅ |
| 452 | Işık Sunağı 2 | 8×11 | 2/4 | 14 ayna (12 döner), 10 duvar, 1 prizma, 1 kapı | 8 | 4096 | ✅ |
| 453 | Işık Sunağı 3 | 8×11 | 2/4 | 14 ayna (12 döner), 11 duvar, 1 prizma | 8 | 4096 | ✅ |
| 454 | Işık Sunağı 4 | 8×11 | 1/2 | 14 ayna (12 döner), 11 duvar, 2 filtre, 1 prizma | 8 | 4096 | ✅ |
| 455 | Işık Sunağı 5 | 8×11 | 1/3 | 15 ayna (11 döner), 12 duvar, 1 prizma, 1 blok | 9 | 32768 | ✅ |
| 456 | Işık Sunağı 6 | 8×11 | 2/4 | 16 ayna (14 döner), 12 duvar, 1 prizma, 1 kapı | 9 | 16384 | ✅ |
| 457 | Işık Sunağı 7 | 8×11 | 2/3 | 17 ayna (15 döner), 13 duvar, 1 filtre, 1 prizma | 10 | 32768 | ✅ |
| 458 | Işık Sunağı 8 | 8×11 | 1/3 | 17 ayna (15 döner), 13 duvar, 1 filtre, 1 prizma | 10 | 32768 | ✅ |
| 459 | Işık Sunağı 9 | 8×11 | 1/2 | 15 ayna (10 döner), 14 duvar, 1 filtre, 1 prizma, 1 blok | 12 | 18432 | ✅ |
| 460 | Işık Sunağı 10 | 8×11 | 2/4 | 20 ayna (16 döner), 14 duvar, 1 prizma, 1 kapı | 10 | 65536 | ✅ |
| 461 | Yedi Hedef 1 | 8×11 | 2/2 | 17 ayna (15 döner), 10 duvar, 1 kapı | 10 | 32768 | ✅ |
| 462 | Yedi Hedef 2 | 8×11 | 3/3 | 20 ayna (15 döner), 10 duvar | 10 | 32768 | ✅ |
| 463 | Yedi Hedef 3 | 8×11 | 2/2 | 16 ayna (14 döner), 11 duvar, 2 filtre | 9 | 16384 | ✅ |
| 464 | Yedi Hedef 4 | 8×11 | 2/2 | 15 ayna (11 döner), 11 duvar, 1 blok | 12 | 20480 | ✅ |
| 465 | Yedi Hedef 5 | 8×11 | 3/3 | 20 ayna (15 döner), 12 duvar, 1 kapı | 10 | 32768 | ✅ |
| 466 | Yedi Hedef 6 | 8×11 | 3/3 | 20 ayna (16 döner), 12 duvar, 1 kapı | 10 | 65536 | ✅ |
| 467 | Yedi Hedef 7 | 8×11 | 3/5 | 21 ayna (16 döner), 13 duvar, 1 filtre, 1 prizma | 10 | 65536 | ✅ |
| 468 | Yedi Hedef 8 | 8×11 | 3/5 | 20 ayna (11 döner), 13 duvar, 1 prizma, 1 blok | 12 | 65536 | ✅ |
| 469 | Yedi Hedef 9 | 8×11 | 3/5 | 21 ayna (16 döner), 14 duvar, 1 prizma, 1 kapı, renkli ışık | 10 | 65536 | ✅ |
| 470 | Yedi Hedef 10 | 8×11 | 3/5 | 20 ayna (16 döner), 14 duvar, 1 prizma, 1 kapı, renkli ışık | 10 | 65536 | ✅ |
| 471 | Kayan Sütunlar 1 | 8×11 | 2/2 | 12 ayna (10 döner), 16 duvar, 1 blok | 8 | 47104 | ✅ |
| 472 | Kayan Sütunlar 2 | 8×11 | 1/1 | 11 ayna (8 döner), 16 duvar, 2 filtre, 1 blok | 8 | 13056 | ✅ |
| 473 | Kayan Sütunlar 3 | 8×11 | 1/1 | 11 ayna (9 döner), 17 duvar, 1 blok | 8 | 24576 | ✅ |
| 474 | Kayan Sütunlar 4 | 8×11 | 2/2 | 13 ayna (10 döner), 17 duvar, 1 blok, 1 kapı | 11 | 16384 | ✅ |
| 475 | Kayan Sütunlar 5 | 8×11 | 2/2 | 12 ayna (8 döner), 18 duvar, 2 blok | 9 | 61952 | ✅ |
| 476 | Kayan Sütunlar 6 | 8×11 | 2/2 | 14 ayna (7 döner), 18 duvar, 2 filtre, 2 blok | 12 | 76160 | ✅ |
| 477 | Kayan Sütunlar 7 | 8×11 | 2/2 | 14 ayna (7 döner), 19 duvar, 2 blok | 13 | 11648 | ✅ |
| 478 | Kayan Sütunlar 8 | 8×11 | 2/2 | 14 ayna (8 döner), 19 duvar, 2 blok, 1 kapı | 10 | 71680 | ✅ |
| 479 | Kayan Sütunlar 9 | 8×11 | 3/3 | 16 ayna (8 döner), 20 duvar, 2 blok | 11 | 53760 | ✅ |
| 480 | Kayan Sütunlar 10 | 8×11 | 2/2 | 15 ayna (8 döner), 20 duvar, 2 filtre, 2 blok | 11 | 53760 | ✅ |
| 481 | Son Mühür 1 | 8×11 | 2/2 | 16 ayna (13 döner), 11 duvar, 2 filtre, 1 kapı | 8 | 8192 | ✅ |
| 482 | Son Mühür 2 | 8×11 | 2/2 | 14 ayna (11 döner), 11 duvar, 1 blok, 1 kapı | 10 | 43008 | ✅ |
| 483 | Son Mühür 3 | 8×11 | 2/2 | 16 ayna (13 döner), 12 duvar, 1 kapı, renkli ışık | 8 | 8192 | ✅ |
| 484 | Son Mühür 4 | 8×11 | 3/3 | 20 ayna (16 döner), 12 duvar, 1 kapı, renkli ışık | 11 | 65536 | ✅ |
| 485 | Son Mühür 5 | 8×11 | 2/2 | 17 ayna (14 döner), 13 duvar, 2 filtre, 1 kapı | 9 | 16384 | ✅ |
| 486 | Son Mühür 6 | 8×11 | 2/2 | 14 ayna (11 döner), 13 duvar, 1 blok, 1 kapı, renkli ışık | 9 | 32768 | ✅ |
| 487 | Son Mühür 7 | 8×11 | 3/3 | 19 ayna (16 döner), 14 duvar, 2 kapı | 10 | 65536 | ✅ |
| 488 | Son Mühür 8 | 8×11 | 3/3 | 19 ayna (16 döner), 14 duvar, 2 kapı, renkli ışık | 10 | 65536 | ✅ |
| 489 | Son Mühür 9 | 8×11 | 3/3 | 21 ayna (16 döner), 15 duvar, 2 filtre, 2 kapı | 10 | 65536 | ✅ |
| 490 | Son Mühür 10 | 8×11 | 3/3 | 19 ayna (11 döner), 15 duvar, 1 blok, 2 kapı, renkli ışık | 11 | 79872 | ✅ |
| 491 | Işığın Kalbi 1 | 8×11 | 2/2 | 14 ayna (11 döner), 12 duvar, 1 filtre, 1 blok | 12 | 36864 | ✅ |
| 492 | Işığın Kalbi 2 | 8×11 | 2/4 | 17 ayna (15 döner), 12 duvar, 1 prizma, 1 kapı | 10 | 32768 | ✅ |
| 493 | Işığın Kalbi 3 | 8×11 | 3/2 | 19 ayna (11 döner), 13 duvar, 1 blok, renkli ışık | 10 | 12288 | ✅ |
| 494 | Işığın Kalbi 4 | 8×11 | 2/2 | 17 ayna (15 döner), 13 duvar, 3 filtre, 1 kapı | 10 | 32768 | ✅ |
| 495 | Işığın Kalbi 5 | 8×11 | 1/3 | 15 ayna (11 döner), 14 duvar, 1 filtre, 1 prizma, 1 blok | 11 | 34816 | ✅ |
| 496 | Işığın Kalbi 6 | 8×11 | 3/2 | 22 ayna (16 döner), 14 duvar, 1 kapı, renkli ışık | 11 | 65536 | ✅ |
| 497 | Işığın Kalbi 7 | 8×11 | 3/3 | 17 ayna (11 döner), 15 duvar, 1 filtre, 1 blok, 1 kapı | 12 | 36864 | ✅ |
| 498 | Işığın Kalbi 8 | 8×11 | 2/3 | 19 ayna (16 döner), 15 duvar, 2 filtre, 1 prizma, 1 kapı | 11 | 65536 | ✅ |
| 499 | Işığın Kalbi 9 | 8×11 | 3/2 | 20 ayna (11 döner), 16 duvar, 1 blok, 1 kapı, renkli ışık | 15 | 73728 | ✅ |
| 500 | Işığın Kalbi 10 | 8×11 | 3/5 | 19 ayna (11 döner), 16 duvar, 1 prizma, 1 blok, 1 kapı | 13 | 63488 | ✅ |
