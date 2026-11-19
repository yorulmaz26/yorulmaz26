# Kayıp Adalar – Hareketli Krallık

Kotlin + libGDX ile geliştirilen, dikey (9:16), çevrimdışı oynanan izometrik **krallık gelişim stratejisi** oyunu.
Bölüm seçimi yoktur: tek, sürekli büyüyen bir krallık vardır.

> Bu klasör depodaki diğer projeden (Kuaförüm Cepte) bağımsız bir Gradle projesidir.

## Oynanış

1. **Krallık (ana ekran):** Yüzen ana adadaki boş arsalara (+) dokunup bina kur, binaları yükselt.
   Üretim binalarının üstünde kaynak baloncuğu belirince dokunup topla. Evler nüfusu artırır.
2. **Ordu:** Kışla'da Piyade, Okçu, Mızraklı, Süvari ve Büyücü eğit (nüfus ve kaynak gerekir).
3. **Harita:** Bulutlu adalara keşif gemisi gönder → korsanlı adalara saldır (ordu gücün ≥ korsan gücü ise kazanırsın)
   → kurtardığın adayı **krallığa çek**: ada süzülerek gelir, köprüyle birleşir; yeni arsalar ve kaynak bonusu kazanırsın.
   Birleşik adaları başka yuvalara **taşıyabilir** veya boşsa **ayırabilirsin**; aynı türden yan yana adalar ek bonus verir.
4. **Araştırma:** Tarım Verimi, İnşaat Hızı, Askerî Eğitim, Keşif Menzili.
5. **Korsan baskınları** (Ana Bina 3+): Savunma Kulesi ve ordun birlikte savunur.
6. **Görev zinciri + danışman:** Ekrandaki görev kartı her an bir sonraki adımı söyler; "Git" seni ilgili binaya,
   arsaya veya haritaya götürür ve hedefi okla gösterir. 6 bölüm / 31 görev, sonrasında sonsuz gelişim hedefleri.

Zaman: inşaat, üretim, nüfus, eğitim, araştırma ve keşif gerçek zamanda ilerler; uygulama kapalıyken de devam eder.
Bekleme süreleri kısa tutuldu ve kristalle hızlandırılabilir. Güvenilir saat (monoton saat + önyükleme sayacı) cihaz
saatini ileri almayı etkisiz kılar.

## Görseller

Tüm oyun nesneleri kullanıcının verdiği referans panolarından kesilmiş sprite'lardır; kodla şekil çizilmez.

- `tools/cut_sprites.py`: koyu lacivert panolardan nesneleri satır/sütun boşluklarına göre ayırır, arka planı ve alt
  yazıları temizler → `assets/game/<kategori>/*.png` (228 sprite: adalar, binalar, birimler, gemiler, kaynaklar,
  ikonlar, efektler, bulutlar, zemin karoları, dekor, danışman portreleri, logo).
- `tools/clean_checker.py`: açık "dama deseni" zeminli yüksek çözünürlüklü sprite'ları (ana ada, 8 bina) temizler.
- `./gradlew :desktop:packSprites` → `assets/atlas/game.atlas` (TextureAtlas; oyun yalnızca atlası yükler, `AssetManager`).
- Yerleşim/ölçek ayarları tek yerde: `core/.../view/KingdomView.kt` içindeki `Layout` nesnesi.
- Arayüz çerçeveleri (parşömen paneller, ahşap/yeşil butonlar) ve yazılar kodla/fontla üretilir; PNG'lerdeki yazılar kullanılmaz.

## Modüller

```
logic/   Saf Kotlin oyun mantığı (JVM'de test edilir)
  kingdom/Config.kt     Veri şeması (assets/data/kingdom.json: binalar, birlikler, adalar, araştırma, görevler, denge)
  kingdom/Kingdom.kt    Simülasyon: inşaat, üretim, nüfus, ordu, keşif, savaş, birleştirme/taşıma/ayırma, baskın, pazar, görevler
  kingdom/WorldGen.kt   Halkalar hâlinde ada haritası
  kingdom/KingdomGame.kt Kayıt + güvenilir saat + simülasyon cephesi
  save/SaveSystem.kt    Sürümlü (v2), CRC'li, atomik, yedekli kayıt; eski v1 kayıtları göç ettirir
  meta/TrustedClock.kt  Manipülasyona dayanıklı saat
core/    libGDX: Theme (tasarım sistemi), Assets (atlas), view/ (KingdomView, WorldView, BattleScene, Sky, PanZoom),
         screens/ (Splash, Menü, Krallık, Harita, Ayarlar)
android/ Android başlatıcısı     desktop/ masaüstü başlatıcısı, atlas paketleyici, ekran görüntüsü sürücüsü
```

## Derleme

```bash
./gradlew :logic:test                # 15 test: kurallar, kayıt, saat ve görev zincirinin oynanabilirliği (bot testi)
./gradlew :desktop:packSprites       # sprite'lar değişince atlası yeniden üret
./gradlew :android:assembleDebug     # android/build/outputs/apk/debug/android-debug.apk
./gradlew :desktop:run               # masaüstünde oyna
```

## Bilinen eksikler

- Sprite'lar referans panolarından otomatik kesildi; bazılarında kenar pürüzü veya küçük panel kalıntısı olabilir.
  Birim ve bina animasyon kareleri yok (hareket, sprite kaydırma/ölçekleme ile yapılıyor).
- Köprüler ahşap zemin karosu sprite'ı tekrar edilerek çiziliyor; özel köprü sprite'ı yok.
- Sesler basit sentez seslerdir. Gerçek para ile satın alma ve reklam yok.
- Arayüz yalnızca Türkçe; metinler şu an kod içinde.
- Gerçek Android cihazda performans testi yapılmadı (oyun masaüstünde Xvfb ile ekran görüntüleriyle doğrulandı).
