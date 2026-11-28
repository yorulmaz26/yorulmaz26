# Test raporu — Prismway (Işık Yolu) 1.8.0

**Tarih:** 28 Kasım 2026.
**Ortam:** Linux bulut konteyneri, JDK 17, Robolectric 4.14.1 (Android 14 çalışma zamanı, gerçek grafik çizimi),
JUnit 4. **Emülatör ve fiziksel cihaz yok.**
**Komut:** `./gradlew :engine:test :app:testDebugUnitTest`

## Özet

| | Sayı |
|---|---|
| Toplam test | **155** |
| Geçen | **151** |
| Başarısız | **0** |
| Atlanan | 4 — `PromoCaptureTest` ve `TabletCaptureTest`, yalnızca `-PpromoDir` ile kayıt yapar. Bu çalışmada 6 dilde ayrıca çalıştırıldı. |

| Test sınıfı | Test | Kapsam |
|---|---|---|
| **LivesTest** | 22 | Can sistemi (aşağıda ayrıntılı) |
| **UiFlowTest** | 33 | Arayüz akışları: bütün ekranlar, 500 bölümün arayüzden oynanması, can akışı, reklamlar, market, dil değişimi … |
| **LocalizedUiTest** | 6 | 6 dilde 13 ekran/pencerede metin taşması |
| **LanguageDetectionTest** | 7 | Sistem dili, İngilizce yedek, kalıcı seçim, çoğul, sayı biçimi, iki dilli ad |
| **I18nTest** | 3 | Eksik/fazla anahtar, yer tutucu, çoğul; koddaki anahtarlar; sabit Türkçe metin taraması |
| **WorldKeysTest** | 2 | Dünya, bölüm grubu, konu, mekanik, renk ve başarı anahtarları; Türkçe metinler motorla aynı |
| **EconomyTextsTest** | 3 | Market ürün adları ve açıklamaları, satın alma engeli ve ödül kodlarının metinleri |
| EconomyTest | 16 | Oyun içi ekonomi |
| GameSessionTest | 10 | Oturum, hamle hakkı, geri alma, ipucu planı |
| ProgressTest | 9 | Kayıt/yükleme, bozuk kayıt |
| AdPolicyTest | 6 | Geçiş reklamı sıklığı, ödüllü video sınırları |
| BeamReflectionTest, ColorFilterTest, PrismTest, DoorKeyTest, BeamFollowerTest | 22 | Işın fiziği ve mekanikler |
| AllLevelsSolvableTest, LevelAuditTest, LevelPackTest, ShortcutsTest, StarRatingTest, LightColorTest | 12 | 500 bölümün çözülebilirliği ve denetimi |

## Can sistemi (§2.4) — TEST EDİLDİ

| İstenen durum | Test | Sonuç |
|---|---|---|
| 20 dk = 1200 sn, merkezi ayar | `configIsTwentyMinutes` (`GameConfig.LIFE_REGEN_SECONDS = 1200`) | geçti |
| 0 → 20/40/60/80 dk'da 1/2/3/4 can (maks. 4) | `zeroToFullTakesEightyMinutes` (her eşikten 1 sn önce artmadığı da denetlenir) | geçti |
| Dolu iken sayaç durur | `zeroToFullTakesEightyMinutes`, `startsWithFourLives` | geçti |
| Yeni kayıp sayacı sıfırlamaz | `furtherLossDoesNotResetRunningTimer` | geçti |
| Oyun açıkken | `regeneratesEveryTwentyMinutesKeepingRemainder`, UiFlowTest `livesFlow` (19 dk'da can yok, 20. dk'da var, rozet "0/4" → "1/4", dolunca bildirim bir kez) | geçti |
| Oyun kapalıyken / zorla durdurma / art arda aç-kapa | `timePassesWhileAppClosedAndRestarted` (47 dk kapalı → 2 can; 30 kez aç-kapa süre kazandırmaz) | geçti |
| Telefon yeniden başlatma, internetsiz | `rebootWithoutNetworkCountsOffTime`, `rebootUsesWallClockButNotBackwards` | geçti |
| Saat ileri | `movingClockForwardGivesNothing`; ağ saati olan yeniden başlatma: `networkTimeBeatsForwardClockAcrossReboot` | geçti |
| Saat geri | `movingClockBackGivesNothing`, `rebootUsesWallClockButNotBackwards` | geçti |
| Tarih değişikliği, saat dilimi | `timeZoneChangeHasNoEffect` (UTC/monoton saat; 1 gün ileri almak can vermez) | geçti |
| Negatif / maksimum üstü | `livesNeverNegativeOrAboveMax` (kayıtta −5 → 0, 50 → 4) | geçti |
| Reklam/market ödülü sonrası | `winningOrRewardKeepsTimerProgress` (yarım sayaç korunur, ödül maksimumu aşmaz); UiFlowTest `rewardedVideoGivesLife` | geçti |
| Kayıt dosyası değiştirme | `editedSaveIsDetectedButProgressKept` (canlar 0'dan dolar, oyun parası başlangıç değerine döner; bölüm ilerlemesi, yıldızlar ve görünümler korunur; imzayı silmek de algılanır) | geçti |
| Güncelleme (eski kayıt) | `oldUnsignedSaveMigratesWithoutPenalty` (1.7.1 kaydı cezasız yüklenir, sonraki kayıt imzalı) | geçti |
| Eski sürüme geri dönüş | `newSaveReadableByOldParser` (kayıt düz JSON; imza ayrı anahtarda) | geçti |
| Kayıt/yükleme | `stateSurvivesSaveAndLoad` | geçti |

**Sınır:** Tamamen çevrimdışı bir oyunda saat hilesine karşı kusursuz koruma yoktur.
- **Aynı açılış içinde:** monoton saat kullanıldığı için saati ileri/geri almak işe yaramaz.
- **Yeniden başlatmadan sonra:** ağ saati yoksa duvar saati kullanılır. Daha önce görülen en ileri zamandan geri
  gidilmez, ama saati ileri alıp telefonu yeniden başlatmak (ağ saati yoksa) süre kazandırabilir.
- **Kayıt imzası:** caydırıcıdır. Root erişimli bir cihazda uygulama kodu incelenerek atlatılabilir. Android 12+
  hedefli sürümlerde kayıt dosyasına root olmadan erişilemez.
- **Normal oyuncu cezalandırılmaz:** 2 dakikaya kadar saat sapması tolere edilir. Eski kayıtlar cezasız taşınır.

## Çok dilli arayüz (§3) — TEST EDİLDİ

- **Eksik çeviri:** 0 (tr, en, de, it, fr, es; her dilde 464 anahtar).
- **Sabit Türkçe metin:** arayüz kodunda 0.
- **`LocalizedUiTest`:** 6 dilde, 360×640 dp (küçük telefon) ekranda şu ekranlar açılır, kesik ya da sığmayan metin
  aranır. **6/6 dil temiz.**
  - öğretici,
  - ana menü,
  - bölümler,
  - oyun,
  - duraklatma,
  - tamamlandı,
  - hamle bitti,
  - can kalmadı,
  - günlük bulmaca,
  - başarılar,
  - market (4 sekme),
  - ayarlar,
  - dil seçimi.
  - Bulunup düzeltilenler:
    - İspanyolcada can rozeti sıkışıyordu.
    - İtalyancada oyun alt başlığı kesiliyordu.
    - İngilizcede video düğmesi iki satıra çıkınca market listesi kayıyordu.
- **Dil değişimi** (`languageSwitch`):
  - Ayarlar'dan İngilizceye geçilir, ekran yeniden başlatılmadan İngilizce olur.
  - Türkçeye geri dönülür.
  - "Telefonun dili" (otomatik) seçilince oyuncu seçimi kalkar.
  - Yıldızlar, kristal, Prizma Taşı, can ve ipucu değişmez.
- **Dil algılama:**
  - Almanca telefon → Almanca.
  - fr-CA → Fransızca.
  - Japonca → İngilizce.
  - Oyuncunun seçimi sistem dilinden önce gelir ve yeniden açılışta korunur.

## Arayüz (§8.2)

| Kontrol | Nasıl | Sonuç |
|---|---|---|
| Bütün ekranlar ve düğmeler | `allScreensRenderAndFlowWorks`, `marketFlow`, `livesFlow` … | TEST EDİLDİ |
| 500 bölüm arayüzden oynanabilir | `allLevelsPlayableThroughUi` | TEST EDİLDİ |
| Dar ekran (360 dp) | `narrowScreenHeaderFits`, `LocalizedUiTest` | TEST EDİLDİ |
| Tablet | `tabletLandscapeWorks`; 7"/10" kayıtlar | TEST EDİLDİ |
| Çentik / sistem çubukları | `contentStaysInsideSystemBars` | TEST EDİLDİ |
| Geri tuşu | `backRightAfterOutOfMovesLosesLife`, `retryWithNoLivesGoesToLevels` … | TEST EDİLDİ |
| Çift dokunma koruması | `doubleTapSpendsHintOnce`, `rapidUndoTapsAllCount` | TEST EDİLDİ |
| Ses/müzik/titreşim ayarları | `allScreensRenderAndFlowWorks` (ayar anahtarları) | TEST EDİLDİ (ses çıkışı gözle/kulakla denetlenemedi) |
| Dokunmatik hassasiyet, animasyon akıcılığı | — | MANUEL KONTROL GEREKLİ (gerçek cihaz) |

## Reklam (§8.4) — sahte sağlayıcıyla TEST EDİLDİ

| Kontrol | Test |
|---|---|
| Ödüllü video tam izlenince ödül, yarıda bırakılınca ödül yok | `rewardedVideoGivesMoves`, `rewardedVideoGivesLife`, `rewardedVideoGivesHintAndCrystals` |
| Aynı videodan iki kez ödül yok, günlük sınır | `AdPolicyTest.rewardsHaveDailyLimitsAndRealEffects` |
| Video açılamazsa | `failedVideoAndPrivacyPolicy` |
| Geçiş reklamı sıklığı (ilk 10 bölüm yok, ≥3 bölüm ve ≥4 dk ara) | `interstitialBetweenLevelsWithFrequencyCap`, `AdPolicyTest` |
| İnternetsiz | Oyun tamamen oynanır; reklam yoksa düğme "hazırlanıyor" der |
| İzin (UMP) | Kod incelemesi; Ayarlar'da "Reklam Gizlilik Seçenekleri" |
| Uygulama içi satın alma / Billing | UYGULANAMAZ (gerçek para yok) |

Gerçek reklam tıklaması ve gerçek satın alma yapılmadı.

## Performans (§8.3)

Masaüstü JVM'de ölçüldü. Telefon değerleri farklıdır; yalnızca gösterge niteliğindedir.

| Ölçüm | Değer |
|---|---|
| Release APK | **2,86 MB** (1.7.1: 4,41 MB → −%35) |
| Release AAB | 5,18 MB |
| `levels.json` (500 bölüm) ayrıştırma | 12,4 ms; açılışta arka plan iş parçacığında |
| Hamle başına ışın izleme (bölüm 457, 8×11) | 1,7 µs |
| Bölümler yüklüyken yığın | ~4,3 MB |
| Çizim döngüsü | `BoardView.onDraw` kare başına nesne üretmiyor. Yalnızca stüdyo girişi ve yıldız animasyonunda (birkaç saniye) gradyan oluşturuluyor. |

Yapılan iyileştirmeler:
- Müzik WAV → OGG Vorbis: 1,41 MB → 69 KB, örnek sayısı birebir, döngü etiketi var.
- APK'ya yalnızca oyunun 6 dili alınıyor (kütüphanelerin diğer dillerdeki kaynakları çıkarıldı).

FPS, RAM, CPU, pil, ısınma, açılış süresi ve düşük donanımlı cihaz davranışı: **MANUEL KONTROL GEREKLİ** (bu ortamda
cihaz ve emülatör yok).

## Bulunup düzeltilen hatalar (bu sürümde)

1. "PG" reklam derecesi gizlilik metinlerinde "genel izleyici" diye yanlış açıklanmıştı. 6 dilde "ebeveyn rehberliği"
   olarak düzeltildi.
2. Market rehberindeki "Dünya başarıları +1 Prizma Taşı" bilgisi 1. dünya için yanlıştı. "2.–5. dünya" olarak
   düzeltildi.
3. Uzun dillerde market video düğmesi iki satıra çıkınca liste kayıyordu: yanlışlıkla satın alma riski vardı.
4. İspanyolcada can rozeti, İtalyancada oyun alt başlığı taşıyordu.
5. Menü ve açılıştaki slogan ortalı değildi.
6. Satın alma engeli mesajları her dilde Türkçe görünecekti. Motor artık kod döndürüyor, metni arayüz çeviriyor.
7. Kayıt dosyası elle değiştirilirse canlar korumasızdı. Artık imza var.
8. Ekran okuyucu ayar anahtarlarını adsız okuyordu.
9. Tanıtım kayıtlarında kazanma penceresi çok erken çıkıyordu. Robolectric saat sıçraması fark edilip kayıt
   zamanlaması düzeltildi.
