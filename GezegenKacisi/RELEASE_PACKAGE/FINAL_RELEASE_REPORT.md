# FINAL RELEASE REPORT — Gezegen Kaçışı / Planet Gap 3.0
Magic Lumos Software · 28 Kasım 2026

## Özet
| Konu | Durum |
|---|---|
| Eski Türkçe ad | Gezegen Kaçışı (korundu) |
| Yeni İngilizce ad | **Planet Gap** — GEÇİCİ ADAY (marka veritabanları sorgulanamadı) |
| Yedek | b4497ac commit'i (uzak dalda) + yerel etiket `backup-pre-global-release` + ZIP | TAMAMLANDI |
| Can sistemi | 20 dk dolum, güvenlik, arayüz, 18 test | TEST EDİLDİ |
| Diller | Türkçe, İngilizce, Almanca, İtalyanca, Fransızca, İspanyolca; eksik çeviri: **0** | TEST EDİLDİ |
| Görseller | 1 simge (+1024 sürüm), 2 kapak, 48 telefon, 10 tablet (7") + 10 tablet (10"), 16 logo dosyası + 8 şeffaf kopya + yazısız simge, 16 sosyal medya görseli, 4 video kapağı | TAMAMLANDI |
| Videolar | 8 MP4 (TR/EN × dikey, yatay, 2 sosyal), 2 SRT, kaynak dosyalar | TAMAMLANDI |
| APK | Test APK (sabit test imzası, SHA-256 f25c07…7d10) | TAMAMLANDI |
| AAB | İmzasız sürüm paketi | TAMAMLANDI / imza ERİŞİM GEREKLİ |
| Play'e yayın / harici yükleme | Yapılmadı (onayınız olmadan yapılmaz) | — |

## 1. Yazılım değişiklikleri
- **Can sistemi** (`Lives.kt`, `GameRules.kt`): en fazla 4 can (korundu), dolum 1200 sn merkezi ayarda, para ile can fiyatı 60. Süre değiştirilemeyen açılış sayacıyla ölçülür (uygulama kapalı/zorla durdurulmuş/internetsiz çalışır; saat, tarih, saat dilimi etkisiz). Yeniden başlatmada kapalı süre internet saatiyle tam, internet yoksa telefon saatiyle geçici sayılır; internet gelince fazla verilen süre geri alınır (borç olarak), eksik verilen eklenir. Kayıt tek seferde ve HMAC imzalı yazılır; elle değiştirilirse canlar sıfırdan dolar. Eski 2.2 kayıtları korunarak taşınır.
- **Can arayüzü**: kalp + "3/4" + "19:42" ya da DOLU, dolunca kalp atışı ve bilgi mesajı, "canın kalmadı" penceresi (video izle +1, 60 para ile +1, bekle).
- **Çok dil**: varsayılan İngilizce, telefon diline göre otomatik seçim (desteklenmiyorsa İngilizce), Ayarlar'dan yeniden başlatmadan değiştirme, kalıcı tercih; Android 13+ "Uygulama dili" listesi; her derlemede eksik çeviri denetimi (`./gradlew :app:checkTranslations`); çoğul biçimler; yeni dil = `values-xx` + `GameView.Lang` + `locales_config.xml`.
- **Ayarlar ekranı**: dil, ses, müzik (yeni, ayrı), reklam gizlilik ayarları, sürüm.
- **Marka**: iki dilli başlık (ana ekran, açılış), dile göre uygulama adı.
- **Yayın uyumu**: API 36, AGP 8.13.2, Gradle 8.14.3, Android 16 geri hareketi, `appCategory=game`, App Bundle dil bölmesi kapalı (oyun içi dil değişimi için), yalnızca 6 dilin kaynakları (APK 7,7 → 7,2 MB), Android 13 temalı simge, yayın imzası için gizli dosya desteği.
- **Düzeltilen hatalar**: lint'in bulduğu 2 hata (eski geri tuşu uyarısı, Fransızca çoğul biçim), kullanılmayan 7 metin kaynağı silindi, App Bundle'da dil değiştirmenin bozulacağı durum giderildi.

## 2. Can sistemi güvenlik testleri
Saat ileri/geri, tarih ve saat dilimi değişikliği, sürekli aç-kapa, zorla durdurma, internetsiz/internetli yeniden başlatma, saat ileri alıp yeniden başlatma, kayıt dosyası değiştirme, güncelleme: hepsi birim testlerinde **geçti**. Bilinen sınır: hiç internete bağlanmayan biri saati ileri alıp telefonu yeniden başlatarak en fazla bir kez dolum kazanabilir; uygulama verisini silmek de canları doldurur (para ve ilerleme de silinir).

## 3. İsim araştırması
28 aday, 7 ölçüt. Google Play, App Store, Steam, web, YouTube, Instagram, TikTok tarandı (arama motoru üzerinden). **Erişilemeyenler:** play.google.com doğrudan, WIPO, EUIPO/TMview, USPTO, TÜRKPATENT; ayrıca web arama sınırı dolduğu için doğrulama taraması yapılamadı. Benzer isim bulunmaması tescil uygunluğu anlamına gelmez. Ayrıntı: 02_Game_Name_Research.

## 4. Görseller ve videolar
Hepsi oyunun kendi çizim koduyla, gerçek oyun durumlarından üretildi (otomatik pilot gerçek kurallarla oynar; çarpışmalar açık). Videolarda müzik ve efektler oyunun kendi sentezlediği seslerdir (lisans gerekmez). Fragman ≈%90 oynanış, son 3 sn logo. Play sürümünde harekete geçirme ifadesi yok; sosyal sürümler "Maceraya Başla! / Start Your Adventure!" ve yayın sonrası için "Hemen Oyna! / Play Now!". Yatay sürümde kenarlar aynı görüntünün bulanık hâli (siyah kenar ve esnetme yok). Tablet görselleri gerçek tablet çözünürlüğünde oyunun kendi çizimidir (fiziksel tablet değil).

## 5. Başarılı / başarısız testler
Bkz. 08_Test_Reports/Test_Report.md. Başarısız test yok. Gerçek cihaz, gerçek reklam, gerçek Wi-Fi ve performans ölçümleri yapılamadı → MANUEL KONTROL GEREKLİ.

## 6. Google Play yayın uygunluğu
Teknik olarak hazır (API 36, AAB, 64 bit/16 KB uyumlu, gereksiz izin yok). **Yayını engelleyen maddeler:**
1. **Paket adı `com.example.gezegenkacisi`** — Google Play "com.example" paket adlarını kabul etmez. Talimatınız gereği değiştirilmedi; yayından önce değiştirmeniz gerekir (ör. `com.magiclumos.planetgap`). Uygulama henüz yayında olmadığı için mağaza kullanıcısı etkilenmez; yalnızca bu test APK'sını kurmuş cihazlar yeni uygulamayı ayrıca kurar.
2. Yükleme anahtarıyla imzalama.
3. AdMob gerçek kimlikleri, gizlilik politikası URL'si, app-ads.txt.

## 7. Manuel olarak tamamlanması gerekenler
Paket adı, yükleme anahtarı + `keystore.properties`, AdMob kimlikleri, gizlilik politikası yayını, veri güvenliği formu, IARC anketi, hedef yaş, marka tescil araştırması, fragmanın YouTube'a yüklenmesi, gerçek cihaz testleri, mağaza metinlerinin ana dili konuşanlarca son okuması.

## 8. Bilinen riskler
"GAP" markası (Gap Inc.) için hukuki görüş önerilir; mağaza metinleri ve çeviriler yapay zekâ ile yazıldı ve otomatik + kısmi editör kontrolünden geçti (DE/IT editör onaylı; FR/ES ve mağaza metinleri editör adımı kullanım sınırına takıldı, tarafımdan gözden geçirildi).

## 9. Dosya konumları (GezegenKacisi/RELEASE_PACKAGE)
01_Game_Build/APK, AAB · 02_Game_Name_Research · 03_Logos (Turkish, English, Bilingual, Transparent) · 04_PlayStore (App_Icon, Feature_Graphics, Phone_Screenshots/TR..ES, Tablet_7_Inch, Tablet_10_Inch) · 05_Trailers (Vertical, Horizontal, Social_Media, Subtitles, Turkish/English kapaklar, Source_Files) · 06_Store_Descriptions/TR..ES · 07_Social_Media · 08_Test_Reports · 09_Privacy_And_Legal · 10_Release_Checklist · 11_Final_Report. ZIP arşivleri (≤30 MB) `RELEASE_PACKAGE_ZIPS/` içinde (depoya eklenmez).
