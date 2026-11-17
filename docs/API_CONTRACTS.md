# Cloud Functions API Sözleşmeleri (v1)

Tümü **HTTPS Callable**, bölge `europe-west1`, `enforceAppCheck: true`, kimlik doğrulama zorunlu (aksi belirtilmedikçe). Yanıt hataları `HttpsError` kodlarıyla döner; `details.reason` makine-okunur nedeni taşır (ör. `SLOT_TAKEN`). İstemci `details.reason` → Türkçe mesaj eşlemesini `app/.../core/data/FunctionErrorMapper.kt` içinde yapar.

Mutasyon yapan tüm çağrılar `idempotencyKey: string (UUID)` alır. Aynı kullanıcı + aynı anahtar ikinci kez gelirse ilk sonucun aynısı döner.

`consents` = `{kvkkVersion, termsVersion}`; sunucudaki `LEGAL_VERSIONS` ile aynı olmalı, aksi halde `CONSENT_REQUIRED`.

Ortak hata nedenleri: `UNAUTHENTICATED`, `PERMISSION_DENIED`, `INVALID_ARGUMENT`, `NOT_FOUND`, `STATE_CHANGED`, `RATE_LIMITED`, `APP_CHECK_FAILED`.

## Kimlik ve kayıt (Aşama 2)
| Fonksiyon | Girdi | Çıktı | Notlar |
|---|---|---|---|
| `bootstrapSession` | `{}` | `{user: {exists, firstName?, lastName?}, memberships:[{salonId, salonName, role, status}], pendingInvitations:[{salonId, inviteId, salonName, specialty}], activeAppointmentId?}` | Giriş sonrası yönlendirme kararı. |
| `registerCustomer` | `{firstName, lastName, consents:{kvkkVersion, explicitConsentVersion}, idempotencyKey}` | `{uid}` | Telefon token'dan alınır. |
| `registerSalonOwner` | `{firstName, lastName, consents, salon:{name, businessPhone, type, city, district, addressLine, description}, logoPath, photoPaths[≤5], idempotencyKey}` | `{salonId, code, salonName}` | Atomik: salon + kod rezervasyonu + OWNER üyelik + varsayılan program. Görseller önce `uploads/{uid}/pending/...` alanına yüklenir; fonksiyon doğrulayıp `salons/{salonId}/...` altına taşır. |
| `createStaffInvitation` | `{salonId, firstName, lastName, specialty, phone, idempotencyKey}` | `{inviteId}` | Yalnızca OWNER. Telefon E.164'e normalize. Aynı telefona açık davet varsa `ALREADY_INVITED`. |
| `revokeInvitation` | `{salonId, inviteId}` | `{status}` | OWNER; yalnızca PENDING. |
| `declineInvitation` | `{salonId, inviteId}` | `{status}` | Davetli (telefon eşleşmeli). |
| `updateStaffMember` | `{salonId, memberUid, firstName, lastName, specialty}` | `{ok}` | OWNER herkesi, üye yalnızca kendini düzenler. |
| `acceptInvitation` | `{salonId, inviteId, consents?}` | `{salonId, salonName}` | `auth.token.phone_number == invite.phoneE164` zorunlu. Kod bilmek yetmez. Profil yoksa davet bilgileriyle oluşturulur (onaylar gerekir). Ayrılmış üye yeniden katılabilir (puanları korunur). |
| `setMemberStatus` | `{salonId, memberUid, status: ACTIVE\|INACTIVE}` | `{affectedFutureAppointments:[id]}` | OWNER; sahip kendini pasifleştiremez. |
| `leaveSalon` | `{salonId}` | `{affectedFutureAppointments}` | STAFF. |

## Salon (Aşama 3–4)
| Fonksiyon | Girdi | Çıktı | Notlar |
|---|---|---|---|
| `updateSalonProfile` | `{salonId, name, type, businessPhone, city, district, addressLine, description, location?:{lat,lng}}` | `{ok}` | OWNER. İl/ilçe listesiyle, koordinat Türkiye sınırıyla doğrulanır. Ad değişirse tüm üyelerin `memberships` aynası aynı transaction'da güncellenir. Kod değiştirilemez. |
| `updateSalonMedia` | `{salonId, logoPendingPath?, photos:[{existingPath}\|{pendingPath}] (≤5, sıralı)}` | `{logoUrl, photos}` | OWNER. Yeni görseller geçici alandan kalıcı alana taşınır; çıkarılan dosyalar ve eski logo silinir. Eşzamanlı değişiklikte `STATE_CHANGED`. |
| `salonLanding` (HTTPS, Hosting `/s/{kod}`) | — | HTML | Uygulama yüklü değilse açılan web sayfası: yalnızca kamuya açık salon alanları, "Uygulamada Aç" intent'i, Play bağlantısı. |
| `updateWeeklySchedule` | `{salonId, days:{"1".."7":{shifts,breaks}}, weeklyDaysOff[], dryRun, acknowledgeConflicts}` | `{applied, conflicts}` | Yalnızca kendi programı. Doğrulama: bitiş>başlangıç, vardiyalar/molalar çakışmaz, mola vardiya içinde, gece yarısı geçmez, gün başına ≤4 vardiya ≤6 mola. |
| `upsertStaffDateClosure` / `deleteStaffDateClosure` | `{salonId, closureId?, startDate, endDate, note, dryRun, acknowledgeConflicts}` | `{applied, conflicts, closureId}` | Geçmiş tarih yok, ≤366 gün; not yalnızca kişi + sahip görür. |
| `updateSalonClosedDays` | `{salonId, weeklyClosedDays[], dryRun, acknowledgeConflicts}` | `{applied, conflicts}` | OWNER; 7 günün tamamı kapatılamaz (`ALL_DAYS_CLOSED`). Tüm uzmanların randevuları analiz edilir. |
| `upsertSalonDateClosure` / `deleteSalonDateClosure` | `{salonId, closureId?, startDate, endDate, note, dryRun, acknowledgeConflicts}` | `{applied, conflicts, closureId}` | OWNER; not `publicNote` olarak kamuya açık. |
| `serviceImpact` | `{salonId, serviceId}` | `{futureAppointments}` | Hizmetin sahibi veya salon sahibi. Bilgilendirme: randevular snapshot taşır, hizmet değişikliği onları etkilemez. |

**Çakışma akışı:** İstemci önce `dryRun:true` çağırır. `conflicts` boşsa uygular; doluysa kullanıcıya listeler ve onayla `acknowledgeConflicts:true` gönderir. Onaysız uygulamada çakışma varsa `CONFLICTS_NOT_ACKNOWLEDGED {conflicts}`. Uygulanan değişiklikte çakışan randevular iptal edilmez: `attention:{reason, source, at}` ile işaretlenir, uzman ve salon sahibine `SCHEDULE_CONFLICT` bildirimi düşer; yeniden planlama/iptal randevu yönetiminden yapılır. `conflicts[]` = `{appointmentId, staffUid, localDate, start, end, status, customerName, reason}`; `reason` ∈ `SALON_CLOSED_DAY | SALON_CLOSURE | STAFF_DAY_OFF | STAFF_CLOSURE | OUTSIDE_WORKING_HOURS | OVERLAPS_BREAK`.

Hizmetler (`salons/{id}/services`) istemciden doğrudan yazılır; Firestore kuralları sahipliği (staffUid == uid, aktif üye), fiyat (1–1.000.000 TL, kuruş) ve süre (5–600 dk, 5'in katı) sınırlarını denetler. Silme yumuşaktır (`deleted:true`).

## Randevu (Aşama 5–6)
| `getAvailability` | `{salonId, staffUid, serviceIds[], fromDate, days ≤ 14}` | `{totalMinutes, totalKurus, horizonDays, slotMinutes, days:[{date, slots:[{startAt(ISO UTC), label "HH:mm"}]}]}` | Sunucu algoritması (§6). Hizmetler aynı uzmana ait, aktif ve silinmemiş olmalı (`SERVICE_STAFF_MISMATCH`, `SERVICE_INACTIVE`). |
| `createAppointment` | `{salonId, staffUid, serviceIds[], startAt, expectedTotalKurus, idempotencyKey}` | `{appointmentId, number, status:"PENDING", pendingExpiresAt}` | Tek transaction: fiyat + müsaitlik yeniden doğrulanır, `staffDayLocks` ile çakışma, `customerLocks` ile tek aktif randevu. Hatalar: `SLOT_TAKEN`, `SLOT_UNAVAILABLE`, `PRICE_CHANGED {newTotalKurus}`, `ACTIVE_APPOINTMENT_EXISTS {appointmentId}`, `SERVICE_INACTIVE`, `STAFF_INACTIVE`, `CANNOT_BOOK_SELF`, `PROFILE_REQUIRED`, `RATE_LIMITED` (10/saat). |
| `transitionAppointment` | `{appointmentId, action: CONFIRM\|REJECT\|CANCEL_BY_CUSTOMER\|CANCEL_BY_STAFF\|MARK_ARRIVED\|MARK_NO_SHOW\|COMPLETE, reason?}` | `{status, applied}` | Durum makinesi (§5). Süresi dolmuş PENDING önce EXPIRED yapılır (`applied:false`). Terminal geçişte kilitler serbest bırakılır; CONFIRM kilitteki son kullanmayı kaldırır. Hatalar: `STATE_CHANGED`, `PERMISSION_DENIED`, `REASON_REQUIRED`, `CANCEL_WINDOW_CLOSED`, `TOO_EARLY`. `COMPLETE` finans kaydıyla birlikte Aşama 7'de (`COMPLETION_NOT_AVAILABLE`). |
| `reassignAppointment` | `{appointmentId, newStaffUid}` | `{staffUid}` | OWNER; PENDING/CONFIRMED. Kalemler (fiyat/süre snapshot) korunur. Hedef uzman o aralıkta çalışıyor olmalı (`STAFF_NOT_AVAILABLE`) ve boş olmalı (`SLOT_TAKEN`). Kilit taşınır, `attention` temizlenir; müşteri + iki uzman bilgilendirilir. |
| `rescheduleAppointment` | `{appointmentId, newStartAt}` | `{startAt}` | Atanmış uzman veya OWNER; yeni başlangıç güncel müsaitlikte (randevunun kendisi hariç) geçerli slot olmalı. Tarih değişirse kilit taşınır, hatırlatma bayrakları sıfırlanır, müşteriye bildirilir (müşteri uygun değilse kendi iptal hakkını kullanır). |
| `getAvailability` (yeniden planlama modu) | `{appointmentId, fromDate, days}` | aynı | Randevunun süresiyle, kendisi meşgul sayılmadan. Uzman/OWNER. |
| `adjustAppointmentPrice` | `{appointmentId, newFinalKurus, reason}` | `{finalKurus}` | Gerekçe 3–300 karakter, 0 ≤ tutar ≤ 1.000.000 TL. IN_PROGRESS: atanmış uzman/sahip, yalnızca `finalKurus`. COMPLETED: yalnızca sahip; `adjustment_{id}_{n}` ledger farkı **orijinal tamamlama dönemlerine** işlenir (sayım değişmez). Olay + denetim kaydı. `NO_CHANGE`, `STATE_CHANGED`. |
| `hideAppointments` | `{appointmentIds[] ≤ 100}` | `{hidden}` | Yalnızca terminal durumdaki ve kullanıcının görebildiği randevular (`CANNOT_HIDE_ACTIVE`). `hiddenFor` dizisine çağıran eklenir; kayıt silinmez. |

## Finans (Aşama 7)
| `getReports` | `{salonId, periodType: DAY\|WEEK\|MONTH, anchorDate}` | `{period:{type,key,start,end}, salon: {completedCount, amountKurus, listedKurus} \| null, staff:[{staffUid, name, role, status, completedCount, amountKurus, listedKurus}]}` | OWNER: salon + tüm uzmanlar; aktif STAFF: `salon=null`, yalnızca kendi satırı. |
| `rebuildReportAggregates` | `{salonId}` | `{entries, aggregates}` | OWNER; sayaçları değişmez ledger'dan yeniden üretir (bakım). |
| `transitionAppointment` `COMPLETE` | — | — | IN_PROGRESS → COMPLETED; aynı transaction'da `financialLedger/completion_{id}` (`tx.create`) + 6 `reportAggregates` artışı (salon ALL ve uzman × D/W/M, tamamlama anının İstanbul dönemleri). İkinci/eşzamanlı tamamlama `STATE_CHANGED`. Müşteriye `REVIEW_INVITE`. |

## Puan ve bildirim (Aşama 6, 8)
| `submitReview` | `{appointmentId, salonRating 1–5, staffRating 1–5, serviceRating? 1–5, comment? ≤500}` | `{reviewId, salonRatingAvg, staffRatingAvg}` | Yalnızca randevunun müşterisi ve COMPLETED. `reviews/{appointmentId}` transaction içinde `create` → randevu başına bir set (eşzamanlı ikinci istek düşer). Salon `ratingSum/Count/Avg` ← salonRating; uzman üyeliği ← staffRating; serviceRating ortalamalara girmez. Kamuya açık belgede müşteri uid yok (`reviewsPrivate`), yazar "Ad S.". Düzenleme yok (v1). Hatalar: `NOT_COMPLETED`, `ALREADY_REVIEWED`, `INVALID_RATING`, `PERMISSION_DENIED`. |
| `registerDevice` | `{installationId, token}` | `{}` | |
| `markNotificationsRead` | `{ids[] \| all:true}` | `{}` | |
| `requestAccountDeletion` | `{}` | `{requestId}` | |

## Zamanlanmış
| `scanAppointments` (her 1 dk, Europe/Istanbul) | PENDING süre aşımı → EXPIRED (kilitler serbest, müşteri + uzmana bildirim); CONFIRMED ve başlangıca ≤2 saat → müşteriye hatırlatma (`flags.reminder2hSentAt`); başlangıç geldi → uzmana "Müşteri geldi mi?" (`flags.arrivalPromptSentAt`, 6 saate kadar). Her biri transaction + bayrakla tek sefer. |
| `pushOnNotification` (Firestore `users/{uid}/notifications/{id}` oluşturma) | Kullanıcının cihazlarına FCM **data** mesajı (`type, title, body, notificationId, appointmentId, salonId`). `push.claimedAt` ile tek gönderim; geçersiz token'lar silinir; `push.result` = SENT/FAILED/NO_DEVICE. İçerikte müşteri adı/telefonu yok; Android kilit ekranında genel metin gösterir. |


## Sonradan eklenenler
| Fonksiyon | Girdi | Çıktı | Notlar |
|---|---|---|---|
| `reportReview` | `{reviewId, reason: OFFENSIVE\|SPAM\|FALSE_INFO\|PERSONAL_DATA\|OTHER, note?}` | `{reportCount, flagged}` | Kullanıcı başına yorum başına bir şikâyet (`reviewReports/{reviewId}_{uid}`); 20/gün. 3 şikâyet veya PERSONAL_DATA → `flagged`. `ALREADY_REPORTED`, `REVIEW_NOT_FOUND`. |
| `moderateReview` | `{reviewId, action: HIDE\|RESTORE\|DISMISS, note?}` | `{hidden, flagged}` | Yalnızca `admin` özel yetkisi. HIDE/RESTORE salon ve uzman puan toplamlarını transaction içinde düzeltir. |
| `closeSalon` | `{salonId, confirm: "KAPAT", reason}` | `{cancelledAppointments}` | OWNER. `IN_PROGRESS_APPOINTMENTS` varsa reddedilir. Salon `CLOSED`, üyelikler `LEFT`, `salonOwners` kilidi silinir. |
| `transferSalonOwnership` | `{salonId, newOwnerUid, confirm: "DEVRET"}` | `{ownerUid}` | OWNER → aktif STAFF. `NEW_OWNER_NOT_ACTIVE_STAFF`, `NEW_OWNER_ALREADY_OWNS_SALON`. |
| `updateMyName` | `{firstName, lastName}` | `{ok}` | Profil + tüm uzman kartları + sahip adı + aktif randevulardaki müşteri/uzman adı. |
| `syncPhoneNumber` | `{}` | `{phoneE164}` | İstemci `updatePhoneNumber` + token yenileme sonrası; numara yalnızca doğrulanmış token'dan alınır. |
| `deleteAccount` | `{confirm: "SIL"}` | `{deleted}` | Bkz. docs/SECURITY.md. |
| `dailyCleanup` (zamanlanmış, 04:17) | — | — | 24 saati geçen geçici yüklemeler ve süresi dolmuş idempotency/hız limiti kayıtları. |

`staffDayLocks` belge kimliği `{kişiUid}_{YYYY-MM-DD}` (kişi bazlı); aralıklar `salonId` ve `role: STAFF|CUSTOMER` taşır.

## İşletme ve müşteri deneyimi (2. tur)
| Fonksiyon | Girdi | Çıktı | Notlar |
|---|---|---|---|
| `createAppointment` (genişletildi) | `+ guestName?, discountCode?, useLoyaltyReward?` | `+ finalKurus` | En fazla 3 aktif randevu (`ACTIVE_APPOINTMENT_LIMIT`). Kendisi için ise müşterinin kişi kilidi de kontrol edilir (`CUSTOMER_BUSY`). Kod/ödül: `DISCOUNT_CONFLICT`, `CODE_NOT_FOUND\|INACTIVE\|NOT_STARTED\|EXPIRED\|EXHAUSTED\|ALREADY_USED`, `LOYALTY_NOT_AVAILABLE`. |
| `getAvailability` (genişletildi) | `+ forSelf?` | — | Yeni randevuda müşterinin kendi meşgul saatleri gizlenir; `forSelf:false` (başkası adına) gizlemez. |
| `createWalkInAppointment` | `{salonId, staffUid, serviceIds, startAt, customerName, customerPhone?, idempotencyKey}` | `{appointmentId, number}` | Uzman (kendi adına) / sahip. Doğrudan CONFIRMED, `customerUid:null`, `walkIn:true`. Mesai dışı serbest; çakışma ve ufuk denetlenir. |
| `getAppointmentContacts` | `{appointmentId}` | `{contacts:[{role: STAFF\|OWNER\|SALON, name, phoneE164}]}` | Yalnızca randevunun müşterisi, aktif randevu. Son 2 saatte iptal için arama. |
| `previewBookingPrice` | `{salonId, staffUid, serviceIds, discountCode?, useLoyaltyReward?}` | `{totalKurus, discountKurus, finalKurus, codeError, loyalty}` | Bağlayıcı değil. |
| `upsertDiscountCode` | `{salonId, code, percent\|amountKurus, validFrom?, validUntil?, maxUses?, active, description?}` | `{code}` | OWNER. Kişi başı bir kullanım (`discountCodeUses`); kullanım randevuda ayrılır, iptal/süre aşımı/gelmedi'de iade. |
| `updateLoyaltyProgram` | `{salonId, enabled, visitsRequired 3–50, rewardPercent 5–100}` | program | OWNER. Ödül = floor(tamamlanan/gerekli) − kullanılan (`salons/{id}/customers/{uid}`). |
| `updateStaffMember` (genişletildi) | `+ commissionPercent?` | — | Yalnızca sahip; `salons/{id}/staffPrivate/{uid}`. Tamamlamada ledger'a `commissionKurus` yazılır; raporlarda toplanır. |
| `joinWaitlist` / `leaveWaitlist` | `{salonId, staffUid, localDate}` / `{id}` | `{id}` / `{ok}` | En fazla 10 kayıt. `onAppointmentFreed` (Firestore tetikleyici) iptal/süre aşımı/taşımada listedekilere bildirim gönderir (30 dk'da bir). |
| `replyToReview` | `{reviewId, text}` | `{replied}` | OWNER; boş metin yanıtı kaldırır; ilk yanıtta müşteriye bildirim. |
| `reviewSalon` | `{salonId, action: APPROVE\|REJECT, note?}` | `{verified}` | Yalnızca `admin`. Onaysız salon aramada görünmez (kod/QR ile ulaşılır). |
| `requestSalonReview` | `{salonId}` | `{status}` | Reddedilen salonun sahibi yeniden incelemeye gönderir. |
| `reopenSalon` | `{salonId}` | `{status}` | Eski sahip; başka salonu yoksa (`ALREADY_OWNER`). Diğer uzmanlar yeniden davet edilir. |
| `searchNearbySalons` | `{lat, lng, radiusKm 1–50, type?}` | `{salons:[…, distanceKm]}` | Geohash öneki + Haversine; aktif ve onaylı salonlar, en yakın 50. |
| `getCalendarFeed` | `{rotate?}` | `{token, path}` | Kişiye özel iCalendar aboneliği; `calendarFeed` HTTP: `GET /cal/{token}.ics` (Hosting yönlendirmesi). |
| `scanAppointments` (genişletildi) | — | — | 24 saat (son 2 saat iptal kuralı hatırlatmasıyla) ve 2 saat hatırlatma. |


## Moderatör paneli
| Fonksiyon | Girdi | Çıktı | Notlar |
|---|---|---|---|
| `adminRemoveSalon` | `{salonId, reason, confirm: "KALDIR"}` | `{cancelledAppointments}` | Yalnızca `admin`. Salon CLOSED + `removedByAdmin`; işlemdekiler dahil aktif randevular iptal, üyelikler biter; sahip `reopenSalon` ile açamaz (`SALON_REMOVED`). |
| `setPlatformAdmin` | `{phone, grant}` | `{uid, admin}` | Yalnızca `admin`. Kişi en az bir kez giriş yapmış olmalı (`USER_NOT_FOUND`); kendi yetkisini kaldıramaz (`CANNOT_REVOKE_SELF`). `platformAdmins/{uid}` listesi. |

Moderatör hesabı girişte doğrudan **Moderatör Paneli**'ne açılır (Salon onayı, Salonlar/kaldırma, salon bazında Yorumlar/silme-geri yükleme, Şikâyetler, Yöneticiler).
