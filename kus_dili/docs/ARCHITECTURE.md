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

Özellik modülleri: `auth`, `profile`, `account`, `chat` (birebir ve grup),
`crypto` (Signal, cihazlar, güvenlik numarası), `privacy` (PIN, kilit, gizli
sohbet), `safety` (engelleme, şikâyet), `notifications`, `learn`, `translator`,
`legal` (kullanım koşulları).

**Kapsam dışı (bilinçli olarak kaldırıldı):** fotoğraf, video, belge, sesli
mesaj ve sesli/görüntülü arama. Uygulama yalnızca metin mesajlaşmasıdır; bu
yüzden kamera, mikrofon, galeri izni ve Firebase Storage kullanılmaz.

## 2. Teknoloji seçimleri

| Konu | Seçim | Not |
|---|---|---|
| UI | Flutter (Material 3, `material_ui`) | Android + iOS |
| Durum | Riverpod 3 (`Notifier`, `Provider`) | |
| Yönlendirme | GoRouter | Oturum durumuna göre `redirect` (Aşama 3) |
| Kimlik | Firebase Auth — telefon (SMS) | App Check (Play Integrity / App Attest) zorunlu |
| Veri | Cloud Firestore | Yalnızca şifreli mesaj gövdesi + asgari üst veri |
| Bildirim | FCM | **Yalnızca veri bildirimi**, içerik yok (bkz. §5) |
| Yerel güvenli depo | `flutter_secure_storage` (Keychain / Android Keystore) | Anahtarlar ve PIN özeti |
| Yerel önbellek | `EncryptedFilePayloadCache` | Çözülmüş mesajlar cihazda AES-256-GCM ile şifreli dosyada; anahtar Keystore/Keychain'de |
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
   anahtarlar yazılır: `keyBundles/{uid}/devices/{cihaz}` ve
   `…/devices/{cihaz}/prekeys/{id}`. Ön-anahtarlar 25'in altına düşünce 100
   yenisi eklenir.
2. Gönderen, alıcıyla oturumu yoksa bir ön-anahtarı atomik olarak tüketip
   (Firestore işlemi) oturum kurar. Mesaj paketi (orijinal metin + gösterim
   modu + kuş dili kuralı + yanıt bilgisi) **gönderen cihazda** şifrelenir.
   Firestore'a yalnızca şifreli zarf ve yönlendirme üst verisi yazılır;
   kurallar şifresiz gövdeyi (`encVersion` 0) reddeder.
3. Alıcı cihaz şifreyi çözer ve kuş dili dönüşümünü **kendi cihazında** yapar.
4. Signal bir mesajı yalnızca bir kez çözebilir (ileriye dönük gizlilik).
   Çözülen ve gönderilen içerik bu yüzden cihazda, anahtarı Keystore/Keychain'de
   duran **AES-256-GCM** ile şifreli bir önbellekte tutulur
   (`EncryptedFilePayloadCache`).
5. **Güvenlik numarası:** 60 haneli sayısal parmak izi; iki taraf karşılaştırır.
   Her iki tarafın **tüm cihazlarının** kimlik anahtarları hesaba katılır.
   Karşı taraf cihaz eklerse ya da bir cihazının anahtarı değişirse oturum
   yenilenir, numara değişir ve sohbette sarı uyarı bandı çıkar (ilk
   kullanımda güven + uyarı).
6. Tüm Signal durum değişiklikleri sıraya alınır; aynı mesajın eşzamanlı
   açılması tekilleştirilir.

**Çoklu cihaz:** Her kurulum ayrı bir Signal cihazıdır (rastgele cihaz
kimliği, kendi kimlik anahtarı ve oturumları). Hesaba en fazla **5 cihaz**
bağlanabilir (`keyBundles/{uid}.devices`, kurallarla zorunlu). Telefon
numarasıyla giriş yapılan yeni cihaz otomatik eklenir; hesabın diğer
cihazlarında "Hesabına yeni bir cihaz bağlandı" uyarısı ve karşı taraflarda
güvenlik numarası uyarısı çıkar. Birebir mesajda paket rastgele bir anahtarla
**AES-256-GCM** ile bir kez şifrelenir (ek doğrulama verisi: sohbet, gönderen,
gönderen cihaz); bu anahtar alıcının her cihazına ve gönderenin diğer
cihazlarına ayrı Signal oturumlarıyla iletilir (`{t:'md', sd, n, c, m, k:{'uid:cihaz': …}}`).
Yeni cihaz yalnızca bağlandıktan sonraki mesajları okur; geçmiş aktarılmaz.
**Bağlı cihazlar** ekranından cihaz kaldırılır: önce listeden çıkar, sonra
açık anahtarları silinir; kaldırılan cihaz bunu fark edince anahtarlarını ve
şifreli önbelleğini silip çıkış yapar.

**Gruplar (Aşama 8) — Signal "Sender Keys":** Her cihaz kendi gönderici
anahtarını üretir ve diğer üyelerin tüm cihazlarına ve kendi diğer cihazlarına
birebir Signal oturumlarıyla şifreleyerek dağıtır
(`chats/{g_…}/senderKeys/{gönderen}_{cihaz}_{dönem}`, değiştirilemez; her
cihazın payı yalnızca ona açılır). Grup mesajı bir kez şifrelenir. Grubun
cihaz kümesi (üyelik ya da bir üyenin cihazları), anahtarın son paylaşıldığı
kümeden farklıysa gönderen yeni dönem başlatır:
çıkarılan üye sonraki mesajları, yeni üye önceki mesajları okuyamaz. Anahtarı
henüz olmayan üye atlanır ve bir sonraki gönderimde yeniden denenir. En fazla
64 üye; kurucu ilk yönetici; yöneticiler ad/üye/yönetici değiştirir; herkes
ayrılabilir (son yönetici ayrılırken yerine bir üye geçer).

**Bilinen sınırlar (henüz yapılmadı):**

* Geçmiş mesajlar yeni cihaza aktarılmaz; yeniden kurulumda eski mesajlar o
  cihazda açılamaz ("bu cihaz bağlanmadan önce gönderildi"); yedekleme yok.
* Yeni cihaz eklemek için yalnızca SMS kodu yeterli (Signal'deki gibi mevcut
  cihazdan QR onayı yok). Bunun yerine diğer cihazlar ve karşı taraflar
  uyarılır; kullanıcı yabancı cihazı kaldırabilir. Cihaz listesi kimlik
  anahtarıyla imzalanmadığı için sunucu sahte cihaz ekleyebilir; bu da aynı
  uyarılarla ve güvenlik numarasıyla fark edilir, engellenmez.
* Kullanılmayan eski cihazlar (ör. sıfırlanan telefon) elle kaldırılana kadar
  listede kalır ve 5 cihaz sınırına sayılır.
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
`multi_device_test.dart`: iki cihaza teslim, kendi diğer cihazında görme,
sonradan bağlanan cihazın eski mesajı okuyamaması, grupta cihaz başına
anahtar, cihaz ekleyince uyarı, kaldırılan cihaza gönderilmemesi, 5 sınırı.
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

Sonradan eklenenler:

* `keyBundles/{uid}`: `{devices: [cihaz], updatedAt}` (en fazla 5);
  `keyBundles/{uid}/devices/{cihaz}`: açık anahtarlar + cihaz adı + son
  etkinlik; altında `prekeys/{id}`.
* `blocks/{uid}`: `{uids: […], updatedAt}` — engel listesi; yalnızca sahibi
  okur (kurallar `get()` ile okur). Birebir sohbette taraflardan biri
  diğerini engellediyse mesaj yazılamaz, sohbet açılamaz.
* `reports/{id}`: şikâyet; uygulamadan kimse okuyamaz (bkz. §6c).
* **Güvenlik kuralları:** yalnızca sohbet üyeleri okur/yazar; mesaj yalnızca
  göndereni tarafından düzenlenir (yeni şifreli sürüm) veya silinir.
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

**Bilinen sınırlar:** Ekran görüntüsü engeli Android'de `FLAG_SECURE` ile
çalışır (gizli sohbetlerde ve tek görüntülük mesajda; Gizlilik ayarından tüm
uygulamaya açılabilir); iOS'ta tam engel mümkün değildir. Alıcı, süreli/tek görüntülük mesajı
başka bir cihazla fotoğraflayabilir. Biyometri şu an kullanıcı arayüzü kilidi
olarak çalışır; önbellek anahtarı biyometriye bağlı değildir. Bildirimler
hiçbir zaman içerik göstermez (§6b).

## 6b. Bildirimler (Aşama 10a) ✅

* Cihaz FCM belirteci `pushTokens/{uid}` belgesinde (yalnızca sahibi ve Cloud
  Function okur; cihaz başına en fazla 10). Çıkışta bu cihazın belirteci silinir.
* `notifyNewMessage` (Cloud Function, `functions/`): yeni mesajda diğer
  üyelere bildirim gönderir. Sunucu içeriği bilmediği için bildirimde mesaj,
  gönderen veya grup adı **yoktur**: yalnızca "Yeni bir mesajın var" ve
  dokununca açılacak sohbet kimliği. Aynı sohbetin bildirimleri birleşir.
* Genel kapatma ve sohbet başına sessize alma; geçersiz belirteçler otomatik
  temizlenir. İzin verilmezse uygulama bildirimsiz çalışır.
* Sınır: içerikli bildirim (cihazda çözerek) iOS Notification Service Extension
  ve Android arka plan işleyicisi gerektirir; henüz yapılmadı. FCM'in yeni FID
  tabanlı kaydına geçiş, Admin SDK desteği olgunlaşınca yapılacak.

## 6c. Engelleme, şikâyet ve kullanım koşulları ✅

Mağaza kuralları (Google Play kullanıcı içeriği, App Store 1.2) gereği:

* **Engelleme:** sohbet menüsünden veya grupta mesajın uzun basma menüsünden.
  Birebir sohbette engel iki yönlüdür ve **sunucu kurallarıyla** zorunludur
  (mesaj ve yeni sohbet reddedilir). Grupta engellenen kişinin mesajları
  istemcide gizlenir ve ondan gelen grup mesajı için bildirim gitmez. Engellenen
  kişi listeyi göremez, bildirim almaz. Profil → Engellenen kişiler.
* **Şikâyet:** neden (spam, taciz, uygunsuz içerik, kimliğe bürünme, diğer),
  isteğe bağlı açıklama, isteğe bağlı "ayrıca engelle". Mesajlar uçtan uca
  şifreli olduğu için sunucu içeriği göremez; kullanıcı **onay verirse** son 5
  mesaj kendi cihazında çözülüp şikâyete eklenir (yalnızca o mesajlar).
  `reports/` uygulamadan okunamaz; moderasyon Firebase konsolundan yapılır.
* **Kullanım koşulları:** profil oluştururken bağlantı ve kabul metni; Profil
  ekranından ve oturum açmadan da açılır. Metin: `docs/KULLANIM_KOSULLARI.md`.

**Sınırlar:** Gruplarda engel sunucuda zorunlu değil (kurallar üye listesinde
döngü kuramaz); engellenen kişi aynı gruba eklenebilir, engelleyen gruptan
ayrılabilir. Moderasyon ekibi ve süreci (24 saat içinde inceleme sözü) yayın
öncesi kurulmalı.

## 6d. Kuş dili öğrenme modülü (Aşama 11) ✅

Mesajlaşmadan bağımsızdır: hesap ve internet gerektirmez (Firebase
yapılandırılmamış olsa bile açılır). İçerik `learn/domain/curriculum.dart`
içinde Türkçe yazılır; kuş dili karşılıkları motorla üretildiği için hatasızdır.

* Dersler: temel kural, selamlaşma, aile, duygular, günlük hayat, doğa.
* Alıştırmalar: Türkçe → kuş dili seçme, kuş dili → Türkçe seçme, kuş dilinde
  yazma (ipuçlu; kurala uyan her yazım kabul), karışık kelimelerden cümle kurma.
  Yanlış seçenekler öğretici hatalardan üretilir (başka varyant, eksik hece,
  yanlış sesli tekrarı).
* Mini test (10 soru, tüm derslerden), telaffuz ekranı ve çevirici çıktısını
  Türkçe sesle okuma (cihaz TTS).
* İlerleme cihazda: XP, seviye, doğruluk, tamamlanan dersler (%80), en iyi test
  puanı, günlük seri.

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
| 9 | Medya ve sesli mesaj | Kapsam dışı (kaldırıldı) |
| 10 | Bildirimler ve aramalar | ✅ içeriksiz bildirimler · aramalar kapsam dışı (kaldırıldı) |
| 11 | Öğrenme modülü | ✅ 6 ders, 4 alıştırma türü, mini test, telaffuz (TTS), ilerleme/seri; hesapsız ve çevrimdışı |
| 12 | Test, optimizasyon, yayın | ✅ yayın yapılandırması, ekran görüntüsü engeli, sayfalama, CI derlemeleri, yayın/gizlilik/mağaza belgeleri — yayın öncesi açık maddeler: docs/RELEASE.md |

Sonradan eklenenler: engelleme ve şikâyet (§6c), kullanım koşulları, çoklu
cihaz (§4), uygulama simgesi ve açılış ekranı (`assets/icon`).
