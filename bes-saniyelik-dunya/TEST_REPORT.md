# Test Raporu — 5 Saniyelik Dünya 1.0.0

Tarih: 2026-11-19 · Ortam: Linux bulut konteyneri, OpenJDK 21, Gradle 8.14.3, Android SDK 35, libGDX 1.13.1.
Bütün sonuçlar bu ortamda **gerçekten çalıştırılmıştır**.

## Özet

| # | Kontrol | Sonuç | Nasıl |
|---|---|---|---|
| 1 | Projenin derlenmesi | ✅ | `./gradlew :core:compileKotlin :desktop:compileKotlin` |
| 2 | Oyunun açılması | ✅ | Masaüstü sürümü Xvfb + Mesa (gerçek OpenGL) altında açıldı, tur hatasız bitti |
| 3 | Karakter kontrolleri | ✅ | `SimulationTest`: otomatik koşu, dokun/basılı tut zıplama, coyote time, zıplama tamponu, çift zıplama |
| 4 | Box2D çarpışmaları | ✅ | `SimulationTest`: platforma iniş, duvara takılma, diken/testere, boşluğa düşme |
| 5 | On dünya mekaniği | ✅ | Her mekanik için ayrı test (ters yer çekimi, hızlı, buz, hayalet, zıplayan, küçük + dar geçitte ertelenen büyüme, ters kontrol, yavaş, değişen platformlar) |
| 6 | Mekanik kombinasyonları | ✅ | Üçlü kombinasyon testi + uyumluluk matrisi/öncelik çözümü (10×10×10 bütün kombinasyonlar) |
| 7 | Beş saniyelik geçişler | ✅ | Değişim zamanı ±20 tick, hızlı dünyada 5 gerçek saniye, 1,2 sn önceden uyarı |
| 8 | Bölüm yükleme | ✅ | 10 harita dosyası, 500 bölüm, veri tutarlılığı, benzersizlik |
| 9 | Bölüm tamamlama | ✅ | Gerçek oyun ayarlarıyla 7 bölüm + arayüzde 10 bölüm baştan sona |
| 10 | 500 bölümün çözülebilirliği | ✅ | (a) kayıtlı çözümle 500/500 baştan oynatma, 3/3 yıldız ve gizli parçalar alındı; (b) çözücüyle **sıfırdan** 500/500 çözüldü |
| 11 | Sonsuz mod engel üretimi | ✅ | 5 tohumda çözücü 90 sn hayatta kaldı; üretim deterministik; kurallar her aşamada değişiyor; hız artıyor |
| 12 | Skor hesaplama | ✅ | Formül, yıldız kuralları, imkânsız puanların reddi |
| 13 | XP ve seviye sistemi | ✅ | Seviye atlama, 5'te bir özel ödül, 10'da bir kozmetik |
| 14 | Görev sistemi | ✅ | 3 günlük / 5 haftalık görev, tarih tohumu, ilerleme, ödül, tekrar alma engeli, gün/hafta yenilenmesi |
| 15 | Ödüller | ✅ | 7 günlük giriş döngüsü, gün kaçırınca sıfırlanmama, koleksiyon ödülü, başarı elmasları |
| 16 | Mağaza | ✅ | Yetersiz bakiye, satın alma, sahip olunanı tekrar alamama, güçlendirme envanteri, altın paketi |
| 17 | Karakter kilitleri | ✅ | Seviye/koleksiyon/yıldız kilitleri satın alınamaz, oynayarak açılır; 20 benzersiz karakter |
| 18 | Kayıt ve yükleme | ✅ | Kalıcılık, bozuk ana kayıtta yedekten kurtarma, elle değiştirilmiş kaydın reddi, sürüm göçü |
| 19 | Menü geçişleri | ✅ | Masaüstü turu: 14 menü ekranı + oyun + sonsuz mod sırayla açıldı, ekran görüntüleri alındı |
| 20 | Android derleme | ✅ | Debug APK, R8 küçültmeli release APK ve AAB üretildi; APK içeriği (4 ABI için libgdx/box2d/freetype .so, varlıklar) doğrulandı |

**Otomatik birim/entegrasyon testleri: 39/39 geçti** (`./gradlew :core:test`, ~30 sn).

| Test sınıfı | Test | Başarısız |
|---|---|---|
| SimulationTest | 19 | 0 |
| LevelsTest | 9 | 0 |
| ProgressTest | 11 | 0 |

## Bölüm üretimi ve doğrulama

`./gradlew :core:generateLevels` → 500 bölüm 148 sn'de üretildi (100 alan × 5 bölüm) ve doğrulandı (rapor: `docs/level_report.tsv`).

* Ortalama bölüm süresi 45 sn (en uzun: büyük final 105 sn), ortalama 89 altın.
* Her bölüm: katı modda (engel isabet kutuları büyütülmüş) çözüldü → normal modda baştan oynatıldı → yıldız/altın/parça yerleştirildi → yeniden doğrulandı.
* Zamanlama toleransı (bütün girişler ±2 tick, meydan okuma/finallerde ±1 tick kaydırıldığında hâlâ geçilebilir): **488/500**. Şu 12 bölüm çözülebilir ama zamanlama penceresi daha dardır: 8, 9, 92, 131, 141, 231, 313, 339, 349, 421, 451, 454.

## Arayüzde uçtan uca oynatma (masaüstü)

`xvfb-run ./gradlew :desktop:run -Dshots=<klasör>` — gerçek `GameScreen` üzerinde kayıtlı çözüm dokunuş girişi olarak oynatıldı:

```
Bölüm 1:   TAMAMLANDI (yıldız 3/3, altın 83, ölüm 0)
Bölüm 23:  TAMAMLANDI (yıldız 3/3, altın 85, ölüm 0)
Bölüm 61:  TAMAMLANDI (yıldız 3/3, altın 101, ölüm 0)
Bölüm 160: TAMAMLANDI (yıldız 3/3, altın 93, ölüm 0)
Bölüm 215: TAMAMLANDI (yıldız 3/3, altın 32, ölüm 0)
Bölüm 280: TAMAMLANDI (yıldız 3/3, altın 107, ölüm 0)
Bölüm 330: TAMAMLANDI (yıldız 3/3, altın 91, ölüm 0)
Bölüm 377: TAMAMLANDI (yıldız 3/3, altın 94, ölüm 0)
Bölüm 420: TAMAMLANDI (yıldız 3/3, altın 122, ölüm 0)
Bölüm 500: TAMAMLANDI (yıldız 3/3, altın 275, ölüm 0)
```

Her haritadan bir bölüm; tamamlanma ekranı, XP, başarı ve seviye bildirimleri görsel olarak kontrol edildi (`docs/screenshots/`).

## Görüntü kalitesi, bölüm haritası, ses

* Kalite: karakter kareleri 200 piksel (telefonda küçültülerek keskin çizilir), arka planlar kaynak çözünürlüğünde 2048×1024 (önce 512 yükseklikti), gökyüzü kaynak genişliğinde 16:9 bant (önce 540 genişlikte, 3,5 kat büyütülüyordu). Bazı paralaks katmanlarında kalan gökyüzü artıklarının oluşturduğu düz kenarlı bantlar temizlendi. Bellek için büyük karakter atlaslarından en fazla 3'ü tutulur; listelerde hafif önizleme atlası kullanılır.
* Bölüm haritası: masaüstünde kontrol edildi; bölüm düğmeleri açılıyor, şu anki bölüme otomatik kaydırılıyor.
* Altın sesi: hacim 0,45 → 0,16 ve kare dalga yerine yumuşak çan tınısı. Açılıştaki "sunar" yazısı kaldırıldı.
* APK 52 MB (yüksek çözünürlüklü görseller nedeniyle). Testler 39/39.

## Yatay ekrana geçiş

Oyun yalnızca yatay olarak çalışır (manifestte `sensorLandscape`, APK'da doğrulandı). Arayüz 1280×720 sanal çözünürlüğe, oyun kamerası 21×11,8 karoya göre yeniden düzenlendi; 15 menü ekranı, oyun içi göstergeler, diyaloglar ve açılış animasyonu masaüstünde 960×540 pencerede kontrol edildi. Bölüm 1, 23, 61, 160, 215, 330, 420 ve 470 yatay ekranda arayüz üzerinden baştan sona oynatıldı (hepsi 3/3 yıldız, 0 ölüm). Testler 39/39.

## Kullanıcı görsellerinin entegrasyonu

220 kullanıcı görseli otomatik kontrol edildi (boyut, şeffaflık, kenar dikişi). Bozuk gelen 34 dosya yeniden üretildi ve kullanıldı; 10 tavan karosu yerine zeminin çevrilmiş hali kullanıldı. Altın animasyonu ve güçlendirme baloncuğu yarı saydam geldiği için programatik çizimler korundu. Masaüstü turunda her haritadan bir bölüm yeni görsellerle baştan sona oynatıldı (hepsi 3/3 yıldız, 0 ölüm); testler 39/39 geçti. APK 32 MB.

## Açılış animasyonu

Masaüstünde (Xvfb + OpenGL) açılıştan oyunun splash ekranına kesintisiz geçiş doğrulandı; kare kare kayıt `docs/intro_animasyon.gif`. Gerilen beyaz bölgenin kenar yumuşaması nedeniyle oluşan açık renkli çerçeve ve portre ekranda devre katmanının boş kalması düzeltildi.

## Bulunan ve düzeltilen hatalar

* (Geri bildirim) Kural değişim yazıları ekranda uzun kalıp oyunu zorlaştırıyordu → bildirim küçük bir etikete dönüştürüldü, ekranın üstünde (HUD altında) 0,55–0,9 sn kalıyor; ölüm yazısı 0,5 sn, ipuçları 1,8 sn, oyun içi bildirimler 1 sn.
* (Geri bildirim) Alanlar 10 değil 5 bölüm olmalı → 100 alan, her alanın son bölümü meydan okuma; testle doğrulanıyor.

* Çözücü geri yüklemesi deterministik değildi (Box2D temas geçmişi) → geri yüklemede oyuncu gövdesi yeniden kuruluyor; hareketli platformlar tam hedef konumuna oturtuluyor.
* Yüksekten düşerken yanlış "ezildin" ölümü → ezilme yalnızca 5 tick süren derin gömülmede.
* Bölüm 69'da yalnızca 9 altın → az altınlı bölümler için ikinci yerleştirme geçişi (en az 24 altın).
* Gökyüzü şeridinde kenar filtreleme bantları, beyaz buton üstünde beyaz simgeler, uzun başlık taşması, ayar satırı çakışması → düzeltildi.
* R8 kural dosyasında sözdizimi hatası → düzeltildi.

## Yapılamayanlar (açıkça)

* **Gerçek Android cihaz veya emülatör testi yapılamadı**: bu konteynerde cihaz bağlı değil ve `/dev/kvm` yok. APK yapısal olarak doğrulandı (paket adı, etiket, başlatıcı aktivite, 4 ABI yerel kütüphaneleri, varlıklar); küçültme riskini azaltmak için oyun ve libGDX sınıfları R8'de korunuyor. Kurulumdan sonra ilk açılış, dokunma gecikmesi, ses ve 60 FPS performansının gerçek cihazda kontrol edilmesi önerilir.
* Düşük/orta segment cihaz performans ölçümü yapılamadı (aynı sebep). Masaüstü yazılım GPU'sunda (llvmpipe) oyun akıcı çalıştı.
* Release APK/AAB **hata ayıklama anahtarıyla** imzalı: kurulup denenebilir, Play Console'a yüklemek için kendi yükleme anahtarınızla yeniden derlenmeli (`keystore.properties`).
