# FINAL RELEASE REPORT — Prismway (Işık Yolu) 1.8.0

**Geliştirici:** Magic Lumos Software
**Rapor tarihi:** 28 Kasım 2026
**Sürüm:** 1.8.0 (versionCode 12)
**Paket adı:** `com.isikyolu.game` (değişmedi)
**Dal:** `ccr-015fd7c3-q10ggh`

Durum etiketleri:

| Etiket | Anlamı |
|---|---|
| **TAMAMLANDI** | Yapıldı |
| **TEST EDİLDİ** | Yapıldı ve otomatik testle ya da ölçümle doğrulandı |
| **EKSİK** | Yapılmadı |
| **ERİŞİM GEREKLİ** | Senin hesabın, anahtarın ya da bu ortamda olmayan bir erişim gerekiyor |
| **UYGULANAMAZ** | Oyunda karşılığı yok |
| **MANUEL KONTROL GEREKLİ** | Gerçek cihazda ya da Play Console'da senin bakman gerekiyor |

---

## 1. Özet

| Konu | Sonuç | Durum |
|---|---|---|
| Eski Türkçe ad | **Işık Yolu** (korundu) | TAMAMLANDI |
| Yeni İngilizce ad | **Prismway** (31 aday arasından, 81/100) | TAMAMLANDI (geçici: marka tescili için hukuki kontrol gerekir) |
| Can sistemi | 4 can, 1 can / 20 dk (1200 sn, `GameConfig`), saat hilesi koruması, imzalı kayıt, yeni arayüz | TEST EDİLDİ (22 motor testi + arayüz akışı) |
| Diller | tr, en, de, it, fr, es. Otomatik algılama, Ayarlar'dan değiştirme ("Telefonun dili" seçeneği dahil) | TEST EDİLDİ |
| Eksik çeviri sayısı | **0** (her dilde 464 anahtar) | TEST EDİLDİ |
| Görseller | 48 telefon + 60 tablet ekran görüntüsü, 2 öne çıkan görsel, 1 simge, 18 logo, 16 sosyal medya görseli, 4 video kapağı | TEST EDİLDİ (`Media_QA.md`) |
| Videolar | 8 fragman (TR/EN × dikey, yatay, 2 sosyal CTA), 2 altyazı, 18 kaynak klip | TEST EDİLDİ |
| Mağaza metinleri | 6 dil, sınırlar içinde | TEST EDİLDİ |
| Testler | 155 test, 0 başarısız (4'ü kayıt modunda ayrıca çalıştırıldı) | TEST EDİLDİ |
| APK | 2,86 MB, test imzalı | TEST EDİLDİ |
| AAB | 5,18 MB, **debug imzalı** | ERİŞİM GEREKLİ (yükleme anahtarı) |
| Google Play'e hazırlık | Teknik gereksinimler karşılandı; Console formları, anahtar ve URL'ler sende | MANUEL KONTROL GEREKLİ |

---

## 2. İsim araştırması (§4)

| Madde | Durum |
|---|---|
| ≥ 20 aday: **31 aday** (25 ilk tur + 6 doğrudan aramadan sonra) | TAMAMLANDI |
| 100 üzerinden puanlama (7 ölçüt) ve ilk 5 | TAMAMLANDI |
| Google Play (ABD+TR), App Store (ABD), Steam, USPTO: doğrudan sorgu, 31 aday | TEST EDİLDİ |
| Google/Bing/YouTube/Instagram/TikTok: yalnızca arama motoru dizini üzerinden; doğrudan erişim ağ izniyle kapalı | kısmen; ERİŞİM GEREKLİ |
| EUIPO/TMview (güvenlik duvarı reddetti), WIPO Brand DB (JavaScript/bot koruması), TÜRKPATENT (oturum/doğrulama) | ERİŞİM GEREKLİ |

**Araştırma tarihi:** 27–28 Kasım 2026.

**İlk 5:**
1. **Prismway** — 81
2. Beam Route — 73
3. Lux Trail — 72
4. Glimway — 72
5. Lumiway — 71

**Seçilen ad: Prismway.**
- **Gerekçe:** Prizma oyunun imza mekaniği, "way" ise "Yolu"nun karşılığı. Tek kelime, 8 harf, 6 dilde kolay okunur.
- **Doğrudan sorgular:** Play, App Store, Steam ve USPTO'da "Prismway" ve "Prism Way" için birebir oyun, uygulama
  ya da kelime markası bulunmadı.
- **Risk:** ABD'de canlı "PRISM: LIGHT THE WAY" markası var (Eidos, 2007 ışık bulmacası, sınıf 9/41).
  Düşük–orta risk olarak değerlendirildi. **Tescilden önce marka vekili görüşü al.**
- **Ayrıntı:** `02_Game_Name_Research/`.

**Uygulanan yerler:**

| Yer | Durum |
|---|---|
| Menü ve açılış/yükleme ekranı: Türkçede "Işık Yolu" büyük + "PRISMWAY" küçük; diğer dillerde "Prismway" büyük + "IŞIK YOLU" küçük | TEST EDİLDİ |
| Uygulama adı: Türkçe telefonda "Işık Yolu", diğerlerinde "Prismway" | TEST EDİLDİ (aapt2) |
| Logolar, mağaza metinleri, videolar, görseller, sosyal medya | TAMAMLANDI |
| Paket adı, applicationId, imza ve kayıt anahtarları | değiştirilmedi |

---

## 3. Yazılım değişiklikleri

Yaklaşık 54 kaynak dosyası, +5.900 / −350 satır, 15 commit.

**Can sistemi**
- `engine/GameConfig.java` (yeni):
  - `LIVES_MAX = 4`
  - `LIFE_REGEN_SECONDS = 1200`
  - `CLOCK_TOLERANCE_SECONDS = 120`
  - hamle hakkı katsayıları
- `Lives`: değerler merkezi ayardan gelir; can asla negatif olmaz; yenilenme sayacı (`regenGained`) var.
- `Progress` ve `Game`: kayıt düz JSON kalır (eski sürümler de okuyabilir); SHA-256 imzası ayrı anahtarda
  (`progress_sig`), `sv` sürüm alanı var.
  - Değiştirilmiş kayıtta can ve oyun parası güvenli değerlere döner; bölüm ilerlemesi, yıldızlar ve görünümler
    korunur.
  - Eski (1.7.1) kayıt cezasız taşınır.
- `LivesBadge`: "3/4 · 19:42" biçimi (dd:ss), "Dolu", can artınca kalp atışı ve gerçek artış ("+1", "+4"), ekran okuyucu metni.
- `MainActivity`: canlar zamanla dolunca bir kez bildirim. Kayıt değiştirildiyse bir kez uyarı.

**Yerelleştirme**
- `app/i18n/L.java` (yeni):
  - JSON dil dosyaları, `{0}` yer tutucuları, çoğul biçimleri (`one`/`other`, Fransızca kuralı dahil),
    dile göre sayı biçimi.
  - Dil seçim sırası: oyuncunun seçimi → Android 13+ uygulama dili → telefon dili → İngilizce.
- `assets/i18n/{tr,en,de,it,fr,es}.json`: her dilde 464 anahtar.
- Arayüzdeki sabit Türkçe metinler (~450) anahtarlara taşındı.
- Motordaki Türkçe cümleler (satın alma engeli, ödül satırları) koda dönüştü.
  - `EconomyTexts` ve `WorldText` bu kodları ve anahtarları metne çevirir.
- Ayarlar'da dil seçici: oyun yeniden başlamadan değişir, kalıcıdır, ilerlemeye dokunmaz.
- Android 13+ "Uygulama dili" listesi (`locales_config.xml`).
- APK'ya yalnızca 6 oyun dili alınır.
- Uzun diller için arayüz:
  - Düğmeler en fazla iki satır ve heceleme açık.
  - `Ui.shrinkToFit` tek satırlık başlıkları küçültür.
  - Oyun ekranında can rozeti sıkışmaz.
  - Market video yuvası sabit yükseklikte.

**Marka**
- `Brand` ve `BrandTitle` (iki dilli başlık).
- `values/strings.xml` "Prismway", `values-tr` "Işık Yolu".

**Diğer**
- Müzik WAV → OGG Vorbis.
- Android 12+ yedekleme kuralları (`data_extraction_rules.xml`).
- İsteğe bağlı yükleme anahtarı ile imzalama (`uploadStoreFile` …; anahtar depoya konmaz).
- Sürüm 1.8.0 (12).

**Testler (yeni):**
- `LivesTest` (11 yeni senaryo)
- `I18nTest`
- `LanguageDetectionTest`
- `LocalizedUiTest`
- `EconomyTextsTest`
- `WorldKeysTest`
- `LightColorTest`
- `UiFlowTest.languageSwitch`
- `livesFlow` genişletildi
- Kayıt testleri: `PromoCaptureTest` (dil seçimi), `TabletCaptureTest`

---

## 4. Can sistemi ve güvenlik testleri (§2)

| Kural | Durum |
|---|---|
| 20 dk'da 1 can; 1200 sn merkezi ayarda | TEST EDİLDİ |
| Maksimum 4 (mevcut değer korundu). 0 → 20/40/60/80 dk'da 1/2/3/4 | TEST EDİLDİ |
| Dolu iken sayaç durur; kayıpta başlar; yeni kayıp sayacı sıfırlamaz | TEST EDİLDİ |
| Oyun kapalıyken süre sayılır; internetsiz çalışır; yeniden başlatmada kaybolmaz | TEST EDİLDİ |
| Negatif yok; ödüller maksimumu aşmaz | TEST EDİLDİ |
| Güncellemede sıfırlanmaz (eski kayıt taşınır) | TEST EDİLDİ |
| Saat ileri/geri, tarih, saat dilimi, aç-kapa, zorla durdurma, yeniden başlatma, internetsiz, kayıt değiştirme | TEST EDİLDİ |
| Arayüz: mevcut/maks, dd:ss geri sayım, yenilenme animasyonu, "canlar doldu" bildirimi | TEST EDİLDİ |
| Can bitti penceresi: video ile can (reklam varsa), kristalle can, market, günlük bulmaca | TEST EDİLDİ |
| Can metinleri 6 dilde | TEST EDİLDİ |
| Oyun kapalıyken sistem bildirimi (push) | EKSİK (bilinçli). Android 13+ bildirim izni ve zamanlayıcı gerektirir; oyunda bildirim izni yok. Bildirim oyun açılınca gösterilir. İstersen eklenebilir. |

**Bilinen sınır:** Tamamen çevrimdışı oyunda kusursuz koruma yoktur.
- Aynı açılış içinde saat hilesi işe yaramaz.
- Yeniden başlatmadan sonra, ağ saati yoksa, ileri alınmış saat süre kazandırabilir. Geri almak kazandırmaz.
- Kayıt imzası caydırıcıdır: root erişimiyle atlatılabilir.
- Ayrıntı: `08_Test_Reports/Test_Report.md`.

---

## 5. Diller (§3)

| Madde | Durum |
|---|---|
| 6 dil, yerel adlarıyla (Türkçe, English, Deutsch, Italiano, Français, Español) | TEST EDİLDİ |
| İlk açılışta sistem dili; desteklenmeyen dilde İngilizce | TEST EDİLDİ |
| Ayarlar'dan değiştirme, yeniden başlatmasız, kalıcı, ilerlemeye etkisiz | TEST EDİLDİ |
| Tüm arayüz metinleri yerelleştirildi; kodda sabit Türkçe metin yok | TEST EDİLDİ (`I18nTest`) |
| Yerelleştirilen alanlar: açılış, menü, ayarlar, bölüm seçimi, oyun içi, öğretici, kurallar, başarılar, dünya ve bölüm adları, market, canlar, günlük ödüller, reklam bildirimleri, kazanma/kaybetme, duraklatma, hatalar, onaylar, istatistikler, bildirimler, gizlilik | TAMAMLANDI |
| Çeviri kalitesi: her dil için ayrı çevirmen + anadil editörü (de 14, it 22, fr 17, es 13 düzeltme) | TAMAMLANDI |
| Taşma: 6 dilde 13 ekranda taşma yok | TEST EDİLDİ (`LocalizedUiTest`) |
| Özel karakterler (ı, ş, ğ, ä, ß, é, ç, ñ, ¡¿, «») | TEST EDİLDİ |
| Tekil/çoğul | TEST EDİLDİ |
| Merkezi yönetici (`L`), dil dosyaları `assets/i18n/<dil>.json` | TAMAMLANDI |
| Eksik anahtar denetimi (`I18nTest`, `tools/i18n_check.py`); yeni dil eklemek için `L.SUPPORTED` + JSON | TAMAMLANDI |
| Karakter, görev, sonsuz mod gibi alanlar | UYGULANAMAZ (oyunda yok) |

---

## 6. Görseller ve videolar (§5, §6)

| Çıktı | Adet / özellik | Durum |
|---|---|---|
| `app_icon_512.png` | 512×512, 32 bit, 9 KB; uyarlanabilir simge (arka plan + ön yüz + tek renkli) kontrol edildi | TEST EDİLDİ |
| `feature_graphic_tr.png`, `feature_graphic_en.png` | 1024×500, alfasız | TEST EDİLDİ |
| Telefon ekran görüntüleri | 6 dil × 8 = **48**, 1080×1920; ilk 3'ü oynanış; arayüz ilgili dilde | TEST EDİLDİ |
| Tablet 7" / 10" | 6 dil × 5 × 2 = **60**; 1200×1920 / 1600×2560; gerçek arayüz çizimi (fiziksel tablet değil, ama yapay değil) | TEST EDİLDİ |
| Logolar | 18: TR, EN, iki dilli (yatay + dikey); koyu, açık, şeffaf; yazısız amblem | TAMAMLANDI |
| `trailer_tr_vertical.mp4`, `trailer_en_vertical.mp4` | 30,00 sn, 1080×1920, H.264 High, AAC, 30 FPS; senaryo 0–3/3–8/8–14/14–21/21–27/27–30; ~%89 oynanış | TEST EDİLDİ |
| Sosyal sürümler (CTA) | TR: "Hemen Oyna!", "Maceraya Başla!"; EN: "Play Now!", "Start Your Adventure!" (4 video) | TAMAMLANDI (yayından önce paylaşma) |
| Yatay TR/EN | 1920×1080, 30 sn | TEST EDİLDİ |
| Altyazı TR/EN | SRT, çakışmasız | TEST EDİLDİ |
| YouTube ve Reels kapakları | 1280×720, 1080×1920 (TR/EN) | TEST EDİLDİ |
| Düzenlenebilir kaynaklar | zaman çizelgesi JSON, 18 ham klip, yeniden üretim betikleri | TAMAMLANDI |
| Sosyal medya paketi | TR + EN × 8 biçim = **16** | TEST EDİLDİ |
| ZIP arşivleri | 13 adet, en büyüğü 10,4 MB | TEST EDİLDİ |
| 60 FPS sürüm | 30 FPS yeterli (istenen "30 veya 60") | UYGULANAMAZ |

**Müzik/ses:** oyunun kendi sentezlenmiş sesleri; lisans engeli yok.

---

## 7. Mağaza metinleri ve ASO (§7)

| Madde | Durum |
|---|---|
| 6 dilde: başlık, kısa/uzun açıklama, slogan, özellikler, oynanış, hedef kitle, anahtar kelimeler, kategori, etiketler, ilk sürüm notları, video başlığı, YouTube açıklaması, 3 sosyal medya metni, 8 görsel alt metni + öne çıkan görsel alt metni | TAMAMLANDI |
| Karakter sınırları: başlık ≤ 30 (hepsi 20), kısa ≤ 80 (en uzun 80, fr), uzun ≤ 4000 (en uzun 1.709) | TEST EDİLDİ (`store_texts.py`) |
| Politika: "en iyi / #1 / ücretsiz / yeni" yok (başlık, kısa açıklama, görsel ve video yazıları), başlıkta emoji yok, rakip adı yok, olmayan özellik yok | TEST EDİLDİ |

Mağaza başlığı: TR "Işık Yolu – Prismway", diğer diller "Prismway – Işık Yolu". İki ad birlikte 30 karaktere
sığdığı için ikisi de kullanıldı. Her dil için ASO'ya yönelik alternatif başlık da var
(ör. "Prismway: Light Beam Puzzle").

---

## 8. Düzeltilen hatalar

1. Gizlilik metinlerinde "PG" reklam derecesi "genel izleyici" diye yanlış açıklanmıştı. 6 dilde "ebeveyn
   rehberliği" olarak düzeltildi.
2. Market rehberindeki "Dünya başarıları +1 Prizma Taşı" bilgisi 1. dünya için yanlıştı.
3. Uzun dillerde market video düğmesi iki satıra çıkınca liste kayıyordu: yanlış satın alma riski.
4. İspanyolcada can rozeti sıkışıyordu; İtalyancada oyun alt başlığı kesiliyordu.
5. Menü ve açılış sloganı ortalı değildi.
6. Satın alma engeli mesajları her dilde Türkçe çıkacaktı. Motor artık kod döndürüyor.
7. Kayıt dosyası elle değiştirilerek can kazanılabiliyordu.
8. Ekran okuyucu, ses/müzik/titreşim anahtarlarını adsız okuyordu.
9. Tanıtım ve tablet kayıtlarında kazanma penceresi erken çıkıyordu (test ortamı saat sıçraması). Kayıt zamanlaması
   düzeltildi.
10. Can rozeti animasyonu kare tabanlı hâle getirildi.

**Bağımsız son kalite denetiminde bulunup düzeltilenler.** 4 inceleyici kullanıldı: kod, çeviri, paket, politika.
Doğrulayıcı ajanlar kullanım sınırına takıldığı için her bulgu tek tek elle doğrulandı.

11. **Kayıt biçimi eski sürüme geri dönüşte ilerlemeyi siliyordu.**
    - Kayıt artık düz JSON; eski ayrıştırıcı okuyabiliyor (`newSaveReadableByOldParser`).
    - İmza ayrı anahtarda (`progress_sig`).
12. **Kayıt elle değiştirilince değiştirilmiş kristal ve Prizma Taşı korunuyordu.**
    - Artık can ve oyun parası güvenli değerlere döner.
    - Bölüm ilerlemesi, yıldızlar ve görünümler korunur.
13. **Android 13+'ta sistem ayarlarından "varsayılan dil" seçimi yok sayılıyordu.**
    - Düzeltildi.
    - Dil listesine "Telefonun dili (otomatik)" seçeneği eklendi.
14. **Sistemden dil değişince market, günlük bulmaca ve başarılar ekranları karışık dilde kalıyordu.**
    - Artık yeniden kuruluyorlar.
    - Oyun ekranındaki bölüm durumu korunur; ekran sonraki açılışta yeni dille kurulur.
15. **Can rozeti her artışta "+1" gösteriyordu.**
    - Artık gerçek artışı gösteriyor ("+4" gibi).
16. **"İlerlemeyi Sıfırla" uyarısı silinenlerin çoğunu söylemiyordu.**
    - Uyarı artık kristal, Prizma Taşı, güçlendirici ve görünümleri de sayıyor.
    - Türkçe metin ters anlam veriyordu ("bölüm kilitlerin").
17. **Türkçe ek hatası ("Bölüm 20'u bitir").**
    - "Önce 20. bölümü bitir" olarak düzeltildi.
    - Diğer Türkçe düzeltmeler: "Baştan başlansın mı?", "Canların zaten dolu", siz → sen, "1.500".
18. **Gizlilik metinleri iki noktada eksikti.**
    - "Yalnızca cihazda" deniyordu ama Android yedeklemesinden söz edilmiyordu.
    - "Reklam Gizlilik Seçenekleri" düğmesinin yalnızca izin gereken bölgelerde göründüğü belirtilmemişti.
    - Sıfırlamanın kapsamı yanlıştı.
    - 6 dilde, hem politikada hem oyun içi metinde düzeltildi.
19. **Mağaza metinleri oyundaki terimle tutarsızdı.**
    - "Taş/stones" yerine oyundaki "blok" terimi kullanıldı (EN/DE/IT/ES).
    - "Bölüme başlamak can harcar" yerine doğru kural yazıldı: "ilk hamle can harcar".
    - Hedef kitle "her yaş" yerine "gençler ve yetişkinler" oldu (13+ hedefiyle tutarlı).
    - 1. görselin alternatif metni görüntüyle eşleşmiyordu; düzeltildi.
20. **Görsel ve video yazılarında "yeni/new" geçiyordu** (Play'in tanıtım terimleri listesinde).
    - "Her gün bir bulmaca", "Her bölüm farklı bir bulmaca" olarak değiştirildi.
    - `store_texts.py` artık görsel ve video yazılarını da tarıyor.
21. **Tablet "bölümler" ve "günlük" görüntülerinde gecikmeli açılan "Tamamlandı" penceresi kalmıştı.**
    - Kayıt artık pencerenin açılmasını bekleyip kapatıyor ve pencere kalmadığını doğruluyor.
22. **Lansman afişinde CTA düğmesi sloganın üstüne biniyordu.**
    - Öğeler üst üste binmeyecek şekilde dizildi.
    - CTA'lı sosyal fragmanların kapanışına "Magic Lumos Software" eklendi.
23. **Sosyal medya görsellerinde tahta, yıldız satırının ortasından kesiliyordu.** Kırpma tahta sınırlarına göre
    düzeltildi.
24. **İngilizce/Almanca bölüm grubu adı konu adıyla aynıydı** ("Moving Blocks · Moving blocks"). Grup adı
    "Shifting Stones" / "Wandernde Steine" yapıldı.
25. **Altyazılar kapanış kartını kapsamıyordu.** Ad ve slogan satırı eklendi.
26. **Veri güvenliği formu ve kontrol listesi eksikti.**
    - Cihaz kimlikleri için "zorunlu" yazıldı.
    - Reklam kimliği amacına dolandırıcılığı önleme eklendi.
    - Android yedeklemesi açıklandı.
    - Kontrol listesine AdMob GDPR mesajı ve app-ads.txt eklendi.
27. **Yükleme anahtarıyla derlenen bir sürüm gerçek AdMob kimlikleri olmadan test reklamlarıyla çıkabiliyordu.**
    Gradle artık bunu durduruyor (bilerek denemek için `-PallowTestAds`).
28. **Düzenlenebilir kaynaklar eksikti.** Yazı tipleri, betikler ve amblemin SVG'si eklendi.

Bilinçli olarak değiştirilmeyenler:
- **Mağaza simgesi:** uygulamanın kendi uyarlanabilir simgesiyle birebir aynı tutuldu. Simgenin ışın halesi oyundaki
  tasarımla aynı.
- **Gizlilik politikasındaki iletişim e-postası:** yer tutucu olarak kaldı. Senin e-posta adresini bilmiyorum;
  ERİŞİM GEREKLİ.

---

## 9. Performans (§8.3)

| Madde | Durum |
|---|---|
| APK 4,41 MB → **2,86 MB** (−%35): müzik OGG (1,41 MB → 69 KB), yalnızca 6 dil kaynağı | TEST EDİLDİ |
| AAB 5,18 MB | TEST EDİLDİ |
| 500 bölüm ayrıştırma 12,4 ms (masaüstü JVM, açılışta arka planda); hamle başına ışın izleme 1,7 µs; yığın ~4,3 MB | TEST EDİLDİ (gösterge) |
| Çizim döngüsünde kare başına nesne üretimi yok (oyun tahtası) | TAMAMLANDI |
| FPS, RAM, CPU, pil, ısınma, açılış süresi, düşük donanım | MANUEL KONTROL GEREKLİ (cihaz/emülatör yok) |

---

## 10. APK / AAB (§10)

| Çıktı | Durum |
|---|---|
| `01_Game_Build/APK/Prismway-IsikYolu-1.8.0-test.apk`: imza doğrulandı, 16 KB hizalı, hedef API 36 | TEST EDİLDİ |
| `01_Game_Build/AAB/Prismway-IsikYolu-1.8.0-debug-signed.aab` | TAMAMLANDI, ama Play'e yüklenmemeli |
| Play'e yüklenecek AAB | **ERİŞİM GEREKLİ:** yükleme anahtarın (keystore) ve şifreleri yok. Yöntem `01_Game_Build/README.md` içinde. |
| Emülatörde ya da cihazda çalıştırma | ERİŞİM GEREKLİ (bu ortamda yok). Robolectric ile gerçek çizimle 33 arayüz akışı çalıştırıldı. |
| Gerçek AdMob kimlikleri | ERİŞİM GEREKLİ (test kimlikleriyle derlendi) |

---

## 11. Testler

- **Toplam:** 155. **Geçen:** 151. **Başarısız:** 0.
- **Atlanan:** 4. Kayıt testleri (`PromoCaptureTest` ×2, `TabletCaptureTest` ×2) `-PpromoDir` ile 6 dilde ayrıca
  çalıştırıldı ve başarılı oldu.
- **Medya denetimi:** `08_Test_Reports/Media_QA.md` — tümü uygun. Bu otomatik denetim boyut, biçim, alfa, sayı, süre ve kodekleri ölçer; görsel içerik ayrıca temas sayfalarıyla gözle incelendi.
- **Mağaza metni denetimi:** `tools/magaza/store_texts.py` — tüm sınırlar uygun.
- **Bağımsız son kalite denetimi:** 4 inceleyici + doğrulayıcı. Sonuçlar §8'de.

---

## 12. Google Play uygunluğu (§8.5)

Ayrıntılı tablo ve resmî kaynaklar: `08_Test_Reports/Play_Requirements.md`.

| Gereksinim | Durum |
|---|---|
| Hedef API 36 (31.08.2026'dan beri zorunlu) | TEST EDİLDİ |
| AAB | TAMAMLANDI (imza: ERİŞİM GEREKLİ) |
| 64 bit ve 16 KB (yerel kod yok) | TEST EDİLDİ |
| İzinler: uygulamanın kendi izinleri yalnızca VIBRATE, INTERNET, ACCESS_NETWORK_STATE; diğerleri AdMob SDK'sından | TAMAMLANDI |
| Gizlilik politikası (6 dil) | TAMAMLANDI. Yayın adresi: ERİŞİM GEREKLİ |
| Veri güvenliği formu (bağımlılık analizi + Google'ın resmî AdMob açıklaması) | TAMAMLANDI. Console'a girilmesi: MANUEL |
| Reklam beyanı, reklam kimliği, içerik derecelendirmesi, hedef yaş | MANUEL KONTROL GEREKLİ (önerilen yanıtlar hazır) |
| SDK güncelliği: AdMob 25.5.0 (desteklenen son eski SDK; kullanımdan kalkma 30.06.2027), UMP 4.0.0 | TAMAMLANDI. 2027'de Next-Gen SDK'ya geçiş planlanmalı. |
| Satın alma / Billing | UYGULANAMAZ |
| Sürüm 11 → 12 | TAMAMLANDI |
| Paket adı aynı | TAMAMLANDI |
| Üçüncü taraf lisansları | TAMAMLANDI (`Third_Party_Licenses.md`) |

**Sonuç:** Oyun ve yayın paketi teknik olarak Google Play'e hazır. Yayınlamak için aşağıdaki manuel adımlar senin
hesabınla yapılmalı.

---

## 13. Senin tamamlaman gerekenler

1. **Yükleme anahtarı:** oluştur, Gradle'a tanıt, `bundleRelease` ile imzalı AAB üret.
2. **AdMob:** gerçek kimliklerini (4 adet) Gradle'a yaz.
3. **Gizlilik politikası:** e-posta adresini ekle, 6 dildeki politikayı herkese açık bir adreste yayımla, adresi
   Console'a ve `privacyPolicyUrl` özelliğine gir.
4. **Play Console:**
   - mağaza girişleri (6 dil; metin ve görseller hazır),
   - uygulama içeriği formları (veri güvenliği, reklam, reklam kimliği, IARC, hedef kitle).
5. **YouTube:** fragmanı yükle (para kazanma kapalı), URL'yi Console'a gir.
6. **Gerçek cihaz testi:**
   - dil algılama ve değiştirme,
   - canlar ve saat değişikliği,
   - test reklamları,
   - AB izin formu,
   - performans (FPS/pil).
   - Ardından kapalı test kanalı.
7. **Marka:** "Prismway" için EUIPO/WIPO/TÜRKPATENT araştırması ve marka vekili görüşü. Sosyal medya kullanıcı
   adlarının kontrolü.
8. **Sosyal medya:** CTA'lı videoları oyun yayına girdikten sonra paylaş.

Adım adım liste: `10_Release_Checklist/Release_Checklist.md`.

---

## 14. Bilinen riskler ve eksikler

- **Marka:**
  - "PRISM: LIGHT THE WAY" (Eidos) komşu markası.
  - Avrupa ve Türkiye marka veritabanları sorgulanamadı.
  - "Prism" önekli bulmacalar mağazada kalabalık.
- **Saat hilesi:** Çevrimdışı oyunda kusursuz değil (bkz. §4).
- **Cihaz:** Gerçek cihaz ve emülatör testi yapılmadı. Görüntüler Robolectric'in gerçek çizimiyle alındı. Fiziksel
  tablet görüntüsü yok.
- **Kayıt ortamı:** Robolectric, kazanma anında test saatini ileri sarabiliyor. Tanıtım kayıtlarında kutlama
  animasyonu gerçek cihazdakinden biraz kısa görünebilir.
- **Çeviriler:** Yapay zekâ çevirmen ve editörlerince yapıldı. Yayından önce her dilde bir anadil konuşucusuna
  kısa bir göz attırmak önerilir.
- **AdMob:** 25.x, 30.06.2027'de kullanımdan kalkıyor.
- **Bildirim:** Oyun kapalıyken "canlar doldu" sistem bildirimi yok (bilinçli; izin gerektirir).

---

## 15. Dosyaların konumları

`isik-yolu/RELEASE_PACKAGE/` klasörü:

| Klasör | İçerik |
|---|---|
| `01_Game_Build/` | APK, AAB ve imza talimatı |
| `02_Game_Name_Research/` | `Name_Candidates.md`, `Market_Research.md`, `Final_Name.md`, `raw/` (ham sorgu verileri) |
| `03_Logos/` | `Turkish/`, `English/`, `Bilingual/`, `Transparent/` |
| `04_PlayStore/` | `App_Icon/`, `Feature_Graphics/`, `Phone_Screenshots/{TR,EN,DE,IT,FR,ES}/`, `Tablet_7_Inch/`, `Tablet_10_Inch/` |
| `05_Trailers/` | `Vertical/`, `Horizontal/`, `Social_Media/`, `Subtitles/`, `Source_Files/`, `Turkish/`, `English/`, `README.md` |
| `06_Store_Descriptions/` | `{TR,EN,DE,IT,FR,ES}/`, `store_texts.<dil>.json` |
| `07_Social_Media/` | `Turkish/`, `English/` |
| `08_Test_Reports/` | `Test_Report.md`, `Media_QA.md`, `Play_Requirements.md` |
| `09_Privacy_And_Legal/` | `privacy_policy_{tr,en,de,it,fr,es}.md`, `Data_Safety_Form.md`, `Third_Party_Licenses.md`, `Content_Rating_and_Audience.md` |
| `10_Release_Checklist/` | `Release_Checklist.md` |
| `11_Final_Report/` | bu dosya |
| `ZIP/` | görsel paketleri (≤ 30 MB) |

**Kaynak kod ve araçlar:**
- `app/src/main/java/com/isikyolu/game/i18n/` (`L`, `Brand`, `EconomyTexts`, `WorldText`)
- `app/src/main/assets/i18n/`
- `engine/src/main/java/com/isikyolu/engine/GameConfig.java`
- `tools/magaza/`: `release_media.py`, `logos.py`, `store_texts.py`, `package_media.py`, `media_qa.py`,
  `isim_arastirma.py`, `build_release_package.sh`
- `tools/i18n_check.py`
- `docs/i18n/SOZLUK.md`

**Yedek:**
- Çalışmaya başlamadan önceki durum: git etiketi `yedek-1.7.1-global-oncesi` (yerel) ve commit `6d94540`.

**Paylaşılmayanlar:**
- İmza anahtarı, şifre ya da gizli API anahtarı pakette yok.
- Hiçbir şey yayımlanmadı ya da harici bir platforma yüklenmedi.
