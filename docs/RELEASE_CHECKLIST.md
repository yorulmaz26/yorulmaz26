# Yayın Kontrol Listesi

## 1. Firebase / Google Cloud
- [ ] Firebase projesi **Blaze** planında; bölge `europe-west1` (Functions, Firestore).
- [ ] Authentication → Phone etkin; SMS bölge politikası yalnızca Türkiye; test numaraları yayında kaldırıldı.
- [ ] Android uygulamaları: `com.kuaforumcepte` (+ geliştirme için `com.kuaforumcepte.debug`); **SHA-1/SHA-256**: debug, upload ve **Play App Signing** anahtarları eklendi.
- [ ] App Check: Play Integrity sağlayıcısı kayıtlı; **Firestore, Storage ve Functions için enforcement açık** (önce izleme modunda bir süre gözlemleyin).
- [ ] `firebase deploy --only firestore:rules,firestore:indexes,storage,functions,hosting` başarılı; indekslerin "Building" durumu bitti.
- [ ] Firestore TTL politikaları: `idempotencyKeys.expireAt`, `rateLimits.expireAt` (indeks dosyasında tanımlı; Console'da etkin olduğunu doğrulayın).
- [ ] Cloud Scheduler işleri oluştu: `scanAppointments` (her dakika), `dailyCleanup` (her gün 04:17).
- [ ] Bütçe uyarıları (Cloud Billing → Budgets) ve Functions hata uyarıları (Error Reporting) kuruldu.
- [ ] `hosting/public/.well-known/assetlinks.json` gerçek Play App Signing SHA-256 ile güncellendi; `https://<alan>/.well-known/assetlinks.json` erişilebilir; Play Console → App Integrity'de doğrulandı.
- [ ] `PLAY_STORE_URL` (gerekirse) Functions ortam değişkeni olarak ayarlandı.

## 2. Android derleme ve imzalama
- [ ] `keystore.properties` + upload anahtarı güvenli yerde (repoda değil); Play App Signing etkin.
- [ ] `versionCode`/`versionName` artırıldı (`app/build.gradle.kts`).
- [ ] `./gradlew :app:bundleRelease` → AAB; R8 sonrası duman testi (giriş, rezervasyon, onay, tamamlama, puanlama, bildirim tıklaması).
- [ ] **minSdk 26** (Android 8.0; `java.time`, adaptive icon, bildirim kanalları — cihazların ~%97'si), **targetSdk 36** (Play'in güncel hedef API şartı).
- [ ] Logo hazırsa `res/drawable/brand_logo` + `res/values/brand.xml → has_brand_logo=true`; uygulama ikonu güncellendi.

## 3. Play Console
- [ ] Veri güvenliği formu (`docs/PLAY_DATA_SAFETY.md`).
- [ ] Gizlilik politikası URL'si (hukuki incelemeden geçmiş KVKK aydınlatma metni + gizlilik politikası).
- [ ] **Hesap silme**: uygulama içi yol (Hesap → Hesabımı Sil) ve Play'in istediği web bağlantısı (destek e-postası/formu).
- [ ] İçerik derecelendirmesi, hedef kitle (18+ önerilir), reklam yok beyanı.
- [ ] Bildirim izni gerekçesi; konum izni yalnızca salon sahibinin "Bulunduğum yer" özelliği için (arka plan konum yok).
- [ ] Mağaza görselleri, kısa/uzun açıklama (Türkçe).

## 4. Hukuk (KVKK)
- [ ] Veri sorumlusu unvanı/iletişim, VERBİS gerekliliği değerlendirmesi.
- [ ] Aydınlatma metni ve kullanım koşulları hukuki incelemeden geçti; sürüm değişirse `functions/src/lib/validation.ts → LEGAL_VERSIONS` ve `LegalDocument` sabitleri güncellenir.
- [ ] Yurt dışına aktarım (Google altyapısı) için dayanak.
- [ ] Mali kayıt saklama süresi ve silme taleplerinde anonimleştirme yaklaşımı onaylandı.

## 5. Erişilebilirlik
- [ ] TalkBack ile: rol seçimi, OTP (6 haneli alan tek öğe olarak okunur), salon listesi, uzman/hizmet seçimi ("Seçildi" durumu), saat seçimi, özet, randevu işlemleri, yıldız puanlama.
- [ ] Yazı boyutu %200'de düzen bozulmuyor; dokunma hedefleri ≥48dp.
- [ ] Açık/koyu temada kontrast (WCAG AA); renk tek başına bilgi taşımıyor (durumlar metinle de yazılı).
- [ ] Carousel duraklatılabilir; otomatik geçiş arka planda durur.

## 6. Performans ve dayanıklılık
- [ ] Düşük bağlantıda: hata + "Tekrar Dene" durumları; rezervasyon onayında ağ kopması → aynı idempotency anahtarıyla tekrar.
- [ ] Görsel sıkıştırma (logo ≤1024 px, foto ≤1600 px, ~≤2 MB); liste sayfalama 20'şer.
- [ ] Baseline profile / başlangıç süresi ölçümü (isteğe bağlı).
- [ ] Firestore okuma maliyeti: salon listesi sayfalı; anlık dinleyiciler ekran görünürken (`WhileSubscribed`).

## 7. Testler
- [ ] CI yeşil: Android derleme + unit testler, Functions birim + Emulator entegrasyon, kural testleri.
- [ ] `./gradlew :app:connectedDebugAndroidTest` bir cihazda yeşil.
- [ ] Uçtan uca elle test senaryoları (README → "Uçtan uca test senaryosu").
