# FINAL RELEASE REPORT — Yanlış Renk / Miscolor 1.4.0

**Geliştirici:** Magic Lumos Software · **Tarih:** 2026-11-28 · **Paket adı:** `com.yanlisrenk.oyun` (değiştirilmedi) · **versionCode/Name:** 15 / 1.4.0
**Yedek:** git etiketi `backup-before-global-release` (commit eec6a3c, yerel) + `yanlis-renk-1.3.0-backup.tar.gz` (oturum geçici klasörü).

Durum kodları: TAMAMLANDI · TEST EDİLDİ · EKSİK · ERİŞİM GEREKLİ · UYGULANAMAZ · MANUEL KONTROL GEREKLİ

## 1. İsim
| Madde | Değer | Durum |
|---|---|---|
| Eski Türkçe ad | **Yanlış Renk** (korundu) | TAMAMLANDI |
| Yeni İngilizce ad | **Miscolor** (mis- = yanlış + color) | TAMAMLANDI |
| Araştırma | 27 aday, 7 ölçütlü 100 puanlık tablo; ilk 5: Miscolor 86, Color Bluff 77, Anticolor 76, Wrongbow 76, Lying Colors 75 | TAMAMLANDI |
| Kaynak erişimi | Yalnızca web araması çalıştı (ABD). Google Play, App Store/iTunes API, Steam, USPTO, WIPO, EUIPO/TMview, TÜRKPATENT doğrudan sorgulanamadı → "çakışma yok" iddia EDİLMEDİ. Bilinen risk: ABD'de giyim sınıfında "MISSCOLOR" markası | MANUEL KONTROL GEREKLİ (avukat/marka taraması) |
| Belgeler | 02_Game_Name_Research/{Name_Candidates, Market_Research, Final_Name}.md | TAMAMLANDI |

## 2. Kod değişiklikleri
| Değişiklik | Durum |
|---|---|
| Hedef API 35 → **36**, AGP 8.7.3 → 8.13.2, Kotlin 2.1.0 → 2.2.21 | TEST EDİLDİ |
| Google Mobile Ads 23.6.0 (kullanımdan kaldırılmış) → **25.5.0**, UMP 3.1.0 → 4.0.0 | TEST EDİLDİ (derleme + emülatörde çevrimdışı açılış) |
| Kaynak dil filtresi (yalnızca 6 dil) | TAMAMLANDI |
| `GameConfig` merkezi ayar dosyası | TAMAMLANDI |
| Yerelleştirme yöneticisi `L` + 6 JSON + `FitLabel` + dil seçici + marka (`Brand`) | TEST EDİLDİ |
| Android `strings.xml` 6 dil (uygulama adı + hata penceresi) | TEST EDİLDİ (aapt2: TR "Yanlış Renk", diğerleri "Miscolor") |
| Tanıtım videosu kaydedici, mağaza/logo üreticileri | TAMAMLANDI |

## 3. Can sistemi
| Gereksinim | Uygulama | Durum |
|---|---|---|
| 1 can / 20 dk, merkezi dosyada | `GameConfig.LIFE_REGEN_SECONDS = 1200`, `MAX_LIVES = 4` | TEST EDİLDİ |
| Maks. doluyken sayaç durur, kayıpta başlar, yeni kayıp sıfırlamaz | `LifeManager` | TEST EDİLDİ |
| Uygulama kapalıyken / zorla durdurma / tekrarlı açma | Monoton saat (elapsedRealtime) | TEST EDİLDİ |
| Cihaz yeniden başlatma | Açılıştan beri geçen süre hemen; kalan süre sunucu saatiyle geriye dönük (hiç doğrulanmamış cihazda 7 gün üst sınırla) | TEST EDİLDİ |
| Çevrimdışı | Monoton saatle çalışır | TEST EDİLDİ |
| Saat ileri/geri, tarih değişimi | Duvar saati can süresini etkilemez | TEST EDİLDİ |
| Saat dilimi değişimi | Gün koruması: yeni gün için ≥21 saat güvenilir süre; gün anahtarı geri gitmez | TEST EDİLDİ |
| Kayıt kurcalama | HMAC imzalı kayıt + yedek; değerler 0..4 aralığına sıkıştırılır | TEST EDİLDİ |
| Asla eksi/maks üstü | `normalize()`, `addLives` sınırı | TEST EDİLDİ |
| Güncelleme sonrası korunma | 15 dk'lık eski kayıt değerleri korunur | TEST EDİLDİ |
| Reklam sonrası can | `grantAdLife()` (doluysa vermez, sayacı sıfırlamaz) | TEST EDİLDİ (birim); gerçek reklam izlenmedi |
| Arayüz: x/4, mm:ss geri sayım, dolma animasyonu, "dolu" bilgisi, "canlar bitti" penceresi, reklam/para ile can | BaseScreen/LivesScreen | TEST EDİLDİ (masaüstü + emülatör metni "20 minutes") |
| Sınır | Root'lu/tam çevrimdışı cihazda %100 koruma mümkün değil (belgelendi) | — |

## 4. Diller
| Madde | Durum |
|---|---|
| tr, en, de, it, fr, es — 1006 anahtar/dil | TAMAMLANDI |
| **Eksik çeviri sayısı: 0** (her dilde; yer tutucu hatası 0) | TEST EDİLDİ (`LocalizationTest`, `tools/i18n/validate.py`) |
| Cihaz dili algılama (desteklenmeyen → İngilizce) | TEST EDİLDİ (birim + emülatör: İngilizce otomatik) |
| Ayarlardan anında değişim, kalıcı, kayda dokunmaz | TEST EDİLDİ (emülatör: EN→TR, 350 altın korundu) |
| Taşma | DE ve FR'de 46 ekran gezildi, 0 hata; uzun metinler otomatik küçülür | TEST EDİLDİ |
| Çeviri kalitesi | Yapay zekâ çevirmen ajanlarıyla üretildi | MANUEL KONTROL GEREKLİ (anadil kontrolü önerilir) |

## 5. Görseller
| Çıktı | Adet | Durum |
|---|---|---|
| Logolar (TR, EN, iki dilli ×2, şeffaf, açık/koyu zemin, metinsiz simge) | 17 dosya | TAMAMLANDI |
| Uygulama simgesi 512 (32-bit PNG, <1 MB) | 1 | TAMAMLANDI |
| Öne çıkan grafik TR/EN 1024×500 (saydamsız) | 2 | TAMAMLANDI |
| Telefon ekran görüntüleri 1080×1920, 6 dil × 8 | **48** | TAMAMLANDI (gerçek oyun çıktısı) |
| Tablet 7" (1200×1920) / 10" (1600×2560) gerçek render | 10 + 10 | TAMAMLANDI |
| Sosyal medya TR/EN (IG post, IG dikey, story, Reels kapağı, TikTok kapağı, YouTube, Facebook, lansman afişi) | 16 | TAMAMLANDI |
| ZIP'ler (her biri ≤30 MB) | RELEASE_PACKAGE/ZIP | TAMAMLANDI |

## 6. Videolar (gerçek oynanış, 30 sn, H.264/AAC 48 kHz, 30 fps)
| Dosya | Durum |
|---|---|
| trailer_tr_vertical.mp4, trailer_en_vertical.mp4 (1080×1920, indirme çağrısız) | TAMAMLANDI |
| Sosyal sürümler (Hemen Oyna!/Maceraya Başla! — Play Now!/Start Your Adventure!) | TAMAMLANDI |
| Yatay 1920×1080 TR/EN (bulanık zemin, siyah bant yok) | TAMAMLANDI |
| Altyazı SRT TR/EN, YouTube ve Reels kapakları, düzenlenebilir kaynaklar | TAMAMLANDI |
| ≥%80 gerçek oynanış: 0–27 sn oyun ekranları (%90), 27–30 sn marka kapanışı | TAMAMLANDI |
| Müzik: projeye özgü sentez (lisanslı) | TAMAMLANDI |
| Play'e video ekleme (YouTube bağlantısı gerekir) | MANUEL KONTROL GEREKLİ |

## 7. Mağaza metinleri
6 dil: başlık, kısa/uzun açıklama, slogan, özellikler, anahtar kelimeler, sürüm notları, video başlığı, YouTube açıklaması, sosyal gönderiler, alt metinler. Karakter sınırları ölçüldü (06_Store_Descriptions/LIMIT_CHECK.md) — **TEST EDİLDİ**. Sosyal gönderiler "yakında" diyor; yayından sonra güncelleyin.

## 8. Düzeltilen hatalar
1. **Yüksek DPI cihazda metin küçültme yerine büyütme** (`Label.setFontScale` temel ölçeği eziyordu) — emülatörde bulundu, düzeltildi, yeniden doğrulandı. Koleksiyon ekranındaki eski 0.82 ölçeğinde aynı hata vardı, düzeltildi.
2. Saat dilimi değiştirerek aynı gün ikinci günlük ödül alma — engellendi.
3. İlerlemeyi sıfırlayarak aynı gün tekrar günlük ödül alma — engellendi.
4. Hiç internete çıkmamış cihazda yeniden başlatma sonrası can süresinin kalıcı kaybı — giderildi.
5. Kullanımdan kaldırılmış reklam SDK'sı (23.x) — güncellendi.
6. Ana menüde can sayacı etiketinin ilk karede yanlış yükseklikle ölçülmesi — düzeltildi.

## 9. Performans
| Ölçüm | Değer | Durum |
|---|---|---|
| Masaüstü (Mesa yazılım GPU) ekran başına ort. kare süresi | 1–6 ms | TEST EDİLDİ |
| APK boyutu | ~29.5 MB (4 ABI); AAB ~27 MB — Play cihaz başına bölünmüş paket sunar | TEST EDİLDİ |
| R8 küçültme | Kapalı (libGDX yansıma kaynaklı açılış çökmesi geçmişi) | — |
| Gerçek cihazda FPS/RAM/pil/ısı | Ölçülmedi (yalnızca hızlandırmasız emülatör) | MANUEL KONTROL GEREKLİ |

## 10. Testler
| Test | Sonuç |
|---|---|
| JUnit (logic + server): 50 test | 50 geçti, 0 başarısız |
| Bölüm denetimi (500 + 500 ustalık, geçilemeyen bölüm) | 0 hata |
| Masaüstü ekran gezintisi DE/FR (46 ekran) | 0 hata |
| Android 11 emülatör: kurulum, açılış, öğretici, günlük ödül, ana menü, ayarlar, dil değişimi | Çökme yok |
| Gerçek reklam tıklaması / gerçek satın alma | Yapılmadı (talimat gereği) |
| Gerçek cihaz testi | MANUEL KONTROL GEREKLİ |

## 11. APK / AAB
| | Durum |
|---|---|
| `01_Game_Build/APK/Miscolor-YanlisRenk-1.4.0.apk` | TEST EDİLDİ (emülatörde) |
| `01_Game_Build/AAB/Miscolor-YanlisRenk-1.4.0.aab` | TAMAMLANDI |
| İmza | **Hata ayıklama anahtarı** → Play'e yüklenemez. ERİŞİM GEREKLİ: kendi keystore'unuz |
| AdMob kimlikleri | Test kimlikleri. ERİŞİM GEREKLİ: gerçek kimlikler |

## 12. Play hazırlık durumu
Teknik gereksinimler (API 36, AAB, 64-bit, 16 KB) karşılanıyor. Yayın için eksik olanlar yalnızca sizin hesabınızla yapılabilecek işler: imza anahtarı, AdMob kimlikleri, gizlilik politikası URL'si, Veri güvenliği formu, içerik derecelendirmesi, hedef kitle, reklam beyanı, marka hukuk kontrolü. **Uygulama yayınlanmadı, hiçbir platforma içerik yüklenmedi.**

## 13. Manuel görevler
1. `keystore.properties` + yayın anahtarı ile yeniden derleme.
2. AdMob uygulama/birim kimlikleriyle derleme (`-PadmobAppId=…` vb.).
3. 09_Privacy_And_Legal/PRIVACY_POLICY_*.md metnini web'de yayımlama (iletişim e-postası ve tarih doldurulacak).
4. Play Console: Veri güvenliği, reklam beyanı, AD_ID beyanı, IARC, hedef kitle.
5. "Miscolor" için profesyonel marka araştırması.
6. Çevirilerin anadil kontrolü; gerçek cihazda performans testi.
7. Videoları YouTube'a yükleyip Play'e bağlama (onayınızla).

## 14. Riskler
- Marka: Miscolor için resmî veritabanı taraması yapılamadı.
- Çeviriler makine destekli; anadil incelemesi önerilir.
- Ön plan hizmeti izni reklam kütüphanesinden geliyor; Console beyanı isteyebilir.
- 13 yaş altı hedeflenirse reklam yapılandırması değişmeli (kod şu an çocuklara yönelik bayrak ayarlamıyor).

## 15. Dosya konumları
`RELEASE_PACKAGE/` — 01_Game_Build · 02_Game_Name_Research · 03_Logos · 04_PlayStore · 05_Trailers · 06_Store_Descriptions · 07_Social_Media · 08_Test_Reports · 09_Privacy_And_Legal · 10_Release_Checklist · 11_Final_Report · ZIP
İkili dosyalar (mp4/apk/aab/png/jpg/zip) git'e eklenmez; metin belgeleri depodadır.
İmza anahtarı, şifre veya gizli API anahtarı pakette yoktur.
