# Reklam Beyanı

**Uygulama:** Gölgen Seni Yakalamasın / Doppelglow (geçici) — `com.golgen.oyun` — v1.1.0
**Reklam ağı:** Google Mobile Ads SDK (AdMob) + Google User Messaging Platform (UMP, EEA/UK onayı)
**Play Console — Contains ads:** **Evet**

## 1. Reklam yerleşimleri

| Format | Nerede | Ne zaman |
|---|---|---|
| Banner | **Yalnızca menü ekranlarında** (ana menü, tur sonu/menü ekranları) | Oynanış ekranında **asla** |
| Geçiş (Interstitial) | **Yalnızca turlar arasında** (tur bittikten sonra, yeni tur başlamadan önce) | Sıklık sınırlarına tabi |
| Ödüllü video (Rewarded) | Kullanıcı açıkça "Reklam izle, +1 can" seçeneğine dokunursa | **Tamamen isteğe bağlı** |

**Oynanış sırasında hiçbir reklam gösterilmez.** Reklamlar oynanışı kesmez, beklenmedik anda açılmaz ve yanlışlıkla tıklamaya yol açacak şekilde kontrollerin yanına yerleştirilmez.

## 2. Sıklık sınırları (frequency capping)

- İlk **3 oyunda geçiş reklamı yok**.
- Geçiş reklamı **en fazla her 3 turda bir**.
- İki geçiş reklamı arasında **en az 150 saniye**.
- **12 saniyeden kısa turlar sayılmaz** (hızlı yeniden denemeler reklamı tetiklemez).
- Ödüllü reklam: **+1 can**, **günde en fazla 3 kez**.

## 3. Ödüllü reklamlar

- Kullanıcıya ödül (+1 can) önceden açıkça belirtilir.
- İzlemek zorunlu değildir; reddetmek oyunu engellemez.
- Ödül şans unsuru içermez (sabit ödül; kumar/ganimet kutusu yok).
- Ödül yalnızca reklam tamamlandığında (SDK ödül geri çağrısı) verilir.

## 4. Onay (UMP)

AEA/Birleşik Krallık (ve İsviçre) kullanıcılarına reklam yüklenmeden önce UMP onay formu gösterilir. Reklam istekleri onay durumuna göre yapılır. AdMob konsolunda GDPR mesajı (ve gerekiyorsa ABD eyalet gizlilik mesajları) yapılandırılmalıdır.

## 5. TEST kimliklerinin değiştirilmesi — YAYIN ÖNCESİ ZORUNLU

Mevcut derlemedeki AdMob kimlikleri **Google'ın TEST kimlikleridir**. Gerçek kimliklerle yayınlamadan önce Gradle özellikleriyle değiştirilmelidir:

```bash
./gradlew bundleRelease \
  -PadmobAppId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY \
  -PadmobBannerId=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ \
  -PadmobInterstitialId=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ \
  -PadmobRewardedId=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
```

(`X/Y/Z` değerleri yer tutucudur; gerçek kimlikleri AdMob konsolundan alın.)

Kontrol listesi:
- [ ] AdMob'da uygulama ve 3 reklam birimi (banner, interstitial, rewarded) oluşturuldu
- [ ] Release derlemesi gerçek kimliklerle üretildi; test kimlikleri release'te kalmadı
- [ ] Geliştirme sırasında gerçek reklamlara **kendi cihazınızdan tıklamayın** (test cihazı tanımlayın) — geçersiz trafik hesabı askıya aldırabilir
- [ ] `app-ads.txt` dosyası geliştirici web sitesinde yayımlandı ve Play mağaza kaydındaki web sitesiyle eşleşiyor
- [ ] AdMob'da UMP/GDPR mesajı yayımlandı
- [ ] Reklam kimliği beyanı (Advertising ID) Play Console'da "Evet" olarak yapıldı

## 6. Politika uyumu

Uygulama; Google Play Reklam politikası, AdMob program politikaları ve "Better Ads" standartlarına uygun olacak şekilde tasarlanmıştır (oynanış ortasında reklam yok, tam ekran reklamlar doğal geçiş noktalarında, sıklık sınırlı, ödüllü reklam isteğe bağlı).

---
*Yayın öncesi hukuki inceleme önerilir.*
