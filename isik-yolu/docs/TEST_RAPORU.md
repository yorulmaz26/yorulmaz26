# Test Raporu — Işık Yolu 1.0.0

Tarih: 18 Kasım 2026 · Komut: `./gradlew :engine:test :app:testDebugUnitTest`

**Sonuç: 41 testin 41'i geçti, 0 hata.**

| Test sınıfı | Test | Sonuç | Süre |
|---|---|---|---|
| `BeamReflectionTest` | 6 | ✅ | 0,05 sn |
| `ColorFilterTest` | 5 | ✅ | <0,01 sn |
| `PrismTest` | 3 | ✅ | <0,01 sn |
| `DoorKeyTest` | 5 | ✅ | <0,01 sn |
| `GameSessionTest` | 5 | ✅ | 0,11 sn |
| `LevelPackTest` | 3 | ✅ | 0,03 sn |
| `AllLevelsSolvableTest` | 2 | ✅ | 0,32 sn |
| `StarRatingTest` | 2 | ✅ | <0,01 sn |
| `ProgressTest` | 7 | ✅ | <0,01 sn |
| `UiFlowTest` (Robolectric, gerçek çizim) | 3 | ✅ | 61 sn |

## İstenen 10 kontrol

| # | Kontrol | Nasıl doğrulandı | Sonuç |
|---|---|---|---|
| 1 | Derleme testi | `:engine:compileJava`, `:app:assembleDebug`, `:app:assembleRelease` (R8). Lint: 0 hata | ✅ |
| 2 | Bölüm yükleme | `LevelPackTest.allLevelsLoad`: 100 bölüm, kimlikler 1–100, grup eşleşmesi, kaynak/hedef var, çakışan hücre yok, JSON gidiş-dönüş aynı, **kopya düzen yok**. Uygulamada `GameLoader` aynı dosyayı yükler (UiFlowTest) | ✅ |
| 3 | Işın yansıma | `BeamReflectionTest`: 8 yön/ayna birleşimi, yansıma tablosu ve tersinirlik, duvar/blok/kaynakta durma, ızgara kenarı, 4000 rastgele yoğun düzende sonlanma ve döngü yokluğu | ✅ |
| 4 | Renk filtresi | `ColorFilterTest`: 6 filtre rengi, engelleme, tam renk eşleşmesi, hedefte karışım (kırmızı + yeşil = sarı), kesişen ışınlar | ✅ |
| 5 | Prizma | `PrismTest`: 4 yönde beyaz → kırmızı sol / yeşil düz / mavi sağ; tek renkli sapma; prizmadan önce filtre | ✅ |
| 6 | Kapı ve anahtar | `DoorKeyTest`: ışık alan anahtar kapıyı açar, karanlık anahtar açmaz, kapı kendini açamaz (en küçük sabit nokta), zincirleme kapılar, çözücü kapılı bölümü çözer. Ayrıca 9. grup bölümlerinde kapılar başlangıçta kapalı | ✅ |
| 7 | 100 bölümün çözülebilirliği | `AllLevelsSolvableTest`: her bölüm başlangıçta çözülmemiş; BFS çözüm buldu; kayıtlı optimum = BFS optimumu; çözüm yeniden oynatılınca çözülüyor; durum uzayı ipucu sınırının altında. `GameSessionTest.hintsLeadToSolution`: her bölüm yalnızca ipuçlarını izleyerek optimum hamlede bitiyor. `UiFlowTest.allHundredLevelsPlayableThroughUi`: 100 bölüm sırayla, kilitler açılarak, **oyun ekranının dokunma arayüzünden** oynandı ve hepsi 3 ★ aldı | ✅ |
| 8 | Yıldız hesaplama | `StarRatingTest`: eşikler ve monotonluk; arayüz testinde 300/300 yıldız | ✅ |
| 9 | Kayıt ve yükleme | `ProgressTest`: JSON gidiş-dönüş, bozuk kayıtta varsayılana dönme, kilit açma, yalnızca iyileşen rekorlar, başarılar, günlük seri, sıfırlama. UiFlowTest: yeni Activity aynı ilerlemeyi SharedPreferences'tan okuyor | ✅ |
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

## Zorluk dağılımı

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

## 100 bölümün doğrulama tablosu

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
