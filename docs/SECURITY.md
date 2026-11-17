# Güvenlik ve Gizlilik Özeti

## Yetki modeli (katmanlar)
| Katman | Görev |
|---|---|
| Firebase Auth (telefon OTP) | Kimlik. SMS kodu Firebase'de üretilir/doğrulanır; uygulama kod görmez. |
| App Check (Play Integrity / debug) | Yalnızca orijinal uygulamadan gelen çağrılar; callable'larda `enforceAppCheck: true` (emülatör hariç). |
| Cloud Functions | Tüm hassas yazımlar: randevu durumu, ücret, sahiplik, üyelik, puan, finans, takvim etkisi. Her callable: kimlik, sahiplik/üyelik denetimi, girdi doğrulama, gerekirse idempotency ve hız limiti, denetim kaydı. |
| Firestore kuralları | İstemci yazımı yalnızca: kendi ad/soyadı, cihaz token'ı, bildirim "okundu", kendi hizmetleri (alan/sınır doğrulamalı). Okumalar en az yetki ilkesiyle (`firestore.rules`). |
| Storage kuralları | İstemci yalnızca `uploads/{uid}/pending` altına ≤5 MB görsel yükler; kalıcı salon görsellerini yalnızca sunucu yazar. |

## Hız limitleri
| İşlem | Limit |
|---|---|
| SMS OTP | Firebase Auth'un yerleşik kötüye kullanım koruması + App Check; Console → Authentication → Settings → SMS bölge politikası: yalnızca TR önerilir. İstemci 60 sn yeniden gönderme bekletmesi. |
| Randevu oluşturma | 10 / saat / kullanıcı |
| Salon kaydı | 5 / saat / kullanıcı |
| Personel daveti | 30 / gün / salon |

## Veri tutarlılığı garantileri (testlerle doğrulanır)
- Aynı uzmana çakışan iki randevu oluşamaz (uzman×gün kilidi transaction'ı).
- Müşteri başına tek aktif randevu (müşteri kilidi transaction'ı).
- Tamamlama yalnızca bir kez finansa yazılır (`completion_{id}` create).
- Randevu başına tek değerlendirme (`reviews/{id}` create), ortalamalar atomik.
- Zamanlanmış işler (süre aşımı, hatırlatma, "Müşteri geldi mi?") bayrakla tek sefer.

## KVKK / veri minimizasyonu
- Müşteri telefonu yalnızca kendi profili ve ilgili randevu belgesinde (müşteri, atanmış uzman, salon sahibi okuyabilir); kamuya açık hiçbir koleksiyonda yok.
- Personel kişisel telefonu yalnızca davet belgesinde (sahip + davetli).
- Değerlendirmelerde yazar "Ad S."; müşteri kimliği ayrı, istemciye kapalı `reviewsPrivate` koleksiyonunda.
- Bildirim içerikleri kişisel veri içermez; kilit ekranında genel metin gösterilir.
- Yedekleme: `android:allowBackup=false`, veri çıkarma kuralları oturum/tercih verisini dışarıda tutar.
- Hesap silme: uygulama içinden (Hesap → Hesabımı Sil). Kişisel veriler silinir/anonimleştirilir; mali kayıtlar kişisel veri olmadan korunur.

## Moderasyon
- Oturumlu herkes bir yorumu bir kez şikâyet edebilir (hakaret, spam, yanıltıcı, kişisel veri, diğer; günde 20 şikâyet sınırı).
- 3 farklı şikâyet veya bir "kişisel veri" şikâyeti yorumu inceleme kuyruğuna alır (yayında kalır).
- **Platform yöneticisi** (`admin` özel yetkisi) Hesap → Yorum Moderasyonu ekranından yorumu yayından kaldırır (puan
  salon/uzman ortalamasından çıkarılır), geri yayınlar (puan geri eklenir) veya şikâyeti reddeder. Tüm kararlar denetim kaydına yazılır.
- Gizlenen yorumlar ve şikâyet ayrıntıları Firestore kurallarıyla yalnızca yöneticiye açıktır.
- Yönetici atama: `functions/scripts/setAdmin.js` (servis hesabı anahtarıyla, yalnızca proje sahibi).

## Salon yaşam döngüsü
- **Devir:** Sahip, salonu aktif bir uzmanına devreder; eski sahip uzman olarak kalır.
- **Kapatma:** "KAPAT" onayı + gerekçe; işlemdeki randevu varsa engellenir; gelecekteki randevular gerekçeyle iptal edilir ve
  müşterilere bildirilir; üyelikler sona erer; finans kayıtları korunur. Ardından sahip hesabını silebilir.

## Kişi bazlı çakışma
Kilit kişi bazlıdır (`staffDayLocks/{kişi}_{gün}`), uzman (STAFF) ve müşteri (CUSTOMER) aralıklarını birlikte tutar:
aynı uzman birden çok salonda çalışsa bile aynı saate ikinci randevu alınamaz; bir kişi kendisi için aynı saatte iki randevu
alamaz ve uzman olarak hizmet verdiği saatte başka salondan kendisi için randevu alamaz (tersi de geçerli). Başkası adına
alınan randevu müşterinin kendi takvimini kilitlemez. Müşteri aynı anda en fazla 3 aktif randevu tutar.

## İptal kuralları
- Müşteri başlangıçtan en geç 2 saat öncesine kadar uygulamadan iptal eder; son 2 saatte `CANCEL_WINDOW_CLOSED`.
  Bu durumda uygulama `getAppointmentContacts` ile yalnızca o randevunun müşterisine uzman/sahip/salon telefonunu verir.
- Uzman ve salon sahibi her zaman (son 2 saat dahil) gerekçeyle iptal edebilir. Her iptalde diğer taraflar bildirim alır
  (müşteri iptali → uzman + sahip; uzman iptali → müşteri + sahip; sahip iptali → müşteri + uzman).

## Yeni veri alanları ve erişim
| Koleksiyon | Okuma | Yazma |
|---|---|---|
| `users/{uid}/favorites` | kişinin kendisi | kişinin kendisi (alan doğrulamalı oluştur/sil) |
| `salons/{id}/staffPrivate/{uid}` (prim oranı) | sahip, uzmanın kendisi | yalnızca sunucu |
| `salons/{id}/customers/{uid}` (tamamlanan/gelmeyen, sadakat) | müşterinin kendisi, sahip, aktif uzman | yalnızca sunucu |
| `salons/{id}/discountCodes` | sahip, aktif uzman (müşteri listeleyemez) | yalnızca sunucu |
| `waitlist` | kaydın sahibi | yalnızca sunucu |
| `discountCodeUses`, `calendarTokens` | kapalı | yalnızca sunucu |

Takvim aboneliği adresi tahmin edilemez bir anahtar içerir (24 bayt); uzman/sahip için müşteri adlarını içerdiğinden kullanıcı
uyarılır ve "Adresi yenile" eski adresi geçersiz kılar. Elden randevularda müşterinin hesabı yoktur; bildirim gönderilmez.
Salon onayı: yeni salon `verified:false` ile oluşur; platform yöneticisi onaylayana kadar aramada ve konum aramasında görünmez.

## Bilinen açık noktalar (yayın öncesi karar gerektirir)
- Yasal metinler TASLAK; hukuki inceleme ve veri sorumlusu bilgisi gerekli. Yurt dışı aktarım dayanağı netleştirilmeli.
- Kişi çakışması randevular üzerinden denetlenir; uzmanın mesai saatinde (randevusu olmayan boş saatinde) kendisi için başka salondan randevu alması bilinçli olarak serbesttir.
- Takvim aboneliği adresi paylaşılırsa randevu bilgileri görülebilir; kullanıcı adresi yenileyerek eskisini iptal edebilir.
