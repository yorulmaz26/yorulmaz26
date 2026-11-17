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
| `adjustAppointmentPrice` | `{appointmentId, newFinalKurus, reason, idempotencyKey}` | `{finalKurus}` | Audit log. |
| `hideAppointments` | `{appointmentIds[] ≤ 100}` | `{hidden}` | Yalnızca terminal durumdaki ve kullanıcının görebildiği randevular (`CANNOT_HIDE_ACTIVE`). `hiddenFor` dizisine çağıran eklenir; kayıt silinmez. |

## Finans (Aşama 7)
| `getReports` | `{salonId, periodType: DAY\|WEEK\|MONTH, anchorDate, staffUid?}` | `{period:{start,end,label}, salon?:{count, amountKurus}, staff:[{staffUid, name, count, amountKurus}]}` | OWNER tümünü; STAFF yalnızca kendi `staffUid`'si (diğer parametreler reddedilir). |

## Puan ve bildirim (Aşama 6, 8)
| `submitReview` | `{appointmentId, salonRating, staffRating, serviceRating?, comment?}` | `{}` | `NOT_COMPLETED`, `ALREADY_REVIEWED`. |
| `registerDevice` | `{installationId, token}` | `{}` | |
| `markNotificationsRead` | `{ids[] \| all:true}` | `{}` | |
| `requestAccountDeletion` | `{}` | `{requestId}` | |

## Zamanlanmış
| `scanAppointments` (her 1 dk, Europe/Istanbul) | PENDING süre aşımı → EXPIRED (kilitler serbest, müşteri + uzmana bildirim); CONFIRMED ve başlangıca ≤2 saat → müşteriye hatırlatma (`flags.reminder2hSentAt`); başlangıç geldi → uzmana "Müşteri geldi mi?" (`flags.arrivalPromptSentAt`, 6 saate kadar). Her biri transaction + bayrakla tek sefer. |
| `pushOnNotification` (Firestore `users/{uid}/notifications/{id}` oluşturma) | Kullanıcının cihazlarına FCM **data** mesajı (`type, title, body, notificationId, appointmentId, salonId`). `push.claimedAt` ile tek gönderim; geçersiz token'lar silinir; `push.result` = SENT/FAILED/NO_DEVICE. İçerikte müşteri adı/telefonu yok; Android kilit ekranında genel metin gösterir. |
