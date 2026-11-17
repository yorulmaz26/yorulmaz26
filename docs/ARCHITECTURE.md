# Kuaförüm Cepte — Mimari ve Veri Tasarımı (v1)

Bu belge uygulamanın bağlayıcı teknik sözleşmesidir. Kod, kurallar ve Cloud Functions bu belgeye uymak zorundadır; değişiklik gerekiyorsa önce burası güncellenir.

---

## 1. Mimari kararlar

| # | Karar | Gerekçe |
|---|-------|---------|
| K1 | **Android native**: Kotlin 2.2, Jetpack Compose + Material 3, Navigation Compose (type-safe route), Hilt, Coroutines/Flow, DataStore, Room (yalnızca önbellek). | İstenen teknoloji yığını. |
| K2 | **Katmanlar**: `core:domain` (saf Kotlin/JVM, Android bağımlılığı yok) → `app` içinde `data` (repository impl., Firebase) → `feature/*` (ViewModel + Compose). | Domain kuralları JVM'de hızlı unit test edilir; UI ve Firebase değiştirilebilir. |
| K3 | **Yetkili sunucu = Cloud Functions (TypeScript, Node 22, bölge `europe-west1`)**. Randevu oluşturma/durum değişikliği, salon/personel oluşturma, finans, puanlama, takvim değişikliği etkisi **yalnızca** callable fonksiyonlarla yapılır. | İstemci ücret, durum, sahiplik, puan, finans alanlarını değiştiremez. Firestore kuralları bu koleksiyonlara istemci yazımını tamamen kapatır. |
| K4 | **Müsaitlik algoritmasının tek doğru kaynağı sunucudadır** (`functions/src/domain/availability.ts`). İstemci slotları `getAvailability` ile alır; gösterilen slot garanti değildir, `createAppointment` transaction içinde yeniden doğrular. | Çift kod tabanında algoritma kayması riskini ortadan kaldırır. |
| K5 | **Çakışma önleme**: Her uzman × yerel gün için tek bir `staffDayLocks` dokümanı. Randevu oluşturma transaction'ı bu dokümanı okur, aralık çakışması kontrol eder ve randevu ile birlikte yazar. Firestore transaction'ı aynı dokümana eşzamanlı yazımları serileştirir → aynı uzmana çakışan iki randevu oluşamaz. | Ölçeklenebilir, gün başına tek kilit; farklı uzmanlar birbirini bloklamaz. |
| K6 | **Tek aktif randevu kuralı**: `customerLocks/{uid}` dokümanı aynı transaction'da okunur/yazılır. | Müşterinin iki cihazdan eşzamanlı iki randevu açması engellenir. |
| K7 | **Para**: `Long` kuruş (`priceKurus`), para birimi sabit `TRY`. Randevuda hizmet fiyat/süre **snapshot** olarak saklanır. | Kayan nokta hatası yok; geçmiş fiyat korunur. |
| K8 | **Zaman**: Saklama UTC `Timestamp`; iş kuralları `Europe/Istanbul` (IANA bölgesi ile, sabit +3 varsayılmaz). Dönem anahtarları yerel tarihle üretilir. | Yaz/kış saati değişse bile doğru kalır. |
| K9 | **Finans**: Değişmez `financialLedger` hareketleri + aynı transaction'da güncellenen `reportAggregates` (gün/hafta/ay × salon/uzman). Sayaçlar sıfırlanmaz; dönem anahtarı değişir. Tamamlama kaydının ID'si `completion_{appointmentId}` → ikinci "tamamla" çift kayıt üretemez. Düzeltme = ters/düzeltme hareketi. | Geçmiş raporlar korunur, idempotent. |
| K10 | **Rol modeli**: Telefon rol vermez. Roller, Firestore'daki üyelik dokümanlarından (`salons/{id}/members/{uid}`) türetilir; kurallar `get()/exists()` ile denetler. Custom claim kullanılmaz (çoklu salon üyeliği ve anında iptal için). | Bir kişi hem müşteri hem birden çok salonda personel olabilir. |
| K11 | **Kimlik**: Firebase Auth telefon OTP. SMS kodu uygulama tarafından üretilmez/doğrulanmaz. Geliştirmede Firebase test telefonları veya Auth Emulator. | Güvenlik. |
| K12 | **App Check**: Release'te Play Integrity, debug'da Debug provider. Callable'lar `enforceAppCheck: true`. | Bot/OTP suistimali. |
| K13 | **Bildirim**: FCM + her zaman yazılan uygulama içi kutu (`users/{uid}/notifications`). Push yalnızca tetikleyici; gerçek içerik kutudan okunur. Kilit ekranına kişisel veri yazılmaz. | İzin reddi/çevrimdışı durumda kayıp yok. |
| K14 | **Zamanlanmış işler**: Cloud Scheduler ile 1 dakikalık tarayıcı (`onSchedule`) — PENDING süre aşımı, 2 saat hatırlatma, "Müşteri geldi mi?". Her gönderim randevu dokümanındaki bayrakla idempotent. Ayrıca durum geçişlerinde `pendingExpiresAt` anlık denetlenir (tarayıcı gecikse bile geç onay reddedilir). | Basit, güvenilir; v2'de Cloud Tasks'a geçilebilir. |
| K15 | **Demo/Mock modu yok.** Geliştirme Firebase Emulator Suite ile yapılır (`local.properties` → `useFirebaseEmulator=true`). Üretim build'i emülatöre bağlanamaz (yalnızca `debug` build type'ta etkin). | Üretim ve test net ayrımı. |

---

## 2. Rol–yetki matrisi

Roller: **CUSTOMER** (oturumlu herkes müşteri olabilir), **OWNER** (salon sahibi; aynı zamanda randevu alınabilir uzman), **STAFF** (aktif personel). Yetki her zaman `salonId` bağlamında değerlendirilir.

| İşlem | Müşteri | Personel (kendi) | Salon sahibi |
|---|---|---|---|
| Aktif salonları/kamuya açık salon bilgisini görmek | ✅ | ✅ | ✅ |
| Randevu oluşturmak (müşteri olarak) | ✅ (tek aktif) | ✅ (müşteri rolüyle) | ✅ (müşteri rolüyle) |
| Kendi randevusunu (müşteri) görmek/iptal (≥2 saat) | ✅ | — | — |
| Kendine atanmış randevuları görmek | — | ✅ | ✅ |
| Salondaki tüm randevuları görmek | — | ❌ | ✅ |
| Randevu onay/ret | — | ✅ kendi | ✅ tüm salon |
| Randevu iptal (salon tarafı, gerekçeli) | — | ✅ kendi | ✅ tüm salon |
| Uzman yeniden atama | — | ❌ | ✅ |
| Geldi / Tamamla / Gelmedi | — | ✅ kendi | ✅ tüm salon |
| Fiyat düzeltme (denetim kayıtlı) | — | ✅ kendi, gerekçeli | ✅ tüm salon |
| Müşteri telefonu görmek | — | ✅ yalnızca kendi randevusunda | ✅ salon randevularında |
| Kendi çalışma saati / mola / izin / özel kapama | — | ✅ | ✅ |
| Kendi hizmetleri (CRUD) | — | ✅ | ✅ |
| Başka personelin hizmet/takvimi | — | ❌ | 👁 görüntüleme (düzenleme ❌) |
| Dükkan ayarları, logo/galeri, salon kapalı günleri | — | ❌ | ✅ |
| Personel davet/pasif/iptal | — | ❌ | ✅ |
| Salon finans toplamları | — | ❌ | ✅ |
| Kişisel finans/işlem raporu | — | ✅ yalnızca kendi | ✅ herkesinki |
| Paylaşım linki / QR / salon kodu | — | 👁 salon kodu | ✅ |
| Değerlendirme yapmak | ✅ yalnızca COMPLETED randevusu, 1 kez | — | — |

Yetki matrisi Kotlin'de `core/domain/.../auth/PermissionPolicy.kt` (UI görünürlüğü), sunucuda `functions/src/domain/permissions.ts` (bağlayıcı) ve `firestore.rules` (okuma/yazma) ile uygulanır.

---

## 3. Ekran haritası ve navigasyon

```
Splash ──► (Firebase yapılandırılmamış) ─► FirebaseSetupRequired
   │
   ├─► (oturum + Beni hatırla + son rol) ─► ilgili ana grafik
   └─► RoleSelection
          ├─ Müşteri Girişi ──► Auth(role=CUSTOMER): PhoneEntry → OtpVerify → [yeni ise] CustomerRegister(+KVKK/rıza) → CustomerHome
          └─ Kuaför/Uzman  ──► Auth(role=BUSINESS): PhoneEntry → OtpVerify →
                                 ├─ bekleyen davet var → InvitationAccept → BusinessHome
                                 ├─ birden çok salon → ActiveSalonPicker → BusinessHome
                                 ├─ üyelik var → BusinessHome
                                 └─ yok → SalonOwnerRegister (çok adımlı: kişi → mekan → adres → görseller → özet) → BusinessHome

CustomerHome (alt menü)
  ├─ Randevu Al: SalonList → SalonDetail → (Uzman seç → Hizmet seç) → SlotPicker → BookingSummary → BookingSuccess
  │      (aktif randevu varsa: ActiveAppointmentBlocker)
  ├─ Randevularım: Aktif / Geçmiş → AppointmentDetail(Bilet) → Cancel / Review / Takvime ekle
  └─ Salon Bilgileri: (seçili salon yoksa boş durum) → SalonInfo (harita, telefon, saatler)

BusinessHome (alt menü; aktif salon bağlamında)
  ├─ İstatistikler: [OWNER] Paylaşım linki, Salon kodu, QR, Salon dönem kartları, Personel kırılımı
  │                 [STAFF] Kendi dönem kartları, salon kodu (bilgi)
  ├─ Bilgileri Yönet: Çalışma saatleri, İzin günleri, Özel kapamalar, Hizmetlerim,
  │                 [OWNER] Dükkan Ayarları, Salon kapalı günleri, Stilist Yönetimi, Logo & Fotoğraflar
  └─ Randevular: [OWNER] Benim Randevularım | Çalışan Randevuları ; [STAFF] Randevularım → AppointmentDetail(işlemler)

Global: NotificationInbox, Settings (tema, Beni hatırla, çıkış, hesap silme talebi, gizlilik/KVKK), deep link hedefleri:
  https://<host>/s/{salonCode}        → SalonDetail
  kuaforumcepte://appointment/{id}    → AppointmentDetail (bildirim tıklaması)
```

Her ekran: `Route` (serializable) + `ViewModel` + `UiState` (`Loading | Content | Empty | Error`) + repository. Seçili salon (müşteri) ve aktif salon (işletme) tek kaynaktan, DataStore'da saklanır.

---

## 4. Firestore veri modeli

Tüm zaman alanları `Timestamp` (UTC). Tüm dokümanlarda `createdAt`, `updatedAt`. Salonlara ait dokümanlarda `salonId`, uzmana ait olanlarda `staffUid`. `timezone` salon dokümanında (`"Europe/Istanbul"`). `🔒` = yalnızca Cloud Functions yazar.

### 4.1 `users/{uid}` — özel profil
| Alan | Tip | Not |
|---|---|---|
| firstName, lastName | string | Kullanıcı düzenleyebilir (ad/soyad) |
| phoneE164 🔒 | string | Auth token'dan sunucuca yazılır |
| consents 🔒 | map `{kvkkVersion, explicitConsentVersion, acceptedAt}` | |
| createdAt, updatedAt | timestamp | |
Okuma: yalnızca sahibi. Alt koleksiyonlar:
- `users/{uid}/devices/{installationId}` — `{token, platform, updatedAt}`; sahibi yazar.
- `users/{uid}/notifications/{id}` 🔒 — `{type, title, body, appointmentId?, salonId?, read, createdAt}`; sahibi okur, yalnızca `read` alanını güncelleyebilir.
- `users/{uid}/memberships/{salonId}` 🔒 — `{salonId, salonName, role, status}`; hızlı "salonlarım" listesi (salon üyeliğinin aynası).

### 4.2 `salons/{salonId}` — kamuya açık salon
`name, nameLower, type (BEAUTY_SALON|MEN_HAIRDRESSER|WOMEN_HAIRDRESSER|UNISEX_HAIRDRESSER), businessPhoneE164, city, district, addressLine, description, location {lat,lng}?, logoUrl, logoPath, photos [{url,path}] (≤5), ownerUid 🔒, ownerDisplayName 🔒, code 🔒 (6 hane), status 🔒 (ACTIVE|SUSPENDED|TEMP_CLOSED*), ratingSum 🔒, ratingCount 🔒, ratingAvg 🔒, weeklyClosedDays [1..7] (ISO, 1=Pzt), bookingSettings 🔒 {slotMinutes:15, horizonDays:60, pendingTtlMinutes:30, customerCancelCutoffMinutes:120, minLeadMinutes:30}, timezone 🔒`

*`TEMP_CLOSED` salon sahibince istenebilir (fonksiyon üzerinden).
Okuma: herkes (oturumlu). İstemci yazımı kapalıdır: profil `updateSalonProfile`, görseller `updateSalonMedia`, kapalı günler `updateSalonClosures` fonksiyonlarıyla güncellenir (il/ilçe doğrulaması, arama anahtarı, üyelik aynası tutarlılığı ve görsel dosya yaşam döngüsü için).

Alt koleksiyonlar:
- `salons/{salonId}/members/{uid}` 🔒 — kamuya açık uzman kartı: `{uid, role OWNER|STAFF, status ACTIVE|INACTIVE|LEFT, firstName, lastName, specialty, ratingSum, ratingCount, ratingAvg, acceptsBookings, joinedAt}`. Telefon **yok**.
- `salons/{salonId}/members/{uid}/schedule/weekly` 🔒 — `{days: {"1":{shifts:[{start:"09:00",end:"13:00"}], breaks:[{start,end}]}, ...}, weeklyDaysOff:[...], version}`; kamuya açık (çalışma saatleri Salon Bilgileri'nde gösterilir). Yazım `updateWeeklySchedule` ile (etki analizi).
- `salons/{salonId}/members/{uid}/closures/{id}` 🔒 — kişisel tarih kapamaları `{startDate:"YYYY-MM-DD", endDate, note}`; okuma: kişinin kendisi + salon sahibi.
- `salons/{salonId}/closures/{id}` 🔒 — salon geneli tarih kapamaları `{startDate, endDate, publicNote}`; okuma: herkes.
- `salons/{salonId}/invitations/{inviteId}` 🔒 — `{phoneE164, firstName, lastName, specialty, status PENDING|ACCEPTED|REVOKED|EXPIRED, createdBy, expiresAt}`; okuma: salon sahibi veya `phoneE164 == request.auth.token.phone_number` olan davetli.
- `salons/{salonId}/services/{serviceId}` — `{staffUid, name, nameLower, description, priceKurus, durationMinutes, active, deleted, createdAt, updatedAt}`. Okuma: herkes (yalnızca `active && !deleted` sorgulanır). Yazma: `staffUid == auth.uid` ve aktif üye; fiyat 1–1.000.000 TL (kuruş), süre 5–600 dk, 5'in katı. Silme = `deleted:true` (fiziksel silme kapalı).

### 4.3 `salonCodes/{code}` 🔒
`{salonId, createdAt}`. Benzersizlik rezervasyonu; okuma: herkes (deep link çözümleme `get`). Liste sorgusu kapalı.

### 4.4 `appointments/{appointmentId}` 🔒
| Alan | Not |
|---|---|
| salonId, staffUid, customerUid | |
| salonName, staffName (snapshot) | Kart gösterimi |
| customerName, customerPhoneE164 | Belge yalnızca müşteri, atanmış uzman, salon sahibi tarafından okunur |
| items: `[{serviceId, name, priceKurus, durationMinutes}]` | **appointmentItems** snapshot'ı (gömülü) |
| totalKurus, totalMinutes, finalKurus | finalKurus = yetkili düzeltmeler sonrası |
| startAt, endAt (UTC), localDate "YYYY-MM-DD", startMinuteOfDay, endMinuteOfDay, timezone | Takvim etki analizi yerel dakikalarla yapılır |
| attention? `{reason, source, at}` | Takvim değişikliği sonrası "ilgilenilmesi gerekiyor" işareti |
| status | Durum makinesi §5 |
| pendingExpiresAt | `min(createdAt + pendingTtl, startAt)` |
| cancelReason, cancelledBy | |
| hiddenFor: `[uid]` | "Listemden gizle" (görünüm bazlı) |
| flags: `{reminder2hSentAt, arrivalPromptSentAt, reviewInviteSentAt}` | idempotent zamanlanmış işler |
| reviewId?, idempotencyKey, version | |

Değişiklik geçmişi: `appointments/{id}/events/{eventId}` 🔒 `{from, to, actorUid, actorRole, reason, at}` (randevuya özel denetim kaydı).

### 4.5 Kilitler 🔒 (istemci erişimi yok)
- `staffDayLocks/{salonId}_{staffUid}_{YYYY-MM-DD}` — `{intervals: [{appointmentId, startMin, endMin}]}` (yalnızca PENDING/CONFIRMED/IN_PROGRESS).
- `customerLocks/{uid}` — `{activeAppointmentId | null}`.
- `salonOwners/{uid}` — `{salonId, code, salonName}`; v1'de kişi başına tek sahip olunan salon ve kayıt tekrarlarında idempotentlik.
- `idempotencyKeys/{uid}_{key}` — `{result, createdAt, expireAt}` (TTL politikası).
- `rateLimits/{uid}_{action}_{window}` — sayaç.

### 4.6 `reviews/{appointmentId}` 🔒
Doküman ID = randevu ID → randevu başına tek değerlendirme seti. `{salonId, staffUid, salonRating 1–5, staffRating 1–5, serviceRating? 1–5, comment ≤500, authorDisplay ("Ayşe K."), createdAt}`. `customerUid` kamuya açık dokümanda tutulmaz; `reviewsPrivate/{appointmentId}` 🔒 içinde. Okuma: herkes. **Düzenleme politikası (v1): değerlendirme gönderildikten sonra değiştirilemez.**

### 4.7 `financialLedger/{entryId}` 🔒
`{salonId, staffUid, appointmentId, type SERVICE_COMPLETED|ADJUSTMENT|REVERSAL, amountKurus (işaretli Long), currency "TRY", occurredAt, localDate, periodKeys {day, week, month}, createdBy, reason?}`. Okuma: salon sahibi (salon kapsamı), personel yalnızca `staffUid == auth.uid`. Asla silinmez/güncellenmez.

### 4.8 `reportAggregates/{salonId}_{scope}_{periodKey}` 🔒
`scope` = `ALL` veya `staffUid`; `periodKey` = `D2026-11-17`, `W2026-11-16` (haftanın pazartesi tarihi), `M2026-11`. Alanlar: `{salonId, staffUid|null, periodType, periodKey, completedCount, amountKurus, updatedAt}`. Ledger ile aynı transaction'da artırılır; `rebuildAggregates` yönetim fonksiyonu ledger'dan yeniden üretebilir.

### 4.9 `auditLogs/{id}` 🔒
Hassas işlemler (salon oluşturma, davet, üyelik değişimi, fiyat düzeltme, yeniden atama, takvim değişikliği etkisi). İstemci okuyamaz (v1).

### 4.10 KVKK veri minimizasyonu
- Müşteri telefonu yalnızca randevu dokümanında ve kullanıcının kendi profilinde; kamuya açık hiçbir koleksiyonda yok.
- Personel kişisel telefonu yalnızca `invitations` (sahibi + davetli okur).
- Salonda gösterilen telefon `businessPhoneE164` (mekan telefonu, kayıt sırasında açıkça "herkese görünür" olarak etiketlenir).
- Değerlendirmede yazar adı kısaltılmış.

---

## 5. Randevu durum makinesi

```
            ┌──────────── EXPIRED (sistem: pendingExpiresAt geçti)
            │
PENDING ────┼──► CONFIRMED ──► IN_PROGRESS ──► COMPLETED (ledger +1, bir kez)
            │        │   │            │
            │        │   └──► NO_SHOW (start sonrası, gelir yok)
            │        │                └──► CANCELLED_BY_STAFF (gerekçeli)
            │        ├──► CANCELLED_BY_CUSTOMER (now ≤ start − 2s)
            │        └──► CANCELLED_BY_STAFF (gerekçeli)
            ├──► CANCELLED_BY_CUSTOMER (now ≤ start − 2s)
            └──► CANCELLED_BY_STAFF (= ret, gerekçeli)
```

| Geçiş | Aktör | Koşul |
|---|---|---|
| PENDING→CONFIRMED | atanmış uzman / salon sahibi | `now < pendingExpiresAt` |
| PENDING→CANCELLED_BY_STAFF | uzman / sahip | gerekçe zorunlu |
| PENDING/CONFIRMED→CANCELLED_BY_CUSTOMER | randevunun müşterisi | `now <= startAt − 120 dk` (tam 2 saat sınırında **izin verilir**) |
| PENDING→EXPIRED | sistem | `now >= pendingExpiresAt` |
| CONFIRMED→CANCELLED_BY_STAFF | uzman / sahip | gerekçe zorunlu |
| CONFIRMED→IN_PROGRESS ("Geldi") | uzman / sahip | `now >= startAt − 15 dk` |
| CONFIRMED→NO_SHOW ("Gelmedi") | uzman / sahip | `now >= startAt` |
| IN_PROGRESS→COMPLETED | uzman / sahip | ledger kaydı + aggregate, idempotent |
| IN_PROGRESS→CANCELLED_BY_STAFF | uzman / sahip | gerekçe zorunlu, gelir yok |

Terminal durumlar: `CANCELLED_*`, `EXPIRED`, `COMPLETED`, `NO_SHOW` → slot kilidi ve müşteri kilidi serbest bırakılır (COMPLETED/NO_SHOW için kilit `IN_PROGRESS`/`CONFIRMED`'dan çıkışta kaldırılır). Eşzamanlı "onay" ve "müşteri iptali": ikisi de transaction içinde güncel durumu okur; ilk gelen kazanır, ikincisi `FAILED_PRECONDITION / STATE_CHANGED` alır ve istemci güncel durumu gösterir.

Tanım: Kotlin `AppointmentStatus` + `AppointmentTransitions`; TS `statusMachine.ts`. Her iki tablo aynı unit test vakalarıyla doğrulanır.

---

## 6. Müsaitlik algoritması (sunucu, `availability.ts`)

Girdi: yerel tarih `d`, uzmanın haftalık programı (vardiyalar + molalar), uzman haftalık izinleri, uzman tarih kapamaları, salon haftalık kapalı günleri, salon tarih kapamaları, `d` gününün meşgul aralıkları (`staffDayLocks`), toplam süre `D`, adım `S` (15 dk), `now`, `minLead`, `horizonDays`.

1. `d < bugün(İstanbul)` veya `d > bugün + horizonDays` → boş.
2. Salon `d` haftanın gününde kapalı / salon kapaması `d`'yi kapsıyor / uzman izinli / uzman kapaması → boş.
3. `free = vardiyalar − molalar − meşgul` (dakika aralığı çıkarma, `[start,end)` yarı açık).
4. Her `free` aralığı `[a,b)` için `t = ceil(a / S)·S`; `t + D <= b` olduğu sürece `t` adayı; `instant(d,t) >= now + minLead` ise slot. `t += S`.
5. `instant(d, t)` = `d` yerel tarihinin `t` dakikası, `Europe/Istanbul` bölgesinde → UTC.

v1 kısıtı: vardiyalar gece yarısını geçemez (`end <= 24:00`); kayıtta doğrulanır, arayüzde açıkça uyarılır.
`createAppointment` aynı fonksiyonu çağırıp istenen başlangıcın slot listesinde olduğunu, ardından transaction'da `staffDayLocks` ile çakışmazlığı denetler.

---

## 7. Finans ve raporlama

- Gün: 00:00–24:00 İstanbul. Hafta: Pazartesi 00:00 → sonraki Pazartesi 00:00. Ay: takvim ayı.
- `completeAppointment` transaction'ı: randevu `IN_PROGRESS` mi? → `financialLedger/completion_{id}` var mı? (varsa idempotent başarı) → ledger yaz + 6 aggregate'i artır (`ALL` ve `staffUid` × D/W/M) + randevu `COMPLETED`.
- Fiyat düzeltme: `adjustAppointmentPrice` (yalnızca IN_PROGRESS veya COMPLETED; COMPLETED ise `ADJUSTMENT` ledger hareketi + aggregate farkı), gerekçe + audit log.
- Arayüz metni: **"Gerçekleşen hizmet bedeli"** (ödeme altyapısı olmadığı için "tahsil edildi" denmez). Açıklama: "Yeni gün/hafta/ay başladığında yeni dönem sayımı başlar; geçmiş raporlar korunur."

## 8. Puanlama
- Salon puanı = salon değerlendirmelerinin ortalaması; uzman puanı = uzman değerlendirmelerinin ortalaması; hizmet puanı ayrı gösterilir, ortalamalara **girmez**.
- `submitReview` transaction'ı: randevu `COMPLETED` + müşteri sahibi + `reviews/{id}` yok → yaz, `ratingSum/ratingCount/ratingAvg` güncelle. `ratingAvg = round1(sum / count)`; `count == 0` → "Yeni".

## 9. Bildirim matrisi
| Olay | Alıcı | Kanal |
|---|---|---|
| PENDING oluştu | atanmış uzman (+ uzman sahibi değilse salon sahibi: özet) | kutu + push |
| CONFIRMED | müşteri | kutu + push |
| İptal | karşı taraf + uzman/sahip | kutu + push |
| EXPIRED | müşteri + uzman | kutu + push |
| Başlangıç zamanı | uzman: "Müşteri geldi mi?" | kutu + push |
| Başlangıç − 2 saat (CONFIRMED) | müşteri: hatırlatma | kutu + push |
| COMPLETED | müşteri: değerlendirme daveti | kutu + push |
Push yükü: `{type, appointmentId}` + genel başlık ("Randevunuz onaylandı"). Ad/telefon yok.

## 10. Mantıksal açıklar ve varsayımlar
1. **Ödeme altyapısı yok** → finans "gerçekleşen hizmet bedeli"dir, tahsilat değildir.
2. **Gece yarısını aşan vardiya** v1'de desteklenmez (doğrulama + uyarı).
3. **Rezervasyon ufku 60 gün, slot 15 dk, PENDING süresi 30 dk, en erken rezervasyon şimdi+30 dk** — salon bazında yapılandırılabilir alanlar, v1'de varsayılanlar.
4. **Müşteri iptal sınırı**: `now <= startAt − 2s` izinli; PENDING için de aynı kural.
5. **Sahip yetkisi**: salondaki tüm randevuları onay/iptal/yeniden atama yapabilir; yeniden atama yalnızca hedef uzman o aralıkta müsaitse ve müşteri bilgilendirilerek yapılır.
6. **Tek aktif randevu** tüm salonlar genelindedir (müşteri başına).
7. **Personel çok salonlu olabilir**; işletme tarafında aktif salon seçilir, veriler salonId ile izole edilir. Bir uzmanın farklı salonlardaki randevuları birbirini **kilitlemez** (kilit salon×uzman×gün). Kişinin iki salonda aynı saatte randevu alması riski v1'de kabul edilir ve not edilir; v2'de kişi bazlı küresel kilit eklenebilir.
8. **Telefon değişikliği/hesap kurtarma**: Firebase Auth `updatePhoneNumber` (yeni numaraya OTP) + `phoneE164` senkronu fonksiyonla; numara kaybında destek süreci (README'de).
9. **Hesap silme**: `requestAccountDeletion` fonksiyonu; mali/denetim kayıtları yasal saklama süresince anonimleştirilerek tutulur (hukuki inceleme gerekir).
10. **Salon kodu**: 6 hane, `100000–999999` (900.000 kapasite). Rastgele üretim + `salonCodes` create-if-absent transaction'ı, 10 deneme; doluluk %80'i aşınca alarm (audit). Kod kimliktir, yetki vermez.
11. **Değerlendirme düzenleme**: v1'de yok (manipülasyonu önlemek için).
12. **App Link alan adı**: `kuaforumcepte.web.app` (Firebase Hosting) varsayıldı; kendi alan adınız varsa `local.properties → appLinkHost` ve `assetlinks.json` güncellenir. Salon konumu harita SDK'sı olmadan seçilir (cihaz konumu veya adresten Geocoder) ve harita uygulamasında kontrol ettirilir; Google Maps API anahtarı gerekmez.
13. **İl/ilçe verisi** uygulamaya gömülü statik JSON (81 il) — değişmeyen referans verisi; dinamik veri değildir.
