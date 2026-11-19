# Kayıp Adalar – Hareketli Krallık

Kotlin + libGDX ile geliştirilen, dikey (9:16), tamamen çevrimdışı oynanabilen 2D izometrik strateji oyunu.
Adalar yalnızca savaş alanı değil, doğrudan stratejik araçlardır: kaydırılır, döndürülür, yükseltilir,
takas edilir, fırlatılır; köprüler kurulur, element kombinasyonları tetiklenir, düşman yolları kesilir.

> Bu klasör depodaki diğer projeden (Kuaförüm Cepte) tamamen bağımsız bir Gradle projesidir.

## Modüller

```
kayip-adalar/
  logic/    Saf Kotlin oyun kuralları (render'dan bağımsız, JVM'de test edilir)
    model/     Ada, birlik, yön, kaynak, mekanik türleri
    engine/    Board (durum), Rules (hareket/köprü/çevre), Combos, Combat (savaş + 5 yapay zekâ + patronlar),
               Objectives (16 hedef türü), Solver (ışın araması ile çözücü)
    level/     LevelDef (JSON şeması), AreaCatalog (100 alan), LevelGenerator (kontrollü prosedürel üretim)
    meta/      MetaConfig (ekonomi verisi), Systems (krallık, araştırma, birlik, can, market, görev, başarım,
               koleksiyon, sefer, rastgele olay, itibar), TrustedClock (manipülasyona dayanıklı saat)
    save/      SaveData (sürümlü kayıt şeması), SaveSystem (CRC, yedek, kurtarma, göç)
    GameCore   Tüm sistemleri birleştiren cephe: bölüm akışı, ödüller, mücadeleler, sonsuz mod,
               günlük bulmaca, krallık savunması, Yeni Çağ (prestij)
    L10n       Yerelleştirme (i18n/strings_<dil>.properties)
  core/     libGDX katmanı: tasarım sistemi (Theme), izometrik çizim (IslandArt, BoardView, KingdomView),
            26 ekran (screens/), ses yöneticisi
  android/  Android başlatıcısı (immersive, dikey), güvenilir zaman kaynağı (elapsedRealtime + BOOT_COUNT)
  desktop/  Geliştirme için masaüstü başlatıcısı + otomatik ekran görüntüsü sürücüsü
  assets/   data/rules.json (birim, kombo, kural dengesi), data/meta.json (ekonomi), levels/area_001..100.json,
            i18n/, fonts/ (DejaVu Sans – lisansı ekli), sfx/ (sentezlenmiş sesler)
  tools/    make_meta.py (ekonomi verisi), make_audio.py (ses sentezi)
```

## Derleme ve çalıştırma

Gereksinimler: JDK 17+, Android SDK (platform 35, build-tools 35). `local.properties` içine `sdk.dir=...` yazın.

```bash
./gradlew :logic:test                 # kural, ekonomi, kayıt ve 500 bölüm çözülebilirlik testleri
./gradlew :android:assembleDebug      # APK: android/build/outputs/apk/debug/android-debug.apk
./gradlew :desktop:run                # masaüstünde 450x800 pencerede oyna
./gradlew :logic:generateLevels       # alan 2–100 bölüm dosyalarını yeniden üret (alan 1 elle tasarlandı)
./gradlew :logic:generateLevels -Pareas=57,61   # yalnızca belirli alanlar
```

Ekran görüntüsü sürücüsü (CI/Xvfb): `desktop/build/install/desktop/bin/desktop --shots <klasör>` (çalışma dizini `assets`).
Örnek görüntüler: `docs/screenshots/`.

## Bölüm sistemi

- 100 alan × 5 bölüm = **500 bölüm**. Alan 1 (bölüm 1–5) elle tasarlanmıştır; alan 2–100 `LevelGenerator` ile
  üretilip JSON olarak saklanır (veri odaklı).
- Alanlar 1–25 her biri yeni bir özellik tanıtır (kaydırma, döndürme, köprüler, sis, ateş/su, buz, rüzgâr,
  enerji/yükseklik, portal, kale savaşı, takas, savunmacı YZ, fırlatma, yerçekimi, çöken adalar, ticaret,
  tuzakçı YZ, zehir, zaman, elektrik, volkan, kaynakçı YZ, yaşam, stratejist YZ, kadim güçler).
  Alanlar 26–100 bu özelliklerden benzersiz çiftlerle "remiks" alanlarıdır (her birinin teması, harita şekli ve adı farklı).
- Alan içi yapı: 1. bölüm öğretir, 2. bağımsız kullandırır, 3. önceki mekanikle birleştirir, 4. iki hedefli
  karmaşık problem, 5. alan finali. Her 25. bölüm büyük patron (2 evre), her 50. bölüm dünya patronu (3 evre).
- **Çözülebilirlik kanıtı:** üretici önce çözülmüş ada ağacını kurar, karıştırır, sonra gerçek kural motoruyla
  (düşman ve patron hamleleri dahil) ışın araması yaparak çözüm bulur. Çözüm bölüm dosyasına yazılır ve
  `AllLevelsTest` 500 bölümün tamamını yeniden oynatarak doğrular. Hamle sınırı ve yıldız eşikleri bu çözüme göre ayarlanır.
- 16 hedef türü: tüm adaları birleştir, kaynak topla, kaleyi koru, üs ele geçir, hedef adaya köprü, köy kurtar,
  kristal topla, adayı hedefe taşı, düşmanı tuzağa düşür, portal zinciri, kaçış yolu, ticaret yolu, hayatta kal,
  özel kombo, patron yen, tüm düşmanları yen.

## Uygulanan sistemler (çalışır durumda)

- Hareketli adalar: kaydırma, döndürme, takas, birleştirme/ayırma (birleşik adalar tek hamlede birlikte kayar),
  yükseltme/alçaltma (yükseklik farkı >1 köprüyü engeller), fırlatma,
  rüzgâr sürüklemesi, yerçekimi zinciri, sabitleme (tuzakçı/patron), donma ve erime, çökme sayaçları, gizli (sisli) adalar,
  dönüşüm (volkan + su → maden), yangın tehlikesi.
- 22 element kombosu + aynı tür 3 ada rezonansı + 4+ ada zincirleme reaksiyonu; Ada Ansiklopedisi keşif takibi.
- Tur tabanlı taktiksel savaş: 10 oyuncu birimi + patron, yükseklik/kale/askerî ada bonusları, menzil (köprü adımı).
- 5 düşman davranışı: saldırgan, savunmacı, tuzakçı (köprü çevirir/ada kilitler), kaynakçı (üretimle asker üretir),
  stratejist (fırsat/tehlike/mesafe puanlaması). 12 patron şablonu, 9 patron yeteneği, evreli davranış.
- Kalıcı krallık: 15 bina (seviye, maliyet artışı, kale seviyesi sınırı, saatlik üretim, depo süresi), görsel olarak büyüyen ada,
  liman takası, rastgele krallık olayları (seçimli).
- Araştırma ağacı: 4 dal × 6 düğüm, her dalda kalıcı alternatif seçim.
- Ekonomi: 7 temel + 3 nadir kaynak; tüm denge değerleri `data/meta.json` ve `data/rules.json` içinde.
- 4 can, 15 dakikada yenilenir; tamamlanmış bölümler, günlük bulmaca, sonsuz mod, krallık ve koleksiyon cansız kullanılabilir.
  `TrustedClock`: aynı önyüklemede monoton saat (cihaz saatini ileri almak işe yaramaz), yeniden başlatmada duvar saati
  (geri alınan saat zamanlayıcıyı kilitlemez), çevrimdışı tek seferlik kazanç sınırlı.
- Market (10 kategori, 43 ürün; oyun içi para), envanter ve kuşanma (ada/kale/köprü renkleri, birlik kostümü, zafer efekti,
  profil çerçevesi, krallık dekorasyonları, gökyüzü teması). Gerçek para için `PurchaseProvider` soyutlaması var.
- Günlük (3) ve haftalık (3) görevler, 104 başarım, 100 koleksiyon öğesi (10 set, set bonusları), itibar seviyeleri ve unvanlar.
- Seferler (gerçek zamanlı, yuva sayısı ve süre Keşif Kulesi ile değişir), Sonsuz Takımadalar, Günlük Strateji Bulmacası
  (aynı gün aynı bulmaca, kişisel rekor), Krallık Savunması (garnizon seçimi, periyodik saldırı), özel mücadele kuralları
  (yalnızca kayıtlı çözümle hâlâ kazanılabiliyorsa sunulur), Yeni Çağ prestij sistemi (500. bölümden sonra).
- Kayıt: sürümlü şema, CRC32, atomik yazma, otomatik yedek, bozuk kayıtta yedekten kurtarma, göç altyapısı;
  uygulama arka plana alınınca kaydedilir.
- Etkileşimli öğretici (çözümün ilk adımını işaretler), ipucu (çözücüyle anlık hesaplanır), pinch/düğme ile yakınlaştırma.
- Animasyonlar: ada kayması/dönmesi, köprü oluşumu, kombo patlamaları, hasar ve ödül yazıları, çöküş, zafer konfetisi.
- Sesler ve müzik `tools/make_audio.py` ile sentezlenir (telif gerektirmez).

## Bilinen eksikler / sonraki adımlar

- Birleştirme mekaniği bölüm üreticisinin çözüm aramasında kullanılabilir ama hiçbir bölüm onu zorunlu kılacak şekilde
  tasarlanmadı (Takas Koyu ve 66. bölümden itibaren ek seçenek olarak açıktır).
- Grafikler prosedürel şekillerle çiziliyor; profesyonel sprite/atlas sanatı ve iskelet animasyonları eklenmedi.
- Ses efektleri basit sentez seslerdir; profesyonel ses tasarımı yapılmadı.
- Google Play Billing ve ödüllü reklam entegrasyonu yok (yalnızca soyutlama hazır).
- Arayüz yalnızca Türkçe; diğer diller için `strings_<dil>.properties` eklenmesi ve dil seçimi gerekir.
- Android cihazda/emülatörde gerçek performans profili çıkarılmadı (APK derleniyor; oynanış masaüstünde Xvfb ile doğrulandı).
- Erişilebilirlik: büyük yazı seçeneği var; renk körlüğü modu ve ekran okuyucu desteği yok.
- Yayın için imzalı release yapılandırması ve Play Store materyalleri hazırlanmadı.
