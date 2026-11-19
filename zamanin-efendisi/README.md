# Zamanın Efendisi: Üç Çağ

> Geçmişi değiştir, bugünü yönet, geleceği fethet.

Android için 2D izometrik, dikey (9:16), tamamen çevrimdışı bir zaman stratejisi oyunu.
**Kotlin + libGDX 1.13.1**, Android Studio ile açılabilen çok modüllü Gradle projesi.

Oyunun kalbi **nedensellik motorudur**: aynı haritanın Geçmiş / Şimdi / Gelecek hâlleri ayrı dünya durumlarıdır ve
Geçmiş'te yapılan her değişiklik deterministik kurallarla sonraki çağlara evrilir. Görseller bu durumdan çizilir;
yani çağ değişimi yalnızca görsel değil, gerçek oyun mantığıdır.

## Modüller

```
logic/    Saf Kotlin oyun mantığı — grafikten bağımsız, JVM'de test edilir
  World.kt            Era, Pos, Terrain, Feature, CellState, EraGrid (benzersiz nesne kimliği, seviye, dayanıklılık, köken)
  EvolutionRules.kt   Çağlar arası evrim kuralları (köprü, bitki, çöl yayılımı, köy→kasaba→şehir, çit→taş sur…)
  CausalityEngine.kt  Artımlı yeniden hesaplama, paradoks tespiti, nedensellik bağlantıları, döngü/geriye dönük bağlantı denetimi
  LevelSession.kt     Bölüm simülasyonu: kahraman, eylemler, düşman yapay zekâsı, zaman tuzağı, hedefler, günlük
  Level.kt            Bölüm tanımı (ASCII harita), hedef türleri, öğretici ipuçları
  levels/             Alan ve bölüm kataloğu (Area01: 1–5. bölümler)
  save/               Sürümlü kayıt + göç, 4 can sistemi (saat hilesi korumalı), ilerleme/ödül/Zaman Ustası seviyesi
core/     libGDX katmanı
  ZamaninEfendisiGame Ekran yönetimi ve ortak hizmetler
  render/IsoRenderer  Kod ile çizilen izometrik dünya ve animasyonlar (görsel dosya gerektirmez)
  screens/            Açılış, Ana menü, Bölümler, Oynanış, Zaman Günlüğü, Profil, Nasıl Oynanır, Ayarlar
  ui/ audio/          Tema, Türkçe karakterli FreeType yazı tipi, arayüz bileşenleri, ses efektleri
android/  AndroidLauncher (monotonik saat + açılış sayacı ile can zamanlayıcısı), manifest, simge
desktop/  Geliştirme için LWJGL3 başlatıcısı
assets/   Yazı tipi (Inter, OFL) ve ses efektleri (tools/gen_sounds.py ile üretildi)
docs/ALANLAR.md  100 alan / 500 bölüm tasarımı ve durum tablosu
```

## Derleme

Android Studio'da `zamanin-efendisi` klasörünü açın (depo kökünü değil). Komut satırından:

```bash
cd zamanin-efendisi
./gradlew :logic:test              # mantık testleri
./gradlew :android:assembleDebug   # APK: android/build/outputs/apk/debug/android-debug.apk
./gradlew :desktop:run             # masaüstünde dene (çalışma klasörü assets/)
```

`local.properties` içinde `sdk.dir` tanımlı olmalıdır (Android Studio otomatik oluşturur). GitHub Actions'ta
`Zamanın Efendisi` iş akışı testleri çalıştırır ve debug APK'yı artefakt olarak yükler.

Masaüstü geliştirici kısayolları: `-Dzaman.start=game:3:2` (3. bölüm, çözümün ilk 2 adımı uygulanmış),
`-Dzaman.start=play:5` (5. bölümü arayüz üzerinden otomatik oynat), `levels`, `journal`, `profile`, `howto`, `settings`.

## Oynanış (Aşama 1)

- Haritada komşu hücreye dokun: yürü. Uzak hücre: otomatik yol bulma. Yanındaki hücre: eylem menüsü
  (hücrenin bütün çağlardaki hâli ve hangi eylemden doğduğu da gösterilir).
- Alttaki çağ düğmeleri zaman geçişi yapar; içlerindeki küçük haritalar o çağın canlı görüntüsüdür ve başka bir
  çağdaki değişiklik anında parlar.
- Her eylem 1 tur. Yıldızlar eylem sayısına göre (Bekle sayılmaz). Geri Al sınırsız ve ücretsiz.
- 4 can, başarısız ana bölüm denemesinde 1 can, her can 15 dakikada sırayla yenilenir; tamamlanmış bölümlerin
  tekrarı can harcamaz. Saat ileri alınsa da aynı açılış içinde monotonik saat kullanıldığı için can dolmaz.

### Alan 1 — İlk Kırılma

| # | Bölüm | Öğrettiği nedensellik |
|---|---|---|
| 1 | Kırık Köprü | Geçmiş'teki ahşap köprü Şimdi'de taş köprü olur |
| 2 | Kumların Yürüyüşü | Çöl her çağ 2 adım ilerler; Geçmiş'teki fidanlar Şimdi'nin tarlalarını korur |
| 3 | Köyün Doğuşu | Köy suya yakınsa kasabaya döner; yanlış ağacı kesmek çölü köye getirir (kelebek etkisi) |
| 4 | Ahşap ve Taş | Şimdi'nin çiti kırılır, Geçmiş'in çiti yıkılmaz taş surdur; zaman tuzağı |
| 5 | Kırılma Komutanı (patron) | Kampı Geçmiş'te dağıt; ama köprün düşmana da yol açar — zaten çıkmış akıncılar kalır |

## Geliştirme aşamaları ve durum

| Aşama | İçerik | Durum |
|---|---|---|
| 1 | Proje, izometrik harita, Geçmiş↔Şimdi, nedensellik motoru, 5 bölüm, başarı/başarısızlık, kayıt, canlar | **Tamamlandı** |
| 2 | Gelecek çağı, yapı/doğa evrimi derinleştirme, zaman enerjisi, yeni bölümler | Sırada |
| 3 | Birlikler, tur tabanlı savaş, 6 düşman stratejisi, kaynaklar, özel mekanikler | Planlandı |
| 4 | Zaman Kalesi (15 bina), araştırma ağacı, ekonomi, market | Planlandı |
| 5 | Günlük/haftalık görevler, sonsuz mod, seferler, koleksiyon/müze, başarımlar, prestij | Planlandı |
| 6 | 100 alan / 500 bölüm, patronlar, dünya finalleri | Tasarım: docs/ALANLAR.md |
| 7 | Performans, ses/animasyon cilası, ekran uyumluluğu, Google Play hazırlığı | Planlandı |

Şu an olmayan özellikler arayüzde düğme olarak gösterilmez (sahte düğme yoktur). Altın, kristal ve deneyim
kazanılır ve kaydedilir; harcanacakları Zaman Kalesi/market Aşama 4'te gelecektir.

## Testler

`logic` modülündeki JUnit testleri: her bölümün referans çözümle kazanıldığı ve par'ı aştırmadığı, her adımda artımlı
nedensellik sonucunun sıfırdan hesapla birebir aynı olduğu, rastgele 1200 değişiklikte artımlı = tam hesap, yalnızca
yakın hücrelerin yeniden hesaplandığı, Gelecek'in Geçmiş'i etkilemediği, paradoks ve geri ödeme, aynı çağ bağlantıları
ile döngü ve geriye dönük bağlantı tespiti, kayıt gidiş-dönüş/yedekten kurtarma/sürüm 0 göçü, can yenilenmesi ve saat
hilesi senaryoları, ilerleme/ödül kuralları.
