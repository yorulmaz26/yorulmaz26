# FINAL RELEASE REPORT — Magic Lumos Software

**Tarih:** 28 Kasım 2026 · **Sürüm:** 1.1.0 (versionCode 2) · **Paket:** `com.golgen.oyun` (değişmedi)
**Yedek:** değişikliklerden önceki durum: commit `4fc4b32` ve GitHub yayını `golgen-v1.0.0-b34` (APK + otomatik kaynak arşivi). Yerel etiket: `yedek/global-oncesi`.

Durum anahtarı: **TAMAMLANDI** · **TEST EDİLDİ** · **EKSİK** · **ERİŞİM GEREKLİ** · **UYGULANAMAZ** · **MANUEL KONTROL GEREKLİ**

## 1. Oyun adı
| | |
|---|---|
| Eski Türkçe ad | **Gölgen Seni Yakalamasın** (korundu) |
| Yeni İngilizce ad | **Doppelglow** — geçici aday, 79/100 puan |
| Araştırma | 24 aday puanlandı. En iyi 5: Doppelglow 79, Ghostrail 68, Umbrix 65, Ombrix 64, Echoveer 62. Erişilebilen kaynaklarda (web araması; Google Play, App Store, Steam, YouTube, Instagram ve TikTok arama dizinleri; ABD marka toplayıcıları) birebir eşleşme bulunmadı. |
| Durum | **MANUEL KONTROL GEREKLİ** — WIPO, EUIPO, USPTO ve TÜRKPATENT veritabanlarına bu ortamdan **erişilemedi**; mağazalar doğrudan açılamadı (yalnızca arama dizini). Son "çürütme" doğrulaması web arama kotası dolduğu için çalışmadı. Benzer isim bulunmaması tescil uygunluğu anlamına gelmez. Ayrıntı: `02_Game_Name_Research/`. |

## 2. Yapılan yazılım değişiklikleri
| İş | Durum |
|---|---|
| Merkezi yapılandırma `core/GameConfig.java`: `LIFE_REGEN_SECONDS = 1200`, `MAX_LIVES = 4` (mevcut değer korundu), dolum fiyatı, ödüllü video sınırı | TAMAMLANDI |
| Can sistemi: 20 dk yenilenme; sayaç dolunca durur; can kaybında süren sayaç sıfırlanmaz; uygulama kapalıyken geçen süre sayılır; çevrimdışı çalışır; negatif/azami üstü değer yok; güncellemede canlar korunur | TEST EDİLDİ (birim testleri) |
| Can güvenliği: monoton saat + açılış sayacı; saat ileri/geri, saat dilimi, kapat-aç, zorla durdurma, yeniden başlatma, **kayıt dosyası imzası** (elle değiştirilen kayıtta elmas ve can sıfırlanır; ilerleme korunur); can harcaması anında diske yazılır | TEST EDİLDİ |
| Can arayüzü: ❤ 3/4, "Sonraki can: 19:42" (dk:sn, canlı), yenilenme animasyonu ve bildirimi, "canlar doldu" bildirimi, **"Canın kalmadı" penceresi** (video ile +1 can, elmasla doldur, kapat; can gelince bölüme başla) | TEST EDİLDİ (emülatör) |
| Çoklu dil: `core/L10n` + `assets/localization/{tr,en,de,it,fr,es}.json` (335 anahtar), sistem dilinden otomatik seçim (desteklenmiyorsa İngilizce), Ayarlar → Dil (yeniden başlatmasız), kalıcı tercih, çoğullar, Türkçe büyük harf, metin sığdırma/satır kaydırma, taşma ve eksik anahtar günlükleri | TEST EDİLDİ |
| İki dilli başlık (ana menü, açılış), başlatıcı adı: Türkçe cihazda "Gölgen Seni Yakalamasın", diğerlerinde "Doppelglow" | TAMAMLANDI |
| Yeni uyarlanabilir simge + Android 13+ tek renkli simge | TAMAMLANDI |
| `targetSdk/compileSdk 36`, sürüm 1.1.0, AAB, yükleme anahtarı seçeneği, 6 dil kaynak filtresi | TEST EDİLDİ (CI) |
| Düzeltilen hatalar: Türkçe uzun harita bilgisinin okunmaz derecede küçülmesi (satırlara bölünüyor); can penceresinde boşluk | TEST EDİLDİ |
| Debug derlemesine özel çekim modu (otomatik pilot + kare kare kayıt) ve CI medya işi; yayın APK'sına girmez | TAMAMLANDI |

## 3. Desteklenen diller
Türkçe, English, Deutsch, Italiano, Français, Español. **Eksik çeviri sayısı: 0** (335 anahtar × 6 dil; birim testi + çalışma anı taraması). 6 dilde 9 ekranın emülatör taramasında metin taşması: **0**.

## 4. Testler
| Test | Sonuç |
|---|---|
| Birim testleri (JUnit) | **103/103 geçti** (can sistemi, kayıt imzası, saat/saat dilimi, yerelleştirme, 500 bölüm kuralları) |
| Emülatör UI testi, Android 14 (API 34) | **56/56 geçti** (`08_Test_Reports/ui_test_results_api34.txt`) |
| Emülatör UI testi, Android 16 (API 36) | **geçti** (CI çalışması 41/42) |
| Yayın paketi otomatik kontrolü | **146/146 geçti** (`08_Test_Reports/release_package_check.md`) |
| Gerçek reklam gösterimi | Test kimlikleriyle banner/geçiş/ödüllü video gösterildi; bazı çalışmalarda Google "No fill" döndü (BİLGİ) |
| Gerçek cihaz, dokunma hissi, ses, ısınma, batarya | **EKSİK** — bu ortamda fiziksel cihaz yok |

## 5. Performans
- Emülatör (yazılımsal GPU) ~20–32 FPS; gerçek cihazı temsil etmez. **Gerçek cihazda 60 FPS ölçümü yapılmadı.** Oyun döngüsünde bellek tahsisi olmadığını doğrulayan birim testi geçiyor.
- Release APK **1,9 MB**, AAB **3,8 MB**. Yalnızca 6 dilin kaynakları paketleniyor. APK'da native kütüphane yok.
- RAM, açılış süresi ve düşük donanım ölçümleri **MANUEL KONTROL GEREKLİ** (dahili test kanalında).

## 6. Hazırlanan görseller ve videolar
| Çıktı | Adet | Konum |
|---|---|---|
| Logo (TR, EN, iki dilli ×2; koyu/açık/şeffaf zemin; 4000×2000) + yazısız simge 1024 | 17 | `03_Logos` |
| Uygulama simgesi 512×512 | 1 | `04_PlayStore/App_Icon` |
| Kapak görseli 1024×500 (TR, EN) | 2 | `04_PlayStore/Feature_Graphics` |
| Telefon ekran görüntüsü 1080×1920 (6 dil × 8, gerçek oyun) | 48 | `04_PlayStore/Phone_Screenshots` |
| Tablet 7" (1200×1920) ve 10" (1600×2560), EN+TR | 16 | `04_PlayStore/Tablet_*` |
| Sosyal medya (TR/EN × 8 biçim) | 16 | `07_Social_Media` |
| Video: TR/EN dikey 30 sn; TR/EN yatay; 4 sosyal sürüm ("Hemen Oyna!", "Maceraya Başla!", "Play Now!", "Start Your Adventure!") | 8 | `05_Trailers` |
| Altyazı (TR, EN), kaynak klipler, kurgu betikleri, müzik | — | `05_Trailers/Subtitles`, `Source_Files` |
| ZIP arşivleri (her biri ≤ 30 MB) | 6 | `ZIP` |

Videolar MP4, H.264 High, AAC 48 kHz, 60 FPS, tam 30 sn. Görüntülerin ~%90'ı gerçek oyun: debug APK'nın çekim modunda, emülatörde, otomatik pilotun oynadığı gerçek oyun motorundan kare kare alındı. Müzik oyunun kendi ürettiği synthwave; telif sorunu yok. Siyah kenarlık ve filigran yok. Yeni kullanıcı başarısı veya indirme sayısı gibi gerçek dışı iddia yok.

## 7. Mağaza metinleri (ASO)
6 dilde başlık, kısa ve uzun açıklama, slogan, özellikler, hedef kitle, anahtar kelime fikirleri, kategori (Arcade), etiketler, sürüm notları, video başlığı, YouTube açıklaması, sosyal gönderiler ve erişilebilirlik metinleri hazırlandı. Karakter sınırları otomatik doğrulandı. Anahtar kelimeler arama hacmi verisi olmadan **fikir** olarak verildi. Konum: `06_Store_Descriptions`.

## 8. Google Play uygunluğu
| Gereksinim | Durum |
|---|---|
| Hedef API 36 (developer.android.com'dan doğrulandı) | TAMAMLANDI |
| AAB | TAMAMLANDI, ama **debug anahtarıyla imzalı** → yükleme anahtarı **ERİŞİM GEREKLİ** |
| 64 bit / 16 KB | UYGULANAMAZ (native kod yok; uyumlu) |
| Geri tuşu (Android 16 öngörülü geri), kenardan kenara, oyun kategorisi | TEST EDİLDİ |
| AdMob gerçek kimlikler, `app-ads.txt` | ERİŞİM GEREKLİ |
| Gizlilik politikası (TR/EN metin hazır) → herkese açık URL | ERİŞİM GEREKLİ |
| Veri güvenliği formu taslağı | MANUEL KONTROL GEREKLİ (Google'ın AdMob veri beyanı sayfasına bu ortamdan erişilemedi) |
| İçerik derecelendirmesi / hedef kitle 13+ / reklam beyanı | MANUEL KONTROL GEREKLİ (önerilen yanıtlar hazır) |
| SDK güncelliği (play-services-ads 23.6.0, UMP 3.1.0) | MANUEL KONTROL GEREKLİ (CI özetinde Google Maven'deki son sürümler raporlanıyor) |
| Uygulama içi satın alma / Play Billing | UYGULANAMAZ (yok; elmaslar yalnızca oynayarak kazanılır) |

**Sonuç:** Teknik olarak dahili test kanalına yüklenmeye hazır. Üretime çıkmadan önce aşağıdaki manuel işler gerekli.

## 9. Manuel olarak tamamlanması gerekenler
1. Yükleme anahtarı ile imzalı AAB derlemek (`-PuploadStoreFile …`) ve Play App Signing.
2. Gerçek AdMob kimlikleri ve `app-ads.txt`.
3. Gizlilik politikasını yayınlamak; iletişim e-postasını ve tarihi doldurmak.
4. Veri güvenliği formunu Google'ın resmî AdMob beyanıyla doğrulamak.
5. IARC anketi, hedef kitle ve reklam beyanı.
6. "Doppelglow" için WIPO/EUIPO/USPTO/TÜRKPATENT marka araştırması; alan adı ve sosyal medya kullanıcı adları.
7. Tanıtım videosunu YouTube'a yüklemek (Play yalnızca YouTube bağlantısı kabul eder).
8. Gerçek cihazlarda dahili test: performans, dokunma, ses, ısınma, düşük donanım.

## 10. Bilinen riskler
- İngilizce ad marka açısından doğrulanmadı.
- Sunucu olmadığı için saat hilesi tamamen engellenemez: telefonu yeniden başlatıp saati ileri almak en fazla 1 can ve 1 ödül günü kazandırır. Kayıt imzası root yetkisi olan, uygulamayı çözümleyen birini durduramaz (caydırıcıdır).
- Çeviriler yapay zekâ çevirmenlerince yazılıp kontrol edildi; yerel konuşur bir kişinin son okuması önerilir.
- Emülatör performansı gerçek cihazı temsil etmez.

## 11. Dosya konumları
`golgen-seni-yakalamasin/RELEASE_PACKAGE/`: `01_Game_Build` (APK, AAB) · `02_Game_Name_Research` · `03_Logos` · `04_PlayStore` · `05_Trailers` · `06_Store_Descriptions` · `07_Social_Media` · `08_Test_Reports` · `09_Privacy_And_Legal` · `10_Release_Checklist` · `11_Final_Report` · `ZIP`.
Kod: `app/src/main/java/com/golgen/oyun/core/GameConfig.java`, `core/L10n.java`, `core/Energy.java`, `core/Progress.java` (kayıt imzası), `screens/LivesDialog.java`, `screens/SettingsScreen.java` (dil seçimi), `app/src/main/assets/localization/`. Araçlar: `tools/l10n_merge.py`, `tools/media_capture.sh`, `tools/release_check.py`.
