# World Wonders

Unity 6 · Android & iOS · dikey 9:16 · tamamen çevrimdışı · 420 bölüm · 7 dünya harikası · Match-3 + 3D keşif.

> Bu klasör, aynı depodaki "Kuaförüm Cepte" uygulamasından bağımsız bir Unity projesidir. Unity Hub'da **Add project from disk → `WorldWonders/`** ile açılır.

## Hızlı başlangıç

1. Unity **6000.0 LTS** (Android Build Support + iOS Build Support modülleriyle) kurun ve `WorldWonders/` klasörünü açın.
   Paketler `Packages/manifest.json`'dan otomatik yüklenir (URP, Input System, Newtonsoft JSON, Addressables, AI Navigation, Test Framework).
   Input System yeni girdi arka ucunu etkinleştirmek için yeniden başlatma isterse **Yes** deyin.
2. İlk açılışta `ProjectSetup` otomatik çalışır: URP varlığını oluşturur, dikey yönü / IL2CPP / ARM64 / paket kimliğini (`com.worldwonders.game`) ayarlar ve `Assets/_Project/Scenes/Main.unity` sahnesini yapı listesine ekler.
   Elle: menü **World Wonders → Projeyi Kur**.
3. `Main` sahnesini açın ve **Play**. Oyun sahne içeriğine bağlı değildir; `GameBootstrap` her şeyi çalışma anında kurar.
4. Android: *File → Build Profiles → Android → Build*. iOS: Xcode projesi üretilir, imzalama Xcode'da yapılır.

## Depo yapısı

```
Assets/_Project/
  Scripts/Core/        Saf C# oyun çekirdeği (UnityEngine referansı YOK, noEngineReferences)
    Config/            Tasarım değerleri (GameConfig), ödül paketi, deterministik RNG, saat
    Content/           7 harika, 84 alan, 84 inşa aşaması, 42 eser + tarihî bilgi, kozmetik ve rozet katalogları
    Save/              Tek sürümlü kayıt modeli, atomik yazma + yedek + SHA-256 bütünlük denetimi, göç
    Services/          Ekonomi, can, ilerleme, yıldız, Jigsaw, inşa, harita, keşif, müze, araç, kozmetik,
                       günlük görev, seri, XP/ustalık, profil, kişisel oda, GameSession (hepsini bağlar)
    Match3/            Match-3 motoru, oyun oturumu, bölüm üretici + denge botu
    Minigames/         Büyüteç / Fırça / Kazı Malası mini oyun kuralları
  Scripts/Game/        Unity katmanı (UI Toolkit ekranları, 3D görünümler, kâşif, girdi, kamera)
  Scripts/Editor/      Proje kurulumu, bölüm doğrulama, geçici varlık listesi
  Resources/Levels/    420 bölümün veri dosyaları (wonder_0..6.json)
  Resources/Shaders/   Stilize URP şaderleri (yerleşik RP yedekli)
  Tests/EditMode/      Unity Test Runner testleri
Tests/CoreTests/       Çekirdek için NUnit testleri (dotnet test)
Tools/CoreLib/         Çekirdeği netstandard2.1 / C# 9 ile (Unity API yüzeyi) derleyen proje
Tools/LevelBuilder/    420 bölümü üreten, botla dengeleyen ve doğrulayan araç
Tools/UnityCompileCheck/  Unity Editor olmadan Game katmanı derleme denetimi
Docs/                  Plan ve durum, bölüm denge raporu, paralel geliştirme sözleşmeleri
```

## Komutlar (Unity gerekmez)

```bash
dotnet test WorldWonders/Tests/CoreTests/CoreTests.csproj            # çekirdek testleri
dotnet run -c Release --project WorldWonders/Tools/LevelBuilder -- WorldWonders   # bölüm verisini yeniden üret
dotnet build WorldWonders/Tools/UnityCompileCheck/UnityCompileCheck.csproj        # Game katmanı derleme denetimi
```

## Mimari ilkeler

- **Kurallar tek yerde:** bütün oyun kuralları `WorldWonders.Core` içindedir ve Unity olmadan test edilir. Unity katmanı yalnız gösterir ve servis çağırır.
- **Tek kayıt modeli:** `SaveData` (sürümlü). Her ödül benzersiz işlem kimliğiyle (`RewardService.Grant`) verilir; aynı ödül iki kez verilemez.
- **Atomik kayıt:** `.tmp` yaz → geri okuyup doğrula → sağlam ana dosyayı `.bak`'a kopyala → taşı. Okumada bozuk/oynanmış dosya (SHA-256 uyuşmazlığı) kullanılmaz; önceki sağlam dosyaya dönülür. Ayarlar ekranında elle yedek alma/geri yükleme vardır.
- **Saat koruması (çevrimdışı, makul düzeyde):** saat geri alınırsa can üretimi kazanç sağlamaz; günlük görevler en yüksek gün anahtarına göre yenilenir, geri alınan gün yeniden açılmaz. İleri alınan saat çevrimdışı ortamda kesin olarak tespit edilemez.
- **Veri odaklı bölümler:** 420 bölüm JSON veri dosyalarıdır. `LevelBuilder` her bölümü farklı şekil/boyut/engel/hedef kombinasyonuyla üretir, hamle sayısını ve yıldız eşiklerini bot simülasyonuyla ayarlar ve bağımsız tohumlarla doğrular (`Docs/LEVEL_REPORT.md`).
- **Geçici görseller açıkça işaretlidir:** 3D modeller ve ikonlar prosedürel primitiflerdir; kökleri `PlaceholderAsset` bileşeni taşır ve oyunda "Geçici 3D görseller" etiketi gösterilir. Final varlıklar geldiğinde bunlar değiştirilecektir.

Durum ve aşama planı: [`Docs/PLAN.md`](Docs/PLAN.md).
