# Yanlış Renk — Test Raporu

Tarih: 2026-11-19 · Ortam: Linux bulut konteyneri, OpenJDK 21, Gradle 8.14.3, Android SDK 35, Mesa (yazılımsal OpenGL) + Xvfb.

> **Önemli:** Testler gerçek bir Android cihazda ya da emülatörde **yapılmadı** (konteynerde KVM yok, emülatör çalışmaz, bağlı cihaz yok). Aşağıda her testin **nerede** çalıştığı açıkça yazılıdır.

## Özet

| Paket | Test | Geçti | Kaldı | Nerede |
|---|---|---|---|---|
| `logic` LevelTests | 11 | 11 | 0 | JVM (JUnit) |
| `logic` MetaTests | 22 | 22 | 0 | JVM (JUnit) |
| `server` TimeServerTest | 1 | 1 | 0 | JVM, gerçek HTTP soketi |
| `desktop` görsel QA | 36 ekran/durum | 36 | 0 | lwjgl3 + Mesa OpenGL (Xvfb), gerçek çizim |
| Android derleme | debug APK, release APK (R8), AAB | 3 | 0 | Android Gradle Plugin 8.7.3 |

Komutlar: `./gradlew :logic:test :server:test`, `xvfb-run ./gradlew :desktop:screenshots`, `./gradlew :android:assembleDebug :android:assembleRelease :android:bundleRelease`.

## İstenen 22 test

| # | Test | Durum | Nasıl doğrulandı |
|---|---|---|---|
| 1 | Android derleme | ✅ | `assembleDebug` başarılı; APK'da 4 ABI için `libgdx.so` + `libgdx-freetype.so`, 100 bölüm dosyası, yazı tipi, ses var |
| 2 | Renk değiştirme | ✅ | `renkDegistirme`: K→M→Y→S→K, ayna ters sıra, kilitli renk atlama, dokunuş sayacı |
| 3 | Normal kapı | ✅ | `normalKapi` |
| 4 | Ters kapı | ✅ | `tersKapi` |
| 5 | Karma kapı | ✅ | `karmaKapilar`: gökkuşağı, yasak, sıradaki, önceki, yankı, sahte, yanıltıcı (yazıya bak), zamanlı |
| 6 | Bütün alanların yüklenmesi | ✅ | `tumAlanlarYukleniyor`: 100 alan dosyası, 20 dünya, 100 benzersiz ad, bölüm numaraları ve final işaretleri |
| 7 | 500 bölümün çözülebilirliği | ✅ | `tum500BolumCozulebilir`: her bölüm doğrulayıcıdan geçer **ve doğrulayıcının dokunuş planı gerçek oyun simülasyonunda (`RunWorld`, 120 Hz) oynatılarak 3 yıldızla bitirilir**. Ek: `yanlisOyuncuKaybeder` — 500 bölümün hiçbiri dokunmadan geçilemez; `ustalikSurumleriCozulebilir`; `bolumlerIlerledikceZorlasir`; `dosyadakiBolumlerUreteceEsit` |
| 8 | Can eksiltme | ✅ | `canEksiltme`, `kilitliBolumBaslatilamaz` (0 canda bölüm açılmaz), `kalkanCaniKorur` |
| 9 | 15 dakikalık yenileme | ✅ | `onBesDakikalikYenileme`: 0/1/2/3 can → 60/45/30/15 dk, sıralı dolum, yeni kayıpta sayaç sıfırlanmaz |
| 10 | Saati ileri alma | ✅ | `saatIleriAlmaCanDoldurmaz`: duvar saati +24 saat → can dolmaz; gerçek 15 dk → 1 can |
| 11 | Saati geri alma | ✅ | `saatGeriAlmaCaniDurdurmaz`: duvar saati −5 saat → yenileme monoton saatle devam eder |
| 12 | Uygulama yeniden açma | ✅ | `uygulamaYenidenAcilinca` (kapalıyken 20 dk → 1 can + doğru kalan süre), `yarimKalanBolumBasarisizSayilir` |
| 13 | Cihaz yeniden başlatma | ✅ (simülasyon) | `cihazYenidenBaslatma`: açılış sayacı değişir; çevrimdışıyken yalnızca açılıştan beri geçen süre verilir, saat hilesi yok sayılır; sunucu zamanı gelince bekleyen 50 dk eklenir. **Gerçek cihazda yeniden başlatma denenmedi.** |
| 14 | Market satın alma | ✅ | `marketSatinAlma`: yetersiz bakiye, satın al, tekrar alınamaz, kuşan, sahip olmadan kuşanılamaz, seviye kilidi, satılık olmayan ürün |
| 15 | Para bakiyesi | ✅ | `paraBakiyesiNegatifOlmaz`: negatif bakiye yok, aynı ödül kimliği iki kez verilmez, defter kaydı |
| 16 | Karakter açma | ✅ | `karakterAcma`: 30 karakter, elmasla alma, 500. bölüm başarısıyla Kral Küp açılması |
| 17 | Görev ödülü | ✅ | `gorevOdulu`: 3 günlük + 5 haftalık görev, tamamlanmadan alınamaz, iki kez alınamaz |
| 18 | Kayıt sistemi | ✅ | `kayitSistemi` (kaydet/yükle, elle değiştirilen dosya → yedekten geri yükleme, ikisi bozuk → çökmeden temiz başlangıç), `eskiKayitGocu` (v1→v2) |
| 19 | Sonsuz mod | ✅ | `sonsuzMod`: 30 parça her başlangıç rengi için doğrulanır, hız ≥1,8× artar, can harcanmaz, haftalık ödül basamakları; `sonsuzModKombo` |
| 20 | Menü geçişleri | ✅ (masaüstü GL) | Görsel QA çalıştırıcısı 25 ekranın tamamını + oyun içi durumları gerçek OpenGL ile açtı, her birini ~45 kare çalıştırdı: **0 hata**. Görüntüler: `docs/screenshots/` |
| 21 | Düşük performans senaryosu | ✅ (simülasyon) | `dusukPerformansSimulasyonu`: 15/30/144 FPS kare süreleriyle aynı bölümler aynı sonucu verir (sabit adımlı simülasyon). Masaüstü QA'da ortalama kare süresi yazılımsal GL'de ~1–2,5 ms. **Düşük donanımlı gerçek Android cihazda ölçüm yapılmadı.** |
| 22 | APK/AAB derleme | ✅ | `assembleRelease` (R8 küçültme açık) + `bundleRelease` başarılı; R8 sonrası kotlinx.serialization sınıflarının korunduğu `dexdump` ile doğrulandı; `apksigner verify` geçti |

## Bilinen sınırlamalar

* Gerçek cihaz/emülatör testi yok. Dokunmatik giriş, titreşim, Android yaşam döngüsü (arka plana alma), ses gecikmesi ve farklı ekran oranları cihazda doğrulanmalıdır.
* Ses dosyaları sayısal olarak doğrulandı (sessiz değil, kırpılma yok, döngü noktası tıklamasız) ancak **dinlenmedi**.
* Ekran görüntüleri 540×1080 masaüstü penceresindendir; çok uzun (20:9) ve tablet oranları `ExtendViewport` ile desteklenir ama görsel olarak cihazda denenmedi.
