# Kuş Dili — Teknik Mimari

> "Sadece anlayan anlasın."

Bu doküman uygulamanın hedef mimarisini ve **güvenlik sınırlarını** tanımlar.
Her aşamada güncellenir. Durum sütunu yalnızca gerçekten çalışan ve testi
olan parçalar için "✅" gösterir.

## 0. Temel ilke: kuş dili ≠ şifreleme

| Katman | Ne yapar | Ne yapmaz |
|---|---|---|
| **Kuş dili motoru** | Metni göstermek için eğlenceli bir "gizli dile" çevirir. | Gizlilik sağlamaz. Kuralı bilen herkes (ve herhangi bir yazılım) çözebilir. |
| **Uçtan uca şifreleme (E2EE)** | Mesajı gönderici cihazında şifreler, yalnızca alıcı cihazlar çözer. Firebase açık metni görmez. | Ekranda ne gösterileceğine karışmaz. |

Bu yüzden:

1. Kuş dili dönüşümü **yalnızca cihazda** yapılır; sunucuda asla dönüşüm yapılmaz.
2. Gönderilen şey **orijinal Türkçe metin + görüntüleme modu/varyant** bilgisidir ve
   bu paketin tamamı E2EE ile şifrelenir. Alıcı cihaz çözdükten sonra metni
   kuş diline çevirip gösterir; "çöz" düğmesi orijinali gösterir.
3. Mesajlaşmada ters dönüşüme (tahmine) gerek kalmaz → belirsizlik riski sıfır.
   Ters dönüşüm motoru öğrenme modülü ve dışarıdan yapıştırılan metinler içindir.

## 1. Depo yerleşimi

```
kus_dili/
├─ lib/                          Flutter uygulaması
│  ├─ main.dart                  ProviderScope + runApp
│  ├─ app/                       Uygulama kabuğu
│  │  ├─ app.dart                MaterialApp.router
│  │  ├─ router.dart             GoRouter (Riverpod sağlayıcısı)
│  │  └─ theme/                  Marka renkleri ve tema
│  ├─ core/                      Ortak altyapı (hata tipleri, logger, crypto köprüsü…)
│  └─ features/<özellik>/
│     ├─ domain/                 Varlıklar, depo arayüzleri, use-case'ler (saf Dart)
│     ├─ data/                   Firebase/yerel veri kaynakları, depo uygulamaları
│     ├─ application/            Riverpod Notifier/Provider'lar
│     └─ presentation/           Ekranlar ve widget'lar
├─ packages/
│  └─ kus_dili_engine/           Saf Dart dönüşüm motoru (Flutter'sız test edilir)
├─ test/                         Uygulama testleri
└─ docs/ARCHITECTURE.md
```

Oturum akışı: `sessionStatusProvider` (Firebase hazır mı → oturum var mı →
profil var mı) GoRouter'ın `redirect` fonksiyonunu besler: `firebaseUnavailable`
→ kurulum ekranı, `signedOut` → `/auth`, `needsProfile` → `/profile/setup`,
`ready` → `/home`. Çevirici her durumda erişilebilir. Çevrimdışıyken önbellekte
profil bulunmaması "profil yok" sayılmaz (yanlışlıkla profil ekranına düşmemek
için).

Bağımlılık yönü: `presentation → application → domain ← data`. `domain` Firebase'i
bilmez; böylece iş kuralları sahte depolarla test edilebilir. (Sahte depolar
**yalnızca testlerde** kullanılır; uygulama gerçek Firebase'e bağlanır.)

Planlanan özellik modülleri: `auth`, `profile`, `contacts`, `chat`, `secret_chat`,
`groups`, `media`, `voice`, `calls`, `notifications`, `privacy`, `learn`, `translator`.

## 2. Teknoloji seçimleri

| Konu | Seçim | Not |
|---|---|---|
| UI | Flutter (Material 3, `material_ui`) | Android + iOS |
| Durum | Riverpod 3 (`Notifier`, `Provider`) | |
| Yönlendirme | GoRouter | Oturum durumuna göre `redirect` (Aşama 3) |
| Kimlik | Firebase Auth — telefon (SMS) | App Check (Play Integrity / App Attest) zorunlu |
| Veri | Cloud Firestore | Yalnızca şifreli mesaj gövdesi + asgari üst veri |
| Dosya | Firebase Storage | Medya istemcide şifrelenir, şifreli blob yüklenir |
| Bildirim | FCM | **Yalnızca veri bildirimi**, içerik yok (bkz. §5) |
| Arama | `flutter_webrtc` | Sinyalleşme Firestore, TURN sunucusu gerekli (coturn) |
| Yerel güvenli depo | `flutter_secure_storage` (Keychain / Android Keystore) | Anahtarlar ve PIN özeti |
| Yerel veritabanı | SQLite + SQLCipher (ör. `drift` + `sqlcipher_flutter_libs`) | Çözülmüş mesaj önbelleği şifreli tutulur |
| Biyometri | `local_auth` | |
| E2EE | Signal Protokolü (bkz. §4) | |

## 3. Kuş dili motoru (`packages/kus_dili_engine`) ✅

* Saf Dart; internet, Flutter veya platform kanalı kullanmaz.
* `KusDiliVariant`: hazır **G'li**, **F'li**, **C'li** ve kullanıcı tanımlı şablonlar
  (`z{v}`, `p{v}r`, sesli harfe özel kurallar). Şablonlar doğrulanır: yalnızca
  Türkçe harfler, en az bir sessiz harf, en fazla 6 harf / 2 `{v}`.
* `KusDiliCodec.encode`: Türkçe → Kuş Dili. Büyük/küçük harf (Türkçe İ/ı dahil),
  noktalama, boşluk, rakam, emoji (ZWJ, ten rengi, bayrak), düzeltme işaretli
  ve ayrık yazılmış karakterler korunur. URL, e-posta ve `@kullanıcı` adları
  dönüştürülmez.
* `KusDiliCodec.decode`: Kuş Dili → Türkçe. Sonuçla birlikte sorun listesi,
  sesli harf sayıları ve güven skoru döner.
* `looksEncoded`, `detectVariant`: metnin hangi varyantta yazıldığını tahmin eder.

### Ters dönüşümde belirsizlik nasıl çözüldü

Naif yaklaşım (`"aga" → "a"` metin değiştirme) bozulur: "Egegege" (Ege) büyük
harf yüzünden yanlış çözülür, "gagagaga" (gaga) gibi örtüşen dizilerde sonuç
şansa kalır.

Motor bunun yerine **soldan sağa deterministik bir ayrıştırıcı** kullanır:
kodlama, her kümenin kendi kodunun art arda eklenmesidir (sessiz → kendisi,
sesli V → V + hece(V)). Çözücü her kümede tek seçenekle karşılaşır: sesliyse
arkasındaki beklenen heceyi (büyük/küçük harf duyarsız) tüketir. Bu nedenle
**geçerli her kuş dili metninin tek bir Türkçe karşılığı vardır** ve
`decode(encode(x)) == x` her girdi için sağlanır (7 varyantta 21.000 rastgele
girdiyle test edilir).

Kalan gerçek belirsizlikler açıkça raporlanır, sessizce tahmin edilmez:

| Durum | Davranış |
|---|---|
| Girdi kuş dili değil / karışık | Uyan kısımlar çözülür, diğerleri olduğu gibi kalır; `missingInfix` sorunu + düşük güven skoru |
| Yarım yazılmış hece ("Meg") | `truncatedInfix` |
| Korunan bölüm aynı zamanda geçerli kodlama ("aga@x.ogo" ← "a@x.o") | Korunan yorum seçilir, `ambiguousProtectedSegment` uyarısı |
| Kısa metnin varyantı ("gaga") | Tespit eşiği (≥2 sesli, ≥%90 uyum); mesajlaşmada tespit yerine varyant mesajla taşınır |

## 4. Uçtan uca şifreleme (Aşama 7)

**Hedef protokol:** Signal Protokolü (X3DH/PQXDH anahtar anlaşması + Double
Ratchet; gruplar için Sender Keys).

**Kütüphane kararı (Aşama 7'de kesinleşecek):**

* Tercih: Signal'in resmî, denetlenmiş **libsignal** (Rust) kütüphanesi; Android'de
  Java/Kotlin, iOS'ta Swift bağlamaları üzerinden platform kanalıyla.
* Alternatif: `libsignal_protocol_dart` — saf Dart portu, kullanışlı ancak
  **bağımsız güvenlik denetiminden geçtiğine dair bilgi yok**; bu yüzden
  üretimde tercih edilmez.
* Kendi kriptografi protokolümüzü **yazmayacağız**.

**Akış:**

1. Kayıtta cihaz kimlik anahtarı, imzalı ön-anahtar ve tek kullanımlık
   ön-anahtarlar üretilir. Özel anahtarlar `flutter_secure_storage`'da kalır;
   yalnızca **açık** anahtar paketi `users/{uid}/devices/{deviceId}` altına yazılır.
2. Gönderici, alıcının her cihazı için oturum kurar ve şu paketi şifreler:
   ```json
   { "v": 1, "type": "text", "text": "Seni seviyorum",
     "display": { "mode": "kusDili", "variant": {"id": "g"} },
     "replyTo": null, "expiresInSec": null, "viewOnce": false }
   ```
3. Firestore'a yalnızca şifreli metin ve yönlendirme için gereken asgari üst veri yazılır.
4. Güvenlik numarası (safety number) karşılaştırma ekranı sunulur.

**Dürüst sınırlar:** Üst veri (kim, kiminle, ne zaman) sunucuda görünür. Kayıp
cihaz, ekran görüntüsü ve kötü amaçlı yazılım E2EE'nin kapsamı dışındadır.
Bağımsız güvenlik incelemesi yapılmadan uygulamada "güvenli" iddiası
**kullanılmayacak**.

> ⚠️ Aşama 4–6'daki mesajlaşma henüz E2EE içermez. Veri modeli baştan şifreli
> gövdeye hazır tasarlanır (`ciphertext`, `encVersion`), ancak Aşama 7
> tamamlanmadan uygulama gerçek kullanıcılara **yayınlanmamalıdır**.

## 5. Firestore veri modeli

Aşama 3'te uygulananlar (`firebase/firestore.rules`, emülatörde 15 test):

* `users/{uid}`: `username`, `displayName`, `createdAt` (sunucu zamanı).
  Telefon numarası profilde **tutulmaz**. Yalnızca görünen ad güncellenebilir.
* `usernames/{ad}`: `{uid}` — benzersizlik kilidi. Profil ile aynı işlemde
  yazılır; kurallar `getAfter()` ile ikisinin tutarlılığını doğrular. Kullanıcı
  başına tek ad, devredilemez, silinemez.
* Okuma/yazma yalnızca **telefonla doğrulanmış** hesaplara açık
  (`sign_in_provider == 'phone'`). Tanımsız her yol kapalı.
* Bilinen sınır: doğrulanmış her kullanıcı profilleri okuyabilir (kullanıcı
  adıyla arama için gerekli); bu, adların listelenebilmesi demektir. Rehberden
  telefonla arkadaş bulma, gizlilik nedeniyle ayrı ve hız sınırlı bir sunucu
  fonksiyonu olarak sonraki aşamalarda tasarlanacak.

Sonraki aşamalar için taslak:

```
users/{uid}                 username, displayName, photoPath, createdAt
  devices/{deviceId}        identityKey, signedPreKey, oneTimePreKeys[], fcmToken
usernames/{username}        uid                       (benzersizlik için)
chats/{chatId}              type: direct|group, members[], createdAt,
                            lastActivityAt  (ÖNİZLEME METNİ YOK)
  messages/{msgId}          senderId, senderDevice, sentAt, encVersion,
                            ciphertexts: {deviceId: base64}, expireAt?
  typing/{uid}              until (TTL)
userChatSettings/{uid}/chats/{chatId}
                            displayMode, variant, hidden, locked, muted
                            (yalnızca sahibi okur; hassas olanlar cihazda tutulur)
```

* **Güvenlik kuralları:** yalnızca sohbet üyeleri okur/yazar; mesaj
  oluşturulduktan sonra değiştirilemez (düzenleme = yeni şifreli sürüm).
* **Süreli mesaj:** `expireAt` + Firestore **TTL politikası** sunucudan siler;
  istemci süresi dolanı hemen gizler. Alıcının ekran görüntüsü alması
  engellenemez (Android'de `FLAG_SECURE` yardımcı olur, iOS'ta tam engel yok).
* **Bildirimler:** Cloud Function yalnızca `{chatId}` içeren *veri bildirimi*
  gönderir; uygulama mesajı cihazda çözer ve kullanıcının tercihine göre
  "Yeni mesaj" ya da içerikli yerel bildirim gösterir. Gizli sohbetlerde
  içerik asla gösterilmez.

## 6. Gizli sohbet ve uygulama kilidi (Aşama 7)

* PIN: düz metin saklanmaz; Argon2id/PBKDF2 özeti + tuz Keystore/Keychain'de.
  Deneme sınırı ve artan bekleme süresi.
* Biyometri: `local_auth`; başarıda Keystore'daki "sohbet kilidi" anahtarının
  kilidi açılır (biyometriye bağlı anahtar), yalnızca UI'da bayrak tutulmaz.
* Gizli sohbetler ana listeden çıkarılır; listeleme bilgisi şifreli yerel
  veritabanında tutulur.
* Uygulama arka plana geçince önizleme bulanıklaştırılır.

## 7. Aşama durumu

| # | Aşama | Durum |
|---|---|---|
| 1 | Proje kurulumu ve mimari | ✅ iskelet, tema, yönlendirme, Riverpod |
| 2 | Kuş dili motoru ve testleri | ✅ 81 birim testi |
| 3 | Kayıt / giriş (telefon) | ✅ kod + testler; gerçek projede deneme kullanıcıda (bkz. FIREBASE_SETUP.md) |
| 4 | Gerçek zamanlı bireysel mesajlaşma | — |
| 5 | Otomatik kuş dili mesajlaşma | — |
| 6 | Mesaj çözme ve dil değiştirme | — |
| 7 | Gizli sohbet + E2EE | — |
| 8 | Grup mesajlaşması | — |
| 9 | Medya ve sesli mesaj | — |
| 10 | Bildirimler ve aramalar | — |
| 11 | Öğrenme modülü | Çevirici ekranı hazır; alıştırmalar/testler sonra |
| 12 | Test, optimizasyon, yayın | — |
