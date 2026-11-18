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

## 4. Uçtan uca şifreleme (Aşama 7a) ✅

**Protokol:** Signal Protokolü — X3DH anahtar anlaşması + Double Ratchet
(ileriye dönük gizlilik, ele geçirme sonrası kendini onarma). Kendi
kriptografi protokolümüzü yazmadık.

**Kütüphane:** [`libsignal_protocol_dart`](https://pub.dev/packages/libsignal_protocol_dart)
(Signal'in Java uygulamasının saf Dart portu). Bu ortamda derlenip test
edilebilen tek seçenek buydu.

> ⚠️ **Dürüst durum:** Bu port Signal'in resmî kütüphanesi değildir ve bağımsız
> bir güvenlik denetiminden geçtiğine dair bilgi bulamadım. Protokol doğru,
> uygulama testli; ama **denetlenmiş** değil. Yayından önce iki yoldan biri
> gerekir: (1) Signal'in resmî `libsignal` (Rust) kütüphanesine Android/iOS
> yerel bağlamalarıyla geçmek (arayüz `MessageSealer` olduğu için uygulamanın
> geri kalanı değişmez) veya (2) mevcut uygulamanın bağımsız bir güvenlik
> incelemesinden geçirilmesi. Bu yapılmadan uygulamada "güvenli/denetlenmiş"
> iddiası kullanılmamalıdır.

**Nasıl çalışır:**

1. İlk açılışta cihaz; kimlik anahtar çifti, kayıt kimliği, imzalı ön-anahtar
   ve 100 tek kullanımlık ön-anahtar üretir. Özel anahtarlar ve oturum
   durumları yalnızca cihazın güvenli deposunda (Android Keystore / iOS
   Keychain, `flutter_secure_storage`) tutulur. Sunucuya yalnızca **açık**
   anahtarlar yazılır: `keyBundles/{uid}` ve `keyBundles/{uid}/prekeys/{id}`.
   Ön-anahtarlar 25'in altına düşünce 100 yenisi eklenir.
2. Gönderen, alıcıyla oturumu yoksa bir ön-anahtarı atomik olarak tüketip
   (Firestore işlemi) oturum kurar. Mesaj paketi (orijinal metin + gösterim
   modu + kuş dili kuralı + yanıt bilgisi) **gönderen cihazda** şifrelenir.
   Firestore'a yalnızca `{t: tür, c: şifreli metin}` ve yönlendirme üst verisi
   yazılır; kurallar şifresiz gövdeyi (`encVersion` 0) artık reddeder.
3. Alıcı cihaz şifreyi çözer ve kuş dili dönüşümünü **kendi cihazında** yapar.
4. Signal bir mesajı yalnızca bir kez çözebilir (ileriye dönük gizlilik).
   Çözülen ve gönderilen içerik bu yüzden cihazda, anahtarı Keystore/Keychain'de
   duran **AES-256-GCM** ile şifreli bir önbellekte tutulur
   (`EncryptedFilePayloadCache`).
5. **Güvenlik numarası:** 60 haneli sayısal parmak izi; iki taraf karşılaştırır.
   Karşı tarafın kimlik anahtarı değişirse (ör. telefonu sıfırladı) oturum
   yenilenir ve sohbette sarı uyarı bandı çıkar (ilk kullanımda güven + uyarı).
6. Tüm Signal durum değişiklikleri sıraya alınır; aynı mesajın eşzamanlı
   açılması tekilleştirilir.

**Gruplar (Aşama 8) — Signal "Sender Keys":** Her üye kendi gönderici
anahtarını üretir ve diğer üyelere birebir Signal oturumlarıyla şifreleyerek
dağıtır (`chats/{g_…}/senderKeys/{gönderen}_{dönem}`, değiştirilemez; her
üyenin payı yalnızca ona açılır). Grup mesajı bir kez şifrelenir. Üyelik,
anahtarın son paylaşıldığı kümeden farklıysa gönderen yeni dönem başlatır:
çıkarılan üye sonraki mesajları, yeni üye önceki mesajları okuyamaz. Anahtarı
henüz olmayan üye atlanır ve bir sonraki gönderimde yeniden denenir. En fazla
64 üye; kurucu ilk yönetici; yöneticiler ad/üye/yönetici değiştirir; herkes
ayrılabilir (son yönetici ayrılırken yerine bir üye geçer).

**Bilinen sınırlar (henüz yapılmadı):**

* Kullanıcı başına **tek cihaz**. İkinci cihaz/yeniden kurulumda eski mesajlar
  o cihazda açılamaz ("Bu mesaj bu cihazda açılamıyor"); yedekleme yok.
* İmzalı ön-anahtar periyodik olarak döndürülmüyor.
* Kimlik değişince mesaj gönderimi engellenmiyor, yalnızca uyarı veriliyor.
* Üst veri (kim, kiminle, ne zaman) sunucuda görünür. Kayıp cihaz, ekran
  görüntüsü ve kötü amaçlı yazılım E2EE'nin kapsamı dışındadır.
* Mesaj araması cihazdaki şifreli önbellekte yapılacak; henüz arayüzü yok.

**Testler:** iki gerçek Signal istemcisi aynı sahte Firestore üzerinden
konuşur (`test/features/crypto/e2ee_test.dart`): sunucuda açık metin olmaması,
karşılıklı yazışma, sıra dışı ve eşzamanlı çözme, ileriye dönük gizlilik,
bozulmuş şifreli metnin reddi, yeniden başlatma, karşı tarafın yeniden
kurulumu, güvenlik numarasının iki tarafta eşitliği, ön-anahtar yenileme.
Uçtan uca arayüz testleri de gerçek şifrelemeyle çalışır.

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

Aşama 4–6'da uygulananlar (kurallar emülatörde 31 testle doğrulanır):

* `chats/{d_<uid1>_<uid2>}`: `type`, `members` (sıralı 2 üye), `createdAt`,
  `lastActivityAt`, `lastSenderId`, `readAt{uid: zaman}`. **Önizleme metni yok.**
  Kimlik üyelerden türetilir; aynı iki kişi için tek sohbet olur.
* `chats/{id}/messages/{id}`: `senderId`, `sentAt` (sunucu zamanı), `encVersion`,
  `body` (mühürlü paket, ≤64 KB), `deleted`, `editedAt`. Mesaj kimliği istemcide
  üretilir (tekrar denemede çift mesaj olmaz). Silme = içeriği boşaltma.
* `users/{uid}/chatSettings/{chatId}`: gönderim modu, varyant, görünüm; yalnızca sahibi.
* Mesaj gövdesi `MessageSealer` arayüzünden geçer: Aşama 7a'dan itibaren
  `SignalSealer` (`encVersion` 1). Kurallar yalnızca `encVersion` 1'i kabul eder;
  geliştirme döneminden kalan şifresiz mesajlar yalnızca okunabilir.
* Mesaj araması sunucuda yapılamaz (gövde şifreli olacak); Aşama 7'deki şifreli
  yerel veritabanında cihaz üzerinde yapılacak.

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

## 6. Gizli sohbet ve uygulama kilidi (Aşama 7b) ✅

* **PIN:** 4-8 rakam. Düz metin saklanmaz: PBKDF2-HMAC-SHA256 (210.000 tur,
  16 bayt rastgele tuz) özeti Keystore/Keychain'de; sabit süreli karşılaştırma.
  5 hatalı denemeden sonra 30 sn bekleme, her hatada iki katı (en fazla 1 saat);
  sayaç da güvenli depoda, uygulamayı kapatıp açmak sıfırlamaz.
* **Biyometri:** `local_auth` (parmak izi / Face ID), PIN'in yanında isteğe bağlı.
* **Kilitli sohbet:** açmak için PIN/biyometri. **Gizli sohbet:** ana listeden
  çıkar, Profil → Gizlilik → Gizli sohbetler (PIN ile) altında görünür.
  **Uygulama kilidi:** açılışta ve arka plandan dönüşte.
* Hangi sohbetin kilitli/gizli olduğu **yalnızca cihazda** (güvenli depoda)
  tutulur; sunucuya yazılmaz. Uygulama arka plana geçince açılmış tüm kilitler
  kapanır; uygulama değiştiricide içerik örtülür.
* **Süreli mesaj:** sohbet başına (Kapalı / 30 sn / 5 dk / 1 sa / 1 gün / 1 hafta).
  Süre şifreli pakette ve sunucuda `expireAt` alanında; istemci süresi dolanı
  anında gizler, sunucudan silme **Firestore TTL politikası** ile olur
  (kurulum: FIREBASE_SETUP.md §5). Kurallar en fazla 8 gün sonrasını kabul eder.
* **Tek görüntülük mesaj:** alıcı açıp kapatınca içerik sunucudan (kurallar
  yalnızca alıcıya bu silmeye izin verir) ve cihazdaki şifreli önbellekten
  silinir. Signal anahtarları tek kullanımlık olduğu için yeniden çözülemez.
* Mesaj önizlemesi hiçbir yerde tutulmaz (sohbet listesi önizlemesizdir).

**Bilinen sınırlar:** Ekran görüntüsü engellenmiyor (Android `FLAG_SECURE`
henüz eklenmedi; iOS'ta tam engel yok). Alıcı, süreli/tek görüntülük mesajı
başka bir cihazla fotoğraflayabilir. Biyometri şu an kullanıcı arayüzü kilidi
olarak çalışır; önbellek anahtarı biyometriye bağlı değildir. Bildirimlerde
içerik gizleme Aşama 10'da (bildirimler) yapılacak.

## 7. Aşama durumu

| # | Aşama | Durum |
|---|---|---|
| 1 | Proje kurulumu ve mimari | ✅ iskelet, tema, yönlendirme, Riverpod |
| 2 | Kuş dili motoru ve testleri | ✅ 81 birim testi |
| 3 | Kayıt / giriş (telefon) | ✅ kod + testler; gerçek projede deneme kullanıcıda (bkz. FIREBASE_SETUP.md) |
| 4 | Gerçek zamanlı bireysel mesajlaşma | ✅ metin, yanıtlama, düzenleme, herkesten silme, okundu bilgisi (yazıyor/çevrimiçi göstergesi henüz yok) |
| 5 | Otomatik kuş dili mesajlaşma | ✅ sohbet başına gönderim modu ve varyant (özel kural dahil), yazarken canlı önizleme |
| 6 | Mesaj çözme ve dil değiştirme | ✅ dokununca animasyonlu çözme, görünüm tercihi (kuş dili / ikisi / Türkçe) |
| 7 | Gizli sohbet + E2EE | ✅ 7a: Signal ile E2EE, güvenlik numarası (denetim bekliyor, bkz. §4) · 7b: PIN/biyometri, kilitli ve gizli sohbet, uygulama kilidi, süreli ve tek görüntülük mesaj |
| 8 | Grup mesajlaşması | ✅ Sender Keys ile E2EE, üyelik değişince anahtar yenileme, yönetici işlemleri, ayrılma |
| 9 | Medya ve sesli mesaj | — |
| 10 | Bildirimler ve aramalar | — |
| 11 | Öğrenme modülü | Çevirici ekranı hazır; alıştırmalar/testler sonra |
| 12 | Test, optimizasyon, yayın | — |
