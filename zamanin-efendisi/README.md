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
  levels/             Alan ve bölüm kataloğu (Area01–Area07: 1–35. bölümler)
  save/               Sürümlü kayıt + göç (v0→v1→v2), 4 can sistemi (saat hilesi korumalı), ilerleme/ödül/Zaman Ustası seviyesi
  meta/               Ekonomi (10 kaynak), Zaman Kalesi binaları ve üretimi, araştırma ağacı, market ve ödeme sağlayıcısı soyutlaması
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

## Oynanış

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

### Alan 2–5 (Aşama 2)

| Alan | Bölümler | Yeni kural |
|---|---|---|
| 2 · Geleceğin Kapısı | 6–10 | Gelecek çağı; köprü 3 aşamada evrilir (ahşap → taş → ışık geçidi); çit → taş sur → enerji kalkanı; Gelecek'te robotlar |
| 3 · Kayıp Orman | 11–15 | Yaşlı ağaç + 2 komşu bitki → geçilmez Kadim Orman; kesmek ormanı önler; orman hem kalkan hem engel |
| 4 · Taş Köprüler | 16–20 | Yapı evrimi hedefleri (Şimdi'de taş köprü, Gelecek'te şehir/enerji kalkanı); zaman mühürleri ile çağlar arası bağlantılar |
| 5 · Enerji Vadisi | 21–25 | Zaman enerjisi: her geçiş 1 enerji; kristaller enerji verir; aynı kristali iki çağda toplamak paradoksla silinir |

### Alan 6–7 (Aşama 3)

| Alan | Bölümler | Yeni kural |
|---|---|---|
| 6 · Donmuş Yıllar | 26–30 | Zamanı Dondur: dondurulan nesne sonraki çağlarda değişmez (köy harabeye dönmez, ağaç ormana, kamp kaleye büyümez) |
| 7 · Yıkılmış Krallık | 31–35 | Müttefik birlikler (dokun → mavi: yürü, kırmızı: saldır), nöbet, kuleler, 6 düşman stratejisi |

**Savaş:** hasar = saldırı − savunma (en az 1). Şövalye kalkanı 1 hasar emer, Mekanik Koruyucu komşularını korur,
Kuşatma Mühendisi kaleye 3 kat vurur, Zaman Büyücüsü vurduğunu 2 tur dondurur. Düşman stratejileri: Saldırgan,
Savunmacı (kalesi düşünce öfkelenir), Kaynak Odaklı (yağma), Zaman Manipülatörü (duvar üstünden faz atlar, sen yokken
hızlanır), Paradoks Yaratıcısı (senin yapılarını siler; köprün silinirse üstündeki kahraman akıntıya kapılır),
Teknoloji Uzmanı (uzaktan vurur, arayı açar, her 3 turda güçlenir).

Kahraman korunan hücrenin üstünde durursa düşmanlar içeri giremez. Akıncı/robot yolu kapalıysa kırılabilir çite yüklenir,
o da yoksa aynı çağdaki Zaman Muhafızı'nın peşine düşer.

## Geliştirme aşamaları ve durum

| Aşama | İçerik | Durum |
|---|---|---|
| 1 | Proje, izometrik harita, Geçmiş↔Şimdi, nedensellik motoru, 5 bölüm, başarı/başarısızlık, kayıt, canlar | **Tamamlandı** |
| 2 | Gelecek çağı, yapı/doğa evrimi (kadim orman), zaman enerjisi ve kristaller, robotlar, mühür bağlantıları, 20 yeni bölüm (toplam 25) | **Tamamlandı** |
| 3 | 9 birlik türü (can/saldırı/savunma/menzil/hareket/yetenek/çağ uyumu), nöbet, kuleler, 6 düşman stratejisi, zaman dondurma, zincir kombosu, önceden görme, Alan 6–7 (toplam 35 bölüm) | **Tamamlandı** |
| 4 | Zaman Kalesi (15 bina, çevrimdışı üretim, görsel büyüme), 5 kategorili 20 düğümlü araştırma ağacı, 10 kaynaklı ekonomi, 10 kategorili market (kozmetikler oyunda görünür), envanter, Zaman Rehberi | **Tamamlandı** |
| 5 | Prosedürel üretici (5 şablon, çözüm simülasyonla doğrulanır), Sonsuz Mod, Günlük Bulmaca, Haftalık Kriz, günlük/haftalık görevler, zaman seferleri, 150 eserlik koleksiyon + 6 salonlu müze, 30 başarım, alternatif tarih görevleri, Krallık Savunması, Yeni Zaman Döngüsü (prestij), rastgele zaman olayları, kişisel rekorlar | **Tamamlandı** |
| 6 | 100 alan / 500 bölüm, patronlar, dünya finalleri | Tasarım: docs/ALANLAR.md |
| 7 | Performans, ses/animasyon cilası, ekran uyumluluğu, Google Play hazırlığı | Planlandı |

Şu an olmayan özellikler arayüzde düğme olarak gösterilmez (sahte düğme yoktur).

### Zaman Kalesi, araştırma ve market (Aşama 4)

- **15 bina** (Zaman Çekirdeği, Laboratuvar, Depo, Akademi, Teknoloji Merkezi, Portal, Enerji Santrali, Müze, Keşif
  Merkezi, Ticaret Merkezi, Savunma Kulesi, Kristal Madeni, Arşiv, Atölye, Görev Merkezi), her biri 5 seviye; diğer
  binalar çekirdeğin seviyesini aşamaz. Üretim, canlarla aynı manipülasyona dayanıklı saatle çevrimdışı birikir ve
  depo sınırına kadar dolar.
- **Araştırma**: Zaman Bilimi, Medeniyet, Askerî, Teknoloji, Paradoks Bilimi (4'er kademe). Etkiler: önizleme hakları,
  Zaman Rehberi (1 kristale doğrulanmış çözümün sıradaki adımı), ödül/üretim çarpanları, savaşta +can, otomatik toplama…
  Hiçbiri bulmacayı kendiliğinden çözmez.
- **Market**: 10 kategori; kale/asker/portal/bina/harita/karakter görünümleri, dekorasyon, profil çerçevesi, zafer
  animasyonu oyunda gerçekten uygulanır; kaynak paketleri anında verir. Ödeme `PurchaseProvider` arayüzünden geçer
  (şimdilik oyun içi para; Google Play Billing ileride bu arayüzle eklenebilir). Para harcamadan ilerlemek mümkündür.

## Testler

`logic` modülündeki JUnit testleri: her bölümün referans çözümle kazanıldığı ve par'ı aştırmadığı, her adımda artımlı
nedensellik sonucunun sıfırdan hesapla birebir aynı olduğu, rastgele 1200 değişiklikte artımlı = tam hesap, yalnızca
yakın hücrelerin yeniden hesaplandığı, Gelecek'in Geçmiş'i etkilemediği, paradoks ve geri ödeme, aynı çağ bağlantıları
ile döngü ve geriye dönük bağlantı tespiti, kayıt gidiş-dönüş/yedekten kurtarma/sürüm 0 göçü, can yenilenmesi ve saat
hilesi senaryoları, ilerleme/ödül kuralları.
