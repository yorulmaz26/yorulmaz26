# Yanlış Renk — Mimari

## Modüller

| Modül | Bağımlılık | İçerik |
|---|---|---|
| `logic/` | yalnızca Kotlin + kotlinx.serialization | Oyunun tüm kuralları; arayüzden tamamen bağımsız, JVM üzerinde test edilir |
| `core/` | logic + libGDX + gdx-freetype | Çizim, Scene2D arayüz, 25 ekran, ses |
| `android/` | core + gdx-backend-android | `AndroidLauncher`, Android platform hizmetleri (monoton saat, açılış sayacı, atomik dosya, titreşim, ağ zamanı) |
| `desktop/` | core + lwjgl3 | Geliştirme ve **görsel kalite kontrol** (Xvfb altında ekran görüntüsü + çökme testi) |
| `server/` | logic + JDK HttpServer | Küçük arka uç: güvenilir zaman, sunucu tarafı can doğrulama, satın alma doğrulaması için yer tutucu |

## İstenen sistemlerin karşılıkları

| İstenen | Sınıf |
|---|---|
| GameManager | `logic/GameSession` (meta sistemlerin tamamı) + `core/YanlisRenkGame` (uygulama yaşam döngüsü) |
| ScreenManager | `YanlisRenkGame.go/back` (geçmiş yığını, geri tuşu) + `screens/BaseScreen` |
| PlayerController / ColorManager | `run/RunWorld.tap()`, `model/ColorCycle` (yön/ayna, kilitli renk atlama) |
| GateManager / GateRuleManager | `model/GateData.validMask/accepts` (8 kural × 4 hareket türü) |
| AreaManager | `level/AreaCatalog` (20 dünya, 100 alan, paletler, odak mekanikler) |
| LevelManager | `level/LevelRepository` (alan paketlerini gerektiğinde yükler; dosya bozuksa aynı tohumla yeniden üretir) |
| LevelValidator | `level/LevelValidator` (insan tepki modeliyle DP çözücü) |
| EndlessModeManager | `run/EndlessGenerator` + `GameScreen` sonsuz/haftalık modu |
| LifeManager | `life/LifeManager` |
| TrustedTimeManager | `time/TrustedClock` + platform `fetchServerTime` + `server/TimeServer` |
| SaveManager | `save/SaveManager` (sürümlü, HMAC bütünlüklü, yedekli, göçlü) |
| CurrencyManager | `GameSession.credit/spend` (defter kaydı, tekrar ödül koruması) |
| ShopManager / InventoryManager | `shop/ShopCatalog` + `GameSession.buy/equip/useConsumable` |
| MissionManager / AchievementManager / ProgressionManager | `progress/Progression.kt` + `GameSession` |
| CharacterManager | `shop/ShopCatalog.characters` + `render/CharacterPainter` |
| AudioManager | `core/Audio` (müzik/efekt ayrı ses düzeyi, yumuşak geçiş) |
| AssetManager | `core/Assets` (dokular, yoğunluğa göre üretilen Türkçe yazı tipleri, sesler) |

## Veri akışı

```
levels/area_XXX.json ──► LevelRepository ──► LevelData ──► Track ──► RunWorld (120 Hz sabit adım)
                                                                   │  olaylar (RunEvent)
                                                                   ▼
                                    GameScreen ──► WorldRenderer (ShapeRenderer + SpriteBatch)
                                         │
                                         └──► GameSession.completeLevel/failLevel ──► SaveManager
```

* Oyun mantığı arayüzden ayrıdır: `RunWorld` hiçbir libGDX sınıfı kullanmaz; testlerdeki otomatik oyuncu ile oyun ekranı **aynı** simülasyonu çalıştırır.
* Karakter hızı yalnızca konuma bağlıdır (`Timeline`), bu yüzden her kapıya varış anı belirlenimcidir; hareketli kapıların rengi bu ana göre hesaplanır.
* Parçacıklar nesne havuzludur (`Particles`, 600 sabit nesne, kare başına bellek ayırma yok). Kapı çizimi görünür aralıkla sınırlıdır.

## Bölüm verisi

* `./gradlew :logic:generateLevels` 500 bölümü tohumlu üreteçle üretir, doğrular ve `android/assets/levels/` altına yazar (100 dosya, ~1,3 MB).
* `LevelTests.dosyadakiBolumlerUreteceEsit` veri dosyalarının üreteçle aynı olduğunu denetler.

## Zaman ve can güvenliği (sınırlarıyla)

1. **Aynı açılış içinde** geçen süre yalnızca `SystemClock.elapsedRealtime()` farkından hesaplanır. Duvar saatini ileri/geri almak can süresini etkilemez (testlerle doğrulandı). Duvar saati sapmaları yalnızca teşhis için sayılır, oyuncu cezalandırılmaz.
2. **Yeniden başlatma** `Settings.Global.BOOT_COUNT` değişimi ya da monoton saatin gerilemesiyle algılanır. Kesin alt sınır olan "açılıştan beri geçen süre" hemen verilir; geri kalan, doğrulanamayan süre bekletilir.
3. **Sunucu zamanı** (kendi `/time` uç noktası ya da HTTPS `Date` başlığı) alındığında bekleyen süre — önceki çapa da doğrulanmışsa — geriye dönük eklenir. Ayarlar ekranında "Eşitle" düğmesi vardır.
4. Günlük ödül/görev günleri için ayrı, **asla geri gitmeyen** esnek bir saat kullanılır: doğrulanmamışken duvar saatinin en fazla 36 saat önüne geçmesine izin verilir (çevrimdışı oyuncu yeni günü kaçırmasın diye). Bu, saat ileri alınarak en fazla 1–2 günlük ödülün erken alınabileceği anlamına gelir; bilinçli bir ödünleşimdir.
5. **Sınırlar:** Tamamen çevrimdışı ve root erişimli bir cihazda monoton saat ya da kayıt anahtarı değiştirilebilir; %100 koruma iddia edilmez. Kayıt HMAC'i cihazdaki bir anahtarla üretilir; amaç basit dosya düzenlemesini yakalamaktır.
6. Önemli işlemlerin (can, satın alma) sunucuda doğrulanması için `server/` içinde `/lives/verify` ve `/purchase/verify` uç noktaları hazırdır; istemci bunları kullanacak şekilde `AndroidPlatform.backendUrl` ile bağlanır. Varsayılan sürümde arka uç adresi boştur.

## Kayıt sistemi

* Dosya: `filesDir/yanlisrenk_save.dat` (`YRSAVE|sürüm|hmac` + JSON). Her yazımda önceki geçerli kayıt `.bak` olur; yazım geçici dosya + `fsync` + yeniden adlandırma ile atomiktir.
* Bozuk ana kayıt → yedek; ikisi de bozuk → `.corrupt` olarak saklanır ve temiz başlangıç yapılır.
* `SaveMigrations` eski sürümleri taşır (v1 → v2 örneği testte). Android otomatik yedeklemesi kayıt dosyalarını kapsar.
* DataStore yerine bu dosya tabanlı yapı seçildi: libGDX'in tek iş parçacıklı oyun döngüsünde eşzamanlı, atomik ve test edilebilir olduğu için. Davranış olarak DataStore'un sağladığı atomik yazma + bozulmaya dayanıklılık korunur.

## Gerçek parayla satın alma (hazırlık)

İlk sürümde gerçek para yoktur. `ShopItem.currency` + `GameSession.buy` tek bir satın alma noktasıdır; Google Play Billing eklendiğinde ürün kimlikleri katalogdaki `id` ile eşlenir, satın alma jetonu `server/purchase/verify` ile doğrulanır ve ödül `credit(..., rewardId = purchaseToken)` ile **bir kez** verilir (aynı jeton iki kez işlenemez).
