# Kuaförüm Cepte — iOS (SwiftUI)

Android uygulamasıyla **aynı Firebase projesini ve sunucu fonksiyonlarını** kullanan yerel iPhone uygulaması.
İş kuralları sunucudadır; iOS yalnızca arayüzdür.

## Durum
| Aşama | İçerik | Durum |
|---|---|---|
| 1 | Giriş (telefon + SMS), müşteri kaydı, salon arama/kod, salon sayfası, uzman/hizmet, saat seçimi, özet (başkası adına, indirim kodu, sadakat), randevularım, bilet, iptal / son 2 saatte arama, puanlama | Bu sürümde |
| 2 | Kuaför paneli: davetler, salon seçimi, randevular (onay/ret/geldi/gelmedi/tamamla/iptal), elden randevu, raporlar, hizmetlerim, çalışma saatleri (çakışma önizlemeli), ekibim ve davet, link/QR | Tamam |
| 3 | Moderatör paneli, salon kaydı (logo/fotoğraf), dükkan ayarları, kampanyalar, bildirimler (APNs), takvim, hesap silme | Sırada |

## Derleme
Xcode projesi [XcodeGen](https://github.com/yonaskolb/XcodeGen) ile `project.yml`'den üretilir:
```bash
brew install xcodegen
cd ios && xcodegen generate && open KuaforumCepte.xcodeproj
```
CI (`.github/workflows/ios.yml`) Mac sunucusunda derler ve iPhone simülatöründe testleri çalıştırır; Mac'iniz olması gerekmez.

## Firebase (iOS)
1. Firebase Console › Proje ayarları › **Uygulama ekle › iOS** › paket kimliği **`com.kuaforumcepte.ios`**.
2. **GoogleService-Info.plist** dosyasını indirin; içeriğini GitHub'da `GOOGLE_SERVICE_INFO_PLIST` gizli değişkenine yapıştırın.
   (Dosya depoya konmaz; yoksa uygulama "Firebase yapılandırılmadı" ekranını gösterir.)
3. Telefonla giriş: test sürümünde Console'daki **test numaraları** ek ayar olmadan çalışır. Gerçek SMS için
   Apple Developer hesabından **APNs anahtarı** oluşturup Firebase › Cloud Messaging › Apple uygulama yapılandırmasına yükleyin.

## TestFlight / App Store
Apple Developer Program üyeliği (yıllık ücretli) gerekir. Üyelik hazır olduğunda CI'ya imzalama ve TestFlight'a
yükleme adımı eklenecek (App Store Connect API anahtarı GitHub gizli değişkeni olarak).
