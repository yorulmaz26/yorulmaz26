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
| `updateSalonClosures` | `{salonId, weeklyClosedDays[], dryRun}` | `{conflicts:[AppointmentSummary]}` | `dryRun=false` ise yazar; çakışan randevular için yeniden planlama/iptal iş akışı başlatılır. |
| `upsertSalonDateClosure` / `deleteSalonDateClosure` | `{salonId, closureId?, startDate, endDate, publicNote, dryRun}` | `{conflicts}` | OWNER. |
| `updateWeeklySchedule` | `{salonId, staffUid, days, weeklyDaysOff, dryRun}` | `{conflicts}` | Kendisi (veya sahibi görüntüler; düzenleme yalnızca kendisi). Doğrulama: bitiş>başlangıç, vardiya çakışmaz, mola vardiya içinde, gece yarısı geçmez. |
| `upsertStaffDateClosure` / `deleteStaffDateClosure` | `{salonId, closureId?, startDate, endDate, note, dryRun}` | `{conflicts}` | |

## Randevu (Aşama 5–6)
| `getAvailability` | `{salonId, staffUid, serviceIds[], fromDate, days ≤ 14}` | `{totalMinutes, totalKurus, days:[{date, slots:[{startAt(ISO UTC), label "HH:mm"}]}]}` | Sunucu algoritması (§6). |
| `createAppointment` | `{salonId, staffUid, serviceIds[], startAt, expectedTotalKurus, idempotencyKey}` | `{appointmentId, status:"PENDING", pendingExpiresAt}` | Hatalar: `SLOT_TAKEN`, `SLOT_UNAVAILABLE`, `PRICE_CHANGED {newTotalKurus}`, `ACTIVE_APPOINTMENT_EXISTS {appointmentId}`, `SERVICE_INACTIVE`, `STAFF_INACTIVE`. |
| `transitionAppointment` | `{appointmentId, action: CONFIRM\|REJECT\|CANCEL_BY_CUSTOMER\|CANCEL_BY_STAFF\|MARK_ARRIVED\|MARK_NO_SHOW\|COMPLETE, reason?, idempotencyKey}` | `{status}` | Durum makinesi (§5). `COMPLETE` ledger yazar. Hatalar: `INVALID_TRANSITION`, `PENDING_EXPIRED`, `CANCEL_WINDOW_CLOSED`, `TOO_EARLY`. |
| `reassignAppointment` | `{appointmentId, newStaffUid, idempotencyKey}` | `{}` | OWNER; hedef uzmanda aynı aralık müsait olmalı. |
| `adjustAppointmentPrice` | `{appointmentId, newFinalKurus, reason, idempotencyKey}` | `{finalKurus}` | Audit log. |
| `hideAppointments` | `{appointmentIds[] ≤ 100}` | `{hidden}` | Yalnızca terminal durumdaki ve kullanıcının görebildiği randevular. |

## Finans (Aşama 7)
| `getReports` | `{salonId, periodType: DAY\|WEEK\|MONTH, anchorDate, staffUid?}` | `{period:{start,end,label}, salon?:{count, amountKurus}, staff:[{staffUid, name, count, amountKurus}]}` | OWNER tümünü; STAFF yalnızca kendi `staffUid`'si (diğer parametreler reddedilir). |

## Puan ve bildirim (Aşama 6, 8)
| `submitReview` | `{appointmentId, salonRating, staffRating, serviceRating?, comment?}` | `{}` | `NOT_COMPLETED`, `ALREADY_REVIEWED`. |
| `registerDevice` | `{installationId, token}` | `{}` | |
| `markNotificationsRead` | `{ids[] \| all:true}` | `{}` | |
| `requestAccountDeletion` | `{}` | `{requestId}` | |

## Zamanlanmış
| `scanAppointments` (her 1 dk) | PENDING süre aşımı → EXPIRED; CONFIRMED & start−2s → hatırlatma; start → "Müşteri geldi mi?"; bayraklarla idempotent. |
| `onAppointmentWritten` (Firestore trigger) | Durum değişiminde bildirim üretimi (event ID ile tekilleştirme). |
