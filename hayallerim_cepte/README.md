# Hayallerim Cepte

> **Hayal Et, Planla, Gerçekleştir.**

Hayallerini, hedeflerini, birikimlerini, alışkanlıklarını, anılarını ve başarılarını tek yerde yönetebileceğin, Android ve iOS için **tamamen çevrimdışı** kişisel yaşam planlayıcısı. Hesap, sunucu, bulut, reklam veya analitik yoktur; tüm veriler yalnızca cihazda saklanır.

- Mimari, ekran haritası, veri modeli, tasarım sistemi: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Yayın ve gerçek cihaz test listesi: [`docs/RELEASE_CHECKLIST.md`](docs/RELEASE_CHECKLIST.md)

## Modüller

| | |
|---|---|
| **Ana Sayfa** | Karşılama, günün sözü, özet istatistikler, seri, genel ilerleme, bugünkü görevler, yaklaşan hedefler, bahçe önizlemesi, haftalık grafik, hızlı erişim. Kartlar sıralanabilir/gizlenebilir. |
| **Hayallerim** | Sınırsız hayal; kapak + çoklu görsel, kategori (özel kategori dahil), hedef tarih, öncelik, bütçe, motivasyon, notlar, gizli hayal. Kart / liste / fotoğraflı pano / zaman çizelgesi; arama, filtre, sıralama. Arşiv. |
| **Hayal Yolculuğum** | Sürüklenerek sıralanan adımlar: açıklama, hedef tarih, fotoğraf, not, hatırlatıcı; eşit ya da ağırlıklı otomatik ilerleme. |
| **Hayal Panom** | Pinterest tarzı pano, kategoriler, motivasyon yazıları, sıralama, kolaj, galeriye kaydetme. |
| **Hedeflerim** | Günlük/haftalık/aylık/yıllık hedefler, alt görevler, tekrarlanan görevler, takvim ve liste, gecikenler, başarı geçmişi. |
| **Alışkanlıklar** | Haftalık takvim, seriler, tamamlanma oranı, hatırlatıcı, 7/21/30/100 gün meydan okumaları, rozetler. |
| **Hayal Kumbarası** | Birikim ekle/çek, işlem geçmişi, aylık hedef, grafik, hedefe ulaşma tahmini, para birimi + manuel kur, gelir/gider, tasarruf hesaplayıcısı. Kuruş hassasiyetinde tam sayı aritmetiği. |
| **Hayal Günlüğüm** | Yazı, fotoğraf, ses kaydı, ruh hâli, etiketler, önemli anlar, takvim, arama, şifreli özel günlükler. |
| **Gelecekteki Ben** | Mektup, fotoğraf, ses, hayal listesi ve hedeflerle zaman kapsülü; tarihinde bildirim. |
| **Hayal Bahçem** | Her hayal bir bitki: Tohum → Filiz → Fidan → Genç → Büyük → Çiçek açan → Meyve veren. 8 bitki türü, 2D çizim, kaydırılabilir bahçe, gece/gündüz. |
| **Başarılarım** | 15 rozet, puan ve seviye, rozet kartını galeriye kaydet/paylaş. |
| **Odaklanma** | Pomodoro (özelleştirilebilir), arka planda doğru süre, hedefe/hayale bağlı oturumlar, günlük/haftalık grafik. |
| **Yaşam Dengem** | 8 alan 1–10, radar grafik, geçmiş ve alan bazında değişim grafiği. |
| **Akıllı Planlayıcı** | "6 ayda 30 kitap okumak istiyorum" → aylık 5, haftalık 1–2, günlük plan; düzenlenebilir, tek dokunuşla hayal/hedef/görev/kumbara oluşturur. Kural tabanlıdır, yapay zekâ değildir. |
| **Güvenlik** | PIN, biyometrik, uygulama kilidi, gizli içerik, alan düzeyinde AES-256-GCM. |
| **Yedekleme** | Parolayla şifrelenmiş tek dosya; bütünlük kontrolü, üzerine yazma veya birleştirme. |
| **Bildirimler** | Günlük motivasyon, hedef/görev/alışkanlık/kumbara/kapsül hatırlatıcıları, önemli günler — tamamen yerel. |

## Teknolojiler ve sürümler

Flutter **3.50.0** (Dart 3.14) · Material 3 · flutter_riverpod 3.5 · go_router 18.1 · drift 2.35 + drift_flutter 0.3 (SQLite) · shared_preferences 2.5 · flutter_local_notifications 23.0 · flutter_secure_storage 11.2 · local_auth 3.0 · fl_chart 1.3 · cryptography 2.9 · decimal 3.2 · image_picker 1.2 · record 7.2 · audioplayers 6.8 · file_picker 13.4 · share_plus 13.3 · gal 3.0 · archive 4.7

Kesin sürümler `pubspec.lock` içindedir.

## Kurulum

```bash
cd hayallerim_cepte
flutter --version          # 3.50.x önerilir
flutter pub get
dart run build_runner build   # yalnızca tablolar değiştiğinde (üretilen dosya depoda var)
flutter run                   # bağlı cihaz/emülatör
```

## Test

```bash
flutter analyze
flutter test                                   # 42 birim + veritabanı + widget testi
flutter test integration_test/app_flow_test.dart -d <cihaz-id>   # gerçek cihazda uçtan uca
```

Test kapsamı: para ayrıştırma/biçimlendirme/kur (kayan nokta hatası yok), Akıllı Planlayıcı, ilerleme/bahçe aşaması/seri/seviye, bildirim zamanlama (günlük/haftalık/aylık/yıllık, iOS 60 sınırı), şifreleme ve PIN kilitlenmesi, hayal → adım → bahçe → rozet akışı, arşiv/kalıcı silme koruması, tekrarlı görevler, alışkanlık kuralları, kumbara bakiyesi, zaman kapsülü kilidi, **şifreli yedeğin farklı anahtarlı başka cihaza geri yüklenmesi ve birleştirme**, ilk açılış akışı, hızlı hayal ekleme, PIN kilidi, tüm bitki aşamalarının çizimi.

Tasarım kontrolü için örnek verili ekran görüntüleri: `flutter test tool/screenshots_test.dart` → `tool/screenshots/`.

GitHub Actions (`.github/workflows/hayallerim-cepte.yml`): analiz + testler, imzasız iOS derlemesi ve indirilebilir release APK.

## Platforma özgü ayarlar

### Android (`android/`)
- `MainActivity` → `FlutterFragmentActivity` (biyometrik doğrulama için).
- `minSdk` 24, core library desugaring açık (planlı bildirimler için).
- İzinler: `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `USE_BIOMETRIC`, `RECORD_AUDIO`, `WRITE_EXTERNAL_STORAGE` (yalnız API ≤ 28). **`INTERNET` yayında açıkça kaldırılır.**
- Planlı bildirim alıcıları (yeniden başlatmada yeniden planlama) tanımlı.
- `allowBackup=false` + `data_extraction_rules.xml`: veriler Google bulut yedeğine gitmez.
- Kesin alarm izni istenmez; bildirimler pil tasarrufu modunda birkaç dakika gecikebilir.
- Yayın imzası `android/key.properties` ile (bkz. RELEASE_CHECKLIST).

### iOS (`ios/`)
- En düşük sürüm iOS 15.
- Info.plist: kamera, fotoğraf kitaplığı, fotoğraf ekleme, mikrofon ve Face ID için Türkçe açıklamalar; geliştirme dili `tr`.
- `AppDelegate`: ön planda bildirim gösterimi için `UNUserNotificationCenter` temsilcisi.
- Bildirim izni ilk kurulumda ya da Ayarlar'dan açıldığında istenir.
- Anahtarlar Keychain'de `first_unlock_this_device` ile saklanır (iCloud Anahtar Zinciri'ne gitmez).

## Gerçek cihazda test

**Android:** Telefonda *Geliştirici seçenekleri › USB hata ayıklama*'yı açın, USB ile bağlayın, `flutter devices` ile görün, `flutter run --release` ile çalıştırın. APK'yı doğrudan kurmak için CI artefaktını indirip `adb install app-release.apk`.

**iPhone:** Mac + Xcode gerekir. `open ios/Runner.xcworkspace` → *Signing & Capabilities*'te Apple kimliğinizi seçin → iPhone'u bağlayın, *Ayarlar › Gizlilik › Geliştirici Modu*'nu açın → `flutter run --release`. İlk çalıştırmada *Ayarlar › Genel › VPN ve Cihaz Yönetimi*'nden geliştiriciye güvenin.

Ardından [`docs/RELEASE_CHECKLIST.md`](docs/RELEASE_CHECKLIST.md) içindeki 11 adımlık senaryoyu **uçak modunda** uygulayın.

## İkon

Uygulama ikonu vektör logodan üretilir:
```bash
flutter test tool/render_icon_test.dart && python3 tool/make_icons.py   # Pillow gerekir
```
