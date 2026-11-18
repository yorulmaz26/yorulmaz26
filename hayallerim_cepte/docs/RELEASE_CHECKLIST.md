# Yayın kontrol listesi

## Ortak
- [ ] `pubspec.yaml` içinde `version: X.Y.Z+N` güncellendi.
- [ ] `flutter analyze` sorunsuz, `flutter test` tamamı geçiyor.
- [ ] `dart run build_runner build` sonrası `app_database.g.dart` değişmiyor.
- [ ] Şema değiştiyse `schemaVersion` artırıldı ve `onUpgrade` adımı eklendi; eski sürümden güncelleme gerçek cihazda denendi.
- [ ] Uçak modunda tüm modüller denendi (aşağıdaki cihaz test senaryosu).
- [ ] Yedek al → uygulamayı sil → yeniden kur → geri yükle senaryosu her iki platformda denendi.
- [ ] Gizlilik politikası metni: "Veri toplanmaz; tüm veriler cihazda saklanır." (Mağaza zorunluluğu.)

## Android (Google Play)
- [ ] `android/key.properties` + yükleme anahtarı oluşturuldu (depoya **eklenmez**):
  ```
  storePassword=…
  keyPassword=…
  keyAlias=upload
  storeFile=/mutlak/yol/upload-keystore.jks
  ```
  Anahtar üretimi: `keytool -genkey -v -keystore upload-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`
- [ ] `flutter build appbundle --release`
- [ ] Play Console › Veri güvenliği: "Veri toplanmıyor, paylaşılmıyor". Uygulama `INTERNET` izni istemez.
- [ ] İzin açıklamaları: bildirim (POST_NOTIFICATIONS), mikrofon (ses notu), biyometrik.
- [ ] Hedef API düzeyi Play'in güncel şartını karşılıyor (`flutter.targetSdkVersion`).

## iOS (App Store)
- [ ] Xcode › Runner › Signing & Capabilities: ekip ve paket kimliği (`com.hayallerimcepte.app` ya da kendi kimliğiniz).
- [ ] `flutter build ipa --release`
- [ ] App Privacy: "Data Not Collected".
- [ ] Info.plist izin metinleri (kamera, fotoğraflar, fotoğraf ekleme, mikrofon, Face ID) Türkçe ve doğru.
- [ ] İhracat uyumluluğu (encryption): Uygulama yalnızca cihaz üzerinde standart AES ile kullanıcı verisini korur. App Store Connect'teki soruyu bu bilgiyle yanıtlayın; gerekirse `ITSAppUsesNonExemptEncryption` anahtarını Info.plist'e ekleyin (karar yayıncıya aittir).

## Gerçek cihaz test senaryosu (uçak modu açık)
1. İlk kurulum: tanıtım → isim + fotoğraf → bildirim izni → ana sayfa.
2. Hayal ekle (kapaklı, 3 adımlı, bütçeli) → adımları tamamla → bahçede ağacın büyüdüğünü gör → "Gerçekleştirdim" → rozet kutlaması.
3. Günlük, haftalık görev + tekrarlı görev; hatırlatıcıyı 2 dk sonraya kur, uygulamayı kapat, bildirimi bekle.
4. Alışkanlık + hatırlatıcı; telefonu yeniden başlat, bildirimin hâlâ geldiğini doğrula.
5. Kumbara: TL hedef, USD işlem (manuel kur), grafik ve tahmin.
6. Günlük: fotoğraf + ses kaydı + özel işaretli; uygulamadan çık, dön → özel içerik kilitli.
7. Zaman kapsülü: 2 dk sonrasına kur → kilitli → bildirim → aç.
8. Odaklanma: 1 dk'ya ayarla, başlat, uygulamayı arka plana at/kapat → bildirim → geri dön, oturum geçmişte.
9. Güvenlik: PIN + biyometrik + uygulama kilidi; 5 yanlış PIN → bekleme.
10. Yedekleme: dosyaya kaydet → geri yükle (birleştir ve üzerine yaz).
11. Ayarlar: koyu tema, vurgu rengi, en büyük sistem yazı boyutu, ekran döndürme / tablet.
