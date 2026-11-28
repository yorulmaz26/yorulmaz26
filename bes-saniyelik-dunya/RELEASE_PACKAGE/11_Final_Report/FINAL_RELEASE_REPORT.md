# FINAL RELEASE REPORT — Magic Lumos Software

**Oyun:** 5 Saniyelik Dünya → uluslararası ad **Stopwatch Shuffle**  
**Sürüm:** 1.1.0 (versionCode 2) · paket adı `com.besaniyelik.dunya` (değiştirilmedi) · rapor tarihi 28 Kasım 2026  
**Yedek:** çalışma öncesi durum `159e260` (uzak dalda) + git etiketi `backup-before-global-release` + `/home/user/backups/…tar.gz` (oturum ortamında)

Durum etiketleri: TAMAMLANDI · TEST EDİLDİ · EKSİK · ERİŞİM GEREKLİ · UYGULANAMAZ · MANUEL KONTROL GEREKLİ

## 1. Özet
| Alan | Durum |
|---|---|
| Kalıcı can sistemi (20 dk, saat koruması, arayüz) | TEST EDİLDİ (16/16 test) |
| 6 dil (tr, en, de, it, fr, es), otomatik algılama, anında dil değişimi | TEST EDİLDİ (eksik çeviri: **0**; 708 metin × 6 dil) |
| İngilizce ad araştırması ve seçimi | TAMAMLANDI — marka veritabanları ERİŞİM GEREKLİ |
| Logolar (TR, EN, iki dilli, saydam, açık/koyu zemin, yazısız amblem) | TAMAMLANDI (19 dosya) |
| Tanıtım videoları | TAMAMLANDI (8 video, 8 altyazı, 4 kapak) |
| Play Store görselleri | TAMAMLANDI (simge, 2 kapak, 48 telefon, 16 tablet) |
| Sosyal medya paketi | TAMAMLANDI (16 görsel) |
| Mağaza metinleri ve ASO (6 dil) | TAMAMLANDI |
| Testler | TEST EDİLDİ: 99/99 birim, 274/274 paket denetimi |
| APK / AAB | TAMAMLANDI — hata ayıklama anahtarıyla imzalı; yükleme anahtarı ERİŞİM GEREKLİ |
| Gerçek cihaz testi | MANUEL KONTROL GEREKLİ |

## 2. İsim
- **Eski Türkçe ad:** 5 Saniyelik Dünya (kaldırılmadı; ikincil ad olarak her yerde görünür)
- **Yeni İngilizce ad:** **Stopwatch Shuffle** — logodaki "5" yazılı kronometre korunur; "shuffle" kuralların karıştırılmasını anlatır.
- **Araştırma:** 33 aday, 7 ölçütle 100 üzerinden puanlandı. İlk 5: Twist O'Clock 76,5 · **Stopwatch Shuffle 72** · Tickwarp 70,5 · Rulequake 70 · Shiftopia 69. Çakışma riski en düşük olan Stopwatch Shuffle seçildi (Twist O'Clock'ta "Twist" adlı tek dokunuşlu oyun ve sınıf 9 markalar bulundu).
- **Erişim engelleri:** Google Play, App Store, Steam doğrudan aramaları ve WIPO, EUIPO, USPTO, TÜRKPATENT veritabanları bu ortamın ağ izinleri nedeniyle sorgulanamadı; web arama dizini kullanıldı. **Benzer ad bulunmaması tescil uygunluğu anlamına gelmez**; yayından önce profesyonel marka araştırması önerilir.
- Ayrıntı: `02_Game_Name_Research/` (Name_Candidates, Market_Research, Final_Name; tarih 27 Kasım 2026).
- **Mağaza başlıkları:** TR "5 Saniyelik Dünya", diğer diller "Stopwatch Shuffle". "Stopwatch Shuffle: 5 Saniyelik Dünya" 36 karakter olduğu için 30 sınırına sığmıyor.
- **Uygulanan yerler:** uygulama adı (Türkçe cihazda Türkçe, diğerlerinde İngilizce), açılış/yükleme ekranı, ana menü, ilk bölüm kartı (iki dilli logo: seçili dilin adı büyük, diğeri küçük), mağaza metinleri, kapak görselleri, videolar, sosyal medya. Açılıştaki Magic Lumos Software animasyonu zaten vardı.

## 3. Yazılım değişiklikleri
- `GameConfig.kt`: merkezi ayarlar (MAX_LIVES 3, LIFE_REGEN_SECONDS 1200, FREE_LEVELS 10, reklam/elmas can değerleri, diller).
- `progress/Lives.kt`: LifeManager (saf hesap) + LifeService; `Platform.time` (Android: `elapsedRealtime` + `Settings.Global.BOOT_COUNT`).
- `i18n/`: `L` (JSON dil dosyaları, adlı yer tutucular, tekil/çoğul, dile göre ondalık), `LevelText` (500 bölüm adı dilin dil bilgisine göre: Almanca cinsiyet çekimi, İtalyanca/Fransızca/İspanyolca isim+sıfat sırası), `PlayerNames`.
- `assets/i18n/{tr,en,de,it,fr,es}.json` (708 metin), `assets/brand/` (İngilizce logo, ikincil adlar).
- Bütün ekranlar dil anahtarlarına bağlandı; arayüz kodunda sabit Türkçe metin kalmadı (testle denetleniyor). Yazı tipi tüm Avrupa karakterlerini içerir. Uzun dillerde düğme yazıları sığmazsa %60'a kadar küçülür (`FitLabel`).
- Ayarlara dil seçimi; telefon dili desteklenmiyorsa İngilizce. Dil, kayıtta saklanır ve ilerlemeyi etkilemez.
- Kayıt imzası CRC32'den HMAC-SHA256'ya yükseltildi (eski kayıtlar okunmaya devam eder).
- Terim ayrımı: bölüm içindeki 3 can **kalp** oldu ("Ekstra Kalp" güçlendirmesi dahil); **can** artık kalıcı canlardır.
- Masaüstü araçları: dil seçimi, sabit adımlı video kaydı, mağaza görüntüsü ve performans ölçümü modları; `tools/brand`, `tools/media`, `tools/i18n`, `tools/qa` betikleri.
- Android: `res/values-tr` (Türkçe ad), sürüm 1.1.0 / kod 2.

## 4. Can sistemi
**Kurallar:** en fazla 3 can (oyundaki mevcut değer: her denemede 3 kalp). Bölüm denemesi başlarken 1 can harcanır; bölüm geçilirse ya da hiç kalp kaybetmeden çıkılırsa geri gelir — yani yalnızca başarısız denemeler can götürür, deneme sırasında uygulamayı zorla kapatmak canı kurtarmaz. Her 20 dakikada 1 can, oyun kapalıyken de; canlar doluyken sayaç durur; süren sayaç yeni harcamada sıfırlanmaz. İlk 10 bölüm ve Sonsuz Dünya can harcamaz. Can yokken "Canların bitti!" penceresi: canlı geri sayım, **Reklam izle: +1 can** (günde 5), **Canları doldur — 12 elmas**, can gelince **Oyna**. Göstergeler: ana menü, harita/bölüm ekranları, mağaza ("❤ 2/3 · 19:42", doluyken "Dolu"); can yenilenince kalp zıplar ve bildirim çıkar.

**Güvenlik (TEST EDİLDİ):**
| Durum | Davranış |
|---|---|
| Saati ileri alma (açıkken / kapat-aç) | kazanç yok (monotonik saat) |
| Saati geri alma, tarih değiştirme | kayıp yok, eksiye düşme yok |
| Saat dilimi değişimi | etkisiz (UTC) |
| Sürekli kapat-aç, zorla durdurma | süre kaybolmaz, fazladan sayılmaz |
| Telefonun yeniden başlatılması | duvar saati kullanılır; geri giden saat 0 sayılır |
| İnternet yok | sorunsuz (sunucu gerekmez) |
| Kayıt dosyasını elle değiştirme | imza tutmaz, kayıt reddedilir |
| Bilinen sınır | saat ileri alınıp telefon yeniden başlatılırsa en fazla canları doldurmaya yetecek süre (60 dk) kazanılabilir; çevrimdışı oyunda önlenemez. Sunucu saati UYGULANAMAZ (oyunun sunucusu yok) |

Bildirim (oyun kapalıyken "canların doldu" bildirimi) eklenmedi: Android 13+ bildirim izni gerektirir; oyun içi bildirim var.

## 5. Diller
Türkçe, İngilizce, Almanca, İtalyanca, Fransızca, İspanyolca — **eksik çeviri: 0**. Kaynak Türkçe ve İngilizce metinler tarafımdan; Almanca, İtalyanca, Fransızca, İspanyolca çeviriler ayrı çeviri ajanlarıyla yapıldı ve denetleyiciyle doğrulandı (yer tutucu, çoğul, cinsiyet biçimi). Ana dili konuşan biri tarafından son okuma **MANUEL KONTROL GEREKLİ**. Yeni dil eklemek: `assets/i18n/<kod>.json` + `GameConfig.LANGUAGES`; eksikler `tools/i18n/check_i18n.py` ve LocalizationTest ile yakalanır.

## 6. Görseller ve videolar
- **Logolar (19):** `03_Logos/` — TR, EN, iki dilli (iki yön), her biri saydam/açık/koyu zemin; yazısız kronometre amblemi (amblemde "5" rakamı vardır). Yazı tipi Nunito Black (SIL OFL).
- **Play Store:** `app_icon_512.png` (512×512, 32 bit, 471 KB); `feature_graphic_tr/en.png` (1024×500, saydamsız); **48 telefon ekran görüntüsü** (6 dil × 8, 1920×1080 JPEG, ilk üçü oynanış, metinler o dilde, oyun arayüzü de o dilde); **16 tablet** görüntüsü (7": 1920×1200, 10": 2560×1600, gerçek oyun bu çözünürlükte çalıştırılarak; fiziksel tablette çekilmedi).
- **Sosyal medya (16):** TR ve EN için Instagram 1080×1080 / 1080×1350 / hikâye, Reels ve TikTok kapağı, YouTube 1280×720, Facebook 1200×630, lansman afişi ("Yakında Google Play'de" — oyun henüz yayınlanmadığı için).
- **Videolar (8, hepsi 30,0 sn, H.264 + AAC, 30 fps):** dikey 1080×1920 TR/EN (mağaza, harekete geçirici çağrısız), yatay 1920×1080 TR/EN, sosyal medya dikey TR/EN ("Maceraya Başla!" / "Start Your Adventure!") ve yayından sonrası için ("Hemen Oyna!" / "Play Now!"). %90 gerçek oynanış (son 3 sn marka kartı), siyah kenar ve filigran yok, görüntü esnetilmedi, müzik kodla üretilen özgün parça. Altyazılar `05_Trailers/Subtitles`, kapaklar `05_Trailers/Social_Media`, kurgu kaynakları `05_Trailers/Source_Files` (ham kayıtlar ~600 MB olduğu için konmadı; komutla yeniden üretilir).
- Google Play videoyu YouTube bağlantısı olarak ister: yükleme sizde (harici platforma hiçbir şey yüklenmedi).

## 7. Düzeltilen hatalar ve iyileştirmeler
- Ayarlar ekranında alt bilgi metni uzun dillerde ekranın dışına taşıyordu → kaydırılabilir yapıldı.
- Yeni etiket sınıfı ilk kurulumda boş işaretçi hatası veriyordu (masaüstü turunda yakalandı) → düzeltildi.
- Bölüm sonu "Sonraki" düğmesinde simge yazının altında kalıyordu → düğme genişletildi.
- Bölüm içi "can" ile kalıcı can karışıklığı → "kalp" terimi.
- Ana menü dışındaki ekranlarda can göstergesi, mağazada can kartı eklendi.

## 8. Performans
Masaüstünde 12 bölüm art arda: ort. 16,7 ms/kare, p95 ≤ 20,6 ms; bellek 4 → 8 MB'ta sabit (sızıntı yok); ilk kare 1,2 sn. Paket: APK 58,6 MB, AAB 58,3 MB (çoğu yüksek çözünürlüklü görseller). Grafik kalitesi düşürülmedi. Batarya, ısınma ve düşük donanımlı cihaz ölçümü **MANUEL KONTROL GEREKLİ**. Reklam kitaplığının gereksiz dillerdeki metinleri pakette duruyor (zararsız; dil filtresi uygulanmadı).

## 9. APK ve AAB
- `01_Game_Build/APK/stopwatch-shuffle-1.1.0-debug-signed-test.apk` — test için kurulabilir.
- `01_Game_Build/AAB/stopwatch-shuffle-1.1.0-debug-signed.aab` — **Play Console bu imzayı kabul etmez**; `keystore.properties` ile kendi yükleme anahtarınızla `./gradlew :android:bundleRelease` çalıştırın (ERİŞİM GEREKLİ).
- Emülatör/cihazda çalıştırma: bu ortamda yapılamadı (KVM yok) — MANUEL KONTROL GEREKLİ.

## 10. Testler
Başarılı: 99/99 birim/entegrasyon testi, 274/274 paket denetimi, 6 dilde uçtan uca masaüstü senaryosu, 14 bölümün otomatik oynanışı, performans/bellek ölçümü. Başarısız: yok. Yapılamayan: gerçek cihaz, gerçek reklam, gerçek tablet.

## 11. Google Play uygunluğu
| Gereklilik | Durum |
|---|---|
| Hedef API 36 (31 Ağu 2026'dan beri zorunlu) | TEST EDİLDİ |
| AAB, 64 bit, 16 KB sayfa | TEST EDİLDİ |
| İzinler (gereksiz izin yok) | TEST EDİLDİ |
| Gizlilik politikası (uygulama içi 6 dil + web metni TR/EN) | TAMAMLANDI; web'de yayımlama ERİŞİM GEREKLİ |
| Veri güvenliği formu | MANUEL KONTROL GEREKLİ — oyunun kendisi veri toplamaz; AdMob SDK verileri `REKLAM_REHBERI.md` §2.7'de (tahmin değil, SDK beyanı) |
| Reklam beyanı: Evet | MANUEL KONTROL GEREKLİ (formu siz doldurursunuz) |
| İçerik derecelendirmesi / hedef yaş | MANUEL KONTROL GEREKLİ (`audience` ayarıyla tutarlı olmalı) |
| SDK güncelliği | play-services-ads 25.5.0 (30 Haz 2027'de kullanımdan kalkıyor, 2028'den önce yeni nesil SDK'ya geçilmeli) |
| Uygulama içi satın alma / Play Billing | UYGULANAMAZ (gerçek parayla satın alma yok) |
| İmza | ERİŞİM GEREKLİ |
| Üçüncü taraf lisansları | Nunito (OFL), libGDX (Apache 2.0), Google Mobile Ads (Google şartları); görseller kullanıcıya ait; müzik ve sesler kodla üretildi |

Kaynak doğrulama: hedef API, 16 KB ve imzalama kuralları 21 Kasım'da developer.android.com'dan okunmuştu; Play Console yardım sayfaları bu ortamdan açılamadı (arama alıntıları). Tablet ekran görüntüsü kuralları kaynaklarda çelişkili (16:9 ↔ 16:10).

## 12. Manuel tamamlanması gerekenler
1. Yükleme anahtarı oluşturup imzalı AAB derlemek.
2. Google Play geliştirici hesabı (geliştirici adı: Magic Lumos Software), kimlik doğrulama.
3. Gizlilik politikasını web'de yayımlamak, iletişim e-postasını eklemek.
4. AdMob hesabı, gerçek reklam kimlikleri, app-ads.txt, `audience` kararı.
5. Play Console formları (veri güvenliği, reklam, hedef kitle, IARC).
6. Mağaza girişlerini 6 dilde yüklemek; videoyu YouTube'a yükleyip bağlantıyı girmek.
7. Marka tescil araştırması (TÜRKPATENT, EUIPO, USPTO).
8. Gerçek telefonda ve tablette dahili test; ana dili konuşanlarca çeviri okuması.
9. Kapalı test (12 kişi, 14 gün) ve üretim erişimi başvurusu.

## 13. Bilinen riskler ve eksiklikler
- Oyun hiç gerçek Android cihazda çalıştırılmadı.
- Can sistemi oyuncu deneyimini değiştirir (3 başarısız denemeden sonra bekleme); değerler `GameConfig.kt` içinde tek yerden değiştirilebilir, `LIVES_ENABLED = false` ile kapatılabilir.
- Çevrimdışı saat koruması kusursuz değildir (bkz. §4).
- Kayıt imza anahtarı uygulamanın içindedir: sıradan düzenlemeyi engeller, kararlı saldırganı değil.
- İsim için hukuki marka araştırması yapılmadı.
- Başlatıcıda "Stopwatch Shuffle" bazı telefonlarda kısaltılmış görünebilir.

## 14. Dosya konumları
`bes-saniyelik-dunya/RELEASE_PACKAGE/`
- `01_Game_Build/` APK, AAB · `02_Game_Name_Research/` · `03_Logos/` · `04_PlayStore/` (App_Icon, Feature_Graphics, Phone_Screenshots/<dil>, Tablet_7_Inch, Tablet_10_Inch)
- `05_Trailers/` (Vertical, Horizontal, Social_Media, Subtitles, Source_Files, Turkish, English) · `06_Store_Descriptions/<dil>/store_listing_<dil>.md`
- `07_Social_Media/` (Turkish, English) · `08_Test_Reports/` · `09_Privacy_And_Legal/` · `10_Release_Checklist/` · `11_Final_Report/`
- `ZIP/` görsel paketleri (her biri ≤ 30 MB; videolar tek tek zaten ≤ 23 MB)
- Kaynak kod ve araçlar: `core/`, `android/`, `desktop/`, `tools/{brand,media,i18n,qa}`; dil dosyaları `assets/i18n/`
