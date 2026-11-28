# Yayın kontrol listesi — Prismway (Işık Yolu) 1.8.0

İşaretli [x] maddeler bu çalışmada yapıldı ve doğrulandı. İşaretsiz [ ] maddeler **senin hesabınla / senin kararınla**
yapılmalı. Hiçbir şey Play Console'a, YouTube'a ya da başka bir platforma yüklenmedi.

## A. Derleme ve imza
- [x] Sürüm 1.8.0 (versionCode 12). Paket adı `com.isikyolu.game` değişmedi.
- [x] Hedef API 36 (Android 16). minSdk 26.
- [x] Release APK ve AAB derlendi (R8 küçültme, kaynak küçültme, 6 dil filtresi).
- [x] Yerel (.so) kütüphane yok. 16 KB hizalama doğrulandı.
- [ ] **Yükleme anahtarı oluştur** ve `~/.gradle/gradle.properties` dosyasına ekle. Teslimdeki AAB debug imzalıdır,
      Play'e yüklenmemeli.
  ```
  keytool -genkeypair -v -keystore ~/prismway-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
  # ~/.gradle/gradle.properties:
  uploadStoreFile=/home/<sen>/prismway-upload.jks
  uploadStorePassword=…
  uploadKeyAlias=upload
  uploadKeyPassword=…
  ./gradlew :app:bundleRelease   # → app/build/outputs/bundle/release/app-release.aab
  ```
- [ ] Play Console'da **Play App Signing**'i kabul et (yeni uygulamalarda varsayılan).
- [ ] Gerçek AdMob kimliklerini (dördü birlikte) `~/.gradle/gradle.properties` dosyasına yaz:
      `admobAppId`, `admobBanner`, `admobInterstitial`, `admobRewarded`. Verilmezse test kimlikleri kullanılır, gelir
      gelmez.
- [ ] `privacyPolicyUrl=https://…` özelliğini ver: oyun içindeki "Web'de aç" düğmesi bu adresi açar.
- [ ] **AdMob › Gizlilik ve mesajlaşma:** bu uygulama için bir **GDPR (AB/İngiltere/İsviçre) izin mesajı** oluştur ve
      yayımla. İstersen ABD eyaletleri mesajını da ekle.
  - Mesaj yoksa izin formu (UMP) hiç görünmez ve "Reklam Gizlilik Seçenekleri" düğmesi çıkmaz.
  - Google, Ocak 2024'ten beri AEA/İngiltere trafiği için sertifikalı bir izin platformu şart koşuyor.
  - UMP'nin hata ayıklama coğrafyası (EEA) ile dene.
- [ ] **app-ads.txt:** AdMob'un verdiği satırı Play'de bildirdiğin geliştirici web sitesinin kök dizinine
      (`https://alanadin/app-ads.txt`) koy.

## B. Mağaza girişi (Play Console › Store presence)
- [ ] Varsayılan dil: İngilizce (en-US) ya da Türkçe (tr-TR). Diğer 5 dili "Çeviri ekle" ile ekle.
- [ ] Her dil için `06_Store_Descriptions/<DİL>/` klasöründeki metinleri yapıştır:
      `title.txt`, `short_description.txt`, `full_description.txt`.
- [ ] Simge: `04_PlayStore/App_Icon/app_icon_512.png`.
- [ ] Öne çıkan görsel: `feature_graphic_tr.png` (Türkçe), `feature_graphic_en.png` (diğer diller).
- [ ] Telefon ekran görüntüleri: `04_PlayStore/Phone_Screenshots/<DİL>/` (8 adet). İlk 3'ü oynanış.
- [ ] Tablet ekran görüntüleri: `Tablet_7_Inch/<DİL>/` ve `Tablet_10_Inch/<DİL>/` (her birinde 5 adet).
- [ ] Görsel alternatif metinleri: `store_listing.md` içindeki "alt text" sütunu.
- [ ] Tanıtım videosu:
  1. `trailer_tr_vertical.mp4` / `trailer_en_vertical.mp4` (ya da yatay sürüm) dosyasını YouTube'a "Herkese açık" ya
     da "Liste dışı" olarak yükle.
  2. **Para kazanmayı kapat**, yerleştirmeye izin ver, yaş kısıtlaması koyma.
  3. URL'yi "Preview video" alanına gir. Oynatma listesi ya da kanal bağlantısı olmasın.
- [ ] Kategori: Oyun › Bulmaca. Etiketler: `store_listing.md` içinde.
- [ ] İletişim e-postası ve gizlilik politikası URL'si.

## C. Uygulama içeriği (Play Console › App content)
- [ ] Gizlilik politikası URL'si.
  - `09_Privacy_And_Legal/privacy_policy_<dil>.md` dosyalarını yayımla.
  - `[geliştirici e-posta adresi]` alanını doldur.
- [ ] Reklamlar: **Evet**.
- [ ] Reklam kimliği: **Evet** (reklam ve analiz).
- [ ] Veri güvenliği: `Data_Safety_Form.md` yanıtları.
- [ ] İçerik derecelendirmesi (IARC): `Content_Rating_and_Audience.md`.
- [ ] Hedef kitle: 13+ (öneri; açıklaması aynı dosyada).
- [ ] Ön plan hizmeti beyanı sorulursa: oyun ön plan hizmeti kullanmaz.

## D. Test
- [x] 160+ otomatik test geçti (ayrıntı: `08_Test_Reports/Test_Report.md`).
- [x] 6 dilde 13 ekran ve pencerede metin taşması yok (`LocalizedUiTest`).
- [ ] **Gerçek cihazda kısa deneme** (bu ortamda emülatör ya da cihaz yok):
  - ilk açılış dili,
  - Ayarlar'dan dil değiştirme,
  - can sayacı ve saat değiştirme,
  - test reklamları ("Test Ad" etiketi),
  - AB'de izin formu.
- [ ] Kapalı test (internal testing) kanalında birkaç kişiyle 1–2 gün oynatma.

## E. İsim ve marka
- [x] İngilizce ad: **Prismway**. Türkçe ad korundu. Araştırma: `02_Game_Name_Research`.
- [ ] Tescil başvurusundan önce bir marka vekilinden görüş al (EUIPO/WIPO/TÜRKPATENT bu ortamda sorgulanamadı;
      komşu marka "PRISM: LIGHT THE WAY").
- [ ] Sosyal medya kullanıcı adlarının (@prismway…) uygunluğunu kontrol et.

## F. Yayın sonrası
- [ ] Sosyal medya CTA sürümlerini ("Hemen Oyna!", "Play Now!") **oyun yayına girdikten sonra** paylaş.
- [ ] Google Mobile Ads SDK v25'in kullanımdan kalkma tarihi 30.06.2027: o tarihten önce GMA Next-Gen SDK'ya geç.
